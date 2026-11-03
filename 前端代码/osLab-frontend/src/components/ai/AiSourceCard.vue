<template>
  <button type="button" class="ai-source-card" @click="emit('open', source)">
    <span class="ai-source-head">
      <span class="ai-source-index oslab-mono">{{ index + 1 }}</span>
      <span class="ai-source-scope">{{ scopeLabel }}</span>
      <span class="ai-source-sep" aria-hidden="true"></span>
      <span class="ai-source-revision">{{ revisionLabel }}</span>
    </span>
    <span class="ai-source-title">{{ title }}</span>
    <span v-if="source.excerpt" class="ai-source-excerpt">{{
      source.excerpt
    }}</span>
    <span v-if="source.locator" class="ai-source-locator oslab-mono">{{
      source.locator
    }}</span>
  </button>
</template>

<script setup lang="ts">
/* eslint-disable no-undef -- Vue SFC 编译器宏由插件注入 */
import { computed } from "vue";
import type { AiSourceRef } from "@/api/ai";
import {
  aiSourceRevisionLabel,
  aiSourceScopeLabel,
  aiSourceTitle,
} from "@/utils/aiSources";

const props = defineProps<{
  source: AiSourceRef;
  index?: number;
}>();

const emit = defineEmits<{
  (event: "open", source: AiSourceRef): void;
}>();

const index = computed(() => props.index ?? 0);
const title = computed(() => aiSourceTitle(props.source));
const scopeLabel = computed(() => aiSourceScopeLabel(props.source));
const revisionLabel = computed(() => aiSourceRevisionLabel(props.source));
</script>

<style scoped>
.ai-source-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  padding: 8px 0 7px;
  border: 0;
  border-top: 1px solid var(--oslab-line-soft);
  border-radius: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--oslab-duration-fast) var(--oslab-ease);
}
.ai-source-card:hover,
.ai-source-card:focus-visible {
  background: var(--oslab-primary-soft);
}
.ai-source-card:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 1px;
  border-radius: var(--oslab-radius-sm);
}
.ai-source-head {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--oslab-muted-strong);
  font-size: 10px;
}
.ai-source-index {
  color: var(--oslab-annotation);
  font-weight: 600;
}
.ai-source-scope {
  color: var(--oslab-muted-strong);
}
.ai-source-sep {
  width: 1px;
  height: 8px;
  background: var(--oslab-line-strong);
}
.ai-source-revision {
  color: var(--oslab-muted);
}
.ai-source-title {
  color: var(--oslab-ink);
  font-size: var(--oslab-text-small);
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.ai-source-card:hover .ai-source-title {
  color: var(--oslab-primary);
}
.ai-source-excerpt {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--oslab-muted-strong);
  font-size: 11px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.ai-source-locator {
  color: var(--oslab-muted);
  font-size: 10px;
  overflow-wrap: anywhere;
}
@media (prefers-reduced-motion: reduce) {
  .ai-source-card {
    transition: none;
  }
}
</style>
