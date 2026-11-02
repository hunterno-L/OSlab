<template>
  <div class="atl">
    <div class="atl-controls">
      <label class="atl-field">
        <span>虚拟地址</span>
        <a-input v-model="address" size="small" placeholder="0x1A2B3C" />
      </label>
      <label class="atl-field">
        <span>页大小</span>
        <a-select v-model="pageSize" size="small" style="width: 110px">
          <a-option :value="1024">1 KB</a-option>
          <a-option :value="4096">4 KB</a-option>
        </a-select>
      </label>
      <a-button size="small" type="primary" @click="run">开始翻译</a-button>
    </div>

    <div class="atl-steps">
      <div class="atl-step" :class="{ on: step >= 1 }">
        <span class="atl-step-no">1</span>
        <div>
          <strong>拆分地址</strong>
          <p>页号 = {{ pageNo }}，页内偏移 = {{ offset }}</p>
        </div>
      </div>
      <div class="atl-step" :class="{ on: step >= 2 }">
        <span class="atl-step-no">2</span>
        <div>
          <strong>查页表</strong>
          <p v-if="step >= 2">
            页号 {{ pageNo }} → 页框号 {{ frameNo }}
            <span v-if="tlbHit" class="atl-tag">TLB 命中</span>
            <span v-else class="atl-tag slow">TLB 缺失，访问内存页表</span>
          </p>
        </div>
      </div>
      <div class="atl-step" :class="{ on: step >= 3 }">
        <span class="atl-step-no">3</span>
        <div>
          <strong>拼接物理地址</strong>
          <p v-if="step >= 3">{{ frameNo }} × 页大小 + {{ offset }} = <b>{{ physical }}</b></p>
        </div>
      </div>
    </div>

    <table class="atl-table">
      <thead>
        <tr><th>页号</th><th>页框号</th><th>有效位</th></tr>
      </thead>
      <tbody>
        <tr v-for="row in table" :key="row.page" :class="{ hit: step >= 2 && row.page === pageNo }">
          <td>{{ row.page }}</td>
          <td>{{ row.frame }}</td>
          <td>{{ row.valid ? "1" : "0" }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

const props = defineProps<{ preset?: { address?: string; pageSize?: number } }>();

const address = ref(props.preset?.address || "0x1A2B3C");
const pageSize = ref(props.preset?.pageSize || 4096);
const step = ref(0);

const table = [
  { page: 0, frame: 5, valid: true },
  { page: 1, frame: 8, valid: true },
  { page: 2, frame: 2, valid: true },
  { page: 3, frame: 9, valid: true },
];

const numeric = computed(() => {
  const raw = address.value.trim();
  const value = raw.toLowerCase().startsWith("0x") ? parseInt(raw.slice(2), 16) : Number(raw);
  return Number.isFinite(value) ? value : 0;
});

const pageNo = computed(() => Math.floor(numeric.value / pageSize.value));
const offset = computed(() => numeric.value % pageSize.value);
const tlbHit = computed(() => pageNo.value % 2 === 0);
const frameNo = computed(() => table.find((row) => row.page === pageNo.value)?.frame ?? 0);
const physical = computed(() => `0x${(frameNo.value * pageSize.value + offset.value).toString(16).toUpperCase()}`);

const run = () => {
  step.value = 0;
  const timer = window.setInterval(() => {
    step.value += 1;
    if (step.value >= 3) window.clearInterval(timer);
  }, 500);
};
</script>

<style scoped>
.atl { display: flex; flex-direction: column; gap: 14px; }
.atl-controls { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.atl-field { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #6b675c; }
.atl-steps { display: flex; flex-direction: column; gap: 10px; }
.atl-step {
  display: flex; gap: 10px; border: 1px solid #efecdf; border-radius: 10px;
  padding: 10px 12px; background: #fff; opacity: 0.55; transition: 0.3s;
}
.atl-step.on { opacity: 1; border-color: #d9c86a; background: #fdfcf4; }
.atl-step-no {
  width: 22px; height: 22px; border-radius: 50%; background: #f2efe3;
  display: inline-flex; align-items: center; justify-content: center; font-size: 12px;
}
.atl-step p { margin: 4px 0 0; font-size: 13px; color: #55524a; }
.atl-tag { margin-left: 6px; padding: 1px 8px; border-radius: 999px; background: #e8f3e4; color: #3d6b2f; font-size: 11px; }
.atl-tag.slow { background: #fbe9e4; color: #8a3a25; }
.atl-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.atl-table th, .atl-table td { border: 1px solid #efecdf; padding: 6px 8px; text-align: center; }
.atl-table th { background: #faf8ef; }
.atl-table tr.hit td { background: #fdf6d8; font-weight: 600; }
</style>
