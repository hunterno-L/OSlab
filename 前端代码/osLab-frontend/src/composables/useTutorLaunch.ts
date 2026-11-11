/**
 * 统一导师启动状态（模块级单例）。
 *
 * 页面只调用 `openTutor`；全局面板（FloatingAiAssistant）在任意路由打开并消费请求。
 * 这里只保存“待处理的启动参数”，不保存消息或会话——会话以服务端为准。
 */
import { shallowRef } from "vue";
import type { TutorLaunchRequest } from "@/types/tutor";

export interface PendingTutorLaunch {
  request: TutorLaunchRequest;
  nonce: number;
}

export const pendingTutorLaunch = shallowRef<PendingTutorLaunch | null>(null);
let nonce = 0;

export function dispatchTutorLaunch(request: TutorLaunchRequest): void {
  pendingTutorLaunch.value = { request, nonce: ++nonce };
  if (typeof window !== "undefined") {
    window.dispatchEvent(
      new CustomEvent("oslab:tutor-open", { detail: request })
    );
  }
}

/** 面板消费一次启动请求（避免重复发送同一草稿）。 */
export function consumeTutorLaunch(): TutorLaunchRequest | null {
  const current = pendingTutorLaunch.value;
  pendingTutorLaunch.value = null;
  return current ? current.request : null;
}
