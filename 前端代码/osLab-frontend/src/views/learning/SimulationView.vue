<template>
  <div class="simulation-view">
    <CourseReturnBar :can-complete-demo="canCompleteDemo" />

    <header
      v-if="returnTarget || nodeCode || contextLabel"
      class="simulation-head"
    >
      <div class="head-context">
        <router-link v-if="returnTarget" :to="returnTarget"
          >返回原学习位置</router-link
        >
        <router-link v-if="nodeCode" :to="nodeWorkspaceTarget"
          >返回知识点工作台</router-link
        >
        <span v-if="contextLabel" class="context-label">{{
          contextLabel
        }}</span>
      </div>
    </header>

    <section
      v-if="courseActivityContext"
      class="course-context"
      :class="{ mismatch: activityMismatch }"
      aria-label="课程活动上下文"
    >
      <span class="context-badge">课程活动</span>
      <span v-if="sourceActivityCode" class="context-code oslab-mono">{{
        sourceActivityCode
      }}</span>
      <span v-if="sourceUnitCode" class="context-unit"
        >单元 {{ sourceUnitCode }}</span
      >
      <span v-if="courseMode === 'REVIEW'" class="context-mode">复习模式</span>
      <template v-if="activityMismatch">
        <strong class="mismatch-title">当前演示不对应本单元</strong>
        <span class="mismatch-hint"
          >原活动算法：{{
            activityAlgorithmTitle
          }}。切换回原算法并重新运行后才能记录演示完成。</span
        >
      </template>
      <span v-else-if="activityAlgorithmKey" class="context-match"
        >当前演示对应活动算法：{{ activityAlgorithmTitle }}</span
      >
    </section>

    <div class="simulation-body">
      <aside class="algorithm-rail" aria-label="算法目录与类别">
        <div class="rail-head">
          <h2>算法目录</h2>
          <span class="rail-count">18 项</span>
        </div>
        <nav class="category-tabs" aria-label="算法类别">
          <button
            v-for="category in CATEGORY_ORDER"
            :key="category"
            type="button"
            class="category-tab"
            :class="{ active: definition.category === category }"
            :aria-pressed="definition.category === category"
            @click="selectCategory(category)"
          >
            <span>{{ CATEGORY_LABELS[category] }}</span>
            <span class="category-count"
              >{{ countByCategory(category) }} 项</span
            >
          </button>
        </nav>

        <div class="algorithm-list">
          <button
            v-for="algorithm in algorithmsByCategory(definition.category)"
            :key="algorithm.key"
            type="button"
            class="algorithm-item"
            :class="{ active: algorithm.key === definition.key }"
            :aria-current="
              algorithm.key === definition.key ? 'true' : undefined
            "
            @click="selectAlgorithm(algorithm.key)"
          >
            <span class="algorithm-short">{{ algorithm.shortTitle }}</span>
            <small>{{ algorithm.title }}</small>
          </button>
        </div>
      </aside>

      <div class="algorithm-workspace">
        <PredictionGate
          v-if="predictionCandidate"
          :definition="definition"
          :input="initialInput || null"
          :action-id="predictActionId || undefined"
          :course-code="sourceCourseCode || undefined"
          @availability="predictionAvailable = $event"
          @submitted="predictionSubmitted = true"
        />
        <div
          class="algorithm-stage"
          :class="{ 'is-prediction-locked': predictionLocked }"
        >
          <AlgorithmDemoPlayer
            :definition="definition"
            :initial-input="initialInput"
            :initial-input-error="initialInputError"
            @progress="allRevealed = $event"
            @completed="onDemoCompleted"
          />
          <div
            v-if="predictionLocked"
            class="prediction-lock"
            role="status"
            aria-live="polite"
          >
            <p>轨迹已封存</p>
            <span class="prediction-lock-hint"
              >先提交这一步的预测，完整轨迹才会展开。</span
            >
            <button type="button" @click="scrollToPrediction">去预测</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import CourseReturnBar from "@/components/learning/CourseReturnBar.vue";
import AlgorithmDemoPlayer from "@/components/learning/AlgorithmDemoPlayer.vue";
import PredictionGate from "@/components/ai/PredictionGate.vue";
import { recordSimulationRun } from "@/api/learning";
import {
  registerAiPageContext,
  unregisterAiPageContext,
} from "@/composables/useAiPageContext";
import {
  ALGORITHMS,
  ALGORITHM_BY_KEY,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  DEFAULT_ALGORITHM_KEY,
  algorithmKeyFromLegacy,
  algorithmKeyFromNodeCode,
  algorithmsByCategory,
  parseDemoQuery,
  queryValue,
  type AlgorithmCategory,
  type DemoInput,
} from "@/simulation";

