'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import type {
  ThroughputCardStatus,
  WmsThroughputState,
} from '../api/throughput-state';
import { useWmsThroughput } from '../hooks/use-wms-throughput';
import {
  sumThroughput,
  zeroFillThroughput,
  type ThroughputDay,
  type ThroughputTotals,
  type WarehouseOption,
} from '../lib/throughput';
import { cellPlaceholder } from './wms-overview-cell';
import { WmsThroughputChart } from './WmsThroughputChart';

/**
 * `/wms` 개요 **daily inbound/outbound throughput card** (TASK-PC-FE-358 —
 * console-integration-contract § 2.4.5.2 «Daily throughput card»). Seeded by
 * the server `getWmsThroughputState` for the default warehouse; when ≥ 2
 * ACTIVE warehouses exist a selector re-queries the same-origin
 * `/api/wms/throughput` proxy for the chosen one (owner decision (a)).
 *
 * Window = the 14 UTC days ending at the server's UTC today (owner decision
 * (b)), labelled «UTC 기준». Bars = counts (inbound `putawayCount` vs outbound
 * `shipmentCount`), zero-filled; quantities appear as totals text only (owner
 * decision (c)).
 *
 * Degrade is card-local: `forbidden` → «권한 없음», anything else → «점검
 * 필요» (the shared overview vocabulary). A failed switch replaces only the
 * chart body. A `401` on a switch is handled by the client api-client's
 * whole-session re-login (not rendered here).
 */

function warehouseLabel(w: WarehouseOption): string {
  return w.name ? `${w.code} · ${w.name}` : w.code;
}

function statusFromError(err: unknown): ThroughputCardStatus {
  return err instanceof ApiError && err.status === 403 ? 'forbidden' : 'degraded';
}

function Totals({ totals }: { totals: ThroughputTotals }) {
  return (
    <p
      data-testid="wms-throughput-totals"
      className="mt-3 text-xs tabular-nums text-muted-foreground"
    >
      {`합계 — 입고 ${totals.putawayCount.toLocaleString()}건 (수량 ${totals.qtyReceived.toLocaleString()}) · 출고 ${totals.shipmentCount.toLocaleString()}건 (수량 ${totals.qtyShipped.toLocaleString()})`}
    </p>
  );
}

function Notice({ testid, children }: { testid: string; children: React.ReactNode }) {
  return (
    <p data-testid={testid} role="status" className="text-sm text-muted-foreground">
      {children}
    </p>
  );
}

function Body({
  status,
  days,
  totals,
  loading,
}: {
  status: ThroughputCardStatus;
  days: ThroughputDay[] | null;
  totals: ThroughputTotals | null;
  loading: boolean;
}) {
  if (loading) {
    return <Notice testid="wms-throughput-loading">불러오는 중…</Notice>;
  }
  if (status === 'no-warehouses') {
    return (
      <Notice testid="wms-throughput-no-warehouses">
        활성 창고가 없어 처리량을 표시할 수 없습니다.
      </Notice>
    );
  }
  if (status !== 'ok' || days === null || totals === null) {
    return (
      <Notice testid="wms-throughput-unavailable">
        {cellPlaceholder(status === 'forbidden' ? 'forbidden' : 'degraded')}
      </Notice>
    );
  }
  if (totals.putawayCount === 0 && totals.shipmentCount === 0) {
    return (
      <Notice testid="wms-throughput-empty">
        이 기간에 입고 · 출고 기록이 없습니다.
      </Notice>
    );
  }
  return (
    <>
      <WmsThroughputChart days={days} />
      <Totals totals={totals} />
    </>
  );
}

export function WmsThroughputCard({ state }: { state: WmsThroughputState }) {
  const selectId = useId();
  const [selectedId, setSelectedId] = useState<string | null>(
    state.selectedWarehouseId,
  );
  const isDefault = selectedId === state.selectedWarehouseId;
  const q = useWmsThroughput({
    warehouseId: selectedId,
    from: state.from,
    to: state.to,
    enabled: !isDefault,
  });

  const view = useMemo(() => {
    if (isDefault) {
      return { status: state.status, days: state.days, totals: state.totals, loading: false };
    }
    if (q.isError) {
      return { status: statusFromError(q.error), days: null, totals: null, loading: false };
    }
    if (!q.data) {
      return { status: 'ok' as const, days: null, totals: null, loading: true };
    }
    const days = zeroFillThroughput(q.data, state.from, state.to);
    return { status: 'ok' as const, days, totals: sumThroughput(days), loading: false };
  }, [isDefault, q.isError, q.error, q.data, state]);

  if (state.notEligible) return null;

  return (
    <section
      data-testid="wms-throughput"
      aria-label="WMS 일별 입고 · 출고 처리량"
      className="mt-8 rounded-md border border-border bg-background p-4"
    >
      <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-foreground">일별 입고 · 출고</h3>
          <p
            data-testid="wms-throughput-window"
            className="text-xs text-muted-foreground"
          >
            {`최근 14일 · UTC 기준 (${state.from} ~ ${state.to})`}
          </p>
        </div>
        {state.warehouses.length >= 2 ? (
          <div className="flex items-center gap-2 text-sm">
            <label htmlFor={selectId} className="text-muted-foreground">
              창고
            </label>
            <select
              id={selectId}
              data-testid="wms-throughput-warehouse"
              value={selectedId ?? ''}
              onChange={(e) => setSelectedId(e.target.value)}
              className="rounded-md border border-border bg-background px-2 py-1 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {state.warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {warehouseLabel(w)}
                </option>
              ))}
            </select>
          </div>
        ) : state.warehouses.length === 1 ? (
          <p
            data-testid="wms-throughput-warehouse-single"
            className="text-xs text-muted-foreground"
          >
            {`창고 ${warehouseLabel(state.warehouses[0])}`}
          </p>
        ) : null}
      </div>
      <Body {...view} />
    </section>
  );
}
