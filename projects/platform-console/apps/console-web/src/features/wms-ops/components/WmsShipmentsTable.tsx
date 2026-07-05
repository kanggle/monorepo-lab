'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { messageForCode } from '@/shared/api/errors';
import type { ShipmentPage, ShipmentQueryParams } from '../api/types';
import type { ShipFilterState } from './wms-ops-helpers';
import { WmsShipmentsFilters } from './WmsShipmentsFilters';
import { WmsShipmentsDataTable } from './WmsShipmentsDataTable';

/**
 * Shipments (택배/출고) region of the wms ops screen (TASK-PC-FE-103 split) —
 * the carrier/warehouse filter form + forbidden / degraded / empty notices +
 * the shipments table + pagination nav. Read-only. Pure presentation: all
 * state + handlers live in the `WmsOpsScreen` container and arrive via props.
 *
 * ── SPLIT (TASK-PC-FE-197) ── the filter form (+ intro copy) moved to
 * `WmsShipmentsFilters` and the loaded-branch table + pagination to
 * `WmsShipmentsDataTable`; this file keeps the heading + the forbidden /
 * degraded / empty / table branching.
 */
export interface WmsShipmentsTableProps {
  shipWhFid: string;
  shipCarrierFid: string;
  filters: ShipFilterState;
  onFiltersChange: Dispatch<SetStateAction<ShipFilterState>>;
  onSubmit: (e: FormEvent) => void;
  forbidden: boolean;
  degraded: boolean;
  data: ShipmentPage;
  query: ShipmentQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function WmsShipmentsTable({
  shipWhFid,
  shipCarrierFid,
  filters,
  onFiltersChange,
  onSubmit,
  forbidden,
  degraded,
  data,
  query,
  onPrevPage,
  onNextPage,
}: WmsShipmentsTableProps) {
  const shipRows = data.content;

  return (
    <>
      {/* ── Shipments (택배/출고 read — carrier code / tracking no) ─────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">택배 / 출고</h2>
      <WmsShipmentsFilters
        shipWhFid={shipWhFid}
        shipCarrierFid={shipCarrierFid}
        filters={filters}
        onFiltersChange={onFiltersChange}
        onSubmit={onSubmit}
      />

      {forbidden ? (
        <div
          role="status"
          data-testid="wms-ship-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="wms-ship-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 출고/택배 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : shipRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="wms-ship-empty"
        >
          표시할 출고/택배 내역이 없습니다.
        </p>
      ) : (
        <WmsShipmentsDataTable
          data={data}
          query={query}
          onPrevPage={onPrevPage}
          onNextPage={onNextPage}
        />
      )}
    </>
  );
}
