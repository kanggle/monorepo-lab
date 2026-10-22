import type { PayoutsResponse } from '../api/settlement-types';

/**
 * Settlement-period payout bars (TASK-PC-FE-359) — pure helpers for
 * `PeriodPayoutsTable`. `payableNetMinor` is an integer won `number` (NOT an
 * F5 decimal string), so no chart-scale exception is involved.
 */

/** Largest positive `payableNetMinor` on the page (0 when none → no bars). */
export function payoutBarMax(rows: PayoutsResponse['items']): number {
  return rows.reduce(
    (m, p) =>
      Number.isFinite(p.payableNetMinor) && p.payableNetMinor > m
        ? p.payableNetMinor
        : m,
    0,
  );
}

/**
 * What the bars are compared against. The screen loads ONE page (default 20)
 * of the period's payouts, so when the producer's `totalElements` is larger
 * the sentence says the comparison is within this page only.
 */
export function payoutBarScopeText(pageCount: number, totalElements: number): string {
  return pageCount < totalElements
    ? `막대는 이 페이지 ${pageCount}건 안에서 가장 큰 지급액 대비 길이입니다 (전체 ${totalElements}건).`
    : `막대는 이 기간 지급 ${pageCount}건 중 가장 큰 지급액 대비 길이입니다.`;
}
