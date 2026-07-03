import { cn } from '@/shared/lib/cn';
import type {
  ErpMastersOverviewState,
  ErpAreaCount,
  CellStatus,
} from '../api/overview-state';

/**
 * erp masters operator **overview snapshot** presentation (TASK-PC-FE-161 —
 * follows the PC-FE-166 wms reference; the THINNEST of the 4 bff-domains). Pure
 * presentational component (no `'use client'`, no server-only imports) —
 * rendered server-side and passed as a slot into `ErpMastersScreen`. STRICTLY
 * READ-ONLY.
 *
 * Renders the `getErpMastersOverviewState` fan-out: 5 master count tiles
 * (부서/직원/직급/원가센터/거래처). No distribution / recent feed (erp masters
 * are effective-dated masterdata, not an activity stream). Count tiles are NOT
 * nav links (`/erp` is a single-route masters screen — PC-FE-168 deviation).
 */

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

function CountTile({ area }: { area: ErpAreaCount }) {
  const ok = area.status === 'ok' && area.count !== null;
  return (
    <div className="flex min-w-[9rem] flex-1 flex-col gap-2 rounded-md border border-border bg-background px-4 py-3">
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`erp-${area.key}-service-status`}
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
          data-testid={`erp-${area.key}-count`}
        >
          {area.count!.toLocaleString()}
        </span>
      ) : (
        <span
          className="text-sm font-medium text-muted-foreground"
          data-testid={`erp-${area.key}-count-degraded`}
        >
          {cellPlaceholder(area.status)}
        </span>
      )}
    </div>
  );
}

export function ErpMastersOverview({
  state,
}: {
  state: ErpMastersOverviewState;
}) {
  if (state.notEligible) {
    return null;
  }

  return (
    <section data-testid="erp-overview" aria-label="ERP 마스터 개요" className="mb-8">
      <h2 className="mb-3 text-lg font-semibold text-foreground">운영 개요</h2>
      <div data-testid="erp-overview-counts" className="flex flex-wrap gap-3">
        {state.counts.map((area) => (
          <CountTile key={area.key} area={area} />
        ))}
      </div>
    </section>
  );
}
