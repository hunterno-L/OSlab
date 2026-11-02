<template>
  <div class="question-page">
    <CourseCatalogSidebar
      :course-code="courseCode"
      :course-title="courseTitle"
      :sections="catalogSections"
      :selected-section-code="selectedSectionCode"
      :selected-unit-code="selectedUnitCode"
      :expanded-sections="expandedSections"
      :loading="treeLoading"
      :error="treeError"
      :progress="courseProgress"
      @select-course="selectCourseScope"
      @select-section="selectSectionScope"
      @select-unit="selectUnitScope"
      @toggle-section="toggleSection"
      @retry="reloadTree"
    />

    <div class="question-main">
      <header class="page-head">
        <div class="page-head-main">
          <p class="page-context">
            {{ scopeLabel }} · 当前范围 <strong>{{ total }}</strong> 题<template
              v-if="studyStateLabel !== '全部状态'"
            >
              · {{ studyStateLabel }}</template
            >
          </p>
        </div>
        <div class="page-head-actions">
          <a-input-number
            v-model="runCount"
            :min="1"
            :max="20"
            :style="{ width: '104px' }"
          >
            <template #prefix>题数</template>
          </a-input-number>
          <a-tooltip content="使用当前目录、节点与作答状态范围开始连续测评">
            <a-button :loading="starting" :disabled="!total" @click="startRun"
              >连续测评</a-button
            >
          </a-tooltip>
        </div>
      </header>

      <div v-if="treeError" class="catalog-error" role="alert">
        <span>课程目录加载失败：{{ treeError }}</span>
        <a-button size="small" type="primary" @click="reloadTree"
          >重试</a-button
        >
      </div>

      <div v-if="catalogError" class="catalog-error" role="alert">
        <span>题目目录加载失败：{{ catalogError }}</span>
        <a-button size="small" type="primary" @click="refreshCatalog()"
          >重试</a-button
        >
      </div>

      <!-- 知识节点：来自 catalog.nodes，与教材目录共用稳定代码 -->
      <section class="filter-panel" aria-label="知识节点">
        <div class="filter-panel-head">
          <span class="filter-label">知识节点</span>
          <span class="filter-hint">按稳定 nodeCode 过滤</span>
          <span v-if="catalogNodes.length" class="filter-count">{{
            catalogNodes.length
          }}</span>
          <button
            v-if="catalogNodes.length > NODE_COLLAPSE_LIMIT"
            type="button"
            class="node-toggle"
            :aria-expanded="nodesExpanded"
            @click="nodesExpanded = !nodesExpanded"
          >
            {{ nodesExpanded ? "收起" : "展开全部" }}
            <span
              class="node-toggle-icon"
              :class="{ open: nodesExpanded }"
            />
          </button>
        </div>
        <div
          class="chip-row"
          :class="{ collapsed: !nodesExpanded && hiddenNodeCount > 0 }"
        >
          <button
            type="button"
            class="filter-chip"
            :class="{ active: !nodeParam }"
            :aria-pressed="!nodeParam"
            @click="selectNode(undefined)"
          >
            全部节点
            <span v-if="catalog" class="chip-count">{{
              catalog.totalQuestions
            }}</span>
          </button>
          <button
            v-for="node in visibleCatalogNodes"
            :key="nodeKeyOf(node)"
            type="button"
            class="filter-chip"
            :class="{ active: nodeParam === nodeKeyOf(node) }"
            :aria-pressed="nodeParam === nodeKeyOf(node)"
            :title="`共 ${node.questionCount} 题 · 已答 ${node.attempted} · 待复习 ${node.needsReview}`"
            @click="selectNode(nodeKeyOf(node))"
          >
            {{ node.displayName || node.nodeName }}
            <span class="chip-count">{{ node.questionCount }}</span>
            <span v-if="node.needsReview" class="chip-review"
              >待复习 {{ node.needsReview }}</span
            >
          </button>
          <span
            v-if="catalogLoading && !catalogNodes.length"
            class="filter-hint"
            >节点加载中…</span
          >
          <span
            v-else-if="catalog && !catalogNodes.length && !catalogError"
            class="filter-hint"
            >当前目录范围暂无可练习的节点</span
          >
        </div>
      </section>

      <section class="filter-row" aria-label="题目类型">
        <div class="state-chips">
          <span class="filter-label">题目类型</span>
          <button
            v-for="option in typeOptions"
            :key="option.value || 'ALL'"
            type="button"
            class="filter-chip"
            :class="{ active: typeParam === option.value }"
            :aria-pressed="typeParam === option.value"
            @click="selectType(option.value)"
          >
            {{ option.label }}
            <span v-if="typeCount(option.value)" class="chip-count">{{
              typeCount(option.value)
            }}</span>
          </button>
        </div>
      </section>

      <section class="filter-row" aria-label="题目筛选">
        <div class="state-chips">
          <span class="filter-label">作答状态</span>
          <button
            v-for="state in stateOptions"
            :key="state.value"
            type="button"
            class="filter-chip"
            :class="{ active: studyStateParam === state.value }"
            :aria-pressed="studyStateParam === state.value"
            @click="setStudyState(state.value)"
          >
            {{ state.label }}
          </button>
        </div>
        <div class="filter-spacer"></div>
        <a-input-search
          class="filter-search"
          aria-label="搜索题目标题"
          :input-attrs="{ 'aria-label': '搜索题目标题' }"
          :model-value="titleParam"
          placeholder="搜索题目标题"
          allow-clear
          @search="setTitle"
          @clear="setTitle('')"
        />
        <a-button v-if="hasActiveFilters" @click="resetFilters"
          >清除筛选</a-button
        >
      </section>

      <section ref="streamRef" class="question-stream" aria-label="题目列表">
        <div v-if="loading && !questions.length" class="oslab-loading">
          <span class="oslab-spinner"></span>
          <span>正在加载题目…</span>
        </div>
        <div v-else-if="loadError" class="oslab-error" role="alert">
          <span class="oslab-state-title">题目加载失败</span>
          <span class="oslab-state-hint">{{ loadError }}</span>
          <a-space>
            <a-button size="small" type="primary" @click="reload"
              >重试</a-button
            >
            <a-button v-if="hasActiveFilters" size="small" @click="resetFilters"
              >清除筛选</a-button
            >
          </a-space>
        </div>
        <div v-else-if="!total && !pinnedQuestion" class="oslab-empty">
          <span class="oslab-state-title">当前范围暂无可练习的题目</span>
          <span class="oslab-state-hint"
            >换一个单元、节点或作答状态后再试；未归入教材的旧题可在“全部节点”中找到。</span
          >
          <a-button v-if="nodeParam" size="small" @click="selectNode(undefined)"
            >查看全部节点</a-button
          >
        </div>
        <template v-else>
          <article
            v-for="question in streamQuestions"
            :id="`question-${question.id}`"
            :key="question.id"
            class="stream-item"
          >
            <p
              v-if="String(question.id) === pinnedQuestionId"
              class="stream-pin"
            >
              指定题目
            </p>
            <QuestionAnswerCard
              :question="question"
              :context-label="cardContextLabel"
              :source-unit-code="sourceContext.sourceUnitCode"
              :source-activity-code="sourceContext.sourceActivityCode"
              @answered="(result) => onAnswered(question, result)"
            />
          </article>

          <div v-if="loading && questions.length" class="oslab-loading">
            <span class="oslab-spinner"></span><span>正在翻页…</span>
          </div>

          <footer v-if="total > PAGE_SIZE" class="stream-footer">
            <span class="side-hint"
              >第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 题</span
            >
            <a-pagination
              :current="page"
              :page-size="PAGE_SIZE"
              :total="total"
              :show-total="false"
              @change="onPageChange"
            />
          </footer>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { Message } from "@arco-design/web-vue";
