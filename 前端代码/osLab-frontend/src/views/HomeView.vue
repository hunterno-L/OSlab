<template>
  <div class="home-view">
    <section class="home-hero" aria-labelledby="home-greeting">
      <div class="home-current">
        <p class="home-greeting">{{ greeting }}，{{ displayName }}</p>
        <h1 id="home-greeting">
          {{ course?.title || "操作系统" }}
          <span v-if="sectionTitle" class="home-section">{{
            sectionTitle
          }}</span>
        </h1>

        <div
          v-if="unitState === 'loading'"
          class="home-placeholder"
          aria-busy="true"
        >
          正在加载当前单元…
        </div>
        <div v-else-if="unitState === 'error'" class="home-placeholder">
          暂时无法加载当前单元
          <button type="button" @click="loadUnit">重试</button>
        </div>
        <div v-else-if="unitState === 'empty'" class="home-placeholder">
          暂时没有可继续的学习单元
          <router-link to="/graph">进入课程学习</router-link>
        </div>
        <template v-else-if="unit">
          <h2 class="home-unit-title">{{ unit?.title }}</h2>
          <p v-if="unit?.objectives?.[0]" class="home-unit-goal">
            目标：{{ unit?.objectives?.[0] }}
          </p>
          <div
            class="home-progress"
            role="progressbar"
            :aria-valuenow="unit?.progress?.percent ?? 0"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <span :style="{ width: `${unit?.progress?.percent ?? 0}%` }"></span>
          </div>
          <p class="home-progress-label">
            {{ unit?.progress?.completedCount ?? 0 }} /
            {{ unit?.progress?.requiredCount ?? 0 }} 个必做活动
          </p>
          <div class="home-actions">
            <router-link class="home-primary" :to="continueHref">
              {{ courseCompleted ? "复习课程" : "继续学习" }}
            </router-link>
            <router-link class="home-secondary" to="/graph">
              查看课程图谱
            </router-link>
          </div>
        </template>
      </div>

      <ProfileCard
        :profile="profile"
        :recommend="recommend"
        :state="profileState"
        @retry="loadProfile"
      />
    </section>

    <UnitTimeline
      v-if="unit"
      :unit="unit"
      :course-code="course?.courseCode || ''"
    />

    <section class="home-theater" aria-labelledby="home-theater-title">
      <div class="home-theater-copy">
        <h2 id="home-theater-title">演算台 · 页面置换 LRU</h2>
        <p>改变访问序列，观察缺页与淘汰。</p>
        <router-link to="/experiments/simulation"> 打开算法模拟 </router-link>
      </div>
      <PagingTheater mode="guided" />
    </section>

    <section
      v-if="isStudent"
      class="home-tasks"
      aria-labelledby="home-tasks-title"
    >
      <h2 id="home-tasks-title">学习任务</h2>
      <div
        v-if="tasksState === 'loading'"
        class="home-placeholder"
        aria-busy="true"
      >
        正在加载学习任务…
      </div>
      <div v-else-if="tasksState === 'error'" class="home-placeholder">
        暂时无法加载学习任务
        <button type="button" @click="loadTasks">重试</button>
      </div>
      <router-link v-else to="/tasks" class="home-task-ledger">
        <span
          ><b>{{ tasks.pending || 0 }}</b
          >待接收</span
        >
        <span
          ><b>{{ tasks.inProgress || 0 }}</b
          >进行中</span
        >
        <span
          ><b>{{ tasks.submitted || 0 }}</b
          >待批阅</span
        >
        <span
          ><b>{{ tasks.reviewed || 0 }}</b
          >已批阅</span
        >
      </router-link>
    </section>

    <EntryLedger :is-student="isStudent" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useStore } from "vuex";
import { getTaskSummary } from "@/api/tasks";
import { masteryRecommend, personalGraph } from "@/api/learning";
import type { PersonalGraphVO } from "@/api/learning";
import {
  getCourseOutline,
  getCourseResume,
  getCourseUnit,
  listCourses,
  unitPath,
} from "@/api/course";
import type { CourseOutline, CourseSummary, CourseUnit } from "@/api/course";
import EntryLedger from "@/components/home/EntryLedger.vue";
import ProfileCard from "@/components/home/ProfileCard.vue";
import UnitTimeline from "@/components/home/UnitTimeline.vue";
import PagingTheater from "@/components/theater/PagingTheater.vue";

