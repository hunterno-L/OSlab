import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { routes } from "@/router/routes";

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

// 全局前置守卫已统一收敛到 src/access/index.ts：
// - 按路由 meta.access 判断登录与权限
// - 需要登录的页面未登录时跳转 /user/login?redirect=...
// - 权限不足时跳转 /noAuth
// 避免与 access/index.ts 重复注册 beforeEach，导致每次路由切换发起冗余登录态请求。

export default router;
