import Link from 'next/link';
import type { NotificationSummary } from '@repo/types';
import { CHANNEL_LABELS } from '../lib/constants';
import { formatDateTime } from '@/shared/lib';

export function NotificationCard({ notification }: { notification: NotificationSummary }) {
  return (
    <Link
      href={`/my/notifications?id=${notification.notificationId}`}
      className="card"
      data-testid="notification-card"
      style={{
        display: 'block',
        padding: 'var(--space-4) var(--space-5)',
        textDecoration: 'none',
        color: 'inherit',
        marginBottom: 'var(--space-3)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <p style={{
            margin: '0 0 var(--space-1)',
            fontSize: 'var(--font-size-xs)',
            color: 'var(--color-text-secondary)',
          }}>
            {formatDateTime(notification.sentAt)}
          </p>
          <p style={{
            margin: '0 0 var(--space-1)',
            fontSize: 'var(--font-size-sm)',
            fontWeight: 'var(--font-weight-semibold)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}>
            {notification.subject}
          </p>
        </div>
        <span
          style={{
            flexShrink: 0,
            marginLeft: 'var(--space-3)',
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
    </Link>
  );
}
