/**
 * 统一 AI 导师会话核心（A2/A3）。
 *
 * 设计约束（01 契约）：
 * - 会话/轮次以服务端为准；浏览器只保存 userId+courseCode 的 sessionId 指针与短草稿。
 * - 状态按 `userId + courseCode` 隔离；异步响应先校验账号/课程/会话身份，旧课程迟到响应
 *   写回自己的课程状态，绝不追加到当前面板。
 * - 发送前冻结上下文与 clientRequestId；未定状态用同一 id 恢复，明确失败后的重试是新尝试。
 * - 同一会话有进行中请求时不重复发送；不显示后端不具备的取消/假流式能力。
 *
 * 兼容：原 `useAiConversation.ts` 保留为再导出，旧页面 import 路径不变。
 */
import { computed, reactive, ref, watch } from "vue";
import store from "@/store";
import {
  createAiSession,
  createAiTurn,
  getAiSession,
  getAiTurn,
  listAiNextActions,
  listAiSessions,
  listAiTurns,
  updateAiSession,
  type AiCourseContext,
  type AiDiagnosis,
  type AiNextAction,
  type AiSessionFocus,
  type AiSessionSummary,
  type AiSessionVO,
  type AiSourceRef,
  type AiToolTraceEntry,
  type AiTurnHistoryItem,
  type AiTurnMode,
  type AiTurnStatus,
  type AiTurnVO,
  type AiUsage,
} from "@/api/ai";
import { getAiPageContext } from "@/composables/useAiPageContext";
import { currentCourseCode } from "@/composables/useCurrentCourse";
import type {
  TutorIntent,
  TutorLaunchRequest,
  TutorScene,
} from "@/types/tutor";
import { validateTutorAttachments } from "@/types/tutor";

export interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
  time: number;
  clientRequestId?: string;
  turnId?: string;
  status?: AiTurnStatus;
  mode?: AiTurnMode;
  sources?: AiSourceRef[];
  toolTrace?: AiToolTraceEntry[];
  diagnosis?: AiDiagnosis | null;
  nextActions?: AiNextAction[];
  usage?: AiUsage | null;
  error?: string;
  pending?: boolean;
  contextSummary?: string;
  scene?: TutorScene;
  intent?: TutorIntent;
  /** 第 N 次尝试（明确失败后的重试不是同一次请求）。 */
  attempt?: number;
}

export interface AskAiOptions {
  /** 覆盖页面注册的上下文（如题卡提交后附带 resourceRef/eventIds） */
  context?: Partial<AiCourseContext>;
  courseCode?: string;
  /** 失败重试时不重复入列用户消息 */
  pushUser?: boolean;
  /** 显式创建一个新会话（如“按可用时间复习”切换目标） */
  newSession?: boolean;
  goal?: string;
  focus?: AiSessionFocus;
  timeBudgetMinutes?: number;
  /** 新建会话时写入的单元定位（页面上下文里未注册时补充） */
  unitCode?: string;
}

export interface TutorSendInput {
  message: string;
  launch?: TutorLaunchRequest;
  context?: Partial<AiCourseContext>;
  pushUser?: boolean;
  newSession?: boolean;
  /** 未定状态恢复时复用原 clientRequestId。 */
  clientRequestId?: string;
  attempt?: number;
  goal?: string;
  focus?: AiSessionFocus;
  timeBudgetMinutes?: number;
}

type PendingPhase = "SENDING" | "UNCERTAIN" | "RUNNING";

interface PendingTurn {
  clientRequestId: string;
  turnId?: string;
  phase: PendingPhase;
  message: string;
  sessionId: string;
  launch?: TutorLaunchRequest;
  context: AiCourseContext;
  attempt: number;
  startedAt: number;
}

interface CourseState {
  session: AiSessionVO | null;
  messages: AiChatMessage[];
  pending: PendingTurn | null;
  readOnly: boolean;
  loaded: boolean;
  restored: boolean;
  error: string;
  historyError: string;
  retryable: boolean;
  lastQuestion: string;
  lastLaunch: TutorLaunchRequest | null;
  lastContext: AiCourseContext | null;
  retryAttempt: number;
  historyLoading: boolean;
  historyHasMore: boolean;
  historyCursor: string;
  sessions: AiSessionSummary[];
  sessionsCursor: string;
  sessionsLoading: boolean;
  sessionsError: string;
  sessionsLoaded: boolean;
}

export const DEFAULT_AI_COURSE = "course.operating-systems";
/** 轮询间隔必须 ≥1.5s（任务书 §3），这里取 1.6s 留出网络抖动余量 */
export const AI_POLL_INTERVAL_MS = 1600;
/** 单轮最长等待；覆盖后端两次模型调用（各 30s）与工具执行 */
export const AI_POLL_TIMEOUT_MS = 120000;
const STORAGE_PREFIX = "oslab-ai-session-pointer-";
const DRAFT_PREFIX = "oslab-tutor-draft-";
const DRAFT_MAX_CHARS = 4000;
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000;
const DEFAULT_GOAL = "继续当前任务";

type SessionPointers = Record<string, string>;

