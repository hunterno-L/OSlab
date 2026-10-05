<template>
  <div id="userLayout">
    <header class="os-header">
      <div class="os-brand">
        <img src="../assets/oj-logo.svg" alt="OSLab 标识" class="os-logo" />
        <span class="os-wordmark">OSLab</span>
        <span class="os-brand-note">操作系统课程设计实验平台</span>
      </div>
      <div class="os-header-actions">
        <button type="button" class="os-text-action" @click="scrollToAbout">
          平台简介
        </button>
        <button
          type="button"
          class="os-text-action os-enter-action"
          @click="focusAccount"
        >
          <span>进入学习</span>
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
      </div>
    </header>

    <main class="os-main">
      <!-- 品牌与简介：左栏 -->
      <section class="os-story" aria-labelledby="os-auth-brand-title">
        <p class="os-kicker">OPERATING SYSTEMS / LEARNING LAB</p>
        <h1 id="os-auth-brand-title">OSLab<span>操作系统智能学习平台</span></h1>
        <p class="os-description">
          连接知识图谱、概念练习与编程实验，<br />让每一次学习，都有清晰的下一步。
        </p>
        <div class="os-flow-stage">
          <AuthFlowCanvas ref="flowRef" :paused="!motionPlaying" />
        </div>
        <div class="os-flow-caption">从理解一个知识点，到完成一次实验。</div>
      </section>

      <!-- 表单区：右栏，页签切换登录/注册 -->
      <section class="os-form-region" aria-label="登录与注册">
        <div class="os-tabs" role="tablist" aria-label="账号入口">
          <button
            id="os-login-tab"
            type="button"
            class="os-tab"
            role="tab"
            aria-controls="os-auth-panel"
            :aria-selected="!isRegister"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            id="os-register-tab"
            type="button"
            class="os-tab"
            role="tab"
            aria-controls="os-auth-panel"
            :aria-selected="isRegister"
            @click="switchMode('register')"
          >
            注册
          </button>
          <span
            class="os-tab-rule"
            :class="{ 'is-register': isRegister }"
            aria-hidden="true"
          ></span>
        </div>
        <router-view v-slot="{ Component }">
          <transition name="os-panel" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </main>

    <section ref="aboutRef" class="os-capabilities" aria-label="平台简介">
      <div class="os-capability">
        <span class="os-capability-number">01</span>
        <div>
          <strong>知识图谱</strong>
          <p>在关系中理解知识</p>
        </div>
      </div>
      <div class="os-capability">
        <span class="os-capability-number">02</span>
        <div>
          <strong>题目与实验</strong>
          <p>用练习连接理解与实践</p>
        </div>
      </div>
      <div class="os-capability">
        <span class="os-capability-number">03</span>
        <div>
          <strong>学习轨迹</strong>
          <p>让每一次进步有迹可循</p>
        </div>
      </div>
    </section>

    <footer class="os-footer">
      <span>OSLab · 操作系统学习与实践</span>
      <button
        type="button"
        class="os-motion"
        :aria-pressed="motionPlaying"
        @click="motionPlaying = !motionPlaying"
      >
        <svg
          v-if="motionPlaying"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <rect x="6" y="4" width="4" height="16" rx="1" />
          <rect x="14" y="4" width="4" height="16" rx="1" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="m7 4 12 8-12 8Z" />
        </svg>
        <span>{{ motionPlaying ? "暂停动效" : "播放动效" }}</span>
      </button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AuthFlowCanvas from "@/components/auth/AuthFlowCanvas.vue";

const route = useRoute();
const router = useRouter();

const isRegister = computed(() => route.path === "/user/register");

// 跨登录/注册页签共享的账号输入：切换模式保留账号，密码在子视图中清空
const sharedAccount = ref("");
provide("oslabSharedAccount", sharedAccount);

// 提交进行中：禁用页签切换，避免请求途中丢失状态
const authBusy = ref(false);
provide("oslabAuthBusy", authBusy);

