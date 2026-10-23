import Link from 'next/link';
import type { WmsOverviewState } from '../api/overview-state';
import { BarSwatch } from '@/shared/ui/InlineBar';
import { CountTile } from './WmsOverviewCountTile';
import { AlertStatusBar, alertStatusTone } from './AlertStatusBar';
import { PerformanceStrip } from '@/shared/ui/PerformanceStrip';
import type { MetricsPeriod } from '@/shared/lib/kst-period';

/** `GET /dashboard/metrics` keys (admin-service-api.md) → screen names. */
const PERFORMANCE_LABELS: Readonly<Record<string, string>> = {
  qtyReceived: '입고 수량',
  qtyShipped: '출고 수량',
  shipmentCount: '출고 건수',
};

const PERIOD_HREFS: Readonly<Record<MetricsPeriod, string>> = {
  week: '/wms?period=week',
  month: '/wms?period=month',
};

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
 *
 * ── «실적» STRIP (ADR-MONO-084 P3, TASK-MONO-808) ── first child of the
 * section: 입고 수량 · 출고 수량 · 출고 건수 for the whole operation
 * (warehouse-global), KST windows. Its failure is its own — the tiles below
 * render regardless. The 배송 tile's 오늘/주간/월간 stay: those are the
 * tenant's own shipment counts, a different question.
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
      <PerformanceStrip
        cell={state.performance}
        selected={state.period}
        periodHrefs={PERIOD_HREFS}
        labels={PERFORMANCE_LABELS}
        chartKey="qtyShipped"
        testid="wms-performance"
      />

      <h2 className="mb-3 text-lg font-semibold text-foreground">운영 개요</h2>

      <nav
        aria-label="WMS 화면 바로가기"
        className="mb-6 flex flex-wrap gap-4 text-sm"
      >
        <Link
          href="/wms/guide"
          data-testid="wms-overview-link-guide"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          가이드
        </Link>
      </nav>

      {/* Per-area count tiles — read-only stat tiles (no drill-in routes). */}
      <div data-testid="wms-overview-counts" className="mb-6 flex flex-wrap gap-3">
        {state.counts.map((area) => (
          <CountTile key={area.key} area={area} />
        ))}
      </div>

      {/* Alert-acknowledgement distribution. */}
      <div className="mb-6">
        <h3 className="mb-2 text-sm font-semibold text-foreground">알림 상태</h3>
        <AlertStatusBar buckets={state.alertStatus} />
        <dl
          data-testid="wms-alert-status"
          className="flex flex-wrap gap-x-8 gap-y-3 rounded-md border border-border bg-background px-4 py-4"
        >
          {state.alertStatus.map((bucket) => (
            <div key={bucket.key}>
              <dt className="text-xs text-muted-foreground">
                <BarSwatch tone={alertStatusTone(bucket.key)} />
                {bucket.label}
              </dt>
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