const states = reactive<Record<string, CourseState>>({});
const activeCourseCode = ref("");
const pollingTurnId = ref("");
const nextActions = ref<AiNextAction[]>([]);
const nextActionsLoading = ref(false);
const nextActionsError = ref("");
const dueReviewCount = ref(0);
const planGeneratedAt = ref("");
const planFocus = ref<AiSessionFocus | "">("");
const planTimeBudgetMinutes = ref<number | null>(null);
let planRequestSequence = 0;
watch(
  currentCourseCode,
  () => {
    planRequestSequence += 1;
    nextActions.value = [];
    nextActionsLoading.value = false;
    nextActionsError.value = "";
    dueReviewCount.value = 0;
    planGeneratedAt.value = "";
    planFocus.value = "";
    planTimeBudgetMinutes.value = null;
  },
  { flush: "sync" }
);

let lastUserId: string | number | null = readUserId();
let userEpoch = 0;
const courseRestores = new Map<string, Promise<void>>();
const ensureInFlight = new Map<string, Promise<AiSessionVO | null>>();

function readUserId(): string | number | null {
  const id = store.state.user?.loginUser?.id;
  return typeof id === "number" || typeof id === "string" ? id : null;
}

function stateOf(courseCode: string): CourseState {
  if (!states[courseCode]) {
    states[courseCode] = {
      session: null,
      messages: [],
      pending: null,
      readOnly: false,
      loaded: false,
      restored: false,
      error: "",
      historyError: "",
      retryable: false,
      lastQuestion: "",
      lastLaunch: null,
      lastContext: null,
      retryAttempt: 0,
      historyLoading: false,
      historyHasMore: false,
      historyCursor: "",
      sessions: [],
      sessionsCursor: "",
      sessionsLoading: false,
      sessionsError: "",
      sessionsLoaded: false,
    };
  }
  return states[courseCode];
}

const currentState = computed(() => stateOf(currentCourseCode.value || ""));
const session = computed(() => currentState.value.session);
const messages = computed(() => currentState.value.messages);
const loading = computed(() => Boolean(currentState.value.pending));
const errorMsg = computed(() => currentState.value.error);
const retryable = computed(() => currentState.value.retryable);
const sessionRestored = computed(() => currentState.value.restored);
const historyLoading = computed(() => currentState.value.historyLoading);
const historyHasMore = computed(() => currentState.value.historyHasMore);
const historyError = computed(() => currentState.value.historyError);
const sessionHistory = computed(() => currentState.value.sessions);
const sessionHistoryLoading = computed(
  () => currentState.value.sessionsLoading
);
const sessionHistoryError = computed(() => currentState.value.sessionsError);
const currentSessionsCursor = computed(() => currentState.value.sessionsCursor);
const isReadOnlySession = computed(() => currentState.value.readOnly);
const pendingTurn = computed(() => currentState.value.pending);
const canRetry = computed(
  () =>
    Boolean(currentState.value.error) &&
    Boolean(currentState.value.lastQuestion) &&
    !currentState.value.pending
);
const hasSession = computed(
  () =>
    Boolean(currentState.value.session) &&
    currentState.value.session?.status === "ACTIVE"
);
const lastQuestion = computed(() => currentState.value.lastQuestion);

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
  Object.keys(states).forEach((key) => delete states[key]);
  activeCourseCode.value = "";
  pollingTurnId.value = "";
  nextActions.value = [];
  nextActionsLoading.value = false;
  nextActionsError.value = "";
  dueReviewCount.value = 0;
  planGeneratedAt.value = "";
  planFocus.value = "";
  planTimeBudgetMinutes.value = null;
  courseRestores.clear();
  ensureInFlight.clear();
}

