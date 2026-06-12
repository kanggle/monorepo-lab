import Link from 'next/link';
import type { NotificationStatus } from '@/entities/notification';
import { buildNotificationsHref } from '@/features/notification/ui/paging';

const BTN = 'rounded-full px-3 py-1.5 text-sm font-medium';
const ACTIVE = `${BTN} bg-ink-100 text-ink-700 hover:bg-ink-200`;
const DISABLED = `${BTN} bg-ink-50 text-ink-300 cursor-default`;

/**
 * Server-rendered inbox pager (`?page=N`), preserving the active `status` filter.
 * Renders `이전` / `다음` as links (disabled spans at the ends) and a 1-based
 * `현재 / 총` indicator. Renders nothing for a single-page inbox.
 */
export function NotificationPagination({
  status,
  page,
  totalPages,
}: {
  status?: NotificationStatus;
  page: number;
  totalPages: number;
}) {
  if (totalPages <= 1) return null;

  const hasPrev = page > 0;
  const hasNext = page < totalPages - 1;

  return (
    <nav className="flex items-center justify-between" aria-label="알림 페이지 이동">
      {hasPrev ? (
        <Link href={buildNotificationsHref(status, page - 1)} className={ACTIVE} rel="prev">
          이전
        </Link>
      ) : (
        <span className={DISABLED} aria-disabled="true">
          이전
        </span>
      )}

      <span className="text-sm text-ink-600" data-testid="page-indicator">
        {page + 1} / {totalPages}
      </span>

      {hasNext ? (
        <Link href={buildNotificationsHref(status, page + 1)} className={ACTIVE} rel="next">
          다음
        </Link>
      ) : (
        <span className={DISABLED} aria-disabled="true">
          다음
        </span>
      )}
    </nav>
  );
}
