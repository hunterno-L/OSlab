<template>
  <div class="sml">
    <div class="sml-stage">
      <svg viewBox="0 0 640 360" class="sml-svg">
        <defs>
          <marker id="sml-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
            <path d="M0,0 L6,3 L0,6 Z" fill="#b9b29a" />
          </marker>
        </defs>
        <g v-for="(edge, index) in edges" :key="'e' + index">
          <line
            :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
            class="sml-edge" :class="{ active: activeIndex === index }"
            marker-end="url(#sml-arrow)"
          />
          <text :x="edge.mx" :y="edge.my" class="sml-edge-label" text-anchor="middle">{{ edge.label }}</text>
        </g>

        <g v-for="state in nodes" :key="state.key">
          <circle :cx="state.x" :cy="state.y" r="46" class="sml-node"
                  :class="{ on: current === state.key }" />
          <text :x="state.x" :y="state.y + 4" text-anchor="middle" class="sml-node-label">{{ state.label }}</text>
        </g>

        <circle v-if="token" :cx="token.x" :cy="token.y" r="7" class="sml-token" />
      </svg>
    </div>

    <div class="sml-controls">
      <a-button size="small" type="primary" @click="toggle">{{ playing ? "暂停" : "播放演示" }}</a-button>
      <a-button size="small" @click="step">单步</a-button>
      <span class="sml-current">当前：{{ currentLabel }}</span>
    </div>

    <ol class="sml-log">
      <li v-for="(line, index) in log" :key="index">{{ line }}</li>
    </ol>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";

type State = { key: string; label: string };
type Edge = { from: string; to: string; label: string };

const props = defineProps<{
  preset?: { states?: State[]; edges?: Edge[] };
}>();

const DEFAULT_STATES: State[] = [
  { key: "user", label: "用户态" },
  { key: "kernel", label: "内核态" },
];
const DEFAULT_EDGES: Edge[] = [
  { from: "user", to: "kernel", label: "中断 / 异常 / 系统调用" },
  { from: "kernel", to: "user", label: "返回用户态" },
];

const states = computed(() => props.preset?.states?.length ? props.preset.states : DEFAULT_STATES);
const edgesInput = computed(() => props.preset?.edges?.length ? props.preset.edges : DEFAULT_EDGES);

const nodes = computed(() => {
  const list = states.value;
  return list.map((state, index) => {
    const angle = -Math.PI / 2 + (index / list.length) * Math.PI * 2;
    return { ...state, x: 320 + Math.cos(angle) * 150, y: 180 + Math.sin(angle) * 110 };
  });
});

const edges = computed(() =>
  edgesInput.value.map((edge) => {
    const a = nodes.value.find((n) => n.key === edge.from)!;
    const b = nodes.value.find((n) => n.key === edge.to)!;
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const len = Math.hypot(dx, dy) || 1;
    const ux = dx / len;
    const uy = dy / len;
    return {
      ...edge,
      x1: a.x + ux * 48,
      y1: a.y + uy * 48,
      x2: b.x - ux * 50,
      y2: b.y - uy * 50,
      mx: (a.x + b.x) / 2 + uy * 14,
      my: (a.y + b.y) / 2 - ux * 14,
    };
  })
);

const current = ref(states.value[0]?.key || "");
const activeIndex = ref(-1);
const playing = ref(false);
const log = ref<string[]>([]);
const token = ref<{ x: number; y: number } | null>(null);
let timer: number | undefined;

const currentLabel = computed(
  () => states.value.find((s) => s.key === current.value)?.label || ""
);

const animateEdge = (index: number) => {
  const edge = edges.value[index];
  if (!edge) return;
  activeIndex.value = index;
  const a = nodes.value.find((n) => n.key === edge.from)!;
  token.value = { x: a.x, y: a.y };
  const started = Date.now();
  const duration = 700;
  const tick = () => {
    const t = Math.min(1, (Date.now() - started) / duration);
    const b = nodes.value.find((n) => n.key === edge.to)!;
    token.value = { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t };
    if (t < 1) {
      requestAnimationFrame(tick);
    } else {
      current.value = edge.to;
      log.value.unshift(`${edge.from} → ${edge.to}：${edge.label}`);
      log.value = log.value.slice(0, 5);
    }
  };
  requestAnimationFrame(tick);
};

const step = () => {
  const from = current.value;
  const candidates = edges.value
    .map((edge, index) => ({ edge, index }))
    .filter((item) => item.edge.from === from);
  if (!candidates.length) return;
  const pickIndex = (Date.now() / 700 | 0) % candidates.length;
  animateEdge(candidates[pickIndex].index);
};

const toggle = () => {
  if (playing.value) {
    playing.value = false;
    if (timer) window.clearInterval(timer);
    return;
  }
  playing.value = true;
  step();
  timer = window.setInterval(step, 1600);
};

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
});
</script>

<style scoped>
.sml { display: flex; flex-direction: column; gap: 12px; }
.sml-stage { border: 1px solid #efecdf; border-radius: 12px; background: #fdfcf6; }
.sml-svg { width: 100%; height: 340px; }
.sml-node { fill: #fff; stroke: #ddd7c4; stroke-width: 1.5; transition: fill 0.3s; }
.sml-node.on { fill: #fff3c4; stroke: #d9c86a; stroke-width: 2.5; }
.sml-node-label { font-size: 12px; fill: #4d4a42; }
.sml-edge { stroke: #ddd7c4; stroke-width: 1.5; }
.sml-edge.active { stroke: #d9b93a; stroke-width: 2.5; }
.sml-edge-label { font-size: 10px; fill: #8a8578; }
.sml-token { fill: #d9b93a; stroke: #fff; stroke-width: 2; }
.sml-controls { display: flex; gap: 10px; align-items: center; }
.sml-current { font-size: 12px; color: #8a8578; }
.sml-log { margin: 0; padding-left: 18px; font-size: 12px; color: #6b675c; line-height: 1.8; }
</style>
