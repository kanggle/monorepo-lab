'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  NotificationTemplateListSchema,
  type NotificationTemplateList,
  NotificationTemplateDetailSchema,
  type NotificationTemplateDetail,
  type NotificationTemplateListParams,
  type CreateTemplateBody,
  type UpdateTemplateBody,
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  NOTIFICATION_MAX_PAGE_SIZE,
} from '../api/notification-types';

/**
 * Client-side ecommerce-ops notification template hooks (TASK-PC-FE-089 — ADR-031 Phase 5b).
 * Every call goes to the same-origin `/api/ecommerce/notifications/templates/**` proxy (the
 * typed API client's single backend entry point); the proxy attaches the HttpOnly
 * **domain-facing IAM OIDC token** server-side — the browser never reads a token
 * or calls the ecommerce gateway directly (contract § 2.3 / § 2.4.10.4).
 *
 * Mutation discipline: NO `Idempotency-Key` (the producer defines none) —
 * confirm-gate + producer state guards (409) are the double-submit defence.
 * type/channel are immutable after creation — update uses PUT with only subject+body.
 */

const NOTIFICATIONS_KEY = 'ecommerce-notifications';

const clampSize = (size?: number): number =>
  clampPageSize(size, NOTIFICATION_DEFAULT_PAGE_SIZE, NOTIFICATION_MAX_PAGE_SIZE);

// --- list -----------------------------------------------------------------

export function notificationsKey(params: NotificationTemplateListParams) {
  return [
    NOTIFICATIONS_KEY,
    'list',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildNotificationsQs(
  params: NotificationTemplateListParams,
): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchTemplates(
  params: NotificationTemplateListParams,
): Promise<NotificationTemplateList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/notifications/templates?${buildNotificationsQs(params)}`,
  );
  return NotificationTemplateListSchema.parse(raw);
}

export function useNotificationTemplates(
  params: NotificationTemplateListParams,
  initial?: NotificationTemplateList,
) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: notificationsKey(params),
    queryFn: () => fetchTemplates(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- detail ---------------------------------------------------------------

async function fetchTemplate(
  id: string,
): Promise<NotificationTemplateDetail> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/notifications/templates/${encodeURIComponent(id)}`,
  );
  return NotificationTemplateDetailSchema.parse(raw);
}

export function useNotificationTemplate(
  id: string | null,
  initial?: NotificationTemplateDetail,
) {
  return useQuery({
    queryKey: [NOTIFICATIONS_KEY, 'detail', id] as const,
    queryFn: () => fetchTemplate(id as string),
    enabled: id !== null,
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutations ------------------------------------------------------------

/**
 * After a mutation: DROP the list cache, INVALIDATE the (active) detail.
 *
 * `removeQueries` for the list (NOT invalidate): every template mutation lives
 * on a separate route (`/new` create, `/[id]` update) and redirects to the
 * `force-dynamic` list, so the list query is inactive at mutation time — a plain
 * invalidate would never refetch it, and the seeded page-0 query
 * (`refetchOnMount: false` + `staleTime: 30s`) would shadow the fresh SSR seed
 * with the stale pre-mutation snapshot (new/changed template missing until a
 * hard reload). Removing the cache lets the fresh seed re-seed it. The detail
 * stays `invalidateQueries`: it IS active on `/[id]` during an update, so a
 * seamless background refetch is correct there. (TASK-PC-FE-126)
 */
function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  templateId?: string,
) {
  qc.removeQueries({ queryKey: [NOTIFICATIONS_KEY, 'list'] });
  if (templateId) {
    qc.invalidateQueries({
      queryKey: [NOTIFICATIONS_KEY, 'detail', templateId],
    });
  }
}

export function useCreateTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTemplateBody) =>
      apiClient.post<{ templateId: string }>(
        '/api/ecommerce/notifications/templates',
        body,
      ),
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdateTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      body,
    }: {
      id: string;
      body: UpdateTemplateBody;
    }) =>
      // PUT with ONLY subject+body — type/channel are immutable (NOT sent)
      apiClient.put<{ templateId: string }>(
        `/api/ecommerce/notifications/templates/${encodeURIComponent(id)}`,
        body,
      ),
    onSuccess: (_d, { id }) => invalidate(qc, id),
  });
}
