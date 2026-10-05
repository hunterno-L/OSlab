import router from "@/router";
import store from "@/store";
import ACCESS_ENUM from "@/access/accessEnum";
import checkAccess from "@/access/checkAccess";

router.beforeEach(async (to, from, next) => {
  console.log("登陆用户信息", store.state.user.loginUser);
  let loginUser = store.state.user.loginUser;
  // 如果之前没登陆过，自动登录。后端不可达时不得让导航失败（否则所有页面
  // 都会渲染成空白 router-view）；按未登录处理，由页面自身的登录跳转兜底。
  if (!loginUser || !loginUser.userRole) {
    try {
      await store.dispatch("user/getLoginUser");
    } catch (e) {
      console.warn("[access] 获取登录态失败，按未登录处理", e);
    }
    loginUser = store.state.user.loginUser;
  }
  const needAccess = (to.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN;
  // 要跳转的页面必须要登陆
  if (needAccess !== ACCESS_ENUM.NOT_LOGIN) {
    // 如果没登陆，跳转到登录页面
    if (
      !loginUser ||
      !loginUser.userRole ||
      loginUser.userRole === ACCESS_ENUM.NOT_LOGIN
    ) {
      next(`/user/login?redirect=${to.fullPath}`);
      return;
    }
    // 如果已经登陆了，但是权限不足，那么跳转到无权限页面
    if (!checkAccess(loginUser, needAccess)) {
      next("/noAuth");
      return;
    }
  }
  next();
});
