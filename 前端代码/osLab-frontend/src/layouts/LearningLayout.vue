<template>
  <div id="learningLayout" class="themed-layout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content id="main-content" class="content">
        <div
          v-if="pageTitle"
          class="page-title-row"
          :class="{ 'content-wide': isWide }"
        >
          <p class="page-eyebrow" aria-hidden="true">ZhiCore / LEARNING</p>
          <h1 class="page-title">{{ pageTitle }}</h1>
        </div>
        <div class="content-width" :class="{ 'content-wide': isWide }">
          <CourseSidebar v-if="showSidebar" :nav-key="navKey" />
          <div class="content-main">
            <router-view />
          </div>
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
import {
  APP_NAVIGATION,
  AUXILIARY_NAVIGATION,
  resolveNavKey,
} from "@/navigation/appNavigation";

const route = useRoute();
const navKey = computed(
  () => (route.meta?.navKey as string) || resolveNavKey(route.path) || ""
);
// Wide routes use the available workspace width.
const isWide = computed(() => route.meta?.contentWidth === "wide");
const pageTitle = computed(() => {
  const title = route.meta?.pageTitle;
  return typeof title === "string" && title.trim() ? title : "";
});
const showSidebar = computed(() => {
  // 显式配置优先：路由 meta.hideSidebar 为 true 时关闭课程侧栏
  // （如题目练习页），主内容占满整行，不留空白列。
  if (route.meta?.hideSidebar === true) return false;
  const section = [...APP_NAVIGATION, ...AUXILIARY_NAVIGATION].find(
    (item) => item.key === navKey.value
  );
  return Boolean(section?.children?.length);
});
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
  min-height: calc(100vh - 84px);
  box-sizing: border-box;
}
.content-width {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.page-title-row {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto 16px;
}
.page-title-row.content-wide {
  max-width: 1760px;
}
.page-eyebrow {
  margin: 0 0 2px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.page-title-row .page-title {
  display: inline;
  margin: 0;
  padding: 0 4px;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: var(--oslab-text-page);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0;
  background: linear-gradient(
    transparent 56%,
    var(--oslab-highlight) 56%,
    var(--oslab-highlight) 92%,
    transparent 92%
  );
}
@media (max-width: 1024px) {
  .themed-layout .header {
    padding: 8px 8px 0;
  }
  .page-title-row .page-title {
    font-size: 28px;
  }
}
/* log3 收尾 A：仅 wide 路由放宽；小于 1760px 时由 .content 的
   24/32px padding 提供页边距，不新增任何填充模块 */
.content-width.content-wide {
  max-width: 1760px;
}
.content-main {
  flex: 1;
  min-width: 0;
}
@media (max-width: 1024px) {
  .themed-layout .content {
    padding: var(--oslab-space-4, 16px) var(--oslab-space-3, 12px)
      var(--oslab-space-8, 32px);
  }
  .content-width {
    flex-direction: column;
  }
  .content-main {
    width: 100%;
  }
}
</style>
