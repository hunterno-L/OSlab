<template>
  <div class="unit-page">
    <CourseCatalogSidebar
      :course-code="courseCode || catalogCourseParam"
      :course-title="courseTitle"
      :sections="catalogSections"
      :selected-section-code="selectedSectionCode"
      :selected-unit-code="selectedUnitCode"
      :expanded-sections="expandedSections"
      :loading="catalogLoading"
      :error="catalogError"
      :progress="catalogProgress"
      @select-course="goGraphCourse"
      @select-section="goGraphSection"
      @select-unit="switchUnit"
      @toggle-section="toggleCatalogSection"
      @retry="reloadCatalog"
    />
    <div class="unit-main">
      <div class="unit-topbar">
        <router-link :to="backTarget" class="back-link"
          >← 返回课程学习</router-link
        >
        <span v-if="selectedUnitCrumb" class="unit-crumb">{{
          selectedUnitCrumb
        }}</span>
      </div>
      <p v-if="loading" role="status">正在加载学习内容…</p>
      <div v-else-if="error" role="alert">
        {{ error }} <a-button @click="load">重试</a-button>
      </div>
      <template v-else-if="unit">
        <header class="unit-hero">
          <div>
            <div class="section-kicker">UNIT / {{ unit.unitCode }}</div>
            <h1 class="oslab-highlight-text">{{ unit.title }}</h1>
            <div class="unit-meta">
              <span v-if="unit.estimatedMinutes"
                >建议学习时间 {{ unit.estimatedMinutes }} 分钟</span
              >
              <span>{{ unit.objectives?.length || 0 }} 个学习目标</span>
              <span>{{ unit.activities?.length || 0 }} 项学习活动</span>
              <span v-if="unit.progress.goalStatus === 'REVIEW_RECOMMENDED'"
                >检查点建议复习</span
              >
              <span v-else-if="unit.progress.goalStatus === 'PASSED'"
                >检查点已通过</span
              >
            </div>
          </div>
          <div class="unit-progress-big">
            <b>{{ unit.progress.percent || 0 }}<small>%</small></b>
            <span
              >{{ unit.progress.completedCount }}/{{
                unit.progress.requiredCount
              }}
              必需活动</span
            >
            <a-progress
              :percent="(unit.progress.percent || 0) / 100"
              :show-text="false"
              stroke-color="var(--oslab-highlight)"
              size="small"
            />
          </div>
        </header>

        <div class="unit-content-grid">
          <div class="unit-content">
            <section class="objectives-card">
              <div class="section-kicker">OBJECTIVES</div>
              <h2>这一单元要解决什么</h2>
              <ul>
                <li
                  v-for="(objective, index) in unit.objectives"
                  :key="objective"
                >
                  <b>{{ index + 1 }}</b
                  ><span>{{ objective }}</span>
                </li>
              </ul>
              <p class="hint">
                阅读与演示记录学习进度；作答与判题结果形成个人掌握证据。
              </p>
            </section>

            <section class="activity-strip-card">
              <div class="section-head">
                <div>
                  <div class="section-kicker">ACTIVITIES</div>
                  <h2>学习活动</h2>
                </div>
                <span class="section-count"
                  >{{
                    unit.activities?.filter((item) => item.completed).length ||
                    0
                  }}
                  / {{ unit.activities?.length || 0 }} 已完成</span
                >
              </div>
              <nav class="activity-strip" aria-label="学习活动">
                <button
                  v-for="(item, index) in unit.activities"
                  :key="item.activityCode"
                  type="button"
                  class="activity-step-card"
                  :class="{
                    active: activeCode === item.activityCode,
                    done: item.completed,
                  }"
                  :aria-current="
                    activeCode === item.activityCode ? 'step' : undefined
                  "
                  @click="selectActivity(item.activityCode)"
                >
                  <span class="step-number">{{
                    item.completed ? "✓" : String(index + 1).padStart(2, "0")
                  }}</span>
                  <span class="step-type"
                    >{{ activityLabel(item.activityType)
                    }}{{ item.required ? "" : " · 选学" }}</span
                  >
                  <strong>{{ item.title }}</strong>
                  <span class="step-status">{{
                    item.completed
                      ? "已完成"
                      : activeCode === item.activityCode
                      ? "进行中"
                      : "未开始"
                  }}</span>
                </button>
              </nav>
              <p class="strip-note">
                <b>①</b>
                <span
                  >先按顺序完成阅读与演示，再进入练习；作答结果才会形成掌握证据。</span
                >
              </p>
            </section>

            <section
              v-if="activity"
              class="activity-card"
              :class="{ 'is-checkpoint': isPracticeActivity }"
              :key="activity.activityCode"
            >
              <div class="activity-card-head">
                <span class="activity-card-index">{{
                  activeActivityNumber
                }}</span>
                <p class="eyebrow">
                  {{ activityLabel(activity.activityType)
                  }}{{ activity.required ? "" : " · 选学" }}
                  <span v-if="activity.completed">· 已完成</span>
                </p>
              </div>
              <h2>{{ activity.title }}</h2>
              <template v-if="activity.content"
                ><MdViewer :value="activity.content.contentMd" />
                <p v-if="activity.content.sourceRef" class="hint">
                  出处：{{ sourceText(activity.content.sourceRef) }}
                </p>
                <p v-if="activity.content.revision" class="hint">
                  内容版本 {{ activity.content.revision }}
                </p></template
              >
              <template v-else-if="isQuestion(activity)"
                ><p v-if="questionLoading">正在加载题目…</p>
                <p v-else-if="questionError" role="alert">
                  {{ questionError }}
                  <a-button size="small" @click="loadQuestion">重试</a-button>
                </p>
                <QuestionAnswerCard
                  v-else-if="question"
                  :key="activity.activityCode"
                  :question="question"
                  :source-unit-code="unit.unitCode"
                  :source-activity-code="activity.activityCode"
                  @answered="answered"
              /></template>
              <template
                v-else-if="
                  activity.activityType === 'DEMO' ||
                  activity.activityType === 'EXPERIMENT'
                "
                ><p
                  v-if="activity.config?.instructions || activity.config?.task"
                >
                  {{ activity.config.instructions || activity.config.task }}
                </p>
                <p>在资源工作区完成任务后返回，进度将按当前活动同步。</p>
                <router-link v-if="resourceTarget" :to="resourceTarget">{{
                  activity.activityType === "DEMO"
                    ? "进入算法模拟"
                    : "进入编程实验"
                }}</router-link>
                <p v-else class="hint">该资源暂未配置可用入口，请联系教师。</p>
                <a-button class="refresh" @click="load"
                  >刷新活动进度</a-button
                ></template
              >
              <p v-else class="hint">
                本活动暂无可用内容，请联系教师检查资源发布状态。
              </p>
              <p v-if="saveError" role="alert" class="error">{{ saveError }}</p>
              <div class="activity-actions">
                <a-button
                  v-if="activity.content && !activity.completed"
                  type="primary"
                  :loading="saving"
                  @click="confirmReading"
                  >{{
                    activity.activityType === "SUMMARY"
                      ? "完成小结"
                      : "确认阅读完成"
                  }}</a-button
                ><a-button
                  v-if="nextActivity"
                  @click="selectActivity(nextActivity.activityCode)"
                  >下一项：{{ nextActivity.title }}</a-button
                >
              </div>
            </section>

            <aside v-if="unit.nodeMappings?.length" class="nodes">
              <div class="section-kicker">RELATED KNOWLEDGE</div>
              <h2>相关知识与人画像</h2>
              <p>
                概念与实践证据可在节点工作台中查看；无实践观测时显示未评估。
              </p>
              <div class="node-chips">
                <router-link
                  v-for="node in unit.nodeMappings"
                  :key="node.nodeCode"
                  :to="{
                    path: `/knowledge/concept/${encodeURIComponent(
                      node.nodeCode
                    )}`,
                    query: {
                      nodeCode: node.nodeCode,
                      returnTo: currentUnitPath,
                      returnActivity: activeCode,
                      returnMode:
                        route.query.mode === 'REVIEW' ? 'REVIEW' : 'MAINLINE',
                    },
                  }"
                  >{{
                    node.name || node.nodeName || node.nodeCode
                  }}</router-link
                >
              </div>
            </aside>
            <footer>
              <router-link
                v-if="unit.previousUnitCode"
                :to="{
                  path: unitPath(unit.courseCode, unit.previousUnitCode),
                  query: unitSwitchQuery,
                }"
                >上一教学条目</router-link
              ><span v-else></span
              ><router-link
                v-if="unit.nextUnitCode"
                :to="{
                  path: unitPath(unit.courseCode, unit.nextUnitCode),
                  query: unitSwitchQuery,
                }"
                >下一教学条目</router-link
              ><span v-else class="hint">已到当前已发布内容末尾</span>
            </footer>
          </div>

          <aside class="unit-ledger" aria-label="本单元学习地图">
            <div class="section-kicker">STUDY LEDGER</div>
            <h2>本单元地图</h2>
            <div class="ledger-progress">
              <b>{{ unit.progress.percent || 0 }}<small>%</small></b>
              <span
                >{{ unit.progress.completedCount }}/{{
                  unit.progress.requiredCount
                }}</span
              >
            </div>
            <span class="meter"
              ><i :style="{ width: `${unit.progress.percent || 0}%` }"></i
            ></span>
            <div class="ledger-steps">
              <div
                v-for="group in activityGroups"
                :key="group.key"
                class="ledger-step"
                :class="{
                  current: group.items.some(
                    (item) => item.activityCode === activeCode
                  ),
                }"
              >
                <span class="step-num">{{ group.index }}</span>
                <div>
                  <b>{{ group.title }}</b>
                  <small>{{ group.hint }}</small>
                </div>
                <em>{{ group.done }} / {{ group.items.length }}</em>
              </div>
            </div>
            <p class="ledger-note">
              先完成阅读，再作答。答错时回到对应阅读段，不直接跳到下一题。
            </p>
            <div class="ledger-foot">
              <span>建议 {{ unit.estimatedMinutes || "—" }} 分钟</span>
              <span
                >当前：{{
                  activity ? activityLabel(activity.activityType) : "—"
                }}</span
              >
            </div>
          </aside>
        </div>
      </template>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MdViewer from "@/components/MdViewer.vue";
