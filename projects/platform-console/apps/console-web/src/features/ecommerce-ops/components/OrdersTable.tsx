'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDateTime } from '@/shared/lib/datetime';
import { orderStatusTone, type OrderList } from '../api/order-types';

interface OrdersTableProps {
  rows: OrderList['content'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Order list table + pagination (TASK-PC-FE-199 — extracted from
 * {@link OrdersScreen}, presentational only). Per-row action: 상세(drill).
 * Query/filter state stays owned by `OrdersScreen`; all `data-testid`s are
 * unchanged.
 */
export function OrdersTable({ rows, pagination }: OrdersTableProps) {
  return (
    <>
      <table className="mb-3 data-table" data-testid="order-table">
        <caption className="sr-only">주문 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              주문 ID
            </th>
            <th scope="col" className="p-2">
              첫 번째 상품
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              총액
            </th>
            <th scope="col" className="p-2">
              주문일
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((o, i) => (
            <tr
              key={o.orderId}
              data-testid={`order-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2 text-xs break-all">{o.orderId}</td>
              <td className="p-2">
                {o.firstItemName}
                {o.itemCount > 1 && (
                  <span className="ml-1 text-xs text-muted-foreground">
                    외 {o.itemCount - 1}건
                  </span>
                )}
              </td>
              <td
                className="p-2"
                data-testid={`order-row-status-${i}`}
              >
                <StatusBadge tone={orderStatusTone(o.status)}>
                  {o.status}
                </StatusBadge>
              </td>
              <td className="p-2">
                {o.totalPrice.toLocaleString('ko-KR')}원
              </td>
              <td className="p-2 text-sm text-muted-foreground">
                {formatDateTime(o.createdAt)}
              </td>
              <td className="p-2">
                <Link href={`/ecommerce/orders/${o.orderId}`}>
                  <Button
                    variant="secondary"
                    size="sm"
                    data-testid={`order-detail-${i}`}
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
        aria-label="주문 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="order-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="order-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="order-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
