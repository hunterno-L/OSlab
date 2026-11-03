/**
 * 统一 AI 课程会话（ZhiCore AI 升级 S2）。
 *
 * - 会话以后端 /learning/ai/sessions 为准；localStorage 只保存
 *   “用户 + 课程 -> sessionId” 指针，不缓存消息、分数或证据。
 * - 发送轮次带 clientRequestId 幂等键；返回 RUNNING 时按 ≥1.5s 间隔轮询
 *   GET /learning/ai/turns/{turnId} 直到终态。
 * - 失败保留最后问题可重试；模型/检索降级（mode=FALLBACK）在消息上标注。
 * - 跨页面共享同一会话；切换课程时切换会话作用域，避免混入另一课程记录。
 */
import { computed, ref } from "vue";
import store from "@/store";
import {
  createAiSession,
  createAiTurn,
  getAiSession,
  getAiTurn,
  listAiNextActions,
  updateAiSession,
  type AiDiagnosis,
  type AiNextAction,
  type AiSourceRef,
  type AiToolTraceEntry,
  type AiTurnMode,
  type AiTurnStatus,
  type AiTurnVO,
  type AiUsage,
  type AiSessionFocus,
  type AiSessionVO,
} from "@/api/ai";
import {
  buildAiTurnContext,
  getAiPageContext,
  type AiTurnPageContext,
} from "@/composables/useAiPageContext";

export interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
  time: number;
  turnId?: string;
  status?: AiTurnStatus;
  mode?: AiTurnMode;
  sources?: AiSourceRef[];
  toolTrace?: AiToolTraceEntry[];
  diagnosis?: AiDiagnosis | null;
  nextActions?: AiNextAction[];
  usage?: AiUsage | null;
  error?: string;
}

export interface AskAiOptions {
  /** 覆盖页面注册的上下文（如题卡提交后附带 resourceRef/eventIds） */
  context?: Partial<AiTurnPageContext>;
  courseCode?: string;
  /** 失败重试时不重复入列用户消息 */
  pushUser?: boolean;
  /** 显式创建一个新会话（如“按可用时间复习”切换目标） */
  newSession?: boolean;
  goal?: string;
  focus?: AiSessionFocus;
  timeBudgetMinutes?: number;
}

export const DEFAULT_AI_COURSE = "course.operating-systems";
/** 轮询间隔必须 ≥1.5s（任务书 §3），这里取 1.6s 留出网络抖动余量 */
export const AI_POLL_INTERVAL_MS = 1600;
/** 单轮最长等待；覆盖后端两次模型调用（各 30s）与工具执行 */
export const AI_POLL_TIMEOUT_MS = 120000;
const STORAGE_PREFIX = "oslab-ai-session-pointer-";
const DEFAULT_GOAL = "继续当前任务";

type SessionPointers = Record<string, string>;

const session = ref<AiSessionVO | null>(null);
const messages = ref<AiChatMessage[]>([]);
const loading = ref(false);
const errorMsg = ref("");
const lastQuestion = ref("");
const lastQuestionContext = ref<Partial<AiTurnPageContext>>({});
const retryable = ref(false);
const activeCourseCode = ref("");
const nextActions = ref<AiNextAction[]>([]);
const nextActionsLoading = ref(false);
const nextActionsError = ref("");
const dueReviewCount = ref(0);
const planGeneratedAt = ref("");
const planFocus = ref<AiSessionFocus | "">("");
const planTimeBudgetMinutes = ref<number | null>(null);
const pollingTurnId = ref("");
const sessionRestored = ref(false);

let restorePromise: Promise<void> | null = null;
let ensureInFlight: Promise<AiSessionVO | null> | null = null;
let lastUserId: string | number | null = null;

function readUserId(): string | number | null {
  const id = store.state.user?.loginUser?.id;
  return typeof id === "number" || typeof id === "string" ? id : null;
}
lastUserId = readUserId();

function pointerKey(): string {
  return lastUserId !== null ? `${STORAGE_PREFIX}${lastUserId}` : "";
}

