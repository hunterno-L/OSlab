<template>
  <div class="demo-panel practice-demo">
    <div class="practice-tabs" role="tablist" aria-label="练习入口">
      <button
        v-for="(tab, index) in tabs"
        :id="`practice-tab-${tab.id}`"
        :key="tab.id"
        type="button"
        role="tab"
        :aria-selected="active === tab.id"
        :tabindex="active === tab.id ? 0 : -1"
        :aria-controls="`practice-panel-${tab.id}`"
        @click="active = tab.id"
        @keydown.right.prevent="focusTab(index + 1)"
        @keydown.left.prevent="focusTab(index - 1)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div
      v-if="active === 'question'"
      id="practice-panel-question"
      role="tabpanel"
      aria-labelledby="practice-tab-question"
      class="practice-panel"
    >
      <p class="practice-question">
        缺页中断发生时，负责选择淘汰页面并完成调入的是谁？
      </p>
      <button
        v-for="option in options"
        :key="option"
        type="button"
        class="practice-option"
        :class="{ 'is-selected': selected === option }"
        :aria-pressed="selected === option"
        @click="selected = option"
      >
        {{ option }}
      </button>
    </div>

    <div
      v-else-if="active === 'code'"
      id="practice-panel-code"
      role="tabpanel"
      aria-labelledby="practice-tab-code"
      class="practice-panel"
    >
      <pre class="practice-code"><code>int lru_hit(int page) {
  for (int i = 0; i &lt; FRAMES; i++)
    if (frames[i] == page) return i;
  return -1;
}</code></pre>
      <ul class="practice-result">
        <li>编译通过</li>
        <li>样例通过</li>
        <li class="is-pending">等待提交</li>
      </ul>
    </div>

    <div
      v-else
      ref="simRef"
      id="practice-panel-sim"
      role="tabpanel"
      aria-labelledby="practice-tab-sim"
      class="practice-panel"
    >
      <div
        :key="simKey"
        class="gantt"
        :class="{ 'is-playing': simPlaying }"
        role="img"
        aria-label="CPU 调度甘特图示意"
      >
        <span class="gantt-block p1" style="--span: 3">P1</span>
        <span class="gantt-block p2" style="--span: 2">P2</span>
        <span class="gantt-block p3" style="--span: 4">P3</span>
      </div>
      <button type="button" class="gantt-replay" @click="replaySim">
        重播
      </button>
      <p class="demo-note">FCFS / SJF / RR / MLFQ 等调度算法可直接运行。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";

const tabs = [
  { id: "question", label: "题目练习" },
  { id: "code", label: "编程实验" },
  { id: "sim", label: "算法演示" },
] as const;

const active = ref<(typeof tabs)[number]["id"]>("question");
const selected = ref("");
const options = [
  "进程调度程序",
  "页表硬件",
  "操作系统缺页处理程序",
  "编译程序",
];

const simRef = ref<HTMLElement | null>(null);
const simPlaying = ref(false);
const simKey = ref(0);
let observer: IntersectionObserver | null = null;

async function watchSimVisibility() {
  await nextTick();
  if (!simRef.value) return;
  observer?.disconnect();
  observer = new IntersectionObserver(
    ([entry]) => {
      if (!entry.isIntersecting) return;
      simPlaying.value = true;
      observer?.disconnect();
      observer = null;
    },
    { threshold: 0.4 }
  );
  observer.observe(simRef.value);
}

function replaySim() {
  simPlaying.value = false;
  simKey.value += 1;
  requestAnimationFrame(() => {
    simPlaying.value = true;
  });
}

async function focusTab(index: number) {
  const next = tabs[(index + tabs.length) % tabs.length];
  active.value = next.id;
  await nextTick();
  document.getElementById(`practice-tab-${next.id}`)?.focus();
}

watch(active, (tab) => {
  if (tab === "sim") void watchSimVisibility();
});

onBeforeUnmount(() => observer?.disconnect());
</script>

<style scoped>
.practice-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--oslab-line);
}

.practice-tabs button {
  padding: 0 0 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--oslab-muted-strong);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}

.practice-tabs button[aria-selected="true"] {
  border-color: var(--oslab-primary);
  color: var(--oslab-ink);
  font-weight: 600;
}

.practice-panel {
  padding-top: 20px;
}

.practice-question {
  margin: 0 0 16px;
  color: var(--oslab-ink);
  font-size: 16px;
  line-height: 1.7;
}

.practice-option {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 11px 14px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-panel);
  color: var(--oslab-ink-soft);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.practice-option:hover {
  border-color: var(--oslab-line-strong);
}

.practice-option.is-selected {
  border-color: var(--oslab-primary);
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
}

.practice-code {
  margin: 0;
  padding: 16px;
  overflow: auto;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 12.5px;
  line-height: 1.7;
}

.practice-result {
  display: grid;
  gap: 8px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.practice-result li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--oslab-ink-soft);
  font-size: 13px;
}

.practice-result li::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--oslab-mastered);
}

.practice-result li.is-pending::before {
  background: var(--oslab-muted);
}

.gantt {
  display: flex;
  gap: 4px;
  height: 64px;
}

.gantt-block {
  flex: var(--span);
  display: grid;
  place-items: center;
  border-radius: var(--oslab-radius-sm);
  font-family: var(--oslab-font-mono);
  font-size: 12px;
  font-weight: 600;
  transform-origin: left center;
}

.gantt-block.p1 {
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
}

.gantt-block.p2 {
  background: var(--oslab-highlight);
  color: var(--oslab-ink);
}

.gantt-block.p3 {
  background: var(--oslab-annotation-soft);
  color: var(--oslab-annotation);
}

.gantt.is-playing .gantt-block {
  animation: gantt-grow 600ms var(--oslab-ease) both;
}

.gantt.is-playing .gantt-block:nth-child(2) {
  animation-delay: 120ms;
}

.gantt.is-playing .gantt-block:nth-child(3) {
  animation-delay: 240ms;
}

.gantt-replay {
  margin-top: 12px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--oslab-primary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

@keyframes gantt-grow {
  from {
    opacity: 0.35;
    transform: scaleX(0);
  }

  to {
    opacity: 1;
    transform: scaleX(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .gantt.is-playing .gantt-block {
    animation: none;
  }
}
</style>
