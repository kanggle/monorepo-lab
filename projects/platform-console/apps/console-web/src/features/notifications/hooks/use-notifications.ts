'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  NotificationListResponseSchema,
  NotificationDetailResponseSchema,
  type NotificationListResponse,
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  NOTIFICATION_MAX_PAGE_SIZE,
} from '../api/notification-types';
import {
  NOTIFICATION_KEY,
  notificationInboxKey,
} from '../api/notification-keys';

/**
 * Client-side notification hooks (TASK-PC-FE-052). Every call goes to the
 * same-origin `/api/erp/notifications/**` proxy (the typed API client's
 * single backend entry point); the proxy attaches the HttpOnly domain-facing
 * IAM OIDC token server-side — the browser never reads a token or calls erp
 * directly (contract § 2.3).
 *
 * NO `refetchInterval` / polling (erp-ops discipline — same rule as the
 * approval / read-model hooks). The inbox is fetched **passively on mount**
 * so the bell badge reflects the unread count without requiring a click; a
 * modest `staleTime` keeps SPA navigation from refetch-storming (this is a
 * cache-freshness TTL, NOT a polling interval). The count refreshes after a
 * mark-read via prefix invalidation.
 *
 * Hooks deliberately DO NOT throw to an error boundary — query `isError`
 * is surfaced so the `NotificationBell` can degrade gracefully when the erp
 * inbox is unavailable (non-erp operators 403, 503, timeout, network).
 */

const clampSize = (size?: number): number =>
  clampPageSize(size, NOTIFICATION_DEFAULT_PAGE_SIZE, NOTIFICATION_MAX_PAGE_SIZE);

// ---------------------------------------------------------------------------
// useNotificationInbox — the caller's recipient-scoped inbox.
// ---------------------------------------------------------------------------

async function fetchNotificationInbox(opts: {
  unread?: boolean;
  page?: number;
  size?: number;
}): Promise<NotificationListResponse> {
  const qs = new URLSearchParams();
  if (opts.unread !== undefined) qs.set('unread', String(opts.unread));
  qs.set('page', String(Math.max(0, opts.page ?? 0)));
  qs.set('size', String(clampSize(opts.size)));
  const raw = await apiClient.get<unknown>(
    `/api/erp/notifications?${qs.toString()}`,
  );
  return NotificationListResponseSchema.parse(raw);
}

export function useNotificationInbox(opts?: {
  unread?: boolean;
  page?: number;
  size?: number;
  /** Set to `false` to disable the query (e.g. when the bell dropdown is
   *  closed). Defaults to `true`. */
  enabled?: boolean;
}) {
  const page = opts?.page ?? 0;
  const size = clampSize(opts?.size);
  const unread = opts?.unread;
  return useQuery({
    queryKey: notificationInboxKey(unread, page, size),
    queryFn: () => fetchNotificationInbox({ unread, page, size }),
    enabled: opts?.enabled !== false,
    ...READ_QUERY_REFETCH,
    // Cache-freshness TTL (NOT polling) — a passive on-mount fetch drives the
    // badge; SPA navigation within the window reuses the cache rather than
    // re-hitting erp on every page.
    staleTime: 30_000,
    retry: false,
    // Errors are returned via `isError` — the bell degrades; no error boundary.
    throwOnError: false,
  });
}

// ---------------------------------------------------------------------------
// useMarkNotificationRead — idempotent mark-read mutation.
// ---------------------------------------------------------------------------

export function useMarkNotificationRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/notifications/${encodeURIComponent(id)}/read`,
      );
      const env = (raw ?? {}) as { data?: unknown };
      return NotificationDetailResponseSchema.parse({
        data: env.data,
        meta: (raw as { meta?: unknown })?.meta ?? {},
      }).data;
    },
    onSuccess: () => {
      // Invalidate the entire notifications prefix so inbox refetches +
      // badge count updates.
      qc.invalidateQueries({ queryKey: [NOTIFICATION_KEY] });
    },
    // Mutation failures are silently ignored by the bell component — a
    // mark-read failure must NOT block navigation (task Failure Scenarios).
  });
}
