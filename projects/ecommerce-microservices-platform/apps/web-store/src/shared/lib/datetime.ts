/**
 * Shared date/time formatters for the web-store storefront (ko-KR).
 *
 * Convention (mirrors platform-console):
 *   - Record timestamps (ordered/paid/shipped/sent/created-at) → {@link formatDateTime}:
 *     date + 24-hour time, "2026. 6. 23. 17:59:15".
 *   - Day-granular values (a calendar day carries the meaning) → {@link formatDate}:
 *     "2026. 6. 23.".
 *
 * Timezone is PINNED to Asia/Seoul so the output is byte-identical between the
 * SSR initial render (container zone, often UTC) and client hydration — no React
 * hydration mismatch. 24-hour via `hourCycle: 'h23'` (avoids ko-KR's "24:00:00"
 * midnight quirk). Empty/absent → `placeholder` ("-" by default); an unparseable
 * string → returned verbatim (never throws).
 */
const TZ = 'Asia/Seoul';

export function formatDateTime(
  iso: string | null | undefined,
  placeholder = '-',
): string {
  if (!iso) return placeholder;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', {
    timeZone: TZ,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  });
}

/** Date only (KST), e.g. "2026. 6. 23.". See {@link formatDateTime}. */
export function formatDate(
  iso: string | null | undefined,
  placeholder = '-',
): string {
  if (!iso) return placeholder;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('ko-KR', { timeZone: TZ });
}
