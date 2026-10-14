'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  GroupDomainSetListSchema,
  GrantGroupDomainSetResultSchema,
  type GrantGroupDomainSetResult,
  GrantableSetOptionListSchema,
  type GrantableSetOption,
} from '../api/domain-sets-types';

/**
 * Client-side ADR-MONO-083 group 「업무 권한 세트」 hooks (TASK-PC-FE-338 slice b
 * — admin-api.md § 도메인 업무 권한 세트 rows 9-11). Every call goes to the
 * same-origin `/api/groups/**` proxy — mirrors `use-operator-groups.ts`'s
 * grants hooks exactly.
 *
 * Header fidelity: add (POST) sends an `idempotencyKey` (the confirm dialog
 * generates it via `crypto.randomUUID()` once per confirmed mutation, same
 * posture as add-member/add-grants); remove (DELETE) carries NO key. Every
 * mutation carries a non-empty operator-entered `reason`.
 */

const GROUP_DOMAIN_SETS_KEY = 'group-domain-sets';

function encPath(s: string): string {
  return encodeURIComponent(s);
}

// --- read: this group's domain permission sets -----------------------------

export function useGroupDomainSets(groupId: string | null) {
  return useQuery({
    queryKey: [GROUP_DOMAIN_SETS_KEY, groupId ?? ''] as const,
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/api/groups/${encPath(groupId as string)}/domain-permission-sets`,
      );
      return GroupDomainSetListSchema.parse(raw).items;
    },
    enabled: groupId !== null,
  });
}

// --- read: grant-picker options — the EXISTING slice-a list route ---------
//    (HTTP call, not a cross-feature TS import — see domain-sets-types.ts
//    file header.) `legacy-compat` excluded — MIGRATION-source-only.

async function queryGrantableSets(): Promise<GrantableSetOption[]> {
  const raw = await apiClient.get<unknown>('/api/domain-permission-sets');
  const parsed = GrantableSetOptionListSchema.parse(raw);
  return parsed.items.filter((s) => s.systemKey !== 'legacy-compat');
}

export function useGrantableDomainSetsForGroup(enabled: boolean) {
  return useQuery({
    queryKey: [GROUP_DOMAIN_SETS_KEY, 'grantable-sets'] as const,
    queryFn: queryGrantableSets,
    enabled,
    staleTime: 30_000,
    refetchOnMount: true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- mutation: add (POST; reason + idempotencyKey BOTH required) ----------

interface AddArgs {
  groupId: string;
  setId: string;
  reason: string;
  idempotencyKey: string;
}

export function useAddGroupDomainSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      setId,
      reason,
      idempotencyKey,
    }: AddArgs): Promise<GrantGroupDomainSetResult> => {
      const raw = await apiClient.post<unknown>(
        `/api/groups/${encPath(groupId)}/domain-permission-sets`,
        { setId, reason, idempotencyKey },
      );
      return GrantGroupDomainSetResultSchema.parse(raw);
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [GROUP_DOMAIN_SETS_KEY, vars.groupId] });
    },
  });
}

// --- mutation: remove (DELETE; reason ONLY, NO key) -------------------------

interface RemoveArgs {
  groupId: string;
  setId: string;
  reason: string;
}

export function useRemoveGroupDomainSet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ groupId, setId, reason }: RemoveArgs) => {
      await apiClient.delete<void>(
        `/api/groups/${encPath(groupId)}/domain-permission-sets/${encPath(setId)}`,
        { body: { reason } },
      );
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [GROUP_DOMAIN_SETS_KEY, vars.groupId] });
    },
  });
}
