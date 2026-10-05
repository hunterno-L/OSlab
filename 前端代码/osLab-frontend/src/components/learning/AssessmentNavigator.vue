<template>
  <div class="navigator" role="group" aria-label="本组题目状态">
    <button
      v-for="(q, index) in questions"
      :key="q.id"
      type="button"
      class="nav-item"
      :class="[stateClass(q), { current: index === currentIndex }]"
      :aria-pressed="index === currentIndex"
      :aria-label="`第 ${index + 1} 题：${stateLabel(q)}`"
      :disabled="q.offline"
      :title="q.offline ? '该题目已下线' : stateLabel(q)"
      @click="$emit('select', index)"
    >
      {{ index + 1 }}
    </button>
  </div>
</template>

<script setup lang="ts">
import type { LearningQuestionVO } from "@/api/learning";

// eslint-disable-next-line no-undef
defineProps<{
  questions: LearningQuestionVO[];
  currentIndex: number;
}>();

// eslint-disable-next-line no-undef
defineEmits(["select"]);

const stateClass = (q: LearningQuestionVO) => {
  if (q.offline) return "state-offline";
  if (!q.hasAttempted) return "state-unanswered";
  return q.latestResult === "CORRECT" ? "state-correct" : "state-wrong";
};

const stateLabel = (q: LearningQuestionVO) => {
  if (q.offline) return "已下线";
  if (!q.hasAttempted) return "未作答";
  return q.latestResult === "CORRECT" ? "答对" : "待复习";
};
</script>

<style scoped>
.navigator {
  display: flex;
  gap: var(--oslab-space-2);
  flex-wrap: wrap;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  padding: var(--oslab-space-3);
}
.nav-item {
  width: 32px;
  height: 32px;
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
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}
.nav-item:hover {
  border-color: var(--oslab-muted-strong);
}
.nav-item.current {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-weight: 700;
}
.state-correct {
  background: var(--oslab-mastered-soft);
  border-color: var(--oslab-mastered);
  color: var(--oslab-mastered);
}
.state-wrong {
  background: var(--oslab-review-soft);
  border-color: var(--oslab-review);
  color: var(--oslab-review);
}
.state-offline {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
