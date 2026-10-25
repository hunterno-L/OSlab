<template>
  <div
    id="os-auth-panel"
    class="os-auth-panel"
    role="tabpanel"
    aria-labelledby="os-register-tab"
  >
    <h2>创建你的学习账号</h2>
    <form autocomplete="on" novalidate @submit.prevent="handleSubmit">
      <label class="os-field">
        <span class="os-field-label">账号</span>
        <input
          id="oslab-account-input"
          v-model="account"
          name="userAccount"
          type="text"
          autocomplete="username"
          spellcheck="false"
          placeholder="输入账号，如 os2026001…"
        />
        <span
          v-if="error && errorField === 'account'"
          class="os-field-error"
          role="alert"
          >{{ error }}</span
        >
      </label>
      <label class="os-field">
        <span class="os-field-label">密码</span>
        <span class="os-input-wrap">
          <input
            id="oslab-password-input"
            v-model="password"
            class="os-password"
            name="userPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="输入密码，至少 8 位…"
          />
          <button
            type="button"
            class="os-eye"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <svg
              v-if="showPassword"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path
                d="M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"
              />
              <path
                d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3.5 7 10 7a9.74 9.74 0 0 0 5.39-1.61"
              />
              <line x1="2" y1="2" x2="22" y2="22" />
            </svg>
            <svg
              v-else
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </button>
        </span>
        <span
          v-if="error && errorField === 'password'"
          class="os-field-error"
          role="alert"
          >{{ error }}</span
        >
      </label>
      <!-- 确认密码字段：挂载后高度展开（280ms） -->
      <div class="os-confirm-row" :class="{ 'is-expanded': expanded }">
        <div class="os-confirm-inner">
          <label class="os-field">
            <span class="os-field-label">确认密码</span>
            <input
              id="oslab-check-input"
              v-model="checkPassword"
              name="checkPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入密码…"
            />
            <span
              v-if="error && errorField === 'check'"
              class="os-field-error"
              role="alert"
              >{{ error }}</span
            >
          </label>
        </div>
      </div>
      <div class="os-form-extra">
        <span class="os-register-hint">账号至少 4 位，密码至少 8 位。</span>
      </div>
      <button type="submit" class="os-submit" :disabled="loading">
        <span>{{ loading ? "正在创建…" : "创建账号" }}</span>
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M5 12h14" />
          <path d="m13 6 6 6-6 6" />
        </svg>
      </button>
      <p
        v-show="error && !errorField"
        class="os-error"
        role="alert"
        aria-live="polite"
      >
        {{ error }}
      </p>
    </form>
  </div>
</template>

<script setup lang="ts">
import { inject, onMounted, ref, watch } from "vue";
import type { Ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import message from "@arco-design/web-vue/es/message";
import { UserControllerService } from "../../../generated";

const route = useRoute();
const router = useRouter();

// 与登录页签共享的账号输入（由 UserLayout 提供）
const sharedAccount = inject<Ref<string>>("oslabSharedAccount", ref(""));
const account = ref(sharedAccount.value);
watch(account, (value) => {
  sharedAccount.value = value;
});
// 提交进行中：通知布局禁用页签切换
const authBusy = inject<Ref<boolean>>("oslabAuthBusy", ref(false));

const password = ref("");
const checkPassword = ref("");
const showPassword = ref(false);
const loading = ref(false);
const error = ref("");
const errorField = ref<"account" | "password" | "check" | "">("");
const expanded = ref(false);

watch([account, password, checkPassword], () => {
  if (errorField.value) {
    error.value = "";
    errorField.value = "";
  }
});

onMounted(() => {
  // 等待初始样式生效后展开确认密码字段
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      expanded.value = true;
    });
  });
});

/**
 * 提交表单：沿用现有注册服务，保留账号/密码/确认密码校验
 */
const handleSubmit = async () => {
  if (loading.value) {
    return;
  }
  error.value = "";
  errorField.value = "";
  if (!account.value || account.value.length < 4) {
    error.value = "账号至少 4 位";
    errorField.value = "account";
    document.getElementById("oslab-account-input")?.focus();
    return;
  }
  if (!password.value || password.value.length < 8) {
    error.value = "密码至少 8 位";
    errorField.value = "password";
    document.getElementById("oslab-password-input")?.focus();
    return;
  }
  if (password.value !== checkPassword.value) {
    error.value = "两次输入的密码不一致";
    errorField.value = "check";
    (
      document.getElementById("oslab-check-input") as HTMLInputElement | null
    )?.focus();
    return;
  }
  loading.value = true;
  authBusy.value = true;
  try {
    const res = await UserControllerService.userRegisterUsingPost({
      userAccount: account.value,
      userPassword: password.value,
      checkPassword: checkPassword.value,
    });
    if (res.code === 0) {
      // 注册成功：回到登录，账号通过共享状态保留
      message.success("注册成功，请登录");
      const query: Record<string, string> = {};
      const redirect = route.query.redirect;
      if (typeof redirect === "string" && redirect) {
        query.redirect = redirect;
      }
      await router.push({ path: "/user/login", query });
      return;
    }
    errorField.value = "";
    error.value = `注册失败：${res.message || "请检查填写内容"}`;
  } catch {
    errorField.value = "";
    error.value = "注册请求未完成，请检查网络连接后重试";
  } finally {
    loading.value = false;
    authBusy.value = false;
  }
};
</script>
