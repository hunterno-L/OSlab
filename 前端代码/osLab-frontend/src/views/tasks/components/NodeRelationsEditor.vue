<template>
  <div class="node-relations">
    <div class="search-row">
      <a-select
        v-model="nodeCode"
        allow-search
        placeholder="选择知识节点"
        style="width: 360px"
      >
        <a-option
          v-for="node in nodes"
          :key="node.nodeCode"
          :value="node.nodeCode"
        >
          {{ node.name }}（{{ node.nodeCode }}）
        </a-option>
      </a-select>
      <a-button :loading="loading" :disabled="!nodeCode" @click="load"
        >读取节点与关系</a-button
      >
      <a-button
        type="primary"
        :loading="saving"
        :disabled="!nodeCode"
        @click="save"
        >保存出向关系</a-button
      >
    </div>

    <a-alert v-if="syncStatus" type="info" class="notice">
      关系已写入 MySQL，图谱同步状态：{{
        syncStatus
      }}。同步延迟时可在“资源审核”中重试。
    </a-alert>

    <template v-if="detail">
      <a-descriptions :column="2" size="small" bordered class="meta">
        <a-descriptions-item label="节点代码">{{
          detail.nodeCode
        }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{
          detail.name
        }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{
          detail.nodeType
        }}</a-descriptions-item>
        <a-descriptions-item label="归属小节">{{
          detail.homeSectionCode || "—"
        }}</a-descriptions-item>
        <a-descriptions-item label="说明" :span="2">{{
          detail.description || "—"
        }}</a-descriptions-item>
      </a-descriptions>

      <h3>出向关系（本节点 → 其他节点）</h3>
      <p class="hint">
        保存时只替换本节点的出向关系，不会删除其他节点指向本节点的入向关系。
      </p>
      <div
        v-for="(relation, index) in outgoing"
        :key="index"
        class="relation-row"
      >
        <a-select
          v-model="relation.toNodeCode"
          allow-search
          placeholder="目标节点"
          style="flex: 1"
        >
          <a-option
            v-for="node in nodes"
            :key="node.nodeCode"
            :value="node.nodeCode"
          >
            {{ node.name }}（{{ node.nodeCode }}）
          </a-option>
        </a-select>
        <a-select v-model="relation.relationType" style="width: 160px">
          <a-option value="REQUIRES">前置 REQUIRE</a-option>
          <a-option value="CONTAINS">包含 CONTAINS</a-option>
          <a-option value="BASED_ON">基于 BASED_ON</a-option>
          <a-option value="IMPLEMENTS">实现 IMPLEMENTS</a-option>
          <a-option value="RELATED_TO">相关 RELATED_TO</a-option>
          <a-option value="CONTRASTS_WITH">对比 CONTRASTS_WITH</a-option>
        </a-select>
        <a-input
          v-model="relation.reason"
          placeholder="理由（可选）"
          style="width: 220px"
        />
        <a-button
          size="small"
          status="danger"
          @click="outgoing.splice(index, 1)"
          >删除</a-button
        >
      </div>
      <a-button size="small" @click="addOutgoing">添加出向关系</a-button>

      <h3>入向关系（其他节点 → 本节点，只读）</h3>
      <a-table :data="incoming" :pagination="false" size="small">
        <template #columns>
          <a-table-column title="起点节点" data-index="fromNodeCode" />
          <a-table-column title="关系" data-index="relationType" :width="160" />
          <a-table-column title="理由" data-index="reason" />
        </template>
        <template #empty><a-empty description="没有入向关系" /></template>
      </a-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { defineProps, onMounted, ref } from "vue";
import { Message } from "@arco-design/web-vue";
import { getNodeDetail, saveNodeRelations } from "@/api/tasks";

const props = defineProps<{ nodes: any[] }>();
const nodeCode = ref("");
const loading = ref(false);
const saving = ref(false);
const detail = ref<any>(null);
const outgoing = ref<any[]>([]);
const incoming = ref<any[]>([]);
const syncStatus = ref("");

const load = async () => {
  if (!nodeCode.value) return;
  loading.value = true;
  try {
    const res = await getNodeDetail(nodeCode.value);
    if (res.data?.code === 0) {
      detail.value = res.data.data;
      outgoing.value = (res.data.data.relations || [])
        .filter((row: any) => row.fromNodeCode === nodeCode.value)
        .map((row: any) => ({ ...row }));
      incoming.value = (res.data.data.relations || []).filter(
        (row: any) => row.toNodeCode === nodeCode.value
      );
      syncStatus.value = "";
    } else {
      Message.error(res.data?.message || "节点加载失败");
    }
  } finally {
    loading.value = false;
  }
};

const save = async () => {
  const seen = new Set<string>();
  for (const relation of outgoing.value) {
    if (!relation.toNodeCode) {
      Message.warning("请选择关系目标节点");
      return;
    }
    const key = `${relation.toNodeCode}|${relation.relationType}`;
    if (seen.has(key)) {
      Message.warning("存在重复关系");
      return;
    }
    seen.add(key);
  }
  saving.value = true;
  try {
    const res = await saveNodeRelations(nodeCode.value, outgoing.value);
    if (res.data?.code === 0) {
      outgoing.value = (res.data.data.outgoing || []).map((row: any) => ({
        ...row,
      }));
      incoming.value = res.data.data.incoming || [];
      syncStatus.value = res.data.data.syncStatus || "PENDING";
      Message.success("关系已保存，图谱同步已排队");
    } else {
      Message.error(res.data?.message || "保存失败");
    }
  } finally {
    saving.value = false;
  }
};

const addOutgoing = () => {
  outgoing.value.push({ toNodeCode: "", relationType: "REQUIRES", reason: "" });
};

onMounted(() => {
  if (props.nodes?.length) nodeCode.value = props.nodes[0].nodeCode;
});
</script>

<style scoped>
.node-relations {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.search-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.notice {
  margin-top: 4px;
}
.meta {
  margin-top: 8px;
}
.relation-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
h3 {
  margin: 14px 0 6px;
  font-size: 15px;
}
.hint {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
  margin: 0 0 8px;
}
</style>
