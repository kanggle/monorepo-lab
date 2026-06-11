import Link from 'next/link';
import { getFanSession } from '@/shared/auth/session';
import { getNotifications, NotificationList } from '@/features/notification';
import { EmptyState } from '@/shared/ui/EmptyState';
import type { NotificationStatus } from '@/entities/notification';

function parseStatus(raw: string | undefined): NotificationStatus | undefined {
  return raw === 'UNREAD' || raw === 'READ' ? raw : undefined;
}

const TABS: { key: string; label: string; href: string }[] = [
  { key: 'ALL', label: '전체', href: '/notifications' },
  { key: 'UNREAD', label: '안읽음', href: '/notifications?status=UNREAD' },
  { key: 'READ', label: '읽음', href: '/notifications?status=READ' },
];

/**
 * Notification inbox — the full list backed by notification-service (FAN-BE-013)
 * via the gateway `/api/v1/notifications`. Server Component: the access token
 * stays on the server; per-row / bulk mark-read writes go through the
 * `'use server'` actions inside `NotificationList`.
 */
export default async function NotificationsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>;
}) {
  const { status } = await searchParams;
  const statusFilter = parseStatus(status);
  const activeKey = statusFilter ?? 'ALL';

  const session = await getFanSession();
  const notifications = await getNotifications(session.accessToken, statusFilter);

  return (
    <section className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-bold text-ink-900">알림</h1>
        <p className="text-sm text-ink-600">멤버십 시작·해지 등 활동 알림을 모아봅니다.</p>
      </header>

      <nav className="flex gap-2">
        {TABS.map((tab) => (
          <Link
            key={tab.key}
            href={tab.href}
            className={[
              'rounded-full px-3 py-1.5 text-sm font-medium',
              tab.key === activeKey
                ? 'bg-brand-600 text-white'
                : 'bg-ink-100 text-ink-700 hover:bg-ink-200',
            ].join(' ')}
          >
            {tab.label}
          </Link>
        ))}
      </nav>

      {notifications.length === 0 ? (
        <EmptyState
          title="알림이 없습니다"
          description={
            statusFilter === 'UNREAD'
              ? '읽지 않은 알림이 없습니다.'
              : '멤버십을 시작하거나 해지하면 여기에서 알림을 확인할 수 있어요.'
          }
        />
      ) : (
        <NotificationList initial={notifications} />
      )}
    </section>
  );
}