import { useRoute, useRouter } from "vue-router";
import CourseCatalogSidebar from "@/components/learning/CourseCatalogSidebar.vue";
import QuestionAnswerCard from "@/components/learning/QuestionAnswerCard.vue";
import { useCourseCatalog } from "@/composables/useCourseCatalog";
import { courseContext } from "@/utils/courseContext";
import {
  getLearningCatalog,
  getLearningQuestion,
  listLearningQuestions,
  startAssessment,
  type LearningAnswerResultVO,
  type LearningCatalogNodeVO,
  type LearningCatalogVO,
  type LearningQuestionQueryRequest,
  type LearningQuestionVO,
} from "@/api/learning";

type StudyState = "ALL" | "UNANSWERED" | "NEEDS_REVIEW" | "CORRECT";

const PAGE_SIZE = 10;
const contextKeys = [
  "returnTo",
  "sourceUnitCode",
  "sourceActivityCode",
  "courseMode",
  "source",
];

const route = useRoute();
const router = useRouter();

// ---------- 课程目录（与图谱页、单元页共用同一壳） ----------
const {
  courseCode,
  courseTitle,
  sections: catalogSections,
  loading: treeLoading,
  error: treeError,
  courseProgress,
  sectionByCode,
  unitByCode,
  load: loadCourseCatalog,
  ancestorCodes,
  sectionCrumb,
} = useCourseCatalog();