store.watch(
  () => readUserId(),
  (id) => {
    if (id === lastUserId) return;
    const previous = lastUserId;
    lastUserId = id;
    userEpoch += 1;
    // 首次从“未加载”水合为登录用户时保留本标签页草稿；真实退出/换号只清理旧用户
    if (previous !== null) clearDraftsFor(previous);
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
    return "请求超时。";
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

function cleanIds(values?: string[]): string[] {
  if (!Array.isArray(values)) return [];
  return Array.from(
    new Set(values.filter((item) => typeof item === "string" && item.trim()))
  ).map((item) => item.trim());
}

/** 发送前冻结上下文：页面 provider 只影响下一轮。 */
function freezeContext(
  courseCode: string,
  launch: TutorLaunchRequest | undefined,
  overrides: Partial<AiCourseContext> | undefined
): AiCourseContext {
  const usePage = !launch || (launch.scope || "PAGE") === "PAGE";
  const page = usePage ? getAiPageContext() : null;
  return {
    courseCode,
    unitCode:
      overrides?.unitCode || launch?.unitCode || page?.unitCode || undefined,
    nodeCodes: cleanIds([
      ...(overrides?.nodeCodes || []),
      ...(launch?.nodeCodes || []),
      ...(page?.nodeCodes || []),
      ...(page?.nodeCode ? [page.nodeCode] : []),
    ]),
    resourceRef:
      overrides?.resourceRef ||
      launch?.resourceRef ||
      page?.resourceRef ||
      undefined,
    eventIds: cleanIds([
      ...(overrides?.eventIds || []),
      ...(launch?.eventIds || []),
      ...(page?.eventIds || []),
    ]),
    selectedSourceIds: cleanIds([
      ...(overrides?.selectedSourceIds || []),
      ...(launch?.selectedSourceIds || []),
      ...(page?.selectedSourceIds || []),
    ]),
    attemptContextId:
      overrides?.attemptContextId || launch?.attemptContextId || undefined,
    assessmentRunId:
      overrides?.assessmentRunId || launch?.assessmentRunId || undefined,
  };
}

function toTutorDirective(launch?: TutorLaunchRequest) {
  if (!launch) return undefined;
  return {
    scene: launch.scene,
    intent: launch.intent,
    scope: launch.scope || ("PAGE" as const),
    attachments: launch.attachments?.length ? launch.attachments : undefined,
  };
}

// region 草稿

function draftKey(courseCode: string): string {
  return lastUserId !== null
    ? `${DRAFT_PREFIX}${lastUserId}:${courseCode}`
    : "";
}

export function readTutorDraft(courseCode?: string): string {
  const code = courseCode || currentCourseCode.value;
  const key = draftKey(code);
  if (!key) return "";
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return "";
    const parsed = JSON.parse(raw) as { text?: string; at?: number };
    if (!parsed || typeof parsed.text !== "string") return "";
    if (!parsed.at || Date.now() - parsed.at > DRAFT_TTL_MS) {
      sessionStorage.removeItem(key);
      return "";
    }
    return parsed.text.slice(0, DRAFT_MAX_CHARS);
  } catch {
    return "";
  }
}

export function writeTutorDraft(text: string, courseCode?: string): void {
  const code = courseCode || currentCourseCode.value;
  const key = draftKey(code);
  if (!key) return;
  try {
    if (!text) {
      sessionStorage.removeItem(key);
      return;
    }
    sessionStorage.setItem(
      key,
      JSON.stringify({ text: text.slice(0, DRAFT_MAX_CHARS), at: Date.now() })
    );
  } catch {
    // 草稿只是尽力保存，存储不可用不阻塞对话
  }
}

export function clearTutorDraft(courseCode?: string): void {
  const code = courseCode || currentCourseCode.value;
  const key = draftKey(code);
  if (!key) return;
  try {
    sessionStorage.removeItem(key);
  } catch {
    // ignore
  }
}

function clearDraftsFor(userId: string | number | null) {
  try {
    const prefix = userId !== null ? `${DRAFT_PREFIX}${userId}:` : DRAFT_PREFIX;
    const keys: string[] = [];
    for (let i = 0; i < sessionStorage.length; i += 1) {
      const key = sessionStorage.key(i);
      if (key && key.startsWith(prefix)) keys.push(key);
    }
    keys.forEach((key) => sessionStorage.removeItem(key));
  } catch {
    // ignore
  }
}

// endregion

// region 会话

async function restoreFromPointer(courseCode: string): Promise<void> {
  const state = stateOf(courseCode);
  const sessionId = readPointers()[courseCode];
  if (!sessionId) return;
  try {
    const response = await getAiSession(sessionId);
    const data = response.data.code === 0 ? response.data.data : null;
    if (data && data.courseCode === courseCode) {
      state.session = data;
      if (data.status === "ACTIVE") {
        state.restored = true;
        await loadInitialTurns(courseCode, data.sessionId);
      } else {
        removePointer(courseCode);
      }
    } else {
      removePointer(courseCode);
    }
  } catch {
    removePointer(courseCode);
  }
}

/** 切换当前课程作用域；各课程保留自己的会话、消息与进行中状态。 */
export async function syncAiCourse(courseCode: string): Promise<void> {
  const course = courseCode.trim();
  if (!course) return;
  if (course !== activeCourseCode.value) {
    activeCourseCode.value = course;
  }
  const state = stateOf(course);
  if (!state.loaded) {
    let restore = courseRestores.get(course);
    if (!restore) {
      restore = (async () => {
        try {
          await restoreFromPointer(course);
        } finally {
          state.loaded = true;
          courseRestores.delete(course);
        }
      })();
      courseRestores.set(course, restore);
    }
    await restore;
  }
}

async function createSession(
  courseCode: string,
  options: {
    goal?: string;
    focus?: AiSessionFocus;
    timeBudgetMinutes?: number;
    unitCode?: string;
  } = {}
): Promise<AiSessionVO> {
  const page = getAiPageContext();
  const response = await createAiSession({
    courseCode,
    goal: options.goal || DEFAULT_GOAL,
    focus: options.focus || "LEARN",
    ...(options.timeBudgetMinutes
      ? { timeBudgetMinutes: options.timeBudgetMinutes }
      : {}),
    ...(options.unitCode || page?.unitCode
      ? { unitCode: options.unitCode || page?.unitCode }
      : {}),
  });
  if (response.data.code !== 0 || !response.data.data) {
    throw new Error(response.data.message || "创建 AI 课程会话失败");
  }
  const state = stateOf(courseCode);
  state.session = response.data.data;
  state.readOnly = false;
  state.restored = false;
  writePointer(courseCode, response.data.data.sessionId);
  return response.data.data;
}

export async function ensureAiSession(
  options: AskAiOptions = {}
): Promise<AiSessionVO | null> {
  const course =
    options.courseCode ||
    getAiPageContext()?.courseCode ||
    currentCourseCode.value;
  if (!course || course !== currentCourseCode.value) return null;
  if (course !== activeCourseCode.value) {
    await syncAiCourse(course);
  }
  const state = stateOf(course);
  if (options.newSession) {
    state.session = null;
    removePointer(course);
  }
  if (
    state.session &&
    state.session.status === "ACTIVE" &&
    state.session.courseCode === course
  ) {
    return state.session;
  }
  const inFlight = ensureInFlight.get(course);
  if (inFlight) return inFlight;
  const promise = (async () => {
    try {
      const pointer = readPointers()[course];
      if (pointer) {
        try {
          const response = await getAiSession(pointer);
          const data = response.data.code === 0 ? response.data.data : null;
          if (data && data.status === "ACTIVE" && data.courseCode === course) {
            state.session = data;
            state.restored = true;
            return data;
          }
        } catch {
          removePointer(course);
        }
      }
      return await createSession(course, options);
    } finally {
      ensureInFlight.delete(course);
    }
  })();
  ensureInFlight.set(course, promise);
  return promise;
}

/** 新话题：关闭旧会话（尽力而为），清空当前课程对话，下一次发送创建新会话。 */
export async function startNewTopic(courseCode?: string): Promise<boolean> {
  const course =
    courseCode || activeCourseCode.value || getAiPageContext()?.courseCode;
  if (!course) return false;
  if (course !== activeCourseCode.value) await syncAiCourse(course);
  const state = stateOf(course);
  if (state.pending) return false;
  const current = state.session;
  if (current && current.status === "ACTIVE") {
    try {
      await updateAiSession(current.sessionId, {
        status: "CLOSED",
        rowVersion: current.rowVersion,
      });
    } catch {
      // 关闭失败不阻塞本地新话题；旧会话仍可从最近对话读取
    }
  }
  removePointer(course);
  state.session = null;
  state.messages = [];
  state.readOnly = false;
  state.restored = false;
  state.error = "";
  state.historyError = "";
  state.retryable = false;
  state.lastQuestion = "";
  state.lastLaunch = null;
  state.lastContext = null;
  state.historyCursor = "";
  state.historyHasMore = false;
  state.loaded = true;
  return true;
}

/** 兼容旧入口：清空当前课程对话。 */
export async function clearAiConversation(): Promise<void> {
  await startNewTopic();
}

/** 读取最近对话列表（分页；只含当前用户与指定课程）。 */
export async function loadSessionHistory(
  options: { courseCode?: string; reset?: boolean } = {}
): Promise<void> {
  const course =
    options.courseCode ||
    getAiPageContext()?.courseCode ||
    currentCourseCode.value;
  if (!course || course !== currentCourseCode.value) return;
  if (course !== activeCourseCode.value) await syncAiCourse(course);
  const state = stateOf(course);
  if (state.sessionsLoading) return;
  if (options.reset) {
    state.sessions = [];
    state.sessionsCursor = "";
    state.sessionsLoaded = false;
  }
  state.sessionsLoading = true;
  state.sessionsError = "";
  try {
    const response = await listAiSessions({
      courseCode: course,
      limit: 20,
      ...(state.sessionsCursor ? { cursor: state.sessionsCursor } : {}),
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "读取最近对话失败");
    }
    const page = response.data.data;
    const known = new Set(state.sessions.map((item) => item.sessionId));
    page.items.forEach((item) => {
      if (!known.has(item.sessionId)) state.sessions.push(item);
    });
    state.sessionsCursor = page.nextCursor || "";
    state.sessionsLoaded = true;
  } catch (e) {
    state.sessionsError = describeError(e);
  } finally {
    state.sessionsLoading = false;
  }
}

/** 打开一个历史会话：ACTIVE 可继续，CLOSED 只读浏览。 */
export async function openHistorySession(sessionId: string): Promise<boolean> {
  const course = activeCourseCode.value;
  if (!course) return false;
  const state = stateOf(course);
  if (state.pending) return false;
  try {
    const response = await getAiSession(sessionId);
    const data = response.data.code === 0 ? response.data.data : null;
    if (!data || data.courseCode !== course) {
      state.error = "该会话不属于当前课程，无法打开。";
      return false;
    }
    state.session = data;
    state.readOnly = data.status !== "ACTIVE";
    state.messages = [];
    state.historyCursor = "";
    state.historyHasMore = false;
    state.error = "";
    state.historyError = "";
    state.restored = true;
    if (state.readOnly) {
      removePointer(course);
    } else {
      writePointer(course, data.sessionId);
    }
    await loadInitialTurns(course, data.sessionId);
    return true;
  } catch (e) {
    state.error = describeError(e);
    return false;
  }
}

// endregion

// region 历史映射

function messageFromHistory(item: AiTurnHistoryItem): AiChatMessage[] {
  const time = item.createdAt ? Date.parse(item.createdAt) : Date.now();
  const result: AiChatMessage[] = [
    {
      role: "user",
      content: item.userMessage || "",
      time: Number.isFinite(time) ? time : Date.now(),
      clientRequestId: item.clientRequestId,
      contextSummary: item.contextSummary || "",
    },
  ];
  const turn = item.turn;
  if (!turn) return result;
  if (turn.status === "RUNNING") {
    result.push({
      role: "assistant",
      content: "",
      time: Date.now(),
      turnId: turn.turnId,
      status: turn.status,
      pending: true,
      mode: turn.mode,
    });
    return result;
  }
  if (turn.status === "SUCCEEDED") {
    result.push({
      role: "assistant",
      content: turn.answer || "",
      time: Number.isFinite(time) ? time : Date.now(),
      turnId: turn.turnId,
      status: turn.status,
      mode: turn.mode,
      sources: turn.sources || [],
      toolTrace: turn.toolTrace || [],
      diagnosis: turn.diagnosis || null,
      nextActions: turn.nextActions || [],
      usage: turn.usage || null,
    });
    return result;
  }
  const reason =
    turn.errorMessage ||
    (turn.status === "FAILED_RETRYABLE"
      ? "导师本轮暂时未能完成，可重试。"
      : "AI 导师未返回有效回答，请重试。");
  result.push({
    role: "assistant",
    content: reason,
    time: Number.isFinite(time) ? time : Date.now(),
    turnId: turn.turnId,
    status: turn.status,
    mode: turn.mode,
    error: reason,
  });
  return result;
}

function mergeHistory(
  state: CourseState,
  items: AiTurnHistoryItem[],
  mode: "append" | "prepend"
) {
  const list: AiChatMessage[] = [];
  items.forEach((item) => list.push(...messageFromHistory(item)));
  const existingTurns = new Set(
    state.messages.filter((m) => m.turnId).map((m) => m.turnId)
  );
  const existingRequests = new Set(
    state.messages
      .filter((m) => m.role === "user" && m.clientRequestId)
      .map((m) => m.clientRequestId)
  );
  const filtered = list.filter((message) => {
    if (message.role === "assistant" && message.turnId) {
      return !existingTurns.has(message.turnId);
    }
    if (message.role === "user" && message.clientRequestId) {
      return !existingRequests.has(message.clientRequestId);
    }
    return true;
  });
  if (mode === "prepend") {
    state.messages = [...filtered, ...state.messages];
  } else {
    filtered.forEach((message) => state.messages.push(message));
  }
}

async function loadInitialTurns(courseCode: string, sessionId: string) {
  const state = stateOf(courseCode);
  state.historyLoading = true;
  state.historyError = "";
  try {
    const response = await listAiTurns(sessionId, { limit: 20 });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "读取会话历史失败");
    }
    const page = response.data.data;
    mergeHistory(state, page.items, "append");
    state.historyCursor = page.nextCursor || "";
    state.historyHasMore = Boolean(page.nextCursor);
    // 刷新恢复：最后一轮仍在运行 → 继续查询真实状态，不伪造“已完成”
    const running = [...page.items]
      .reverse()
      .find((item) => item.turn?.status === "RUNNING");
    if (running && !state.pending) {
      const userMessage =
        page.items.find((item) => item.turnId === running.turnId)
          ?.userMessage || "";
      state.pending = {
        clientRequestId: running.clientRequestId,
        turnId: running.turnId,
        phase: "RUNNING",
        message: userMessage,
        sessionId,
        context: { courseCode },
        attempt: 1,
        startedAt: Date.now(),
      };
      void continuePolling(
        state,
        lastUserId,
        running.turnId,
        running.turn,
        running.clientRequestId,
        1
      );
    }
  } catch (e) {
    state.historyError = describeError(e);
  } finally {
    state.historyLoading = false;
  }
}

