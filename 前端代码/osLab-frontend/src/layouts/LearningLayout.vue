<template>
  <div id="learningLayout" class="themed-layout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
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
// log3 收尾 A（log3.txt 第 1 条）：wide 路由（知识图谱/实验中心/实验工作区）
// 放宽内容容器到 1760px，其余路由维持 1440px；只由路由 meta.contentWidth 驱动，
// 不整站放宽。窄于 1760px 时内容贴满并保留 24-32px 页边距（.content 的 padding）。
const isWide = computed(() => route.meta?.contentWidth === "wide");
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
.themed-layout .header {
  background: var(--oslab-panel, #ffffff);
  padding: 0;
  margin-bottom: 0;
  box-shadow: none;
}
.themed-layout .content {
  background: var(--oslab-canvas, #f7f7f5);
  padding: var(--oslab-space-6, 24px) var(--oslab-space-8, 32px);
  min-height: calc(100vh - 64px);
  box-sizing: border-box;
}
.content-width {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  display: flex;
  gap: var(--oslab-space-6, 24px);
  align-items: flex-start;
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
