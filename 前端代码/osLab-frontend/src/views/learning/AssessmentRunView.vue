<template>
  <div class="assessment-run">
    <a-spin :loading="loading" style="width: 100%">
      <!-- ============ 整组自测：进行中 ============ -->
      <template v-if="isSelfTest && run && !finished">
        <div class="run-head">
          <div>
            <h1 class="run-title">整组自测</h1>
            <div class="side-hint">
              第 {{ currentIndex + 1 }} / {{ questions.length }} 题 ·
              交卷后统一判分
            </div>
          </div>
          <div class="head-actions">
            <span class="save-state" :class="`is-${saveState}`" role="status">
              {{ saveStateText }}
            </span>
            <a-button size="small" :loading="exiting" @click="exitTemporarily"
              >暂存退出</a-button
            >
            <a-button
              size="small"
              type="primary"
              :loading="finishing"
              @click="finishRun"
              >交卷</a-button
            >
          </div>
        </div>

        <a-alert v-if="conflict" type="warning" class="run-alert">
          <template #title>草稿版本冲突</template>
          另一标签页已更新草稿。为避免覆盖，本次保存未生效。
          <a-button size="mini" type="primary" @click="applyConflictDrafts"
            >载入服务端草稿</a-button
          >
        </a-alert>
        <a-alert
          v-else-if="saveState === 'error'"
          type="error"
          class="run-alert"
        >
          <template #title>草稿保存失败</template>
          {{ saveError }} 当前输入仍在本页，请重试保存。
          <a-button size="mini" @click="flushSave()">重试保存</a-button>
        </a-alert>

        <!-- 答题卡：只显示作答/标记状态，不显示对错 -->
        <div class="self-nav" role="group" aria-label="答题卡">
          <button
            v-for="(q, index) in questions"
            :key="q.id"
            type="button"
            class="self-nav-item"
            :class="{
              current: index === currentIndex,
              answered: isAnswered(q),
              marked: marked[String(q.id)],
              offline: q.offline || q.revisionChanged,
            }"
            :disabled="Boolean(q.offline)"
            :aria-pressed="index === currentIndex"
            :aria-label="`第 ${index + 1} 题：${navLabel(q)}`"
            :title="navLabel(q)"
            @click="goToQuestion(index)"
          >
            {{ index + 1 }}
            <span v-if="marked[String(q.id)]" class="nav-dot"></span>
          </button>
        </div>

        <SelfTestQuestionCard
          v-if="currentQuestion"
          :key="currentQuestion.id"
          :question="currentQuestion"
          :model-value="
            answers[String(currentQuestion.id)] ?? emptyAnswer(currentQuestion)
          "
          :marked-unsure="Boolean(marked[String(currentQuestion.id)])"
          :disabled="Boolean(currentQuestion.offline)"
          @update:model-value="onAnswerUpdate"
          @update:marked-unsure="onMarkedUpdate"
        />

        <div class="nav-buttons">
          <a-button
            :disabled="currentIndex === 0"
            @click="goToQuestion(currentIndex - 1)"
            >上一题</a-button
          >
          <a-button
            v-if="currentIndex < questions.length - 1"
            type="primary"
            @click="goToQuestion(currentIndex + 1)"
            >下一题</a-button
          >
          <a-button
            v-else
            type="primary"
            :loading="finishing"
            @click="finishRun"
            >检查并交卷</a-button
          >
        </div>

        <p class="self-test-note">
          自测期间不提供提示、答案与解析；标记不确定不扣分。需要辅导时请先交卷。
        </p>
      </template>

      <!-- ============ 整组自测：结果 ============ -->
      <template v-else-if="isSelfTest && finished">
        <div class="run-head">
          <div>
            <h1 class="run-title">自测结果</h1>
            <div class="side-hint">
              {{ result?.summaryText || "正在读取本组结果…" }}
            </div>
          </div>
          <div class="head-actions">
            <a-button size="small" :loading="resultLoading" @click="loadResult"
              >刷新结果</a-button
            >
          </div>
        </div>

        <a-alert v-if="resultError" type="error" class="run-alert">
          <template #title>结果读取失败</template>
          {{ resultError }}
          <a-button size="mini" @click="loadResult">重试</a-button>
        </a-alert>

        <template v-if="result">
          <section class="result-stats" aria-label="本组统计">
            <div class="stat">
              <span class="stat-value">{{ accuracyText }}</span>
              <span class="stat-label">客观题正确率</span>
            </div>
            <div class="stat">
              <span class="stat-value"
                >{{ result.correctCount }} /
                {{ result.objectiveQuestions }}</span
              >
              <span class="stat-label">答对 / 有效客观题</span>
            </div>
            <div class="stat">
              <span class="stat-value">{{ result.unansweredCount }}</span>
              <span class="stat-label">未作答（不计错）</span>
            </div>
            <div class="stat">
              <span class="stat-value">{{ result.markedUnsureCount }}</span>
              <span class="stat-label">标记不确定</span>
            </div>
            <div v-if="result.pendingSelfAssessmentCount" class="stat">
              <span class="stat-value">{{
                result.pendingSelfAssessmentCount
              }}</span>
              <span class="stat-label">待自评</span>
            </div>
            <div v-if="result.invalidCount" class="stat">
              <span class="stat-value">{{ result.invalidCount }}</span>
              <span class="stat-label">失效（不计分）</span>
            </div>
          </section>

          <section
            v-if="focusEntries.length"
            class="focus-row"
            aria-label="错题与不确定题"
          >
            <span class="focus-label">优先复盘</span>
            <button
              v-for="entry in focusEntries"
              :key="entry.questionId"
              type="button"
              class="focus-chip"
              @click="scrollToResult(entry.questionId)"
            >
              {{ entry.title }}
              <span v-if="entry.markedUnsure" class="focus-flag">不确定</span>
            </button>
          </section>

          <section class="result-list" aria-label="逐题复盘">
            <article
              v-for="item in result.questions"
              :id="`result-${item.questionId}`"
              :key="item.questionId"
              class="result-item"
              :class="`is-${item.status.toLowerCase()}`"
            >
              <header class="result-head">
                <span class="result-status">{{ statusText(item.status) }}</span>
                <h2 class="result-title">{{ item.title }}</h2>
                <span v-if="item.markedUnsure" class="result-flag">不确定</span>
              </header>
              <p v-if="item.invalidReason" class="result-note">
                {{ item.invalidReason }}
              </p>
              <p
                v-if="item.status === 'PENDING_SELF_ASSESSMENT'"
                class="result-note"
              >
                综合题不自动判分。请对照下方参考解析自行评估。
              </p>
              <p v-if="item.status === 'UNANSWERED'" class="result-note">
                本题未作答，不计为错误观测。
              </p>
              <details
                v-if="item.analysis || item.misconception"
                class="result-details"
              >
                <summary>查看解析</summary>
                <MdViewer
                  v-if="item.analysis"
                  class="result-analysis"
                  :value="item.analysis"
                />
                <p v-if="item.misconception" class="result-note">
                  常见误区：{{ item.misconception }}
                </p>
              </details>
              <ul v-if="item.masteryChanges?.length" class="result-mastery">
                <li
                  v-for="change in item.masteryChanges"
                  :key="change.nodeName"
                >
                  {{ change.nodeName }}：{{ percentText(change.masteryBefore) }}
                  →
                  {{ percentText(change.masteryAfter) }}
                  <span v-if="change.countedAsObservation === false"
                    >（未计独立观测）</span
                  >
                </li>
              </ul>
            </article>
          </section>

          <div class="result-actions">
            <a-button type="primary" @click="returnToPractice">{{
              returnTargetLabel
            }}</a-button>
            <a-button
              @click="router.push({ path: '/records', query: contextQuery })"
              >查看学习记录</a-button
            >
          </div>
        </template>
      </template>

      <!-- ============ 逐题练习（PRACTICE，保持即时反馈） ============ -->
      <template v-else>
        <div class="run-head" v-if="run">
          <div>
            <h1 class="run-title">知识测评</h1>
            <div class="side-hint">
              第 {{ currentIndex + 1 }} / {{ questions.length }} 题 ·
              本轮首次答对 {{ firstCorrectCount }} 题
            </div>
          </div>
          <a-button size="small" @click="finishRun" :disabled="finished">
            结束本轮
          </a-button>
        </div>

        <AssessmentNavigator
          v-if="questions.length"
          class="run-navigator"
          :questions="questions"
          :current-index="currentIndex"
          @select="goToQuestion"
        />

        <div v-if="currentQuestion" class="question-area">
          <QuestionAnswerCard
            :key="currentQuestion.id"
            :question="currentQuestion"
            :assessment-run-id="runId"
            :course-code="String(route.query.courseCode || '')"
            :disabled="finished || finishing || currentQuestion.offline"
            :disabled-reason="finished ? '本轮测评已结束' : '本轮测评正在结束'"
            @answered="answerHandler"
          />
          <div class="nav-buttons">
            <a-button
              :disabled="currentIndex === 0"
              @click="goToQuestion(currentIndex - 1)"
            >
              上一题
            </a-button>
            <a-button
              v-if="currentIndex < questions.length - 1"
              :type="nextPrimary ? 'primary' : 'secondary'"
              @click="goToQuestion(currentIndex + 1)"
            >
              下一题
            </a-button>
            <a-button
              v-else
              type="primary"
              :disabled="finished"
              @click="finishRun"
            >
              本轮总结
            </a-button>
          </div>
        </div>

        <div v-if="finished && summary" class="section-card run-summary">
          <div class="section-title">本轮总结</div>
          <a-descriptions :column="3" bordered size="small">
            <a-descriptions-item label="题目数">
              {{ summary.totalQuestions }}
            </a-descriptions-item>
            <a-descriptions-item label="已作答">
              {{ summary.answeredQuestions }}
            </a-descriptions-item>
            <a-descriptions-item label="本轮首次答对">
              {{ summary.firstCorrect }}
            </a-descriptions-item>
          </a-descriptions>
          <div class="btn-row">
            <a-button
              @click="router.push({ path: '/records', query: contextQuery })"
            >
              查看学习记录
            </a-button>
            <a-button type="primary" @click="returnToPractice">
              返回题目练习
            </a-button>
          </div>
        </div>

        <div
          v-if="!loading && !questions.length && !finished"
          class="oslab-empty"
        >
          <span class="oslab-state-title">本轮没有可作答的题目</span>
          <a-button size="small" type="primary" @click="returnToPractice"
            >返回题目练习</a-button
          >
        </div>
      </template>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { useStore } from "vuex";
