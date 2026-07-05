import { formatDateTime } from '@/shared/lib/datetime';
import type { WmsOverviewState, CellStatus } from '../api/overview-state';
import { cellPlaceholder } from './wms-overview-cell';

/**
 * 최근 출고 (recent-shipments) glance — a passive activity glance rendered as a
 * SEPARATE slot (PC-FE-177) so the 개요 reads 규모(counts) → 주의(알림 분포 +
 * 알림 테이블) → 활동(최근 출고): the page passes this as `WmsOpsScreen`'s
 * `recentActivity` slot, rendered AFTER the alerts table, so the alert
 * distribution + table are contiguous and this passive glance sits last.
 * Server component (no `'use client'`); fed by the same `getWmsOverviewState`.
 *
 * ── SPLIT (TASK-PC-FE-197) ── extracted verbatim from `WmsOverview.tsx` into
 * its own module (it is already a distinct barrel export + slot).
 */
export function WmsRecentShipments({ state }: { state: WmsOverviewState }) {
  if (state.notEligible) {
    return null;
  }
  return (
    <div className="mt-8">
      <RecentShipments
        rows={state.recentShipments}
        status={state.recentShipmentsStatus}
      />
    </div>
  );
}

function RecentShipments({
  rows,
  status,
}: {
  rows: WmsOverviewState['recentShipments'];
  status: CellStatus;
}) {
  const empty = !rows || rows.length === 0;
  return (
    <div
      data-testid="wms-recent-shipments"
      className="rounded-md border border-border bg-background p-4"
    >
      <h3 className="mb-3 text-sm font-semibold text-foreground">최근 출고</h3>
      {status !== 'ok' ? (
        <p className="text-sm text-muted-foreground">{cellPlaceholder(status)}</p>
      ) : empty ? (
        <p className="text-sm text-muted-foreground">최근 항목이 없습니다.</p>
      ) : (
        <ul className="space-y-2 text-sm">
          {rows?.map((s) => (
            <li
              key={s.shipmentId}
              className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
            >
              <span className="min-w-0 flex-1 truncate text-foreground">
                {s.shipmentNo ?? s.orderNo ?? s.shipmentId}
              </span>
              <span className="shrink-0 text-muted-foreground">
                {s.carrierCode ?? '—'}
              </span>
              <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
                {s.shippedAt ? formatDateTime(s.shippedAt) : '—'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
