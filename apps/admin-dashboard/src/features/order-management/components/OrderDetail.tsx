'use client';

import { PageLayout, StatusBadge, DescriptionList, Section } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { useOrder } from '../hooks/use-order';
import { useChangeOrderStatus } from '../hooks/use-change-order-status';
import { useUserEmail } from '@/shared/hooks';
import { OrderStatusActions } from './OrderStatusActions';
import type { OrderItem } from '@repo/types';

interface Props {
  orderId: string;
}

function OrderItemsTable({ items }: { items: OrderItem[] }) {
  if (items.length === 0) {
    return <p style={{ color: '#6b7280' }}>주문 항목이 없습니다.</p>;
  }

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>
        <tr>
          <th style={{ padding: '8px 16px', textAlign: 'left', borderBottom: '2px solid #e5e7eb' }}>상품명</th>
          <th style={{ padding: '8px 16px', textAlign: 'left', borderBottom: '2px solid #e5e7eb' }}>옵션</th>
          <th style={{ padding: '8px 16px', textAlign: 'right', borderBottom: '2px solid #e5e7eb' }}>단가</th>
          <th style={{ padding: '8px 16px', textAlign: 'right', borderBottom: '2px solid #e5e7eb' }}>수량</th>
          <th style={{ padding: '8px 16px', textAlign: 'right', borderBottom: '2px solid #e5e7eb' }}>소계</th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr key={`${item.productId}-${item.variantId}`} style={{ borderBottom: '1px solid #e5e7eb' }}>
            <td style={{ padding: '8px 16px' }}>{item.productName}</td>
            <td style={{ padding: '8px 16px' }}>{item.optionName}</td>
            <td style={{ padding: '8px 16px', textAlign: 'right' }}>{item.unitPrice.toLocaleString()}원</td>
            <td style={{ padding: '8px 16px', textAlign: 'right' }}>{item.quantity}</td>
            <td style={{ padding: '8px 16px', textAlign: 'right' }}>{(item.unitPrice * item.quantity).toLocaleString()}원</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function OrderDetail({ orderId }: Props) {
  const { data: order, isLoading, isError, refetch } = useOrder(orderId);
  const mutation = useChangeOrderStatus(orderId);
  const { email, isLoading: isUserLoading, isError: isUserError } = useUserEmail(order?.userId ?? '');

  const userEmail = isUserLoading
    ? '불러오는 중...'
    : isUserError || !email
      ? '-'
      : email;

  if (isLoading || !order) {
    return <PageLayout.Skeleton />;
  }

  if (isError) {
    return <ErrorMessage message="주문 정보를 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <PageLayout title={`주문 ${orderId}`} actions={[{ label: '← 주문 관리', href: '/orders', variant: 'secondary' as const }]}>
      <Section title="주문 정보">
        <DescriptionList
          items={[
            { label: '상태', value: <StatusBadge status={order.status} /> },
            { label: '주문자 ID', value: order.userId },
            { label: '주문자 이메일', value: userEmail },
            { label: '총액', value: `${order.totalPrice.toLocaleString()}원` },
            { label: '주문일', value: new Date(order.createdAt).toLocaleString('ko-KR') },
            { label: '수정일', value: new Date(order.updatedAt).toLocaleString('ko-KR') },
          ]}
        />
        <OrderStatusActions
          status={order.status}
          isPending={mutation.isPending}
          error={mutation.error}
          onChangeStatus={(target) => mutation.mutate(target)}
        />
      </Section>

      <Section title="주문 항목">
        <OrderItemsTable items={order.items} />
      </Section>

      <Section title="배송지 정보">
        <DescriptionList
          items={[
            { label: '수령인', value: order.shippingAddress.recipient },
            { label: '연락처', value: order.shippingAddress.phone },
            { label: '우편번호', value: order.shippingAddress.zipCode },
            { label: '주소', value: `${order.shippingAddress.address1} ${order.shippingAddress.address2 ?? ''}` },
          ]}
        />
      </Section>
    </PageLayout>
  );
}
