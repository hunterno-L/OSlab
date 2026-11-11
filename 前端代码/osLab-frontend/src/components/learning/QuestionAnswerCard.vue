<template>
  <article class="qa-card">
    <header v-if="showMetadata" class="qa-head">
      <h2 class="qa-title">{{ question.title }}</h2>
      <div class="qa-meta">
        <span v-if="typeText" class="qa-tag qa-tag-type">{{ typeText }}</span>
        <span class="qa-tag">{{ difficultyText }}</span>
        <span v-if="contextLabel" class="qa-tag">{{ contextLabel }}</span>
        <span v-if="question.chapter" class="qa-tag">{{
          question.chapter
        }}</span>
        <span
          v-for="node in question.nodeDisplayNames || question.nodeNames || []"
          :key="node"
          class="qa-tag"
          >{{ node }}</span
        >
        <span
          v-if="question.hasAttempted"
          class="qa-tag qa-tag-state"
          :class="
            question.latestResult === 'CORRECT'
              ? 'qa-tag-state--correct'
              : 'qa-tag-state--review'
          "
          >最近{{ question.latestResult === "CORRECT" ? "答对" : "答错" }}</span
        >
      </div>
    </header>

    <div v-if="unavailableReason" class="qa-unavailable" role="alert">
      {{ unavailableReason }}
    </div>
    <MdViewer class="qa-content" :value="question.content" />
    <button
      class="qa-metadata-toggle"
      type="button"
      :aria-expanded="showMetadata"
      @click="showMetadata = !showMetadata"
    >
      {{ showMetadata ? "隐藏标签" : "显示标签" }}
    </button>

    <!-- 判断题：JUDGE 提交 "true"/"false" -->
    <a-radio-group
      v-if="question.type === 'JUDGE'"
      v-model:model-value="judgeAnswer"
      :disabled="answerDisabled"
      class="qa-options"
      direction="vertical"
    >
      <a-radio class="qa-option" value="true">正确</a-radio>
      <a-radio class="qa-option" value="false">错误</a-radio>
    </a-radio-group>

    <!-- 单选题：SINGLE 提交 "A" -->
    <a-radio-group
      v-else-if="question.type === 'SINGLE'"
      v-model:model-value="singleAnswer"
      :disabled="answerDisabled"
      class="qa-options"
      direction="vertical"
    >
      <a-radio
        v-for="opt in parsedOptions"
        :key="opt.key"
        class="qa-option"
        :value="opt.key"
      >
        <span class="qa-option-key">{{ opt.key }}</span>
        <MdViewer class="qa-option-text" :value="opt.value" />
      </a-radio>
    </a-radio-group>

    <!-- 多选题：MULTIPLE 提交 ["A","C"] -->
    <a-checkbox-group
      v-else-if="question.type === 'MULTIPLE'"
      v-model:model-value="multipleAnswer"
      :disabled="answerDisabled"
      class="qa-options"
      direction="vertical"
    >
      <a-checkbox
        v-for="opt in parsedOptions"
        :key="opt.key"
        class="qa-option"
        :value="opt.key"
      >
        <span class="qa-option-key">{{ opt.key }}</span>
        <MdViewer class="qa-option-text" :value="opt.value" />
      </a-checkbox>
    </a-checkbox-group>

    <!-- 填空题：FILL_BLANK 提交字符串；输入高 ≥44px -->
    <a-input
      v-else-if="question.type === 'FILL_BLANK'"
      v-model:model-value="fillAnswer"
      class="qa-fill"
      placeholder="请输入答案"
      :disabled="answerDisabled"
      allow-clear
    />
    <a-textarea
      v-else-if="question.type === 'COMPREHENSIVE'"
      v-model:model-value="comprehensiveAnswer"
      class="qa-fill"
      placeholder="写下你的思路或答案，提交后对照参考解析"
      :auto-size="{ minRows: 5, maxRows: 16 }"
      :disabled="answerDisabled"
    />
    <div v-else-if="!unavailableReason" class="qa-unsupported">
      暂不支持的题型：{{ question.type }}
    </div>

    <div v-if="!result" class="qa-help-row">
      <span class="qa-help-label">提交前记录帮助：</span>
      <button
        type="button"
        class="qa-help-btn"
        :disabled="helpSaving"
        @click="markHelp('HINT')"
      >
        我需要提示
      </button>
      <button
        v-if="aiActionId"
        type="button"
        class="qa-help-btn qa-help-btn--reveal"
        :disabled="helpSaving"
        @click="markHelp('ANSWER_REVEALED')"
      >
        我已查看答案
      </button>
      <AiHelpBadge :level="currentHelpLevel" />
      <span v-if="helpNotice" class="qa-hint">{{ helpNotice }}</span>
    </div>

    <div class="qa-submit-row">
      <a-button
        v-if="hasResult && hasNext"
        type="primary"
        @click="emit('next')"
      >
        下一题
      </a-button>
      <a-button
        v-else
        :type="hasResult ? 'outline' : 'primary'"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ hasResult ? "再答一次" : "提交答案" }}
      </a-button>
      <a-button
        v-if="hasResult && hasNext"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >
        再答一次
      </a-button>
      <span
        v-if="question.type === 'MULTIPLE' && !hasResult && !answerDisabled"
        class="qa-hint"
        >多选：选齐全部正确选项后提交</span
      >
      <span v-if="question.hasAttempted && !hasResult" class="qa-hint"
        >你曾作答过本题，可再次练习</span
      >
      <span v-if="aiStarting" class="qa-hint">正在准备 AI 任务…</span>
      <span v-if="aiSubmitError" class="qa-hint qa-help-error" role="alert">{{
        aiSubmitError
      }}</span>
    </div>

    <!-- 提交后反馈收敛：结果 + 简短依据 + 详细解析入口 -->
    <section
      v-if="result"
      class="qa-feedback"
      :class="
        result.correct === null
          ? 'is-ungraded'
          : result.correct
          ? 'is-correct'
          : 'is-wrong'
      "
      role="status"
    >
      <div class="qa-result">
        <span class="qa-result-badge">{{
          result.correct === null
            ? "待评阅：已显示参考解析"
            : result.correct
            ? "回答正确"
            : "回答错误"
        }}</span>
        <span class="qa-result-hint">{{
          serverJudgeMessage || resultHint
        }}</span>
      </div>

      <div class="qa-feedback-actions">
        <button
          type="button"
          class="qa-detail-toggle"
          :aria-expanded="detailedOpen"
          @click="detailedOpen = !detailedOpen"
        >
          {{ detailedOpen ? "收起详细解析" : "查看详细解析" }}
        </button>
        <a-button
          v-if="result.correct === false"
          size="mini"
          @click="openRemedial"
          >补学本题知识点</a-button
        >
      </div>

      <template v-if="detailedOpen">
        <div class="qa-help-evidence">
          <AiHelpBadge :level="evidence?.helpLevel || 'UNKNOWN'" />
          <span v-if="evidence" class="qa-hint">{{
            evidence.eligibleForObservation
              ? evidence.eligibilityReason || "本次计入独立观测"
              : evidence.eligibilityReason
          }}</span>
          <span v-else class="qa-hint"
            >普通练习不生成帮助快照；从 AI 任务进入可记录提示与看答案。</span
          >
          <span v-if="nextReviewDueAt" class="qa-hint"
            >下次复习：{{ nextReviewDueAt }}</span
          >
        </div>

        <div class="qa-feedback-block">
          <h3 class="qa-feedback-title">解析</h3>
          <MdViewer
            v-if="result.analysis"
            class="qa-feedback-text"
            :value="result.analysis"
          />
          <p v-else class="qa-feedback-text qa-hint">本题暂无解析</p>
        </div>

        <div v-if="result.misconception" class="qa-feedback-block">
          <h3 class="qa-feedback-title">常见误区</h3>
          <p class="qa-feedback-text">{{ result.misconception }}</p>
        </div>

        <div
          v-if="result.masteryChanges && result.masteryChanges.length"
          class="qa-feedback-block"
        >
          <h3 class="qa-feedback-title">掌握度变化</h3>
          <ul class="qa-mastery-list">
            <li
              v-for="change in result.masteryChanges"
              :key="change.nodeName"
              class="qa-mastery-item"
            >
              <span class="qa-mastery-node">{{ change.nodeName }}</span>
              <span class="oslab-mono qa-mastery-value"
                >{{ percentText(change.masteryBefore) }} →
                {{ percentText(change.masteryAfter) }}</span
              >
              <span
                class="oslab-status-tag"
                :class="statusTagClass(change.status)"
                >{{ statusText(change.status) }}</span
              >
              <span v-if="change.countedAsObservation === false" class="qa-hint"
                >重复作答，不计独立观测</span
              >
            </li>
          </ul>
        </div>

        <div class="qa-feedback-block qa-next">
          <h3 class="qa-feedback-title">下一步</h3>
          <p class="qa-feedback-text">{{ nextHint }}</p>
        </div>

        <div class="qa-feedback-block qa-ai-analysis">
          <div class="qa-ai-head">
            <h3 class="qa-feedback-title">AI 分析</h3>
            <AiModeBadge v-if="analysisTurn?.mode" :mode="analysisTurn.mode" />
          </div>
          <a-button
            v-if="!analysisTurn"
            size="small"
            :loading="analysisLoading"
            :disabled="analysisLoading"
            @click="requestAnalysis"
          >
            请求 AI 分析
          </a-button>
          <p v-if="analysisError" class="qa-help-error" role="alert">
            {{ analysisError }}
          </p>
          <template v-if="analysisTurn">
            <MdViewer
              v-if="analysisTurn.answer"
              class="qa-feedback-text"
              :value="analysisTurn.answer"
            />
            <AiDiagnosisPanel :diagnosis="analysisTurn.diagnosis" />
            <AiToolTrace :entries="analysisTurn.toolTrace" />
            <div v-if="analysisTurn.sources?.length" class="qa-ai-sources">
              <AiSourceCard
                v-for="(source, index) in analysisTurn.sources"
                :key="source.sourceId + source.blockId"
                :source="source"
                :index="index"
                @open="openAnalysisSource"
              />
            </div>
            <AiNextActions
              v-if="analysisTurn.nextActions?.length"
              :actions="analysisTurn.nextActions"
              :course-code="analysisCourseCode"
              title="下一步"
              empty-text=""
              compact
              :refreshable="false"
            />
          </template>
        </div>
      </template>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import MdViewer from "@/components/MdViewer.vue";
