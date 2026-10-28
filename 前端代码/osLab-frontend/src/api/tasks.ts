import axios from "axios";
import type { BaseResponse } from "@/api/learning";

/**
 * 学习任务 API（教师 / 学生）。
 * 全部使用全局 axios 实例，沿用登录态、错误提示与 withCredentials。
 */

export interface TaskItemContent {
  title?: string;
  content?: string;
  contentMd?: string;
  type?: "JUDGE" | "SINGLE" | "MULTIPLE" | "FILL_BLANK";
  options?: { key: string; value: string }[];
  answer?: unknown;
  analysis?: string;
  misconception?: string;
  difficulty?: number;
  sourceMode?: "REFERENCE" | "AUTHORED";
  sourceQuestionId?: string;
  sourceQuestionCode?: string;
  sourceRevision?: number;
  sourceUpdatedAt?: number;
  nodeCodes?: string[];
  files?: {
    fileId: string;
    originalName: string;
    size?: number;
    ext?: string;
  }[];
  requireText?: boolean;
  requireFile?: boolean;
  requireConfirm?: boolean;
  textPrompt?: string;
  manualReview?: boolean;
  requirePass?: boolean;
  judgeCase?: { input: string; output: string }[];
  judgeConfig?: {
    timeLimit?: number;
    memoryLimit?: number;
    stackLimit?: number;
  };
  originalCode?: string;
  languages?: string[];
  samples?: { input: string; output: string }[];
  tags?: string[];
  [key: string]: unknown;
}

export interface TaskItem {
  itemCode: string;
  itemType: "TEXT" | "MATERIAL" | "QUESTION" | "EXPERIMENT";
  sourceMode?: string | null;
  sortOrder: number;
  required: boolean;
  maxScore?: number | null;
  runtimeResourceId?: string | null;
  content: TaskItemContent;
}

export interface TaskDetail {
  taskCode: string;
  title: string;
  description?: string;
  courseCode?: string | null;
  unitCode?: string | null;
  dueAt?: string | null;
  allowLate?: boolean;
  taskStatus: "DRAFT" | "PUBLISHED" | "CLOSED";
  status?: string;
  acceptedAt?: string | null;
  publishedAt?: string | null;
  supplements?: { content: string; createdAt?: string }[];
  overdue?: boolean;
  currentRound?: number;
  editableRound?: number;
  allowed?: Record<string, boolean>;
  items: TaskItem[];
  history?: any[];
  teacherName?: string;
  rowVersion?: number;
  audience?: { classNames?: string[]; userIds?: (string | number)[] };
  audienceSummary?: { validCount: number; students: any[]; excluded: any[] };
  [key: string]: any;
}

const base = "/api";

export const getMyTasks = (params: Record<string, unknown>) =>
  axios.get<BaseResponse<any>>(`${base}/learning/tasks`, { params });

export const getTaskSummary = () =>
  axios.get<BaseResponse<any>>(`${base}/learning/tasks/summary`);

export const getTaskDetail = (taskCode: string) =>
  axios.get<BaseResponse<TaskDetail>>(
    `${base}/learning/tasks/${encodeURIComponent(taskCode)}`
  );

export const acceptTask = (taskCode: string) =>
  axios.post<BaseResponse<TaskDetail>>(
    `${base}/learning/tasks/${encodeURIComponent(taskCode)}/accept`
  );

export const saveTaskWork = (
  taskCode: string,
  itemCode: string,
  payload: any
) =>
  axios.put<BaseResponse<any>>(
    `${base}/learning/tasks/${encodeURIComponent(
      taskCode
    )}/items/${encodeURIComponent(itemCode)}/work`,
    payload
  );

export const answerTaskQuestion = (
  taskCode: string,
  itemCode: string,
  payload: any
) =>
  axios.post<BaseResponse<any>>(
    `${base}/learning/tasks/${encodeURIComponent(
      taskCode
    )}/items/${encodeURIComponent(itemCode)}/answer`,
    payload
  );

export const submitTaskCode = (
  taskCode: string,
  itemCode: string,
  payload: any
) =>
  axios.post<BaseResponse<any>>(
    `${base}/learning/tasks/${encodeURIComponent(
      taskCode
    )}/items/${encodeURIComponent(itemCode)}/code-submissions`,
    payload
  );

