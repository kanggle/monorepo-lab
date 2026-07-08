'use client';

import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDateTime } from '@/shared/lib/datetime';
import type { GenericRow, RefPage, RefQueryParams, RefType } from '../api/types';
import {
  refCode,
  refLastEventAt,
  refName,
  refRowKey,
  refStatus,
  refStatusTone,
} from './wms-master-helpers';

/**
 * The 마스터 참조 데이터 table + pagination nav for {@link WmsMasterTable}
 * (TASK-PC-FE-223) — rendered only in the loaded/non-empty branch (the
 * parent owns the forbidden / degraded / empty branches). The row shape is
 * the producer's generic `RefPage` content (§ 1.7 `*Ref` row, tolerant
 * `GenericRow`) — columns render the common denormalised fields (코드/명칭/
 * 상태/최근 갱신) via the `wms-master-helpers` field extractors, never a
 * per-type hardcoded column set. Pure presentation — mirrors
 * `WmsAsnDataTable`'s pagination shape (prev-disabled off the REQUESTED
 * `query.page`, pageinfo + next-disabled off the RETURNED `data.page`).
 */
export interface WmsMasterDataTableProps {
  type: RefType;
  data: RefPage;
  query: RefQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function WmsMasterDataTable({
  type,
  data,
  query,
  onPrevPage,
  onNextPage,
}: WmsMasterDataTableProps) {
  const rows = data.content as GenericRow[];
  const totalPages = Math.max(1, data.page.totalPages);

  return (
    <>
      <table className="mb-3 data-table" data-testid="wms-master-table">
        <caption className="sr-only">마스터 참조 데이터 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              코드
            </th>
            <th scope="col" className="p-2">
              명칭
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              최근 갱신
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr
              key={refRowKey(type, row, i)}
              data-testid={`wms-master-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2">{refCode(type, row)}</td>
              <td className="p-2">{refName(row) ?? '—'}</td>
              <td className="p-2" data-testid={`wms-master-status-${i}`}>
                <StatusBadge tone={refStatusTone(refStatus(row))}>
                  {refStatus(row) ?? '—'}
                </StatusBadge>
              </td>
              <td className="p-2">{formatDateTime(refLastEventAt(row))}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="mb-8 flex items-center justify-between"
        aria-label="마스터 참조 데이터 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={(query.page ?? 0) <= 0}
          onClick={onPrevPage}
          data-testid="wms-master-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="wms-master-pageinfo"
        >
          {`${data.page.number + 1} / ${totalPages} 페이지 · 총 ${data.page.totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={data.page.number + 1 >= data.page.totalPages}
          onClick={onNextPage}
          data-testid="wms-master-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
