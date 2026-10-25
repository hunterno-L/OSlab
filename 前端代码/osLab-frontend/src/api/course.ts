import axios from "axios";
import type { BaseResponse } from "@/api/learning";

export interface CourseProgress {
  status: string;
  goalStatus: string;
  requiredCount: number;
  completedCount: number;
  percent: number;
}
export interface CourseSummary {
  courseCode: string;
  title: string;
  description?: string;
  status: string;
  publishedUnitCount: number;
  completedUnitCount: number;
  progressPercent: number;
}
export interface UnitSummary {
  unitCode: string;
  courseCode?: string;
  sectionCode: string;
  title: string;
  sortOrder?: number;
  estimatedMinutes?: number;
  nodeMappings?: {
    nodeCode: string;
    role: string;
    name?: string;
  }[];
  progress?: CourseProgress;
}
export interface CourseSection {
  sectionCode: string;
  parentSectionCode?: string;
  title: string;
  textbookNumber?: string;
  sectionType?: string;
  children?: CourseSection[];
  units?: UnitSummary[];
}
export interface CourseOutline {
  courseCode: string;
  title: string;
  sections: CourseSection[];
  progress?: CourseProgress;
}
export interface CourseActivity {
  activityCode: string;
  title: string;
  activityType: string;
  sortOrder: number;
  required: boolean;
  reference: { kind: string; code: string; questionId?: string };
  questionId?: string;
  config?: Record<string, unknown>;
  completionRule: string;
  completed?: boolean;
  passed?: boolean;
  content?: {
    chunkCode: string;
    title: string;
    contentMd: string;
    sourceRef?: Record<string, unknown>;
    revision?: number;
  };
  resource?: {
    resourceCode: string;
    algorithmKey?: string;
    adapterKey?: string;
    nodeCode?: string;
    nodeMappings?: { nodeCode: string }[];
    resourceConfig?: Record<string, unknown>;
    questionId?: string;
  };
}
export interface CourseUnit extends UnitSummary {
  currentActivityCode?: string;
  courseCode: string;
  objectives: string[];
  revision?: number;
  status?: string;
  nodeMappings: {
    nodeCode: string;
    name?: string;
    nodeName?: string;
    role: string;
  }[];
  /** 单元配套题关系：不表示必做活动，也不单独写进度/画像 */
  questionMappings?: {
    questionCode: string;
    usage: "PRACTICE" | "ASSESSMENT" | "REVIEW";
  }[];
  activities: CourseActivity[];
  progress: CourseProgress;
  previousUnitCode?: string;
  nextUnitCode?: string;
}
export interface ImportReport {
  valid: boolean;
  items: { type: string; code: string; action: string }[];
  errors: { path: string; message: string }[];
  created?: number;
  updated?: number;
  unchanged?: number;
}
export interface PackageUpload {
  package: Record<string, unknown>;
  assets: { path: string; contentBase64: string; contentType: string }[];
}
export async function courseRequest<T>(
  path: string,
  method = "get",
  data?: unknown
): Promise<T> {
  const response = await axios.request<BaseResponse<T>>({
    url: `/api/${path}`,
    method,
    data,
  });
  if (response.data.code !== 0)
    throw new Error(response.data.message || "请求失败");
  return response.data.data;
}
export const listCourses = () => courseRequest<CourseSummary[]>("courses");
export const getCourseOutline = (code: string) =>
  courseRequest<CourseOutline>(`courses/${encodeURIComponent(code)}/outline`);
export const getCourseResume = (code: string) =>
  courseRequest<{ unitCode?: string; activityCode?: string; reason: string }>(
    `courses/${encodeURIComponent(code)}/resume`
  );
export const getCourseUnit = (code: string) =>
  courseRequest<CourseUnit>(`courses/units/${encodeURIComponent(code)}`);
export const getNodeUnits = (code: string) =>
  courseRequest<(UnitSummary & { courseCode: string })[]>(
    `courses/nodes/${encodeURIComponent(code)}/units`
  );
export const completeCourseActivity = (
  activityCode: string,
  data: {
    sourceUnitCode: string;
    mode?: "MAINLINE" | "REVIEW";
    confirmed?: boolean;
    runToEnd?: boolean;
    attemptId?: string;
    submissionId?: string;
  }
) =>
  courseRequest<
    CourseProgress & {
      activityCode: string;
      completed: boolean;
      passed?: boolean;
    }
  >(
    `courses/activities/${encodeURIComponent(activityCode)}/complete`,
    "post",
    data
  );
export const unitPath = (courseCode: string, unitCode: string) =>
  `/courses/${encodeURIComponent(courseCode)}/units/${encodeURIComponent(
    unitCode
  )}`;
export const saveCoursePosition = (
  code: string,
  activityCode: string,
  mode: "MAINLINE" | "REVIEW"
) =>
  courseRequest(`courses/units/${encodeURIComponent(code)}/position`, "post", {
    activityCode,
    mode,
  });

/** Route context is accepted only when both codes exist. Independent resources remain independent. */
export function courseContext(query: Record<string, unknown>) {
  return typeof query.sourceUnitCode === "string" &&
    typeof query.sourceActivityCode === "string"
    ? {
        sourceUnitCode: query.sourceUnitCode,
        sourceActivityCode: query.sourceActivityCode,
      }
    : {};
}
