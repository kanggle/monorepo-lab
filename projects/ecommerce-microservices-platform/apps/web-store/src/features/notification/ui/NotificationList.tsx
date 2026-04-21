'use client';

import Link from 'next/link';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { PaginationNav } from '@/shared/ui/PaginationNav';
import { usePagination } from '@/shared/hooks/use-pagination';
import { useNotifications } from '../model/use-notifications';
import { NotificationCard } from './NotificationCard';

export function NotificationList() {
  const { page, size, handlePageChange, handleSizeChange } = usePagination(0);

  const { data, isLoading, isError, refetch } = useNotifications(page, size);

  const notifications = (data?.content ?? []).filter((n) => n.status === 'SENT');
  const totalElements = data?.totalElements ?? 0;
  const error = isError ? '알림 목록을 불러오는데 실패했습니다.' : '';

  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)' }}>
        <h1 className="page-title" style={{ margin: 0 }}>알림</h1>
        <Link
          href="/my/notifications/settings"
          className="btn"
          style={{ textDecoration: 'none', fontSize: 'var(--font-size-sm)' }}
        >
          알림 설정
        </Link>
      </div>

      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} style={{ padding: 'var(--space-4)', border: '1px solid var(--color-border-light)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-3)' }}>
                <Skeleton width="40%" height="14px" />
                <Skeleton width="60px" height="14px" />
              </div>
              <Skeleton width="70%" height="14px" />
            </div>
          ))}
        </div>
      )}
      {error && <ErrorMessage message={error} onRetry={() => refetch()} />}
      {!isLoading && !error && notifications.length === 0 && (
        <EmptyState message="알림이 없습니다." />
      )}
      {notifications.map((notification) => (
        <NotificationCard key={notification.notificationId} notification={notification} />
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
