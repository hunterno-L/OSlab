<template>
  <div class="learning-records-view">
    <header class="records-heading">
      <h1>{{ activeView === "review" ? "待巩固" : "学习记录" }}</h1>
      <span v-if="activeView !== 'review'" class="hint"
        >{{ total }} 条学习活动</span
      >
    </header>
    <section v-if="activeView === 'timeline'">
      <div class="filter-bar">
        <a-input
          v-model:model-value="filters.nodeName"
          placeholder="知识点名称/编码"
          allow-clear
          style="width: 190px"
          @press-enter="onSearch"
        />
        <a-select
          v-model:model-value="filters.eventType"
          placeholder="活动类型"
          allow-clear
          style="width: 170px"
        >
          <a-option value="ATTEMPT">题目练习</a-option>
          <a-option value="JUDGE">编程实验判题</a-option>
          <a-option value="SIMULATION">交互模拟（判分）</a-option>
          <a-option value="SIMULATION_RUN">算法演示运行</a-option>
          <a-option value="TASK">学习任务</a-option>
          <a-option value="UNIT">单元活动</a-option>
        </a-select>
        <a-select
          v-model:model-value="filters.courseCode"
          placeholder="全部课程"
          allow-clear
          style="width: 200px"
        >
          <a-option
            v-for="course in courses"
            :key="course.courseCode"
            :value="course.courseCode"
            >{{ course.title }}</a-option
          >
        </a-select>
        <a-select
          v-model:model-value="filters.unitCode"
          placeholder="全部单元"
          allow-clear
          :disabled="!filters.courseCode"
          style="width: 200px"
        >
          <a-option
            v-for="unit in courseUnits"
            :key="unit.unitCode"
            :value="unit.unitCode"
            >{{ unit.title }}</a-option
          >
        </a-select>
        <a-range-picker
          v-model:model-value="filters.range"
          value-format="YYYY-MM-DD"
          allow-clear
          style="width: 250px"
        />
        <a-button type="primary" :loading="loading" @click="onSearch">
          查询
        </a-button>
        <a-button @click="onReset">重置</a-button>
      </div>

      <a-alert v-if="usingEventFallback" type="warning" class="fallback-banner">
        活动记录接口暂不可用，已回退到事件时间线。普通模拟运行与学习任务记录需应用
        v9 数据库迁移并重启后端后显示。
      </a-alert>

      <!-- 空态一：接口失败 -->
      <div v-if="loadError" class="section-card empty-state">
        <div class="empty-title">学习记录加载失败</div>
        <div class="hint">{{ loadError }}</div>
        <a-button type="primary" style="margin-top: 12px" @click="loadTimeline">
          重试
        </a-button>
      </div>
      <!-- 空态二：从未学习 -->
      <div
        v-else-if="!loading && total === 0 && !hasActiveFilter"
        class="section-card empty-state"
      >
        <div class="empty-title">还没有学习记录</div>
        <div class="hint">
          暂无题目练习、编程实验、算法演示、学习任务或单元活动记录。
        </div>
        <a-button
          type="primary"
          style="margin-top: 12px"
          @click="router.push('/graph')"
        >
          前往知识图谱
        </a-button>
      </div>
      <!-- 空态三：筛选无结果 -->
      <div
        v-else-if="!loading && total === 0 && hasActiveFilter"
        class="section-card empty-state"
      >
        <div class="empty-title">没有符合条件的学习记录</div>
        <div class="hint">换个知识点名称或时间范围试试。</div>
        <a-button style="margin-top: 12px" @click="onReset">清除筛选</a-button>
      </div>

      <template v-else>
        <div v-if="loading" class="timeline-loading">
          <a-skeleton :animation="true"
            ><a-skeleton-line :rows="6"
          /></a-skeleton>
        </div>
        <div v-else class="record-timeline">
          <section
            v-for="group in dateGroups"
            :key="group.date"
            class="timeline-group"
          >
            <h2 class="timeline-date">{{ group.date }}</h2>
            <article
              v-for="activity in group.activities"
              :key="activity.activityId"
              class="timeline-event activity-card"
            >
              <time class="event-time">{{
                clockTime(activity.occurredAt)
              }}</time>
              <i
                :class="[
                  'event-dot',
                  `dot-${(activity.result || 'completed').toLowerCase()}`,
                ]"
              />
              <div class="event-content">
                <div class="event-heading">
                  <div class="event-title">
                    <a
                      v-if="activity.returnRoute"
                      class="cell-link"
                      @click="openActivity(activity)"
                      >{{ activity.title }}</a
                    >
                    <span v-else>{{ activity.title }}</span>
                    <a-tag color="blue" size="small">{{
                      activityTypeName(activity.activityType)
                    }}</a-tag>
                    <a-tag
                      :color="resultColor(activity.result || activity.status)"
                      size="small"
                      >{{
                        resultText(activity.result || activity.status)
                      }}</a-tag
                    >
                  </div>
                  <div class="event-actions">
                    <a-button
                      v-if="activity.replayEventId"
                      size="small"
                      type="text"
                      @click="openReplayForEvent(activity.replayEventId)"
                      ><template #icon><IconHistory /></template>回放</a-button
                    >
                    <a-button
                      v-if="activity.resourceType === 'QUESTION_SUBMIT'"
                      size="small"
                      type="text"
                      @click="openSubmitDetail(activity.resourceId)"
                      >提交详情</a-button
                    >
                  </div>
                </div>
                <div class="event-context">
                  <span>{{ activityTypeName(activity.activityType) }}</span>
                  <span v-if="activity.courseCode"
                    >课程 {{ activity.courseCode }}</span
                  >
                  <span v-if="activity.unitCode"
                    >单元 {{ activity.unitCode }}</span
                  >
                  <span v-if="activity.taskCode"
                    >任务 {{ activity.taskCode }}</span
                  >
                  <span v-if="activity.roundNo"
                    >第 {{ activity.roundNo }} 轮</span
                  >
                </div>
                <p v-if="activity.summary" class="event-evidence hint">
                  {{ activity.summary }}
                </p>
                <details
                  v-if="activity.nodeRefs && activity.nodeRefs.length"
                  class="activity-nodes"
                >
                  <summary>
                    关联 {{ activity.nodeRefs.length }} 个知识节点与掌握变化
                  </summary>
                  <div
                    v-for="ref in activity.nodeRefs"
                    :key="ref.nodeCode || ref.nodeName"
                    class="activity-node-row"
                  >
                    <a
                      class="cell-link"
                      @click="goNode(ref.nodeCode || ref.nodeName)"
                      >{{ ref.nodeName }}</a
                    >
                    <span class="activity-mastery oslab-mono"
                      >{{ percentText(ref.masteryBefore) }} →
                      {{ percentText(ref.masteryAfter) }}</span
                    >
                    <a-tag size="small" :color="resultColor(ref.result)">{{
                      resultText(ref.result)
                    }}</a-tag>
                    <span class="hint">{{
                      ref.independent ? "独立证据" : "非独立证据"
                    }}</span>
                    <a-button
                      v-if="ref.eventId"
                      size="mini"
                      type="text"
                      @click="openReplayForEvent(ref.eventId)"
                      >回放</a-button
                    >
                  </div>
                </details>
                <details v-if="activity.inputSnapshot" class="activity-run">
                  <summary>
                    运行输入与结果摘要（普通演示，不计掌握证据）
                  </summary>
                  <div class="run-snapshot-grid">
                    <div>
                      <span class="hint">输入快照</span>
                      <pre>{{ prettyJson(activity.inputSnapshot) }}</pre>
                    </div>
                    <div>
                      <span class="hint">结果摘要</span>
                      <pre>{{ prettyJson(activity.resultSnapshot) }}</pre>
                    </div>
                  </div>
                  <a-button
                    size="small"
                    type="primary"
                    @click="restoreRun(activity)"
                    >恢复输入并打开同一算法</a-button
                  >
                </details>
              </div>
            </article>
          </section>
        </div>
        <div class="pager-row">
          <a-pagination
            :total="total"
            :current="current"
            :page-size="pageSize"
            show-total
            show-page-size
            :page-size-options="[10, 20, 50]"
            @change="onPageChange"
            @page-size-change="onPageSizeChange"
          />
        </div>
      </template>
    </section>

    <!-- ======== 待巩固 ======== -->
    <section v-else>
      <div v-if="weakError" class="section-card empty-state">
        <div class="empty-title">待巩固清单加载失败</div>
        <div class="hint">{{ weakError }}</div>
        <a-button type="primary" style="margin-top: 12px" @click="loadWeakness">
          重试
        </a-button>
      </div>
      <div
        v-else-if="!weakLoading && weakNodes.length === 0"
        class="section-card empty-state"
      >
        <div class="empty-title">当前没有待巩固的节点</div>
        <div class="hint">
          薄弱（红色）与待巩固（黄色）节点会出现在这里，并附最近失败与归因说明。
        </div>
        <a-button
          type="primary"
          style="margin-top: 12px"
          @click="router.push('/graph')"
        >
          查看知识图谱
        </a-button>
      </div>
      <a-spin v-else :loading="weakLoading" style="display: block; width: 100%">
        <div
          v-for="node in weakNodes"
          :key="node.nodeName"
          class="section-card weak-card"
        >
          <div class="weak-head">
            <a class="cell-link weak-name" @click="goNode(node.nodeName)">
              {{ node.nodeName }}
            </a>
            <a-tag v-if="node.chapter" size="small">{{ node.chapter }}</a-tag>
            <span :class="['status-pill', statusPillClass(node.status)]">
              {{ statusText(node.status) }}
            </span>
            <span :class="['status-pill', failurePillClass(node.failureState)]">
              {{ failureStateText(node.failureState) }}
            </span>
          </div>
          <div class="weak-metrics">
            <span>综合掌握 {{ percentText(node.pKnown) }}</span>
            <span
              >概念分
              {{
                scoreText(node.conceptScore, node.conceptEvidenceCount)
              }}</span
            >
            <span
              >实践分
              {{
                scoreText(node.practiceScore, node.practiceEvidenceCount)
              }}</span
            >
            <span>概念证据 {{ node.conceptEvidenceCount ?? 0 }} 次</span>
            <span>实践证据 {{ node.practiceEvidenceCount ?? 0 }} 次</span>
            <span v-if="node.lastActivityTime">
              最近活动 {{ formatTime(node.lastActivityTime) }}
            </span>
          </div>
          <div v-if="node.lastFailure" class="weak-failure">
            <div class="failure-line">
              最近失败：{{ failureSummary(node.lastFailure) }}
              <a-tag
                v-if="node.lastFailure.certaintyLabel"
                size="small"
                color="arcoblue"
              >
                {{ node.lastFailure.certaintyLabel }}
              </a-tag>
            </div>
            <ul
              v-if="node.lastFailure.attributionBasis?.length"
              class="basis-list"
            >
              <li
                v-for="(item, i) in node.lastFailure.attributionBasis"
                :key="i"
              >
                {{ item }}
              </li>
            </ul>
          </div>
          <div class="weak-actions">
            <a-button
              size="small"
              type="primary"
              @click="goNode(node.nodeName)"
            >
              去学习
            </a-button>
            <a-button
              size="small"
              @click="
                router.push({
                  path: '/assessments',
                  query: { nodeName: node.nodeName },
                })
              "
            >
              题目练习
            </a-button>
          </div>
        </div>
      </a-spin>
    </section>

    <!-- ======== 回放弹窗（图谱快照 + 时间轴 + 播放，全程只读） ======== -->
    <a-modal
      v-model:visible="replay.visible"
      :width="replayWidth"
      :footer="false"
      title="学习历史回放"
      unmount-on-close
    >
      <div class="replay-modal-body">
        <!-- 步骤时间线载入失败 -->
        <div v-if="replay.eventsError" class="empty-state">
          <div class="empty-title">时间线加载失败</div>
          <div class="hint">{{ replay.eventsError }}</div>
          <a-button
            type="primary"
            style="margin-top: 12px"
            @click="reopenReplay"
          >
            重试
          </a-button>
        </div>
        <!-- 空态：没有历史事件 -->
        <div
          v-else-if="!replay.eventsLoading && replay.steps.length === 0"
          class="empty-state"
        >
          <div class="empty-title">还没有可回放的学习事件</div>
          <div class="hint">
            完成题目练习、编程实验或交互模拟后，这里可以按事件回放掌握状态的变化过程。
          </div>
        </div>
        <a-spin
          v-else
          :loading="replay.eventsLoading"
          style="display: block; width: 100%"
        >
          <template v-if="currentStep">
            <div class="replay-meta">
              回放时间点：{{
                formatTime(replay.data?.replayAt || currentStep.time)
              }}
            </div>
            <div v-if="replay.data?.eventGroupKey" class="hint">
              同一次学习动作影响的全部节点已一起回放（事件组
              {{ replay.data.eventGroupKey }}）。
            </div>
            <div v-if="replay.data" class="replay-stats">
              <span class="stat-item">
                <i class="dot dot-green" />已掌握
                {{ replay.data.graph.statusCounts?.GREEN ?? 0 }}
              </span>
              <span class="stat-item">
                <i class="dot dot-yellow" />待巩固
                {{ replay.data.graph.statusCounts?.YELLOW ?? 0 }}
              </span>
              <span class="stat-item">
                <i class="dot dot-red" />薄弱
                {{ replay.data.graph.statusCounts?.RED ?? 0 }}
              </span>
              <span class="stat-item">
                <i class="dot dot-gray" />未学习
                {{ replay.data.graph.statusCounts?.GRAY ?? 0 }}
              </span>
              <span class="stat-item">
                已学节点 {{ learnedNodes(replay.data).length }} /
                {{ replay.data.graph.nodes.length }}
              </span>
              <span class="stat-item">
                有学习证据的节点 {{ replay.data.graph.evidenceCount ?? 0 }} 个
              </span>
            </div>

            <div class="replay-main">
              <div class="replay-graph">
                <div v-if="replay.error" class="empty-state">
                  <div class="empty-title">回放加载失败</div>
                  <div class="hint">{{ replay.error }}</div>
                  <a-button
                    type="primary"
                    style="margin-top: 12px"
                    @click="retryStepSnapshot"
                  >
                    重试
                  </a-button>
                </div>
                <a-spin
                  v-else
                  :loading="replay.loading"
                  style="display: block; width: 100%"
                >
                  <KnowledgeGraph
                    :nodes="replayGraph.nodes"
                    :links="replayGraph.links"
                    :categories="replayGraph.categories"
                    :selected-id="replay.selectedId"
                    :chart-height="replayGraphHeight"
                    @node-click="onReplayNodeClick"
                  />
                </a-spin>
                <p class="hint replay-graph-note">
                  关系结构按当前图谱显示，历史状态按所选事件回放。
                </p>
              </div>
              <aside class="replay-detail">
                <template v-if="replaySelectedNode">
                  <div class="detail-head">
                    <span class="detail-name">{{
                      replaySelectedNode.name
                    }}</span>
                    <span
                      :class="[
                        'status-pill',
                        statusPillClass(replaySelectedNode.status),
                      ]"
                    >
                      {{ statusText(replaySelectedNode.status) }}
                    </span>
                  </div>
                  <div class="hint detail-chapter">
                    {{ replaySelectedNode.chapter || "未分类" }}
                    <span v-if="replaySelectedNode.category">
                      · {{ replaySelectedNode.category }}</span
                    >
                  </div>
                  <div>
                    <a-tag
                      v-if="replaySelectedNode.snapshotAvailable === false"
                      size="small"
                      color="gray"
                    >
                      历史细分状态未记录
                    </a-tag>
                    <a-tag v-else size="small" color="green">完整快照</a-tag>
                  </div>
                  <dl class="detail-facts">
                    <div class="detail-fact">
                      <dt>综合掌握</dt>
                      <dd>{{ percentText(replaySelectedNode.pKnown) }}</dd>
                    </div>
                    <div class="detail-fact">
                      <dt>概念分</dt>
                      <dd>
                        {{
                          scoreText(
                            replaySelectedNode.conceptScore,
                            replaySelectedNode.conceptEvidenceCount
                          )
                        }}
                      </dd>
                    </div>
                    <div class="detail-fact">
                      <dt>实践分</dt>
                      <dd>
                        {{
                          scoreText(
                            replaySelectedNode.practiceScore,
                            replaySelectedNode.practiceEvidenceCount
                          )
                        }}
                      </dd>
                    </div>
                    <div class="detail-fact">
                      <dt>概念证据</dt>
                      <dd>
                        {{ replaySelectedNode.conceptEvidenceCount ?? 0 }} 次
                      </dd>
                    </div>
                    <div class="detail-fact">
                      <dt>实践证据</dt>
                      <dd>
                        {{ replaySelectedNode.practiceEvidenceCount ?? 0 }} 次
                      </dd>
                    </div>
                  </dl>
                  <p v-if="replaySelectedNode.recentReason" class="hint">
                    最近证据：{{ replaySelectedNode.recentReason }}
                  </p>
                  <p class="hint">当时状态参考：{{ replaySuggestion }}</p>
                  <p class="hint">回放为只读视图，不触发学习、提交或删除。</p>
                </template>
                <p v-else class="hint">
                  点击图谱节点或下方列表行，查看该时点的掌握度、证据与建议。
                </p>
              </aside>
            </div>

            <!-- 时间轴控制：滑块 + 前一步/后一步 + 播放/暂停 -->
            <div v-if="replay.steps.length > 1" class="replay-controls">
              <a-button
                size="small"
                :disabled="replay.stepIndex <= 0"
                @click="stepPrev"
              >
                上一步
              </a-button>
              <a-button
                size="small"
                :disabled="replay.stepIndex >= replay.steps.length - 1"
                @click="stepNext"
              >
                下一步
              </a-button>
              <a-slider
                class="replay-slider"
                :model-value="replay.stepIndex"
                :min="0"
                :max="replay.steps.length - 1"
                :step="1"
                :format-tooltip="sliderTooltip"
                @change="onSliderChange"
              />
              <a-button size="small" type="primary" @click="togglePlayback">
                {{ replay.playing ? "暂停" : "播放" }}
              </a-button>
            </div>
            <div class="replay-current">
              <span class="current-step">
                第 {{ replay.stepIndex + 1 }} / {{ replay.steps.length }} 步
              </span>
              <span>{{ formatTime(currentStep.time) }}</span>
              <span>{{ eventTypeName(currentStep.eventType) }}</span>
              <a-tag :color="resultColor(currentStep.result)" size="small">
                {{ resultText(currentStep.result) }}
              </a-tag>
              <span v-if="currentStep.nodeNames.length > 1" class="hint">
                影响 {{ currentStep.nodeNames.length }} 个节点：{{
                  currentStep.nodeNames.join("、")
                }}
              </span>
            </div>

            <div v-if="replay.data" class="replay-list">
              <div class="replay-grid replay-list-head">
                <span>知识点</span>
                <span>章节</span>
                <span>状态</span>
                <span>综合掌握</span>
                <span>概念分</span>
                <span>实践分</span>
                <span>快照</span>
              </div>
              <button
                v-for="node in learnedNodes(replay.data)"
                :key="node.name"
                type="button"
                class="replay-grid replay-row"
                :class="{
                  'replay-row-active': replay.selectedName === node.name,
                }"
                :aria-pressed="replay.selectedName === node.name"
                @click="selectReplayNode(node)"
              >
                <span>
                  <span class="cell-link">{{ node.name }}</span>
                </span>
                <span class="cell-muted">{{ node.chapter || "未分类" }}</span>
                <span>
                  <span :class="['status-pill', statusPillClass(node.status)]">
                    {{ statusText(node.status) }}
                  </span>
                </span>
                <span>{{ percentText(node.pKnown) }}</span>
                <span>{{
                  scoreText(node.conceptScore, node.conceptEvidenceCount)
                }}</span>
                <span>{{
                  scoreText(node.practiceScore, node.practiceEvidenceCount)
                }}</span>
                <span>
                  <a-tag
                    v-if="node.snapshotAvailable === false"
                    size="small"
                    color="gray"
                  >
                    历史细分状态未记录
                  </a-tag>
                  <a-tag v-else size="small" color="green">完整快照</a-tag>
                </span>
              </button>
              <div
                v-if="learnedNodes(replay.data).length === 0"
                class="empty-state"
              >
                该时点还没有已学习节点。
              </div>
            </div>
            <div class="hint replay-footnote">
              回放仅展示个人学习状态；关系结构按当前图谱显示，历史状态按所选事件回放，不重建历史课程拓扑。
            </div>
          </template>
        </a-spin>
      </div>
    </a-modal>

    <!-- ======== 判题提交详情弹窗 ======== -->
    <a-modal
      v-model:visible="submitDetail.visible"
      :width="submitDetailWidth"
      :footer="false"
      title="编程实验提交详情"
    >
      <div v-if="submitDetail.error" class="empty-state">
        <div class="empty-title">提交详情加载失败</div>
        <div class="hint">{{ submitDetail.error }}</div>
      </div>
      <a-spin
        v-else
        :loading="submitDetail.loading"
        style="display: block; width: 100%"
      >
        <a-descriptions
          v-if="submitDetail.data"
          :column="1"
          bordered
          size="medium"
        >
          <a-descriptions-item label="实验题目">
            {{ submitDetail.data.questionVO?.title || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="判题结果">
            {{ submitDetail.data.judgeInfo?.message || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="提交时间">
            {{ formatTime(String(submitDetail.data.createTime || "")) }}
          </a-descriptions-item>
          <a-descriptions-item label="提交代码">
            <pre class="code-block">{{ submitDetail.data.code }}</pre>
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { IconHistory } from "@arco-design/web-vue/es/icon";
import {
  learningActivities,
  masterySnapshot,
  masteryTimeline,
  masteryWeakness,
  type LastFailureVO,
  type ActivityNodeRefVO,
  type LearningActivityVO,
  type LearningEventVO,
  type MasterySnapshotVO,
  type WeakNodeVO,
} from "@/api/learning";
import {
  getCourseOutline,
  listCourses,
  type CourseSummary,
  type UnitSummary,
} from "@/api/course";
import {
  QuestionControllerService,
  type QuestionSubmitVO,
} from "../../../generated";
import KnowledgeGraph from "@/components/KnowledgeGraph.vue";
import { scoreText } from "@/utils/masteryDisplay";

const route = useRoute();
const router = useRouter();
const viewportWidth = ref(window.innerWidth);
const replayWidth = computed(() => Math.min(1080, viewportWidth.value - 24));
const submitDetailWidth = computed(() =>
  Math.min(720, viewportWidth.value - 24)
);
const updateViewport = () => {
  viewportWidth.value = window.innerWidth;
};
window.addEventListener("resize", updateViewport);

const activeView = computed(() =>
  route.query.view === "review" ? "review" : "timeline"
);

// ---------- 时间线：筛选、分页、数据 ----------

const filters = reactive({
  nodeName: "",
  eventType: "",
  courseCode: "",
  unitCode: "",
  range: [] as string[],
});

const loading = ref(false);
const loadError = ref("");
const usingEventFallback = ref(false);
const activities = ref<LearningActivityVO[]>([]);
const total = ref(0);
const current = ref(1);
const pageSize = ref(10);
let timelineSequence = 0;

const courses = ref<CourseSummary[]>([]);
const courseUnits = ref<UnitSummary[]>([]);

const EVENT_TYPE_OPTIONS = [
  "ATTEMPT",
  "JUDGE",
  "SIMULATION",
  "SIMULATION_RUN",
  "TASK",
  "UNIT",
];

const hasActiveFilter = computed(
  () =>
    filters.nodeName.trim() !== "" ||
    Boolean(filters.eventType) ||
    Boolean(filters.courseCode) ||
    Boolean(filters.unitCode) ||
    (filters.range?.length === 2 && !!filters.range[0] && !!filters.range[1])
);

const loadCourses = async () => {
  try {
    courses.value = await listCourses();
  } catch {
    courses.value = [];
  }
};

const loadCourseUnits = async (courseCode: string) => {
  courseUnits.value = [];
  if (!courseCode) return;
  try {
    const outline = await getCourseOutline(courseCode);
    const result: UnitSummary[] = [];
    const walk = (sections: typeof outline.sections) => {
      sections.forEach((section) => {
        result.push(...(section.units || []));
        if (section.children?.length) walk(section.children);
      });
    };
    walk(outline.sections || []);
    courseUnits.value = result;
  } catch {
    courseUnits.value = [];
  }
};

watch(
  () => filters.courseCode,
  (code) => {
    filters.courseCode = code || "";
    filters.unitCode = "";
    void loadCourseUnits(filters.courseCode);
  }
);

const EVIDENCE_EVENT_TYPES = ["ATTEMPT", "JUDGE", "SIMULATION"];

const fallbackTitle = (event: LearningEventVO) => {
  try {
    const evidence = JSON.parse(event.evidence || "{}");
    if (evidence?.title) return String(evidence.title);
  } catch {
    // 历史证据不是 JSON 时使用稳定兜底标题
  }
  if (event.eventType === "ATTEMPT") return `题目练习 #${event.resourceId}`;
  if (event.eventType === "JUDGE") return `提交 #${event.resourceId}`;
  if (event.eventType === "SIMULATION") return "交互模拟";
  return "学习活动";
};

const fallbackSummary = (event: LearningEventVO) => {
  try {
    const evidence = JSON.parse(event.evidence || "{}");
    return [evidence.source, evidence.resultText, evidence.note]
      .filter(Boolean)
      .join(" · ");
  } catch {
    return "";
  }
};

const fallbackReturnRoute = (event: LearningEventVO) => {
  if (event.eventType === "ATTEMPT") {
    return `/assessments?questionId=${event.resourceId}`;
  }
  if (event.eventType === "SIMULATION") {
    return `/experiments/simulation?nodeName=${encodeURIComponent(
      event.nodeName
    )}`;
  }
  return null;
};

/** 活动接口不可用时，把既有事件时间线映射成同构活动卡片。 */
const mapEventsToFallbackActivities = (
  events: LearningEventVO[]
): LearningActivityVO[] => {
  const groups = new Map<string, LearningEventVO[]>();
  events
    .filter((event) => event.eventType !== "STATE_BASELINE")
    .forEach((event) => {
      const key = event.eventGroupKey
        ? `g:${event.eventGroupKey}`
        : `e:${event.id}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)?.push(event);
    });
  return Array.from(groups.values()).map((group) => {
    const anchor = group[0];
    const seen = new Set<string>();
    const nodeRefs = group
      .map((event) => {
        const key = event.nodeCode || event.nodeName;
        if (seen.has(key)) return null;
        seen.add(key);
        return {
          nodeCode: event.nodeCode || null,
          nodeName: event.nodeName,
          masteryBefore: event.masteryBefore,
          masteryAfter: event.masteryAfter,
          result: event.result,
          independent: !!event.isFirst && event.result !== "SYSTEM_ERROR",
          eventId: event.id,
        };
      })
      .filter(Boolean) as ActivityNodeRefVO[];
    return {
      activityId: `event:${
        anchor.eventGroupKey ? anchor.eventGroupKey : anchor.id
      }`,
      activityType: anchor.eventType,
      title: fallbackTitle(anchor),
      status: anchor.result,
      result: anchor.result,
      occurredAt: anchor.createTime,
      unitCode: anchor.sourceUnitCode || null,
      activityCode: anchor.sourceActivityCode || null,
      taskCode: anchor.sourceTaskCode || null,
      taskItemCode: anchor.sourceTaskItemCode || null,
      roundNo: anchor.sourceTaskRound ?? null,
      resourceType: anchor.resourceType,
      resourceId: anchor.resourceId,
      summary: fallbackSummary(anchor),
      nodeRefs,
      evidenceRefs: group.map((event) => event.id),
      replayEventId: anchor.id,
      returnRoute: fallbackReturnRoute(anchor),
    };
  });
};

const loadEventFallback = async (
  params: { startTime?: string; endTime?: string },
  sequence: number
) => {
  if (filters.eventType && !EVIDENCE_EVENT_TYPES.includes(filters.eventType)) {
    return false;
  }
  try {
    const res = await masteryTimeline({
      nodeName: filters.nodeName.trim() || undefined,
      eventType: filters.eventType || undefined,
      startTime: params.startTime,
      endTime: params.endTime,
      current: current.value,
      pageSize: pageSize.value,
    });
    if (sequence !== timelineSequence) return true;
    if (res.data?.code !== 0) return false;
    activities.value = mapEventsToFallbackActivities(
      res.data.data?.events ?? []
    );
    total.value = Number(res.data.data?.total ?? 0);
    usingEventFallback.value = true;
    loadError.value = "";
    return true;
  } catch {
    return false;
  }
};

const loadTimeline = async () => {
  const sequence = ++timelineSequence;
  loading.value = true;
  loadError.value = "";
  const params: {
    type?: string;
    nodeCode?: string;
    courseCode?: string;
    unitCode?: string;
    startTime?: string;
    endTime?: string;
    current?: number;
    pageSize?: number;
  } = {
    current: current.value,
    pageSize: pageSize.value,
  };
  if (filters.nodeName.trim()) {
    params.nodeCode = filters.nodeName.trim();
  }
  if (filters.eventType) {
    params.type = filters.eventType;
  }
  if (filters.courseCode) {
    params.courseCode = filters.courseCode;
  }
  if (filters.unitCode) {
    params.unitCode = filters.unitCode;
  }
  if (filters.range?.length === 2 && filters.range[0] && filters.range[1]) {
    params.startTime = `${filters.range[0]} 00:00:00`;
    params.endTime = `${filters.range[1]} 23:59:59`;
  }
  try {
    const res = await learningActivities(params);
    if (sequence !== timelineSequence) return;
    if (res.data?.code === 0) {
      activities.value = res.data.data?.records ?? [];
      total.value = Number(res.data.data?.total ?? 0);
      usingEventFallback.value = false;
      return;
    }
    if (await loadEventFallback(params, sequence)) return;
    loadError.value =
      res.data?.message || "学习记录加载失败（活动接口不可用且事件回退失败）";
    activities.value = [];
    total.value = 0;
  } catch {
    if (sequence !== timelineSequence) return;
    if (await loadEventFallback(params, sequence)) return;
    loadError.value =
      "学习记录接口暂不可用，事件回退也失败；请检查后端版本或稍后重试";
    activities.value = [];
    total.value = 0;
  } finally {
    if (sequence === timelineSequence) loading.value = false;
  }
};

const onSearch = () => {
  current.value = 1;
  loadTimeline();
};

const onReset = () => {
  filters.nodeName = "";
  filters.eventType = "";
  filters.courseCode = "";
  filters.unitCode = "";
  filters.range = [];
  courseUnits.value = [];
  current.value = 1;
  loadTimeline();
};

const onPageChange = (page: number) => {
  current.value = page;
  loadTimeline();
};

const onPageSizeChange = (size: number) => {
  pageSize.value = size;
  current.value = 1;
  loadTimeline();
};

// ---------- 待巩固：懒加载 ----------

const weakLoading = ref(false);
const weakError = ref("");
const weakNodes = ref<WeakNodeVO[]>([]);
const weakLoaded = ref(false);

const loadWeakness = async () => {
  weakLoading.value = true;
  weakError.value = "";
  try {
    const res = await masteryWeakness();
    if (res.data?.code === 0) {
      weakNodes.value = res.data.data ?? [];
      weakLoaded.value = true;
    } else {
      weakError.value = res.data?.message || "待巩固清单加载失败";
    }
  } catch {
    weakError.value = "网络或服务异常，请稍后重试";
  } finally {
    weakLoading.value = false;
  }
};

// ---------- 回放（图谱快照 + 时间轴 + 播放，全程只读） ----------

/** 回放步骤：同一 eventGroupKey 的一次学习事务合成一步（无组键的历史事件各自成步） */
interface ReplayStep {
  key: string;
  anchorEventId: number;
  events: LearningEventVO[];
  time: string;
  result: string;
  eventType: string;
  nodeNames: string[];
}

const PLAY_INTERVAL_MS = 1200;
const REPLAY_PAGE_SIZE = 100;
const REPLAY_MAX_PAGES = 50;

const replay = reactive({
  visible: false,
  loading: false, // 当前步骤快照加载
  error: "", // 当前步骤快照错误
  data: null as MasterySnapshotVO | null,
  anchorEventId: null as number | string | null,
  eventsLoading: false, // 回放步骤（全量时间线）载入
  eventsError: "",
  steps: [] as ReplayStep[],
  stepIndex: 0,
  playing: false,
  selectedName: "",
  selectedId: "",
});
const snapshotCache = new Map<string, MasterySnapshotVO>();
let replaySequence = 0;
let playTimer: number | null = null;

watch(
  () => replay.visible,
  (visible) => {
    if (!visible) {
      replaySequence += 1;
      stopPlayback();
      replay.loading = false;
      replay.eventsLoading = false;
      snapshotCache.clear();
    }
  }
);

/** 一次提交的多个回调按 eventGroupKey 合并为一步，不拆成虚假步骤 */
const buildReplaySteps = (events: LearningEventVO[]): ReplayStep[] => {
  const groups = new Map<string, LearningEventVO[]>();
  const order: string[] = [];
  for (const event of events) {
    const key = event.eventGroupKey
      ? `g:${event.eventGroupKey}`
      : `e:${event.id}`;
    if (!groups.has(key)) {
      groups.set(key, []);
      order.push(key);
    }
    groups.get(key)?.push(event);
  }
  // events 已按 (createTime,id) 升序；锚点取组内最后一条，与后端
  // replaySnapshot"事件组整体计入该时点"的语义一致
  return order.map((key) => {
    const list = groups.get(key) ?? [];
    const anchor = list[list.length - 1];
    return {
      key,
      anchorEventId: anchor.id,
      events: list,
      time: anchor.createTime,
      result: anchor.result,
      eventType: anchor.eventType,
      nodeNames: Array.from(new Set(list.map((e) => e.nodeName))),
    };
  });
};

const loadReplaySteps = async () => {
  replay.eventsLoading = true;
  replay.eventsError = "";
  try {
    const collected: LearningEventVO[] = [];
    let total = 0;
    let page = 1;
    // 后端 myTimeline 每页上限 100，这里翻页取全量作为回放步骤（有页数保险上限）
    do {
      const res = await masteryTimeline({
        current: page,
        pageSize: REPLAY_PAGE_SIZE,
      });
      const payload = res.data?.code === 0 ? res.data.data : null;
      if (!payload) {
        throw new Error(res.data?.message || "时间线加载失败");
      }
      total = Number(payload.total ?? 0);
      const events = payload.events ?? [];
      collected.push(...events);
      if (events.length === 0) break;
      page += 1;
    } while (collected.length < total && page <= REPLAY_MAX_PAGES);
    collected.sort((a, b) => {
      if (a.createTime === b.createTime) return Number(a.id) - Number(b.id);
      return a.createTime < b.createTime ? -1 : 1;
    });
    replay.steps = buildReplaySteps(collected);
  } catch (e) {
    replay.steps = [];
    replay.eventsError =
      e instanceof Error && e.message
        ? e.message
        : "网络或服务异常，请稍后重试";
  } finally {
    replay.eventsLoading = false;
  }
};

const loadStepSnapshot = async (index: number) => {
  const step = replay.steps[index];
  if (!step) return;
  const cacheKey = String(step.anchorEventId);
  const cached = snapshotCache.get(cacheKey);
  if (cached) {
    replay.data = cached;
    replay.error = "";
    replay.loading = false;
    return;
  }
  const sequence = ++replaySequence;
  replay.loading = true;
  replay.error = "";
  try {
    const res = await masterySnapshot(step.anchorEventId);
    if (sequence !== replaySequence) return;
    if (res.data?.code === 0 && res.data.data) {
      snapshotCache.set(cacheKey, res.data.data);
      replay.data = res.data.data;
    } else {
      replay.error = res.data?.message || "回放加载失败";
    }
  } catch {
    if (sequence !== replaySequence) return;
    replay.error = "网络或服务异常，请稍后重试";
  } finally {
    if (sequence === replaySequence) replay.loading = false;
  }
};

const gotoStep = async (index: number) => {
  if (index < 0 || index >= replay.steps.length) return;
  replay.stepIndex = index;
  await loadStepSnapshot(index);
};

const stopPlayback = () => {
  if (playTimer !== null) {
    window.clearInterval(playTimer);
    playTimer = null;
  }
  replay.playing = false;
};

const togglePlayback = () => {
  if (replay.playing) {
    stopPlayback();
    return;
  }
  // 播放到末尾后再次播放：回到第 1 步
  if (replay.stepIndex >= replay.steps.length - 1) {
    replay.stepIndex = 0;
  }
  replay.playing = true;
  void loadStepSnapshot(replay.stepIndex);
  playTimer = window.setInterval(() => {
    if (replay.stepIndex >= replay.steps.length - 1) {
      stopPlayback();
      return;
    }
    replay.stepIndex += 1;
    void loadStepSnapshot(replay.stepIndex);
  }, PLAY_INTERVAL_MS);
};

const stepPrev = () => {
  stopPlayback();
  void gotoStep(replay.stepIndex - 1);
};

const stepNext = () => {
  stopPlayback();
  void gotoStep(replay.stepIndex + 1);
};

const onSliderChange = (value: number | number[] | undefined) => {
  stopPlayback();
  const index = Array.isArray(value) ? value[0] : Number(value);
  if (!Number.isFinite(index)) return;
  void gotoStep(index);
};

const sliderTooltip = (value: number) =>
  replay.steps[value] ? clockTime(replay.steps[value].time) : "";

const retryStepSnapshot = () => {
  void loadStepSnapshot(replay.stepIndex);
};

const reopenReplay = async () => {
  await loadReplaySteps();
  if (replay.steps.length) {
    await gotoStep(Math.min(replay.stepIndex, replay.steps.length - 1));
  }
};

const openReplayForEvent = async (eventId: number | string) => {
  replay.visible = true;
  replay.anchorEventId = eventId;
  replay.data = null;
  replay.error = "";
  replay.steps = [];
  replay.stepIndex = 0;
  replay.selectedName = "";
  replay.selectedId = "";
  replay.eventsError = "";
  await loadReplaySteps();
  if (replay.steps.length === 0) return;
  const index = replay.steps.findIndex((step) =>
    step.events.some((e) => String(e.id) === String(eventId))
  );
  await gotoStep(index >= 0 ? index : 0);
};

const selectReplayNode = (node: { name: string; id?: string | null }) => {
  replay.selectedName = node.name;
  replay.selectedId = node.id || node.name;
};

const onReplayNodeClick = (data: {
  name?: string;
  id?: string | number | null;
}) => {
  if (!data?.name) return;
  selectReplayNode({
    name: data.name,
    id: data.id == null ? null : String(data.id),
  });
};

// 回放图谱 = 当前拓扑 + 该时点历史掌握状态叠加（只读投影，无写操作入口）
const replayGraph = computed(() => {
  const graph = replay.data?.graph;
  if (!graph) return { nodes: [], links: [], categories: [] };
  return {
    nodes: graph.nodes.map((node) => ({
      id: node.id || node.name,
      name: node.name,
      category: node.category || "",
      chapter: node.chapter || "",
      description: node.description || "",
      status: node.status || "GRAY",
    })),
    links: graph.links ?? [],
    categories: graph.categories ?? [],
  };
});

const replayGraphHeight = computed(() =>
  viewportWidth.value < 768 ? "300px" : "420px"
);

const replaySelectedNode = computed(() => {
  const graph = replay.data?.graph;
  if (!graph || !replay.selectedName) return null;
  return graph.nodes.find((node) => node.name === replay.selectedName) ?? null;
});

/** 按该时点状态推导的只读参考建议（不写库、不触发动作） */
const replaySuggestion = computed(() => {
  const node = replaySelectedNode.value;
  if (!node) return "";
  if (node.snapshotAvailable === false) {
    return "该节点在此时间点只有事件记录，细分掌握状态未记录。";
  }
  if (node.status === "GREEN")
    return "该时点已掌握，可作为后续知识的前置基础。";
  if (node.status === "YELLOW") return "该时点待巩固，建议针对性练习。";
  if (node.status === "RED") return "该时点薄弱，需要优先补救。";
  return "该时点还没有学习证据。";
});

const currentStep = computed(() => replay.steps[replay.stepIndex] ?? null);

/** 已学节点 = 回放投影中带快照标记（true/false）的节点 */
const learnedNodes = (snapshot: MasterySnapshotVO) =>
  snapshot.graph.nodes.filter((n) => n.snapshotAvailable != null);

// ---------- 判题提交详情 ----------

const submitDetail = reactive({
  visible: false,
  loading: false,
  error: "",
  data: null as QuestionSubmitVO | null,
});
let submitDetailSequence = 0;
watch(
  () => submitDetail.visible,
  (visible) => {
    if (!visible) {
      submitDetailSequence += 1;
      submitDetail.loading = false;
    }
  }
);

const openSubmitDetail = async (questionSubmitId: number | string) => {
  const sequence = ++submitDetailSequence;
  submitDetail.visible = true;
  submitDetail.loading = true;
  submitDetail.error = "";
  submitDetail.data = null;
  try {
    const res = await QuestionControllerService.getQuestionSubmitByIdUsingGet(
      questionSubmitId as number
    );
    if (sequence !== submitDetailSequence) return;
    if (res.code === 0) {
      submitDetail.data = res.data ?? null;
    } else {
      submitDetail.error = res.message || "提交详情加载失败";
    }
  } catch {
    if (sequence !== submitDetailSequence) return;
    submitDetail.error = "网络或服务异常，请稍后重试";
  } finally {
    if (sequence === submitDetailSequence) submitDetail.loading = false;
  }
};

// ---------- 展示工具 ----------

const formatTime = (time: string) =>
  time ? new Date(time).toLocaleString() : "";

const percentText = (value: number | null | undefined) =>
  value === null || value === undefined ? "-" : `${Math.round(value * 100)}%`;

const eventTypeName = (type: string) => {
  const map: Record<string, string> = {
    ATTEMPT: "题目练习",
    JUDGE: "编程实验判题",
    SIMULATION: "交互模拟（判分）",
    SIMULATION_RUN: "算法演示运行",
    TASK_ACCEPTED: "接受任务",
    TASK_SUBMISSION: "任务提交",
    UNIT_COMPLETED: "单元活动",
    STATE_BASELINE: "状态基线",
  };
  return map[type] || type;
};
// 模板以活动语义命名；与事件类型共用同一映射。
const activityTypeName = eventTypeName;

const resultText = (result: string | null | undefined) => {
  const map: Record<string, string> = {
    PASS: "通过",
    FAIL: "未通过",
    SYSTEM_ERROR: "系统错误",
    BASELINE: "状态基线",
    ACCEPTED: "已接受",
    IN_PROGRESS: "进行中",
    SUBMITTED: "已提交",
    RETURNED: "已退回",
    REVIEWED: "已批阅",
    COMPLETED: "已完成",
    SIMULATION_RUN: "运行完成",
  };
  return map[result || ""] || result || "";
};

const resultColor = (result: string | null | undefined) => {
  const map: Record<string, string> = {
    PASS: "green",
    FAIL: "red",
    SYSTEM_ERROR: "gray",
    BASELINE: "gray",
    ACCEPTED: "green",
    IN_PROGRESS: "blue",
    SUBMITTED: "blue",
    RETURNED: "orange",
    REVIEWED: "green",
    COMPLETED: "green",
    SIMULATION_RUN: "gray",
  };
  return map[result || ""] || "gray";
};

const statusText = (status: string) => {
  const map: Record<string, string> = {
    GRAY: "未学习",
    RED: "薄弱",
    YELLOW: "待巩固",
    GREEN: "已掌握",
  };
  return map[status] || status;
};

const statusPillClass = (status: string) => {
  const map: Record<string, string> = {
    GRAY: "pill-gray",
    RED: "pill-red",
    YELLOW: "pill-yellow",
    GREEN: "pill-green",
  };
  return map[status] || "pill-gray";
};

const failureStateText = (state: string) => {
  const map: Record<string, string> = {
    CURRENT: "当前失误",
    HISTORICAL: "历史失误",
    CORRECTED: "已纠正",
    NONE: "无失败记录",
  };
  return map[state] || state;
};

const failurePillClass = (state: string) => {
  const map: Record<string, string> = {
    CURRENT: "pill-red",
    HISTORICAL: "pill-yellow",
    CORRECTED: "pill-green",
    NONE: "pill-gray",
  };
  return map[state] || "pill-gray";
};

const dateGroups = computed(() => {
  const groups = new Map<string, LearningActivityVO[]>();
  activities.value.forEach((activity) => {
    const date = new Date(activity.occurredAt).toLocaleDateString("zh-CN");
    if (!groups.has(date)) groups.set(date, []);
    groups.get(date)?.push(activity);
  });
  return Array.from(groups, ([date, items]) => ({ date, activities: items }));
});
const clockTime = (time: string) =>
  new Date(time).toLocaleTimeString("zh-CN", { hour12: false });

const prettyJson = (value: string | null | undefined) => {
  if (!value) return "—";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

const openActivity = (activity: LearningActivityVO) => {
  if (activity.returnRoute && activity.returnRoute.startsWith("/")) {
    router.push(activity.returnRoute);
    return;
  }
  if (activity.resourceType === "QUESTION_SUBMIT" && activity.resourceId) {
    openSubmitDetail(activity.resourceId);
  }
};

/** 普通演示记录：从输入快照恢复同一算法与参数 */
const restoreRun = (activity: LearningActivityVO) => {
  if (!activity.algorithmKey || !activity.inputSnapshot) {
    if (activity.returnRoute) router.push(activity.returnRoute);
    return;
  }
  let input: Record<string, unknown>;
  try {
    input = JSON.parse(activity.inputSnapshot);
    if (!input || typeof input !== "object" || Array.isArray(input)) {
      throw new Error("invalid snapshot");
    }
  } catch {
    if (activity.returnRoute) router.push(activity.returnRoute);
    return;
  }
  const query: Record<string, string> = {
    algorithmKey: activity.algorithmKey,
  };
  if (activity.courseCode) query.courseCode = activity.courseCode;
  if (activity.unitCode) query.sourceUnitCode = activity.unitCode;
  if (activity.activityCode) query.sourceActivityCode = activity.activityCode;
  if (Array.isArray(input.processes)) {
    query.processes = JSON.stringify(input.processes);
    query.timeSlice = String(input.quantum ?? 2);
    if (Array.isArray(input.mlfqQueues)) {
      query.queueSlices = (input.mlfqQueues as number[]).join(",");
    }
  } else if (Array.isArray(input.operations)) {
    query.memorySize = String(input.memorySize ?? 0);
    query.operations = JSON.stringify(input.operations);
    if (Array.isArray(input.initialPartitions)) {
      query.initialPartitions = (input.initialPartitions as number[]).join(",");
    }
  } else if (typeof input.frames !== "undefined") {
    query.frames = String(input.frames);
    query.accessSequence =
      (input.sequence as number[] | undefined)?.join(",") || "";
  } else {
    query.head = String(input.head ?? 1);
    query.requests = (input.requests as number[] | undefined)?.join(",") || "";
    query.direction = String(input.direction || "UP");
  }
  router.push({ path: "/experiments/simulation", query });
};

const failureSummary = (failure: LastFailureVO) => {
  const typeMap: Record<string, string> = {
    LEARNING_QUESTION: "测评题目",
    QUESTION_SUBMIT: "编程实验",
    SIMULATION: "交互模拟",
    STATE_BASELINE: "状态基线",
  };
  const parts = [
    typeMap[failure.resourceType] || failure.resourceType,
    resultText(failure.result),
  ];
  if (failure.createTime) {
    parts.push(formatTime(failure.createTime));
  }
  return parts.join(" · ");
};

const goNode = (nodeName: string) => {
  router.push(`/knowledge/node/${encodeURIComponent(nodeName)}`);
};

// ---------- 初始化：route query 预筛选（兼容 /question_submit 旧跳转） ----------

watch(
  () => [route.query.nodeName, route.query.eventType],
  () => {
    filters.nodeName =
      typeof route.query.nodeName === "string" ? route.query.nodeName : "";
    const preset = route.query.eventType;
    filters.eventType = "";
    if (typeof preset === "string" && EVENT_TYPE_OPTIONS.includes(preset)) {
      filters.eventType = preset;
    }
    current.value = 1;
    if (activeView.value === "timeline") void loadTimeline();
  },
  { immediate: true }
);
watch(
  activeView,
  (view, previous) => {
    if (view === "review") {
      if (!weakLoaded.value) void loadWeakness();
    } else if (previous !== undefined) void loadTimeline();
  },
  { immediate: true }
);
watch(
  () => route.query.eventId,
  (id) => {
    if (typeof id === "string" && /^\d+$/.test(id)) {
      void openReplayForEvent(id);
    }
  },
  { immediate: true }
);
onMounted(() => {
  void loadCourses();
});
onBeforeUnmount(() => {
  timelineSequence += 1;
  replaySequence += 1;
  submitDetailSequence += 1;
  stopPlayback();
  window.removeEventListener("resize", updateViewport);
});
</script>

<style scoped>
.learning-records-view {
  max-width: 1200px;
  margin: 0 auto;
}
.records-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 4px 0 18px;
}
.records-heading h1 {
  margin: 0;
  font-size: 22px;
  color: var(--oslab-text-title);
}

/* ---- 筛选栏 ---- */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 0 16px;
  border-bottom: 1px solid var(--oslab-border);
  margin-bottom: 16px;
}
.fallback-banner {
  margin-bottom: 14px;
}

/* ---- 空态 ---- */
.empty-state {
  padding: 40px 16px;
  text-align: center;
}
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--oslab-text-title);
  margin-bottom: 8px;
}

/* ---- 日期分组时间线 ---- */
.timeline-loading {
  padding: 24px 0;
}
.timeline-group {
  position: relative;
  margin: 24px 0;
}
.timeline-group::before {
  content: "";
  position: absolute;
  left: 95px;
  top: 39px;
  bottom: 0;
  width: 2px;
  background: #e5e6eb;
}
.timeline-date {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--oslab-text-title);
  font-weight: 600;
}
.timeline-event {
  position: relative;
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 38px;
  padding-bottom: 20px;
}
.event-time {
  padding-top: 6px;
  font-size: 12px;
  color: var(--oslab-text-secondary);
  font-variant-numeric: tabular-nums;
}
.event-dot {
  position: absolute;
  left: 91px;
  top: 9px;
  width: 10px;
  height: 10px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #86909c;
  box-sizing: border-box;
}
.dot-pass {
  background: #00b42a;
}
.dot-fail {
  background: #f53f3f;
}
.event-content {
  min-width: 0;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--oslab-divider);
}
.event-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.event-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--oslab-text-title);
  font-size: 14px;
  overflow-wrap: anywhere;
}
.event-heading .arco-btn {
  flex-shrink: 0;
}
.event-context {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-top: 8px;
  color: var(--oslab-text-secondary);
  font-size: 12px;
}
.mastery-comparison {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
}
.comparison-label {
  width: 44px;
  font-size: 12px;
  color: var(--oslab-text-secondary);
}
.comparison-value {
  flex: 0 1 120px;
  min-width: 80px;
  font-size: 12px;
  color: var(--oslab-text-body);
}
.comparison-track {
  height: 4px;
  background: #f2f3f5;
  margin-top: 6px;
  overflow: hidden;
}
.comparison-track i {
  display: block;
  height: 100%;
  background: #c9cdd4;
}
.comparison-track.after i {
  background: #2447d8;
}
.comparison-arrow {
  color: #86909c;
  width: 14px;
}
.event-evidence {
  margin: 12px 0 0;
  overflow-wrap: anywhere;
}
.event-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.activity-nodes,
.activity-run {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
  font-size: 12px;
}
.activity-nodes summary,
.activity-run summary {
  cursor: pointer;
  color: var(--oslab-text-secondary);
}
.activity-node-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 6px 0;
  border-top: 1px dashed var(--oslab-line-soft);
}
.activity-node-row:first-of-type {
  margin-top: 6px;
}
.activity-mastery {
  color: var(--oslab-text-secondary);
}
.run-snapshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  margin: 8px 0;
}
.run-snapshot-grid pre {
  max-height: 220px;
  overflow: auto;
  margin: 4px 0 0;
  padding: 8px;
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  color: var(--oslab-text-secondary);
  font-family: var(--oslab-font-mono, monospace);
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.cell-link {
  color: var(--oslab-primary);
  cursor: pointer;
}
.cell-link:hover {
  text-decoration: underline;
}
.cell-muted {
  color: var(--oslab-text-secondary);
}
.cell-mastery {
  font-size: 12px;
  white-space: nowrap;
}

/* ---- 分页 ---- */
.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ---- 待巩固卡片 ---- */
.weak-card {
  padding: 16px;
  margin-bottom: 12px;
}
.weak-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.weak-name {
  font-size: 15px;
  font-weight: 600;
}
.weak-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--oslab-text-body);
  margin-bottom: 10px;
}
.weak-failure {
  background: var(--oslab-panel-sub);
  border-radius: var(--oslab-radius-md);
  padding: 8px 12px;
  margin-bottom: 10px;
}
.failure-line {
  font-size: 12px;
  color: var(--oslab-text-body);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.basis-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--oslab-text-secondary);
  line-height: 1.8;
}
.weak-actions {
  display: flex;
  gap: 8px;
}

/* ---- 状态胶囊（与知识图谱页一致） ---- */
.status-pill {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 600;
  white-space: nowrap;
}
.pill-green {
  background: var(--oslab-green-bg);
  color: var(--oslab-status-green);
}
.pill-yellow {
  background: var(--oslab-amber-bg);
  color: var(--oslab-status-amber);
}
.pill-red {
  background: var(--oslab-red-bg);
  color: var(--oslab-status-red);
}
.pill-gray {
  background: var(--oslab-divider);
  color: var(--oslab-text-secondary);
}

/* ---- 回放弹窗 ---- */
.replay-meta {
  font-size: 13px;
  color: var(--oslab-text-title);
  font-weight: 600;
  margin-bottom: 4px;
}
.replay-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 12px 0;
  font-size: 13px;
  color: var(--oslab-text-body);
}
.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-green {
  background: var(--oslab-status-green);
}
.dot-yellow {
  background: var(--oslab-status-amber);
}
.dot-red {
  background: var(--oslab-status-red);
}
.dot-gray {
  background: var(--oslab-status-gray);
}
.replay-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr 84px 90px 76px 76px 150px;
  align-items: center;
  gap: 8px;
}
.replay-list-head {
  font-size: 12px;
  color: var(--oslab-text-secondary);
  padding: 6px 0;
  border-bottom: 1px solid var(--oslab-border);
}
.replay-row {
  font-size: 13px;
  color: var(--oslab-text-body);
  padding: 8px 0;
  border-bottom: 1px solid var(--oslab-divider);
}
.replay-footnote {
  margin-top: 12px;
}

/* ---- 回放：图谱 + 详情 + 时间轴 ---- */
.replay-modal-body {
  display: flex;
  flex-direction: column;
  max-height: min(78vh, 900px);
  overflow-y: auto;
}
.replay-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 12px;
  align-items: start;
  margin-top: 8px;
}
.replay-graph {
  min-width: 0;
}
.replay-graph-note {
  margin: 6px 0 0;
}
.replay-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--oslab-border);
  border-radius: var(--oslab-radius-md);
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.detail-name {
  color: var(--oslab-text-title);
  font-weight: 600;
  overflow-wrap: anywhere;
}
.detail-facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
  margin: 0;
}
.detail-fact dt {
  font-size: 12px;
  color: var(--oslab-text-secondary);
}
.detail-fact dd {
  margin: 0;
  font-size: 13px;
  color: var(--oslab-text-body);
  font-variant-numeric: tabular-nums;
}
.replay-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.replay-slider {
  flex: 1;
  margin: 0 8px;
}
.replay-current {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--oslab-text-body);
}
.current-step {
  color: var(--oslab-text-title);
  font-weight: 600;
  white-space: nowrap;
}
.replay-row {
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.replay-row:hover {
  background: var(--oslab-panel-soft);
}
.replay-row-active {
  background: var(--oslab-primary-soft);
}
.replay-list .replay-grid.replay-row {
  padding-left: 6px;
  padding-right: 6px;
}

/* ---- 提交详情 ---- */
.code-block {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  background: var(--oslab-panel-sub);
  border-radius: var(--oslab-radius-sm);
  padding: 8px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  text-align: left;
}
/* C+B 学习图册 */
.learning-records-view {
  gap: 16px;
}
.records-heading h1 {
  display: inline;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 30px;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
.filter-bar {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.event-content {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.event-title {
  color: var(--oslab-ink);
  font-weight: 600;
}
.event-evidence,
.failure-line {
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
}
.status-pill {
  border-radius: var(--oslab-radius-pill);
}
.pager-row {
  border-top: 1px solid var(--oslab-line-soft);
}

@media (max-width: 768px) {
  .replay-main {
    grid-template-columns: 1fr;
  }
  .replay-controls {
    flex-wrap: wrap;
  }
  .replay-slider {
    flex: 1 1 100%;
    order: 5;
    margin: 4px 0;
  }
}
@media (max-width: 640px) {
  .filter-bar > .arco-input-wrapper,
  .filter-bar > .arco-select-view,
  .filter-bar > .arco-picker {
    width: 100% !important;
  }
  .timeline-group::before {
    left: 5px;
  }
  .timeline-event {
    grid-template-columns: minmax(0, 1fr);
    gap: 5px;
    padding-left: 24px;
  }
  .event-dot {
    left: 1px;
  }
  .event-time {
    padding-top: 0;
  }
  .event-heading {
    gap: 8px;
  }
  .event-title {
    font-size: 13px;
  }
  .mastery-comparison {
    gap: 8px;
  }
  .comparison-label {
    width: 100%;
  }
  .replay-list {
    overflow-x: auto;
  }
  .replay-grid {
    min-width: 670px;
  }
  .pager-row {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
