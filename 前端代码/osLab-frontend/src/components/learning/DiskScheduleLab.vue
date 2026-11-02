<template>
  <div class="dsl">
    <div class="dsl-controls">
      <label class="dsl-field"><span>磁头起点</span>
        <a-input-number v-model="head" :min="0" :max="maxTrack" size="small" />
      </label>
      <label class="dsl-field dsl-wide"><span>请求序列</span>
        <a-input v-model="input" size="small" placeholder="98,183,37,122,14,124,65,67" />
      </label>
    </div>

    <div class="dsl-algos">
      <button
        v-for="item in ALGOS" :key="item.key" type="button" class="dsl-algo"
        :class="{ active: algo === item.key }" @click="algo = item.key"
      >
        {{ item.label }}<span class="dsl-faults">{{ movementOf(item.key) }}</span>
      </button>
    </div>

    <div class="dsl-path">
      <span class="dsl-path-head">{{ head }}</span>
      <span v-for="(track, index) in path" :key="index" class="dsl-track">{{ track }}</span>
    </div>

    <div class="dsl-stats">
      <div class="dsl-stat"><span>移动磁道数</span><strong>{{ movement }}</strong></div>
      <div class="dsl-stat"><span>平均寻道</span><strong>{{ average }}</strong></div>
      <div class="dsl-stat"><span>请求数</span><strong>{{ requests.length }}</strong></div>
    </div>

    <svg class="dsl-chart" viewBox="0 0 600 160" preserveAspectRatio="none">
      <polyline
        :points="polyline"
        fill="none"
        stroke="#d9b93a"
        stroke-width="2"
      />
      <circle v-for="(point, index) in points" :key="index" :cx="point.x" :cy="point.y" r="2.5" fill="#6b5500" />
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

const ALGOS = [
  { key: "FCFS", label: "FCFS" },
  { key: "SSTF", label: "SSTF" },
  { key: "SCAN", label: "SCAN" },
  { key: "CSCAN", label: "C-SCAN" },
] as const;
type AlgoKey = (typeof ALGOS)[number]["key"];

const props = defineProps<{
  preset?: { head?: number; requests?: number[]; maxTrack?: number; algo?: AlgoKey };
}>();

const DEFAULT_REQ = [98, 183, 37, 122, 14, 124, 65, 67];
const maxTrack = computed(() => props.preset?.maxTrack || 199);
const head = ref(props.preset?.head ?? 53);
const input = ref((props.preset?.requests || DEFAULT_REQ).join(","));
const algo = ref<AlgoKey>(props.preset?.algo || "FCFS");

const requests = computed(() =>
  input.value.split(/[^0-9]+/).filter(Boolean).map(Number).slice(0, 20)
);

const build = (key: AlgoKey): number[] => {
  const list = [...requests.value];
  const out: number[] = [];
  let current = head.value;
  if (key === "FCFS") return [...list];

  if (key === "SSTF") {
    while (list.length) {
      let best = 0;
      list.forEach((track, index) => {
        if (Math.abs(track - current) < Math.abs(list[best] - current)) best = index;
      });
      current = list.splice(best, 1)[0];
      out.push(current);
    }
    return out;
  }

  const sorted = [...list].sort((a, b) => a - b);
  if (key === "SCAN") {
    const down = sorted.filter((t) => t < current).sort((a, b) => b - a);
    const up = sorted.filter((t) => t >= current).sort((a, b) => a - b);
    return [...down, ...up];
  }
  const up = sorted.filter((t) => t >= current).sort((a, b) => a - b);
  const down = sorted.filter((t) => t < current).sort((a, b) => a - b);
  return [...up, ...down];
};

const path = computed(() => build(algo.value));

const movement = computed(() => {
  let total = 0;
  let current = head.value;
  path.value.forEach((track) => {
    total += Math.abs(track - current);
    current = track;
  });
  return total;
});

const movementOf = (key: AlgoKey) => {
  let total = 0;
  let current = head.value;
  build(key).forEach((track) => {
    total += Math.abs(track - current);
    current = track;
  });
  return total;
};

const average = computed(() =>
  requests.value.length ? (movement.value / requests.value.length).toFixed(1) : "0"
);

const points = computed(() => {
  const seq = [head.value, ...path.value];
  const n = Math.max(seq.length - 1, 1);
  return seq.map((track, index) => ({
    x: (index / n) * 600,
    y: 150 - (track / maxTrack.value) * 140,
  }));
});

const polyline = computed(() => points.value.map((p) => `${p.x},${p.y}`).join(" "));
</script>

<style scoped>
.dsl { display: flex; flex-direction: column; gap: 14px; }
.dsl-controls { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.dsl-field { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #6b675c; }
.dsl-wide { flex: 1; min-width: 240px; }
.dsl-algos { display: flex; gap: 8px; flex-wrap: wrap; }
.dsl-algo {
  display: inline-flex; gap: 6px; align-items: center;
  padding: 5px 12px; border: 1px solid #e3dfd2; border-radius: 999px;
  background: #fff; color: #4d4a42; font: inherit; font-size: 13px; cursor: pointer;
}
.dsl-algo.active { border-color: #d9c86a; background: #fdf6d8; color: #6b5500; }
.dsl-faults { color: #8a8578; font-size: 12px; }
.dsl-path { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.dsl-path-head { font-weight: 600; color: #6b5500; }
.dsl-track {
  padding: 1px 8px; border-radius: 6px; background: #f4f2e8; font-size: 12px; color: #4d4a42;
}
.dsl-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.dsl-stat { border: 1px solid #efecdf; border-radius: 10px; padding: 10px 12px; background: #fdfcf6; }
.dsl-stat span { display: block; font-size: 12px; color: #8a8578; }
.dsl-stat strong { font-size: 20px; }
.dsl-chart { width: 100%; height: 160px; background: #fdfcf6; border: 1px solid #efecdf; border-radius: 10px; }
</style>
