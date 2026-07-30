/**
 * Shared domain-overview cell vocabulary (TASK-PC-FE-264).
 *
 * `ecommerce-ops`, `iam-overview`, `erp-ops`, `scm-ops`, and `wms-ops` each
 * reimplemented the SAME cell-status placeholder text and status-dot/label
 * maps verbatim. Per `platform/refactoring-policy.md` § Prioritization
 * duplication outranks naming, so the vocabulary is promoted here (the
 * `shared/ui/ConfirmDialog`/`RetryButton`/`DegradeBanner` precedent,
 * TASK-PC-FE-262/263 — this is the `shared/lib` analogue: pure
 * functions/consts, no JSX). Each domain keeps its own `CellStatus` type
 * declaration in its `api/overview-state.ts` (structurally identical to
 * `OverviewCellStatus` below, so no domain needs to import or alias this
 * module's type) and re-exports these under its own existing local names.
 */

export type OverviewCellStatus = 'ok' | 'forbidden' | 'degraded';

export function overviewCellPlaceholder(status: OverviewCellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

export const OVERVIEW_STATUS_DOT: Record<OverviewCellStatus, string> = {
  ok: 'bg-green-500',
  degraded: 'bg-red-500',
  forbidden: 'bg-muted-foreground/40',
};

export const OVERVIEW_STATUS_LABEL: Record<OverviewCellStatus, string> = {
  ok: '정상',
  degraded: '점검 필요',
  forbidden: '권한 없음',
};
