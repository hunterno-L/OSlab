import axios, { type AxiosResponse } from "axios";

/**
 * 知识图谱智能学习闭环 API（契约 §3.5，/learning/*）
 *
 * 直接使用全局 axios 默认实例：plugins/axios.ts 的拦截器（未登录跳转、
 * 错误提示、withCredentials）对 generated 客户端与本模块同时生效。
 * 路径带 /api 前缀，与 generated/services 的约定一致。
 */

// ========== 基础类型（与后端 BaseResponse / VO 对应） ==========

export interface BaseResponse<T> {
  code: number;
  data: T;
  message: string;
}

export type LearningApiResponse<T> = AxiosResponse<BaseResponse<T>>;

function invalidateWorkbenchAfterSuccess<T>(response: LearningApiResponse<T>) {
  if (response.data.code === 0 && typeof window !== "undefined") {
    window.dispatchEvent(new Event("oslab:learning-changed"));
  }
  return response;
}

export type LearningQuestionType =
  | "JUDGE"
  | "SINGLE"
  | "MULTIPLE"
  | "FILL_BLANK";

export type LearningReviewStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "APPROVED"
  | "REJECTED"
  | "PUBLISHED"
  | "OFFLINE";

export type MasteryStatus = "GRAY" | "RED" | "YELLOW" | "GREEN";

export interface QuestionOption {
  key: string;
  value: string;
}

export interface MasteryChangeVO {
  nodeName: string;
  masteryBefore: number | null;
  masteryAfter: number | null;
  status: MasteryStatus;
  countedAsObservation: boolean;
}

export interface LearningQuestionVO {
  id: number;
  title: string;
  content: string;
  type: LearningQuestionType;
  /** JSON 数组字符串：[{"key":"A","value":"..."}]，仅 SINGLE/MULTIPLE */
  options: string | null;
  chapter: string | null;
  difficulty: number;
  reviewStatus: LearningReviewStatus;
  userId: number;
  authorName?: string | null;
  createTime?: string;
  updateTime?: string;
  nodeNames: string[];
  nodeDisplayNames?: string[];
  hasAttempted: boolean;
  attemptCount?: number;
  latestResult?: "CORRECT" | "WRONG" | null;
  firstResult?: "CORRECT" | "WRONG" | null;
  /** 测评会话占位：题目已下线时为 true（不可作答，从有效分母扣除） */
  offline?: boolean;
  /** 仅本人已作答或管理视角后下发 */
  analysis: string | null;
  misconception: string | null;
  /** 仅作者/管理员视角下发；学生视图恒为 null */
  correctAnswer: string | null;
}

export interface LearningAnswerResultVO {
  attemptId: number;
  correct: boolean;
  analysis: string | null;
  misconception: string | null;
  isFirst: boolean;
  masteryChanges: MasteryChangeVO[];
}

export interface MasteryNodeVO {
  displayName?: string;
  nodeType?: string;
  layer?: string;
  homeSectionCode?: string;
  nodeCode?: string;
  id: string;
  name: string;
  category: string;
  chapter?: string | null;
  description?: string | null;
  pKnown: number | null;
  confidence: number | null;
  conceptScore: number | null;
  practiceScore: number | null;
  conceptEvidenceCount: number;
  practiceEvidenceCount: number;
  status: MasteryStatus;
  recentReason?: string | null;
  lastEventId?: number | null;
  lastEventType?: "ATTEMPT" | "JUDGE" | "SIMULATION" | "STATE_BASELINE" | null;
  lastResult?: "PASS" | "FAIL" | "SYSTEM_ERROR" | "BASELINE" | null;
  /** 回放专用：true=有完整快照；false=该时点只有历史事件（细分状态未记录）；null=非回放/未学习 */
  snapshotAvailable?: boolean | null;
}

export interface GraphLinkDTO {
  source: string;
  target: string;
  value?: string;
  relationType?: string;
}

export interface CategoryDTO {
  name: string;
}

export interface ChapterSummaryVO {
  name: string;
  count: number;
}

export interface PersonalGraphVO {
  nodes: MasteryNodeVO[];
  links: GraphLinkDTO[];
  categories: CategoryDTO[];
  totalNodes?: number;
  totalLinks?: number;
  evidenceCount?: number;
  statusCounts?: Record<MasteryStatus, number>;
  chapters?: ChapterSummaryVO[];
  lastUpdatedAt?: string | null;
  recommendedNodeName?: string | null;
}

