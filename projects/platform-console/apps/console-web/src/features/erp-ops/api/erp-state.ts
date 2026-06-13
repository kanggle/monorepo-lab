import { redirect } from 'next/navigation';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import {
  listDepartments,
  listEmployees,
  listJobGrades,
  listCostCenters,
  listBusinessPartners,
  listEmployeeOrgViews,
  listDelegationFacts,
} from './erp-api';
import {
  listApprovalRequests,
  listApprovalInbox,
} from './approval-api';
import type {
  DepartmentListResponse,
  EmployeeListResponse,
  JobGradeListResponse,
  CostCenterListResponse,
  BusinessPartnerListResponse,
  EmployeeOrgViewListResponse,
  DelegationFactListResponse,
  ErpListQueryParams,
} from './types';
import type { ApprovalListResponse } from './approval-types';

/**
 * Server-side erp operations state for the FOUR `(console)/erp/**`
 * routes (TASK-PC-FE-010 surface; TASK-PC-FE-076 drill-in split).
 * The single monolithic `getErpSectionState` (one page, 9 parallel
 * legs) is replaced by four FOCUSED loaders — one per sidebar drill
 * destination — so each route fetches ONLY its own slice:
 *
 *   - `/erp`            → {@link getErpMastersState}    (5 masters)
 *   - `/erp/orgview`    → {@link getErpOrgViewState}    (read-model org-view)
 *   - `/erp/approval`   → {@link getErpApprovalState}   (requests + inbox)
 *   - `/erp/delegation` → {@link getErpDelegationState} (delegation facts)
 *
 * STRICTLY READ-ONLY — no mutation ever (the masterdata write
 * affordances + the approval/delegation mutations are client-driven
 * through the same-origin proxy; this seed only reads).
 *
 * Eligibility gate (console-integration-contract § 2.4.8, reusing
 * the § 2.4.5 tenant-model divergence): erp resolves the operator's
 * tenant from the JWT `tenant_id ∈ {erp,*}` claim producer-side —
 * the console does NOT send a tenant. Each route first resolves erp
 * eligibility from the data-driven registry (via
 * `resolveErpEligibility()` in `./erp-eligibility`) and passes it in
 * here. If not eligible the loader blocks with `notEligible` and NO
 * erp call is ever made.
 *
 * Resilience boundary (§ 2.4.8 / § 2.5, unchanged per loader):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade.
 *   - `403` (token not erp-scoped / outside org subtree / external
 *     traffic at internal-only boundary) → inline `forbidden`.
 *   - `503` / timeout / network ({@link ErpUnavailableError}) →
 *     `degraded` — ONLY this route degrades; the console shell + the
 *     other erp routes + the IAM / wms / scm / finance sections stay.
 *   - **no 429 handling** (§ 2.4.8 — identical to finance § 2.4.7):
 *     a stray 429 lands as a generic ApiError → degrade (no
 *     fabricated backoff).
 *
 * Per-route degrade authority change vs the old single page: under
 * the monolith the org-view / approval / delegation legs were
 * "best-effort behind the masters" (caught to `null` so a single
 * outage could not degrade the masters). With the split each of
 * those slices is its OWN route, so each leg is now the SOLE degrade
 * authority for its route — an approval-service outage degrades
 * `/erp/approval` only, never the masters route.
 */

/** Shared base flags for every per-route state. */
interface ErpRouteFlags {
  /** True when the operator is not erp-eligible (no erp
   *  product/tenant in their registry) — actionable block, no erp
   *  call fabricated. */
  notEligible: boolean;
  /** True on a 403 (token not erp-scoped / insufficient scope) — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — this route degrades only. */
  degraded: boolean;
}

