<template>
  <div class="graph-page">
    <!-- 学生左侧：教材章/节/单元目录，选择驱动中央图谱范围。
         教师维护复用同一页面，保留原章节摘要与管理操作。 -->
    <CourseCatalogSidebar
      v-if="!isTeaching"
      :course-code="courseCode"
      :course-title="courseTitle"
      :sections="catalogSections"
      :courses="catalogCourses"
      :outlines="catalogOutlines"
      :selected-section-code="selectedSectionCode"
      :selected-unit-code="selectedUnitCode"
      :expanded-sections="expandedSections"
      :loading="catalogLoading"
      :error="catalogError"
      :progress="catalogProgress"
      @select-course="handleCatalogCourse"
      @select-section="handleCatalogSection"
      @select-unit="handleCatalogUnit"
      @toggle-section="toggleSection"
      @retry="loadCatalog"
    />
    <aside v-else class="course-side">
      <div class="course-card">
        <div class="course-logo">OS</div>
        <div class="course-meta">
          <div class="course-name">操作系统</div>
          <div class="course-term">课程知识图谱</div>
        </div>
      </div>

      <div class="side-divider"></div>

      <div class="side-section">
        <div class="side-title">章节学习覆盖</div>
        <template v-if="focusChapter">
          <div class="chapter-focus">
            <div class="chapter-focus-name">{{ focusChapter.name }}</div>
            <div class="chapter-focus-sub">
              <span>有学习记录的节点</span>
              <span class="chapter-focus-pct"
                >{{ focusChapter.progress }}%</span
              >
            </div>
            <a-progress
              :percent="focusChapter.progress / 100"
              :show-text="false"
              stroke-color="var(--oslab-primary)"
              size="small"
            />
            <div class="chapter-focus-note">
              已掌握 {{ focusChapter.mastered }}/{{ focusChapter.total }} ·
              已测均值 {{ focusChapter.avgText }}
            </div>
          </div>
          <div v-for="ch in otherChapters" :key="ch.name" class="chapter-line">
            <span>{{ ch.name }}</span>
            <span class="chapter-line-pct">{{ ch.progress }}%</span>
          </div>
        </template>
        <div v-else class="side-hint">暂无章节数据</div>
      </div>

      <div class="side-section">
        <div class="side-title">学习状态</div>
        <div class="status-count">
          <i class="dot dot-mastered"></i>已掌握
          <b class="count">{{ statusCounts.GREEN }}</b>
        </div>
        <div class="status-count">
          <i class="dot dot-review"></i>待巩固
          <b class="count">{{ statusCounts.YELLOW }}</b>
        </div>
        <div class="status-count">
          <i class="dot dot-weak"></i>薄弱
          <b class="count">{{ statusCounts.RED }}</b>
        </div>
        <div class="status-count">
          <i class="dot dot-unseen"></i>未学习
          <b class="count">{{ statusCounts.GRAY }}</b>
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <div class="graph-main">
      <div class="workspace-header">
        <div>
          <h2 v-if="isTeaching" class="page-title">知识维护</h2>
          <p class="page-subtitle">
            {{
              isTeaching
                ? "维护课程知识结构、节点资料与学习资源映射。"
                : "从左栏选择章、节或学习单元；图谱与右侧详情会跟随目录范围。"
            }}
          </p>
        </div>
        <div class="header-meta">
          <template v-if="isTeaching && focusChapterName">
            当前章节：{{ focusChapterName }}
          </template>
          <template v-else-if="!isTeaching">
            当前范围：{{ scopeLabel }}
          </template>
        </div>
      </div>

      <div class="content-row">
        <!-- 中央图谱卡片 -->
        <div class="graph-col">
          <div class="graph-card">
            <div class="graph-card-head">
              <div class="graph-card-heading">
                <span class="kicker">KNOWLEDGE GRAPH</span>
                <h2 class="card-title">知识图谱</h2>
              </div>
              <div class="graph-card-tools">
                <label class="label-toggle">
                  <span>关系标签</span>
                  <a-switch v-model="showRelationLabels" size="small" />
                </label>
                <a-tooltip
                  v-if="!isTeaching"
                  content="只保留当前选中节点的补救路径与一跳邻接"
                >
                  <a-button
                    size="mini"
                    :type="pathFocus ? 'primary' : 'outline'"
                    :aria-pressed="pathFocus"
                    @click="pathFocus = !pathFocus"
                  >
                    只看学习路径
                  </a-button>
                </a-tooltip>
                <a-tooltip content="复位视野：图谱居中并恢复默认缩放">
                  <a-button size="mini" @click="resetViewCanvas">
                    重置视野
                  </a-button>
                </a-tooltip>
              </div>
            </div>

            <div class="graph-scope">
              <a-input-search
                v-model="searchText"
                aria-label="搜索范围内的知识点"
                :input-attrs="{ 'aria-label': '搜索范围内的知识点' }"
                placeholder="搜索范围内的知识点"
                allow-clear
                class="graph-search"
                style="width: 260px"
                @search="searchFocus"
                @clear="searchText = ''"
              />
              <a-select
                v-model="statusFilter"
                aria-label="按掌握状态筛选"
                placeholder="全部状态"
                allow-clear
                class="graph-status"
                style="width: 126px"
              >
                <a-option value="GREEN">已掌握</a-option>
                <a-option value="YELLOW">待巩固</a-option>
                <a-option value="RED">薄弱</a-option>
                <a-option value="GRAY">未学习</a-option>
              </a-select>
              <a-select
                v-model="jumpNodeKey"
                aria-label="跳转到节点"
                placeholder="跳转到节点"
                allow-search
                class="graph-node-jump"
                style="width: 150px"
                @change="onJumpNode"
              >
                <a-option
                  v-for="node in canvasGraph.nodes"
                  :key="nodeKeyOf(node)"
                  :value="nodeKeyOf(node)"
                >
                  {{ node.displayName || node.name }}
                </a-option>
              </a-select>
              <div class="graph-stats">
                课程共 {{ graphMeta.totalNodes }} 个节点 ·
                {{ graphMeta.totalLinks }} 条关系 · 可见
                <b>{{ canvasGraph.nodes.length }}</b> 个节点 ·
                {{ evidenceCount }}/{{ nodes.length }} 有学习记录
              </div>
            </div>

            <a-spin :loading="loading" style="width: 100%; display: block">
              <div class="canvas-wrap">
                <div
                  v-if="selectedNode"
                  class="graph-annotation-chip"
                  aria-hidden="true"
                >
                  <span class="graph-annotation-index">{{
                    selectedAnnotationNumber
                  }}</span>
                  <span class="graph-annotation-copy">
                    <b>{{ selectedTypeLabel }}类</b> ·
                    {{ selectedShortCrumb }} ·
                    {{ selectedRelationCount }} 条关系
                  </span>
                </div>
                <KnowledgeGraph
                  v-if="canvasGraph.nodes.length"
                  ref="graphRef"
                  :nodes="canvasGraph.nodes"
                  :links="canvasLinks"
                  :categories="categories"
                  :selected-id="selectedNodeId"
                  :pulses="pulses"
                  :annotations="graphAnnotations"
                  :show-status="hasPersonalGraph"
                  :show-relation-labels="showRelationLabels"
                  edge-tone="muted"
                  chart-height="480px"
                  @node-click="handleNodeClick"
                  @node-dblclick="handleNodeDblClick"
                />
                <div
                  v-else-if="graphError"
                  class="oslab-error canvas-state"
                  role="alert"
                >
                  <span class="oslab-state-title">{{ graphError }}</span>
                  <span class="oslab-state-hint"
                    >请确认登录状态与网络连接后重试</span
                  >
                  <a-button type="primary" size="small" @click="loadGraphData">
                    重试
                  </a-button>
                </div>
                <div v-else-if="!loading" class="oslab-empty canvas-state">
                  <template v-if="scopeEmpty">
                    <span class="oslab-state-title"
                      >本{{ scopeEmptyLabel }}暂无可见知识节点</span
                    >
                    <span class="oslab-state-hint"
                      >单元发布并在 nodeMappings
                      中关联现有节点后会显示在这里</span
                    >
                  </template>
                  <template v-else-if="nodes.length">
                    <span class="oslab-state-title"
                      >当前筛选条件下没有可见节点</span
                    >
                    <span class="oslab-state-hint"
                      >调整或清除筛选后即可恢复图谱</span
                    >
                    <a-button size="small" @click="resetViewFilters">
                      清除筛选
                    </a-button>
                  </template>
                  <template v-else>
                    <span class="oslab-state-title">知识图谱暂无数据</span>
                    <span class="oslab-state-hint"
                      >课程图谱尚未建立节点，请联系管理员维护</span
                    >
                  </template>
                </div>
              </div>
            </a-spin>

            <div class="graph-footer">
              <div class="legend-group legend-category">
                <span class="legend-caption">类型</span>
                <span class="legend-item">
                  <i class="swatch swatch-concept"></i>概念
                </span>
                <span class="legend-item">
                  <i class="swatch swatch-algorithm"></i>算法
                </span>
                <span class="legend-item">
                  <i class="swatch swatch-function"></i>机制
                </span>
                <span class="legend-item">
                  <i class="swatch swatch-default"></i>实现
                </span>
              </div>
              <div v-if="hasPersonalGraph" class="legend-group legend-mastery">
                <span class="legend-caption">掌握</span>
                <span class="legend-item legend-status legend-status-mastered"
                  >已掌握</span
                >
                <span class="legend-item legend-status legend-status-review"
                  >待巩固</span
                >
                <span class="legend-item legend-status legend-status-weak"
                  >薄弱</span
                >
                <span class="legend-item legend-status legend-status-unseen"
                  >未学习</span
                >
              </div>
              <div class="legend-group legend-path">
                <span class="legend-caption">路径</span>
                <span class="legend-item">
                  <i class="rel-line rel-path"></i>补救路径
                </span>
              </div>
              <div class="legend-group legend-relations">
                <span class="legend-caption">关系</span>
                <span class="legend-item">
                  <i class="rel-line rel-dependent"></i>依赖/前置
                </span>
                <span class="legend-item">
                  <i class="rel-line rel-contain"></i>包含
                </span>
                <span class="legend-item">
                  <i class="rel-line rel-related"></i>相关
                </span>
                <span class="legend-item">
                  <i class="rel-line rel-implements"></i>实现
                </span>
              </div>
              <span class="side-hint legend-hint"
                >文字色 = 掌握度 · 滚轮缩放 · 拖拽查看 · 单击选中 ·
                双击进入学习</span
              >
            </div>
          </div>
        </div>

        <!-- 右侧知识点详情 -->
        <div class="detail-col">
          <div class="detail-card">
            <template v-if="selectedUnit && !selectedNode">
              <div class="detail-head">
                <span class="card-title">学习单元</span>
                <span
                  v-if="selectedUnitProgress"
                  class="oslab-status-tag"
                  :class="
                    'oslab-status-tag--' +
                    (selectedUnitProgress.status === 'COMPLETED'
                      ? 'mastered'
                      : selectedUnitProgress.status === 'IN_PROGRESS'
                      ? 'review'
                      : 'unseen')
                  "
                >
                  {{
                    selectedUnitProgress.status === "COMPLETED"
                      ? "已完成"
                      : selectedUnitProgress.status === "IN_PROGRESS"
                      ? "学习中"
                      : "未开始"
                  }}
                </span>
              </div>

              <h3 class="node-name">{{ selectedUnit.title }}</h3>
              <div class="node-crumb">{{ selectedUnitCrumb }}</div>

              <p v-if="unitPreviewLoading" class="side-hint">
                正在加载单元信息…
              </p>
              <template v-else>
                <p v-if="unitPreviewError" class="side-hint">
                  {{ unitPreviewError }}
                </p>

                <div class="section-title">学习目标</div>
                <ul v-if="unitObjectives.length" class="unit-objectives">
                  <li v-for="objective in unitObjectives" :key="objective">
                    {{ objective }}
                  </li>
                </ul>
                <p v-else class="side-hint">
                  该单元还没有填写可检查的学习目标。
                </p>

                <template v-if="selectedUnitProgress">
                  <div class="section-title">学习进度</div>
                  <div class="score-sub">
                    已完成必需活动
                    {{ selectedUnitProgress.completedCount }}/{{
                      selectedUnitProgress.requiredCount
                    }}
                    · {{ selectedUnitProgress.percent }}%
                    <template
                      v-if="
                        selectedUnitProgress.goalStatus === 'REVIEW_RECOMMENDED'
                      "
                    >
                      · 建议复习
                    </template>
                    <template
                      v-else-if="selectedUnitProgress.goalStatus === 'PASSED'"
                    >
                      · 检查点已通过
                    </template>
                  </div>
                  <a-progress
                    :percent="(selectedUnitProgress.percent || 0) / 100"
                    :show-text="false"
                    stroke-color="var(--oslab-primary)"
                    size="small"
                  />
                </template>

                <div class="section-title">关联知识节点</div>
                <div v-if="selectedUnitMappings.length" class="mapping-chips">
                  <button
                    v-for="mapping in selectedUnitMappings"
                    :key="mapping.nodeCode"
                    type="button"
                    class="mapping-chip"
                    @click="selectUnitNode(mapping)"
                  >
                    <span>{{
                      mapping.name || nodeNameOfCode(mapping.nodeCode)
                    }}</span>
                    <small>{{ roleText(mapping.role) }}</small>
                  </button>
                </div>
                <p v-else class="side-hint">
                  该单元尚未关联图谱节点，发布后会在此显示。
                </p>

                <a-button
                  type="primary"
                  long
                  size="large"
                  class="cta-main"
                  @click="openSelectedUnit"
                >
                  {{
                    selectedUnitProgress?.status === "COMPLETED"
                      ? "复习本单元"
                      : selectedUnitProgress?.status === "IN_PROGRESS"
                      ? "继续学习"
                      : "开始学习"
                  }}
                </a-button>
              </template>
            </template>

            <template v-else-if="selectedNode">
              <div class="detail-head">
                <span class="card-title">NODE / INSPECTOR</span>
                <span
                  v-if="hasPersonalGraph"
                  class="oslab-status-tag"
                  :class="
                    'oslab-status-tag--' +
                    statusTagClass(selectedMastery?.status || 'GRAY')
                  "
                >
                  {{ statusText(selectedMastery?.status || "GRAY") }}
                </span>
              </div>

              <h3 class="node-name oslab-highlight-text">
                {{ selectedNode.displayName || selectedNode.name }}
              </h3>
              <div class="node-crumb">
                {{ selectedCrumb }} · {{ nodeTypeLabel(selectedNode) }}
              </div>
              <button
                v-if="selectedUnit"
                type="button"
                class="context-link"
                @click="backToUnitOverview"
              >
                ← 返回单元概览
              </button>
              <button
                v-else-if="nodeUnitCandidates.length"
                type="button"
                class="context-link"
                @click="selectUnitScope(nodeUnitCandidates[0].unitCode)"
              >
                打开所在单元「{{ nodeUnitCandidates[0].title }}」
              </button>

              <blockquote
                v-if="selectedNode.description"
                class="detail-definition"
              >
                <span class="detail-definition-label">定义</span>
                <span>{{ selectedNode.description }}</span>
              </blockquote>
              <p v-else class="detail-definition detail-definition--empty">
                <span class="detail-definition-label">定义</span>
                <span>暂无节点描述</span>
              </p>

              <AnnotationNote
                :index="selectedAnnotationNumber"
                title="批注 / NOTES"
                tone="primary"
              >
                {{ selectedAnnotationText }}
              </AnnotationNote>

              <div class="section-title">掌握状态</div>
              <template v-if="selectedMastery">
                <div class="score-row">
                  <span class="score-label">概念理解</span>
                  <a-progress
                    :percent="selectedMastery.conceptScore ?? 0"
                    :show-text="false"
                    stroke-color="var(--oslab-primary)"
                    style="flex: 1"
                  />
                  <span class="score-pct blue">
                    {{
                      scoreText(
                        selectedMastery.conceptScore,
                        selectedMastery.conceptEvidenceCount
                      )
                    }}
                  </span>
                </div>
                <div class="score-row">
                  <span class="score-label">实践应用</span>
                  <a-progress
                    :percent="selectedMastery.practiceScore ?? 0"
                    :show-text="false"
                    stroke-color="#4f8875"
                    style="flex: 1"
                  />
                  <span class="score-pct green">
                    {{
                      scoreText(
                        selectedMastery.practiceScore,
                        selectedMastery.practiceEvidenceCount
                      )
                    }}
                  </span>
                </div>
                <div class="score-sub">
                  可信度 {{ percentText(selectedMastery.confidence) }} ·
                  概念证据 {{ selectedMastery.conceptEvidenceCount || 0 }} 条 ·
                  实践证据 {{ selectedMastery.practiceEvidenceCount || 0 }} 条
                </div>
              </template>
              <p v-else-if="hasPersonalGraph" class="side-hint">
                尚未学习：完成本节点的题目、实验或模拟练习后，这里会显示掌握状态。
              </p>
              <p v-else class="side-hint">
                登录学生账号后展示个人掌握状态与学习证据。
              </p>

              <div class="section-title">掌握证据</div>
              <div v-if="selectedQuestionStats" class="evidence-stats">
                <div>
                  <b>{{ selectedQuestionStats.total }}</b>
                  <span>本节点题目</span>
                </div>
                <div>
                  <b>{{ selectedQuestionStats.attempted }}</b>
                  <span>已作答</span>
                </div>
              </div>
              <div v-if="workbenchLoading" class="evidence-loading">
                <span class="oslab-spinner"></span>
                <span class="side-hint">正在获取学习证据…</span>
              </div>
              <template
                v-else-if="latestEvent || lastFailure || questionStatsLine"
              >
                <div
                  v-if="latestEvent"
                  class="evidence-card"
                  :class="
                    latestEvent.result === 'PASS'
                      ? 'evidence-pass'
                      : 'evidence-fail'
                  "
                >
                  <a-space size="small" style="margin-bottom: 6px">
                    <a-tag size="small" color="red" outline>
                      {{ evidenceTag(latestEvent) }}
                    </a-tag>
                  </a-space>
                  <div class="evidence-title">
                    {{ evidenceTitle(latestEvent) }}
                  </div>
                  <div class="evidence-meta">
                    {{ resultText(latestEvent.result) }} ·
                    {{ relativeTime(latestEvent.createTime) }}
                  </div>
                </div>
                <div v-if="lastFailure" class="evidence-card evidence-fail">
                  <a-space size="small" style="margin-bottom: 6px">
                    <a-tag size="small" color="red" outline>最近失败</a-tag>
                  </a-space>
                  <div class="evidence-title">
                    {{ lastFailure.errorType || "最近一次未通过的记录" }}
                  </div>
                  <div class="evidence-meta">
                    {{ resultText(lastFailure.result)
                    }}<template v-if="lastFailure.createTime">
                      · {{ relativeTime(lastFailure.createTime) }}</template
                    >
                  </div>
                  <div
                    v-if="lastFailure.recommendedAction"
                    class="evidence-action"
                  >
                    建议：{{ lastFailure.recommendedAction }}
                  </div>
                </div>
              </template>
              <p v-else class="side-hint">
                暂无学习证据：完成本节点的题目、编程实验或算法演示后会在这里出现。
              </p>

              <div class="section-title">关联节点</div>
              <div v-if="allRelatedRelations.length" class="relation-chips">
                <button
                  v-for="item in allRelatedRelations.slice(0, 3)"
                  :key="`chip-${item.nodeKey}`"
                  type="button"
                  class="relation-chip"
                  @click="selectAndFocus(item.nodeKey)"
                >
                  <i class="dot" :class="dotClass(item.status)"></i>
                  {{ item.displayName }}
                </button>
              </div>
              <template
                v-if="
                  relationGroups.prerequisites.length ||
                  relationGroups.successors.length ||
                  relationGroups.related.length
                "
              >
                <div
                  v-if="relationGroups.prerequisites.length"
                  class="rel-group"
                >
                  <div class="rel-group-caption">前置知识（先学）</div>
                  <button
                    v-for="item in relationGroups.prerequisites"
                    :key="'pre-' + item.nodeKey"
                    type="button"
                    class="rel-node"
                    @click="selectAndFocus(item.nodeKey)"
                  >
                    <i class="dot" :class="dotClass(item.status)"></i>
                    <span class="rel-node-name">{{ item.displayName }}</span>
                  </button>
                </div>
                <div v-if="relationGroups.successors.length" class="rel-group">
                  <div class="rel-group-caption">后续知识（后学）</div>
                  <button
                    v-for="item in relationGroups.successors"
                    :key="'suc-' + item.nodeKey"
                    type="button"
                    class="rel-node"
                    @click="selectAndFocus(item.nodeKey)"
                  >
                    <i class="dot" :class="dotClass(item.status)"></i>
                    <span class="rel-node-name">{{ item.displayName }}</span>
                  </button>
                </div>
                <div v-if="relationGroups.related.length" class="rel-group">
                  <div class="rel-group-caption">相关节点</div>
                  <button
                    v-for="item in relationGroups.related"
                    :key="'rel-' + item.nodeKey"
                    type="button"
                    class="rel-node"
                    @click="selectAndFocus(item.nodeKey)"
                  >
                    <i class="dot" :class="dotClass(item.status)"></i>
                    <span class="rel-node-name">{{ item.displayName }}</span>
                    <span v-for="tag in item.tags" :key="tag" class="rel-tag">{{
                      tag
                    }}</span>
                  </button>
                </div>
              </template>
              <p v-else class="side-hint">暂无关联节点</p>

              <!-- log3 收尾 C：详情只保留"进入学习"一个主入口；
                   题目/实验入口只在节点工作台的对应页签出现 -->
              <a-button
                type="primary"
                long
                size="large"
                class="cta-main"
                :disabled="!canEnterNodeWorkspace"
                @click="enterNodeWorkspace()"
              >
                进入节点工作台
              </a-button>
              <button
                type="button"
                class="cta-secondary"
                @click="openNodeQuestions"
              >
                查看该节点的关联题目
              </button>

              <template v-if="pathData && pathData.items.length > 1">
                <div class="section-title">
                  补救学习路径（{{ pathData.items.length }} 步）
                </div>
                <button
                  v-for="item in pathData.items"
                  :key="item.nodeName"
                  type="button"
                  class="path-item"
                  :class="{
                    'path-target': item.nodeName === pathData.targetNodeName,
                  }"
                  @click="selectAndFocus(nodeKeyForName(item.nodeName))"
                >
                  <span class="path-order">{{ item.order }}</span>
                  <div class="path-body">
                    <div class="path-name">
                      {{ item.nodeName }}
                      <span
                        class="oslab-status-tag mini"
                        :class="
                          'oslab-status-tag--' +
                          statusTagClass(item.status || 'GRAY')
                        "
                        >{{ statusText(item.status || "GRAY") }}</span
                      >
                    </div>
                    <div class="path-reason">{{ item.reason }}</div>
                  </div>
                </button>
              </template>

              <template v-else-if="primaryRecommendation">
                <div class="section-title">推荐下一步</div>
                <div class="recommend-action">
                  {{ primaryRecommendation.action }}
                </div>
                <div class="recommend-reason">
                  原因：{{ primaryRecommendation.reason }}
                </div>
              </template>

              <!-- 管理维护收进折叠区，不与学生 CTA 混排（5 个管理 Modal 全部保留） -->
              <a-collapse
                v-if="isTeaching && isAdmin"
                class="admin-zone"
                :bordered="false"
              >
                <a-collapse-item header="管理维护" key="admin">
                  <a-space wrap>
                    <a-button size="small" @click="showChapterModal"
                      >修改章节</a-button
                    >
                    <a-popconfirm
                      content="删除节点及其关联关系后无法恢复，确定继续吗？"
                      @ok="deleteSelectedNode"
                    >
                      <a-button size="small" status="danger"
                        >删除此节点</a-button
                      >
                    </a-popconfirm>
                    <a-button size="small" @click="showAddNodeModal"
                      >新建节点</a-button
                    >
                    <a-button size="small" @click="showRelationModal"
                      >添加关系</a-button
                    >
                    <a-button size="small" @click="showCypherModal"
                      >执行 Cypher</a-button
                    >
                    <a-button
                      size="small"
                      type="primary"
                      status="warning"
                      @click="openNodeContentModal"
                      >编辑学习素材</a-button
                    >
                  </a-space>
                </a-collapse-item>
              </a-collapse>
            </template>

            <template v-else>
              <div class="detail-head">
                <span class="card-title">知识点详情</span>
              </div>
              <div class="oslab-empty detail-state">
                <span class="oslab-state-title">未选中知识点</span>
                <span class="oslab-state-hint"
                  >点击图谱节点，查看学习状态、证据与下一步推荐</span
                >
              </div>
            </template>

            <section class="graph-ai-plan" aria-label="AI 下一步">
              <div class="section-title">AI 下一步</div>
              <p v-if="graphWeaknessReasons.length" class="graph-ai-reason">
                薄弱依据：{{ graphWeaknessReasons.join("；") }}
              </p>
              <p v-else class="side-hint">
                当前证据不足，先完成一次作答或预测再判断薄弱点。
              </p>
              <AiNextActions
                :actions="nextActions"
                :course-code="courseCode"
                :loading="nextActionsLoading"
                :error="nextActionsError"
                :due-count="dueReviewCount"
                title=""
                empty-text="完成一次学习活动后会生成可执行任务。"
                compact
                :limit="2"
                @refresh="refreshGraphPlan"
              />
              <button
                type="button"
                class="cta-secondary graph-ai-ask"
                @click="askGraphTutor"
              >
                向统一导师提问
              </button>
            </section>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 管理员弹窗（节点/关系/类型用选择控件，Cypher 为高级工具；功能全部保留） -->
  <a-modal
    v-model:visible="addRelationVisible"
    title="添加关系"
    width="700px"
    :footer="false"
  >
    <a-form :model="relationForm" label-align="left">
      <a-form-item field="fromName" label="起始节点">
        <a-select
          v-model="relationForm.fromName"
          placeholder="搜索并选择起始节点"
          allow-search
        >
          <a-option v-for="n in nodeOptions" :key="n" :value="n">
            {{ n }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="relationType" label="关系类型">
        <a-select v-model="relationForm.relationType" placeholder="请选择关系">
          <a-option value="相关">相关</a-option>
          <a-option value="包含">包含</a-option>
          <a-option value="依赖">依赖</a-option>
          <a-option value="实现">实现</a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="toName" label="目标节点">
        <a-select
          v-model="relationForm.toName"
          placeholder="搜索并选择目标节点"
          allow-search
        >
          <a-option v-for="n in nodeOptions" :key="n" :value="n">
            {{ n }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-button type="primary" @click="addRelation">添加</a-button>
    </a-form>
  </a-modal>
  <a-modal
    v-model:visible="addNodeVisible"
    title="新建知识节点"
    :ok-loading="nodeSubmitting"
    :on-before-ok="addNode"
  >
    <a-form :model="nodeForm" label-align="left">
      <a-form-item field="name" label="名称" required>
        <a-input v-model="nodeForm.name" placeholder="请输入节点名称" />
      </a-form-item>
      <a-form-item field="chapter" label="所属章节" required>
        <a-select
          v-model="nodeForm.chapter"
          placeholder="选择已有章节或输入课程章节"
          allow-create
          allow-search
        >
          <a-option
            v-for="chapter in availableChapters"
            :key="chapter"
            :value="chapter"
          >
            {{ chapter }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="category" label="节点类型">
        <a-select
          v-model="nodeForm.category"
          placeholder="选择已有类型或输入新类型"
          allow-create
          allow-search
        >
          <a-option v-for="c in availableCategories" :key="c" :value="c">
            {{ c }}
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="description" label="描述">
        <a-textarea
          v-model="nodeForm.description"
          placeholder="请输入节点说明"
        />
      </a-form-item>
    </a-form>
  </a-modal>
  <a-modal
    v-if="isTeaching && isAdmin"
    v-model:visible="chapterModalVisible"
    title="修改节点所属章节"
    :ok-loading="chapterSubmitting"
    :on-before-ok="updateChapter"
  >
    <a-form :model="chapterForm" label-align="left">
      <a-form-item label="节点">
        {{ chapterForm.nodeName }}
      </a-form-item>
      <a-form-item field="chapter" label="所属章节" required>
        <a-select
          v-model="chapterForm.chapter"
          placeholder="选择已有章节或输入课程章节"
          allow-create
          allow-search
        >
          <a-option
            v-for="chapter in availableChapters"
            :key="chapter"
            :value="chapter"
          >
            {{ chapter }}
          </a-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>
  <a-modal
    v-model:visible="cypherVisible"
    title="高级工具：执行 Cypher"
    :confirm-loading="cypherSubmitting"
    @ok="executeCypher"
  >
    <a-alert type="warning" :show-icon="true" style="margin-bottom: 16px">
      高级工具：常用维护请使用上方节点/关系表单；此操作直接作用于知识图谱，仅限管理员确认语句无误后使用。
    </a-alert>
    <a-textarea
      v-model="cypherQuery"
      aria-label="Cypher 语句"
      :auto-size="{ minRows: 6, maxRows: 12 }"
      placeholder="例如：MATCH (n {name: '示例节点'}) RETURN n"
    />
  </a-modal>
  <!-- 节点共享学习素材维护（learning_node_content） -->
  <a-modal
    v-model:visible="nodeContentVisible"
    :title="`编辑学习素材：${selectedNode?.name || ''}`"
    :width="680"
    :confirm-loading="nodeContentSubmitting"
    @ok="saveNodeContentSubmit"
  >
    <a-alert type="normal" :show-icon="true" style="margin-bottom: 16px">
      素材面向全部学生在节点学习页展示；PUBLISHED 立即下发，DRAFT
      仅保存不上发，OFFLINE 下发位保留但不展示。
    </a-alert>
    <a-form :model="nodeContentForm" label-align="left" auto-label-width>
      <a-form-item label="状态">
        <a-radio-group v-model="nodeContentForm.status" type="button">
          <a-radio value="DRAFT">草稿</a-radio>
          <a-radio value="PUBLISHED">发布</a-radio>
          <a-radio value="OFFLINE">下线</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="素材内容">
        <a-textarea
          v-model="nodeContentForm.contentMd"
          :auto-size="{ minRows: 8, maxRows: 20 }"
          :max-length="50000"
          placeholder="节点学习素材（纯文本，支持换行分层）；上限 50000 字"
        />
      </a-form-item>
      <a-form-item label="资源链接">
        <a-textarea
          v-model="nodeContentForm.resourceLinks"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          placeholder='JSON 数组，例如：[{"title":"参考讲义","url":"https://..."}]，留空表示无'
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { Message } from "@arco-design/web-vue";

import KnowledgeGraph from "@/components/KnowledgeGraph.vue";
import AnnotationNote from "@/components/learning/AnnotationNote.vue";
import CourseCatalogSidebar from "@/components/learning/CourseCatalogSidebar.vue";
import {
  getCourseUnit,
  unitPath,
  type CourseUnit,
  type UnitSummary,
} from "@/api/course";
import { useCourseCatalog } from "@/composables/useCourseCatalog";
import { scoreText } from "@/utils/masteryDisplay";
import AiNextActions from "@/components/ai/AiNextActions.vue";
import { useAiConversation } from "@/composables/useAiConversation";
import { openTutorScene } from "@/utils/assessmentTutor";
import {
  personalGraph,
  masteryPath,
  getNodeContent,
  saveNodeContent,
  type LearningPathVO,
  type MasteryNodeVO,
  type NodeWorkbenchVO,
} from "@/api/learning";
import {
  registerAiPageContext,
  unregisterAiPageContext,
} from "@/composables/useAiPageContext";

// AI 助手页面上下文（log3 阶段 4）：挂载注册、卸载注销；升级为课程上下文
const aiPageContextProvider = () => ({
  pageName: "课程学习",
  courseCode: courseCode.value || undefined,
  courseTitle: courseTitle.value || undefined,
  nodeName: selectedNode.value?.name || undefined,
  nodeCode: selectedNode.value?.nodeCode || undefined,
  nodeCodes: selectedNode.value?.nodeCode ? [selectedNode.value.nodeCode] : [],
  sectionCode: selectedSectionCode.value || undefined,
  unitCode: selectedUnitCode.value || undefined,
  eventIds: selectedWorkbench.value?.events?.length
    ? selectedWorkbench.value.events
        .slice(0, 3)
        .map((event) => String(event.id))
    : [],
});
onMounted(() => registerAiPageContext(aiPageContextProvider));
onBeforeUnmount(() => unregisterAiPageContext(aiPageContextProvider));
import {
  loadNodeWorkbench,
  peekNodeWorkbench,
} from "@/composables/useNodeWorkbench";
import { KnowledgeGraphControllerService } from "../../../generated";
import store from "@/store";
import ACCESS_ENUM from "@/access/accessEnum";

const router = useRouter();
const route = useRoute();

// ============ 图谱数据 ============
const nodes = ref<MasteryNodeVO[]>([]);
const links = ref<any[]>([]);
const categories = ref<any[]>([]);
const loading = ref(false);
// 加载/空态/错误态（设计 §10：不使用空白区域掩盖请求失败）
const graphError = ref("");

const searchText = ref("");
const statusFilter = ref("");
const pathFocus = ref(false);
const jumpNodeKey = ref("");
// 关系文字标签常显开关（hover/选中相邻边始终显示）
const showRelationLabels = ref(false);

// §10.3 教学上下文：/teaching/knowledge 维护模式保留全量图谱与管理操作；
// /graph 学生视角由教材目录驱动范围。
const isTeaching = computed(() => route.path.startsWith("/teaching"));

// ============ 课程目录与范围 ============
const {
  courseCode,
  courseTitle,
  resume: courseResume,
  loading: catalogLoading,
  error: catalogError,
  sections: catalogSections,
  courses: catalogCourses,
  outlines: catalogOutlines,
  courseProgress: catalogProgress,
  sectionByCode,
  unitByCode,
  units: catalogUnits,
  load: loadCatalogSource,
  ancestorCodes,
  descendantCodes,
  sectionCrumb,
  courseCodeOfSection,
  courseCodeOfUnit,
} = useCourseCatalog();
const selectedSectionCode = ref("");
const selectedUnitCode = ref("");
const selectedNodeKey = ref("");
const expandedSections = ref<string[]>([]);

// 统一 AI 计划：图谱详情显示当前课程的下一步，并可直接导航到原页面
const {
  nextActions,
  nextActionsLoading,
  nextActionsError,
  dueReviewCount,
  refreshNextActions,
  syncCourse: syncAiCourse,
} = useAiConversation();

function refreshGraphPlan() {
  if (!courseCode.value) return;
  void refreshNextActions({ courseCode: courseCode.value, limit: 5 });
}

const graphWeaknessReasons = computed(() => {
  const reasons: string[] = [];
  const failure = lastFailure.value;
  if (failure?.attributionBasis?.length) {
    reasons.push(...failure.attributionBasis);
  }
  if (failure?.recommendedAction) reasons.push(failure.recommendedAction);
  const recommendation = primaryRecommendation.value;
  if (recommendation?.reason) reasons.push(recommendation.reason);
  return Array.from(new Set(reasons.filter(Boolean))).slice(0, 4);
});

function askGraphTutor() {
  const evidenceIds = (selectedWorkbench.value?.events || [])
    .slice(0, 3)
    .map((event) => String(event.id));
  const evidence = evidenceIds.map((id) => `#${id}`).join("、");
  const nodeName = selectedNode.value?.name || "未命名";
  const nodeCode = selectedNode.value?.nodeCode;
  const nameSuffix = nodeCode ? `（${nodeCode}）` : "";
  const weakness = graphWeaknessReasons.value.length
    ? `薄弱依据：${graphWeaknessReasons.value.join("；")}。`
    : "目前证据不足。";
  const evidenceText = evidence ? `相关证据：${evidence}。` : "";
  const draft = `我在课程图谱里选中了节点「${nodeName}」${nameSuffix}。${weakness}${evidenceText}请说明可能原因并给出下一步。`;
  openTutorScene({
    scene: "NODE",
    intent: "EXPLAIN",
    courseCode: courseCode.value || "",
    nodeCodes: selectedNode.value?.nodeCode
      ? [selectedNode.value.nodeCode]
      : [],
    eventIds: evidenceIds,
    draft,
    delivery: "SEND",
  });
}

watch(
  courseCode,
  (code) => {
    if (code) void refreshNextActions({ courseCode: code, limit: 5 });
  },
  { immediate: true }
);
const unitPreview = ref<CourseUnit | null>(null);
const unitPreviewLoading = ref(false);
const unitPreviewError = ref("");
let unitPreviewEpoch = 0;

// §10.5/§10.6 后端补救路径（PathItem 投影）与状态脉冲
const pathData = ref<LearningPathVO | null>(null);
const pulses = ref<
  Array<{ key: string; name: string; color: "blue" | "amber" }>
>([]);

// 统一身份规则：nodeKey = nodeCode || stableId || String(id) || name。
// 选中、路由、点击载荷、画布 selectedId、工作台跳转全部复用这一规则；
// 旧全量图谱节点没有 nodeCode 时仍能通过 id / name 稳定回查。
interface GraphNodeKeyLike {
  nodeCode?: string | null;
  stableId?: string | null;
  id?: string | number | null;
  name?: string | null;
}
const identityPart = (value: unknown) =>
  value === null || value === undefined || value === ""
    ? ""
    : String(value).trim();
const nodeKeyOf = (node?: GraphNodeKeyLike | null) => {
  if (!node) return "";
  return (
    identityPart(node.nodeCode) ||
    identityPart(node.stableId) ||
    identityPart(node.id) ||
    identityPart(node.name)
  );
};
const nodeByKey = computed(() => {
  const map = new Map<string, MasteryNodeVO>();
  for (const node of nodes.value) map.set(nodeKeyOf(node), node);
  return map;
});
const selectedNode = computed(
  () => nodeByKey.value.get(selectedNodeKey.value) || null
);
const selectedNodeId = computed(() =>
  selectedNode.value ? nodeKeyOf(selectedNode.value) : ""
);
const selectedName = computed(() => selectedNode.value?.name || "");
const selectedWorkbench = ref<NodeWorkbenchVO | null>(null);
const workbenchLoading = ref(false);
const personalGraphMeta = ref<{
  totalNodes: number;
  totalLinks: number;
  recommendedNodeName: string;
} | null>(null);

// 个人图谱是否加载成功（决定状态条/圆点与状态图例是否展示）
const hasPersonalGraph = computed(() => personalGraphMeta.value !== null);

const isAdmin = computed(
  () => store.state.user.loginUser?.userRole === ACCESS_ENUM.ADMIN
);

const isLoggedIn = () => {
  const role = store.state.user?.loginUser?.userRole;
  return !!role && role !== ACCESS_ENUM.NOT_LOGIN;
};

// useNodeWorkbench 缓存键按用户隔离
const workbenchUserId = computed(() =>
  String(store.state.user?.loginUser?.id || "anonymous")
);

const nodeByName = computed(() => {
  const map = new Map<string, MasteryNodeVO>();
  for (const node of nodes.value) {
    map.set(node.name, node);
  }
  return map;
});
/** 旧接口只回传节点名称时，用当前快照解析出稳定 nodeKey（找不到才退回名称） */
const nodeKeyForName = (name?: string | null) => {
  const key = identityPart(name);
  if (!key) return "";
  const node = nodeByName.value.get(key);
  return node ? nodeKeyOf(node) : key;
};

const evidenceCount = computed(
  () => nodes.value.filter((n) => n.status && n.status !== "GRAY").length
);

// 画布动作（真实动作：重置视野 —— center/zoom 复位为全景，不重建布局）
const graphRef = ref();
const resetViewCanvas = () => graphRef.value?.resetView?.();

const statusCounts = computed(() => {
  const counts = { GREEN: 0, YELLOW: 0, RED: 0, GRAY: 0 };
  for (const node of nodes.value) {
    const key = (node.status || "GRAY") as keyof typeof counts;
    counts[key] += 1;
  }
  return counts;
});

const availableCategories = computed(() =>
  Array.from(new Set(categories.value.map((c: any) => c.name)))
);

/** 全部节点名（关系弹窗下拉搜索） */
const nodeOptions = computed(() =>
  nodes.value.map((n) => n.name).sort((a, b) => a.localeCompare(b, "zh"))
);

// ============ 真实数据派生（全图/我的掌握共用完整快照，不构造演示子图） ============
const chapterLabel = (node?: Pick<MasteryNodeVO, "chapter">) =>
  node?.chapter?.trim() || "未分类";

const compareChapters = (a: string, b: string) => {
  if (a === b) return 0;
  if (a === "未分类") return 1;
  if (b === "未分类") return -1;
  return a.localeCompare(b, "zh-CN", { numeric: true });
};

const availableChapters = computed(() =>
  Array.from(new Set(nodes.value.map((node) => chapterLabel(node)))).sort(
    compareChapters
  )
);

/** 默认焦点：后端推荐节点（薄弱中 pKnown 最低）；无证据时回退首个节点 */
const defaultFocusName = computed(() => {
  if (graphMeta.value.recommendedNodeName) {
    return graphMeta.value.recommendedNodeName;
  }
  const candidates = nodes.value
    .filter(
      (n) => ("RED" === n.status || "YELLOW" === n.status) && n.pKnown != null
    )
    .sort((a, b) => (a.pKnown ?? 1) - (b.pKnown ?? 1));
  return candidates[0]?.name || nodes.value[0]?.name || "";
});

/** 节点 -> 章节（后端快照真实 chapter 字段） */
function chapterOf(nodeName: string): string {
  return chapterLabel(nodeByName.value.get(nodeName));
}

/** 章节进度：覆盖率 = 有学习记录节点占比；均值仅统计已测节点 */
const chapterProgress = computed(() => {
  const groups = new Map<
    string,
    { sum: number; evidence: number; total: number; mastered: number }
  >();
  for (const node of nodes.value) {
    const key = chapterLabel(node);
    const g = groups.get(key) || { sum: 0, evidence: 0, total: 0, mastered: 0 };
    g.total += 1;
    if (node.status && node.status !== "GRAY" && node.pKnown != null) {
      g.evidence += 1;
      g.sum += node.pKnown;
    }
    if (node.status === "GREEN") {
      g.mastered += 1;
    }
    groups.set(key, g);
  }
  const result: {
    name: string;
    progress: number;
    total: number;
    withEvidence: number;
    mastered: number;
    avgText: string;
  }[] = [];
  for (const [name, g] of groups.entries()) {
    result.push({
      name,
      progress: g.total ? Math.round((g.evidence / g.total) * 100) : 0,
      total: g.total,
      withEvidence: g.evidence,
      mastered: g.mastered,
      avgText: g.evidence ? `${Math.round((g.sum / g.evidence) * 100)}%` : "-",
    });
  }
  return result.sort((a, b) => compareChapters(a.name, b.name));
});

/** 推荐节点：优先后端 recommendedNodeName，回退首个薄弱节点 */
const focusName = computed(() => selectedName.value || defaultFocusName.value);

/** 补救路径边：路径项 → 直接前置（依赖方 -> 前置），用于高亮 */
const pathEdgeKeys = computed(() => {
  const keys = new Set<string>();
  const nameToId = new Map(nodes.value.map((n) => [n.name, String(n.id)]));
  for (const item of pathData.value?.items || []) {
    for (const pre of item.prerequisites || []) {
      const src = nameToId.get(item.nodeName);
      const tgt = nameToId.get(pre);
      if (src && tgt) keys.add(src + "->" + tgt);
    }
  }
  return keys;
});

/** 后端图谱统计（totalNodes/totalLinks/recommendedNodeName） */
const graphMeta = computed(
  () =>
    personalGraphMeta.value || {
      totalNodes: nodes.value.length,
      totalLinks: links.value.length,
      recommendedNodeName: "",
    }
);

// ============ 教材范围：课程 → 章/节 → 单元 → 图谱节点 ============
interface UnitNodeMapping {
  nodeCode: string;
  role: string;
  name?: string;
  nodeName?: string;
}

const UNIT_MAPPING_ROLE_TEXT: Record<string, string> = {
  TEACHES: "教学",
  PREREQUISITE: "前置",
  PRACTICE: "练习",
  ASSESSMENT: "测评",
  REVIEW: "复习",
};
const roleText = (role?: string) =>
  UNIT_MAPPING_ROLE_TEXT[String(role || "").toUpperCase()] || role || "关联";

const selectedUnitSummary = computed<UnitSummary | null>(() =>
  selectedUnitCode.value
    ? unitByCode.value.get(selectedUnitCode.value) || null
    : null
);
const selectedUnit = computed<CourseUnit | UnitSummary | null>(() =>
  unitPreview.value?.unitCode === selectedUnitCode.value
    ? unitPreview.value
    : selectedUnitSummary.value
);
const selectedUnitMappings = computed<UnitNodeMapping[]>(() => {
  const preview = unitPreview.value;
  if (
    preview?.unitCode === selectedUnitCode.value &&
    preview.nodeMappings?.length
  ) {
    return preview.nodeMappings as UnitNodeMapping[];
  }
  return (selectedUnitSummary.value?.nodeMappings || []) as UnitNodeMapping[];
});

/** nodeMappings 的 nodeCode 在当前图谱快照中找到对应节点键（旧节点回退名称） */
const mappedNodeKey = (mapping: UnitNodeMapping): string => {
  if (nodeByKey.value.has(mapping.nodeCode)) return mapping.nodeCode;
  for (const name of [mapping.name, mapping.nodeName]) {
    if (!name) continue;
    const node = nodeByName.value.get(name);
    if (node) return nodeKeyOf(node);
  }
  return "";
};

const unitDirectKeys = computed(() => {
  const keys = new Set<string>();
  for (const mapping of selectedUnitMappings.value) {
    const key = mappedNodeKey(mapping);
    if (key) keys.add(key);
  }
  return keys;
});
const unitTeachKeys = computed(() => {
  const keys = new Set<string>();
  for (const mapping of selectedUnitMappings.value) {
    if (String(mapping.role || "").toUpperCase() !== "TEACHES") continue;
    const key = mappedNodeKey(mapping);
    if (key) keys.add(key);
  }
  return keys;
});
/** 单元范围 = 教学节点 + 真实关系的一跳邻接（作弱化上下文） */
const unitContextKeys = computed(() => {
  const keys = new Set(unitDirectKeys.value);
  if (!keys.size) return keys;
  const idToKey = new Map(
    nodes.value.map((node) => [String(node.id), nodeKeyOf(node)])
  );
  const keyToId = new Map(
    nodes.value.map((node) => [nodeKeyOf(node), String(node.id)])
  );
  for (const key of Array.from(keys)) {
    const id = keyToId.get(key);
    if (!id) continue;
    for (const link of links.value) {
      const source = String(link.source);
      const target = String(link.target);
      if (source === id && idToKey.has(target))
        keys.add(idToKey.get(target) as string);
      if (target === id && idToKey.has(source))
        keys.add(idToKey.get(source) as string);
    }
  }
  return keys;
});

/** 章节/小节范围 = 后代小节中的节点 + 后代已发布单元映射的教学节点 */
const sectionScopeKeys = computed<Set<string> | null>(() => {
  if (!selectedSectionCode.value) return null;
  const codes = descendantCodes(selectedSectionCode.value);
  const keys = new Set<string>();
  for (const node of nodes.value) {
    if (node.homeSectionCode && codes.has(node.homeSectionCode)) {
      keys.add(nodeKeyOf(node));
    }
  }
  for (const unit of catalogUnits.value) {
    if (!codes.has(unit.sectionCode)) continue;
    for (const mapping of (unit.nodeMappings || []) as UnitNodeMapping[]) {
      const key = mappedNodeKey(mapping);
      if (key) keys.add(key);
    }
  }
  return keys;
});

/** 目录范围节点；深链选中的范围外节点仍保留在画布，供右侧详情展示 */
const scopeBaseNodes = computed(() => {
  let result = nodes.value;
  if (selectedUnitCode.value) {
    result = nodes.value.filter((node) =>
      unitContextKeys.value.has(nodeKeyOf(node))
    );
  } else if (selectedSectionCode.value) {
    const allowed = sectionScopeKeys.value;
    result = allowed
      ? nodes.value.filter((node) => allowed.has(nodeKeyOf(node)))
      : nodes.value;
  }
  if (
    selectedNodeKey.value &&
    !result.some((node) => nodeKeyOf(node) === selectedNodeKey.value)
  ) {
    const selected = nodeByKey.value.get(selectedNodeKey.value);
    if (selected) result = [...result, selected];
  }
  return result;
});

const filteredNodes = computed(() => {
  let result = scopeBaseNodes.value;
  const keyword = searchText.value.trim().toLowerCase();
  if (keyword) {
    result = result.filter((node) =>
      `${node.displayName || ""} ${node.name || ""}`
        .toLowerCase()
        .includes(keyword)
    );
  }
  if (statusFilter.value) {
    result = result.filter((node) => node.status === statusFilter.value);
  }
  return result;
});

/** 焦点节点的稳定键（选中优先，否则后端推荐/首个薄弱节点） */
const defaultFocusKey = computed(() =>
  nodeKeyOf(nodeByName.value.get(defaultFocusName.value))
);
const focusKey = computed(() => selectedNodeKey.value || defaultFocusKey.value);

/** 补救路径节点键（后端拓扑排序结果，前置在前） */
const pathNodeKeys = computed(() => {
  const keys = new Set<string>();
  for (const item of pathData.value?.items || []) {
    const node = nodeByName.value.get(item.nodeName);
    keys.add(node ? nodeKeyOf(node) : item.nodeName);
  }
  return keys;
});

/** 焦点节点的一跳邻接键集合 */
const neighborKeys = computed(() => {
  const keys = new Set<string>();
  const focus = focusKey.value;
  if (!focus) return keys;
  keys.add(focus);
  const keyToId = new Map(
    nodes.value.map((node) => [nodeKeyOf(node), String(node.id)])
  );
  const idToKey = new Map(
    nodes.value.map((node) => [String(node.id), nodeKeyOf(node)])
  );
  const focusId = keyToId.get(focus);
  if (!focusId) return keys;
  for (const link of links.value) {
    const source = String(link.source);
    const target = String(link.target);
    if (source === focusId && idToKey.has(target))
      keys.add(idToKey.get(target) as string);
    if (target === focusId && idToKey.has(source))
      keys.add(idToKey.get(source) as string);
  }
  return keys;
});

// 「只看学习路径」：把画布收窄到后端补救路径 + 焦点一跳邻接（显式筛选，不删全图节点）
const canvasBaseNodes = computed(() => {
  if (!pathFocus.value) return filteredNodes.value;
  const allowed = new Set<string>([
    ...pathNodeKeys.value,
    ...neighborKeys.value,
  ]);
  return filteredNodes.value.filter((node) => allowed.has(nodeKeyOf(node)));
});

// 画布数据 = 范围内节点；单元 TEACHES 节点标记为路径提示（不是选中）
const canvasGraph = computed(() => ({
  nodes: canvasBaseNodes.value.map((node) =>
    selectedUnitCode.value && unitTeachKeys.value.has(nodeKeyOf(node))
      ? {
          ...node,
          pathHighlighted: true,
          highlightReason: "teaches-current-unit",
        }
      : node
  ),
}));

/** 节点是否仍在当前可见画布内（筛选/范围隐藏后不得静默跳转） */
const isNodeVisibleInCanvas = (node?: MasteryNodeVO | null) =>
  !!node &&
  canvasGraph.value.nodes.some((item) => nodeKeyOf(item) === nodeKeyOf(node));
const canEnterNodeWorkspace = computed(() =>
  isNodeVisibleInCanvas(selectedNode.value)
);

const canvasLinks = computed(() => {
  const ids = new Set(canvasBaseNodes.value.map((n) => String(n.id)));
  const base = links.value.filter(
    (l) => ids.has(String(l.source)) && ids.has(String(l.target))
  );
  if (!pathFocus.value) {
    return base;
  }
  // 补救学习路径边：交给图谱组件用统一路径 token 绘制（不在视图散落色值）
  return base.map((l) => {
    const key = String(l.source) + "->" + String(l.target);
    if (pathEdgeKeys.value.has(key)) {
      return {
        ...l,
        lineStyle: { pathEmphasis: true },
      };
    }
    return l;
  });
});

// 筛选残留守卫：选中节点被搜索/状态筛掉后清空选中，
// 右栏不把节点详情残留到不可见节点上。
watch(filteredNodes, (visible) => {
  if (!selectedNodeKey.value) return;
  if (visible.some((node) => nodeKeyOf(node) === selectedNodeKey.value)) return;
  clearNodeSelection(false);
});

// ============ 右栏 ============
const selectedMastery = computed(() => {
  const node = selectedNode.value;
  if (!node || !node.status || node.status === "GRAY") return null;
  return node;
});

const nodeNameOfCode = (nodeCode: string) => {
  const node = nodeByKey.value.get(nodeCode);
  if (node) return node.displayName || node.name;
  const mapping = selectedUnitMappings.value.find(
    (item) => item.nodeCode === nodeCode
  );
  return mapping?.name || mapping?.nodeName || nodeCode;
};

const unitObjectives = computed<string[]>(
  () => unitPreview.value?.objectives || []
);
const selectedUnitProgress = computed(
  () =>
    unitPreview.value?.progress || selectedUnitSummary.value?.progress || null
);
const selectedUnitCrumb = computed(() => {
  const summary = selectedUnitSummary.value;
  return summary ? sectionCrumb(summary.sectionCode) : "";
});

/** 当前节点被哪些已发布单元正式映射（供“打开所在单元”） */
const nodeUnitCandidates = computed<UnitSummary[]>(() => {
  const node = selectedNode.value;
  if (!node) return [];
  const key = nodeKeyOf(node);
  const result: UnitSummary[] = [];
  for (const unit of catalogUnits.value) {
    const mappings = (unit.nodeMappings || []) as UnitNodeMapping[];
    const hit = mappings.some(
      (mapping) =>
        mappedNodeKey(mapping) === key ||
        (!!mapping.name && mapping.name === node.name)
    );
    if (hit) result.push(unit);
  }
  return result;
});

const selectedCrumb = computed(() => {
  const node = selectedNode.value;
  if (!node) return "";
  const parts: string[] = [];
  const sectionCode =
    node.homeSectionCode || nodeUnitCandidates.value[0]?.sectionCode || "";
  if (sectionCode) parts.push(sectionCrumb(sectionCode));
  if (
    selectedUnit.value &&
    selectedUnitMappings.value.some(
      (mapping) => mappedNodeKey(mapping) === nodeKeyOf(node)
    )
  ) {
    parts.push(selectedUnit.value.title);
  }
  if (!parts.length) {
    const containParent = new Map<string, string>();
    const idToName = new Map(nodes.value.map((n) => [String(n.id), n.name]));
    for (const link of links.value) {
      if (normalizeRelationType(link) === "contain") {
        const p = idToName.get(String(link.source));
        const c = idToName.get(String(link.target));
        if (p && c) containParent.set(c, p);
      }
    }
    parts.push(chapterOf(node.name), containParent.get(node.name) || "");
  }
  return parts.filter(Boolean).join(" / ");
});

const scopeLabel = computed(() => {
  if (selectedUnit.value) return `单元「${selectedUnit.value.title}」`;
  if (selectedSectionCode.value) {
    const section = sectionByCode.value.get(selectedSectionCode.value);
    return section
      ? sectionCrumb(selectedSectionCode.value) || section.title
      : selectedSectionCode.value;
  }
  return courseTitle.value || "全部课程";
});
const scopeEmptyLabel = computed(() => {
  if (selectedUnitCode.value) return "单元";
  if (!selectedSectionCode.value) return "课程";
  const section = sectionByCode.value.get(selectedSectionCode.value);
  return section?.parentSectionCode ? "小节" : "章";
});
const scopeEmpty = computed(
  () => !loading.value && scopeBaseNodes.value.length === 0
);

const focusChapterName = computed(() => chapterOf(focusName.value));
const focusChapter = computed(
  () =>
    chapterProgress.value.find((ch) => ch.name === focusChapterName.value) ||
    null
);
const otherChapters = computed(() =>
  chapterProgress.value.filter((ch) => ch.name !== focusChapterName.value)
);

// ============ 关系归一化与关联节点分组（R10：存储 A dependent→ B = A 依赖 B） ============
const normalizeRelationType = (link: any): string => {
  const raw = String(link?.relationType ?? link?.value ?? "")
    .trim()
    .toLowerCase();
  return (
    (
      {
        requires: "dependent",
        contains: "contain",
        related_to: "related",
        implemente: "implements",
        contrasts_with: "contrasts",
        based_on: "based",
      } as Record<string, string>
    )[raw] || raw
  );
};

const STATUS_TAG_CLASS: Record<string, string> = {
  GREEN: "mastered",
  YELLOW: "review",
  RED: "weak",
  GRAY: "unseen",
};
const statusTagClass = (status: string) => STATUS_TAG_CLASS[status] || "unseen";
const dotClass = (status: string) => "dot-" + statusTagClass(status);

const STATUS_RANK: Record<string, number> = {
  GRAY: 0,
  RED: 1,
  YELLOW: 2,
  GREEN: 3,
};

const RELATION_TAG_TEXT: Record<string, string> = {
  contrasts: "对比",
  based: "依据",
  related: "相关",
  contain: "包含",
  implements: "实现",
};

interface RelatedItem {
  nodeKey: string;
  name: string;
  displayName: string;
  status: string;
  tags: string[];
}

/**
 * 关联节点分组：用当前完整图谱 nodes/links 在前端按 source/target+类型过滤。
 * dependent 存储方向 A→B 表示 A 依赖 B（B 是 A 的前置知识，先学 B）：
 *   前置知识 = 本节点作为 source 时的 target；后续知识 = 本节点作为 target 时的 source。
 * related/contain/implements 不产生学习顺序，归入「相关节点」并保留关系标签。
 * 条目携带 nodeKey：重名 displayName 不会互相覆盖，入口不再回退到名称查找。
 */
const relationGroups = computed(() => {
  const node = selectedNode.value;
  const empty = {
    prerequisites: [] as RelatedItem[],
    successors: [] as RelatedItem[],
    related: [] as RelatedItem[],
  };
  if (!node) return empty;
  const idToNode = new Map(
    nodes.value.map(
      (item) => [String(item.id), item] as [string, MasteryNodeVO]
    )
  );
  const itemOf = (target: MasteryNodeVO): RelatedItem => ({
    nodeKey: nodeKeyOf(target),
    name: target.name,
    displayName: target.displayName || target.name,
    status: target.status || "GRAY",
    tags: [],
  });
  const prereq = new Map<string, RelatedItem>();
  const succ = new Map<string, RelatedItem>();
  const rel = new Map<string, RelatedItem>();
  const selfId = String(node.id);
  for (const link of links.value) {
    const type = normalizeRelationType(link);
    const sId = String(link.source);
    const tId = String(link.target);
    const sNode = idToNode.get(sId);
    const tNode = idToNode.get(tId);
    if (!sNode || !tNode || sId === tId) continue;
    if (type === "dependent") {
      if (sId === selfId) prereq.set(nodeKeyOf(tNode), itemOf(tNode));
      if (tId === selfId) succ.set(nodeKeyOf(sNode), itemOf(sNode));
    } else if (RELATION_TAG_TEXT[type]) {
      const other = sId === selfId ? tNode : tId === selfId ? sNode : null;
      if (!other) continue;
      const key = nodeKeyOf(other);
      const entry = rel.get(key) || itemOf(other);
      if (!entry.tags.includes(RELATION_TAG_TEXT[type])) {
        entry.tags = [...entry.tags, RELATION_TAG_TEXT[type]];
      }
      rel.set(key, entry);
    }
  }
  const sorted = (items: Map<string, RelatedItem>) =>
    Array.from(items.values()).sort(
      (a, b) =>
        a.displayName.localeCompare(b.displayName, "zh") ||
        a.name.localeCompare(b.name, "zh")
    );
  return {
    prerequisites: sorted(prereq),
    successors: sorted(succ),
    related: sorted(rel),
  };
});

const VISIBLE_ANNOTATION_LIMIT = 12;

/**
 * 小图（<=12 可见节点）按可见顺序给全部节点编号，对齐设计稿 ①–⑪；
 * 大图回退为选中节点 ③ + 两个最近关系 ①②，避免 185 节点全量编号。
 */
const buildVisibleAnnotations = (
  visibleNodes: Array<{
    id?: unknown;
    nodeCode?: string;
    stableId?: string;
    name?: string;
  }>,
  selectedKey: string,
  relationItems: Array<{ nodeKey: string }>,
  limit = VISIBLE_ANNOTATION_LIMIT
) => {
  const result: Record<string, number> = {};
  if (visibleNodes.length > 0 && visibleNodes.length <= limit) {
    if (selectedKey) result[selectedKey] = 3;
    let number = 1;
    visibleNodes.forEach((node) => {
      const key = nodeKeyOf(node);
      if (key === selectedKey) return;
      if (number === 3) number = 4;
      result[key] = number;
      number += 1;
    });
    return result;
  }
  if (!selectedKey) return result;
  result[selectedKey] = 3;
  const seen = new Set([selectedKey]);
  relationItems
    .filter((item) => {
      if (!item.nodeKey || seen.has(item.nodeKey)) return false;
      seen.add(item.nodeKey);
      return true;
    })
    .slice(0, 2)
    .forEach((item, index) => {
      result[item.nodeKey] = index + 1;
    });
  return result;
};

const graphAnnotations = computed<Record<string, number>>(() =>
  buildVisibleAnnotations(canvasGraph.value.nodes, selectedNodeKey.value, [
    ...relationGroups.value.prerequisites,
    ...relationGroups.value.successors,
    ...relationGroups.value.related,
  ])
);

const allVisibleNumbered = computed(() => {
  const count = canvasGraph.value.nodes.length;
  return count > 0 && count <= VISIBLE_ANNOTATION_LIMIT;
});

const neighborAnnotationCount = computed(() =>
  Math.max(0, Object.keys(graphAnnotations.value).length - 1)
);

const selectedAnnotationNumber = computed(
  () => graphAnnotations.value[selectedNodeKey.value] || 3
);

const selectedRelationCount = computed(
  () =>
    relationGroups.value.prerequisites.length +
    relationGroups.value.successors.length +
    relationGroups.value.related.length
);

const selectedTypeLabel = computed(() =>
  selectedNode.value ? nodeTypeLabel(selectedNode.value) : ""
);

const selectedShortCrumb = computed(() => {
  const parts = selectedCrumb.value
    .split(" / ")
    .map((part) => part.trim())
    .filter(Boolean);
  const section =
    parts.find((part) => /^\d+\.\d+/.test(part)) ||
    parts[parts.length - 1] ||
    "";
  return (
    section
      .replace(/操作系统的?/g, "")
      .replace(/\s+/g, " ")
      .trim() || section
  );
});

const allRelatedRelations = computed(() => [
  ...relationGroups.value.prerequisites,
  ...relationGroups.value.successors,
  ...relationGroups.value.related,
]);

const selectedAnnotationText = computed(() => {
  if (!selectedNode.value) return "";
  const relationText = allVisibleNumbered.value
    ? "编号对应当前可见节点；"
    : neighborAnnotationCount.value === 2
    ? "①② 为最近一跳关系；"
    : neighborAnnotationCount.value === 1
    ? "① 为最近一跳关系；"
    : "";
  return `当前节点；${relationText}${selectedTypeLabel.value} · ${selectedCrumb.value} · ${selectedRelationCount.value} 条关系。先读定义，再进入节点工作台。`;
});

// ============ 学习证据（按需来自 nodeWorkbench，复用缓存） ============
// log3 收尾 C：图谱详情不再渲染题目/实验入口，相关入口只出现在
// 节点学习工作台的"题目练习 / 实践"页签。
const questionStatsLine = computed(() => {
  const stats = selectedWorkbench.value?.questionStats;
  if (!stats || !stats.total) return "";
  return `本节点题目 ${stats.total} · 已作答 ${stats.attempted}`;
});

const selectedQuestionStats = computed(
  () => selectedWorkbench.value?.questionStats || null
);

const lastFailure = computed(
  () => selectedWorkbench.value?.lastFailure || null
);

// ============ 数据加载 ============
let graphRequestSequence = 0;
const loadGraphData = async () => {
  const request = ++graphRequestSequence;
  const requestCourse = courseCode.value;
  loading.value = true;
  graphError.value = "";
  // 状态脉冲：对比刷新前后 status 变化的节点（进步蓝 / 退步琥珀）
  const prevStatus = new Map(
    nodes.value.map(
      (n) => [nodeKeyOf(n), n.status || "GRAY"] as [string, string]
    )
  );
  let handled = false;
  try {
    if (!isTeaching.value && requestCourse && isLoggedIn()) {
      try {
        const res = await personalGraph(requestCourse);
        if (
          request !== graphRequestSequence ||
          requestCourse !== courseCode.value
        )
          return;
        if (res.data?.code === 0 && res.data.data) {
          nodes.value = res.data.data.nodes || [];
          links.value = res.data.data.links || [];
          categories.value = res.data.data.categories || [];
          personalGraphMeta.value = {
            totalNodes: res.data.data.totalNodes ?? nodes.value.length,
            totalLinks: res.data.data.totalLinks ?? links.value.length,
            recommendedNodeName: res.data.data.recommendedNodeName || "",
          };
          handled = true;
        }
      } catch (e) {
        console.warn("个人图谱加载失败", e);
      }
    }
    if (!handled && isTeaching.value) {
      const res =
        await KnowledgeGraphControllerService.getAllKnowledgeGraphDataUsingGet();
      if (res.code === 0 && res.data) {
        nodes.value = (res.data.nodes || []) as MasteryNodeVO[];
        links.value = res.data.links || [];
        categories.value = res.data.categories || [];
        personalGraphMeta.value = null;
        handled = true;
      } else {
        graphError.value = res.message || "加载知识图谱失败";
        Message.error("加载知识图谱失败: " + (res.message || "未知错误"));
      }
    } else if (!handled) {
      nodes.value = [];
      links.value = [];
      categories.value = [];
      personalGraphMeta.value = null;
      graphError.value = requestCourse
        ? "加载当前课程图谱失败，请重试"
        : "暂无可用课程";
    }
  } catch (error) {
    graphError.value = "加载知识图谱失败，请检查网络或稍后重试";
    Message.error("加载知识图谱失败");
    console.error(error);
  } finally {
    if (request === graphRequestSequence) {
      loading.value = false;
      if (
        handled &&
        selectedNodeKey.value &&
        !nodeByKey.value.has(selectedNodeKey.value)
      ) {
        clearNodeSelection();
      }
      refreshPulse(prevStatus);
    }
  }
};

/** 状态变化节点 → 脉冲（进步蓝/退步琥珀，组件 1.2s 后自动清除） */
const refreshPulse = (prevStatus: Map<string, string>) => {
  const changed: Array<{ key: string; name: string; color: "blue" | "amber" }> =
    [];
  for (const node of nodes.value) {
    const key = nodeKeyOf(node);
    const before = prevStatus.get(key);
    const after = node.status || "GRAY";
    if (before !== undefined && before !== after) {
      changed.push({
        key,
        name: node.name,
        color:
          (STATUS_RANK[after] ?? 0) > (STATUS_RANK[before] ?? 0)
            ? "blue"
            : "amber",
      });
    }
  }
  pulses.value = changed;
};

/** 清空节点选中（保留目录范围） */
function clearNodeSelection(resetPathFocus = true) {
  pathRequestSeq += 1;
  selectedNodeKey.value = "";
  selectedWorkbench.value = null;
  workbenchLoading.value = false;
  pathData.value = null;
  if (resetPathFocus) pathFocus.value = false;
}

/** 目录范围内默认选中：后端推荐优先，否则首个可见节点 */
const ensureVisibleSelection = () => {
  const visible = filteredNodes.value;
  if (!visible.length) {
    clearNodeSelection(false);
    return;
  }
  if (
    selectedNodeKey.value &&
    visible.some((node) => nodeKeyOf(node) === selectedNodeKey.value)
  ) {
    return;
  }
  const recommended = nodeByName.value.get(graphMeta.value.recommendedNodeName);
  const hit =
    recommended &&
    visible.some((node) => nodeKeyOf(node) === nodeKeyOf(recommended))
      ? recommended
      : visible[0];
  selectAndFocus(nodeKeyOf(hit), false);
};

/** 教学维护视角：全量图谱默认选中最薄弱节点 */
const ensureSelection = () => {
  if (!selectedNodeKey.value || !nodeByKey.value.has(selectedNodeKey.value)) {
    const target = nodeByName.value.get(defaultFocusName.value);
    if (target) selectAndFocus(nodeKeyOf(target), false);
  }
};

/** 节点工作台按需获取：先查缓存（30s TTL / 用户隔离），未命中再请求 */
const loadSelectedWorkbench = async (nodeName: string) => {
  if (!nodeName) return;
  const requestNode = nodeName;
  const cached = peekNodeWorkbench(workbenchUserId.value, requestNode);
  if (cached) {
    if (selectedName.value === requestNode) {
      selectedWorkbench.value = cached;
    }
    return;
  }
  if (selectedName.value === requestNode) {
    selectedWorkbench.value = null;
  }
  if (!isLoggedIn()) return;
  workbenchLoading.value = true;
  try {
    const data = await loadNodeWorkbench(workbenchUserId.value, requestNode);
    // 切换节点守卫：旧请求晚到不覆盖新节点详情
    if (selectedName.value === requestNode) {
      selectedWorkbench.value = data;
    }
  } catch (e) {
    // 工作台聚合失败不阻塞详情展示
    if (selectedName.value === requestNode) {
      selectedWorkbench.value = null;
    }
  } finally {
    if (selectedName.value === requestNode) {
      workbenchLoading.value = false;
    }
  }
};

/** 后端补救学习路径（教学维护视角不加载个人路径） */
let pathRequestSeq = 0;
const loadPath = async (nodeName: string) => {
  if (!nodeName || isTeaching.value || !courseCode.value) {
    pathRequestSeq += 1;
    pathData.value = null;
    return;
  }
  const seq = ++pathRequestSeq;
  pathData.value = null;
  try {
    const res = await masteryPath(nodeName, courseCode.value);
    if (seq === pathRequestSeq) {
      pathData.value = res.data?.code === 0 ? res.data.data : null;
    }
  } catch {
    if (seq === pathRequestSeq) pathData.value = null;
  }
};

watch(pathFocus, (enabled) => {
  const target = selectedNode.value?.name || defaultFocusName.value;
  if (enabled && target && !pathData.value) {
    loadPath(target);
  }
});

/** 节点选中：接受 nodeKey 或兼容 name；不改变左侧目录范围 */
const selectAndFocus = (keyOrName: unknown, syncQuery = true) => {
  if (typeof keyOrName !== "string") return;
  const node =
    nodeByKey.value.get(keyOrName) || nodeByName.value.get(keyOrName) || null;
  if (!node) return;
  selectedNodeKey.value = nodeKeyOf(node);
  selectedWorkbench.value = null;
  loadSelectedWorkbench(node.name);
  loadPath(node.name);
  if (syncQuery) syncRouteQuery();
};

const onJumpNode = (key: unknown) => {
  if (typeof key !== "string" || !key) return;
  selectAndFocus(key);
  jumpNodeKey.value = "";
};

const handleNodeClick = (node?: GraphNodeKeyLike | null) => {
  const key = nodeKeyOf(node);
  if (!key) return;
  selectAndFocus(key);
};

// 双击进入节点学习工作台（稳定 nodeKey 优先，旧节点回退名称）
const handleNodeDblClick = (node?: GraphNodeKeyLike | null) => {
  const key = nodeKeyOf(node);
  if (key) enterNodeWorkspace(key);
};

const searchFocus = () => {
  const keyword = searchText.value.trim().toLowerCase();
  if (!keyword) return;
  const hit = filteredNodes.value.find((node) =>
    `${node.displayName || ""} ${node.name || ""}`
      .toLowerCase()
      .includes(keyword)
  );
  if (hit) {
    selectAndFocus(nodeKeyOf(hit));
  } else {
    Message.info("当前范围内未找到匹配的知识节点");
  }
};

/** 清除搜索/状态筛选（目录范围保持不变） */
const resetViewFilters = () => {
  searchText.value = "";
  statusFilter.value = "";
  pathFocus.value = false;
};

// ============ 目录选择与范围联动 ============
const queryText = (value: unknown) =>
  (Array.isArray(value)
    ? String(value[0] ?? "")
    : typeof value === "string"
    ? value
    : ""
  ).trim();

const loadCatalog = async () => {
  await loadCatalogSource(courseCode.value || undefined);
  await applyRouteQuery();
};

const expandSectionPath = (sectionCode?: string) => {
  if (!sectionCode) return;
  const merged = new Set([
    ...expandedSections.value,
    ...ancestorCodes(sectionCode),
  ]);
  expandedSections.value = Array.from(merged);
};

const toggleSection = (sectionCode: string) => {
  const next = new Set(expandedSections.value);
  if (next.has(sectionCode)) next.delete(sectionCode);
  else next.add(sectionCode);
  expandedSections.value = Array.from(next);
};

const resetUnitPreview = () => {
  unitPreviewEpoch += 1;
  unitPreview.value = null;
  unitPreviewLoading.value = false;
  unitPreviewError.value = "";
};

const loadUnitPreview = async (unitCode: string) => {
  const epoch = ++unitPreviewEpoch;
  unitPreview.value = null;
  unitPreviewError.value = "";
  unitPreviewLoading.value = true;
  try {
    const data = await getCourseUnit(unitCode);
    if (epoch !== unitPreviewEpoch || selectedUnitCode.value !== unitCode)
      return;
    unitPreview.value = data;
  } catch (error) {
    if (epoch === unitPreviewEpoch && selectedUnitCode.value === unitCode) {
      unitPreviewError.value =
        error instanceof Error ? error.message : "单元信息加载失败";
    }
  } finally {
    if (epoch === unitPreviewEpoch && selectedUnitCode.value === unitCode) {
      unitPreviewLoading.value = false;
    }
  }
};

const selectUnitScope = (unitCode: string, syncQuery = true) => {
  const unit = unitByCode.value.get(unitCode);
  if (!unit) return;
  selectedUnitCode.value = unitCode;
  selectedSectionCode.value = unit.sectionCode;
  resetUnitPreview();
  clearNodeSelection();
  expandSectionPath(unit.sectionCode);
  void loadUnitPreview(unitCode);
  if (syncQuery) syncRouteQuery();
};

const selectSectionScope = (sectionCode: string, syncQuery = true) => {
  if (!sectionByCode.value.has(sectionCode)) return;
  selectedSectionCode.value = sectionCode;
  selectedUnitCode.value = "";
  resetUnitPreview();
  clearNodeSelection();
  expandSectionPath(sectionCode);
  ensureVisibleSelection();
  if (syncQuery) syncRouteQuery();
};

const selectCourseScope = (syncQuery = true) => {
  selectedSectionCode.value = "";
  selectedUnitCode.value = "";
  resetUnitPreview();
  clearNodeSelection();
  ensureVisibleSelection();
  if (syncQuery) syncRouteQuery();
};

/** 侧栏课程头：切换课程并回到该课程总览，会话作用域同步跟随 */
const handleCatalogCourse = async (targetCourse: string) => {
  if (targetCourse && targetCourse !== courseCode.value) {
    await loadCatalogSource(targetCourse);
    if (targetCourse !== courseCode.value) return;
    void syncAiCourse(targetCourse);
    selectedSectionCode.value = "";
    selectedUnitCode.value = "";
    resetUnitPreview();
    clearNodeSelection();
    ensureVisibleSelection();
    syncRouteQuery();
    return;
  }
  selectCourseScope();
};

/** 侧栏章节：目标章节可能属于另一门课程，先切课程再选中 */
const handleCatalogSection = async (sectionCode: string) => {
  const targetCourse = courseCodeOfSection(sectionCode);
  if (targetCourse && targetCourse !== courseCode.value) {
    await loadCatalogSource(targetCourse);
    if (targetCourse !== courseCode.value) return;
    void syncAiCourse(targetCourse);
  }
  selectSectionScope(sectionCode);
};

/** 侧栏单元：目标单元可能属于另一门课程，先切课程再选中 */
const handleCatalogUnit = async (unitCode: string) => {
  const targetCourse = courseCodeOfUnit(unitCode);
  if (targetCourse && targetCourse !== courseCode.value) {
    await loadCatalogSource(targetCourse);
    if (targetCourse !== courseCode.value) return;
    void syncAiCourse(targetCourse);
  }
  selectUnitScope(unitCode);
};

/** 单元映射入口：先解析为当前快照的稳定 nodeKey（含旧节点名称回退） */
const selectUnitNode = (mapping: UnitNodeMapping) => {
  const key = mappedNodeKey(mapping);
  if (key) selectAndFocus(key);
};

// ============ 路由同步（稳定代码优先，旧 nodeName 兼容） ============
let applyingRouteQuery = 0;
let applyRouteEpoch = 0;

const stateQuery = () => {
  const query: Record<string, string> = {};
  if (courseCode.value) query.courseCode = courseCode.value;
  if (selectedSectionCode.value) query.sectionCode = selectedSectionCode.value;
  if (selectedUnitCode.value) query.unitCode = selectedUnitCode.value;
  else if (!selectedSectionCode.value && courseCode.value) {
    query.scope = "course";
  }
  if (selectedNodeKey.value) {
    const node = nodeByKey.value.get(selectedNodeKey.value);
    if (node?.nodeCode) query.nodeCode = node.nodeCode;
    else if (node?.name) query.nodeName = node.name;
  }
  return query;
};

const syncRouteQuery = (force = false) => {
  if (isTeaching.value || route.path !== "/graph") return;
  if (!force && applyingRouteQuery > 0) return;
  const target = stateQuery();
  const keys = [
    "courseCode",
    "sectionCode",
    "unitCode",
    "nodeCode",
    "nodeName",
    "scope",
  ];
  const same = keys.every(
    (key) => queryText(route.query[key]) === (target[key] || "")
  );
  if (same) return;
  void router.replace({ query: target });
};

const applyRouteQuery = async () => {
  if (isTeaching.value || route.path !== "/graph") return;
  const epoch = ++applyRouteEpoch;
  applyingRouteQuery += 1;
  try {
    const requestedCourse = queryText(route.query.courseCode);
    if (requestedCourse && requestedCourse !== courseCode.value) {
      await loadCatalogSource(requestedCourse);
    }
    // 目录加载期间 query 又变了：交给最后一次调用处理
    if (epoch !== applyRouteEpoch) return;
    const requestedUnit = queryText(route.query.unitCode);
    const requestedSection = queryText(route.query.sectionCode);
    const requestedNode =
      queryText(route.query.nodeCode) || queryText(route.query.nodeName);
    const courseScope = queryText(route.query.scope) === "course";

    if (requestedUnit && unitByCode.value.has(requestedUnit)) {
      if (selectedUnitCode.value !== requestedUnit) {
        selectUnitScope(requestedUnit, false);
      }
    } else if (requestedSection && sectionByCode.value.has(requestedSection)) {
      if (
        selectedUnitCode.value ||
        selectedSectionCode.value !== requestedSection
      ) {
        selectSectionScope(requestedSection, false);
      }
    } else if (courseScope) {
      if (selectedUnitCode.value || selectedSectionCode.value) {
        selectCourseScope(false);
      } else if (!selectedNodeKey.value) {
        ensureVisibleSelection();
      }
    } else if (requestedNode) {
      // 旧节点深链只带节点：保持全课程范围，不强制跳到续学单元
      if (selectedUnitCode.value || selectedSectionCode.value) {
        selectCourseScope(false);
      }
    } else {
      const resumeUnit = courseResume.value?.unitCode;
      const fallbackUnit = catalogUnits.value[0]?.unitCode || "";
      const targetUnit =
        resumeUnit && unitByCode.value.has(resumeUnit)
          ? resumeUnit
          : fallbackUnit;
      if (targetUnit) {
        if (selectedUnitCode.value !== targetUnit) {
          selectUnitScope(targetUnit, false);
        }
      } else if (selectedUnitCode.value || selectedSectionCode.value) {
        selectCourseScope(false);
      } else if (!selectedNodeKey.value) {
        ensureVisibleSelection();
      }
    }

    if (requestedNode) {
      const node =
        nodeByKey.value.get(requestedNode) ||
        nodeByName.value.get(requestedNode);
      if (node && nodeKeyOf(node) !== selectedNodeKey.value) {
        selectAndFocus(nodeKeyOf(node), false);
      }
    }
    syncRouteQuery(true);
  } finally {
    applyingRouteQuery = Math.max(0, applyingRouteQuery - 1);
  }
};

// ============ 右侧操作 ============
const openSelectedUnit = () => {
  const unit = selectedUnit.value;
  if (!unit || !courseCode.value) return;
  router.push({
    path: unitPath(courseCode.value, unit.unitCode),
    query: { returnTo: route.fullPath },
  });
};

const backToUnitOverview = () => {
  clearNodeSelection();
  syncRouteQuery();
};

const nodeWorkspaceTarget = (node: MasteryNodeVO) =>
  node.nodeCode
    ? `/knowledge/concept/${encodeURIComponent(node.nodeCode)}`
    : `/knowledge/node/${encodeURIComponent(node.name)}`;

/** 节点上下文：稳定 nodeCode + 所属小节 + 当前单元（仅当单元映射了该节点） */
const nodeContextQuery = (node: MasteryNodeVO) => {
  const query: Record<string, string> = {};
  if (courseCode.value) query.courseCode = courseCode.value;
  if (node.nodeCode) query.nodeCode = node.nodeCode;
  const sectionCode = node.homeSectionCode || selectedSectionCode.value;
  if (sectionCode) query.sectionCode = sectionCode;
  if (
    selectedUnitCode.value &&
    selectedUnitMappings.value.some(
      (mapping) => mappedNodeKey(mapping) === nodeKeyOf(node)
    )
  ) {
    query.unitCode = selectedUnitCode.value;
  }
  return query;
};

/** 返回图谱：当前范围/筛选 + 本次进入的节点，避免回到旧选中节点的 query */
const graphReturnPath = (node: MasteryNodeVO) => {
  const query: Record<string, string | string[]> = {};
  for (const [key, value] of Object.entries(route.query)) {
    if (value !== null && value !== undefined) query[key] = value;
  }
  Object.assign(query, nodeContextQuery(node));
  if (node.nodeCode) {
    delete query.nodeName;
  } else {
    delete query.nodeCode;
    if (node.name) query.nodeName = node.name;
  }
  return router.resolve({ path: route.path, query }).fullPath;
};

// 模板必须显式无参调用；传入事件对象时直接拒绝，绝不能把 MouseEvent 当节点键。
const enterNodeWorkspace = (...args: unknown[]) => {
  const [keyOrName] = args;
  if (
    args.length > 0 &&
    keyOrName !== undefined &&
    typeof keyOrName !== "string"
  ) {
    return;
  }
  const key = typeof keyOrName === "string" ? keyOrName.trim() : "";
  const node = key
    ? nodeByKey.value.get(key) || nodeByName.value.get(key)
    : selectedNode.value;
  if (!node) return;
  if (!isNodeVisibleInCanvas(node)) return;
  router.push({
    path: nodeWorkspaceTarget(node),
    query: { ...nodeContextQuery(node), returnTo: graphReturnPath(node) },
  });
};

const openNodeQuestions = () => {
  const node = selectedNode.value;
  if (!node) return;
  router.push({
    path: "/assessments",
    query: {
      ...nodeContextQuery(node),
      nodeName: node.name,
      returnTo: graphReturnPath(node),
    },
  });
};

// ============ 工具 ============
const percentText = (value: number | null | undefined) =>
  value === null || value === undefined ? "-" : `${Math.round(value * 100)}%`;

const NODE_TYPE_LABELS: Record<string, string> = {
  CONCEPT: "概念",
  ALGORITHM: "算法",
  MECHANISM: "机制",
  IMPLEMENTATION: "实现",
};
const nodeTypeLabel = (
  node?: Pick<MasteryNodeVO, "nodeType" | "category"> | null
) =>
  NODE_TYPE_LABELS[String(node?.nodeType || "").toUpperCase()] ||
  node?.category ||
  "未标注";

const statusText = (status: string) => {
  const map: Record<string, string> = {
    GRAY: "未学习",
    RED: "薄弱",
    YELLOW: "待巩固",
    GREEN: "已掌握",
  };
  return map[status] || status;
};

const resultText = (result: string) => {
  const map: Record<string, string> = {
    PASS: "答对",
    FAIL: "答错",
    SYSTEM_ERROR: "系统错误",
  };
  return map[result] || result;
};

const evidenceTag = (event: any) => {
  try {
    const parsed = JSON.parse(event.evidence || "{}");
    const sourceMap: Record<string, string> = {
      // 键为后端 evidence.source 原值（数据），仅显示值改名
      普通题作答: "题目",
      编程实验判题: "实验",
    };
    return sourceMap[parsed.source] || parsed.source || "学习事件";
  } catch {
    return "学习事件";
  }
};

const evidenceTitle = (event: any) => {
  try {
    const parsed = JSON.parse(event.evidence || "{}");
    return parsed.title || parsed.resultText || "学习事件";
  } catch {
    return "学习事件";
  }
};

const relativeTime = (time: string) => {
  if (!time) return "";
  const diff = Date.now() - new Date(time).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  return new Date(time).toLocaleDateString();
};

const latestEvent = computed(
  () => selectedWorkbench.value?.events?.[0] || null
);

const primaryRecommendation = computed(() => {
  const rec = selectedWorkbench.value?.recommendations?.[0];
  if (!rec) return null;
  const actions: Record<string, string> = {
    WEAK_NODE: "先完成该节点的基础题练习。",
    PRACTICE: "做几道变式题巩固一下。",
    PREREQUISITE: `先学习前置知识「${rec.nodeName}」。`,
  };
  return {
    action: actions[rec.type] || "继续针对性练习。",
    reason: rec.reason,
  };
});

// 学习数据变化（答题/实验/模拟）后刷新当前节点的证据区（缓存已被 composable 失效）
const handleLearningChanged = () => {
  if (selectedName.value) {
    loadSelectedWorkbench(selectedName.value);
  }
};

// ============ 管理员功能（5 个管理 Modal 功能全部保留） ============
const addRelationVisible = ref(false);
const showRelationModal = () => {
  addRelationVisible.value = true;
};
const relationForm = ref({ fromName: "", toName: "", relationType: "" });
const addRelation = async () => {
  if (
    !relationForm.value.fromName.trim() ||
    !relationForm.value.toName.trim() ||
    !relationForm.value.relationType
  ) {
    Message.warning("请完整填写关系信息");
    return;
  }
  try {
    const res =
      await KnowledgeGraphControllerService.addKnowledgeGraphRelationUsingPost(
        relationForm.value
      );
    if (res.code === 0) {
      Message.success("关系添加成功");
      addRelationVisible.value = false;
      relationForm.value = { fromName: "", toName: "", relationType: "" };
      await loadGraphData();
    } else {
      Message.error(`关系添加失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("关系添加失败，请稍后重试");
  }
};

const addNodeVisible = ref(false);
const nodeSubmitting = ref(false);
const nodeForm = ref({ name: "", chapter: "", category: "", description: "" });
const showAddNodeModal = () => {
  nodeForm.value = {
    name: "",
    chapter: selectedNode.value ? chapterLabel(selectedNode.value) : "",
    category: "",
    description: "",
  };
  addNodeVisible.value = true;
};
const addNode = async (): Promise<boolean> => {
  if (!isTeaching.value || !isAdmin.value || nodeSubmitting.value) return false;
  if (!nodeForm.value.name?.trim()) {
    Message.warning("请输入节点名称");
    return false;
  }
  if (!nodeForm.value.chapter.trim()) {
    Message.warning("请选择或输入所属章节");
    return false;
  }
  if (nodeForm.value.chapter.trim().length > 100) {
    Message.warning("所属章节最多 100 个字符");
    return false;
  }
  nodeSubmitting.value = true;
  try {
    const res =
      await KnowledgeGraphControllerService.addKnowledgeGraphNodeUsingPost({
        ...nodeForm.value,
        name: nodeForm.value.name.trim(),
        chapter: nodeForm.value.chapter.trim(),
        category: nodeForm.value.category?.trim(),
        description: nodeForm.value.description?.trim(),
      });
    if (res.code === 0) {
      Message.success("节点创建成功");
      addNodeVisible.value = false;
      window.dispatchEvent(new Event("oslab:learning-changed"));
      await loadGraphData();
      return true;
    } else {
      Message.error(`节点创建失败：${res.message || "未知错误"}`);
      return false;
    }
  } catch (error) {
    Message.error("节点创建失败，请稍后重试");
    return false;
  } finally {
    nodeSubmitting.value = false;
  }
};

const chapterModalVisible = ref(false);
const chapterSubmitting = ref(false);
const chapterForm = ref({ nodeName: "", chapter: "" });
const showChapterModal = () => {
  if (!isTeaching.value || !isAdmin.value || !selectedNode.value) return;
  chapterForm.value = {
    nodeName: selectedNode.value.name,
    chapter: chapterLabel(selectedNode.value),
  };
  chapterModalVisible.value = true;
};
const updateChapter = async (): Promise<boolean> => {
  if (!isTeaching.value || !isAdmin.value || chapterSubmitting.value)
    return false;
  const nodeName = chapterForm.value.nodeName;
  const chapter = chapterForm.value.chapter.trim();
  if (!nodeName || !chapter) {
    Message.warning("请选择或输入所属章节");
    return false;
  }
  if (chapter.length > 100) {
    Message.warning("所属章节最多 100 个字符");
    return false;
  }
  chapterSubmitting.value = true;
  try {
    const response =
      await KnowledgeGraphControllerService.updateNodeChapterUsingPost({
        nodeName,
        chapter,
      });
    if (response.code !== 0) {
      Message.error(response.message || "章节更新失败");
      return false;
    }
    chapterModalVisible.value = false;
    selectedWorkbench.value = null;
    window.dispatchEvent(new Event("oslab:learning-changed"));
    await loadGraphData();
    Message.success("章节已更新");
    return true;
  } catch {
    Message.error("章节更新失败，请稍后重试");
    return false;
  } finally {
    chapterSubmitting.value = false;
  }
};

const deleteSelectedNode = async () => {
  const nodeName = selectedNode.value?.name || "";
  if (!nodeName) {
    Message.warning("请先选择要删除的节点");
    return;
  }
  try {
    const res = await KnowledgeGraphControllerService.deleteByNameUsingPost({
      name: nodeName,
    });
    if (res.code === 0) {
      Message.success("节点已删除");
      clearNodeSelection();
      syncRouteQuery();
      await loadGraphData();
    } else {
      Message.error(`删除节点失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("删除节点失败，请稍后重试");
  }
};

const cypherVisible = ref(false);
const cypherSubmitting = ref(false);
const cypherQuery = ref("");
const showCypherModal = () => {
  cypherQuery.value = "";
  cypherVisible.value = true;
};
const executeCypher = async () => {
  const query = cypherQuery.value.trim();
  if (!query) {
    Message.warning("请输入 Cypher 语句");
    return;
  }
  cypherSubmitting.value = true;
  try {
    const res = await KnowledgeGraphControllerService.executeUsingPost({
      query,
    });
    if (res.code === 0) {
      Message.success("Cypher 语句执行成功");
      cypherVisible.value = false;
      await loadGraphData();
    } else {
      Message.error(`执行失败：${res.message || "未知错误"}`);
    }
  } catch (error) {
    Message.error("执行失败，请检查语句或稍后重试");
  } finally {
    cypherSubmitting.value = false;
  }
};

// 节点共享学习素材维护
const nodeContentVisible = ref(false);
const nodeContentSubmitting = ref(false);
const nodeContentForm = ref({
  contentMd: "",
  resourceLinks: "",
  status: "DRAFT" as "DRAFT" | "PUBLISHED" | "OFFLINE",
});

const openNodeContentModal = async () => {
  const nodeName = selectedName.value;
  if (!nodeName) {
    Message.warning("请先选择要维护素材的节点");
    return;
  }
  nodeContentForm.value = {
    contentMd: "",
    resourceLinks: "",
    status: "DRAFT",
  };
  try {
    const res = await getNodeContent(nodeName);
    if (res.data?.code === 0 && res.data.data) {
      nodeContentForm.value.contentMd = res.data.data.contentMd || "";
      nodeContentForm.value.resourceLinks = res.data.data.resourceLinks || "";
      nodeContentForm.value.status = res.data.data.status || "DRAFT";
    }
  } catch (e) {
    // 无素材记录时保持空表单
  }
  nodeContentVisible.value = true;
};

const saveNodeContentSubmit = async () => {
  const nodeName = selectedName.value;
  if (!nodeName) {
    Message.warning("请先选择节点");
    return;
  }
  const resourceLinks = nodeContentForm.value.resourceLinks.trim();
  if (resourceLinks) {
    try {
      JSON.parse(resourceLinks);
    } catch (e) {
      Message.error("资源链接必须是合法的 JSON 数组");
      return;
    }
  }
  nodeContentSubmitting.value = true;
  try {
    const res = await saveNodeContent({
      nodeName,
      contentMd: nodeContentForm.value.contentMd,
      resourceLinks: resourceLinks || null,
      status: nodeContentForm.value.status,
    });
    if (res.data?.code === 0) {
      Message.success("学习素材已保存");
      nodeContentVisible.value = false;
      // 选中节点的工作台可能正在展示，重新拉取以反映素材变化
      if (selectedName.value) {
        loadSelectedWorkbench(selectedName.value);
      }
    } else {
      Message.error(res.data?.message || "保存失败");
    }
  } catch (e) {
    Message.error("保存失败，请稍后重试");
  } finally {
    nodeContentSubmitting.value = false;
  }
};

onMounted(async () => {
  window.addEventListener("oslab:learning-changed", handleLearningChanged);
  const preferredCourse = queryText(route.query.courseCode) || undefined;
  if (isTeaching.value) await loadGraphData();
  else await loadCatalogSource(preferredCourse);
  if (isTeaching.value) {
    ensureSelection();
    return;
  }
  // 首次进入：query 优先；无 query 时定位 resume 单元，其次第一个已发布单元
  await applyRouteQuery();
});

watch(courseCode, () => {
  if (!isTeaching.value) {
    clearNodeSelection();
    void loadGraphData();
  }
});

watch(
  () => route.query,
  () => {
    if (route.path === "/graph" && applyingRouteQuery === 0) {
      void applyRouteQuery();
    }
  }
);

onBeforeUnmount(() => {
  graphRequestSequence += 1;
  pathRequestSeq += 1;
  window.removeEventListener("oslab:learning-changed", handleLearningChanged);
});
</script>

<style scoped>
.graph-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* ---- 左侧章节摘要（不是导航入口） ---- */
.course-side {
  width: 208px;
  flex-shrink: 0;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-3);
  position: sticky;
  top: 12px;
}
.course-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 6px 14px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.course-logo {
  width: 36px;
  height: 36px;
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.course-name {
  font-size: var(--oslab-font-body);
  font-weight: 600;
  color: var(--oslab-ink);
}
.course-term {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  margin-top: 2px;
}
.side-divider {
  border-top: 1px solid var(--oslab-line-soft);
  margin: 6px 0;
}
.side-section {
  padding: 8px 4px;
}
.side-title {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  margin-bottom: 10px;
}
.chapter-focus {
  background: var(--oslab-panel-soft);
  border-radius: var(--oslab-radius-md);
  padding: 10px;
  margin-bottom: 10px;
}
.chapter-focus-name {
  font-size: var(--oslab-font-body);
  font-weight: 700;
  color: var(--oslab-ink);
  margin-bottom: 8px;
}
.chapter-focus-sub {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  margin-bottom: 6px;
}
.chapter-focus-pct {
  font-size: var(--oslab-font-body);
  font-weight: 700;
  color: var(--oslab-primary);
}
.chapter-focus-note {
  margin-top: 8px;
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
}
.chapter-line {
  display: flex;
  justify-content: space-between;
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
  padding: 4px 2px;
}
.chapter-line-pct {
  color: var(--oslab-muted-strong);
}
.status-count {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--oslab-ink-soft);
  padding: 4px 0;
}
.status-count .count {
  margin-left: auto;
  color: var(--oslab-ink);
}

/* 状态圆点：颜色 + 文字双信号（§10 可访问性） */
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
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

/* ---- 主区 ---- */
.graph-main {
  flex: 1;
  min-width: 0;
}
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 14px;
}
.page-title {
  margin: 0;
  font-size: var(--oslab-text-page);
  font-weight: 600;
  color: var(--oslab-ink);
  line-height: 1.3;
}
.page-subtitle {
  margin: 6px 0 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-font-body);
}
.header-meta {
  color: var(--oslab-muted-strong);
  font-size: 13px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar-spacer {
  flex: 1;
}
.text-tabs {
  display: flex;
  align-items: center;
}
.text-tab {
  font-size: var(--oslab-font-body);
  color: var(--oslab-ink-soft);
  cursor: pointer;
  padding: 4px 2px 8px;
  position: relative;
}
.text-tab.active {
  color: var(--oslab-primary);
  font-weight: 600;
}
.text-tab.active::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--oslab-primary);
}
.tab-divider {
  width: 1px;
  height: 14px;
  background: var(--oslab-line);
  margin: 0 12px;
}

/* log3 收尾 A.3：三块职责 —— 画布 minmax(0,1fr) 吃满剩余宽度，
   详情栏定宽 340px；无内层 max-width，宽度由布局层 wide 容器供给 */
.content-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}
.graph-col {
  min-width: 0;
}
.detail-col {
  min-width: 0;
}

