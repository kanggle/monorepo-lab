'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { messageForCode } from '@/shared/api/errors';
import type { InventoryPage, InventoryQueryParams, InventoryRow } from '../api/types';
import type { InvFilterState } from './wms-ops-helpers';
import { WmsInventoryFilters } from './WmsInventoryFilters';
import { WmsInventoryDataTable } from './WmsInventoryDataTable';

/**
 * Inventory-snapshot region — TASK-PC-FE-173 dedicated `/wms/inventory`
 * screen. Was previously mounted on the `/wms` 개요 screen (TASK-PC-FE-103
 * split); now rendered ONLY by `WmsInventoryScreen` (the query table is
 * unfit for a glance-overview — PC-FE-170 same principle). Free to grow
 * inventory-specific affordances (extra filters/columns/row detail) since
 * it no longer shares a surface with the 개요 screen. Pure presentation:
 * all state + handlers live in the container and arrive via props.
 *
 * ── SPLIT (TASK-PC-FE-197) ── the filter form moved to `WmsInventoryFilters`
 * and the loaded-branch table + pagination to `WmsInventoryDataTable`; this
 * file keeps the heading + the forbidden / degraded / empty / table branching.
 */
export interface WmsInventoryTableProps {
  whFid: string;
  skuFid: string;
  lowFid: string;
  /** TASK-PC-FE-173 — 위치 ID filter input id. */
  locFid: string;
  /** TASK-PC-FE-173 — 로트 ID filter input id. */
  lotFid: string;
  /** TASK-PC-FE-173 — 최소 보유 filter input id. */
  minFid: string;
  filters: InvFilterState;
  onFiltersChange: Dispatch<SetStateAction<InvFilterState>>;
  onSubmit: (e: FormEvent) => void;
  forbidden: boolean;
  degraded: boolean;
  data: InventoryPage;
  query: InventoryQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
  /** TASK-PC-FE-173 — per-row "상세" → composite-key by-key lookup
   *  (the container owns the detail panel state / fetch). */
  onSelect: (row: InventoryRow) => void;
}

export function WmsInventoryTable({
  whFid,
  skuFid,
  lowFid,
  locFid,
  lotFid,
  minFid,
  filters,
  onFiltersChange,
  onSubmit,
  forbidden,
  degraded,
  data,
  query,
  onPrevPage,
  onNextPage,
  onSelect,
}: WmsInventoryTableProps) {
  const invRows = data.content;

  return (
    <>
      {/* ── Inventory ──────────────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 현황
      </h2>
      <WmsInventoryFilters
        whFid={whFid}
        skuFid={skuFid}
        lowFid={lowFid}
        locFid={locFid}
        lotFid={lotFid}
        minFid={minFid}
        filters={filters}
        onFiltersChange={onFiltersChange}
        onSubmit={onSubmit}
      />

      {forbidden ? (
        <div
          role="status"
          data-testid="wms-inv-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="wms-inv-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 재고 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : invRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="wms-inv-empty"
        >
          표시할 재고가 없습니다.
        </p>
      ) : (
        <WmsInventoryDataTable
          data={data}
          query={query}
          onPrevPage={onPrevPage}
          onNextPage={onNextPage}
          onSelect={onSelect}
        />
      )}
    </>
  );
}
