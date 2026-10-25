<template>
  <div class="teacher-dashboard">
    <header class="page-head">
      <div class="page-head-text">
        <h1 class="oslab-page-title">班级分析</h1>
        <p class="page-desc">
          全部数值来自学习事件、掌握度与作答记录的实时聚合，可对照数据库核验；页面不展示虚构的更新时间，样本为空时如实显示「暂无」而非
          0%。
        </p>
      </div>
      <div class="page-actions">
        <span class="class-label">班级</span>
        <a-select
          v-model:model-value="className"
          class="class-select"
          placeholder="选择班级"
          :loading="classesLoading"
          allow-search
        >
          <a-option
            v-for="c in classOptions"
            :key="c.className"
            :value="c.className"
          >
            {{ c.className }}（{{ c.studentCount }} 人）
          </a-option>
        </a-select>
        <a-button
          type="primary"
          :disabled="!className"
          :loading="dashboardLoading"
          @click="refreshAll"
        >
          刷新
        </a-button>
      </div>
    </header>

    <div v-if="classesError" class="oslab-error" role="alert">
      <span class="oslab-state-title">班级列表加载失败</span>
      <span class="oslab-state-hint">{{ classesError }}</span>
      <a-button size="small" @click="loadClasses">重试</a-button>
    </div>
    <div
      v-else-if="!classOptions.length && !classesLoading"
      class="oslab-empty"
    >
      <span class="oslab-state-title">暂无班级数据</span>
      <span class="oslab-state-hint">
        students 表暂无绑定账号的班级记录，无法生成班级分析。
      </span>
    </div>

    <template v-else-if="className">
      <div v-if="dashboardError && !dashboard" class="oslab-error" role="alert">
        <span class="oslab-state-title">班级数据加载失败</span>
        <span class="oslab-state-hint">{{ dashboardError }}</span>
        <a-button type="primary" size="small" @click="refreshAll">
          重试
        </a-button>
      </div>
      <div v-else-if="!dashboard" class="oslab-loading">
        <span class="oslab-spinner" />
        <span class="oslab-state-hint">正在聚合班级数据…</span>
      </div>

      <template v-else>
        <div v-if="dashboardError" class="stale-banner" role="alert">
          最新数据刷新失败：{{
            dashboardError
          }}。当前展示本班级最近一次成功加载的数据，可点击「刷新」重试。
        </div>

        <section class="kpi-grid" aria-label="班级概况">
          <div class="kpi-card">
            <span class="kpi-label">班级学生数</span>
            <span class="kpi-value">{{ dashboard.studentCount }}</span>
            <span class="kpi-hint"> students 表中绑定本班级的有效账号数 </span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">实验有效通过率</span>
            <span
              v-if="dashboard.experimentPassRate !== null"
              class="kpi-value"
            >
              {{ (dashboard.experimentPassRate * 100).toFixed(1)
              }}<span class="kpi-unit">%</span>
            </span>
            <span v-else class="kpi-value kpi-value-empty">暂无样本</span>
            <span class="kpi-hint">
              编程提交按提交 ID 去重并排除系统错误；无有效提交时显示暂无，不显示
              0%
            </span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">有掌握记录的知识点</span>
            <span class="kpi-value">{{ dashboard.nodeHeat.length }}</span>
            <span class="kpi-hint">
              班级内出现掌握记录的图谱节点个数（非题目数）
            </span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">有作答记录的测评题</span>
            <span class="kpi-value">{{
              dashboard.questionAccuracy.length
            }}</span>
            <span class="kpi-hint">
              班级内有作答记录且未删除的题目个数（服务端按题目聚合）
            </span>
          </div>
        </section>

        <div class="panel-grid">
          <section class="section-card panel">
            <div class="panel-head">
              <h2 class="oslab-section-title">薄弱知识点 Top 5</h2>
              <span class="panel-sub">
                按薄弱（掌握状态＝薄弱）人数排序，服务端返回前
                5；点击行查看节点详情
              </span>
            </div>
            <div v-if="dashboard.weakTop.length" class="node-list">
              <button
                v-for="node in dashboard.weakTop"
                :key="node.nodeName"
                type="button"
                class="node-row"
                :class="{ 'node-row-active': drillNode === node.nodeName }"
                @click="toggleDrill(node.nodeName)"
              >
                <span class="node-name">{{ node.nodeName }}</span>
                <span class="oslab-status-tag oslab-status-tag--weak">
                  薄弱 {{ node.weakCount }} 人
                </span>
                <span class="node-avg" :class="avgTone(node.avgPKnown)">
                  均 {{ percent(node.avgPKnown) }}
                </span>
              </button>
            </div>
            <div v-else class="oslab-empty">
              <span class="oslab-state-title">暂无薄弱点数据</span>
              <span class="oslab-state-hint">
                该班级暂无掌握记录，或暂无薄弱状态的知识点。
              </span>
            </div>
          </section>

          <section
            class="section-card panel"
            :class="{ 'panel-wide': heatView === 'graph' }"
          >
            <div class="panel-head">
              <h2 class="oslab-section-title">班级节点掌握热力</h2>
              <a-radio-group
                v-model:model-value="heatView"
                type="button"
                size="small"
                @change="onHeatViewChange"
              >
                <a-radio value="list">列表</a-radio>
                <a-radio value="graph">图谱</a-radio>
              </a-radio-group>
              <span class="panel-sub">
                {{
                  heatView === "graph"
                    ? "节点颜色为班级聚合掌握状态，点击节点查看班级摘要"
                    : "按平均掌握度升序；人数为有掌握记录的学生数；点击行查看节点详情"
                }}
              </span>
            </div>

            <!-- 图谱视图：课程拓扑 + 班级聚合四状态只读投影 -->
            <template v-if="heatView === 'graph'">
              <div
                v-if="courseGraphError && !courseGraph"
                class="oslab-error"
                role="alert"
              >
                <span class="oslab-state-title">课程图谱加载失败</span>
                <span class="oslab-state-hint">{{ courseGraphError }}</span>
                <a-button size="small" @click="loadCourseGraph">重试</a-button>
              </div>
              <div
                v-else-if="!courseGraph || courseGraphLoading"
                class="oslab-loading"
              >
                <span class="oslab-spinner" />
                <span class="oslab-state-hint">正在载入课程拓扑…</span>
              </div>
              <template v-else>
                <div class="heat-legend">
                  <span class="legend-item">
                    <i class="legend-swatch swatch-mastered" />已掌握
                    {{ heatStatusCounts.GREEN }}
                  </span>
                  <span class="legend-item">
                    <i class="legend-swatch swatch-review" />待巩固
                    {{ heatStatusCounts.YELLOW }}
                  </span>
                  <span class="legend-item">
                    <i class="legend-swatch swatch-weak" />薄弱
                    {{ heatStatusCounts.RED }}
                  </span>
                  <span class="legend-item">
                    <i class="legend-swatch swatch-unseen" />未学习
                    {{ heatStatusCounts.GRAY }}
                  </span>
                </div>
                <div
                  v-if="!dashboard.nodeHeat.length"
                  class="oslab-empty heat-empty"
                >
                  <span class="oslab-state-title">暂无学习证据</span>
                  <span class="oslab-state-hint">
                    该班级还没有任何掌握记录，全部节点按「未学习」显示；学生开始学习后这里才会出现班级聚合状态。
                  </span>
                </div>
                <KnowledgeGraph
                  :nodes="teacherGraph.nodes"
                  :links="teacherGraph.links"
                  :categories="teacherGraph.categories"
                  :selected-id="teacherSelectedId"
                  fill-by-status
                  :chart-height="graphHeight"
                  @node-click="onHeatNodeClick"
                />
                <p class="hint heat-note">
                  颜色映射（班级聚合，与列表阈值一致）：班级平均掌握度 ≥70%
                  已掌握、35%–69% 待巩固、&lt;35%
                  薄弱；班级内无掌握记录的节点为未学习。数据全部来自教师端班级聚合（nodeHeat），不包含任何单名学生的个人掌握度；关系结构为当前课程拓扑。图谱为只读视图，节点管理功能在「教学管理」中。
                </p>
              </template>
            </template>

            <!-- 列表视图（原样保留） -->
            <template v-else>
              <div
                v-if="dashboard.nodeHeat.length"
                class="node-list list-scroll"
              >
                <button
                  v-for="node in dashboard.nodeHeat"
                  :key="node.nodeName"
                  type="button"
                  class="node-row"
                  :class="{ 'node-row-active': drillNode === node.nodeName }"
                  @click="toggleDrill(node.nodeName)"
                >
                  <span class="node-name">{{ node.nodeName }}</span>
                  <span class="heat-bar">
                    <span
                      class="heat-bar-fill"
                      :class="fillTone(node.avgPKnown)"
                      :style="{ width: heatWidth(node.avgPKnown) }"
                    />
                  </span>
                  <span class="node-avg" :class="avgTone(node.avgPKnown)">
                    {{ percent(node.avgPKnown) }}
                  </span>
                  <span class="hint">{{ node.studentCount }} 人有证据</span>
                </button>
              </div>
              <div v-else class="oslab-empty">
                <span class="oslab-state-title">暂无学习证据</span>
                <span class="oslab-state-hint">
                  该班级还没有任何掌握记录，学生开始学习后此处才会出现数据。
                </span>
              </div>
            </template>
          </section>

          <section
            v-if="drillNode"
            class="section-card panel panel-drill"
            aria-label="节点下钻详情"
          >
            <div class="panel-head">
              <h2 class="oslab-section-title">节点下钻 · {{ drillNode }}</h2>
              <a-button size="mini" @click="closeDrill">关闭</a-button>
            </div>
            <div v-if="drillLoading && !drill" class="oslab-loading">
              <span class="oslab-spinner" />
            </div>
            <div v-else-if="drillError" class="oslab-error" role="alert">
              <span class="oslab-state-title">节点详情加载失败</span>
              <span class="oslab-state-hint">{{ drillError }}</span>
              <a-button size="small" @click="drillRetry">重试</a-button>
            </div>
            <div v-else-if="drill" class="drill-grid">
              <div class="drill-summary">
                <dl class="drill-facts">
                  <div class="fact">
                    <dt>学习覆盖率</dt>
                    <dd>{{ percent(drill.coverage) }}</dd>
                    <dd class="fact-note">有掌握记录学生 / 班级学生数</dd>
                  </div>
                  <div class="fact">
                    <dt>平均掌握度</dt>
                    <dd :class="avgTone(drill.avgPKnown)">
                      {{
                        drill.avgPKnown !== null
                          ? percent(drill.avgPKnown)
                          : "暂无记录"
                      }}
                    </dd>
                  </div>
                  <div class="fact">
                    <dt>薄弱学生</dt>
                    <dd class="fact-weak">{{ drill.weakCount }} 人</dd>
                    <dd class="fact-note">掌握状态为薄弱的人数</dd>
                  </div>
                  <div class="fact">
                    <dt>薄弱占比</dt>
                    <dd class="fact-weak">{{ weakRatioText }}</dd>
                    <dd class="fact-note">薄弱学生 / 班级学生数</dd>
                  </div>
                  <div class="fact">
                    <dt>班级人数</dt>
                    <dd>{{ drill.studentCount }}</dd>
                    <dd class="fact-note">students 表绑定本班级的账号数</dd>
                  </div>
                  <div class="fact">
                    <dt>首次测评正确率</dt>
                    <dd>
                      {{
                        drill.firstAccuracy !== null
                          ? percent(drill.firstAccuracy)
                          : "暂无样本"
                      }}
                    </dd>
                    <dd class="fact-note">分母为首次有效作答数</dd>
                  </div>
                  <div class="fact fact-wide">
                    <dt>实验有效通过率</dt>
                    <dd>
                      {{
                        drill.experimentPassRate !== null
                          ? percent(drill.experimentPassRate)
                          : "暂无有效提交"
                      }}
                    </dd>
                    <dd class="fact-note">
                      编程提交按提交 ID 去重并排除系统错误
                    </dd>
                  </div>
                </dl>
                <div class="fact-block">
                  <h3 class="block-title">常见误区</h3>
                  <p v-if="!drill.misconceptionStats.length" class="hint">
                    暂无误区记录（来自测评题的误区标注与班级错误作答）。
                  </p>
                  <ul v-else class="drill-list">
                    <li
                      v-for="m in drill.misconceptionStats"
                      :key="m.misconception"
                      class="drill-item"
                    >
                      <span class="item-title" :title="m.misconception">
                        {{ m.misconception }}
                      </span>
                      <span class="item-meta">
                        {{ m.wrongCount }} 次错误作答
                      </span>
                    </li>
                  </ul>
                </div>

                <div class="fact-block">
                  <h3 class="block-title">建议教学动作</h3>
                  <p v-if="!teachingSuggestions.length" class="hint">
                    暂无可给出的建议（数据不足）。
                  </p>
                  <ul v-else class="drill-list suggestion-list">
                    <li
                      v-for="(tip, index) in teachingSuggestions"
                      :key="index"
                      class="drill-item suggestion-item"
                    >
                      {{ tip }}
                    </li>
                  </ul>
                  <p class="hint">
                    建议由班级聚合数据（薄弱人数、覆盖率、正确率、误区、实验提交）规则推导，仅供教学参考。
                  </p>
                </div>
              </div>

              <div class="fact-block">
                <h3 class="block-title">
                  关联测评题（{{ drill.questions.length }}）
                </h3>
                <p class="block-note">
                  服务端按节点映射表精确关联，不按标题模糊匹配。
                </p>
                <p v-if="!drill.questions.length" class="hint">
                  该节点暂无关联测评题。
                </p>
                <ul v-else class="drill-list">
                  <li
                    v-for="q in drill.questions"
                    :key="q.questionId"
                    class="drill-item"
                  >
                    <span class="item-title" :title="q.title">
                      {{ q.title }}
                    </span>
                    <span class="item-meta">
                      <span class="type-tag">{{ typeText(q.type) }}</span>
                      <template v-if="q.firstCorrectRate !== null">
                        首次正确 {{ percent(q.firstCorrectRate) }}（{{
                          q.firstCorrectCount
                        }}/{{ q.firstAttemptCount }}）
                      </template>
                      <template v-else>暂无首次作答</template>
                      <span class="hint">共 {{ q.attemptCount }} 次作答</span>
                    </span>
                  </li>
                </ul>
              </div>

              <div class="fact-block">
                <h3 class="block-title">
                  关联编程实验（{{ drill.experiments.length }}）
                </h3>
                <p class="block-note">
                  服务端按知识标签精确关联；通过率按提交 ID 去重并排除系统错误。
                </p>
                <p v-if="!drill.experiments.length" class="hint">
                  该节点暂无关联编程实验。
                </p>
                <ul v-else class="drill-list">
                  <li
                    v-for="e in drill.experiments"
                    :key="e.questionId"
                    class="drill-item"
                  >
                    <span class="item-title" :title="e.title">
                      {{ e.title }}
                    </span>
                    <span class="item-meta">
                      <template v-if="e.passRate !== null">
                        通过率 {{ percent(e.passRate) }}（{{ e.passCount }}/{{
                          e.submitCount
                        }}）
                      </template>
                      <template v-else>暂无有效提交</template>
                    </span>
                  </li>
                </ul>
              </div>
            </div>
            <div v-if="drill" class="drill-actions">
              <a-button
                type="primary"
                long
                @click="openNodeWorkspace(drillNode)"
              >
                打开节点学习工作台
              </a-button>
              <p class="hint">
                工作台展示当前登录账号本人的学习视角预览，不代表班级学生的掌握数据。
              </p>
            </div>
          </section>
        </div>

        <div class="panel-grid">
          <section class="section-card panel">
            <div class="panel-head">
              <h2 class="oslab-section-title">题目正确率（低 → 高）</h2>
              <span class="panel-sub">
                正确率分母为全部作答次数（含重复作答），服务端按题目聚合
              </span>
            </div>
            <div
              v-if="dashboard.questionAccuracy.length"
              class="q-list list-scroll"
            >
              <div
                v-for="q in dashboard.questionAccuracy"
                :key="q.questionId"
                class="q-row"
              >
                <span class="q-title" :title="q.title">{{ q.title }}</span>
                <span class="q-meta">
                  <span class="type-tag">{{ typeText(q.type) }}</span>
                  <span class="q-rate" :class="avgTone(q.correctRate)">
                    {{ percent(q.correctRate) }}
                  </span>
                  <span class="hint">
                    首次答对 {{ q.firstCorrectCount }} 次 · 共
                    {{ q.attemptCount }} 次作答
                  </span>
                </span>
              </div>
            </div>
            <div v-else class="oslab-empty">
              <span class="oslab-state-title">暂无作答记录</span>
              <span class="oslab-state-hint">
                该班级还没有测评题作答数据。
              </span>
            </div>
          </section>

          <section class="section-card panel">
            <div class="panel-head">
              <h2 class="oslab-section-title">近期学习事件</h2>
              <span class="panel-sub">
                服务端返回最近 20 条；点击事件行查看原始证据
              </span>
            </div>
            <div v-if="dashboard.recentEvents.length" class="event-list">
              <div
                v-for="event in dashboard.recentEvents"
                :key="event.id"
                class="event-item"
              >
                <button
                  type="button"
                  class="event-row"
                  :aria-expanded="openEvidenceId === event.id"
                  @click="toggleEvidence(event.id)"
                >
                  <span class="cell">
                    <span class="cell-label">学生</span>
                    <span class="cell-value">
                      {{ event.anonymousName || "未知学生" }}
                    </span>
                  </span>
                  <span class="cell">
                    <span class="cell-label">节点</span>
                    <span class="cell-value">{{ event.nodeName }}</span>
                  </span>
                  <span class="cell">
                    <span class="cell-label">类型</span>
                    <span class="cell-value">
                      {{ eventTypeText(event.eventType) }}
                      <span v-if="event.isFirst" class="first-tag">首次</span>
                    </span>
                  </span>
                  <span class="cell">
                    <span class="cell-label">结果</span>
                    <span
                      class="cell-value oslab-status-tag"
                      :class="resultTone(event.result)"
                    >
                      {{ resultText(event.result) }}
                    </span>
                  </span>
                  <span class="cell">
                    <span class="cell-label">掌握度变化</span>
                    <span class="cell-value oslab-mono">
                      {{ masteryText(event) }}
                    </span>
                  </span>
                  <span class="cell">
                    <span class="cell-label">时间</span>
                    <span class="cell-value">{{
                      formatTime(event.createTime)
                    }}</span>
                  </span>
                </button>
                <div v-if="openEvidenceId === event.id" class="event-evidence">
                  <p class="cell-label">原始证据（学习事件 evidence 字段）</p>
                  <pre class="oslab-mono">{{
                    evidenceText(event.evidence)
                  }}</pre>
                </div>
              </div>
            </div>
            <div v-else class="oslab-empty">
              <span class="oslab-state-title">暂无学习事件</span>
              <span class="oslab-state-hint"> 该班级还没有学习事件数据。 </span>
            </div>
          </section>
        </div>

        <section class="section-card panel">
          <div class="panel-head">
            <h2 class="oslab-section-title">班级时间线</h2>
            <span class="panel-sub">
              仅本班级学生的学习事件（班级约束），点击事件行查看原始证据
            </span>
          </div>
          <div class="timeline-tools">
            <a-select
              v-model:model-value="timelineEventType"
              class="timeline-type-select"
              placeholder="全部事件类型"
              allow-clear
              @change="onTimelineFilterChange"
            >
              <a-option value="ATTEMPT">题目作答</a-option>
              <a-option value="JUDGE">实验判题</a-option>
              <a-option value="SIMULATION">模拟练习</a-option>
              <a-option value="STATE_BASELINE">状态基线</a-option>
            </a-select>
            <span v-if="!timelineError" class="hint">
              共 {{ timeline.total }} 条事件
            </span>
          </div>
          <div v-if="timelineLoading" class="oslab-loading">
            <span class="oslab-spinner" />
          </div>
          <div v-else-if="timelineError" class="oslab-error" role="alert">
            <span class="oslab-state-title">时间线加载失败</span>
            <span class="oslab-state-hint">{{ timelineError }}</span>
            <a-button size="small" @click="timelineRetry">重试</a-button>
          </div>
          <div v-else-if="!timeline.events.length" class="oslab-empty">
            <span class="oslab-state-title">暂无学习事件</span>
            <span class="oslab-state-hint">
              {{
                timelineEventType
                  ? "当前事件类型筛选没有匹配的事件。"
                  : "该班级还没有学习事件。"
              }}
            </span>
            <a-button
              v-if="timelineEventType"
              size="small"
              @click="clearTimelineFilter"
            >
              清除筛选
            </a-button>
          </div>
          <ol v-else class="timeline-list">
            <li
              v-for="event in timeline.events"
              :key="event.id"
              class="timeline-item"
            >
              <span
                class="timeline-dot"
                :class="dotTone(event.result)"
                aria-hidden="true"
              />
              <button
                type="button"
                class="event-row"
                :aria-expanded="openEvidenceId === event.id"
                @click="toggleEvidence(event.id)"
              >
                <span class="cell">
                  <span class="cell-label">学生</span>
                  <span class="cell-value">
                    {{ event.anonymousName || "未知学生" }}
                  </span>
                </span>
                <span class="cell">
                  <span class="cell-label">节点</span>
                  <span class="cell-value">{{ event.nodeName }}</span>
                </span>
                <span class="cell">
                  <span class="cell-label">类型</span>
                  <span class="cell-value">
                    {{ eventTypeText(event.eventType) }}
                    <span v-if="event.isFirst" class="first-tag">首次</span>
                  </span>
                </span>
                <span class="cell">
                  <span class="cell-label">结果</span>
                  <span
                    class="cell-value oslab-status-tag"
                    :class="resultTone(event.result)"
                  >
                    {{ resultText(event.result) }}
                  </span>
                </span>
                <span class="cell">
                  <span class="cell-label">掌握度变化</span>
                  <span class="cell-value oslab-mono">
                    {{ masteryText(event) }}
                  </span>
                </span>
                <span class="cell">
                  <span class="cell-label">时间</span>
                  <span class="cell-value">{{
                    formatTime(event.createTime)
                  }}</span>
                </span>
              </button>
              <div v-if="openEvidenceId === event.id" class="event-evidence">
                <p class="cell-label">原始证据（学习事件 evidence 字段）</p>
                <pre class="oslab-mono">{{ evidenceText(event.evidence) }}</pre>
              </div>
            </li>
          </ol>
          <div
            v-if="!timelineError && timeline.total > TIMELINE_PAGE_SIZE"
            class="timeline-foot"
          >
            <a-pagination
              :current="timelinePage"
              :page-size="TIMELINE_PAGE_SIZE"
              :total="timeline.total"
              size="mini"
              @change="onTimelinePageChange"
            />
          </div>
        </section>

        <section class="section-card panel">
          <div class="panel-head">
            <h2 class="oslab-section-title">章节资源完整度</h2>
            <span class="panel-sub">全课程口径，不随班级筛选变化</span>
          </div>
          <div v-if="coverageLoading" class="oslab-loading">
            <span class="oslab-spinner" />
          </div>
          <div v-else-if="coverageError" class="oslab-error" role="alert">
            <span class="oslab-state-title">章节完整度加载失败</span>
            <span class="oslab-state-hint">{{ coverageError }}</span>
            <a-button size="small" @click="loadChapterCoverage">重试</a-button>
          </div>
          <div v-else-if="!coverage.length" class="oslab-empty">
            <span class="oslab-state-title">暂无章节数据</span>
            <span class="oslab-state-hint"
              >知识图谱暂无节点，无法统计章节完整度。</span
            >
          </div>
          <template v-else>
            <div class="coverage-table">
              <div class="coverage-head">
                <span>章节</span>
                <span>知识节点数</span>
                <span>已发布素材</span>
                <span>测评覆盖</span>
                <span>实验覆盖</span>
                <span>未关联资源节点</span>
              </div>
              <div
                v-for="record in coverage"
                :key="record.chapter"
                class="coverage-row"
              >
                <span class="cell-value chapter-name">
                  <span class="cell-label">章节</span>
                  {{ record.chapter }}
                </span>
                <span class="coverage-num">
                  <span class="cell-label">知识节点数</span>
                  {{ record.nodeCount }}
                </span>
                <span class="coverage-num">
                  <span class="cell-label">已发布素材</span>
                  {{ record.publishedContentCount }}
                </span>
                <span class="coverage-num">
                  <span class="cell-label">测评覆盖</span>
                  {{ record.assessmentCoveredCount }}
                </span>
                <span class="coverage-num">
                  <span class="cell-label">实验覆盖</span>
                  {{ record.experimentCoveredCount }}
                </span>
                <span class="coverage-nodes">
                  <span class="cell-label">未关联资源节点</span>
                  {{ uncoveredText(record) }}
                </span>
              </div>
            </div>
            <p class="hint coverage-note">
              口径：已发布素材＝节点已发布共享学习素材；测评覆盖＝已发布测评题经节点映射表关联；实验覆盖＝节点名出现在实验题知识标签；未关联＝三者皆无的节点（最多列出
              20 个）。
            </p>
          </template>
        </section>
      </template>
    </template>

    <div v-else class="oslab-loading">
      <span class="oslab-spinner" />
      <span class="oslab-state-hint">正在加载班级列表…</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  teacherChapterCoverage,
  teacherClasses,
  teacherDashboard,
  teacherNodeDetail,
  teacherTimeline,
  type ChapterCoverageVO,
  type ClassOptionVO,
  type LearningEventVO,
  type MasteryStatus,
  type NodeDrillVO,
  type NodeHeatVO,
  type TeacherDashboardVO,
  type TimelineVO,
} from "@/api/learning";
import KnowledgeGraph from "@/components/KnowledgeGraph.vue";
import { KnowledgeGraphControllerService } from "../../../generated";
import type { GraphDataDTO } from "../../../generated";

