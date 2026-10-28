<template>
  <section class="legacy-demo" aria-label="原版算法演示">
    <div
      ref="gridRef"
      class="legacy-grid"
      :class="{ resizing }"
      :style="{ '--source-width': requestedSourceWidth }"
    >
      <div class="legacy-stage" aria-label="原算法参数、动画舞台与结果表">
        <a-alert v-if="initialInputError" type="error" class="entry-error">
          {{ initialInputError }}
        </a-alert>
        <a-alert v-if="runtimeError" type="error" class="entry-error">
          {{ runtimeError }}
        </a-alert>
        <div class="stage-viewport">
          <iframe
            :key="resource.route"
            ref="frameRef"
            class="legacy-frame"
            :style="{ height: `${frameHeight}px` }"
            :src="frameUrl"
            :title="`${definition.title}原版演示`"
            @load="onFrameLoad"
          />
        </div>
      </div>

      <div
        class="source-resize-handle"
        role="separator"
        tabindex="0"
        aria-label="调整算法源码宽度"
        aria-orientation="vertical"
        :aria-valuemin="MIN_SOURCE_WIDTH"
        :aria-valuemax="maxSourceWidth"
        :aria-valuenow="Math.round(actualSourceWidth)"
        @pointerdown="startResize"
        @pointermove="resizeFromPointer"
        @pointerup="stopResize"
        @pointercancel="stopResize"
        @lostpointercapture="stopResize"
        @keydown="resizeFromKeyboard"
      />

      <section
        ref="sourcePanelRef"
        class="source-panel"
        aria-label="C++ 算法源代码"
      >
        <header class="source-head">
          <div>
            <h3>算法源代码</h3>
            <span class="source-path oslab-mono">{{
              `${definition.key}.cpp`
            }}</span>
          </div>
          <span class="source-state" :class="{ running }" aria-live="polite">
            {{ sourceState }}
          </span>
        </header>
        <a-spin v-if="sourceLoading" class="source-loading" />
        <div v-else-if="sourceError" class="source-error">
          {{ sourceError }}
        </div>
        <pre v-else class="source-code" aria-label="C++ 算法源代码"><code><span
          v-for="row in sourceRows"
          :key="row.number"
          :id="`legacy-line-${row.number}`"
          class="source-line"
          :class="{ active: activeLines.includes(row.number) }"
        ><span class="line-number oslab-mono">{{ row.number }}</span><span><span v-for="(token, index) in row.tokens" :key="index" :class="`cpp-${token.kind}`">{{ token.text }}</span></span></span></code></pre>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { AlgorithmDefinition, DemoInput } from "@/simulation";
import {
  LEGACY_DEMO_RESOURCES,
  serializeLegacyInput,
} from "@/simulation/legacy";
import { parseCppSource, type CppSourceRow } from "@/simulation/cppSource";

// eslint-disable-next-line no-undef
const props = defineProps<{
  definition: AlgorithmDefinition;
  initialInput?: DemoInput;
  initialInputError?: string;
}>();
// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (event: "progress", allRevealed: boolean): void;
  (
    event: "completed",
    payload: {
      algorithmKey: string;
      title: string;
      input: Record<string, unknown> | null;
      resultSummary: Record<string, unknown>;
    }
  ): void;
}>();

const TRACE_CHANNEL = "oslab-legacy-simulation";
const MIN_SOURCE_WIDTH = 360;
const MIN_STAGE_WIDTH = 560;
const HANDLE_WIDTH = 12;
const frameRef = ref<HTMLIFrameElement | null>(null);
const gridRef = ref<HTMLElement | null>(null);
const sourcePanelRef = ref<HTMLElement | null>(null);
const sourceRows = ref<CppSourceRow[]>([]);
const phaseLines = ref<Record<string, number[]>>({});
const sourceLoading = ref(true);
const sourceError = ref("");
const runtimeError = ref("");
const frameLoaded = ref(false);
const legacyReady = ref(false);
const running = ref(false);
const paused = ref(false);
const frameHeight = ref(920);
const sourceWidth = ref<number | null>(null);
const actualSourceWidth = ref(0);
const gridWidth = ref(0);
const resizing = ref(false);
const requestedSourceWidth = computed(() =>
  sourceWidth.value === null ? "36%" : `${sourceWidth.value}px`
);
const maxSourceWidth = computed(() =>
  Math.max(MIN_SOURCE_WIDTH, gridWidth.value - MIN_STAGE_WIDTH - HANDLE_WIDTH)
);
const complete = ref(false);
const activeLines = ref<number[]>([]);
const pendingPhase = ref("");
let pendingFlush = 0;
let sourceRequest = 0;
let layoutObserver: ResizeObserver | null = null;
let scrollFrame = 0;
let currentRunId = -1;
let lastSequence = 0;
let resizePointer: { id: number; startX: number; startWidth: number } | null =
  null;

