<template>
  <div id="experimentWorkbench" class="experiment-workbench oslab-lab-b">
    <CourseReturnBar />
    <p v-if="courseProgressError" role="alert">
      {{ courseProgressError }}
      <a-button size="small" @click="saveCourseProgress"
        >重试保存活动进度</a-button
      >
    </p>
    <div class="workbench-crumb">
      <a-button type="text" size="small" @click="returnToCatalog">
        <template #icon><icon-arrow-left /></template>
        返回编程实验
      </a-button>
      <span v-if="contextNode">{{ contextNode }}</span>
    </div>
    <!-- 顶部：实验名 / 章节 / 关联节点 / 语言选择 / 提交主按钮 -->
    <div class="workbench-header">
      <div class="workbench-title">
        <div class="lab-title-line">
          <span class="lab-fig">{{ labFigure }}</span>
          <h2 class="title">{{ question?.title || "编程实验" }}</h2>
        </div>
        <div class="meta">
          <a-tag v-if="chapter" color="arcoblue">章节：{{ chapter }}</a-tag>
          <a-tag
            v-for="(tag, index) of question?.tags || []"
            :key="index"
            color="gray"
            class="node-tag"
            @click="goNode(tag)"
          >
            {{ tag }}
          </a-tag>
        </div>
        <p class="lab-spec">{{ labSpec }}</p>
      </div>
      <a-space wrap>
        <a-select
          v-model="form.language"
          :style="{ width: '120px' }"
          placeholder="语言"
          @change="onLanguageChange"
        >
          <a-option value="c">C</a-option>
          <a-option value="cpp">C++</a-option>
          <a-option value="java">Java</a-option>
          <a-option value="go">Go</a-option>
        </a-select>
        <a-button
          type="primary"
          :loading="submitting"
          :disabled="isLocalBuild || phase === 'polling' || loading"
          @click="doSubmit"
        >
          {{ isLocalBuild ? "本地构建实验" : "提交评测" }}
        </a-button>
        <a-button type="text" @click="goRecords">查看提交记录</a-button>
      </a-space>
    </div>

    <a-alert v-if="question" type="info" class="local-build-notice">
      {{ judgeModeDescription }}
    </a-alert>

    <div class="workbench-body">
      <!-- 左：实验说明 / 关联图谱 / 向导师提问 -->
      <div class="panel panel-left">
        <a-tabs v-model:active-key="descriptionTab" @change="loadRelatedGraph">
          <a-tab-pane key="desc" title="实验说明">
            <a-spin :loading="loading" style="width: 100%">
              <MdViewer :value="question?.content || '暂无实验说明'" />
            </a-spin>
            <aside class="lab-margin-note">
              {{
                isLocalBuild
                  ? "草稿自动保存；请在本地环境查看构建与运行结果。"
                  : "草稿自动保存；提交后可在右侧查看判题进度与结果。"
              }}
            </aside>
          </a-tab-pane>
          <a-tab-pane key="graph" title="关联图谱">
            <a-spin :loading="graphLoading" style="width: 100%">
              <KnowledgeGraph
                v-if="descriptionTab === 'graph' && nodes.length"
                :nodes="nodes"
                :links="links"
                :categories="categories"
              />
              <a-empty v-else-if="!graphLoading" description="暂无关联节点" />
            </a-spin>
            <a-button style="margin-top: 8px" @click="goGraphPage">
              查看完整知识图谱
            </a-button>
          </a-tab-pane>
          <a-tab-pane key="tutor" title="向导师提问">
            <section class="tutor-launch" aria-label="向统一导师提问">
              <p class="hint">
                打开导师不会自动调用模型。代码与报错只在你勾选后附带，可先编辑再发送。
              </p>
              <a-textarea
                v-model="tutorQuestion"
                aria-label="描述实验中的问题"
                placeholder="例如：为什么我的实现会超时？应该先检查哪一步？"
                :auto-size="{ minRows: 2, maxRows: 5 }"
                :max-length="4000"
                show-word-limit
              />
              <div class="tutor-attach">
                <a-checkbox
                  v-model="attachJudge"
                  :disabled="!lastJudgeResultSummary"
                >
                  附带最近判题结果{{ lastJudgeResultSummary ? "" : "（暂无）" }}
                </a-checkbox>
                <a-textarea
                  v-if="attachJudge"
                  v-model="judgeAttachmentText"
                  aria-label="要附带的判题结果"
                  :auto-size="{ minRows: 2, maxRows: 6 }"
                />
                <p
                  v-if="attachJudge"
                  class="hint"
                  :class="{ 'is-over': judgeAttachmentText.length > 8000 }"
                >
                  {{ judgeAttachmentText.length }} / 8000 字{{
                    judgeAttachmentText.length > 8000
                      ? "，已超出上限，请手动精简"
                      : ""
                  }}
                </p>
                <a-checkbox v-model="attachCode">
                  附带当前编辑器代码（仅你勾选后上传）
                </a-checkbox>
                <a-textarea
                  v-if="attachCode"
                  v-model="codeAttachmentText"
                  aria-label="要附带的代码片段"
                  :auto-size="{ minRows: 3, maxRows: 10 }"
                />
                <p
                  v-if="attachCode"
                  class="hint"
                  :class="{ 'is-over': codeAttachmentText.length > 8000 }"
                >
                  {{ codeAttachmentText.length }} / 8000 字{{
                    codeAttachmentText.length > 8000
                      ? "，已超出上限，请手动精简"
                      : ""
                  }}
                </p>
                <a-collapse>
                  <a-collapse-item header="可选：附带实验说明选段（≤2000 字）">
                    <a-textarea
                      v-model="selectionAttachmentText"
                      aria-label="要附带的实验说明选段"
                      :auto-size="{ minRows: 2, maxRows: 5 }"
                    />
                    <p
                      class="hint"
                      :class="{
                        'is-over': selectionAttachmentText.length > 2000,
                      }"
                    >
                      {{ selectionAttachmentText.length }} / 2000 字
                    </p>
                  </a-collapse-item>
                </a-collapse>
              </div>
              <a-alert v-if="attachmentError" type="warning">
                {{ attachmentError }}
              </a-alert>
              <a-space wrap>
                <a-button
                  type="primary"
                  :disabled="Boolean(attachmentError)"
                  @click="openExperimentTutor('SEND')"
                >
                  发送给导师
                </a-button>
                <a-button
                  :disabled="Boolean(attachmentError)"
                  @click="openExperimentTutor('DRAFT')"
                >
                  只填入导师输入框
                </a-button>
              </a-space>
              <p class="hint tutor-guard-hint">
                导师会带入当前实验标题、关联节点与真实判题定位；页面内不再维护第二套聊天记录。
              </p>
            </section>
          </a-tab-pane>
        </a-tabs>
      </div>

      <!-- 中：代码编辑器 -->
      <div class="panel panel-editor">
        <div class="editor-heading">
          <span class="panel-kicker">CODE / EDITOR</span>
          <span class="hint">{{ codeSourceLabel }}；编辑后自动保存</span>
          <a-button
            size="mini"
            :disabled="!question || !starterCode"
            @click="restoreStarter"
            >恢复初始代码</a-button
          >
          <a-button v-if="hasBackup" size="mini" @click="restoreBackup"
            >恢复替换前草稿</a-button
          >
        </div>
        <p
          v-if="codeLanguageWarning"
          class="code-language-warning"
          role="status"
        >
          {{ codeLanguageWarning }}
        </p>
        <CodeEditor
          :value="form.code as string"
          :language="form.language"
          :handle-change="changeCode"
        />
      </div>

      <!-- 右：提交结果与诊断（窄屏时置底） -->
      <div class="panel panel-result">
        <section class="status-card">
          <span class="panel-kicker">EVALUATION</span>
          <h3>评测结果</h3>
          <template v-if="phase === 'idle' && !submitDetail">
            <a-empty
              :description="
                isLocalBuild
                  ? '按实验说明在本地环境验证，本实验不自动判分'
                  : '提交评测后，在此查看判题进度与结果'
              "
            />
          </template>

          <template v-else-if="phase === 'polling'">
            <a-spin dot style="margin-right: 8px" />
            <span class="status-line">
              已提交 #{{ submitId }}，{{ statusText }}
            </span>
            <div class="hint">提交后可以继续修改代码，判题不影响编辑器内容</div>
          </template>

          <template v-else-if="phase === 'timeout'">
            <a-alert type="warning">
              评测仍在处理中（已查询约 60 秒），未判定为失败。
            </a-alert>
            <a-button
              type="primary"
              status="warning"
              size="small"
              style="margin-top: 8px"
              @click="resumePolling"
            >
              继续查询
            </a-button>
          </template>

          <template v-else-if="submitDetail">
            <div class="status-line">
              <a-tag :color="terminalColor" size="large">
                {{ terminalText }}
              </a-tag>
              <span class="judge-message">{{ judgeMessageText }}</span>
            </div>
            <a-descriptions
              :column="1"
              bordered
              size="small"
              style="margin-top: 12px"
            >
              <a-descriptions-item label="提交编号">
                {{ submitDetail.id }}
              </a-descriptions-item>
              <a-descriptions-item label="语言">
                {{ submitDetail.language }}
              </a-descriptions-item>
              <a-descriptions-item
                v-if="submitDetail.judgeInfo?.time !== undefined"
                label="运行时间"
              >
                {{ submitDetail.judgeInfo?.time }} ms
              </a-descriptions-item>
              <a-descriptions-item
                v-if="submitDetail.judgeInfo?.memory !== undefined"
                label="内存占用"
              >
                {{ submitDetail.judgeInfo?.memory }} MB
              </a-descriptions-item>
            </a-descriptions>

            <!-- 关联节点与补救行动 -->
            <div class="remediation">
              <div class="remediation-title">
                {{ isAccepted ? "巩固建议" : "补救行动" }}
              </div>
              <template v-if="(question?.tags || []).length">
                <a-button
                  v-for="tag of question?.tags"
                  :key="tag"
                  size="small"
                  @click="goNode(tag)"
                >
                  {{ isAccepted ? "巩固" : "复习" }}知识点：{{ tag }}
                </a-button>
              </template>
              <a-button
                v-if="!isAccepted && tutorNodeName"
                size="small"
                type="outline"
                @click="askTutor"
              >
                向导师提问
              </a-button>
              <div class="hint">
                {{
                  isAccepted
                    ? "实验已通过，可继续巩固关联知识点。"
                    : "修改代码后可重新提交评测；也可先复习关联知识点再试。"
                }}
              </div>
            </div>
          </template>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  CategoryDTO,
  KnowledgeGraphControllerService,
  LinkDTO,
  NodeDTO,
  QuestionControllerService,
  QuestionSubmitAddRequest,
  QuestionSubmitVO,
  QuestionVO,
} from "../../../generated";
import message from "@arco-design/web-vue/es/message";
import CodeEditor from "@/components/CodeEditor.vue";
import MdViewer from "@/components/MdViewer.vue";
import KnowledgeGraph from "@/components/KnowledgeGraph.vue";
import { openTutor } from "@/composables/useAiLauncher";
import {
  currentCourseCode,
  resolveCurrentCourse,
} from "@/composables/useCurrentCourse";
import {
  validateTutorAttachments,
  type TutorAttachment,
  type TutorLaunchRequest,
} from "@/types/tutor";
import { useRoute, useRouter } from "vue-router";
import CourseReturnBar from "@/components/learning/CourseReturnBar.vue";
import { courseContext, completeCourseActivity } from "@/api/course";
import store from "@/store";
import {
  registerAiPageContext,
  unregisterAiPageContext,
} from "@/composables/useAiPageContext";

