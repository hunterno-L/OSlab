<template>
  <div id="teachingLayout" class="themed-layout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content id="main-content" class="content">
        <CourseSidebar v-if="!hideSidebar" :nav-key="'teaching'" />
        <div class="content-main">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import GlobalHeader from "@/components/GlobalHeader.vue";
import CourseSidebar from "@/components/learning/CourseSidebar.vue";

const route = useRoute();
const hideSidebar = computed(() => route.meta.hideSidebar === true);
</script>

<style scoped>
.themed-layout {
  min-height: 100vh;
  /* 底色由 body::before 的固定渐变层承载，布局壳保持透明 */
  background-color: transparent;
}
.themed-layout .header {
  height: auto;
  line-height: normal;
  padding: var(--oslab-space-3) var(--oslab-space-3) 0;
  margin-bottom: 0;
  background: transparent;
  box-shadow: none;
}
.themed-layout .content {
  background: transparent;
  padding: var(--oslab-space-5) var(--oslab-space-8) var(--oslab-space-8);
  display: flex;
  gap: var(--oslab-space-4, 16px);
  align-items: flex-start;
}
.content-main {
  flex: 1;
  min-width: 0;
}
@media (max-width: 1024px) {
  .themed-layout .header {
    padding: var(--oslab-space-2) var(--oslab-space-2) 0;
  }
  .themed-layout .content {
    flex-direction: column;
    padding: var(--oslab-space-4) var(--oslab-space-3) var(--oslab-space-8);
  }
  .content-main {
    width: 100%;
  }
}
</style>
