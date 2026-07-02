'use client';

import { useRouter } from 'next/navigation';
import { useOrder } from '../hooks/use-ecommerce-orders';
import type { OrderDetail as OrderDetailType } from '../api/order-types';
import { OrderStatusDialog } from './OrderStatusDialog';
import { DetailHeader } from './DetailHeader';
import { formatDateTime } from '@/shared/lib/datetime';

/**
 * ecommerce order detail section (TASK-PC-FE-083 — § 2.4.10 #16). The console
 * equivalent of the `admin-dashboard` order detail screen: order header +
 * item lines + totals + shipping address + status-transition action area.
 *
 * Server-seeded detail is passed in; the client query keeps it fresh after a
 * status mutation invalidation.
 *
 * Status transitions are rendered via `OrderStatusDialog` (only allowed targets
 * from `allowedTransitions(status)` are shown). Producer is the final authority;
 * 400/422/409/404 are surfaced inline by the dialog without crashing.
 */
export interface OrderDetailProps {
  order: OrderDetailType;
}

export function OrderDetail({ order }: OrderDetailProps) {
  const router = useRouter();
  const detailQ = useOrder(order.orderId, order);
  const data = detailQ.data ?? order;

  function handleStatusSuccess() {
    router.refresh();
  }

  return (
    <section
      aria-labelledby="order-detail-heading"
      data-testid="order-detail"
    >
      <DetailHeader
        headingId="order-detail-heading"
        title="주문 상세"
        backHref="/ecommerce/orders"
        backTestId="order-detail-back"
      />

      {/* Order header */}
      <dl className="mb-6 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-muted-foreground">주문 ID</dt>
          <dd
            className="break-all text-xs"
            data-testid="order-detail-id"
          >
            {data.orderId}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="order-detail-status">{data.status}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">사용자 ID</dt>
          <dd className="break-all text-xs">{data.userId}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">총액</dt>
          <dd data-testid="order-detail-total">
            {data.totalPrice.toLocaleString('ko-KR')}원
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">주문일</dt>
          <dd className="text-xs">
            {formatDateTime(data.createdAt)}
          </dd>
        </div>
        {data.updatedAt && (
          <div>
            <dt className="text-muted-foreground">수정일</dt>
            <dd className="text-xs">
              {formatDateTime(data.updatedAt)}
            </dd>
          </div>
        )}
      </dl>

      {/* Items */}
      <div className="mb-6" data-testid="order-items">
        <h2 className="mb-2 text-base font-medium text-foreground">
          주문 상품
        </h2>
        {data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">주문 상품이 없습니다.</p>
        ) : (
          <table className="w-full text-sm">
            <caption className="sr-only">주문 상품 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">상품명</th>
                <th scope="col" className="p-2">옵션</th>
                <th scope="col" className="p-2">수량</th>
                <th scope="col" className="p-2">단가</th>
                <th scope="col" className="p-2">소계</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((item, i) => (
                <tr
                  key={`${item.productId}-${item.variantId}`}
                  data-testid={`order-item-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{item.productName}</td>
                  <td className="p-2 text-muted-foreground">{item.optionName}</td>
                  <td className="p-2">{item.quantity}</td>
                  <td className="p-2">
                    {item.unitPrice.toLocaleString('ko-KR')}원
                  </td>
                  <td className="p-2">
                    {(item.unitPrice * item.quantity).toLocaleString('ko-KR')}원
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={4} className="p-2 text-right font-medium">
                  합계
                </td>
                <td className="p-2 font-semibold">
                  {data.totalPrice.toLocaleString('ko-KR')}원
                </td>
              </tr>
            </tfoot>
          </table>
        )}
      </div>

      {/* Shipping address */}
      <div className="mb-6" data-testid="order-shipping">
        <h2 className="mb-2 text-base font-medium text-foreground">
          배송지
        </h2>
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <div>
            <dt className="text-muted-foreground">수령인</dt>
            <dd>{data.shippingAddress.recipient}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">연락처</dt>
            <dd>{data.shippingAddress.phone}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">우편번호</dt>
            <dd>{data.shippingAddress.zipCode}</dd>
          </div>
          <div className="col-span-2">
            <dt className="text-muted-foreground">주소</dt>
            <dd>
              {data.shippingAddress.address1}
              {data.shippingAddress.address2
                ? ` ${data.shippingAddress.address2}`
                : ''}
            </dd>
          </div>
        </dl>
      </div>

      {/* Status transition area */}
      <OrderStatusDialog
        orderId={data.orderId}
        currentStatus={data.status}
        onSuccess={handleStatusSuccess}
      />
    </section>
  );
}
