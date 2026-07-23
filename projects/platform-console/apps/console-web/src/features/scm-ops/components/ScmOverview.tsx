import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import { formatDateTime } from '@/shared/lib/datetime';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import type {
  ScmOverviewState,
  ScmAreaCount,
  CellStatus,
} from '../api/overview-state';
import { S5Warning } from './S5Warning';
import { poStatusTone } from './scm-ops-helpers';

/**
 * scm operator **overview snapshot** presentation (TASK-PC-FE-167 — follows
 * the PC-FE-166 wms reference). Pure presentational component (no `'use
 * client'`, no server-only imports) — rendered server-side directly by the
 * `/scm` 개요 page (TASK-PC-FE-220: 개요 now shows ONLY this overview band).
 * STRICTLY READ-ONLY.
 *
 * Renders the `getScmOverviewState` fan-out: per-area count tiles (발주/재고
 * 스냅샷), a PO-status distribution, and a recent-PO glance. When the 재고
 * 스냅샷 count resolved, the REQUIRED S5 warning is rendered PROMINENTLY via
 * `<S5Warning>` (§ 2.4.6 obligation — never stripped). Count tiles are NOT nav
 * links (`/scm` is a single-route ops screen — PC-FE-168 deviation).
 */

/** Korean labels for the PO-status distribution buckets. */
const PO_STATUS_LABELS: Record<string, string> = {
  DRAFT: '초안',
  SUBMITTED: '제출',
  ACKNOWLEDGED: '접수',
  CONFIRMED: '확정',
  PARTIALLY_RECEIVED: '부분입고',
  RECEIVED: '입고',
  SETTLED: '정산',
  CLOSED: '마감',
  CANCELED: '취소',
};

function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

const SERVICE_STATUS_DOT: Record<CellStatus, string> = {
  ok: 'bg-green-500',
  degraded: 'bg-red-500',
  forbidden: 'bg-muted-foreground/40',
};
const SERVICE_STATUS_LABEL: Record<CellStatus, string> = {
  ok: '정상',
  degraded: '점검 필요',
  forbidden: '권한 없음',
};

function CountTile({ area }: { area: ScmAreaCount }) {
  const ok = area.status === 'ok' && area.count !== null;
  return (
    <div className="flex min-w-[9rem] flex-1 flex-col gap-2 rounded-md border border-border bg-background px-4 py-3">
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`scm-${area.key}-service-status`}
        data-status={area.status}
      >
        <span
          className={cn(
            'h-1.5 w-1.5 shrink-0 rounded-full',
            SERVICE_STATUS_DOT[area.status],
          )}
          aria-hidden="true"
        />
        {area.label}
        <span className="sr-only">
          서비스 상태: {SERVICE_STATUS_LABEL[area.status]}
        </span>
      </span>
      {ok ? (
        <span
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid={`scm-${area.key}-count`}
        >
          {area.count!.toLocaleString()}
        </span>
      ) : (
        <span
          className="text-sm font-medium text-muted-foreground"
          data-testid={`scm-${area.key}-count-degraded`}
        >
          {cellPlaceholder(area.status)}
        </span>
      )}
    </div>
  );
}

export function ScmOverview({ state }: { state: ScmOverviewState }) {
  if (state.notEligible) {
    return null;
  }

  return (
    <section data-testid="scm-overview" aria-label="SCM 운영 개요" className="mb-8">
      <h2 className="mb-3 text-lg font-semibold text-foreground">운영 개요</h2>

      <nav
        aria-label="SCM 화면 바로가기"
        className="mb-6 flex flex-wrap gap-4 text-sm"
      >
        <Link
          href="/scm/guide"
          data-testid="scm-overview-link-guide"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          가이드
        </Link>
      </nav>

      {/* S5 obligation — surfaced prominently whenever inventory-visibility
          data (the 재고 스냅샷 count) is shown (§ 2.4.6). */}
      {state.s5Warning && <S5Warning warning={state.s5Warning} />}

      {/* Per-area count tiles — read-only stat tiles (no drill-in routes). */}
      <div data-testid="scm-overview-counts" className="mb-6 flex flex-wrap gap-3">
        {state.counts.map((area) => (
          <CountTile key={area.key} area={area} />
        ))}
      </div>

      {/* PO-status distribution. */}
      <div className="mb-6">
        <h3 className="mb-2 text-sm font-semibold text-foreground">발주 상태</h3>
        <dl
          data-testid="scm-po-status"
          className="flex flex-wrap gap-x-8 gap-y-3 rounded-md border border-border bg-background px-4 py-4"
        >
          {state.poStatus.map((bucket) => (
            <div key={bucket.status}>
              <dt className="text-xs text-muted-foreground">
                {PO_STATUS_LABELS[bucket.status] ?? bucket.status}
              </dt>
              <dd
                className="text-lg font-semibold tabular-nums text-foreground"
                data-testid={`scm-po-status-${bucket.status}`}
              >
                {bucket.cellStatus === 'ok' && bucket.count !== null
                  ? bucket.count.toLocaleString()
                  : '—'}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      {/* Recent activity — purchase orders. */}
      <RecentPurchaseOrders
        rows={state.recentPos}
        status={state.recentPosStatus}
      />
    </section>
  );
}

function RecentPurchaseOrders({
  rows,
  status,
}: {
  rows: ScmOverviewState['recentPos'];
  status: CellStatus;
}) {
  const empty = !rows || rows.length === 0;
  return (
    <div
      data-testid="scm-recent-pos"
      className="rounded-md border border-border bg-background p-4"
    >
      <h3 className="mb-3 text-sm font-semibold text-foreground">최근 발주</h3>
      {status !== 'ok' ? (
        <p className="text-sm text-muted-foreground">{cellPlaceholder(status)}</p>
      ) : empty ? (
        <p className="text-sm text-muted-foreground">최근 항목이 없습니다.</p>
      ) : (
        <ul className="space-y-2 text-sm">
          {rows?.map((po) => (
            <li
              key={po.id}
              className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
            >
              <span className="min-w-0 flex-1 truncate text-foreground">
                {po.poNumber ?? po.id}
              </span>
              <StatusBadge tone={poStatusTone(po.status)} className="shrink-0">
                {po.status ? (PO_STATUS_LABELS[po.status] ?? po.status) : '—'}
              </StatusBadge>
              {po.totalAmount ? (
                <span className="shrink-0 tabular-nums text-foreground">
                  {po.totalAmount}
                  {po.currency ? ` ${po.currency}` : ''}
                </span>
              ) : null}
              <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
                {po.createdAt ? formatDateTime(po.createdAt) : '—'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