export interface LearningEventVO {
  id: number;
  userId: number;
  anonymousName?: string | null;
  nodeName: string;
  nodeCode?: string | null;
  sourceUnitCode?: string | null;
  sourceActivityCode?: string | null;
  sourceTaskCode?: string | null;
  sourceTaskItemCode?: string | null;
  sourceTaskRound?: number | null;
  eventType: "ATTEMPT" | "JUDGE" | "SIMULATION" | "STATE_BASELINE";
  resourceType: string;
  resourceId: number;
  result: "PASS" | "FAIL" | "SYSTEM_ERROR" | "BASELINE";
  isFirst: boolean;
  masteryBefore: number | null;
  masteryAfter: number | null;
  evidence: string | null;
  /** 事件组键：同一学习事务影响多节点时共享（attempt:{id} 等） */
  eventGroupKey?: string | null;
  createTime: string;
}

export interface RecommendationVO {
  nodeName: string;
  type: string;
  reason: string;
  score: number;
}

export interface NodeRefVO {
  name: string;
  nodeCode?: string;
  displayName?: string;
  status: MasteryStatus;
  chapter?: string | null;
}

export interface QuestionStatsVO {
  total: number;
  attempted: number;
  unattempted: number;
  byType?: Record<string, number>;
}

export interface ExperimentSummaryVO {
  id: number;
  title: string;
  lastResult?: string | null;
}

export interface LastFailureVO {
  resourceType: string;
  resourceId: number;
  result: "PASS" | "FAIL" | "SYSTEM_ERROR" | "BASELINE";
  evidence?: string | null;
  createTime?: string | null;
  // ---- Phase 7 §11.3 归因呈现 ----
  errorType?: string | null;
  confidence?: number | null;
  recommendedAction?: string | null;
  knowledgeImpact?: boolean | null;
  /** CURRENT 当前失误 / HISTORICAL 历史失误 / CORRECTED 已纠正 / NONE 无失败 */
  failureState?: "CURRENT" | "HISTORICAL" | "CORRECTED" | "NONE";
  /** 依据强度标签（不包装成校准概率） */
  certaintyLabel?: string | null;
  attributionBasis?: string[] | null;
}

export interface SimulationEntryVO {
  nodeName: string;
  algorithms: string[];
}

export interface NodeWorkbenchVO {
  textbookChunks?: {
    chunkCode: string;
    title: string;
    contentMd: string;
    sourceRef?: Record<string, unknown>;
    revision?: number;
    sourceSectionTitle?: string;
  }[];
  learningUnits?: { unitCode: string; courseCode: string; title: string }[];
  mastery: MasteryNodeVO;
  prerequisiteNodes: string[];
  prerequisites?: NodeRefVO[];
  successors?: NodeRefVO[];
  questions: LearningQuestionVO[];
  events: LearningEventVO[];
  recommendations: RecommendationVO[];
  questionStats?: QuestionStatsVO;
  misconceptions?: string[];
  experiments?: ExperimentSummaryVO[];
  lastFailure?: LastFailureVO | null;
  simulationEntry?: SimulationEntryVO | null;
  /** 共享学习素材正文（仅 PUBLISHED；null 时页面回落动态模板） */
  contentMd?: string | null;
  /** 共享素材关联资料 JSON 数组 [{title,url}] */
  resourceLinks?: string | null;
}

export interface PageStep {
  step: number;
  page: number;
  frames: number[];
  fault: boolean;
  evicted: number | null;
}

export interface PagePredictionResult {
  faultCorrect: boolean;
  evictedCorrect: boolean;
  actualFault: boolean;
  actualEvicted: number | null;
  eventId: number | null;
}

export interface SimulationPageVO {
  steps: PageStep[];
  pageFaults: number;
  hitRate: number;
  prediction: PagePredictionResult | null;
}

export interface CpuSegment {
  process: string;
  start: number;
  end: number;
}

export interface CpuProcessStat {
  name: string;
  arrivalTime: number;
  burstTime: number;
  startTime: number;
  completionTime: number;
  waitingTime: number;
  turnaroundTime: number;
}

export interface CpuPredictionResult {
  correct: boolean;
  expectedProcess: string;
  predictedProcess: string;
  eventId: number | null;
}

