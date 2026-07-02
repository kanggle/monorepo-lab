'use client';

import Link from 'next/link';
import { ErrorMessage } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useNotificationDetail } from '../model/use-notification-detail';
import { CHANNEL_LABELS } from '../lib/constants';
import { formatDateTime } from '@/shared/lib';

interface Props {
  notificationId: string;
}

export function NotificationDetail({ notificationId }: Props) {
  const { notification, isLoading, error, retryLoad } = useNotificationDetail(notificationId);

  return (
    <div>
      {isLoading && (
        <div>
          <Skeleton width="80px" height="14px" />
          <div style={{ marginTop: 'var(--space-4)', marginBottom: 'var(--space-4)' }}>
            <Skeleton width="60%" height="24px" />
          </div>
          <Skeleton width="100%" height="200px" />
        </div>
      )}
      {error && <ErrorMessage message={error} onRetry={retryLoad} />}

      {notification && (
        <div>
          <Link
            href="/my/notifications"
            style={{
              display: 'inline-block',
              fontSize: 'var(--font-size-sm)',
              color: 'var(--color-text-secondary)',
              marginBottom: 'var(--space-4)',
              textDecoration: 'none',
            }}
          >
            &larr; 알림 목록
          </Link>

          <div style={{ marginBottom: 'var(--space-6)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
              <h1 className="page-title" style={{ margin: 0 }}>{notification.subject}</h1>
              <span
                style={{
                  padding: 'var(--space-1) var(--space-2)',
                  fontSize: 'var(--font-size-xs)',
                  borderRadius: 'var(--radius-full)',
                  background: 'var(--color-gray-100)',
                  color: 'var(--color-text-secondary)',
                }}
              >
                {CHANNEL_LABELS[notification.channel]}
              </span>
            </div>
            <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
              {formatDateTime(notification.sentAt)}
            </p>
          </div>

          <div
            className="card"
            style={{
              padding: 'var(--space-6)',
              lineHeight: '1.6',
              fontSize: 'var(--font-size-sm)',
              whiteSpace: 'pre-wrap',
            }}
          >
            {notification.body}
          </div>
        </div>
      )}
    </div>
  );
}
