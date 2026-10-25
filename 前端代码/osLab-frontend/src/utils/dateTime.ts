const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const timeFormatter = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const toDate = (value?: string | number | Date | null): Date | null => {
  if (value === null || value === undefined || value === "") return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const dateParts = (date: Date) => {
  const parts: Record<string, string> = {};
  for (const part of dateFormatter.formatToParts(date)) {
    parts[part.type] = part.value;
  }
  return `${parts.year}-${parts.month}-${parts.day}`;
};

export const formatDate = (value?: string | number | Date | null): string => {
  const date = toDate(value);
  return date ? dateParts(date) : "—";
};

export const formatDateTime = (
  value?: string | number | Date | null
): string => {
  const date = toDate(value);
  return date ? `${dateParts(date)} ${timeFormatter.format(date)}` : "—";
};