const TIMELINE_PAGE_SIZE = 50;

const router = useRouter();
const route = useRoute();

// ---------- 班级列表 ----------
const classesLoading = ref(false);
const classesError = ref<string | null>(null);
const classOptions = ref<ClassOptionVO[]>([]);
const className = ref("");

// ---------- 班级大屏（按 className 缓存，切换班级不残留旧内容） ----------
const dashboardCache = new Map<string, TeacherDashboardVO>();
let dashboardSeq = 0;
const dashboard = ref<TeacherDashboardVO | null>(null);
const dashboardLoading = ref(false);
const dashboardError = ref<string | null>(null);

// ---------- 时间线（按 班级+类型+页码 缓存） ----------
const timelineCache = new Map<string, TimelineVO>();
let timelineSeq = 0;
const timeline = ref<TimelineVO>({ total: 0, events: [] });
const timelineLoading = ref(false);
const timelineError = ref<string | null>(null);
const timelineEventType = ref<string | undefined>(undefined);
const timelinePage = ref(1);

// ---------- 章节资源完整度（全课程口径，与班级无关） ----------
const coverage = ref<ChapterCoverageVO[]>([]);
const coverageLoading = ref(false);
const coverageError = ref<string | null>(null);
let coverageSeq = 0;

// ---------- 节点下钻 ----------
const drillNode = ref("");
const drill = ref<NodeDrillVO | null>(null);
const drillLoading = ref(false);
const drillError = ref<string | null>(null);
let drillSeq = 0;

