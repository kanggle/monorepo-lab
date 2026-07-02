/**
 * Promotion start/end are stored as UTC day-edge Instants (start → `00:00:00Z`,
 * end → `23:59:59Z`; see `use-promotion-form` `dayToInstant`). Render the
 * intended calendar day in ko-KR, formatting in **UTC** so the end-of-day
 * `23:59:59Z` instant does not roll to the next day under a positive local
 * offset (e.g. KST +9). Falls back to the raw string if unparseable.
 */
export function formatPromotionDay(iso: string): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  });
}