// 成功提交后让知识流向前推进一小段（总额外延迟由子视图控制在 200ms 内）
const flowRef = ref<{ advance: (amount: number) => void } | null>(null);
function advanceFlow() {
  flowRef.value?.advance(0.06);
}
provide("oslabAdvanceFlow", advanceFlow);

// 动效控制：尊重 prefers-reduced-motion，默认跟随系统
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const motionPlaying = ref(!reducedMotion.matches);

function switchMode(mode: "login" | "register") {
  if (authBusy.value) return;
  const target = mode === "register" ? "/user/register" : "/user/login";
  if (route.path === target) return;
  const query: Record<string, string> = {};
  const redirect = route.query.redirect;
  if (typeof redirect === "string" && redirect) {
    query.redirect = redirect;
  }
  router.push({ path: target, query });
}

const aboutRef = ref<HTMLElement | null>(null);

function scrollToAbout() {
  aboutRef.value?.scrollIntoView({
    block: "nearest",
    behavior: reducedMotion.matches ? "auto" : "smooth",
  });
}

function focusAccount() {
  const input = document.getElementById("oslab-account-input");
  input?.scrollIntoView({
    block: "center",
    behavior: reducedMotion.matches ? "auto" : "smooth",
  });
  input?.focus();
}
</script>

<style>
/* 认证入口全局样式：以 #userLayout 命名空间限定，供布局与登录/注册子视图共用 */
#userLayout {
  --os-paper: #fafaf8;
  --os-ink: #171a1d;
  --os-body: #525962;
  --os-muted: #777f86;
  --os-line: #dadfe3;
  --os-blue: #2059c9;
  min-height: 100vh;
  background: var(--os-paper);
  color: var(--os-ink);
  font-family: "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
  letter-spacing: 0;
}
#userLayout,
#userLayout * {
  box-sizing: border-box;
}
#userLayout button,
#userLayout input {
  font: inherit;
  letter-spacing: 0;
}
#userLayout button {
  cursor: pointer;
}
#userLayout button:focus-visible {
  outline: 2px solid var(--os-blue);
  outline-offset: 2px;
}

/* ---------- 顶部 ---------- */
#userLayout .os-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 25px 5%;
}
#userLayout .os-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
#userLayout .os-logo {
  width: 31px;
  height: 31px;
  flex: none;
}
#userLayout .os-wordmark {
  font-size: 22px;
  font-weight: 600;
}
#userLayout .os-brand-note {
  font-size: 12px;
  color: var(--os-body);
  border-left: 1px solid var(--os-line);
  padding-left: 20px;
  margin-left: 8px;
}
#userLayout .os-header-actions {
  display: flex;
  align-items: center;
  gap: 26px;
}
#userLayout .os-text-action {
  border: 0;
  padding: 8px 0;
  background: none;
  color: var(--os-body);
  font-size: 12px;
}
#userLayout .os-enter-action {
  color: var(--os-ink);
  display: flex;
  align-items: center;
  gap: 9px;
}
#userLayout .os-enter-action svg {
  width: 15px;
  height: 15px;
}

/* ---------- 主区网格 ---------- */
#userLayout .os-main {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(270px, 1fr);
  gap: 58px;
  padding: 58px 7% 42px;
  align-items: start;
}
#userLayout .os-story {
  min-width: 0;
}
#userLayout .os-kicker {
  font: 11px/1.4 Consolas, monospace;
  color: var(--os-muted);
  margin: 0 0 17px;
}
#userLayout h1 {
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 78px;
  font-weight: 600;
  line-height: 1.08;
  margin: 0;
  letter-spacing: 0;
}
#userLayout h1 span {
  display: block;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 28px;
  font-weight: 500;
  line-height: 1.5;
  margin-top: 15px;
}
#userLayout .os-description {
  font-size: 15px;
  line-height: 1.9;
  color: var(--os-body);
  margin: 26px 0 0;
}
#userLayout .os-flow-stage {
  width: 100%;
  aspect-ratio: 2.5;
  min-height: 235px;
  margin-top: 24px;
  position: relative;
}
#userLayout .oslab-flow-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
#userLayout .os-flow-caption {
  font-size: 11px;
  color: var(--os-muted);
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 15px;
}
#userLayout .os-flow-caption::before {
  content: "";
  width: 20px;
  height: 2px;
  background: var(--os-blue);
}