export interface SimulationCpuVO {
  gantt: CpuSegment[];
  processStats: CpuProcessStat[];
  avgWaitingTime: number;
  avgTurnaroundTime: number;
  prediction: CpuPredictionResult | null;
}

export interface NodeHeatVO {
  nodeName: string;
  studentCount: number;
  avgPKnown: number;
  weakCount: number;
}

export interface QuestionAccuracyVO {
  questionId: number;
  title: string;
  type: LearningQuestionType;
  attemptCount: number;
  firstCorrectCount: number;
  correctRate: number;
}

export interface TeacherDashboardVO {
  className: string;
  studentCount: number;
  nodeHeat: NodeHeatVO[];
  weakTop: NodeHeatVO[];
  questionAccuracy: QuestionAccuracyVO[];
  /** 实验有效通过率（按提交去重、排除系统故障）；无样本为 null */
  experimentPassRate: number | null;
  recentEvents: LearningEventVO[];
}

export interface TimelineVO {
  total: number;
  events: LearningEventVO[];
}

// ---------- 教学管理（Phase 8 §12） ----------

export interface ClassOptionVO {
  className: string;
  studentCount: number;
}

export interface NodeDrillQuestionVO {
  questionId: number;
  title: string;
  type: LearningQuestionType;
  reviewStatus: string;
  attemptCount: number;
  firstAttemptCount: number;
  firstCorrectCount: number;
  /** 首次正确率；无首次作答样本为 null */
  firstCorrectRate: number | null;
}

export interface NodeDrillExperimentVO {
  questionId: number;
  title: string;
  submitCount: number;
  passCount: number;
  /** 通过率；无有效提交为 null */
  passRate: number | null;
}

export interface MisconceptionStatVO {
  misconception: string;
  wrongCount: number;
}

export interface NodeDrillVO {
  className: string;
  nodeName: string;
  studentCount: number;
  evidenceCount: number;
  coverage: number;
  weakCount: number;
  avgPKnown: number | null;
  /** 首次测评正确率（分母=首次有效作答数）；无样本为 null */
  firstAccuracy: number | null;
  /** 实验有效通过率；无样本为 null */
  experimentPassRate: number | null;
  questions: NodeDrillQuestionVO[];
  experiments: NodeDrillExperimentVO[];
  misconceptionStats: MisconceptionStatVO[];
}

export interface ChapterCoverageVO {
  chapter: string;
  nodeCount: number;
  publishedContentCount: number;
  assessmentCoveredCount: number;
  experimentCoveredCount: number;
  uncoveredNodes: string[];
  uncoveredCount: number;
}

export interface TutorSourceVO {
  actionType?: string;
  targetId?: string;
  targetUrl?: string;
  parentChunkCode?: string;
  revision?: number;
  sourceRef?: Record<string, unknown>;
  sourceSectionTitle?: string;
  sourceType: string;
  sourceId: string;
  title: string;
  snippet?: string;
}

export interface TutorAnswerVO {
  mode: "AI" | "FALLBACK";
  answer: string;
  sources: TutorSourceVO[];
  retrievalTrace: string[];
}

// ========== 请求类型 ==========

export interface LearningQuestionQueryRequest {
  chapter?: string;
  nodeName?: string;
  courseCode?: string;
  sectionCode?: string;
  unitCode?: string;
  nodeCode?: string;
  type?: LearningQuestionType | "";
  difficulty?: number;
  studyState?: "ALL" | "UNANSWERED" | "NEEDS_REVIEW" | "CORRECT";
  title?: string;
  current?: number;
  pageSize?: number;
  /** 管理端：只看自己的题目 */
  myOnly?: boolean;
  /** 管理端：审核队列 */
  reviewQueue?: boolean;
  reviewStatus?: LearningReviewStatus | "";
}

export interface LearningQuestionAddRequest {
  title: string;
  content: string;
  type: LearningQuestionType;
  options?: QuestionOption[];
  answer: string | string[] | boolean;
  analysis?: string;
  misconception?: string;
  chapter?: string;
  difficulty?: number;
  nodeNames: string[];
}

export interface LearningQuestionUpdateRequest
  extends LearningQuestionAddRequest {
  id: number;
}

// ========== 题目 ==========

