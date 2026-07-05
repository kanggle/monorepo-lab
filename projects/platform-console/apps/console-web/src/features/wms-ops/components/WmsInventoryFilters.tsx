'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { InvFilterState } from './wms-ops-helpers';

/**
 * The 재고 현황 filter form for {@link WmsInventoryTable} (TASK-PC-FE-197 split) —
 * 창고/SKU/위치/로트 ID + 최소 보유 + 저재고만 toggle + 조회 submit. Controlled:
 * the container owns the filter state + the submit that turns it into the list
 * query. Pure presentation — markup + testids preserved verbatim
 * (`wms-inv-filter-*`).
 */
export interface WmsInventoryFiltersProps {
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
}

export function WmsInventoryFilters({
  whFid,
  skuFid,
  lowFid,
  locFid,
  lotFid,
  minFid,
  filters,
  onFiltersChange,
  onSubmit,
}: WmsInventoryFiltersProps) {
  return (
    <form
      onSubmit={onSubmit}
      className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      role="search"
      aria-label="재고 현황 필터"
    >
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
          data-testid="wms-inv-filter-warehouse"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={skuFid}
          className="block text-sm font-medium text-foreground"
        >
          SKU ID
        </label>
        <input
          id={skuFid}
          type="text"
          value={filters.skuId}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, skuId: e.target.value }))
          }
          data-testid="wms-inv-filter-sku"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={locFid}
          className="block text-sm font-medium text-foreground"
        >
          위치 ID
        </label>
        <input
          id={locFid}
          type="text"
          value={filters.locationId}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, locationId: e.target.value }))
          }
          data-testid="wms-inv-filter-location"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={lotFid}
          className="block text-sm font-medium text-foreground"
        >
          로트 ID
        </label>
        <input
          id={lotFid}
          type="text"
          value={filters.lotId}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, lotId: e.target.value }))
          }
          data-testid="wms-inv-filter-lot"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={minFid}
          className="block text-sm font-medium text-foreground"
        >
          최소 보유
        </label>
        <input
          id={minFid}
          type="number"
          value={filters.minOnHand}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, minOnHand: e.target.value }))
          }
          data-testid="wms-inv-filter-minonhand"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div className="flex items-end">
        <label
          htmlFor={lowFid}
          className="flex items-center gap-2 text-sm font-medium text-foreground"
        >
          <input
            id={lowFid}
            type="checkbox"
            checked={filters.lowStockOnly}
            onChange={(e) =>
              onFiltersChange((f) => ({
                ...f,
                lowStockOnly: e.target.checked,
              }))
            }
            data-testid="wms-inv-filter-lowstock"
            className="h-4 w-4 rounded border-border"
          />
          저재고만
        </label>
      </div>
      <div className="flex items-end">
        <Button type="submit" data-testid="wms-inv-filter-submit">
          조회
        </Button>
      </div>
    </form>
  );
}
