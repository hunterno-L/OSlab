import { ref } from "vue";
import { listCourses, type CourseSummary } from "@/api/course";

const KEY = "oslab:current-course";
const DEFAULT_COURSE = "course.operating-systems";
export const currentCourseCode = ref("");
let selectionSequence = 0;
let committedSequence = 0;
let latestResolution: Promise<CourseSummary | null> | null = null;
let latestResolutionSequence = 0;

function savedCourse(): string {
  try {
    return sessionStorage.getItem(KEY) || "";
  } catch {
    return "";
  }
}

/** Only commit a choice after validating it against the server's published list. */
export function selectAvailableCourse(
  courses: CourseSummary[],
  preferred?: string
): CourseSummary | null {
  selectionSequence += 1;
  return commitAvailableCourse(courses, preferred);
}

function commitAvailableCourse(courses: CourseSummary[], preferred?: string) {
  committedSequence = selectionSequence;
  const available = courses.filter((course) => course.publishedUnitCount > 0);
  const picked =
    available.find((course) => course.courseCode === preferred) ||
    available.find((course) => course.courseCode === currentCourseCode.value) ||
    available.find((course) => course.courseCode === savedCourse()) ||
    available.find((course) => course.courseCode === DEFAULT_COURSE) ||
    available[0] ||
    null;
  currentCourseCode.value = picked?.courseCode || "";
  try {
    if (picked) sessionStorage.setItem(KEY, picked.courseCode);
    else sessionStorage.removeItem(KEY);
  } catch {
    // The in-memory selection still works when storage is disabled.
  }
  return picked;
}

export function resolveCurrentCourse(
  preferred?: string,
  isCurrent: () => boolean = () => true
) {
  const sequence = ++selectionSequence;
  const request = (async () => {
    const courses = await listCourses();
    if (!isCurrent()) return null;
    if (sequence !== selectionSequence) {
      const newer = latestResolution;
      if (newer && latestResolutionSequence > sequence) {
        try {
          await newer;
        } catch {
          /* Resolve from this caller's list below. */
        }
      }
      if (!isCurrent()) return null;
      if (committedSequence < sequence)
        return commitAvailableCourse(courses, preferred);
      const selected = courses.find(
        (item) =>
          item.publishedUnitCount > 0 &&
          item.courseCode === currentCourseCode.value
      );
      if (selected) return selected;
      if (currentCourseCode.value) return null;
    }
    return commitAvailableCourse(courses, preferred);
  })();
  latestResolution = request;
  latestResolutionSequence = sequence;
  return request;
}
