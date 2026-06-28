'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  NotificationInboxResponseSchema,
  NotificationDetailResponseSchema,
  type NotificationInboxResponse,
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  NOTIFICATION_MAX_PAGE_SIZE,
} from '../api/notification-types';
import {
  NOTIFICATION_KEY,
  notificationInboxKey,
} from '../api/notification-keys';

/**
 * Client-side notification hooks. After ADR-MONO-043 P3b the bell reads the
 * **console-bff notification aggregator** via the same-origin
 * `/api/console/notifications/**` proxy (was the erp-direct
 * `/api/erp/notifications/**`). The proxy attaches the HttpOnly domain-facing
 * IAM OIDC token + `X-Tenant-Id` server-side and forwards to console-bff,
 * which fans in the per-domain inboxes with per-domain failure isolation (D5)
 * + per-domain credential dispatch (D6) — the browser never reads a token or
 * calls a domain directly.
 *
 * NO `refetchInterval` / polling. The inbox is fetched **passively on mount**
 * so the bell badge reflects the unread count without a click; a modest
 * `staleTime` keeps SPA navigation from refetch-storming (cache-freshness TTL,
 * NOT a polling interval). The count refreshes after a mark-read via prefix
 * invalidation.
 *
 * Hooks deliberately DO NOT throw to an error boundary — query `isError` is
 * surfaced so the `NotificationBell` degrades gracefully. (The aggregator
 * itself always returns 200 with `degradedDomains` per D5; `isError` here
 * covers transport/proxy failure, e.g. console-bff unreachable.)
 */

const clampSize = (size?: number): number =>
  clampPageSize(size, NOTIFICATION_DEFAULT_PAGE_SIZE, NOTIFICATION_MAX_PAGE_SIZE);

// ---------------------------------------------------------------------------
// useNotificationInbox — the merged cross-domain inbox (console-bff aggregator).
// ---------------------------------------------------------------------------

async function fetchNotificationInbox(opts: {
  unread?: boolean;
  page?: number;
  size?: number;
}): Promise<NotificationInboxResponse> {
  const qs = new URLSearchParams();
  if (opts.unread !== undefined) qs.set('unread', String(opts.unread));
  qs.set('page', String(Math.max(0, opts.page ?? 0)));
  qs.set('size', String(clampSize(opts.size)));
  const raw = await apiClient.get<unknown>(
    `/api/console/notifications/inbox?${qs.toString()}`,
  );
  return NotificationInboxResponseSchema.parse(raw);
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
    // Mark-read is now addressed to the OWNING domain (the aggregator dispatches
    // it with that domain's credential — contract § 4.5). The bell passes the
    // item's `sourceDomain` + `id`.
    mutationFn: async ({ sourceDomain, id }: { sourceDomain: string; id: string }) => {
      const raw = await apiClient.post<unknown>(
        `/api/console/notifications/${encodeURIComponent(sourceDomain)}/${encodeURIComponent(id)}/read`,
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
