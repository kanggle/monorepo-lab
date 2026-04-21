'use client';

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { getOrders } from '@/features/order-management/api/order-api';
import { DataTable, StatusBadge, ListError, type ColumnDef } from '@/shared/ui';
import type { AdminOrderSummary } from '@repo/types';

const RECENT_COUNT = 5;

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function RecentOrdersTable() {
  const router = useRouter();
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'recent-orders'],
    queryFn: () => getOrders({ page: 0, size: RECENT_COUNT }),
  });

  if (query.isError) {
    return <ListError message="최근 주문을 불러오지 못했습니다." onRetry={() => query.refetch()} />;
  }

  const columns: ColumnDef<AdminOrderSummary>[] = [
    { key: 'orderId', header: '주문번호', render: (o) => o.orderId.slice(0, 8) },
    {
      key: 'firstItemName',
      header: '상품',
      render: (o) =>
        o.firstItemName
          ? o.itemCount > 1
            ? `${o.firstItemName} 외 ${o.itemCount - 1}건`
            : o.firstItemName
          : '-',
    },
    {
      key: 'totalPrice',
      header: '금액',
      render: (o) => `${o.totalPrice.toLocaleString('ko-KR')}원`,
    },
    { key: 'status', header: '상태', render: (o) => <StatusBadge status={o.status} /> },
    { key: 'createdAt', header: '주문일시', render: (o) => formatDateTime(o.createdAt) },
  ];

  return (
    <DataTable<AdminOrderSummary>
      columns={columns}
      data={query.data?.content ?? []}
      isLoading={query.isLoading}
      emptyMessage="최근 주문이 없습니다."
      rowKey={(o) => o.orderId}
      onRowClick={(o) => router.push(`/orders/${o.orderId}`)}
    />
  );
}
