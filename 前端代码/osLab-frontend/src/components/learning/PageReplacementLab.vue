<template>
  <div class="prl">
    <div class="prl-controls">
      <label class="prl-field">
        <span>页框数</span>
        <a-input-number
          v-model="frameCount"
          :min="2"
          :max="5"
          size="small"
          @change="reset"
        />
      </label>
      <label class="prl-field prl-field-wide">
        <span>访问串</span>
        <a-input
          v-model="refInput"
          size="small"
          placeholder="例如 7,0,1,2,0,3,0,4"
          @change="reset"
        />
      </label>
      <a-button size="small" type="primary" @click="runAll">重新计算</a-button>
      <a-button size="small" @click="demoBelady">看 Belady 异常</a-button>
      <a-button size="small" @click="resetRef">恢复示例</a-button>
    </div>

    <div class="prl-algos">
      <button
        v-for="item in ALGOS"
        :key="item.key"
        type="button"
        class="prl-algo"
        :class="{ active: algo === item.key }"
        @click="algo = item.key"
      >
        {{ item.label }}
        <span class="prl-algo-faults">缺页 {{ faultsOf(item.key) }}</span>
      </button>
    </div>

    <div class="prl-stats">
      <div class="prl-stat">
        <span class="prl-stat-label">缺页次数</span>
        <strong>{{ current.faults }}</strong>
      </div>
      <div class="prl-stat">
        <span class="prl-stat-label">缺页率</span>
        <strong>{{ percent(current.faults, refs.length) }}</strong>
      </div>
      <div class="prl-stat">
        <span class="prl-stat-label">命中率</span>
        <strong>{{ percent(refs.length - current.faults, refs.length) }}</strong>
      </div>
      <div class="prl-stat">
        <span class="prl-stat-label">访问次数</span>
        <strong>{{ refs.length }}</strong>
      </div>
    </div>

    <div class="prl-table-wrap">
      <table class="prl-table">
        <thead>
          <tr>
            <th>#</th>
            <th v-for="i in frameCount" :key="i">页框 {{ i }}</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(step, index) in current.steps" :key="index">
            <td class="prl-page">{{ step.page }}</td>
            <td v-for="(frame, fi) in step.frames" :key="fi" :class="{ 'is-new': fi === step.insertAt }">
              {{ frame === null ? "—" : frame }}
            </td>
            <td>
              <span v-if="step.hit" class="prl-tag hit">命中</span>
              <span v-else class="prl-tag miss">
                缺页{{ step.evicted === null ? "·空闲" : "·换出 " + step.evicted }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="prl-compare">
      <div v-for="item in ALGOS" :key="item.key" class="prl-bar-row">
        <span class="prl-bar-name">{{ item.label }}</span>
        <span class="prl-bar-track">
          <span
            class="prl-bar"
            :class="{ current: algo === item.key }"
            :style="{ width: barWidth(item.key) }"
          ></span>
        </span>
        <span class="prl-bar-value">{{ faultsOf(item.key) }} 次</span>
      </div>
    </div>

    <p v-if="hint" class="prl-hint">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";

const ALGOS = [
  { key: "FIFO", label: "FIFO" },
  { key: "LRU", label: "LRU" },
  { key: "OPT", label: "OPT" },
  { key: "CLOCK", label: "CLOCK" },
] as const;

type AlgoKey = (typeof ALGOS)[number]["key"];

type Step = {
  page: number;
  frames: Array<number | null>;
  hit: boolean;
  evicted: number | null;
  insertAt: number;
};

type Result = { steps: Step[]; faults: number };

const props = defineProps<{
  preset?: {
    frames?: number;
    ref?: string;
    algo?: AlgoKey;
  };
}>();

const DEFAULT_REF = "7,0,1,2,0,3,0,4,2,3,0,3,2,1,2,0,1,7,0,1";
const BELADY_REF = "1,2,3,4,1,2,5,1,2,3,4,5";

const frameCount = ref(props.preset?.frames || 3);
const refInput = ref(props.preset?.ref || DEFAULT_REF);
const algo = ref<AlgoKey>(props.preset?.algo || "FIFO");
const hint = ref("");

const refs = computed(() =>
  refInput.value
    .split(/[^0-9]+/)
    .filter((item) => item !== "")
    .map((item) => Number(item))
    .slice(0, 40)
);

const simulate = (algoKey: AlgoKey, frames: number, pages: number[]): Result => {
  const slots: Array<number | null> = Array.from({ length: frames }, () => null);
  const steps: Step[] = [];
  const queue: number[] = [];
  const lastUsed = new Map<number, number>();
  const refBits = new Map<number, number>();
  let pointer = 0;
  let faults = 0;

  pages.forEach((page, index) => {
    const hitIndex = slots.indexOf(page);
    if (hitIndex >= 0) {
      if (algoKey !== "FIFO") lastUsed.set(page, index);
      if (algoKey === "CLOCK") refBits.set(page, 1);
      steps.push({ page, frames: [...slots], hit: true, evicted: null, insertAt: -1 });
      return;
    }

    faults += 1;
    let insertAt = slots.indexOf(null);
    let evicted: number | null = null;

    if (insertAt < 0) {
      if (algoKey === "FIFO") {
        evicted = queue.shift() as number;
        insertAt = slots.indexOf(evicted);
      } else if (algoKey === "LRU") {
        let oldest = Infinity;
        slots.forEach((value, i) => {
          const used = lastUsed.get(value as number) ?? -1;
          if (used < oldest) {
            oldest = used;
            insertAt = i;
          }
        });
        evicted = slots[insertAt] as number;
      } else if (algoKey === "OPT") {
        let farthest = -1;
        slots.forEach((value, i) => {
          const next = pages.indexOf(value as number, index + 1);
          const distance = next < 0 ? Infinity : next;
          if (distance > farthest) {
            farthest = distance;
            insertAt = i;
          }
        });
        evicted = slots[insertAt] as number;
      } else {
        while (true) {
          const value = slots[pointer] as number;
          if ((refBits.get(value) || 0) === 0) {
            insertAt = pointer;
            evicted = value;
            pointer = (pointer + 1) % frames;
            break;
          }
          refBits.set(value, 0);
          pointer = (pointer + 1) % frames;
        }
      }
    }

    slots[insertAt] = page;
    if (algoKey === "FIFO") {
      queue.push(page);
      if (queue.length > frames) queue.shift();
    }
    if (algoKey === "LRU") lastUsed.set(page, index);
    if (algoKey === "CLOCK") refBits.set(page, 1);

    steps.push({ page, frames: [...slots], hit: false, evicted, insertAt });
  });

  return { steps, faults };
};

const results = computed<Record<AlgoKey, Result>>(() => {
  const pages = refs.value;
  const frames = frameCount.value;
  return {
    FIFO: simulate("FIFO", frames, pages),
    LRU: simulate("LRU", frames, pages),
    OPT: simulate("OPT", frames, pages),
    CLOCK: simulate("CLOCK", frames, pages),
  };
});

const current = computed(() => results.value[algo.value]);

const faultsOf = (key: AlgoKey) => results.value[key].faults;

const maxFaults = computed(() =>
  Math.max(1, ...ALGOS.map((item) => faultsOf(item.key)))
);

const barWidth = (key: AlgoKey) =>
  `${Math.round((faultsOf(key) / maxFaults.value) * 100)}%`;

const percent = (part: number, total: number) =>
  total === 0 ? "0%" : `${Math.round((part / total) * 100)}%`;

const reset = () => {
  hint.value = "";
};

const resetRef = () => {
  refInput.value = DEFAULT_REF;
  frameCount.value = 3;
  hint.value = "";
};

const runAll = () => {
  hint.value = `已按 ${frameCount.value} 个页框重新计算 ${refs.value.length} 次访问`;
};

const demoBelady = () => {
  refInput.value = BELADY_REF;
  frameCount.value = 3;
  algo.value = "FIFO";
  hint.value = "FIFO + 3 个页框：缺页 9 次；把页框调到 4，缺页反而变成 10 次——这就是 Belady 异常。";
};

watch(
  () => props.preset,
  (preset) => {
    if (preset?.frames) frameCount.value = preset.frames;
    if (preset?.ref) refInput.value = preset.ref;
    if (preset?.algo) algo.value = preset.algo;
  },
  { deep: true }
);
</script>

<style scoped>
.prl {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.prl-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.prl-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b675c;
}
.prl-field-wide {
  flex: 1;
  min-width: 240px;
}
.prl-algos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.prl-algo {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid #e3dfd2;
  border-radius: 999px;
  background: #fff;
  color: #4d4a42;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.prl-algo.active {
  border-color: #d9c86a;
  background: #fdf6d8;
  color: #6b5500;
}
.prl-algo-faults {
  color: #8a8578;
  font-size: 12px;
}
.prl-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.prl-stat {
  border: 1px solid #efecdf;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fdfcf6;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.prl-stat-label {
  font-size: 12px;
  color: #8a8578;
}
.prl-stat strong {
  font-size: 20px;
}
.prl-table-wrap {
  overflow-x: auto;
  border: 1px solid #efecdf;
  border-radius: 10px;
}
.prl-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.prl-table th,
.prl-table td {
  border-bottom: 1px solid #f1eee2;
  padding: 6px 8px;
  text-align: center;
  white-space: nowrap;
}
.prl-table th {
  background: #faf8ef;
  font-weight: 600;
}
.prl-table td.is-new {
  background: #fdf6d8;
  font-weight: 600;
}
.prl-page {
  color: #8a8578;
}
.prl-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
}
.prl-tag.hit {
  background: #e8f3e4;
  color: #3d6b2f;
}
.prl-tag.miss {
  background: #fbe9e4;
  color: #8a3a25;
}
.prl-compare {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.prl-bar-row {
  display: grid;
  grid-template-columns: 70px 1fr 70px;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #6b675c;
}
.prl-bar-track {
  height: 10px;
  border-radius: 999px;
  background: #f2efe3;
  overflow: hidden;
}
.prl-bar {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: #cfc8a6;
}
.prl-bar.current {
  background: #d9b93a;
}
.prl-bar-value {
  text-align: right;
}
.prl-hint {
  margin: 0;
  font-size: 12px;
  color: #8a6d00;
  background: #fdf6d8;
  border-radius: 8px;
  padding: 8px 10px;
}
</style>
