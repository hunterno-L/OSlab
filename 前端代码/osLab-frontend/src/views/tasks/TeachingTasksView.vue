<template>
  <div class="teaching-tasks">
    <a-card>
      <div class="toolbar">
        <a-space wrap>
          <a-input
            v-model="filters.title"
            placeholder="任务标题"
            allow-clear
            style="width: 200px"
          />
          <a-select
            v-model="filters.status"
            placeholder="状态"
            allow-clear
            style="width: 140px"
          >
            <a-option value="DRAFT">草稿</a-option>
            <a-option value="PUBLISHED">已发布</a-option>
            <a-option value="CLOSED">已关闭</a-option>
          </a-select>
          <a-select
            v-model="filters.className"
            placeholder="班级"
            allow-clear
            style="width: 160px"
          >
            <a-option
              v-for="option in classes"
              :key="option.className"
              :value="option.className"
            >
              {{ option.className }}（{{ option.studentCount }}）
            </a-option>
          </a-select>
          <a-checkbox v-model="filters.archived">查看已归档</a-checkbox>
          <a-button type="primary" @click="reload">筛选</a-button>
        </a-space>
        <a-space>
          <a-button @click="router.push('/teaching/tasks/new')"
            >新建任务</a-button
          >
          <a-button @click="load">刷新</a-button>
        </a-space>
      </div>

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
        row-key="taskCode"
        @page-change="onPageChange"
        @page-size-change="onPageSizeChange"
      >
        <template #columns>
          <a-table-column title="任务" :width="300">
            <template #cell="{ record }">
              <div class="task-cell">
                <button
                  type="button"
                  class="task-title"
                  @click="openReview(record)"
                >
                  {{ record.title }}
                </button>
                <a-tag size="small" :color="statusColor(record.status)">{{
                  statusText(record.status)
                }}</a-tag>
                <a-tag v-if="record.isArchived" size="small">已归档</a-tag>
              </div>
              <div class="task-sub">
                共 {{ record.itemCount }} 项
                <span v-if="record.scored">
                  · 满分 {{ formatNumber(record.totalMaxScore) }}</span
                >
                <span v-if="record.manualRequired"> · 含人工批阅</span>
              </div>
            </template>
          </a-table-column>
          <a-table-column title="截止时间" :width="160">
            <template #cell="{ record }">
              {{ record.dueAt ? formatTime(record.dueAt) : "不限" }}
              <a-tag v-if="record.allowLate" size="small">允许逾期</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="接收" :width="80">
            <template #cell="{ record }">{{ record.recipientCount }}</template>
          </a-table-column>
          <a-table-column title="已提交" :width="80">
            <template #cell="{ record }">{{ record.submittedCount }}</template>
          </a-table-column>
          <a-table-column title="待批阅" :width="90">
            <template #cell="{ record }">
              <a-tag
                v-if="Number(record.pendingReviewCount) > 0"
                color="orange"
              >
                {{ record.pendingReviewCount }}
              </a-tag>
              <span v-else class="muted">0</span>
            </template>
          </a-table-column>
          <a-table-column title="更新时间" :width="160">
            <template #cell="{ record }">{{
              formatTime(record.updateTime)
            }}</template>
          </a-table-column>
          <a-table-column title="操作" :width="360">
            <template #cell="{ record }">
              <a-space wrap size="mini">
                <a-button
                  v-if="record.status === 'DRAFT'"
                  size="small"
                  type="primary"
                  @click="edit(record)"
                >
                  编辑
                </a-button>
                <a-button
                  v-if="record.status === 'DRAFT'"
                  size="small"
                  @click="publish(record)"
                >
                  发布
                </a-button>
                <a-button
                  v-if="record.status === 'DRAFT'"
                  size="small"
                  status="danger"
                  @click="remove(record)"
                >
                  删除
                </a-button>
                <a-button
                  v-if="record.status === 'PUBLISHED'"
                  size="small"
                  @click="edit(record)"
                >
                  设置
                </a-button>
                <a-button
                  v-if="record.status !== 'DRAFT'"
                  size="small"
                  type="primary"
                  @click="openReview(record)"
                >
                  批阅
                </a-button>
                <a-button
                  v-if="record.status === 'PUBLISHED'"
                  size="small"
                  @click="close(record)"
                >
                  关闭
                </a-button>
                <a-button size="small" @click="duplicate(record)"
                  >复制</a-button
                >
                <a-button
                  v-if="!record.isArchived"
                  size="small"
                  @click="archive(record)"
                  >归档</a-button
                >
              </a-space>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty description="还没有创建学习任务" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Message, Modal } from "@arco-design/web-vue";