// ---------- 班级热力图谱（列表/图谱切换；只读投影） ----------
const heatView = ref<"list" | "graph">("list");
const courseGraph = ref<GraphDataDTO | null>(null);
const courseGraphLoading = ref(false);
const courseGraphError = ref<string | null>(null);
let courseGraphSeq = 0;

const viewportWidth = ref(window.innerWidth);
const onViewportResize = () => {
  viewportWidth.value = window.innerWidth;
};
window.addEventListener("resize", onViewportResize);
onBeforeUnmount(() => {
  window.removeEventListener("resize", onViewportResize);
});
const graphHeight = computed(() =>
  viewportWidth.value < 768 ? "300px" : "520px"
);

const loadCourseGraph = async () => {
  const seq = ++courseGraphSeq;
  courseGraphLoading.value = true;
  courseGraphError.value = null;
  try {
    // 公开课程拓扑快照（无个人数据）；班级聚合着色来自 nodeHeat
    const res =
      await KnowledgeGraphControllerService.getAllKnowledgeGraphDataUsingGet();
    if (seq !== courseGraphSeq) return;
    if (res.code === 0 && res.data) {
      courseGraph.value = res.data;
    } else {
      courseGraphError.value = res.message || "课程图谱加载失败";
    }
  } catch {
    if (seq !== courseGraphSeq) return;
    courseGraphError.value = "课程图谱加载失败，请检查网络后重试";
  } finally {
    if (seq === courseGraphSeq) courseGraphLoading.value = false;
  }
};

