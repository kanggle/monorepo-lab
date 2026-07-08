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
  TenantSchema,
  type Tenant,
  TenantPageSchema,
  type TenantPage,
  type TenantListParams,
  type CreateTenantInput,
  type UpdateTenantInput,
} from '../api/types';

/**
 * Client-side tenant-management hooks (TASK-PC-FE-226 — architecture.md
 * § Server vs Client Components: React Query is client-only). Every call goes
 * to the same-origin `/api/tenants/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly operator token +
 * active tenant server-side — the browser never reads a token or calls IAM
 * directly (contract § 2.3).
 *
 * No auto-refetch loop (`READ_QUERY_REFETCH`); a re-query is a filter/page
 * change (new queryKey) or a mutation-success `invalidateQueries`.
 *
 * Header fidelity note: `create` sends an `idempotencyKey` (the confirm
 * dialog generates it via `crypto.randomUUID()` once per confirmed create,
 * mirroring the operators-create precedent — the producer only
 * RECOMMENDS one, but sending it costs nothing and dedupes a retried
 * confirm); `update` carries NO key (partial PATCH is naturally idempotent).
 * Both mutations require a non-empty operator-entered `reason` — the hook
 * never fabricates one.
 */

const TENANTS_KEY = 'tenants';

function listKey(params: TenantListParams) {
  return [
    TENANTS_KEY,
    'list',
    params.status ?? null,
    params.tenantType ?? null,
    params.page ?? 0,
    params.size ?? 20,
  ] as const;
}

function invalidateTenants(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [TENANTS_KEY] });
}

// --- read: list -------------------------------------------------------------

async function queryList(params: TenantListParams): Promise<TenantPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.tenantType) qs.set('tenantType', params.tenantType);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  const raw = await apiClient.get<unknown>(`/api/tenants?${qs.toString()}`);
  return TenantPageSchema.parse(raw);
}

export function useTenantsList(
  params: TenantListParams,
  initial?: TenantPage,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.tenantType;
  return useQuery({
    queryKey: listKey(params),
    queryFn: () => queryList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- read: detail ------------------------------------------------------------

async function queryDetail(tenantId: string): Promise<Tenant> {
  const raw = await apiClient.get<unknown>(
    `/api/tenants/${encodeURIComponent(tenantId)}`,
  );
  return TenantSchema.parse(raw);
}

export function useTenant(tenantId: string, initial?: Tenant) {
  return useQuery({
    queryKey: [TENANTS_KEY, 'detail', tenantId] as const,
    queryFn: () => queryDetail(tenantId),
    initialData: initial,
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutation: create --------------------------------------------------------

interface CreateArgs {
  input: CreateTenantInput;
  reason: string;
  idempotencyKey: string;
}

export function useCreateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ input, reason, idempotencyKey }: CreateArgs) => {
      const raw = await apiClient.post<unknown>('/api/tenants', {
        ...input,
        reason,
        idempotencyKey,
      });
      return TenantSchema.parse(raw) as Tenant;
    },
    onSuccess: () => invalidateTenants(qc),
  });
}

// --- mutation: update (NO idempotency key — partial PATCH) ------------------

interface UpdateArgs {
  tenantId: string;
  input: UpdateTenantInput;
  reason: string;
}

export function useUpdateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ tenantId, input, reason }: UpdateArgs) => {
      const raw = await apiClient.patch<unknown>(
        `/api/tenants/${encodeURIComponent(tenantId)}`,
        { ...input, reason },
      );
      return TenantSchema.parse(raw) as Tenant;
    },
    onSuccess: (updated) => {
      invalidateTenants(qc);
      qc.setQueryData([TENANTS_KEY, 'detail', updated.tenantId], updated);
    },
  });
}
