'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { usePeriods } from '../hooks/use-ecommerce-settlements';
import {
  SETTLEMENT_DEFAULT_PAGE_SIZE,
  type PeriodsResponse,
  type PeriodsListParams,
} from '../api/settlement-types';
import { SettlementPeriodsTable } from './SettlementPeriodsTable';

/**
 * Settlement periods section (TASK-PC-FE-221 Phase A). Owns pagination + query;
 * the server-rendered page-0 result seeds the table. Each row drills into the
 * period's payouts detail. Read-only — period open/close is Phase B.
 *
 * Resilience (§ 2.5): 403 → inline forbidden; 503/timeout → this section
 * degrades only.
 */
export interface SettlementPeriodsSectionProps {
  initialPeriods: PeriodsResponse;
}

export function SettlementPeriodsSection({
  initialPeriods,
}: SettlementPeriodsSectionProps) {
  const [query, setQuery] = useState<PeriodsListParams>({
    page: 0,
    size: initialPeriods.size || SETTLEMENT_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0;
  const listQ = usePeriods(query, seeded ? initialPeriods : undefined);
  const data = seeded ? listQ.data ?? initialPeriods : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  const rows = data?.items ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  return (
    <section aria-labelledby="settlements-periods-heading" className="mb-10">
      <h2
        id="settlements-periods-heading"
        className="mb-1 text-lg font-semibold"
      >
        정산 기간
      </h2>
      <p className="mb-3 text-sm text-muted-foreground">
        정산 기간 목록. OPEN 기간은 적립 중이고, CLOSED 기간은 적립을 지급
        (payout)으로 접어 마감된 상태입니다. 행의 &ldquo;지급 내역&rdquo;에서 기간별 셀러
        지급을 확인할 수 있습니다. (기간 개시·마감은 콘솔 범위 밖 — 조회 전용)
      </p>

      {forbidden ? (
        <div
          role="status"
          data-testid="settlements-periods-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="settlements-periods-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          정산 기간 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-periods-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-periods-empty"
        >
          표시할 정산 기간이 없습니다.
        </p>
      ) : (
        <SettlementPeriodsTable
          rows={rows}
          pagination={{
            prevDisabled: (query.page ?? 0) <= 0,
            nextDisabled: (data?.page ?? 0) + 1 >= totalPages,
            pageInfo: `${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`,
            onPrev: () =>
              setQuery((q) => ({
                ...q,
                page: Math.max(0, (q.page ?? 0) - 1),
              })),
            onNext: () => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
          }}
        />
      )}
    </section>
  );
}