export function listLearningQuestions(
  params: LearningQuestionQueryRequest
): Promise<
  LearningApiResponse<{
    records: LearningQuestionVO[];
    total: number;
    current: number;
    size: number;
  }>
> {
  return axios.post("/api/learning/questions/list/page", params);
}

export function getLearningQuestion(
  questionId: number | string
): Promise<LearningApiResponse<LearningQuestionVO>> {
  return axios.get("/api/learning/questions/get", { params: { questionId } });
}

export function answerLearningQuestion(data: {
  questionId: number | string;
  /** JUDGE: "true"/"false"；SINGLE: "A"；MULTIPLE: ["A","C"]；FILL_BLANK: string */
  answer: string | string[];
  /** 所属测评会话 id（测评流程作答时携带） */
  assessmentRunId?: number | string;
  sourceUnitCode?: string;
  sourceActivityCode?: string;
  /** 提交幂等键（同键重发返回既有结果；主动重试使用新键） */
  submissionKey?: string;
}): Promise<LearningApiResponse<LearningAnswerResultVO>> {
  return axios
    .post<BaseResponse<LearningAnswerResultVO>>(
      "/api/learning/questions/answer",
      data
    )
    .then((response) =>
      invalidateWorkbenchAfterSuccess<LearningAnswerResultVO>(response)
    );
}

// ========== 题目练习目录（阶段 4：章节 -> 知识点聚合） ==========

/** 知识点聚合（章节内局部口径；attempted/needsReview/correct 与 studyState 的"本人最新有效作答"同义） */
export interface LearningCatalogNodeVO {
  nodeCode?: string | null;
  nodeName: string;
  displayName?: string | null;
  /** 该章节内映射到该知识点的去重题目数（按 questionId） */
  questionCount: number;
  /** 本人已有作答的题目数；未作答 = questionCount - attempted（后端不下发） */
  attempted: number;
  /** 本人最新一次作答错误的题目数 */
  needsReview: number;
  /** 本人最新一次作答正确的题目数 */
  correct: number;
}

export interface LearningCatalogChapterVO {
  /** 章节名（question.chapter 原值；章节信息缺失归入"未归类"且恒排最后） */
  chapter: string;
  /** 该章节内去重题目数（按 questionId；一题映射多节点只计一次） */
  questionCount: number;
  nodes: LearningCatalogNodeVO[];
}

export interface LearningCatalogUnitVO {
  unitCode: string;
  sectionCode: string;
  title: string;
  sortOrder?: number;
  questionCount: number;
}

export interface LearningCatalogSectionVO {
  sectionCode: string;
  parentSectionCode?: string | null;
  title: string;
  textbookNumber?: string | null;
  sectionType?: string | null;
  sortOrder?: number;
  questionCount: number;
  units: LearningCatalogUnitVO[];
  children: LearningCatalogSectionVO[];
}

export interface LearningCatalogVO {
  /** 去重后的题目总数（当前教材范围 + 学习状态过滤后的口径） */
  totalQuestions: number;
  courseCode?: string | null;
  /** 旧章节聚合（兼容旧入口；新学生目录使用 sections/units） */
  chapters: LearningCatalogChapterVO[];
  /** 教材小节树（稳定 sectionCode） */
  sections?: LearningCatalogSectionVO[];
  /** 当前范围内的知识节点 */
  nodes?: LearningCatalogNodeVO[];
  attempted?: number;
  needsReview?: number;
  correct?: number;
}

/** 题目练习目录聚合：目录范围与列表共用稳定代码谓词（仅 PUBLISHED 且未删除） */
export function getLearningCatalog(params?: {
  chapter?: string;
  courseCode?: string;
  sectionCode?: string;
  unitCode?: string;
  studyState?: string;
}): Promise<LearningApiResponse<LearningCatalogVO>> {
  return axios.post("/api/learning/questions/catalog", params || {});
}

// ========== 测评会话（Phase 4 §8） ==========

export interface AssessmentRunVO {
  runId: string;
  status: "ACTIVE" | "FINISHED";
  questions: LearningQuestionVO[];
}

export interface AssessmentStartResult {
  runId: string;
  questionIds: string[];
}

export interface AssessmentFinishResult {
  runId: string;
  totalQuestions: number;
  answeredQuestions: number;
  firstCorrect: number;
}