const expandedSections = ref<string[]>([]);
const treeReady = ref(false);

const queryValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const courseParam = computed(() => queryValue(route.query.courseCode));
const sectionParam = computed(() => queryValue(route.query.sectionCode));
const unitParam = computed(() => queryValue(route.query.unitCode));
const nodeParam = computed(
  () => queryValue(route.query.nodeCode) || queryValue(route.query.nodeName)
);
const titleParam = computed(() => queryValue(route.query.title));
const page = computed(() => {
  const value = Number(queryValue(route.query.current));
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 1;
});
const studyStateParam = computed<StudyState>(() => {
  const value = queryValue(route.query.studyState);
  return stateOptions.some((item) => item.value === value)
    ? (value as StudyState)
    : "ALL";
});

const typeParam = computed(() => queryValue(route.query.type).toUpperCase());

const selectedUnitCode = computed(() => {
  const code = unitParam.value;
  return code && unitByCode.value.has(code) ? code : "";
});
const selectedSectionCode = computed(() => {
  if (selectedUnitCode.value) {
    return unitByCode.value.get(selectedUnitCode.value)?.sectionCode || "";
  }
  const code = sectionParam.value;
  return code && sectionByCode.value.has(code) ? code : "";
});

const stateOptions: Array<{ value: StudyState; label: string }> = [
  { value: "ALL", label: "全部状态" },
  { value: "UNANSWERED", label: "未作答" },
  { value: "NEEDS_REVIEW", label: "待复习" },
  { value: "CORRECT", label: "已答对" },
];

const typeOptions: Array<{ value: string; label: string }> = [
  { value: "", label: "全部" },
  { value: "CHOICE", label: "选择题" },
  { value: "JUDGE", label: "判断题" },
  { value: "FILL_BLANK", label: "填空题" },
  { value: "COMPREHENSIVE", label: "综合题" },
];
const studyStateLabel = computed(
  () =>
    stateOptions.find((item) => item.value === studyStateParam.value)?.label ||
    "全部状态"
);

const sourceContext = computed(() => courseContext(route.query));

const scopeLabel = computed(() => {
  if (selectedUnitCode.value) {
    return `单元「${
      unitByCode.value.get(selectedUnitCode.value)?.title ||
      selectedUnitCode.value
    }」`;
  }
  if (selectedSectionCode.value) {
    return sectionCrumb(selectedSectionCode.value) || "当前小节";
  }
  return courseTitle.value || "全部课程";
});

const cardContextLabel = computed(() => {
  const sourceUnit = sourceContext.value.sourceUnitCode
    ? unitByCode.value.get(sourceContext.value.sourceUnitCode)
    : null;
  if (sourceUnit) return `单元 · ${sourceUnit.title}`;
  if (selectedUnitCode.value) {
    const unit = unitByCode.value.get(selectedUnitCode.value);
    if (unit) return `单元 · ${unit.title}`;
  }
  return "";
});

