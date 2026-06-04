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
import type { MasterOption } from './MasterWriteDialog';

/**
 * erp operations section shell (TASK-PC-FE-010 — ADR-MONO-013 Phase 6).
 *
 * READ + WRITE (TASK-PC-FE-046 department pilot → TASK-PC-FE-048 all 5
 * masters): renders the `<AsOfPicker>` (E3) + 5 master lists. When
 * `mastersWritable`, every master gains create/update/retire affordances; FK
 * inputs (employee → 부서/직급/비용센터, cost-center → 부서) are dropdowns
 * sourced from the section's loaded lists. The producer's E6 authz is the
 * authority (a 403 surfaces inline — the console never pre-judges write
 * authority). The other resilience/E2/E3 invariants are unchanged.
 */
export interface ErpOpsScreenProps {
  initialDepartments: DepartmentListResponse | null;
  initialEmployees: EmployeeListResponse | null;
  initialJobGrades: JobGradeListResponse | null;
  initialCostCenters: CostCenterListResponse | null;
  initialBusinessPartners: BusinessPartnerListResponse | null;
  /** TASK-PC-FE-046/048: enable the write affordances across all 5 masters. */
  mastersWritable?: boolean;
}

/** Maps a list response's rows to `{ id, code, name }` parent/FK options. */
function toOptions(
  resp:
    | DepartmentListResponse
    | JobGradeListResponse
    | CostCenterListResponse
    | null,
): MasterOption[] {
  return (resp?.data ?? []).map((d) => ({
    id: d.id,
    code: d.code,
    name: d.name,
  }));
}

export function ErpOpsScreen({
  initialDepartments,
  initialEmployees,
  initialJobGrades,
  initialCostCenters,
  initialBusinessPartners,
  mastersWritable = false,
}: ErpOpsScreenProps) {
  const departments = toOptions(initialDepartments);
  const jobGrades = toOptions(initialJobGrades);
  const costCenters = toOptions(initialCostCenters);

  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-2 text-2xl font-semibold">
        ERP 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        {mastersWritable
          ? '부서·직원·직급·비용센터·거래처 마스터를 조회하고 등록/수정/폐기할 수 있습니다 (TASK-PC-FE-048). 권한이 없는 작업은 실행 시 안내됩니다.'
          : '부서·직원·직급·비용센터·거래처 마스터 조회 (읽기 전용). erp 운영 표면을 콘솔 안에서 조회합니다.'}
      </p>

      <AsOfPicker />

      <DepartmentList
        initial={initialDepartments ?? undefined}
        writable={mastersWritable}
      />
      <EmployeeList
        initial={initialEmployees ?? undefined}
        writable={mastersWritable}
        optionSources={{ departments, jobGrades, costCenters }}
      />
      <JobGradeList
        initial={initialJobGrades ?? undefined}
        writable={mastersWritable}
      />
      <CostCenterList
        initial={initialCostCenters ?? undefined}
        writable={mastersWritable}
        optionSources={{ departments }}
      />
      <BusinessPartnerList
        initial={initialBusinessPartners ?? undefined}
        writable={mastersWritable}
      />
    </section>
  );
}
