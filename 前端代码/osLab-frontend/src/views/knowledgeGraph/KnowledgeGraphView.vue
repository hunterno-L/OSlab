<template>
  <div class="graph-page">
    <!-- 左侧：章节摘要（学习覆盖/状态统计；设计 §5.1 —— 摘要不是导航入口，
         章节筛选统一走顶部工具栏） -->
    <aside class="course-side">
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
              stroke-color="#2059c9"
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
    <main class="graph-main">
      <div class="workspace-header">
        <div>
          <h2 class="page-title">{{ isTeaching ? "知识维护" : "知识图谱" }}</h2>
          <p class="page-subtitle">
            {{
              isTeaching
                ? "维护课程知识结构、节点资料与学习资源映射。"
                : "探索课程结构，沿着你的薄弱点规划下一步学习。"
            }}
          </p>
        </div>
        <div class="header-meta">
          <template v-if="focusChapterName">
            当前章节：{{ focusChapterName }}
          </template>
        </div>
      </div>

      <div class="toolbar">
        <div class="text-tabs">
          <a
            class="text-tab"
            :class="{ active: viewMode === 'full' }"
            @click="viewMode = 'full'"
          >
            课程结构
          </a>
          <span class="tab-divider"></span>
          <a
            class="text-tab"
            :class="{ active: viewMode === 'mine' }"
            @click="viewMode = 'mine'"
          >
            我的掌握
          </a>
        </div>
        <a-input-search
          v-model="searchText"
          placeholder="搜索知识点"
          allow-clear
          style="width: 190px"
          @search="searchFocus"
          @clear="searchText = ''"
        />
        <a-select
          v-model="chapterFilter"
          placeholder="全部章节"
          allow-clear
          style="width: 140px"
        >
          <a-option v-for="c in availableChapters" :key="c" :value="c">
            {{ c }}
          </a-option>
        </a-select>
        <a-select
          v-model="categoryFilter"
          placeholder="全部类型"
          allow-clear
          style="width: 130px"
        >
          <a-option v-for="c in availableCategories" :key="c" :value="c">
            {{ c }}
          </a-option>
        </a-select>
        <a-select
          v-model="statusFilter"
          placeholder="全部状态"
          allow-clear
          style="width: 120px"
        >
          <a-option value="GREEN">已掌握</a-option>
          <a-option value="YELLOW">待巩固</a-option>
          <a-option value="RED">薄弱</a-option>
          <a-option value="GRAY">未学习</a-option>
        </a-select>
        <div class="toolbar-spacer"></div>
        <div class="header-meta">
          {{ evidenceCount }}/{{ nodes.length }} 节点有学习记录
        </div>
      </div>

      <div class="content-row">
        <!-- 中央图谱卡片 -->
        <div class="graph-col">
          <div class="graph-card">
            <div class="graph-card-head">
              <span class="card-title">课程知识结构</span>
              <div class="toolbar-spacer"></div>
              <label class="label-toggle">
                <span>关系标签</span>
                <a-switch v-model="showRelationLabels" size="small" />
              </label>
              <a-tooltip
                content="学习路径属于「我的掌握」视图，请先切换页签"
                :disabled="viewMode !== 'full'"
              >
                <a-button
                  size="mini"
                  :type="pathFocus ? 'primary' : 'outline'"
                  :disabled="viewMode === 'full'"
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
              <span class="head-meta">
                课程共 {{ graphMeta.totalNodes }} 个节点 ·
                {{ graphMeta.totalLinks }} 条关系 · 当前可见
                {{ canvasGraph.nodes.length }} 个节点
              </span>
            </div>

            <a-spin :loading="loading" style="width: 100%; display: block">
              <div class="canvas-wrap">
                <KnowledgeGraph
                  v-if="canvasGraph.nodes.length"
                  ref="graphRef"
                  :nodes="canvasGraph.nodes"
                  :links="canvasLinks"
                  :categories="categories"
                  :selected-id="selectedNodeId"
                  :pulses="pulses"
                  :show-status="hasPersonalGraph"
                  :show-relation-labels="showRelationLabels"
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
                  <template v-if="nodes.length">
                    <span class="oslab-state-title"
                      >当前筛选条件下没有可见节点</span
                    >
                    <span class="oslab-state-hint"
                      >调整或清除筛选后即可恢复图谱</span
                    >
                    <a-button size="small" @click="clearFilters">
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
              <div class="legend-group">
                <span class="legend-caption">类别</span>
                <span class="legend-item">
                  <i class="swatch" style="background: #eaf0fd"></i>概念
                </span>
                <span class="legend-item">
                  <i class="swatch" style="background: #f6eddf"></i>算法
                </span>
                <span class="legend-item">
                  <i class="swatch" style="background: #e9f1ed"></i>功能
                </span>
                <span class="legend-item">
                  <i class="swatch" style="background: #f0f2f0"></i>未标注
                </span>
              </div>
              <div class="legend-group">
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
              <div v-if="hasPersonalGraph" class="legend-group">
                <span class="legend-caption">掌握</span>
                <span class="legend-item"
                  ><i class="dot dot-mastered"></i>已掌握</span
                >
                <span class="legend-item"
                  ><i class="dot dot-review"></i>待巩固</span
                >
                <span class="legend-item"
                  ><i class="dot dot-weak"></i>薄弱</span
                >
                <span class="legend-item"
                  ><i class="dot dot-unseen"></i>未学习</span
                >
              </div>
              <span class="side-hint legend-hint"
                >滚轮缩放 · 拖拽查看 · 单击选中 · 双击进入学习</span
              >
            </div>
          </div>
        </div>

        <!-- 右侧知识点详情 -->
        <div class="detail-col">
          <div class="detail-card">
            <template v-if="selectedNode">
              <div class="detail-head">
                <span class="card-title">知识点详情</span>
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

              <h3 class="node-name">{{ selectedNode.name }}</h3>
              <div class="node-crumb">
                {{ selectedCrumb }} · 类别
                {{ selectedNode.category || "未标注" }}
              </div>

              <p v-if="selectedNode.description" class="node-description">
                {{ selectedNode.description }}
              </p>
              <p v-else class="detail-empty">暂无节点描述</p>

              <div class="section-title">掌握状态</div>
              <template v-if="selectedMastery">
                <div class="score-row">
                  <span class="score-label">概念理解</span>
                  <a-progress
                    :percent="selectedMastery.conceptScore ?? 0"
                    :show-text="false"
                    stroke-color="#2059c9"
                    style="flex: 1"
                  />
                  <span class="score-pct blue">
                    {{ percentText(selectedMastery.conceptScore) }}
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
                    {{ percentText(selectedMastery.practiceScore) }}
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
                课程结构预览：登录学生账号后展示个人掌握状态与学习证据。
              </p>

              <div class="section-title">掌握证据</div>
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
                <div v-if="questionStatsLine" class="score-sub">
                  {{ questionStatsLine }}
                </div>
              </template>
              <p v-else class="side-hint">
                暂无学习证据：完成本节点的题目、编程实验或算法演示后会在这里出现。
              </p>

              <div class="section-title">关联节点</div>
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
                    :key="'pre-' + item.name"
                    type="button"
                    class="rel-node"
                    @click="selectAndFocus(item.name)"
                  >
                    <i class="dot" :class="dotClass(item.status)"></i>
                    <span class="rel-node-name">{{ item.name }}</span>
                  </button>
                </div>
                <div v-if="relationGroups.successors.length" class="rel-group">
                  <div class="rel-group-caption">后续知识（后学）</div>
                  <button
                    v-for="item in relationGroups.successors"
                    :key="'suc-' + item.name"
                    type="button"
                    class="rel-node"
                    @click="selectAndFocus(item.name)"
                  >
                    <i class="dot" :class="dotClass(item.status)"></i>
                    <span class="rel-node-name">{{ item.name }}</span>
                  </button>
                </div>
                <div v-if="relationGroups.related.length" class="rel-group">
                  <div class="rel-group-caption">相关节点</div>
                  <button
                    v-for="item in relationGroups.related"
                    :key="'rel-' + item.name"
                    type="button"
                    class="rel-node"
                    @click="selectAndFocus(item.name)"
                  >
                    <i class="dot" :class="dotClass(item.status)"></i>
                    <span class="rel-node-name">{{ item.name }}</span>
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
                @click="enterWorkspace(selectedNode.name || '')"
              >
                进入学习
              </a-button>

              <template v-if="pathData && pathData.items.length > 1">
                <div class="section-title">
                  补救学习路径（{{ pathData.items.length }} 步）
                </div>
                <div
                  v-for="item in pathData.items"
                  :key="item.nodeName"
                  class="path-item"
                  :class="{
                    'path-target': item.nodeName === pathData.targetNodeName,
                  }"
                  tabindex="0"
                  @click="selectAndFocus(item.nodeName)"
                  @keydown.enter.prevent="selectAndFocus(item.nodeName)"
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
                </div>
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
          </div>
        </div>
      </div>
    </main>
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

