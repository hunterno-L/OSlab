<template>
  <Transition name="os-dialog" appear>
    <div class="os-auth-overlay" @click.self="close">
      <div
        ref="panelRef"
        class="os-auth-dialog"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="isRegister ? 'os-register-tab' : 'os-login-tab'"
        @keydown.tab="trapFocus"
      >
        <div class="os-auth-dialog-head">
          <div class="os-tabs" role="tablist" aria-label="账号入口">
            <button
              id="os-login-tab"
              type="button"
              class="os-tab"
              role="tab"
              aria-controls="os-auth-panel"
              :aria-selected="!isRegister"
              :tabindex="!isRegister ? 0 : -1"
              @click="switchMode('login')"
              @keydown.right.prevent="focusMode('register')"
              @keydown.left.prevent="focusMode('login')"
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
              :tabindex="isRegister ? 0 : -1"
              @click="switchMode('register')"
              @keydown.right.prevent="focusMode('register')"
              @keydown.left.prevent="focusMode('login')"
            >
              注册
            </button>
            <span
              class="os-tab-rule"
              :class="{ 'is-register': isRegister }"
              aria-hidden="true"
            ></span>
          </div>
          <button
            type="button"
            class="os-auth-close"
            :disabled="authBusy"
            aria-label="关闭登录窗口"
            @click="close"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import {
  computed,
  inject,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
} from "vue";
import type { Ref } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const isRegister = computed(() => route.path === "/user/register");
const authBusy = inject<Ref<boolean>>("oslabAuthBusy", ref(false));
const panelRef = ref<HTMLElement | null>(null);
const previousOverflow = document.body.style.overflow;

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function focusables(): HTMLElement[] {
  return panelRef.value
    ? Array.from(panelRef.value.querySelectorAll<HTMLElement>(FOCUSABLE))
    : [];
}

function focusInitial() {
  const account = document.getElementById("oslab-account-input");
  if (account instanceof HTMLElement) {
    account.focus();
    return;
  }
  focusables()[0]?.focus();
}

function trapFocus(event: KeyboardEvent) {
  const list = focusables();
  if (!list.length) return;
  const first = list[0];
  const last = list[list.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

async function close() {
  if (authBusy.value) return;
  await router.replace("/welcome");
  await nextTick();
  document.querySelector<HTMLElement>("[data-auth-trigger]")?.focus();
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    event.preventDefault();
    void close();
  }
}

async function focusMode(mode: "login" | "register") {
  if (authBusy.value) return;
  switchMode(mode);
  await nextTick();
  document
    .getElementById(mode === "login" ? "os-login-tab" : "os-register-tab")
    ?.focus();
}

function switchMode(mode: "login" | "register") {
  if (authBusy.value) return;
  const target = mode === "register" ? "/user/register" : "/user/login";
  if (route.path === target) return;
  const query: Record<string, string> = {};
  if (typeof route.query.redirect === "string" && route.query.redirect) {
    query.redirect = route.query.redirect;
  }
  void router.push({ path: target, query });
}

onMounted(() => {
  document.body.style.overflow = "hidden";
  document.addEventListener("keydown", onKeydown);
  void nextTick(focusInitial);
});

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow;
  document.removeEventListener("keydown", onKeydown);
});
</script>

<style scoped>
.os-auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: grid;
  place-items: center;
  padding: var(--oslab-space-6);
  /* 星图之上压一层深蓝黑罩层，让白纸卡成为唯一焦点（不做磨砂玻璃） */
  background: rgba(4, 8, 20, 0.74);
}

.os-auth-dialog {
  width: min(420px, 100%);
  max-height: 90vh;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--oslab-space-6);
  background: var(--oslab-panel);
  border: 1px solid transparent;
  border-radius: var(--oslab-radius-container);
  box-shadow: 0 30px 80px rgba(2, 6, 18, 0.6);
}

.os-auth-dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--oslab-space-4);
}

.os-auth-close {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin: -4px -4px 0 0;
  border: 0;
  border-radius: var(--oslab-radius-sm);
  background: none;
  color: var(--oslab-muted-strong);
  cursor: pointer;
  touch-action: manipulation;
  transition: background-color var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}

.os-auth-close:hover {
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink);
}

.os-auth-close:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.os-auth-close svg {
  width: 18px;
  height: 18px;
}

.os-auth-close:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.os-dialog-enter-active,
.os-dialog-leave-active {
  transition: opacity var(--oslab-duration-fast) var(--oslab-ease);
}

.os-dialog-enter-from,
.os-dialog-leave-to {
  opacity: 0;
}

@media (max-width: 640px) {
  .os-auth-overlay {
    align-items: end;
    padding: 0;
  }

  .os-auth-dialog {
    width: 100%;
    max-height: 92vh;
    padding: 20px 20px calc(20px + env(safe-area-inset-bottom));
    border-radius: 14px 14px 0 0;
  }
}

@media (pointer: coarse) {
  .os-auth-close {
    width: 44px;
    height: 44px;
    margin: -6px -8px 0 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .os-dialog-enter-active,
  .os-dialog-leave-active,
  .os-auth-close {
    transition: none;
  }
}
</style>
