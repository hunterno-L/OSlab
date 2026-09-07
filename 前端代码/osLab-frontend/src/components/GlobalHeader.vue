<template>
  <a-row id="globalHeader" align="center" :wrap="false">
    <a-col flex="auto">
      <a-menu
        mode="horizontal"
        :selected-keys="selectedKeys"
        @menu-item-click="doMenuClick"
      >
        <a-menu-item
          key="0"
          :style="{ padding: 0, marginRight: '38px' }"
          disabled
        >
          <div class="title-bar">
            <img class="logo" src="../assets/oj-logo.svg" />
            <div class="title">OSLab 操作系统课程设计实验平台</div>
          </div>
        </a-menu-item>
        <a-menu-item v-for="item in visibleRoutes" :key="item.path">
          {{ item.name }}
        </a-menu-item>
      </a-menu>
    </a-col>
    <a-col flex="auto">
      <div class="user-info">
        <div class="user-name">
          {{
            store.state.user?.loginUser
              ? store.state.user.loginUser.userName ?? "无昵称"
              : "未登录"
          }}
        </div>
        <div
          class="user-avatar"
          @click="toggleMenu"
          @mouseenter="showMenu = true"
          @mouseleave="showMenu = false"
        >
          <img
            :src="
              store.state.user?.loginUser?.avatarUrl ||
              require('@/assets/avatar.png')
            "
            class="avatar"
          />
          <div v-if="showMenu" class="dropdown-menu">
            <div v-if="isLoggedIn" @click="logout">退出登录</div>
            <div v-else @click="navigateToLogin">登录</div>
          </div>
        </div>
      </div>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { routes } from "../router/routes";
import { useRouter } from "vue-router";
import { computed, ref } from "vue";
import { useStore } from "vuex";
import checkAccess from "@/access/checkAccess";
import ACCESS_ENUM from "@/access/accessEnum";
import { UserControllerService } from "../../generated";

const router = useRouter();
const store = useStore();

// 展示在菜单的路由数组
const visibleRoutes = computed(() => {
  return routes.filter((item, index) => {
    if (item.meta?.hideInMenu) {
      return false;
    }
    // 根据权限过滤菜单
    if (
      !checkAccess(store.state.user.loginUser, item?.meta?.access as string)
    ) {
      return false;
    }
    return true;
  });
});

// 默认主页
const selectedKeys = ref(["/"]);

// 路由跳转后，更新选中的菜单项
router.afterEach((to, from, failure) => {
  selectedKeys.value = [to.path];
});

const doMenuClick = (key: string) => {
  router.push({
    path: key,
  });
};
// 控制菜单的显示与隐藏
const showMenu = ref(false);

// 切换菜单显示
const toggleMenu = () => {
  showMenu.value = !showMenu.value;
};

const isLoggedIn = computed(() => {
  const userRole = store.state.user.loginUser?.userRole;
  return Boolean(userRole) && userRole !== ACCESS_ENUM.NOT_LOGIN;
});

const navigateToLogin = () => {
  router.push({ path: "/user/login" });
};

// 调用退出登录接口
const logout = async () => {
  try {
    const response = await UserControllerService.userLogoutUsingPost();
    console.log("Logout successful:", response);
    store.commit("user/updateUser", {
      userName: "未登录",
      userRole: ACCESS_ENUM.NOT_LOGIN,
    });
    router.push({ path: "/user/login" });
  } catch (error) {
    console.error("Logout failed:", error);
  }
};
</script>

<style scoped>
.title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: #444;
  margin-left: 16px;
}

.logo {
  height: 48px;
}
.user-info {
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  margin-right: 10px; /* 设置昵称和头像之间的间距 */
  margin-left: 10px;
  color: #444;
}

.user-avatar {
  position: relative;
  cursor: pointer;
  margin-right: 10px;
  margin-left: 10px;
}

.avatar {
  width: 40px; /* 头像大小 */
  height: 40px;
  border-radius: 50%; /* 使头像圆形 */
}

.dropdown-menu {
  position: absolute;
  z-index: 1000; /* 确保这个值高于其他元素 */
  top: 45px;
  right: 0;
  background-color: white;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  border-radius: 4px;
  padding: 8px 0;
  width: 150px;
  font-size: 14px;
}

.dropdown-menu div {
  padding: 8px;
  cursor: pointer;
  text-align: center;
}

.dropdown-menu div:hover {
  background-color: #f0f0f0;
}
</style>
