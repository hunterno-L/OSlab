<template>
  <ul class="evidence-ledger">
    <li v-for="row in rows" :key="row.name">
      <strong>{{ row.name }}</strong>
      <span>{{ row.meaning }}</span>
      <em>{{ row.source }}</em>
    </li>
  </ul>
</template>

<script setup lang="ts">
const rows = [
  {
    name: "活动进度",
    meaning: "是否阅读、运行、提交或完成指定活动",
    source: "真实活动行为",
  },
  {
    name: "单元进度",
    meaning: "必需活动完成比例与当前位置",
    source: "活动进度聚合",
  },
  {
    name: "达标情况",
    meaning: "单元检查点是否通过、建议复习什么",
    source: "服务端测评或判题",
  },
  {
    name: "节点掌握",
    meaning: "累积的概念与实践证据",
    source: "学习事件与 BKT",
  },
];
</script>

<style scoped>
.evidence-ledger {
  margin: 0;
  padding: 0;
  list-style: none;
}

.evidence-ledger li {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr) 150px;
  gap: 20px;
  align-items: start;
  padding: 16px 0;
  border-top: 1px solid var(--oslab-line);
}

.evidence-ledger li:last-child {
  border-bottom: 1px solid var(--oslab-line);
}

.evidence-ledger strong {
  color: var(--oslab-ink);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
}

.evidence-ledger span {
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.evidence-ledger em {
  color: var(--oslab-muted-strong);
  font-size: 12px;
  font-style: normal;
  line-height: 1.7;
  text-align: right;
}

@media (max-width: 640px) {
  .evidence-ledger li {
    grid-template-columns: 1fr;
    gap: 4px;
    padding: 14px 0;
  }

  .evidence-ledger em {
    text-align: left;
  }
}
</style>
