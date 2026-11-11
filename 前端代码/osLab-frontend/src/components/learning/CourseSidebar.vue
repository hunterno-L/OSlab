<template>
  <aside
    class="course-sidebar"
    :aria-label="navKey === 'teaching' ? '教学管理导航' : '课程导航'"
    :class="{ 'course-sidebar--compact': navKey === 'experiments' }"
  >
    <div class="sidebar-block">
      <button
        v-for="item in childItems"
        :key="item.key"
        class="sidebar-item"
        type="button"
        :aria-current="isActive(item.path) ? 'page' : undefined"
        :class="{ active: isActive(item.path) }"
        @click="go(item.path)"
      >
        {{ item.label }}
      </button>
    </div>
    <slot />
  </aside>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  APP_NAVIGATION,
  AUXILIARY_NAVIGATION,
} from "@/navigation/appNavigation";

// eslint-disable-next-line no-undef
const props = defineProps<{ navKey: string }>();

const route = useRoute();
const router = useRouter();

const section = computed(
  () =>
    [...APP_NAVIGATION, ...AUXILIARY_NAVIGATION].find(
      (n) => n.key === props.navKey
    ) || null
);
const childItems = computed(() => section.value?.children || []);

const sideModeKeys = ["view", "studyState"];
const firstQueryValue = (value: unknown) =>
  String((Array.isArray(value) ? value[0] : value) ?? "");

const activeItemPath = computed(() => {
  const candidates = childItems.value.map((item) => ({
    path: item.path,
    resolved: router.resolve(item.path),
  }));
  const exactItems = candidates.filter(
    (item) => item.resolved.path === route.path
  );
  const modeKeysOf = (query: Record<string, unknown>) =>
    sideModeKeys.filter((key) => query[key] !== undefined);
  // Only sibling mode keys affect selection; node/chapter/type filters do not.
  const selectedMode = exactItems.find((item) => {
    const keys = modeKeysOf(item.resolved.query);
    return (
      keys.length > 0 &&
      keys.every(
        (key) =>
          firstQueryValue(route.query[key]) ===
          firstQueryValue(item.resolved.query[key])
      )
    );
  });
  const baseItem = exactItems.find(
    (item) => modeKeysOf(item.resolved.query).length === 0
  );
  if (selectedMode || baseItem) return (selectedMode || baseItem)?.path;

  // A simulation is a sibling of the experiment catalog, not its detail.
  if (route.path.startsWith("/experiments/code/")) return "/experiments";
  if (route.path.startsWith("/assessments/run/")) return "/assessments";
  if (route.path.startsWith("/teaching/")) {
    return candidates
      .filter((item) => route.path.startsWith(`${item.resolved.path}/`))
      .sort((a, b) => b.resolved.path.length - a.resolved.path.length)[0]?.path;
  }
  return "";
});

const isActive = (path: string) => activeItemPath.value === path;

const go = (path: string) => {
  const [base, query = ""] = path.split("?");
  router.push({
    path: base,
    query: Object.fromEntries(new URLSearchParams(query)),
  });
};
</script>

<style scoped>
.course-sidebar {
  box-sizing: border-box;
  width: 200px;
  flex-shrink: 0;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-line, rgba(0, 0, 0, 0.11));
  border-radius: var(--oslab-radius-lg, 8px);
  padding: var(--oslab-space-3, 12px);
  position: sticky;
  top: 0;
  align-self: flex-start;
}
.sidebar-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* Legacy side-mode card width. */
.course-sidebar--compact {
  width: 160px;
}
.sidebar-item {
  padding: var(--oslab-space-2, 8px) 10px;
  border-radius: var(--oslab-radius-md, 6px);
  color: var(--oslab-ink-soft, #37352f);
  font-size: var(--oslab-font-body, 14px);
  cursor: pointer;
  border: 0;
  background: transparent;
  text-align: left;
  font-family: inherit;
  transition: background var(--oslab-duration-fast, 150ms)
    var(--oslab-ease, ease);
}
.sidebar-item:hover,
.sidebar-item:focus-visible {
  background: var(--oslab-panel-soft, #f9f9f8);
}
.sidebar-item.active {
  background: var(--oslab-primary-soft, #eaf0fd);
  color: var(--oslab-primary, #2447d8);
  font-weight: 600;
}
@media (max-width: 1024px) {
  .course-sidebar {
    box-sizing: border-box;
    width: 100%;
    padding: 8px;
    position: static;
    overflow-x: auto;
  }
  .sidebar-block {
    flex-direction: row;
    width: max-content;
    min-width: 100%;
  }
  .sidebar-item {
    flex: 0 0 auto;
    white-space: nowrap;
  }
}

/* C+B 学习图册 */
.course-sidebar {
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.sidebar-item {
  border-radius: var(--oslab-radius-control);
}
.sidebar-item.active {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
  box-shadow: inset 2px 0 0 var(--oslab-primary);
}
</style>
