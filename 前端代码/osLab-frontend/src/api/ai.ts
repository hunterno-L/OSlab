import axios, { type AxiosResponse } from "axios";
import type { BaseResponse } from "@/api/learning";

/**
 * 统一 AI 学习会话 API（ZhiCore AI 升级 S2）。
 *
 * 与 src/api/learning.ts 同一约定：直接使用全局 axios 默认实例（withCredentials、
 * 未登录跳转、错误提示由 plugins/axios.ts 统一处理），路径带 /api 前缀，
 * 返回原始 AxiosResponse<BaseResponse<T>>。长整型 ID 在冻结契约中一律为字符串，
 * 前端不得转成 Number。
 */

// ========== 冻结公共对象 ==========

export type AiHelpLevel = "NONE" | "HINT" | "ANSWER_REVEALED" | "UNKNOWN";
export type AiSelfReportedHelp = "NONE" | "EXTERNAL_AI" | "PEER" | "OTHER";
export type AiSessionFocus = "LEARN" | "REVIEW";
export type AiTurnStatus =
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED_RETRYABLE"
  | "FAILED";
export type AiTurnMode = "AI" | "FALLBACK";
export type AiNextActionType =
  | "READ"
  | "QUESTION"
  | "PREDICT"
  | "EXPLAIN"
  | "RETEST";
export type AiNextActionStatus = "PENDING" | "STARTED" | "DONE" | "SKIPPED";
export type AiTargetKind = "UNIT" | "QUESTION" | "SIMULATION" | "MATERIAL";
export type AiDiagnosisStatus = "TENTATIVE" | "SUPPORTED" | "INSUFFICIENT";

export interface AiSourceRef {
  sourceId: string;
  ownerScope: "COURSE" | "PERSONAL";
  courseCode: string;
  revision: number | null;
  blockId: string;
  locator: string;
  excerpt: string;
}

export interface AiToolTraceEntry {
  tool: string;
  status: "OK" | "EMPTY" | "FAILED" | "SKIPPED" | "BUDGET_EXCEEDED" | string;
  ms: number;
  summary: string;
}

export interface AiHypothesis {
  code: string;
  label: string;
}

export interface AiDiagnosis {
  hypotheses: AiHypothesis[];
  status: AiDiagnosisStatus;
  supportingEventIds: string[];
  conflictingEventIds: string[];
  verificationActionIds: string[];
  explanation?: string;
}

export interface AiTargetRef {
  kind: AiTargetKind;
  code: string;
  anchor: string | null;
}

export interface AiNextAction {
  actionId: string;
  type: AiNextActionType;
  targetRef: AiTargetRef;
  reason: string;
  evidenceIds: string[];
  estimatedMinutes: number | null;
  status: AiNextActionStatus;
  returnRef: AiTargetRef | null;
}

export interface AiEvidenceSnapshot {
  eventIds: string[];
  skillCode: string;
  result: string;
  helpLevel: AiHelpLevel;
  selfReportedHelp: string;
  eligibleForObservation: boolean;
  eligibilityReason: string;
}

export interface AiTokenUsage {
  prompt: number;
  completion: number;
  total: number;
}

export interface AiUsage {
  model: string;
  latencyMs: number;
  tokens: AiTokenUsage | null;
  modelCalls?: number;
  toolCalls?: number;
}

// ========== 会话 / 轮次 ==========