export function startAssessment(data: {
  chapter?: string;
  nodeName?: string;
  courseCode?: string;
  sectionCode?: string;
  unitCode?: string;
  nodeCode?: string;
  type?: string;
  difficulty?: number;
  studyState?: string;
  count: number;
}): Promise<LearningApiResponse<AssessmentStartResult>> {
  return axios.post("/api/learning/assessment/start", data);
}

export function getAssessmentRun(
  runId: number | string
): Promise<LearningApiResponse<AssessmentRunVO>> {
  return axios.get("/api/learning/assessment/run", { params: { runId } });
}

export function finishAssessment(data: {
  runId: string;
  answeredCount?: number;
  firstCorrect?: number;
}): Promise<LearningApiResponse<AssessmentFinishResult>> {
  return axios.post("/api/learning/assessment/finish", data);
}

export function addLearningQuestion(
  data: LearningQuestionAddRequest
): Promise<LearningApiResponse<number>> {
  return axios.post("/api/learning/questions/add", data);
}

export function updateLearningQuestion(
  data: LearningQuestionUpdateRequest
): Promise<LearningApiResponse<boolean>> {
  return axios.post("/api/learning/questions/update", data);
}

export interface ReviewRecordVO {
  reviewerId: number;
  reviewerName?: string;
  action: string;
  reason?: string | null;
  createTime?: string;
}

/** 管理员题面/编辑 VO：学生 VO + 标准答案 + 审核历史（仅 ADMIN 端点返回） */
export interface LearningQuestionManageVO
  extends Omit<
    LearningQuestionVO,
    "attemptCount" | "latestResult" | "firstResult"
  > {
  correctAnswer: string;
  reviewHistory?: ReviewRecordVO[];
}

export function getManageQuestion(
  questionId: number | string
): Promise<LearningApiResponse<LearningQuestionManageVO>> {
  return axios.get("/api/learning/questions/manage/get", {
    params: { questionId },
  });
}

export function pageManageQuestions(
  params: LearningQuestionQueryRequest
): Promise<
  LearningApiResponse<{
    records: LearningQuestionManageVO[];
    total: number;
    current: number;
    size: number;
  }>
> {
  return axios.post("/api/learning/questions/manage/list/page", params);
}

export function reviewLearningQuestion(data: {
  questionId: number | string;
  action: "SUBMIT" | "APPROVE" | "REJECT" | "PUBLISH" | "OFFLINE";
  reason?: string;
}): Promise<LearningApiResponse<boolean>> {
  return axios.post("/api/learning/questions/review", data);
}

// ========== 个人图谱与掌握度 ==========

export function personalGraph(): Promise<LearningApiResponse<PersonalGraphVO>> {
  return axios.get("/api/learning/mastery/graph");
}

// ---------- 补救学习路径（Phase 6 §10 / §6.3） ----------

export type PathActionKind =
  | "NODE"
  | "ASSESSMENT"
  | "EXPERIMENT"
  | "SIMULATION";

export interface LearningActionVO {
  kind: PathActionKind;
  nodeName: string;
  resourceId?: number | null;
  algorithms?: string[] | null;
  chapter?: string | null;
}

export interface PathItemVO {
  nodeName: string;
  order: number;
  status: string;
  reason: string;
  prerequisites: string[];
  action: LearningActionVO;
}

export interface LearningPathVO {
  targetNodeName: string;
  basisEventIds: number[];
  items: PathItemVO[];
  cycleNodes: string[];
}

export function masteryPath(
  targetNodeName: string
): Promise<LearningApiResponse<LearningPathVO>> {
  return axios.get("/api/learning/mastery/path", {
    params: { targetNodeName },
  });
}

export function nodeWorkbench(
  nodeName: string
): Promise<LearningApiResponse<NodeWorkbenchVO>> {
  return axios.get("/api/learning/mastery/node", {
    params: nodeName.startsWith("os.") ? { nodeCode: nodeName } : { nodeName },
  });
}

export function masteryRecommend(): Promise<
  LearningApiResponse<RecommendationVO[]>
> {
  return axios.get("/api/learning/mastery/recommend");
}

// ========== 学习记录（Phase 7 §11） ==========

/** 历史快照回放：按某事件时点生成的图谱投影 */
export interface MasterySnapshotVO {
  atEventId: number;
  replayAt: string;
  eventGroupKey?: string | null;
  graph: PersonalGraphVO;
}