import { Message, Modal } from "@arco-design/web-vue";
import AssessmentNavigator from "@/components/learning/AssessmentNavigator.vue";
import QuestionAnswerCard from "@/components/learning/QuestionAnswerCard.vue";
import SelfTestQuestionCard from "@/components/learning/SelfTestQuestionCard.vue";
import MdViewer from "@/components/MdViewer.vue";
import {
  finishAssessment,
  getAssessmentRun,
  type AssessmentFinishResult,
  type AssessmentRunVO,
  type LearningAnswerResultVO,
  type LearningQuestionVO,
} from "@/api/learning";
import {
  getAssessmentResult,
  saveAssessmentDrafts,
  type AssessmentDraft,
  type AssessmentResultVO,
} from "@/api/assessment";
import { notifyLearningUpdated } from "@/utils/learningEvents";

const route = useRoute();
const router = useRouter();
const store = useStore();

// 雪花 id 保持字符串透传（Number 精度丢失会导致查询错误 id）
const runId = computed(() =>
  typeof route.params.runId === "string" ? route.params.runId : ""
);

const loading = ref(false);
const run = ref<AssessmentRunVO | null>(null);
const questions = ref<LearningQuestionVO[]>([]);
const currentIndex = ref(0);
const finished = ref(false);
const finishing = ref(false);
const summary = ref<AssessmentFinishResult | null>(null);
const firstCorrectCount = ref(0);
let loadSequence = 0;

