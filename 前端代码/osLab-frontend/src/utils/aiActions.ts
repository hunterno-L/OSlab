import type { RouteLocationRaw } from "vue-router";
import type { AiNextAction, AiTargetRef } from "@/api/ai";

/** 下一步任务类型中文名（展示用；不改后端枚举） */
export const AI_ACTION_TYPE_LABELS: Record<string, string> = {
  READ: "阅读",
  QUESTION: "作答",
  PREDICT: "预测",
  EXPLAIN: "解释",
  RETEST: "复测",
};

export const AI_ACTION_STATUS_LABELS: Record<string, string> = {
  PENDING: "待开始",
  STARTED: "进行中",
  DONE: "已完成",
  SKIPPED: "已跳过",
};

export const AI_TARGET_KIND_LABELS: Record<string, string> = {
  UNIT: "课程单元",
  QUESTION: "题目",
  SIMULATION: "算法模拟",
  MATERIAL: "资料段落",
};

/** 人工核验的预测案例代码 -> 现有模拟页算法键（只做页面入口映射） */
export function simulationAlgorithmForPredictCase(code: string): string {
  const value = (code || "").toLowerCase();
  // 案例代码可能同时包含 lru 与 fifo（如 lru-vs-fifo），按算法归属判断；
  // 缺页/换出计数案例的代码不含算法名，服务端目录使用 FIFO。
  if (value.startsWith("page.lru")) return "page.lru";
  if (value.startsWith("page.fifo")) return "page.fifo";
  if (value.includes("lru")) return "page.lru";
  if (value.includes("fifo")) return "page.fifo";
  if (
    value.includes("faults") ||
    value.includes("eviction") ||
    value.includes("count")
  ) {
    return "page.fifo";
  }
  return "page.opt";
}

/** 从资料块 blockId（courseCode::material:123::p0）解析个人资料 id */
export function materialIdFromBlockId(blockId?: string | null): string {
  const match = /::material:(\d+)::/.exec(blockId || "");
  return match ? match[1] : "";
}

/** 来源定位里的段落序号（locator 形如 material:12#p2 或 sec.x#chunk-y-p2） */
export function paragraphFromLocator(locator?: string | null): number {
  const match = /#p(\d+)$/.exec(locator || "");
  return match ? Number(match[1]) : 0;
}

/**
 * 把目标定位映射到原页面入口（不新建学习页）。
 * actionId 只在实际开始任务时附带；补学返回不应重启上一任务。
 */
export function targetLocation(
  target: AiTargetRef | null | undefined,
  courseCode: string,
  actionId?: string
): RouteLocationRaw | null {
  if (!target || !target.code) return null;
  if (target.kind === "UNIT") {
    const query: Record<string, string> = {};
    if (target.anchor) query.activity = target.anchor;
    if (actionId) query.aiActionId = actionId;
    return {
      path: `/courses/${encodeURIComponent(
        courseCode
      )}/units/${encodeURIComponent(target.code)}`,
      query,
    };
  }
  if (target.kind === "QUESTION") {
    return {
      path: "/assessments",
      query: {
        courseCode,
        questionId: target.code,
        ...(actionId ? { aiActionId: actionId } : {}),
      },
    };
  }
  if (target.kind === "SIMULATION") {
    return {
      path: "/experiments/simulation",
      query: {
        courseCode,
        algorithmKey: simulationAlgorithmForPredictCase(target.code),
        predictCase: target.code,
        ...(actionId ? { aiActionId: actionId } : {}),
      },
    };
  }
  // MATERIAL：由全局资料对话框打开具体段落，不跳转页面
  return null;
}

/**
 * 把 NextAction 映射到原页面入口（不新建学习页）。
 * 返回 null 表示当前目标缺少可用入口，调用方应给出可读提示。
 */
export function nextActionTarget(
  action: AiNextAction,
  courseCode: string
): RouteLocationRaw | null {
  return targetLocation(action.targetRef, courseCode, action.actionId);
}

/** 预测结果显示的“差异步骤”文本（后端 diffSteps 或本地计算） */
export function describePredictionDiff(diffSteps?: string[] | null): string {
  if (!Array.isArray(diffSteps) || diffSteps.length === 0) return "";
  return diffSteps.join("；");
}