const onHeatViewChange = (value: unknown) => {
  if (value === "graph" && !courseGraph.value && !courseGraphLoading.value) {
    loadCourseGraph();
  }
};

/** nodeHeat 聚合数据按节点名索引 */
const heatMap = computed(() => {
  const map = new Map<string, NodeHeatVO>();
  for (const node of dashboard.value?.nodeHeat ?? []) {
    map.set(node.nodeName, node);
  }
  return map;
});

/**
 * 班级聚合四状态映射（阈值与列表 fillTone/avgTone 完全一致）：
 * 不在 nodeHeat（班级无掌握记录）→ GRAY 未学习；
 * avgPKnown < 0.35 → RED 薄弱；0.35 ≤ avgPKnown < 0.7 → YELLOW 待巩固；
 * avgPKnown ≥ 0.7 → GREEN 已掌握。绝不混入单名学生个人掌握度。
 */
const heatStatusOf = (nodeName: string): MasteryStatus => {
  const heat = heatMap.value.get(nodeName);
  if (!heat || heat.avgPKnown === null || heat.avgPKnown === undefined) {
    return "GRAY";
  }
  if (heat.avgPKnown < 0.35) return "RED";
  if (heat.avgPKnown < 0.7) return "YELLOW";
  return "GREEN";
};

