<template>
  <section class="node-courses">
    <router-link to="/graph">课程学习与教材目录</router-link
    ><template v-if="units.length"
      ><span>在课程中学习：</span
      ><router-link
        v-for="unit in units"
        :key="unit.unitCode"
        :to="{
          path: unitPath(unit.courseCode, unit.unitCode),
          query: { mode: 'REVIEW' },
        }"
        >{{ unit.title }}</router-link
      ></template
    ><span v-if="error"
      >课程关联暂时无法加载 <button @click="load">重试</button></span
    >
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from "vue";
import { getNodeUnits, unitPath, UnitSummary } from "@/api/course";
// eslint-disable-next-line no-undef
const props = defineProps<{ nodeCode?: string }>();
const units = ref<(UnitSummary & { courseCode: string })[]>([]),
  error = ref(false);
let epoch = 0;
async function load() {
  const current = ++epoch;
  units.value = [];
  error.value = false;
  if (!props.nodeCode) return;
  try {
    const data = await getNodeUnits(props.nodeCode);
    if (current === epoch) units.value = data;
  } catch {
    if (current === epoch) error.value = true;
  }
}
watch(() => props.nodeCode, load, { immediate: true });
</script>
<style scoped>
.node-courses {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 0;
  font-size: 13px;
  color: #86909c;
}
.node-courses a {
  color: #2447d8;
  text-decoration: none;
}
.node-courses button {
  border: 0;
  background: none;
  color: #2447d8;
  cursor: pointer;
}
</style>
