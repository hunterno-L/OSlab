<template>
  <section class="entry-ledger" aria-labelledby="entry-ledger-title">
    <h2 id="entry-ledger-title">全部入口</h2>
    <router-link
      v-for="entry in visibleEntries"
      :key="entry.path"
      class="entry-row"
      :to="entry.path"
    >
      <span class="entry-label">{{ entry.label }}</span>
      <span class="entry-desc">{{ entry.description }}</span>
      <span class="entry-arrow" aria-hidden="true">→</span>
    </router-link>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";

interface Entry {
  label: string;
  description: string;
  path: string;
  studentOnly?: boolean;
}

// eslint-disable-next-line no-undef
const props = defineProps<{ isStudent?: boolean }>();

const entries: Entry[] = [
  {
    label: "课程学习",
    description: "图谱、单元与当前进度",
    path: "/graph",
  },
  {
    label: "学习任务",
    description: "待办、批阅反馈与提交记录",
    path: "/tasks",
    studentOnly: true,
  },
  {
    label: "题目练习",
    description: "按知识点筛选、作答与复习",
    path: "/assessments",
  },
  {
    label: "编程实验",
    description: "写代码、提交判题、查看记录",
    path: "/experiments",
  },
  {
    label: "算法模拟",
    description: "CPU 调度、内存分配、页面置换、磁盘调度",
    path: "/experiments/simulation",
  },
  {
    label: "学习资源",
    description: "课程资料与参考链接",
    path: "/resources",
  },
];

const visibleEntries = computed(() =>
  entries.filter((entry) => !entry.studentOnly || props.isStudent)
);
</script>

<style scoped>
.entry-ledger {
  padding: 28px 0 8px;
  border-top: 1px solid var(--oslab-line);
}

.entry-ledger h2 {
  margin: 0 0 8px;
  color: var(--oslab-ink);
  font-size: 18px;
  line-height: 1.4;
}

.entry-row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) 24px;
  gap: 16px;
  align-items: center;
  padding: 15px 8px;
  border-bottom: 1px solid var(--oslab-line);
  color: inherit;
  text-decoration: none;
}

.entry-row:hover {
  background: var(--oslab-panel-soft);
}

.entry-row:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: -2px;
}

.entry-label {
  color: var(--oslab-ink);
  font-size: 15px;
  font-weight: 600;
}

.entry-desc {
  color: var(--oslab-muted-strong);
  font-size: 13px;
  line-height: 1.6;
}

.entry-arrow {
  color: var(--oslab-muted-strong);
  font-size: 15px;
  text-align: right;
}

.entry-row:hover .entry-arrow {
  color: var(--oslab-primary);
}

@media (max-width: 640px) {
  .entry-row {
    grid-template-columns: minmax(0, 1fr) 24px;
    gap: 4px 12px;
    padding: 14px 6px;
  }

  .entry-desc {
    grid-row: 2;
    grid-column: 1;
  }
}
</style>
