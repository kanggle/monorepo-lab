/**
 * Shared cell vocabulary for the wms overview presentation (TASK-PC-FE-197
 * split, extracted from `WmsOverview`). The non-`ok` placeholder text plus the
 * per-area service-status dot / label maps — reused by the count tiles
 * (`WmsOverviewCountTile`) and the recent-activity glances
 * (`WmsRecentShipments` / `WmsRecentAdjustments`). No hooks, no JSX.
 *
 * Thin re-export shim (TASK-PC-FE-264) — the vocabulary itself moved to
 * `shared/lib/overview-cell.ts` (5-domain duplication); this file keeps its
 * existing exported names so all 4 importers stay unchanged.
 */

export {
  overviewCellPlaceholder as cellPlaceholder,
  OVERVIEW_STATUS_DOT as SERVICE_STATUS_DOT,
  OVERVIEW_STATUS_LABEL as SERVICE_STATUS_LABEL,
} from '@/shared/lib/overview-cell';