/* ---- 图谱卡片 ---- */
.graph-card {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-4);
}
.graph-card-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--oslab-ink);
}
.head-meta {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
}
.label-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
}
.canvas-wrap {
  min-height: 200px;
}
.canvas-state {
  height: 480px;
}
.graph-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  align-items: center;
  margin-top: 8px;
  padding: 10px 4px 0;
  border-top: 1px solid var(--oslab-line-soft);
}
.legend-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.legend-caption {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
}
.legend-hint {
  margin-left: auto;
}
.swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  border: 1px solid var(--oslab-graph-node-border);
}
.swatch-concept {
  background: var(--oslab-graph-category-concept);
}
.swatch-algorithm {
  background: var(--oslab-graph-category-algorithm);
}
.swatch-function {
  background: var(--oslab-graph-category-function);
}
.swatch-default {
  background: var(--oslab-graph-category-default);
}
.legend-status {
  font-weight: 600;
}
.legend-status-mastered {
  color: var(--oslab-mastered-text);
}
.legend-status-review {
  color: var(--oslab-review-text);
}
.legend-status-weak {
  color: var(--oslab-weak-text);
}
.legend-status-unseen {
  color: var(--oslab-unseen-text);
}
.rel-line {
  display: inline-block;
  width: 20px;
  height: 0;
  vertical-align: middle;
}
.rel-dependent {
  border-top: 2px solid var(--oslab-graph-relation-dependent);
}
.rel-contain {
  border-top: 2px dashed var(--oslab-graph-relation-contain);
}
.rel-related {
  border-top: 2px dotted var(--oslab-graph-relation-related);
}
.rel-implements {
  border-top: 2px dashed var(--oslab-graph-relation-implements);
}
.rel-path {
  border-top: 2px solid var(--oslab-graph-path-edge);
}

