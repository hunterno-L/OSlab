<template>
  <header id="globalHeader">
    <router-link class="title-bar" to="/home" aria-label="ZhiCore 首页">
      <img
        class="logo brand-mark"
        :src="brandMarkUrl"
        width="36"
        height="36"
        alt=""
        aria-hidden="true"
      />
      <span class="title">ZhiCore <small>OPERATING SYSTEMS</small></span>
    </router-link>
    <nav class="main-navigation" aria-label="主导航">
      <router-link
        v-for="item in visibleRoutes"
        :key="item.path"
        class="main-nav-link"
        :class="{ active: selectedKeys.includes(item.path) }"
        :aria-current="selectedKeys.includes(item.path) ? 'page' : undefined"
        :to="item.path"
      >
        {{ item.name }}
      </router-link>
    </nav>
    <form class="header-search" role="search" @submit.prevent="submitSearch">
      <a-input
        v-model="searchText"
        allow-clear
        placeholder="搜索算法、题目"
        aria-label="搜索算法或题目"
        @press-enter="submitSearch"
      >
        <template #prefix><icon-search /></template>
      </a-input>
    </form>
    <div class="user-info">
      <a-dropdown
        v-if="isLoggedIn"
        trigger="click"
        @select="handleProfileAction"
      >
        <button
          class="user-trigger"
          type="button"
          aria-haspopup="menu"
          :aria-label="`${displayName}，打开账户菜单`"
        >
          <user-avatar
            class="user-avatar"
            :user-id="loginUser?.id"
            :user-name="displayName"
            :user-avatar="loginUser?.userAvatar"
            :size="36"
          />
          <span class="user-profile">
            <span class="user-name">{{ displayName }}</span>
            <span class="user-role">
              {{ isAdmin ? "管理员 · 操作系统" : "学生 · 操作系统" }}
            </span>
          </span>
          <icon-down class="user-chevron" aria-hidden="true" />
        </button>
        <template #content>
          <a-doption value="account">
            <template #icon><icon-user /></template>
            个人信息
          </a-doption>
          <a-doption value="classes">
            <template #icon><icon-user-group /></template>
            {{ isAdmin ? "班级管理" : "我的班级" }}
          </a-doption>
          <a-doption value="records">
            <template #icon><icon-history /></template>
            我的学习
          </a-doption>
          <a-doption value="models">
            <template #icon><icon-settings /></template>
            AI 模型设置
          </a-doption>
          <a-doption value="logout">
            <template #icon><icon-export /></template>
            退出登录
          </a-doption>
        </template>
      </a-dropdown>
      <button
        v-else
        class="login-trigger"
        type="button"
        aria-label="登录"
        @click="navigateToLogin"
      >
        登录
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { APP_NAVIGATION, resolveNavKey } from "@/navigation/appNavigation";
import { useRoute, useRouter } from "vue-router";
import { computed, ref } from "vue";
import { useStore } from "vuex";
import checkAccess from "@/access/checkAccess";
import ACCESS_ENUM from "@/access/accessEnum";
import UserAvatar from "@/components/UserAvatar.vue";
import { resolveDisplayName } from "@/utils/userPresentation";
import { Message } from "@arco-design/web-vue";
import { UserControllerService } from "../../generated";
import { ApiError } from "../../generated/core/ApiError";
import { ALGORITHMS } from "@/simulation/catalog";

const brandMarkUrl = `${process.env.BASE_URL}brand-mark.svg`;
const router = useRouter();
const route = useRoute();
const store = useStore();
const searchText = ref("");

// 顶栏一级菜单：来自统一导航配置，按权限过滤；同一入口按角色解析目标地址。
const visibleRoutes = computed(() => {
  const role = store.state.user.loginUser?.userRole;
  return APP_NAVIGATION.filter((item) =>
    checkAccess(store.state.user.loginUser, item.access as string)
  ).map((item) => ({
    path: item.rolePaths?.[role] || item.path,
    name: item.label,
    key: item.key,
  }));
});

// 路由变化后，按领域解析顶栏选中态（详情页保持所属一级入口高亮）。
// records/account 等辅助页面没有顶栏入口，因此保持无选中项。
const selectedKeys = computed(() => {
  const key = resolveNavKey(route.path);
  const item = APP_NAVIGATION.find((n) => n.key === key);
  if (!item) return [];
  const role = store.state.user.loginUser?.userRole;
  return [item.rolePaths?.[role] || item.path];
});

const loginUser = computed(() => store.state.user?.loginUser || {});

const isLoggedIn = computed(() => {
  const userRole = loginUser.value?.userRole;
  return Boolean(userRole) && userRole !== ACCESS_ENUM.NOT_LOGIN;
});

const isAdmin = computed(() => {
  return loginUser.value?.userRole === ACCESS_ENUM.ADMIN;
});

// 昵称统一走展示规则：trim 后有值显示原值，空白回退“用户”。
const displayName = computed(() =>
  resolveDisplayName(loginUser.value?.userName)
);

const navigateToLogin = () => {
  router.push({ path: "/user/login" });
};

