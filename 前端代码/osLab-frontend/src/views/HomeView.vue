<template>
  <main class="home-view">
    <section class="home-intro">
      <div>
        <h1>首页</h1>
        <p class="intro-copy">你好，{{ displayName }}</p>
      </div>
    </section>

    <section class="course-entry" aria-labelledby="course-entry-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">当前课程</p>
          <h2 id="course-entry-title">操作系统</h2>
        </div>
      </div>
      <div class="entry-grid">
        <button
          v-for="entry in entries"
          :key="entry.path"
          class="entry-item"
          type="button"
          @click="go(entry.path)"
        >
          <span class="entry-icon" aria-hidden="true">
            <component :is="entry.icon" />
          </span>
          <span class="entry-body">
            <strong>{{ entry.label }}</strong>
          </span>
          <icon-arrow-right class="entry-arrow" aria-hidden="true" />
        </button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";

const router = useRouter();
const store = useStore();
const displayName = computed(
  () => store.state.user?.loginUser?.userName || "同学"
);

const entries = [
  {
    label: "知识图谱",
    path: "/graph",
    icon: "icon-share-alt",
  },
  {
    label: "题目练习",
    path: "/assessments",
    icon: "icon-edit",
  },
  {
    label: "实验中心",
    path: "/experiments",
    icon: "icon-code",
  },
  {
    label: "学习资源",
    path: "/resources",
    icon: "icon-bookmark",
  },
];

const go = (path: string) => router.push(path);
</script>

<style scoped>
.home-view {
  width: min(1120px, 100%);
  margin: 0 auto;
  padding: 12px 0 32px;
}

.home-intro {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  padding: 12px 0 24px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--oslab-primary, #165dff);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
}

h1,
h2 {
  margin: 0;
  color: var(--oslab-text-title, #1d2129);
}

h1 {
  font-size: 24px;
}

h2 {
  font-size: 20px;
}

.intro-copy {
  max-width: 600px;
  margin: 10px 0 0;
  color: var(--oslab-text-body, #4e5969);
  line-height: 1.7;
}

.course-entry {
  margin-top: 8px;
  padding: 24px 0 28px;
  border-top: 1px solid var(--oslab-border, #e5e6eb);
}

.section-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--oslab-divider, #f2f3f5);
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.entry-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  text-align: left;
  background: var(--oslab-panel-sub, #f7f8fa);
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-md, 6px);
  color: inherit;
  cursor: pointer;
}

.entry-item:hover,
.entry-item:focus-visible {
  border-color: var(--oslab-primary, #165dff);
  outline: none;
}

.entry-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  color: var(--oslab-primary, #165dff);
  background: #edf3ff;
  border-radius: 6px;
}

.entry-body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.entry-body strong {
  color: var(--oslab-text-title, #1d2129);
  font-size: 14px;
}

.entry-arrow {
  flex: 0 0 auto;
  color: var(--oslab-text-secondary, #86909c);
}

@media (max-width: 720px) {
  .home-intro,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .home-intro {
    padding: 12px 0 20px;
  }

  .course-entry {
    padding: 20px 0;
  }

  .entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>
