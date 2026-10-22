import { BarSwatch, StackedBar, type BarSegment } from '@/shared/ui/InlineBar';
import type { OutboundStatusCount } from '../api/types';
import {
  OUTBOUND_FLOW,
  OUTBOUND_SIDE_BRANCHES,
  outboundStatusTone,
} from './outbound-ops-helpers';

/**
 * Order-status pipeline of the `/wms/outbound` screen (TASK-PC-FE-362). No
 * `'use client'` and no server-only import (it renders inside the client
 * `OutboundOpsScreen`); the counts are computed once at SSR time by
 * `getOutboundSectionState` and arrive as a prop — nothing here re-reads.
 *
 * The bar holds the NORMAL flow only, in the contract order
 * (`OUTBOUND_FLOW` — order-status.md § Transitions). `CANCELLED` /
 * `BACKORDERED` are off-flow terminals: numbers beside the bar, never on it.
 * The numbers stay as text — the bar is decorative.
 *
 * Draws no bar when any of the 7 cells is not `ok` (a partial distribution
 * would read as the whole); all 7 zero → a note instead of an empty bar.
 */

/** `null` unless every flow cell is `ok`. */
export function buildOutboundFlowSegments(
  counts: readonly OutboundStatusCount[],
): BarSegment[] | null {
  const byStatus = new Map(counts.map((c) => [c.status, c]));
  const all = [...OUTBOUND_FLOW, ...OUTBOUND_SIDE_BRANCHES];
  if (all.some((s) => {
    const c = byStatus.get(s);
    return !c || c.cellStatus !== 'ok' || c.count === null;
  })) {
    return null;
  }
  return OUTBOUND_FLOW.map((s) => ({
    key: s,
    value: byStatus.get(s)!.count,
    tone: outboundStatusTone(s),
  }));
}

function numberText(c: OutboundStatusCount | undefined): string {
  return c && c.cellStatus === 'ok' && c.count !== null
    ? c.count.toLocaleString()
    : '—';
}

export function OutboundStatusPipeline({
  counts,
}: {
  counts: readonly OutboundStatusCount[];
}) {
  if (counts.length === 0) return null;
  const byStatus = new Map(counts.map((c) => [c.status, c]));
  const segments = buildOutboundFlowSegments(counts);
  const sum = (statuses: readonly string[]) =>
    statuses.reduce((acc, s) => acc + (byStatus.get(s)?.count ?? 0), 0);
  const allZero =
    segments !== null &&
    sum(OUTBOUND_FLOW) + sum(OUTBOUND_SIDE_BRANCHES) <= 0;
  const flowTotal = segments ? sum(OUTBOUND_FLOW) : 0;

  return (
    <div className="mb-6" data-testid="outbound-status-pipeline">
      <h2 className="mb-1 text-sm font-semibold text-foreground">출고 상태</h2>
      <p
        className="mb-3 text-xs text-muted-foreground"
        data-testid="outbound-status-pipeline-scope"
      >
        건수는 로그인한 운영자의 테넌트 범위 주문만 집계합니다 (wms 운영자는
        전체, 고객사 운영자는 자기 회사 주문).
      </p>
      {allZero ? (
        <p
          className="mb-2 text-sm text-muted-foreground"
          data-testid="outbound-status-pipeline-empty"
        >
          출고 주문이 없습니다.
        </p>
      ) : segments && flowTotal > 0 ? (
        <div className="mb-2">
          <StackedBar segments={segments} testid="outbound-status-pipeline-bar" />
        </div>
      ) : null}
      <dl
        data-testid="outbound-status-pipeline-list"
        className="flex flex-wrap gap-x-8 gap-y-3 rounded-md border border-border bg-background px-4 py-4"
      >
        {OUTBOUND_FLOW.map((s) => (
          <div key={s}>
            <dt className="text-xs text-muted-foreground">
              <BarSwatch tone={outboundStatusTone(s)} />
              {s}
            </dt>
            <dd
              className="text-lg font-semibold tabular-nums text-foreground"
              data-testid={`outbound-status-${s}`}
            >
              {numberText(byStatus.get(s))}
            </dd>
          </div>
        ))}
        {OUTBOUND_SIDE_BRANCHES.map((s) => (
          <div key={s} data-testid={`outbound-status-branch-${s}`}>
            <dt className="text-xs text-muted-foreground">{s}</dt>
            <dd
              className="text-lg font-semibold tabular-nums text-foreground"
              data-testid={`outbound-status-${s}`}
            >
              {numberText(byStatus.get(s))}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
