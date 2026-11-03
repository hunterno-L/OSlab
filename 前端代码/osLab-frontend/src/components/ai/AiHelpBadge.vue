<template>
  <span
    class="ai-help-badge"
    :class="`ai-help-badge--${tone}`"
    :title="description"
    >{{ label }}</span
  >
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed } from "vue";
import type { AiHelpLevel } from "@/api/ai";

const props = withDefaults(
  defineProps<{
    level?: AiHelpLevel | string | null;
  }>(),
  { level: "UNKNOWN" }
);

const normalized = computed<AiHelpLevel>(() => {
  const value = String(props.level || "").toUpperCase();
  if (value === "NONE") return "NONE";
  if (value === "HINT") return "HINT";
  if (value === "ANSWER_REVEALED") return "ANSWER_REVEALED";
  return "UNKNOWN";
});

const LABELS: Record<AiHelpLevel, string> = {
  NONE: "未使用平台提示",
  HINT: "使用过提示",
  ANSWER_REVEALED: "已看答案",
  UNKNOWN: "帮助情况未知",
};

const DESCRIPTIONS: Record<AiHelpLevel, string> = {
  NONE: "平台未记录到提示或答案揭示；不代表没有使用平台外帮助。",
  HINT: "作答前使用过平台提示，本次表现不能等同于独立完成。",
  ANSWER_REVEALED: "作答前已查看答案或解析，本次作答不计独立观测。",
  UNKNOWN: "历史记录缺少帮助信息；不倒推为“未获得帮助”。",
};

const tone = computed(() => normalized.value.toLowerCase().replace("_", "-"));
const label = computed(() => LABELS[normalized.value]);
const description = computed(() => DESCRIPTIONS[normalized.value]);
</script>

<style scoped>
.ai-help-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
  background: var(--oslab-panel-muted);
  color: var(--oslab-muted-strong);
}
.ai-help-badge--none {
  background: var(--oslab-mastered-soft);
  color: var(--oslab-mastered-text);
}
.ai-help-badge--hint {
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
}
.ai-help-badge--answer-revealed {
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
}
</style>
