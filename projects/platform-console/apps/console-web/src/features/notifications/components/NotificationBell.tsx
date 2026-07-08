'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/shared/lib/cn';
import { useNotificationInbox, useMarkNotificationRead } from '../hooks/use-notifications';
import {
  isApprovalSource,
  type AggregatedNotification,
} from '../api/notification-types';

/**
 * TASK-PC-FE-052 — Console header notification bell. A bell icon button with
 * an unread-count badge (hidden when count === 0 or when the inbox is
 * unavailable/errored). Click toggles a dropdown that lists the caller's
 * recent notifications (recipient-scoped, v1 — first page of inbox).
 *
 * Dropdown behaviour (AccountMenu pattern):
 *   - outside-click or Escape closes the dropdown.
 *   - clicking a row: call mark-read (idempotent, fire-and-forget) then
 *     navigate if `isApprovalSource(n)` → `/erp/approval?request=<sourceId>`
 *     (the real 결재함 route — the page preselects the request; PC-FE-230).
 *     mark-read failure MUST NOT block navigation.
 *   - empty state: "새 알림이 없습니다".
 *
 * DEGRADE POSTURE (integration-heavy resilience — AC-4):
 *   When the inbox query `isError` (403 non-erp operator, 503, timeout,
 *   network), the bell renders inert: plain bell icon, NO badge, and the
 *   dropdown (if opened) shows a quiet "알림을 불러올 수 없습니다" message.
 *   The console shell NEVER crashes — no error boundary is triggered.
 *   Non-erp operators will always see the degrade state gracefully.
 *
 * NO polling / refetchInterval. The inbox is fetched **passively on mount**
 * (via the always-enabled query) so the unread badge is visible without the
 * operator having to open the dropdown first; a `staleTime` in the hook keeps
 * SPA navigation from refetch-storming. The count refreshes after a mark-read.
 */

