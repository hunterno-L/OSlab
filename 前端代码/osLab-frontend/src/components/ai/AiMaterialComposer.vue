<template>
  <section class="ai-material-composer" aria-label="个人资料补充">
    <header class="ai-material-head">
      <h2>补充个人资料</h2>
      <span class="ai-material-count oslab-mono"
        >{{ content.length }} / 20000</span
      >
    </header>
    <p class="ai-material-hint">
      粘贴自己的笔记或 UTF-8 Markdown/TXT，只对本人可见，检索时按当前课程隔离。
    </p>
    <a-input
      v-model="title"
      class="ai-material-title"
      placeholder="资料标题（可选，默认按内容生成）"
      :max-length="200"
    />
    <a-textarea
      v-model="content"
      class="ai-material-text"
      placeholder="粘贴笔记内容…"
      :auto-size="{ minRows: 4, maxRows: 10 }"
      :max-length="20000"
    />
    <div class="ai-material-actions">
      <a-button
        type="primary"
        size="small"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >
        加入课程资料
      </a-button>
      <span
        v-if="message"
        class="ai-material-message"
        :class="{ 'is-error': messageError }"
        >{{ message }}</span
      >
    </div>
    <details v-if="materials.length" class="ai-material-list-wrap">
      <summary class="ai-material-list-summary">
        我的资料（{{ materials.length }}）
      </summary>
      <ul class="ai-material-list">
        <li
          v-for="material in materials"
          :key="material.materialId"
          class="ai-material-item"
        >
          <button
            type="button"
            class="ai-material-open"
            @click="openMaterial(material.materialId)"
          >
            {{ material.title }}
          </button>
          <span
            class="ai-material-status"
            :class="`is-${material.indexStatus.toLowerCase()}`"
            >{{ indexLabel(material.indexStatus) }}</span
          >
          <span class="ai-material-rev oslab-mono"
            >v{{ material.revision }}</span
          >
        </li>
      </ul>
    </details>
  </section>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed, onMounted, ref, watch } from "vue";
import {
  createAiMaterial,
  listAiMaterials,
  rebuildPersonalAiIndex,
  type AiMaterialVO,
} from "@/api/ai";
import { openAiMaterial } from "@/utils/aiSources";

const props = defineProps<{
  courseCode: string;
  nodeCodes?: string[];
}>();

const emit = defineEmits<{
  (event: "created", material: AiMaterialVO): void;
}>();

const title = ref("");
const content = ref("");
const submitting = ref(false);
const message = ref("");
const messageError = ref(false);
const materials = ref<AiMaterialVO[]>([]);
const listLoading = ref(false);

const canSubmit = computed(
  () =>
    Boolean(props.courseCode) &&
    content.value.trim().length > 0 &&
    !submitting.value
);

const INDEX_LABELS: Record<string, string> = {
  PENDING: "索引中",
  KEYWORD_ONLY: "关键词索引",
  EMBEDDED: "语义索引",
  FAILED: "索引失败",
};
const indexLabel = (status: string) => INDEX_LABELS[status] || status;

async function loadMaterials() {
  if (!props.courseCode) {
    materials.value = [];
    return;
  }
  listLoading.value = true;
  try {
    const response = await listAiMaterials(props.courseCode);
    materials.value =
      response.data.code === 0 && Array.isArray(response.data.data)
        ? response.data.data
        : [];
  } catch {
    materials.value = [];
  } finally {
    listLoading.value = false;
  }
}

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  message.value = "";
  messageError.value = false;
  try {
    const response = await createAiMaterial({
      courseCode: props.courseCode,
      title: title.value.trim() || undefined,
      contentMd: content.value.trim(),
      ...(props.nodeCodes?.length
        ? { nodeCodes: props.nodeCodes.join(",") }
        : {}),
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "资料保存失败");
    }
    // 个人索引重建是尽力而为：失败不回滚已保存的资料
    try {
      await rebuildPersonalAiIndex(props.courseCode);
    } catch {
      // 由资料列表的索引状态提示当前可用范围
    }
    title.value = "";
    content.value = "";
    message.value = "已加入课程个人资料，后续提问可以引用。";
    emit("created", response.data.data);
    await loadMaterials();
  } catch (e) {
    messageError.value = true;
    message.value = e instanceof Error ? e.message : "资料保存失败";
  } finally {
    submitting.value = false;
  }
}

function openMaterial(materialId: string) {
  openAiMaterial(materialId, 0);
}

onMounted(() => {
  void loadMaterials();
});
watch(
  () => props.courseCode,
  () => {
    void loadMaterials();
  }
);
</script>

<style scoped>
.ai-material-composer {
  display: flex;
  flex-direction: column;
  gap: var(--oslab-space-2);
  padding: var(--oslab-space-3) 0;
  border-top: 1px solid var(--oslab-line-soft);
  border-bottom: 1px solid var(--oslab-line-soft);
}
.ai-material-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--oslab-space-2);
}
.ai-material-head h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 16px;
  font-weight: 600;
}
.ai-material-count {
  color: var(--oslab-muted);
  font-size: 11px;
}
.ai-material-hint {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
  line-height: 1.55;
}
.ai-material-title,
.ai-material-text {
  background: var(--oslab-panel);
}
.ai-material-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oslab-space-2);
}
.ai-material-message {
  color: var(--oslab-mastered-text);
  font-size: var(--oslab-text-small);
}
.ai-material-message.is-error {
  color: var(--oslab-weak-text);
}
.ai-material-list-wrap {
  border-top: 1px solid var(--oslab-line-soft);
  padding-top: var(--oslab-space-2);
}
.ai-material-list-summary {
  cursor: pointer;
  color: var(--oslab-muted-strong);
  font-size: var(--oslab-text-small);
}
.ai-material-list {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ai-material-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}
.ai-material-open {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  color: var(--oslab-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-material-open:hover {
  text-decoration: underline;
}
.ai-material-status {
  padding: 0 5px;
  border-radius: 3px;
  background: var(--oslab-panel-muted);
  color: var(--oslab-muted-strong);
  font-size: 10px;
  line-height: 18px;
}
.ai-material-open:focus-visible,
.ai-material-list-summary:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
  border-radius: var(--oslab-radius-sm);
}
.ai-material-rev {
  color: var(--oslab-muted);
}
</style>