/** 加载更早一页；保持当前阅读位置（滚动由面板负责）。 */
export async function loadOlderTurns(): Promise<void> {
  const course = activeCourseCode.value;
  if (!course) return;
  const state = stateOf(course);
  if (!state.session || !state.historyHasMore || state.historyLoading) return;
  state.historyLoading = true;
  state.historyError = "";
  try {
    const response = await listAiTurns(state.session.sessionId, {
      before: state.historyCursor,
      limit: 20,
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "读取更早记录失败");
    }
    const page = response.data.data;
    mergeHistory(state, page.items, "prepend");
    state.historyCursor = page.nextCursor || "";
    state.historyHasMore = Boolean(page.nextCursor);
  } catch (e) {
    state.historyError = describeError(e);
  } finally {
    state.historyLoading = false;
  }
}

export async function retryHistory(): Promise<void> {
  const state = currentState.value;
  state.historyError = "";
  if (!state.session) return;
  state.messages = [];
  state.historyCursor = "";
  state.historyHasMore = false;
  await loadInitialTurns(activeCourseCode.value, state.session.sessionId);
}

// endregion

// region 发送与轮询

function pushUserMessage(
  state: CourseState,
  text: string,
  requestId: string,
  launch: TutorLaunchRequest | undefined,
  attempt: number
) {
  state.messages.push({
    role: "user",
    content: text,
    time: Date.now(),
    clientRequestId: requestId,
    scene: launch?.scene,
    intent: launch?.intent,
    attempt: attempt > 1 ? attempt : undefined,
  });
}

