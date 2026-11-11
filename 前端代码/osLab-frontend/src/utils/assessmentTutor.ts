import * as aiLauncher from "@/composables/useAiLauncher";

/**
 * 题卡 → 统一导师的薄适配。
 *
 * <p>A 的 `openTutor` 尚未合入前，退回现有的 `askAiAssistant` 文本入口；
 * 这里不维护会话、不复制聊天逻辑，A 合入后自动切到正式契约。
 * 帮助等级必须在调用本方法前由服务端尝试上下文记录成功。
 */
export interface AssessmentTutorRequest {
  intent: "HINT" | "EXPLAIN";
  courseCode: string;
  questionId?: string;
  attemptContextId?: string;
  assessmentRunId?: string;
  draft?: string;
}

export interface TutorSceneRequest {
  scene: "GLOBAL" | "UNIT" | "NODE" | "QUESTION" | "EXPERIMENT";
  intent: "ASK" | "EXPLAIN" | "HINT" | "DEBUG" | "PLAN";
  courseCode: string;
  unitCode?: string;
  nodeCodes?: string[];
  eventIds?: string[];
  selectedSourceIds?: string[];
  attachments?: Array<{
    kind: "SELECTION" | "CODE" | "ERROR";
    label: string;
    text: string;
    sourceId?: string;
  }>;
  draft?: string;
  delivery?: "DRAFT" | "SEND";
}

type TutorLauncher = {
  openTutor?: (request: Record<string, unknown>) => void;
};

/** 页面级统一导师入口：A 的 openTutor 就绪后自动切到正式契约。 */
export function openTutorScene(request: TutorSceneRequest): void {
  if (typeof window === "undefined") return;
  const launcher = aiLauncher as unknown as TutorLauncher;
  if (typeof launcher.openTutor === "function") {
    launcher.openTutor({
      scene: request.scene,
      intent: request.intent,
      courseCode: request.courseCode,
      scope: "PAGE",
      ...(request.unitCode ? { unitCode: request.unitCode } : {}),
      ...(request.nodeCodes?.length ? { nodeCodes: request.nodeCodes } : {}),
      ...(request.eventIds?.length ? { eventIds: request.eventIds } : {}),
      ...(request.selectedSourceIds?.length
        ? { selectedSourceIds: request.selectedSourceIds }
        : {}),
      ...(request.attachments?.length
        ? { attachments: request.attachments }
        : {}),
      ...(request.draft ? { draft: request.draft } : {}),
      delivery: request.delivery || "DRAFT",
    });
    return;
  }
  const fallback =
    request.draft ||
    (request.intent === "HINT"
      ? "请给我一个不直接给出答案的提示。"
      : "请解释当前内容并给出下一步。");
  aiLauncher.askAiAssistant(fallback);
}

export function openAssessmentTutor(request: AssessmentTutorRequest): void {
  if (typeof window === "undefined") return;
  const launcher = aiLauncher as unknown as TutorLauncher;
  if (typeof launcher.openTutor === "function") {
    launcher.openTutor({
      scene: "QUESTION",
      intent: request.intent,
      courseCode: request.courseCode,
      scope: "PAGE",
      ...(request.questionId
        ? { resourceRef: `question:${request.questionId}` }
        : {}),
      ...(request.attemptContextId
        ? { attemptContextId: request.attemptContextId }
        : {}),
      ...(request.assessmentRunId
        ? { assessmentRunId: request.assessmentRunId }
        : {}),
      ...(request.draft ? { draft: request.draft } : {}),
      delivery: request.intent === "HINT" ? "SEND" : "DRAFT",
    });
    return;
  }
  // 兼容占位：A 的开放入口就绪后自动走上面的正式分支
  const prompt =
    request.intent === "HINT"
      ? "请给我一个不直接给出答案的提示，帮助我自己找到这道题的解法。"
      : "请解释这道题的关键思路，但先不要直接给出最终答案。";
  aiLauncher.askAiAssistant(
    request.questionId
      ? `${prompt}（题目定位：question:${request.questionId}）`
      : prompt
  );
}
