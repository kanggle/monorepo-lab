'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  type JobGradeListResponse,
  type ErpListQueryParams,
} from '../api/types';
import { useJobGrades } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Job-grades list (TASK-PC-FE-010 / § 2.4.8) — paginated table.
 * Producer orders by `displayOrder` asc; the consumer respects the
 * producer order (no client-side re-sort that would override).
 *
 * E2 honesty: retired rows rendered visually distinct but NEVER
 * hidden.
 */
export interface JobGradeListProps {
  initial?: JobGradeListResponse;
}

export function JobGradeList({ initial }: JobGradeListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useJobGrades(query, initial);
  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-jobgrades-heading">
      <h2
        id="erp-jobgrades-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        직급 (job-grades)
      </h2>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-jobgrades-empty"
        >
          표시할 직급이 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 w-full border-collapse text-sm"
            data-testid="erp-jobgrades-table"
          >
            <caption className="sr-only">직급 목록 (displayOrder 오름차순)</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">정렬 (displayOrder)</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">유효기간</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((g, i) => {
                const retired = isRetired(g.effectivePeriod);
                return (
                  <tr
                    key={g.id}
                    data-testid={`erp-jobgrade-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{g.code}</td>
                    <td className="p-2">{g.name}</td>
                    <td className="p-2">{g.displayOrder ?? '—'}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-jobgrade-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(g.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={g.effectivePeriod} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="직급 페이지 이동"
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
              data-testid="erp-jobgrades-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-jobgrades-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="erp-jobgrades-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
