import { computed, ref } from "vue";
import {
  getCourseOutline,
  getCourseResume,
  listCourses,
  type CourseOutline,
  type CourseSection,
  type CourseSummary,
  type UnitSummary,
} from "@/api/course";

export interface CourseResume {
  unitCode?: string;
  activityCode?: string;
  reason: string;
}

/** 课程目录的共享只读视图：学生只看到 outline 返回的已发布单元。 */
export function useCourseCatalog() {
  const courses = ref<CourseSummary[]>([]);
  const courseCode = ref("");
  const outline = ref<CourseOutline | null>(null);
  const resume = ref<CourseResume | null>(null);
  const loading = ref(false);
  const error = ref("");
  let epoch = 0;

  async function load(preferredCourseCode?: string) {
    const request = ++epoch;
    loading.value = true;
    error.value = "";
    try {
      const list = await listCourses();
      if (request !== epoch) return;
      courses.value = list;
      const picked =
        list.find((item) => item.courseCode === preferredCourseCode) ||
        list.find((item) => item.courseCode === courseCode.value) ||
        list[0];
      if (!picked) {
        outline.value = null;
        courseCode.value = "";
        error.value = "暂无可用课程";
        return;
      }
      courseCode.value = picked.courseCode;
      const data = await getCourseOutline(picked.courseCode);
      if (request !== epoch) return;
      outline.value = data;
      try {
        const resumeData = await getCourseResume(picked.courseCode);
        if (request === epoch) resume.value = resumeData;
      } catch {
        if (request === epoch) resume.value = null;
      }
    } catch (e) {
      if (request !== epoch) return;
      outline.value = null;
      error.value = e instanceof Error ? e.message : "课程目录加载失败";
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
  };
}
