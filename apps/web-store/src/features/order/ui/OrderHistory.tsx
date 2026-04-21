'use client';

import { OrderCard } from '@/entities/order';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { PaginationNav } from '@/shared/ui/PaginationNav';
import { usePagination } from '@/shared/hooks/use-pagination';
import { useOrders } from '../model/use-orders';

export function OrderHistory() {
  const { page, size, handlePageChange, handleSizeChange } = usePagination(0);

  const { data, isLoading, isError, refetch } = useOrders(page, size);

  const orders = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const error = isError ? '주문 목록을 불러오는데 실패했습니다.' : '';

  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  return (
    <div>
      <h1 className="page-title">주문 내역</h1>

      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border-light)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-3)' }}>
                <Skeleton width="40%" height="14px" />
                <Skeleton width="60px" height="14px" />
              </div>
              <Skeleton width="70%" height="14px" />
              <div style={{ marginTop: 'var(--space-2)' }}>
                <Skeleton width="30%" height="16px" />
              </div>
            </div>
          ))}
        </div>
      )}
      {error && <ErrorMessage message={error} onRetry={() => refetch()} />}
      {!isLoading && !error && orders.length === 0 && (
        <EmptyState message="주문 내역이 없습니다." />
      )}
      {orders.map((order) => (
        <OrderCard key={order.orderId} order={order} />
      ))}

      {!isLoading && !error && totalElements > 0 && (
        <PaginationNav
          page={page}
          totalPages={totalPages}
          size={size}
          onPageChange={handlePageChange}
          onSizeChange={handleSizeChange}
        />
      )}
    </div>
  );
}
