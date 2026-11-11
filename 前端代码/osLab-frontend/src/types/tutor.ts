/**
 * 统一导师公共契约（01 契约 §2，D1 冻结）。
 *
 * 场景/意图/作用域只描述“这一轮辅导”，与会话目标 focus（学习/复习）分开。
 * B 的页面入口只依赖本文件与 `openTutor`，不触碰面板内部状态。
 */
import type { AiTutorAttachment } from "@/api/ai";

export type { AiTutorAttachment };

export type TutorScene = "GLOBAL" | "UNIT" | "NODE" | "QUESTION" | "EXPERIMENT";

export type TutorIntent = "ASK" | "EXPLAIN" | "HINT" | "DEBUG" | "PLAN";

export type TutorContextScope = "PAGE" | "COURSE";

export interface TutorAttachment extends AiTutorAttachment {
  kind: "SELECTION" | "CODE" | "ERROR";
}

export interface TutorLaunchRequest {
  scene: TutorScene;
  intent: TutorIntent;
  /** 课程编码；空串表示由当前页面上下文解析（兼容旧入口）。 */
  courseCode: string;
  scope?: TutorContextScope;
  unitCode?: string;
  nodeCodes?: string[];
  resourceRef?: string;
  selectedSourceIds?: string[];
  eventIds?: string[];
  /** 服务端建立的答题证据上下文（B 的普通尝试）。 */
  attemptContextId?: string;
  /** 服务端评估运行 id（自测）。 */
  assessmentRunId?: string;
  /** 预填草稿；打开面板不调用模型。 */
  draft?: string;
  attachments?: TutorAttachment[];
  /** DRAFT：只填入输入框（默认）；SEND：立即发送。 */
  delivery?: "DRAFT" | "SEND";
  /** 面板头部展示的当前场景名称（可选，仅展示）。 */
  sceneLabel?: string;
}

/** 与服务端一致的材料上限：超限明确拒绝，不静默截断。 */
export const TUTOR_ATTACHMENT_LIMITS = {
  selectionChars: 2000,
  snippetChars: 8000,
  totalChars: 12000,
  count: 5,
  messageChars: 4000,
} as const;

export interface TutorAttachmentCheck {
  ok: boolean;
  error?: string;
}

export function validateTutorAttachments(
  attachments: TutorAttachment[] | undefined
): TutorAttachmentCheck {
  if (!attachments || attachments.length === 0) return { ok: true };
  if (attachments.length > TUTOR_ATTACHMENT_LIMITS.count) {
    return {
      ok: false,
      error: `附带材料过多（最多 ${TUTOR_ATTACHMENT_LIMITS.count} 项），请减少后重试。`,
    };
  }
  let total = 0;
  for (const item of attachments) {
    const text = (item.text || "").trim();
    if (!text) {
      return { ok: false, error: "附带材料内容不能为空。" };
    }
    const limit =
      item.kind === "SELECTION"
        ? TUTOR_ATTACHMENT_LIMITS.selectionChars
        : TUTOR_ATTACHMENT_LIMITS.snippetChars;
    if (text.length > limit) {
      return {
        ok: false,
        error: `附带材料「${
          item.label || item.kind
        }」过长（最多 ${limit} 字），请选择更小的范围。`,
      };
    }
    total += text.length;
    if (total > TUTOR_ATTACHMENT_LIMITS.totalChars) {
      return {
        ok: false,
        error: `附带材料合计过长（最多 ${TUTOR_ATTACHMENT_LIMITS.totalChars} 字），请减少选择。`,
      };
    }
  }
  return { ok: true };
}

export const TUTOR_SCENE_LABELS: Record<TutorScene, string> = {
  GLOBAL: "全局导师",
  UNIT: "单元辅导",
  NODE: "节点辅导",
  QUESTION: "题目辅导",
  EXPERIMENT: "实验排错",
};

export const TUTOR_INTENT_LABELS: Record<TutorIntent, string> = {
  ASK: "提问",
  EXPLAIN: "解释",
  HINT: "提示",
  DEBUG: "排错",
  PLAN: "安排学习",
};

export function normalizeScene(value?: string | null): TutorScene {
  return value === "UNIT" ||
    value === "NODE" ||
    value === "QUESTION" ||
    value === "EXPERIMENT"
    ? value
    : "GLOBAL";
}

export function normalizeIntent(value?: string | null): TutorIntent {
  return value === "EXPLAIN" ||
    value === "HINT" ||
    value === "DEBUG" ||
    value === "PLAN"
    ? value
    : "ASK";
}
