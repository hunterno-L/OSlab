import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { routes } from "@/router/routes";
import { UserControllerService } from "../../generated";

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

// 添加全局前置守卫
router.beforeEach(async (to, from, next) => {
  // 假设有一个方法 `isAuthenticated` 用于检查用户是否登录
  const isAuthenticated = await checkUserLoginStatus();
  console.log("isAuthenticated:", isAuthenticated);

  // 如果用户未登录，且目标路由不是登录页，则跳转到登录页
  if (
    !isAuthenticated &&
    to.path !== "/user/login" &&
    to.path !== "/user/register"
  ) {
    next({ path: "/user/login" }); // 跳转到登录页
  } else {
    next(); // 允许正常跳转
  }
});

// 登录状态检查函数
async function checkUserLoginStatus() {
  // 替换为你的登录状态检查逻辑，例如检查 localStorage 或 Vuex 中的值
  try {
    const response = await UserControllerService.getLoginUserUsingGet();
    return response.code === 0;
  } catch (error) {
    console.error("Error checking login status:", error);
    return false;
  }
}

export default router;