const teacherGraph = computed(() => {
  const graph = courseGraph.value;
  if (!graph?.nodes) {
    return { nodes: [], links: [], categories: [] };
  }
  return {
    nodes: graph.nodes
      .filter((node) => !!node.name)
      .map((node) => ({
        id: node.id || node.name || "",
        name: node.name || "",
        category: node.category || "",
        chapter: node.chapter || "",
        description: node.description || "",
        status: heatStatusOf(node.name || ""),
      })),
    links: graph.links ?? [],
    categories: graph.categories ?? [],
  };
});

/** 图例计数：与图谱节点实际着色同源（nodeHeat 聚合映射结果） */
const heatStatusCounts = computed(() => {
  const counts: Record<MasteryStatus, number> = {
    GREEN: 0,
    YELLOW: 0,
    RED: 0,
    GRAY: 0,
  };
  for (const node of teacherGraph.value.nodes) {
    counts[(node.status || "GRAY") as MasteryStatus] += 1;
  }
  return counts;
});

/** 下钻节点在图谱中的选中高亮（按映射后的节点 id） */
const teacherSelectedId = computed(() => {
  if (!drillNode.value) return "";
  const node = teacherGraph.value.nodes.find(
    (item) => item.name === drillNode.value
  );
  return node ? node.id : "";
});

const onHeatNodeClick = (data: { name?: string }) => {
  if (data?.name) toggleDrill(data.name);
};

// ---------- 节点下钻：班级摘要补充（薄弱占比 + 建议教学动作） ----------
const weakRatioText = computed(() => {
  const detail = drill.value;
  if (!detail || !detail.studentCount) return "—";
  return percent(detail.weakCount / detail.studentCount);
});

/** 建议教学动作：由班级聚合真实字段规则推导，不引入演示数据 */
const teachingSuggestions = computed(() => {
  const detail = drill.value;
  if (!detail) return [] as string[];
  const tips: string[] = [];
  if (detail.weakCount > 0) {
    tips.push(
      `${detail.weakCount} 名学生处于薄弱状态，建议优先安排针对性讲解与补救练习。`
    );
  }
  if (detail.coverage < 1) {
    tips.push(
      `学习覆盖率 ${percent(
        detail.coverage
      )}，尚有学生未产生学习记录，可布置入门任务。`
    );
  }
  if (detail.firstAccuracy !== null && detail.firstAccuracy < 0.5) {
    tips.push(
      `首次测评正确率 ${percent(
        detail.firstAccuracy
      )}，建议先巩固概念再进入实验。`
    );
  }
  if (detail.misconceptionStats.length) {
    tips.push(
      `结合高频误区「${detail.misconceptionStats[0].misconception}」设计课堂提问或判断题。`
    );
  }
  if (detail.experiments.length && detail.experimentPassRate === null) {
    tips.push("关联编程实验尚无有效提交，建议课上演示提交流程。");
  }
  if (!tips.length) {
    tips.push("班级整体掌握良好，可作为后续知识点的检查点复用。");
  }
  return tips;
});

// ---------- 证据展开（同一时刻展开一条） ----------
const openEvidenceId = ref<number | null>(null);

// ========== 数据加载 ==========

