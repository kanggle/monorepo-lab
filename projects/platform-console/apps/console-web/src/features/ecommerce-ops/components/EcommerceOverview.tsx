import Link from 'next/link';
import { formatDateTime } from '@/shared/lib/datetime';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import type {
  EcommerceOverviewState,
  AreaCount,
  CellStatus,
} from '../api/overview-state';
import { sellerStatusTone } from '../api/seller-types';
import type { OrderStatus } from '../api/order-types';

/**
 * ecommerce operator **overview snapshot** presentation (TASK-PC-FE-156).
 * Server component — STRICTLY READ-ONLY, no `'use client'`. Renders the
 * `getEcommerceOverviewState` fan-out result: per-area count cards (each a
 * quick-launch `Link`, PC-FE-155 back-compat testids), order-status
 * distribution, and recent orders + sellers. A non-`ok` cell renders a compact
 * "점검 필요" / "권한 없음" placeholder instead of a number (never blanks).
 */

/** Korean labels for the order-status distribution buckets (tolerant; an
 * unmapped/future status falls back to the raw value at the call site). */
const ORDER_STATUS_LABELS: Partial<Record<OrderStatus, string>> = {
  PENDING: '대기',
  CONFIRMED: '확정',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELLED: '취소',
  STUCK_RECOVERY_FAILED: '복구실패',
};

function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

function CountCard({ area }: { area: AreaCount }) {
  const ok = area.status === 'ok' && area.count !== null;
  return (
    <Link
      href={area.href}
      data-testid={area.testid}
      className="flex min-w-[7.5rem] flex-1 flex-col gap-1 rounded-md border border-border bg-background px-4 py-3 transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <span className="text-sm text-muted-foreground">{area.label}</span>
      {ok ? (
        <span
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid={`${area.key}-count`}
        >
          {area.count!.toLocaleString()}
        </span>
      ) : (
        <span
          className="text-sm font-medium text-muted-foreground"
          data-testid={`${area.key}-count-degraded`}
        >
          {cellPlaceholder(area.status)}
        </span>
      )}
    </Link>
  );
}

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

function RecentPanel({
  title,
  testid,
  status,
  empty,
  children,
}: {
  title: string;
  testid: string;
  status: CellStatus;
  empty: boolean;
  children: React.ReactNode;
}) {
  return (
    <div
      data-testid={testid}
      className="rounded-md border border-border bg-background p-4"
    >
      <h3 className="mb-3 text-sm font-semibold text-foreground">{title}</h3>
      {status !== 'ok' ? (
        <p className="text-sm text-muted-foreground">{cellPlaceholder(status)}</p>
      ) : empty ? (
        <p className="text-sm text-muted-foreground">최근 항목이 없습니다.</p>
      ) : (
        <ul className="space-y-2 text-sm">{children}</ul>
      )}
    </div>
  );
}

function RecentOrders({
  rows,
  status,
}: {
  rows: EcommerceOverviewState['recentOrders'];
  status: CellStatus;
}) {
  return (
    <RecentPanel
      title="최근 주문"
      testid="ecommerce-recent-orders"
      status={status}
      empty={!rows || rows.length === 0}
    >
      {rows?.map((o) => (
        <li
          key={o.orderId}
          className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
        >
          <span className="min-w-0 flex-1 truncate text-foreground">
            {o.firstItemName}
            {o.itemCount > 1 ? ` 외 ${o.itemCount - 1}건` : ''}
          </span>
          <span className="shrink-0 text-muted-foreground">
            {ORDER_STATUS_LABELS[o.status as OrderStatus] ?? o.status}
          </span>
          <span className="shrink-0 tabular-nums text-foreground">
            ₩{o.totalPrice.toLocaleString('ko-KR')}
          </span>
          <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
            {formatDateTime(o.createdAt)}
          </span>
        </li>
      ))}
    </RecentPanel>
  );
}

function RecentSellers({
  rows,
  status,
}: {
  rows: EcommerceOverviewState['recentSellers'];
  status: CellStatus;
}) {
  return (
    <RecentPanel
      title="최근 셀러"
      testid="ecommerce-recent-sellers"
      status={status}
      empty={!rows || rows.length === 0}
    >
      {rows?.map((s) => {
        return (
          <li
            key={s.sellerId}
            className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
          >
            <span className="min-w-0 flex-1 truncate text-foreground">
              {s.displayName}
            </span>
            <StatusBadge tone={sellerStatusTone(s.status)} className="shrink-0">
              {s.status}
            </StatusBadge>
            <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
              {formatDateTime(s.createdAt)}
            </span>
          </li>
        );
      })}
    </RecentPanel>
  );
}
