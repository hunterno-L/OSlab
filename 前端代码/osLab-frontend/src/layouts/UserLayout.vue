<template>
  <div id="userLayout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <main id="main-content">
      <LandingView />
    </main>
    <AuthDialog v-if="isAuthRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed, provide, ref } from "vue";
import { useRoute } from "vue-router";
import LandingView from "@/views/landing/LandingView.vue";
import AuthDialog from "@/components/auth/AuthDialog.vue";
import "@/styles/auth-entry.css";

const route = useRoute();
const isAuthRoute = computed(
  () => route.path === "/user/login" || route.path === "/user/register"
);

const sharedAccount = ref("");
provide("oslabSharedAccount", sharedAccount);

const authBusy = ref(false);
provide("oslabAuthBusy", authBusy);
</script>

<style>
#userLayout {
  min-height: 100vh;
  background: var(--oslab-canvas);
  color: var(--oslab-ink);
  font-family: var(--oslab-font-ui);
}

#userLayout,
#userLayout *,
#userLayout *::before,
#userLayout *::after {
  box-sizing: border-box;
}
</style>