// ---------- 目录写回（保留来源/返回上下文，丢弃旧分类参数） ----------
const replaceFilters = (
  patch: Record<string, string | undefined>,
  resetPage = false
) => {
  const next: Record<string, string> = {};
  for (const key of contextKeys) {
    const value = queryValue(route.query[key]);
    if (value) next[key] = value;
  }
  const state: Record<string, string | undefined> = {
    courseCode: courseParam.value || courseCode.value || undefined,
    sectionCode: sectionParam.value || undefined,
    unitCode: unitParam.value || undefined,
    nodeCode: queryValue(route.query.nodeCode) || undefined,
    studyState:
      studyStateParam.value === "ALL" ? undefined : studyStateParam.value,
    type: typeParam.value || undefined,
    title: titleParam.value || undefined,
    current: page.value > 1 ? String(page.value) : undefined,
    ...patch,
  };
  for (const [key, value] of Object.entries(state)) {
    if (value !== undefined && value !== "") next[key] = String(value);
  }
  if (resetPage) delete next.current;
  if (!next.studyState) delete next.studyState;
  if (!next.title) delete next.title;
  // 旧名称参数统一迁移到 nodeCode
  delete next.nodeName;
  const target = router.resolve({ path: route.path, query: next }).fullPath;
  if (target !== route.fullPath) void router.replace({ query: next });
};

const toggleSection = (sectionCode: string) => {
  const next = new Set(expandedSections.value);
  if (next.has(sectionCode)) next.delete(sectionCode);
  else next.add(sectionCode);
  expandedSections.value = Array.from(next);
};

const expandSectionPath = (sectionCode?: string) => {
  if (!sectionCode) return;
  expandedSections.value = Array.from(
    new Set([...expandedSections.value, ...ancestorCodes(sectionCode)])
  );
};

const selectCourseScope = () =>
  replaceFilters(
    {
      courseCode: courseCode.value || undefined,
      sectionCode: undefined,
      unitCode: undefined,
      nodeCode: undefined,
    },
    true
  );

const selectSectionScope = (sectionCode: string) => {
  expandSectionPath(sectionCode);
  replaceFilters(
    { sectionCode, unitCode: undefined, nodeCode: undefined },
    true
  );
};

const selectUnitScope = (unitCode: string) => {
  const unit = unitByCode.value.get(unitCode);
  if (!unit) return;
  expandSectionPath(unit.sectionCode);
  replaceFilters(
    { sectionCode: unit.sectionCode, unitCode, nodeCode: undefined },
    true
  );
};

const selectNode = (nodeCode?: string) =>
  replaceFilters({ nodeCode: nodeCode || undefined }, true);

const selectType = (value: string) =>
  replaceFilters({ type: value || undefined, current: undefined }, true);

const typeCount = (value: string) => {
  const counts = catalog.value?.typeCounts;
  if (!counts) return 0;
  return counts[value || "ALL"] ?? 0;
};

const setStudyState = (state: StudyState) => {
  replaceFilters({ studyState: state === "ALL" ? undefined : state }, true);
};

const setTitle = (value: string) => {
  const title = (value || "").trim();
  replaceFilters({ title: title || undefined }, true);
};

const resetFilters = () =>
  replaceFilters(
    { nodeCode: undefined, studyState: undefined, title: undefined },
    true
  );

const hasActiveFilters = computed(
  () =>
    Boolean(nodeParam.value) ||
    studyStateParam.value !== "ALL" ||
    Boolean(titleParam.value)
);

// ---------- 题目目录与列表 ----------
const catalog = ref<LearningCatalogVO | null>(null);
const catalogLoading = ref(false);
const catalogError = ref("");
let catalogEpoch = 0;

