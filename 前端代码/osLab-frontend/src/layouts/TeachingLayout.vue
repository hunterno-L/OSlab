<template>
  <div id="teachingLayout" class="themed-layout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content id="main-content" class="content">
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
.themed-layout {
  min-height: 100vh;
  background-color: var(--oslab-canvas);
  background-image: radial-gradient(
    rgba(32, 36, 43, 0.075) 1px,
    transparent 1px
  );
  background-size: 22px 22px;
}
.themed-layout .header {
  height: auto;
  line-height: normal;
  padding: 12px 12px 0;
  margin-bottom: 0;
  background: transparent;
  box-shadow: none;
}
.themed-layout .content {
  background: transparent;
  padding: 20px 32px 32px;
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
    padding: 8px 8px 0;
  }
  .themed-layout .content {
    flex-direction: column;
    padding: 16px 12px 32px;
  }
  .content-main {
    width: 100%;
  }
}
</style>