const isSelfTest = computed(
  () => (run.value?.mode || "PRACTICE") === "SELF_TEST"
);

const queryValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const contextQuery = computed(() => {
  const result: Record<string, string> = {};
  [
    "source",
    "nodeName",
    "nodeCode",
    "courseCode",
    "sectionCode",
    "unitCode",
    "returnTo",
    "category",
    "chapter",
    "type",
    "difficulty",
    "studyState",
    "title",
    "current",
  ].forEach((key) => {
    const value = queryValue(route.query[key]);
    if (value) result[key] = value;
  });
  return result;
});

const hasReturnTarget = computed(() => {
  const returnTo = queryValue(route.query.returnTo);
  return returnTo.startsWith("/") && !returnTo.startsWith("//");
});

const returnTargetLabel = computed(() => {
  const returnTo = queryValue(route.query.returnTo);
  if (returnTo.startsWith("/courses/")) return "返回原单元";
  if (returnTo.startsWith("/assessments")) return "返回题目练习";
  return hasReturnTarget.value ? "返回原位置" : "继续练习";
});

const returnToPractice = () => {
  const returnTo = queryValue(route.query.returnTo);
  if (returnTo.startsWith("/") && !returnTo.startsWith("//")) {
    router.push(returnTo);
    return;
  }
  router.push({ path: "/assessments", query: contextQuery.value });
};