import AiHelpBadge from "@/components/ai/AiHelpBadge.vue";
import AiModeBadge from "@/components/ai/AiModeBadge.vue";
import AiDiagnosisPanel from "@/components/ai/AiDiagnosisPanel.vue";
import AiToolTrace from "@/components/ai/AiToolTrace.vue";
import AiSourceCard from "@/components/ai/AiSourceCard.vue";
import AiNextActions from "@/components/ai/AiNextActions.vue";
import {
  answerLearningQuestion,
  type LearningAnswerResultVO,
  type LearningQuestionVO,
  type QuestionOption,
} from "@/api/learning";
import {
  createPracticeAttempt,
  recordPracticeHelp,
  type PracticeAttemptVO,
} from "@/api/assessment";
import { openAssessmentTutor } from "@/utils/assessmentTutor";
import { notifyLearningUpdated } from "@/utils/learningEvents";
import type {
  AiActivityRefVO,
  AiEvidenceSnapshot,
  AiSourceRef,
  AiSubmitActionRequest,
  AiTurnVO,
} from "@/api/ai";
import {
  getStartedAiAction,
  recordAiHelp,
  startAiPlanAction,
  submitAiPlanAction,
} from "@/composables/useAiActions";
import { askAi, useAiConversation } from "@/composables/useAiConversation";
import { askAiAssistant } from "@/composables/useAiLauncher";
import { getAiPageContext } from "@/composables/useAiPageContext";
import {
  aiSourceRoute,
  highlightSourceInPage,
  openAiMaterialSource,
} from "@/utils/aiSources";

