'use client';

import { useRouter } from 'next/navigation';
import { DataTable, StatusBadge, FilterBar, ListError } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useOrders } from '../hooks/use-orders';
import { useUserEmail } from '@/shared/hooks';
import { ORDER_STATUS_OPTIONS } from '@/shared/lib/status-options';
import type { AdminOrderSummary } from '@repo/types';

function UserEmailCell({ userId }: { userId: string }) {
  const { email, isLoading, isError } = useUserEmail(userId);

  if (isLoading) return <span style={{ color: '#9ca3af' }}>불러오는 중...</span>;
  if (isError || !email) return <span style={{ color: '#9ca3af' }}>-</span>;

  return <span>{email}</span>;
}

const columns: ColumnDef<AdminOrderSummary>[] = [
  {
    key: 'orderId',
    header: '주문번호',
    render: (order: AdminOrderSummary) => order.orderId.slice(0, 8) + '...',
  },
  { key: 'userId', header: '주문자ID',
    render: (order: AdminOrderSummary) => order.userId.slice(0, 8) + '...',
  },
  {
    key: 'userEmail',
    header: '주문자 이메일',
    render: (order: AdminOrderSummary) => <UserEmailCell userId={order.userId} />,
  },
  {
    key: 'status',
    header: '상태',
    sortable: true,
    render: (order: AdminOrderSummary) => <StatusBadge status={order.status} />,
  },
  {
    key: 'firstItemName',
    header: '상품명',
    sortable: true,
    render: (order: AdminOrderSummary) =>
      order.firstItemName
        ? order.itemCount > 1
          ? `${order.firstItemName} 외 ${order.itemCount - 1}건`
          : order.firstItemName
        : '-',
  },
  {
    key: 'totalPrice',
    header: '총액',
    sortable: true,
    render: (order: AdminOrderSummary) => `${order.totalPrice.toLocaleString()}원`,
  },
  { key: 'itemCount', header: '상품수', sortable: true },
  {
    key: 'createdAt',
    header: '주문일',
    sortable: true,
    render: (order: AdminOrderSummary) =>
      new Date(order.createdAt).toLocaleDateString('ko-KR'),
  },
];

export function OrderList() {
  const router = useRouter();
  const { data, isLoading, isError, refetch, pagination, filters } = useOrders();

  if (isError) {
    return <ListError message="주문 목록을 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <div>
      <FilterBar
        statusOptions={ORDER_STATUS_OPTIONS}
        statusValue={filters.status}
        onStatusChange={(value) => filters.setFilter('status', value)}
      />
      <DataTable<AdminOrderSummary>
        columns={columns}
        data={data?.content ?? []}
        pagination={pagination}
        isLoading={isLoading}
        emptyMessage="주문이 없습니다."
        onRowClick={(item) => router.push(`/orders/${item.orderId}`)}
      />
    </div>
  );
}