/* ---- 右栏 ---- */
.detail-card {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-4);
}
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.node-name {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: var(--oslab-ink);
  line-height: 1.35;
}
.node-crumb {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  margin-bottom: 12px;
}
.node-description {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
  white-space: pre-wrap;
  border-bottom: 1px solid var(--oslab-line-soft);
  padding-bottom: 12px;
  margin: 0 0 4px;
}
.detail-empty {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-font-body);
  border-bottom: 1px solid var(--oslab-line-soft);
  padding-bottom: 12px;
  margin: 0 0 4px;
}
.detail-state {
  padding-top: var(--oslab-space-12);
}
.section-title {
  margin: 16px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--oslab-muted-strong);
}
.score-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.score-label {
  width: 60px;
  font-size: 13px;
  color: var(--oslab-ink);
  flex-shrink: 0;
}
.score-pct {
  width: 42px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
}
.score-pct.blue {
  color: var(--oslab-primary);
}
.score-pct.green {
  color: var(--oslab-mastered-text);
}
.score-sub {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
}
.evidence-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.evidence-card {
  border-radius: var(--oslab-radius-md);
  padding: 10px 12px;
  margin-bottom: 8px;
}
.evidence-fail {
  background: var(--oslab-weak-soft);
  border: 1px solid #e5cbce;
}
.evidence-pass {
  background: var(--oslab-mastered-soft);
  border: 1px solid #cadbd5;
}
.evidence-title {
  font-size: 13px;
  color: var(--oslab-ink);
  line-height: 1.6;
}
.evidence-meta {
  margin-top: 4px;
  font-size: var(--oslab-text-small);
  color: var(--oslab-weak-text);
  text-align: right;
}
.evidence-pass .evidence-meta {
  color: var(--oslab-mastered-text);
}
.evidence-action {
  margin-top: 6px;
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
  line-height: 1.6;
}
.rel-group {
  margin-bottom: 10px;
}
.rel-group-caption {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  margin-bottom: 6px;
}
.rel-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  background: var(--oslab-panel-soft);
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  padding: 6px 10px;
  margin-bottom: 6px;
  cursor: pointer;
  font-size: 13px;
  font-family: var(--oslab-font-ui);
  color: var(--oslab-ink-soft);
  transition: background var(--oslab-duration-fast) var(--oslab-ease),
    border-color var(--oslab-duration-fast) var(--oslab-ease);
}
.rel-node:hover {
  background: var(--oslab-primary-soft);
  border-color: var(--oslab-primary);
}
.rel-node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rel-tag {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 16px;
  padding: 0 6px;
  border-radius: var(--oslab-radius-pill);
  border: 1px solid var(--oslab-line);
  color: var(--oslab-muted-strong);
  background: var(--oslab-panel);
}
.cta-main {
  margin-top: 16px;
}
.recommend-action {
  font-size: 13px;
  font-weight: 600;
  color: var(--oslab-ink);
  margin-bottom: 4px;
}
.recommend-reason {
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  line-height: 1.6;
}
.graph-ai-plan {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--oslab-line-soft);
}
.graph-ai-plan .section-title {
  margin-top: 0;
}
.graph-ai-reason {
  margin: 0 0 6px;
  color: var(--oslab-review-text);
  font-size: var(--oslab-text-small);
  line-height: 1.55;
  text-wrap: pretty;
}
.graph-ai-ask {
  margin-top: 8px;
}
.path-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 8px;
  border: 0;
  border-bottom: 1px dashed var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.path-item:hover {
  background: var(--oslab-panel-soft);
}
.path-item.path-target {
  background: var(--oslab-primary-soft);
  border-radius: var(--oslab-radius-md);
}
.path-order {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}
.path-target .path-order {
  background: var(--oslab-primary);
  border-color: var(--oslab-primary);
  color: #fff;
}
.path-body {
  flex: 1;
  min-width: 0;
}
.path-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--oslab-ink);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.oslab-status-tag.mini {
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
}
.path-reason {
  margin-top: 2px;
  font-size: var(--oslab-text-small);
  color: var(--oslab-muted-strong);
  line-height: 1.6;
}
.admin-zone {
  margin-top: 16px;
  border-top: 1px solid var(--oslab-line-soft);
}
.side-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.6;
}

