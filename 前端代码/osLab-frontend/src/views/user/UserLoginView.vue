<template>
  <div
    id="os-auth-panel"
    class="os-auth-panel"
    role="tabpanel"
    aria-labelledby="os-login-tab"
  >
    <h2>继续你的学习</h2>
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
            autocomplete="current-password"
            placeholder="输入密码…"
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
      <div class="os-form-extra">
        <label class="os-remember">
          <input v-model="rememberAccount" type="checkbox" />
          记住账号
        </label>
      </div>
      <button type="submit" class="os-submit" :disabled="loading">
        <span>{{ loading ? "正在登录…" : "登录" }}</span>
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
import { useStore } from "vuex";
import { UserControllerService } from "../../../generated";
import { ROLE_HOME } from "@/navigation/appNavigation";
import ACCESS_ENUM from "@/access/accessEnum";

const REMEMBER_KEY = "oslab:remember-account";

const route = useRoute();
const router = useRouter();
const store = useStore();

// 与注册页签共享的账号输入（由 UserLayout 提供）
const sharedAccount = inject<Ref<string>>("oslabSharedAccount", ref(""));
const account = ref(sharedAccount.value);
watch(account, (value) => {
  sharedAccount.value = value;
});
// 提交进行中：通知布局禁用页签切换
const authBusy = inject<Ref<boolean>>("oslabAuthBusy", ref(false));

const password = ref("");
const showPassword = ref(false);
const rememberAccount = ref(false);
const loading = ref(false);
const error = ref("");
const errorField = ref<"account" | "password" | "">("");

watch([account, password], () => {
  if (errorField.value) {
    error.value = "";
    errorField.value = "";
  }
});

onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY);
  if (saved && !account.value) {
    account.value = saved;
    rememberAccount.value = true;
  }
});

/**
 * 仅接受站内目标路径，无目标时使用当前角色默认入口。
 */
function resolveRedirect(raw: unknown): string {
  if (typeof raw === "string" && raw.startsWith("/") && !raw.startsWith("//")) {
    return raw;
  }
  const role = store.state.user.loginUser?.userRole;
  return ROLE_HOME[role] || ROLE_HOME[ACCESS_ENUM.USER];
}

/**
 * 提交表单：沿用现有登录服务与状态逻辑
 */
const handleSubmit = async () => {
  if (loading.value) {
    return;
  }
  error.value = "";
  errorField.value = "";
  if (!account.value) {
    error.value = "请输入账号";
    errorField.value = "account";
    document.getElementById("oslab-account-input")?.focus();
    return;
  }
  if (!password.value) {
    error.value = "请输入密码";
    errorField.value = "password";
    document.getElementById("oslab-password-input")?.focus();
    return;
  }
  loading.value = true;
  authBusy.value = true;
  try {
    const res = await UserControllerService.userLoginUsingPost({
      userAccount: account.value,
      userPassword: password.value,
    });
    if (res.code === 0 && res.data) {
      if (rememberAccount.value) {
        localStorage.setItem(REMEMBER_KEY, account.value);
      } else {
        localStorage.removeItem(REMEMBER_KEY);
      }
      await store.dispatch("user/getLoginUser");
      localStorage.setItem("userId", String(res.data.id));
      await router.replace(resolveRedirect(route.query.redirect));
      return;
    }
    errorField.value = "";
    error.value = `登录失败：${res.message || "请检查账号和密码"}`;
  } catch {
    errorField.value = "";
    error.value = "登录请求未完成，请检查网络连接后重试";
  } finally {
    loading.value = false;
    authBusy.value = false;
  }
};
</script>
