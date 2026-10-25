<template>
  <button
    class="catalog-toggle"
    type="button"
    :aria-expanded="drawerOpen"
    @click="drawerOpen = !drawerOpen"
  >
    课程目录
  </button>
  <div
    v-if="drawerOpen"
    class="catalog-backdrop"
    aria-hidden="true"
    @click="drawerOpen = false"
  ></div>
  <aside class="course-catalog" :class="{ 'drawer-open': drawerOpen }">
    <div class="catalog-head">
      <div class="course-logo">OS</div>
      <div class="catalog-course">
        <strong>{{ courseTitle || "操作系统" }}</strong>
        <span>{{ courseCode || "课程目录" }}</span>
      </div>
      <button
        class="drawer-close"
        type="button"
        aria-label="收起课程目录"
        @click="drawerOpen = false"
      >
        ×
      </button>
    </div>

    <button
      type="button"
      class="catalog-all"
      :class="{ active: !selectedSectionCode && !selectedUnitCode }"
      @click="pickCourse"
    >
      <span>全部课程</span>
      <small v-if="publishedUnitCount">{{ publishedUnitCount }} 个单元</small>
    </button>

    <div class="catalog-body">
      <p v-if="loading" class="catalog-hint" role="status">目录加载中…</p>
      <div v-else-if="error" class="catalog-error" role="alert">
        <span>{{ error }}</span>
        <a-button size="mini" @click="$emit('retry')">重试</a-button>
      </div>
      <ul v-else-if="rows.length" class="catalog-list">
        <li
          v-for="row in rows"
          :key="row.key"
          class="catalog-row"
          :class="[
            'depth-' + row.depth,
            {
              'is-section': row.kind === 'section',
              'is-unit': row.kind === 'unit',
              'is-active':
                row.kind === 'section'
                  ? !selectedUnitCode && row.code === selectedSectionCode
                  : row.code === selectedUnitCode,
              'in-path':
                row.kind === 'section' &&
                !!selectedUnitCode &&
                row.code === selectedSectionCode,
            },
          ]"
        >
          <template v-if="row.kind === 'section'">
            <button
              v-if="row.expandable"
              type="button"
              class="catalog-arrow"
              :class="{ open: row.expanded }"
              :aria-expanded="row.expanded"
              :aria-label="row.expanded ? '折叠' : '展开'"
              @click.stop="$emit('toggle-section', row.code)"
            >
              <span aria-hidden="true">›</span>
            </button>
            <span
              v-else
              class="catalog-arrow placeholder"
              aria-hidden="true"
            ></span>
            <button
              type="button"
              class="catalog-label"
              :aria-current="
                !selectedUnitCode && row.code === selectedSectionCode
                  ? 'true'
                  : undefined
              "
              @click="pickSection(row.code)"
            >
              <span class="catalog-title">
                <span
                  v-if="row.section?.textbookNumber"
                  class="catalog-number"
                  >{{ row.section.textbookNumber }}</span
                >
                {{ row.section?.title }}
              </span>
              <small v-if="row.meta" class="catalog-meta">{{ row.meta }}</small>
            </button>
          </template>
          <template v-else>
            <span class="unit-rail" aria-hidden="true"></span>
            <button
              type="button"
              class="catalog-label unit-label"
              :aria-current="row.code === selectedUnitCode ? 'true' : undefined"
              @click="pickUnit(row.code)"
            >
              <span class="catalog-title">{{ row.unit?.title }}</span>
              <small class="catalog-meta">{{ unitMeta(row.unit) }}</small>
            </button>
          </template>
        </li>
      </ul>
      <div v-else class="catalog-empty">
        <p>课程目录尚未发布</p>
        <small>教师发布单元后会显示在这里。</small>
      </div>
    </div>

    <div v-if="progress" class="catalog-foot">
      <div class="catalog-foot-row">
        <span>已学完单元</span>
        <strong
          >{{ progress.completedCount }}/{{ progress.requiredCount }}</strong
        >
      </div>
      <a-progress
        :percent="(progress.percent || 0) / 100"
        :show-text="false"
        size="small"
        stroke-color="var(--oslab-primary)"
      />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { CourseProgress, CourseSection, UnitSummary } from "@/api/course";

interface CatalogRow {
  key: string;
  kind: "section" | "unit";
  depth: number;
  code: string;
  section?: CourseSection;
  unit?: UnitSummary;
  expandable?: boolean;
  expanded?: boolean;
  meta?: string;
}