// eslint-disable-next-line no-undef
const props = defineProps<{
  question: LearningQuestionVO;
  assessmentRunId?: string | number;
  sourceUnitCode?: string;
  sourceActivityCode?: string;
  disabled?: boolean;
  disabledReason?: string;
  /** 列表流中的真实目录上下文（如“单元 · 小节”） */
  contextLabel?: string;
  /** 从 AI 下一步任务进入时的 actionId（帮助等级与证据资格走统一动作接口） */
  aiActionId?: string;
  /** 当前课程编码（普通题卡也用于 AI 分析的会话作用域） */
  courseCode?: string;
  /** 列表流中后面还有题目时，提交后主操作变为「下一题」 */
  hasNext?: boolean;
}>();

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (e: "answered", result: LearningAnswerResultVO): void;
  (e: "next"): void;
}>();

const router = useRouter();
const { errorMsg: conversationError } = useAiConversation();
const submitting = ref(false);
const showMetadata = ref(false);
const judgeAnswer = ref("");
const singleAnswer = ref("");
const multipleAnswer = ref<string[]>([]);
const fillAnswer = ref("");
const comprehensiveAnswer = ref("");
const result = ref<LearningAnswerResultVO | null>(null);
const aiRef = ref<AiActivityRefVO | null>(null);
const aiStarting = ref(false);
const aiSubmitError = ref("");
const serverJudgeMessage = ref("");
const evidence = ref<AiEvidenceSnapshot | null>(null);
const helpSaving = ref(false);
const helpNotice = ref("");
const analysisTurn = ref<AiTurnVO | null>(null);
const analysisLoading = ref(false);
const analysisError = ref("");
const nextReviewDueAt = ref("");
const detailedOpen = ref(false);
const practiceAttempt = ref<PracticeAttemptVO | null>(null);

