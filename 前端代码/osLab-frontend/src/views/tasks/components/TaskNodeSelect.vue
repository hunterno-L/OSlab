<template>
  <div class="node-select">
    <div class="field-label">关联知识节点（可选，不填则不产生掌握证据）</div>
    <a-select
      :model-value="modelValue"
      multiple
      allow-search
      allow-clear
      placeholder="选择图谱真实节点"
      style="width: 100%"
      @update:model-value="update"
    >
      <a-option
        v-for="node in nodes"
        :key="node.nodeCode"
        :value="node.nodeCode"
      >
        {{ node.name }}（{{ node.nodeCode }}）
      </a-option>
    </a-select>
  </div>
</template>

<script setup lang="ts">
import { defineEmits, defineProps } from "vue";

defineProps<{
  modelValue?: string[];
  nodes: any[];
}>();
const emit = defineEmits<{
  (event: "update:modelValue", value: string[]): void;
}>();
const update = (value: string[]) => emit("update:modelValue", value || []);
</script>

<style scoped>
.node-select {
  margin-top: 10px;
}
.field-label {
  font-weight: 600;
  margin-bottom: 6px;
}
</style>