// eslint-disable-next-line no-undef
const props = defineProps<{
  courseCode: string;
  courseTitle?: string;
  sections: CourseSection[];
  selectedSectionCode?: string;
  selectedUnitCode?: string;
  expandedSections: string[];
  loading?: boolean;
  error?: string;
  progress?: CourseProgress | null;
}>();

// eslint-disable-next-line no-undef
const emit = defineEmits<{
  (event: "select-course"): void;
  (event: "select-section", sectionCode: string): void;
  (event: "select-unit", unitCode: string): void;
  (event: "toggle-section", sectionCode: string): void;
  (event: "retry"): void;
}>();

const drawerOpen = ref(false);

const expandedSet = computed(() => new Set(props.expandedSections));

const publishedUnitCount = computed(() => {
  let count = 0;
  const visit = (items: CourseSection[]) => {
    for (const section of items) {
      count += section.units?.length || 0;
      if (section.children?.length) visit(section.children);
    }
  };
  visit(props.sections);
  return count;
});

const rows = computed<CatalogRow[]>(() => {
  const result: CatalogRow[] = [];
  const visit = (items: CourseSection[], depth: number) => {
    for (const section of items) {
      const expandable = Boolean(
        section.children?.length || section.units?.length
      );
      const expanded = expandable && expandedSet.value.has(section.sectionCode);
      result.push({
        key: "section:" + section.sectionCode,
        kind: "section",
        depth,
        code: section.sectionCode,
        section,
        expandable,
        expanded,
        meta: !expandable
          ? "内容整理中"
          : section.units?.length && !section.children?.length
          ? `${section.units.length} 个单元`
          : "",
      });
      if (!expanded) continue;
      if (section.children?.length) visit(section.children, depth + 1);
      for (const unit of section.units || []) {
        result.push({
          key: "unit:" + unit.unitCode,
          kind: "unit",
          depth: depth + 1,
          code: unit.unitCode,
          unit,
        });
      }
    }
  };
  visit(props.sections, 0);
  return result;
});

const statusText = (unit?: UnitSummary) => {
  const status = unit?.progress?.status;
  if (status === "COMPLETED") return "已完成";
  if (status === "IN_PROGRESS") return "学习中";
  return "未开始";
};

const unitMeta = (unit?: UnitSummary) => {
  if (!unit) return "";
  const minutes = unit.estimatedMinutes
    ? ` · ${unit.estimatedMinutes} 分钟`
    : "";
  return `${statusText(unit)}${minutes}`;
};

const pickCourse = () => {
  drawerOpen.value = false;
  emit("select-course");
};
const pickSection = (code: string) => {
  drawerOpen.value = false;
  emit("select-section", code);
};
const pickUnit = (code: string) => {
  drawerOpen.value = false;
  emit("select-unit", code);
};
</script>

