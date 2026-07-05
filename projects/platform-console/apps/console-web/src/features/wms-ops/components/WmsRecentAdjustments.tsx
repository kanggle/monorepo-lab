import { formatDateTime } from '@/shared/lib/datetime';
import type { WmsOverviewState, CellStatus } from '../api/overview-state';
import { cellPlaceholder } from './wms-overview-cell';

/**
 * 최근 재고 조정 (recent-adjustments) glance — the 재고-side activity companion to
 * `WmsRecentShipments` (PC-FE-186). A SEPARATE slot server component fed by the
 * same `getWmsOverviewState` fan-out; the `/wms` page renders it alongside
 * 최근 출고 in the `recentActivity` slot (활동 group), so the page reads
 * 규모(counts) → 주의(알림) → 활동(최근 재고 조정 + 최근 출고). STRICTLY READ-ONLY.
 *
 * ── SPLIT (TASK-PC-FE-197) ── extracted verbatim from `WmsOverview.tsx` into
 * its own module (it is already a distinct barrel export + slot).
 */
export function WmsRecentAdjustments({ state }: { state: WmsOverviewState }) {
  if (state.notEligible) {
    return null;
  }
  return (
    <div className="mt-8">
      <RecentAdjustments
        rows={state.recentAdjustments}
        status={state.recentAdjustmentsStatus}
      />
    </div>
  );
}

function RecentAdjustments({
  rows,
  status,
}: {
  rows: WmsOverviewState['recentAdjustments'];
  status: CellStatus;
}) {
  const empty = !rows || rows.length === 0;
  return (
    <div
      data-testid="wms-recent-adjustments"
      className="rounded-md border border-border bg-background p-4"
    >
      <h3 className="mb-3 text-sm font-semibold text-foreground">
        최근 재고 조정
      </h3>
      {status !== 'ok' ? (
        <p className="text-sm text-muted-foreground">{cellPlaceholder(status)}</p>
      ) : empty ? (
        <p className="text-sm text-muted-foreground">최근 항목이 없습니다.</p>
      ) : (
        <ul className="space-y-2 text-sm">
          {rows?.map((a, i) => (
            <li
              key={a.id ?? `${a.skuId ?? 'sku'}:${a.occurredAt ?? i}`}
              className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
            >
              <span className="min-w-0 flex-1 truncate text-foreground">
                {a.reasonCode ?? '재고 조정'}
              </span>
              <span className="shrink-0 tabular-nums text-muted-foreground">
                {a.bucket ?? '—'}
                {typeof a.delta === 'number'
                  ? ` ${a.delta > 0 ? '+' : ''}${a.delta.toLocaleString()}`
                  : ''}
              </span>
              <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
                {a.occurredAt ? formatDateTime(a.occurredAt) : '—'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