const route = useRoute();
const router = useRouter();

const INPUT_QUERY_KEYS = [
  "processes",
  "timeSlice",
  "quantum",
  "queueSlices",
  "mlfqQueues",
  "frames",
  "accessSequence",
  "sequence",
  "memorySize",
  "initialPartitions",
  "operations",
  "head",
  "requests",
  "direction",
];

const algorithmKey = ref(DEFAULT_ALGORITHM_KEY);
const allRevealed = ref(false);
const definition = computed(
  () => ALGORITHM_BY_KEY.get(algorithmKey.value) || ALGORITHMS[0]
);
const nodeCode = computed(() => queryValue(route.query.nodeCode));
const nodeName = computed(() => queryValue(route.query.nodeName));
const sourceActivityCode = computed(() =>
  queryValue(route.query.sourceActivityCode)
);
const sourceUnitCode = computed(() => queryValue(route.query.sourceUnitCode));
const sourceCourseCode = computed(() => queryValue(route.query.courseCode));
const courseMode = computed(() =>
  queryValue(route.query.courseMode) === "REVIEW" ? "REVIEW" : "MAINLINE"
);
const courseActivityContext = computed(() =>
  Boolean(sourceActivityCode.value || sourceUnitCode.value)
);
const contextLabel = computed(() => {
  if (nodeCode.value) return `当前节点：${nodeName.value || nodeCode.value}`;
  if (nodeName.value) return `当前节点：${nodeName.value}`;
  return "";
});
const returnTarget = computed(() => {
  const value = queryValue(route.query.returnTo);
  return value.startsWith("/") && !value.startsWith("//") ? value : "";
});
const nodeWorkspaceTarget = computed(() =>
  nodeCode.value
    ? `/knowledge/concept/${encodeURIComponent(nodeCode.value)}`
    : `/knowledge/node/${encodeURIComponent(nodeName.value)}`
);

const countByCategory = (category: AlgorithmCategory) =>
  ALGORITHMS.filter((algorithm) => algorithm.category === category).length;

// ---------- 课程活动上下文与完成资格 ----------
const activityAlgorithmKey = ref("");

const captureActivityAlgorithm = () => {
  if (!sourceActivityCode.value && !sourceUnitCode.value) {
    activityAlgorithmKey.value = "";
    return;
  }
  const explicit = queryValue(route.query.algorithmKey).toLowerCase();
  const byNode = algorithmKeyFromNodeCode(nodeCode.value);
  const legacy = algorithmKeyFromLegacy(
    queryValue(route.query.algorithm),
    queryValue(route.query.mode) || queryValue(route.query.tab)
  );
  activityAlgorithmKey.value = ALGORITHM_BY_KEY.has(explicit)
    ? explicit
    : byNode || legacy || "";
};
captureActivityAlgorithm();

const activityAlgorithmTitle = computed(() => {
  const algorithm = ALGORITHM_BY_KEY.get(activityAlgorithmKey.value);
  return algorithm ? algorithm.title : activityAlgorithmKey.value;
});
const activityMismatch = computed(
  () =>
    Boolean(activityAlgorithmKey.value) &&
    definition.value.key !== activityAlgorithmKey.value
);
const canCompleteDemo = computed(
  () => allRevealed.value && !activityMismatch.value
);

/**
 * 普通演示完成一次运行：写独立活动记录（不写判题/掌握证据）。
 * 记录失败不影响演示本身，只给出轻提示。
 */
const onDemoCompleted = async (payload: {
  algorithmKey: string;
  title: string;
  input: Record<string, unknown> | null;
  resultSummary: Record<string, unknown>;
}) => {
  try {
    await recordSimulationRun({
      algorithmKey: payload.algorithmKey,
      title: payload.title,
      input: payload.input,
      resultSummary: payload.resultSummary,
      courseCode: sourceCourseCode.value || null,
      unitCode: activityMismatch.value ? null : sourceUnitCode.value || null,
      activityCode: activityMismatch.value
        ? null
        : sourceActivityCode.value || null,
    });
  } catch {
    Message.warning("演示运行记录未能保存，不影响本次演示结果");
  }
};

