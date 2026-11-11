/**
 * 统一导师入口（01 契约 §2）。
 *
 * 面板是 App.vue 的全局单例；页面只提交 TutorLaunchRequest，不持有面板状态。
 * 旧 `openAiAssistant` / `askAiAssistant` 保留为薄适配，不再维护独立会话。
 */
import { getAiCourseCode } from "@/composables/useAiPageContext";
import { dispatchTutorLaunch } from "@/composables/useTutorLaunch";
import type { TutorLaunchRequest } from "@/types/tutor";

/** 从任意原页面打开全局 AI 导师（新契约）。 */
export function openTutor(request: TutorLaunchRequest): void {
  if (typeof window === "undefined") return;
  dispatchTutorLaunch({
    ...request,
    courseCode: request.courseCode || getAiCourseCode() || "",
    delivery: request.delivery || "DRAFT",
    scope: request.scope || "PAGE",
  });
}

/** 兼容旧入口：只打开面板，不发送。 */
export function openAiAssistant(): void {
  openTutor({
    scene: "GLOBAL",
    intent: "ASK",
    courseCode: getAiCourseCode() || "",
    delivery: "DRAFT",
  });
}

/** 兼容旧入口：带入一条初始问题并立即发送（如“解释我选中的这段资料”）。 */
export function askAiAssistant(text: string): void {
  const prompt = text.trim();
  if (!prompt) {
    openAiAssistant();
    return;
  }
  openTutor({
    scene: "GLOBAL",
    intent: "ASK",
    courseCode: getAiCourseCode() || "",
    draft: prompt,
    delivery: "SEND",
  });
}
