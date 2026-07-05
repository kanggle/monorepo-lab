import type { EcommerceOverviewState } from '../api/overview-state';
import { RankingBarChart } from './RankingBarChart';
import { CountCard } from './EcommerceCountCard';
import { RecentOrders, RecentSellers } from './EcommerceRecentPanels';
import { ORDER_STATUS_LABELS } from './overview-labels';

/**
 * ecommerce operator **overview snapshot** presentation (TASK-PC-FE-156;
 * TASK-PC-FE-164 — count cards now show period metrics: 오늘/주간/월간 +
 * 전체 total as secondary context from the `/summary` endpoints).
 * Server component — STRICTLY READ-ONLY, no `'use client'`. Renders the
 * `getEcommerceOverviewState` fan-out result: per-area count cards (each a
 * quick-launch `Link`, PC-FE-155 back-compat testids), order-status
 * distribution, and recent orders + sellers. A non-`ok` cell renders a compact
 * "점검 필요" / "권한 없음" placeholder instead of a number (never blanks).
 *
 * TASK-PC-FE-199: the count-card ({@link CountCard}) and recent-activity panels
 * ({@link RecentOrders}/{@link RecentSellers}) presentational pieces live in
 * sibling files; shared labels in `overview-labels`. This container keeps the
 * section layout orchestration (behavior-preserving split).
 */
export function EcommerceOverview({
  state,
}: {
  state: EcommerceOverviewState;
}) {
  if (state.notEligible) {
    return null;
  }

  return (
    <section data-testid="ecommerce-overview" aria-label="이커머스 운영 개요">
      {/* Per-area count cards — each card IS the quick-launch link. */}
      <div className="mb-8">
        <h2 className="mb-3 text-lg font-semibold text-foreground">운영 개요</h2>
        <div
          data-testid="ecommerce-ops-links"
          className="flex flex-wrap gap-3"
        >
          {state.counts.map((area) => (
            <CountCard key={area.key} area={area} />
          ))}
        </div>
      </div>

      {/* Order-status distribution. */}
      <div className="mb-8">
        <h3 className="mb-3 text-sm font-semibold text-foreground">주문 상태</h3>
        <dl
          data-testid="ecommerce-order-status"
          className="flex flex-wrap gap-x-8 gap-y-3 rounded-md border border-border bg-background px-4 py-4"
        >
          {state.orderStatus.map((bucket) => (
            <div key={bucket.status}>
              <dt className="text-xs text-muted-foreground">
                {ORDER_STATUS_LABELS[bucket.status] ?? bucket.status}
              </dt>
              <dd
                className="text-lg font-semibold tabular-nums text-foreground"
                data-testid={`order-status-${bucket.status}`}
              >
                {bucket.cellStatus === 'ok' && bucket.count !== null
                  ? bucket.count.toLocaleString()
                  : '—'}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* Sales rankings — top-5 product/seller charts (TASK-PC-FE-172). */}
      <div className="mb-8">
        <h3 className="mb-3 text-sm font-semibold text-foreground">판매 순위</h3>
        <div className="grid gap-6 md:grid-cols-2">
          <RankingBarChart
            title="상품별 주문횟수"
            entries={state.insights?.topProductsByOrderCount ?? []}
            status={state.insightsStatus}
            format="count"
            testid="ecommerce-rank-products-orders"
          />
          <RankingBarChart
            title="상품별 매출"
            entries={state.insights?.topProductsByRevenue ?? []}
            status={state.insightsStatus}
            format="currency"
            testid="ecommerce-rank-products-revenue"
          />
          <RankingBarChart
            title="셀러별 주문횟수"
            entries={state.insights?.topSellersByOrderCount ?? []}
            status={state.insightsStatus}
            format="count"
            testid="ecommerce-rank-sellers-orders"
          />
          <RankingBarChart
            title="셀러별 매출"
            entries={state.insights?.topSellersByRevenue ?? []}
            status={state.insightsStatus}
            format="currency"
            testid="ecommerce-rank-sellers-revenue"
          />
        </div>
      </div>

      {/* Recent activity — orders + sellers. */}
      <div className="grid gap-6 md:grid-cols-2">
        <RecentOrders
          rows={state.recentOrders}
          status={state.recentOrdersStatus}
        />
        <RecentSellers
          rows={state.recentSellers}
          status={state.recentSellersStatus}
        />
      </div>
    </section>
  );
}