<style scoped>
.course-catalog {
  width: 258px;
  flex: 0 0 258px;
  align-self: stretch;
  max-height: calc(100vh - 112px);
  display: flex;
  flex-direction: column;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-line, #e5e6eb);
  border-radius: var(--oslab-radius-lg, 10px);
  position: sticky;
  top: 12px;
  overflow: hidden;
}
.catalog-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 12px;
  border-bottom: 1px solid var(--oslab-line-soft, #f2f3f5);
}
.course-logo {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: var(--oslab-primary, #2447d8);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 34px;
}
.catalog-course {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.catalog-course strong {
  color: var(--oslab-ink, #1d2129);
  font-size: 14px;
}
.catalog-course span {
  margin-top: 2px;
  color: var(--oslab-muted-strong, #86909c);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.drawer-close {
  display: none;
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--oslab-muted-strong, #86909c);
  font-size: 20px;
  cursor: pointer;
}
.catalog-all {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 8px 8px 0;
  padding: 9px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--oslab-ink-soft, #4e5969);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.catalog-all small {
  color: var(--oslab-muted-strong, #86909c);
  font-size: 11px;
}
.catalog-all:hover,
.catalog-all.active {
  background: var(--oslab-primary-soft, #edf3ff);
  color: var(--oslab-primary, #2447d8);
}
.catalog-all.active,
.catalog-row.is-active {
  background: var(--oslab-highlight-soft);
}
.catalog-row.is-active {
  box-shadow: inset 2px 0 0 var(--oslab-primary);
}
.catalog-all.active,
.catalog-row.is-active .catalog-title {
  color: var(--oslab-ink);
}
.catalog-row.in-path {
  background: var(--oslab-panel-muted);
}
.course-catalog {
  width: 264px;
  flex-basis: 264px;
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.catalog-course span,
.catalog-all small {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.catalog-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 8px 10px;
}
.catalog-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.catalog-row {
  display: flex;
  align-items: stretch;
  gap: 2px;
  border-radius: 6px;
  margin: 1px 0;
}
.catalog-row.is-section {
  color: var(--oslab-ink, #1d2129);
}
.catalog-row.is-unit {
  color: var(--oslab-ink-soft, #4e5969);
}
.catalog-row.is-active {
  background: var(--oslab-primary-soft, #edf3ff);
}
.catalog-row.is-active .catalog-title {
  color: var(--oslab-primary, #2447d8);
  font-weight: 600;
}
.catalog-row.in-path {
  background: rgba(36, 71, 216, 0.04);
}
.catalog-arrow {
  width: 20px;
  flex: 0 0 20px;
  border: 0;
  background: transparent;
  color: var(--oslab-muted-strong, #86909c);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.catalog-arrow span {
  display: inline-block;
  transition: transform 0.15s ease;
}
.catalog-arrow.open span {
  transform: rotate(90deg);
}
.catalog-arrow.placeholder {
  cursor: default;
}
.catalog-label {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 6px 7px 2px;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.catalog-row.depth-1 .catalog-label,
.catalog-row.depth-2 .catalog-label {
  padding-left: 2px;
}
.catalog-title {
  font-size: 13px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
}
.catalog-number {
  margin-right: 4px;
  color: var(--oslab-muted-strong, #86909c);
  font-variant-numeric: tabular-nums;
}
.catalog-meta {
  color: var(--oslab-muted-strong, #86909c);
  font-size: 11px;
  line-height: 1.4;
}
.catalog-row.is-section .catalog-title {
  font-weight: 500;
}
.catalog-row.depth-0 .catalog-title {
  font-weight: 600;
}
.unit-rail {
  width: 20px;
  flex: 0 0 20px;
  position: relative;
}
.unit-rail::before {
  content: "";
  position: absolute;
  left: 10px;
  top: 4px;
  bottom: 4px;
  border-left: 1px solid var(--oslab-line, #e5e6eb);
}
.unit-label {
  padding-top: 6px;
  padding-bottom: 6px;
}
.unit-label .catalog-title {
  font-size: 12.5px;
}
.catalog-hint,
.catalog-empty {
  margin: 14px 8px;
  color: var(--oslab-muted-strong, #86909c);
  font-size: 12px;
  line-height: 1.7;
}
.catalog-empty p {
  margin: 0 0 4px;
  color: var(--oslab-ink-soft, #4e5969);
  font-size: 13px;
}
.catalog-empty small {
  font-size: 11px;
}
.catalog-error {
  margin: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--oslab-weak, #f53f3f);
  font-size: 12px;
}
.catalog-foot {
  padding: 10px 14px 12px;
  border-top: 1px solid var(--oslab-line-soft, #f2f3f5);
}
.catalog-foot-row {
  display: flex;
  justify-content: space-between;
  color: var(--oslab-muted-strong, #86909c);
  font-size: 11px;
  margin-bottom: 6px;
}
.catalog-foot-row strong {
  color: var(--oslab-ink, #1d2129);
  font-weight: 600;
}
.catalog-toggle {
  display: none;
}
.catalog-backdrop {
  display: none;
}

/* 最终覆盖：确保当前单元/章节使用荧光笔，而不是旧的浅蓝选中态 */
.catalog-all.active,
.catalog-row.is-active {
  background: var(--oslab-highlight-soft);
}
.catalog-row.is-active {
  box-shadow: inset 2px 0 0 var(--oslab-primary);
}
.catalog-all.active,
.catalog-row.is-active .catalog-title {
  color: var(--oslab-ink);
}
.catalog-row.in-path {
  background: var(--oslab-panel-muted);
}

@media (max-width: 1024px) {
  .catalog-toggle {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    position: fixed;
    left: 12px;
    bottom: 18px;
    z-index: 30;
    padding: 9px 15px;
    border: 1px solid var(--oslab-line, #e5e6eb);
    border-radius: 999px;
    background: var(--oslab-panel, #fff);
    color: var(--oslab-ink-soft, #4e5969);
    font-size: 13px;
    box-shadow: 0 6px 18px rgba(29, 33, 41, 0.16);
    cursor: pointer;
  }
  .catalog-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 40;
    background: rgba(29, 33, 41, 0.35);
  }
  .course-catalog {
    position: fixed;
    z-index: 50;
    left: 0;
    top: 0;
    bottom: 0;
    width: min(320px, 86vw);
    max-height: none;
    border-radius: 0;
    transform: translateX(-102%);
    transition: transform 0.18s ease;
  }
  .course-catalog.drawer-open {
    transform: translateX(0);
  }
  .drawer-close {
    display: inline-flex;
  }
}
</style>
