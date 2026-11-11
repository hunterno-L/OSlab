<template>
  <div v-if="task" class="task-review">
    <a-card class="review-head">
      <div class="head-top">
        <a-button size="small" @click="backToTasks">返回任务列表</a-button>
        <a-space>
          <a-tag :color="task.taskStatus === 'PUBLISHED' ? 'green' : 'orange'">
            {{
              task.taskStatus === "PUBLISHED"
                ? "已发布"
                : task.taskStatus === "CLOSED"
                ? "已关闭"
                : "草稿"
            }}
          </a-tag>
          <a-button
            size="small"
            @click="
              router.push({
                path: `/teaching/tasks/${task.taskCode}/edit`,
                query: taskContextQuery,
              })
            "
          >
            任务设置
          </a-button>
        </a-space>
      </div>
      <h1 class="task-title">{{ task.title }}</h1>
      <div class="task-meta">
        <span v-if="taskContextQuery.className"
          >当前班级：{{ taskContextQuery.className }} ·
        </span>
        截止时间：{{ task.dueAt ? formatTime(task.dueAt) : "不限" }}
        <span class="muted"> · 共 {{ task.items?.length || 0 }} 个内容块</span>
        <span v-if="task.publishedPolicy?.manualRequired" class="muted">
          · 含人工批阅</span
        >
      </div>
    </a-card>

    <div class="stat-grid">
      <div v-for="stat in statsCards" :key="stat.key" class="stat-card">
        <span class="stat-value">{{ statsReady ? stat.value : "—" }}</span>
        <span class="stat-label">{{ stat.label }}</span>
      </div>
    </div>

    <a-card>
      <template #title>
        <div class="list-title">
          <span>接收人与提交情况</span>
          <a-checkbox v-model="pendingOnly" @change="onReviewFilterChange"
            >仅看待批阅</a-checkbox
          >
          <a-button size="small" :loading="loading" @click="loadRecipients"
            >刷新</a-button
          >
        </div>
      </template>
      <div v-if="recipientsError" class="oslab-error" role="alert">
        <span>{{ recipientsError }}</span>
        <a-button size="small" @click="loadRecipients">重试</a-button>
      </div>
      <a-table
        :data="recipients"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1090 }"
        row-key="recipientId"
        @page-change="onRecipientPageChange"
        @page-size-change="onRecipientPageSizeChange"
      >
        <template #columns>
          <a-table-column title="学生" :width="180">
            <template #cell="{ record }">
              <div class="student-name">{{ record.name }}</div>
              <div class="muted">{{ record.studentNumber }}</div>
            </template>
          </a-table-column>
          <a-table-column title="班级" data-index="className" :width="140" />
          <a-table-column title="状态" :width="110">
            <template #cell="{ record }">
              <a-tag :color="statusColor(record.status)">{{
                statusText(record.status)
              }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="轮次" :width="70">
            <template #cell="{ record }">{{ record.roundNo || "—" }}</template>
          </a-table-column>
          <a-table-column title="提交时间" :width="160">
            <template #cell="{ record }">
              {{ record.submittedAt ? formatTime(record.submittedAt) : "—" }}
              <a-tag v-if="record.isLate" size="small" color="red">逾期</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="自动得分" :width="100">
            <template #cell="{ record }">{{
              record.autoScore ?? "—"
            }}</template>
          </a-table-column>
          <a-table-column title="总分" :width="100">
            <template #cell="{ record }">
              <template v-if="record.reviewStatus === 'SUBMITTED'">
                <a-tag color="orange">待批阅</a-tag>
              </template>
              <template v-else>{{ record.totalScore ?? "—" }}</template>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="130">
            <template #cell="{ record }">
              <a-button
                v-if="record.latestSubmissionId"
                size="small"
                type="primary"
                @click="openSubmission(record.latestSubmissionId)"
              >
                查看提交
              </a-button>
              <span v-else class="muted">未提交</span>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty
            :description="
              recipientsError
                ? '提交情况尚未加载成功'
                : pendingOnly
                ? '暂无待批阅提交'
                : '还没有接收人'
            "
          />
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:visible="drawerVisible"
      width="min(860px, 100vw)"
      :footer="false"
      title="提交详情与批阅"
    >
      <a-spin :loading="detailLoading" style="width: 100%">
        <template v-if="detail">
          <div class="detail-head">
            <div>
              <strong>{{ detail.student?.name }}</strong>
              <span class="muted">
                {{ detail.student?.studentNumber }} ·
                {{ detail.student?.className }}
              </span>
            </div>
            <a-space>
              <a-tag :color="statusColor(detail.status)">{{
                statusText(detail.status)
              }}</a-tag>
              <a-tag v-if="detail.isLate" color="red">逾期</a-tag>
            </a-space>
          </div>
          <a-descriptions :column="3" size="small" class="detail-meta">
            <a-descriptions-item label="轮次"
              >第 {{ detail.roundNo }} 轮</a-descriptions-item
            >
            <a-descriptions-item label="提交时间">{{
              formatTime(detail.submittedAt)
            }}</a-descriptions-item>
            <a-descriptions-item label="自动得分">{{
              detail.autoScore ?? "—"
            }}</a-descriptions-item>
          </a-descriptions>

          <div
            v-for="entry in detail.items || []"
            :key="entry.itemCode"
            class="submission-item"
          >
            <div class="submission-head">
              <strong>{{ entry.title }}</strong>
              <a-tag size="small">{{ typeText(entry.itemType) }}</a-tag>
              <a-tag v-if="entry.manual" size="small" color="orange"
                >人工批阅</a-tag
              >
              <span
                v-if="entry.maxScore !== null && entry.maxScore !== undefined"
                class="muted"
              >
                满分 {{ entry.maxScore }}
              </span>
              <span v-if="entry.autoScore !== undefined" class="muted">
                自动 {{ entry.autoScore }}
              </span>
            </div>
            <div v-if="entry.answered === false" class="muted">未作答</div>
            <div v-if="entry.answer !== undefined" class="line">
              <span class="label">作答</span>
              <code>{{ renderAnswer(entry.answer) }}</code>
            </div>
            <div v-if="entry.correct !== undefined" class="line">
              <span class="label">结果</span>
              <a-tag :color="entry.correct ? 'green' : 'red'">{{
                entry.correct ? "正确" : "错误"
              }}</a-tag>
              <span v-if="entry.analysis" class="muted"
                >解析：{{ entry.analysis }}</span
              >
            </div>
            <div v-if="entry.passed !== undefined" class="line">
              <span class="label">实验</span>
              <a-tag :color="entry.passed ? 'green' : 'red'">{{
                entry.passed ? "通过" : "未通过"
              }}</a-tag>
              <span v-if="entry.judgeInfo?.message" class="muted">{{
                entry.judgeInfo.message
              }}</span>
            </div>
            <div v-if="entry.textResponse" class="line">
              <span class="label">文字</span>
              <span class="pre-wrap">{{ entry.textResponse }}</span>
            </div>
            <div v-if="(entry.files || []).length" class="line">
              <span class="label">文件</span>
              <a
                v-for="file in entry.files"
                :key="file.fileId"
                class="file-link"
                :href="file.downloadPath"
                target="_blank"
                rel="noopener"
              >
                {{ file.originalName }}
              </a>
            </div>
            <div v-if="entry.code?.code" class="line">
              <span class="label">代码</span>
              <pre class="code-block">{{ entry.code.code }}</pre>
            </div>
            <div v-if="entry.manual" class="manual-grade">
              <a-space>
                <span class="label">评分</span>
                <a-input-number
                  v-model="reviewScores[entry.itemCode]"
                  :min="0"
                  :max="Number(entry.maxScore || 0)"
                  :precision="1"
                  placeholder="分数"
                />
                <span class="muted">/ {{ entry.maxScore }}</span>
              </a-space>
              <a-textarea
                v-model="reviewComments[entry.itemCode]"
                placeholder="评语（可选）"
                :auto-size="{ minRows: 1, maxRows: 3 }"
              />
            </div>
          </div>

          <div class="overall">
            <div class="field-label">总体评语</div>
            <a-textarea v-model="overallComment" :auto-size="{ minRows: 2 }" />
          </div>

          <div class="drawer-actions">
            <a-button
              v-if="detail.status === 'SUBMITTED'"
              type="primary"
              :loading="reviewing"
              @click="submitReview"
            >
              提交批阅
            </a-button>
            <a-button
              v-if="
                detail.status === 'SUBMITTED' || detail.status === 'REVIEWED'
              "
              status="warning"
              @click="returnVisible = true"
            >
              退回修改
            </a-button>
            <a-button @click="drawerVisible = false">关闭</a-button>
          </div>
        </template>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:visible="returnVisible"
      title="退回修改"
      :confirm-loading="returning"
      @ok="submitReturn"
    >
      <a-form layout="vertical">
        <a-form-item label="退回原因（必填）" required>
          <a-textarea v-model="returnForm.reason" :auto-size="{ minRows: 3 }" />
        </a-form-item>
        <a-form-item label="新的截止时间（任务已超期且禁止逾期时必填）">
          <a-date-picker
            v-model="returnForm.dueAt"
            show-time
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
  <div v-else-if="taskError" class="oslab-error" role="alert">
    <span>{{ taskError }}</span>
    <a-button @click="reloadReview">重试</a-button>
    <a-button @click="backToTasks">返回任务列表</a-button>
  </div>
  <div v-else class="oslab-loading" role="status">正在加载任务…</div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import {
  getTeacherSubmission,
  getTeachingTask,
  listTaskRecipients,
  returnTaskSubmission,
  reviewTaskSubmission,
  type TaskDetail,
} from "@/api/tasks";