const currentHelpLevel = computed(
  () => evidence.value?.helpLevel || aiRef.value?.helpLevel || "UNKNOWN"
);
const analysisCourseCode = computed(
  () =>
    props.courseCode ||
    getAiPageContext()?.courseCode ||
    "course.operating-systems"
);

async function loadAiAction() {
  const actionId = props.aiActionId;
  if (!actionId) {
    aiRef.value = null;
    return;
  }
  const cached = getStartedAiAction(actionId);
  if (cached) {
    aiRef.value = cached;
    return;
  }
  aiStarting.value = true;
  aiSubmitError.value = "";
  const ref = await startAiPlanAction(actionId);
  if (ref) aiRef.value = ref;
  else aiSubmitError.value = "任务启动失败，请刷新页面后重试。";
  aiStarting.value = false;
}

onMounted(loadAiAction);
watch(
  () => props.aiActionId,
  () => {
    result.value = null;
    evidence.value = null;
    analysisTurn.value = null;
    void loadAiAction();
  }
);

/** 建立/复用本题的练习尝试上下文；失败时不提交、不展示提示。 */
async function ensurePracticeAttempt(): Promise<PracticeAttemptVO | null> {
  if (practiceAttempt.value) return practiceAttempt.value;
  const key = newSubmissionKey();
  try {
    const response = await createPracticeAttempt({
      questionId: String(props.question.id),
      courseCode: props.courseCode,
      sourceUnitCode: props.sourceUnitCode,
      sourceActivityCode: props.sourceActivityCode,
      clientAttemptKey: key,
    });
    if (response.data?.code === 0 && response.data.data) {
      practiceAttempt.value = response.data.data;
      return practiceAttempt.value;
    }
    const message = response.data?.message || "无法建立练习尝试，请重试";
    aiSubmitError.value = message;
    return null;
  } catch {
    aiSubmitError.value = "无法建立练习尝试，请检查网络后重试";
    return null;
  }
}