// AI 助手页面上下文（log3 阶段 4）：挂载注册、卸载注销
// 最近判题结果摘要：取现有 submitDetail / judgeInfo 字段（终态 2/3 才算有效），不发明新字段
const lastJudgeResultSummary = computed(() => {
  const detail = submitDetail.value;
  if (!detail || (detail.status !== 2 && detail.status !== 3)) {
    return undefined;
  }
  const label = isAccepted.value ? "通过" : terminalText.value;
  const raw = detail.judgeInfo?.message || "";
  const message = raw ? `，判题消息：${raw}` : "";
  return `#${detail.id ?? "—"} ${label}${message}`;
});

const experimentCourseReady = ref(false);
const experimentCourseCode = computed(() =>
  experimentCourseReady.value ? currentCourseCode.value : ""
);
const aiPageContextProvider = () => ({
  pageName: "编程实验工作区",
  courseCode: experimentCourseCode.value,
  unitCode: routeValue(route.query.sourceUnitCode) || undefined,
  experimentTitle: question.value?.title || undefined,
  chapter: chapter.value || undefined,
  resourceRef: `experiment:${props.id}`,
  lastJudgeResult: lastJudgeResultSummary.value,
});
onMounted(() => registerAiPageContext(aiPageContextProvider));
onBeforeUnmount(() => unregisterAiPageContext(aiPageContextProvider));

