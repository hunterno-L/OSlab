<template>
  <article class="self-test-card">
    <header class="stc-head">
      <h2 class="stc-title">{{ question.title }}</h2>
      <div class="stc-meta">
        <span class="stc-tag">{{ typeText }}</span>
        <span v-if="question.chapter" class="stc-tag">{{
          question.chapter
        }}</span>
        <span v-if="question.offline" class="stc-tag stc-tag-warn"
          >已失效，不计分</span
        >
        <span v-else-if="question.revisionChanged" class="stc-tag stc-tag-warn"
          >题目已更新，不计分</span
        >
      </div>
    </header>

    <p v-if="question.offlineReason" class="stc-notice" role="status">
      {{ question.offlineReason }}
    </p>

    <MdViewer class="stc-content" :value="question.content || ''" />

    <a-radio-group
      v-if="question.type === 'JUDGE'"
      :model-value="singleValue"
      :disabled="disabled"
      class="stc-options"
      direction="vertical"
      @change="onSingleChange"
    >
      <a-radio class="stc-option" value="true">正确</a-radio>
      <a-radio class="stc-option" value="false">错误</a-radio>
    </a-radio-group>

    <a-radio-group
      v-else-if="question.type === 'SINGLE'"
      :model-value="singleValue"
      :disabled="disabled"
      class="stc-options"
      direction="vertical"
      @change="onSingleChange"
    >
      <a-radio
        v-for="option in parsedOptions"
        :key="option.key"
        class="stc-option"
        :value="option.key"
      >
        <span class="stc-option-key">{{ option.key }}</span>
        <MdViewer class="stc-option-text" :value="option.value" />
      </a-radio>
    </a-radio-group>

    <a-checkbox-group
      v-else-if="question.type === 'MULTIPLE'"
      :model-value="multipleValue"
      :disabled="disabled"
      class="stc-options"
      direction="vertical"
      @change="onMultipleChange"
    >
      <a-checkbox
        v-for="option in parsedOptions"
        :key="option.key"
        class="stc-option"
        :value="option.key"
      >
        <span class="stc-option-key">{{ option.key }}</span>
        <MdViewer class="stc-option-text" :value="option.value" />
      </a-checkbox>
    </a-checkbox-group>

    <a-input
      v-else-if="question.type === 'FILL_BLANK'"
      :model-value="textValue"
      class="stc-fill"
      placeholder="请输入答案"
      allow-clear
      :disabled="disabled"
      @update:model-value="onTextChange"
    />

    <a-textarea
      v-else-if="question.type === 'COMPREHENSIVE'"
      :model-value="textValue"
      class="stc-fill"
      placeholder="写下你的思路或答案；交卷后对照参考解析自评"
      :auto-size="{ minRows: 5, maxRows: 16 }"
      :disabled="disabled"
      @update:model-value="onTextChange"
    />

    <div v-else class="stc-notice">暂不支持的题型：{{ question.type }}</div>

    <footer class="stc-foot">
      <a-checkbox
        :model-value="markedUnsure"
        :disabled="disabled"
        @change="(value) => emit('update:markedUnsure', Boolean(value))"
      >
        标记为不确定
      </a-checkbox>
      <span class="stc-hint">自测期间不显示对错与解析，交卷后统一查看</span>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MdViewer from "@/components/MdViewer.vue";
import type { LearningQuestionVO, QuestionOption } from "@/api/learning";

// eslint-disable-next-line no-undef
const props = defineProps<{
  question: LearningQuestionVO;
  modelValue: string | string[] | null;
  markedUnsure: boolean;
  disabled?: boolean;
}>();

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (e: "update:modelValue", value: string | string[] | null): void;
  (e: "update:markedUnsure", value: boolean): void;
}>();

const TYPE_TEXT: Record<string, string> = {
  JUDGE: "判断题",
  SINGLE: "单选题",
  MULTIPLE: "多选题",
  FILL_BLANK: "填空题",
  COMPREHENSIVE: "综合应用题",
};
const typeText = computed(() => TYPE_TEXT[props.question?.type] || "题目");

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

const singleValue = computed(() =>
  typeof props.modelValue === "string" ? props.modelValue : ""
);
const multipleValue = computed(() =>
  Array.isArray(props.modelValue) ? props.modelValue : []
);
const textValue = computed(() =>
  typeof props.modelValue === "string" ? props.modelValue : ""
);

const emitAnswer = (value: string | string[]) =>
  emit("update:modelValue", value);
const onSingleChange = (value: string | number | boolean) =>
  emitAnswer(String(value));
const onMultipleChange = (value: Array<string | number | boolean>) =>
  emitAnswer(value.map(String).sort());
const onTextChange = (value: string) => emitAnswer(String(value ?? ""));
</script>

<style scoped>
.self-test-card {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.stc-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.stc-title {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}
.stc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.stc-tag {
  padding: 1px 8px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel-muted);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.stc-tag-warn {
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
}
.stc-notice {
  margin: 0;
  padding: 8px 12px;
  border: 1px solid var(--oslab-review);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-review-soft);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
}
.stc-content {
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-font-body);
  line-height: var(--oslab-leading-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.stc-options {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
}
.stc-options :deep(.arco-radio),
.stc-options :deep(.arco-checkbox) {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  min-height: 48px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
  touch-action: manipulation;
}
.stc-options :deep(.arco-radio-checked),
.stc-options :deep(.arco-checkbox-checked) {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
}
.stc-options :deep(.arco-radio-label),
.stc-options :deep(.arco-checkbox-label) {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.stc-option-key {
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
.stc-option-text {
  display: inline;
  flex: 1;
  min-width: 0;
  margin: 0;
  color: var(--oslab-ink-soft);
  font-size: 14px;
  line-height: 1.6;
}
.stc-option-text :deep(p) {
  display: inline;
  margin: 0;
}
.stc-fill {
  max-width: 560px;
}
.stc-foot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
  padding-top: var(--oslab-space-2);
  border-top: 1px solid var(--oslab-line-soft);
}
.stc-hint {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
</style>