/** 提交前记录帮助等级；答案揭示单独确认，提交后不可再改。 */
async function markHelp(level: "HINT" | "ANSWER_REVEALED") {
  if (result.value || helpSaving.value) return;
  const actionId = props.aiActionId;
  if (!actionId) {
    // 普通练习：帮助等级先落到服务端尝试上下文，再打开导师
    if (level === "ANSWER_REVEALED") {
      const confirmed = window.confirm(
        "标记后本次作答不计独立观测，且提交后不可撤销。确认已查看答案或解析？"
      );
      if (!confirmed) return;
    }
    helpSaving.value = true;
    helpNotice.value = "";
    const attempt = await ensurePracticeAttempt();
    if (!attempt) {
      helpSaving.value = false;
      helpNotice.value = aiSubmitError.value || "练习尝试建立失败，请重试。";
      return;
    }
    const response = await recordPracticeHelp(attempt.attemptContextId, {
      helpLevel: level,
    });
    helpSaving.value = false;
    if (!response) {
      helpNotice.value = "帮助等级记录失败，请重试。";
      return;
    }
    helpNotice.value = response.eligibleForObservation
      ? "已记录本次帮助等级。"
      : response.eligibilityReason || "本次作答不再计为独立观测。";
    if (level === "HINT") {
      openAssessmentTutor({
        intent: "HINT",
        courseCode: analysisCourseCode.value,
        questionId: String(props.question.id),
        attemptContextId: attempt.attemptContextId,
        assessmentRunId: props.assessmentRunId
          ? String(props.assessmentRunId)
          : undefined,
      });
    }
    return;
  }
  if (level === "ANSWER_REVEALED") {
    const confirmed = window.confirm(
      "标记后本次作答不计独立观测，且提交后不可撤销。确认已查看答案或解析？"
    );
    if (!confirmed) return;
  }
  helpSaving.value = true;
  helpNotice.value = "";
  const response = await recordAiHelp(actionId, level);
  helpSaving.value = false;
  if (!response) {
    helpNotice.value = "帮助等级记录失败，请重试。";
    return;
  }
  if (aiRef.value) {
    aiRef.value = { ...aiRef.value, helpLevel: response.helpLevel };
  }
  helpNotice.value = response.eligibleForObservation
    ? "已记录本次帮助等级。"
    : response.eligibilityReason || "本次作答不再计为独立观测。";
  if (level === "HINT") {
    // 提示内容由导师生成，但不得直接泄露答案；帮助等级已先落库
    askAiAssistant(
      "请给我一个不直接给出答案的提示，帮助我自己找到这道题的解法。"
    );
  }
}

/** 错题就地补学：回到本题来源单元的对应活动，不新建页面。 */
function openRemedial() {
  const courseCode = props.courseCode || getAiPageContext()?.courseCode || "";
  if (courseCode && props.sourceUnitCode) {
    void router.push({
      path: `/courses/${encodeURIComponent(
        courseCode
      )}/units/${encodeURIComponent(props.sourceUnitCode)}`,
      query: props.sourceActivityCode
        ? { activity: props.sourceActivityCode }
        : {},
    });
    return;
  }
  Message.info("可从课程目录进入对应单元复习本题知识点。");
}

async function requestAnalysis() {
  if (!result.value || analysisLoading.value) return;
  analysisLoading.value = true;
  analysisError.value = "";
  try {
    const verdict =
      result.value.correct === null
        ? "本题未自动评分"
        : result.value.correct
        ? "回答正确"
        : "回答错误";
    const turn = await askAi(
      `我刚提交了题目「${props.question.title}」，${verdict}。请结合我的学习证据分析可能的原因（证据不足时保留不确定性），并给出一个可立即执行的下一步。`,
      {
        context: {
          courseCode: analysisCourseCode.value,
          resourceRef: `question:${props.question.id}`,
          eventIds: evidence.value?.eventIds || [],
        },
      }
    );
    if (!turn) {
      analysisError.value =
        conversationError.value || "AI 分析暂时不可用，请稍后重试。";
      return;
    }
    analysisTurn.value = turn;
  } finally {
    analysisLoading.value = false;
  }
}

function openAnalysisSource(source: AiSourceRef) {
  if (openAiMaterialSource(source)) return;
  if (highlightSourceInPage(source)) return;
  const route = aiSourceRoute(source);
  if (route) {
    void router.push(route);
    Message.info("已打开对应课程章节。");
    return;
  }
  Message.warning("该来源暂无可打开的站内位置。");
}

watch(
  () => props.question.id,
  () => {
    showMetadata.value = false;
    detailedOpen.value = false;
    practiceAttempt.value = null;
  }
);

const unavailableReason = computed(() => {
  if (props.question.offline || props.question.reviewStatus !== "PUBLISHED") {
    return "该题目当前不可作答";
  }
  return props.disabled ? props.disabledReason || "当前不可作答" : "";
});

const answerDisabled = computed(
  () => submitting.value || Boolean(unavailableReason.value)
);

const parsedOptions = computed<QuestionOption[]>(() => {
  const raw = props.question?.options;
  if (!raw) return [];
  try {
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    return Array.isArray(parsed) ? (parsed as QuestionOption[]) : [];
  } catch {
    return [];
  }
});

const TYPE_TEXT: Record<string, string> = {
  JUDGE: "判断题",
  SINGLE: "单选题",
  MULTIPLE: "多选题",
  FILL_BLANK: "填空题",
  COMPREHENSIVE: "综合应用题",
};
const typeText = computed(() => TYPE_TEXT[props.question?.type] || "");