function readPointers(): SessionPointers {
  const key = pointerKey();
  if (!key) return {};
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : {};
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
      return {};
    const out: SessionPointers = {};
    Object.entries(parsed as Record<string, unknown>).forEach(
      ([course, id]) => {
        if (typeof id === "string" && id.trim()) out[course] = id;
      }
    );
    return out;
  } catch (e) {
    console.warn("[AI 会话] 读取会话指针失败", e);
    return {};
  }
}

function writePointer(courseCode: string, sessionId: string) {
  const key = pointerKey();
  if (!key || !courseCode) return;
  try {
    const pointers = readPointers();
    pointers[courseCode] = sessionId;
    localStorage.setItem(key, JSON.stringify(pointers));
  } catch (e) {
    console.warn("[AI 会话] 保存会话指针失败", e);
  }
}

function removePointer(courseCode: string) {
  const key = pointerKey();
  if (!key || !courseCode) return;
  try {
    const pointers = readPointers();
    delete pointers[courseCode];
    localStorage.setItem(key, JSON.stringify(pointers));
  } catch (e) {
    console.warn("[AI 会话] 清理会话指针失败", e);
  }
}

function resetForUser() {
  session.value = null;
  messages.value = [];
  loading.value = false;
  errorMsg.value = "";
  lastQuestion.value = "";
  lastQuestionContext.value = {};
  retryable.value = false;
  activeCourseCode.value = "";
  nextActions.value = [];
  nextActionsLoading.value = false;
  nextActionsError.value = "";
  dueReviewCount.value = 0;
  planGeneratedAt.value = "";
  planFocus.value = "";
  planTimeBudgetMinutes.value = null;
  pollingTurnId.value = "";
  sessionRestored.value = false;
  restorePromise = null;
  ensureInFlight = null;
}

// 登录用户变化：清空内存态并切换到该用户自己的会话指针（不串号）
store.watch(
  () => readUserId(),
  (id) => {
    if (id === lastUserId) return;
    lastUserId = id;
    resetForUser();
  }
);

function describeError(e: unknown): string {
  const error = e as {
    message?: string;
    code?: string;
    status?: number;
    response?: { status?: number; data?: { message?: string } };
  };
  if (error?.code === "ECONNABORTED" || error?.message === "timeout") {
    return "请求超时，请检查网络后重试。";
  }
  const status = error?.status ?? error?.response?.status;
  if (status === 401 || status === 403) {
    return "登录状态已失效或无权限，请重新登录后再试。";
  }
  if (status === 404) {
    return "AI 服务暂未开放，请稍后再试或联系管理员。";
  }
  const backendMessage = error?.response?.data?.message;
  if (typeof backendMessage === "string" && backendMessage.trim()) {
    return backendMessage;
  }
  return error?.message && error.message !== "Network Error"
    ? error.message
    : "AI 服务暂时不可用，请稍后重试。";
}

