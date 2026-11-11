<template>
  <section class="follow-workspace" aria-label="教学工作台">
    <div class="workspace-top">
      <div>
        <h2>今天的教学工作</h2>
        <p>结合班级学习证据，查看需要关注的知识点和近期任务。</p>
      </div>
      <div class="workspace-actions">
        <a-button type="primary" @click="newTask">新建学习任务</a-button>
        <a-button @click="router.push('/teaching/classes')">班级管理</a-button>
        <a-button @click="goTasks">学习任务</a-button>
        <a-button @click="router.push('/teaching/content')">课程内容</a-button>
      </div>
    </div>
    <div v-if="!className" class="empty">
      请选择或创建班级，再查看班级学习情况。任务和课程内容仍可从上方进入。
    </div>
    <template v-else>
      <div class="course-control">
        <label for="teacher-course">当前课程</label>
        <a-select
          id="teacher-course"
          v-model="courseCode"
          :loading="coursesLoading"
          placeholder="选择课程"
          aria-label="当前课程"
        >
          <a-option
            v-for="course in courses"
            :key="course.courseCode"
            :value="course.courseCode"
            >{{ course.title }}</a-option
          >
        </a-select>
        <a-button v-if="coursesError" size="small" @click="loadCourses"
          >重试课程</a-button
        >
      </div>
      <p v-if="coursesError" class="error" role="alert">{{ coursesError }}</p>
      <p v-else-if="!coursesLoading && !courses.length" class="empty">
        暂无可用课程，请先维护课程内容。
      </p>
      <div class="workspace-grid">
        <section class="workspace-section" aria-labelledby="follow-heading">
          <div class="section-head">
            <div>
              <h3 id="follow-heading">需要跟进</h3>
              <p>
                按当前班级和课程统计；各类人数可能重叠，每类最多显示 5
                个知识点。
              </p>
            </div>
            <a-button
              v-if="courseCode"
              size="small"
              :loading="followLoading"
              @click="loadFollow"
              >刷新</a-button
            >
          </div>
          <p v-if="followLoading" class="empty" role="status">
            正在加载学习情况…
          </p>
          <div v-else-if="followError" class="error" role="alert">
            {{ followError }}
            <a-button size="small" @click="loadFollow">重试</a-button>
          </div>
          <p v-else-if="!courseCode" class="empty">
            选择课程后查看待跟进知识点。
          </p>
          <template v-else-if="follow">
            <div class="summary" aria-label="班级跟进人数">
              <span
                >到期复习
                <strong>{{ follow.dueReviewStudentCount }}</strong> 人</span
              ><span
                >需要补学
                <strong>{{ follow.weakStudentCount }}</strong> 人</span
              ><span
                >缺少证据
                <strong>{{ follow.missingEvidenceStudentCount }}</strong>
                人</span
              >
            </div>
            <div v-for="group in groups" :key="group.kind" class="follow-group">
              <h4>{{ group.label }}</h4>
              <p class="group-hint">{{ group.reason }}</p>
              <ul v-if="group.items.length" class="rows">
                <li v-for="item in group.items" :key="item.nodeCode">
                  <span
                    >{{ item.nodeName }}
                    <span class="muted"
                      >· {{ item.studentCount }} 人</span
                    ></span
                  >
                  <div class="row-actions">
                    <a-button size="small" @click="$emit('node', item.nodeCode)"
                      >查看证据</a-button
                    ><a-button size="small" @click="assignNode(item)"
                      >布置练习</a-button
                    >
                  </div>
                </li>
              </ul>
              <p v-else class="empty">{{ group.empty }}</p>
            </div>
            <a-button @click="askTutor">向导师整理教学思路</a-button>
            <p class="group-hint">仅填入班级汇总草稿，不会自动发送。</p>
          </template>
        </section>
        <section class="workspace-section" aria-labelledby="tasks-heading">
          <div class="section-head">
            <div>
              <h3 id="tasks-heading">本班近期任务</h3>
              <p>最多显示 5 项；按班级查看，不按课程筛选。</p>
            </div>
            <a-button size="small" :loading="tasksLoading" @click="loadTasks"
              >刷新</a-button
            >
          </div>
          <p v-if="tasksLoading" class="empty" role="status">正在加载任务…</p>
          <div v-else-if="tasksError" class="error" role="alert">
            {{ tasksError }}
            <a-button size="small" @click="loadTasks">重试</a-button>
          </div>
          <p v-else-if="!tasks.length" class="empty">
            本班暂无任务，可新建学习任务。
          </p>
          <ul v-else class="rows task-list">
            <li v-for="task in tasks" :key="task.taskCode">
              <div>
                <strong>{{ task.title }}</strong>
                <p class="group-hint">
                  {{ statusText(task.status) }} · 待批阅
                  {{
                    task.pendingReviewCount == null
                      ? "暂无数据"
                      : `${task.pendingReviewCount} 人`
                  }}<span v-if="task.dueAt">
                    · 截止 {{ formatTime(task.dueAt) }}</span
                  >
                </p>
              </div>
              <a-button size="small" @click="openTask(task)">{{
                task.status === "DRAFT" ? "编辑" : "查看批阅"
              }}</a-button>
            </li>
          </ul>
          <a-button @click="goTasks()">查看本班全部任务</a-button>
        </section>
      </div>
    </template>
  </section>
