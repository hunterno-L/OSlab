<template>
  <div class="nml">
    <div v-if="loading" class="nml-state">正在加载知识关系…</div>
    <div v-else-if="!center" class="nml-state">暂无关联节点，先看知识内容建立概念。</div>

    <template v-else>
      <svg class="nml-canvas" viewBox="0 0 640 300">
        <g v-for="(node, index) in satellites" :key="node.name">
          <line
            :x1="320" :y1="150" :x2="node.x" :y2="node.y"
            class="nml-edge" :class="node.kind"
          />
          <circle :cx="node.x" :cy="node.y" r="34" class="nml-halo" :class="node.kind" />
          <text :x="node.x" :y="node.y + 4" text-anchor="middle" class="nml-label">
            {{ short(node.name) }}
          </text>
        </g>
        <circle cx="320" cy="150" r="46" class="nml-center" />
        <text x="320" y="154" text-anchor="middle" class="nml-center-label">{{ short(center) }}</text>
      </svg>

      <div class="nml-legend">
        <span class="dot prev"></span>前置
        <span class="dot next"></span>后续
      </div>

      <div class="nml-lists">
        <div class="nml-list">
          <span class="nml-list-title">前置</span>
          <span v-for="name in prev" :key="name" class="nml-chip">{{ name }}</span>
          <span v-if="!prev.length" class="nml-empty">无</span>
        </div>
        <div class="nml-list">
          <span class="nml-list-title">后续</span>
          <span v-for="name in next" :key="name" class="nml-chip">{{ name }}</span>
          <span v-if="!next.length" class="nml-empty">无</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { nodeWorkbench } from "@/api/learning";

const props = defineProps<{ preset?: { nodeCode?: string; nodeName?: string } }>();

const loading = ref(true);
const center = ref("");
const prev = ref<string[]>([]);
const next = ref<string[]>([]);

const short = (name: string) => (name.length > 6 ? name.slice(0, 6) + "…" : name);

const satellites = computed(() => {
  const items = [
    ...prev.value.slice(0, 3).map((name) => ({ name, kind: "prev" })),
    ...next.value.slice(0, 3).map((name) => ({ name, kind: "next" })),
  ];
  const count = items.length;
  return items.map((item, index) => {
    const angle = count <= 1 ? -Math.PI / 2 : -Math.PI / 2 + (index / count) * Math.PI * 2;
    return { ...item, x: 320 + Math.cos(angle) * 180, y: 150 + Math.sin(angle) * 105 };
  });
});

onMounted(async () => {
  try {
    const res = await nodeWorkbench(
      props.preset?.nodeCode || props.preset?.nodeName || ""
    );
    const data = res.data?.data;
    center.value = data?.mastery?.name || props.preset?.nodeName || "";
    const pick = (list: any[]) =>
      (list || [])
        .map((item) =>
          typeof item === "string"
            ? item
            : item?.displayName || item?.title || item?.name
        )
        .filter((name: string) => Boolean(name));
    prev.value = pick(data?.prerequisites || data?.prerequisiteNodes);
    next.value = pick(data?.successors);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.nml { display: flex; flex-direction: column; gap: 12px; }
.nml-state { font-size: 13px; color: #8a8578; padding: 8px 0; }
.nml-canvas { width: 100%; height: 300px; background: #fdfcf6; border: 1px solid #efecdf; border-radius: 12px; }
.nml-edge { stroke: #d8d3c0; stroke-width: 1.5; stroke-dasharray: 6 5; animation: dash 20s linear infinite; }
.nml-edge.next { stroke: #cfc07a; }
@keyframes dash { to { stroke-dashoffset: -200; } }
.nml-halo { fill: #f4f2e8; stroke: #e3dfd2; }
.nml-halo.next { fill: #fdf6d8; stroke: #e0cd77; }
.nml-label { font-size: 12px; fill: #4d4a42; }
.nml-center { fill: #fff3c4; stroke: #d9c86a; stroke-width: 2; }
.nml-center-label { font-size: 13px; font-weight: 600; fill: #6b5500; }
.nml-legend { display: flex; gap: 16px; font-size: 12px; color: #8a8578; align-items: center; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; margin-right: 4px; }
.dot.prev { background: #d8d3c0; }
.dot.next { background: #cfc07a; }
.nml-lists { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.nml-list { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.nml-list-title { font-size: 12px; color: #8a8578; margin-right: 4px; }
.nml-chip { padding: 2px 10px; border-radius: 999px; background: #f4f2e8; font-size: 12px; color: #4d4a42; }
.nml-empty { font-size: 12px; color: #b3ad9a; }
</style>
