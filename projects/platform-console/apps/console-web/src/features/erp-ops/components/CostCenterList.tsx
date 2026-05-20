'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  labelForUnknownEnum,
  type CostCenterListResponse,
  type ErpListQueryParams,
} from '../api/types';
import { useCostCenters } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Cost-centers list (TASK-PC-FE-010 / § 2.4.8) — paginated table.
 * Cost-centers reference departments; this list surfaces the
 * raw `departmentId` (the detail view resolves it to a name +
 * retired-reference badge if the ref is broken).
 *
 * E2 honesty: retired rows rendered visually distinct but NEVER
 * hidden.
 */
export interface CostCenterListProps {
  initial?: CostCenterListResponse;
}

export function CostCenterList({ initial }: CostCenterListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useCostCenters(query, initial);
  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-costcenters-heading">
      <h2
        id="erp-costcenters-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        비용센터 (cost-centers)
      </h2>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-costcenters-empty"
        >
          표시할 비용센터가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 w-full border-collapse text-sm"
            data-testid="erp-costcenters-table"
          >
            <caption className="sr-only">비용센터 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">소속 부서</th>
                <th scope="col" className="p-2">유효기간</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((c, i) => {
                const retired = isRetired(c.effectivePeriod);
                return (
                  <tr
                    key={c.id}
                    data-testid={`erp-costcenter-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{c.code}</td>
                    <td className="p-2">{c.name}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-costcenter-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(c.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">{c.departmentId ?? '—'}</td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={c.effectivePeriod} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="비용센터 페이지 이동"
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
              data-testid="erp-costcenters-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-costcenters-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="erp-costcenters-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
