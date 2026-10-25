<template>
  <div class="outline-page">
    <router-link to="/courses">返回课程</router-link>
    <p v-if="loading" role="status">正在加载目录…</p>
    <p v-else-if="error" role="alert">
      {{ error }} <a-button @click="load">重试</a-button>
    </p>
    <template v-else-if="outline"
      ><header>
        <h1>{{ outline.title }}</h1>
        <p>目录中仅已发布的教学条目可进入学习；其余内容正在整理。</p>
        <p v-if="outline.progress">
          学习进度 {{ outline.progress.percent || 0 }}% ·
          完成情况与知识掌握分别记录。
        </p>
      </header>
      <CourseOutlineTree
        :sections="outline.sections"
        :course-code="outline.courseCode"
    /></template>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute } from "vue-router";
import { getCourseOutline, CourseOutline } from "@/api/course";
import CourseOutlineTree from "@/components/learning/CourseOutlineTree.vue";
const route = useRoute(),
  outline = ref<CourseOutline | null>(null),
  loading = ref(false),
  error = ref("");
let version = 0;
async function load() {
  const current = ++version;
  loading.value = true;
  error.value = "";
  outline.value = null;
  try {
    const data = await getCourseOutline(String(route.params.courseCode));
    if (current === version) outline.value = data;
  } catch (e) {
    if (current === version)
      error.value = e instanceof Error ? e.message : "目录加载失败";
  } finally {
    if (current === version) loading.value = false;
  }
}
watch(() => route.params.courseCode, load, { immediate: true });
</script>
<style scoped>
.outline-page {
  max-width: 1040px;
  margin: auto;
  padding: 20px 0 40px;
}
a {
  color: #2447d8;
  text-decoration: none;
}
header {
  margin: 24px 0;
}
h1 {
  font-size: 26px;
}
p {
  color: #4e5969;
  line-height: 1.7;
}
</style>
