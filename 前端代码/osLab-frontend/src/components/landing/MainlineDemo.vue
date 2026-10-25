<template>
  <div class="demo-panel mainline-demo">
    <div class="demo-switch" role="group" aria-label="主线与补救演示">
      <button
        type="button"
        :aria-pressed="mode === 'main'"
        @click="mode = 'main'"
      >
        主线
      </button>
      <button
        type="button"
        :aria-pressed="mode === 'review'"
        @click="mode = 'review'"
      >
        补救
      </button>
    </div>

    <div class="mainline-grid">
      <ol class="chapter-rail" aria-label="教材章节">
        <li
          v-for="(chapter, index) in chapters"
          :key="chapter"
          :class="{ 'is-current': index === 0 }"
        >
          <span class="chapter-index" aria-hidden="true">{{ index + 1 }}</span>
          <span class="chapter-label">{{ chapter }}</span>
        </li>
      </ol>

      <div class="mainline-stage">
        <div class="unit-card">
          <p class="unit-path">第一章 · 计算机系统概述</p>
          <h3>当前学习单元</h3>
          <p class="unit-goal">目标：完成本章第一个学习单元。</p>
          <p class="unit-desc">
            从第一个已发布单元开始，依次完成阅读、例子、演示与测评。
          </p>
          <span class="unit-cta">继续学习 →</span>
        </div>

        <div v-if="mode === 'review'" class="remedy-card">
          <span class="remedy-label">补充单元</span>
          <p>薄弱点：页面置换。完成补充内容后回到主线位置。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const mode = ref<"main" | "review">("main");
const chapters = [
  "计算机系统概述",
  "进程与线程",
  "内存管理",
  "文件管理",
  "输入/输出（I/O）管理",
];
</script>

<style scoped>
.mainline-demo {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.mainline-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 24px;
  align-items: start;
}

.chapter-rail {
  position: relative;
  margin: 0;
  padding: 0 0 0 26px;
  list-style: none;
}

.chapter-rail::before {
  content: "";
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: 8px;
  width: 1px;
  background: var(--oslab-line-strong);
}

.chapter-rail li {
  position: relative;
  padding: 7px 0;
  color: var(--oslab-muted-strong);
  font-size: 13px;
  line-height: 1.5;
}

.chapter-index {
  position: absolute;
  top: 7px;
  left: -26px;
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: 999px;
  background: var(--oslab-panel);
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}

.chapter-rail li.is-current {
  color: var(--oslab-ink);
  font-weight: 600;
}

.chapter-rail li.is-current .chapter-index {
  border-color: var(--oslab-highlight);
  background: var(--oslab-highlight);
  color: var(--oslab-ink);
}

.chapter-rail li.is-current .chapter-label {
  padding: 1px 3px;
  background: linear-gradient(transparent 52%, var(--oslab-highlight-soft) 52%);
}

.mainline-stage {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.unit-card {
  padding: 18px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-lg);
  background: var(--oslab-panel-soft);
}

.unit-path {
  margin: 0;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 11px;
}

.unit-card h3 {
  margin: 10px 0 0;
  color: var(--oslab-ink);
  font-size: 18px;
  line-height: 1.4;
}

.unit-goal {
  margin: 10px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.6;
}

.unit-desc {
  margin: 6px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
  line-height: 1.6;
}

.unit-cta {
  display: inline-block;
  margin-top: 16px;
  color: var(--oslab-primary);
  font-size: 14px;
  font-weight: 600;
}

.remedy-card {
  padding: 14px 16px;
  border: 1px solid var(--oslab-annotation-soft);
  border-left: 3px solid var(--oslab-annotation);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-annotation-soft);
}

.remedy-label {
  color: var(--oslab-annotation);
  font-size: 12px;
  font-weight: 600;
}

.remedy-card p {
  margin: 6px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .mainline-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
</style>
