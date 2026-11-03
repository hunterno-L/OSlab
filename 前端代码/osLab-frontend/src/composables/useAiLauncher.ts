/**
 * 从任意原页面打开全局 AI 导师。
 * 面板是 App.vue 的全局单例；页面只派发意图事件，不直接持有面板状态。
 */
export function openAiAssistant(): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event("oslab:ai-open"));
}

/** 打开助手并带入一条初始问题（如“请解释我选中的这段资料”）。 */
export function askAiAssistant(text: string): void {
  if (typeof window === "undefined") return;
  const prompt = text.trim();
  if (!prompt) {
    openAiAssistant();
    return;
  }
  window.dispatchEvent(
    new CustomEvent("oslab:ai-ask", { detail: { text: prompt } })
  );
}