import QuestionAnswerCard from "@/components/learning/QuestionAnswerCard.vue";
import CourseCatalogSidebar from "@/components/learning/CourseCatalogSidebar.vue";
import { useCourseCatalog } from "@/composables/useCourseCatalog";
import {
  ALGORITHM_BY_KEY,
  algorithmKeyFromLegacy,
  algorithmKeyFromNodeCode,
} from "@/simulation";
import {
  getCourseUnit,
  saveCoursePosition,
  completeCourseActivity,
  unitPath,
  CourseUnit,
  CourseActivity,
} from "@/api/course";
import {
  getLearningQuestion,
  LearningQuestionVO,
  LearningAnswerResultVO,
} from "@/api/learning";
const route = useRoute(),
  router = useRouter();
const unit = ref<CourseUnit | null>(null),
  activeCode = ref(""),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  saveError = ref("");
const question = ref<LearningQuestionVO | null>(null),
  questionLoading = ref(false),
  questionError = ref("");
let loadEpoch = 0,
  questionEpoch = 0;

// 与图谱页共用同一课程目录；目录选择驱动范围而不是第二条活动导航
const catalogCourseParam = computed(() =>
  String(route.params.courseCode || "")
);
const {
  courseCode,
  courseTitle,
  sections: catalogSections,
  loading: catalogLoading,
  error: catalogError,
  courseProgress: catalogProgress,
  unitByCode,
  load: loadCatalogSource,
  ancestorCodes,
  sectionCrumb,
} = useCourseCatalog();
const expandedSections = ref<string[]>([]);
const selectedUnitCode = computed(() => String(route.params.unitCode || ""));
const selectedUnitSummary = computed(
  () => unitByCode.value.get(selectedUnitCode.value) || null
);
const selectedSectionCode = computed(
  () => selectedUnitSummary.value?.sectionCode || unit.value?.sectionCode || ""
);
const selectedUnitCrumb = computed(() => {
  const summary = selectedUnitSummary.value;
  return summary ? sectionCrumb(summary.sectionCode) : "";
});
const expandCurrentUnit = () => {
  const summary = selectedUnitSummary.value;
  if (!summary) return;
  const merged = new Set([
    ...expandedSections.value,
    ...ancestorCodes(summary.sectionCode),
  ]);
  expandedSections.value = Array.from(merged);
};
watch([selectedUnitSummary, selectedUnitCode], expandCurrentUnit, {
  immediate: true,
});
const reloadCatalog = async () => {
  await loadCatalogSource(catalogCourseParam.value || undefined);
  expandCurrentUnit();
};
const toggleCatalogSection = (sectionCode: string) => {
  const next = new Set(expandedSections.value);
  if (next.has(sectionCode)) next.delete(sectionCode);
  else next.add(sectionCode);
  expandedSections.value = Array.from(next);
};
const unitSwitchQuery = computed<Record<string, string>>(() => {
  const query: Record<string, string> = {};
  if (route.query.mode === "REVIEW") query.mode = "REVIEW";
  const returnTo = Array.isArray(route.query.returnTo)
    ? route.query.returnTo[0]
    : route.query.returnTo;
  if (
    typeof returnTo === "string" &&
    returnTo.startsWith("/") &&
    !returnTo.startsWith("//")
  ) {
    query.returnTo = returnTo;
  }
  return query;
});
const goGraphCourse = () => {
  const query: Record<string, string> = { scope: "course" };
  const course = courseCode.value || catalogCourseParam.value;
  if (course) query.courseCode = course;
  router.push({ path: "/graph", query });
};
const goGraphSection = (sectionCode: string) => {
  const query: Record<string, string> = { sectionCode };
  const course = courseCode.value || catalogCourseParam.value;
  if (course) query.courseCode = course;
  router.push({ path: "/graph", query });
};
const switchUnit = (unitCode: string) => {
  if (!unitCode || unitCode === selectedUnitCode.value) return;
  const course = courseCode.value || catalogCourseParam.value;
  if (!course) return;
  router.push({
    path: unitPath(course, unitCode),
    query: unitSwitchQuery.value,
  });
};
const graphBackTarget = computed(() => {
  const query: Record<string, string> = {};
  const course =
    unit.value?.courseCode || courseCode.value || catalogCourseParam.value;
  if (course) query.courseCode = course;
  if (selectedUnitCode.value) query.unitCode = selectedUnitCode.value;
  return { path: "/graph", query };
});
const backTarget = computed(() => {
  const returnTo = Array.isArray(route.query.returnTo)
    ? route.query.returnTo[0]
    : route.query.returnTo;
  if (
    typeof returnTo === "string" &&
    returnTo.startsWith("/") &&
    !returnTo.startsWith("//")
  ) {
    return returnTo;
  }
  return graphBackTarget.value;
});
const activity = computed(() =>
  unit.value?.activities.find((a) => a.activityCode === activeCode.value)
);
const nextActivity = computed(() => {
  const activities = unit.value?.activities || [];
  return activities[
    activities.findIndex((a) => a.activityCode === activeCode.value) + 1
  ];
});
const activityGroups = computed(() => {
  const list = unit.value?.activities || [];
  const groupOf = (types: string[]) =>
    list.filter((item) => types.includes(item.activityType));
  const groups = [
    {
      key: "read",
      index: 1,
      title: "阅读与演示",
      hint: "按顺序读，完成阅读记录",
      items: groupOf(["READING", "WORKED_EXAMPLE", "DEMO", "SUMMARY"]),
    },
    {
      key: "practice",
      index: 2,
      title: "练习与测评",
      hint: "作答后才形成掌握证据",
      items: groupOf(["PRACTICE", "ASSESSMENT"]),
    },
    {
      key: "lab",
      index: 3,
      title: "实践",
      hint: "实验与演示同步进度",
      items: groupOf(["EXPERIMENT"]),
    },
  ];
  return groups
    .filter((group) => group.items.length > 0)
    .map((group) => ({
      ...group,
      done: group.items.filter((item) => item.completed).length,
    }));
});
const isPracticeActivity = computed(() =>
  ["PRACTICE", "ASSESSMENT"].includes(activity.value?.activityType || "")
);
const activeActivityNumber = computed(() =>
  isPracticeActivity.value ? "②" : "①"
);
const currentUnitPath = computed(() =>
  unit.value ? unitPath(unit.value.courseCode, unit.value.unitCode) : ""
);
const isQuestion = (a: CourseActivity) =>
  ["PRACTICE", "ASSESSMENT"].includes(a.activityType);