const submitSearch = () => {
  const keyword = searchText.value.trim();
  if (!keyword) return;
  const normalized = keyword.toLocaleLowerCase();
  const algorithm = ALGORITHMS.find(
    (item) =>
      item.title.toLocaleLowerCase().includes(normalized) ||
      item.shortTitle.toLocaleLowerCase().includes(normalized)
  );
  if (algorithm) {
    void router.push({
      path: "/experiments/simulation",
      query: { algorithmKey: algorithm.key },
    });
    return;
  }
  void router.push({ path: "/experiments", query: { title: keyword } });
};

const handleProfileAction = async (value: string | number) => {
  if (value === "account") {
    await router.push({ path: "/account" });
    return;
  }
  if (value === "records") {
    await router.push({ path: "/records" });
    return;
  }
  if (value === "models") {
    await router.push({ path: "/settings/models" });
    return;
  }
  if (value === "classes") {
    await router.push({
      path: isAdmin.value ? "/teaching/classes" : "/classes",
    });
    return;
  }
  if (value !== "logout") return;
  try {
    const response = await UserControllerService.userLogoutUsingPost();
    if (response?.code !== 0) {
      // The shared interceptor handles expired sessions and their redirect.
      if (response?.code !== 40100) {
        Message.error(response?.message || "退出登录失败，请稍后重试");
      }
      return;
    }
    // 登出与会话失效共用同一身份重置：完整清空昵称、头像、ID 并清理隔离键
    store.commit("user/resetUser");
    await router.push({ path: "/user/login" });
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) {
      Message.error("退出登录请求未完成，请稍后重试");
    }
  }
};
</script>

<style scoped>
#globalHeader {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(190px, 22%) auto;
  align-items: center;
  gap: 18px;
  min-height: 60px;
  padding: 0 16px;
  box-sizing: border-box;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}

.title-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--oslab-ink, #1b1b1b);
  text-decoration: none;
}

.title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 20px;
  font-weight: 700;
  white-space: nowrap;
}

.title small {
  color: var(--oslab-muted-strong, #757069);
  font-size: 11px;
  font-weight: 400;
}

.logo {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  display: block;
  object-fit: contain;
}

.main-navigation {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow-x: auto;
  box-sizing: border-box;
  scrollbar-width: thin;
}

.main-nav-link {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 42px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--oslab-radius-control);
  color: var(--oslab-ink-soft);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}

.main-nav-link:hover,
.main-nav-link:focus-visible {
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink);
}

.main-nav-link.active {
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
  font-weight: 600;
}
.header-search {
  min-width: 0;
}
.header-search :deep(.arco-input-wrapper) {
  min-height: 40px;
  border-radius: 22px;
  background: var(--oslab-panel-soft);
}
.header-search :deep(.arco-input) {
  font-size: 13px;
}
.user-info {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  position: relative;
  min-width: 0;
  max-width: 220px;
}

.user-trigger,
.login-trigger {
  border: 0;
  background: transparent;
  font: inherit;
  color: inherit;
  cursor: pointer;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  padding: 4px 0;
  max-width: 100%;
}

.user-profile {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.3;
  margin: 0 8px;
  text-align: left;
  min-width: 0;
}

.user-name {
  color: var(--oslab-ink, #1b1b1b);
  font-size: 13px;
  font-weight: 600;
  display: block;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  color: var(--oslab-muted-strong, #757069);
  font-size: 11px;
}

.user-chevron {
  color: var(--oslab-muted, #a39e98);
  font-size: 14px;
  flex: 0 0 auto;
}

.user-avatar {
  flex: 0 0 36px;
}

.login-trigger {
  min-height: 40px;
  padding: 8px 12px;
  border-radius: var(--oslab-radius-control);
  color: var(--oslab-primary, #2447d8);
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease);
}

.login-trigger:hover {
  background: var(--oslab-panel-soft);
}
@media (max-width: 900px) {
  #globalHeader {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 4px 16px;
    padding: 10px 12px 0;
  }
  .main-navigation {
    grid-column: 1 / -1;
    grid-row: 2;
    width: 100%;
    gap: 2px;
    padding-right: 28px;
    -webkit-mask-image: linear-gradient(
      to right,
      #000 calc(100% - 24px),
      transparent
    );
    mask-image: linear-gradient(to right, #000 calc(100% - 24px), transparent);
  }
  .header-search {
    grid-column: 1 / 2;
    grid-row: 3;
    width: 100%;
  }
  .user-info {
    grid-column: 2;
    grid-row: 1;
  }
  .main-nav-link {
    min-height: 40px;
    padding: 0 10px;
    font-size: 12px;
  }
  .user-info {
    max-width: 180px;
  }
  .user-name {
    max-width: 104px;
  }
}
@media (max-width: 480px) {
  .title small,
  .user-role {
    display: none;
  }
  .user-info {
    max-width: 154px;
  }
  .user-name {
    max-width: 84px;
  }
  .main-nav-link {
    padding: 0 8px;
    font-size: 12px;
  }
  .logo {
    width: 30px;
    height: 30px;
    flex-basis: 30px;
  }
  .header-search {
    grid-column: 1 / -1;
    grid-row: 3;
  }
}

/* 触屏：导航、账户与搜索框统一到 44px 触控目标 */
@media (pointer: coarse) {
  .main-nav-link,
  .login-trigger,
  .user-trigger {
    min-height: 44px;
  }

  .header-search :deep(.arco-input-wrapper) {
    min-height: 44px;
  }
}
</style>