const currentQuestion = computed(
  () => questions.value[currentIndex.value] || null
);

// ================= 整组自测：草稿与交卷 =================

type SaveState = "idle" | "dirty" | "saving" | "saved" | "error" | "conflict";

const answers = ref<Record<string, string | string[] | null>>({});
const marked = ref<Record<string, boolean>>({});
const rowVersion = ref(0);
const savedAt = ref<number | null>(null);
const saveState = ref<SaveState>("idle");
const saveError = ref("");
const conflict = ref(false);
const exiting = ref(false);
const hydrating = ref(false);
const result = ref<AssessmentResultVO | null>(null);
const resultLoading = ref(false);
const resultError = ref("");
let saveTimer: ReturnType<typeof setTimeout> | null = null;
let savingPromise: Promise<boolean> | null = null;
let dirty = false;

const emptyAnswer = (question: LearningQuestionVO): string | string[] =>
  question.type === "MULTIPLE" ? [] : "";

const saveStateText = computed(() => {
  switch (saveState.value) {
    case "dirty":
      return "有未保存修改";
    case "saving":
      return "保存中…";
    case "saved":
      return "已保存";
    case "error":
      return "保存失败";
    case "conflict":
      return "版本冲突";
    default:
      return "草稿已同步";
  }
});

const setAnswer = (
  question: LearningQuestionVO,
  value: string | string[] | null
) => {
  answers.value = { ...answers.value, [String(question.id)]: value };
};

const setMarked = (question: LearningQuestionVO, value: boolean) => {
  marked.value = { ...marked.value, [String(question.id)]: value };
};

const onAnswerUpdate = (value: string | string[] | null) => {
  const question = currentQuestion.value;
  if (question) setAnswer(question, value);
};

const onMarkedUpdate = (value: boolean) => {
  const question = currentQuestion.value;
  if (question) setMarked(question, value);
};

const isBlankAnswer = (value: string | string[] | null | undefined) => {
  if (value === null || value === undefined) return true;
  if (Array.isArray(value)) return value.length === 0;
  return String(value).trim() === "";
};

const isAnswered = (question: LearningQuestionVO) =>
  !isBlankAnswer(answers.value[String(question.id)]);

const navLabel = (question: LearningQuestionVO) => {
  if (question.offline) return "已下线";
  if (question.revisionChanged) return "题目已更新，不计分";
  if (marked.value[String(question.id)]) return "已标记不确定";
  return isAnswered(question) ? "已作答" : "未作答";
};

