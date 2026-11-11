<template>
  <section class="profile-card" aria-labelledby="profile-title">
    <h2 id="profile-title">我的学习画像</h2>

    <div
      v-if="state === 'loading'"
      class="profile-placeholder"
      aria-busy="true"
    >
      正在加载学习画像…
    </div>
    <div v-else-if="state === 'error'" class="profile-placeholder">
      暂时无法加载学习画像
      <button type="button" @click="emit('retry')">重试</button>
    </div>
    <template v-else>
      <div v-if="total > 0" class="profile-bar" aria-hidden="true">
        <span
          v-for="row in rows"
          :key="row.key"
          :style="{
            width: `${(row.value / total) * 100}%`,
            background: row.color,
          }"
        ></span>
      </div>
      <p v-else class="profile-empty">暂无学习证据</p>

      <ul class="profile-counts">
        <li v-for="row in rows" :key="row.key">
          <i :style="{ background: row.color }" aria-hidden="true"></i>
          {{ row.label }}<b>{{ row.value }}</b>
        </li>
      </ul>

      <p v-if="(profile?.evidenceCount ?? 0) > 0" class="profile-evidence">
        有效证据 {{ profile?.evidenceCount }} 条
      </p>

      <router-link v-if="recommend" class="profile-next" :to="nodeHref">
        下一步：{{ recommend }}
      </router-link>

      <div class="profile-links">
        <router-link
          :to="{ path: '/records', query: courseCode ? { courseCode } : {} }"
          >查看学习记录</router-link
        >
        <router-link
          v-if="hasRemedy"
          :to="{
            path: '/records',
            query: { view: 'review', ...(courseCode ? { courseCode } : {}) },
          }"
        >
          去巩固
        </router-link>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";

// eslint-disable-next-line no-undef
const props = defineProps<{
  profile: {
    statusCounts?: Record<"GREEN" | "YELLOW" | "RED" | "GRAY", number>;
    evidenceCount?: number;
    recommendedNodeName?: string | null;
  } | null;
  recommend: string | null;
  recommendCode?: string | null;
  courseCode?: string;
  state: "loading" | "ready" | "empty" | "error";
}>();

// eslint-disable-next-line no-undef
const emit = defineEmits<{ retry: [] }>();

const rows = computed(() => {
  const counts = props.profile?.statusCounts;
  return [
    {
      key: "GREEN",
      label: "已掌握",
      value: counts?.GREEN ?? 0,
      color: "var(--oslab-mastered)",
    },
    {
      key: "YELLOW",
      label: "待巩固",
      value: counts?.YELLOW ?? 0,
      color: "var(--oslab-review)",
    },
    {
      key: "RED",
      label: "薄弱",
      value: counts?.RED ?? 0,
      color: "var(--oslab-weak)",
    },
    {
      key: "GRAY",
      label: "未学习",
      value: counts?.GRAY ?? 0,
      color: "var(--oslab-unseen)",
    },
  ];
});

const total = computed(() =>
  rows.value.reduce((sum, row) => sum + row.value, 0)
);

const hasRemedy = computed(() =>
  rows.value.some(
    (row) => (row.key === "YELLOW" || row.key === "RED") && row.value > 0
  )
);

const nodeHref = computed(() => ({
  path: props.recommendCode
    ? `/knowledge/concept/${encodeURIComponent(props.recommendCode)}`
    : `/knowledge/node/${encodeURIComponent(props.recommend || "")}`,
  query: props.courseCode ? { courseCode: props.courseCode } : {},
}));
</script>

<style scoped>
.profile-card {
  padding: 20px;
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}

.profile-card h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 18px;
  line-height: 1.4;
}

.profile-bar {
  display: flex;
  height: 8px;
  margin-top: 16px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--oslab-panel-muted);
}

.profile-bar span {
  display: block;
  height: 100%;
}

.profile-empty {
  margin: 16px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 13px;
}

.profile-counts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.profile-counts li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--oslab-ink-soft);
  font-size: 13px;
}

.profile-counts i {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
}

.profile-counts b {
  margin-left: auto;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.profile-evidence {
  margin: 14px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}

.profile-next {
  display: inline-block;
  margin-top: 12px;
  color: var(--oslab-primary);
  font-size: 14px;
  text-decoration: none;
}

.profile-next:hover {
  text-decoration: underline;
}

.profile-links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--oslab-line-soft);
}

.profile-links a {
  color: var(--oslab-muted-strong);
  font-size: 13px;
  text-decoration: none;
}

.profile-links a:hover {
  color: var(--oslab-primary);
}

.profile-links a:focus-visible,
.profile-next:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.profile-placeholder {
  margin-top: 16px;
  padding: 14px;
  border: 1px dashed var(--oslab-line-strong);
  border-radius: var(--oslab-radius-lg);
  color: var(--oslab-muted-strong);
  font-size: 13px;
  line-height: 1.6;
}

.profile-placeholder button {
  margin-left: 8px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--oslab-primary);
  font: inherit;
  cursor: pointer;
}
</style>
