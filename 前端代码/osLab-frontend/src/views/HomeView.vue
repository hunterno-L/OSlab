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
          <router-link
            :to="{
              path: '/graph',
              query: course ? { courseCode: course.courseCode } : {},
            }"
            >进入课程学习</router-link
          >
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
            <span
              :style="{
                '--home-progress': (unit?.progress?.percent ?? 0) / 100,
              }"
            ></span>
          </div>
          <p class="home-progress-label">
            {{ unit?.progress?.completedCount ?? 0 }} /
            {{ unit?.progress?.requiredCount ?? 0 }} 个必做活动
          </p>
          <div class="home-actions">
            <router-link class="home-primary" :to="continueHref">
              {{ courseCompleted ? "复习课程" : "继续学习" }}
            </router-link>
            <router-link
              class="home-secondary"
              :to="{
                path: '/graph',
                query: course ? { courseCode: course.courseCode } : {},
              }"
            >
              查看课程图谱
            </router-link>
          </div>
        </template>
      </div>

      <ProfileCard
        :profile="profile"
        :recommend="recommend"
        :recommend-code="recommendCode"
        :course-code="course?.courseCode"
        :state="profileState"
        @retry="loadProfile"
      />
    </section>

    <section class="home-ai-plan" aria-labelledby="home-ai-plan-title">
      <div class="home-ai-head">
        <div>
          <h2 id="home-ai-plan-title">AI 学习计划</h2>
          <p class="home-ai-sub">
            {{
              dueReviewCount > 0
                ? `有 ${dueReviewCount} 项到期复习已进入计划`
                : "根据本人证据安排阅读、作答、预测或复测"
            }}
          </p>
        </div>
        <div class="home-ai-actions">
          <button type="button" @click="loadPlan('LEARN')">继续当前任务</button>
          <button
            type="button"
            class="is-review"
            @click="loadPlan('REVIEW', 15)"
          >
            复习 15 分钟
          </button>
        </div>
      </div>
      <AiNextActionCard
        v-if="currentAction && !planHasCurrentAction"
        :action="currentAction"
        :course-code="course?.courseCode || ''"
        @started="refreshPlan"
      />
      <AiNextActions
        :actions="nextActions"
        :course-code="course?.courseCode || ''"
        :loading="nextActionsLoading"
        :error="nextActionsError"
        :due-count="dueReviewCount"
        title=""
        empty-text="暂时没有可执行的下一步；完成一次阅读或作答后会重新生成。"
        compact
        :limit="2"
        :refreshable="false"
        @refresh="refreshPlan"
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
        <router-link to="/experiments/simulation"> 打开算法演示 </router-link>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useStore } from "vuex";
import { useRoute } from "vue-router";
import { selectAvailableCourse } from "@/composables/useCurrentCourse";
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
import AiNextActions from "@/components/ai/AiNextActions.vue";
import AiNextActionCard from "@/components/ai/AiNextActionCard.vue";
import { useAiConversation } from "@/composables/useAiConversation";
import {
  registerAiPageContext,
  unregisterAiPageContext,
} from "@/composables/useAiPageContext";

type LoadState = "loading" | "ready" | "empty" | "error";
type Resume = {
  unitCode?: string;
  activityCode?: string;
  reason: string;
};

const store = useStore();
const route = useRoute();

const {
  session,
  nextActions,
  nextActionsLoading,
  nextActionsError,
  dueReviewCount,
  refreshNextActions,
  setGoal,
} = useAiConversation();

const course = ref<CourseSummary | null>(null);

const currentAction = computed(() => session.value?.currentAction || null);
const planHasCurrentAction = computed(
  () =>
    Boolean(currentAction.value) &&
    nextActions.value.some(
      (action) => action.actionId === currentAction.value?.actionId
    )
);

/** 首页把课程、当前单元与知识节点注册进统一会话上下文 */
const aiContextProvider = () => ({
  pageName: "学习首页",
  courseCode: course.value?.courseCode,
  courseTitle: course.value?.title,
  unitCode: unit.value?.unitCode,
  unitName: unit.value?.title,
  nodeCodes: (unit.value?.nodeMappings || []).map((node) => node.nodeCode),
});

async function refreshPlan() {
  const code = course.value?.courseCode;
  if (!code) return;
  await refreshNextActions({ courseCode: code, limit: 5 });
}

async function loadPlan(focus?: "LEARN" | "REVIEW", minutes?: number) {
  const code = course.value?.courseCode;
  if (!code) return;
  if (focus) {
    const updated = await setGoal({
      courseCode: code,
      focus,
      goal: focus === "REVIEW" ? "按可用时间复习" : "继续当前任务",
      ...(minutes ? { timeBudgetMinutes: minutes } : {}),
    });
    if (!updated) return;
  }
  await refreshPlan();
}

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

const outline = ref<CourseOutline | null>(null);
const resume = ref<Resume | null>(null);
const unit = ref<CourseUnit | null>(null);
const unitState = ref<LoadState>("loading");

