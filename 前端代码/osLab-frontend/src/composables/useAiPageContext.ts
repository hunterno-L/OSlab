/**
 * AI 助手页面上下文注册（升级为统一课程上下文）。
 *
 * 页面在 onMounted 时注册一个 provider（返回当前页面的短上下文对象），
 * onBeforeUnmount 时注销。助手发送轮次时通过 buildAiTurnContext() 取
 * courseCode / unitCode / nodeCodes / resourceRef / eventIds / selectedSourceIds，
 * 后端会重新校验归属与内容，不信任前端提供的分数或结论。
 *
 * 约束：只传定位标识与短摘要，禁止抓取整个 DOM、禁止自动上传大段源码。
 */

/** 页面显式注册给 AI 助手的上下文（全部为短文本/定位标识，字段按需可选） */
export interface AiPageContext {
  /** 页面名称，如“知识图谱” */
  pageName: string;
  /** 当前课程编码（统一会话按课程隔离） */
  courseCode?: string;
  courseTitle?: string;
  /** 当前单元编码与名称 */
  unitCode?: string;
  unitName?: string;
  /** 当前学习节点（展示名与稳定编码） */
  nodeName?: string;
  nodeCode?: string;
  nodeCodes?: string[];
  /** 当前章节/小节 */
  chapter?: string;
  /** 当前实验/题目等资源定位，如 question:123 */
  resourceRef?: string;
  /** 当前实验标题 */
  experimentTitle?: string;
  /** 最近一次判题结果摘要（如“通过”或一句话失败原因） */
  lastJudgeResult?: string;
  /** 与本页相关的学习事件 id（字符串，后端只读核验） */
  eventIds?: string[];
  /** 用户在本页主动选中的资料来源 id */
  selectedSourceIds?: string[];
}

/**
 * 后端轮次上下文（冻结契约 AiCourseContext）。
 * courseCode 缺省时后端以会话课程为准。
 */
export interface AiTurnPageContext {
  courseCode?: string;
  unitCode?: string;
  nodeCodes: string[];
  resourceRef?: string;
  eventIds: string[];
  selectedSourceIds: string[];
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

function cleanCodes(values?: string[]): string[] {
  if (!Array.isArray(values)) return [];
  return Array.from(
    new Set(values.filter((item) => typeof item === "string" && item.trim()))
  ).map((item) => item.trim());
}

/** 把页面上下文收敛成后端冻结的轮次上下文结构 */
export function buildAiTurnContext(
  overrides: Partial<AiTurnPageContext> = {}
): AiTurnPageContext {
  const ctx = getAiPageContext() ?? ({} as AiPageContext);
  const nodeCodes = cleanCodes([
    ...(ctx.nodeCodes ?? []),
    ...(ctx.nodeCode ? [ctx.nodeCode] : []),
  ]);
  return {
    courseCode: overrides.courseCode || ctx.courseCode || undefined,
    unitCode: overrides.unitCode || ctx.unitCode || undefined,
    nodeCodes: cleanCodes([...nodeCodes, ...(overrides.nodeCodes ?? [])]),
    resourceRef: overrides.resourceRef || ctx.resourceRef || undefined,
    eventIds: cleanCodes([
      ...(ctx.eventIds ?? []),
      ...(overrides.eventIds ?? []),
    ]),
    selectedSourceIds: cleanCodes([
      ...(ctx.selectedSourceIds ?? []),
      ...(overrides.selectedSourceIds ?? []),
    ]),
  };
}

/** 当前页面所属课程；未注册时返回空串 */
export function getAiCourseCode(): string {
  return getAiPageContext()?.courseCode || "";
}

/** 当前页面是否选择了资料段落（来源卡/选段提问） */
export function getAiSelectedSourceIds(): string[] {
  return cleanCodes(getAiPageContext()?.selectedSourceIds);
}

/** 把上下文对象拼成给 AI 的短摘要文本；无上下文时返回空串 */
export function buildPageContextSummary(): string {
  const ctx = getAiPageContext();
  if (!ctx || !ctx.pageName) {
    return "";
  }
  const lines: string[] = [`页面：${ctx.pageName}`];
  if (ctx.courseTitle || ctx.courseCode) {
    lines.push(
      `当前课程：${ctx.courseTitle || ctx.courseCode}${
        ctx.courseTitle && ctx.courseCode ? `（${ctx.courseCode}）` : ""
      }`
    );
  }
  if (ctx.unitName || ctx.unitCode) {
    lines.push(`当前单元：${ctx.unitName || ctx.unitCode}`);
  }
  if (ctx.nodeName || ctx.nodeCode) {
    lines.push(`当前节点：${ctx.nodeName || ctx.nodeCode}`);
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
