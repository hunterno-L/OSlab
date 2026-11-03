<template>
  <section
    v-if="visible"
    id="prediction-gate"
    class="prediction-gate"
    aria-label="先预测这一步"
  >
    <header class="prediction-head">
      <div>
        <h2>先预测这一步</h2>
        <p class="prediction-sub">
          {{
            actionRef
              ? "本次预测由服务端确定性计算器核对，并计入学习证据。"
              : "本次预测为练习，不写入掌握证据；从 AI 下一步进入可记录证据。"
          }}
        </p>
      </div>
      <AiHelpBadge v-if="actionRef" :level="actionRef.helpLevel" />
    </header>

    <p v-if="error" class="prediction-error" role="alert">{{ error }}</p>

    <template v-if="!submitted">
      <p class="prediction-prompt">{{ prompt }}</p>

      <div v-if="prefixRows.length" class="prediction-prefix">
        <table>
          <thead>
            <tr>
              <th>步骤</th>
              <th>访问页</th>
              <th>结果</th>
              <th>换出</th>
              <th>驻留页框</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in prefixRows" :key="row.step">
              <td class="oslab-mono">{{ row.step }}</td>
              <td class="oslab-mono">{{ row.page }}</td>
              <td>{{ row.fault ? "缺页" : "命中" }}</td>
              <td class="oslab-mono">{{ row.evicted ?? "—" }}</td>
              <td class="oslab-mono">{{ row.frames.join(" → ") || "空" }}</td>
            </tr>
          </tbody>
        </table>
        <p class="prediction-hint">
          以上是提交前允许看到的前缀；待预测步之后的轨迹已遮挡。
        </p>
      </div>

      <div v-if="aggregate" class="prediction-answer">
        <label for="prediction-count">整条序列的总缺页次数</label>
        <a-input-number
          id="prediction-count"
          v-model="countValue"
          :min="0"
          :max="9999"
          :precision="0"
        />
        <a-button
          type="primary"
          :disabled="countValue === null"
          @click="submitAggregate"
        >
          提交预测并揭示轨迹
        </a-button>
      </div>
      <div v-else class="prediction-answer">
        <span class="prediction-answer-label">选择应换出的页</span>
        <div class="prediction-options">
          <button
            v-for="page in candidateVictims"
            :key="page"
            type="button"
            class="prediction-option"
            :class="{ selected: choice === page }"
            @click="choice = page"
          >
            换出页 <b class="oslab-mono">{{ page }}</b>
          </button>
        </div>
        <a-button
          type="primary"
          :disabled="choice === null || submitting"
          :loading="submitting"
          @click="submitVictim"
        >
          提交预测并揭示轨迹
        </a-button>
      </div>
    </template>

    <template v-else>
      <div
        class="prediction-result"
        :class="correct ? 'is-correct' : 'is-wrong'"
      >
        <b>{{ correct ? "预测正确" : "预测与真实轨迹不一致" }}</b>
        <span v-if="serverResult?.judgeMessage">{{
          serverResult.judgeMessage
        }}</span>
        <span v-else>
          你预测换出页 {{ choice ?? countValue }}，实际为
          {{ actualValue ?? "—" }}。
        </span>
      </div>

      <div v-if="evidence" class="prediction-evidence">
        <AiHelpBadge :level="evidence.helpLevel" />
        <span class="prediction-hint">{{
          evidence.eligibleForObservation
            ? evidence.eligibilityReason || "本次预测计入独立观测"
            : evidence.eligibilityReason
        }}</span>
      </div>

      <p v-if="analysis" class="prediction-analysis">{{ analysis }}</p>
      <ul v-if="serverResult?.diffSteps?.length" class="prediction-diff">
        <li v-for="line in serverResult.diffSteps" :key="line">{{ line }}</li>
      </ul>

      <div v-if="revealedRows.length" class="prediction-revealed">
        <h3>提交后揭示的真实轨迹</h3>
        <table>
          <thead>
            <tr>
              <th>步骤</th>
              <th>访问页</th>
              <th>结果</th>
              <th>换出</th>
              <th>驻留页框</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in revealedRows"
              :key="row.step"
              :class="{ 'is-target': row.step === targetStepNumber }"
            >
              <td class="oslab-mono">{{ row.step }}</td>
              <td class="oslab-mono">{{ row.page }}</td>
              <td>{{ row.fault ? "缺页" : "命中" }}</td>
              <td class="oslab-mono">{{ row.evicted ?? "—" }}</td>
              <td class="oslab-mono">{{ row.frames.join(" → ") || "空" }}</td>
            </tr>
          </tbody>
        </table>
        <p class="prediction-hint">
          高亮行为本次预测涉及的步骤；预测值
          {{ choice ?? countValue }}，真实值 {{ actualValue ?? "—" }}。
        </p>
      </div>

      <AiNextActions
        v-if="serverResult?.evidence"
        :actions="nextActions"
        :course-code="courseCode"
        title="下一步"
        empty-text=""
        compact
        :refreshable="false"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed, onMounted, ref, watch } from "vue";
