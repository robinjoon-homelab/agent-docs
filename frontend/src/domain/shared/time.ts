const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function formatDate(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return `${formatDate(iso)} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Korean relative time used across the app; falls back to the calendar date after a week. */
export function relativeTime(iso: string, now: Date): string {
  const elapsed = now.getTime() - new Date(iso).getTime();
  if (elapsed < MINUTE) return '방금 전';
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)}분 전`;
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)}시간 전`;
  if (elapsed < 7 * DAY) return `${Math.floor(elapsed / DAY)}일 전`;
  return formatDate(iso);
}

export function daysUntil(iso: string, now: Date): number {
  return (new Date(iso).getTime() - now.getTime()) / DAY;
}

export function minutesBetween(fromIso: string, to: Date): number {
  return (to.getTime() - new Date(fromIso).getTime()) / MINUTE;
}