const setSourceWidth = (width: number) => {
  sourceWidth.value = Math.min(
    maxSourceWidth.value,
    Math.max(MIN_SOURCE_WIDTH, width)
  );
};

const startResize = (event: PointerEvent) => {
  if (event.button !== 0 || !sourcePanelRef.value) return;
  event.preventDefault();
  resizePointer = {
    id: event.pointerId,
    startX: event.clientX,
    startWidth: sourcePanelRef.value.getBoundingClientRect().width,
  };
  resizing.value = true;
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
};

const resizeFromPointer = (event: PointerEvent) => {
  if (!resizePointer || resizePointer.id !== event.pointerId) return;
  setSourceWidth(
    resizePointer.startWidth + resizePointer.startX - event.clientX
  );
};

const stopResize = (event: PointerEvent) => {
  if (!resizePointer || resizePointer.id !== event.pointerId) return;
  resizePointer = null;
  resizing.value = false;
};

const resizeFromKeyboard = (event: KeyboardEvent) => {
  const width = sourcePanelRef.value?.getBoundingClientRect().width;
  if (width === undefined) return;
  let next: number;
  if (event.key === "ArrowLeft") next = width + 24;
  else if (event.key === "ArrowRight") next = width - 24;
  else if (event.key === "Home") next = maxSourceWidth.value;
  else if (event.key === "End") next = MIN_SOURCE_WIDTH;
  else return;
  event.preventDefault();
  setSourceWidth(next);
};

const resource = computed(() => {
  const found = LEGACY_DEMO_RESOURCES[props.definition.key];
  if (!found)
    throw new Error(`No original demonstration for ${props.definition.key}`);
  return found;
});
const frameUrl = computed(() => `/legacy-simulation/#${resource.value.route}`);
const sourceState = computed(() => {
  if (runtimeError.value) return "执行错误";
  if (running.value && paused.value) return "已暂停";
  if (running.value) return "运行中";
  if (complete.value) return "已完成";
  if (!legacyReady.value) return "加载中";
  return "待运行";
});

const showPhase = (phase: string) => {
  const lines = phaseLines.value[phase];
  if (!lines?.length) return;
  activeLines.value = lines;
  if (scrollFrame) cancelAnimationFrame(scrollFrame);
  scrollFrame = requestAnimationFrame(() => {
    scrollFrame = 0;
    const row = document.getElementById(`legacy-line-${lines[0]}`);
    const panel = row?.closest(".source-code");
    if (!row || !panel) return;
    const rect = row.getBoundingClientRect();
    const bounds = panel.getBoundingClientRect();
    if (rect.top < bounds.top + 24 || rect.bottom > bounds.bottom - 24)
      panel.scrollTop += rect.top - bounds.top - bounds.height / 3;
  });
};

const flushLine = () => {
  pendingFlush = 0;
  if (pendingPhase.value) showPhase(pendingPhase.value);
};

const clearTrace = () => {
  if (pendingFlush) window.clearTimeout(pendingFlush);
  pendingFlush = 0;
  pendingPhase.value = "";
  activeLines.value = [];
  if (scrollFrame) cancelAnimationFrame(scrollFrame);
  scrollFrame = 0;
};

const send = (type: string) => {
  const target = frameRef.value?.contentWindow;
  if (!target || !frameLoaded.value) return;
  target.postMessage(
    {
      channel: TRACE_CHANNEL,
      type,
      key: props.definition.key,
      ...(props.initialInput
        ? {
            input: JSON.parse(
              serializeLegacyInput(props.definition, props.initialInput)
            ),
          }
        : {}),
    },
    window.location.origin
  );
};

