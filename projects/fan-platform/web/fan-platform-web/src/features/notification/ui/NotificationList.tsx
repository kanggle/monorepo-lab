'use client';
import { useState, useTransition } from 'react';
import {
  markNotificationRead,
  markAllNotificationsRead,
} from '@/features/notification/api/actions';
import { TYPE_LABEL, TYPE_ACCENT, formatRelative } from '@/features/notification/ui/labels';
import type { Notification } from '@/entities/notification';

/**
 * Full inbox list. Like the bell, it holds an optimistic local copy so a
 * mark-as-read flips the row instantly; the server action revalidates the page.
 */
export function NotificationList({ initial }: { initial: Notification[] }) {
  const [items, setItems] = useState(initial);
  const [, startTransition] = useTransition();

  const unreadIds = items.filter((n) => !n.read).map((n) => n.id);

  const markOne = (id: string) => {
    const target = items.find((n) => n.id === id);
    if (!target || target.read) return;
    setItems((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true, status: 'READ' } : n)),
    );
    startTransition(async () => {
      try {
        await markNotificationRead(id);
      } catch {
        setItems((prev) =>
          prev.map((n) => (n.id === id ? { ...n, read: false, status: 'UNREAD' } : n)),
        );
      }
    });
  };

  const markAll = () => {
    if (unreadIds.length === 0) return;
    setItems((prev) => prev.map((n) => ({ ...n, read: true, status: 'READ' })));
    startTransition(() => {
      void markAllNotificationsRead(unreadIds);
    });
  };

  return (
    <div className="flex flex-col gap-3">
      {unreadIds.length > 0 ? (
        <div className="flex justify-end">
          <button
            type="button"
            onClick={markAll}
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
          >
            모두 읽음 ({unreadIds.length})
          </button>
        </div>
      ) : null}

      <ul className="flex flex-col gap-2" data-testid="notification-list">
        {items.map((n) => (
          <li key={n.id}>
            <button
              type="button"
              data-testid="notification-row"
              onClick={() => markOne(n.id)}
              disabled={n.read}
              className={[
                'flex w-full flex-col gap-1.5 rounded-xl border p-4 text-left transition-colors',
                n.read
                  ? 'border-ink-200 bg-white'
                  : 'border-brand-200 bg-brand-50/40 hover:bg-brand-50',
              ].join(' ')}
            >
              <span className="flex items-center gap-2">
                {!n.read ? (
                  <span className="h-2 w-2 shrink-0 rounded-full bg-brand-600" />
                ) : null}
                <span
                  className={[
                    'rounded-full px-2 py-0.5 text-xs font-medium',
                    TYPE_ACCENT[n.type],
                  ].join(' ')}
                >
                  {TYPE_LABEL[n.type]}
                </span>
                <span className="ml-auto text-xs text-ink-400">
                  {formatRelative(n.createdAt)}
                </span>
              </span>
              <span className="text-base font-semibold text-ink-900">{n.title}</span>
              <span className="text-sm text-ink-600">{n.body}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
