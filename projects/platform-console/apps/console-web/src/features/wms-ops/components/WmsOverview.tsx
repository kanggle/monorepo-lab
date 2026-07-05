import type { WmsOverviewState } from '../api/overview-state';
import { CountTile } from './WmsOverviewCountTile';

/**
 * wms operator **overview snapshot** presentation (TASK-PC-FE-166 — the first
 * bff-domain reference impl of the console domain-landing overview series;
 * analogue of `EcommerceOverview`). Pure presentational component (no
 * `'use client'`, no server-only imports) — rendered server-side and passed as
 * a slot into the client `WmsOpsScreen`. STRICTLY READ-ONLY.
 *
 * Renders the `getWmsOverviewState` fan-out result: per-area count tiles for
 * the operational-scale areas (재고 — total + 저재고 attention sub-count,
 * PC-FE-177 / 배송 — 오늘/주간/월간/전체) and an alert-acknowledgement
 * distribution (미확인/확인) — the sole representation of alerts, an attention
 * signal rather than a scale area (PC-FE-170). The 최근 출고 glance is a
 * SEPARATE slot (`WmsRecentShipments`, PC-FE-177) rendered AFTER the alerts
 * table so the page reads 규모 → 주의 → 활동. A non-`ok` cell renders a compact
 * "점검 필요" / "권한 없음" placeholder instead of a number (never blanks).
 *
 * PER-DOMAIN DEVIATION vs ecommerce (PC-FE-168): the count tiles are NOT nav
 * links — unlike ecommerce's § 2.4.10 sub-route cards, `/wms` is a single-route
 * ops screen (no per-area drill-in routes), so the tiles are read-only stat
 * tiles that summarize the tables rendered directly below.
 *
 * ── SPLIT (TASK-PC-FE-197) ── the count-tile presentation moved to
 * `WmsOverviewCountTile` (+ the shared cell vocabulary in `wms-overview-cell`);
 * the 최근 출고 / 최근 재고 조정 glances live in `WmsRecentShipments` /
 * `WmsRecentAdjustments`. This file is the thin overview-section composer.
 */
export function WmsOverview({ state }: { state: WmsOverviewState }) {
  if (state.notEligible) {
    return null;
  }

  return (
    <section
      data-testid="wms-overview"
      aria-label="WMS 운영 개요"
      className="mb-8"
    >
      <h2 className="mb-3 text-lg font-semibold text-foreground">운영 개요</h2>

      {/* Per-area count tiles — read-only stat tiles (no drill-in routes). */}
      <div data-testid="wms-overview-counts" className="mb-6 flex flex-wrap gap-3">
        {state.counts.map((area) => (
          <CountTile key={area.key} area={area} />
        ))}
      </div>

      {/* Alert-acknowledgement distribution. */}
      <div className="mb-6">
        <h3 className="mb-2 text-sm font-semibold text-foreground">알림 상태</h3>
        <dl
          data-testid="wms-alert-status"
          className="flex flex-wrap gap-x-8 gap-y-3 rounded-md border border-border bg-background px-4 py-4"
        >
          {state.alertStatus.map((bucket) => (
            <div key={bucket.key}>
              <dt className="text-xs text-muted-foreground">{bucket.label}</dt>
              <dd
                className="text-lg font-semibold tabular-nums text-foreground"
                data-testid={`wms-alert-status-${bucket.key}`}
              >
                {bucket.cellStatus === 'ok' && bucket.count !== null
                  ? bucket.count.toLocaleString()
                  : '—'}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  );
}