const loadSource = async () => {
  const request = ++sourceRequest;
  sourceLoading.value = true;
  sourceError.value = "";
  activeLines.value = [];
  sourceRows.value = [];
  phaseLines.value = {};
  try {
    const response = await fetch(
      `/algorithm-code/${encodeURIComponent(props.definition.key)}.cpp`,
      { cache: "no-cache" }
    );
    if (!response.ok)
      throw new Error(`源码加载失败（HTTP ${response.status}）`);
    const source = await response.text();
    if (request !== sourceRequest) return;
    const parsed = parseCppSource(source);
    sourceRows.value = parsed.rows;
    phaseLines.value = parsed.phases;
  } catch (error) {
    if (request === sourceRequest) {
      sourceError.value =
        error instanceof Error ? error.message : "源码加载失败";
    }
  } finally {
    if (request === sourceRequest) sourceLoading.value = false;
  }
};

const onFrameLoad = () => {
  frameLoaded.value = true;
  send("configure");
};

const onMessage = (event: MessageEvent) => {
  if (
    event.origin !== window.location.origin ||
    event.source !== frameRef.value?.contentWindow
  )
    return;
  const message = event.data;
  if (
    !message ||
    message.channel !== TRACE_CHANNEL ||
    message.key !== props.definition.key
  )
    return;
  if (message.type === "mounted") {
    legacyReady.value = false;
    runtimeError.value = "";
    frameLoaded.value = true;
    send("configure");
    return;
  }
  if (message.type === "ready") {
    legacyReady.value = true;
    runtimeError.value = "";
    return;
  }
  if (message.type === "run-start") {
    currentRunId = Number(message.runId);
    lastSequence = 0;
    running.value = true;
    paused.value = false;
    complete.value = false;
    clearTrace();
    runtimeError.value = "";
    showPhase("initialize");
    emit("progress", false);
    return;
  }
  if (message.type === "trace") {
    if (!running.value || Number(message.runId) !== currentRunId) return;
    if (message.file !== `src/views/${resource.value.sourceFile}`) return;
    const sequence = Number(message.sequence);
    if (Number.isFinite(sequence) && sequence <= lastSequence) return;
    lastSequence = sequence;
    const phase = String(message.phase || "");
    if (phaseLines.value[phase]?.length) {
      pendingPhase.value = phase;
      if (!pendingFlush) pendingFlush = window.setTimeout(flushLine, 24);
    }
    return;
  }
  if (message.type === "reset") {
    currentRunId = Number(message.runId);
    running.value = false;
    complete.value = false;
    paused.value = false;
    clearTrace();
    emit("progress", false);
    return;
  }
  if (message.type === "play-state") {
    paused.value = Boolean(message.paused);
    return;
  }
  if (message.type === "resize") {
    const height = Number(message.height);
    if (Number.isFinite(height) && height > 0)
      frameHeight.value = Math.max(560, height + 8);
    return;
  }
  if (message.type === "completed") {
    if (Number(message.runId) !== currentRunId) return;
    if (pendingFlush) window.clearTimeout(pendingFlush);
    pendingFlush = 0;
    pendingPhase.value = "";
    showPhase("result");
    running.value = false;
    paused.value = false;
    complete.value = true;
    emit("progress", true);
    emit("completed", {
      algorithmKey: props.definition.key,
      title: props.definition.title,
      input: (message.input || null) as Record<string, unknown> | null,
      resultSummary: (message.summary || {}) as Record<string, unknown>,
    });
    return;
  }
  if (message.type === "error") {
    running.value = false;
    complete.value = false;
    paused.value = false;
    runtimeError.value = String(message.message || "原演示执行失败");
  }
};

watch(
  () => [props.definition.key, props.definition.title, props.initialInputError],
  () => {
    frameLoaded.value = false;
    legacyReady.value = false;
    running.value = false;
    complete.value = false;
    runtimeError.value = "";
    paused.value = false;
    clearTrace();
    currentRunId = -1;
    lastSequence = 0;
    emit("progress", false);
    void loadSource();
  },
  { immediate: true }
);

