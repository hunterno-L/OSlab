<template>
  <ul class="outline-tree">
    <li v-for="section in sections" :key="section.sectionCode">
      <h3>{{ section.textbookNumber }} {{ section.title }}</h3>
      <ul v-if="section.units?.length" class="unit-list">
        <li v-for="unit in section.units" :key="unit.unitCode">
          <router-link
            :to="{
              path: unitPath(courseCode, unit.unitCode),
              query:
                unit.progress?.status === 'COMPLETED' ? { mode: 'REVIEW' } : {},
            }"
            >{{ unit.title }}</router-link
          >
          <span
            >{{
              unit.progress?.status === "COMPLETED"
                ? "已完成"
                : unit.progress?.status === "IN_PROGRESS"
                ? "学习中"
                : "未开始"
            }}<template v-if="unit.estimatedMinutes">
              · 约 {{ unit.estimatedMinutes }} 分钟</template
            ></span
          >
        </li>
      </ul>
      <CourseOutlineTree
        v-if="section.children?.length"
        :sections="section.children"
        :course-code="courseCode"
      />
      <p v-else-if="!section.units?.length" class="pending">内容整理中</p>
    </li>
  </ul>
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { CourseSection, unitPath } from "@/api/course";
export default defineComponent({
  name: "CourseOutlineTree",
  props: {
    sections: { type: Array as PropType<CourseSection[]>, required: true },
    courseCode: { type: String, required: true },
  },
  setup: () => ({ unitPath }),
});
</script>
<style scoped>
.outline-tree,
.unit-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.outline-tree > li {
  padding: 16px 0;
  border-top: 1px solid #eef0f3;
}
.outline-tree .outline-tree {
  padding-left: 20px;
}
h3 {
  font-size: 15px;
  margin: 0 0 12px;
  color: #1d2129;
}
.unit-list li {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
}
.unit-list a {
  color: #2447d8;
  text-decoration: none;
}
.unit-list span,
.pending {
  font-size: 12px;
  color: #86909c;
}
.pending {
  margin: 8px 0;
}
@media (max-width: 640px) {
  .unit-list li {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
