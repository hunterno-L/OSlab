<template>
  <div class="demo-panel graph-demo">
    <span class="demo-badge">示例</span>
    <div class="demo-switch" role="group" aria-label="图谱视图">
      <button
        type="button"
        :aria-pressed="mode === 'public'"
        @click="mode = 'public'"
      >
        公共图谱
      </button>
      <button
        type="button"
        :aria-pressed="mode === 'profile'"
        @click="mode = 'profile'"
      >
        我的画像
      </button>
    </div>

    <svg
      viewBox="0 0 480 300"
      role="img"
      aria-label="知识图谱与个人学习画像示意"
    >
      <title>知识图谱与个人学习画像示意</title>
      <g v-for="edge in edges" :key="edge.id">
        <line
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          class="edge"
        />
        <text :x="edge.lx" :y="edge.ly" text-anchor="middle" class="edge-label">
          {{ edge.label }}
        </text>
      </g>
      <g
        v-for="node in nodes"
        :key="node.id"
        class="node"
        :class="[
          `is-${node.type}`,
          mode === 'profile' ? `is-${node.status}` : '',
        ]"
      >
        <rect :x="node.x" :y="node.y" width="104" height="40" rx="8" />
        <text
          :x="node.x + 52"
          :y="node.y + 19"
          text-anchor="middle"
          class="node-label"
        >
          {{ node.label }}
        </text>
        <text
          v-if="mode === 'profile'"
          :x="node.x + 52"
          :y="node.y + 33"
          text-anchor="middle"
          class="node-status"
        >
          {{ statusLabel[node.status] }}
        </text>
      </g>
    </svg>

    <p v-if="mode === 'profile'" class="demo-note">
      「页表」暂无实践证据，显示为未学习，不按薄弱处理。
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

type Status = "mastered" | "review" | "weak" | "unseen";
type NodeType = "concept" | "algorithm" | "mechanism";

const mode = ref<"public" | "profile">("public");

const statusLabel: Record<Status, string> = {
  mastered: "已掌握",
  review: "待巩固",
  weak: "薄弱",
  unseen: "未学习",
};

const nodes: {
  id: string;
  label: string;
  type: NodeType;
  status: Status;
  x: number;
  y: number;
}[] = [
  {
    id: "lru",
    label: "LRU",
    type: "algorithm",
    status: "mastered",
    x: 188,
    y: 56,
  },
  {
    id: "fifo",
    label: "FIFO",
    type: "algorithm",
    status: "review",
    x: 40,
    y: 140,
  },
  {
    id: "opt",
    label: "OPT",
    type: "algorithm",
    status: "weak",
    x: 336,
    y: 140,
  },
  {
    id: "fault",
    label: "缺页中断",
    type: "mechanism",
    status: "mastered",
    x: 188,
    y: 140,
  },
  {
    id: "locality",
    label: "局部性原理",
    type: "concept",
    status: "mastered",
    x: 188,
    y: 224,
  },
  {
    id: "ptable",
    label: "页表",
    type: "mechanism",
    status: "unseen",
    x: 336,
    y: 224,
  },
];

const edges = [
  {
    id: "1",
    x1: 240,
    y1: 96,
    x2: 240,
    y2: 140,
    label: "前置",
    lx: 252,
    ly: 122,
  },
  {
    id: "2",
    x1: 240,
    y1: 180,
    x2: 240,
    y2: 224,
    label: "依据",
    lx: 252,
    ly: 206,
  },
  {
    id: "3",
    x1: 144,
    y1: 76,
    x2: 188,
    y2: 76,
    label: "对比",
    lx: 166,
    ly: 66,
  },
  {
    id: "4",
    x1: 292,
    y1: 76,
    x2: 336,
    y2: 160,
    label: "对比",
    lx: 326,
    ly: 112,
  },
  {
    id: "5",
    x1: 292,
    y1: 244,
    x2: 336,
    y2: 244,
    label: "相关",
    lx: 314,
    ly: 234,
  },
];
</script>

<style scoped>
.graph-demo {
  min-height: 320px;
}

.graph-demo svg {
  display: block;
  width: 100%;
  height: auto;
  margin-top: 16px;
}

.edge {
  stroke: var(--oslab-line-strong);
  stroke-width: 1;
}

.edge-label {
  fill: var(--oslab-muted-strong);
  font-family: var(--oslab-font-ui);
  font-size: 10px;
}

.node rect {
  fill: var(--oslab-graph-node-fill-default);
  stroke: var(--oslab-line-strong);
  stroke-width: 1;
}

.node.is-algorithm rect {
  fill: var(--oslab-graph-category-algorithm);
}

.node.is-mechanism rect {
  fill: var(--oslab-graph-category-function);
}

.node.is-concept rect {
  fill: var(--oslab-graph-category-concept);
}

.node-label {
  fill: var(--oslab-ink);
  font-family: var(--oslab-font-ui);
  font-size: 13px;
  font-weight: 600;
}

.node-status {
  font-family: var(--oslab-font-ui);
  font-size: 10px;
}

.node.is-mastered rect {
  stroke: var(--oslab-mastered);
  stroke-width: 2;
}

.node.is-mastered .node-status {
  fill: var(--oslab-mastered);
}

.node.is-review rect {
  stroke: var(--oslab-review);
  stroke-width: 2;
}

.node.is-review .node-status {
  fill: var(--oslab-review);
}

.node.is-weak rect {
  stroke: var(--oslab-weak);
  stroke-width: 2;
}

.node.is-weak .node-status {
  fill: var(--oslab-weak);
}

.node.is-unseen rect {
  stroke: var(--oslab-unseen);
  stroke-dasharray: 4 4;
}

.node.is-unseen .node-status {
  fill: var(--oslab-unseen);
}

@media (max-width: 640px) {
  .graph-demo {
    min-height: 0;
  }
}
</style>