const questions = ref<LearningQuestionVO[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref("");
const streamRef = ref<HTMLElement | null>(null);
let listEpoch = 0;
let refreshEpoch = 0;

const pinnedQuestion = ref<LearningQuestionVO | null>(null);
const pinnedQuestionId = computed(() => queryValue(route.query.questionId));
let pinEpoch = 0;

const catalogNodes = computed<LearningCatalogNodeVO[]>(
  () => catalog.value?.nodes || []
);

const nodeKeyOf = (node: LearningCatalogNodeVO) =>
  node.nodeCode || node.nodeName;

// 节点过多时默认收起：保留前 12 个，当前选中的节点始终可见
const NODE_COLLAPSE_LIMIT = 12;
const nodesExpanded = ref(false);
const visibleCatalogNodes = computed<LearningCatalogNodeVO[]>(() => {
  const nodes = catalogNodes.value;
  if (nodesExpanded.value || nodes.length <= NODE_COLLAPSE_LIMIT) return nodes;
  const head = nodes.slice(0, NODE_COLLAPSE_LIMIT);
  const selected = nodes.find(
    (node) => nodeKeyOf(node) === nodeParam.value
  );
  if (selected && !head.includes(selected)) head.push(selected);
  return head;
});
const hiddenNodeCount = computed(
  () => catalogNodes.value.length - visibleCatalogNodes.value.length
);
watch([selectedSectionCode, selectedUnitCode], () => {
  nodesExpanded.value = false;
});

const pageCount = computed(() =>
  Math.max(1, Math.ceil(total.value / PAGE_SIZE))
);

const streamQuestions = computed(() => {
  const pinned = pinnedQuestion.value;
  if (
    !pinned ||
    questions.value.some((item) => String(item.id) === String(pinned.id))
  ) {
    return questions.value;
  }
  return [pinned, ...questions.value];
});

const effectiveScope = () => {
  const unitCode = selectedUnitCode.value || undefined;
  const sectionCode =
    selectedSectionCode.value || sectionParam.value || undefined;
  return {
    courseCode: courseCode.value || courseParam.value || undefined,
    sectionCode,
    unitCode,
    nodeCode: nodeParam.value || undefined,
    studyState:
      studyStateParam.value === "ALL" ? undefined : studyStateParam.value,
    type: typeParam.value || undefined,
    title: titleParam.value || undefined,
  };
};

const refreshCatalog = async (normalizeNode = true) => {
  const epoch = ++catalogEpoch;
  const scope = effectiveScope();
  catalogLoading.value = true;
  catalogError.value = "";
  try {
    const res = await getLearningCatalog({
      courseCode: scope.courseCode,
      sectionCode: scope.sectionCode,
      unitCode: scope.unitCode,
      studyState: scope.studyState,
      type: scope.type,
    });
    if (epoch !== catalogEpoch) return false;
    if (res.data?.code === 0 && res.data.data) {
      catalog.value = res.data.data;
      const nodes = res.data.data.nodes || [];
      const current = nodeParam.value;
      if (normalizeNode && current && nodes.length) {
        const hit = nodes.find(
          (node) =>
            node.nodeCode === current ||
            node.nodeName === current ||
            node.displayName === current
        );
        if (!hit) {
          // 目录切换后原节点不在新范围：清除节点筛选并保留其他条件
          replaceFilters({ nodeCode: undefined }, true);
          return true;
        }
        if (!queryValue(route.query.nodeCode) && hit.nodeCode) {
          replaceFilters({ nodeCode: hit.nodeCode }, false);
          return true;
        }
      }
    } else {
      catalogError.value = res.data?.message || "题目目录加载失败";
    }
  } catch (error) {
    if (epoch === catalogEpoch) {
      catalogError.value =
        error instanceof Error ? error.message : "题目目录加载失败";
    }
  } finally {
    if (epoch === catalogEpoch) catalogLoading.value = false;
  }
  return false;
};

const loadList = async () => {
  const epoch = ++listEpoch;
  const scope = effectiveScope();
  const request: LearningQuestionQueryRequest = {
    current: page.value,
    pageSize: PAGE_SIZE,
    courseCode: scope.courseCode,
    sectionCode: scope.sectionCode,
    unitCode: scope.unitCode,
    nodeCode: scope.nodeCode,
    title: scope.title,
    type: scope.type,
    studyState: scope.studyState as LearningQuestionQueryRequest["studyState"],
  };
  loading.value = true;
  loadError.value = "";
  try {
    const res = await listLearningQuestions(request);
    if (epoch !== listEpoch) return;
    if (res.data?.code === 0) {
      questions.value = res.data.data?.records || [];
      total.value = res.data.data?.total || 0;
      if (!questions.value.length && total.value > 0 && page.value > 1) {
        replaceFilters({ current: undefined }, false);
        return;
      }
      await focusRequestedQuestion();
    } else {
      loadError.value = res.data?.message || "加载题目失败，请稍后重试";
    }
  } catch (error) {
    if (epoch === listEpoch) {
      loadError.value =
        error instanceof Error ? error.message : "网络异常，请稍后重试";
    }
  } finally {
    if (epoch === listEpoch) loading.value = false;
  }
};

const focusRequestedQuestion = async () => {
  const id = pinnedQuestionId.value;
  if (!id) {
    pinnedQuestion.value = null;
    return;
  }
  if (questions.value.some((item) => String(item.id) === id)) {
    pinnedQuestion.value = null;
    await nextTick();
    document
      .getElementById(`question-${id}`)
      ?.scrollIntoView({ block: "center" });
    return;
  }
  const epoch = ++pinEpoch;
  try {
    const res = await getLearningQuestion(id);
    if (epoch !== pinEpoch) return;
    if (res.data?.code === 0 && res.data.data) {
      pinnedQuestion.value = res.data.data;
      await nextTick();
      document
        .getElementById(`question-${id}`)
        ?.scrollIntoView({ block: "start" });
    } else {
      pinnedQuestion.value = null;
    }
  } catch {
    if (epoch === pinEpoch) pinnedQuestion.value = null;
  }
};

const refreshAll = async () => {
  const epoch = ++refreshEpoch;
  // 课程切换或首次进入：加载课程目录并归一 URL
  if (courseParam.value && courseParam.value !== courseCode.value) {
    await loadCourseCatalog(courseParam.value);
    if (epoch !== refreshEpoch) return;
  } else if (!courseCode.value) {
    await loadCourseCatalog(courseParam.value || undefined);
    if (epoch !== refreshEpoch) return;
  }
  if (!courseParam.value && courseCode.value) {
    replaceFilters({ courseCode: courseCode.value }, false);
    return;
  }
  if (
    courseParam.value &&
    courseCode.value &&
    courseParam.value !== courseCode.value
  ) {
    replaceFilters({ courseCode: courseCode.value }, false);
    return;
  }
  // 无效的旧 section/unit 深链回落到课程范围
  const unitInvalid = Boolean(unitParam.value) && !selectedUnitCode.value;
  const sectionInvalid =
    Boolean(sectionParam.value) && !selectedSectionCode.value;
  if (sectionInvalid) {
    replaceFilters(
      { sectionCode: undefined, unitCode: undefined, nodeCode: undefined },
      false
    );
    return;
  }
  if (unitInvalid) {
    replaceFilters({ unitCode: undefined, nodeCode: undefined }, false);
    return;
  }
  expandSectionPath(selectedSectionCode.value);
  const changed = await refreshCatalog();
  if (epoch !== refreshEpoch || changed) return;
  await loadList();
};

const reload = () => {
  void refreshAll();
};

const reloadTree = async () => {
  await loadCourseCatalog(courseCode.value || courseParam.value || undefined);
  void refreshAll();
};

const onPageChange = (next: number) => {
  replaceFilters(
    { current: next > 1 ? String(next) : undefined, questionId: undefined },
    false
  );
  void nextTick(() =>
    streamRef.value?.scrollIntoView({ block: "start", behavior: "smooth" })
  );
};

const onAnswered = (
  question: LearningQuestionVO,
  result: LearningAnswerResultVO
) => {
  question.hasAttempted = true;
  question.attemptCount = (question.attemptCount || 0) + 1;
  if (result.correct === null) {
    // 综合应用题：保存作答但不自动判分
    Message.info("已保存作答，综合题不自动评分");
  } else {
    question.latestResult = result.correct ? "CORRECT" : "WRONG";
  }
  // 只刷新目录计数，不重载列表：避免其他题卡与当前反馈被冲掉
  void refreshCatalog(false);
};

// ---------- 连续测评（继承同一范围；不是练习列表主界面） ----------
const runCount = ref(5);
const starting = ref(false);

const startRun = async () => {
  const scope = effectiveScope();
  starting.value = true;
  try {
    const res = await startAssessment({
      courseCode: scope.courseCode,
      sectionCode: scope.sectionCode,
      unitCode: scope.unitCode,
      nodeCode: scope.nodeCode,
      studyState: scope.studyState || "ALL",
      count: runCount.value,
    });
    if (res.data?.code === 0 && res.data.data?.runId) {
      const query: Record<string, string> = {
        returnTo: route.fullPath,
      };
      for (const key of [
        "courseCode",
        "sectionCode",
        "unitCode",
        "nodeCode",
        "studyState",
        "title",
        "current",
      ]) {
        const value = queryValue(route.query[key]);
        if (value) query[key] = value;
      }
      if (scope.studyState) query.studyState = scope.studyState;
      await router.push({
        path: `/assessments/run/${res.data.data.runId}`,
        query,
      });
    } else {
      Message.error(res.data?.message || "开始连续测评失败");
    }
  } catch (error) {
    Message.error(
      error instanceof Error ? error.message : "开始连续测评失败，请稍后重试"
    );
  } finally {
    starting.value = false;
  }
};

// ---------- 路由驱动：筛选/翻页变化只保留最后一次请求结果 ----------
watch(
  () =>
    [
      courseParam.value,
      sectionParam.value,
      unitParam.value,
      nodeParam.value,
      studyStateParam.value,
      typeParam.value,
      titleParam.value,
      page.value,
      pinnedQuestionId.value,
    ].join("|"),
  () => {
    if (!treeReady.value) return;
    void refreshAll();
  }
);

onMounted(async () => {
  await loadCourseCatalog(courseParam.value || undefined);
  treeReady.value = true;
  if (!courseParam.value && courseCode.value) {
    replaceFilters({ courseCode: courseCode.value }, false);
    return;
  }
  void refreshAll();
});
</script>

<style scoped>
.question-page {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 4px 0 40px;
}
.question-main {
  flex: 1;
  min-width: 0;
  max-width: 940px;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
}
.page-context {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.page-head-actions {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
}
.catalog-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
  padding: 10px 14px;
  border: 1px solid var(--oslab-weak);
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-weak-soft);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.filter-panel {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-3);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-panel);
}
.filter-panel-head {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
}
.filter-count {
  padding: 0 7px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-weak-soft);
  color: var(--oslab-muted-strong);
  font-size: 11px;
  line-height: 18px;
  font-variant-numeric: tabular-nums;
}
.node-toggle {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  line-height: 1.5;
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease);
}
.node-toggle:hover {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.node-toggle-icon {
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid currentColor;
  transition: transform var(--oslab-duration-fast) var(--oslab-ease);
}
.node-toggle-icon.open {
  transform: rotate(180deg);
}
.filter-label {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  font-weight: 500;
  white-space: nowrap;
}
.filter-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.chip-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
  max-height: 2000px;
  transition: max-height 0.32s var(--oslab-ease);
}
.chip-row.collapsed {
  max-height: 140px;
  overflow: hidden;
  -webkit-mask-image: linear-gradient(
    to bottom,
    #000 72%,
    transparent 100%
  );
  mask-image: linear-gradient(to bottom, #000 72%, transparent 100%);
}
.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  line-height: 1.5;
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}
.filter-chip:hover {
  border-color: var(--oslab-muted-strong);
}
.filter-chip.active {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-weight: 500;
}
.chip-count {
  color: var(--oslab-muted-strong);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.filter-chip.active .chip-count {
  color: var(--oslab-primary);
}
.chip-review {
  padding: 0 6px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;
}
.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
}
.state-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.filter-spacer {
  flex: 1;
}
.filter-search {
  width: 220px;
}
.question-stream {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
  scroll-margin-top: 16px;
}
.stream-item {
  scroll-margin-top: 16px;
}
.stream-pin {
  margin: 0 0 6px;
  color: var(--oslab-primary);
  font-size: var(--oslab-text-small);
}
.stream-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
  padding-top: var(--oslab-space-2);
}
/* C+B 学习图册 */
.question-page {
  gap: 16px;
}
.filter-panel,
.filter-row,
.question-stream {
  width: 100%;
  max-width: 1120px;
}
.question-main {
  min-width: 0;
}
.page-head {
  padding: 0 0 12px;
  border-bottom: 1px solid var(--oslab-line);
}
.filter-panel {
  padding: 12px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.filter-chip {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
}
.filter-chip.active {
  background: var(--oslab-highlight-soft);
  border-color: var(--oslab-highlight);
  color: var(--oslab-ink);
  font-weight: 600;
}
.chip-review {
  color: var(--oslab-annotation);
}
.question-stream {
  gap: 14px;
}
.stream-item {
  margin: 0;
}

@media (max-width: 1024px) {
  .question-page {
    display: block;
  }
  .question-main {
    max-width: none;
  }
}
@media (max-width: 760px) {
  .page-head-actions {
    width: 100%;
    justify-content: flex-start;
  }
  .filter-search {
    width: 100%;
  }
  .filter-spacer {
    display: none;
  }
  .filter-chip {
    padding: 5px 9px;
  }
}
</style>