function applyTurnResult(
  state: CourseState,
  turn: AiTurnVO,
  clientRequestId: string,
  attempt: number
) {
  const existing = state.messages.find(
    (message) => message.role === "assistant" && message.turnId === turn.turnId
  );
  if (turn.status === "SUCCEEDED") {
    const message: AiChatMessage = {
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
      clientRequestId,
      attempt: attempt > 1 ? attempt : undefined,
    };
    if (existing) {
      Object.assign(existing, message, { pending: false });
    } else {
      state.messages.push(message);
    }
    if (currentCourseCode.value && state === stateOf(currentCourseCode.value))
      mergeNextActions(turn.nextActions);
    state.error = "";
    state.retryable = false;
    if (typeof window !== "undefined") {
      window.dispatchEvent(
        new CustomEvent("oslab:ai-turn-completed", { detail: { turn } })
      );
    }
    return;
  }
  const reason =
    turn.errorMessage ||
    (turn.status === "FAILED_RETRYABLE"
      ? "导师本轮暂时未能完成，可重试。"
      : "AI 导师未返回有效回答，请重试。");
  const message: AiChatMessage = {
    role: "assistant",
    content: reason,
    time: Date.now(),
    turnId: turn.turnId,
    status: turn.status,
    mode: turn.mode,
    error: reason,
    clientRequestId,
    attempt: attempt > 1 ? attempt : undefined,
  };
  if (existing) {
    Object.assign(existing, message, { pending: false });
  } else {
    state.messages.push(message);
  }
  state.error = reason;
  state.retryable = turn.status === "FAILED_RETRYABLE";
}