// ---------- 四类深链输入 ----------
const initialInputParse = computed(() =>
  parseDemoQuery(
    definition.value.category,
    route.query as Record<string, unknown>
  )
);
const initialInput = computed<DemoInput | undefined>(() =>
  initialInputParse.value.hasInput ? initialInputParse.value.input : undefined
);
const initialInputError = computed(() => initialInputParse.value.error || "");

// ---------- 先预测这一步：提交前遮住后续轨迹 ----------
const predictActionId = computed(() => queryValue(route.query.aiActionId));
const predictionSubmitted = ref(false);
const predictionAvailable = ref(false);
const predictionLocked = computed(
  () => predictionAvailable.value && !predictionSubmitted.value
);
const hasPageInput = computed(() => {
  const input = initialInput.value as
    | { frames?: number; sequence?: number[] }
    | undefined;
  return Boolean(
    input &&
      Number.isInteger(input.frames) &&
      Array.isArray(input.sequence) &&
      input.sequence.length >= 2
  );
});
const predictionCandidate = computed(
  () =>
    definition.value.category === "page" &&
    (hasPageInput.value || Boolean(predictActionId.value))
);

function scrollToPrediction() {
  document
    .getElementById("prediction-gate")
    ?.scrollIntoView({ behavior: "smooth", block: "center" });
}

const aiContextProvider = () => ({
  pageName: "算法演示",
  courseCode: sourceCourseCode.value || undefined,
  nodeCode: nodeCode.value || undefined,
  nodeCodes: nodeCode.value ? [nodeCode.value] : [],
  resourceRef: `simulation:${definition.value.key}`,
});

watch(
  () => [definition.value.key, initialInput.value],
  () => {
    predictionSubmitted.value = false;
    predictionAvailable.value = false;
  }
);

const applyRoute = () => {
  const explicit = queryValue(route.query.algorithmKey).toLowerCase();
  const byNode = algorithmKeyFromNodeCode(nodeCode.value);
  const legacy = algorithmKeyFromLegacy(
    queryValue(route.query.algorithm),
    queryValue(route.query.mode) || queryValue(route.query.tab)
  );
  algorithmKey.value = ALGORITHM_BY_KEY.has(explicit)
    ? explicit
    : byNode || legacy || DEFAULT_ALGORITHM_KEY;
};

const selectAlgorithm = (key: string) => {
  if (!ALGORITHM_BY_KEY.has(key)) return;
  if (key === algorithmKey.value) return;
  const next: Record<string, string> = {};
  Object.entries(route.query).forEach(([name, value]) => {
    const text = queryValue(value);
    if (text) next[name] = text;
  });
  next.algorithmKey = key;
  // 切换算法后旧输入不再适用；课程上下文/返回路径字段保留
  delete next.algorithm;
  delete next.mode;
  delete next.tab;
  INPUT_QUERY_KEYS.forEach((name) => delete next[name]);
  router.replace({ query: next });
};

const selectCategory = (category: AlgorithmCategory) => {
  const first = algorithmsByCategory(category)[0];
  if (first) selectAlgorithm(first.key);
};

watch(
  () => [
    queryValue(route.query.algorithmKey),
    queryValue(route.query.algorithm),
    queryValue(route.query.mode),
    queryValue(route.query.tab),
    nodeCode.value,
  ],
  applyRoute
);

watch(
  () => `${sourceActivityCode.value}|${sourceUnitCode.value}`,
  () => captureActivityAlgorithm()
);

onMounted(() => {
  applyRoute();
  registerAiPageContext(aiContextProvider);
});
onBeforeUnmount(() => {
  unregisterAiPageContext(aiContextProvider);
});
</script>

