import { redirect } from 'next/navigation';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import {
  listDepartments,
  listEmployees,
  listJobGrades,
  listCostCenters,
  listBusinessPartners,
} from './erp-api';
import type {
  DepartmentListResponse,
  EmployeeListResponse,
  JobGradeListResponse,
  CostCenterListResponse,
  BusinessPartnerListResponse,
  ErpListQueryParams,
} from './types';

/**
 * Server-side erp operations section state for the `(console)/erp`
 * route (TASK-PC-FE-010 — the FOURTH non-GAP federation; the FIRST
 * internal-system-primary). STRICTLY READ-ONLY — no mutation ever.
 *
 * Eligibility gate (console-integration-contract § 2.4.8, reusing
 * the § 2.4.5 tenant-model divergence): erp resolves the operator's
 * tenant from the JWT `tenant_id ∈ {erp,*}` claim producer-side —
 * the console does NOT send a tenant. To avoid fabricating a
 * cross-tenant call, the `(console)/erp` PAGE (the app layer — the
 * layer allowed to compose `features/*`) first resolves the
 * operator's erp eligibility from the data-driven registry (§ 2.2,
 * `getCatalog()`) and passes it in here. If not eligible the section
 * blocks with an actionable "no erp-scoped access" state and NO erp
 * call is ever made. erp still rejects cross-tenant producer-side
 * regardless (`403 TENANT_FORBIDDEN`, never weakened here).
 *
 * List-driven + E3 first-class (§ 2.4.8, honest erp constraint —
 * the INVERSE of FE-009 finance which was account-id-driven): erp
 * v1 exposes both list and detail GETs for every master, all
 * supporting `?asOf=<ISO-8601>` point-in-time read. The section
 * therefore opens directly to the master list views with an
 * `<AsOfPicker>` (URL-bound) controlling the point-in-time read for
 * every list / detail query. This state seed fetches a first-page
 * snapshot of each master so the operator's landing page is
 * populated.
 *
 * Resilience boundary (§ 2.4.8 / § 2.5, mirrors `finance-state.ts`):
 *   - `401` (GAP OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade (no partial
 *     authed state; consistent with the FE-002..009 401 discipline).
 *   - `403` (token not erp-scoped / insufficient scope / outside
 *     org subtree per E6 / external traffic at internal-only
 *     boundary per E7) → a non-crashing inline "not available /
 *     not scoped" state.
 *   - `503` / timeout / network → DEGRADED — ONLY the erp section
 *     renders a degraded notice; the console shell + the GAP / wms
 *     / scm / finance sections stay intact.
 *   - **no 429 handling** (§ 2.4.8 — identical to finance § 2.4.7):
 *     erp has no documented 429; a 429 would land as an unexpected
 *     ApiError → degrade rather than crash (no fabricated backoff).
 *   - any other producer error → degrade rather than crash.
 */
export interface ErpSectionState {
  /** First-page list snapshots of every master (success path). All
   *  five are fetched in parallel server-side so the operator's
   *  landing page is populated; subsequent navigation (a different
   *  asOf / page / filter / master detail) goes through the client
   *  hooks behind the proxy. `null` for any leg whose snapshot
   *  failed but the section overall did NOT degrade. */
  departments: DepartmentListResponse | null;
  employees: EmployeeListResponse | null;
  jobGrades: JobGradeListResponse | null;
  costCenters: CostCenterListResponse | null;
  businessPartners: BusinessPartnerListResponse | null;
  /** True when the operator is not erp-eligible (no erp
   *  product/tenant in their registry) — actionable block, no erp
   *  call fabricated. */
  notEligible: boolean;
  /** True on a 403 (token not erp-scoped / insufficient scope) —
   *  inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — erp section degrades only. */
  degraded: boolean;
}

const EMPTY: ErpSectionState = {
  departments: null,
  employees: null,
  jobGrades: null,
  costCenters: null,
  businessPartners: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible  whether the operator is erp-eligible, resolved
 *   by the page from the data-driven registry. `false` ⇒ block (no
 *   erp call).
 * @param asOf      optional E3 point-in-time read — when supplied
 *   it threads through verbatim to every list query (the producer
 *   returns the state-at-that-instant). When omitted the producer
 *   resolves to "today" (UTC).
 */
export async function getErpSectionState(
  eligible: boolean,
  asOf?: string | null,
): Promise<ErpSectionState> {
  if (!eligible) {
    // Not erp-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  // E3 — thread `asOf` through every leg verbatim. Producer-side
  // returns the state-at-that-instant for each master (NOT current
  // state); the rendered UI matches the asOf-instant response. We
  // deliberately omit `active` so retired rows are NOT hidden at
  // the consumer (E2 honesty — visually distinct but rendered).
  const params: ErpListQueryParams = {
    page: 0,
    size: 20,
  };
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
      departments,
      employees,
      jobGrades,
      costCenters,
      businessPartners,
      notEligible: false,
      forbidden: false,
      degraded: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean WHOLE-SESSION re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.status === 403) {
      // Token not erp-scoped / outside org subtree / external
      // traffic at internal-only boundary → inline "not available
      // / not scoped".
      return { ...EMPTY, forbidden: true };
    }
    if (err instanceof ErpUnavailableError) {
      // Degrade ONLY the erp section — shell + GAP / wms / scm /
      // finance sections intact.
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error (incl. an unexpected 429 — erp has
    // no documented rate-limit, identical to finance, so it falls
    // here, not into a fabricated backoff path) → degrade rather
    // than crash.
    return { ...EMPTY, degraded: true };
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
} from './erp-keys';
