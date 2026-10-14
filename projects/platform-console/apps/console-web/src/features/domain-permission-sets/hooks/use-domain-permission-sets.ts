'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  DomainRoleListSchema,
  DomainPermissionSetSchema,
  DomainPermissionSetListSchema,
  type DomainRole,
  type DomainPermissionSet,
  type CreateDomainPermissionSetInput,
  type UpdateDomainPermissionSetInput,
} from '../api/types';

/**
 * Client-side ADR-MONO-083 「업무 권한 세트」 hooks (TASK-PC-FE-338 —
 * architecture.md § Server vs Client Components: React Query is client-only).
 * Every call goes to the same-origin `/api/domain-roles` +
 * `/api/domain-permission-sets/**` proxy; the proxy attaches the HttpOnly
 * operator token + active tenant server-side — the browser never reads a
 * token or calls IAM directly (contract § 2.3).
 *
 * Header fidelity: CREATE sends an `idempotencyKey` (the screen generates it
 * via `crypto.randomUUID()` once per confirmed mutation); PATCH/DELETE carry
 * NO key. Every mutation carries a non-empty operator-entered `reason`
 * (→ `X-Operator-Reason`); the hook never fabricates one.
 */

const SETS_KEY = 'domain-permission-sets';
const ROLES_KEY = 'domain-roles';

function invalidateSets(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [SETS_KEY] });
}

// --- read: role catalog ------------------------------------------------------

async function queryRoles(): Promise<DomainRole[]> {
  const raw = await apiClient.get<unknown>('/api/domain-roles');
  return DomainRoleListSchema.parse(raw).items;
}

export function useDomainRoles(initial?: DomainRole[]) {
  return useQuery({
    queryKey: [ROLES_KEY] as const,
    queryFn: queryRoles,
    initialData: initial,
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- read: sets ---------------------------------------------------------------

async function querySets(): Promise<DomainPermissionSet[]> {
  const raw = await apiClient.get<unknown>('/api/domain-permission-sets');
  return DomainPermissionSetListSchema.parse(raw).items;
}

export function useDomainPermissionSets(initial?: DomainPermissionSet[]) {
  return useQuery({
    queryKey: [SETS_KEY, 'list'] as const,
    queryFn: querySets,
    initialData: initial,
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutation: create (reason + Idempotency-Key) -----------------------------

export function useCreateDomainPermissionSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      input,
      reason,
      idempotencyKey,
    }: {
      input: CreateDomainPermissionSetInput;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>('/api/domain-permission-sets', {
        ...input,
        reason,
        idempotencyKey,
      });
      return DomainPermissionSetSchema.parse(raw);
    },
    onSuccess: () => invalidateSets(qc),
  });
}

// --- mutation: edit (reason only — NO idempotency key, FULL roles replace) --

export function useUpdateDomainPermissionSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      setId,
      patch,
      reason,
    }: {
      setId: string;
      patch: UpdateDomainPermissionSetInput;
      reason: string;
    }) => {
      const body: Record<string, unknown> = { reason };
      if (patch.name !== undefined) body.name = patch.name;
      if (patch.description !== undefined) body.description = patch.description;
      if (patch.roles !== undefined) body.roles = patch.roles;
      const raw = await apiClient.patch<unknown>(
        `/api/domain-permission-sets/${encodeURIComponent(setId)}`,
        body,
      );
      return DomainPermissionSetSchema.parse(raw);
    },
    onSuccess: () => invalidateSets(qc),
  });
}

// --- mutation: delete (reason only — NO idempotency key) ---------------------

export function useDeleteDomainPermissionSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ setId, reason }: { setId: string; reason: string }) => {
      await apiClient.delete<void>(
        `/api/domain-permission-sets/${encodeURIComponent(setId)}`,
        { body: { reason } },
      );
    },
    onSuccess: () => invalidateSets(qc),
  });
}