// AI 助手页面上下文（log3 阶段 4）：挂载注册、卸载注销
const aiPageContextProvider = () => ({
  pageName: "知识图谱",
  nodeName: selectedNode.value?.name || undefined,
  chapter: chapterFilter.value || undefined,
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

const viewMode = ref<"mine" | "full">("mine");
const searchText = ref("");
const chapterFilter = ref("");
const categoryFilter = ref("");
const statusFilter = ref("");
const pathFocus = ref(false);
// 关系文字标签常显开关（hover/选中相邻边始终显示）
const showRelationLabels = ref(false);

// §10.3 教学上下文：/teaching/knowledge 默认课程结构 + 维护面板；
// /graph 为学生视角，默认「我的掌握」。管理员在学习侧只见自己的预览上下文。
const isTeaching = computed(() => route.path.startsWith("/teaching"));

// §10.5/§10.6 后端补救路径（PathItem 投影）与状态脉冲
const pathData = ref<LearningPathVO | null>(null);
const pulses = ref<Array<{ name: string; color: "blue" | "amber" }>>([]);

// 选中节点（按 name 记录，跨视图稳定）
const selectedName = ref("");
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

/** 补救路径节点名（后端拓扑排序结果，前置在前） */
const pathNodeNames = computed(() =>
  (pathData.value?.items || []).map((item) => item.nodeName)
);

/** 补救路径边：路径项 → 直接前置（依赖方 -> 前置），用于高亮 */
const pathEdgeKeys = computed(() => {
  const keys = new Set<string>();
  const nameToId = new Map(nodes.value.map((n) => [n.name, n.id]));
  for (const item of pathData.value?.items || []) {
    for (const pre of item.prerequisites || []) {
      const src = nameToId.get(item.nodeName);
      const tgt = nameToId.get(pre);
      if (src && tgt) keys.add(src + "->" + tgt);
    }
  }
  return keys;
});

/** 焦点节点（推荐/选中）的一跳邻接节点名集合 */
const neighborNames = computed(() => {
  const focus = focusName.value;
  if (!focus) return new Set<string>();
  const idToName = new Map(nodes.value.map((n) => [n.id as string, n.name]));
  const nameToId = new Map(nodes.value.map((n) => [n.name, n.id]));
  const focusId = nameToId.get(focus);
  const neighbors = new Set<string>([focus]);
  if (!focusId) return neighbors;
  for (const link of links.value) {
    if (link.source === focusId && idToName.get(link.target)) {
      neighbors.add(idToName.get(link.target) as string);
    }
    if (link.target === focusId && idToName.get(link.source)) {
      neighbors.add(idToName.get(link.source) as string);
    }
  }
  return neighbors;
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

const filteredNodes = computed(() => {
  let result = nodes.value;
  if (searchText.value) {
    result = result.filter((n) =>
      (n.name || "").toLowerCase().includes(searchText.value.toLowerCase())
    );
  }
  if (chapterFilter.value) {
    result = result.filter(
      (node) => chapterLabel(node) === chapterFilter.value
    );
  }
  if (categoryFilter.value) {
    result = result.filter((n) => n.category === categoryFilter.value);
  }
  if (statusFilter.value) {
    result = result.filter((n) => n.status === statusFilter.value);
  }
  return result;
});

// 「只看学习路径」：把画布收窄到后端补救路径 + 焦点一跳邻接（显式筛选，不删全图节点）
const canvasBaseNodes = computed(() => {
  if (viewMode.value === "full") {
    return filteredNodes.value;
  }
  if (!pathFocus.value) {
    return filteredNodes.value;
  }
  const allowed = new Set<string>([
    ...pathNodeNames.value,
    ...neighborNames.value,
  ]);
  return filteredNodes.value.filter((n) => allowed.has(n.name));
});

// 画布数据 = 筛选后节点（类别底色/状态条/播种坐标由组件内计算）
const canvasGraph = computed(() => ({
  nodes: canvasBaseNodes.value,
}));

const canvasLinks = computed(() => {
  const ids = new Set(canvasBaseNodes.value.map((n) => n.id));
  const base = links.value.filter(
    (l) => ids.has(l.source) && ids.has(l.target)
  );
  if (viewMode.value === "full" || !pathFocus.value) {
    return base;
  }
  // 补救学习路径边主蓝强调（视图注入样式，画布语义样式之上）
  return base.map((l) => {
    const key = l.source + "->" + l.target;
    if (pathEdgeKeys.value.has(key)) {
      return {
        ...l,
        lineStyle: { color: "#2059c9", width: 2.4, curveness: 0 },
      };
    }
    return l;
  });
});

// log3 收尾 B.5：筛选残留守卫 —— 选中节点必须仍在当前可见集合内；
// 被筛选隐藏时自动改选第一个可见节点（无可见节点则清空选中），
// 高亮与右栏详情不得残留到隐藏节点上。
watch(canvasBaseNodes, (visible) => {
  if (!selectedName.value) return;
  if (visible.some((n) => n.name === selectedName.value)) return;
  const first = visible[0];
  if (first) {
    selectAndFocus(first.name);
  } else {
    selectedName.value = "";
    selectedWorkbench.value = null;
    pathData.value = null;
  }
});

// ============ 右栏 ============
const selectedNode = computed(
  () => nodeByName.value.get(selectedName.value) || null
);
const selectedNodeId = computed(() => selectedNode.value?.id || "");
const selectedMastery = computed(() => {
  const node = selectedNode.value;
  if (!node || !node.status || node.status === "GRAY") return null;
  return node;
});

const selectedCrumb = computed(() => {
  const node = selectedNode.value;
  if (!node) return "";
  const containParent = new Map<string, string>();
  const idToName = new Map(nodes.value.map((n) => [n.id as string, n.name]));
  for (const link of links.value) {
    if (normalizeRelationType(link) === "contain") {
      const p = idToName.get(link.source);
      const c = idToName.get(link.target);
      if (p && c) containParent.set(c, p);
    }
  }
  return [chapterOf(node.name), containParent.get(node.name)]
    .filter(Boolean)
    .join(" / ");
});

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
  return raw === "implemente" ? "implements" : raw;
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
  related: "相关",
  contain: "包含",
  implements: "实现",
};

interface RelatedItem {
  name: string;
  status: string;
  tags: string[];
}

/**
 * 关联节点分组：用当前完整图谱 nodes/links 在前端按 source/target+类型过滤。
 * dependent 存储方向 A→B 表示 A 依赖 B（B 是 A 的前置知识，先学 B）：
 *   前置知识 = 本节点作为 source 时的 target；后续知识 = 本节点作为 target 时的 source。
 * related/contain/implements 不产生学习顺序，归入「相关节点」并保留关系标签。
 */
const relationGroups = computed(() => {
  const node = selectedNode.value;
  const empty = {
    prerequisites: [] as RelatedItem[],
    successors: [] as RelatedItem[],
    related: [] as RelatedItem[],
  };
  if (!node) return empty;
  const idToName = new Map(
    nodes.value.map((n) => [String(n.id), n.name] as [string, string])
  );
  const statusOf = (name: string) =>
    nodeByName.value.get(name)?.status || "GRAY";
  const prereq = new Set<string>();
  const succ = new Set<string>();
  const rel = new Map<string, RelatedItem>();
  const selfId = String(node.id);
  for (const link of links.value) {
    const type = normalizeRelationType(link);
    const sId = String(link.source);
    const tId = String(link.target);
    const sName = idToName.get(sId);
    const tName = idToName.get(tId);
    if (!sName || !tName || sName === tName) continue;
    if (type === "dependent") {
      if (sId === selfId) prereq.add(tName);
      if (tId === selfId) succ.add(sName);
    } else if (RELATION_TAG_TEXT[type]) {
      const other = sId === selfId ? tName : tId === selfId ? sName : "";
      if (!other) continue;
      const entry = rel.get(other) || {
        name: other,
        status: statusOf(other),
        tags: [],
      };
      if (!entry.tags.includes(RELATION_TAG_TEXT[type])) {
        entry.tags = [...entry.tags, RELATION_TAG_TEXT[type]];
      }
      rel.set(other, entry);
    }
  }
  const simple = (names: Iterable<string>): RelatedItem[] =>
    Array.from(names)
      .map((name) => ({ name, status: statusOf(name), tags: [] }))
      .sort((a, b) => a.name.localeCompare(b.name, "zh"));
  return {
    prerequisites: simple(prereq),
    successors: simple(succ),
    related: Array.from(rel.values()).sort((a, b) =>
      a.name.localeCompare(b.name, "zh")
    ),
  };
});

// ============ 学习证据（按需来自 nodeWorkbench，复用缓存） ============
// log3 收尾 C：图谱详情不再渲染题目/实验入口，相关入口只出现在
// 节点学习工作台的"题目练习 / 实践"页签。
const questionStatsLine = computed(() => {
  const stats = selectedWorkbench.value?.questionStats;
  if (!stats || !stats.total) return "";
  return `本节点题目 ${stats.total} · 已作答 ${stats.attempted}`;
});

const lastFailure = computed(
  () => selectedWorkbench.value?.lastFailure || null
);

// ============ 数据加载 ============
const loadGraphData = async () => {
  loading.value = true;
  graphError.value = "";
  // 状态脉冲：对比刷新前后 status 变化的节点（进步蓝 / 退步琥珀）
  const prevStatus = new Map(
    nodes.value.map((n) => [n.name, n.status || "GRAY"] as [string, string])
  );
  let handled = false;
  try {
    if (isLoggedIn()) {
      try {
        const res = await personalGraph();
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
        console.warn("个人图谱加载失败，回退全量图谱", e);
      }
    }
    if (!handled) {
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
    }
  } catch (error) {
    graphError.value = "加载知识图谱失败，请检查网络或稍后重试";
    Message.error("加载知识图谱失败");
    console.error(error);
  } finally {
    loading.value = false;
    if (handled) {
      ensureSelection();
    }
    refreshPulse(prevStatus);
  }
};

/** 状态变化节点 → 脉冲（进步蓝/退步琥珀，组件 1.2s 后自动清除） */
const refreshPulse = (prevStatus: Map<string, string>) => {
  const changed: Array<{ name: string; color: "blue" | "amber" }> = [];
  for (const node of nodes.value) {
    const before = prevStatus.get(node.name);
    const after = node.status || "GRAY";
    if (before !== undefined && before !== after) {
      changed.push({
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

// 默认选中：默认焦点（最薄弱节点），页面打开即有 curated 视图与右栏内容
const ensureSelection = () => {
  if (
    (!selectedName.value || !nodeByName.value.has(selectedName.value)) &&
    defaultFocusName.value
  ) {
    selectedName.value = defaultFocusName.value;
    selectedWorkbench.value = null;
    pathData.value = null;
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
const loadPath = async (nodeName: string) => {
  if (!nodeName || isTeaching.value) {
    pathData.value = null;
    return;
  }
  pathData.value = null;
  try {
    const res = await masteryPath(nodeName);
    if (selectedName.value === nodeName) {
      pathData.value = res.data?.code === 0 ? res.data.data : null;
    }
  } catch {
    if (selectedName.value === nodeName) pathData.value = null;
  }
};

// 学习路径是「我的掌握」视图的个人概念：切到课程结构时收起路径聚焦
watch(viewMode, (mode) => {
  if (mode === "full") {
    pathFocus.value = false;
  }
});

watch(pathFocus, (enabled) => {
  if (enabled && focusName.value && !pathData.value) {
    loadPath(focusName.value);
  }
});

const handleNodeClick = (node: any) => {
  const name = node?.name || "";
  if (!name) return;
  selectAndFocus(name);
};

// 双击统一进入动态节点学习工作台（删除静态 HTML 映射）
const handleNodeDblClick = (node: any) => {
  const nodeName = node?.name || "";
  if (!nodeName) return;
  router.push({
    path: `/knowledge/node/${encodeURIComponent(nodeName)}`,
  });
};

const selectAndFocus = (name: string) => {
  selectedName.value = name;
  loadSelectedWorkbench(name);
  loadPath(name);
};

const searchFocus = () => {
  const kw = searchText.value.trim().toLowerCase();
  if (!kw) return;
  const hit = nodes.value.find((n) =>
    (n.name || "").toLowerCase().includes(kw)
  );
  if (hit) {
    selectAndFocus(hit.name);
  } else {
    Message.info("未找到匹配的知识节点");
  }
};

/** 清除全部筛选（空态里的真实动作） */
const clearFilters = () => {
  searchText.value = "";
  chapterFilter.value = "";
  categoryFilter.value = "";
  statusFilter.value = "";
  pathFocus.value = false;
};

// ============ 操作 ============
const enterWorkspace = (nodeName: string) => {
  if (!nodeName) return;
  router.push({ path: `/knowledge/node/${encodeURIComponent(nodeName)}` });
};

// ============ 工具 ============
const percentText = (value: number | null | undefined) =>
  value === null || value === undefined ? "-" : `${Math.round(value * 100)}%`;

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
    chapter:
      chapterFilter.value && chapterFilter.value !== "未分类"
        ? chapterFilter.value
        : "",
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
    if (
      chapterFilter.value &&
      !availableChapters.value.includes(chapterFilter.value)
    ) {
      chapterFilter.value = "";
    }
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
  const nodeName = selectedName.value;
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
      selectedName.value = "";
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
  // 教学维护视角默认课程结构；学生视角默认「我的掌握」（现有行为保留）
  viewMode.value = isTeaching.value ? "full" : "mine";
  window.addEventListener("oslab:learning-changed", handleLearningChanged);
  // 支持从其他页面带 nodeName 直达
  const q = route.query;
  if (typeof q.nodeName === "string" && q.nodeName) {
    selectedName.value = q.nodeName;
  }
  await loadGraphData();
  // Preview aggregation is only needed after an explicit selection/deep link.
  if (typeof q.nodeName === "string" && nodeByName.value.has(q.nodeName)) {
    selectAndFocus(q.nodeName);
  }
});

onBeforeUnmount(() => {
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
  width: 10px;
  height: 10px;
  border-radius: 3px;
  border: 1px solid var(--oslab-line);
}
.rel-line {
  display: inline-block;
  width: 20px;
  height: 0;
  vertical-align: middle;
}
.rel-dependent {
  border-top: 2px solid var(--oslab-primary);
}
.rel-contain {
  border-top: 2px dashed #7d8ba1;
}
.rel-related {
  border-top: 2px dotted #b3ada5;
}
.rel-implements {
  border-top: 2px dashed var(--oslab-review);
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
  color: var(--oslab-mastered);
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
  color: var(--oslab-weak);
  text-align: right;
}
.evidence-pass .evidence-meta {
  color: var(--oslab-mastered);
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
.path-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border-radius: var(--oslab-radius-md);
  border-bottom: 1px dashed var(--oslab-line-soft);
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
