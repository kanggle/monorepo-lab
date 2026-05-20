'use client';

import type {
  BusinessPartnerListResponse,
  CostCenterListResponse,
  DepartmentListResponse,
  EmployeeListResponse,
  JobGradeListResponse,
} from '../api/types';
import { AsOfPicker } from './AsOfPicker';
import { DepartmentList } from './DepartmentList';
import { EmployeeList } from './EmployeeList';
import { JobGradeList } from './JobGradeList';
import { CostCenterList } from './CostCenterList';
import { BusinessPartnerList } from './BusinessPartnerList';

/**
 * erp operations section shell (TASK-PC-FE-010 — ADR-MONO-013 Phase
 * 6, the FOURTH non-GAP federated domain; FIRST
 * internal-system-primary).
 *
 * STRICTLY READ-ONLY. The section renders:
 *   - `<AsOfPicker>` (E3 first-class — URL-bound, threads `?asOf=`
 *     through every list / detail query);
 *   - 5 master lists (departments, employees, job-grades,
 *     cost-centers, business-partners) — paginated; retired rows
 *     visually distinct but NEVER hidden;
 *
 * Master detail views live on their own sub-routes (server-side
 * rendered with the page-level seed). The screen here is the
 * landing surface — list-driven with E3 asOf.
 *
 * Initial seed: server-side via `getErpSectionState(eligible,
 * asOf)`; subsequent re-queries (asOf change / page change) go
 * through the same-origin `/api/erp/**` proxy via the client
 * hooks.
 *
 * Resilience (§ 2.5): 401 is handled by the server route
 * (whole-session re-login — not surfaced here); 403 → inline
 * actionable; 503 / timeout → this section degrades only (the
 * console shell + the GAP / wms / scm / finance sections stay
 * intact). **No 429 handling** (§ 2.4.8 — erp has no documented
 * 429; a stray 429 is rendered as a generic error, NOT retried).
 */
export interface ErpOpsScreenProps {
  initialDepartments: DepartmentListResponse | null;
  initialEmployees: EmployeeListResponse | null;
  initialJobGrades: JobGradeListResponse | null;
  initialCostCenters: CostCenterListResponse | null;
  initialBusinessPartners: BusinessPartnerListResponse | null;
}

export function ErpOpsScreen({
  initialDepartments,
  initialEmployees,
  initialJobGrades,
  initialCostCenters,
  initialBusinessPartners,
}: ErpOpsScreenProps) {
  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-2 text-2xl font-semibold">
        ERP 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        부서·직원·직급·비용센터·거래처 마스터 조회 (읽기 전용). erp
        운영 표면을 콘솔 안에서 조회합니다. 마스터 등록/수정/폐기
        작업은 콘솔 범위가 아닙니다.
      </p>

      <AsOfPicker />

      <DepartmentList initial={initialDepartments ?? undefined} />
      <EmployeeList initial={initialEmployees ?? undefined} />
      <JobGradeList initial={initialJobGrades ?? undefined} />
      <CostCenterList initial={initialCostCenters ?? undefined} />
      <BusinessPartnerList initial={initialBusinessPartners ?? undefined} />
    </section>
  );
}
