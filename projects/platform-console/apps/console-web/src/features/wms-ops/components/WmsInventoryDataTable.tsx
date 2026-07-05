'use client';

import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import type { InventoryPage, InventoryQueryParams, InventoryRow } from '../api/types';

/**
 * The 재고 현황 table + pagination nav for {@link WmsInventoryTable}
 * (TASK-PC-FE-197 split) — rendered only in the loaded/non-empty branch (the
 * parent owns the forbidden / degraded / empty branches). Read-only rows with a
 * per-row "상세" → composite-key lookup callback; pagination keys prev-disabled
 * off the REQUESTED `query.page` while pageinfo + next-disabled key off the
 * RETURNED `data.page`. Pure presentation — markup + testids preserved verbatim
 * (`wms-inv-table`, `wms-inv-row-*`, `wms-inv-low-*`, `wms-inv-detail-*`,
 * `wms-inv-prev/next/pageinfo`).
 */
export interface WmsInventoryDataTableProps {
  data: InventoryPage;
  query: InventoryQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
  onSelect: (row: InventoryRow) => void;
}

export function WmsInventoryDataTable({
  data,
  query,
  onPrevPage,
  onNextPage,
  onSelect,
}: WmsInventoryDataTableProps) {
  const invRows = data.content;
  const invTotalPages = Math.max(1, data.page.totalPages);

  return (
    <>
      <table
        className="mb-3 data-table"
        data-testid="wms-inv-table"
      >
        <caption className="sr-only">재고 현황</caption>
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
              손상
            </th>
            <th scope="col" className="p-2">
              최근 조정
            </th>
            <th scope="col" className="p-2">
              저재고
            </th>
            <th scope="col" className="p-2">
              <span className="sr-only">상세</span>
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
              <td className="p-2">{r.damagedQty ?? '—'}</td>
              <td className="p-2">
                {r.lastAdjustedAt ? formatDateTime(r.lastAdjustedAt) : '—'}
              </td>
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
              <td className="p-2">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => onSelect(r)}
                  data-testid={`wms-inv-detail-${i}`}
                >
                  상세
                </Button>
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
  );
}
