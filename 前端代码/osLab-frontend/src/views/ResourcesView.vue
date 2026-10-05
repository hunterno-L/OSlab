<template>
  <div class="resources-view">
    <div class="page-intro">
      <div>
        <div class="eyebrow">RESOURCES</div>
        <h1>学习资源</h1>
      </div>
      <div class="intro-metric">
        <strong>{{ visibleResources.length }}</strong>
        <span>项资料</span>
      </div>
    </div>

    <a-alert v-if="nodeName" class="context-alert">
      当前节点：<b>{{ nodeName }}</b>
      <a-button type="text" size="small" @click="clearNode"
        >清除节点筛选</a-button
      >
    </a-alert>

    <section class="filter-panel">
      <a-input-search
        v-model="keyword"
        allow-clear
        placeholder="搜索资料标题或简介"
        search-button
        @search="syncQuery"
      />
      <a-select
        v-model="chapter"
        placeholder="全部章节"
        allow-clear
        @change="syncQuery"
      >
        <a-option v-for="item in chapters" :key="item" :value="item">{{
          item
        }}</a-option>
      </a-select>
      <a-select
        v-model="nodeName"
        placeholder="全部节点"
        allow-clear
        allow-search
        @change="syncQuery"
      >
        <a-option v-for="node in nodeNames" :key="node" :value="node">
          {{ node }}
        </a-option>
      </a-select>
      <a-select
        v-model="type"
        placeholder="全部类型"
        allow-clear
        @change="syncQuery"
      >
        <a-option value="HTML">HTML 页面</a-option>
        <a-option value="DOCUMENT">文档</a-option>
      </a-select>
      <a-tooltip content="重置筛选">
        <a-button aria-label="重置筛选" @click="resetFilters">
          <template #icon><icon-refresh /></template>
        </a-button>
      </a-tooltip>
    </section>

    <section class="resource-grid">
      <article
        v-for="resource in visibleResources"
        :key="resource.id"
        class="resource-card"
      >
        <div class="resource-card-head">
          <div>
            <div class="resource-kicker">{{ resource.chapter }}</div>
            <h2>{{ resource.title }}</h2>
          </div>
          <a-tag color="arcoblue">{{ resource.type }}</a-tag>
        </div>
        <p class="resource-description">{{ resource.description }}</p>
        <div class="resource-meta">
          <a-button
            v-for="node in resource.nodeNames"
            :key="node"
            type="text"
            size="small"
            class="node-link"
            @click="goNode(node)"
          >
            {{ node }}
          </a-button>
        </div>
        <div class="resource-actions">
          <a-button type="primary" @click="preview(resource)"
            ><template #icon><icon-book /></template>预览资料</a-button
          >
          <a-button type="text" @click="openResource(resource.url)"
            ><template #icon><icon-launch /></template>新窗口打开</a-button
          >
        </div>
      </article>
      <a-empty
        v-if="!visibleResources.length"
        description="没有符合条件的资料"
      />
    </section>

    <a-drawer
      v-model:visible="previewVisible"
      :title="activeResource?.title || '资料预览'"
      :width="'min(1120px, 100vw)'"
      unmount-on-close
    >
      <iframe
        v-if="activeResource"
        class="resource-frame"
        :src="activeResource.url"
        :title="activeResource.title"
      />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  getLearningResources,
  getResourcesForNode,
  type LearningResource,
  type LearningResourceType,
} from "@/data/learningResources";

const route = useRoute();
const router = useRouter();
const resources = getLearningResources();
const keyword = ref("");
const chapter = ref("");
const type = ref<LearningResourceType | "">("");
const nodeName = ref("");
const previewVisible = ref(false);
const activeResource = ref<LearningResource | null>(null);

const chapters = computed(() =>
  Array.from(new Set(resources.map((resource) => resource.chapter))).sort(
    (a, b) => a.localeCompare(b, "zh-CN")
  )
);

const nodeNames = computed(() =>
  Array.from(new Set(resources.flatMap((resource) => resource.nodeNames))).sort(
    (a, b) => a.localeCompare(b, "zh-CN")
  )
);

