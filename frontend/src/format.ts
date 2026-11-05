export function clock(value: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(value));
}

/** "Thu 6 Nov, 9:00 AM" style, for anything that may not be today. */
export function dayTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

/** "Thu 6 Nov" style. */
export function dayLabel(value: string): string {
  return new Intl.DateTimeFormat(undefined, { weekday: "short", day: "numeric", month: "short" }).format(new Date(value));
}

export function titleCase(value: string): string {
  if (value === "FOLLOW_UP") return "Follow-up";
  if (value === "NO_SHOW") return "No-show";
  if (value === "RESCHEDULE_PENDING") return "Awaiting decision";
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export function message(cause: unknown, fallback: string): string {
  return cause instanceof Error ? cause.message : fallback;
}
