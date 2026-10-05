<template>
  <main class="profile-view">
    <section class="profile-header">
      <div>
        <p class="eyebrow">账户</p>
        <h1>学生信息</h1>
        <p class="profile-subtitle">仅展示当前登录账号的基本资料。</p>
      </div>
      <a-button type="outline" @click="router.push('/graph')">
        <icon-arrow-left />
        返回图谱
      </a-button>
    </section>

    <section class="profile-panel" aria-labelledby="profile-details-title">
      <div class="profile-summary">
        <span class="profile-avatar" aria-hidden="true">{{ initials }}</span>
        <div>
          <h2 id="profile-details-title">
            {{ user.userName || "未设置昵称" }}
          </h2>
          <p>{{ roleText }}</p>
        </div>
      </div>

      <a-descriptions :column="1" bordered size="medium">
        <a-descriptions-item label="账号名称">
          {{ user.userName || "未设置" }}
        </a-descriptions-item>
        <a-descriptions-item label="用户编号">
          {{ user.id ?? "未提供" }}
        </a-descriptions-item>
        <a-descriptions-item label="注册时间">
          {{ formatDate(user.createTime) }}
        </a-descriptions-item>
        <a-descriptions-item label="个人简介">
          {{ user.userProfile || "暂无简介" }}
        </a-descriptions-item>
      </a-descriptions>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import ACCESS_ENUM from "@/access/accessEnum";

const router = useRouter();
const store = useStore();
const user = computed(() => store.state.user?.loginUser || {});
const roleText = computed(() =>
  user.value.userRole === ACCESS_ENUM.ADMIN ? "管理员" : "学生"
);
const initials = computed(() => (user.value.userName || "学").slice(0, 1));

const formatDate = (value?: string) => {
  if (!value) return "未提供";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};
</script>

<style scoped>
.profile-view {
  width: min(860px, 100%);
  margin: 0 auto;
  padding: 12px 0 32px;
}

.profile-header,
.profile-panel {
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-lg, 8px);
}

.profile-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-end;
  padding: 28px 32px;
}

.profile-panel {
  margin-top: 16px;
  padding: 24px 28px 28px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--oslab-primary, #165dff);
  font-size: 12px;
  font-weight: 600;
}

h1,
h2 {
  margin: 0;
  color: var(--oslab-text-title, #1d2129);
}

h1 {
  font-size: 26px;
}

h2 {
  font-size: 18px;
}

.profile-subtitle,
.profile-summary p {
  margin: 8px 0 0;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 13px;
}

.profile-summary {
  display: flex;
  gap: 14px;
  align-items: center;
  padding-bottom: 22px;
  margin-bottom: 22px;
  border-bottom: 1px solid var(--oslab-divider, #f2f3f5);
}

.profile-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  border-radius: 50%;
  background: #edf3ff;
  color: var(--oslab-primary, #165dff);
  font-size: 20px;
  font-weight: 600;
}

@media (max-width: 640px) {
  .profile-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 24px;
  }

  .profile-panel {
    padding: 20px;
  }
}
</style>
