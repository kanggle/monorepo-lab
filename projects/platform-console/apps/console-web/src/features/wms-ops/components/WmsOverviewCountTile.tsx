import { cn } from '@/shared/lib/cn';
import type { WmsAreaCount } from '../api/overview-state';
import {
  cellPlaceholder,
  SERVICE_STATUS_DOT,
  SERVICE_STATUS_LABEL,
} from './wms-overview-cell';

/**
 * Per-area count tile presentation for {@link WmsOverview} (TASK-PC-FE-197
 * split). A read-only stat tile (NOT a nav link — `/wms` is a single-route ops
 * screen, PC-FE-168 deviation): a FLOW area (배송) renders 오늘/주간/월간 period
 * buckets + a 전체 total; a LEVEL area (재고) renders a single total + an
 * optional 저재고 attention sub-count; a non-`ok` cell renders a compact
 * "점검 필요" / "권한 없음" placeholder instead of a number (never blanks). Pure
 * presentation — markup + testids preserved verbatim.
 */

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

export function CountTile({ area }: { area: WmsAreaCount }) {
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
