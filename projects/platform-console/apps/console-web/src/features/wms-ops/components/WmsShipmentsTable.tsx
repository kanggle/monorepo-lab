'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import { messageForCode } from '@/shared/api/errors';
import type { ShipmentPage, ShipmentQueryParams } from '../api/types';
import type { ShipFilterState } from './wms-ops-helpers';

/**
 * Shipments (택배/출고) region of the wms ops screen (TASK-PC-FE-103 split) —
 * the carrier/warehouse filter form + forbidden / degraded / empty notices +
 * the shipments table + pagination nav. Read-only. Pure presentation: all
 * state + handlers live in the `WmsOpsScreen` container and arrive via props.
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
  const shipTotalPages = Math.max(1, data.page.totalPages);

  return (
    <>
      {/* ── Shipments (택배/출고 read — carrier code / tracking no) ─────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">택배 / 출고</h2>
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
      )}
    </>
  );
}
