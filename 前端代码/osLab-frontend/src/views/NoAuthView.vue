<template>
  <main class="no-auth-view">
    <section class="no-auth-card">
      <span class="no-auth-code">403</span>
      <h1>没有访问权限</h1>
      <p class="no-auth-hint">
        当前账号无权访问该页面。教学管理功能仅对管理员开放；如需相关权限，请联系课程管理员。
      </p>
      <div class="no-auth-actions">
        <a-button type="primary" @click="router.push('/home')">
          返回首页
        </a-button>
        <a-button @click="router.push('/user/login')">前往登录</a-button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

// /noAuth：登录用户访问无权限页面时的落点（router.beforeEach 权限守卫）。
// 原为脚手架占位页（Vue logo + HelloWorld 演示），改为与全站一致的最小无权限页。
const router = useRouter();
</script>

<style scoped>
.no-auth-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  padding: var(--oslab-space-6, 32px) 16px;
}

.no-auth-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--oslab-space-2, 8px);
  max-width: 460px;
  padding: var(--oslab-space-6, 32px) var(--oslab-space-6, 32px);
  text-align: center;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-line, #e5e6eb);
  border-radius: var(--oslab-radius-lg, 8px);
}

.no-auth-code {
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
  color: var(--oslab-primary, #2447d8);
  font-variant-numeric: tabular-nums;
}

h1 {
  margin: 4px 0 0;
  font-size: 20px;
  color: var(--oslab-ink, #1d2129);
}

.no-auth-hint {
  margin: 0;
  color: var(--oslab-muted-strong, #86909c);
  font-size: var(--oslab-font-body, 14px);
  line-height: 1.7;
}

.no-auth-actions {
  display: flex;
  gap: var(--oslab-space-2, 8px);
  margin-top: var(--oslab-space-3, 16px);
}
</style>
