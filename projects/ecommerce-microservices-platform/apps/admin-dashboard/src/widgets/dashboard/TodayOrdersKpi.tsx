'use client';

import { useQuery } from '@tanstack/react-query';
import { getOrders } from '@/features/order-management/api/order-api';
import { KpiCard } from './KpiCard';
import { aggregateToday } from './lib/aggregate-orders';

// 집계 한계: 첫 페이지(RECENT_ORDERS_PAGE_SIZE)만 조회해 프론트 필터링한다.
// totalElements > RECENT_ORDERS_PAGE_SIZE 인 경우 이전 페이지 주문은 집계되지 않으며
// 경고 문구를 subValue에 노출한다. 백엔드 집계 API가 생기면 제거 대상.
const RECENT_ORDERS_PAGE_SIZE = 100;

export function TodayOrdersKpi() {
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'today-orders'],
    queryFn: () => getOrders({ page: 0, size: RECENT_ORDERS_PAGE_SIZE }),
  });

  const metrics = query.data ? aggregateToday(query.data.content) : null;
  const truncated =
    query.data != null && query.data.totalElements > RECENT_ORDERS_PAGE_SIZE;

  return (
    <KpiCard
      title="오늘 주문"
      value={metrics ? `${metrics.count.toLocaleString('ko-KR')}건` : '-'}
      subValue={
        metrics
          ? truncated
            ? `${metrics.revenue.toLocaleString('ko-KR')}원 · ※ 최근 ${RECENT_ORDERS_PAGE_SIZE}건 기준`
            : `${metrics.revenue.toLocaleString('ko-KR')}원`
          : undefined
      }
      isLoading={query.isLoading}
      error={query.isError ? '주문 데이터를 불러오지 못했습니다.' : null}
      onRetry={query.refetch}
    />
  );
}
