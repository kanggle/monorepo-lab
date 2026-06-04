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
  type UpdateProfileInput,
  OperatorAssignmentsResponseSchema,
  type OperatorAssignmentsResponse,
  SetOrgScopeResultSchema,
  type SetOrgScopeResult,
} from '../api/types';
// TASK-PC-FE-050 — org_scope drives the erp read-model filter, so a
// successful set must invalidate the erp read-model queries. We import the
// `ERP_KEY` query-key constant from the erp-ops `erp-keys` module — the
// feature's intentionally dependency-free, CLIENT-SAFE key factory (its
// header documents that the erp-ops BARREL must NOT be imported from a
// client component because it re-exports server-only state that drags
// `next/headers` into the client bundle). This is a cross-feature query-key
// coordination only, not a feature-internal logic dependency.
import { ERP_KEY } from '@/features/erp-ops/api/erp-keys';
// Pure (zod-only, client-safe) erp department types for the org_scope
// picker — imported from the types module directly (NOT the barrel; same
// client-safety reason as ERP_KEY above).
import {
  DepartmentListResponseSchema,
  type DepartmentListResponse,
} from '@/features/erp-ops/api/types';

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

// --- org-scope assignments (TASK-PC-FE-050) -------------------------------

/** Assignments query key — scoped by operatorId (the active tenant is
 *  attached server-side, so it is NOT part of the client key; a tenant
 *  switch remounts the page with a fresh server render). */
function assignmentsKey(operatorId: string) {
  return [OPERATORS_KEY, 'assignments', operatorId] as const;
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

// --- mutation: update-profile (self; 204; no reason/key) — TASK-PC-FE-016 -

export function useUpdateOwnProfile() {
  return useMutation({
    mutationFn: async (input: UpdateProfileInput) => {
      // 204 No Content — the proxy returns an empty 204. The body shape
      // mirrors the registry read shape verbatim:
      // `{ operatorContext: { defaultAccountId: string | null } }`.
      await apiClient.post<unknown>('/api/operators/me/profile', {
        operatorContext: { defaultAccountId: input.defaultAccountId },
      });
      return true;
    },
    // No operators-list invalidation — the profile carrier lives on the
    // registry response, not the operators table; consumers re-read via
    // `getCatalog()` (registry is read-side authoritative — fire-and-re-read).
  });
}

// --- mutation: admin-set-profile (admin-on-behalf-of; 204; reason ONLY) ---
// TASK-PC-FE-017 / TASK-BE-307. SUPER_ADMIN sets ANOTHER operator's
// `operatorContext.defaultAccountId`. The proxy attaches the operator token +
// active tenant + `X-Operator-Reason` server-side; NO `Idempotency-Key` per
// the producer matrix (mirror /roles + /status non-uniformity — § 2.4.3
// row 7). Self via this path → producer `400 SELF_PROFILE_UPDATE_FORBIDDEN
// _VIA_ADMIN_PATH` (UI gates the per-row button when the row is self).

interface SetOperatorProfileArgs {
  operatorId: string;
  /** UUID-like opaque string OR `null` to clear (the producer rejects ""). */
  defaultAccountId: string | null;
  /** Operator audit reason — required, non-empty trimmed. */
  reason: string;
}

export function useSetOperatorProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      operatorId,
      defaultAccountId,
      reason,
    }: SetOperatorProfileArgs) => {
      // 204 No Content — the proxy returns an empty 204. Body shape on the
      // wire is `{ defaultAccountId, reason }` (the proxy reconstructs the
      // GAP-side `{ operatorContext: { defaultAccountId } }` body).
      await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/profile`,
        { defaultAccountId, reason },
      );
      return true;
    },
    // Invalidate the operators list so any cached value reflects the new
    // baseline (in v1 the list does not expose `operatorContext` per item;
    // when extended in a follow-up task, this invalidation is the read-back).
    onSuccess: () => invalidateOperators(qc),
  });
}

// --- read: org-scope assignments (TASK-PC-FE-050) -------------------------

async function queryAssignments(
  operatorId: string,
): Promise<OperatorAssignmentsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/operators/${encodeURIComponent(operatorId)}/assignments`,
  );
  return OperatorAssignmentsResponseSchema.parse(raw);
}

