<template>
  <div class="csl">
    <div class="csl-controls">
      <div class="csl-algos">
        <button
          v-for="item in ALGOS"
          :key="item.key"
          type="button"
          class="csl-algo"
          :class="{ active: algo === item.key }"
          @click="algo = item.key"
        >
          {{ item.label }}
        </button>
      </div>
      <label v-if="algo === 'RR'" class="csl-field">
        <span>时间片</span>
        <a-input-number v-model="quantum" :min="1" :max="10" size="small" />
      </label>
    </div>

    <table class="csl-table">
      <thead>
        <tr>
          <th>进程</th>
          <th>到达</th>
          <th>运行</th>
          <th>优先级</th>
          <th>完成</th>
          <th>周转</th>
          <th>等待</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.name">
          <td>{{ row.name }}</td>
          <td>{{ row.arrival }}</td>
          <td>{{ row.burst }}</td>
          <td>{{ row.priority }}</td>
          <td>{{ row.finish }}</td>
          <td>{{ row.turnaround }}</td>
          <td>{{ row.waiting }}</td>
        </tr>
      </tbody>
    </table>

    <div class="csl-gantt">
      <div class="csl-gantt-track">
        <div
          v-for="(seg, index) in timeline"
          :key="index"
          class="csl-seg"
          :class="'csl-c' + (index % 5)"
          :style="{ width: percent(seg.end - seg.start) }"
          :title="`${seg.name} ${seg.start}-${seg.end}`"
        >
          <span v-if="seg.end - seg.start >= 2">{{ seg.name }}</span>
        </div>
      </div>
      <div class="csl-ticks">
        <span v-for="t in ticks" :key="t" :style="{ left: percent(t) }">{{ t }}</span>
      </div>
    </div>

    <div class="csl-stats">
      <div class="csl-stat"><span>平均周转</span><strong>{{ avgTurnaround }}</strong></div>
      <div class="csl-stat"><span>平均等待</span><strong>{{ avgWaiting }}</strong></div>
      <div class="csl-stat"><span>CPU 利用率</span><strong>{{ utilization }}</strong></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

type Proc = { name: string; arrival: number; burst: number; priority: number };
type Seg = { name: string; start: number; end: number };

const ALGOS = [
  { key: "FCFS", label: "先来先服务" },
  { key: "SJF", label: "短作业优先" },
  { key: "RR", label: "时间片轮转" },
  { key: "PRIORITY", label: "优先级" },
] as const;

type AlgoKey = (typeof ALGOS)[number]["key"];

const props = defineProps<{
  preset?: { processes?: Proc[]; algo?: AlgoKey; quantum?: number };
}>();

const DEFAULT_PROCS: Proc[] = [
  { name: "P1", arrival: 0, burst: 8, priority: 3 },
  { name: "P2", arrival: 1, burst: 4, priority: 1 },
  { name: "P3", arrival: 2, burst: 9, priority: 4 },
  { name: "P4", arrival: 3, burst: 5, priority: 2 },
];

const procs = computed<Proc[]>(() => props.preset?.processes?.length ? props.preset.processes : DEFAULT_PROCS);
const algo = ref<AlgoKey>(props.preset?.algo || "FCFS");
const quantum = ref(props.preset?.quantum || 2);

