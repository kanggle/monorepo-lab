/**
 * Shared date/time formatters for operator-facing views (ko-KR).
 *
 * Convention (console-wide):
 *   - Record timestamps (created/updated/occurred/fetched-at, audit) →
 *     {@link formatDateTime}: date + 24-hour time, "2026. 6. 23. 17:59:15".
 *   - Day-granular values (a calendar day carries the meaning, not the instant)
 *     → {@link formatDate}: "2026. 6. 23.".
 *
 * Timezone is PINNED to Asia/Seoul (this is a KST operator console) so the
 * output is byte-identical between the SSR initial render (container zone, often
 * UTC) and client hydration — avoiding a React hydration mismatch on
 * server-seeded views. 24-hour via `hourCycle: 'h23'` so midnight renders
 * "00:00:00" (ko-KR's `hour12:false` can emit "24:00:00").
 *
 * Tolerant: empty/absent → `placeholder` ("-" by default); an unparseable
 * string → returned verbatim (never throws).
 *
 * NOTE: values stored as UTC day-edge instants (e.g. promotion start 00:00:00Z /
 * end 23:59:59Z) must NOT use these — a KST offset rolls the end to the next
 * day. Use the UTC-anchored `formatPromotionDay` for those.
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
