'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  OrgNodeListSchema,
  OrgNodeSchema,
  SubtreeTenantsSchema,
  OrgAdminListSchema,
  OrgAdminGrantSchema,
  type OrgNode,
  type Ceiling,
  type CreateOrgNodeInput,
  type UpdateOrgNodeInput,
  type GrantOrgAdminInput,
} from '../api/types';

/**
 * Client-side org-hierarchy hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the same-origin
 * `/api/org-nodes/**` proxy (the typed api-client's single backend entry
 * point); the proxy attaches the HttpOnly operator token + tenant server-side —
 * the browser never reads a token or calls IAM directly (contract § 2.3).
 *
 * Every mutation carries an operator-entered `reason` (the reason-capture gate,
 * → `X-Operator-Reason`) and invalidates the `['org-nodes']` tree so the tree +
 * detail re-render from the source of truth.
 */

const ORG_NODES_KEY = 'org-nodes';

function encPath(...segments: string[]): string {
  return segments.map((s) => encodeURIComponent(s)).join('/');
}

// --- read: tree -------------------------------------------------------------

export function useOrgNodes(initial: OrgNode[]) {
  return useQuery({
    queryKey: [ORG_NODES_KEY],
    queryFn: async (): Promise<OrgNode[]> => {
      const raw = await apiClient.get<unknown>('/api/org-nodes');
      return OrgNodeListSchema.parse(raw).items;
    },
    initialData: initial,
    // Seeded from the server render — treat as fresh so we don't immediately
    // re-fetch. A mutation invalidates the key → an explicit refetch.
    staleTime: 30_000,
    refetchOnMount: false,
  });
}

// --- read: subtree tenants (node + all descendants) -------------------------

export function useOrgNodeTenants(id: string | null) {
  return useQuery({
    queryKey: [ORG_NODES_KEY, id, 'tenants'],
    queryFn: async (): Promise<string[]> => {
      const raw = await apiClient.get<unknown>(
        `/api/org-nodes/${encPath(id as string)}/tenants`,
      );
      return SubtreeTenantsSchema.parse(raw).tenantIds;
    },
    enabled: id !== null,
  });
}

// --- read: node admins ------------------------------------------------------

export function useOrgNodeAdmins(id: string | null) {
  return useQuery({
    queryKey: [ORG_NODES_KEY, id, 'admins'],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/api/org-nodes/${encPath(id as string)}/admins`,
      );
      return OrgAdminListSchema.parse(raw).items;
    },
    enabled: id !== null,
  });
}

// --- mutations --------------------------------------------------------------

function invalidateTree(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [ORG_NODES_KEY] });
}

export function useCreateOrgNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      input,
      reason,
    }: {
      input: CreateOrgNodeInput;
      reason: string;
    }) => {
      const raw = await apiClient.post<unknown>('/api/org-nodes', {
        name: input.name,
        parentId: input.parentId,
        ceiling: input.ceiling,
        reason,
      });
      return OrgNodeSchema.parse(raw);
    },
    onSuccess: () => invalidateTree(qc),
  });
}

export function useUpdateOrgNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      patch,
      reason,
    }: {
      id: string;
      patch: UpdateOrgNodeInput;
      reason: string;
    }) => {
      const body: Record<string, unknown> = { reason };
      if (patch.name !== undefined) body.name = patch.name;
      if (patch.parentId !== undefined) body.parentId = patch.parentId;
      const raw = await apiClient.patch<unknown>(
        `/api/org-nodes/${encPath(id)}`,
        body,
      );
      return OrgNodeSchema.parse(raw);
    },
    onSuccess: () => invalidateTree(qc),
  });
}

export function useDeleteOrgNode() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      // DELETE with a body — `apiClient.delete` forwards `opts.body` (the
      // `use-operator-assignments` precedent). The audit reason must NOT ride in
      // the query string: it is operator-authored free text and would be captured
      // by access logs, browser history and `Referer` headers.
      await apiClient.delete<void>(`/api/org-nodes/${encPath(id)}`, {
        body: { reason },
      });
    },
    onSuccess: () => invalidateTree(qc),
  });
}

export function useSetCeiling() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      ceiling,
      reason,
    }: {
      id: string;
      ceiling: Ceiling;
      reason: string;
    }) => {
      const raw = await apiClient.put<unknown>(
        `/api/org-nodes/${encPath(id)}/ceiling`,
        { ceiling, reason },
      );
      return OrgNodeSchema.parse(raw);
    },
    onSuccess: () => invalidateTree(qc),
  });
}

export function useGrantOrgAdmin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      input,
      reason,
    }: {
      id: string;
      input: GrantOrgAdminInput;
      reason: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/org-nodes/${encPath(id)}/admins`,
        { operatorId: input.operatorId, roleName: input.roleName, reason },
      );
      return OrgAdminGrantSchema.parse(raw);
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [ORG_NODES_KEY, vars.id, 'admins'] });
      invalidateTree(qc);
    },
  });
}

export function useRevokeOrgAdmin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      operatorId,
      reason,
    }: {
      id: string;
      operatorId: string;
      reason: string;
    }) => {
      // Body, not query string — see useDeleteOrgNode.
      await apiClient.delete<void>(
        `/api/org-nodes/${encPath(id)}/admins/${encPath(operatorId)}`,
        { body: { reason } },
      );
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: [ORG_NODES_KEY, vars.id, 'admins'] });
      invalidateTree(qc);
    },
  });
}