const loadClasses = async () => {
  classesLoading.value = true;
  classesError.value = null;
  const prev = className.value;
  try {
    const res = await teacherClasses();
    if (res.data?.code === 0) {
      classOptions.value = res.data.data || [];
      // 默认班级：query 指定 > 第一个真实班级（不硬编码）
      const fromQuery = Array.isArray(route.query.className)
        ? route.query.className[0]
        : route.query.className;
      if (
        fromQuery &&
        classOptions.value.some((c) => c.className === fromQuery)
      ) {
        className.value = fromQuery;
      } else if (!className.value && classOptions.value.length) {
        className.value = classOptions.value[0].className;
      }
      // 章节完整度为全课程口径，不依赖班级选择
      loadChapterCoverage();
      // watch 只在班级变化时触发；重试等场景班级未变，这里手动加载
      if (className.value && className.value === prev) {
        loadDashboard();
        loadTimeline();
      }
    } else {
      classesError.value = res.data?.message || "班级列表加载失败";
    }
  } catch {
    // 网络异常：进入错误态，由用户重试
    classesError.value = "班级列表加载失败，请检查网络后重试";
  } finally {
    classesLoading.value = false;
  }
};

const loadDashboard = async () => {
  const target = className.value;
  if (!target) return;
  const seq = ++dashboardSeq;
  dashboardError.value = null;
  // 先渲染本班级缓存（命中则立即呈现），旧班级内容不残留
  dashboard.value = dashboardCache.get(target) || null;
  dashboardLoading.value = true;
  try {
    const res = await teacherDashboard(target);
    // 请求守卫：仅当仍是当前班级且请求未被更新的替换时写回
    if (seq !== dashboardSeq || className.value !== target) return;
    if (res.data?.code === 0 && res.data.data) {
      dashboardCache.set(target, res.data.data);
      dashboard.value = res.data.data;
      loadTimeline();
    } else {
      dashboardError.value = res.data?.message || "班级数据加载失败";
    }
  } catch {
    // 网络异常：保留缓存数据并显示刷新失败提示
    if (seq !== dashboardSeq || className.value !== target) return;
    dashboardError.value = "班级数据加载失败，请检查网络后重试";
  } finally {
    if (seq === dashboardSeq) dashboardLoading.value = false;
  }
};

const timelineCacheKey = (target: string) =>
  `${target}|${timelineEventType.value || "ALL"}|${timelinePage.value}`;

const loadTimeline = async () => {
  const target = className.value;
  if (!target) return;
  const seq = ++timelineSeq;
  const key = timelineCacheKey(target);
  timeline.value = timelineCache.get(key) || { total: 0, events: [] };
  timelineError.value = null;
  timelineLoading.value = true;
  try {
    const res = await teacherTimeline({
      className: target,
      eventType: timelineEventType.value,
      current: timelinePage.value,
      pageSize: TIMELINE_PAGE_SIZE,
    });
    if (seq !== timelineSeq || className.value !== target) return;
    if (res.data?.code === 0 && res.data.data) {
      timelineCache.set(key, res.data.data);
      timeline.value = res.data.data;
    } else {
      timelineError.value = res.data?.message || "时间线加载失败";
    }
  } catch {
    // 网络异常：进入错误态
    if (seq !== timelineSeq || className.value !== target) return;
    timelineError.value = "时间线加载失败，请检查网络后重试";
  } finally {
    if (seq === timelineSeq) timelineLoading.value = false;
  }
};

const loadChapterCoverage = async () => {
  const seq = ++coverageSeq;
  coverageError.value = null;
  coverageLoading.value = true;
  try {
    const res = await teacherChapterCoverage();
    if (seq !== coverageSeq) return;
    if (res.data?.code === 0) {
      coverage.value = res.data.data || [];
    } else {
      coverageError.value = res.data?.message || "章节完整度加载失败";
    }
  } catch {
    // 网络异常：进入错误态
    if (seq !== coverageSeq) return;
    coverageError.value = "章节完整度加载失败，请检查网络后重试";
  } finally {
    if (seq === coverageSeq) coverageLoading.value = false;
  }
};

const loadDrill = async (nodeName: string) => {
  const target = className.value;
  if (!target) return;
  const seq = ++drillSeq;
  drill.value = null;
  drillError.value = null;
  drillLoading.value = true;
  try {
    const res = await teacherNodeDetail(target, nodeName);
    // 请求守卫：班级、节点任一变化都丢弃旧响应
    if (
      seq !== drillSeq ||
      className.value !== target ||
      drillNode.value !== nodeName
    ) {
      return;
    }
    if (res.data?.code === 0 && res.data.data) {
      drill.value = res.data.data;
    } else {
      drillError.value = res.data?.message || "节点详情加载失败";
    }
  } catch {
    // 网络异常：进入错误态
    if (
      seq !== drillSeq ||
      className.value !== target ||
      drillNode.value !== nodeName
    ) {
      return;
    }
    drillError.value = "节点详情加载失败，请检查网络后重试";
  } finally {
    if (seq === drillSeq) drillLoading.value = false;
  }
};

// ========== 交互 ==========

const syncClassQuery = (value: string) => {
  router
    .replace({ query: { ...route.query, className: value } })
    .catch(() => undefined);
};

watch(className, (next, prev) => {
  if (!next || next === prev) return;
  // 切换班级：清空下钻与证据展开，时间线回到第一页并重置筛选
  drillSeq++;
  drillNode.value = "";
  drill.value = null;
  drillError.value = null;
  drillLoading.value = false;
  openEvidenceId.value = null;
  timelinePage.value = 1;
  timelineEventType.value = undefined;
  syncClassQuery(next);
  loadDashboard();
});

const refreshAll = () => {
  loadDashboard();
  loadTimeline();
  loadChapterCoverage();
  // 图谱视图打开且课程拓扑尚未加载/加载失败时一并重试
  if (heatView.value === "graph" && !courseGraph.value) {
    loadCourseGraph();
  }
};

const toggleDrill = (nodeName: string) => {
  if (drillNode.value === nodeName) {
    closeDrill();
    return;
  }
  drillNode.value = nodeName;
  loadDrill(nodeName);
};

const closeDrill = () => {
  drillSeq++;
  drillNode.value = "";
  drill.value = null;
  drillError.value = null;
  drillLoading.value = false;
};

const drillRetry = () => {
  if (drillNode.value) loadDrill(drillNode.value);
};

const timelineRetry = () => {
  loadTimeline();
};

const onTimelineFilterChange = () => {
  timelinePage.value = 1;
  loadTimeline();
};

const onTimelinePageChange = (page: number) => {
  timelinePage.value = page;
  loadTimeline();
};

const clearTimelineFilter = () => {
  timelineEventType.value = undefined;
  timelinePage.value = 1;
  loadTimeline();
};

const toggleEvidence = (id: number) => {
  openEvidenceId.value = openEvidenceId.value === id ? null : id;
};