const newClientId = () => {
  const cryptoApi =
    typeof crypto !== "undefined"
      ? (crypto as Crypto & { randomUUID?: () => string })
      : null;
  return cryptoApi && typeof cryptoApi.randomUUID === "function"
    ? cryptoApi.randomUUID()
    : `draft-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const draftsPayload = (): AssessmentDraft[] =>
  questions.value.map((question) => ({
    questionId: String(question.id),
    answer: answers.value[String(question.id)] ?? emptyAnswer(question),
    markedUnsure: Boolean(marked.value[String(question.id)]),
  }));

const applyDrafts = (
  from: Array<{
    questionId: string;
    answer?: string | string[] | null;
    markedUnsure?: boolean;
    draftAnswer?: string | string[] | null;
  }>
) => {
  hydrating.value = true;
  const nextAnswers: Record<string, string | string[] | null> = {};
  const nextMarked: Record<string, boolean> = {};
  for (const item of from) {
    const key = String(item.questionId);
    nextAnswers[key] =
      "answer" in item ? item.answer ?? null : item.draftAnswer ?? null;
    nextMarked[key] = Boolean(item.markedUnsure);
  }
  answers.value = nextAnswers;
  marked.value = nextMarked;
  dirty = false;
  // 等水合触发的 watcher 跑完再允许保存，避免刷新后把刚载入的草稿再次回写
  void nextTick(() => {
    hydrating.value = false;
  });
};

const scheduleSave = () => {
  if (!isSelfTest.value || finished.value || hydrating.value) return;
  dirty = true;
  saveState.value = "dirty";
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    saveTimer = null;
    void flushSave();
  }, 700);
};

const errorConflict = (
  serverVersion: number,
  serverDrafts: AssessmentDraft[]
) => {
  conflict.value = true;
  rowVersion.value = serverVersion;
  saveState.value = "conflict";
  if (Array.isArray(serverDrafts) && serverDrafts.length) {
    applyDrafts(serverDrafts);
    conflict.value = true;
    saveState.value = "conflict";
  }
};

const flushSave = async (): Promise<boolean> => {
  if (!isSelfTest.value || finished.value || !runId.value) return true;
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
  if (savingPromise) {
    await savingPromise;
  }
  if (!dirty) return saveState.value !== "error";
  dirty = false;
  saveState.value = "saving";
  const clientSaveId = newClientId();
  const version = rowVersion.value;
  const payload = draftsPayload();
  const task = (async () => {
    try {
      const response = await saveAssessmentDrafts(runId.value, {
        clientSaveId,
        rowVersion: version,
        drafts: payload,
      });
      if (response.data?.code !== 0 || !response.data.data) {
        dirty = true;
        saveState.value = "error";
        saveError.value = response.data?.message || "草稿保存失败";
        return false;
      }
      const data = response.data.data;
      if (data.conflict) {
        errorConflict(data.rowVersion, data.serverDrafts);
        return false;
      }
      rowVersion.value = data.rowVersion;
      savedAt.value = data.savedAt;
      saveState.value = "saved";
      saveError.value = "";
      conflict.value = false;
      return true;
    } catch (e) {
      dirty = true;
      saveState.value = "error";
      saveError.value =
        e instanceof Error ? e.message : "草稿保存失败，请检查网络后重试";
      return false;
    }
  })();
  savingPromise = task;
  try {
    const saved = await task;
    return saved;
  } finally {
    savingPromise = null;
    if (dirty && saveState.value !== "error" && !conflict.value) {
      scheduleSave();
    }
  }
};

const applyConflictDrafts = () => {
  // 服务端草稿已在冲突响应中载入；此处只需清除提示并允许继续编辑
  conflict.value = false;
  saveState.value = "saved";
  Message.success("已载入服务端草稿，请继续作答");
};

const goToQuestion = async (index: number) => {
  if (index < 0 || index >= questions.value.length) return;
  if (isSelfTest.value) {
    await flushSave();
  }
  currentIndex.value = index;
};

const exitTemporarily = async () => {
  if (exiting.value) return;
  exiting.value = true;
  const saved = await flushSave();
  exiting.value = false;
  if (!saved) {
    const confirmed = window.confirm(
      "草稿尚未保存成功，退出将丢失本次修改。仍要暂存退出吗？"
    );
    if (!confirmed) return;
  } else {
    Message.success("草稿已保存，可从原入口继续");
  }
  returnToPractice();
};

const submissionStorageKey = () => {
  const userId = store.state.user?.loginUser?.id ?? "anonymous";
  return `oslab:selftest:submission:${userId}:${runId.value}`;
};

const submissionKey = () => {
  try {
    const existing = sessionStorage.getItem(submissionStorageKey());
    if (existing) return existing;
    const key = newClientId();
    sessionStorage.setItem(submissionStorageKey(), key);
    return key;
  } catch {
    return newClientId();
  }
};

const doFinishSelfTest = async () => {
  finishing.value = true;
  try {
    const response = await finishAssessment({
      runId: runId.value,
      submissionKey: submissionKey(),
      rowVersion: rowVersion.value,
    });
    if (response.data?.code !== 0) {
      Message.error(response.data?.message || "交卷失败，请稍后重试");
      return;
    }
    finished.value = true;
    await loadResult();
    notifyLearningUpdated({
      courseCode: queryValue(route.query.courseCode),
      unitCode: queryValue(route.query.unitCode) || undefined,
      runId: runId.value,
      reason: "ASSESSMENT_FINISHED",
    });
  } catch (e) {
    Message.error(
      e instanceof Error ? e.message : "交卷失败，请检查网络后重试"
    );
  } finally {
    finishing.value = false;
  }
};

const finishRun = async () => {
  if (!run.value || finished.value || finishing.value) return;
  if (!isSelfTest.value) {
    await finishPracticeRun();
    return;
  }
  const saved = await flushSave();
  if (!saved) {
    Message.error("草稿尚未保存成功，交卷已取消；请先重试保存");
    return;
  }
  const unanswered = questions.value.filter(
    (question) =>
      !question.offline && !question.revisionChanged && !isAnswered(question)
  );
  const unsure = questions.value.filter(
    (question) => marked.value[String(question.id)]
  );
  const lines = [
    `未作答 ${unanswered.length} 题${
      unanswered.length
        ? `（第 ${unanswered
            .map((q) => questions.value.indexOf(q) + 1)
            .join("、")} 题）`
        : ""
    }；标记不确定 ${unsure.length} 题。`,
    "交卷后统一判分并显示解析，答案不能再修改。",
  ];
  Modal.confirm({
    title: "确认交卷？",
    content: lines.join("\n"),
    okText: "交卷",
    cancelText: "继续答题",
    onOk: async () => {
      await doFinishSelfTest();
    },
  });
};

const loadResult = async () => {
  if (!runId.value || resultLoading.value) return;
  resultLoading.value = true;
  resultError.value = "";
  try {
    const response = await getAssessmentResult(runId.value);
    if (response.data?.code === 0 && response.data.data) {
      result.value = response.data.data;
    } else {
      resultError.value = response.data?.message || "结果读取失败";
    }
  } catch (e) {
    resultError.value =
      e instanceof Error ? e.message : "结果读取失败，请稍后重试";
  } finally {
    resultLoading.value = false;
  }
};

const accuracyText = computed(() =>
  result.value?.accuracy === null || result.value?.accuracy === undefined
    ? "-"
    : `${Math.round(result.value.accuracy * 100)}%`
);

const percentText = (value: number | null | undefined) =>
  value === null || value === undefined ? "-" : `${Math.round(value * 100)}%`;

const STATUS_TEXT: Record<string, string> = {
  CORRECT: "答对",
  WRONG: "答错",
  UNANSWERED: "未作答",
  PENDING_SELF_ASSESSMENT: "待自评",
  INVALID_OFFLINE: "已下线",
  INVALID_VERSION: "版本失效",
};
const statusText = (status: string) => STATUS_TEXT[status] || status;

const focusEntries = computed(() => {
  if (!result.value) return [];
  const ids = new Set([
    ...result.value.wrongQuestionIds,
    ...result.value.unsureQuestionIds,
  ]);
  return result.value.questions.filter((item) => ids.has(item.questionId));
});

const scrollToResult = (questionId: string) => {
  document
    .getElementById(`result-${questionId}`)
    ?.scrollIntoView({ block: "start", behavior: "smooth" });
};

// ================= 逐题练习（PRACTICE） =================

/** 反馈展开后「下一题」才是主操作；作答前主操作是卡内的提交答案 */
const feedbackQuestionId = ref("");
const nextPrimary = computed(
  () =>
    Boolean(currentQuestion.value) &&
    feedbackQuestionId.value === String(currentQuestion.value?.id)
);
watch(
  () => (currentQuestion.value ? String(currentQuestion.value.id) : ""),
  () => {
    feedbackQuestionId.value = "";
  }
);

const answerHandler = computed(() => {
  const questionId = currentQuestion.value?.id;
  const answeredRunId = runId.value;
  return (answer: LearningAnswerResultVO) =>
    onAnswered(questionId, answeredRunId, answer);
});

const onAnswered = (
  questionId: LearningQuestionVO["id"] | undefined,
  answeredRunId: string,
  answer: LearningAnswerResultVO
) => {
  if (answeredRunId !== runId.value) return;
  const question = questions.value.find((item) => item.id === questionId);
  if (!question || question.offline) return;
  if (!question.hasAttempted) {
    question.firstResult = answer.correct ? "CORRECT" : "WRONG";
  }
  question.hasAttempted = true;
  question.latestResult = answer.correct ? "CORRECT" : "WRONG";
  feedbackQuestionId.value = String(question.id);
  firstCorrectCount.value = questions.value.filter(
    (item) => !item.offline && item.firstResult === "CORRECT"
  ).length;
};

const finishPracticeRun = async () => {
  if (!run.value || finished.value || finishing.value) return;
  const requestedRunId = runId.value;
  finishing.value = true;
  try {
    const response = await finishAssessment({
      runId: run.value.runId,
      answeredCount: questions.value.filter((q) => q.hasAttempted).length,
      firstCorrect: firstCorrectCount.value,
    });
    if (requestedRunId !== runId.value) return;
    if (response.data?.code === 0) {
      summary.value = response.data.data;
      finished.value = true;
      currentIndex.value = questions.value.length
        ? questions.value.length - 1
        : 0;
    } else {
      Message.error(response.data?.message || "结束本轮失败");
    }
  } catch (e) {
    if (requestedRunId === runId.value)
      Message.error("结束本轮失败，请稍后重试");
  } finally {
    if (requestedRunId === runId.value) finishing.value = false;
  }
};

// ================= 加载与路由 =================

const load = async () => {
  if (!runId.value) return;
  const sequence = ++loadSequence;
  const requestedRunId = runId.value;
  loading.value = true;
  try {
    const response = await getAssessmentRun(requestedRunId);
    if (sequence !== loadSequence || requestedRunId !== runId.value) return;
    if (response.data?.code !== 0) {
      Message.error(response.data?.message || "加载测评会话失败");
      return;
    }
    const data = response.data.data;
    run.value = data;
    questions.value = (data.questions || []).map((question) => ({
      ...question,
      offline:
        question.offline ||
        !question.type ||
        question.reviewStatus !== "PUBLISHED",
      revisionChanged: Boolean(question.revisionChanged),
    }));
    finished.value = data.status === "FINISHED";
    firstCorrectCount.value = questions.value.filter(
      (q) => !q.offline && q.firstResult === "CORRECT"
    ).length;
    if (data.mode === "SELF_TEST") {
      rowVersion.value = data.rowVersion ?? 0;
      savedAt.value = data.savedAt ?? null;
      applyDrafts(
        questions.value.map((question) => ({
          questionId: String(question.id),
          draftAnswer: question.draftAnswer ?? emptyAnswer(question),
          markedUnsure: Boolean(question.markedUnsure),
        }))
      );
      if (finished.value) {
        await loadResult();
      }
      return;
    }
    if (finished.value) {
      const available = questions.value.filter((question) => !question.offline);
      summary.value = {
        runId: data.runId,
        totalQuestions: available.length,
        answeredQuestions: available.filter((question) => question.hasAttempted)
          .length,
        firstCorrect: firstCorrectCount.value,
      };
    }
  } catch {
    if (sequence === loadSequence)
      Message.error("加载测评会话失败，请稍后重试");
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
};

watch(
  runId,
  () => {
    run.value = null;
    questions.value = [];
    currentIndex.value = 0;
    finished.value = false;
    finishing.value = false;
    summary.value = null;
    firstCorrectCount.value = 0;
    result.value = null;
    resultError.value = "";
    answers.value = {};
    marked.value = {};
    saveState.value = "idle";
    conflict.value = false;
    void load();
  },
  { immediate: true }
);

watch(
  [() => JSON.stringify(answers.value), () => JSON.stringify(marked.value)],
  () => scheduleSave()
);

onBeforeRouteLeave(async () => {
  if (!isSelfTest.value || finished.value) return true;
  const saved = await flushSave();
  if (!saved) {
    return window.confirm("草稿尚未保存成功，离开将丢失本次修改。仍要离开吗？");
  }
  return true;
});

onBeforeUnmount(() => {
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
});
</script>

<style scoped>
.assessment-run {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.run-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
}
.run-title {
  margin: 0 0 var(--oslab-space-1);
  color: var(--oslab-ink);
  font-size: var(--oslab-text-page);
  font-weight: 600;
  line-height: 1.3;
  font-family: var(--oslab-font-display);
}
.head-actions {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-2);
  flex-wrap: wrap;
}
.save-state {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.save-state.is-error {
  color: var(--oslab-weak-text);
}
.save-state.is-conflict {
  color: var(--oslab-review-text);
}
.run-alert {
  margin: 0;
}
.run-navigator {
  flex-shrink: 0;
}
.self-nav {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-3);
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
}
.self-nav-item {
  position: relative;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--oslab-radius-md);
  border: 1px solid var(--oslab-line);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.self-nav-item.answered {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.self-nav-item.current {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
  font-weight: 700;
}
.self-nav-item.marked {
  border-style: dashed;
}
.self-nav-item.offline {
  opacity: 0.45;
  cursor: not-allowed;
}
.nav-dot {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--oslab-annotation);
}
.question-area {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.nav-buttons {
  display: flex;
  justify-content: space-between;
}
.self-test-note {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.run-summary {
  padding: var(--oslab-space-4);
  border-radius: var(--oslab-radius-container);
}
.section-title {
  font-size: var(--oslab-font-body);
  font-weight: 600;
  color: var(--oslab-ink);
  margin-bottom: var(--oslab-space-2);
}
.btn-row {
  display: flex;
  gap: var(--oslab-space-2);
  margin-top: var(--oslab-space-3);
}
.result-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--oslab-space-3);
  padding: var(--oslab-space-4);
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-value {
  color: var(--oslab-ink);
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.stat-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.focus-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.focus-label {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.focus-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 5px 10px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
}
.focus-chip:hover {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
}
.focus-flag {
  color: var(--oslab-annotation);
  font-size: 11px;
}
.result-list {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
}
.result-item {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
  padding: 14px 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-left-width: 4px;
  border-radius: var(--oslab-radius-lg);
  scroll-margin-top: 16px;
}
.result-item.is-correct {
  border-left-color: var(--oslab-mastered-text);
}
.result-item.is-wrong {
  border-left-color: var(--oslab-weak-text);
}
.result-item.is-pending-self-assessment,
.result-item.is-invalid-offline,
.result-item.is-invalid-version {
  border-left-color: var(--oslab-review-text);
}
.result-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.result-status {
  padding: 1px 8px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-muted);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.result-title {
  margin: 0;
  flex: 1;
  min-width: 0;
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 600;
}
.result-flag {
  color: var(--oslab-annotation);
  font-size: var(--oslab-text-small);
}
.result-note {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.result-details summary {
  cursor: pointer;
  color: var(--oslab-primary);
  font-size: var(--oslab-text-small);
}
.result-analysis {
  margin-top: var(--oslab-space-2);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
}
.result-mastery {
  margin: 0;
  padding-left: 18px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.result-actions {
  display: flex;
  gap: var(--oslab-space-2);
  flex-wrap: wrap;
  padding-bottom: 24px;
}
@media (max-width: 640px) {
  .run-title {
    font-size: 22px;
  }
  .head-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
