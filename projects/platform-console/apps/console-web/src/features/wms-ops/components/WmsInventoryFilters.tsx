'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { InvFilterState } from './wms-ops-helpers';
import { WmsRefPicker } from './WmsRefPicker';

/**
 * The 재고 현황 filter form for {@link WmsInventoryTable} (TASK-PC-FE-197 split) —
 * 창고/SKU/위치/로트 (TASK-PC-FE-333 — `WmsRefPicker`, 코드 · 이름으로 고르면 id 가
 * 필터 상태에 들어간다) + 최소 보유 + 저재고만 toggle + 조회 submit. Controlled:
 * the container owns the filter state + the submit that turns it into the list
 * query. Pure presentation — markup + testids preserved verbatim
 * (`wms-inv-filter-*`; the picker inputs keep the SAME base testid, now
 * suffixed `-input` by `WmsRefPicker`).
 *
 * Edge Cases (TASK-PC-FE-333):
 *   - 위치 후보는 고른 창고로 좁힌다; 창고를 바꾸면 범위 밖 조합을 막기 위해
 *     위치 선택도 같이 해제한다.
 *   - 로트 후보는 고른 SKU 로 좁힌다; SKU 를 바꾸거나 해제하면 로트 선택도
 *     같이 해제한다.
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
        <WmsRefPicker
          id={whFid}
          label="창고"
          type="warehouses"
          value={filters.warehouseId || undefined}
          onChange={(id) =>
            // Edge Case — 창고를 바꾸면 다른 창고의 위치가 남지 않도록 위치
            // 선택도 같이 해제한다(범위 밖 조합 방지).
            onFiltersChange((f) => ({
              ...f,
              warehouseId: id ?? '',
              locationId: '',
            }))
          }
          testid="wms-inv-filter-warehouse"
        />
      </div>
      <div>
        <WmsRefPicker
          id={skuFid}
          label="SKU"
          type="skus"
          value={filters.skuId || undefined}
          onChange={(id) =>
            // Edge Case — SKU 를 바꾸거나 해제하면 로트 선택도 같이 해제한다.
            onFiltersChange((f) => ({ ...f, skuId: id ?? '', lotId: '' }))
          }
          testid="wms-inv-filter-sku"
        />
      </div>
      <div>
        <WmsRefPicker
          id={locFid}
          label="위치"
          type="locations"
          value={filters.locationId || undefined}
          onChange={(id) =>
            onFiltersChange((f) => ({ ...f, locationId: id ?? '' }))
          }
          scope={{ warehouseId: filters.warehouseId || undefined }}
          testid="wms-inv-filter-location"
        />
      </div>
      <div>
        <WmsRefPicker
          id={lotFid}
          label="로트"
          type="lots"
          value={filters.lotId || undefined}
          onChange={(id) => onFiltersChange((f) => ({ ...f, lotId: id ?? '' }))}
          scope={{ skuId: filters.skuId || undefined }}
          testid="wms-inv-filter-lot"
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
