import { formatDateTime } from '@/shared/lib/datetime';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import type { EcommerceOverviewState, CellStatus } from '../api/overview-state';
import { sellerStatusTone } from '../api/seller-types';
import { orderStatusTone, type OrderStatus } from '../api/order-types';
import { ORDER_STATUS_LABELS, cellPlaceholder } from './overview-labels';

/**
 * Recent-activity panels for the ecommerce operator overview (TASK-PC-FE-199 —
 * extracted from {@link EcommerceOverview}, presentational only). A non-`ok`
 * panel renders a "점검 필요" / "권한 없음" placeholder; an empty `ok` panel a
 * "최근 항목이 없습니다." note. All `data-testid`s are unchanged.
 */

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

export function RecentOrders({
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
          <StatusBadge tone={orderStatusTone(o.status)} className="shrink-0">
            {ORDER_STATUS_LABELS[o.status as OrderStatus] ?? o.status}
          </StatusBadge>
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

export function RecentSellers({
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
