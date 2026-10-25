<template>
  <section class="registry-panel">
    <h2>来源、词表与资源登记</h2>
    <p>
      读取已登记项目并编辑 JSON。资源启用需填写已核验的适配器、参数和
      runtimeVerified；保存后服务端重新校验。
    </p>
    <div class="controls">
      <a-select v-model="kind" @change="reset"
        ><a-option value="sources">教材来源</a-option
        ><a-option value="nodes">节点词表</a-option
        ><a-option value="resources">模拟与实验资源</a-option></a-select
      ><a-select
        v-model="code"
        allow-search
        placeholder="选择已登记项目"
        @change="read"
        ><a-option
          v-for="entry in entries"
          :key="entryCode(entry)"
          :value="entryCode(entry)"
          >{{ entry.title || entry.name || entryCode(entry) }} ·
          {{ entry.reviewStatus || entry.status || "已登记" }}</a-option
        ></a-select
      ><a-button :disabled="!code" :loading="busy" @click="read">读取</a-button>
    </div>
    <a-textarea
      v-if="content"
      v-model="content"
      :auto-size="{ minRows: 10, maxRows: 30 }"
      aria-label="登记资源 JSON"
    /><a-button v-if="content" type="primary" :loading="busy" @click="save"
      >校验并保存登记</a-button
    >
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <h2>图谱同步</h2>
    <div class="controls">
      <a-button :loading="busy" @click="sync(false)">查看同步状态</a-button
      ><a-button :loading="busy" @click="sync(true)">重试同步图谱</a-button>
    </div>
    <p v-if="syncState">
      {{ syncState.status }} · 待同步 {{ syncState.pendingCount }} · 图谱{{
        syncState.graphAvailable ? "可用" : "不可用"
      }}
      · 唯一约束{{ syncState.constraintReady ? "已就绪" : "未就绪" }}
    </p>
    <details v-if="syncState">
      <summary>同步批次详情</summary>
      <pre>{{ JSON.stringify(syncState.runs, null, 2) }}</pre>
    </details>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { courseRequest } from "@/api/course";
// eslint-disable-next-line no-undef
const props = defineProps<{
  catalog: Record<string, unknown>;
  courseCode: string;
}>();
// eslint-disable-next-line no-undef
const emit = defineEmits(["changed"]);
const kind = ref("sources"),
  code = ref(""),
  content = ref(""),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const syncState = ref<{
  status: string;
  pendingCount: number;
  graphAvailable: boolean;
  constraintReady: boolean;
  runs: unknown;
} | null>(null);
const entries = computed(
  () => (props.catalog[kind.value] || []) as Record<string, unknown>[]
);
const entryCode = (entry: Record<string, unknown>) =>
  String(
    entry[
      { sources: "sourceCode", nodes: "nodeCode", resources: "resourceCode" }[
        kind.value
      ] || "code"
    ] || ""
  );
function reset() {
  code.value = "";
  content.value = "";
}
async function run(action: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await action();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败";
  } finally {
    busy.value = false;
  }
}
const read = () =>
  run(async () => {
    if (!code.value) return;
    content.value = JSON.stringify(
      await courseRequest(
        `teaching/content/${kind.value}/${encodeURIComponent(code.value)}`
      ),
      null,
      2
    );
  });
const save = () =>
  run(async () => {
    const data = JSON.parse(content.value);
    if (kind.value === "nodes" && !data.courseCode)
      data.courseCode = props.courseCode;
    await courseRequest(
      `teaching/content/${kind.value}/${encodeURIComponent(code.value)}`,
      "put",
      data
    );
    notice.value = "登记已保存";
    emit("changed");
  });
const sync = (retry: boolean) =>
  run(async () => {
    syncState.value = await courseRequest(
      "teaching/content/graph-sync",
      retry ? "post" : "get",
      retry ? {} : undefined
    );
  });
onMounted(() => sync(false));
</script>
<style scoped>
.registry-panel {
  padding: 24px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: white;
  margin: 18px 0;
}
.controls {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 16px 0;
}
.arco-select {
  max-width: 440px;
}
h2 {
  font-size: 18px;
}
p {
  color: #4e5969;
  line-height: 1.7;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
}
</style>