async function continuePolling(
  state: CourseState,
  userId: string | number | null,
  turnId: string,
  initial: AiTurnVO,
  requestId?: string,
  attempt = 1
): Promise<AiTurnVO | null> {
  let turn = initial;
  const startedAt = Date.now();
  try {
    while (turn.status === "RUNNING") {
      if (lastUserId !== userId) return null;
      if (Date.now() - startedAt > AI_POLL_TIMEOUT_MS) {
        state.error =
          "本轮仍在后台处理。可以继续等待，或稍后从最近对话恢复，不会重复提问。";
        return null;
      }
      await delay(AI_POLL_INTERVAL_MS);
      if (lastUserId !== userId) return null;
      pollingTurnId.value = turnId;
      const response = await getAiTurn(turnId);
      if (response.data.code !== 0 || !response.data.data) {
        state.error = "读取本轮状态失败，可点击恢复继续查询。";
        return null;
      }
      turn = response.data.data;
      if (state.pending) state.pending.turnId = turnId;
    }
    pollingTurnId.value = "";
    if (lastUserId !== userId) return null;
    applyTurnResult(
      state,
      turn,
      requestId || state.pending?.clientRequestId || "",
      attempt
    );
    return turn;
  } catch (e) {
    if (lastUserId === userId) {
      state.error = describeError(e);
      state.retryable = true;
    }
    return null;
  } finally {
    if (
      state.pending &&
      (!state.pending.turnId || state.pending.turnId === turnId) &&
      state.pending.phase !== "UNCERTAIN" &&
      turn.status !== "RUNNING"
    ) {
      state.pending = null;
    }
    if (pollingTurnId.value === turnId) pollingTurnId.value = "";
  }
}

/**
 * 发送一轮导师提问。
 *
 * @returns 终态轮次；网络未定、超时或失败时返回 null，并保留可恢复状态。
 */
