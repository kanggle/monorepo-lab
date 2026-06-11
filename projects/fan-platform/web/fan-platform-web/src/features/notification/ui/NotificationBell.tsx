'use client';
import { useState, useTransition } from 'react';
import Link from 'next/link';
import {
  markNotificationRead,
  markAllNotificationsRead,
} from '@/features/notification/api/actions';
import { TYPE_LABEL, TYPE_ACCENT, formatRelative } from '@/features/notification/ui/labels';
import type { Notification } from '@/entities/notification';

/**
 * Header notification bell. Receives only plain data (no access token); reads
 * happened server-side in the header, writes go through `'use server'` actions.
 *
 * The unread count is held in **optimistic local state** so a mark-as-read flips
 * the badge instantly without revalidating the shared (main) layout. The server
 * action still `revalidatePath('/notifications')` so the full inbox stays
 * consistent on next visit.
 */
export function NotificationBell({
  initialItems,
  initialUnread,
}: {
  initialItems: Notification[];
  initialUnread: number;
}) {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState(initialItems);
  const [unread, setUnread] = useState(initialUnread);
  const [, startTransition] = useTransition();

  const badge = unread > 9 ? '9+' : String(unread);

  const markOne = (id: string) => {
    const target = items.find((n) => n.id === id);
    if (!target || target.read) return;
    // optimistic
    setItems((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true, status: 'READ' } : n)),
    );
    setUnread((c) => Math.max(0, c - 1));
    startTransition(async () => {
      try {
        await markNotificationRead(id);
      } catch {
        // revert on failure
        setItems((prev) =>
          prev.map((n) => (n.id === id ? { ...n, read: false, status: 'UNREAD' } : n)),
        );
        setUnread((c) => c + 1);
      }
    });
  };

  const markAllVisible = () => {
    const unreadIds = items.filter((n) => !n.read).map((n) => n.id);
    if (unreadIds.length === 0) return;
    setItems((prev) => prev.map((n) => ({ ...n, read: true, status: 'READ' })));
    setUnread((c) => Math.max(0, c - unreadIds.length));
    startTransition(async () => {
      try {
        await markAllNotificationsRead(unreadIds);
      } catch {
        // best-effort: leave the optimistic state (a stale read flips back on
        // next server fetch). The inbox page is the authoritative surface.
      }
    });
  };

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="알림"
        aria-expanded={open}
        data-testid="notification-bell"
        className="relative flex h-9 w-9 items-center justify-center rounded-full text-ink-600 hover:bg-ink-100 hover:text-brand-600"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-5 w-5"
          aria-hidden="true"
        >
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {unread > 0 ? (
          <span
            data-testid="notification-badge"
            className="absolute -right-0.5 -top-0.5 flex min-w-[1.1rem] items-center justify-center rounded-full bg-brand-600 px-1 text-[0.65rem] font-bold leading-4 text-white"
          >
            {badge}
          </span>
        ) : null}
      </button>

      {open ? (
        <>
          {/* click-away backdrop */}
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            className="fixed inset-0 z-10 cursor-default"
            onClick={() => setOpen(false)}
          />
          <div
            data-testid="notification-dropdown"
            className="absolute right-0 z-20 mt-2 w-80 overflow-hidden rounded-xl border border-ink-200 bg-white shadow-lg"
          >
            <div className="flex items-center justify-between border-b border-ink-100 px-4 py-2.5">
              <p className="text-sm font-semibold text-ink-900">알림</p>
              {unread > 0 ? (
                <button
                  type="button"
                  onClick={markAllVisible}
                  className="text-xs font-medium text-brand-600 hover:text-brand-700"
                >
                  모두 읽음
                </button>
              ) : null}
            </div>

            {items.length === 0 ? (
              <p className="px-4 py-10 text-center text-sm text-ink-500">새 알림이 없습니다</p>
            ) : (
              <ul className="max-h-96 divide-y divide-ink-50 overflow-y-auto">
                {items.map((n) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      data-testid="notification-item"
                      onClick={() => markOne(n.id)}
                      disabled={n.read}
                      className={[
                        'flex w-full flex-col gap-1 px-4 py-3 text-left',
                        n.read ? 'bg-white' : 'bg-brand-50/40 hover:bg-brand-50',
                      ].join(' ')}
                    >
                      <span className="flex items-center gap-2">
                        {!n.read ? (
                          <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-brand-600" />
                        ) : null}
                        <span
                          className={[
                            'rounded-full px-1.5 py-0.5 text-[0.65rem] font-medium',
                            TYPE_ACCENT[n.type],
                          ].join(' ')}
                        >
                          {TYPE_LABEL[n.type]}
                        </span>
                        <span className="ml-auto text-[0.7rem] text-ink-400">
                          {formatRelative(n.createdAt)}
                        </span>
                      </span>
                      <span className="text-sm font-medium text-ink-900">{n.title}</span>
                      <span className="line-clamp-2 text-xs text-ink-600">{n.body}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <div className="border-t border-ink-100 px-4 py-2 text-center">
              <Link
                href="/notifications"
                onClick={() => setOpen(false)}
                className="text-xs font-medium text-brand-600 hover:text-brand-700"
              >
                전체 보기
              </Link>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