const route = useRoute();
const router = useRouter();
const taskContextQuery = computed(() => ({
  className:
    typeof route.query.className === "string"
      ? route.query.className
      : undefined,
  courseCode:
    typeof route.query.courseCode === "string"
      ? route.query.courseCode
      : undefined,
}));
const backToTasks = () =>
  router.push({ path: "/teaching/tasks", query: taskContextQuery.value });
const pendingOnly = ref(route.query.pendingReview === "1");
const recipientsError = ref("");
let recipientSeq = 0;
const taskCode = computed(() => String(route.params.taskCode || ""));
const task = ref<TaskDetail | null>(null);
const taskError = ref("");
let taskSeq = 0;
const loading = ref(false);
const recipients = ref<any[]>([]);
const stats = ref<any>({});
const statsReady = ref(false);
const current = ref(1);
const pageSize = ref(20);
const total = ref(0);
const drawerVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<any>(null);
const reviewScores = reactive<Record<string, number | undefined>>({});
const reviewComments = reactive<Record<string, string>>({});
const overallComment = ref("");
const reviewing = ref(false);
const returning = ref(false);
const returnVisible = ref(false);
const returnForm = reactive<{ reason: string; dueAt?: string }>({
  reason: "",
  dueAt: undefined,
});

const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showTotal: true,
  showPageSize: true,
  pageSizeOptions: [10, 20, 50, 100],
}));

