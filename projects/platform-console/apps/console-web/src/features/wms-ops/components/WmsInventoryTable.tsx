'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import { messageForCode } from '@/shared/api/errors';
import type { InventoryPage, InventoryQueryParams } from '../api/types';
import type { InvFilterState } from './wms-ops-helpers';

/**
 * Inventory-snapshot region of the wms ops screen (TASK-PC-FE-103 split) — the
 * filter form + forbidden / degraded / empty notices + the inventory table +
 * pagination nav. Pure presentation: all state + handlers live in the
 * `WmsOpsScreen` container and arrive via props.
 */
export interface WmsInventoryTableProps {
  whFid: string;
  skuFid: string;
  lowFid: string;
  filters: InvFilterState;
  onFiltersChange: Dispatch<SetStateAction<InvFilterState>>;
  onSubmit: (e: FormEvent) => void;
  forbidden: boolean;
  degraded: boolean;
  data: InventoryPage;
  query: InventoryQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function WmsInventoryTable({
  whFid,
  skuFid,
  lowFid,
  filters,
  onFiltersChange,
  onSubmit,
  forbidden,
  degraded,
  data,
  query,
  onPrevPage,
  onNextPage,
}: WmsInventoryTableProps) {
  const invRows = data.content;
  const invTotalPages = Math.max(1, data.page.totalPages);

  return (
    <>
      {/* ── Inventory snapshot ─────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 스냅샷
      </h2>
      <form
        onSubmit={onSubmit}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="재고 스냅샷 필터"
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
          표시할 재고 스냅샷이 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="wms-inv-table"
          >
            <caption className="sr-only">재고 스냅샷</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  위치
                </th>
                <th scope="col" className="p-2">
                  SKU
                </th>
                <th scope="col" className="p-2">
                  로트
                </th>
                <th scope="col" className="p-2">
                  가용
                </th>
                <th scope="col" className="p-2">
                  예약
                </th>
                <th scope="col" className="p-2">
                  보유
                </th>
                <th scope="col" className="p-2">
                  저재고
                </th>
              </tr>
            </thead>
            <tbody>
              {invRows.map((r, i) => (
                <tr
                  key={`${r.locationId}-${r.skuId}-${r.lotId ?? 'nolot'}`}
                  data-testid={`wms-inv-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{r.locationCode ?? r.locationId}</td>
                  <td className="p-2">{r.skuCode ?? r.skuId}</td>
                  <td className="p-2">{r.lotNo ?? r.lotId ?? '—'}</td>
                  <td className="p-2">{r.availableQty ?? '—'}</td>
                  <td className="p-2">{r.reservedQty ?? '—'}</td>
                  <td className="p-2">{r.onHandQty ?? '—'}</td>
                  <td className="p-2">
                    {r.lowStockFlag ? (
                      <span
                        className="rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive"
                        data-testid={`wms-inv-low-${i}`}
                      >
                        저재고
                      </span>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="재고 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={onPrevPage}
              data-testid="wms-inv-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="wms-inv-pageinfo"
            >
              {`${data.page.number + 1} / ${invTotalPages} 페이지 · 총 ${data.page.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={data.page.number + 1 >= data.page.totalPages}
              onClick={onNextPage}
              data-testid="wms-inv-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </>
  );
}
