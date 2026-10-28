<template>
  <div class="course-page">
    <header>
      <h1>课程学习</h1>
      <p>沿教材目录学习，随时查看知识关联与个人学习画像。</p>
    </header>
    <div class="resource-links">
      <router-link to="/assessments">题目练习</router-link
      ><router-link to="/experiments/simulation">算法演示</router-link
      ><router-link to="/experiments">编程实验</router-link
      ><router-link to="/graph">知识图谱与学习画像</router-link>
    </div>
    <p v-if="loading" role="status">正在加载课程…</p>
    <div v-else-if="error" role="alert">
      {{ error }} <a-button @click="load">重新加载</a-button>
    </div>
    <template v-else>
      <p v-if="!courses.length" class="empty">
        课程内容准备中，发布后将在这里显示。
      </p>
      <section
        v-for="course in courses"
        :key="course.courseCode"
        class="course-card"
      >
        <h2>{{ course.title }}</h2>
        <p>{{ course.description }}</p>
        <template v-if="course.publishedUnitCount"
          ><p>
            学习进度 {{ course.progressPercent || 0 }}% ·
            {{ course.completedUnitCount || 0 }}/{{ course.publishedUnitCount }}
            个教学条目
          </p>
          <a-progress :percent="(course.progressPercent || 0) / 100"
        /></template>
        <p v-else class="empty">教材内容整理中，暂未开放学习。</p>
        <div class="actions">
          <a-button
            v-if="course.publishedUnitCount"
            type="primary"
            :loading="resuming === course.courseCode"
            @click="resume(course.courseCode)"
            >{{
              resumeReasons[course.courseCode] === "FIRST_UNIT"
                ? "开始学习"
                : resumeReasons[course.courseCode] === "COURSE_COMPLETED"
                ? "复习课程"
                : "继续学习"
            }}</a-button
          ><router-link
            :to="`/courses/${encodeURIComponent(course.courseCode)}`"
            >查看课程目录</router-link
          >
        </div>
      </section>
    </template>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  listCourses,
  getCourseResume,
  unitPath,
  CourseSummary,
} from "@/api/course";
const router = useRouter();
const resumeReasons = ref<Record<string, string>>({});
const courses = ref<CourseSummary[]>([]),
  loading = ref(false),
  error = ref(""),
  resuming = ref("");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    courses.value = await listCourses();
    await Promise.all(
      courses.value
        .filter((c) => c.publishedUnitCount)
        .map(async (c) => {
          const resume = await getCourseResume(c.courseCode);
          resumeReasons.value[c.courseCode] = resume.reason;
        })
    );
  } catch (e) {
    error.value = e instanceof Error ? e.message : "课程加载失败";
  } finally {
    loading.value = false;
  }
}
async function resume(code: string) {
  resuming.value = code;
  try {
    const target = await getCourseResume(code);
    await router.push(
      target.unitCode
        ? {
            path: unitPath(code, target.unitCode),
            query: {
              ...(target.activityCode ? { activity: target.activityCode } : {}),
              ...(target.reason === "COURSE_COMPLETED"
                ? { mode: "REVIEW" }
                : {}),
            },
          }
        : `/courses/${encodeURIComponent(code)}`
    );
  } catch (e) {
    error.value = e instanceof Error ? e.message : "继续位置加载失败";
  } finally {
    resuming.value = "";
  }
}
onMounted(load);
</script>
<style scoped>
.course-page {
  max-width: 1040px;
  margin: 0 auto;
  padding: 16px 0 40px;
}
h1 {
  font-size: 26px;
  margin: 0 0 12px;
}
header p,
.course-card p {
  color: #4e5969;
  line-height: 1.7;
}
.resource-links,
.actions {
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}
.resource-links {
  margin: 24px 0;
}
.course-card {
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  padding: 24px;
  margin: 20px 0;
  background: #fff;
}
h2 {
  font-size: 20px;
  margin: 0;
}
.actions {
  margin-top: 20px;
}
a {
  color: #2447d8;
  text-decoration: none;
}
.empty {
  color: #86909c !important;
}
</style>
