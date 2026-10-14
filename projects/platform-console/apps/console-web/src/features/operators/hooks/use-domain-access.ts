'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  DomainAccessResponseSchema,
  type DomainAccessResponse,
  GrantDomainSetResultSchema,
  type GrantDomainSetResult,
  GrantableSetOptionListSchema,
  type GrantableSetOption,
} from '../api/domain-access-types';

/**
 * Client-side ADR-MONO-083 「업무 권한」 hooks for ONE operator's per-tenant
 * domain access (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트
 * rows 6-8). Every call goes to the same-origin `/api/operators/**` proxy
 * (the typed api-client's single backend entry point) — the browser never
 * reads a token or calls IAM directly.
 *
 * Header fidelity (contract rows 6-8): GET is a read (no mutation headers);
 * grant (POST) carries ONLY a non-empty `reason` (NO `Idempotency-Key` — a
 * repeat grant is `409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED`, never a
 * double-submit retry key); personal revoke (DELETE) carries ONLY a reason,
 * forwarded in the request BODY (never the query string).
 */

const DOMAIN_ACCESS_KEY = 'operator-domain-access';

function encPath(s: string): string {
  return encodeURIComponent(s);
}

// --- read: one operator's effective domain access + per-set sources -------

async function queryDomainAccess(operatorId: string): Promise<DomainAccessResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/operators/${encPath(operatorId)}/domain-access`,
  );
  return DomainAccessResponseSchema.parse(raw);
}

/** `enabled` gated on a non-empty operatorId — mount unconditionally, fetch
 *  only once a target operator is chosen (mirrors `useOperatorAssignments`). */
export function useOperatorDomainAccess(operatorId: string | null) {
  return useQuery({
    queryKey: [DOMAIN_ACCESS_KEY, operatorId ?? ''] as const,
    queryFn: () => queryDomainAccess(operatorId as string),
    enabled: Boolean(operatorId && operatorId.trim()),
    staleTime: 0,
    refetchOnMount: true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- read: grant-picker options — the EXISTING slice-a list route ---------
//    (an HTTP call to `/api/domain-permission-sets`, NOT a TS import of
//    `features/domain-permission-sets` — architecture.md § Forbidden
//    Dependencies; see domain-access-types.ts file header.) `legacy-compat`
//    is excluded client-side — the producer rejects it with
//    `400 VALIDATION_ERROR` (MIGRATION-source-only backfill marker).

async function queryGrantableSets(): Promise<GrantableSetOption[]> {
  const raw = await apiClient.get<unknown>('/api/domain-permission-sets');
  const parsed = GrantableSetOptionListSchema.parse(raw);
  return parsed.items.filter((s) => s.systemKey !== 'legacy-compat');
}

export function useGrantableDomainSets(enabled: boolean) {
  return useQuery({
    queryKey: [DOMAIN_ACCESS_KEY, 'grantable-sets'] as const,
    queryFn: queryGrantableSets,
    enabled,
    staleTime: 30_000,
    refetchOnMount: true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- mutation: grant (POST; reason ONLY, NO key) — contract row 7 ---------

interface GrantArgs {
  operatorId: string;
  setId: string;
  reason: string;
}

export function useGrantOperatorDomainSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, setId, reason }: GrantArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/operators/${encPath(operatorId)}/domain-permission-sets`,
        { setId, reason },
      );
      return GrantDomainSetResultSchema.parse(raw) as GrantDomainSetResult;
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [DOMAIN_ACCESS_KEY, vars.operatorId] });
    },
  });
}

// --- mutation: personal revoke (DELETE; reason ONLY, NO key) — row 8 ------
//    Removes only the DIRECT/MIGRATION rows of this set — GROUP:* rows
//    survive (the producer's own rule; the screen hides the button when no
//    DIRECT/MIGRATION source exists so this is never attempted on a
//    group-only set).

interface RevokeArgs {
  operatorId: string;
  setId: string;
  reason: string;
}

export function useRevokeOperatorDomainSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, setId, reason }: RevokeArgs) => {
      await apiClient.delete<void>(
        `/api/operators/${encPath(operatorId)}/domain-permission-sets/${encPath(setId)}`,
        { body: { reason } },
      );
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [DOMAIN_ACCESS_KEY, vars.operatorId] });
    },
  });
}
