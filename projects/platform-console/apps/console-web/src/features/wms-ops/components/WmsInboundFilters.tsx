'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import { ASN_STATUS_FILTER_OPTIONS, type InboundFilterState } from './wms-ops-helpers';

/**
 * The 입고예정(ASN) filter form for {@link WmsAsnTable} (TASK-PC-FE-222) —
 * 상태 select + 창고 ID / 공급처 ID + 입고예정일 from/to (date range, partial
 * input allowed — from-only / to-only) + 조회 submit. Controlled: the
 * container owns the filter state + the submit that turns it into the list
 * query. Pure presentation — markup + testids preserved verbatim
 * (`wms-inbound-filter-*`).
 */
export interface WmsInboundFiltersProps {
  statusFid: string;
  whFid: string;
  supplierFid: string;
  dateFromFid: string;
  dateToFid: string;
  filters: InboundFilterState;
  onFiltersChange: Dispatch<SetStateAction<InboundFilterState>>;
  onSubmit: (e: FormEvent) => void;
}

export function WmsInboundFilters({
  statusFid,
  whFid,
  supplierFid,
  dateFromFid,
  dateToFid,
  filters,
  onFiltersChange,
  onSubmit,
}: WmsInboundFiltersProps) {
  return (
    <form
      onSubmit={onSubmit}
      className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5"
      role="search"
      aria-label="입고예정(ASN) 필터"
    >
      <div>
        <label
          htmlFor={statusFid}
          className="block text-sm font-medium text-foreground"
        >
          상태
        </label>
        <select
          id={statusFid}
          value={filters.status}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, status: e.target.value }))
          }
          data-testid="wms-inbound-filter-status"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {ASN_STATUS_FILTER_OPTIONS.map((s) => (
            <option key={s || 'all'} value={s}>
              {s || '전체'}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label
          htmlFor={whFid}
          className="block text-sm font-medium text-foreground"
        >
          창고 ID
        </label>
        <input
          id={whFid}
          type="text"
          value={filters.warehouseId}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, warehouseId: e.target.value }))
          }
          data-testid="wms-inbound-filter-warehouse"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={supplierFid}
          className="block text-sm font-medium text-foreground"
        >
          공급처 ID
        </label>
        <input
          id={supplierFid}
          type="text"
          value={filters.supplierPartnerId}
          onChange={(e) =>
            onFiltersChange((f) => ({
              ...f,
              supplierPartnerId: e.target.value,
            }))
          }
          data-testid="wms-inbound-filter-supplier"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={dateFromFid}
          className="block text-sm font-medium text-foreground"
        >
          입고예정일(부터)
        </label>
        <input
          id={dateFromFid}
          type="date"
          value={filters.expectedArriveDateFrom}
          onChange={(e) =>
            onFiltersChange((f) => ({
              ...f,
              expectedArriveDateFrom: e.target.value,
            }))
          }
          data-testid="wms-inbound-filter-datefrom"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={dateToFid}
          className="block text-sm font-medium text-foreground"
        >
          입고예정일(까지)
        </label>
        <input
          id={dateToFid}
          type="date"
          value={filters.expectedArriveDateTo}
          onChange={(e) =>
            onFiltersChange((f) => ({
              ...f,
              expectedArriveDateTo: e.target.value,
            }))
          }
          data-testid="wms-inbound-filter-dateto"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div className="flex items-end">
        <Button type="submit" data-testid="wms-inbound-filter-submit">
          조회
        </Button>
      </div>
    </form>
  );
}