/* ---------- 表单区 ---------- */
#userLayout .os-form-region {
  padding-top: 21px;
  width: 100%;
  max-width: 386px;
  justify-self: end;
  min-height: 540px;
}
#userLayout .os-tabs {
  position: relative;
  display: flex;
  gap: 41px;
  margin-bottom: 34px;
  width: 128px;
}
#userLayout .os-tab {
  border: 0;
  background: none;
  padding: 0 0 15px;
  font-size: 14px;
  color: var(--os-muted);
}
#userLayout .os-tab[aria-selected="true"] {
  color: var(--os-ink);
  font-weight: 500;
}
#userLayout .os-tab-rule {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 28px;
  height: 2px;
  background: var(--os-blue);
  transition: transform 240ms cubic-bezier(0.2, 0.7, 0.2, 1);
}
#userLayout .os-tab-rule.is-register {
  transform: translateX(69px);
}
#userLayout .os-auth-panel h2 {
  font-size: 26px;
  line-height: 1.4;
  font-weight: 500;
  margin: 0;
}
#userLayout .os-form-subtitle {
  font-size: 12px;
  line-height: 1.7;
  color: var(--os-muted);
  margin: 13px 0 31px;
  min-height: 41px;
}
#userLayout .os-field {
  display: block;
  margin: 0 0 24px;
}
#userLayout .os-field-label {
  display: block;
  font-size: 12px;
  margin-bottom: 12px;
}
#userLayout .os-input-wrap {
  position: relative;
}
#userLayout input[type="text"],
#userLayout input[type="password"] {
  width: 100%;
  height: 48px;
  font-size: 14px;
  color: var(--os-ink);
  border: 1px solid var(--os-line);
  border-radius: 6px;
  background: #fff;
  padding: 0 15px;
  transition: border-color 180ms, box-shadow 180ms;
}
#userLayout input::placeholder {
  color: #959da5;
}
#userLayout input:focus {
  outline: none;
  border-color: var(--os-blue);
  box-shadow: 0 0 0 2px #2059c914;
}
#userLayout .os-password {
  padding-right: 46px !important;
}
#userLayout .os-eye {
  position: absolute;
  right: 5px;
  top: 5px;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  background: none;
  border: 0;
  color: var(--os-muted);
}
#userLayout .os-eye svg {
  width: 17px;
  height: 17px;
}
/* 确认密码字段：注册视图挂载后展开（280ms） */
#userLayout .os-confirm-row {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  transition: grid-template-rows 280ms cubic-bezier(0.2, 0.7, 0.2, 1),
    opacity 180ms;
}
#userLayout .os-confirm-row.is-expanded {
  grid-template-rows: 1fr;
  opacity: 1;
}
#userLayout .os-confirm-inner {
  overflow: hidden;
  min-height: 0;
}
#userLayout .os-form-extra {
  min-height: 31px;
  margin: 0 0 18px;
  font-size: 11px;
  color: var(--os-body);
}
#userLayout .os-remember {
  display: flex;
  align-items: center;
  gap: 9px;
}
#userLayout .os-remember input {
  accent-color: var(--os-blue);
  width: 14px;
  height: 14px;
  margin: 0;
}
#userLayout .os-register-hint {
  color: var(--os-muted);
  line-height: 1.6;
}
#userLayout .os-submit {
  background: var(--os-ink);
  color: #fff;
  height: 50px;
  width: 100%;
  border: 0;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 21px;
  font-size: 14px;
  transition: background 180ms, transform 180ms;
}
#userLayout .os-submit:hover {
  background: #282d34;
}
#userLayout .os-submit:active {
  transform: translateY(1px);
}
#userLayout .os-submit svg {
  width: 17px;
  height: 17px;
}
#userLayout .os-submit:disabled {
  cursor: wait;
  background: #3c434c;
}
#userLayout .os-account-note {
  color: var(--os-muted);
  font-size: 10px;
  line-height: 1.8;
  margin: 22px 0 0;
}
#userLayout .os-error {
  font-size: 11px;
  color: #a9695d;
  margin: 12px 0 0;
}

