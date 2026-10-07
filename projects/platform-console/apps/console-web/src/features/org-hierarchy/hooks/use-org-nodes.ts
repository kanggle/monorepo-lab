'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { z } from 'zod';
import { apiClient } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';
import {
  OrgNodeListSchema,
  OrgNodeSchema,
  SubtreeTenantsSchema,
  OrgAdminListSchema,
  OrgAdminGrantSchema,
  PlacementEffectSchema,
  PlacementResultSchema,
  type PlacementEffect,
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

// --- tenant placement (TASK-PC-FE-312 / TASK-BE-625) -------------------------

/** 409 from the placement write (admin-api.md § PUT …/org-node). */
export const PLACEMENT_CONFLICT_CODE = 'TENANT_ORG_NODE_CONFLICT';

function placementPreviewPath(tenantId: string, toOrgNodeId: string | null) {
  const base = `/api/tenants/${encPath(tenantId)}/org-node/preview`;
  return toOrgNodeId === null
    ? base
    : `${base}?orgNodeId=${encodeURIComponent(toOrgNodeId)}`;
}

/**
 * The previewed effect of a placement (`lostDomains` …), read BEFORE the write
 * so the confirmation dialog can say what turns off. Never cached across
 * dialogs (`gcTime: 0`) — a stale preview would describe the wrong ceiling.
 */
export function usePlacementPreview(
  tenantId: string,
  toOrgNodeId: string | null,
) {
  return useQuery({
    queryKey: [ORG_NODES_KEY, 'placement-preview', tenantId, toOrgNodeId],
    queryFn: async (): Promise<PlacementEffect> => {
      const raw = await apiClient.get<unknown>(
        placementPreviewPath(tenantId, toOrgNodeId),
      );
      return PlacementEffectSchema.parse(raw);
    },
    staleTime: 0,
    gcTime: 0,
  });
}

export function usePlaceTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      tenantId,
      toOrgNodeId,
      reason,
    }: {
      tenantId: string;
      /** `null` = take the tenant out (무소속). The key is always sent. */
      toOrgNodeId: string | null;
      reason: string;
    }) => {
      const raw = await apiClient.put<unknown>(
        `/api/tenants/${encPath(tenantId)}/org-node`,
        { orgNodeId: toOrgNodeId, reason },
      );
      return PlacementResultSchema.parse(raw);
    },
    // Every node's subtree-tenant list may have changed (source AND
    // destination, plus their ancestors) — refetch the whole org-nodes family.
    onSuccess: () => invalidateTree(qc),
    // 409 TENANT_ORG_NODE_CONFLICT = someone else moved it first: reload what
    // the screen shows so the retry starts from the real placement.
    onError: (err) => {
      if (err instanceof ApiError && err.code === PLACEMENT_CONFLICT_CODE) {
        invalidateTree(qc);
      }
    },
  });
}

const TenantIdPageSchema = z.object({
  items: z.array(z.object({ tenantId: z.string() })),
  page: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});

/** Upper bound on `/api/tenants` pages read for the candidate list
 *  (100 per page — the producer max). */
const MAX_TENANT_PAGES = 20;

/**
 * Tenants the actor could put under a node — built ONLY from reads the console
 * already has (no new endpoint, TASK-PC-FE-312 AC-0):
 *
 *   ① for each top-most node in the actor's reach (`nodes` is the reach-scoped
 *     flat list; a node whose parent is not in it is a reach root),
 *     `GET /api/org-nodes/{id}/tenants` — every PLACED tenant an `ORG_ADMIN`
 *     administers;
 *   ② `GET /api/tenants` — SUPER_ADMIN only, adds the UNPLACED tenants. Any
 *     other actor gets a 403 there, which is expected and swallowed (① stands).
 *
 * The server stays the authority: whatever is picked is checked again by the
 * preview and the write (404 when out of reach).
 */
export function usePlacementCandidates(nodes: OrgNode[], enabled: boolean) {
  const ids = new Set(nodes.map((n) => n.orgNodeId));
  const rootIds = nodes
    .filter((n) => n.parentId === null || !ids.has(n.parentId))
    .map((n) => n.orgNodeId)
    .sort();
  return useQuery({
    queryKey: [ORG_NODES_KEY, 'placement-candidates', rootIds],
    queryFn: async (): Promise<string[]> => {
      const found = new Set<string>();
      for (const id of rootIds) {
        const raw = await apiClient.get<unknown>(
          `/api/org-nodes/${encPath(id)}/tenants`,
        );
        for (const t of SubtreeTenantsSchema.parse(raw).tenantIds) found.add(t);
      }
      try {
        for (let page = 0; page < MAX_TENANT_PAGES; page += 1) {
          const raw = await apiClient.get<unknown>(
            `/api/tenants?page=${page}&size=100`,
          );
          const parsed = TenantIdPageSchema.parse(raw);
          for (const t of parsed.items) found.add(t.tenantId);
          if (parsed.page + 1 >= parsed.totalPages) break;
        }
      } catch (err) {
        // Not SUPER_ADMIN (403) → ① alone is the actor's reach. Anything
        // else is a real failure and must not be hidden as «no candidates».
        if (!(err instanceof ApiError && err.status === 403)) throw err;
      }
      return [...found].sort();
    },
    enabled,
    staleTime: 0,
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
