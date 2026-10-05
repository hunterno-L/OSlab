/**
 * AI 助手页面上下文注册（log3 阶段 4）：
 * 页面在 onMounted 时注册一个 provider（返回当前页面的简短上下文小对象），
 * onBeforeUnmount 时注销；AI 助手发送提问时通过 buildPageContextSummary()
 * 把上下文拼成短摘要。显式注册、小对象传递：禁止抓取整个 DOM、
 * 禁止自动上传大段源码。
 */

/** 页面显式注册给 AI 助手的上下文（全部为短文本摘要，字段按需可选） */
export interface AiPageContext {
  /** 页面名称，如“知识图谱” */
  pageName: string;
  /** 当前学习节点名称 */
  nodeName?: string;
  /** 当前章节/小节 */
  chapter?: string;
  /** 当前实验标题 */
  experimentTitle?: string;
  /** 最近一次判题结果摘要（如“通过”或一句话失败原因） */
  lastJudgeResult?: string;
}

export type AiPageContextProvider = () => AiPageContext | null;

/** 单例：同一时刻只有一个页面持有上下文 */
let currentProvider: AiPageContextProvider | null = null;

/** 页面挂载时注册上下文 provider（后注册者覆盖先注册者） */
export function registerAiPageContext(provider: AiPageContextProvider): void {
  currentProvider = provider;
}

/** 页面卸载时注销；仅当仍是自己注册的 provider 时才清空，避免误删他人 */
export function unregisterAiPageContext(provider: AiPageContextProvider): void {
  if (currentProvider === provider) {
    currentProvider = null;
  }
}

/** 读取当前页面上下文；provider 抛错时按“无上下文”处理，不影响提问 */
export function getAiPageContext(): AiPageContext | null {
  if (!currentProvider) {
    return null;
  }
  try {
    return currentProvider() ?? null;
  } catch (e) {
    console.error("[AI 助手] 读取页面上下文失败", e);
    return null;
  }
}

/** 把上下文对象拼成给 AI 的短摘要文本；无上下文时返回空串 */
export function buildPageContextSummary(): string {
  const ctx = getAiPageContext();
  if (!ctx || !ctx.pageName) {
    return "";
  }
  const lines: string[] = [`页面：${ctx.pageName}`];
  if (ctx.nodeName) {
    lines.push(`当前节点：${ctx.nodeName}`);
  }
  if (ctx.chapter) {
    lines.push(`当前章节：${ctx.chapter}`);
  }
  if (ctx.experimentTitle) {
    lines.push(`当前实验：${ctx.experimentTitle}`);
  }
  if (ctx.lastJudgeResult) {
    lines.push(`最近一次判题结果：${ctx.lastJudgeResult}`);
  }
  return lines.join("；");
}
