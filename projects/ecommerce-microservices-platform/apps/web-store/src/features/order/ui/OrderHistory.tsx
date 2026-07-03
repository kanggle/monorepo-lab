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
        <div>
          {Array.from({ length: 3 }).map((_, i) => (
            <div
              key={i}
              className="card"
              style={{ padding: 'var(--space-4) var(--space-5)', marginBottom: 'var(--space-3)' }}
            >
              {/* Line 1: date (left) + status badge (right) — mirrors OrderCard. */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-1)' }}>
                <Skeleton width="96px" height="12px" />
                <Skeleton width="64px" height="22px" borderRadius="var(--radius-full)" />
              </div>
              {/* Line 2: product name · total price single line. */}
              <Skeleton width="70%" height="16px" />
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
