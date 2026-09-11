'use client';

import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDate, formatDateTime } from '@/shared/lib/datetime';
import { masterRefLabel } from '@/shared/lib/master-ref-label';
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
              {/* 🔴🔴 TASK-PC-FE-281 § 제외 — **이 칸은 바꾸지 마라.** `asnNo` 는 다른
                  엔티티를 가리키는 참조가 아니라 **이 행 자신의 업무 번호**다. 해석할
                  «이름» 이 없으므로 `masterRefLabel` 의 세 상태가 성립하지 않고, 그것을
                  `이름 확인 불가` 로 바꾸면 **이 행을 지목할 방법이 화면에서 사라진다.**
                  ⇒ 여기서는 UUID 폴백이 옳다. `data-master-ref` 도 달지 않는다(모집단 밖). */}
              <td className="p-2">{a.asnNo ?? a.asnId}</td>
              <td className="p-2" data-testid={`wms-asn-status-${i}`}>
                <StatusBadge tone={asnStatusTone(a.status)}>
                  {a.status ?? '—'}
                </StatusBadge>
              </td>
              {/* 🔴 TASK-MONO-659 — `supplierName` 은 옆 칸에서 이미 이름을 그리는데
                  창고만 UUID 였다. 생산자가 이제 `warehouseCode` 를 싣는다. */}
              <td
                className="p-2"
                data-master-ref="asn.warehouseId"
                title={a.warehouseId ?? undefined}
              >
                {masterRefLabel(
                  a.warehouseId,
                  a.warehouseCode ? { code: a.warehouseCode } : null,
                )}
              </td>
              {/* 🔴 TASK-PC-FE-281 — 공급사는 **마스터 참조**다. 🔵 이 칸은 코드가 아니라
                  이름을 싣는다(`supplierName`) — `codeName` 이 «이름만» 도 처리한다. */}
              <td
                className="p-2"
                data-master-ref="asn.supplierPartnerId"
                title={a.supplierPartnerId ?? undefined}
              >
                {masterRefLabel(
                  a.supplierPartnerId,
                  a.supplierName ? { name: a.supplierName } : null,
                )}
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
