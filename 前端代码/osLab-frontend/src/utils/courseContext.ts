export interface CourseActivityContext {
  sourceUnitCode?: string;
  sourceActivityCode?: string;
}

/** Only an unambiguous, nonblank pair identifies a course activity. */
export function courseContext(
  source: Record<string, unknown>
): CourseActivityContext {
  const unitCode =
    typeof source.sourceUnitCode === "string"
      ? source.sourceUnitCode.trim()
      : "";
  const activityCode =
    typeof source.sourceActivityCode === "string"
      ? source.sourceActivityCode.trim()
      : "";
  return unitCode && activityCode
    ? { sourceUnitCode: unitCode, sourceActivityCode: activityCode }
    : {};
}
