<template>
  <div v-if="context.sourceUnitCode" class="course-return">
    <span>当前资源来自课程学习活动</span
    ><router-link
      v-if="returnPath"
      :to="{
        path: returnPath,
        query: {
          activity: context.sourceActivityCode,
          mode: route.query.courseMode === 'REVIEW' ? 'REVIEW' : 'MAINLINE',
        },
      }"
      >返回原学习位置</router-link
    ><a-button
      v-if="canCompleteDemo"
      size="small"
      :loading="saving"
      :disabled="saved"
      @click="completeDemo"
      >{{ saved ? "演示进度已保存" : "记录演示完成" }}</a-button
    ><span v-if="error" role="alert">{{ error }}</span>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import { completeCourseActivity, courseContext } from "@/api/course";
// eslint-disable-next-line no-undef
defineProps<{ canCompleteDemo?: boolean }>();
const route = useRoute(),
  saving = ref(false),
  saved = ref(false),
  error = ref("");
const context = computed(() => courseContext(route.query));
const returnPath = computed(() =>
  typeof route.query.returnTo === "string" &&
  route.query.returnTo.startsWith("/courses/") &&
  !route.query.returnTo.startsWith("//")
    ? route.query.returnTo
    : ""
);
async function completeDemo() {
  if (!context.value.sourceActivityCode || !context.value.sourceUnitCode)
    return;
  saving.value = true;
  error.value = "";
  try {
    await completeCourseActivity(context.value.sourceActivityCode, {
      sourceUnitCode: context.value.sourceUnitCode,
      mode: route.query.courseMode === "REVIEW" ? "REVIEW" : "MAINLINE",
      runToEnd: true,
    });
    saved.value = true;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "进度保存失败";
  } finally {
    saving.value = false;
  }
}
</script>
<style scoped>
.course-return {
  display: flex;
  gap: 18px;
  align-items: center;
  flex-wrap: wrap;
  padding: 14px 18px;
  border: 1px solid #c9ddff;
  background: #f5f8ff;
  border-radius: 6px;
  margin-bottom: 18px;
  font-size: 13px;
  color: #4e5969;
}
a {
  color: #2447d8;
  text-decoration: none;
}
</style>