import type {
  AiActivityRefVO,
  AiActionSubmitResultVO,
  AiEvidenceSnapshot,
} from "@/api/ai";
import AiHelpBadge from "@/components/ai/AiHelpBadge.vue";
import AiNextActions from "@/components/ai/AiNextActions.vue";
import {
  getStartedAiAction,
  startAiPlanAction,
  submitAiPlanAction,
} from "@/composables/useAiActions";
import { useAiConversation } from "@/composables/useAiConversation";
import { runPage, type PageAlgorithmKey } from "@/simulation/page";
import type {
  AlgorithmDefinition,
  DemoInput,
  DemoStep,
} from "@/simulation/types";

const props = defineProps<{
  definition: AlgorithmDefinition;
  input?: DemoInput | null;
  actionId?: string;
  courseCode?: string;
}>();

const emit = defineEmits<{
  (
    event: "submitted",
    payload: { correct: boolean; actual: number | null }
  ): void;
  (event: "availability", available: boolean): void;
}>();

interface PredictRow {
  step: number;
  page: number;
  fault: boolean;
  evicted: number | null;
  frames: number[];
}

const actionRef = ref<AiActivityRefVO | null>(null);
const error = ref("");
const choice = ref<number | null>(null);
const countValue = ref<number | null>(0);
const submitted = ref(false);
const submitting = ref(false);
const correct = ref(false);
const analysis = ref("");
const actualValue = ref<number | null>(null);
const serverResult = ref<AiActionSubmitResultVO | null>(null);
const evidence = ref<AiEvidenceSnapshot | null>(null);

const { nextActions } = useAiConversation();

const isPage = computed(() => props.definition.category === "page");

const localInput = computed(() => {
  const input = props.input as { frames?: number; sequence?: number[] } | null;
  if (
    !input ||
    !Number.isInteger(input.frames) ||
    !Array.isArray(input.sequence) ||
    input.sequence.length < 2
  ) {
    return null;
  }
  return {
    frames: input.frames as number,
    sequence: input.sequence as number[],
  };
});

const visible = computed(() => {
  if (!isPage.value) return false;
  if (actionRef.value) {
    return actionRef.value.predictStepIndex < 0 || predictIndex.value >= 0;
  }
  return Boolean(localInput.value) && predictIndex.value >= 0;
});

const aggregate = computed(() =>
  Boolean(actionRef.value && actionRef.value.predictStepIndex < 0)
);

/** 本地/服务端前缀都能复算出同一条确定性轨迹；AI 模式只用允许看到的前缀 */
const traceSteps = computed<DemoStep[]>(() => {
  if (actionRef.value) {
    const frames = actionRef.value.frames;
    const sequence = actionRef.value.visibleSequence;
    if (!frames || !sequence?.length) return [];
    return runPage(props.definition.key as PageAlgorithmKey, {
      frames,
      sequence,
    }).steps;
  }
  if (!localInput.value) return [];
  return runPage(props.definition.key as PageAlgorithmKey, localInput.value)
    .steps;
});

const predictIndex = computed(() => {
  if (actionRef.value) return actionRef.value.predictStepIndex;
  return traceSteps.value.findIndex((step) => stepState(step).evicted !== null);
});

const targetStep = computed<DemoStep | null>(() => {
  if (aggregate.value) return null;
  const index = predictIndex.value;
  return index >= 0 ? traceSteps.value[index] || null : null;
});

