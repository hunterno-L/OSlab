<template>
  <main class="profile-view">
    <section class="profile-header">
      <div>
        <p class="eyebrow">账户</p>
        <h1>个人信息</h1>
        <p class="profile-subtitle">仅展示当前登录账号的基本资料。</p>
      </div>
      <a-button type="outline" @click="router.push('/graph')">
        <icon-arrow-left />
        返回图谱
      </a-button>
    </section>

    <section class="profile-panel" aria-labelledby="profile-details-title">
      <div class="profile-summary">
        <user-avatar
          :user-id="user.id"
          :user-name="displayName"
          :user-avatar="user.userAvatar"
          :size="52"
        />
        <div>
          <h2 id="profile-details-title">{{ displayName }}</h2>
          <p>{{ roleText }}</p>
        </div>
        <div class="profile-summary-actions">
          <a-button
            v-if="!editing"
            type="outline"
            size="small"
            @click="startEditing"
          >
            编辑资料
          </a-button>
        </div>
      </div>

      <a-descriptions :column="1" bordered size="medium">
        <a-descriptions-item label="昵称">
          {{ displayName }}
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

      <a-form
        v-if="editing"
        class="profile-edit-form"
        :model="editForm"
        layout="vertical"
        @submit.prevent
      >
        <a-form-item label="昵称" field="userName">
          <a-input
            v-model="editForm.userName"
            :max-length="256"
            allow-clear
            placeholder="输入昵称，留空将显示“用户”"
          />
        </a-form-item>
        <a-form-item label="头像地址" field="userAvatar">
          <a-input
            v-model="editForm.userAvatar"
            :max-length="1024"
            allow-clear
            placeholder="粘贴图片地址，如 https://...；留空即清除头像"
          />
        </a-form-item>
        <p v-if="saveError" class="profile-edit-error" role="alert">
          {{ saveError }}
        </p>
        <a-space>
          <a-button type="primary" :loading="saving" @click="saveProfile">
            保存
          </a-button>
          <a-button @click="cancelEditing">取消</a-button>
        </a-space>
      </a-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import { Message } from "@arco-design/web-vue";
import ACCESS_ENUM from "@/access/accessEnum";
import UserAvatar from "@/components/UserAvatar.vue";
import { resolveDisplayName } from "@/utils/userPresentation";
import { UserControllerService } from "../../../generated";

// 数据库列宽，与后端 UserController.updateMyUser 的校验保持一致
const USER_NAME_MAX_LENGTH = 256;
const USER_AVATAR_MAX_LENGTH = 1024;

const router = useRouter();
const store = useStore();
const user = computed(() => store.state.user?.loginUser || {});
// 昵称统一走展示规则：空白昵称显示“用户”，不再出现“未设置昵称/未登录”
const displayName = computed(() => resolveDisplayName(user.value.userName));
const roleText = computed(() =>
  user.value.userRole === ACCESS_ENUM.ADMIN ? "管理员" : "学生"
);

const editing = ref(false);
const saving = ref(false);
const saveError = ref("");
const editForm = reactive({ userName: "", userAvatar: "" });

const startEditing = () => {
  editForm.userName =
    typeof user.value.userName === "string" ? user.value.userName : "";
  editForm.userAvatar =
    typeof user.value.userAvatar === "string" ? user.value.userAvatar : "";
  saveError.value = "";
  editing.value = true;
};

const cancelEditing = () => {
  editing.value = false;
  saveError.value = "";
};

const saveProfile = async () => {
  if (saving.value) {
    return;
  }
  saveError.value = "";
  const userName = editForm.userName.trim();
  const userAvatar = editForm.userAvatar.trim();
  if (userName.length > USER_NAME_MAX_LENGTH) {
    saveError.value = `昵称最长 ${USER_NAME_MAX_LENGTH} 个字符`;
    return;
  }
  if (userAvatar.length > USER_AVATAR_MAX_LENGTH) {
    saveError.value = `头像地址最长 ${USER_AVATAR_MAX_LENGTH} 个字符`;
    return;
  }
  saving.value = true;
  try {
    // 只编辑昵称与头像地址；简介原样带回，避免被清空
    const res = await UserControllerService.updateMyUserUsingPost({
      userName,
      userAvatar,
      userProfile:
        typeof user.value.userProfile === "string"
          ? user.value.userProfile
          : undefined,
    });
    if (res.code !== 0) {
      // 失败：保留表单输入并给出错误反馈，顶栏不提前变更
      saveError.value = res.message || "保存失败，请稍后重试";
      return;
    }
    editing.value = false;
    Message.success("资料已更新");
    // 保存成功后刷新登录态，顶栏与用户管理摘要随之同步
    await store.dispatch("user/getLoginUser");
  } catch {
    saveError.value = "保存请求未完成，请检查网络后重试";
  } finally {
    saving.value = false;
  }
};

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

.profile-summary-actions {
  margin-left: auto;
}

.profile-edit-form {
  margin-top: 20px;
  max-width: 420px;
}

.profile-edit-error {
  margin: 0 0 12px;
  color: var(--oslab-danger, #f53f3f);
  font-size: 13px;
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