const profile = ref<PersonalGraphVO | null>(null);
const recommend = ref<string | null>(null);
const recommendCode = ref<string | null>(null);
const profileState = ref<LoadState>("loading");
let homeSequence = 0;
let profileSequence = 0;

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
  const sequence = ++homeSequence;
  profileSequence += 1;
  unitState.value = "loading";
  profileState.value = "loading";
  course.value = null;
  outline.value = null;
  resume.value = null;
  unit.value = null;
  profile.value = null;
  recommend.value = null;
  recommendCode.value = null;
  try {
    const courses = await listCourses();
    if (sequence !== homeSequence) return;
    const requested =
      typeof route.query.courseCode === "string"
        ? route.query.courseCode
        : undefined;
    course.value = selectAvailableCourse(courses, requested);
    unit.value = null;
    profile.value = null;
    recommend.value = null;
    recommendCode.value = null;
    if (!course.value) {
      unitState.value = "empty";
      profileState.value = "empty";
      return;
    }
    const courseCode = course.value.courseCode;
    void loadProfile();
    void refreshPlan();
    const [nextOutline, nextResume] = await Promise.all([
      getCourseOutline(courseCode),
      getCourseResume(courseCode),
    ]);
    if (sequence !== homeSequence) return;
    outline.value = nextOutline;
    resume.value = nextResume;
    const code = resume.value.unitCode || firstUnitCode(outline.value);
    if (!code) {
      unitState.value = "empty";
      return;
    }
    const nextUnit = await getCourseUnit(code);
    if (sequence !== homeSequence) return;
    unit.value = nextUnit;
    unitState.value = "ready";
  } catch {
    if (sequence === homeSequence) {
      unitState.value = "error";
      if (!course.value) profileState.value = "error";
    }
  }
}

async function loadProfile() {
  const code = course.value?.courseCode;
  if (!code) {
    profileState.value = "empty";
    return;
  }
  const sequence = ++profileSequence;
  profileState.value = "loading";
  try {
    const res = await personalGraph(code);
    if (sequence !== profileSequence || code !== course.value?.courseCode)
      return;
    if (res.data.code !== 0) throw new Error(res.data.message);
    profile.value = res.data.data;
    const node = profile.value?.nodes?.find(
      (item) =>
        item.name === profile.value?.recommendedNodeName ||
        item.nodeCode === profile.value?.recommendedNodeName
    );
    recommend.value = node?.displayName || node?.name || null;
    recommendCode.value = node?.nodeCode || null;
    if (!recommend.value) {
      try {
        const rec = await masteryRecommend(code);
        if (sequence !== profileSequence || code !== course.value?.courseCode)
          return;
        const first = rec.data.code === 0 ? rec.data.data?.[0] : null;
        recommend.value = first?.displayName || first?.nodeName || null;
        recommendCode.value = first?.nodeCode || null;
      } catch {
        if (sequence !== profileSequence || code !== course.value?.courseCode)
          return;
        recommend.value = null;
      }
    }
    if (sequence !== profileSequence || code !== course.value?.courseCode)
      return;
    profileState.value = "ready";
  } catch {
    if (sequence === profileSequence) profileState.value = "error";
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
  if (resume.value?.reason === "COURSE_COMPLETED")
    return `/graph?courseCode=${encodeURIComponent(course.value.courseCode)}`;
  const base = unitPath(course.value.courseCode, unit.value.unitCode);
  return currentActivityCode.value
    ? `${base}?activity=${encodeURIComponent(currentActivityCode.value)}`
    : base;
});

const courseCompleted = computed(
  () => resume.value?.reason === "COURSE_COMPLETED"
);

onMounted(() => {
  registerAiPageContext(aiContextProvider);
  void loadUnit();
  void loadTasks();
});

watch(
  () => route.query.courseCode,
  (value, previous) => {
    if (value !== previous) void loadUnit();
  }
);

onBeforeUnmount(() => {
  homeSequence += 1;
  profileSequence += 1;
  unregisterAiPageContext(aiContextProvider);
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
  margin-top: 16px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--oslab-panel-muted);
}

.home-progress span {
  display: block;
  width: 100%;
  height: 100%;
  transform: scaleX(var(--home-progress, 0));
  transform-origin: left center;
  background: linear-gradient(
    90deg,
    var(--oslab-highlight),
    var(--oslab-highlight-strong)
  );
  transition: transform var(--oslab-duration-slow) var(--oslab-ease);
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

.home-ai-plan {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 0;
  border-top: 1px dashed var(--oslab-line-strong);
}
.home-ai-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.home-ai-head h2 {
  margin: 0;
  color: var(--oslab-ink);
  font-family: var(--oslab-font-display);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.35;
  text-wrap: balance;
}
.home-ai-sub {
  margin: 4px 0 0;
  color: var(--oslab-muted-strong);
  font-size: 13px;
  line-height: 1.6;
  text-wrap: pretty;
}
.home-ai-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.home-ai-actions button {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--oslab-line-strong);
  border-radius: var(--oslab-radius-control);
  background: var(--oslab-panel);
  color: var(--oslab-ink);
  font: inherit;
  font-size: var(--oslab-text-small);
  cursor: pointer;
  transition: border-color var(--oslab-duration-fast) var(--oslab-ease),
    color var(--oslab-duration-fast) var(--oslab-ease);
}
.home-ai-actions button:hover {
  border-color: var(--oslab-primary);
  color: var(--oslab-primary);
}
.home-ai-actions button.is-review {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary-soft);
  color: var(--oslab-primary);
}
.home-ai-actions button:focus-visible {
  outline: 2px solid var(--oslab-primary);
  outline-offset: 2px;
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
