'use client';

import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDate, formatDateTime } from '@/shared/lib/datetime';
import { asnStatusTone } from './wms-ops-helpers';
import type { AsnPage, AsnQueryParams, AsnRow } from '../api/types';

/**
 * The 입고예정(ASN) table + pagination nav for {@link WmsAsnTable}
 * (TASK-PC-FE-222) — rendered only in the loaded/non-empty branch (the
 * parent owns the forbidden / degraded / empty branches). Each row carries a
 * "검수" affordance (`getAsnInspection`, inline panel — not a route, mirrors
 * the `/wms/inventory` composite-key "상세" pattern); pagination keys
 * prev-disabled off the REQUESTED `query.page` while pageinfo + next-disabled
 * key off the RETURNED `data.page`. Pure presentation — markup + testids
 * preserved verbatim (`wms-asn-table`, `wms-asn-row-*`, `wms-asn-status-*`,
 * `wms-asn-inspection-*`, `wms-asn-prev/next/pageinfo`).
 */
export interface WmsAsnDataTableProps {
  data: AsnPage;
  query: AsnQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
  onInspect: (row: AsnRow) => void;
}

export function WmsAsnDataTable({
  data,
  query,
  onPrevPage,
  onNextPage,
  onInspect,
}: WmsAsnDataTableProps) {
  const rows = data.content;
  const totalPages = Math.max(1, data.page.totalPages);

  return (
    <>
      <table className="mb-3 data-table" data-testid="wms-asn-table">
        <caption className="sr-only">입고예정(ASN) 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              ASN 번호
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              창고
            </th>
            <th scope="col" className="p-2">
              공급처
            </th>
            <th scope="col" className="p-2">
              입고예정일
            </th>
            <th scope="col" className="p-2">
              라인 수
            </th>
            <th scope="col" className="p-2">
              접수 시각
            </th>
            <th scope="col" className="p-2">
              <span className="sr-only">검수</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((a, i) => (
            <tr
              key={a.asnId}
              data-testid={`wms-asn-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2">{a.asnNo ?? a.asnId}</td>
              <td className="p-2" data-testid={`wms-asn-status-${i}`}>
                <StatusBadge tone={asnStatusTone(a.status)}>
                  {a.status ?? '—'}
                </StatusBadge>
              </td>
              <td className="p-2">{a.warehouseId ?? '—'}</td>
              <td className="p-2">
                {a.supplierName ?? a.supplierPartnerId ?? '—'}
              </td>
              <td className="p-2">{formatDate(a.expectedArriveDate)}</td>
              <td className="p-2">{a.lineCount ?? '—'}</td>
              <td className="p-2">{formatDateTime(a.receivedAt)}</td>
              <td className="p-2">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => onInspect(a)}
                  data-testid={`wms-asn-inspection-${i}`}
                >
                  검수
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="mb-8 flex items-center justify-between"
        aria-label="입고예정 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={(query.page ?? 0) <= 0}
          onClick={onPrevPage}
          data-testid="wms-asn-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="wms-asn-pageinfo"
        >
          {`${data.page.number + 1} / ${totalPages} 페이지 · 총 ${data.page.totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={data.page.number + 1 >= data.page.totalPages}
          onClick={onNextPage}
          data-testid="wms-asn-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