// eslint-disable-next-line no-undef
interface Props {
  id: string;
}

// eslint-disable-next-line no-undef
const props = withDefaults(defineProps<Props>(), {
  id: () => "",
});

const router = useRouter();
const route = useRoute();

// ---------- 题面与关联图谱 ----------
const question = ref<QuestionVO | null>(null);
const isLocalBuild = computed(
  () => question.value?.judgeMode === "LOCAL_BUILD"
);
const judgeModeDescription = computed(() => {
  if (question.value?.judgeMode === "LOCAL_BUILD")
    return "本实验需按说明在本地环境构建和验证，平台不自动判分。";
  if (question.value?.judgeMode === "CONCEPT_HARNESS")
    return "可在线提交概念验证代码；通过只说明验证程序符合测试，不代表已完成 Minix 内核构建。实际构建请按实验说明在本地完成。";
  if (question.value?.judgeMode === "AUTO")
    return "本实验可在线提交代码，平台按测试数据评测。";
  return "当前实验的评测方式尚未提供，请查看实验说明。";
});
const loading = ref(false);
const chapter = ref("");
const nodes = ref<NodeDTO[]>([]);
const links = ref<LinkDTO[]>([]);
const categories = ref<CategoryDTO[]>([]);
const descriptionTab = ref("desc");
const graphLoading = ref(false);
let graphLoaded = false;
let loadToken = 0;
let disposed = false;
const routeValue = (value: unknown) =>
  Array.isArray(value)
    ? String(value[0] || "")
    : typeof value === "string"
    ? value
    : "";
const contextNode = computed(() => routeValue(route.query.nodeName));
let courseResolveSequence = 0;
watch(
  () => route.query.courseCode,
  async (requested) => {
    const sequence = ++courseResolveSequence;
    experimentCourseReady.value = false;
    try {
      await resolveCurrentCourse(
        routeValue(requested),
        () => !disposed && sequence === courseResolveSequence
      );
      if (!disposed && sequence === courseResolveSequence)
        experimentCourseReady.value = true;
    } catch {
      if (!disposed && sequence === courseResolveSequence)
        currentCourseCode.value = "";
    }
  },
  { immediate: true }
);

// ---------- 代码与草稿 ----------
const form = ref<QuestionSubmitAddRequest>({
  language: "c",
  code: "",
});

const labFigure = computed(() => {
  const title = question.value?.title || "";
  const match = /实验\s*([\d.]+)/.exec(title);
  return match ? `FIG. ${match[1]}` : "LAB";
});

const labSpec = computed(() => {
  const language = String(form.value.language || "c").toUpperCase();
  const tags = (question.value?.tags || []).slice(0, 2).join(" / ");
  return tags ? `${language} · ${tags}` : `${language} · 判题由后端执行`;
});

const currentUserId = () => store.state.user.loginUser?.id ?? "guest";
/** 草稿按 用户+实验+语言 区分；失败与查询刷新不会覆盖草稿 */
const draftKey = () =>
  `oslab:code-draft:${currentUserId()}:${props.id}:${form.value.language}`;
