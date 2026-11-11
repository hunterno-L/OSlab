import { computed, getCurrentScope, onScopeDispose, ref } from "vue";
import { selectAvailableCourse } from "@/composables/useCurrentCourse";
import {
  getCourseOutline,
  getCourseResume,
  listCourses,
  type CourseOutline,
  type CourseProgress,
  type CourseSection,
  type CourseSummary,
  type UnitSummary,
} from "@/api/course";

export interface CourseResume {
  unitCode?: string;
  activityCode?: string;
  reason: string;
}

export interface CourseCatalogGroup {
  courseCode: string;
  title: string;
  publishedUnitCount: number;
  completedUnitCount: number;
  progressPercent: number;
  sections: CourseSection[];
  progress: CourseProgress | null;
}

/** 课程目录的共享只读视图：学生只看到 outline 返回的已发布单元。 */
export function useCourseCatalog() {
  const courses = ref<CourseSummary[]>([]);
  const courseCode = ref("");
  const outline = ref<CourseOutline | null>(null);
  const resume = ref<CourseResume | null>(null);
  const loading = ref(false);
  const error = ref("");
  const outlines = ref<Record<string, CourseOutline>>({});
  const outlineLoading = ref<Record<string, boolean>>({});
  const outlineErrors = ref<Record<string, string>>({});
  let epoch = 0;
  if (getCurrentScope())
    onScopeDispose(() => {
      epoch += 1;
    });

  /**
   * 侧栏必须来自全部已发布课程，而不是当前课程的一份 outline；
   * 否则切换课程后其他课程会从目录里“消失”。
   */
  const publishedCourses = computed(() =>
    courses.value.filter((item) => item.publishedUnitCount > 0)
  );

  const courseGroups = computed<CourseCatalogGroup[]>(() =>
    publishedCourses.value.map((item) => ({
      courseCode: item.courseCode,
      title: item.title,
      publishedUnitCount: item.publishedUnitCount,
      completedUnitCount: item.completedUnitCount,
      progressPercent: item.progressPercent,
      sections: outlines.value[item.courseCode]?.sections || [],
      progress: outlines.value[item.courseCode]?.progress || null,
    }))
  );

  async function fetchOutline(code: string, request: number) {
    outlineLoading.value = { ...outlineLoading.value, [code]: true };
    outlineErrors.value = { ...outlineErrors.value, [code]: "" };
    try {
      const data = await getCourseOutline(code);
      if (request !== epoch) return null;
      outlines.value = { ...outlines.value, [code]: data };
      return data;
    } catch {
      if (request === epoch) {
        outlineErrors.value = {
          ...outlineErrors.value,
          [code]: "课程目录加载失败，请检查网络后重试",
        };
      }
      return null;
    } finally {
      if (request === epoch) {
        outlineLoading.value = { ...outlineLoading.value, [code]: false };
      }
    }
  }

  async function load(preferredCourseCode?: string) {
    const request = ++epoch;
    loading.value = true;
    error.value = "";
    courseCode.value = "";
    courses.value = [];
    outline.value = null;
    resume.value = null;
    outlines.value = {};
    outlineLoading.value = {};
    outlineErrors.value = {};
    try {
      const list = await listCourses();
      if (request !== epoch) return;
      courses.value = list;
      const available = list.filter((item) => item.publishedUnitCount > 0);
      const picked = selectAvailableCourse(list, preferredCourseCode);
      if (!picked) {
        outline.value = null;
        courseCode.value = "";
        error.value = "暂无已发布课程";
        return;
      }
      courseCode.value = picked.courseCode;
      resume.value = null;
      const pickedOutlinePromise = fetchOutline(picked.courseCode, request);
      const resumePromise = getCourseResume(picked.courseCode).catch(
        () => null
      );
      // Other courses populate the sidebar progressively without blocking this course.
      void Promise.all(
        available
          .filter((item) => item.courseCode !== picked.courseCode)
          .map((item) => fetchOutline(item.courseCode, request))
      );
      const [pickedOutline, resumeData] = await Promise.all([
        pickedOutlinePromise,
        resumePromise,
      ]);
      if (request !== epoch) return;
      outline.value = pickedOutline;
      if (!pickedOutline) {
        error.value =
          outlineErrors.value[picked.courseCode] || "课程目录加载失败";
        return;
      }
      resume.value = resumeData;
    } catch {
      if (request !== epoch) return;
      outline.value = null;
      error.value = "课程目录加载失败，请检查网络后重试";
    } finally {
      if (request === epoch) loading.value = false;
    }
  }

  const sections = computed<CourseSection[]>(
    () => outline.value?.sections || []
  );
  const courseTitle = computed(
    () =>
      outline.value?.title ||
      courses.value.find((c) => c.courseCode === courseCode.value)?.title ||
      "课程学习"
  );
  const courseProgress = computed(() => outline.value?.progress || null);

  const sectionByCode = computed(() => {
    const map = new Map<string, CourseSection>();
    const visit = (items: CourseSection[]) => {
      for (const item of items) {
        map.set(item.sectionCode, item);
        if (item.children?.length) visit(item.children);
      }
    };
    visit(sections.value);
    return map;
  });

  const unitByCode = computed(() => {
    const map = new Map<string, UnitSummary>();
    const visit = (items: CourseSection[]) => {
      for (const item of items) {
        for (const unit of item.units || []) map.set(unit.unitCode, unit);
        if (item.children?.length) visit(item.children);
      }
    };
    visit(sections.value);
    return map;
  });

  /** 跨课程选择章节/单元时，先用全局代码反查所属课程。 */
  const sectionCourseMap = computed(() => {
    const map = new Map<string, string>();
    for (const [code, data] of Object.entries(outlines.value)) {
      const visit = (items: CourseSection[]) => {
        for (const section of items) {
          map.set(section.sectionCode, code);
          if (section.children?.length) visit(section.children);
        }
      };
      visit(data.sections || []);
    }
    return map;
  });

  const unitCourseMap = computed(() => {
    const map = new Map<string, string>();
    for (const [code, data] of Object.entries(outlines.value)) {
      const visit = (items: CourseSection[]) => {
        for (const section of items) {
          for (const unit of section.units || []) map.set(unit.unitCode, code);
          if (section.children?.length) visit(section.children);
        }
      };
      visit(data.sections || []);
    }
    return map;
  });

  const courseCodeOfSection = (sectionCode?: string) =>
    sectionCode ? sectionCourseMap.value.get(sectionCode) || "" : "";
  const courseCodeOfUnit = (unitCode?: string) =>
    unitCode ? unitCourseMap.value.get(unitCode) || "" : "";

  // 目录顺序遍历（章 → 子节 → 单元），保证“第一个已发布单元”与目录一致
  const units = computed(() => {
    const result: UnitSummary[] = [];
    const visit = (items: CourseSection[]) => {
      for (const section of items) {
        result.push(
          ...(section.units || [])
            .slice()
            .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
        );
        if (section.children?.length) visit(section.children);
      }
    };
    visit(sections.value);
    return result;
  });

  function ancestorCodes(sectionCode?: string): string[] {
    const codes: string[] = [];
    let cursor = sectionCode;
    const seen = new Set<string>();
    while (cursor && !seen.has(cursor)) {
      seen.add(cursor);
      codes.unshift(cursor);
      cursor = sectionByCode.value.get(cursor)?.parentSectionCode || undefined;
    }
    return codes;
  }

  function descendantCodes(sectionCode: string): Set<string> {
    const result = new Set<string>();
    const visit = (code: string) => {
      if (result.has(code)) return;
      result.add(code);
      const section = sectionByCode.value.get(code);
      for (const child of section?.children || []) visit(child.sectionCode);
    };
    visit(sectionCode);
    return result;
  }

  function sectionCrumb(sectionCode?: string): string {
    return ancestorCodes(sectionCode)
      .map((code) => {
        const section = sectionByCode.value.get(code);
        return section
          ? `${section.textbookNumber || ""} ${section.title}`.trim()
          : code;
      })
      .filter(Boolean)
      .join(" / ");
  }

  function unitSection(unitCode?: string): CourseSection | null {
    const unit = unitCode ? unitByCode.value.get(unitCode) : undefined;
    return unit ? sectionByCode.value.get(unit.sectionCode) || null : null;
  }

  return {
    courses,
    publishedCourses,
    courseGroups,
    outlines,
    outlineLoading,
    outlineErrors,
    courseCode,
    courseTitle,
    outline,
    resume,
    loading,
    error,
    sections,
    courseProgress,
    sectionByCode,
    unitByCode,
    units,
    load,
    ancestorCodes,
    descendantCodes,
    sectionCrumb,
    unitSection,
    courseCodeOfSection,
    courseCodeOfUnit,
  };
}
