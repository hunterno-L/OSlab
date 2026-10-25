<template>
  <div class="my-tasks">
    <div class="task-summary">
      <button
        v-for="card in summaryCards"
        :key="card.key"
        type="button"
        class="summary-card"
        :class="{ active: filter === card.filter }"
        @click="switchFilter(card.filter)"
      >
        <span class="summary-value">{{ card.value }}</span>
        <span class="summary-label">{{ card.label }}</span>
      </button>
    </div>

    <a-card class="task-list-card">
      <template #title>
        <div class="list-title">
          <span>我的学习任务</span>
          <a-input-search
            v-model="keyword"
            aria-label="搜索任务标题"
            :input-attrs="{ 'aria-label': '搜索任务标题' }"
            placeholder="搜索任务标题"
            style="width: 240px"
            allow-clear
            @search="reload"
          />
        </div>
      </template>
      <a-table
        :data="records"
        :loading="loading"
        :pagination="{
          current,
          pageSize,
          total,
          showTotal: true,
          showPageSize: true,
        }"
        @page-change="onPageChange"
        @page-size-change="onPageSizeChange"
        row-key="taskCode"
      >
        <template #columns>
          <a-table-column title="任务" :width="360">
            <template #cell="{ record }">
              <router-link class="task-link" :to="`/tasks/${record.taskCode}`">
                <span class="task-title">{{ record.title }}</span>
                <span class="task-desc">{{ record.description || "—" }}</span>
              </router-link>
            </template>
          </a-table-column>
          <a-table-column title="组成" :width="230">
            <template #cell="{ record }">
              <a-space wrap size="mini">
                <a-tag
                  v-for="label in compositionLabels(record)"
                  :key="label"
                  size="small"
                >
                  {{ label }}
                </a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="截止时间" :width="170">
            <template #cell="{ record }">
              <span :class="{ 'text-danger': record.overdue }">
                {{ record.dueAt ? formatTime(record.dueAt) : "不限" }}
              </span>
              <a-tag v-if="record.overdue" size="small" color="red" class="ml-4"
                >逾期</a-tag
              >
            </template>
          </a-table-column>
          <a-table-column title="状态" :width="110">
            <template #cell="{ record }">
              <a-tag :color="statusColor(record.status)">{{
                statusText(record.status)
              }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="成绩" :width="140">
            <template #cell="{ record }">
              <template v-if="record.submissionStatus === 'REVIEWED'">
                <strong v-if="record.scored">
                  {{ formatScore(record.totalScore, record.totalMaxScore) }}
                </strong>
                <a-tag v-else color="green">已完成</a-tag>
              </template>
              <template v-else-if="record.submissionStatus === 'SUBMITTED'">
                <a-tag color="orange">待批阅</a-tag>
              </template>
              <template v-else-if="record.scored">
                <span class="muted"
                  >满分 {{ formatNumber(record.totalMaxScore) }}</span
                >
              </template>
              <span v-else class="muted">不计分</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120">
            <template #cell="{ record }">
              <a-button size="small" type="primary" @click="openTask(record)">
                {{ record.status === "PENDING" ? "查看并接收" : "打开任务" }}
              </a-button>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty description="暂时没有学习任务" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import { getMyTasks, getTaskSummary } from "@/api/tasks";
import { formatDateTime } from "@/utils/dateTime";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const records = ref<any[]>([]);
const current = ref(1);
const pageSize = ref(10);
const total = ref(0);
const keyword = ref("");
const filter = ref("ALL");
const summary = ref<any>({
  pending: 0,
  inProgress: 0,
  submitted: 0,
  reviewed: 0,
});

const summaryCards = computed(() => [
  {
    key: "pending",
    label: "待接收",
    value: summary.value.pending || 0,
    filter: "PENDING",
  },
  {
    key: "progress",
    label: "进行中",
    value: summary.value.inProgress || 0,
    filter: "IN_PROGRESS",
  },
  {
    key: "submitted",
    label: "待批阅",
    value: summary.value.submitted || 0,
    filter: "SUBMITTED",
  },
  {
    key: "reviewed",
    label: "已批阅",
    value: summary.value.reviewed || 0,
    filter: "REVIEWED",
  },
]);

const loadSummary = async () => {
  try {
    const res = await getTaskSummary();
    if (res.data?.code === 0) summary.value = res.data.data || summary.value;
  } catch {
    // 首页摘要失败不影响任务列表
  }
};

const load = async () => {
  loading.value = true;
  try {
    const params: Record<string, unknown> = {
      current: current.value,
      pageSize: pageSize.value,
    };
    if (filter.value !== "ALL") params.status = filter.value;
    if (keyword.value.trim()) params.keyword = keyword.value.trim();
    const res = await getMyTasks(params);
    if (res.data?.code === 0) {
      records.value = res.data.data?.records || [];
      total.value = Number(res.data.data?.total || 0);
    } else {
      Message.error(res.data?.message || "任务列表加载失败");
    }
  } catch {
    Message.error("任务列表加载失败");
  } finally {
    loading.value = false;
  }
};

const queryText = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const replaceQuery = (patch: Record<string, string | undefined>) => {
  const next: Record<string, string> = {};
  for (const [key, value] of Object.entries({ ...route.query, ...patch })) {
    const text = queryText(value);
    if (text) next[key] = text;
  }
  const changed = JSON.stringify(next) !== JSON.stringify(route.query);
  if (changed) void router.replace({ query: next });
  else load();
};

const syncFromRoute = () => {
  filter.value = queryText(route.query.status) || "ALL";
  keyword.value = queryText(route.query.keyword);
  const currentValue = Number(queryText(route.query.current));
  current.value =
    Number.isFinite(currentValue) && currentValue > 0 ? currentValue : 1;
  const sizeValue = Number(queryText(route.query.pageSize));
  pageSize.value = Number.isFinite(sizeValue) && sizeValue > 0 ? sizeValue : 10;
  load();
};

const reload = () => {
  current.value = 1;
  replaceQuery({
    keyword: keyword.value.trim() || undefined,
    current: undefined,
  });
  loadSummary();
};

const switchFilter = (value: string) => {
  filter.value = value;
  current.value = 1;
  replaceQuery({
    status: value === "ALL" ? undefined : value,
    current: undefined,
  });
};

const onPageChange = (page: number) => {
  current.value = page;
  replaceQuery({ current: page > 1 ? String(page) : undefined });
};

const onPageSizeChange = (size: number) => {
  pageSize.value = size;
  current.value = 1;
  replaceQuery({
    pageSize: size === 10 ? undefined : String(size),
    current: undefined,
  });
};

watch(() => route.query, syncFromRoute, { deep: true });

const openTask = (record: any) => {
  router.push(`/tasks/${record.taskCode}`);
};

const compositionLabels = (record: any) => {
  const labels: Record<string, string> = {
    TEXT: "文字",
    MATERIAL: "资料",
    QUESTION: "题目",
    EXPERIMENT: "实验",
  };
  return Object.entries(record.composition || {}).map(
    ([key, count]) => `${labels[key] || key} × ${count}`
  );
};

const statusText = (status?: string) => {
  const map: Record<string, string> = {
    PENDING: "待接收",
    IN_PROGRESS: "进行中",
    RETURNED: "退回修改",
    SUBMITTED: "待批阅",
    REVIEWED: "已批阅",
  };
  return map[status || ""] || status || "—";
};

const statusColor = (status?: string) => {
  const map: Record<string, string> = {
    PENDING: "blue",
    IN_PROGRESS: "arcoblue",
    RETURNED: "orange",
    SUBMITTED: "gold",
    REVIEWED: "green",
  };
  return map[status || ""] || "gray";
};

const formatTime = (value?: string) => formatDateTime(value);

const numberFormatter = new Intl.NumberFormat("zh-CN");
const formatNumber = (value: any) => {
  const number = Number(value);
  return Number.isFinite(number) ? numberFormatter.format(number) : "—";
};

const formatScore = (score: any, max: any) => {
  if (score === null || score === undefined) return "—";
  return `${formatNumber(score)} / ${formatNumber(max)}`;
};

onMounted(() => {
  loadSummary();
  syncFromRoute();
});
</script>

<style scoped>
.my-tasks {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.task-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  padding: 16px 18px;
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: var(--oslab-radius-lg, 8px);
  background: var(--oslab-panel, #fff);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color 150ms ease, background 150ms ease;
}
.summary-card:hover,
.summary-card.active {
  border-color: var(--oslab-primary, #2447d8);
  background: var(--oslab-primary-soft, #eaf0fd);
}
.summary-value {
  font-size: 26px;
  font-weight: 600;
  color: var(--oslab-ink, #1d2129);
}
.summary-label {
  font-size: 13px;
  color: var(--oslab-muted-strong, #757069);
}
.task-list-card {
  border-radius: var(--oslab-radius-lg, 8px);
}
.list-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.task-link {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 0;
  background: none;
  padding: 0;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  text-decoration: none;
}
.task-title {
  font-weight: 600;
  color: var(--oslab-ink, #1d2129);
}
.task-desc {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
.text-danger {
  color: var(--oslab-weak-text);
}
.muted {
  color: var(--oslab-muted-strong, #757069);
}
.ml-4 {
  margin-left: 4px;
}
/* C+B 学习图册 */
.task-summary {
  gap: 12px;
}
.summary-card {
  padding: 16px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
  text-align: left;
}
.summary-card.active {
  background: var(--oslab-highlight-soft);
  border-color: var(--oslab-highlight);
}
.summary-value {
  font-family: var(--oslab-font-mono);
  font-size: 30px;
  color: var(--oslab-ink);
}
.summary-label {
  color: var(--oslab-muted-strong);
}
.task-title {
  color: var(--oslab-ink);
  font-weight: 600;
}
.task-desc {
  color: var(--oslab-muted-strong);
}

@media (max-width: 900px) {
  .task-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .list-title {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
