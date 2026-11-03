<template>
  <section v-if="diagnosis" class="ai-diagnosis" aria-label="诊断分析">
    <header class="ai-diagnosis-head">
      <span class="ai-diagnosis-title">诊断分析</span>
      <span
        class="ai-diagnosis-status"
        :class="`is-${diagnosis.status.toLowerCase()}`"
        >{{ statusLabel }}</span
      >
    </header>
    <p v-if="diagnosis.explanation" class="ai-diagnosis-text">
      {{ diagnosis.explanation }}
    </p>
    <ul
      v-if="diagnosis.hypotheses && diagnosis.hypotheses.length"
      class="ai-diagnosis-hypotheses"
    >
      <li
        v-for="hypothesis in diagnosis.hypotheses"
        :key="hypothesis.code"
        class="ai-diagnosis-hypothesis"
      >
        <span class="ai-diagnosis-hypothesis-label">{{
          hypothesis.label
        }}</span>
        <span class="ai-diagnosis-hypothesis-code oslab-mono">{{
          hypothesis.code
        }}</span>
      </li>
    </ul>
    <p v-else class="ai-diagnosis-text ai-diagnosis-muted">
      当前证据不足以提出具体原因，先保留“待确认”。
    </p>
    <dl class="ai-diagnosis-evidence">
      <div v-if="diagnosis.supportingEventIds?.length">
        <dt>支持证据</dt>
        <dd class="oslab-mono">
          {{ diagnosis.supportingEventIds.join("、") }}
        </dd>
      </div>
      <div v-if="diagnosis.conflictingEventIds?.length">
        <dt>冲突证据</dt>
        <dd class="oslab-mono">
          {{ diagnosis.conflictingEventIds.join("、") }}
        </dd>
      </div>
      <div v-if="diagnosis.verificationActionIds?.length">
        <dt>建议核验</dt>
        <dd class="oslab-mono">
          {{ diagnosis.verificationActionIds.join("、") }}
        </dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed } from "vue";
import type { AiDiagnosis } from "@/api/ai";

const props = defineProps<{
  diagnosis?: AiDiagnosis | null;
}>();

const STATUS_LABELS: Record<string, string> = {
  TENTATIVE: "暂定原因",
  SUPPORTED: "有证据支持",
  INSUFFICIENT: "证据不足",
};
const statusLabel = computed(
  () => STATUS_LABELS[props.diagnosis?.status || ""] || "待确认"
);
</script>

<style scoped>
.ai-diagnosis {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--oslab-line-soft);
}
.ai-diagnosis-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.ai-diagnosis-title {
  color: var(--oslab-ink);
  font-size: 11px;
  font-weight: 700;
}
.ai-diagnosis-status {
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-diagnosis-status.is-supported {
  color: var(--oslab-mastered-text);
}
.ai-diagnosis-status.is-insufficient {
  color: var(--oslab-review-text);
}
.ai-diagnosis-text {
  margin: 5px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 11px;
  line-height: 1.6;
  text-wrap: pretty;
}
.ai-diagnosis-muted {
  color: var(--oslab-muted-strong);
}
.ai-diagnosis-hypotheses {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ai-diagnosis-hypothesis {
  display: flex;
  align-items: baseline;
  gap: 7px;
  font-size: 11px;
  color: var(--oslab-ink-soft);
}
.ai-diagnosis-hypothesis::before {
  content: "";
  flex: none;
  width: 5px;
  height: 5px;
  background: var(--oslab-annotation);
  clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
}
.ai-diagnosis-hypothesis-code {
  color: var(--oslab-muted);
  font-size: 10px;
}
.ai-diagnosis-evidence {
  margin: 6px 0 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 10px;
}
.ai-diagnosis-evidence div {
  display: flex;
  gap: 6px;
}
.ai-diagnosis-evidence dt {
  flex: none;
  color: var(--oslab-muted-strong);
}
.ai-diagnosis-evidence dd {
  margin: 0;
  color: var(--oslab-ink-soft);
  overflow-wrap: anywhere;
}
</style>
