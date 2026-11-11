/**
 * 学习更新通知（B → A/B 消费）。
 *
 * <p>只在后端写入成功后触发；载荷只含定位与原因，不带成绩或掌握度，
 * 避免前端为了动画自造学习事实。旧事件 `oslab:learning-changed` 继续保留兼容。
 */
export type LearningUpdatedReason =
  | "ANSWERED"
  | "ASSESSMENT_FINISHED"
  | "ACTIVITY_DONE"
  | "JUDGED";

export interface LearningUpdatedPayload {
  courseCode: string;
  unitCode?: string;
  activityCode?: string;
  eventIds?: string[];
  runId?: string;
  reason: LearningUpdatedReason;
}

export function notifyLearningUpdated(payload: LearningUpdatedPayload): void {
  if (typeof window === "undefined" || !payload?.courseCode) return;
  window.dispatchEvent(
    new CustomEvent("oslab:learning-updated", { detail: payload })
  );
  window.dispatchEvent(new Event("oslab:learning-changed"));
}
