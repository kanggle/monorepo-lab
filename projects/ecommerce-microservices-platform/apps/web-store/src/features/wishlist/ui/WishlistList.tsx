'use client';

import { ErrorMessage, EmptyState } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { PaginationNav } from '@/shared/ui/PaginationNav';
import { usePagination } from '@/shared/hooks/use-pagination';
import { useWishlist } from '../model/use-wishlist';
import { WishlistItemCard } from './WishlistItemCard';

export function WishlistList() {
  const { page, size, handlePageChange, handleSizeChange } = usePagination(0);

  const { data, isLoading, isError, refetch } = useWishlist(page, size);

  const items = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  return (
    <div>
      <h1 className="page-title">위시리스트</h1>

      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div
              key={i}
              style={{
                padding: 'var(--space-4)',
                border: '1px solid var(--color-border-light)',
                borderRadius: 'var(--radius-md)',
              }}
            >
              <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center' }}>
                <Skeleton width="60px" height="60px" />
                <div style={{ flex: 1 }}>
                  <Skeleton width="60%" height="14px" />
                  <div style={{ marginTop: 'var(--space-2)' }}>
                    <Skeleton width="30%" height="14px" />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {isError && (
        <ErrorMessage
          message="위시리스트를 불러오는데 실패했습니다."
          onRetry={() => refetch()}
        />
      )}

      {!isLoading && !isError && items.length === 0 && (
        <EmptyState message="위시리스트가 비어 있습니다." />
      )}

      {!isLoading && !isError && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {items.map((item) => (
            <WishlistItemCard key={item.wishlistItemId} item={item} />
          ))}
        </div>
      )}

      {!isLoading && !isError && totalElements > 0 && (
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
