import axios from "axios";

export type FollowUpKind = "DUE_REVIEW" | "WEAK" | "MISSING_EVIDENCE";
export interface TeacherFollowUp {
  className: string;
  courseCode: string;
  studentCount: number;
  dueReviewStudentCount: number;
  weakStudentCount: number;
  missingEvidenceStudentCount: number;
  items: {
    kind: FollowUpKind;
    nodeCode: string;
    nodeName: string;
    studentCount: number;
  }[];
}

export async function getTeacherFollowUps(
  className: string,
  courseCode: string
): Promise<TeacherFollowUp> {
  const response = await axios.get("/api/learning/teacher/follow-ups", {
    params: { className, courseCode },
  });
  if (response.data?.code !== 0 || !response.data.data)
    throw new Error(response.data?.message || "待跟进数据加载失败");
  return response.data.data as TeacherFollowUp;
}