/** 待巩固节点：薄弱节点 + 失败归类 */
export interface WeakNodeVO {
  nodeName: string;
  chapter?: string | null;
  status: MasteryStatus;
  pKnown: number | null;
  conceptScore?: number | null;
  practiceScore?: number | null;
  conceptEvidenceCount?: number;
  practiceEvidenceCount?: number;
  lastActivityTime?: string | null;
  failureState: "CURRENT" | "HISTORICAL" | "CORRECTED" | "NONE";
  lastFailure?: LastFailureVO | null;
}

/** 活动中的一个知识节点证据 */
export interface ActivityNodeRefVO {
  nodeCode?: string | null;
  nodeName: string;
  masteryBefore?: number | null;
  masteryAfter?: number | null;
  result?: string | null;
  independent?: boolean;
  eventId?: number | string | null;
}

/** 一次学习活动（时间线主单位） */
export interface LearningActivityVO {
  activityId: string;
  activityType: string;
  title: string;
  status?: string | null;
  result?: string | null;
  occurredAt: string;
  courseCode?: string | null;
  unitCode?: string | null;
  activityCode?: string | null;
  taskCode?: string | null;
  taskItemCode?: string | null;
  roundNo?: number | null;
  resourceType?: string | null;
  resourceId?: number | string | null;
  algorithmKey?: string | null;
  summary?: string | null;
  inputSnapshot?: string | null;
  resultSnapshot?: string | null;
  nodeRefs?: ActivityNodeRefVO[];
  evidenceRefs?: (number | string)[];
  replayEventId?: number | string | null;
  returnRoute?: string | null;
}

export interface LearningActivityPageVO {
  total: number;
  records: LearningActivityVO[];
}

export interface SimulationRunPayload {
  algorithmKey: string;
  title?: string;
  courseCode?: string | null;
  unitCode?: string | null;
  activityCode?: string | null;
  input?: Record<string, unknown> | null;
  resultSummary?: Record<string, unknown> | null;
}

/** 按“一次学习活动”分页的记录查询；服务端只查当前登录用户 */
export function learningActivities(params: {
  type?: string;
  courseCode?: string;
  unitCode?: string;
  nodeCode?: string;
  startTime?: string;
  endTime?: string;
  current?: number;
  pageSize?: number;
}): Promise<LearningApiResponse<LearningActivityPageVO>> {
  return axios.get("/api/learning/records/activities", { params });
}

/** 记录普通算法演示的一次完整运行（不写掌握证据） */
export function recordSimulationRun(
  payload: SimulationRunPayload
): Promise<LearningApiResponse<number>> {
  return axios.post("/api/learning/records/simulations", payload);
}

/** 本人学习时间线（只查当前登录用户；时间格式 yyyy-MM-dd HH:mm:ss） */
export function masteryTimeline(params: {
  nodeName?: string;
  eventType?: string;
  startTime?: string;
  endTime?: string;
  current?: number;
  pageSize?: number;
}): Promise<LearningApiResponse<TimelineVO>> {
  return axios.get("/api/learning/mastery/timeline", { params });
}

/** 历史快照回放（只读，不写事件不重算掌握度） */
export function masterySnapshot(
  atEventId: number | string
): Promise<LearningApiResponse<MasterySnapshotVO>> {
  return axios.get("/api/learning/mastery/snapshot", {
    params: { atEventId },
  });
}

/** 待巩固节点列表 */
export function masteryWeakness(): Promise<LearningApiResponse<WeakNodeVO[]>> {
  return axios.get("/api/learning/mastery/weakness");
}

// ========== GraphRAG 节点导师 ==========

export function tutorAsk(data: {
  nodeName?: string;
  nodeCode?: string;
  question: string;
}): Promise<LearningApiResponse<TutorAnswerVO>> {
  return axios.post("/api/learning/tutor/ask", data, { timeout: 45000 });
}

// ========== 教师（教学管理） ==========

export function teacherDashboard(
  className: string
): Promise<LearningApiResponse<TeacherDashboardVO>> {
  return axios.get("/api/learning/teacher/dashboard", {
    params: { className },
  });
}

export function teacherTimeline(params: {
  className: string;
  userId?: number;
  nodeName?: string;
  eventType?: string;
  current?: number;
  pageSize?: number;
}): Promise<LearningApiResponse<TimelineVO>> {
  return axios.get("/api/learning/teacher/timeline", { params });
}