function newClientRequestId(): string {
  // 项目 TypeScript 版本较旧，@types 中尚无 Crypto.randomUUID，做能力检测
  const cryptoApi =
    typeof crypto !== "undefined"
      ? (crypto as Crypto & { randomUUID?: () => string })
      : null;
  return cryptoApi && typeof cryptoApi.randomUUID === "function"
    ? cryptoApi.randomUUID()
    : `turn-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function mergeNextActions(actions?: AiNextAction[] | null) {
  if (!Array.isArray(actions)) return;
  nextActions.value = actions;
  planGeneratedAt.value = new Date().toISOString();
}

/** 仅当前登录用户仍一致时才应用响应，避免请求期间切换账号串号 */
function stillSameUser(userId: string | number | null): boolean {
  return lastUserId === userId;
}

async function restoreFromPointer(courseCode: string): Promise<void> {
  const sessionId = readPointers()[courseCode];
  if (!sessionId) return;
  try {
    const response = await getAiSession(sessionId);
    const data = response.data.code === 0 ? response.data.data : null;
    if (data && data.status === "ACTIVE" && data.courseCode === courseCode) {
      session.value = data;
      sessionRestored.value = true;
    } else {
      removePointer(courseCode);
    }
  } catch {
    // 指针过期（会话关闭/删除/无权限）时安静清理，等下次发送再新建
    removePointer(courseCode);
  }
}

/**
 * 切换当前课程作用域。跨课程时清空消息，但保留各课程自己的
 * sessionId 指针；切回原课程会恢复原会话。
 */
export async function syncAiCourse(courseCode: string): Promise<void> {
  const course = courseCode.trim();
  if (!course || course === activeCourseCode.value) {
    if (restorePromise) await restorePromise;
    return;
  }
  activeCourseCode.value = course;
  session.value = null;
  messages.value = [];
  errorMsg.value = "";
  nextActions.value = [];
  nextActionsError.value = "";
  dueReviewCount.value = 0;
  planGeneratedAt.value = "";
  planFocus.value = "";
  planTimeBudgetMinutes.value = null;
  sessionRestored.value = false;
  restorePromise = restoreFromPointer(course);
  await restorePromise;
}

async function createSession(options: AskAiOptions): Promise<AiSessionVO> {
  const course = activeCourseCode.value || DEFAULT_AI_COURSE;
  const page = getAiPageContext();
  const response = await createAiSession({
    courseCode: course,
    goal: options.goal || DEFAULT_GOAL,
    focus: options.focus || "LEARN",
    ...(options.timeBudgetMinutes
      ? { timeBudgetMinutes: options.timeBudgetMinutes }
      : {}),
    ...(page?.unitCode ? { unitCode: page.unitCode } : {}),
  });
  if (response.data.code !== 0 || !response.data.data) {
    throw new Error(response.data.message || "创建 AI 课程会话失败");
  }
  session.value = response.data.data;
  sessionRestored.value = false;
  writePointer(course, response.data.data.sessionId);
  return response.data.data;
}

/**
 * 确保当前课程存在可用会话：优先复用内存态 -> localStorage 指针 -> 新建。
 */
export async function ensureAiSession(
  options: AskAiOptions = {}
): Promise<AiSessionVO | null> {
  const course =
    options.courseCode ||
    getAiPageContext()?.courseCode ||
    activeCourseCode.value ||
    DEFAULT_AI_COURSE;
  if (course !== activeCourseCode.value) {
    await syncAiCourse(course);
  } else if (restorePromise) {
    await restorePromise;
  }
  if (options.newSession) {
    session.value = null;
    removePointer(course);
  }
  if (
    session.value &&
    session.value.status === "ACTIVE" &&
    session.value.courseCode === course
  ) {
    return session.value;
  }
  if (ensureInFlight) return ensureInFlight;
  ensureInFlight = (async () => {
    try {
      const pointer = readPointers()[course];
      if (pointer) {
        try {
          const response = await getAiSession(pointer);
          const data = response.data.code === 0 ? response.data.data : null;
          if (data && data.status === "ACTIVE" && data.courseCode === course) {
            session.value = data;
            sessionRestored.value = true;
            return data;
          }
        } catch {
          removePointer(course);
        }
      }
      return await createSession(options);
    } finally {
      ensureInFlight = null;
    }
  })();
  return ensureInFlight;
}

/** 轮询到终态；每 1.6s 一次，超时抛出可读错误。 */
async function pollTurn(
  turnId: string,
  initial: AiTurnVO,
  userId: string | number | null
): Promise<AiTurnVO | null> {
  let turn = initial;
  const startedAt = Date.now();
  while (turn.status === "RUNNING") {
    if (!stillSameUser(userId)) return null;
    if (Date.now() - startedAt > AI_POLL_TIMEOUT_MS) {
      throw new Error("AI 处理超时，请稍后重试。");
    }
    await delay(AI_POLL_INTERVAL_MS);
    if (!stillSameUser(userId)) return null;
    pollingTurnId.value = turnId;
    const response = await getAiTurn(turnId);
    if (response.data.code !== 0) {
      throw new Error(response.data.message || "读取 AI 轮次状态失败");
    }
    turn = response.data.data;
  }
  pollingTurnId.value = "";
  return turn;
}

function pushUserMessage(text: string) {
  messages.value.push({ role: "user", content: text, time: Date.now() });
}

function pushAssistantTurn(turn: AiTurnVO) {
  messages.value.push({
    role: "assistant",
    content: turn.answer || "",
    time: Date.now(),
    turnId: turn.turnId,
    status: turn.status,
    mode: turn.mode,
    sources: turn.sources || [],
    toolTrace: turn.toolTrace || [],
    diagnosis: turn.diagnosis || null,
    nextActions: turn.nextActions || [],
    usage: turn.usage || null,
  });
  mergeNextActions(turn.nextActions);
  if (typeof window !== "undefined") {
    window.dispatchEvent(
      new CustomEvent("oslab:ai-turn-completed", { detail: { turn } })
    );
  }
}

function markTurnFailure(turn: AiTurnVO, fallback: string) {
  const reason =
    turn.errorMessage ||
    (turn.status === "FAILED_RETRYABLE"
      ? "导师本轮暂时未能完成，可重试。"
      : fallback);
  errorMsg.value = reason;
  retryable.value = turn.status === "FAILED_RETRYABLE";
  messages.value.push({
    role: "assistant",
    content: reason,
    time: Date.now(),
    turnId: turn.turnId,
    status: turn.status,
    mode: turn.mode,
    error: reason,
  });
}

/**
 * 发送一轮提问。返回终态轮次供题卡/模拟页内联展示诊断与来源；
 * 失败或超时返回 null，并把可读错误放到 errorMsg。
 */
export async function askAi(
  text: string,
  options: AskAiOptions = {}
): Promise<AiTurnVO | null> {
  const question = text.trim();
  if (!question || loading.value) return null;
  const userId = lastUserId;
  const context = buildAiTurnContext({
    ...options.context,
    courseCode:
      options.courseCode ||
      options.context?.courseCode ||
      getAiPageContext()?.courseCode ||
      activeCourseCode.value ||
      DEFAULT_AI_COURSE,
  });
  if (options.pushUser !== false) {
    pushUserMessage(question);
  }
  lastQuestion.value = question;
  lastQuestionContext.value = options.context || {};
  loading.value = true;
  errorMsg.value = "";
  retryable.value = false;
  try {
    let current = await ensureAiSession({
      ...options,
      courseCode: context.courseCode,
    });
    if (!current) {
      throw new Error("无法建立 AI 课程会话");
    }
    const requestTurn = async (target: AiSessionVO) =>
      createAiTurn(target.sessionId, {
        clientRequestId: newClientRequestId(),
        message: question,
        context,
      });
    let response = await requestTurn(current);
    // 会话被关闭/删除时重建一次并重发，避免用户手动刷新
    if (response.data.code === 40400 || response.data.code === 40300) {
      removePointer(context.courseCode || current.courseCode);
      session.value = null;
      current = await ensureAiSession({
        ...options,
        courseCode: context.courseCode,
        newSession: true,
      });
      if (current) response = await requestTurn(current);
    }
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "AI 导师请求失败");
    }
    if (!stillSameUser(userId)) return null;
    const turn = await pollTurn(
      response.data.data.turnId,
      response.data.data,
      userId
    );
    if (!turn || !stillSameUser(userId)) return null;
    if (turn.status === "SUCCEEDED") {
      pushAssistantTurn(turn);
      return turn;
    }
    markTurnFailure(turn, "AI 导师未返回有效回答，请重试。");
    return turn;
  } catch (e) {
    if (stillSameUser(userId)) {
      errorMsg.value = describeError(e);
      retryable.value = true;
    }
    return null;
  } finally {
    if (stillSameUser(userId)) loading.value = false;
  }
}

/** 失败重试：复用最后问题，不重复入列用户消息；使用新的 clientRequestId。 */
export async function retryAi(): Promise<AiTurnVO | null> {
  if (!lastQuestion.value || loading.value) return null;
  return askAi(lastQuestion.value, {
    context: lastQuestionContext.value,
    pushUser: false,
  });
}

/** 清空当前课程会话：后端关闭（尽力而为），本地换新会话。 */
export async function clearAiConversation(): Promise<void> {
  const current = session.value;
  const course = current?.courseCode || activeCourseCode.value;
  if (current) {
    try {
      await updateAiSession(current.sessionId, {
        status: "CLOSED",
        rowVersion: current.rowVersion,
      });
    } catch {
      // 关闭失败不阻塞本地清空；下次发送会新建会话
    }
  }
  if (course) removePointer(course);
  session.value = null;
  messages.value = [];
  nextActions.value = [];
  errorMsg.value = "";
  lastQuestion.value = "";
  lastQuestionContext.value = {};
  retryable.value = false;
  sessionRestored.value = false;
  pollingTurnId.value = "";
}

/** 读取当前课程下一步计划；首页、图谱、节点工作台共用同一份服务端计划。 */
export async function refreshAiNextActions(
  options: { courseCode?: string; limit?: number } = {}
): Promise<AiNextAction[]> {
  const course =
    options.courseCode ||
    getAiPageContext()?.courseCode ||
    activeCourseCode.value ||
    DEFAULT_AI_COURSE;
  if (course !== activeCourseCode.value) {
    await syncAiCourse(course);
  }
  nextActionsLoading.value = true;
  nextActionsError.value = "";
  try {
    const response = await listAiNextActions({
      courseCode: course,
      limit: options.limit || 5,
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "读取下一步失败");
    }
    const data = response.data.data;
    nextActions.value = data.actions || [];
    dueReviewCount.value = data.dueReviewCount || 0;
    planGeneratedAt.value = data.generatedAt || "";
    planFocus.value = data.focus || "";
    planTimeBudgetMinutes.value = data.timeBudgetMinutes ?? null;
    // GET next-actions 可能自动创建会话：同步 session 与指针，保证后续轮次同会话
    if (
      data.sessionId &&
      (!session.value || session.value.sessionId !== data.sessionId)
    ) {
      try {
        const restored = await getAiSession(data.sessionId);
        if (restored.data.code === 0 && restored.data.data) {
          session.value = restored.data.data;
          writePointer(course, data.sessionId);
        }
      } catch {
        // 会话读取失败不影响本次计划展示
      }
    }
    return nextActions.value;
  } catch (e) {
    nextActionsError.value = describeError(e);
    return [];
  } finally {
    nextActionsLoading.value = false;
  }
}

/** 切换学习/复习目标与时间预算（首页“按可用时间复习”）。 */
export async function setAiSessionGoal(options: {
  courseCode?: string;
  focus: AiSessionFocus;
  goal?: string;
  timeBudgetMinutes?: number;
}): Promise<AiSessionVO | null> {
  const current = await ensureAiSession({
    courseCode: options.courseCode,
    focus: options.focus,
    goal: options.goal,
  });
  if (!current) return null;
  try {
    const response = await updateAiSession(current.sessionId, {
      focus: options.focus,
      goal: options.goal,
      timeBudgetMinutes: options.timeBudgetMinutes,
      rowVersion: current.rowVersion,
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "更新会话目标失败");
    }
    session.value = response.data.data;
    writePointer(response.data.data.courseCode, response.data.data.sessionId);
    return response.data.data;
  } catch (e) {
    errorMsg.value = describeError(e);
    return null;
  }
}

/** 将后续推理切到已测试通过的模型；课程、资料、任务与证据保持不变。 */
export async function setAiModelProfile(
  profileId: string | null
): Promise<boolean> {
  const current = await ensureAiSession();
  if (!current) return false;
  try {
    const response = await updateAiSession(current.sessionId, {
      modelProfileId: profileId || undefined,
      rowVersion: current.rowVersion,
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "切换模型失败");
    }
    session.value = response.data.data;
    return true;
  } catch (e) {
    errorMsg.value = describeError(e);
    return false;
  }
}

const canRetry = computed(
  () => Boolean(errorMsg.value) && Boolean(lastQuestion.value) && !loading.value
);
const hasSession = computed(
  () => Boolean(session.value) && session.value?.status === "ACTIVE"
);

export function useAiConversation() {
  return {
    session,
    messages,
    loading,
    errorMsg,
    canRetry,
    retryable,
    lastQuestion,
    activeCourseCode,
    nextActions,
    nextActionsLoading,
    nextActionsError,
    dueReviewCount,
    planGeneratedAt,
    planFocus,
    planTimeBudgetMinutes,
    pollingTurnId,
    sessionRestored,
    hasSession,
    ask: askAi,
    retry: retryAi,
    clear: clearAiConversation,
    ensureSession: ensureAiSession,
    syncCourse: syncAiCourse,
    refreshNextActions: refreshAiNextActions,
    setGoal: setAiSessionGoal,
    setModelProfile: setAiModelProfile,
  };
}
