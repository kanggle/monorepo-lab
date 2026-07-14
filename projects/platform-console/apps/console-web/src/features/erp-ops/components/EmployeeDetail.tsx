'use client';

import {
  KNOWN_EMPLOYMENT_STATUSES,
  KNOWN_MASTER_STATUSES,
  employmentStatusTone,
  labelForUnknownEnum,
  masterStatusTone,
  type Employee,
} from '../api/types';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import {
  useCostCenter,
  useDepartment,
  useEmployee,
  useJobGrade,
} from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';
import { RetiredReferenceBadge } from './RetiredReferenceBadge';

/**
 * Employee detail (TASK-PC-FE-010 / § 2.4.8).
 *
 * E1 reference integrity (the headline employee invariant — the
 * task spec explicitly calls out "employee → retired department"):
 * each of `departmentId` / `jobGradeId` / `costCenterId` is
 * resolved via its master detail hook in parallel; if the resolved
 * master is `RETIRED`, the `<RetiredReferenceBadge>` surfaces next
 * to the reference. NEVER silently sanitized.
 *
 * E2 honesty: `effectivePeriod` rendered honestly; a retired-master
 * employee or a `SEPARATED` employee is shown as such (a
 * `<EffectivePeriodBadge>` + an `employmentStatus` chip).
 *
 * Confidential: this view does render the employee `name` (the
 * operator UI surfaces it) — the api module never logs it; the
 * test asserts the log-spy invariant.
 *
 * STRICTLY READ-ONLY.
 */
export interface EmployeeDetailProps {
  id: string;
  initial?: Employee;
}

export function EmployeeDetail({ id, initial }: EmployeeDetailProps) {
  const q = useEmployee(id);
  const e = q.data ?? initial ?? null;
  // E1 — resolve every cross-ref so we can surface retired
  // references honestly.
  const departmentQ = useDepartment(e?.departmentId ?? null);
  const jobGradeQ = useJobGrade(e?.jobGradeId ?? null);
  const costCenterQ = useCostCenter(e?.costCenterId ?? null);
  const departmentRetired = departmentQ.data?.status === 'RETIRED';
  const jobGradeRetired = jobGradeQ.data?.status === 'RETIRED';
  const costCenterRetired = costCenterQ.data?.status === 'RETIRED';

  if (!e) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="erp-employee-detail-loading"
      >
        직원 정보를 불러오는 중…
      </p>
    );
  }

  return (
    <section
      aria-labelledby="erp-employee-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="erp-employee-detail"
    >
      <h2
        id="erp-employee-detail-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        직원 상세
      </h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">사번</dt>
          <dd className="text-foreground" data-testid="erp-employee-number">
            {e.employeeNumber}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">이름</dt>
          <dd className="text-foreground" data-testid="erp-employee-name">
            {e.name}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <StatusBadge
              tone={masterStatusTone(e.status)}
              data-testid="erp-employee-status"
            >
              {labelForUnknownEnum(e.status, KNOWN_MASTER_STATUSES)}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">고용 상태</dt>
          <dd className="text-foreground">
            <StatusBadge
              tone={employmentStatusTone(e.employmentStatus)}
              data-testid="erp-employee-employment"
            >
              {labelForUnknownEnum(e.employmentStatus, KNOWN_EMPLOYMENT_STATUSES)}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">유효기간</dt>
          <dd className="text-foreground">
            <EffectivePeriodBadge period={e.effectivePeriod} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">부서</dt>
          <dd className="text-foreground">
            {e.departmentId ? (
              <>
                <span data-testid="erp-employee-department-ref">
                  {e.departmentId}
                </span>
                {departmentQ.data && (
                  <span className="ml-1 text-muted-foreground">
                    ({departmentQ.data.name})
                  </span>
                )}
                {departmentRetired && (
                  <RetiredReferenceBadge reason="retired department" />
                )}
              </>
            ) : (
              '—'
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">직급</dt>
          <dd className="text-foreground">
            {e.jobGradeId ? (
              <>
                <span data-testid="erp-employee-jobgrade-ref">
                  {e.jobGradeId}
                </span>
                {jobGradeQ.data && (
                  <span className="ml-1 text-muted-foreground">
                    ({jobGradeQ.data.name})
                  </span>
                )}
                {jobGradeRetired && (
                  <RetiredReferenceBadge reason="retired job-grade" />
                )}
              </>
            ) : (
              '—'
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">비용센터</dt>
          <dd className="text-foreground">
            {e.costCenterId ? (
              <>
                <span data-testid="erp-employee-costcenter-ref">
                  {e.costCenterId}
                </span>
                {costCenterQ.data && (
                  <span className="ml-1 text-muted-foreground">
                    ({costCenterQ.data.name})
                  </span>
                )}
                {costCenterRetired && (
                  <RetiredReferenceBadge reason="retired cost-center" />
                )}
              </>
            ) : (
              '—'
            )}
          </dd>
        </div>
      </dl>
    </section>
  );
}
