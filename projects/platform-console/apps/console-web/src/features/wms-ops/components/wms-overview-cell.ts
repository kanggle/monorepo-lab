import type { CellStatus } from '../api/overview-state';

/**
 * Shared cell vocabulary for the wms overview presentation (TASK-PC-FE-197
 * split, extracted from `WmsOverview`). The non-`ok` placeholder text plus the
 * per-area service-status dot / label maps — reused by the count tiles
 * (`WmsOverviewCountTile`) and the recent-activity glances
 * (`WmsRecentShipments` / `WmsRecentAdjustments`). No hooks, no JSX.
 */

export function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

/**
 * Per-area service-status indicator — mirrors the ecommerce overview / the
 * console-home "도메인 상태 요약" dot vocabulary. The cell status IS the
 * per-area signal: `ok` = the area's list read responded, `forbidden` = 403,
 * `degraded` = 503/timeout/network reach failure. No extra fan-out.
 */
export const SERVICE_STATUS_DOT: Record<CellStatus, string> = {
  ok: 'bg-green-500',
  degraded: 'bg-red-500',
  forbidden: 'bg-muted-foreground/40',
};
export const SERVICE_STATUS_LABEL: Record<CellStatus, string> = {
  ok: '정상',
  degraded: '점검 필요',
  forbidden: '권한 없음',
};