const statsCards = computed(() => [
  { key: "total", label: "接收人数", value: stats.value.total || 0 },
  { key: "pending", label: "待接收", value: stats.value.pending || 0 },
  {
    key: "progress",
    label: "进行中",
    value:
      Number(stats.value.inProgress || 0) + Number(stats.value.returned || 0),
  },
  { key: "submitted", label: "待批阅", value: stats.value.submitted || 0 },
  { key: "reviewed", label: "已批阅", value: stats.value.reviewed || 0 },
]);

const loadTask = async () => {
  const seq = ++taskSeq;
  task.value = null;
  taskError.value = "";
  try {
    const res = await getTeachingTask(taskCode.value);
    if (seq !== taskSeq) return false;
    if (res.data?.code !== 0)
      throw new Error(res.data?.message || "任务加载失败");
    task.value = res.data.data;
    return true;
  } catch (error) {
    if (seq === taskSeq)
      taskError.value =
        error instanceof Error ? error.message : "任务加载失败，请重试";
    return false;
  }
};

const loadRecipients = async () => {
  const seq = ++recipientSeq;
  loading.value = true;
  recipientsError.value = "";
  recipients.value = [];
  total.value = 0;
  stats.value = {};
  statsReady.value = false;
  try {
    const res = await listTaskRecipients(taskCode.value, {
      current: current.value,
      pageSize: pageSize.value,
      pendingReview: pendingOnly.value,
      className: taskContextQuery.value.className,
    });
    if (seq !== recipientSeq) return;
    if (res.data?.code === 0) {
      recipients.value = res.data.data?.records || [];
      total.value = Number(res.data.data?.total || 0);
      stats.value = res.data.data?.stats || {};
      statsReady.value = true;
    } else {
      recipientsError.value = res.data?.message || "提交情况加载失败，请重试";
    }
  } catch {
    if (seq === recipientSeq)
      recipientsError.value = "提交情况加载失败，请检查网络后重试";
  } finally {
    if (seq === recipientSeq) loading.value = false;
  }
};

