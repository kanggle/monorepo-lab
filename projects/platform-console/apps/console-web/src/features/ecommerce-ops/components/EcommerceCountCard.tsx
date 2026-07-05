import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import type { AreaCount, CellStatus } from '../api/overview-state';
import { cellPlaceholder } from './overview-labels';

/**
 * Per-area count card for the ecommerce operator overview (TASK-PC-FE-199 —
 * extracted from {@link EcommerceOverview}, presentational only). Each card IS
 * the quick-launch `Link`; renders오늘/주간/월간 period metrics + 전체 total
 * (back-compat testids), or a compact "점검 필요" / "권한 없음" placeholder for a
 * non-`ok` cell. All `data-testid`s are unchanged.
 */

/**
 * Per-area service-status indicator — mirrors the console-home "도메인 상태 요약"
 * dot vocabulary (DomainHealthSummaryCard). The cell status IS the per-service
 * signal here: `ok` means the area's list endpoint responded (service reachable +
 * authorized), `forbidden` is a 403 (no operator permission), `degraded` is a
 * 503/timeout/network reach failure. No extra fan-out — this reuses the count
 * cell's own resolved status.
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

export function CountCard({ area }: { area: AreaCount }) {
  const ok = area.status === 'ok' && area.count !== null;
  return (
    <Link
      href={area.href}
      data-testid={area.testid}
      className="flex min-w-[9rem] flex-1 flex-col gap-2 rounded-md border border-border bg-background px-4 py-3 transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`${area.key}-service-status`}
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
        <>
          {/* Period counts — 오늘 / 주간 / 월간 (primary content). */}
          <dl className="flex gap-3">
            <div className="flex flex-col items-center">
              <dt className="text-[0.65rem] text-muted-foreground">오늘</dt>
              <dd
                className="text-lg font-semibold tabular-nums text-foreground"
                data-testid={`${area.key}-count-today`}
              >
                {area.today!.toLocaleString()}
              </dd>
            </div>
            <div className="flex flex-col items-center">
              <dt className="text-[0.65rem] text-muted-foreground">주간</dt>
              <dd
                className="text-lg font-semibold tabular-nums text-foreground"
                data-testid={`${area.key}-count-week`}
              >
                {area.week!.toLocaleString()}
              </dd>
            </div>
            <div className="flex flex-col items-center">
              <dt className="text-[0.65rem] text-muted-foreground">월간</dt>
              <dd
                className="text-lg font-semibold tabular-nums text-foreground"
                data-testid={`${area.key}-count-month`}
              >
                {area.month!.toLocaleString()}
              </dd>
            </div>
          </dl>
          {/* Total (secondary context — back-compat testid `<key>-count`). */}
          <span className="text-xs text-muted-foreground">
            전체{' '}
            <span
              className="font-medium tabular-nums text-foreground"
              data-testid={`${area.key}-count`}
            >
              {area.count!.toLocaleString()}
            </span>
          </span>
        </>
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
