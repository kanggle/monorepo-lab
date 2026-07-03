import { cn } from '@/shared/lib/cn';
import { formatDateTime } from '@/shared/lib/datetime';
import type {
  WmsOverviewState,
  WmsAreaCount,
  CellStatus,
} from '../api/overview-state';

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
 */

function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

/**
 * Per-area service-status indicator — mirrors the ecommerce overview / the
 * console-home "도메인 상태 요약" dot vocabulary. The cell status IS the
 * per-area signal: `ok` = the area's list read responded, `forbidden` = 403,
 * `degraded` = 503/timeout/network reach failure. No extra fan-out.
 */
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

/** One 오늘/주간/월간 period bucket (renders "—" for an unresolved sub-read). */
function PeriodBucket({
  label,
  value,
  testid,
}: {
  label: string;
  value: number | null;
  testid: string;
}) {
  return (
    <div className="flex flex-col items-center">
      <dt className="text-[0.65rem] text-muted-foreground">{label}</dt>
      <dd
        className="text-lg font-semibold tabular-nums text-foreground"
        data-testid={testid}
      >
        {value !== null ? value.toLocaleString() : '—'}
      </dd>
    </div>
  );
}

function CountTile({ area }: { area: WmsAreaCount }) {
  const ok = area.status === 'ok' && area.count !== null;
  return (
    <div className="flex min-w-[9rem] flex-1 flex-col gap-2 rounded-md border border-border bg-background px-4 py-3">
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`wms-${area.key}-service-status`}
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
      {!ok ? (
        <span
          className="text-sm font-medium text-muted-foreground"
          data-testid={`wms-${area.key}-count-degraded`}
        >
          {cellPlaceholder(area.status)}
        </span>
      ) : area.period ? (
        <>
          {/* FLOW area (배송) — 오늘/주간/월간 period-to-date (primary). */}
          <dl className="flex gap-3">
            <PeriodBucket
              label="오늘"
              value={area.period.today}
              testid={`wms-${area.key}-count-today`}
            />
            <PeriodBucket
              label="주간"
              value={area.period.week}
              testid={`wms-${area.key}-count-week`}
            />
            <PeriodBucket
              label="월간"
              value={area.period.month}
              testid={`wms-${area.key}-count-month`}
            />
          </dl>
          {/* 전체 total (secondary — back-compat testid `wms-<key>-count`). */}
          <span className="text-xs text-muted-foreground">
            전체{' '}
            <span
              className="font-medium tabular-nums text-foreground"
              data-testid={`wms-${area.key}-count`}
            >
              {area.count!.toLocaleString()}
            </span>
          </span>
        </>
      ) : (
        /* LEVEL area (재고) — single-total snapshot (no time dimension) + an
           optional 저재고 attention sub-count (PC-FE-177). */
        <>
          <span
            className="text-2xl font-semibold tabular-nums text-foreground"
            data-testid={`wms-${area.key}-count`}
          >
            {area.count!.toLocaleString()}
          </span>
          {area.lowStock !== undefined && (
            <span
              className={cn(
                'text-xs',
                area.lowStock !== null && area.lowStock > 0
                  ? 'font-medium text-amber-600 dark:text-amber-500'
                  : 'text-muted-foreground',
              )}
            >
              저재고{' '}
              <span
                className="tabular-nums"
                data-testid={`wms-${area.key}-lowstock`}
              >
                {area.lowStock !== null ? area.lowStock.toLocaleString() : '—'}
              </span>
            </span>
          )}
        </>
      )}
    </div>
  );
}

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

/**
 * 최근 출고 (recent-shipments) glance — a passive activity glance rendered as a
 * SEPARATE slot (PC-FE-177) so the 개요 reads 규모(counts) → 주의(알림 분포 +
 * 알림 테이블) → 활동(최근 출고): the page passes this as `WmsOpsScreen`'s
 * `recentActivity` slot, rendered AFTER the alerts table, so the alert
 * distribution + table are contiguous and this passive glance sits last.
 * Server component (no `'use client'`); fed by the same `getWmsOverviewState`.
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