const DIFFICULTY_TEXT: Record<number, string> = {
  1: "入门",
  2: "基础",
  3: "进阶",
  4: "挑战",
  5: "综合",
};
const difficultyText = computed(() =>
  props.question?.difficulty && DIFFICULTY_TEXT[props.question.difficulty]
    ? `难度 ${DIFFICULTY_TEXT[props.question.difficulty]}`
    : "未标注难度"
);

const hasResult = computed(() => Boolean(result.value));

const resultHint = computed(() =>
  result.value?.correct === null
    ? "本题暂不自动评分，也不计入掌握度。"
    : result.value?.correct
    ? "本知识点得到一次有效验证。"
    : "对照解析弄清错因，再答一次或先继续本组练习。"
);

const nextHint = computed(() =>
  result.value?.correct === null
    ? "对照参考解析检查思路，然后继续练习。"
    : result.value?.correct
    ? "点击「下一题」继续本组练习，也可以返回知识点挑选其他题目。"
    : "建议先消化解析与常见误区再答一次；也可以先继续后面的题目，稍后回来复习。"
);

const canSubmit = computed(() => {
  if (answerDisabled.value || aiStarting.value) return false;
  switch (props.question?.type) {
    case "JUDGE":
      return judgeAnswer.value !== "";
    case "SINGLE":
      return singleAnswer.value !== "";
    case "MULTIPLE":
      return multipleAnswer.value.length > 0;
    case "FILL_BLANK":
      return fillAnswer.value.trim() !== "";
    case "COMPREHENSIVE":
      return comprehensiveAnswer.value.trim() !== "";
    default:
      return false;
  }
});

