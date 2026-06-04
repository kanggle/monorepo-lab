/**
 * Client-safe TanStack Query key factories + small input helpers
 * for `features/erp-ops` (TASK-PC-FE-010).
 *
 * Server-only modules (`erp-api.ts` reads cookies; `erp-state.ts`
 * imports the api client) must NOT be imported from client
 * components — Next.js refuses to bundle `next/headers` for the
 * client. This module is intentionally **isolated and dependency-
 * free** so the client hooks (`hooks/use-erp-ops.ts`) can import
 * the keys + `normaliseAsOf` without dragging server-only code.
 *
 * `erp-state.ts` re-exports these symbols (so the existing
 * `erp-state.ts` import surface is preserved for callers that
 * already import from it).
 */

export const ERP_KEY = 'erp-ops';

/** Sanitises the asOf input to the wire string (URL query-param
 *  form). Strings only — no Number coercion. Empty / undefined →
 *  the wire param is omitted (producer resolves to "today" UTC). */
export function normaliseAsOf(asOf?: string | null): string | undefined {
  if (!asOf) return undefined;
  const t = asOf.trim();
  return t || undefined;
}

export function departmentsListKey(
  asOf: string | undefined,
  page: number,
  size: number,
  filters?: Record<string, string>,
) {
  return [ERP_KEY, 'departments', 'list', asOf ?? null, page, size, filters ?? null] as const;
}

export function departmentDetailKey(
  id: string,
  asOf: string | undefined,
) {
  return [ERP_KEY, 'departments', 'detail', id, asOf ?? null] as const;
}

export function employeesListKey(
  asOf: string | undefined,
  page: number,
  size: number,
  filters?: Record<string, string>,
) {
  return [ERP_KEY, 'employees', 'list', asOf ?? null, page, size, filters ?? null] as const;
}

export function employeeDetailKey(
  id: string,
  asOf: string | undefined,
) {
  return [ERP_KEY, 'employees', 'detail', id, asOf ?? null] as const;
}

export function jobGradesListKey(
  asOf: string | undefined,
  page: number,
  size: number,
) {
  return [ERP_KEY, 'job-grades', 'list', asOf ?? null, page, size] as const;
}

export function jobGradeDetailKey(
  id: string,
  asOf: string | undefined,
) {
  return [ERP_KEY, 'job-grades', 'detail', id, asOf ?? null] as const;
}

export function costCentersListKey(
  asOf: string | undefined,
  page: number,
  size: number,
  filters?: Record<string, string>,
) {
  return [ERP_KEY, 'cost-centers', 'list', asOf ?? null, page, size, filters ?? null] as const;
}

export function costCenterDetailKey(
  id: string,
  asOf: string | undefined,
) {
  return [ERP_KEY, 'cost-centers', 'detail', id, asOf ?? null] as const;
}

export function businessPartnersListKey(
  asOf: string | undefined,
  page: number,
  size: number,
  filters?: Record<string, string>,
) {
  return [ERP_KEY, 'business-partners', 'list', asOf ?? null, page, size, filters ?? null] as const;
}

export function businessPartnerDetailKey(
  id: string,
  asOf: string | undefined,
) {
  return [ERP_KEY, 'business-partners', 'detail', id, asOf ?? null] as const;
}

// ---------------------------------------------------------------------------
// read-model — employee org-view (TASK-PC-FE-049)
// ---------------------------------------------------------------------------

export function employeeOrgViewsListKey(
  asOf: string | undefined,
  page: number,
  size: number,
  filters?: { departmentId?: string; status?: string },
) {
  return [ERP_KEY, 'read-model', 'employees', 'list', asOf ?? null, page, size, filters ?? null] as const;
}

export function employeeOrgViewDetailKey(
  id: string,
  asOf: string | undefined,
) {
  return [ERP_KEY, 'read-model', 'employees', 'detail', id, asOf ?? null] as const;
}

// ---------------------------------------------------------------------------
// approval workflow (TASK-PC-FE-051) — list / detail / inbox keys. The
// mutations (create + 4 transitions) invalidate the `approval` prefix so
// list + detail + inbox all refetch on success.
// ---------------------------------------------------------------------------

export const APPROVAL_PREFIX = 'approval';

export function approvalListKey(
  status: string | undefined,
  role: string | undefined,
  page: number,
  size: number,
) {
  return [
    ERP_KEY,
    APPROVAL_PREFIX,
    'list',
    status ?? null,
    role ?? null,
    page,
    size,
  ] as const;
}

export function approvalDetailKey(id: string) {
  return [ERP_KEY, APPROVAL_PREFIX, 'detail', id] as const;
}

export function approvalInboxKey(page: number, size: number) {
  return [ERP_KEY, APPROVAL_PREFIX, 'inbox', page, size] as const;
}
