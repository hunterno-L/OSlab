<template>
  <section class="ai-next-actions" :class="{ compact }" aria-label="AI 下一步">
    <header class="ai-next-head">
      <div class="ai-next-titles">
        <h2 v-if="title" class="ai-next-title">{{ title }}</h2>
        <p v-if="dueCount > 0" class="ai-next-due">
          有 {{ dueCount }} 项到期复习已进入计划
        </p>
      </div>
      <button
        v-if="refreshable"
        type="button"
        class="ai-next-refresh"
        :disabled="loading"
        @click="emit('refresh')"
      >
        {{ loading ? "刷新中…" : "刷新" }}
      </button>
    </header>
    <p v-if="loading && !actions.length" class="ai-next-state" aria-busy="true">
      正在生成下一步…
    </p>
    <p v-else-if="error" class="ai-next-state is-error" role="alert">
      {{ error }}
    </p>
    <p v-else-if="!actions.length" class="ai-next-state">
      {{ emptyText }}
    </p>
    <div v-else class="ai-next-list">
      <AiNextActionCard
        v-for="action in visibleActions"
        :key="action.actionId"
        :action="action"
        :course-code="courseCode"
        :compact="compact"
        @started="emit('started', $event)"
      />
    </div>
    <button
      v-if="actions.length > limit && limit > 0"
      type="button"
      class="ai-next-more"
      @click="expanded = !expanded"
    >
      {{ expanded ? "收起" : `查看全部 ${actions.length} 项` }}
    </button>
  </section>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed, ref } from "vue";
import type { AiNextAction } from "@/api/ai";
import AiNextActionCard from "@/components/ai/AiNextActionCard.vue";

const props = withDefaults(
  defineProps<{
    actions: AiNextAction[];
    courseCode: string;
    loading?: boolean;
    error?: string;
    dueCount?: number;
    title?: string;
    emptyText?: string;
    compact?: boolean;
    refreshable?: boolean;
    /** 折叠时最多展示的条数；0 表示不折叠 */
    limit?: number;
  }>(),
  {
    loading: false,
    error: "",
    dueCount: 0,
    title: "接下来可以做",
    emptyText: "当前没有待完成的下一步任务。",
    compact: false,
    refreshable: true,
    limit: 0,
  }
);

const expanded = ref(false);
const visibleActions = computed(() =>
  props.limit > 0 && !expanded.value
    ? props.actions.slice(0, props.limit)
    : props.actions
);

const emit = defineEmits<{
  (event: "refresh"): void;
  (event: "started", actionId: string): void;
}>();
</script>

<style scoped>
.ai-next-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ai-next-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--oslab-space-2);
}
.ai-next-titles {
  min-width: 0;
}
.ai-next-title {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.35;
  text-wrap: balance;
}
.ai-next-due {
  margin: 2px 0 0;
  color: var(--oslab-review-text);
  font-size: var(--oslab-text-small);
  line-height: 1.5;
}
.ai-next-refresh {
  flex: none;
  min-height: 32px;
  padding: 0 2px;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
}
.ai-next-refresh:hover:not(:disabled) {
  border-bottom-color: currentColor;
  color: var(--oslab-primary);
}
.ai-next-refresh:disabled {
  color: var(--oslab-muted);
  cursor: not-allowed;
}
.ai-next-state {
  margin: 0;
  padding: 10px 0;
  border-top: 1px dashed var(--oslab-line);
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.6;
}
.ai-next-state.is-error {
  border-top-color: var(--oslab-weak);
  color: var(--oslab-weak-text);
}
.ai-next-list {
  display: flex;
  flex-direction: column;
}
.compact .ai-next-title {
  font-size: 15px;
}
.ai-next-more {
  align-self: stretch;
  min-height: 32px;
  padding: 0;
  border: 0;
  border-top: 1px solid var(--oslab-line-soft);
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  text-align: left;
  cursor: pointer;
}
.ai-next-more:hover {
  color: var(--oslab-primary);
}
.ai-next-refresh:focus-visible,
.ai-next-more:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
  border-radius: var(--oslab-radius-sm);
}
</style>
