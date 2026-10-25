<template>
  <section class="unit-timeline" aria-labelledby="unit-timeline-title">
    <h2 id="unit-timeline-title">本单元进度</h2>
    <ol class="timeline-track">
      <li
        v-for="activity in visible"
        :key="activity.activityCode"
        :class="{
          'is-current': activity.activityCode === currentCode,
          'is-done': activity.completed,
        }"
      >
        <router-link
          :to="href(activity.activityCode)"
          :aria-current="
            activity.activityCode === currentCode ? 'step' : undefined
          "
        >
          <span class="activity-dot" aria-hidden="true"></span>
          <span class="activity-label">
            {{ LABELS[activity.activityType] || activity.title }}
          </span>
        </router-link>
      </li>
    </ol>
    <router-link
      v-if="narrow && activities.length > visible.length"
      class="timeline-all"
      :to="unit?.unitCode ? unitPath(courseCode, unit.unitCode) : '/graph'"
    >
      查看全部活动
    </router-link>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { unitPath } from "@/api/course";

// eslint-disable-next-line no-undef
const props = defineProps<{
  unit: {
    unitCode: string;
    courseCode: string;
    currentActivityCode?: string;
    activities: Array<{
      activityCode: string;
      title: string;
      activityType: string;
      completed?: boolean;
      sortOrder?: number;
    }>;
  } | null;
  courseCode: string;
}>();

const LABELS: Record<string, string> = {
  READING: "阅读",
  WORKED_EXAMPLE: "跟随例子",
  DEMO: "互动演示",
  PRACTICE: "练习",
  ASSESSMENT: "测评",
  EXPERIMENT: "编程实验",
  SUMMARY: "小结",
};

const narrow = ref(false);
let media: MediaQueryList | null = null;

function syncNarrow() {
  narrow.value = media?.matches ?? false;
}

onMounted(() => {
  media = window.matchMedia("(max-width: 640px)");
  syncNarrow();
  media.addEventListener("change", syncNarrow);
});

onBeforeUnmount(() => media?.removeEventListener("change", syncNarrow));

const activities = computed(() =>
  [...(props.unit?.activities ?? [])].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  )
);

const currentCode = computed(
  () =>
    props.unit?.currentActivityCode ||
    activities.value.find((activity) => !activity.completed)?.activityCode ||
    ""
);

const visible = computed(() => {
  if (!narrow.value) return activities.value;
  const index = activities.value.findIndex(
    (activity) => activity.activityCode === currentCode.value
  );
  if (index < 0) return activities.value.slice(0, 3);
  return activities.value.slice(
    Math.max(0, index - 1),
    Math.min(activities.value.length, index + 2)
  );
});

function href(activityCode: string) {
  if (!props.unit) return "/graph";
  return `${unitPath(
    props.courseCode,
    props.unit.unitCode
  )}?activity=${encodeURIComponent(activityCode)}`;
}
</script>

<style scoped>
.unit-timeline {
  padding: 26px 0 24px;
  border-top: 1px solid var(--oslab-line);
}

.unit-timeline h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 18px;
  line-height: 1.4;
}

.timeline-track {
  display: flex;
  margin: 18px 0 0;
  padding: 0;
  overflow-x: auto;
  list-style: none;
}

.timeline-track li {
  position: relative;
  flex: 1 0 108px;
  min-width: 0;
}

.timeline-track li::after {
  content: "";
  position: absolute;
  top: 7px;
  left: calc(50% + 10px);
  width: calc(100% - 20px);
  height: 1px;
  background: var(--oslab-line);
}

.timeline-track li:last-child::after {
  display: none;
}

.timeline-track a {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0 6px;
  color: var(--oslab-muted-strong);
  text-decoration: none;
}

.timeline-track a:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.activity-dot {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 14px;
  height: 14px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: 50%;
  background: var(--oslab-panel);
}

.activity-label {
  padding: 2px 6px;
  border-radius: var(--oslab-radius-sm);
  font-size: 12px;
  line-height: 1.4;
  text-align: center;
}

.is-done .activity-dot {
  border-color: var(--oslab-mastered);
  background: var(--oslab-mastered);
}

.is-done .activity-dot::after {
  content: "✓";
  color: var(--oslab-on-primary);
  font-size: 9px;
  line-height: 1;
}

.is-current .activity-dot {
  border-color: var(--oslab-primary);
  background: var(--oslab-highlight);
  box-shadow: 0 0 0 4px var(--oslab-highlight-soft);
}

.is-current .activity-label {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  font-weight: 600;
}

.timeline-all {
  display: inline-block;
  margin-top: 12px;
  color: var(--oslab-primary);
  font-size: 13px;
  text-decoration: none;
}

.timeline-all:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .timeline-track li {
    flex-basis: 92px;
  }
}
</style>
