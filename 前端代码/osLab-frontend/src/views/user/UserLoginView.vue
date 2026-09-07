<template>
  <div id="userLoginView">
    <h2 style="margin-bottom: 16px; text-align: center">用户登录</h2>
    <a-form
      style="max-width: 480px; margin: 0 auto"
      label-align="left"
      auto-label-width
      :model="form"
      @submit="handleSubmit"
    >
      <a-form-item field="userAccount" label="账号">
        <a-input v-model="form.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item field="userPassword" label="密码">
        <a-input-password
          v-model="form.userPassword"
          placeholder="请输入密码"
        />
      </a-form-item>
      <div style="display: flex; flex-direction: column; align-items: center">
        <a-form-item style="width: 100%">
          <a-button type="primary" html-type="submit" style="width: 100%"
            >登录</a-button
          >
        </a-form-item>
        <a-form-item style="width: 100%">
          <a-button @click="goToTargetPage" type="outline" style="width: 100%"
            >注册</a-button
          >
        </a-form-item>
      </div>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { UserControllerService, UserLoginRequest } from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import { useRouter } from "vue-router";
import { useStore } from "vuex";

/**
 * 表单信息
 */
const form = reactive({
  userAccount: "",
  userPassword: "",
} as UserLoginRequest);

const router = useRouter();
const store = useStore();

/**
 * 提交表单
 * @param data
 */
const handleSubmit = async () => {
  try {
    const res = await UserControllerService.userLoginUsingPost(form);
    // 登录成功，跳转到主页
    if (res.code === 0 && res.data) {
      await store.dispatch("user/getLoginUser");
      localStorage.setItem("userId", String(res.data.id));
      await router.replace({ path: "/" });
      return;
    }
    message.error(`登录失败：${res.message || "请检查账号和密码"}`);
  } catch {
    message.error("登录请求未完成，请检查网络连接后重试");
  }
};
const goToTargetPage = () => {
  router.push({ path: "/user/register" }); // 根据路由名称进行跳转
};
</script>
