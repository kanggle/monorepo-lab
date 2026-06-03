'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  isRetired,
  KNOWN_MASTER_STATUSES,
  KNOWN_PARTNER_TYPES,
  labelForUnknownEnum,
  type BusinessPartnerListResponse,
  type ErpListQueryParams,
} from '../api/types';
import { useBusinessPartners } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Business-partners list (TASK-PC-FE-010 / § 2.4.8) — paginated
 * table. `paymentTerms` is intentionally NOT rendered in the
 * list view (it is a detail-only confidential financial element);
 * the detail view renders a redacted summary the operator can
 * inspect.
 *
 * E2 honesty: retired rows rendered visually distinct but NEVER
 * hidden.
 *
 * Confidential / audit-heavy: nothing about these records is
 * logged (the api module only logs status + sanitised path).
 */
export interface BusinessPartnerListProps {
  initial?: BusinessPartnerListResponse;
}

export function BusinessPartnerList({ initial }: BusinessPartnerListProps) {
  const [query, setQuery] = useState<ErpListQueryParams>({
    page: 0,
    size: initial?.meta.size ?? 20,
  });
  const q = useBusinessPartners(query, initial);
  const dataResp = q.data ?? initial ?? { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
  const rows = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="erp-businesspartners-heading">
      <h2
        id="erp-businesspartners-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        거래처 (business-partners)
      </h2>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="erp-businesspartners-empty"
        >
          표시할 거래처가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="erp-businesspartners-table"
          >
            <caption className="sr-only">거래처 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">코드</th>
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">유형</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">유효기간</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((p, i) => {
                const retired = isRetired(p.effectivePeriod);
                return (
                  <tr
                    key={p.id}
                    data-testid={`erp-businesspartner-row-${i}`}
                    data-retired={retired ? 'true' : 'false'}
                    className={`border-b border-border ${retired ? 'opacity-70' : ''}`}
                  >
                    <td className="p-2">{p.code}</td>
                    <td className="p-2">{p.name}</td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-businesspartner-type-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(p.partnerType, KNOWN_PARTNER_TYPES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <span
                        data-testid={`erp-businesspartner-status-${i}`}
                        className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      >
                        {labelForUnknownEnum(p.status, KNOWN_MASTER_STATUSES)}
                      </span>
                    </td>
                    <td className="p-2">
                      <EffectivePeriodBadge period={p.effectivePeriod} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="거래처 페이지 이동"
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
              data-testid="erp-businesspartners-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="erp-businesspartners-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="erp-businesspartners-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