const openNodeWorkspace = (nodeName: string) => {
  router.push({
    path: `/knowledge/node/${encodeURIComponent(nodeName)}`,
  });
};

// ========== 展示工具（口径文案与状态映射） ==========

const percent = (value: number | null | undefined) =>
  value === null || value === undefined ? "—" : `${Math.round(value * 100)}%`;

const heatWidth = (value: number) => `${Math.round(value * 100)}%`;

/** 平均掌握度文本色：颜色不是唯一信号，展示值始终带百分比文字 */
const avgTone = (value: number | null | undefined) => {
  if (value === null || value === undefined) return "";
  if (value < 0.35) return "tone-weak";
  if (value < 0.7) return "tone-review";
  return "tone-mastered";
};

const fillTone = (value: number) => {
  if (value < 0.35) return "fill-weak";
  if (value < 0.7) return "fill-review";
  return "fill-mastered";
};

const resultTone = (result: string) => {
  if (result === "PASS") return "oslab-status-tag--mastered";
  if (result === "FAIL") return "oslab-status-tag--weak";
  return "oslab-status-tag--unseen";
};

const dotTone = (result: string) => {
  if (result === "PASS") return "dot-mastered";
  if (result === "FAIL") return "dot-weak";
  return "dot-unseen";
};

const resultText = (result: string) => {
  const map: Record<string, string> = {
    PASS: "通过",
    FAIL: "未通过",
    SYSTEM_ERROR: "系统错误",
    BASELINE: "基线",
  };
  return map[result] || result;
};

const eventTypeText = (type: string) => {
  const map: Record<string, string> = {
    ATTEMPT: "题目作答",
    JUDGE: "实验判题",
    SIMULATION: "模拟练习",
    STATE_BASELINE: "状态基线",
  };
  return map[type] || type;
};

const typeText = (type?: string) => {
  const map: Record<string, string> = {
    JUDGE: "判断",
    SINGLE: "单选",
    MULTIPLE: "多选",
    FILL_BLANK: "填空",
  };
  return map[type || ""] || type;
};

const masteryText = (event: LearningEventVO) => {
  const before = event.masteryBefore;
  const after = event.masteryAfter;
  if (
    before !== null &&
    before !== undefined &&
    after !== null &&
    after !== undefined
  ) {
    return `${percent(before)} → ${percent(after)}`;
  }
  if (after !== null && after !== undefined) {
    return `→ ${percent(after)}`;
  }
  return "—";
};

/** 展示原始证据：JSON 串格式化，非 JSON 原样展示，空值如实说明 */
const evidenceText = (evidence: string | null) => {
  if (!evidence) return "（该事件没有证据描述）";
  try {
    return JSON.stringify(JSON.parse(evidence), null, 2);
  } catch {
    // 非 JSON 字符串按原文展示
    return evidence;
  }
};

/** 未关联资源节点文案：数量 + 服务端下发的节点名（最多 20 个，超出补「等」） */
const uncoveredText = (record: ChapterCoverageVO) => {
  if (!record.uncoveredCount) return "无";
  const names = record.uncoveredNodes.join("、");
  const more =
    record.uncoveredCount > record.uncoveredNodes.length ? " 等" : "";
  return `${record.uncoveredCount} 个${names ? `：${names}` : ""}${more}`;
};

const formatTime = (time: string) =>
  time ? new Date(time).toLocaleString() : "—";

loadClasses();
</script>

<style scoped>
.teacher-dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
  color: var(--oslab-ink);
}

/* ---------- 页面头 ---------- */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-3) var(--oslab-space-4);
}

.page-head-text {
  min-width: 0;
}

.page-desc {
  margin: var(--oslab-space-1) 0 0;
  max-width: 640px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.6;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
}

.class-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.class-select {
  width: 220px;
}

/* ---------- KPI ---------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--oslab-space-4);
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--oslab-space-3) var(--oslab-space-4);
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
}

.kpi-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.kpi-value {
  color: var(--oslab-ink);
  font-size: var(--oslab-text-page);
  font-weight: 600;
  line-height: 1.25;
}

.kpi-unit {
  margin-left: 2px;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  font-weight: 500;
}

.kpi-value-empty {
  color: var(--oslab-muted);
  font-size: var(--oslab-text-section);
  font-weight: 500;
}

.kpi-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.5;
}

/* ---------- 面板 ---------- */
.panel {
  padding: var(--oslab-space-4);
  min-width: 0;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--oslab-space-4);
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--oslab-space-1) var(--oslab-space-3);
  margin-bottom: var(--oslab-space-3);
}

.panel-sub {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.stale-banner {
  padding: var(--oslab-space-2) var(--oslab-space-3);
  background: var(--oslab-review-soft);
  border: 1px solid var(--oslab-review);
  border-radius: var(--oslab-radius-md);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}

/* ---------- 节点列表（薄弱点 / 热力） ---------- */
.node-list {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-1);
}

.list-scroll {
  max-height: 384px;
  overflow-y: auto;
}

.node-row {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  width: 100%;
  min-width: 0;
  padding: 6px 8px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--oslab-radius-md);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease),
    border-color var(--oslab-duration-fast) var(--oslab-ease);
}

.node-row:hover {
  background: var(--oslab-panel-soft);
}

.node-row-active {
  background: var(--oslab-primary-soft);
  border-color: var(--oslab-primary);
}

.node-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--oslab-ink-soft);
}

.node-row .oslab-status-tag {
  flex-shrink: 0;
}

.node-avg {
  flex-shrink: 0;
  color: var(--oslab-ink-soft);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.tone-mastered {
  color: var(--oslab-mastered-text);
}

.tone-review {
  color: var(--oslab-review-text);
}

.tone-weak {
  color: var(--oslab-weak-text);
}

.heat-bar {
  flex-shrink: 0;
  width: 72px;
  height: 6px;
  overflow: hidden;
  background: var(--oslab-line-soft);
  border-radius: var(--oslab-radius-pill);
}

.heat-bar-fill {
  display: block;
  height: 100%;
  border-radius: var(--oslab-radius-pill);
}

.fill-mastered {
  background: var(--oslab-mastered);
}

.fill-review {
  background: var(--oslab-review);
}

.fill-weak {
  background: var(--oslab-weak);
}

/* ---------- 班级热力图谱（只读投影） ---------- */
.panel-wide {
  grid-column: 1 / -1;
}

.heat-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
  margin-bottom: var(--oslab-space-2);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.legend-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid var(--oslab-line);
}

/* 图例与节点实际颜色一致：浅底（fillByStatus 底色）+ 强色描边（状态条/圆点色） */
.swatch-mastered {
  background: var(--oslab-mastered-soft);
  border-color: var(--oslab-mastered-text);
}

.swatch-review {
  background: var(--oslab-review-soft);
  border-color: var(--oslab-review-text);
}

