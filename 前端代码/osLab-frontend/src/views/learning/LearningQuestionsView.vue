<template>
  <div class="questions-flow">
    <nav class="page-breadcrumb" aria-label="面包屑">
      <span>操作系统</span>
      <span class="crumb-sep" aria-hidden="true">/</span>
      <span class="crumb-current">题目练习</span>
    </nav>

    <header class="page-head">
      <h1 class="oslab-page-title">题目练习</h1>
      <p class="page-context">
        <template v-if="catalog">共 {{ catalog.totalQuestions }} 题</template>
        <template v-else-if="catalogLoading">正在加载目录…</template>
        <template v-else>目录暂不可用</template>
        <template v-if="contextText"> · {{ contextText }}</template>
      </p>
    </header>

    <!-- 目录失败：给出真实重试动作，不影响题目列表与答题区 -->
    <div v-if="catalogError" class="catalog-error" role="alert">
      <span>章节目录加载失败：{{ catalogError }}</span>
      <a-button size="small" type="primary" @click="loadCatalog">重试</a-button>
    </div>

    <!-- 章节条：来自题目目录聚合（catalog.chapters），未原值计数 -->
    <nav v-else-if="!catalog" class="oslab-loading" aria-label="章节目录">
      <span class="oslab-spinner"></span>
      <span>正在加载章节目录…</span>
    </nav>
    <template v-else>
      <nav class="chip-row" aria-label="章节选择">
        <button
          type="button"
          class="chip"
          :class="{ active: !query.chapter }"
          :aria-pressed="!query.chapter"
          @click="selectChapter(undefined)"
        >
          全部章节<span class="chip-count">{{ catalog.totalQuestions }}</span>
        </button>
        <button
          v-for="chapter in catalog.chapters"
          :key="chapter.chapter"
          type="button"
          class="chip"
          :class="{ active: query.chapter === chapter.chapter }"
          :aria-pressed="query.chapter === chapter.chapter"
          :title="`${chapter.chapter}：共 ${chapter.questionCount} 题`"
          @click="selectChapter(chapter.chapter)"
        >
          {{ chapter.chapter
          }}<span class="chip-count">{{ chapter.questionCount }}</span>
        </button>
      </nav>

      <!-- 知识点条：当前章节下的知识点及题量/进度（未作答 = questionCount - attempted） -->
      <nav
        v-if="currentChapter"
        class="chip-row node-row"
        aria-label="知识点选择"
      >
        <span class="chip-row-label">知识点</span>
        <button
          type="button"
          class="chip"
          :class="{ active: !query.nodeName }"
          :aria-pressed="!query.nodeName"
          @click="selectNode(undefined)"
        >
          全部
        </button>
        <button
          v-for="node in currentChapter.nodes"
          :key="node.nodeName"
          type="button"
          class="chip"
          :class="{ active: query.nodeName === node.nodeName }"
          :aria-pressed="query.nodeName === node.nodeName"
          :title="`共 ${node.questionCount} 题 · 已答 ${
            node.attempted
          } · 未答 ${node.questionCount - node.attempted} · 待复习 ${
            node.needsReview
          }`"
          @click="selectNode(node.nodeName)"
        >
          {{ node.nodeName
          }}<span class="chip-count">{{ node.questionCount }} 题</span>
          <span v-if="node.needsReview" class="chip-review"
            >待复习 {{ node.needsReview }}</span
          >
        </button>
      </nav>
      <nav
        v-else-if="query.nodeName"
        class="chip-row node-row"
        aria-label="知识点筛选"
      >
        <span class="chip-row-label">知识点</span>
        <span class="chip active">{{ query.nodeName }}</span>
        <button type="button" class="chip" @click="selectNode(undefined)">
          清除知识点
        </button>
      </nav>
    </template>

    <!-- 筛选条：题型 / 难度 / 学习状态 / 题目搜索（调既有 list/page） -->
    <section class="filter-row" aria-label="题目筛选">
      <a-select
        :model-value="query.type"
        placeholder="全部题型"
        allow-clear
        :style="{ width: '150px' }"
        @change="setType"
      >
        <a-option
          v-for="item in typeOptions"
          :key="item.value"
          :value="item.value"
          >{{ item.label }}</a-option
        >
      </a-select>
      <a-select
        :model-value="query.difficulty"
        placeholder="全部难度"
        allow-clear
        :style="{ width: '150px' }"
        @change="setDifficulty"
      >
        <a-option
          v-for="item in difficultyOptions"
          :key="item.value"
          :value="item.value"
          >{{ item.label }}</a-option
        >
      </a-select>
      <a-select
        :model-value="query.studyState === 'ALL' ? undefined : query.studyState"
        placeholder="全部状态"
        allow-clear
        :style="{ width: '150px' }"
        @change="setStudyState"
      >
        <a-option
          v-for="item in studyStateOptions"
          :key="item.value"
          :value="item.value"
          >{{ item.label }}</a-option
        >
      </a-select>
      <a-input-search
        class="filter-search"
        :model-value="query.title || ''"
        placeholder="搜索题目标题"
        allow-clear
        @search="setTitle"
        @clear="setTitle('')"
      />
      <a-button :disabled="!hasActiveFilters" @click="resetFilters"
        >重置</a-button
      >
    </section>

    <!-- 练习区：单题大卡片 + 本组进度 + 上一题/下一题；右侧为本组题目索引 -->
    <div class="practice-grid">
      <section class="practice-main" aria-label="答题区">
        <div class="practice-toolbar">
          <div class="practice-progress">
            <span class="practice-progress-label">本组进度</span>
            <span class="oslab-mono practice-progress-value"
              >已答 {{ answeredInQueue }} / {{ queue.length }}</span
            >
          </div>
          <div class="practice-toolbar-actions">
            <a-button
              v-if="isNarrow"
              size="small"
              @click="queueDrawerVisible = true"
            >
              本组题目（{{ queue.length }}）
            </a-button>
            <a-input-number
              v-model:model-value="runCount"
              :min="1"
              :max="20"
              :style="{ width: '104px' }"
            >
              <template #prefix>题数</template>
            </a-input-number>
            <a-button
              :loading="starting"
              :disabled="total === 0"
              @click="startRun"
            >
              开始连续测评
            </a-button>
          </div>
        </div>

        <div v-if="loading && !queue.length" class="oslab-loading">
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
        <div v-else-if="!total" class="oslab-empty">
          <span class="oslab-state-title">暂无匹配的题目</span>
          <span class="oslab-state-hint"
            >换个章节、知识点或筛选条件后再试。</span
          >
          <a-space>
            <a-button
              v-if="hasActiveFilters"
              size="small"
              type="primary"
              @click="resetFilters"
              >清除筛选</a-button
            >
            <a-button
              v-if="query.chapter"
              size="small"
              @click="selectChapter(undefined)"
              >返回全部章节</a-button
            >
          </a-space>
        </div>
        <template v-else-if="focusedQuestion">
          <QuestionAnswerCard
            :key="focusedQuestion.id"
            :question="focusedQuestion"
            @answered="onAnswered"
          />
          <div class="practice-actions">
            <a-button :disabled="!canGoPrev" @click="goPrev">上一题</a-button>
            <div class="practice-actions-right">
              <a-button
                v-if="query.nodeName"
                type="text"
                @click="selectNode(undefined)"
                >返回知识点</a-button
              >
              <a-button
                :type="feedbackVisible ? 'primary' : 'secondary'"
                :disabled="!canGoNext"
                @click="goNext"
                >下一题</a-button
              >
            </div>
          </div>
        </template>
        <div v-else class="oslab-empty">
          <span class="oslab-state-title">本页暂无可显示的题目</span>
          <a-button size="small" type="primary" @click="goFirstPage"
            >回到第一页</a-button
          >
        </div>

        <footer v-if="total > PAGE_SIZE" class="practice-footer">
          <span class="side-hint">共 {{ total }} 题</span>
          <a-pagination
            :current="query.current"
            :page-size="PAGE_SIZE"
            :total="total"
            show-total
            @change="onPageChange"
          />
        </footer>
      </section>

      <!-- 桌面端「本组题目」索引：当前题队列，不是全站导航 -->
      <aside
        v-if="!isNarrow && queue.length"
        class="queue-rail"
        aria-label="本组题目"
      >
        <h2 class="queue-rail-title">本组题目</h2>
        <ol class="queue-list">
          <li v-for="(item, index) in queue" :key="item.id">
            <button
              type="button"
              class="queue-item"
              :class="{ current: index === focusedIndex }"
              :aria-current="index === focusedIndex ? 'true' : undefined"
              @click="focusQuestion(item)"
            >
              <span class="queue-index oslab-mono">{{ index + 1 }}</span>
              <span class="queue-title">{{ item.title }}</span>
              <span class="oslab-status-tag" :class="stateTagClass(item)">{{
                stateTagText(item)
              }}</span>
            </button>
          </li>
        </ol>
      </aside>
    </div>

    <!-- 窄屏：本组题目改为顶部抽屉 -->
    <a-drawer
      v-model:visible="queueDrawerVisible"
      title="本组题目"
      placement="top"
      :height="380"
      :footer="false"
      unmount-on-close
    >
      <ol class="queue-list">
        <li v-for="(item, index) in queue" :key="item.id">
          <button
            type="button"
            class="queue-item"
            :class="{ current: index === focusedIndex }"
            @click="onDrawerSelect(item)"
          >
            <span class="queue-index oslab-mono">{{ index + 1 }}</span>
            <span class="queue-title">{{ item.title }}</span>
            <span class="oslab-status-tag" :class="stateTagClass(item)">{{
              stateTagText(item)
            }}</span>
          </button>
        </li>
      </ol>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Message } from "@arco-design/web-vue";
import { useRoute, useRouter } from "vue-router";
import QuestionAnswerCard from "@/components/learning/QuestionAnswerCard.vue";
import {
  getLearningCatalog,
  getLearningQuestion,
  listLearningQuestions,
  startAssessment,
  type LearningAnswerResultVO,
  type LearningCatalogVO,
  type LearningQuestionQueryRequest,
  type LearningQuestionType,
  type LearningQuestionVO,
} from "@/api/learning";

type StudyState = "ALL" | "UNANSWERED" | "NEEDS_REVIEW" | "CORRECT";
type FilterQuery = LearningQuestionQueryRequest & { studyState?: StudyState };

const PAGE_SIZE = 10;

const route = useRoute();
const router = useRouter();

// ---------- 目录（章节 -> 知识点聚合） ----------
const catalog = ref<LearningCatalogVO | null>(null);
const catalogLoading = ref(false);
const catalogError = ref("");

const loadCatalog = async () => {
  catalogLoading.value = true;
  catalogError.value = "";
  try {
    const res = await getLearningCatalog();
    if (res.data?.code === 0 && res.data.data) {
      catalog.value = res.data.data;
    } else {
      catalogError.value = res.data?.message || "请稍后重试";
    }
  } catch (error) {
    catalogError.value = "网络异常，请稍后重试";
  } finally {
    catalogLoading.value = false;
  }
};

const currentChapter = computed(
  () =>
    catalog.value?.chapters.find(
      (chapter) => chapter.chapter === query.value.chapter
    ) || null
);

// ---------- 题目列表（既有 list/page；query 参数与 URL 同步） ----------
const query = ref<FilterQuery>({
  current: 1,
  pageSize: PAGE_SIZE,
  chapter: undefined,
  nodeName: undefined,
  type: undefined,
  difficulty: undefined,
  studyState: "ALL",
  title: undefined,
});
const questions = ref<LearningQuestionVO[]>([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref("");
let loadSequence = 0;
let lastFilterKey = "";

// 外部聚焦题（深链/被筛选掉的题）；作答后钉住以保证反馈不被重载冲掉
const externalQuestion = ref<LearningQuestionVO | null>(null);
let focusSequence = 0;

const typeOptions: Array<{ value: LearningQuestionType; label: string }> = [
  { value: "JUDGE", label: "判断题" },
  { value: "SINGLE", label: "单选题" },
  { value: "MULTIPLE", label: "多选题" },
  { value: "FILL_BLANK", label: "填空题" },
];

const studyStateOptions: Array<{
  value: Exclude<StudyState, "ALL">;
  label: string;
}> = [
  { value: "UNANSWERED", label: "未作答" },
  { value: "NEEDS_REVIEW", label: "待复习" },
  { value: "CORRECT", label: "已答对" },
];

const difficultyOptions = [
  { value: 1, label: "入门" },
  { value: 2, label: "基础" },
  { value: 3, label: "进阶" },
  { value: 4, label: "挑战" },
  { value: 5, label: "综合" },
];

const queryValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const parseRoute = (): FilterQuery => {
  const q = route.query;
  const studyState = queryValue(q.studyState) || queryValue(q.category);
  const type = queryValue(q.type) as LearningQuestionType;
  const difficultyValue = Number(queryValue(q.difficulty));
  return {
    current: Number(queryValue(q.current)) || 1,
    pageSize: PAGE_SIZE,
    chapter: queryValue(q.chapter) || undefined,
    nodeName: queryValue(q.nodeName) || undefined,
    type: typeOptions.some((item) => item.value === type) ? type : undefined,
    difficulty: difficultyOptions.some((item) => item.value === difficultyValue)
      ? difficultyValue
      : undefined,
    studyState: studyStateOptions.some((item) => item.value === studyState)
      ? (studyState as StudyState)
      : "ALL",
    title: queryValue(q.title) || undefined,
  };
};

const filterKeyOf = (q: FilterQuery) =>
  JSON.stringify([
    q.chapter,
    q.nodeName,
    q.type,
    q.difficulty,
    q.studyState,
    q.title,
    q.current,
  ]);

const syncFromRoute = async () => {
  const next = parseRoute();
  const nextKey = filterKeyOf(next);
  const filterChanged = nextKey !== lastFilterKey;
  query.value = next;
  lastFilterKey = nextKey;
  if (filterChanged) {
    // 上下文变化后，旧的外部题不再属于当前队列
    externalQuestion.value = null;
    await load();
  }
  const questionId = queryValue(route.query.questionId);
  if (
    questionId &&
    !queue.value.some((item) => String(item.id) === questionId) &&
    String(externalQuestion.value?.id || "") !== questionId
  ) {
    void loadFocused(questionId);
  }
};

const buildListRequest = (): LearningQuestionQueryRequest => {
  const request: LearningQuestionQueryRequest = {
    current: query.value.current || 1,
    pageSize: query.value.pageSize || PAGE_SIZE,
  };
  // 「未归类」与 null/空白章节由后端共享谓词归一（L3-R3）：chapter 原值透传。
  if (query.value.chapter) request.chapter = query.value.chapter;
  if (query.value.nodeName) request.nodeName = query.value.nodeName;
  if (query.value.type) request.type = query.value.type;
  if (query.value.difficulty) request.difficulty = query.value.difficulty;
  if (query.value.title) request.title = query.value.title;
  if (query.value.studyState && query.value.studyState !== "ALL") {
    request.studyState = query.value.studyState;
  }
  return request;
};

const load = async () => {
  const sequence = ++loadSequence;
  loading.value = true;
  loadError.value = "";
  try {
    const res = await listLearningQuestions(buildListRequest());
    if (sequence !== loadSequence) return;
    if (res.data?.code === 0) {
      questions.value = res.data.data?.records || [];
      total.value = res.data.data?.total || 0;
    } else {
      loadError.value = res.data?.message || "加载题目失败，请稍后重试";
    }
  } catch (error) {
    if (sequence === loadSequence) loadError.value = "网络异常，请稍后重试";
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
};

const loadFocused = async (questionId: string) => {
  const sequence = ++focusSequence;
  try {
    const res = await getLearningQuestion(questionId);
    if (sequence !== focusSequence) return;
    if (res.data?.code === 0 && res.data.data) {
      externalQuestion.value = res.data.data;
    } else {
      externalQuestion.value = null;
      Message.error(res.data?.message || "题目不存在或不可用");
    }
  } catch (error) {
    if (sequence === focusSequence) {
      externalQuestion.value = null;
      Message.error("加载题目失败，请稍后重试");
    }
  }
};

// ---------- 队列与聚焦 ----------
const queue = computed(() => questions.value);

const focusedQuestion = computed<LearningQuestionVO | null>(() => {
  const questionId = queryValue(route.query.questionId);
  if (questionId) {
    const inQueue = queue.value.find((item) => String(item.id) === questionId);
    return inQueue || externalQuestion.value;
  }
  return queue.value[0] || null;
});

const focusedIndex = computed(() => {
  const current = focusedQuestion.value;
  if (!current) return -1;
  return queue.value.findIndex(
    (item) => String(item.id) === String(current.id)
  );
});

const answeredInQueue = computed(
  () => queue.value.filter((item) => item.hasAttempted).length
);

const feedbackQuestionId = ref("");
const feedbackVisible = computed(
  () =>
    Boolean(focusedQuestion.value) &&
    feedbackQuestionId.value === String(focusedQuestion.value?.id)
);
watch(
  () => (focusedQuestion.value ? String(focusedQuestion.value.id) : ""),
  () => {
    feedbackQuestionId.value = "";
  }
);

const canGoPrev = computed(() => focusedIndex.value > 0);
const canGoNext = computed(
  () =>
    queue.value.length > 0 &&
    (focusedIndex.value < 0 || focusedIndex.value < queue.value.length - 1)
);

const stateTagClass = (question: LearningQuestionVO) => {
  if (!question.hasAttempted) return "oslab-status-tag--unseen";
  return question.latestResult === "CORRECT"
    ? "oslab-status-tag--mastered"
    : "oslab-status-tag--review";
};

const stateTagText = (question: LearningQuestionVO) => {
  if (!question.hasAttempted) return "未作答";
  return question.latestResult === "CORRECT" ? "已答对" : "待复习";
};

// ---------- 路由写回（保留 source/returnTo 等无关参数） ----------
const replaceQuery = (patch: Record<string, string | undefined>) => {
  const next: Record<string, string> = {};
  Object.entries(route.query).forEach(([key, value]) => {
    const v = queryValue(value);
    if (v) next[key] = v;
  });
  Object.entries(patch).forEach(([key, value]) => {
    if (value === undefined || value === "") delete next[key];
    else next[key] = String(value);
  });
  const target = router.resolve({ path: route.path, query: next }).fullPath;
  if (target !== route.fullPath) {
    router.replace({ query: next });
  }
};

const selectChapter = (chapter?: string) =>
  replaceQuery({
    chapter: chapter || undefined,
    nodeName: undefined,
    questionId: undefined,
    current: undefined,
  });

const selectNode = (nodeName?: string) =>
  replaceQuery({
    nodeName: nodeName || undefined,
    questionId: undefined,
    current: undefined,
  });

const setType = (value: LearningQuestionType | undefined) =>
  replaceQuery({
    type: value || undefined,
    questionId: undefined,
    current: undefined,
  });

const setDifficulty = (value: number | undefined) =>
  replaceQuery({
    difficulty: value ? String(value) : undefined,
    questionId: undefined,
    current: undefined,
  });

const setStudyState = (value: StudyState | undefined) =>
  replaceQuery({
    studyState: !value || value === "ALL" ? undefined : value,
    category: undefined,
    questionId: undefined,
    current: undefined,
  });

const setTitle = (value: string | undefined) => {
  const title = (value || "").trim();
  replaceQuery({
    title: title || undefined,
    questionId: undefined,
    current: undefined,
  });
};

const resetFilters = () =>
  replaceQuery({
    chapter: undefined,
    nodeName: undefined,
    type: undefined,
    difficulty: undefined,
    studyState: undefined,
    title: undefined,
    category: undefined,
    questionId: undefined,
    current: undefined,
  });

const focusQuestion = (question: LearningQuestionVO) => {
  if (question?.id === undefined || question.id === null) return;
  replaceQuery({ questionId: String(question.id) });
};

const goPrev = () => {
  const index = focusedIndex.value;
  if (index > 0) focusQuestion(queue.value[index - 1]);
};

const goNext = () => {
  const index = focusedIndex.value;
  const next = index < 0 ? queue.value[0] : queue.value[index + 1];
  if (next) focusQuestion(next);
};

const goFirstPage = () =>
  replaceQuery({ current: undefined, questionId: undefined });

const onPageChange = (page: number) => {
  replaceQuery({ current: String(page || 1), questionId: undefined });
};

const reload = () => {
  void load();
};

// ---------- 作答流：钉住当前题并刷新本组/目录计数 ----------
const onAnswered = (result: LearningAnswerResultVO) => {
  const current = focusedQuestion.value;
  if (current) {
    const inQueue = queue.value.find(
      (item) => String(item.id) === String(current.id)
    );
    if (inQueue) {
      inQueue.hasAttempted = true;
      inQueue.latestResult = result.correct ? "CORRECT" : "WRONG";
    }
    externalQuestion.value = current;
    feedbackQuestionId.value = String(current.id);
    if (queryValue(route.query.questionId) !== String(current.id)) {
      replaceQuery({ questionId: String(current.id) });
    }
  }
  void load();
  void loadCatalog();
};

// ---------- 连续测评（只传 start 支持的范围字段） ----------
const runCount = ref(5);
const starting = ref(false);

const startRun = async () => {
  starting.value = true;
  try {
    const res = await startAssessment({
      chapter: query.value.chapter || undefined,
      nodeName: query.value.nodeName || undefined,
      type: query.value.type || undefined,
      difficulty: query.value.difficulty || undefined,
      studyState: query.value.studyState || "ALL",
      count: runCount.value,
    });
    if (res.data?.code === 0 && res.data.data?.runId) {
      const contextQuery: Record<string, string> = {};
      [
        "source",
        "nodeName",
        "returnTo",
        "chapter",
        "type",
        "difficulty",
        "studyState",
      ].forEach((key) => {
        const value = queryValue(route.query[key]);
        if (value) contextQuery[key] = value;
      });
      if (query.value.nodeName) contextQuery.nodeName = query.value.nodeName;
      if (query.value.chapter) contextQuery.chapter = query.value.chapter;
      if (query.value.type) contextQuery.type = query.value.type;
      if (query.value.difficulty) {
        contextQuery.difficulty = String(query.value.difficulty);
      }
      contextQuery.studyState = query.value.studyState || "ALL";
      router.push({
        path: `/assessments/run/${res.data.data.runId}`,
        query: contextQuery,
      });
    } else {
      Message.error(res.data?.message || "开始连续测评失败");
    }
  } catch (error) {
    Message.error("开始连续测评失败，请稍后重试");
  } finally {
    starting.value = false;
  }
};

// ---------- 页面上下文与派生状态 ----------
const contextText = computed(() => {
  const parts: string[] = [];
  if (query.value.chapter) parts.push(query.value.chapter);
  if (query.value.nodeName) parts.push(query.value.nodeName);
  const type = typeOptions.find((item) => item.value === query.value.type);
  if (type) parts.push(type.label);
  const difficulty = difficultyOptions.find(
    (item) => item.value === query.value.difficulty
  );
  if (difficulty) parts.push(difficulty.label);
  if (query.value.studyState && query.value.studyState !== "ALL") {
    const state = studyStateOptions.find(
      (item) => item.value === query.value.studyState
    );
    if (state) parts.push(state.label);
  }
  if (query.value.title) parts.push(`搜索“${query.value.title}”`);
  return parts.join(" · ");
});

const hasActiveFilters = computed(() =>
  Boolean(
    query.value.chapter ||
      query.value.nodeName ||
      query.value.type ||
      query.value.difficulty ||
      (query.value.studyState && query.value.studyState !== "ALL") ||
      query.value.title
  )
);

// ---------- 窄屏（<1024px）：本组题目索引改为顶部抽屉 ----------
const narrowMedia = window.matchMedia("(max-width: 1023px)");
const isNarrow = ref(narrowMedia.matches);
const queueDrawerVisible = ref(false);
const handleMediaChange = (event: MediaQueryListEvent) => {
  isNarrow.value = event.matches;
  if (!event.matches) queueDrawerVisible.value = false;
};

const onDrawerSelect = (question: LearningQuestionVO) => {
  queueDrawerVisible.value = false;
  focusQuestion(question);
};

watch(
  () => route.query,
  () => {
    void syncFromRoute();
  },
  { deep: true, immediate: true }
);

onMounted(() => {
  narrowMedia.addEventListener("change", handleMediaChange);
  void loadCatalog();
});

onBeforeUnmount(() => {
  narrowMedia.removeEventListener("change", handleMediaChange);
});
</script>

<style scoped>
.questions-flow {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.page-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.crumb-current {
  color: var(--oslab-ink-soft);
}
.page-head {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-1);
}
.page-context {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
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

/* ---- 章节条 / 知识点条 ---- */
.chip-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.node-row {
  padding-top: var(--oslab-space-1);
}
.chip-row-label {
  flex-shrink: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-font-body);
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}
.chip:hover {
  border-color: var(--oslab-muted-strong);
}
.chip.active {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-weight: 500;
}
.chip-count {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.chip.active .chip-count {
  color: var(--oslab-primary);
}
.chip-review {
  padding: 0 6px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-review-soft);
  color: var(--oslab-review);
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;
}

/* ---- 筛选条 ---- */
.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
}
.filter-search {
  width: 220px;
}

/* ---- 练习区 ---- */
.practice-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: var(--oslab-space-6);
  align-items: start;
  margin-top: var(--oslab-space-1);
}
.practice-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.practice-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
}
.practice-progress {
  display: inline-flex;
  align-items: baseline;
  gap: var(--oslab-space-2);
}
.practice-progress-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.practice-progress-value {
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-variant-numeric: tabular-nums;
}
.practice-toolbar-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.practice-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
}
.practice-actions-right {
  display: inline-flex;
  align-items: center;
  gap: var(--oslab-space-2);
}
.practice-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
}

/* ---- 本组题目索引 ---- */
.queue-rail {
  position: sticky;
  top: var(--oslab-space-4);
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-4);
}
.queue-rail-title {
  margin: 0 0 var(--oslab-space-3);
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 600;
}
.queue-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-1);
}
.queue-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  padding: 8px;
  border: 1px solid transparent;
  border-radius: var(--oslab-radius-md);
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--oslab-duration-fast) var(--oslab-ease);
}
.queue-item:hover {
  background: var(--oslab-panel-soft);
}
.queue-item.current {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.queue-index {
  flex-shrink: 0;
  min-width: 20px;
  text-align: center;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.queue-title {
  flex: 1;
  min-width: 0;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.queue-item .oslab-status-tag {
  flex-shrink: 0;
}

/* ---- 响应式：<1024px 单栏，本组题目走顶部抽屉 ---- */
@media (max-width: 1023px) {
  .practice-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .practice-toolbar-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
@media (max-width: 640px) {
  .filter-search {
    width: 100%;
  }
  .catalog-error {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