import {
  archiveTeachingTask,
  closeTeachingTask,
  deleteTeachingTask,
  duplicateTeachingTask,
  getTeacherClasses,
  listTeachingTasks,
  publishTeachingTask,
} from "@/api/tasks";

const router = useRouter();
const loading = ref(false);
const records = ref<any[]>([]);
const total = ref(0);
const current = ref(1);
const pageSize = ref(10);
const classes = ref<any[]>([]);
const filters = reactive({
  title: "",
  status: "",
  className: "",
  archived: false,
});

const load = async () => {
  loading.value = true;
  try {
    const params: Record<string, unknown> = {
      current: current.value,
      pageSize: pageSize.value,
      archived: filters.archived,
    };
    if (filters.title.trim()) params.title = filters.title.trim();
    if (filters.status) params.status = filters.status;
    if (filters.className) params.className = filters.className;
    const res = await listTeachingTasks(params);
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

const loadClasses = async () => {
  try {
    const res = await getTeacherClasses();
    if (res.data?.code === 0) classes.value = res.data.data || [];
  } catch {
    // 班级筛选失败不阻塞列表
  }
};

const reload = () => {
  current.value = 1;
  load();
};

const onPageChange = (page: number) => {
  current.value = page;
  load();
};
const onPageSizeChange = (size: number) => {
  pageSize.value = size;
  current.value = 1;
  load();
};

const edit = (record: any) =>
  router.push(`/teaching/tasks/${record.taskCode}/edit`);
const openReview = (record: any) =>
  router.push(`/teaching/tasks/${record.taskCode}/review`);

const publish = (record: any) => {
  Modal.confirm({
    title: "发布任务",
    content: "发布后将冻结内容、分值与接收名单，学生可以开始接收。",
    okText: "发布",
    onOk: async () => {
      const res = await publishTeachingTask(record.taskCode, record.rowVersion);
      if (res.data?.code === 0) {
        Message.success("任务已发布");
        await load();
      } else {
        Message.error(res.data?.message || "发布失败");
      }
    },
  });
};

const close = (record: any) => {
  Modal.confirm({
    title: "关闭任务",
    content: "关闭后停止新接收与提交，但保留查阅、下载和批阅。",
    okText: "关闭",
    onOk: async () => {
      const res = await closeTeachingTask(record.taskCode);
      if (res.data?.code === 0) {
        Message.success("任务已关闭");
        await load();
      } else {
        Message.error(res.data?.message || "关闭失败");
      }
    },
  });
};

const duplicate = async (record: any) => {
  const res = await duplicateTeachingTask(record.taskCode);
  if (res.data?.code === 0) {
    Message.success("已复制为新草稿");
    router.push(`/teaching/tasks/${res.data.data.taskCode}/edit`);
  } else {
    Message.error(res.data?.message || "复制失败");
  }
};

const archive = async (record: any) => {
  const res = await archiveTeachingTask(record.taskCode);
  if (res.data?.code === 0) {
    Message.success("已归档");
    await load();
  } else {
    Message.error(res.data?.message || "归档失败");
  }
};

const remove = (record: any) => {
  Modal.confirm({
    title: "删除草稿",
    content: "仅草稿可删除，操作不可恢复。",
    okText: "删除",
    okButtonProps: { status: "danger" },
    onOk: async () => {
      const res = await deleteTeachingTask(record.taskCode);
      if (res.data?.code === 0) {
        Message.success("草稿已删除");
        await load();
      } else {
        Message.error(res.data?.message || "删除失败");
      }
    },
  });
};

const statusText = (status?: string) =>
  ((
    { DRAFT: "草稿", PUBLISHED: "已发布", CLOSED: "已关闭" } as Record<
      string,
      string
    >
  )[status || ""] ||
  status ||
  "");
const statusColor = (status?: string) =>
  ((
    { DRAFT: "gray", PUBLISHED: "green", CLOSED: "orange" } as Record<
      string,
      string
    >
  )[status || ""] || "gray");
const formatNumber = (value: any) => {
  const number = Number(value);
  return Number.isFinite(number) ? number.toFixed(0) : "0";
};
const formatTime = (value?: string) => {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

onMounted(() => {
  load();
  loadClasses();
});
</script>

<style scoped>
.teaching-tasks :deep(.arco-card) {
  border-radius: var(--oslab-radius-lg, 8px);
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.task-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.task-title {
  border: 0;
  background: none;
  padding: 0;
  color: var(--oslab-ink, #1d2129);
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}
.task-sub {
  margin-top: 4px;
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
.muted {
  color: var(--oslab-muted-strong, #757069);
}
</style>
