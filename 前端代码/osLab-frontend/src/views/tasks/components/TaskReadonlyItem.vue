<template>
  <div class="readonly-item">
    <MdViewer
      v-if="content.contentMd || content.content || content.description"
      :value="content.contentMd || content.content || content.description"
    />
    <div v-if="content.options?.length" class="lines">
      <div v-for="option in content.options" :key="option.key">
        <strong>{{ option.key }}.</strong> {{ option.value }}
      </div>
    </div>
    <div v-if="content.files?.length" class="lines">
      <div v-for="file in content.files" :key="file.fileId">
        <icon-file /> {{ file.originalName }}
      </div>
    </div>
    <div class="lines">
      <div v-if="content.type">
        <span class="label">题型</span>{{ content.type }}
      </div>
      <div v-if="content.analysis">
        <span class="label">解析</span>{{ content.analysis }}
      </div>
      <div v-if="content.judgeConfig">
        <span class="label">判题限制</span>
        {{ content.judgeConfig.timeLimit }} ms /
        {{ content.judgeConfig.memoryLimit }} MB
      </div>
      <div v-if="content.manualReview">
        <span class="label">评阅方式</span>人工评阅
      </div>
      <div v-if="content.judgeCase?.length">
        <span class="label">隐藏用例</span>{{ content.judgeCase.length }} 条
      </div>
      <div v-if="content.samples?.length">
        <span class="label">公开样例</span>{{ content.samples.length }} 条
      </div>
      <div v-if="content.nodeCodes?.length">
        <span class="label">知识节点</span>{{ content.nodeCodes.join("、") }}
      </div>
      <div v-if="content.requireText">
        <span class="label">提交要求</span>文字答复
      </div>
      <div v-if="content.requireFile">
        <span class="label">提交要求</span>文件上传
      </div>
      <div v-if="content.requireConfirm">
        <span class="label">提交要求</span>阅读确认
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps } from "vue";
import MdViewer from "@/components/MdViewer.vue";
defineProps<{ content: any; type: string }>();
</script>

<style scoped>
.readonly-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.label {
  display: inline-block;
  min-width: 76px;
  color: var(--oslab-muted-strong, #757069);
}
</style>