const onReviewFilterChange = () => {
  current.value = 1;
  recipients.value = [];
  router.replace({
    query: {
      ...route.query,
      pendingReview: pendingOnly.value ? "1" : undefined,
    },
  });
};
const onRecipientPageChange = (page: number) => {
  current.value = page;
  loadRecipients();
};
const onRecipientPageSizeChange = (size: number) => {
  pageSize.value = size;
  current.value = 1;
  loadRecipients();
};

const openSubmission = async (id: string) => {
  drawerVisible.value = true;
  detailLoading.value = true;
  try {
    const res = await getTeacherSubmission(taskCode.value, id);
    if (res.data?.code === 0) {
      detail.value = res.data.data;
      overallComment.value = res.data.data.review?.overallComment || "";
      for (const key of Object.keys(reviewScores)) delete reviewScores[key];
      for (const key of Object.keys(reviewComments)) delete reviewComments[key];
      const existing = res.data.data.review?.items || [];
      for (const item of res.data.data.items || []) {
        if (!item.manual) continue;
        const previous = existing.find(
          (row: any) => row.itemCode === item.itemCode
        );
        reviewScores[item.itemCode] = previous
          ? Number(previous.score)
          : undefined;
        reviewComments[item.itemCode] = previous?.comment || "";
      }
    } else {
      Message.error(res.data?.message || "提交详情加载失败");
    }
  } finally {
    detailLoading.value = false;
  }
};

const submitReview = async () => {
  if (!detail.value) return;
  const items = [];
  for (const item of detail.value.items || []) {
    if (!item.manual) continue;
    const score = reviewScores[item.itemCode];
    if (score === undefined || score === null) {
      Message.warning(`请为「${item.title}」填写分数`);
      return;
    }
    items.push({
      itemCode: item.itemCode,
      score,
      comment: reviewComments[item.itemCode] || "",
    });
  }
  reviewing.value = true;
  try {
    const res = await reviewTaskSubmission(
      taskCode.value,
      detail.value.submissionId,
      {
        items,
        overallComment: overallComment.value,
      }
    );
    if (res.data?.code === 0) {
      detail.value = res.data.data;
      Message.success("批阅已保存");
      await loadRecipients();
    } else {
      Message.error(res.data?.message || "批阅失败");
    }
  } finally {
    reviewing.value = false;
  }
};

