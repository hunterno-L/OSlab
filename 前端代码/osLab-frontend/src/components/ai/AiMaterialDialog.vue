<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    :footer="false"
    :width="760"
    unmount-on-close
    class="ai-material-modal"
  >
    <div v-if="loading" class="ai-material-state" aria-busy="true">
      正在读取资料…
    </div>
    <div v-else-if="error" class="ai-material-state is-error" role="alert">
      {{ error }}
    </div>
    <div v-else-if="detail" class="ai-material-body">
      <p class="ai-material-meta">
        <span>{{ detail.material.courseCode }}</span>
        <span>修订 {{ detail.material.revision }}</span>
        <span class="oslab-mono">{{ indexLabel }}</span>
      </p>
      <article class="ai-material-content">
        <div
          v-for="(paragraph, index) in paragraphs"
          :key="index"
          class="ai-material-paragraph"
          :class="{ 'is-target': index === targetParagraph }"
        >
          <MdViewer :value="paragraph" />
        </div>
      </article>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import MdViewer from "@/components/MdViewer.vue";
import { getAiMaterial, type AiMaterialDetailVO } from "@/api/ai";

interface OpenMaterialDetail {
  materialId?: string;
  paragraph?: number;
  title?: string;
}

const visible = ref(false);
const loading = ref(false);
const error = ref("");
const detail = ref<AiMaterialDetailVO | null>(null);
const targetParagraph = ref(0);
const requestedTitle = ref("");

const title = computed(
  () => detail.value?.material.title || requestedTitle.value || "个人资料段落"
);

const INDEX_LABELS: Record<string, string> = {
  PENDING: "索引待处理",
  KEYWORD_ONLY: "仅关键词索引",
  EMBEDDED: "语义索引可用",
  FAILED: "索引失败",
};
const indexLabel = computed(
  () => INDEX_LABELS[detail.value?.material.indexStatus || ""] || "索引状态未知"
);

const paragraphs = computed(() => {
  const content = detail.value?.contentMd || "";
  return content
    .split(/\n\s*\n/)
    .map((item) => item.trim())
    .filter(Boolean);
});

async function open(detailPayload: OpenMaterialDetail) {
  const materialId = detailPayload.materialId || "";
  if (!materialId) return;
  requestedTitle.value = detailPayload.title || "";
  targetParagraph.value = Math.max(0, Number(detailPayload.paragraph) || 0);
  visible.value = true;
  loading.value = true;
  error.value = "";
  detail.value = null;
  try {
    const response = await getAiMaterial(materialId);
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "资料读取失败");
    }
    detail.value = response.data.data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "资料读取失败";
  } finally {
    loading.value = false;
  }
}

function onOpenMaterial(event: Event) {
  const custom = event as CustomEvent<OpenMaterialDetail>;
  void open(custom.detail || {});
}

onMounted(() => {
  window.addEventListener("oslab:ai-open-material", onOpenMaterial);
});
onBeforeUnmount(() => {
  window.removeEventListener("oslab:ai-open-material", onOpenMaterial);
});
</script>

<style scoped>
.ai-material-state {
  padding: var(--oslab-space-6);
  color: var(--oslab-muted-strong);
  text-align: center;
}
.ai-material-state.is-error {
  color: var(--oslab-weak-text);
}
.ai-material-body {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-3);
  max-height: 68vh;
  overflow-y: auto;
}
.ai-material-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oslab-space-3);
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.ai-material-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ai-material-paragraph {
  padding: 6px 10px;
  border-radius: var(--oslab-radius-sm);
  transition: background var(--oslab-duration-fast) var(--oslab-ease),
    box-shadow var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-material-paragraph.is-target {
  background: var(--oslab-highlight-soft);
  box-shadow: inset 0 2px 0 var(--oslab-highlight-strong);
}
.ai-material-paragraph :deep(.bytemd) {
  border: none;
  padding: 0;
  background: transparent;
}
.ai-material-paragraph :deep(.markdown-body) {
  background: transparent;
  font-size: var(--oslab-font-body);
}
</style>