</template>
<script setup lang="ts">
import {
  computed,
  defineEmits,
  defineExpose,
  defineProps,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  registerAiPageContext,
  unregisterAiPageContext,
} from "@/composables/useAiPageContext";
import { listCourses, type CourseSummary } from "@/api/course";
import { listTeachingTasks } from "@/api/tasks";
import {
  getTeacherFollowUps,
  type FollowUpKind,
  type TeacherFollowUp,
} from "@/api/teachingWorkspace";
import { openTutor } from "@/composables/useAiLauncher";
const props = defineProps<{ className: string }>();
defineEmits<{ (event: "node", nodeName: string): void }>();
const router = useRouter();
const route = useRoute();
const courses = ref<CourseSummary[]>([]),
  courseCode = ref(""),
  coursesLoading = ref(false),
  coursesError = ref("");
const follow = ref<TeacherFollowUp | null>(null),
  followLoading = ref(false),
  followError = ref("");
interface TaskRow {
  taskCode: string;
  title: string;
  status: string;
  pendingReviewCount?: number;
  dueAt?: string;
}
const tasks = ref<TaskRow[]>([]),
  tasksLoading = ref(false),
  tasksError = ref("");
let courseSeq = 0,
  followSeq = 0,
  tasksSeq = 0;
const loadCourses = async () => {
  const seq = ++courseSeq;
  coursesLoading.value = true;
  coursesError.value = "";
  try {
    const result = await listCourses();
    if (seq !== courseSeq) return;
    courses.value = result || [];
    const requested = Array.isArray(route.query.courseCode)
      ? route.query.courseCode[0]
      : route.query.courseCode;
    courseCode.value = courses.value.some((c) => c.courseCode === requested)
      ? String(requested)
      : courses.value.some((c) => c.courseCode === courseCode.value)
      ? courseCode.value
      : courses.value[0]?.courseCode || "";
  } catch {
    if (seq === courseSeq) {
      courses.value = [];
      courseCode.value = "";
      coursesError.value = "课程加载失败，请重试。";
    }
  } finally {
    if (seq === courseSeq) coursesLoading.value = false;
  }
};
const loadFollow = async () => {
  const seq = ++followSeq,
    cls = props.className,
    course = courseCode.value;
  follow.value = null;
  followError.value = "";
  followLoading.value = Boolean(cls && course);
  if (!cls || !course) return;
  try {
    const data = await getTeacherFollowUps(cls, course);
    if (
      seq === followSeq &&
      cls === props.className &&
      course === courseCode.value
    )
      follow.value = data;
  } catch {
    if (seq === followSeq) followError.value = "待跟进情况加载失败，请重试。";
  } finally {
    if (seq === followSeq) followLoading.value = false;
  }
};
const loadTasks = async () => {
  const seq = ++tasksSeq,
    cls = props.className;
  tasks.value = [];
  tasksError.value = "";
  tasksLoading.value = Boolean(cls);
  if (!cls) return;
  try {
    const res = await listTeachingTasks({
      className: cls,
      pageSize: 5,
      current: 1,
      archived: false,
    });
    if (seq !== tasksSeq || cls !== props.className) return;
    if (res.data?.code !== 0) throw new Error();
    tasks.value = res.data.data?.records || [];
  } catch {
    if (seq === tasksSeq) tasksError.value = "近期任务加载失败，请重试。";
  } finally {
    if (seq === tasksSeq) tasksLoading.value = false;
  }
};
const refresh = () => {
  void loadFollow();
  void loadTasks();
};
defineExpose({ refresh });
watch(() => [props.className, courseCode.value], loadFollow, {
  immediate: true,
});
watch(() => props.className, loadTasks, { immediate: true });
watch(courseCode, (value) => {
  if (value && route.query.courseCode !== value) {
    void router
      .replace({ query: { ...route.query, courseCode: value } })
      .catch(() => undefined);
  }
});
watch(
  () => route.query.courseCode,
  (value) => {
    const requested = Array.isArray(value) ? value[0] : value;
    if (
      requested &&
      requested !== courseCode.value &&
      courses.value.some((c) => c.courseCode === requested)
    ) {
      courseCode.value = String(requested);
    }
  }
);
const aiPageContextProvider = () => ({
  pageName: props.className ? `教学工作台 · ${props.className}` : "教学工作台",
  courseCode: courseCode.value || undefined,
  courseTitle: courses.value.find((c) => c.courseCode === courseCode.value)
    ?.title,
});
onMounted(() => {
  registerAiPageContext(aiPageContextProvider);
  void loadCourses();
});
onBeforeUnmount(() => {
  unregisterAiPageContext(aiPageContextProvider);
  courseSeq++;
  followSeq++;
  tasksSeq++;
});
const groups = computed(() =>
  (
    [
      {
        kind: "DUE_REVIEW",
        label: "到期复习",
        reason: "已有复习计划且到达复习时间。",
        empty: "暂无到期复习知识点。",
      },
      {
        kind: "WEAK",
        label: "需要补学",
        reason: "已有学习证据显示掌握薄弱。",
        empty: "暂无需要补学的知识点。",
      },
      {
        kind: "MISSING_EVIDENCE",
        label: "缺少证据",
        reason: "尚无独立作答或运行证据，不代表薄弱。",
        empty: "暂无缺少证据的知识点。",
      },
    ] as { kind: FollowUpKind; label: string; reason: string; empty: string }[]
  ).map((g) => ({
    ...g,
    items: follow.value?.items.filter((i) => i.kind === g.kind) || [],
  }))
);
const newTask = () =>
  router.push({
    path: "/teaching/tasks/new",
    query: {
      ...(props.className ? { className: props.className } : {}),
      ...(courseCode.value ? { courseCode: courseCode.value } : {}),
    },
  });
