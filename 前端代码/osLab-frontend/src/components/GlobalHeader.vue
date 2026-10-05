<template>
  <header id="globalHeader">
    <router-link class="title-bar" to="/home" aria-label="OSLab 首页">
      <img class="logo" src="../assets/oj-logo.svg" alt="" />
      <span class="title">OSLab <small>操作系统</small></span>
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
          <span class="user-avatar" aria-hidden="true">
            <span class="avatar-letter">{{ displayName[0] || "学" }}</span>
          </span>
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
            学生信息
          </a-doption>
          <a-doption value="records">
            <template #icon><icon-history /></template>
            学习记录
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
import { computed } from "vue";
import { useStore } from "vuex";
import checkAccess from "@/access/checkAccess";
import ACCESS_ENUM from "@/access/accessEnum";
import { Message } from "@arco-design/web-vue";
import { UserControllerService } from "../../generated";
import { ApiError } from "../../generated/core/ApiError";

const router = useRouter();
const route = useRoute();
const store = useStore();

// 顶栏一级菜单：来自统一导航配置，按权限过滤
const visibleRoutes = computed(() => {
  return APP_NAVIGATION.filter((item) =>
    checkAccess(store.state.user.loginUser, item.access as string)
  ).map((item) => ({ path: item.path, name: item.label, key: item.key }));
});

// 路由变化后，按领域解析顶栏选中态（详情页保持所属一级入口高亮）。
// records/account 等辅助页面没有顶栏入口，因此保持无选中项。
const selectedKeys = computed(() => {
  const key = resolveNavKey(route.path);
  const item = APP_NAVIGATION.find((n) => n.key === key);
  return item ? [item.path] : [];
});

const isLoggedIn = computed(() => {
  const userRole = store.state.user.loginUser?.userRole;
  return Boolean(userRole) && userRole !== ACCESS_ENUM.NOT_LOGIN;
});

const isAdmin = computed(() => {
  return store.state.user.loginUser?.userRole === ACCESS_ENUM.ADMIN;
});

const displayName = computed(
  () => store.state.user?.loginUser?.userName || "未登录"
);

const navigateToLogin = () => {
  router.push({ path: "/user/login" });
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
    store.commit("user/updateUser", {
      userName: "未登录",
      userRole: ACCESS_ENUM.NOT_LOGIN,
    });
    localStorage.removeItem("userId");
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
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
  min-height: 64px;
  padding: 0 20px;
  box-sizing: border-box;
  background: var(--oslab-panel, #ffffff);
  border-bottom: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
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
  color: var(--oslab-ink, #1b1b1b);
  font-size: 17px;
  font-weight: 600;
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
}

.main-navigation {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: thin;
}

.main-nav-link {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 2px solid transparent;
  color: var(--oslab-ink-soft, #37352f);
  text-decoration: none;
  font-size: 14px;
  white-space: nowrap;
  transition: color var(--oslab-duration-fast, 150ms) var(--oslab-ease, ease);
}

.main-nav-link:hover,
.main-nav-link:focus-visible {
  color: var(--oslab-primary, #2059c9);
}

.main-nav-link.active {
  border-bottom-color: var(--oslab-primary, #2059c9);
  color: var(--oslab-primary, #2059c9);
  font-weight: 600;
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
  position: relative;
  flex: 0 0 36px;
}

.avatar-letter {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--oslab-primary, #2059c9);
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-trigger {
  color: var(--oslab-primary, #2059c9);
  padding: 8px 12px;
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
  }
  .main-nav-link {
    min-height: 42px;
    padding: 0 11px;
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
  .logo {
    width: 30px;
    height: 30px;
    flex-basis: 30px;
  }
}
</style>
