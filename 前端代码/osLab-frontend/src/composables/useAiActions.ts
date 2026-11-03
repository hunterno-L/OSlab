/**
 * AI 下一步任务的执行状态（开始 / 提交 / 帮助等级）。
 *
 * 任务卡、题卡与模拟预测共用这一份模块级缓存：
 * - start 返回的 ActivityRefVO 只包含提交前允许看到的内容，缓存在内存中，
 *   刷新页面后由页面重新 start 取得（start 幂等，证据上下文按 actionId 复用）。
 * - submit 使用服务端下发的 submissionKey，重复点击不会重复判分。
 * - 帮助等级必须在提交前写入；提交后控件禁用，证据快照不可再改。
 */
import { ref } from "vue";
import store from "@/store";
import {
  recordAiActionHelp,
  startAiAction,
  submitAiAction,
  type AiActionSubmitResultVO,
  type AiActivityRefVO,
  type AiHelpLevel,
  type AiHelpResultVO,
  type AiSelfReportedHelp,
  type AiSubmitActionRequest,
} from "@/api/ai";
import { refreshAiNextActions } from "@/composables/useAiConversation";
import { getAiPageContext } from "@/composables/useAiPageContext";

const startedActions = ref<Record<string, AiActivityRefVO>>({});
const startingActionId = ref("");
const submittingActionId = ref("");
const actionError = ref("");
const lastSubmitResult = ref<AiActionSubmitResultVO | null>(null);

function currentCourse(): string {
  return getAiPageContext()?.courseCode || "";
}

function currentUserId(): string | number | null {
  const id = store.state.user?.loginUser?.id;
  return typeof id === "number" || typeof id === "string" ? id : null;
}

let lastUserId = currentUserId();
store.watch(
  () => currentUserId(),
  (id) => {
    if (id === lastUserId) return;
    lastUserId = id;
    startedActions.value = {};
    startingActionId.value = "";
    submittingActionId.value = "";
    actionError.value = "";
    lastSubmitResult.value = null;
  }
);

export function getStartedAiAction(actionId: string): AiActivityRefVO | null {
  return startedActions.value[actionId] || null;
}

/** 开始一条下一步任务；返回只含提交前可见内容的 ActivityRefVO。 */
export async function startAiPlanAction(
  actionId: string
): Promise<AiActivityRefVO | null> {
  if (!actionId) return null;
  const cached = startedActions.value[actionId];
  if (cached) return cached;
  startingActionId.value = actionId;
  actionError.value = "";
  try {
    const response = await startAiAction(actionId);
    if (response.data.code !== 0 || !response.data.data?.activityRef) {
      throw new Error(response.data.message || "任务无法开始");
    }
    startedActions.value = {
      ...startedActions.value,
      [actionId]: response.data.data.activityRef,
    };
    return response.data.data.activityRef;
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : "任务无法开始";
    return null;
  } finally {
    startingActionId.value = "";
  }
}

/** 提交任务；委托原判分/页面置换计算器，不伪造正确与否。 */
export async function submitAiPlanAction(
  actionId: string,
  body: AiSubmitActionRequest,
  courseCode?: string
): Promise<AiActionSubmitResultVO | null> {
  if (!actionId || submittingActionId.value) return null;
  submittingActionId.value = actionId;
  actionError.value = "";
  try {
    const response = await submitAiAction(actionId, body);
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "提交失败，请重试");
    }
    lastSubmitResult.value = response.data.data;
    if (typeof window !== "undefined") {
      window.dispatchEvent(new Event("oslab:learning-changed"));
    }
    // 完成/失败都会改变当前任务与未完成队列；静默刷新，不阻塞本次反馈
    void refreshAiNextActions({
      courseCode: courseCode || currentCourse() || undefined,
    });
    return response.data.data;
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : "提交失败，请重试";
    return null;
  } finally {
    submittingActionId.value = "";
  }
}

/** 提交前记录帮助等级；答案揭示必须单独标识并影响证据资格。 */
export async function recordAiHelp(
  actionId: string,
  helpLevel: Exclude<AiHelpLevel, "UNKNOWN">,
  selfReportedHelp?: AiSelfReportedHelp
): Promise<AiHelpResultVO | null> {
  if (!actionId) return null;
  actionError.value = "";
  try {
    const response = await recordAiActionHelp(actionId, {
      helpLevel,
      ...(selfReportedHelp ? { selfReportedHelp } : {}),
    });
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || "帮助等级记录失败");
    }
    const cached = startedActions.value[actionId];
    if (cached) {
      startedActions.value = {
        ...startedActions.value,
        [actionId]: {
          ...cached,
          helpLevel: response.data.data.helpLevel,
        },
      };
    }
    return response.data.data;
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : "帮助等级记录失败";
    return null;
  }
}

export function useAiActions() {
  return {
    startedActions,
    startingActionId,
    submittingActionId,
    actionError,
    lastSubmitResult,
    startAction: startAiPlanAction,
    submitAction: submitAiPlanAction,
    recordHelp: recordAiHelp,
    getStartedAction: getStartedAiAction,
  };
}
