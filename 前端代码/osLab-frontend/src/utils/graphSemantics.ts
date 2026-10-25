/** Canonical registry relations retain the historical UI keys and directions. */
export function normalizedGraphRelation(value: unknown): string {
  const key = String(value || "")
    .trim()
    .toLowerCase();
  return (
    (
      {
        requires: "dependent",
        contains: "contain",
        related_to: "related",
        implemente: "implements",
        contrasts_with: "contrasts",
        based_on: "based",
      } as Record<string, string>
    )[key] || key
  );
}
export function safeLocalLearningUrl(value?: string): boolean {
  return Boolean(
    value &&
      /^\/(?:knowledge\/(?:concept|node)\/|courses\/|assessments(?:[/?#]|$)|experiments\/|records(?:[?#]|$))/.test(
        value
      ) &&
      !value.includes("\\") &&
      !Array.from(value).some((char) => char.charCodeAt(0) < 32)
  );
}
