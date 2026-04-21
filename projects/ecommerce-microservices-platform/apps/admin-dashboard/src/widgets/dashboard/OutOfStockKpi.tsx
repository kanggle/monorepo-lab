'use client';

import { useQuery } from '@tanstack/react-query';
import { getProducts } from '@/features/product-management/api/product-api';
import { KpiCard } from './KpiCard';

export function OutOfStockKpi() {
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'out-of-stock'],
    queryFn: () => getProducts({ page: 0, size: 1, status: 'SOLD_OUT' }),
  });

  const total = query.data?.totalElements ?? 0;

  return (
    <KpiCard
      title="품절 상품"
      value={query.isLoading ? '-' : `${total.toLocaleString('ko-KR')}개`}
      subValue={query.isLoading ? undefined : 'SOLD_OUT 상태'}
      isLoading={query.isLoading}
      error={query.isError ? '상품 데이터를 불러오지 못했습니다.' : null}
      onRetry={query.refetch}
    />
  );
}
