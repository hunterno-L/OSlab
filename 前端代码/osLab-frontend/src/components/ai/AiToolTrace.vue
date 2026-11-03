<template>
  <details v-if="entries.length" class="ai-trace">
    <summary class="ai-trace-summary">
      工具轨迹
      <span class="ai-trace-count oslab-mono">{{ entries.length }} 项</span>
    </summary>
    <ul class="ai-trace-list">
      <li v-for="(entry, index) in entries" :key="index" class="ai-trace-item">
        <span class="ai-trace-tool oslab-mono">{{
          toolLabel(entry.tool)
        }}</span>
        <span
          class="ai-trace-status"
          :class="`is-${entry.status.toLowerCase()}`"
          >{{ statusLabel(entry.status) }}</span
        >
        <span v-if="entry.ms" class="ai-trace-ms oslab-mono"
          >{{ entry.ms }} ms</span
        >
        <span v-if="entry.summary" class="ai-trace-text">{{
          entry.summary
        }}</span>
      </li>
    </ul>
  </details>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import type { AiToolTraceEntry } from "@/api/ai";

withDefaults(
  defineProps<{
    entries?: AiToolTraceEntry[];
  }>(),
  { entries: () => [] }
);

const TOOL_LABELS: Record<string, string> = {
  retrieve_sources: "检索资料",
  read_learning_evidence: "读取本人证据",
  select_verified_activity: "选择核验任务",
  check_page_trace: "页面置换核验",
  propose_next_actions: "生成下一步",
};

const STATUS_LABELS: Record<string, string> = {
  OK: "完成",
  EMPTY: "无结果",
  FAILED: "失败",
  SKIPPED: "跳过",
  BUDGET_EXCEEDED: "超预算",
};

const toolLabel = (tool: string) => TOOL_LABELS[tool] || tool;
const statusLabel = (status: string) => STATUS_LABELS[status] || status;
</script>

<style scoped>
.ai-trace {
  margin-top: 10px;
  border-top: 1px solid var(--oslab-line-soft);
}
.ai-trace-summary {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 0;
  cursor: pointer;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  list-style: none;
}
.ai-trace-summary::-webkit-details-marker {
  display: none;
}
.ai-trace-summary::before {
  content: "";
  flex: none;
  width: 0;
  height: 0;
  border-left: 4px solid currentColor;
  border-top: 3px solid transparent;
  border-bottom: 3px solid transparent;
  transition: transform var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-trace[open] .ai-trace-summary::before {
  transform: rotate(90deg);
}
.ai-trace-summary:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
  border-radius: var(--oslab-radius-sm);
}
.ai-trace-count {
  margin-left: auto;
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-trace-list {
  list-style: none;
  margin: 0;
  padding: 0 0 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.ai-trace-item {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  font-size: 11px;
  color: var(--oslab-ink-soft);
}
.ai-trace-tool {
  font-weight: 600;
}
.ai-trace-status {
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-trace-status.is-ok {
  color: var(--oslab-mastered-text);
}
.ai-trace-status.is-failed,
.ai-trace-status.is-budget_exceeded {
  color: var(--oslab-weak-text);
}
.ai-trace-ms {
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-trace-text {
  flex-basis: 100%;
  color: var(--oslab-muted-strong);
  line-height: 1.5;
}
@media (prefers-reduced-motion: reduce) {
  .ai-trace-summary::before {
    transition: none;
  }
}
</style>
