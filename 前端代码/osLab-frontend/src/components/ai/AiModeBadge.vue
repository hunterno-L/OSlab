<template>
  <span
    v-if="mode"
    class="ai-mode-badge"
    :class="mode === 'FALLBACK' ? 'is-fallback' : 'is-ai'"
    :title="hint"
    >{{ label }}</span
  >
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed } from "vue";

const props = defineProps<{
  mode?: "AI" | "FALLBACK" | string | null;
}>();

const mode = computed(() =>
  props.mode === "AI" || props.mode === "FALLBACK" ? props.mode : ""
);
const label = computed(() =>
  mode.value === "FALLBACK" ? "降级回答（未使用语义检索）" : "AI 回答"
);
const hint = computed(() =>
  mode.value === "FALLBACK"
    ? "本轮模型或语义检索不可用，回答由规则/关键词路径生成；界面明确标注，不包装成完整语义检索。"
    : "本轮由模型结合课程资料与本人证据生成。"
);
</script>

<style scoped>
.ai-mode-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}
.ai-mode-badge.is-ai {
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.ai-mode-badge.is-fallback {
  background: var(--oslab-review-soft);
  color: var(--oslab-review-text);
}
</style>
