<template>
  <aside
    class="annotation-note"
    :class="tone === 'primary' ? 'is-primary' : 'is-annotation'"
    :aria-label="title || `批注 ${index}`"
  >
    <span class="annotation-note__index" aria-hidden="true">{{ index }}</span>
    <div class="annotation-note__body">
      <p v-if="title" class="annotation-note__title">{{ title }}</p>
      <p class="annotation-note__text"><slot /></p>
    </div>
  </aside>
</template>

<script setup lang="ts">
// eslint-disable-next-line no-undef
defineProps<{
  index: number | string;
  title?: string;
  tone?: "annotation" | "primary";
}>();
</script>

<style scoped>
.annotation-note {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  margin: 10px 0;
  padding: 9px 11px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
}
.annotation-note__index {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--oslab-annotation);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.annotation-note.is-primary {
  border-color: var(--oslab-primary);
  background: var(--oslab-highlight-soft);
}
.annotation-note.is-primary .annotation-note__index {
  background: var(--oslab-primary);
}
.annotation-note__title {
  margin: 0 0 2px;
  color: var(--oslab-ink);
  font-size: 11.5px;
  font-weight: 600;
}
.annotation-note__text {
  margin: 0;
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 12px;
  line-height: 1.55;
}
</style>
