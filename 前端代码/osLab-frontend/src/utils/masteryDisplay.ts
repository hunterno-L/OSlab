export function scoreText(
  score: number | null | undefined,
  evidenceCount: number | null | undefined
): string {
  if (!evidenceCount || score === null || score === undefined) return "未评估";
  return `${Math.round(score * 100)}%`;
}
