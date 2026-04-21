'use client';

import { useQuery } from '@tanstack/react-query';
import { getOrders } from '@/features/order-management/api/order-api';
import { KpiCard } from './KpiCard';

export function PendingOrdersKpi() {
  const pendingQuery = useQuery({
    queryKey: ['admin', 'dashboard', 'pending-orders', 'PENDING'],
    queryFn: () => getOrders({ page: 0, size: 1, status: 'PENDING' }),
  });
  const confirmedQuery = useQuery({
    queryKey: ['admin', 'dashboard', 'pending-orders', 'CONFIRMED'],
    queryFn: () => getOrders({ page: 0, size: 1, status: 'CONFIRMED' }),
  });

  const isLoading = pendingQuery.isLoading || confirmedQuery.isLoading;
  const isError = pendingQuery.isError || confirmedQuery.isError;

  const total =
    (pendingQuery.data?.totalElements ?? 0) + (confirmedQuery.data?.totalElements ?? 0);

  return (
    <KpiCard
      title="처리 대기 주문"
      value={isLoading ? '-' : `${total.toLocaleString('ko-KR')}건`}
      subValue={isLoading ? undefined : 'PENDING + CONFIRMED'}
      isLoading={isLoading}
      error={isError ? '주문 데이터를 불러오지 못했습니다.' : null}
      onRetry={() => {
        pendingQuery.refetch();
        confirmedQuery.refetch();
      }}
    />
  );
}
