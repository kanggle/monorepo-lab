/**
 * Shared date/time formatters for operator-facing views (ko-KR).
 *
 * Convention (console-wide):
 *   - Record timestamps (created/updated/placed-at, audit) → {@link formatDateTime}:
 *     date + 24-hour time, e.g. "2026. 6. 23. 17:59:15".
 *   - Day-granular values (a calendar day carries the meaning, not the instant)
 *     → {@link formatDate}, e.g. "2026. 6. 23.".
 *
 * 24-hour via `hourCycle: 'h23'` so midnight renders "00:00:00" (ko-KR's
 * default `hour12:false` can emit "24:00:00"). Local zone (operator's browser).
 * Unparseable / empty input falls back to the raw string (or "-" when empty).
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });
}

/** Date only (local zone), e.g. "2026. 6. 23.". See {@link formatDateTime}. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('ko-KR');
}
