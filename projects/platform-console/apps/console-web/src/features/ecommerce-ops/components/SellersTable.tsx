'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDateTime } from '@/shared/lib/datetime';
import { sellerStatusTone, type SellerList } from '../api/seller-types';

interface SellersTableProps {
  rows: SellerList['content'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Seller list table + pagination (TASK-PC-FE-200 — extracted from
 * {@link SellersScreen}, presentational only). READ display only per-row
 * action: 상세(drill). Query/pagination state stays owned by `SellersScreen`;
 * all `data-testid`s are unchanged.
 */
export function SellersTable({ rows, pagination }: SellersTableProps) {
  return (
    <>
      <table
        className="mb-3 data-table"
        data-testid="seller-table"
      >
        <caption className="sr-only">셀러 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              셀러 ID
            </th>
            <th scope="col" className="p-2">
              셀러 이름
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              등록일
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((s, i) => (
            <tr
              key={s.sellerId}
              data-testid={`seller-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2 font-mono text-xs">{s.sellerId}</td>
              <td className="p-2">{s.displayName}</td>
              <td className="p-2" data-testid={`seller-row-status-${i}`}>
                <StatusBadge tone={sellerStatusTone(s.status)}>
                  {s.status}
                </StatusBadge>
              </td>
              <td className="p-2 text-sm text-muted-foreground">
                {formatDateTime(s.createdAt)}
              </td>
              <td className="p-2">
                <Link
                  href={`/ecommerce/sellers/${s.sellerId}`}
                >
                  <Button
                    variant="secondary"
                    size="sm"
                    data-testid={`seller-detail-${i}`}
                  >
                    상세
                  </Button>
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="flex items-center justify-between"
        aria-label="셀러 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="seller-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="seller-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="seller-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
