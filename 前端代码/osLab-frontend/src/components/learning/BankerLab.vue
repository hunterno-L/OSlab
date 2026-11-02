<template>
  <div class="bkl">
    <div class="bkl-available">
      <span class="bkl-label">可用资源</span>
      <label v-for="(name, index) in RES" :key="name" class="bkl-field">
        <span>{{ name }}</span>
        <a-input-number v-model="available[index]" :min="0" :max="20" size="small" />
      </label>
      <a-button size="small" type="primary" @click="check">检查安全性</a-button>
    </div>

    <div class="bkl-table-wrap">
      <table class="bkl-table">
        <thead>
          <tr>
            <th rowspan="2">进程</th>
            <th colspan="3">Allocation</th>
            <th colspan="3">Max</th>
            <th colspan="3">Need</th>
          </tr>
          <tr>
            <th v-for="name in RES" :key="'a' + name">{{ name }}</th>
            <th v-for="name in RES" :key="'m' + name">{{ name }}</th>
            <th v-for="name in RES" :key="'n' + name">{{ name }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(proc, index) in processes" :key="proc">
            <td>{{ proc }}</td>
            <td v-for="(value, i) in allocation[index]" :key="'a' + i">{{ value }}</td>
            <td v-for="(value, i) in max[index]" :key="'m' + i">{{ value }}</td>
            <td v-for="(value, i) in need[index]" :key="'n' + i">{{ value }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="finished" class="bkl-result" :class="safe ? 'ok' : 'bad'">
      <template v-if="safe">
        安全序列：<strong>{{ sequence.join(" → ") }}</strong>
      </template>
      <template v-else>
        找不到安全序列：剩余可用资源无法满足任何一个进程的 Need，系统处于不安全状态。
      </template>
    </div>
    <p v-else class="bkl-hint">调整可用资源后点「检查安全性」，看是否存在安全序列。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

const RES = ["A", "B", "C"];

const props = defineProps<{
  preset?: {
    processes?: string[];
    allocation?: number[][];
    max?: number[][];
    available?: number[];
  };
}>();

const processes = computed(() => props.preset?.processes || ["P0", "P1", "P2", "P3", "P4"]);
const allocation = computed(
  () => props.preset?.allocation || [
    [0, 1, 0], [2, 0, 0], [3, 0, 2], [2, 1, 1], [0, 0, 2],
  ]
);
const max = computed(
  () => props.preset?.max || [
    [7, 5, 3], [3, 2, 2], [9, 0, 2], [2, 2, 2], [4, 3, 3],
  ]
);

const available = ref<number[]>(props.preset?.available || [3, 3, 2]);
const finished = ref(false);
const safe = ref(false);
const sequence = ref<string[]>([]);

const need = computed(() =>
  max.value.map((row, i) => row.map((value, j) => value - allocation.value[i][j]))
);

const check = () => {
  const work = [...available.value];
  const done = new Array(processes.value.length).fill(false);
  const order: string[] = [];
  let progressed = true;

  while (progressed && order.length < processes.value.length) {
    progressed = false;
    for (let i = 0; i < processes.value.length; i += 1) {
      if (done[i]) continue;
      const canRun = need.value[i].every((value, j) => value <= work[j]);
      if (!canRun) continue;
      for (let j = 0; j < work.length; j += 1) work[j] += allocation.value[i][j];
      done[i] = true;
      order.push(processes.value[i]);
      progressed = true;
    }
  }

  sequence.value = order;
  safe.value = order.length === processes.value.length;
  finished.value = true;
};
</script>

<style scoped>
.bkl { display: flex; flex-direction: column; gap: 14px; }
.bkl-available { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.bkl-label { font-size: 13px; color: #6b675c; }
.bkl-field { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #8a8578; }
.bkl-table-wrap { overflow-x: auto; border: 1px solid #efecdf; border-radius: 10px; }
.bkl-table { width: 100%; border-collapse: collapse; font-size: 13px; text-align: center; }
.bkl-table th, .bkl-table td { border: 1px solid #f1eee2; padding: 6px 10px; }
.bkl-table th { background: #faf8ef; font-weight: 600; }
.bkl-result { border-radius: 10px; padding: 12px 14px; font-size: 13px; }
.bkl-result.ok { background: #e8f3e4; color: #3d6b2f; }
.bkl-result.bad { background: #fbe9e4; color: #8a3a25; }
.bkl-hint { margin: 0; font-size: 12px; color: #8a8578; }
</style>
