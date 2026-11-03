<template>
  <article
    class="ai-action-card"
    :class="[
      `is-${action.type.toLowerCase()}`,
      { 'is-done': action.status === 'DONE', 'is-compact': compact },
    ]"
  >
    <header class="ai-action-head">
      <span class="ai-action-type">{{ typeLabel }}</span>
      <span
        class="ai-action-status"
        :class="`is-${action.status.toLowerCase()}`"
        >{{ statusLabel }}</span
      >
      <span v-if="action.estimatedMinutes" class="ai-action-minutes"
        >约 {{ action.estimatedMinutes }} 分钟</span
      >
    </header>
    <p class="ai-action-reason">{{ action.reason }}</p>
    <p class="ai-action-target">
      <span class="ai-action-target-arrow" aria-hidden="true">→</span>
      <span class="ai-action-target-kind">{{ targetKindLabel }}</span>
      <span class="ai-action-target-code oslab-mono">{{
        action.targetRef?.code
      }}</span>
    </p>
    <p v-if="error" class="ai-action-error" role="alert">{{ error }}</p>
    <div class="ai-action-foot">
      <button
        type="button"
        class="ai-action-start"
        :disabled="busy || action.status === 'DONE'"
        @click="onStart"
      >
        {{ action.status === "DONE" ? "已完成" : busy ? "正在打开…" : "开始" }}
      </button>
      <button
        v-if="returnLocation"
        type="button"
        class="ai-action-return"
        @click="goReturn"
      >
        补学后返回
        <span class="ai-action-return-target">{{ returnShortLabel }}</span>
      </button>
      <span v-if="action.evidenceIds?.length" class="ai-action-evidence"
        >证据
        <span class="oslab-mono">{{
          action.evidenceIds.join("、")
        }}</span></span
      >
    </div>
  </article>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import type { AiNextAction } from "@/api/ai";
import { startAiPlanAction } from "@/composables/useAiActions";
import {
  AI_ACTION_STATUS_LABELS,
  AI_ACTION_TYPE_LABELS,
  AI_TARGET_KIND_LABELS,
  materialIdFromBlockId,
  nextActionTarget,
  paragraphFromLocator,
  targetLocation,
} from "@/utils/aiActions";
import { openAiMaterial } from "@/utils/aiSources";

const props = withDefaults(
  defineProps<{
    action: AiNextAction;
    courseCode: string;
    compact?: boolean;
  }>(),
  { compact: false }
);

const emit = defineEmits<{
  (event: "started", actionId: string): void;
}>();

const router = useRouter();
const busy = ref(false);
const error = ref("");

const typeLabel = computed(
  () => AI_ACTION_TYPE_LABELS[props.action.type] || props.action.type
);
const statusLabel = computed(
  () => AI_ACTION_STATUS_LABELS[props.action.status] || props.action.status
);
const targetKindLabel = computed(
  () =>
    AI_TARGET_KIND_LABELS[props.action.targetRef?.kind || ""] ||
    props.action.targetRef?.kind ||
    ""
);
const returnLocation = computed(() =>
  targetLocation(props.action.returnRef, props.courseCode)
);
const returnShortLabel = computed(() => {
  const target = props.action.returnRef;
  if (!target) return "";
  return `${AI_TARGET_KIND_LABELS[target.kind] || target.kind} ${target.code}`;
});

function goReturn() {
  if (returnLocation.value) {
    void router.push(returnLocation.value);
  }
}

async function onStart() {
  if (busy.value || props.action.status === "DONE") return;
  busy.value = true;
  error.value = "";
  try {
    const ref = await startAiPlanAction(props.action.actionId);
    if (!ref) {
      error.value = "任务暂时无法开始，请刷新后重试。";
      emit("started", props.action.actionId);
      return;
    }
    emit("started", props.action.actionId);
    const target = props.action.targetRef;
    if (target?.kind === "MATERIAL") {
      const materialId = materialIdFromBlockId(target.code);
      if (materialId) {
        openAiMaterial(
          materialId,
          paragraphFromLocator(target.anchor || target.code)
        );
      } else {
        error.value = "资料入口缺失，请从课程单元继续阅读。";
      }
      return;
    }
    const location = nextActionTarget(props.action, props.courseCode);
    if (!location) {
      error.value = "该任务暂无可用的站内入口。";
      return;
    }
    await router.push(location);
  } catch {
    error.value = "任务暂时无法开始，请稍后重试。";
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.ai-action-card {
  padding: 14px 0 12px;
  border-top: 1px solid var(--oslab-line-soft);
}
.ai-action-card.is-compact {
  padding: 10px 0 9px;
}
.ai-action-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
}
.ai-action-type {
  padding: 0 2px;
  color: var(--oslab-ink);
  font-size: 13px;
  font-weight: 700;
  box-shadow: inset 0 -7px 0 var(--oslab-highlight-soft);
}
.ai-action-card.is-predict .ai-action-type {
  box-shadow: inset 0 -7px 0 var(--oslab-highlight);
}
.ai-action-card.is-retest .ai-action-type {
  box-shadow: inset 0 -7px 0 var(--oslab-review-soft);
}
.ai-action-status {
  color: var(--oslab-muted);
  font-size: 11px;
}
.ai-action-status.is-started {
  color: var(--oslab-primary);
}
.ai-action-status.is-done {
  color: var(--oslab-mastered-text);
}
.ai-action-minutes {
  margin-left: auto;
  color: var(--oslab-muted);
  font-size: 11px;
}
.ai-action-reason {
  margin: 7px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.65;
  text-wrap: pretty;
}
.ai-action-card.is-compact .ai-action-reason {
  font-size: var(--oslab-text-small);
  line-height: 1.6;
}
.ai-action-target {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 5px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  overflow-wrap: anywhere;
}
.ai-action-target-arrow {
  color: var(--oslab-annotation);
  line-height: 1;
}
.ai-action-target-kind {
  color: var(--oslab-muted);
}
.ai-action-target-code {
  color: var(--oslab-muted-strong);
  overflow-wrap: anywhere;
}
.ai-action-error {
  margin: 6px 0 0;
  color: var(--oslab-weak-text);
  font-size: 11px;
}
.ai-action-foot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin-top: 9px;
}
.ai-action-start {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid currentColor;
  background: transparent;
  color: var(--oslab-primary);
  font: inherit;
  font-size: var(--oslab-text-small);
  font-weight: 600;
  cursor: pointer;
}
.ai-action-start:disabled {
  border-bottom-color: transparent;
  color: var(--oslab-muted);
  cursor: not-allowed;
}
.ai-action-return {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  min-height: 36px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 11px;
  text-decoration: underline;
  text-decoration-color: var(--oslab-line-strong);
  text-underline-offset: 3px;
  cursor: pointer;
}
.ai-action-return:hover {
  color: var(--oslab-primary);
  text-decoration-color: var(--oslab-primary);
}
.ai-action-return-target {
  color: var(--oslab-muted);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.ai-action-evidence {
  margin-left: auto;
  color: var(--oslab-muted);
  font-size: 10px;
  overflow-wrap: anywhere;
}
.ai-action-start:focus-visible,
.ai-action-return:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
  border-radius: var(--oslab-radius-sm);
}
</style>
