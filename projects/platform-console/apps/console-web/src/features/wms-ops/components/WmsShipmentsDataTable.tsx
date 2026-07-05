'use client';

import { Button } from '@/shared/ui/Button';
import type { ShipmentPage, ShipmentQueryParams } from '../api/types';

/**
 * The 출고/택배 table + pagination nav for {@link WmsShipmentsTable}
 * (TASK-PC-FE-197 split) — rendered only in the loaded/non-empty branch (the
 * parent owns the forbidden / degraded / empty branches). Read-only rows (no
 * mutation affordance); pagination keys prev-disabled off the REQUESTED
 * `query.page` while pageinfo + next-disabled key off the RETURNED `data.page`.
 * Pure presentation — markup + testids preserved verbatim (`wms-ship-table`,
 * `wms-ship-row-*`, `wms-ship-carrier-*`, `wms-ship-prev/next/pageinfo`).
 */
export interface WmsShipmentsDataTableProps {
  data: ShipmentPage;
  query: ShipmentQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function WmsShipmentsDataTable({
  data,
  query,
  onPrevPage,
  onNextPage,
}: WmsShipmentsDataTableProps) {
  const shipRows = data.content;
  const shipTotalPages = Math.max(1, data.page.totalPages);

  return (
    <>
      <table className="mb-3 data-table" data-testid="wms-ship-table">
        <caption className="sr-only">출고/택배 내역</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              출고번호
            </th>
            <th scope="col" className="p-2">
              주문번호
            </th>
            <th scope="col" className="p-2">
              택배사
            </th>
            <th scope="col" className="p-2">
              운송장번호
            </th>
            <th scope="col" className="p-2">
              출고시각 (UTC)
            </th>
            <th scope="col" className="p-2">
              수량
            </th>
          </tr>
        </thead>
        <tbody>
          {shipRows.map((s, i) => (
            <tr
              key={s.shipmentId}
              data-testid={`wms-ship-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2">{s.shipmentNo ?? '—'}</td>
              <td className="p-2">{s.orderNo ?? '—'}</td>
              <td className="p-2">
                {s.carrierCode ? (
                  <span data-testid={`wms-ship-carrier-${i}`}>
                    {s.carrierCode}
                  </span>
                ) : (
                  '—'
                )}
              </td>
              <td className="p-2">{s.trackingNo ?? '—'}</td>
              <td className="p-2">{s.shippedAt ?? '—'}</td>
              <td className="p-2">{s.totalQty ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="mb-8 flex items-center justify-between"
        aria-label="출고/택배 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={(query.page ?? 0) <= 0}
          onClick={onPrevPage}
          data-testid="wms-ship-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="wms-ship-pageinfo"
        >
          {`${data.page.number + 1} / ${shipTotalPages} 페이지 · 총 ${data.page.totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={data.page.number + 1 >= data.page.totalPages}
          onClick={onNextPage}
          data-testid="wms-ship-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
