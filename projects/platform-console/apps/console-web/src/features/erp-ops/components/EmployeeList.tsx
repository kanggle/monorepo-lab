'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_EMPLOYMENT_STATUSES,
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  type EmployeeListResponse,
  type ErpListQueryParams,
} from '../api/types';
import { useEmployees } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Employees list (TASK-PC-FE-010 / § 2.4.8) — paginated table.
 *
 * E2 honesty: retired (master status `RETIRED` OR `effectiveTo` in
 * the past) AND `SEPARATED` employees are rendered visually
 * distinct but **NEVER hidden** at the consumer.
 *
 * Confidential: this component does render employee `name` to the
 * DOM (the operator UI surfaces it) but the api module logs
 * nothing — no name / contact / employeeNumber appears in any
 * structured log. The test asserts the log-spy invariant.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface EmployeeListProps {
  initial?: EmployeeListResponse;
}

export function EmployeeList({ initial }: EmployeeListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useEmployees(query, initial);
  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-employees-heading">
      <h2
        id="erp-employees-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        직원 (employees)
      </h2>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-employees-empty"
        >
          표시할 직원이 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 w-full border-collapse border border-border text-sm"
            data-testid="erp-employees-table"
          >
            <caption className="sr-only">직원 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">사번</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">고용상태</th>
                <th scope="col" className="p-2">부서</th>
                <th scope="col" className="p-2">유효기간</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((e, i) => {
                const retired = isRetired(e.effectivePeriod);
                const isSeparated = e.employmentStatus === 'SEPARATED';
                const visuallyDim = retired || isSeparated;
                return (
                  <tr
                    key={e.id}
                    data-testid={`erp-employee-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    data-separated={isSeparated ? 'true' : 'false'}
                    className={`border-b border-border ${visuallyDim ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{e.employeeNumber}</td>
                    <td className="p-2">{e.name}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-employee-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(e.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-employee-employment-${i}`}
                        className={`rounded px-1.5 py-0.5 text-xs ${isSeparated ? 'bg-amber-100 text-amber-900 dark:bg-amber-950/60 dark:text-amber-100' : 'bg-muted text-muted-foreground'}`}
                      >
                        {labelForUnknownEnum(
                          e.employmentStatus,
                          KNOWN_EMPLOYMENT_STATUSES,
                        )}
                      </span>
                    </td>
                    <td className="p-2">{e.departmentId ?? '—'}</td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={e.effectivePeriod} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="직원 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({
                  ...s,
                  page: Math.max(0, (s.page ?? 0) - 1),
                }))
              }
              data-testid="erp-employees-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-employees-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="erp-employees-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
