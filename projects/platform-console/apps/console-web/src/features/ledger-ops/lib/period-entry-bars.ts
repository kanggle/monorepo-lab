import type { Period } from '../api/types';

/**
 * Ledger period `entryCount` bars (TASK-PC-FE-359) — pure helpers for
 * `PeriodsTable`. `entryCount` is a plain integer count (null while OPEN), not
 * F5 money, so no chart-scale exception is involved.
 */

/** Largest positive `entryCount` on the page (0 when none → no bars). */
export function periodEntryMax(rows: readonly Period[]): number {
  return rows.reduce(
    (m, p) =>
      typeof p.entryCount === 'number' && Number.isFinite(p.entryCount) && p.entryCount > m
        ? p.entryCount
        : m,
    0,
  );
}

/**
 * What the bars are compared against. The producer lists periods
 * most-recent window first (`ledger-api.md` § 7) and the screen loads one
 * page (default 20): page 1 is «the most recent N», later pages are «this
 * page», and a single page holding everything is «all N».
 */
export function periodEntryBarScopeText(
  pageCount: number,
  totalElements: number,
  page: number,
): string {
  if (pageCount >= totalElements) {
    return `막대는 회계 기간 ${pageCount}개 전체 중 가장 많은 분개 수 대비 길이입니다.`;
  }
  return page === 0
    ? `막대는 최근 ${pageCount}개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 ${totalElements}개).`
    : `막대는 이 페이지 ${pageCount}개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 ${totalElements}개).`;
}