const activityLabel = (type: string) =>
  ({
    READING: "阅读",
    WORKED_EXAMPLE: "跟随例子",
    DEMO: "互动演示",
    PRACTICE: "练习",
    ASSESSMENT: "测评",
    EXPERIMENT: "编程实验",
    SUMMARY: "小结",
  }[type] || type);
const sourceText = (source: Record<string, unknown>) =>
  Object.entries(source)
    .filter(([, value]) => value !== null && typeof value !== "object")
    .map(([, value]) => String(value))
    .join(" · ");
const resourceTarget = computed(() => {
  const a = activity.value,
    u = unit.value;
  if (!a || !u) return null;
  const config = { ...(a.resource?.resourceConfig || {}), ...(a.config || {}) };
  const query: Record<string, string> = {
    sourceUnitCode: u.unitCode,
    sourceActivityCode: a.activityCode,
    returnTo: currentUnitPath.value,
    returnActivity: a.activityCode,
    courseMode: route.query.mode === "REVIEW" ? "REVIEW" : "MAINLINE",
  };
  for (const [key, value] of Object.entries(config))
    if (value !== undefined && value !== null)
      query[key] =
        typeof value === "object" ? JSON.stringify(value) : String(value);
  if (config.sequence && !config.accessSequence)
    query.accessSequence = JSON.stringify(config.sequence);
  if (a.activityType === "DEMO") {
    const rawKey = String(
      config.algorithmKey ||
        config.algorithm ||
        a.resource?.algorithmKey ||
        a.resource?.adapterKey ||
        ""
    ).trim();
    let algorithmKey = ALGORITHM_BY_KEY.has(rawKey.toLowerCase())
      ? rawKey.toLowerCase()
      : algorithmKeyFromLegacy(rawKey);
    const nodeCode = String(
      config.nodeCode ||
        a.resource?.nodeCode ||
        a.resource?.nodeMappings?.[0]?.nodeCode ||
        ""
    );
    if (!algorithmKey) algorithmKey = algorithmKeyFromNodeCode(nodeCode);
    const algorithm = algorithmKey ? ALGORITHM_BY_KEY.get(algorithmKey) : null;
    if (!algorithm) return null;
    query.algorithmKey = algorithm.key;
    query.nodeCode = nodeCode || algorithm.nodeCode;
    if (config.nodeName) query.nodeName = String(config.nodeName);
    return { path: "/experiments/simulation", query };
  }
  const id = a.questionId || a.resource?.questionId || config.questionId;
  return id
    ? { path: `/experiments/code/${encodeURIComponent(String(id))}`, query }
    : null;
});
async function load() {
  const epoch = ++loadEpoch;
  loading.value = true;
  error.value = "";
  try {
    const data = await getCourseUnit(String(route.params.unitCode));
    if (epoch !== loadEpoch) return;
    unit.value = data;
    const requested =
      typeof route.query.activity === "string" ? route.query.activity : "";
    activeCode.value =
      data.activities.find((a) => a.activityCode === requested)?.activityCode ||
      data.activities.find((a) => a.activityCode === data.currentActivityCode)
        ?.activityCode ||
      data.activities.find((a) => !a.completed)?.activityCode ||
      data.activities[0]?.activityCode ||
      "";
    await persistPosition();
  } catch (e) {
    if (epoch === loadEpoch)
      error.value = e instanceof Error ? e.message : "内容加载失败";
  } finally {
    if (epoch === loadEpoch) loading.value = false;
  }
}
function selectActivity(code: string) {
  activeCode.value = code;
  saveError.value = "";
  router.replace({ query: { ...route.query, activity: code } });
  void persistPosition();
}
async function persistPosition() {
  const code = unit.value?.unitCode,
    activityCode = activeCode.value;
  if (!code || !activityCode) return;
  try {
    await saveCoursePosition(
      code,
      activityCode,
      route.query.mode === "REVIEW" ? "REVIEW" : "MAINLINE"
    );
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : "学习位置保存失败";
  }
}
async function loadQuestion() {
  const epoch = ++questionEpoch;
  question.value = null;
  questionError.value = "";
  const a = activity.value;
  if (!a || !isQuestion(a)) return;
  const id = a.questionId || a.reference.questionId;
  if (!id) {
    questionError.value = "题目尚未关联可用题库记录";
    return;
  }
  questionLoading.value = true;
  try {
    const response = await getLearningQuestion(id);
    if (epoch !== questionEpoch) return;
    if (response.data.code !== 0) throw new Error(response.data.message);
    question.value = response.data.data;
  } catch (e) {
    if (epoch === questionEpoch)
      questionError.value = e instanceof Error ? e.message : "题目加载失败";
  } finally {
    if (epoch === questionEpoch) questionLoading.value = false;
  }
}
async function complete(data: { confirmed?: boolean; attemptId?: string }) {
  if (!activity.value || !unit.value) return;
  const a = activity.value,
    u = unit.value;
  saving.value = true;
  saveError.value = "";
  try {
    const progress = await completeCourseActivity(a.activityCode, {
      sourceUnitCode: u.unitCode,
      mode: route.query.mode === "REVIEW" ? "REVIEW" : "MAINLINE",
      ...data,
    });
    if (unit.value?.unitCode === u.unitCode) {
      u.progress = progress;
      a.completed = progress.completed;
      a.passed = progress.passed;
      const refreshed = await getCourseUnit(u.unitCode);
      if (unit.value?.unitCode === u.unitCode) {
        u.progress = refreshed.progress;
        for (const item of u.activities) {
          const state = refreshed.activities.find(
            (entry) => entry.activityCode === item.activityCode
          );
          if (state) {
            item.completed = state.completed;
            item.passed = state.passed;
          }
        }
      }
    }
  } catch (e) {
    saveError.value =
      e instanceof Error ? e.message : "活动进度保存失败，请重试";
  } finally {
    saving.value = false;
  }
}
const confirmReading = () => complete({ confirmed: true });
const answered = (result: LearningAnswerResultVO) =>
  complete({ attemptId: String(result.attemptId) });