<style scoped>
.simulation-view {
  max-width: none;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-4);
  padding-bottom: 40px;
}
.simulation-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
}
.simulation-head .oslab-page-title {
  margin: 0;
}
.page-subtitle {
  margin: 6px 0 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.head-context {
  display: flex;
  align-items: center;
  gap: var(--oslab-space-3);
  flex-wrap: wrap;
  font-size: var(--oslab-text-small);
}
.head-context a {
  color: var(--oslab-primary);
  text-decoration: none;
}
.context-label {
  color: var(--oslab-muted-strong);
}
.course-context {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2) var(--oslab-space-3);
  padding: 10px 14px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
  font-size: var(--oslab-text-small);
  color: var(--oslab-ink-soft);
}
.course-context.mismatch {
  border-color: var(--oslab-review-text);
  background: var(--oslab-review-soft, #f6eddf);
}
.context-badge {
  padding: 2px 8px;
  border-radius: var(--oslab-radius-pill);
  background: var(--oslab-panel);
  color: var(--oslab-primary);
  font-size: 12px;
}
.context-code,
.context-unit {
  color: var(--oslab-muted-strong);
}
.context-mode {
  color: var(--oslab-muted-strong);
}
.context-match {
  color: var(--oslab-mastered, #4f8875);
}
.mismatch-title {
  color: var(--oslab-weak, #a9695d);
}
.mismatch-hint {
  color: var(--oslab-muted-strong);
}
.simulation-body {
  display: grid;
  grid-template-columns: 156px minmax(0, 1fr);
  gap: var(--oslab-space-4);
  align-items: start;
}
.algorithm-rail {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
  position: sticky;
  top: 12px;
  padding: 8px 6px;
  border-right: 1px solid var(--oslab-line);
  background: var(--oslab-panel);
}
.rail-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.rail-head h2 {
  margin: 0;
  font-size: 16px;
  color: var(--oslab-ink);
}
.rail-count {
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.category-tabs {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: var(--oslab-space-2);
  border-bottom: 1px solid var(--oslab-line-soft);
}
.category-tab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 9px;
  border: 1px solid transparent;
  border-radius: var(--oslab-radius-md);
  background: transparent;
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: var(--oslab-text-small);
  text-align: left;
  cursor: pointer;
}
.category-tab:hover {
  background: var(--oslab-panel-soft);
}
.category-tab.active {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-weight: 500;
}
.category-count {
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.algorithm-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.algorithm-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--oslab-radius-md);
  background: transparent;
  font: inherit;
  cursor: pointer;
  color: var(--oslab-ink-soft);
}
.algorithm-item:hover {
  background: var(--oslab-panel-soft);
}
.algorithm-item.active {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.algorithm-short {
  font-size: var(--oslab-text-small);
  font-weight: 600;
}
.algorithm-item small {
  color: var(--oslab-muted-strong);
  font-size: 11px;
  line-height: 1.5;
}
.algorithm-workspace {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
}
.algorithm-stage {
  position: relative;
  min-width: 0;
}
/* 预测前把轨迹压成一条“封存窗口”，避免整页模糊 */
.algorithm-stage.is-prediction-locked {
  max-height: 260px;
  overflow: hidden;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
}
.algorithm-stage.is-prediction-locked .legacy-demo {
  filter: blur(6px) saturate(0.6);
  opacity: 0.55;
  pointer-events: none;
  user-select: none;
}
.prediction-lock {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  background: var(--oslab-panel-soft);
  background: color-mix(in srgb, var(--oslab-panel-soft) 88%, transparent);
  text-align: center;
}
.prediction-lock::before {
  content: "";
  width: 28px;
  height: 3px;
  margin-bottom: 2px;
  background: var(--oslab-highlight);
}
.prediction-lock p {
  margin: 0;
  max-width: 34ch;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
  text-wrap: balance;
}
.prediction-lock-hint {
  max-width: 36ch;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.55;
}
.prediction-lock button {
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--oslab-primary);
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
  font: inherit;
  font-size: var(--oslab-text-small);
  font-weight: 600;
  cursor: pointer;
}
.prediction-lock button:hover {
  background: var(--oslab-primary-hover);
}
.prediction-lock button:focus-visible {
  outline: 2px solid var(--oslab-on-primary);
  outline-offset: 2px;
}
/* C+B 学习图册 */
.simulation-body {
  grid-template-columns: 156px minmax(0, 1fr);
  gap: 12px;
}
.algorithm-rail {
  padding: 8px 6px;
  background: var(--oslab-panel);
  border-right: 1px solid var(--oslab-line);
}
.algorithm-item {
  min-height: 34px;
  border-radius: 2px;
}
.algorithm-item.active,
.category-tab.active {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
}
.algorithm-workspace {
  min-width: 0;
}

@media (max-width: 1024px) {
  .simulation-body {
    grid-template-columns: 1fr;
  }
  .algorithm-rail {
    position: static;
  }
  .category-tabs {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .category-tab {
    flex: 1 1 150px;
  }
  .algorithm-list {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .algorithm-item {
    flex: 1 1 150px;
  }
}
</style>
