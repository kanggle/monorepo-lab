'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { messageForCode } from '@/shared/api/errors';
import type { AsnPage, AsnQueryParams, AsnRow } from '../api/types';
import type { InboundFilterState } from './wms-ops-helpers';
import { WmsInboundFilters } from './WmsInboundFilters';
import { WmsAsnDataTable } from './WmsAsnDataTable';

/**
 * 입고예정(ASN) region — TASK-PC-FE-222 dedicated `/wms/inbound` screen (the
 * console's first surface for the "front half" of the inbound flow: ASN
 * received → 검수 (inspection); putaway write ops are explicitly OUT of
 * scope — the admin read-model does not project them). Free-standing
 * (mirrors `WmsInventoryTable` / `OutboundOrdersTable`): all state + handlers
 * live in the `WmsInboundScreen` container and arrive via props.
 */
export interface WmsAsnTableProps {
  statusFid: string;
  whFid: string;
  supplierFid: string;
  dateFromFid: string;
  dateToFid: string;
  filters: InboundFilterState;
  onFiltersChange: Dispatch<SetStateAction<InboundFilterState>>;
  onSubmit: (e: FormEvent) => void;
  forbidden: boolean;
  degraded: boolean;
  data: AsnPage;
  query: AsnQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
  /** TASK-PC-FE-222 — per-row "검수" → `getAsnInspection` lookup (the
   *  container owns the detail panel state / fetch). */
  onInspect: (row: AsnRow) => void;
}

export function WmsAsnTable({
  statusFid,
  whFid,
  supplierFid,
  dateFromFid,
  dateToFid,
  filters,
  onFiltersChange,
  onSubmit,
  forbidden,
  degraded,
  data,
  query,
  onPrevPage,
  onNextPage,
  onInspect,
}: WmsAsnTableProps) {
  const rows = data.content;

  return (
    <>
      {/* ── ASN (입고예정) ─────────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">입고예정 (ASN)</h2>
      <WmsInboundFilters
        statusFid={statusFid}
        whFid={whFid}
        supplierFid={supplierFid}
        dateFromFid={dateFromFid}
        dateToFid={dateToFid}
        filters={filters}
        onFiltersChange={onFiltersChange}
        onSubmit={onSubmit}
      />

      {forbidden ? (
        <div
          role="status"
          data-testid="wms-asn-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="wms-asn-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 입고 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="wms-asn-empty"
        >
          표시할 입고예정(ASN)이 없습니다.
        </p>
      ) : (
        <WmsAsnDataTable
          data={data}
          query={query}
          onPrevPage={onPrevPage}
          onNextPage={onNextPage}
          onInspect={onInspect}
        />
      )}
    </>
  );
}
