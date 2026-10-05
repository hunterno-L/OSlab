<template>
  <article class="qa-card">
    <!-- 题干、题型、难度、章节和知识点标签在同一标题区（设计 §7.3） -->
    <header class="qa-head">
      <h2 class="qa-title">{{ question.title }}</h2>
      <div class="qa-meta">
        <span v-if="typeText" class="qa-tag qa-tag-type">{{ typeText }}</span>
        <span class="qa-tag">{{ difficultyText }}</span>
        <span v-if="question.chapter" class="qa-tag">{{
          question.chapter
        }}</span>
        <span
          v-for="node in question.nodeNames || []"
          :key="node"
          class="qa-tag"
          >{{ node }}</span
        >
      </div>
    </header>

    <div v-if="unavailableReason" class="qa-unavailable" role="alert">
      {{ unavailableReason }}
    </div>
    <p class="qa-content">{{ question.content }}</p>

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
        {{ opt.key }}. {{ opt.value }}
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
        {{ opt.key }}. {{ opt.value }}
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
    <div v-else-if="!unavailableReason" class="qa-unsupported">
      暂不支持的题型：{{ question.type }}
    </div>

    <div class="qa-submit-row">
      <a-button
        :type="hasResult ? 'outline' : 'primary'"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ hasResult ? "再答一次" : "提交答案" }}
      </a-button>
      <span
        v-if="question.type === 'MULTIPLE' && !hasResult && !answerDisabled"
        class="qa-hint"
        >多选：选齐全部正确选项后提交</span
      >
      <span v-if="question.hasAttempted && !hasResult" class="qa-hint"
        >你曾作答过本题，可再次练习</span
      >
    </div>

    <!-- 提交后反馈就地展开：结果 → 解析 → 常见误区 → 掌握度变化 → 下一步 -->
    <section
      v-if="result"
      class="qa-feedback"
      :class="result.correct ? 'is-correct' : 'is-wrong'"
      role="status"
    >
      <div class="qa-result">
        <span class="qa-result-badge">{{
          result.correct ? "回答正确" : "回答错误"
        }}</span>
        <span class="qa-result-hint">{{ resultHint }}</span>
      </div>

      <div class="qa-feedback-block">
        <h3 class="qa-feedback-title">解析</h3>
        <p v-if="result.analysis" class="qa-feedback-text">
          {{ result.analysis }}
        </p>
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
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Message } from "@arco-design/web-vue";
import {
  answerLearningQuestion,
  type LearningAnswerResultVO,
  type LearningQuestionVO,
  type QuestionOption,
} from "@/api/learning";

// eslint-disable-next-line no-undef
const props = defineProps<{
  question: LearningQuestionVO;
  assessmentRunId?: string | number;
  disabled?: boolean;
  disabledReason?: string;
}>();

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (e: "answered", result: LearningAnswerResultVO): void;
}>();

const submitting = ref(false);
const judgeAnswer = ref("");
const singleAnswer = ref("");
const multipleAnswer = ref<string[]>([]);
const fillAnswer = ref("");
const result = ref<LearningAnswerResultVO | null>(null);

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
  result.value?.correct
    ? "本知识点得到一次有效验证。"
    : "对照解析弄清错因，再答一次或先继续本组练习。"
);

const nextHint = computed(() =>
  result.value?.correct
    ? "点击「下一题」继续本组练习，也可以返回知识点挑选其他题目。"
    : "建议先消化解析与常见误区再答一次；也可以先继续后面的题目，稍后回来复习。"
);

const canSubmit = computed(() => {
  if (answerDisabled.value) return false;
  switch (props.question?.type) {
    case "JUDGE":
      return judgeAnswer.value !== "";
    case "SINGLE":
      return singleAnswer.value !== "";
    case "MULTIPLE":
      return multipleAnswer.value.length > 0;
    case "FILL_BLANK":
      return fillAnswer.value.trim() !== "";
    default:
      return false;
  }
});

/** 每次提交生成新的幂等键：同键重发服务端去重，主动重试/再答一次使用新键 */
const newSubmissionKey = () =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `sub-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

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
    default:
      return;
  }
  submitting.value = true;
  try {
    const res = await answerLearningQuestion({
      questionId: props.question.id,
      answer,
      assessmentRunId: props.assessmentRunId,
      submissionKey: newSubmissionKey(),
    });
    if (res.data?.code === 0) {
      result.value = res.data.data;
      emit("answered", result.value);
    } else {
      Message.error(res.data?.message || "判分失败");
    }
  } catch (e) {
    Message.error("提交失败，请稍后重试");
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
  color: var(--oslab-mastered);
}
.qa-feedback.is-wrong .qa-result-badge {
  color: var(--oslab-weak);
}
.qa-result-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
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
  padding: var(--oslab-space-3);
  border: 1px solid var(--oslab-line-soft);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.qa-unsupported {
  color: var(--oslab-weak);
  font-size: var(--oslab-font-body);
}
</style>