.swatch-weak {
  background: var(--oslab-weak-soft);
  border-color: var(--oslab-weak-text);
}

.swatch-unseen {
  background: var(--oslab-unseen-soft);
  border-color: var(--oslab-unseen-text);
}

.heat-empty {
  padding: var(--oslab-space-6) var(--oslab-space-4);
}

.heat-note {
  margin: var(--oslab-space-2) 0 0;
}

.suggestion-list {
  max-height: none;
  overflow: visible;
}

.suggestion-item {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  line-height: 1.6;
}

/* ---------- 题目正确率 ---------- */
.q-list {
  display: flex;
  flex-direction: column;
}

.q-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--oslab-space-3);
  padding: 6px 8px;
  border-radius: var(--oslab-radius-md);
}

.q-row:hover {
  background: var(--oslab-panel-soft);
}

.q-row + .q-row {
  border-top: 1px solid var(--oslab-line-soft);
}

.q-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--oslab-ink-soft);
}

.q-meta {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--oslab-space-2);
}

.q-rate {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.type-tag {
  padding: 0 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 18px;
  white-space: nowrap;
}

.first-tag {
  margin-left: 2px;
  padding: 0 6px;
  background: var(--oslab-primary-soft);
  border-radius: var(--oslab-radius-pill);
  color: var(--oslab-primary);
  font-size: var(--oslab-text-small);
  line-height: 18px;
  white-space: nowrap;
}

/* ---------- 事件行（近期事件 / 时间线） ---------- */
.event-list {
  display: flex;
  flex-direction: column;
}

.event-item + .event-item {
  border-top: 1px solid var(--oslab-line-soft);
}

.event-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1.3fr) 96px 84px 120px minmax(0, 1fr);
  gap: var(--oslab-space-2);
  align-items: center;
  width: 100%;
  padding: 6px 8px;
  background: transparent;
  border: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease);
}

.event-row:hover {
  background: var(--oslab-panel-soft);
}

.cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--oslab-ink-soft);
}

.cell-label {
  display: none;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.event-evidence {
  margin: 0 8px var(--oslab-space-2);
  padding: var(--oslab-space-2) var(--oslab-space-3);
  background: var(--oslab-panel-soft);
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
}

.event-evidence .cell-label {
  display: block;
  margin-bottom: var(--oslab-space-1);
}

.event-evidence pre {
  max-height: 220px;
  margin: 0;
  overflow-y: auto;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  white-space: pre-wrap;
  word-break: break-all;
}

/* ---------- 时间线 ---------- */
.timeline-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oslab-space-3);
  margin-bottom: var(--oslab-space-3);
}

.timeline-type-select {
  width: 160px;
}

.timeline-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline-item {
  position: relative;
  padding: 0 0 var(--oslab-space-3) var(--oslab-space-6);
}

.timeline-item::before {
  content: "";
  position: absolute;
  top: 12px;
  bottom: -2px;
  left: 5px;
  width: 1px;
  background: var(--oslab-line);
}

.timeline-item:last-child::before {
  display: none;
}

.timeline-dot {
  position: absolute;
  top: 9px;
  left: 0;
  width: 11px;
  height: 11px;
  border: 2px solid var(--oslab-panel);
  border-radius: 50%;
}

.dot-mastered {
  background: var(--oslab-mastered);
}

.dot-review {
  background: var(--oslab-review);
}

.dot-weak {
  background: var(--oslab-weak);
}

.dot-unseen {
  background: var(--oslab-unseen);
}

.timeline-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--oslab-space-2);
}

/* ---------- 节点下钻 ---------- */
.panel-drill {
  grid-column: 1 / -1;
}

.drill-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(0, 1.4fr) minmax(0, 1.2fr);
  gap: var(--oslab-space-6);
}

.drill-facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--oslab-space-3) var(--oslab-space-4);
  margin: 0 0 var(--oslab-space-4);
}

.fact dt {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.fact dd {
  margin: 2px 0 0;
  color: var(--oslab-ink);
  font-size: var(--oslab-text-section);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.fact dd.fact-note {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  font-weight: 400;
}

.fact-weak {
  color: var(--oslab-weak-text);
}

.fact-wide {
  grid-column: 1 / -1;
}

.fact-block {
  min-width: 0;
}

.block-title {
  margin: 0;
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 600;
}

.block-note {
  margin: var(--oslab-space-1) 0 var(--oslab-space-2);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.drill-list {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-1);
  max-height: 320px;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
}

.drill-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
}

.item-title {
  overflow: hidden;
  color: var(--oslab-ink-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oslab-space-2);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.drill-actions {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-1);
  max-width: 320px;
  margin-top: var(--oslab-space-4);
}

.drill-actions p {
  margin: 0;
}

/* ---------- 章节资源完整度 ---------- */
.coverage-table {
  display: flex;
  flex-direction: column;
}

.coverage-head,
.coverage-row {
  display: grid;
  grid-template-columns: minmax(110px, 1.1fr) 88px 96px 88px 88px minmax(
      200px,
      1.8fr
    );
  gap: var(--oslab-space-2);
  align-items: center;
  padding: 6px 10px;
}

.coverage-head {
  background: var(--oslab-panel-soft);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}

.coverage-row {
  color: var(--oslab-ink-soft);
}

.coverage-row + .coverage-row {
  border-top: 1px solid var(--oslab-line-soft);
}

.coverage-num {
  font-variant-numeric: tabular-nums;
}

.chapter-name {
  color: var(--oslab-ink);
  font-weight: 500;
}

.coverage-nodes {
  overflow: hidden;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coverage-note {
  margin: var(--oslab-space-2) 0 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }

  .drill-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-head {
    align-items: stretch;
    flex-direction: column;
  }

  .page-actions {
    flex-wrap: wrap;
  }

  .class-select {
    flex: 1;
    min-width: 0;
    width: auto;
  }

  /* 事件行卡片化 */
  .event-row {
    grid-template-columns: 1fr 1fr;
    gap: var(--oslab-space-1) var(--oslab-space-3);
  }

  .cell-label {
    display: block;
  }

  .cell-value {
    white-space: normal;
  }

  /* 章节完整度卡片化 */
  .coverage-head {
    display: none;
  }

  .coverage-row {
    grid-template-columns: 1fr 1fr;
    gap: var(--oslab-space-1) var(--oslab-space-3);
    margin-bottom: var(--oslab-space-2);
    border: 1px solid var(--oslab-line-soft);
    border-radius: var(--oslab-radius-md);
  }

  .coverage-row + .coverage-row {
    border-top: 1px solid var(--oslab-line-soft);
  }

  .coverage-row .cell-label {
    display: block;
  }

  .coverage-nodes {
    grid-column: 1 / -1;
    white-space: normal;
  }
}

@media (max-width: 480px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .drill-facts {
    grid-template-columns: 1fr;
  }
}
</style>
