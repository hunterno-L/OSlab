<template>
  <section class="classes-page">
    <div class="class-actions">
      <a-input
        v-model="classCode"
        allow-clear
        placeholder="输入班级邀请码"
        aria-label="班级邀请码"
        @press-enter="joinClass"
      />
      <a-button type="primary" :loading="joining" @click="joinClass">
        加入班级
      </a-button>
    </div>

    <a-spin :loading="loading" class="classes-content">
      <a-empty
        v-if="!loading && classes.length === 0"
        description="尚未加入班级"
      />
      <div v-else class="class-list">
        <article
          v-for="item in classes"
          :key="item.classId || item.id"
          class="class-row"
        >
          <div class="class-main">
            <h2>{{ item.className }}</h2>
            <p>{{ item.description || "暂无班级说明" }}</p>
          </div>
          <div class="class-meta">
            <span>教师：{{ item.teacherName || "未提供" }}</span>
            <span>加入时间：{{ formatDate(item.joinedAt) }}</span>
          </div>
          <div class="class-row-actions">
            <a-button type="text" @click="router.push('/tasks')"
              >查看学习任务</a-button
            >
            <a-button type="text" status="danger" @click="leaveClass(item)"
              >退出班级</a-button
            >
          </div>
        </article>
      </div>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Message } from "@arco-design/web-vue";
import { useRouter } from "vue-router";
import {
  joinManagedClass,
  leaveManagedClass,
  listMyClasses,
  type ManagedClass,
} from "@/api/tasks";

const router = useRouter();
const classes = ref<ManagedClass[]>([]);
const classCode = ref("");
const loading = ref(false);
const joining = ref(false);

const loadClasses = async () => {
  loading.value = true;
  try {
    const response = await listMyClasses();
    if (response.data.code === 0) classes.value = response.data.data || [];
    else Message.error(response.data.message || "班级列表加载失败");
  } catch {
    Message.error("班级列表加载失败，请稍后重试");
  } finally {
    loading.value = false;
  }
};

const joinClass = async () => {
  const code = classCode.value.trim();
  if (!code || joining.value) return;
  joining.value = true;
  try {
    const response = await joinManagedClass(code);
    if (response.data.code === 0) {
      classCode.value = "";
      Message.success(`已加入${response.data.data.className}`);
      await loadClasses();
    } else Message.error(response.data.message || "加入班级失败");
  } catch {
    Message.error("加入班级失败，请检查邀请码后重试");
  } finally {
    joining.value = false;
  }
};

const leaveClass = async (item: ManagedClass) => {
  const classId = Number(item.classId || item.id);
  if (!window.confirm(`确定退出“${item.className}”吗？`)) return;
  try {
    const response = await leaveManagedClass(classId);
    if (response.data.code === 0) {
      Message.success("已退出班级");
      await loadClasses();
    } else Message.error(response.data.message || "退出班级失败");
  } catch {
    Message.error("退出班级失败，请稍后重试");
  }
};

const formatDate = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
};

onMounted(loadClasses);
</script>

<style scoped>
.classes-page {
  width: 100%;
  padding-bottom: 32px;
}
.class-actions {
  display: flex;
  max-width: 520px;
  gap: 8px;
  margin-bottom: 20px;
}
.class-actions :deep(.arco-input-wrapper) {
  min-width: 0;
}
.classes-content {
  display: block;
  min-height: 140px;
}
.class-list {
  border-top: 1px solid var(--oslab-line);
}
.class-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 0.8fr) auto;
  gap: 20px;
  align-items: center;
  padding: 18px 4px;
  border-bottom: 1px solid var(--oslab-line);
}
.class-main h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 16px;
}
.class-main p,
.class-meta {
  margin: 5px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}
.class-meta {
  display: grid;
  gap: 4px;
  margin: 0;
}
.class-row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
@media (max-width: 760px) {
  .class-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .class-row-actions {
    justify-content: flex-start;
  }
}
</style>