export async function sendTutor(
  input: TutorSendInput
): Promise<AiTurnVO | null> {
  const text = input.message.trim();
  if (!text) return null;
  if (text.length > 4000) {
    const course =
      input.launch?.courseCode ||
      input.context?.courseCode ||
      activeCourseCode.value;
    if (course) {
      stateOf(course).error = "问题过长（最多 4000 字），请精简后发送。";
    }
    return null;
  }
  const course =
    input.launch?.courseCode ||
    input.context?.courseCode ||
    getAiPageContext()?.courseCode ||
    currentCourseCode.value;
  if (!course || course !== currentCourseCode.value) return null;
  if (course !== activeCourseCode.value) await syncAiCourse(course);
  const state = stateOf(course);
  if (state.pending) {
    state.error =
      state.pending.phase === "UNCERTAIN"
        ? "上一轮网络状态未确定，请先点击恢复核对。"
        : "导师正在回复当前会话，请等待完成后再发送。";
    return null;
  }
  if (state.readOnly) {
    state.error = "当前查看的是已关闭会话。请先开始新话题再提问。";
    return null;
  }
  const attachmentCheck = validateTutorAttachments(input.launch?.attachments);
  if (!attachmentCheck.ok) {
    state.error = attachmentCheck.error || "附带材料超限。";
    return null;
  }
  if (
    input.launch?.intent === "HINT" &&
    !input.launch.attemptContextId &&
    !input.launch.assessmentRunId
  ) {
    state.error =
      "提示需要关联有效的题目尝试；请先在题目卡片中开始作答，再请求提示。";
    return null;
  }
  const userId = lastUserId;
  const epoch = userEpoch;
  const requestId = input.clientRequestId || newClientRequestId();
  const attempt = input.attempt || 1;
  const context = freezeContext(course, input.launch, input.context);
  const directive = toTutorDirective(input.launch);
  state.error = "";
  state.retryable = false;
  state.lastQuestion = text;
  state.lastLaunch = input.launch || null;
  state.lastContext = context;
  state.retryAttempt = attempt;
  if (input.pushUser !== false) {
    pushUserMessage(state, text, requestId, input.launch, attempt);
  }
  try {
    const current = await ensureAiSession({
      courseCode: course,
      newSession: input.newSession,
      goal: input.goal,
      focus: input.focus,
      timeBudgetMinutes: input.timeBudgetMinutes,
      unitCode: context.unitCode,
    });
    if (!current) throw new Error("无法建立 AI 课程会话");
    if (lastUserId !== userId || userEpoch !== epoch) return null;
    state.pending = {
      clientRequestId: requestId,
      phase: "SENDING",
      message: text,
      sessionId: current.sessionId,
      launch: input.launch,
      context,
      attempt,
      startedAt: Date.now(),
    };
    const response = await createAiTurn(current.sessionId, {
      clientRequestId: requestId,
      message: text,
      context,
      ...(directive ? { tutor: directive } : {}),
    });
    if (lastUserId !== userId || userEpoch !== epoch) return null;
    if (response.data.code !== 0 || !response.data.data) {
      state.pending = null;
      throw new Error(response.data.message || "AI 导师请求失败");
    }
    const turn = response.data.data;
    state.pending.phase = "RUNNING";
    state.pending.turnId = turn.turnId;
    if (turn.status === "RUNNING") {
      return await continuePolling(
        state,
        userId,
        turn.turnId,
        turn,
        requestId,
        attempt
      );
    }
    applyTurnResult(state, turn, requestId, attempt);
    state.pending = null;
    return turn;
  } catch (e) {
    if (lastUserId !== userId) return null;
    if (
      state.pending &&
      state.pending.phase === "SENDING" &&
      !state.pending.turnId
    ) {
      // 请求是否到达服务端未知：保留同一 clientRequestId，供恢复/幂等重发
      state.pending.phase = "UNCERTAIN";
      state.pending.startedAt = Date.now();
      state.error = `${describeError(
        e
      )} 网络状态未确定：可点击“恢复”核对，不会重复提问。`;
      state.retryable = false;
    } else {
      state.pending = null;
      state.error = describeError(e);
      state.retryable = true;
    }
    return null;
  }
}

/** 兼容旧入口：按页面上下文发送一轮提问。 */
export async function askAi(
  text: string,
  options: AskAiOptions = {}
): Promise<AiTurnVO | null> {
  const course =
    options.courseCode ||
    options.context?.courseCode ||
    getAiPageContext()?.courseCode ||
    currentCourseCode.value;
  if (!course || course !== currentCourseCode.value) return null;
  const launch: TutorLaunchRequest = {
    scene: "GLOBAL",
    intent: "ASK",
    courseCode: course,
    scope: "PAGE",
  };
  return sendTutor({
    message: text,
    launch,
    context: options.context,
    pushUser: options.pushUser,
    newSession: options.newSession,
    goal: options.goal,
    focus: options.focus,
    timeBudgetMinutes: options.timeBudgetMinutes,
  });
}

/** 未定/超时/读取失败后的恢复：同一 clientRequestId 幂等重发或继续查询原轮次。 */
export async function resumePendingTurn(): Promise<AiTurnVO | null> {
  const course = activeCourseCode.value;
  if (!course) return null;
  const state = stateOf(course);
  const pending = state.pending;
  if (!pending) {
    if (state.retryable) return retryAi();
    return null;
  }
  state.error = "";
  if (pending.turnId) {
    try {
      const response = await getAiTurn(pending.turnId);
      if (response.data.code !== 0 || !response.data.data) {
        state.error = "恢复失败：无法读取本轮状态，请稍后重试。";
        return null;
      }
      const turn = response.data.data;
      if (turn.status === "RUNNING") {
        return continuePolling(
          state,
          lastUserId,
          turn.turnId,
          turn,
          pending.clientRequestId,
          pending.attempt
        );
      }
      applyTurnResult(state, turn, pending.clientRequestId, pending.attempt);
      state.pending = null;
      return turn;
    } catch (e) {
      state.error = `恢复失败：${describeError(e)}`;
      return null;
    }
  }
  // 网络未定：用同一幂等键重发，服务端会返回已有轮次而不是新建
  const response = await createAiTurn(pending.sessionId, {
    clientRequestId: pending.clientRequestId,
    message: pending.message,
    context: pending.context,
    ...(toTutorDirective(pending.launch)
      ? { tutor: toTutorDirective(pending.launch) }
      : {}),
  });
  if (response.data.code !== 0 || !response.data.data) {
    state.error = response.data.message || "恢复失败，请稍后重试。";
    return null;
  }
  const turn = response.data.data;
  pending.turnId = turn.turnId;
  pending.phase = "RUNNING";
  if (turn.status === "RUNNING") {
    return continuePolling(
      state,
      lastUserId,
      turn.turnId,
      turn,
      pending.clientRequestId,
      pending.attempt
    );
  }
  applyTurnResult(state, turn, pending.clientRequestId, pending.attempt);
  state.pending = null;
  return turn;
}