/** Formats a UTC ISO-8601 string to a short human-readable label. */
function formatShortDate(iso: string): string {
  try {
    const d = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60_000);
    if (diffMin < 1) return '방금';
    if (diffMin < 60) return `${diffMin}분 전`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}시간 전`;
    const diffDay = Math.floor(diffHr / 24);
    if (diffDay < 30) return `${diffDay}일 전`;
    return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
  } catch {
    return '';
  }
}

/** Returns a short label for the notification `type`. Unknown types render
 *  generically (forward-tolerant). */
function typeLabel(type: string): string {
  switch (type) {
    case 'APPROVAL_SUBMITTED':
      return '결재 상신';
    case 'APPROVAL_APPROVED':
      return '결재 승인';
    case 'APPROVAL_REJECTED':
      return '결재 반려';
    case 'APPROVAL_WITHDRAWN':
      return '결재 회수';
    default:
      return '알림';
  }
}

interface NotificationRowProps {
  // Aggregator items always carry `sourceDomain` (required) — the bell only
  // ever renders the merged aggregator feed, so mark-read can address the owner.
  notification: AggregatedNotification;
  onClose: () => void;
}

function NotificationRow({ notification: n, onClose }: NotificationRowProps) {
  const router = useRouter();
  const { mutate: markRead } = useMarkNotificationRead();

  const handleClick = () => {
    // Fire-and-forget mark-read addressed to the owning domain — failure must
    // NOT block navigation (the aggregator dispatches per-domain credential).
    markRead({ sourceDomain: n.sourceDomain, id: n.id });
    // Prefer the §1 deepLink when the domain supplies one; else fall back to the
    // erp approval deep-link (sourceType/sourceId extension). The fallback
    // targets the real 결재함 route `/erp/approval` (which preselects the
    // request via `?request=`), NOT `/erp` — the masters page ignores the
    // param and would strand the operator on the wrong slice (PC-FE-230).
    if (n.deepLink) {
      router.push(n.deepLink);
    } else if (isApprovalSource(n) && n.sourceId) {
      router.push(`/erp/approval?request=${encodeURIComponent(n.sourceId)}`);
    }
    onClose();
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      className={cn(
        'flex w-full items-start gap-2 px-3 py-2 text-left transition-colors',
        'hover:bg-accent focus-visible:bg-accent focus-visible:outline-none',
        !n.read && 'bg-accent/30',
      )}
      data-testid={`notification-row-${n.id}`}
    >
      {/* Unread dot — visible when read === false */}
      <span
        className={cn(
          'mt-1.5 h-2 w-2 shrink-0 rounded-full',
          n.read ? 'bg-transparent' : 'bg-blue-500',
        )}
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1">
          <span className="text-xs font-medium text-muted-foreground">
            {typeLabel(n.type)}
          </span>
          <span className="ml-auto shrink-0 text-xs text-muted-foreground">
            {formatShortDate(n.createdAt)}
          </span>
        </div>
        <p className="truncate text-sm text-foreground">{n.title}</p>
      </div>
    </button>
  );
}

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  // Fetch the inbox passively on mount so the unread badge is visible without
  // opening the dropdown; no refetch on window focus, no polling (staleTime
  // gates SPA-navigation refetches in the hook).
  const { data, isError, isLoading } = useNotificationInbox();

  // Unread count from the fetched page (first increment — no separate count
  // endpoint). When errored / loading the count is treated as 0 (badge
  // hidden per degrade posture).
  const unreadCount =
    !isError && data ? data.items.filter((n) => !n.read).length : 0;

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const closeDropdown = () => setOpen(false);

  return (
    <div className="relative" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        aria-label="알림"
        title="알림"
        aria-haspopup="menu"
        aria-expanded={open}
        data-testid="notification-bell-trigger"
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'relative inline-flex h-8 w-8 items-center justify-center rounded-md border border-border text-muted-foreground',
          'transition-colors hover:bg-accent hover:text-foreground',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        )}
      >
        {/* Bell SVG icon */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>

        {/* Unread badge — hidden when count === 0 or when errored/unavailable */}
        {unreadCount > 0 && (
          <span
            aria-label={`읽지 않은 알림 ${unreadCount}개`}
            data-testid="notification-badge"
            className={cn(
              'absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center',
              'rounded-full bg-blue-500 text-[10px] font-bold text-white',
            )}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          role="menu"
          aria-label="알림 목록"
          data-testid="notification-dropdown"
          className={cn(
            'absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-md border border-border bg-background shadow-md',
          )}
        >
          <div className="border-b border-border px-3 py-2">
            <p className="text-sm font-medium text-foreground">알림</p>
          </div>

          {/* Degrade state — quiet unavailable message (no crash, no error boundary) */}
          {isError && (
            <div
              data-testid="notification-unavailable"
              className="px-3 py-4 text-sm text-muted-foreground"
            >
              알림을 불러올 수 없습니다
            </div>
          )}

          {/* Loading state */}
          {!isError && isLoading && (
            <div className="px-3 py-4 text-sm text-muted-foreground">
              불러오는 중...
            </div>
          )}

          {/* Notification list */}
          {!isError && !isLoading && data && (
            <>
              {data.items.length === 0 ? (
                <div
                  data-testid="notification-empty"
                  className="px-3 py-4 text-sm text-muted-foreground"
                >
                  새 알림이 없습니다
                </div>
              ) : (
                <div className="max-h-80 overflow-y-auto">
                  {data.items.map((n) => (
                    <NotificationRow
                      key={`${n.sourceDomain}:${n.id}`}
                      notification={n}
                      onClose={closeDropdown}
                    />
                  ))}
                </div>
              )}
              {/* D5 per-domain degrade hint — a domain's feed failed to load but
                  the bell still shows the rest (never blanks the shell). */}
              {data.degradedDomains.length > 0 && (
                <div
                  data-testid="notification-degraded"
                  className="border-t border-border px-3 py-2 text-xs text-muted-foreground"
                >
                  일부 도메인 알림을 불러오지 못했습니다
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
