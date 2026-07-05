'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { ShipFilterState } from './wms-ops-helpers';

/**
 * The 택배/출고 carrier/warehouse filter form for {@link WmsShipmentsTable}
 * (TASK-PC-FE-197 split) — includes the read-only intro copy above the form.
 * Controlled: the container owns the filter state + the submit that turns it
 * into the list query. Pure presentation — markup + testids preserved verbatim
 * (`wms-ship-filter-*`).
 */
export interface WmsShipmentsFiltersProps {
  shipWhFid: string;
  shipCarrierFid: string;
  filters: ShipFilterState;
  onFiltersChange: Dispatch<SetStateAction<ShipFilterState>>;
  onSubmit: (e: FormEvent) => void;
}

export function WmsShipmentsFilters({
  shipWhFid,
  shipCarrierFid,
  filters,
  onFiltersChange,
  onSubmit,
}: WmsShipmentsFiltersProps) {
  return (
    <>
      <p className="mb-4 text-sm text-muted-foreground">
        출고 확정된 화물의 택배사 · 운송장번호 · 출고시각 (읽기 전용 — 출고
        확정은 출고 운영 화면에서 수행합니다).
      </p>
      <form
        onSubmit={onSubmit}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
        role="search"
        aria-label="택배/출고 필터"
      >
        <div>
          <label
            htmlFor={shipWhFid}
            className="block text-sm font-medium text-foreground"
          >
            창고 ID
          </label>
          <input
            id={shipWhFid}
            type="text"
            value={filters.warehouseId}
            onChange={(e) =>
              onFiltersChange((f) => ({ ...f, warehouseId: e.target.value }))
            }
            data-testid="wms-ship-filter-warehouse"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div>
          <label
            htmlFor={shipCarrierFid}
            className="block text-sm font-medium text-foreground"
          >
            택배사 코드
          </label>
          <input
            id={shipCarrierFid}
            type="text"
            value={filters.carrierCode}
            onChange={(e) =>
              onFiltersChange((f) => ({ ...f, carrierCode: e.target.value }))
            }
            data-testid="wms-ship-filter-carrier"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="wms-ship-filter-submit">
            조회
          </Button>
        </div>
      </form>
    </>
  );
}
