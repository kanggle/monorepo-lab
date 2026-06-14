/**
 * Human-readable date-time formatting for ERP-ops surfaces.
 *
 * Producer values arrive as ISO-8601 instants (e.g. `2026-06-01T00:00:00Z`).
 * Rendering those verbatim in tables is hard to read, so this formats them to
 * `YYYY-MM-DD HH:mm` in KST (Asia/Seoul).
 *
 * The timezone is PINNED (not the browser's local zone) so the output is
 * byte-identical between the SSR initial render and the client hydration —
 * avoiding a React hydration mismatch on cards seeded with server-provided
 * `initial` data (DelegationFactCard / org-view).
 *
 * Tolerant: an absent value → the supplied placeholder ('—' by default); an
 * unparseable string → returned verbatim (never throws — NON_NULL-absent /
 * free-string tolerance, matching the existing erp-ops rendering contract).
 */
const KST_PARTS = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

export function fmtDateTime(
  ts: string | null | undefined,
  placeholder = '—',
): string {
  if (ts == null || ts === '') return placeholder;
  const d = new Date(ts);
  if (Number.isNaN(d.getTime())) return ts; // unparseable → verbatim
  const parts = KST_PARTS.formatToParts(d);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? '';
  // en-CA already yields 2-digit zero-padded fields; assemble explicitly so the
  // separator is a stable "YYYY-MM-DD HH:mm" regardless of locale punctuation.
  const hour = get('hour') === '24' ? '00' : get('hour'); // guard ICU 24:00
  return `${get('year')}-${get('month')}-${get('day')} ${hour}:${get('minute')}`;
}