/**
 * Reads the operator's assignment row for the ACTIVE tenant (0 or 1 rows).
 * `enabled` is gated on a non-empty operatorId so the dialog can mount the
 * hook unconditionally and only fetch once a target operator is chosen.
 * NO auto-refetch interval / window-focus refetch (a one-shot read on open).
 */
export function useOperatorAssignments(operatorId: string | null) {
  return useQuery({
    queryKey: assignmentsKey(operatorId ?? ''),
    queryFn: () => queryAssignments(operatorId as string),
    enabled: Boolean(operatorId && operatorId.trim()),
    staleTime: 0,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

// --- read: erp departments for the org_scope picker (TASK-PC-FE-050) ------
// Reuses the EXISTING erp departments read proxy
// (`/api/erp/masterdata/departments`, active-tenant scoped, GAP OIDC
// domain-facing token attached server-side). A thin, self-contained client
// query (NOT the erp-ops `useDepartments` hook — that lives behind the
// erp-ops barrel which is server-coupled; importing it into this GAP-domain
// client feature would drag server-only code). On a fetch failure
// (503 / tenant not erp-entitled) `isError` flips and the dialog degrades to
// manual id entry. No asOf / URL-param coupling — the picker reads the
// CURRENT department tree.

const DEPT_PICKER_SIZE = 200;

async function queryOrgScopeDepartments(): Promise<DepartmentListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/departments?page=0&size=${DEPT_PICKER_SIZE}`,
  );
  return DepartmentListResponseSchema.parse(raw);
}

export function useOrgScopeDepartments(enabled: boolean) {
  return useQuery({
    // Distinct from the erp-ops department list keys (no asOf / page / filter
    // dimensions) — this is the org_scope picker's own one-shot read.
    queryKey: [ERP_KEY, 'org-scope-picker', 'departments'] as const,
    queryFn: queryOrgScopeDepartments,
    enabled,
    staleTime: 30_000,
    refetchOnMount: true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

// --- mutation: set-org-scope (PUT; reason ONLY, no key) — TASK-PC-FE-050 ---
// Sets / clears the (operator, tenant) assignment's `org_scope`. The proxy
// route is PUT-only; the api client `put()` sends to the same-origin
// `/api/operators/{id}/assignments/{tenantId}/org-scope`. The tri-state
// `orgScope` (`null` clear / `[]` 차단 / `[ids]` subtree) + the reason are
// in the body; the proxy forwards reason as `X-Operator-Reason`. NO
// idempotency key (idempotent full-replace PUT).

interface SetOrgScopeArgs {
  operatorId: string;
  /** The assignment's tenant (= active tenant; producer requires equality
   *  with `X-Tenant-Id`). */
  tenantId: string;
  /** `null` clears (전체) · `[]` is explicit 차단 · `[ids]` is the subtree. */
  orgScope: string[] | null;
  /** Operator audit reason — required, non-empty trimmed. */
  reason: string;
}

export function useSetOperatorOrgScope() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      operatorId,
      tenantId,
      orgScope,
      reason,
    }: SetOrgScopeArgs) => {
      const raw = await apiClient.put<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/assignments/${encodeURIComponent(tenantId)}/org-scope`,
        // `orgScope: null` is sent as the explicit JSON null literal (clear
        // intent) — NOT omitted; the tri-state is unambiguous on the wire.
        { orgScope, reason },
      );
      return SetOrgScopeResultSchema.parse(raw) as SetOrgScopeResult;
    },
    onSuccess: (_data, vars) => {
      // 1) the operators surface (assignments + list).
      invalidateOperators(qc);
      qc.invalidateQueries({
        queryKey: [OPERATORS_KEY, 'assignments', vars.operatorId],
      });
      // 2) the erp read-model — org_scope changes the read filter (the
      //    employee org-view list is org-scoped at consume time, ERP-BE-008).
      //    Invalidate by prefix `['erp-ops', 'read-model', ...]` (the
      //    erp-ops key factory's `read-model` segment). The next render of
      //    any subscribed read-model card refetches with the new scope.
      qc.invalidateQueries({ queryKey: [ERP_KEY, 'read-model'] });
    },
  });
}