watch(
  catalogCourseParam,
  (code) => {
    void loadCatalogSource(code || undefined);
  },
  { immediate: true }
);
watch(
  () => [route.params.unitCode, route.query.mode],
  () => {
    unit.value = null;
    void load();
  },
  { immediate: true }
);
watch(activeCode, loadQuestion);
// 同单元内 query.activity 变化（外部返回、前进后退）也要切换当前步骤
watch(
  () => route.query.activity,
  (activity) => {
    if (!unit.value) return;
    const requested = typeof activity === "string" ? activity : "";
    if (!requested || requested === activeCode.value) return;
    if (!unit.value.activities.some((a) => a.activityCode === requested))
      return;
    activeCode.value = requested;
    saveError.value = "";
    void persistPosition();
  }
);
</script>
<style scoped>
.unit-page {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 4px 0 40px;
}
.unit-main {
  flex: 1;
  min-width: 0;
  max-width: 980px;
}
.unit-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.back-link {
  font-size: 13px;
}
.unit-crumb {
  color: #86909c;
  font-size: 12px;
}
a {
  color: #2447d8;
  text-decoration: none;
}
header {
  margin: 8px 0 20px;
}
h1 {
  font-size: 26px;
}
h2 {
  font-size: 19px;
  margin-top: 0;
}
p,
li {
  line-height: 1.8;
  color: #4e5969;
}
.hint {
  font-size: 12px;
  color: #86909c;
}
.activity-steps {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px 2px 10px;
  margin-bottom: 14px;
}
.activity-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 180px;
  max-width: 240px;
  padding: 10px 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  color: #4e5969;
  cursor: pointer;
}
.activity-step.active {
  border-color: #2447d8;
  background: #f2f6ff;
  color: #2447d8;
}
.step-index {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f3f5;
  font-size: 12px;
}
.activity-step.done .step-index {
  background: #e8f5ec;
  color: #2e7d4f;
}
.step-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.step-body strong {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
}
.step-body small {
  color: #86909c;
  font-size: 11px;
}
.activity-card {
  min-height: 300px;
  border: 1px solid #e5e6eb;
  background: #fff;
  border-radius: 8px;
  padding: 28px;
  min-width: 0;
}
.eyebrow {
  color: #2447d8;
  font-size: 12px;
}
.activity-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 28px;
}
.nodes {
  border-top: 1px solid #e5e6eb;
  padding-top: 24px;
  margin-top: 32px;
}
.nodes a {
  display: inline-block;
  margin: 0 20px 12px 0;
}
.nodes h2 {
  font-size: 16px;
}
.refresh {
  margin-left: 16px;
}
footer {
  display: flex;
  justify-content: space-between;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e5e6eb;
}
.error {
  color: #f53f3f;
}
/* C+B 学习图册 */
.unit-page .eyebrow {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--oslab-muted-strong);
}
.activity-card {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.activity-step {
  border-radius: var(--oslab-radius-md);
}
.activity-step.active {
  background: var(--oslab-highlight-soft);
}
.step-index {
  font-family: var(--oslab-font-mono);
}

/* 2026-10-24 学习单元工作台：活动卡片带 + 右侧学习地图 */
.unit-main {
  max-width: none;
}
.unit-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px 24px;
  margin: 8px 0 16px;
}
.unit-hero h1 {
  display: inline;
  margin: 0;
  padding: 0 4px;
  font-family: var(--oslab-font-display);
  font-size: 34px;
  line-height: 1.2;
  font-weight: 700;
}
.unit-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 6px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.unit-progress-big {
  flex: 0 0 auto;
  min-width: 170px;
  text-align: right;
}
.unit-progress-big b {
  color: var(--oslab-ink);
  font-family: var(--oslab-font-mono);
  font-size: 30px;
}
.unit-progress-big b small {
  font-size: 12px;
  color: var(--oslab-muted-strong);
}
.unit-progress-big span {
  display: block;
  margin: 2px 0 6px;
  color: var(--oslab-muted-strong);
  font-size: 10.5px;
}
.unit-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 258px;
  gap: 16px;
  align-items: start;
}
.unit-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.section-kicker {
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}
.objectives-card,
.activity-strip-card,
.activity-card,
.nodes,
.unit-ledger {
  background: var(--oslab-panel);
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.objectives-card {
  padding: 14px 16px;
  border-left: 3px solid var(--oslab-primary);
}
.objectives-card h2 {
  margin: 2px 0 8px;
  font-size: 15px;
}
.objectives-card ul {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.objectives-card li {
  display: flex;
  gap: 8px;
  color: var(--oslab-ink-soft);
  font-size: 12px;
  line-height: 1.6;
}
.objectives-card li b {
  color: var(--oslab-primary);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.objectives-card .hint {
  margin: 8px 0 0;
}
.activity-strip-card {
  padding: 14px 16px;
}
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}
.section-head h2 {
  margin: 2px 0 0;
  font-size: 16px;
}
.section-count {
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.activity-strip {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding: 2px 2px 6px;
  overflow-x: auto;
  scrollbar-width: thin;
}
.activity-step-card {
  position: relative;
  flex: 0 0 168px;
  min-height: 122px;
  padding: 10px;
  border: 1px solid var(--oslab-line);
  border-radius: var(--oslab-radius-md);
  background: var(--oslab-panel-soft);
  text-align: left;
}
.activity-step-card:hover {
  border-color: var(--oslab-line-strong);
  background: var(--oslab-panel);
}
.activity-step-card.active {
  border-color: var(--oslab-primary);
  background: var(--oslab-panel);
  box-shadow: 0 4px 14px rgba(36, 71, 216, 0.1);
}
.activity-step-card.active::before {
  content: "";
  position: absolute;
  left: 50%;
  top: -4px;
  width: 28px;
  height: 5px;
  border-radius: 1px;
  background: var(--oslab-highlight);
  transform: translateX(-50%);
}
.step-number {
  display: block;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.step-type {
  display: block;
  margin-top: 5px;
  color: var(--oslab-primary);
  font-size: 9.5px;
  letter-spacing: 0.06em;
}
.activity-step-card strong {
  display: block;
  margin-top: 3px;
  color: var(--oslab-ink);
  font-size: 12px;
  line-height: 1.5;
}
.step-status {
  position: absolute;
  left: 10px;
  bottom: 8px;
  color: var(--oslab-muted-strong);
  font-size: 9.5px;
}
.activity-step-card.active .step-status {
  color: var(--oslab-primary);
}
.activity-step-card.done .step-number {
  color: var(--oslab-mastered-text);
}
.strip-note {
  display: flex;
  gap: 7px;
  margin: 8px 0 0;
  padding: 5px 8px;
  border-left: 2px solid var(--oslab-primary);
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  line-height: 1.5;
}
.strip-note b {
  color: var(--oslab-primary);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
}
.activity-card {
  padding: 18px 20px;
}
.activity-card.is-checkpoint {
  border-left: 3px solid var(--oslab-annotation);
}
.activity-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.activity-card-index {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--oslab-primary);
  color: #fff;
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.activity-card .eyebrow {
  margin: 0;
}
.activity-card h2 {
  margin: 6px 0 12px;
  font-family: var(--oslab-font-display);
  font-size: 22px;
}
.nodes {
  padding: 14px 16px;
}
.nodes h2 {
  margin: 2px 0 4px;
  font-size: 15px;
}
.nodes > p {
  margin: 0 0 8px;
  color: var(--oslab-muted-strong);
  font-size: 11px;
}
.node-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.node-chips a {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--oslab-panel-soft);
  color: var(--oslab-ink-soft);
  font-size: 11px;
  text-decoration: none;
}
.node-chips a:hover {
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink);
}
.unit-ledger {
  position: sticky;
  top: 12px;
  padding: 14px;
}
.unit-ledger h2 {
  margin: 2px 0 8px;
  font-family: var(--oslab-font-display);
  font-size: 18px;
}
.ledger-progress {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
}
.ledger-progress b {
  color: var(--oslab-ink);
  font-size: 30px;
  line-height: 1;
}
.ledger-progress b small {
  font-size: 11px;
}
.ledger-progress span {
  margin-left: auto;
  font-size: 10px;
}
.meter {
  display: block;
  height: 8px;
  margin: 8px 0 10px;
  border-radius: 999px;
  background: #e6e7dc;
  overflow: hidden;
}
.meter i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    var(--oslab-highlight),
    var(--oslab-highlight-strong)
  );
}
.ledger-steps {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ledger-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-top: 1px solid var(--oslab-line);
}
.step-num {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 1px solid var(--oslab-line-strong);
  border-radius: 50%;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
}
.ledger-step.current .step-num {
  border-color: var(--oslab-primary);
  background: var(--oslab-primary);
  color: #fff;
}
.ledger-step b {
  display: block;
  color: var(--oslab-ink);
  font-size: 11.5px;
}
.ledger-step small {
  display: block;
  color: var(--oslab-muted-strong);
  font-size: 9.5px;
}
.ledger-step em {
  margin-left: auto;
  color: var(--oslab-muted-strong);
  font-family: var(--oslab-font-mono);
  font-style: normal;
  font-size: 9px;
}
.ledger-note {
  margin: 12px 0;
  padding: 9px 10px;
  border-left: 2px solid var(--oslab-primary);
  background: var(--oslab-highlight-soft);
  color: var(--oslab-ink-soft);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  line-height: 1.6;
}
.ledger-foot {
  display: flex;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--oslab-line);
  color: var(--oslab-muted-strong);
  font-size: 9.5px;
}

@media (max-width: 1024px) {
  .unit-content-grid {
    grid-template-columns: 1fr;
  }
  .unit-ledger {
    position: static;
  }
}

@media (max-width: 1024px) {
  .unit-page {
    display: block;
  }
  .unit-main {
    max-width: none;
  }
}
@media (max-width: 760px) {
  .activity-card {
    padding: 18px;
  }
  .activity-step {
    min-width: 160px;
  }
}
</style>