/* ---------- 页签切换过渡 ---------- */
#userLayout .os-panel-enter-active,
#userLayout .os-panel-leave-active {
  transition: opacity 180ms cubic-bezier(0.2, 0.7, 0.2, 1),
    transform 180ms cubic-bezier(0.2, 0.7, 0.2, 1);
}
#userLayout .os-panel-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
#userLayout .os-panel-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---------- 能力区 ---------- */
#userLayout .os-capabilities {
  margin: 0 7%;
  padding: 26px 0 23px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 32px;
  border-top: 1px solid var(--os-line);
}
#userLayout .os-capability {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 10px;
  align-items: start;
}
#userLayout .os-capability-number {
  font: 11px/1.6 Consolas, monospace;
  color: var(--os-blue);
}
#userLayout .os-capability strong {
  font-size: 13px;
  font-weight: 500;
}
#userLayout .os-capability p {
  margin: 8px 0 0;
  color: var(--os-muted);
  font-size: 11px;
  line-height: 1.6;
}

/* ---------- 页脚 ---------- */
#userLayout .os-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 7% 25px;
  gap: 20px;
  font-size: 10px;
  color: var(--os-muted);
}
#userLayout .os-motion {
  border: 0;
  padding: 0;
  background: none;
  color: var(--os-muted);
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 10px;
}
#userLayout .os-motion svg {
  width: 13px;
  height: 13px;
}

/* ---------- 中屏 ---------- */
@media (max-width: 1100px) {
  #userLayout .os-header {
    padding: 23px 4%;
  }
  #userLayout .os-main {
    padding: 45px 5% 34px;
    gap: 34px;
    grid-template-columns: minmax(0, 1.55fr) minmax(270px, 1fr);
  }
  #userLayout h1 {
    font-size: 64px;
  }
  #userLayout h1 span {
    font-size: 24px;
  }
  #userLayout .os-description {
    font-size: 13px;
  }
  #userLayout .os-brand-note {
    display: none;
  }
  #userLayout .os-capabilities {
    margin: 0 5%;
  }
  #userLayout .os-footer {
    padding: 20px 5% 25px;
  }
}

/* ---------- 窄屏：品牌 → 缩短的知识流 → 表单 → 简介 ---------- */
@media (max-width: 740px) {
  #userLayout .os-header {
    padding: 20px 6%;
  }
  #userLayout .os-header-actions {
    gap: 20px;
  }
  #userLayout .os-main {
    display: flex;
    flex-direction: column;
    padding: 25px 7% 35px;
    gap: 30px;
  }
  #userLayout .os-story {
    width: 100%;
  }
  #userLayout h1 {
    font-size: 56px;
  }
  #userLayout h1 span {
    font-size: 23px;
  }
  #userLayout .os-description {
    font-size: 14px;
    margin-top: 20px;
  }
  #userLayout .os-flow-stage {
    min-height: 155px;
    margin-top: 10px;
    aspect-ratio: 2.55;
  }
  #userLayout .os-flow-caption {
    margin-top: 2px;
  }
  #userLayout .os-form-region {
    align-self: center;
    max-width: 430px;
    min-height: 0;
    padding-top: 0;
  }
  #userLayout .os-form-subtitle {
    min-height: 0;
    margin-bottom: 26px;
  }
  #userLayout .os-capabilities {
    margin: 0 7%;
    gap: 18px;
    grid-template-columns: 1fr;
  }
  #userLayout .os-footer {
    padding: 16px 7% 25px;
    flex-wrap: wrap;
  }
  /* 移动端输入字号保持 16px，避免聚焦缩放 */
  #userLayout input[type="text"],
  #userLayout input[type="password"] {
    font-size: 16px;
  }
}

/* ---------- 动效偏好 ---------- */
@media (prefers-reduced-motion: reduce) {
  #userLayout * {
    transition: none !important;
  }
}
</style>