const taskContext = () => ({
  ...(props.className ? { className: props.className } : {}),
  ...(courseCode.value ? { courseCode: courseCode.value } : {}),
});
const goTasks = () =>
  router.push({ path: "/teaching/tasks", query: taskContext() });
const openTask = (task: TaskRow) =>
  router.push({
    path: `/teaching/tasks/${encodeURIComponent(task.taskCode)}/${
      task.status === "DRAFT" ? "edit" : "review"
    }`,
    query: {
      ...taskContext(),
      ...(task.status !== "DRAFT" && (task.pendingReviewCount || 0) > 0
        ? { pendingReview: "1" }
        : {}),
    },
  });
const assignNode = (item: TeacherFollowUp["items"][number]) =>
  router.push({
    path: "/teaching/tasks/new",
    query: {
      className: props.className,
      courseCode: courseCode.value,
      nodeCode: item.nodeCode,
      nodeName: item.nodeName,
      followUpKind: item.kind,
    },
  });
const statusText = (status: string) =>
  ({ DRAFT: "草稿", PUBLISHED: "已发布", CLOSED: "已关闭" }[status] || status);
const formatTime = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
};
const askTutor = () => {
  if (!follow.value) return;
  const f = follow.value;
  const title =
    courses.value.find((c) => c.courseCode === courseCode.value)?.title ||
    courseCode.value;
  openTutor({
    scene: "GLOBAL",
    intent: "PLAN",
    scope: "COURSE",
    courseCode: courseCode.value,
    sceneLabel: `${props.className} · ${title}`,
    delivery: "DRAFT",
    draft: `请根据以下班级汇总协助教师规划教学，不推断个体情况：${
      props.className
    }，${title}，学生 ${f.studentCount} 人；到期复习 ${
      f.dueReviewStudentCount
    } 人，需要补学 ${f.weakStudentCount} 人，缺少证据 ${
      f.missingEvidenceStudentCount
    } 人，各类可能重叠。知识点：${
      f.items
        .map(
          (i) =>
            `${i.nodeName}（${
              i.kind === "DUE_REVIEW"
                ? "到期复习"
                : i.kind === "WEAK"
                ? "需要补学"
                : "缺少证据"
            } ${i.studentCount}人）`
        )
        .join("、") || "暂无"
    }。请提出可执行的教学思路。`,
  });
};
</script>
<style scoped>
.follow-workspace {
  margin: 0 0 24px;
  padding: 20px;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-line, #dbe2ea);
  border-radius: var(--oslab-radius-container, 14px);
}
.workspace-top,
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}
h2,
h3,
h4,
p {
  margin: 0;
}
h2 {
  font-size: 19px;
}
h3 {
  font-size: 16px;
}
h4 {
  font-size: 14px;
}
.workspace-top p,
.section-head p,
.group-hint,
.muted {
  color: var(--oslab-muted-strong, #525b66);
  font-size: 14px;
  line-height: 1.55;
}
.workspace-top p,
.section-head p {
  margin-top: 5px;
}
.workspace-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.course-control {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 20px 0 12px;
  flex-wrap: wrap;
}
.course-control :deep(.arco-select) {
  width: 240px;
  max-width: 100%;
}
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 24px;
  margin-top: 18px;
}
.workspace-section {
  min-width: 0;
}
.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 16px 0;
  font-size: 14px;
}
.summary strong {
  font-variant-numeric: tabular-nums;
}
.follow-group {
  margin: 16px 0;
}
.group-hint {
  margin-top: 4px;
}
.rows {
  margin: 8px 0 14px;
  padding: 0;
  list-style: none;
}
.rows li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--oslab-line, #dbe2ea);
  overflow-wrap: anywhere;
}
.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.rows li > :first-child {
  min-width: 0;
}
.rows :deep(.arco-btn) {
  flex-shrink: 0;
}
.empty {
  color: var(--oslab-muted-strong, #525b66);
  margin: 12px 0;
  line-height: 1.6;
}
.error {
  color: var(--oslab-danger, #a33d2e);
  margin: 12px 0;
}
@media (max-width: 900px) {
  .workspace-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 480px) {
  .follow-workspace {
    padding: 16px;
  }
  .follow-workspace :deep(.arco-btn),
  .course-control :deep(.arco-select-view) {
    min-height: 44px;
  }
  .rows li {
    align-items: flex-start;
  }
}
</style>
