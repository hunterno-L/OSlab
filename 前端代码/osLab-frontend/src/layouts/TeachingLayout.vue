<template>
  <div id="teachingLayout" class="themed-layout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
        <CourseSidebar :nav-key="'teaching'" />
        <div class="content-main">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from "@/components/GlobalHeader.vue";
import CourseSidebar from "@/components/learning/CourseSidebar.vue";
</script>

<style scoped>
.themed-layout .header {
  background: var(--oslab-panel, #ffffff);
  margin-bottom: var(--oslab-space-4, 16px);
  box-shadow: var(--oslab-divider, #f2f3f5) 1px 1px 5px;
}
.themed-layout .content {
  background: var(--oslab-canvas, #f5f7fa);
  padding: var(--oslab-space-4, 16px) 20px;
  display: flex;
  gap: var(--oslab-space-4, 16px);
  align-items: flex-start;
}
.content-main {
  flex: 1;
  min-width: 0;
}
@media (max-width: 1024px) {
  .themed-layout .content {
    flex-direction: column;
    padding: 12px;
  }
  .content-main {
    width: 100%;
  }
}
</style>