export interface AiSessionVO {
  sessionId: string;
  courseCode: string;
  goal: string;
  focus: AiSessionFocus;
  timeBudgetMinutes: number | null;
  currentAction: AiNextAction | null;
  modelProfileId: string | null;
  rowVersion: number;
  status: "ACTIVE" | "CLOSED" | string;
  unitCode?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiTurnVO {
  turnId: string;
  status: AiTurnStatus;
  answer: string;
  mode: AiTurnMode;
  sources: AiSourceRef[];
  toolTrace: AiToolTraceEntry[];
  diagnosis: AiDiagnosis | null;
  nextActions: AiNextAction[];
  usage: AiUsage | null;
  errorMessage?: string | null;
  generatedAt?: string;
}

export interface AiCourseContext {
  courseCode?: string;
  unitCode?: string;
  nodeCodes?: string[];
  resourceRef?: string;
  eventIds?: string[];
  selectedSourceIds?: string[];
}

export interface AiCreateSessionRequest {
  courseCode: string;
  goal?: string;
  focus?: AiSessionFocus;
  timeBudgetMinutes?: number;
  modelProfileId?: string;
  unitCode?: string;
}

export interface AiUpdateSessionRequest {
  goal?: string;
  focus?: AiSessionFocus;
  timeBudgetMinutes?: number;
  modelProfileId?: string;
  unitCode?: string;
  currentActionId?: string;
  status?: "ACTIVE" | "CLOSED";
  /** 写操作必须携带，后端做乐观锁校验 */
  rowVersion: number;
}

export interface AiCreateTurnRequest {
  clientRequestId: string;
  message: string;
  context?: AiCourseContext;
}

export interface AiNextActionsVO {
  actions: AiNextAction[];
  generatedAt: string;
  focus?: AiSessionFocus;
  sessionId?: string | null;
  timeBudgetMinutes?: number;
  dueReviewCount?: number;
}

// ========== 活动执行 ==========

export interface AiActivityOption {
  key: string;
  label: string;
}

export interface AiPredictStep {
  step: number;
  page: number;
  fault: boolean;
  evicted: number | null;
  frames: number[];
}

export interface AiActivityRefVO {
  actionId: string;
  type: AiNextActionType;
  targetRef: AiTargetRef;
  title: string;
  prompt: string;
  options: AiActivityOption[];
  frames: number | null;
  visibleSequence: number[];
  visibleSteps: AiPredictStep[];
  predictStepIndex: number;
  submissionKey: string;
  helpLevel: AiHelpLevel;
  expectsText: boolean;
}

export interface AiMasteryChangeVO {
  nodeName: string;
  masteryBefore: number | null;
  masteryAfter: number | null;
  status: string;
  countedAsObservation: boolean;
}

export interface AiActionSubmitResultVO {
  result:
    | "PASS"
    | "FAIL"
    | "RECORDED"
    | "NOT_ASSESSED"
    | "SYSTEM_ERROR"
    | "COMPILE_ERROR"
    | string;
  correct: boolean | null;
  judgeMessage: string;
  evidence: AiEvidenceSnapshot;
  analysis?: string | null;
  diffSteps?: string[];
  masteryChanges?: AiMasteryChangeVO[];
  nextReviewDueAt?: string | null;
}

export interface AiSubmitActionRequest {
  submissionKey: string;
  choiceKey?: string;
  predictedProcess?: string;
  text?: string;
  selfReportedHelp?: AiSelfReportedHelp;
}

export interface AiHelpResultVO {
  actionId: string;
  helpLevel: AiHelpLevel;
  selfReportedHelp: string;
  eligibleForObservation: boolean;
  eligibilityReason: string;
}

// ========== 资料 / 索引 ==========

export interface AiMaterialVO {
  materialId: string;
  chunkCount: number;
  revision: number;
  courseCode: string;
  title: string;
  indexStatus: string;
  embeddingModel?: string | null;
  createdAt?: string;
}

export interface AiMaterialDetailVO {
  material: AiMaterialVO;
  contentMd: string;
}

export interface AiIndexReportVO {
  courseCode: string;
  blocks: number;
  written: number;
  skipped: number;
  removed: number;
  embedded: number;
  embeddingModel: string;
  degraded: boolean;
  message: string;
}

// ========== 模型配置 ==========

export interface AiModelCapabilities {
  chat: boolean;
  structured: boolean;
  nativeTools: boolean;
}

export interface AiModelProfileVO {
  profileId: string;
  name: string;
  baseUrlMasked: string;
  model: string;
  capabilities: AiModelCapabilities;
  testedAt: string | null;
  active: boolean;
  platformDefault?: boolean;
  status?: "CANDIDATE" | "TESTED" | "ACTIVE" | "REJECTED" | string;
  testStatus?: "UNTESTED" | "PASSED" | "FAILED" | string;
  apiKeyMasked?: string;
}

export interface AiModelProfileTestVO {
  ok: boolean;
  latencyMs: number;
  capabilities: AiModelCapabilities;
  error?: string;
}

export interface AiModelProfileCreateRequest {
  name: string;
  serviceType?: "OPENAI_COMPATIBLE" | "ARK";
  baseUrl: string;
  model: string;
  apiKey: string;
}

// ========== 请求封装 ==========

const API = "/api";
export type AiApiResponse<T> = AxiosResponse<BaseResponse<T>>;

/** 解包 BaseResponse；业务失败抛出后端 message，避免各页面重复判断。 */
export function aiData<T>(response: AiApiResponse<T>): T {
  if (response.data.code !== 0) {
    throw new Error(response.data.message || "AI 服务请求失败");
  }
  return response.data.data;
}

export function createAiSession(
  body: AiCreateSessionRequest
): Promise<AiApiResponse<AiSessionVO>> {
  return axios.post(`${API}/learning/ai/sessions`, body);
}

export function getAiSession(
  sessionId: string
): Promise<AiApiResponse<AiSessionVO>> {
  return axios.get(
    `${API}/learning/ai/sessions/${encodeURIComponent(sessionId)}`
  );
}

export function updateAiSession(
  sessionId: string,
  body: AiUpdateSessionRequest
): Promise<AiApiResponse<AiSessionVO>> {
  return axios.patch(
    `${API}/learning/ai/sessions/${encodeURIComponent(sessionId)}`,
    body
  );
}

export function createAiTurn(
  sessionId: string,
  body: AiCreateTurnRequest
): Promise<AiApiResponse<AiTurnVO>> {
  return axios.post(
    `${API}/learning/ai/sessions/${encodeURIComponent(sessionId)}/turns`,
    body,
    { timeout: 60000 }
  );
}

export function getAiTurn(turnId: string): Promise<AiApiResponse<AiTurnVO>> {
  return axios.get(`${API}/learning/ai/turns/${encodeURIComponent(turnId)}`);
}

export function startAiAction(actionId: string): Promise<
  AiApiResponse<{
    actionId: string;
    status: string;
    activityRef: AiActivityRefVO;
  }>
> {
  return axios.post(
    `${API}/learning/ai/actions/${encodeURIComponent(actionId)}/start`
  );
}

export function submitAiAction(
  actionId: string,
  body: AiSubmitActionRequest
): Promise<AiApiResponse<AiActionSubmitResultVO>> {
  return axios.post(
    `${API}/learning/ai/actions/${encodeURIComponent(actionId)}/submit`,
    body,
    { timeout: 60000 }
  );
}

/**
 * 记录帮助等级。必须在提交作答事务之前调用；后端按单调升级合并，
 * 提交后再调用不会改变已生成的证据快照。
 */
export function recordAiActionHelp(
  actionId: string,
  body: {
    helpLevel: Exclude<AiHelpLevel, "UNKNOWN">;
    selfReportedHelp?: AiSelfReportedHelp;
    note?: string;
  }
): Promise<AiApiResponse<AiHelpResultVO>> {
  return axios.post(
    `${API}/learning/ai/actions/${encodeURIComponent(actionId)}/help`,
    body
  );
}

export function listAiNextActions(params: {
  courseCode?: string;
  limit?: number;
}): Promise<AiApiResponse<AiNextActionsVO>> {
  return axios.get(`${API}/learning/ai/next-actions`, { params });
}

export function createAiMaterial(body: {
  courseCode: string;
  title?: string;
  text?: string;
  contentMd?: string;
  nodeCodes?: string;
}): Promise<AiApiResponse<AiMaterialVO>> {
  return axios.post(`${API}/learning/ai/materials`, body, { timeout: 60000 });
}

export function listAiMaterials(
  courseCode?: string
): Promise<AiApiResponse<AiMaterialVO[]>> {
  return axios.get(`${API}/learning/ai/materials`, {
    params: courseCode ? { courseCode } : undefined,
  });
}

export function getAiMaterial(
  materialId: string
): Promise<AiApiResponse<AiMaterialDetailVO>> {
  return axios.get(
    `${API}/learning/ai/materials/${encodeURIComponent(materialId)}`
  );
}

export function deleteAiMaterial(
  materialId: string
): Promise<AiApiResponse<boolean>> {
  return axios.delete(
    `${API}/learning/ai/materials/${encodeURIComponent(materialId)}`
  );
}

export function rebuildCourseAiIndex(
  courseCode: string
): Promise<AiApiResponse<AiIndexReportVO>> {
  return axios.post(
    `${API}/learning/ai/index/rebuild`,
    { courseCode },
    { timeout: 120000 }
  );
}

export function rebuildPersonalAiIndex(
  courseCode?: string
): Promise<AiApiResponse<{ materials: number }>> {
  return axios.post(
    `${API}/learning/ai/index/rebuild-personal`,
    courseCode ? { courseCode } : {},
    { timeout: 120000 }
  );
}

export function listAiModelProfiles(): Promise<
  AiApiResponse<AiModelProfileVO[]>
> {
  return axios.get(`${API}/ai/model-profiles`);
}

export function createAiModelProfile(
  body: AiModelProfileCreateRequest
): Promise<AiApiResponse<{ profileId: string; status: string }>> {
  return axios.post(`${API}/ai/model-profiles`, body);
}

export function testAiModelProfile(
  profileId: string
): Promise<AiApiResponse<AiModelProfileTestVO>> {
  return axios.post(
    `${API}/ai/model-profiles/test`,
    { profileId },
    { timeout: 90000 }
  );
}

export function updateAiModelProfile(
  profileId: string,
  body: {
    active?: boolean;
    name?: string;
    serviceType?: string;
    baseUrl?: string;
    model?: string;
    apiKey?: string;
  }
): Promise<AiApiResponse<AiModelProfileVO>> {
  return axios.patch(
    `${API}/ai/model-profiles/${encodeURIComponent(profileId)}`,
    body
  );
}