onMounted(() => {
  window.addEventListener("message", onMessage);
  layoutObserver = new ResizeObserver(() => {
    gridWidth.value = gridRef.value?.getBoundingClientRect().width || 0;
    actualSourceWidth.value =
      sourcePanelRef.value?.getBoundingClientRect().width || 0;
    if (sourceWidth.value !== null && sourceWidth.value > maxSourceWidth.value)
      setSourceWidth(maxSourceWidth.value);
  });
  gridWidth.value = gridRef.value?.getBoundingClientRect().width || 0;
  actualSourceWidth.value =
    sourcePanelRef.value?.getBoundingClientRect().width || 0;
  if (gridRef.value) layoutObserver.observe(gridRef.value);
  if (sourcePanelRef.value) layoutObserver.observe(sourcePanelRef.value);
});
onBeforeUnmount(() => {
  window.removeEventListener("message", onMessage);
  if (pendingFlush) window.clearTimeout(pendingFlush);
  sourceRequest += 1;
  layoutObserver?.disconnect();
  if (scrollFrame) cancelAnimationFrame(scrollFrame);
});
</script>

<style scoped>
.legacy-demo {
  min-width: 0;
  color: var(--oslab-ink);
}
.legacy-grid {
  display: grid;
  grid-template-columns:
    minmax(0, 1fr) 12px
    clamp(360px, var(--source-width), calc(100% - 572px));
  align-items: start;
}
.legacy-stage {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--oslab-line);
  background: #fff;
}
.stage-viewport {
  width: 100%;
  overflow: hidden;
}
.legacy-frame {
  display: block;
  width: 100%;
  border: 0;
  background: #fff;
}
.legacy-grid.resizing .legacy-frame {
  pointer-events: none;
}
.source-resize-handle {
  position: sticky;
  top: 12px;
  align-self: start;
  height: calc(100vh - 24px);
  cursor: col-resize;
  touch-action: none;
}
.source-resize-handle::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 5px;
  width: 2px;
  background: var(--oslab-line);
  content: "";
}
.source-resize-handle:hover::before,
.source-resize-handle:focus-visible::before,
.legacy-grid.resizing .source-resize-handle::before {
  background: var(--oslab-primary);
}
.source-resize-handle:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: -2px;
}
.entry-error {
  margin: 8px;
}
.source-panel {
  position: sticky;
  top: 12px;
  min-width: 0;
  max-height: calc(100vh - 24px);
  overflow: hidden;
  border: 1px solid var(--oslab-line);
  background: #fff;
}
.source-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--oslab-line);
  background: var(--oslab-panel);
}
.source-head h3 {
  margin: 0 0 3px;
  font-size: 14px;
  font-weight: 600;
}
.source-path {
  display: block;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  overflow-wrap: anywhere;
}
.source-state {
  flex: none;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.source-state.running {
  color: var(--oslab-highlight);
}
.source-code {
  max-height: calc(100vh - 78px);
  margin: 0;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
  color: #24292f;
  background: #fbfcfe;
  font: 14px/1.75 "Cascadia Code", "Cascadia Mono", Consolas, "Microsoft YaHei",
    monospace;
  font-variant-ligatures: none;
  tab-size: 4;
}
.source-code code {
  display: block;
  min-width: 0;
  padding: 10px 0;
}
.source-line {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  min-height: 24px;
  padding-right: 14px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.source-line.active {
  color: #10201c;
  background: #e3f2ec;
  box-shadow: inset 3px 0 #167b67;
}
.line-number {
  padding-right: 11px;
  color: #9aa0a6;
  font-size: 12px;
  text-align: right;
  user-select: none;
}
.source-line.active .line-number {
  color: #167b67;
  font-weight: 700;
}
.cpp-keyword {
  color: #af00db;
}
.cpp-directive {
  color: #6b7280;
}
.cpp-header {
  color: #af00db;
}
.cpp-string {
  color: #a31515;
}
.cpp-number {
  color: #098658;
}
.cpp-comment {
  color: #008000;
}
.source-loading,
.source-error {
  display: block;
  padding: 20px 12px;
}
.source-error {
  color: var(--oslab-weak);
  font-size: 13px;
}
@media (max-width: 1180px) {
  .legacy-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
  .source-resize-handle {
    display: none;
  }
  .source-panel {
    position: static;
    max-height: 70vh;
  }
  .source-code {
    max-height: calc(70vh - 65px);
  }
}
</style>