const visibleResources = computed(() => {
  const nodeResources = nodeName.value
    ? getResourcesForNode(nodeName.value)
    : resources;
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return nodeResources.filter((resource) => {
    const keywordMatches =
      !normalizedKeyword ||
      `${resource.title} ${resource.description}`
        .toLowerCase()
        .includes(normalizedKeyword);
    const chapterMatches = !chapter.value || resource.chapter === chapter.value;
    const typeMatches = !type.value || resource.type === type.value;
    return keywordMatches && chapterMatches && typeMatches;
  });
});

const routeValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";

const syncFromRoute = () => {
  const q = route.query;
  nodeName.value = routeValue(q.nodeName);
  keyword.value = routeValue(q.search);
  chapter.value = routeValue(q.chapter);
  const queryType = routeValue(q.type) as LearningResourceType;
  type.value =
    queryType === "HTML" || queryType === "DOCUMENT" ? queryType : "";
};

const syncQuery = () => {
  router.replace({
    query: {
      ...route.query,
      nodeName: nodeName.value || undefined,
      search: keyword.value || undefined,
      chapter: chapter.value || undefined,
      type: type.value || undefined,
    },
  });
};

const resetFilters = () => {
  keyword.value = "";
  chapter.value = "";
  type.value = "";
  nodeName.value = "";
  syncQuery();
};

const clearNode = () => {
  const nextQuery = { ...route.query };
  delete nextQuery.nodeName;
  router.replace({ query: nextQuery });
};

const goNode = (name: string) => {
  router.push({ path: `/knowledge/node/${encodeURIComponent(name)}` });
};

const preview = (resource: LearningResource) => {
  activeResource.value = resource;
  previewVisible.value = true;
};

const openResource = (url: string) => {
  window.open(url, "_blank", "noopener,noreferrer");
};

watch(
  () => route.query,
  () => syncFromRoute(),
  { deep: true, immediate: true }
);
</script>

<style scoped>
.resources-view {
  max-width: 1160px;
  margin: 0 auto;
  padding: 4px 0 28px;
}
.page-intro {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  margin-bottom: 18px;
}
.eyebrow {
  color: var(--oslab-primary, #165dff);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}
.page-intro h1 {
  margin: 4px 0 6px;
  color: var(--oslab-text-title, #1d2129);
  font-size: 24px;
}
.page-intro p {
  margin: 0;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 13px;
}
.intro-metric {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.intro-metric strong {
  color: var(--oslab-text-title, #1d2129);
  font-size: 24px;
  line-height: 1;
}
.context-alert {
  margin-bottom: 14px;
}
.filter-panel {
  display: grid;
  grid-template-columns: minmax(210px, 1fr) 160px 180px 130px auto;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 16px;
  background: var(--oslab-panel, #fff);
  border-bottom: 1px solid var(--oslab-border, #e5e6eb);
}
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.resource-card {
  display: flex;
  flex-direction: column;
  min-height: 220px;
  padding: 16px;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-lg, 8px);
}
.resource-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.resource-kicker {
  margin-bottom: 4px;
  color: var(--oslab-primary, #165dff);
  font-size: 12px;
}
.resource-card h2 {
  margin: 0;
  color: var(--oslab-text-title, #1d2129);
  font-size: 17px;
}
.resource-description {
  flex: 1;
  margin: 12px 0;
  color: var(--oslab-text-body, #4e5969);
  font-size: 13px;
  line-height: 1.7;
}
.resource-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding-top: 8px;
  border-top: 1px solid var(--oslab-divider, #f2f3f5);
}
.node-link {
  padding: 0 4px;
}
.resource-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.resource-frame {
  width: 100%;
  height: calc(100vh - 112px);
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-md, 6px);
}
@media (max-width: 820px) {
  .filter-panel {
    grid-template-columns: 1fr 1fr;
  }
  .filter-panel :deep(.arco-input-search) {
    grid-column: 1 / -1;
  }
  .resource-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .page-intro,
  .resource-card-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .intro-metric {
    align-items: flex-start;
  }
  .filter-panel {
    grid-template-columns: 1fr;
  }
  .filter-panel :deep(.arco-input-search) {
    grid-column: auto;
  }
}
</style>
