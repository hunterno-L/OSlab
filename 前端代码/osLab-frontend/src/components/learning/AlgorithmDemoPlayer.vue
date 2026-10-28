<template>
  <section class="legacy-demo" aria-label="原版算法演示">
    <div class="legacy-grid">
      <div class="legacy-stage" aria-label="原算法参数、动画舞台与结果表">
        <a-alert v-if="initialInputError" type="error" class="entry-error">
          {{ initialInputError }}
        </a-alert>
        <a-alert v-if="runtimeError" type="error" class="entry-error">
          {{ runtimeError }}
        </a-alert>
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

      <section class="source-panel" aria-label="原始算法源代码">
        <header class="source-head">
          <div>
            <h3>算法源代码</h3>
            <span class="source-path oslab-mono">{{
              resource.sourceFile
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
        <pre
          v-else
          class="source-code"
          aria-label="原始 Vue 组件中的算法实现"
        ><code><span
          v-for="row in sourceRows"
          :key="row.number"
          :id="`legacy-line-${row.number}`"
          class="source-line"
          :class="{ active: activeLine === row.number }"
        ><span class="line-number oslab-mono">{{ row.number }}</span><span>{{ row.text || " " }}</span></span></code></pre>
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

interface SourceRow {
  number: number;
  text: string;
}

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
const frameRef = ref<HTMLIFrameElement | null>(null);
const sourceRows = ref<SourceRow[]>([]);
const sourceLoading = ref(true);
const sourceError = ref("");
const runtimeError = ref("");
const frameLoaded = ref(false);
const legacyReady = ref(false);
const running = ref(false);
const paused = ref(false);
const frameHeight = ref(920);
const complete = ref(false);
const activeLine = ref<number | null>(null);
const pendingLine = ref<number | null>(null);
let pendingFlush = 0;
let sourceRequest = 0;

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

const showActiveLine = (line: number) => {
  activeLine.value = line;
  if (!line) return;
  requestAnimationFrame(() => {
    document.getElementById(`legacy-line-${line}`)?.scrollIntoView({
      block: "nearest",
      behavior: "smooth",
    });
  });
};

const flushLine = () => {
  pendingFlush = 0;
  if (pendingLine.value !== null) showActiveLine(pendingLine.value);
};

const clearTrace = () => {
  if (pendingFlush) window.clearTimeout(pendingFlush);
  pendingFlush = 0;
  pendingLine.value = null;
  activeLine.value = null;
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
  activeLine.value = null;
  sourceRows.value = [];
  try {
    const response = await fetch(
      resource.value.sourceFile
        ? `/legacy-simulation/source/${resource.value.sourceFile}`
        : ""
    );
    if (!response.ok)
      throw new Error(`源码加载失败（HTTP ${response.status}）`);
    const source = await response.text();
    const open = source.match(/<script\b[^>]*>/m);
    if (!open || open.index === undefined)
      throw new Error("原组件没有可展示的 script 段");
    const start = open.index + open[0].length;
    const end = source.indexOf("<" + "/script>", start);
    if (end < 0) throw new Error("原组件 script 段不完整");
    const offset = source.slice(0, start).split(/\r?\n/).length - 1;
    const script = source.slice(start, end).replace(/^\r?\n/, "");
    const trimmedOffset = script.length !== source.slice(start, end).length;
    if (request !== sourceRequest) return;
    sourceRows.value = script.split(/\r?\n/).map((text, index) => ({
      number: offset + (trimmedOffset ? 2 : 1) + index,
      text,
    }));
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
    running.value = true;
    paused.value = false;
    complete.value = false;
    clearTrace();
    runtimeError.value = "";
    emit("progress", false);
    return;
  }
  if (message.type === "trace") {
    if (message.file !== `src/views/${resource.value.sourceFile}`) return;
    const line = Number(message.line);
    if (Number.isInteger(line) && line > 0) {
      pendingLine.value = line;
      if (!pendingFlush) pendingFlush = window.setTimeout(flushLine, 24);
    }
    return;
  }
  if (message.type === "reset") {
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
      frameHeight.value = Math.max(720, Math.min(4000, height + 8));
    return;
  }
  if (message.type === "completed") {
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
    emit("progress", false);
    void loadSource();
  },
  { immediate: true }
);

onMounted(() => window.addEventListener("message", onMessage));
onBeforeUnmount(() => {
  window.removeEventListener("message", onMessage);
  if (pendingFlush) window.clearTimeout(pendingFlush);
  sourceRequest += 1;
});
</script>

<style scoped>
.legacy-demo {
  min-width: 0;
  color: var(--oslab-ink);
}
.legacy-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(330px, 0.85fr);
  align-items: start;
  gap: 12px;
}
.legacy-stage {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--oslab-line);
  background: #fff;
}
.legacy-frame {
  display: block;
  width: 100%;
  min-height: 920px;
  border: 0;
  background: #fff;
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
  overflow: auto;
  color: #282c34;
  background: #fafbfc;
  font: 12px/1.55 Consolas, "SFMono-Regular", Menlo, monospace;
  tab-size: 2;
}
.source-code code {
  display: block;
  min-width: max-content;
  padding: 8px 0;
}
.source-line {
  display: grid;
  grid-template-columns: 44px auto;
  min-height: 18px;
  padding-right: 12px;
  white-space: pre;
}
.source-line.active {
  color: #162022;
  background: #d8eee8;
  box-shadow: inset 3px 0 #167b67;
}
.line-number {
  padding-right: 9px;
  color: #8a9198;
  text-align: right;
  user-select: none;
}
.source-line.active .line-number {
  color: #167b67;
  font-weight: 700;
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
@media (max-width: 1050px) {
  .legacy-grid {
    grid-template-columns: minmax(0, 1fr);
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