/** 每次提交生成新的幂等键：同键重发服务端去重，主动重试/再答一次使用新键 */
const newSubmissionKey = () => {
  const cryptoApi =
    typeof crypto !== "undefined"
      ? (crypto as Crypto & { randomUUID?: () => string })
      : null;
  return cryptoApi && typeof cryptoApi.randomUUID === "function"
    ? cryptoApi.randomUUID()
    : `sub-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const submit = async () => {
  if (!canSubmit.value || submitting.value) return;
  // 四种答案格式与后端判分契约严格一致
  let answer: string | string[];
  switch (props.question.type) {
    case "JUDGE":
      answer = judgeAnswer.value;
      break;
    case "SINGLE":
      answer = singleAnswer.value;
      break;
    case "MULTIPLE":
      answer = [...multipleAnswer.value].sort();
      break;
    case "FILL_BLANK":
      answer = fillAnswer.value.trim();
      break;
    case "COMPREHENSIVE":
      answer = comprehensiveAnswer.value.trim();
      break;
    default:
      return;
  }
  submitting.value = true;
  aiSubmitError.value = "";
  serverJudgeMessage.value = "";
  try {
    if (props.aiActionId) {
      const ref = aiRef.value || (await startAiPlanAction(props.aiActionId));
      if (!ref) {
        aiSubmitError.value = "任务尚未就绪，请刷新后重试。";
        return;
      }
      const body: AiSubmitActionRequest = {
        submissionKey: ref.submissionKey,
      };
      if (props.question.type === "JUDGE" || props.question.type === "SINGLE") {
        body.choiceKey = String(answer);
      } else if (props.question.type === "MULTIPLE") {
        body.choiceKey = (answer as string[]).join(",");
      } else {
        body.text = String(answer);
      }
      const data = await submitAiPlanAction(
        props.aiActionId,
        body,
        props.courseCode
      );
      if (!data) {
        aiSubmitError.value =
          "提交失败，请检查网络后重试（同一提交键不会重复判分）。";
        return;
      }
      evidence.value = data.evidence || null;
      serverJudgeMessage.value = data.judgeMessage || "";
      nextReviewDueAt.value = data.nextReviewDueAt || "";
      result.value = {
        attemptId: null,
        correct: data.correct,
        analysis: data.analysis ?? null,
        misconception: null,
        isFirst: Boolean(data.evidence?.eligibleForObservation),
        masteryChanges: data.masteryChanges || [],
      };
      emit("answered", result.value);
      return;
    }
    const attempt = await ensurePracticeAttempt();
    if (!attempt) return;
    const res = await answerLearningQuestion({
      questionId: props.question.id,
      answer,
      assessmentRunId: props.assessmentRunId,
      attemptContextId: attempt.attemptContextId,
      sourceUnitCode: props.sourceUnitCode,
      sourceActivityCode: props.sourceActivityCode,
      submissionKey: attempt.submissionKey,
    });
    if (res.data?.code === 0) {
      result.value = res.data.data;
      detailedOpen.value = false;
      emit("answered", result.value);
      notifyLearningUpdated({
        courseCode: props.courseCode || getAiPageContext()?.courseCode || "",
        unitCode: props.sourceUnitCode,
        activityCode: props.sourceActivityCode,
        eventIds: evidence.value?.eventIds,
        reason: "ANSWERED",
      });
      // 再答一次使用新的尝试上下文与提交键；终身观测去重仍由服务端判定
      practiceAttempt.value = null;
    }
  } catch {
    // The global axios interceptor shows business and transport errors once.
  } finally {
    submitting.value = false;
  }
};

const percentText = (value: number | null | undefined) =>
  value === null || value === undefined ? "-" : `${Math.round(value * 100)}%`;

const STATUS_TEXT: Record<string, string> = {
  GRAY: "未学习",
  RED: "薄弱",
  YELLOW: "待巩固",
  GREEN: "已掌握",
};
const statusText = (status: string) => STATUS_TEXT[status] || status;

const STATUS_TAG_CLASS: Record<string, string> = {
  GRAY: "oslab-status-tag--unseen",
  RED: "oslab-status-tag--weak",
  YELLOW: "oslab-status-tag--review",
  GREEN: "oslab-status-tag--mastered",
};
const statusTagClass = (status: string) =>
  STATUS_TAG_CLASS[status] || "oslab-status-tag--unseen";
</script>

<style scoped>
.qa-card {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-6);
}
.qa-head {
  margin-bottom: var(--oslab-space-3);
}
.qa-metadata-toggle {
  display: inline-block;
  margin: -8px 0 var(--oslab-space-3);
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  line-height: 1.4;
  cursor: pointer;
}
.qa-metadata-toggle:hover,
.qa-metadata-toggle:focus-visible {
  color: var(--oslab-primary);
  text-decoration: underline;
}
.qa-title {
  margin: 0;
  color: var(--oslab-ink);
  font-size: var(--oslab-text-section);
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.qa-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oslab-space-2);
  margin-top: var(--oslab-space-2);
}
.qa-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-soft);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 20px;
  white-space: nowrap;
}
.qa-tag-type {
  border-color: transparent;
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.qa-tag-state--correct {
  border-color: transparent;
  background: var(--oslab-mastered-soft, #e9f1ed);
  color: var(--oslab-mastered, #2f6b53);
}
.qa-tag-state--review {
  border-color: transparent;
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
}
.qa-unavailable {
  margin-bottom: var(--oslab-space-3);
  padding: 8px 12px;
  border: 1px solid var(--oslab-review);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-review-soft);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
}
.qa-content {
  margin: 0 0 var(--oslab-space-4);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 选项：整行点击区域；选中 = 蓝色边线 + 浅蓝底色 */
.qa-options {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
}
.qa-options :deep(.arco-radio),
.qa-options :deep(.arco-checkbox) {
  display: flex;
  align-items: flex-start;
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease);
}
.qa-options :deep(.arco-radio:hover),
.qa-options :deep(.arco-checkbox:hover) {
  background: var(--oslab-panel-soft);
}
.qa-options :deep(.arco-radio-checked),
.qa-options :deep(.arco-checkbox-checked) {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.qa-options :deep(.arco-radio-disabled),
.qa-options :deep(.arco-checkbox-disabled) {
  opacity: 0.6;
}
.qa-options :deep(.arco-radio-label),
.qa-options :deep(.arco-checkbox-label) {
  flex: 1;
  min-width: 0;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: 1.55;
  white-space: normal;
  overflow-wrap: anywhere;
}
.qa-fill {
  max-width: 480px;
}
.qa-fill :deep(.arco-input-wrapper) {
  height: 44px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
}
.qa-fill :deep(.arco-input-wrapper.arco-input-focus) {
  border-color: var(--oslab-primary);
  background: var(--oslab-panel);
}
.qa-fill :deep(.arco-input) {
  height: 42px;
  line-height: 42px;
}

.qa-submit-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
  margin-top: var(--oslab-space-4);
}
.qa-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.qa-help-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: var(--oslab-space-3);
  padding: 8px 10px;
  border: 1px dashed var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.qa-help-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.qa-help-btn {
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
}
.qa-help-btn:hover:not(:disabled) {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
}
.qa-help-btn--reveal:hover:not(:disabled) {
  border-color: var(--oslab-weak-text);
  color: var(--oslab-weak-text);
}
.qa-help-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.qa-help-error {
  color: var(--oslab-weak-text);
}
.qa-help-evidence {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: calc(-1 * var(--oslab-space-2));
}
.qa-ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--oslab-space-1);
}
.qa-ai-sources {
  display: flex;
  flex-direction: column;
  margin-top: var(--oslab-space-2);
}
.qa-ai-analysis {
  padding-top: var(--oslab-space-3);
  border-top: 1px solid var(--oslab-line-soft);
}

/* 反馈区：结果 → 解析 → 常见误区 → 掌握度变化 → 下一步 */
.qa-feedback {
  margin-top: var(--oslab-space-4);
  padding-top: var(--oslab-space-4);
  border-top: 1px solid var(--oslab-line);
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
}
.qa-result {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
  padding: 10px 14px;
  border-radius: var(--oslab-radius-md);
}
.qa-feedback.is-correct .qa-result {
  background: var(--oslab-mastered-soft);
}
.qa-feedback.is-wrong .qa-result {
  background: var(--oslab-weak-soft);
}
.qa-result-badge {
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 600;
}
.qa-feedback.is-correct .qa-result-badge {
  color: var(--oslab-mastered-text);
}
.qa-feedback.is-wrong .qa-result-badge {
  color: var(--oslab-weak-text);
}
.qa-result-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.qa-feedback-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.qa-detail-toggle {
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--oslab-primary);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
}
.qa-detail-toggle:hover,
.qa-detail-toggle:focus-visible {
  text-decoration: underline;
}
.qa-feedback-title {
  margin: 0 0 var(--oslab-space-1);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  font-weight: 600;
}
.qa-feedback-text {
  margin: 0;
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.qa-mastery-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
}
.qa-mastery-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
  font-size: var(--oslab-font-body);
}
.qa-mastery-node {
  color: var(--oslab-ink);
  font-weight: 500;
}
.qa-mastery-value {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.qa-next {
  padding-top: var(--oslab-space-3);
  border-top: 1px solid var(--oslab-line-soft);
}
.qa-unsupported {
  color: var(--oslab-weak-text);
  font-size: var(--oslab-font-body);
}

/* C+B 学习图册 */
.qa-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.qa-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--oslab-ink);
}
.qa-tag {
  border: 0;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-muted);
  color: var(--oslab-ink-soft);
}
.qa-content {
  max-width: 72ch;
}
.qa-options :deep(.arco-radio),
.qa-options :deep(.arco-checkbox) {
  align-items: center;
  box-sizing: border-box;
  min-height: 48px;
  padding: 10px 12px;
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  touch-action: manipulation;
}
.qa-options :deep(.arco-radio:hover),
.qa-options :deep(.arco-checkbox:hover) {
  background: var(--oslab-panel-soft);
}
.qa-options :deep(.arco-radio-checked),
.qa-options :deep(.arco-checkbox-checked) {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.qa-options :deep(.arco-radio-label),
.qa-options :deep(.arco-checkbox-label) {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.qa-option-key {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  border-radius: 6px;
  background: var(--oslab-panel-muted);
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 11px;
  font-weight: 600;
}
.qa-options :deep(.arco-radio-checked) .qa-option-key,
.qa-options :deep(.arco-checkbox-checked) .qa-option-key {
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
}
.qa-option-text {
  display: inline;
  flex: 1;
  min-width: 0;
  margin: 0;
  color: var(--oslab-ink-soft);
  font-size: 14px;
  line-height: 1.6;
}
.qa-option-text :deep(p) {
  display: inline;
  margin: 0;
}
.qa-submit-row :deep(.arco-btn-primary) {
  border-radius: var(--oslab-radius-control);
}
</style>