export const getTaskCodeStatus = (
  taskCode: string,
  itemCode: string,
  id: string
) =>
  axios.get<BaseResponse<any>>(
    `${base}/learning/tasks/${encodeURIComponent(
      taskCode
    )}/items/${encodeURIComponent(itemCode)}/code-submissions/${id}`
  );

export const submitTask = (taskCode: string, payload: any) =>
  axios.post<BaseResponse<any>>(
    `${base}/learning/tasks/${encodeURIComponent(taskCode)}/submit`,
    payload
  );

export const getTaskHistory = (taskCode: string) =>
  axios.get<BaseResponse<any[]>>(
    `${base}/learning/tasks/${encodeURIComponent(taskCode)}/submissions`
  );

export const getTaskSubmission = (taskCode: string, submissionId: string) =>
  axios.get<BaseResponse<any>>(
    `${base}/learning/tasks/${encodeURIComponent(
      taskCode
    )}/submissions/${submissionId}`
  );

export const uploadTaskFile = (
  file: File,
  taskCode: string,
  itemCode: string,
  purpose: "TEACHER_MATERIAL" | "STUDENT_WORK"
) => {
  const form = new FormData();
  form.append("file", file);
  return axios.post<BaseResponse<any>>(`${base}/task-files`, form, {
    params: { taskCode, itemCode, purpose },
  });
};

export const deleteTaskFile = (fileId: string) =>
  axios.delete<BaseResponse<boolean>>(`${base}/task-files/${fileId}`);

// ---------------- 教师 ----------------

export const listTeachingTasks = (params: Record<string, unknown>) =>
  axios.get<BaseResponse<any>>(`${base}/teaching/tasks`, { params });

export const createTeachingTask = (payload: any) =>
  axios.post<BaseResponse<TaskDetail>>(`${base}/teaching/tasks`, payload);

export const getTeachingTask = (taskCode: string) =>
  axios.get<BaseResponse<TaskDetail>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}`
  );

export const saveTeachingTask = (taskCode: string, payload: any) =>
  axios.put<BaseResponse<TaskDetail>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}`,
    payload
  );

export const previewTeachingTask = (taskCode: string) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/preview`
  );

export const previewTaskAudience = (taskCode: string, payload: any) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/audience-preview`,
    payload
  );

export const publishTeachingTask = (taskCode: string, rowVersion?: number) =>
  axios.post<BaseResponse<TaskDetail>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/publish`,
    { rowVersion }
  );

export const updateTaskSettings = (taskCode: string, payload: any) =>
  axios.patch<BaseResponse<TaskDetail>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/settings`,
    payload
  );

export const addTaskRecipients = (taskCode: string, payload: any) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/recipients`,
    payload
  );

export const closeTeachingTask = (taskCode: string) =>
  axios.post<BaseResponse<TaskDetail>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/close`
  );

export const duplicateTeachingTask = (taskCode: string) =>
  axios.post<BaseResponse<TaskDetail>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/duplicate`
  );

export const archiveTeachingTask = (taskCode: string) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/archive`
  );

export const deleteTeachingTask = (taskCode: string) =>
  axios.delete<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}`
  );

export const listTaskRecipients = (
  taskCode: string,
  params: Record<string, unknown>
) =>
  axios.get<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(taskCode)}/recipients`,
    { params }
  );

export const getTeacherSubmission = (taskCode: string, submissionId: string) =>
  axios.get<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(
      taskCode
    )}/submissions/${submissionId}`
  );

export const reviewTaskSubmission = (
  taskCode: string,
  submissionId: string,
  payload: any
) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(
      taskCode
    )}/submissions/${submissionId}/review`,
    payload
  );

