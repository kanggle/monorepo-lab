'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  type DepartmentListResponse,
  type ErpListQueryParams,
} from '../api/types';
import { useDepartments } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Departments list (TASK-PC-FE-010 / § 2.4.8) — paginated table.
 *
 * E2 honesty: retired rows (`effectiveTo` in the past) are rendered
 * visually distinct (a `<EffectivePeriodBadge>` + a row-level
 * `opacity-70` class) but **NEVER hidden / filtered** at the
 * consumer. The test pins this.
 *
 * E3: the asOf URL param threads through via `useDepartments()` →
 * the proxy → producer; the rendered state matches the
 * asOf-instant response.
 *
 * Tolerant: unknown / future `status` enums render via
 * `labelForUnknownEnum` with a generic label, never a throw.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface DepartmentListProps {
  initial?: DepartmentListResponse;
}

export function DepartmentList({ initial }: DepartmentListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useDepartments(query, initial);
  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-departments-heading">
      <h2
        id="erp-departments-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        부서 (departments)
      </h2>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-departments-empty"
        >
          표시할 부서가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 w-full border-collapse border border-border text-sm"
            data-testid="erp-departments-table"
          >
            <caption className="sr-only">부서 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">상위 부서</th>
                <th scope="col" className="p-2">유효기간</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((d, i) => {
                const retired = isRetired(d.effectivePeriod);
                return (
                  <tr
                    key={d.id}
                    data-testid={`erp-department-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{d.code}</td>
                    <td className="p-2">{d.name}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-department-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(d.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">{d.parentId ?? '—'}</td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={d.effectivePeriod} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="부서 페이지 이동"
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
              data-testid="erp-departments-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-departments-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="erp-departments-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