const targetStepNumber = computed(() => targetStep.value?.index ?? 0);

const prefixRows = computed<PredictRow[]>(() => {
  if (aggregate.value) return [];
  const index = predictIndex.value;
  const source =
    actionRef.value && actionRef.value.visibleSteps?.length
      ? actionRef.value.visibleSteps.map((step) => ({
          index: step.step,
          state: {
            page: step.page,
            fault: step.fault,
            evicted: step.evicted,
            frames: step.frames,
          },
        }))
      : traceSteps.value.slice(0, Math.max(0, index));
  return source.map((step) => ({
    step: step.index,
    page: stepState(step).page,
    fault: stepState(step).fault,
    evicted: stepState(step).evicted,
    frames: stepState(step).frames,
  }));
});

const candidateVictims = computed<number[]>(() => {
  if (prefixRows.value.length) {
    return prefixRows.value[prefixRows.value.length - 1].frames;
  }
  if (localInput.value) {
    // 待预测步之前没有已展示步骤时，按算法重放前缀得到驻留页框
    const index = Math.max(0, predictIndex.value - 1);
    const step = traceSteps.value[index];
    return step ? stepState(step).frames : [];
  }
  return [];
});

const prompt = computed(() => {
  if (actionRef.value?.prompt) return actionRef.value.prompt;
  if (aggregate.value) {
    return `按 ${props.definition.title}，整条访问序列共发生多少次缺页？`;
  }
  const step = targetStep.value;
  if (!step) return "当前输入没有可预测的换出步骤。";
  return `按 ${props.definition.title}，第 ${step.index} 次访问页 ${
    stepState(step).page
  } 时应换出哪一页？`;
});

const revealedRows = computed<PredictRow[]>(() => {
  if (aggregate.value) {
    return traceSteps.value.map((step) => ({
      step: step.index,
      page: stepState(step).page,
      fault: stepState(step).fault,
      evicted: stepState(step).evicted,
      frames: stepState(step).frames,
    }));
  }
  const index = predictIndex.value;
  // 本地模式有完整轨迹；AI 模式只揭示到待预测步为止
  const steps = actionRef.value
    ? traceSteps.value.slice(0, Math.max(0, index + 1))
    : traceSteps.value;
  return steps.map((step) => ({
    step: step.index,
    page: stepState(step).page,
    fault: stepState(step).fault,
    evicted: stepState(step).evicted,
    frames: stepState(step).frames,
  }));
});

function stepState(step: { state: Record<string, unknown>; index?: number }) {
  const state = step.state as {
    page?: number;
    fault?: boolean;
    evicted?: number | null;
    frames?: number[];
  };
  return {
    page: state.page ?? 0,
    fault: Boolean(state.fault),
    evicted: state.evicted ?? null,
    frames: Array.isArray(state.frames) ? state.frames : [],
  };
}

function faultCount(): number {
  return traceSteps.value.filter((step) => stepState(step).fault).length;
}

async function loadAction() {
  const actionId = props.actionId;
  if (!actionId) {
    actionRef.value = null;
    emit("availability", Boolean(localInput.value));
    return;
  }
  const cached = getStartedAiAction(actionId);
  if (cached) {
    actionRef.value = cached;
    emit("availability", true);
    return;
  }
  error.value = "";
  const ref = await startAiPlanAction(actionId);
  if (ref) {
    actionRef.value = ref;
    emit("availability", true);
  } else {
    error.value = "预测任务无法开始；演示轨迹已解锁，请稍后重试。";
    emit("availability", false);
  }
}

async function submitVictim() {
  if (choice.value === null || submitting.value) return;
  const actual = targetStep.value ? stepState(targetStep.value).evicted : null;
  await finish(choice.value, actual);
}

async function submitAggregate() {
  if (countValue.value === null || submitting.value) return;
  await finish(countValue.value, faultCount());
}

