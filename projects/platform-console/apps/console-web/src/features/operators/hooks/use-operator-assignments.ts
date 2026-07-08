'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
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
import {
  OPERATORS_KEY,
  assignmentsKey,
  invalidateOperators,
} from './operators-keys';

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
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- read: erp departments for the org_scope picker (TASK-PC-FE-050) ------
// Reuses the EXISTING erp departments read proxy
// (`/api/erp/masterdata/departments`, active-tenant scoped, IAM OIDC
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
    ...READ_QUERY_REFETCH,
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

// --- mutation: assign-operator (POST; reason ONLY, no key) — TASK-PC-FE-157 -
// Creates the (operator, active-tenant) assignment row ("내 직원에게 내 테넌트
// 접근 부여"). The proxy route is POST-only; it attaches the operator token +
// active tenant + `X-Operator-Reason` server-side. The tenantId in the path is
// the ACTIVE tenant (the producer confines the mutation to it). NO idempotency
// key (the (operator, tenant) PK is the natural dedupe; a re-create is
// 409 ASSIGNMENT_ALREADY_EXISTS, mapped inline).

interface AssignArgs {
  operatorId: string;
  /** The target tenant (= active tenant slug; producer confines to it). */
  tenantId: string;
  /** Operator audit reason — required, non-empty trimmed. */
  reason: string;
}

export function useAssignOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, tenantId, reason }: AssignArgs) => {
      await apiClient.post<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/assignments/${encodeURIComponent(tenantId)}`,
        { reason },
      );
      return true;
    },
    onSuccess: (_data, vars) => {
      // The newly-assigned operator now falls inside the active-tenant list
      // scope → refresh the list + that operator's assignment row.
      invalidateOperators(qc);
      qc.invalidateQueries({
        queryKey: [OPERATORS_KEY, 'assignments', vars.operatorId],
      });
    },
  });
}

// --- mutation: unassign-operator (DELETE; reason ONLY, no key) — PC-FE-157 --
// Removes the (operator, active-tenant) assignment row. The proxy route is
// DELETE-only; the reason travels in the request body (→ `X-Operator-Reason`
// server-side). A home-tenant-only operator (no explicit assignment) →
// 404 ASSIGNMENT_NOT_FOUND (mapped inline).

interface UnassignArgs {
  operatorId: string;
  /** The assignment's tenant (= active tenant slug). */
  tenantId: string;
  /** Operator audit reason — required, non-empty trimmed. */
  reason: string;
}

export function useUnassignOperator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ operatorId, tenantId, reason }: UnassignArgs) => {
      // DELETE with a body — `apiClient.delete` forwards `opts.body`
      // (serialised by the shared fetch core); the DELETE route handler reads
      // `{ reason }` and forwards it as `X-Operator-Reason`.
      await apiClient.delete<unknown>(
        `/api/operators/${encodeURIComponent(operatorId)}/assignments/${encodeURIComponent(tenantId)}`,
        { body: { reason } },
      );
      return true;
    },
    onSuccess: (_data, vars) => {
      invalidateOperators(qc);
      qc.invalidateQueries({
        queryKey: [OPERATORS_KEY, 'assignments', vars.operatorId],
      });
    },
  });
}