/** 真实可选班级列表（students 表聚合） */
export function teacherClasses(): Promise<
  LearningApiResponse<ClassOptionVO[]>
> {
  return axios.get("/api/learning/teacher/classes");
}

/** 节点下钻（映射表/标签精确关联） */
export function teacherNodeDetail(
  className: string,
  nodeName: string
): Promise<LearningApiResponse<NodeDrillVO>> {
  return axios.get("/api/learning/teacher/node/detail", {
    params: { className, nodeName },
  });
}

/** 章节资源完整度 */
export function teacherChapterCoverage(): Promise<
  LearningApiResponse<ChapterCoverageVO[]>
> {
  return axios.get("/api/learning/teacher/chapters/coverage");
}

// ---------- 测评题 JSON 导入（预览 → 确认导入草稿） ----------

export interface ImportItemVO {
  index: number;
  valid: boolean;
  error?: string | null;
  title?: string;
  type?: string;
  chapter?: string;
  nodeNames?: string[];
}

export interface ImportPreviewVO {
  total: number;
  validCount: number;
  invalidCount: number;
  items: ImportItemVO[];
}

export interface ImportResultVO {
  createdCount: number;
  skippedCount: number;
  questionIds: number[];
}

export function importLearningQuestionsPreview(
  content: string
): Promise<LearningApiResponse<ImportPreviewVO>> {
  return axios.post("/api/learning/questions/import/preview", { content });
}

export function importLearningQuestionsCommit(
  content: string
): Promise<LearningApiResponse<ImportResultVO>> {
  return axios.post("/api/learning/questions/import/commit", { content });
}

// ---------- 节点共享学习素材（知识维护） ----------

export interface LearningNodeContentVO {
  id: number;
  nodeName: string;
  contentMd: string | null;
  resourceLinks: string | null;
  status: "DRAFT" | "PUBLISHED" | "OFFLINE";
  updateTime: string;
}

export function getNodeContent(
  nodeName: string
): Promise<LearningApiResponse<LearningNodeContentVO | null>> {
  return axios.get("/api/learning/nodeContent/get", {
    params: { nodeName },
  });
}

export function listNodeContents(): Promise<
  LearningApiResponse<LearningNodeContentVO[]>
> {
  return axios.get("/api/learning/nodeContent/list");
}

export function saveNodeContent(data: {
  nodeName: string;
  contentMd?: string | null;
  resourceLinks?: string | null;
  status: "DRAFT" | "PUBLISHED" | "OFFLINE";
}): Promise<LearningApiResponse<boolean>> {
  return axios.post("/api/learning/nodeContent/save", data);
}

// ========== 模拟器 ==========

export interface CpuProcessInput {
  name: string;
  arrivalTime: number;
  burstTime: number;
}

export function runCpuSimulation(data: {
  sourceUnitCode?: string;
  sourceActivityCode?: string;
  nodeName?: string;
  algorithm: "FCFS" | "SJF" | "RR";
  timeSlice?: number;
  processes: CpuProcessInput[];
  /** 预测模式：第 predictIndex 个时刻应运行的进程 */
  predictIndex?: number;
  predictedProcess?: string;
}): Promise<LearningApiResponse<SimulationCpuVO>> {
  return axios
    .post<BaseResponse<SimulationCpuVO>>("/api/learning/simulation/cpu", data)
    .then((response) =>
      invalidateWorkbenchAfterSuccess<SimulationCpuVO>(response)
    );
}

export function runPageSimulation(data: {
  sourceUnitCode?: string;
  sourceActivityCode?: string;
  nodeName?: string;
  algorithm: "FIFO" | "LRU" | "OPT";
  frames: number;
  accessSequence: number[];
  /** 预测模式：第 predictStep 步是否缺页与淘汰页（OPT 仅判分，不计入学习事件） */
  predictStep?: number;
  predictedFault?: boolean;
  predictedEvicted?: number;
}): Promise<LearningApiResponse<SimulationPageVO>> {
  return axios
    .post<BaseResponse<SimulationPageVO>>("/api/learning/simulation/page", data)
    .then((response) =>
      invalidateWorkbenchAfterSuccess<SimulationPageVO>(response)
    );
}