async function finish(predicted: number, actual: number | null) {
  submitting.value = true;
  error.value = "";
  try {
    if (props.actionId) {
      const ref = actionRef.value || (await startAiPlanAction(props.actionId));
      if (!ref) {
        error.value = "预测任务尚未就绪，请刷新后重试。";
        return;
      }
      const result = await submitAiPlanAction(
        props.actionId,
        {
          submissionKey: ref.submissionKey,
          predictedProcess: String(predicted),
        },
        props.courseCode
      );
      if (!result) {
        error.value = "预测提交失败，请重试（同一提交键不会重复计数）。";
        return;
      }
      serverResult.value = result;
      evidence.value = result.evidence || null;
      correct.value = Boolean(result.correct);
      analysis.value = result.analysis || "";
      actualValue.value = actual;
    } else {
      correct.value = predicted === actual;
      actualValue.value = actual;
      analysis.value = correct.value
        ? `按 ${props.definition.title} 的规则，第 ${targetStepNumber.value} 步确实换出页 ${actual}。`
        : `按 ${props.definition.title} 的规则，第 ${targetStepNumber.value} 步应换出页 ${actual}，不是 ${predicted}。回看命中是否应更新顺序，以及缺页与换出的区别。`;
    }
    submitted.value = true;
    emit("submitted", { correct: correct.value, actual });
  } finally {
    submitting.value = false;
  }
}

onMounted(loadAction);
watch(
  () => props.actionId,
  () => {
    submitted.value = false;
    serverResult.value = null;
    evidence.value = null;
    choice.value = null;
    countValue.value = 0;
    void loadAction();
  }
);
</script>

<style scoped>
.prediction-gate {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--oslab-line);
  border-top: 3px solid var(--oslab-highlight);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel);
}
.prediction-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--oslab-space-2);
}
.prediction-head h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
  text-wrap: balance;
}
.prediction-sub {
  margin: 4px 0 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.55;
  text-wrap: pretty;
}
.prediction-error {
  margin: 0;
  color: var(--oslab-weak-text);
  font-size: var(--oslab-text-small);
}
.prediction-prompt {
  margin: 0;
  color: var(--oslab-ink);
  font-size: var(--oslab-font-body);
  font-weight: 500;
  line-height: 1.6;
}
.prediction-prefix,
.prediction-revealed {
  overflow-x: auto;
}
.prediction-prefix table,
.prediction-revealed table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--oslab-text-small);
}
.prediction-prefix th,
.prediction-revealed th {
  padding: 4px 8px;
  border-bottom: 1px solid var(--oslab-line);
  color: var(--oslab-muted-strong);
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
}
.prediction-prefix td,
.prediction-revealed td {
  padding: 4px 8px;
  border-bottom: 1px solid var(--oslab-line-soft);
  color: var(--oslab-ink-soft);
  white-space: nowrap;
}
.prediction-revealed tr.is-target td {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  font-weight: 600;
}
.prediction-hint {
  margin: 4px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  line-height: 1.5;
}
.prediction-answer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.prediction-answer-label {
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.prediction-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.prediction-option {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: var(--oslab-radius-sm);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    background var(--oslab-duration-fast) var(--oslab-ease);
}
.prediction-option:hover {
  border-color: var(--oslab-ink-soft);
}
.prediction-option.selected {
  border-color: var(--oslab-ink-soft);
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  box-shadow: inset 0 -4px 0 var(--oslab-highlight);
}
.prediction-option:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}
.prediction-result {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--oslab-radius-md);
  font-size: var(--oslab-text-small);
}
.prediction-result.is-correct {
  background: var(--oslab-mastered-soft);
  color: var(--oslab-mastered-text);
}
.prediction-result.is-wrong {
  background: var(--oslab-weak-soft);
  color: var(--oslab-weak-text);
}
.prediction-evidence {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.prediction-analysis {
  margin: 0;
  padding: 9px 0 0;
  border-top: 1px solid var(--oslab-line-soft);
  color: var(--oslab-ink-soft);
  font-size: var(--oslab-text-small);
  line-height: 1.65;
  white-space: pre-wrap;
  text-wrap: pretty;
}
.prediction-gate :deep(.arco-btn-primary) {
  border-radius: var(--oslab-radius-control);
}
.prediction-diff {
  margin: 0;
  padding-left: 18px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  line-height: 1.6;
}
.prediction-revealed h3 {
  margin: 0 0 6px;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  font-weight: 600;
}
@media (prefers-reduced-motion: reduce) {
  .prediction-option {
    transition: none;
  }
}
</style>