const timeline = computed<Seg[]>(() => {
  const list = procs.value.map((p) => ({ ...p, remaining: p.burst }));
  const segs: Seg[] = [];
  let time = 0;
  let done = 0;

  if (algo.value === "RR") {
    const queue: string[] = [];
    const arrivalMap = new Map(list.map((p) => [p.name, p]));
    let index = 0;
    while (done < list.length) {
      while (index < list.length && list[index].arrival <= time) {
        queue.push(list[index].name);
        index += 1;
      }
      if (!queue.length) {
        time = list[index]?.arrival ?? time;
        continue;
      }
      const name = queue.shift() as string;
      const job = arrivalMap.get(name) as (Proc & { remaining: number });
      const run = Math.min(quantum.value, job.remaining);
      segs.push({ name, start: time, end: time + run });
      time += run;
      job.remaining -= run;
      while (index < list.length && list[index].arrival <= time) {
        queue.push(list[index].name);
        index += 1;
      }
      if (job.remaining > 0) {
        queue.push(name);
      } else {
        done += 1;
      }
    }
    return segs;
  }

  while (done < list.length) {
    const ready = list.filter((p) => p.arrival <= time && p.remaining > 0);
    if (!ready.length) {
      time = Math.min(...list.filter((p) => p.remaining > 0).map((p) => p.arrival));
      continue;
    }
    let pick = ready[0];
    if (algo.value === "SJF") {
      pick = ready.reduce((a, b) => (b.remaining < a.remaining ? b : a));
    } else if (algo.value === "PRIORITY") {
      pick = ready.reduce((a, b) => (b.priority < a.priority ? b : a));
    }
    segs.push({ name: pick.name, start: time, end: time + pick.remaining });
    time += pick.remaining;
    pick.remaining = 0;
    done += 1;
  }
  return segs;
});

const finishMap = computed(() => {
  const map = new Map<string, number>();
  timeline.value.forEach((seg) => map.set(seg.name, seg.end));
  return map;
});

const rows = computed(() =>
  procs.value.map((p) => {
    const finish = finishMap.value.get(p.name) ?? 0;
    return {
      ...p,
      finish,
      turnaround: finish - p.arrival,
      waiting: finish - p.arrival - p.burst,
    };
  })
);

const total = computed(() => Math.max(...timeline.value.map((s) => s.end), 1));
const ticks = computed(() => {
  const step = Math.max(1, Math.round(total.value / 8));
  const arr: number[] = [];
  for (let t = 0; t <= total.value; t += step) arr.push(t);
  return arr;
});

const percent = (value: number) => `${(value / total.value) * 100}%`;
const avgTurnaround = computed(
  () => (rows.value.reduce((sum, r) => sum + r.turnaround, 0) / rows.value.length).toFixed(1)
);
const avgWaiting = computed(
  () => (rows.value.reduce((sum, r) => sum + r.waiting, 0) / rows.value.length).toFixed(1)
);
const utilization = computed(() => {
  const busy = timeline.value.reduce((sum, s) => sum + (s.end - s.start), 0);
  return `${Math.round((busy / total.value) * 100)}%`;
});
</script>

<style scoped>
.csl { display: flex; flex-direction: column; gap: 14px; }
.csl-controls { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.csl-algos { display: flex; gap: 8px; flex-wrap: wrap; }
.csl-algo {
  padding: 5px 12px; border: 1px solid #e3dfd2; border-radius: 999px;
  background: #fff; color: #4d4a42; font: inherit; font-size: 13px; cursor: pointer;
}
.csl-algo.active { border-color: #d9c86a; background: #fdf6d8; color: #6b5500; }
.csl-field { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #6b675c; }
.csl-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.csl-table th, .csl-table td { border: 1px solid #efecdf; padding: 6px 8px; text-align: center; }
.csl-table th { background: #faf8ef; }
.csl-gantt { display: flex; flex-direction: column; gap: 6px; }
.csl-gantt-track { display: flex; height: 30px; border-radius: 8px; overflow: hidden; background: #f6f3e7; }
.csl-seg { display: flex; align-items: center; justify-content: center; font-size: 12px; color: #4d4a42; }
.csl-c0 { background: #f5e6a8; } .csl-c1 { background: #d8e8c8; } .csl-c2 { background: #cfe0f0; }
.csl-c3 { background: #f2d7c8; } .csl-c4 { background: #e3d9f0; }
.csl-ticks { position: relative; height: 16px; font-size: 11px; color: #8a8578; }
.csl-ticks span { position: absolute; transform: translateX(-50%); }
.csl-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.csl-stat { border: 1px solid #efecdf; border-radius: 10px; padding: 10px 12px; background: #fdfcf6; }
.csl-stat span { display: block; font-size: 12px; color: #8a8578; }
.csl-stat strong { font-size: 20px; }
</style>
