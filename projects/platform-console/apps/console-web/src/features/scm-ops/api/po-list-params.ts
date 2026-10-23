/**
 * `/scm/procurement` URL query → the PO list's initial filter (TASK-MONO-810).
 *
 * The 개요 «실적» cards link here with the window their number came from
 * (`?submittedFrom=…&submittedTo=…`, the response's own `current` bounds) or
 * with a status (`?status=RECEIVED`). This module decides what of the URL is
 * applied. Pure — no I/O, safe on both sides.
 *
 * Decision (documented in console-integration-contract § 2.4.6): a value the
 * console can tell is wrong is DROPPED here and reported (`ignored`), not sent.
 * Passing it through would make the producer answer 400, which the list
 * surfaces as «일시적으로 불러올 수 없습니다» — a transient-failure message for
 * what is a bad link. Dropping it shows the unfiltered list with a notice
 * that says which part of the address was not applied, and no period chip, so
 * the screen never claims a filter it did not run.
 *   - `submittedFrom` / `submittedTo`: an ISO-8601 instant WITH an offset
 *     (`Z` or `±hh:mm`), parseable. A `+` that arrived as a space (an
 *     un-encoded `+09:00` in a hand-typed URL) is put back first.
 *   - `status` / `excludeStatus`: one of {@link KNOWN_STATUSES} (the list's
 *     own select options). The 개요 card links send `excludeStatus=CANCELED`.
 *   - Each bound stands alone (only one bound is a valid, half-open filter).
 *     `from >= to` is not second-guessed: it is a valid empty window.
 */

const KNOWN_STATUSES: ReadonlySet<string> = new Set([
  'DRAFT',
  'SUBMITTED',
  'ACKNOWLEDGED',
  'CONFIRMED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'SETTLED',
  'CLOSED',
  'CANCELED',
]);

const INSTANT =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})$/;

export interface PoListInitialFilter {
  status?: string;
  submittedFrom?: string;
  submittedTo?: string;
  excludeStatus?: string;
}

export interface PoListUrlFilter {
  filter: PoListInitialFilter;
  /** URL keys present but not applied (in a fixed order). */
  ignored: Array<'status' | 'submittedFrom' | 'submittedTo' | 'excludeStatus'>;
}

type Raw = string | string[] | undefined;

function first(v: Raw): string | undefined {
  const s = Array.isArray(v) ? v[0] : v;
  return s === undefined ? undefined : s.trim();
}

/** `true` for an ISO-8601 instant with an explicit offset that parses. */
export function isIsoInstant(value: string): boolean {
  return INSTANT.test(value) && !Number.isNaN(Date.parse(value));
}

function instant(v: Raw): { value?: string; bad: boolean } {
  const s = first(v);
  if (s === undefined || s === '') return { bad: false };
  const repaired = s.replace(/ (\d{2}:\d{2})$/, '+$1');
  return isIsoInstant(repaired) ? { value: repaired, bad: false } : { bad: true };
}

export function parsePoListSearchParams(sp: {
  status?: Raw;
  submittedFrom?: Raw;
  submittedTo?: Raw;
  excludeStatus?: Raw;
}): PoListUrlFilter {
  const filter: PoListInitialFilter = {};
  const ignored: PoListUrlFilter['ignored'] = [];

  const status = first(sp.status);
  if (status) {
    if (KNOWN_STATUSES.has(status)) filter.status = status;
    else ignored.push('status');
  }
  const from = instant(sp.submittedFrom);
  if (from.value) filter.submittedFrom = from.value;
  if (from.bad) ignored.push('submittedFrom');
  const to = instant(sp.submittedTo);
  if (to.value) filter.submittedTo = to.value;
  if (to.bad) ignored.push('submittedTo');
  const excludeStatus = first(sp.excludeStatus);
  if (excludeStatus) {
    if (KNOWN_STATUSES.has(excludeStatus)) filter.excludeStatus = excludeStatus;
    else ignored.push('excludeStatus');
  }

  return { filter, ignored };
}

/** `2026-10-01T00:00:00+09:00` → `2026-10-01 00:00` on the KST wall clock,
 *  whatever offset the instant was written in (fixed +09:00 — no DST). */
export function formatKstInstant(iso: string): string {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return iso;
  const kst = new Date(t + 9 * 60 * 60 * 1000).toISOString();
  return `${kst.slice(0, 10)} ${kst.slice(11, 16)}`;
}
