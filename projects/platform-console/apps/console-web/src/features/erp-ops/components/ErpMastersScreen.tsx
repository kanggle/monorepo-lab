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
import { ReadOnlyNote } from './ReadOnlyNotice';

/**
 * erp **마스터** route screen (`/erp/masters` — TASK-PC-FE-076 drill-in
 * split; the masters slice of the former `ErpOpsScreen`; relocated from the
 * domain root `/erp` by TASK-PC-FE-232). Renders the `<AsOfPicker>` (E3) +
 * the 5 master lists. When `mastersWritable`, every master gains
 * create/update/retire affordances; FK inputs (employee → 부서/직급/비용센터,
 * cost-center → 부서) are dropdowns sourced from the section's loaded lists.
 * The producer's E6 authz is the authority (a 403 surfaces inline — the
 * console never pre-judges write authority).
 *
 * The other four erp sections (개요 / 통합 조회 / 결재함 / 위임) are their own
 * sibling routes reached via the sidebar ERP drill — they no longer stack
 * under this screen. The operator overview snapshot that used to be
 * embedded here (TASK-PC-FE-161 `ErpMastersOverview`) has been PROMOTED to
 * the standalone `/erp` overview (`getErpOverviewState` + `ErpOverviewScreen`)
 * — this screen is now a pure master-lists screen with no overview slot.
 */
export interface ErpMastersScreenProps {
  initialDepartments: DepartmentListResponse | null;
  initialEmployees: EmployeeListResponse | null;
  initialJobGrades: JobGradeListResponse | null;
  initialCostCenters: CostCenterListResponse | null;
  initialBusinessPartners: BusinessPartnerListResponse | null;
  /**
   * TASK-PC-FE-046/048 + TASK-PC-FE-347: the write affordances across all 5 masters. REQUIRED —
   * the page computes it from the operator's `ERP_MASTERDATA_WRITE` (contract § 2.4.3.4); `true` also
   * when the role set is unknown (fail-open), the producer's E6 authz stays the authority.
   */
  canWrite: boolean;
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

export function ErpMastersScreen({
  initialDepartments,
  initialEmployees,
  initialJobGrades,
  initialCostCenters,
  initialBusinessPartners,
  canWrite,
}: ErpMastersScreenProps) {
  const departments = toOptions(initialDepartments);
  const jobGrades = toOptions(initialJobGrades);
  const costCenters = toOptions(initialCostCenters);

  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-2 text-2xl font-semibold">
        ERP 마스터
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        {canWrite
          ? '부서·직원·직급·비용센터·거래처 마스터를 조회하고 등록/수정/폐기할 수 있습니다 (TASK-PC-FE-048). 권한이 없는 작업은 실행 시 안내됩니다.'
          : '부서·직원·직급·비용센터·거래처 마스터 조회 (읽기 전용).'}
      </p>

      {!canWrite && (
        <ReadOnlyNote
          testId="masters-read-only-note"
          action="마스터 등록 · 수정 · 폐기 · 부서 이동 · 계정 연결"
          roleLabel="기준정보 변경"
        />
      )}

      <AsOfPicker />

      <DepartmentList
        initial={initialDepartments ?? undefined}
        writable={canWrite}
        parentOptions={departments}
      />
      <EmployeeList
        initial={initialEmployees ?? undefined}
        writable={canWrite}
        optionSources={{ departments, jobGrades, costCenters }}
      />
      <JobGradeList
        initial={initialJobGrades ?? undefined}
        writable={canWrite}
      />
      <CostCenterList
        initial={initialCostCenters ?? undefined}
        writable={canWrite}
        optionSources={{ departments }}
      />
      <BusinessPartnerList
        initial={initialBusinessPartners ?? undefined}
        writable={canWrite}
      />
    </section>
  );
}
