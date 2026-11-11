import axios from "axios";
import type { LearningApiResponse, MasteryChangeVO } from "@/api/learning";

/**
 * 整组自测草稿/结果与普通练习尝试（B 责任域）。
 * 自测进行中这些接口只返回题面、草稿与保存状态；结果只在交卷后可读。
 */

export interface AssessmentDraft {
  questionId: string;
  answer: string | string[] | null;
  markedUnsure: boolean;
}

export interface AssessmentDraftSaveResult {
  rowVersion: number;
  savedAt: number | null;
  /** true = 另一标签页已更新；不覆盖，返回服务端草稿 */
  conflict: boolean;
  serverDrafts: AssessmentDraft[];
}

export type SelfTestQuestionStatus =
  | "CORRECT"
  | "WRONG"
  | "UNANSWERED"
  | "PENDING_SELF_ASSESSMENT"
  | "INVALID_OFFLINE"
  | "INVALID_VERSION";

export interface AssessmentResultQuestion {
  questionId: string;
  title: string;
  type: string | null;
  status: SelfTestQuestionStatus;
  markedUnsure: boolean;
  submittedAnswer: string | string[] | null;
  correct: boolean | null;
  analysis: string | null;
  misconception: string | null;
  invalidReason: string | null;
  masteryChanges: MasteryChangeVO[];
  attemptId: string | null;
  eventIds: string[];
}

export interface AssessmentResultVO {
  runId: string;
  mode: "SELF_TEST";
  status: "FINISHED";
  finishedAt: string;
  totalQuestions: number;
  validQuestions: number;
  answeredQuestions: number;
  objectiveQuestions: number;
  correctCount: number;
  wrongCount: number;
  accuracy: number | null;
  unansweredCount: number;
  pendingSelfAssessmentCount: number;
  invalidCount: number;
  markedUnsureCount: number;
  questions: AssessmentResultQuestion[];
  wrongQuestionIds: string[];
  unsureQuestionIds: string[];
  summaryText: string;
}

export function saveAssessmentDrafts(
  runId: string,
  data: { clientSaveId: string; rowVersion: number; drafts: AssessmentDraft[] }
): Promise<LearningApiResponse<AssessmentDraftSaveResult>> {
  return axios.put(
    `/api/learning/assessment/runs/${encodeURIComponent(runId)}/drafts`,
    data
  );
}

export function getAssessmentResult(
  runId: string
): Promise<LearningApiResponse<AssessmentResultVO>> {
  return axios.get(
    `/api/learning/assessment/runs/${encodeURIComponent(runId)}/result`
  );
}

export interface PracticeAttemptVO {
  attemptContextId: string;
  questionId: string;
  questionRevision: number;
  submissionKey: string;
  helpLevel: "NONE" | "HINT" | "ANSWER_REVEALED" | "UNKNOWN";
  status: string;
}

export function createPracticeAttempt(data: {
  questionId: string;
  courseCode?: string;
  sourceUnitCode?: string;
  sourceActivityCode?: string;
  clientAttemptKey: string;
}): Promise<LearningApiResponse<PracticeAttemptVO>> {
  return axios.post("/api/learning/practice/attempts", data);
}

export interface PracticeHelpResult {
  attemptContextId: string;
  helpLevel: "NONE" | "HINT" | "ANSWER_REVEALED" | "UNKNOWN";
  eligibleForObservation: boolean;
  eligibilityReason: string;
}

export function recordPracticeHelp(
  attemptContextId: string,
  data: { helpLevel: "HINT" | "ANSWER_REVEALED"; note?: string }
): Promise<LearningApiResponse<PracticeHelpResult>> {
  return axios.post(
    `/api/learning/practice/attempts/${encodeURIComponent(
      attemptContextId
    )}/help`,
    data
  );
}