const submitReturn = async () => {
  if (!detail.value) return;
  if (!returnForm.reason.trim()) {
    Message.warning("请填写退回原因");
    return;
  }
  returning.value = true;
  try {
    const res = await returnTaskSubmission(
      taskCode.value,
      detail.value.submissionId,
      {
        reason: returnForm.reason.trim(),
        dueAt: returnForm.dueAt || null,
      }
    );
    if (res.data?.code === 0) {
      detail.value = res.data.data;
      returnVisible.value = false;
      returnForm.reason = "";
      returnForm.dueAt = undefined;
      Message.success("已退回，学生可重新提交");
      await loadRecipients();
    } else {
      Message.error(res.data?.message || "退回失败");
    }
  } finally {
    returning.value = false;
  }
};

const typeText = (type?: string) =>
  ((
    {
      TEXT: "文字",
      MATERIAL: "资料",
      QUESTION: "客观题",
      EXPERIMENT: "实验",
    } as Record<string, string>
  )[type || ""] ||
  type ||
  "");
const statusText = (status?: string) =>
  ((
    {
      PENDING: "待接收",
      IN_PROGRESS: "进行中",
      RETURNED: "退回修改",
      SUBMITTED: "待批阅",
      REVIEWED: "已批阅",
    } as Record<string, string>
  )[status || ""] ||
  status ||
  "");
const statusColor = (status?: string) =>
  ((
    {
      PENDING: "blue",
      IN_PROGRESS: "arcoblue",
      RETURNED: "orange",
      SUBMITTED: "gold",
      REVIEWED: "green",
    } as Record<string, string>
  )[status || ""] || "gray");
const formatTime = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const renderAnswer = (value: any) => {
  if (value === null || value === undefined) return "—";
  return typeof value === "string" ? value : JSON.stringify(value);
};

const reloadReview = async () => {
  recipientSeq++;
  statsReady.value = false;
  if (await loadTask()) await loadRecipients();
};
watch(
  () => [taskCode.value, route.query.className, route.query.pendingReview],
  () => {
    pendingOnly.value = route.query.pendingReview === "1";
    current.value = 1;
    drawerVisible.value = false;
    detail.value = null;
    void reloadReview();
  },
  { immediate: true }
);
</script>

<style scoped>
.task-review {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.review-head {
  border-radius: var(--oslab-radius-lg, 8px);
}
.head-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.task-title {
  margin: 12px 0 4px;
  font-size: 22px;
  font-weight: 600;
}
.task-meta {
  color: var(--oslab-ink-soft, #37352f);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
.stat-card {
  padding: 14px 16px;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: var(--oslab-radius-lg, 8px);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
}
.stat-label {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
.list-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.student-name {
  font-weight: 600;
}
.muted {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.detail-meta {
  margin-bottom: 10px;
}
.submission-item {
  border-top: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.08));
  padding: 12px 0;
}
.submission-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.line {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 6px;
}
.line .label,
.manual-grade .label {
  flex: 0 0 42px;
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
  padding-top: 6px;
}
.pre-wrap {
  white-space: pre-wrap;
}
.code-block {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 10px;
  border-radius: 6px;
  font-size: 12px;
  flex: 1;
}
.file-link {
  margin-right: 10px;
  color: var(--oslab-primary, #2447d8);
}
.manual-grade {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.manual-grade :deep(.arco-textarea) {
  flex: 1;
}
.overall {
  margin-top: 14px;
}
.field-label {
  font-weight: 600;
  margin-bottom: 6px;
}
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.08));
}
@media (max-width: 1000px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 480px) {
  .head-top,
  .detail-head,
  .manual-grade {
    flex-wrap: wrap;
  }
  .task-title {
    overflow-wrap: anywhere;
  }
  .task-review :deep(.arco-btn) {
    min-height: 44px;
  }
}
</style>