type LoadState = "loading" | "ready" | "empty" | "error";
type Resume = {
  unitCode?: string;
  activityCode?: string;
  reason: string;
};

const store = useStore();

const displayName = computed(
  () => store.state.user?.loginUser?.userName || "同学"
);
const isStudent = computed(
  () => store.state.user?.loginUser?.userRole === "user"
);
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return "早上好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

const course = ref<CourseSummary | null>(null);
const outline = ref<CourseOutline | null>(null);
const resume = ref<Resume | null>(null);
const unit = ref<CourseUnit | null>(null);
const unitState = ref<LoadState>("loading");

const profile = ref<PersonalGraphVO | null>(null);
const recommend = ref<string | null>(null);
const profileState = ref<LoadState>("loading");

const tasks = ref<Record<string, number>>({});
const tasksState = ref<LoadState>("loading");

function firstUnitCode(data: CourseOutline | null): string | null {
  if (!data) return null;
  for (const section of data.sections) {
    if (section.units?.length) return section.units[0].unitCode;
    const nested = firstUnitCode({
      ...data,
      sections: section.children || [],
    });
    if (nested) return nested;
  }
  return null;
}

function findSectionTitle(
  data: CourseOutline | null,
  sectionCode?: string
): string {
  if (!data || !sectionCode) return "";
  for (const section of data.sections) {
    if (section.sectionCode === sectionCode) {
      return [section.textbookNumber, section.title].filter(Boolean).join(" ");
    }
    const nested = findSectionTitle(
      { ...data, sections: section.children || [] },
      sectionCode
    );
    if (nested) return nested;
  }
  return "";
}

async function loadUnit() {
  unitState.value = "loading";
  try {
    const courses = await listCourses();
    course.value = courses[0] || null;
    if (!course.value) {
      unitState.value = "empty";
      return;
    }
    outline.value = await getCourseOutline(course.value.courseCode);
    resume.value = await getCourseResume(course.value.courseCode);
    const code = resume.value.unitCode || firstUnitCode(outline.value);
    if (!code) {
      unitState.value = "empty";
      return;
    }
    unit.value = await getCourseUnit(code);
    unitState.value = "ready";
  } catch {
    unitState.value = "error";
  }
}

async function loadProfile() {
  profileState.value = "loading";
  try {
    const res = await personalGraph();
    if (res.data.code !== 0) throw new Error(res.data.message);
    profile.value = res.data.data;
    recommend.value = profile.value?.recommendedNodeName || null;
    if (!recommend.value) {
      try {
        const rec = await masteryRecommend();
        recommend.value =
          rec.data.code === 0 ? rec.data.data?.[0]?.nodeName || null : null;
      } catch {
        recommend.value = null;
      }
    }
    profileState.value = "ready";
  } catch {
    profileState.value = "error";
  }
}

async function loadTasks() {
  if (!isStudent.value) return;
  tasksState.value = "loading";
  try {
    const res = await getTaskSummary();
    if (res.data.code !== 0) throw new Error(res.data.message);
    tasks.value = res.data.data || {};
    tasksState.value = "ready";
  } catch {
    tasksState.value = "error";
  }
}

const sectionTitle = computed(() =>
  findSectionTitle(outline.value, unit.value?.sectionCode)
);

const currentActivityCode = computed(() => {
  if (resume.value?.activityCode) return resume.value.activityCode;
  if (unit.value?.currentActivityCode) return unit.value.currentActivityCode;
  return unit.value?.activities.find((activity) => !activity.completed)
    ?.activityCode;
});

const continueHref = computed(() => {
  if (!course.value || !unit.value) return "/graph";
  if (resume.value?.reason === "COURSE_COMPLETED") return "/graph";
  const base = unitPath(course.value.courseCode, unit.value.unitCode);
  return currentActivityCode.value
    ? `${base}?activity=${encodeURIComponent(currentActivityCode.value)}`
    : base;
});