/* C+B 学习图册：课程学习页 */
.graph-page {
  gap: 16px;
}
.course-side {
  width: 264px;
  padding: 14px;
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.workspace-header {
  align-items: center;
  margin-bottom: 14px;
}
.page-title {
  font-family: var(--oslab-font-display);
  font-size: 30px;
}
.page-subtitle {
  color: var(--oslab-muted-strong);
}
.content-row {
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}
.graph-card {
  padding: 16px;
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.canvas-wrap {
  border-radius: var(--oslab-radius-lg);
  background: radial-gradient(rgba(32, 36, 43, 0.12) 1px, transparent 1.1px) 0 0 /
      20px 20px,
    var(--oslab-panel-soft);
  overflow: hidden;
}
.detail-card {
  padding: 16px;
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.detail-head .card-title {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--oslab-muted-strong);
}
.node-name {
  font-family: var(--oslab-font-display);
  font-size: 24px;
  line-height: 1.3;
}
.node-crumb {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  color: var(--oslab-muted-strong);
}
.node-description {
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--oslab-ink-soft);
}
.section-title {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--oslab-muted-strong);
}
.cta-main {
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-primary);
}
.graph-footer {
  border-top-color: var(--oslab-line-soft);
  font-size: 11.5px;
}
.legend-caption {
  font-family: var(--oslab-font-mono);
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.legend-item {
  gap: 5px;
}
.detail-empty {
  color: var(--oslab-muted-strong);
}
.mapping-chip {
  border-radius: var(--oslab-radius-control);
}

/* 2026-10-24 图谱截图对齐：卡片头/筛选行、③ 注记条、详情结构 */
.graph-card-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.graph-card-heading .kicker {
  display: block;
  margin-bottom: 2px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}
.graph-card-heading .card-title {
  margin: 0;
  font-family: var(--oslab-font-display);
  font-size: 20px;
}
.graph-card-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.graph-scope {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 0 12px;
  border-bottom: 1px solid var(--oslab-line-soft);
}
.graph-search {
  width: 260px;
}
.graph-search :deep(.arco-input-wrapper) {
  border-radius: var(--oslab-radius-control);
}
.graph-status {
  width: 126px;
}
.graph-stats {
  margin-left: auto;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.graph-stats b {
  color: var(--oslab-ink-soft);
}
.canvas-wrap {
  position: relative;
}
.graph-annotation-chip {
  position: absolute;
  left: 14px;
  top: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 7px;
  max-width: calc(100% - 28px);
  padding: 5px 10px 5px 6px;
  border: 1px solid var(--oslab-primary);
  border-radius: var(--oslab-radius-pill);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--oslab-shadow-card);
  pointer-events: none;
}
.graph-annotation-index {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.graph-annotation-copy {
  overflow: hidden;
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.graph-annotation-copy b {
  color: var(--oslab-ink);
  font-weight: 600;
}
.detail-definition {
  margin: 2px 0 10px;
  padding: 10px 12px;
  border-left: 2px solid var(--oslab-primary);
  border-radius: 0 var(--oslab-radius-md) var(--oslab-radius-md) 0;
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
.detail-definition-label {
  display: block;
  margin-bottom: 3px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}
.detail-definition--empty {
  color: var(--oslab-muted-strong);
}
.detail-card :deep(.annotation-note) {
  margin: 8px 0 12px;
}
.evidence-stats {
  display: flex;
  gap: 22px;
  padding: 4px 0 10px;
}
.evidence-stats div {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.evidence-stats b {
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 20px;
}
.evidence-stats span {
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.relation-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.relation-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
  font-size: 11px;
}
.relation-chip:hover {
  border-color: var(--oslab-primary);
}
.cta-secondary {
  width: 100%;
  min-height: 36px;
  margin-top: 8px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font-size: 13px;
  font-weight: 500;
}
.cta-secondary:hover {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
}
.rel-dependent,
.rel-contain,
.rel-related,
.rel-implements {
  border-top-color: var(--oslab-graph-edge-muted);
}
.rel-path {
  border-top-color: var(--oslab-graph-path-edge);
}

/* log3 收尾 A.3：1024px 以下纵向排列（详情栏排到画布下方），
   课程侧栏折叠隐藏；minmax(0,1fr) 保证不横向溢出 */
@media (max-width: 1024px) {
  .content-row {
    grid-template-columns: minmax(0, 1fr);
  }
  .course-side {
    display: none;
  }
}

@media (max-width: 768px) {
  .graph-page {
    flex-direction: column;
  }
  .canvas-state {
    height: 360px;
  }
  .legend-hint {
    margin-left: 0;
  }
}
</style>
