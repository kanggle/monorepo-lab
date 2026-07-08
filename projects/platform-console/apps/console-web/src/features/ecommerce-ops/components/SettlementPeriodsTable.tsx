'use client';

import Link from 'next/link';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { Button } from '@/shared/ui/Button';
import { formatDate, formatDateTime } from '@/shared/lib/datetime';
import {
  periodStatusTone,
  type PeriodsResponse,
} from '../api/settlement-types';

interface SettlementPeriodsTableProps {
  rows: PeriodsResponse['items'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Settlement periods table + pagination (TASK-PC-FE-221 Phase A, presentational
 * only). Each row drills into the period's payouts detail
 * (`/ecommerce/settlements/periods/{periodId}`). The from/to bounds are
 * day-granular (`formatDate`); `closedAt` is a record timestamp (`formatDateTime`).
 */
export function SettlementPeriodsTable({
  rows,
  pagination,
}: SettlementPeriodsTableProps) {
  return (
    <>
      <div className="overflow-x-auto">
        <table
          className="mb-3 data-table"
          data-testid="settlements-periods-table"
        >
          <caption className="sr-only">정산 기간 목록</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                기간 ID
              </th>
              <th scope="col" className="p-2">
                시작일
              </th>
              <th scope="col" className="p-2">
                종료일
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                마감일시
              </th>
              <th scope="col" className="p-2 text-right">
                셀러 수
              </th>
              <th scope="col" className="p-2">
                작업
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p, i) => (
              <tr
                key={p.periodId}
                data-testid={`period-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2 font-mono text-xs">{p.periodId}</td>
                <td className="p-2 text-sm text-muted-foreground">
                  {formatDate(p.from)}
                </td>
                <td className="p-2 text-sm text-muted-foreground">
                  {formatDate(p.to)}
                </td>
                <td className="p-2" data-testid={`period-row-status-${i}`}>
                  <StatusBadge tone={periodStatusTone(p.status)}>
                    {p.status}
                  </StatusBadge>
                </td>
                <td className="p-2 text-sm text-muted-foreground">
                  {formatDateTime(p.closedAt)}
                </td>
                <td className="p-2 text-right tabular-nums">
                  {p.sellerCount ?? '—'}
                </td>
                <td className="p-2">
                  <Link href={`/ecommerce/settlements/periods/${p.periodId}`}>
                    <Button
                      variant="secondary"
                      size="sm"
                      data-testid={`period-detail-${i}`}
                    >
                      지급 내역
                    </Button>
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav
        className="flex items-center justify-between"
        aria-label="정산 기간 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="settlements-periods-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="settlements-periods-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="settlements-periods-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