const courseCompleted = computed(
  () => resume.value?.reason === "COURSE_COMPLETED"
);

onMounted(() => {
  void loadUnit();
  void loadProfile();
  void loadTasks();
});
</script>

<style scoped>
.home-view {
  width: min(1280px, 100%);
  margin: 0 auto;
  padding: 8px 0 40px;
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(320px, 5fr);
  gap: 24px;
  align-items: start;
  padding: 8px 0 32px;
}

.home-greeting {
  margin: 0 0 12px;
  color: var(--oslab-muted-strong);
  font-size: 14px;
}

.home-view h1 {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 32px;
  line-height: 1.25;
}

.home-section {
  display: block;
  margin-top: 6px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-ui);
  font-size: 15px;
}

.home-unit-title {
  margin: 24px 0 0;
  color: var(--oslab-ink);
  font-size: 22px;
  line-height: 1.4;
}

.home-unit-goal {
  margin: 8px 0 0;
  color: var(--oslab-ink-soft);
  font-size: 14px;
  line-height: 1.7;
}

.home-progress {
  height: 8px;
  margin-top: 18px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--oslab-panel-muted);
}

.home-progress span {
  display: block;
  height: 100%;
  background: linear-gradient(
    90deg,
    var(--oslab-highlight),
    var(--oslab-highlight-strong)
  );
  transition: width var(--oslab-duration-slow) var(--oslab-ease);
}

.home-progress-label {
  margin: 8px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 12px;
}

.home-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.home-primary,
.home-secondary {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 18px;
  border-radius: var(--oslab-radius-control);
  font-size: 14px;
  text-decoration: none;
}

.home-primary {
  background: var(--oslab-primary);
  color: var(--oslab-on-primary);
}

.home-primary:hover {
  background: var(--oslab-primary-hover);
}

.home-secondary {
  border: 1px solid var(--oslab-line-strong);
  background: var(--oslab-panel);
  color: var(--oslab-ink);
}

.home-secondary:hover {
  border-color: var(--oslab-primary);
}

.home-primary:focus-visible,
.home-secondary:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

.home-placeholder {
  margin-top: 16px;
  padding: 16px;
  border: 1px dashed var(--oslab-line-strong);
  border-radius: var(--oslab-radius-lg);
  color: var(--oslab-muted-strong);
  font-size: 14px;
  line-height: 1.6;
}

.home-placeholder button,
.home-placeholder a {
  margin-left: 8px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--oslab-primary);
  font: inherit;
  cursor: pointer;
  text-decoration: none;
}

.home-theater {
  display: grid;
  grid-template-columns: minmax(0, 4fr) minmax(0, 8fr);
  gap: 24px;
  align-items: start;
  padding: 32px 0;
  border-top: 1px solid var(--oslab-line);
}

.home-theater-copy h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-size: 18px;
  line-height: 1.4;
}

.home-theater-copy p {
  margin: 10px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 14px;
  line-height: 1.7;
}

.home-theater-copy a {
  display: inline-block;
  margin-top: 14px;
  color: var(--oslab-primary);
  font-size: 14px;
  text-decoration: none;
}

.home-theater-copy a:hover {
  text-decoration: underline;
}

.home-tasks {
  padding: 28px 0;
  border-top: 1px solid var(--oslab-line);
}

.home-tasks h2 {
  margin: 0 0 16px;
  color: var(--oslab-ink);
  font-size: 18px;
  line-height: 1.4;
}

.home-task-ledger {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 16px 0;
  color: inherit;
  text-decoration: none;
}

.home-task-ledger span {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--oslab-muted-strong);
  font-size: 13px;
}

.home-task-ledger b {
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
}

.home-task-ledger:hover b {
  color: var(--oslab-primary);
}

.home-task-ledger:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
}

@media (max-width: 1100px) {
  .home-hero {
    grid-template-columns: 1fr;
  }

  .home-theater {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home-task-ledger {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .home-primary,
  .home-secondary {
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-progress span {
    transition: none;
  }
}
</style>
