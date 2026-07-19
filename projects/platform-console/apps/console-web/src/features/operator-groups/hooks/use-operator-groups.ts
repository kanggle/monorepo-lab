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
  GroupSchema,
  GroupPageSchema,
  GroupMemberListSchema,
  GroupGrantListSchema,
  type Group,
  type GroupPage,
  type GroupListParams,
  type CreateGroupInput,
  type UpdateGroupInput,
  type AddGrantsInput,
} from '../api/types';

/**
 * Client-side operator-groups hooks (TASK-PC-FE-250 — architecture.md § Server
 * vs Client Components: React Query is client-only). Every call goes to the
 * same-origin `/api/groups/**` proxy (the typed api-client's single backend
 * entry point); the proxy attaches the HttpOnly operator token + active tenant
 * server-side — the browser never reads a token or calls IAM directly
 * (contract § 2.3).
 *
 * Header fidelity: create / add-member / add-grants send an `idempotencyKey`
 * (the confirm dialog generates it via `crypto.randomUUID()` once per confirmed
 * mutation); update / delete / remove-member / revoke-grant carry NO key. Every
 * mutation carries a non-empty operator-entered `reason` (→ `X-Operator-Reason`);
 * the hook never fabricates one. DELETEs forward the reason in the BODY (never
 * the query string — it is operator-authored free text captured by access logs
 * / history / `Referer` otherwise; the `use-org-nodes` precedent).
 */

const GROUPS_KEY = 'groups';

function encPath(...segments: string[]): string {
  return segments.map((s) => encodeURIComponent(s)).join('/');
}

function listKey(params: GroupListParams) {
  return [
    GROUPS_KEY,
    'list',
    params.tenantId ?? null,
    params.page ?? 0,
    params.size ?? 20,
  ] as const;
}

function invalidateGroups(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [GROUPS_KEY] });
}

// --- read: list -------------------------------------------------------------

async function queryList(params: GroupListParams): Promise<GroupPage> {
  const qs = new URLSearchParams();
  if (params.tenantId) qs.set('tenantId', params.tenantId);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampPageSize(params.size, 20, 100)));
  const raw = await apiClient.get<unknown>(`/api/groups?${qs.toString()}`);
  return GroupPageSchema.parse(raw);
}

export function useGroupsList(params: GroupListParams, initial?: GroupPage) {
  const seeded =
    initial !== undefined && (params.page ?? 0) === 0 && !params.tenantId;
  return useQuery({
    queryKey: listKey(params),
    queryFn: () => queryList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- read: detail -----------------------------------------------------------

export function useGroup(groupId: string | null, initial?: Group) {
  return useQuery({
    queryKey: [GROUPS_KEY, 'detail', groupId] as const,
    queryFn: async (): Promise<Group> => {
      const raw = await apiClient.get<unknown>(
        `/api/groups/${encPath(groupId as string)}`,
      );
      return GroupSchema.parse(raw);
    },
    enabled: groupId !== null,
    initialData: initial,
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- read: members ----------------------------------------------------------

export function useGroupMembers(groupId: string | null) {
  return useQuery({
    queryKey: [GROUPS_KEY, groupId, 'members'],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/api/groups/${encPath(groupId as string)}/members`,
      );
      return GroupMemberListSchema.parse(raw).items;
    },
    enabled: groupId !== null,
  });
}

// --- read: grants -----------------------------------------------------------

export function useGroupGrants(groupId: string | null) {
  return useQuery({
    queryKey: [GROUPS_KEY, groupId, 'grants'],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/api/groups/${encPath(groupId as string)}/grants`,
      );
      return GroupGrantListSchema.parse(raw).items;
    },
    enabled: groupId !== null,
  });
}

// --- mutation: create -------------------------------------------------------

export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      input,
      reason,
      idempotencyKey,
    }: {
      input: CreateGroupInput;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>('/api/groups', {
        ...input,
        reason,
        idempotencyKey,
      });
      return GroupSchema.parse(raw);
    },
    onSuccess: () => invalidateGroups(qc),
  });
}

// --- mutation: update (NO idempotency key — partial PATCH) ------------------

export function useUpdateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      patch,
      reason,
    }: {
      groupId: string;
      patch: UpdateGroupInput;
      reason: string;
    }) => {
      const body: Record<string, unknown> = { reason };
      if (patch.name !== undefined) body.name = patch.name;
      if (patch.description !== undefined) body.description = patch.description;
      const raw = await apiClient.patch<unknown>(
        `/api/groups/${encPath(groupId)}`,
        body,
      );
      return GroupSchema.parse(raw);
    },
    onSuccess: (updated) => {
      invalidateGroups(qc);
      qc.setQueryData([GROUPS_KEY, 'detail', updated.groupId], updated);
    },
  });
}

// --- mutation: delete -------------------------------------------------------

export function useDeleteGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      reason,
    }: {
      groupId: string;
      reason: string;
    }) => {
      await apiClient.delete<void>(`/api/groups/${encPath(groupId)}`, {
        body: { reason },
      });
    },
    onSuccess: () => invalidateGroups(qc),
  });
}

// --- mutation: add member ---------------------------------------------------

export function useAddMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      operatorId,
      reason,
      idempotencyKey,
    }: {
      groupId: string;
      operatorId: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      await apiClient.post<unknown>(`/api/groups/${encPath(groupId)}/members`, {
        operatorId,
        reason,
        idempotencyKey,
      });
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [GROUPS_KEY, vars.groupId, 'members'] });
      invalidateGroups(qc);
    },
  });
}

// --- mutation: remove member ------------------------------------------------

export function useRemoveMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      operatorId,
      reason,
    }: {
      groupId: string;
      operatorId: string;
      reason: string;
    }) => {
      await apiClient.delete<void>(
        `/api/groups/${encPath(groupId)}/members/${encPath(operatorId)}`,
        { body: { reason } },
      );
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [GROUPS_KEY, vars.groupId, 'members'] });
      invalidateGroups(qc);
    },
  });
}

// --- mutation: add grants ---------------------------------------------------

export function useAddGrants() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      input,
      reason,
      idempotencyKey,
    }: {
      groupId: string;
      input: AddGrantsInput;
      reason: string;
      idempotencyKey: string;
    }) => {
      const body: Record<string, unknown> = { reason, idempotencyKey };
      if (input.roles !== undefined) body.roles = input.roles;
      if (input.tenantAssignments !== undefined) {
        body.tenantAssignments = input.tenantAssignments;
      }
      await apiClient.post<unknown>(
        `/api/groups/${encPath(groupId)}/grants`,
        body,
      );
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [GROUPS_KEY, vars.groupId, 'grants'] });
      invalidateGroups(qc);
    },
  });
}

// --- mutation: revoke grant -------------------------------------------------

export function useRemoveGrant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      groupId,
      grantId,
      reason,
    }: {
      groupId: string;
      grantId: string;
      reason: string;
    }) => {
      await apiClient.delete<void>(
        `/api/groups/${encPath(groupId)}/grants/${encPath(grantId)}`,
        { body: { reason } },
      );
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [GROUPS_KEY, vars.groupId, 'grants'] });
      invalidateGroups(qc);
    },
  });
}