const backupKey = () => `${draftKey()}:before-restore`;
function saveBackup(value: string) {
  const previous = localStorage.getItem(backupKey());
  if (previous !== null) {
    const archive = `${backupKey()}:history:${Date.now()}-${Math.random()
      .toString(36)
      .slice(2)}`;
    localStorage.setItem(archive, previous);
  }
  localStorage.setItem(backupKey(), value);
}
const codeSource = ref<"starter" | "draft" | "empty">("empty");
const hasBackup = ref(false);
function detectedLanguage(code: string): string {
  if (
    /\b(?:std::|using\s+namespace\s+std)\b|#\s*include\s*<iostream>/.test(code)
  )
    return "cpp";
  if (/\bpublic\s+class\b|\bSystem\.out\./.test(code)) return "java";
  if (/\bpackage\s+main\b|\bfunc\s+main\s*\(/.test(code)) return "go";
  if (/#\s*include\s*<(?:stdio|string|stdlib)\.h>/.test(code)) return "c";
  return "";
}
const starterCode = computed(() => {
  const code = question.value?.originalCode || "";
  const language = detectedLanguage(code);
  return !language || language === form.value.language ? code : "";
});
const codeSourceLabel = computed(() =>
  codeSource.value === "draft"
    ? "当前内容：本地草稿"
    : codeSource.value === "starter"
    ? "当前内容：题目初始代码"
    : "当前语言暂无初始代码"
);
const codeLanguageWarning = computed(() => {
  const language = detectedLanguage(form.value.code || "");
  return language && language !== form.value.language
    ? `当前内容看起来是 ${language.toUpperCase()}，与所选语言不一致；草稿已保留，请切换语言或自行修改。`
    : "";
});

const loadDraft = () => {
  try {
    const draft = localStorage.getItem(draftKey());
    form.value.code = draft ?? starterCode.value;
    codeSource.value =
      draft !== null ? "draft" : starterCode.value ? "starter" : "empty";
    hasBackup.value = localStorage.getItem(backupKey()) !== null;
  } catch {
    form.value.code = starterCode.value;
    codeSource.value = starterCode.value ? "starter" : "empty";
    hasBackup.value = false;
  }
};

const changeCode = (value: string) => {
  form.value.code = value;
  codeSource.value = "draft";
  try {
    localStorage.setItem(draftKey(), value);
  } catch {
    // 浏览器存储不可用时，编辑和提交仍可继续。
  }
};

const restoreStarter = () => {
  const starter = starterCode.value;
  if (!starter) return;
  const current = form.value.code || "";
  if (current !== starter) {
    if (
      !window.confirm(
        "恢复初始代码会替换当前内容。当前草稿会保留，可通过“恢复替换前草稿”找回。继续吗？"
      )
    )
      return;
    try {
      saveBackup(current);
      hasBackup.value = true;
    } catch {
      message.error("无法保存当前草稿，未恢复初始代码。");
      return;
    }
  }
  form.value.code = starter;
  codeSource.value = "starter";
  try {
    localStorage.setItem(draftKey(), starter);
  } catch {
    message.warning("初始代码已载入，但无法保存到本地。");
  }
};
const restoreBackup = () => {
  try {
    const backup = localStorage.getItem(backupKey());
    if (backup === null) return;
    if (
      (form.value.code || "") !== backup &&
      !window.confirm("恢复替换前草稿会替换当前内容。继续吗？")
    )
      return;
    const current = form.value.code || "";
    saveBackup(current);
    localStorage.setItem(draftKey(), backup);
    form.value.code = backup;
    codeSource.value = "draft";
  } catch {
    message.error("无法读取或保存草稿。");
  }
};

const onLanguageChange = () => {
  loadDraft();
};

// ---------- 加载 ----------
const loadData = async () => {
  const token = ++loadToken;
  loading.value = true;
  question.value = null;
  nodes.value = [];
  links.value = [];
  categories.value = [];
  graphLoaded = false;
  graphLoading.value = false;
  descriptionTab.value = "desc";
  chapter.value = routeValue(route.query.chapter);
  try {
    const res = await QuestionControllerService.getQuestionVoByIdUsingGet(
      props.id as unknown as number
    );
    if (token !== loadToken) return;
    if (res.code === 0 && res.data) {
      question.value = res.data;
      loadDraft();
    } else {
      message.error("加载失败，" + res.message);
    }
  } catch (error) {
    if (token === loadToken) message.error("加载实验失败，请稍后重试");
  } finally {
    if (token === loadToken) loading.value = false;
  }
};

const loadRelatedGraph = async (key: string | number) => {
  if (key !== "graph" || graphLoaded || graphLoading.value) return;
  if (!question.value?.tags?.length) return;
  const requestId = props.id;
  graphLoading.value = true;
  try {
    const graph =
      await KnowledgeGraphControllerService.getKnowledgeGraphDataByListUsingPost(
        question.value.tags
      );
    if (requestId !== props.id) return;
    if (graph.code === 0 && graph.data) {
      nodes.value = graph.data.nodes || [];
      links.value = graph.data.links || [];
      categories.value = graph.data.categories || [];
      graphLoaded = true;
    }
  } catch (error) {
    message.error("加载关联图谱失败，请稍后重试");
  } finally {
    if (requestId === props.id) graphLoading.value = false;
  }
};

// ---------- 提交与留页串行轮询 ----------
const submitting = ref(false);
const submitId = ref<number | null>(null);
const submitDetail = ref<QuestionSubmitVO | null>(null);
const courseProgressError = ref("");
const saveCourseProgress = async () => {
  const context = courseContext(route.query);
  if (!context.sourceUnitCode || !context.sourceActivityCode || !submitId.value)
    return;
  courseProgressError.value = "";
  try {
    await completeCourseActivity(context.sourceActivityCode, {
      sourceUnitCode: context.sourceUnitCode,
      mode: route.query.courseMode === "REVIEW" ? "REVIEW" : "MAINLINE",
      submissionId: String(submitId.value),
    });
  } catch (e) {
    courseProgressError.value =
      e instanceof Error ? e.message : "活动进度保存失败";
  }
};
/** idle：未提交 / polling：轮询中 / timeout：超时可继续 / terminal：终态 */
const phase = ref<"idle" | "polling" | "timeout" | "terminal">("idle");

const POLL_INTERVAL_MS = 2000;
const POLL_MAX_ROUNDS = 30;
let pollTimer: number | null = null;
let pollRounds = 0;
let pollToken = 0;
let submitEpoch = 0;

// 真实状态枚举：0 等待中 / 1 判题中 / 2 成功 / 3 失败
const statusText = computed(() => {
  const status = submitDetail.value?.status;
  if (status === 0) return "等待判题";
  if (status === 1) return "判题中";
  return "等待判题";
});

const isAccepted = computed(
  () =>
    submitDetail.value?.status === 2 &&
    submitDetail.value?.judgeInfo?.message === "Accepted"
);

const terminalText = computed(() => {
  if (!submitDetail.value) return "";
  if (isAccepted.value) return "通过";
  if (submitDetail.value.status === 2) return "未通过";
  return "失败";
});

const terminalColor = computed(() => (isAccepted.value ? "green" : "red"));

const JUDGE_MESSAGE_TEXT: Record<string, string> = {
  Accepted: "通过（Accepted）",
  "Wrong Answer": "答案错误（Wrong Answer）",
  "Compile Error": "编译错误（Compile Error）",
  "Runtime Error": "运行错误（Runtime Error）",
  "Time Limit Exceeded": "超时（Time Limit Exceeded）",
  "Memory leak": "内存超限（Memory leak）",
  "Output Limit Exceeded": "输出超限（Output Limit Exceeded）",
  "Presentation Error": "展示错误（Presentation Error）",
  "Dangerous Operation": "危险操作（Dangerous Operation）",
  "System Error": "系统错误（不会计入知识失误）",
  Waiting: "等待中",
};

const judgeMessageText = computed(() => {
  const raw = submitDetail.value?.judgeInfo?.message || "";
  return JUDGE_MESSAGE_TEXT[raw] || raw || "暂无判题消息";
});

const stopPolling = () => {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
};

const pollOnce = async (token: number) => {
  if (disposed || token !== pollToken) return;
  try {
    const res = await QuestionControllerService.getQuestionSubmitByIdUsingGet(
      submitId.value as number
    );
    if (disposed || token !== pollToken) return;
    if (res.code === 0 && res.data) {
      submitDetail.value = res.data;
      const status = res.data.status;
      if (status === 2 || status === 3) {
        phase.value = "terminal";
        await saveCourseProgress();
        window.dispatchEvent(new Event("oslab:learning-changed"));
        return;
      }
    }
  } catch (e) {
    // 单次查询异常不中断轮询，也不伪造成失败
  }
  if (disposed || token !== pollToken) return;
  pollRounds += 1;
  if (pollRounds >= POLL_MAX_ROUNDS) {
    phase.value = "timeout";
    return;
  }
  pollTimer = window.setTimeout(() => pollOnce(token), POLL_INTERVAL_MS);
};

const startPolling = () => {
  if (disposed || submitId.value === null) return;
  stopPolling();
  pollToken += 1;
  pollRounds = 0;
  phase.value = "polling";
  pollTimer = window.setTimeout(() => pollOnce(pollToken), POLL_INTERVAL_MS);
};

const resumePolling = () => {
  startPolling();
};

const doSubmit = async () => {
  if (disposed || submitting.value || !question.value?.id) {
    return;
  }
  if (isLocalBuild.value) {
    message.info("请按实验说明在本地 MIPS 环境构建与验证");
    return;
  }
  if (!form.value.code || !form.value.code.trim()) {
    message.warning("请先编写代码再提交");
    return;
  }
  const requestId = props.id;
  const epoch = ++submitEpoch;
  const isCurrentRequest = () =>
    !disposed && requestId === props.id && epoch === submitEpoch;
  submitting.value = true;
  stopPolling();
  pollToken += 1;
  try {
    const res = await QuestionControllerService.doQuestionSubmitUsingPost({
      ...form.value,
      ...courseContext(route.query),
      questionId: question.value.id,
    });
    if (!isCurrentRequest()) return;
    if (res.code === 0 && res.data) {
      submitId.value = res.data;
      submitDetail.value = null;
      message.success("已提交，正在等待判题");
      startPolling();
    } else {
      message.error("提交失败，" + res.message);
    }
  } catch (e) {
    if (isCurrentRequest()) message.error("提交失败，请稍后重试");
  } finally {
    if (isCurrentRequest()) submitting.value = false;
  }
};

// ---------- 实验导师：统一入口，代码/报错由学生明确勾选 ----------
/** 导师上下文节点：优先路由携带的节点，否则取实验题第一个映射节点 */
const tutorNodeName = computed(
  () => contextNode.value || question.value?.tags?.[0] || ""
);
const tutorQuestion = ref("");
const attachCode = ref(false);
const attachJudge = ref(false);
const codeAttachmentText = ref("");
const judgeAttachmentText = ref("");
const selectionAttachmentText = ref("");

function buildJudgeAttachmentText() {
  const detail = submitDetail.value;
  if (!detail) return "";
  const label =
    detail.status === 2
      ? isAccepted.value
        ? "通过"
        : "未通过"
      : detail.status === 3
      ? "判题失败"
      : "判题中";
  return `实验 #${props.id} 最近提交 #${detail.id ?? "—"} ${label}\n判题消息：${
    judgeMessageText.value || "无"
  }`;
}

watch(attachJudge, (on) => {
  if (on && !judgeAttachmentText.value.trim()) {
    judgeAttachmentText.value = buildJudgeAttachmentText();
  }
});

watch(attachCode, (on) => {
  if (on && !codeAttachmentText.value.trim()) {
    codeAttachmentText.value = form.value.code || "";
  }
});

/** 只收集学生明确勾选/编辑的内容；绝不自动抓取整个编辑器或实验全文。 */
function buildExperimentAttachments(): TutorAttachment[] {
  const items: TutorAttachment[] = [];
  if (selectionAttachmentText.value.trim()) {
    items.push({
      kind: "SELECTION",
      label: "实验说明选段",
      text: selectionAttachmentText.value.trim(),
      sourceId: `experiment:${props.id}`,
    });
  }
  if (attachCode.value && codeAttachmentText.value.trim()) {
    items.push({
      kind: "CODE",
      label: `当前代码（${form.value.language || "未选择语言"}）`,
      text: codeAttachmentText.value.trim(),
      sourceId: `experiment:${props.id}`,
    });
  }
  if (attachJudge.value && judgeAttachmentText.value.trim()) {
    items.push({
      kind: "ERROR",
      label: "最近判题结果",
      text: judgeAttachmentText.value.trim(),
      sourceId: submitDetail.value
        ? `submit:${submitDetail.value.id}`
        : `experiment:${props.id}`,
    });
  }
  return items;
}

const attachmentError = computed(() => {
  if (
    selectionAttachmentText.value.length > 2000 ||
    codeAttachmentText.value.length > 8000 ||
    judgeAttachmentText.value.length > 8000
  ) {
    return "附带内容超出长度上限，请手动精简后再发送（不会静默截断）。";
  }
  const check = validateTutorAttachments(buildExperimentAttachments());
  return check.ok ? "" : check.error || "附带材料超限。";
});

function openExperimentTutor(delivery: "DRAFT" | "SEND") {
  if (attachmentError.value) {
    message.warning(attachmentError.value);
    return;
  }
  const request: TutorLaunchRequest = {
    scene: "EXPERIMENT",
    intent: lastJudgeResultSummary.value ? "DEBUG" : "ASK",
    courseCode: experimentCourseCode.value,
    scope: "PAGE",
    nodeCodes: routeValue(route.query.nodeCode)
      ? [routeValue(route.query.nodeCode)]
      : [],
    resourceRef: `experiment:${props.id}`,
    eventIds: [],
    draft:
      tutorQuestion.value.trim() ||
      (lastJudgeResultSummary.value
        ? "我的提交没有通过，请帮我定位问题并给出修改建议。"
        : "请结合当前实验帮我梳理排查步骤。"),
    attachments: buildExperimentAttachments(),
    delivery,
    sceneLabel: question.value?.title || undefined,
  };
  openTutor(request);
}

/** 失败提交后的导师入口：切到“向导师提问”页签并预填，由用户明确发送（不自动消耗 AI 配额） */
const askTutor = () => {
  descriptionTab.value = "tutor";
  attachJudge.value = true;
  if (!tutorQuestion.value.trim()) {
    tutorQuestion.value = isAccepted.value
      ? "请点评我的实现，并给出巩固相关知识点的方法。"
      : "我的提交没有通过，请帮我定位问题并给出修改建议。";
  }
};

// ---------- 跳转 ----------
const goNode = (nodeName: string) => {
  if (!nodeName) return;
  const code =
    nodeName === contextNode.value
      ? routeValue(route.query.nodeCode)
      : nodes.value.find((node) => node.name === nodeName)?.nodeCode || "";
  router.push({
    path: code
      ? `/knowledge/concept/${encodeURIComponent(code)}`
      : `/knowledge/node/${encodeURIComponent(nodeName)}`,
    query: experimentCourseCode.value
      ? { courseCode: experimentCourseCode.value }
      : {},
  });
};

const goGraphPage = () => {
  router.push({
    path: "/graph",
    query: experimentCourseCode.value
      ? { courseCode: experimentCourseCode.value }
      : {},
  });
};

const goRecords = () => {
  router.push({
    path: "/records",
    query: {
      eventType: "JUDGE",
      nodeName: contextNode.value || undefined,
      courseCode: experimentCourseCode.value || undefined,
    },
  });
};

const returnToCatalog = () => {
  const returnTo = routeValue(route.query.returnTo);
  if (returnTo.startsWith("/") && !returnTo.startsWith("//")) {
    router.push(returnTo);
    return;
  }
  router.push({
    path: "/experiments",
    query: {
      courseCode: experimentCourseCode.value || undefined,
      nodeName: contextNode.value || undefined,
      chapter: chapter.value || undefined,
    },
  });
};

// ---------- 生命周期 ----------
const resetSubmitState = () => {
  stopPolling();
  pollToken += 1;
  submitEpoch += 1;
  submitting.value = false;
  submitId.value = null;
  submitDetail.value = null;
  phase.value = "idle";
};

onMounted(() => {
  loadData();
});

// 切换实验（路由参数变化复用组件）时停止轮询并重置状态
watch(
  () => props.id,
  (newId, oldId) => {
    if (newId !== oldId) {
      resetSubmitState();
      loadData();
    }
  }
);

onBeforeUnmount(() => {
  disposed = true;
  submitEpoch += 1;
  stopPolling();
  loadToken += 1;
  pollToken += 1;
});
</script>

<style scoped>
.experiment-workbench {
  max-width: 1600px;
  margin: 0 auto;
  padding: 0 4px;
}

.workbench-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 12px 0;
}

.workbench-title .title {
  margin: 0 0 8px;
  font-size: 22px;
}

.workbench-title .meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.node-tag {
  cursor: pointer;
}

.workbench-body {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(420px, 1.2fr);
  gap: 16px;
  align-items: start;
}

.panel {
  min-width: 0;
  background: var(--oslab-panel, #fff);
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-lg, 8px);
}

.workbench-crumb {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.panel-left {
  grid-row: span 2;
  padding: 0 16px 16px;
}
.panel-result {
  grid-column: 2;
}
.status-card {
  padding: 16px;
}
.status-card h3 {
  margin: 0 0 14px;
  color: var(--oslab-text-title, #1d2129);
  font-size: 15px;
}
.editor-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  color: var(--oslab-text-title, #1d2129);
  font-size: 13px;
}
.editor-heading .hint {
  margin: 0;
  flex: 1 1 220px;
}
.code-language-warning {
  margin: 0;
  padding: 12px 14px;
  color: var(--oslab-lab-ink-soft, var(--oslab-ink-soft));
  background: var(--oslab-lab-panel, var(--oslab-panel));
  border-bottom: 1px solid var(--oslab-line);
  font-size: 14px;
  line-height: 1.6;
}

.panel-left :deep(.arco-tabs-content) {
  padding-top: 8px;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.judge-message {
  font-weight: 500;
}

.hint {
  margin-top: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}

.remediation {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.remediation-title {
  font-weight: 600;
}

.tutor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.tutor-sub {
  margin: 0;
  color: var(--oslab-text-secondary, #86909c);
  font-size: 12px;
}
.tutor-messages {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 380px;
  overflow-y: auto;
  padding: 10px;
  background: var(--oslab-panel-sub, #f7f8fa);
  border: 1px solid var(--oslab-divider, #f2f3f5);
  border-radius: var(--oslab-radius-md, 6px);
}
.tutor-msg {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: 92%;
}
.tutor-msg.is-user {
  align-self: flex-end;
  align-items: flex-end;
}
.tutor-msg.is-tutor {
  align-self: flex-start;
  align-items: flex-start;
}
.tutor-role {
  color: var(--oslab-text-secondary, #86909c);
  font-size: 11px;
}
.tutor-mode {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 8px;
  font-size: 11px;
  line-height: 16px;
  vertical-align: 1px;
}
.tutor-mode.is-ai {
  color: var(--oslab-primary, #2447d8);
  background: var(--oslab-primary-soft, #eaf0fd);
}
.tutor-mode.is-fallback {
  color: var(--oslab-text-secondary, #86909c);
  background: var(--oslab-panel-sub, #f7f8fa);
}
.tutor-sources {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
.tutor-sources button {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-size: 12px;
  color: var(--oslab-primary, #2447d8);
  cursor: pointer;
  text-align: left;
  overflow-wrap: anywhere;
}
.tutor-bubble {
  padding: 8px 10px;
  border: 1px solid var(--oslab-border, #e5e6eb);
  border-radius: var(--oslab-radius-md, 6px);
  background: var(--oslab-panel, #fff);
  color: var(--oslab-text-body, #4e5969);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.tutor-msg.is-user .tutor-bubble {
  background: var(--oslab-primary-soft, #eaf0fd);
  border-color: transparent;
}
.tutor-bubble-pending {
  color: var(--oslab-text-secondary, #86909c);
}
.tutor-empty {
  margin: 0;
}
.tutor-input {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 8px;
}
.tutor-guard-hint {
  margin: 0;
}

/* 1024px 以下：两栏纵向堆叠，评测结果置底 */
/* C+B 学习图册 */
.experiment-workbench {
  gap: 16px;
}
.workbench-header {
  padding: 12px 0;
  border-bottom: 1px solid var(--oslab-line);
}
.workbench-title .title {
  font-family: var(--oslab-font-display);
  font-size: 28px;
}
.workbench-crumb {
  font-family: var(--oslab-font-mono);
  font-size: 10px;
}
.panel {
  border-radius: var(--oslab-radius-container);
  box-shadow: var(--oslab-shadow-card);
}
.status-card,
.remediation {
  border-radius: var(--oslab-radius-md);
}
.node-tag {
  border-radius: var(--oslab-radius-pill);
}

/* 2026-10-24 实验页 B：蓝图底、墨线方角、红批注 */
.oslab-lab-b.experiment-workbench {
  padding: 14px;
  background: linear-gradient(rgba(31, 95, 191, 0.055) 1px, transparent 1px) 0 0 /
      24px 24px,
    linear-gradient(90deg, rgba(31, 95, 191, 0.055) 1px, transparent 1px) 0 0 /
      24px 24px,
    linear-gradient(rgba(31, 95, 191, 0.1) 1px, transparent 1px) 0 0 / 120px
      120px,
    linear-gradient(90deg, rgba(31, 95, 191, 0.1) 1px, transparent 1px) 0 0 /
      120px 120px,
    var(--oslab-lab-bg);
}
.oslab-lab-b .workbench-crumb {
  padding-bottom: 4px;
  border-bottom: 1px solid var(--oslab-lab-line-strong);
  color: var(--oslab-lab-muted);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.06em;
}
.oslab-lab-b .workbench-header {
  padding: 10px 0 8px;
  border-bottom: 1px solid var(--oslab-lab-ink);
}
.oslab-lab-b .lab-title-line {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}
.oslab-lab-b .lab-fig {
  padding: 1px 5px;
  border: 1px solid var(--oslab-lab-red);
  color: var(--oslab-lab-red);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
  letter-spacing: 0.08em;
}
.oslab-lab-b .workbench-title .title {
  margin: 0;
  font-family: var(--oslab-font-tech);
  font-size: 26px;
  letter-spacing: 0.04em;
}
.oslab-lab-b .lab-spec {
  margin: 4px 0 0;
  color: var(--oslab-lab-muted);
  font-family: var(--oslab-font-mono);
  font-size: 8.5px;
  letter-spacing: 0.06em;
}
.oslab-lab-b .panel {
  border-color: var(--oslab-lab-line-strong);
  border-radius: 0;
  background: var(--oslab-lab-panel);
  box-shadow: none;
}
.oslab-lab-b .panel-editor {
  border-color: var(--oslab-lab-ink);
  background: #10141a;
}
.oslab-lab-b .editor-heading,
.oslab-lab-b .status-card h3 {
  font-family: var(--oslab-font-tech);
}
.oslab-lab-b .editor-heading {
  border-bottom: 1px solid var(--oslab-lab-ink);
  background: var(--oslab-lab-panel);
  color: var(--oslab-lab-ink);
}
.oslab-lab-b .panel-kicker {
  color: var(--oslab-lab-red);
  font-family: var(--oslab-font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}
.oslab-lab-b .status-card {
  padding: 14px;
  border-bottom: 1px solid var(--oslab-lab-line-strong);
  background: var(--oslab-lab-panel);
}
.oslab-lab-b .status-card h3 {
  margin-bottom: 10px;
  color: var(--oslab-lab-ink);
}
.oslab-lab-b .lab-margin-note {
  margin: 12px 0 0 24px;
  padding-left: 9px;
  border-left: 2px solid var(--oslab-lab-red);
  color: var(--oslab-lab-red);
  font-family: var(--oslab-font-display);
  font-size: 11px;
  line-height: 1.55;
  transform: rotate(-1deg);
}
.oslab-lab-b :deep(.arco-tabs-tab-active .arco-tabs-tab-title) {
  color: var(--oslab-lab-red);
}
.oslab-lab-b :deep(.arco-tabs-ink-bar) {
  background: var(--oslab-lab-red);
}
.oslab-lab-b :deep(.markdown-body h2) {
  padding-left: 8px;
  border-left: 2px solid var(--oslab-lab-red);
  font-family: var(--oslab-font-tech);
  font-size: 14px;
}
.oslab-lab-b :deep(.markdown-body h3) {
  padding-left: 8px;
  border-left: 2px solid var(--oslab-lab-red);
  font-family: var(--oslab-font-tech);
}
.oslab-lab-b :deep(.arco-btn-primary) {
  border-radius: 0;
  background: var(--oslab-lab-ink);
}
.oslab-lab-b :deep(.arco-select-view) {
  border-radius: 0;
  background: var(--oslab-lab-panel);
}
.oslab-lab-b .node-tag {
  border: 1px solid var(--oslab-lab-line);
  border-radius: 0;
  background: transparent;
  color: var(--oslab-lab-ink-soft);
}
.oslab-lab-b .status-card,
.oslab-lab-b .remediation {
  border-radius: 0;
}
.oslab-lab-b .status-card :deep(.arco-empty) {
  color: var(--oslab-lab-muted);
}

@media (max-width: 1024px) {
  .workbench-body {
    grid-template-columns: 1fr;
  }
  .panel-result {
    grid-column: 1;
  }
  .panel-left {
    grid-row: auto;
  }
}

/* 实验工作台：长文与编辑区域分别滚动，窄屏恢复自然页面流。 */
.experiment-workbench {
  --lab-workbench-gap: 16px;
  --lab-reading-height: clamp(440px, calc(100vh - 205px), 900px);
}

.workbench-body {
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: var(--lab-workbench-gap);
}

.panel-left {
  max-height: var(--lab-reading-height);
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.panel-left :deep(.arco-tabs) {
  overflow: visible;
}

.panel-left :deep(.arco-tabs-nav) {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--oslab-lab-panel, #fff);
}

.oslab-lab-b :deep(.arco-btn-primary.arco-btn-disabled) {
  color: #525b66;
  background: #dbe2ea;
  border-color: #c3cedb;
  cursor: not-allowed;
}

.panel-left :deep(.markdown-body) {
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.panel-left :deep(.markdown-body h1),
.panel-left :deep(.markdown-body h2),
.panel-left :deep(.markdown-body h3) {
  line-height: 1.35;
  margin-top: 1.4em;
}

.oslab-lab-b .panel-left :deep(.markdown-body h2) {
  font-size: 18px;
}

.oslab-lab-b .panel-left :deep(.markdown-body h3) {
  font-size: 16px;
}

.panel-left :deep(.markdown-body pre),
.panel-left :deep(.markdown-body table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
}

.panel-left :deep(.markdown-body pre),
.panel-left :deep(.markdown-body code) {
  font-size: 13px;
}

.panel-editor :deep(.code-editor-container) {
  height: clamp(340px, 52vh, 600px);
}

.panel-result {
  max-height: 360px;
  overflow: auto;
  overscroll-behavior: contain;
}

@media (max-width: 1024px) {
  .workbench-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .panel-left,
  .panel-result {
    max-height: none;
    overflow: visible;
  }

  .panel-left :deep(.arco-tabs-nav) {
    position: static;
  }
}

@media (max-width: 480px) {
  .editor-heading :deep(.arco-btn) {
    flex: 1 1 auto;
    min-height: 44px;
  }

  .oslab-lab-b.experiment-workbench {
    padding: 8px;
  }

  .workbench-body {
    gap: 12px;
  }

  .panel-left {
    padding: 0 12px 12px;
  }

  .panel-editor :deep(.code-editor-container) {
    height: 360px;
  }
}

@media (pointer: coarse) {
  .experiment-workbench :deep(.arco-btn),
  .experiment-workbench :deep(.arco-tabs-tab),
  .experiment-workbench :deep(.arco-select-view) {
    min-height: 44px;
  }
}
</style>