export const returnTaskSubmission = (
  taskCode: string,
  submissionId: string,
  payload: any
) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/tasks/${encodeURIComponent(
      taskCode
    )}/submissions/${submissionId}/return`,
    payload
  );

export const listTaskQuestions = (params: Record<string, unknown>) =>
  axios.get<BaseResponse<any>>(`${base}/teaching/tasks/resources/questions`, {
    params,
  });

export const getTaskQuestionResource = (id: string) =>
  axios.get<BaseResponse<TaskItemContent>>(
    `${base}/teaching/tasks/resources/questions/${encodeURIComponent(id)}`
  );

export const listTaskExperiments = (params: Record<string, unknown>) =>
  axios.get<BaseResponse<any>>(`${base}/teaching/tasks/resources/experiments`, {
    params,
  });

export const getTaskExperimentResource = (id: string) =>
  axios.get<BaseResponse<TaskItemContent>>(
    `${base}/teaching/tasks/resources/experiments/${encodeURIComponent(id)}`
  );

export const getTeacherClasses = () =>
  axios.get<BaseResponse<any[]>>(`${base}/learning/teacher/classes`);

export interface ManagedClass {
  id: number;
  classId?: number;
  className: string;
  classCode: string;
  description?: string;
  creatorId?: number;
  studentCount?: number;
  teacherName?: string;
  joinedAt?: string;
}

export interface ClassMember {
  userId: string;
  userName: string;
  userAccount: string;
  userAvatar?: string;
  joinedAt: string;
}

export const listManagedClasses = () =>
  axios.get<BaseResponse<ManagedClass[]>>(`${base}/teaching/classes`);

export const createManagedClass = (payload: {
  className: string;
  description?: string;
}) =>
  axios.post<BaseResponse<ManagedClass>>(`${base}/teaching/classes`, payload);

export const updateManagedClass = (
  classId: number,
  payload: { className: string; description?: string }
) =>
  axios.patch<BaseResponse<ManagedClass>>(
    `${base}/teaching/classes/${classId}`,
    payload
  );

export const rotateManagedClassCode = (classId: number) =>
  axios.post<BaseResponse<ManagedClass>>(
    `${base}/teaching/classes/${classId}/rotate-code`
  );

export const deleteManagedClass = (classId: number) =>
  axios.delete<BaseResponse<boolean>>(`${base}/teaching/classes/${classId}`);

export const listManagedClassMembers = (classId: number) =>
  axios.get<BaseResponse<ClassMember[]>>(
    `${base}/teaching/classes/${classId}/members`
  );

export const removeManagedClassMember = (
  classId: number,
  userId: string | number
) =>
  axios.delete<BaseResponse<boolean>>(
    `${base}/teaching/classes/${classId}/members/${userId}`
  );

export const listMyClasses = () =>
  axios.get<BaseResponse<ManagedClass[]>>(`${base}/classes/mine`);

export const joinManagedClass = (classCode: string) =>
  axios.post<BaseResponse<ManagedClass>>(`${base}/classes/join`, { classCode });

export const leaveManagedClass = (classId: number) =>
  axios.post<BaseResponse<boolean>>(`${base}/classes/${classId}/leave`);

export const listStudents = (payload: Record<string, unknown>) =>
  axios.post<BaseResponse<any>>(`${base}/student/list/page`, payload);

// ---------------- 内容维护 ----------------

export const createExperimentDraft = (payload: any) =>
  axios.post<BaseResponse<any>>(`${base}/teaching/experiments`, payload);

export const getExperimentDraft = (id: string) =>
  axios.get<BaseResponse<any>>(
    `${base}/teaching/experiments/${encodeURIComponent(id)}`
  );

export const saveExperimentDraft = (id: string, payload: any) =>
  axios.put<BaseResponse<any>>(
    `${base}/teaching/experiments/${encodeURIComponent(id)}`,
    payload
  );

export const applyExperimentDraft = (id: string) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/experiments/${encodeURIComponent(id)}/apply`
  );

export const publishExperimentDraft = (id: string) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/experiments/${encodeURIComponent(id)}/publish`
  );

export const offlineExperiment = (id: string) =>
  axios.post<BaseResponse<any>>(
    `${base}/teaching/experiments/${encodeURIComponent(id)}/offline`
  );

export const getNodeDetail = (nodeCode: string) =>
  axios.get<BaseResponse<any>>(
    `${base}/teaching/content/nodes/${encodeURIComponent(nodeCode)}`
  );

export const saveNodeRelations = (nodeCode: string, relations: any[]) =>
  axios.put<BaseResponse<any>>(
    `${base}/teaching/content/nodes/${encodeURIComponent(nodeCode)}/relations`,
    { relations }
  );

export const getTeachingCatalog = () =>
  axios.get<BaseResponse<any>>(`${base}/teaching/content/catalog`);
