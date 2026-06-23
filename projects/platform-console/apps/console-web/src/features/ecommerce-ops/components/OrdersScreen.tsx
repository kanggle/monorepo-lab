'use client';

import { useId, useState } from 'react';
import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useOrders } from '../hooks/use-ecommerce-orders';
import {
  ORDER_DEFAULT_PAGE_SIZE,
  ORDER_STATUS_VALUES,
  type OrderList,
  type OrderListParams,
} from '../api/order-types';

/**
 * ecommerce order operations list section (TASK-PC-FE-083 — § 2.4.10 #15).
 * The console equivalent of the `admin-dashboard` order list screen.
 *
 * Server-rendered initial page is passed in; client re-query handles
 * status-filter / pagination. Per-row action: 상세(drill).
 *
 * Resilience (§ 2.5): 401 handled by the server route (whole-session
 * re-login); 403 → inline actionable; 503/timeout → this section degrades
 * only.
 */

export interface OrdersScreenProps {
  orders: OrderList;
}

const STATUS_FILTER_OPTIONS = ['', ...ORDER_STATUS_VALUES] as const;

export function OrdersScreen({ orders }: OrdersScreenProps) {
  const statusFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [query, setQuery] = useState<OrderListParams>({
    page: 0,
    size: orders.size || ORDER_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0 && !query.status;
  const listQ = useOrders(query, seeded ? orders : undefined);
  const data = listQ.data ?? orders;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      page: 0,
      size: orders.size || ORDER_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data.content;
  const totalPages = Math.max(
    1,
    Math.ceil(data.totalElements / (data.size || 20)),
  );

  return (
    <section aria-labelledby="ecommerce-orders-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1 id="ecommerce-orders-heading" className="text-2xl font-semibold">
          E-Commerce 주문
        </h1>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        주문 목록 · 상세(아이템 + 배송지) · 상태 전이. admin-dashboard 주문 화면을
        콘솔에서 운영합니다.
      </p>

      <form
        onSubmit={submitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="주문 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="order-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="order-filter-submit">
          조회
        </Button>
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="order-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="order-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 주문 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="order-empty">
          표시할 주문이 없습니다.
        </p>
      ) : (
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
                    {o.status}
                  </td>
                  <td className="p-2">
                    {o.totalPrice.toLocaleString('ko-KR')}원
                  </td>
                  <td className="p-2 text-sm text-muted-foreground">
                    {new Date(o.createdAt).toLocaleDateString('ko-KR')}
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
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="order-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="order-pageinfo"
            >
              {`${data.page + 1} / ${totalPages} 페이지 · 총 ${data.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= totalPages}
              onClick={() =>
                setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="order-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