const BLOCKED: ErpRouteFlags = {
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/** Maps a thrown producer error to the § 2.5 per-route taxonomy.
 *  401 redirects (never returns); 403 → forbidden; everything else
 *  (incl. {@link ErpUnavailableError} + a stray 429) → degraded. */
function flagsForError(err: unknown): ErpRouteFlags {
  if (err instanceof ApiError && err.status === 401) {
    // No partial authed state → clean WHOLE-SESSION re-login.
    redirect('/login');
  }
  if (err instanceof ApiError && err.status === 403) {
    return { ...BLOCKED, forbidden: true };
  }
  // ErpUnavailableError (503 / timeout / network) + any other
  // producer error (incl. an undocumented 429) → degrade, never crash.
  return { ...BLOCKED, degraded: true };
}

// ---------------------------------------------------------------------------
// `/erp` — masterdata route (TASK-PC-FE-076; was the masters slice of the old
// single page). The 5 masters are the SOLE degrade authority for this route.
// ---------------------------------------------------------------------------

export interface ErpMastersState extends ErpRouteFlags {
  departments: DepartmentListResponse | null;
  employees: EmployeeListResponse | null;
  jobGrades: JobGradeListResponse | null;
  costCenters: CostCenterListResponse | null;
  businessPartners: BusinessPartnerListResponse | null;
}

const EMPTY_MASTERS: ErpMastersState = {
  ...BLOCKED,
  departments: null,
  employees: null,
  jobGrades: null,
  costCenters: null,
  businessPartners: null,
};

/**
 * @param eligible whether the operator is erp-eligible (resolved by
 *   the page from the data-driven registry). `false` ⇒ block (no call).
 * @param asOf optional E3 point-in-time read — threaded through every
 *   master list query verbatim (producer returns the state-at-that-
 *   instant). Omitted ⇒ producer resolves to "today" (UTC).
 */
export async function getErpMastersState(
  eligible: boolean,
  asOf?: string | null,
): Promise<ErpMastersState> {
  if (!eligible) return { ...EMPTY_MASTERS, notEligible: true };

  // E3 — thread `asOf` through every leg verbatim. `active` deliberately
  // omitted so retired rows are NOT hidden (E2 honesty).
  const params: ErpListQueryParams = { page: 0, size: 20 };
  if (asOf) params.asOf = asOf;

  try {
    const [departments, employees, jobGrades, costCenters, businessPartners] =
      await Promise.all([
        listDepartments(params),
        listEmployees(params),
        listJobGrades(params),
        listCostCenters(params),
        listBusinessPartners(params),
      ]);
    return {
      ...BLOCKED,
      departments,
      employees,
      jobGrades,
      costCenters,
      businessPartners,
    };
  } catch (err) {
    return { ...EMPTY_MASTERS, ...flagsForError(err) };
  }
}

// ---------------------------------------------------------------------------
// `/erp/orgview` — read-model employee org-view (TASK-PC-FE-049/069). The
// single read-model leg IS this route's degrade authority (no longer a
// best-effort leg behind the masters).
// ---------------------------------------------------------------------------

export interface ErpOrgViewState extends ErpRouteFlags {
  employeeOrgViews: EmployeeOrgViewListResponse | null;
}

const EMPTY_ORGVIEW: ErpOrgViewState = { ...BLOCKED, employeeOrgViews: null };

export async function getErpOrgViewState(
  eligible: boolean,
  asOf?: string | null,
): Promise<ErpOrgViewState> {
  if (!eligible) return { ...EMPTY_ORGVIEW, notEligible: true };

  const orgViewParams = { page: 0, size: 20, ...(asOf ? { asOf } : {}) };
  try {
    const employeeOrgViews = await listEmployeeOrgViews(orgViewParams);
    return { ...BLOCKED, employeeOrgViews };
  } catch (err) {
    return { ...EMPTY_ORGVIEW, ...flagsForError(err) };
  }
}

// ---------------------------------------------------------------------------
// `/erp/approval` — approval workflow (TASK-PC-FE-051). Requests list + the
// caller's inbox, fetched in parallel; together the route's degrade authority.
// (approval has no asOf concept — single-stage workflow, not an effective-
// dated master read.)
// ---------------------------------------------------------------------------

export interface ErpApprovalState extends ErpRouteFlags {
  approvalRequests: ApprovalListResponse | null;
  approvalInbox: ApprovalListResponse | null;
}

const EMPTY_APPROVAL: ErpApprovalState = {
  ...BLOCKED,
  approvalRequests: null,
  approvalInbox: null,
};

export async function getErpApprovalState(
  eligible: boolean,
): Promise<ErpApprovalState> {
  if (!eligible) return { ...EMPTY_APPROVAL, notEligible: true };

  try {
    const [approvalRequests, approvalInbox] = await Promise.all([
      listApprovalRequests({ page: 0, size: 20 }),
      listApprovalInbox({ page: 0, size: 20 }),
    ]);
    return { ...BLOCKED, approvalRequests, approvalInbox };
  } catch (err) {
    return { ...EMPTY_APPROVAL, ...flagsForError(err) };
  }
}

// ---------------------------------------------------------------------------
// `/erp/delegation` — delegation 관리(write, client-driven `<DelegationScreen>`,
// no server seed) + 현황 (read-model delegation facts, TASK-PC-FE-055). Only
// the read-model fact list is seeded here; it is the route's degrade authority.
// ---------------------------------------------------------------------------

export interface ErpDelegationState extends ErpRouteFlags {
  delegationFacts: DelegationFactListResponse | null;
}

const EMPTY_DELEGATION: ErpDelegationState = {
  ...BLOCKED,
  delegationFacts: null,
};

export async function getErpDelegationState(
  eligible: boolean,
): Promise<ErpDelegationState> {
  if (!eligible) return { ...EMPTY_DELEGATION, notEligible: true };

  try {
    const delegationFacts = await listDelegationFacts({ page: 0, size: 20 });
    return { ...BLOCKED, delegationFacts };
  } catch (err) {
    return { ...EMPTY_DELEGATION, ...flagsForError(err) };
  }
}

// ---------------------------------------------------------------------------
// TanStack Query — queryKey factory + normaliseAsOf live in
// `./erp-keys.ts` (client-safe; no server-only imports) so client
// hooks under `../hooks/use-erp-ops.ts` can import the keys
// without dragging server-only code into the client bundle. The
// existing import surface from `./erp-state` is preserved via the
// re-export below.
// ---------------------------------------------------------------------------

export {
  ERP_KEY,
  normaliseAsOf,
  departmentsListKey,
  departmentDetailKey,
  employeesListKey,
  employeeDetailKey,
  jobGradesListKey,
  jobGradeDetailKey,
  costCentersListKey,
  costCenterDetailKey,
  businessPartnersListKey,
  businessPartnerDetailKey,
  employeeOrgViewsListKey,
  employeeOrgViewDetailKey,
  delegationFactsListKey,
  delegationFactDetailKey,
} from './erp-keys';
