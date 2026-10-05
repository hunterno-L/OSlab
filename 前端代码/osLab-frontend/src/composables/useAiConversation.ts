/**
 * AI 助手会话状态与持久化（log3 阶段 4）：
 * - 全局单例消息流；localStorage 按用户 id 隔离存储，切换/退出登录自动切换
 *   会话，不把上一个账号（如管理员）的本地内容带给下一个账号；
 * - 请求复用 ChatControllerService.askQuestionUsingPost（/api/chat/ask，
 *   单字符串进出）：把页面上下文摘要 + 最近 8 条消息序列化成一条清晰文本；
 * - 失败保留最后提问以便重试；发送中禁止重复提交；带超时与可读错误提示；
 * - 不新增 provider/streaming/语音，前端不含任何 key；助手只回答问题，
 *   不执行任何有副作用的平台操作。
 */
import { computed, ref, watch } from "vue";
import { useStore } from "vuex";
import { ChatControllerService } from "../../generated/services/ChatControllerService";
import { buildPageContextSummary } from "./useAiPageContext";

export interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
  time: number;
}

/** 随请求携带的最近消息条数（§8.3-8：6-8 条） */
const HISTORY_LIMIT = 8;
/** 本地最多保留的消息条数，防止无限膨胀 */
const MAX_STORED = 50;
/** 单条消息进入提示词的最大长度 */
const MAX_PROMPT_CHARS = 2000;
const REQUEST_TIMEOUT_MS = 60000;
const STORAGE_PREFIX = "oslab-ai-conversation-";

// 模块级单例状态：助手在全站只挂载一份
const messages = ref<AiChatMessage[]>([]);
const loading = ref(false);
const errorMsg = ref("");
const lastQuestion = ref("");

function truncate(text: string, max = MAX_PROMPT_CHARS): string {
  return text.length > max ? `${text.slice(0, max)}…（已截断）` : text;
}

/** 把页面上下文摘要 + 最近消息序列化为一条清晰文本（单字符串进出） */
function buildPrompt(): string {
  const parts: string[] = [
    "你是 OSLab 在线实验平台的「AI 助手」。请用中文，依据【页面上下文】与【最近对话】简洁、准确地回答用户的最新提问；你只能回答问题，不能执行任何平台操作。",
  ];
  const contextSummary = buildPageContextSummary();
  if (contextSummary) {
    parts.push(`【页面上下文】${contextSummary}`);
  }
  const recent = messages.value.slice(-HISTORY_LIMIT).map((msg) => {
    const speaker = msg.role === "user" ? "用户" : "AI 助手";
    return `${speaker}：${truncate(msg.content)}`;
  });
  parts.push(`【最近对话】\n${recent.join("\n")}`);
  return parts.join("\n\n");
}

/** 给裸请求包一层超时，超时/服务不可用都有可读提示（§8.3-9） */
function withTimeout<T>(promise: Promise<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = window.setTimeout(
      () => reject(new Error("timeout")),
      REQUEST_TIMEOUT_MS
    );
    promise.then(
      (value) => {
        window.clearTimeout(timer);
        resolve(value);
      },
      (err) => {
        window.clearTimeout(timer);
        reject(err);
      }
    );
  });
}

function describeError(e: any): string {
  if (e && e.message === "timeout") {
    return "请求超时，请检查网络后重试。";
  }
  const status = e?.status ?? e?.response?.status;
  if (status === 401 || status === 403) {
    return "登录状态已失效或无权限，请重新登录后再试。";
  }
  if (status === 404) {
    return "AI 服务暂未开放，请稍后再试或联系管理员。";
  }
  return "AI 服务暂时不可用，请稍后重试。";
}

/**
 * 会话组合式函数：在 AiChatPanel（全站唯一实例）中调用一次。
 */
export function useAiConversation() {
  const store = useStore();
  const loginUser = computed<any>(() => store.state?.user?.loginUser ?? {});
  const userId = computed<string | number | null>(() => {
    const id = loginUser.value?.id;
    return typeof id === "number" || typeof id === "string" ? id : null;
  });

  function storageKey(): string {
    // 未登录不持久化（助手本身也只在登录后显示）
    return userId.value !== null ? `${STORAGE_PREFIX}${userId.value}` : "";
  }

  function persist() {
    const key = storageKey();
    if (!key) {
      return;
    }
    try {
      localStorage.setItem(
        key,
        JSON.stringify(messages.value.slice(-MAX_STORED))
      );
    } catch (e) {
      // 隐私模式等存储异常不影响对话本身
      console.warn("[AI 助手] 会话保存失败", e);
    }
  }

  function load() {
    const key = storageKey();
    if (!key) {
      messages.value = [];
      return;
    }
    try {
      const raw = localStorage.getItem(key);
      const parsed = raw ? JSON.parse(raw) : [];
      messages.value = Array.isArray(parsed)
        ? parsed
            .filter(
              (m: any) =>
                m &&
                (m.role === "user" || m.role === "assistant") &&
                typeof m.content === "string"
            )
            .slice(-MAX_STORED)
        : [];
    } catch (e) {
      console.warn("[AI 助手] 会话读取失败，已重置", e);
      messages.value = [];
    }
  }

  // 登录用户变化：清空错误/待重试状态并切换到该用户的会话（§8.3-6）
  watch(
    userId,
    () => {
      loading.value = false;
      errorMsg.value = "";
      lastQuestion.value = "";
      load();
    },
    { immediate: true }
  );

  const canRetry = computed(
    () => !!errorMsg.value && !!lastQuestion.value && !loading.value
  );

  async function sendInternal(
    text: string,
    pushUser: boolean
  ): Promise<boolean> {
    const question = text.trim();
    if (!question || loading.value) {
      return false;
    }
    const requestUserId = userId.value;
    if (pushUser) {
      lastQuestion.value = question;
      messages.value.push({
        role: "user",
        content: question,
        time: Date.now(),
      });
      persist();
    }
    loading.value = true;
    errorMsg.value = "";
    try {
      const res: any = await withTimeout(
        ChatControllerService.askQuestionUsingPost(buildPrompt())
      );
      if (requestUserId !== userId.value) {
        return false; // 请求期间用户已切换，丢弃响应，避免串号
      }
      if (
        res &&
        res.code === 0 &&
        typeof res.data === "string" &&
        res.data.trim()
      ) {
        messages.value.push({
          role: "assistant",
          content: res.data,
          time: Date.now(),
        });
        persist();
        return true;
      }
      errorMsg.value =
        (res && typeof res.message === "string" && res.message) ||
        "AI 服务没有返回有效内容，请稍后重试。";
      return false;
    } catch (e: any) {
      if (requestUserId !== userId.value) {
        return false;
      }
      errorMsg.value = describeError(e);
      return false;
    } finally {
      if (requestUserId === userId.value) {
        loading.value = false;
      }
    }
  }

  /** 发送新提问 */
  async function send(text: string): Promise<boolean> {
    return sendInternal(text, true);
  }

  /** 失败重试：重发最后一次提问，不重复入列用户消息 */
  async function retry(): Promise<boolean> {
    if (!lastQuestion.value || loading.value) {
      return false;
    }
    return sendInternal(lastQuestion.value, false);
  }

  /** 清空当前会话 */
  function clear() {
    messages.value = [];
    lastQuestion.value = "";
    errorMsg.value = "";
    persist();
  }

  return { messages, loading, errorMsg, canRetry, send, retry, clear };
}
