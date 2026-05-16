'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  OperatorPageSchema,
  type OperatorPage,
  type OperatorListParams,
  CreateOperatorResultSchema,
  type CreateOperatorResult,
  type CreateOperatorInput,
  EditRolesResultSchema,
  type EditRolesResult,
  ChangeStatusResultSchema,
  type ChangeStatusResult,
  type ChangePasswordInput,
  type OperatorStatus,
} from '../api/types';

/**
 * Client-side operators hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/operators/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly operator token +
 * tenant server-side AND applies the per-endpoint header matrix — the
 * browser never reads a token or calls GAP directly (contract § 2.3), and
 * a password is never read back from a token cookie.
 *
 * Per-endpoint header fidelity (§ 2.4.3): `create` sends an
 * `idempotencyKey` (the dialog generates it via `crypto.randomUUID()`
 * once per confirmed action, reused only on a retry of THAT action);
 * `edit-roles`/`change-status` send a reason ONLY (NO key); `change-
 * password` is the self path (no reason, no key). The hooks never
 * fabricate a reason — it is required input from the confirm dialog.
 *
 * A password is NEVER placed in a query string, log, or React Query key
 * (the create / change-password keys carry no secret). On a roles change
 * the producer invalidates its own permission cache (awareness note —
 * `admin:operator:perm:{operatorId}`); the console invalidates its local
 * operators list so the table reflects the change.
 */

const OPERATORS_KEY = 'operators';

function listKey(params: OperatorListParams) {
  return [
    OPERATORS_KEY,
    params.status ?? null,
    params.page ?? 0,
    params.size ?? 20,
  ] as const;
}

// --- read: list -----------------------------------------------------------

async function queryList(
  params: OperatorListParams,
): Promise<OperatorPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(Math.min(100, Math.max(1, params.size ?? 20))));
  const raw = await apiClient.get<unknown>(
    `/api/operators?${qs.toString()}`,
  );
  return OperatorPageSchema.parse(raw);
}

export function useOperatorsList(
  params: OperatorListParams,
  initial?: OperatorPage,
) {
  return useQuery({
    queryKey: listKey(params),
    queryFn: () => queryList(params),
    initialData: initial,
    // Seeded from the server render ⇒ that page is fresh (the server
    // already fetched it with the operator token). A filter / page change
    // is a new queryKey → one fresh proxy call. NO auto-refetch interval.
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
}

function invalidateOperators(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [OPERATORS_KEY] });
}

// --- mutation: create -----------------------------------------------------

interface CreateArgs {
  input: CreateOperatorInput;
  reason: string;
  /** Stable per the confirmed create (the confirm dialog generates it). */
  idempotencyKey: string;
}

export function useCreateOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ input, reason, idempotencyKey }: CreateArgs) => {
      const raw = await apiClient.post<unknown>('/api/operators', {
        ...input,
        reason,
        idempotencyKey,
      });
      return CreateOperatorResultSchema.parse(raw) as CreateOperatorResult;
    },
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- mutation: edit-roles (NO idempotency key — per the producer) ---------

interface EditRolesArgs {
  operatorId: string;
  roles: string[];
  reason: string;
}

export function useEditOperatorRoles() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, roles, reason }: EditRolesArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/roles`,
        { roles, reason },
      );
      return EditRolesResultSchema.parse(raw) as EditRolesResult;
    },
    // The producer invalidates its own perm cache on a role change; we
    // invalidate the local operators list so the table reflects it.
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- mutation: change-status (NO idempotency key — per the producer) ------

interface ChangeStatusArgs {
  operatorId: string;
  status: OperatorStatus;
  reason: string;
}

export function useChangeOperatorStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, status, reason }: ChangeStatusArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/status`,
        { status, reason },
      );
      return ChangeStatusResultSchema.parse(raw) as ChangeStatusResult;
    },
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- mutation: change-password (self; 204; no reason/key) -----------------

export function useChangeOwnPassword() {
  return useMutation({
    mutationFn: async (input: ChangePasswordInput) => {
      // 204 No Content — the proxy returns an empty 200/204; we ignore the
      // body. The password is in the POST body to the same-origin proxy
      // ONLY (HTTPS, server-side forwarded) — never a query string / log.
      await apiClient.post<unknown>('/api/operators/me/password', {
        currentPassword: input.currentPassword,
        newPassword: input.newPassword,
      });
      return true;
    },
    // No list invalidation — changing your own password does not alter the
    // operators table.
  });
}