/** 失败重试：复用最后问题与上下文，新 clientRequestId，不重复用户气泡。 */
export async function retryAi(): Promise<AiTurnVO | null> {
  const course = activeCourseCode.value;
  if (!course) return null;
  const state = stateOf(course);
  if (state.pending) return null;
  if (!state.lastQuestion) return null;
  // 移除上一条终态失败气泡，避免同一问题堆叠多条“失败”消息
  for (let i = state.messages.length - 1; i >= 0; i -= 1) {
    const message = state.messages[i];
    if (message.role === "assistant" && message.error) {
      state.messages.splice(i, 1);
      break;
    }
  }
  return sendTutor({
    message: state.lastQuestion,
    launch: state.lastLaunch || undefined,
    context: state.lastContext || undefined,
    pushUser: false,
    attempt: state.retryAttempt + 1,
  });
}

// endregion

// region 计划 / 目标 / 模型

export async function refreshAiNextActions(
  options: { courseCode?: string; limit?: number } = {}
): Promise<AiNextAction[]> {
  const course =
    options.courseCode ||
    getAiPageContext()?.courseCode ||
    currentCourseCode.value;
  if (!course || course !== currentCourseCode.value) return [];
  const sequence = ++planRequestSequence;
  if (course !== activeCourseCode.value) {
    await syncAiCourse(course);
  }
  if (sequence !== planRequestSequence || course !== currentCourseCode.value)
    return [];
  nextActionsLoading.value = true;
  nextActionsError.value = "";
  try {
    const response = await listAiNextActions({
      courseCode: course,
      limit: options.limit || 5,
    });
    if (sequence !== planRequestSequence || course !== currentCourseCode.value)
      return [];
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "读取下一步失败");
    }
    const data = response.data.data;
    nextActions.value = data.actions || [];
    dueReviewCount.value = data.dueReviewCount || 0;
    planGeneratedAt.value = data.generatedAt || "";
    planFocus.value = data.focus || "";
    planTimeBudgetMinutes.value = data.timeBudgetMinutes ?? null;
    if (
      data.sessionId &&
      (!stateOf(course).session ||
        stateOf(course).session?.sessionId !== data.sessionId)
    ) {
      try {
        const restored = await getAiSession(data.sessionId);
        if (restored.data.code === 0 && restored.data.data) {
          const state = stateOf(course);
          state.session = restored.data.data;
          writePointer(course, data.sessionId);
          // 首次打开就采用服务端会话时同步补载历史，避免只显示本轮消息
          if (restored.data.data.status === "ACTIVE") {
            state.restored = true;
            await loadInitialTurns(course, data.sessionId);
          }
        }
      } catch {
        // 会话读取失败不影响本次计划展示
      }
    }
    return nextActions.value;
  } catch (e) {
    if (sequence === planRequestSequence)
      nextActionsError.value = describeError(e);
    return [];
  } finally {
    if (sequence === planRequestSequence) nextActionsLoading.value = false;
  }
}

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
  const course = current.courseCode;
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
    stateOf(course).session = response.data.data;
    writePointer(response.data.data.courseCode, response.data.data.sessionId);
    return response.data.data;
  } catch (e) {
    stateOf(course).error = describeError(e);
    return null;
  }
}

/** 切换后续推理模型；只影响下一轮，当前轮展示真实 usage.model。 */
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
    stateOf(current.courseCode).session = response.data.data;
    return true;
  } catch (e) {
    stateOf(current.courseCode).error = describeError(e);
    return false;
  }
}

// endregion

export function getActiveTutorCourseCode(): string {
  return activeCourseCode.value;
}

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
    historyLoading,
    historyHasMore,
    historyError,
    sessionHistory,
    sessionHistoryLoading,
    sessionHistoryError,
    currentSessionsCursor,
    isReadOnlySession,
    pendingTurn,
    ask: askAi,
    send: sendTutor,
    retry: retryAi,
    resume: resumePendingTurn,
    clear: clearAiConversation,
    newTopic: startNewTopic,
    ensureSession: ensureAiSession,
    syncCourse: syncAiCourse,
    refreshNextActions: refreshAiNextActions,
    setGoal: setAiSessionGoal,
    setModelProfile: setAiModelProfile,
    loadSessionHistory,
    openHistorySession,
    loadOlderTurns,
    retryHistory,
    readDraft: readTutorDraft,
    writeDraft: writeTutorDraft,
    clearDraft: clearTutorDraft,
  };
}
