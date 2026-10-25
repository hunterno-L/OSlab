<template>
  <div class="task-file-list">
    <div v-for="file in state.files" :key="file.fileId" class="task-file-row">
      <a :href="file.downloadPath" target="_blank" rel="noopener">
        <icon-file /> {{ file.originalName }}
      </a>
      <span class="muted">{{ humanSize(file.size) }}</span>
      <a-button
        size="mini"
        status="danger"
        type="text"
        @click="remove(file.fileId)"
        >移除</a-button
      >
    </div>
    <a-upload
      :custom-request="upload"
      :show-file-list="false"
      accept=".doc,.docx,.pdf,.txt,.md,.png,.jpg,.jpeg,.webp"
    >
      <a-button size="small" type="outline">上传文件</a-button>
    </a-upload>
    <span class="hint"
      >支持 doc/docx/pdf/txt/md/图片，单文件不超过 10MB，最多 5 个。</span
    >
  </div>
</template>

<script setup lang="ts">
import { defineEmits, defineProps } from "vue";

const props = defineProps<{
  item: any;
  state: any;
}>();
const emit = defineEmits<{
  (event: "upload", option: any, item: any): void;
  (event: "remove", item: any, fileId: string): void;
}>();

const upload = (option: any) => {
  emit("upload", option, props.item);
};
const remove = (fileId: string) => {
  emit("remove", props.item, String(fileId));
};
const humanSize = (size?: number) => {
  if (!size) return "";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
};
</script>

<style scoped>
.task-file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.task-file-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
a {
  color: var(--oslab-primary, #2447d8);
}
.muted,
.hint {
  color: var(--oslab-muted-strong, #757069);
  font-size: 12px;
}
</style>
