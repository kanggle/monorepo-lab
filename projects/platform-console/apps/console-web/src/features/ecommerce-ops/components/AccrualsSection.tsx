'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useAccruals } from '../hooks/use-ecommerce-settlements';
import {
  SETTLEMENT_DEFAULT_PAGE_SIZE,
  type AccrualsResponse,
  type AccrualsListParams,
} from '../api/settlement-types';
import { AccrualsTable } from './AccrualsTable';

/**
 * Settlement accrual-lines section (TASK-PC-FE-221 Phase A). Owns the
 * sellerId/orderId filter form + pagination + query; the server-rendered page-0
 * (unfiltered) result seeds the table. Read-only append-only ledger — no manual
 * accrual write (REST has none; task Edge).
 *
 * Resilience (§ 2.5): 403 → inline forbidden; 503/timeout → this section
 * degrades only.
 */
export interface AccrualsSectionProps {
  initialAccruals: AccrualsResponse;
}

export function AccrualsSection({ initialAccruals }: AccrualsSectionProps) {
  const sellerFieldId = useId();
  const orderFieldId = useId();
  const [draftSellerId, setDraftSellerId] = useState('');
  const [draftOrderId, setDraftOrderId] = useState('');
  const [query, setQuery] = useState<AccrualsListParams>({
    page: 0,
    size: initialAccruals.size || SETTLEMENT_DEFAULT_PAGE_SIZE,
  });

  const seeded =
    (query.page ?? 0) === 0 && !query.sellerId && !query.orderId;
  const listQ = useAccruals(query, seeded ? initialAccruals : undefined);
  const data = seeded ? listQ.data ?? initialAccruals : listQ.data;
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

  function applyFilters(e: React.FormEvent) {
    e.preventDefault();
    setQuery((q) => ({
      ...q,
      page: 0,
      sellerId: draftSellerId.trim() || undefined,
      orderId: draftOrderId.trim() || undefined,
    }));
  }

  function resetFilters() {
    setDraftSellerId('');
    setDraftOrderId('');
    setQuery((q) => ({ ...q, page: 0, sellerId: undefined, orderId: undefined }));
  }

  return (
    <section aria-labelledby="settlements-accruals-heading" className="mb-10">
      <h2
        id="settlements-accruals-heading"
        className="mb-1 text-lg font-semibold"
      >
        정산 라인 (append-only ledger)
      </h2>
      <p className="mb-3 text-sm text-muted-foreground">
        셀러별 라인 단위 수수료 적립 · 환불 시 음수 REVERSAL. 셀러/주문으로
        필터링할 수 있습니다. (조회 전용 — 수동 적립 없음)
      </p>

      <form
        onSubmit={applyFilters}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="정산 라인 필터"
      >
        <div className="w-56">
          <label
            htmlFor={sellerFieldId}
            className="block text-sm font-medium text-foreground"
          >
            셀러 ID
          </label>
          <input
            id={sellerFieldId}
            type="text"
            value={draftSellerId}
            onChange={(e) => setDraftSellerId(e.target.value)}
            data-testid="settlements-accruals-seller-input"
            autoComplete="off"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="w-56">
          <label
            htmlFor={orderFieldId}
            className="block text-sm font-medium text-foreground"
          >
            주문 ID
          </label>
          <input
            id={orderFieldId}
            type="text"
            value={draftOrderId}
            onChange={(e) => setDraftOrderId(e.target.value)}
            data-testid="settlements-accruals-order-input"
            autoComplete="off"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button type="submit" data-testid="settlements-accruals-filter-submit">
          조회
        </Button>
        {(query.sellerId || query.orderId) && (
          <Button
            type="button"
            variant="secondary"
            onClick={resetFilters}
            data-testid="settlements-accruals-filter-reset"
          >
            초기화
          </Button>
        )}
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="settlements-accruals-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="settlements-accruals-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          정산 라인 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-accruals-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="settlements-accruals-empty"
        >
          표시할 정산 라인이 없습니다.
        </p>
      ) : (
        <AccrualsTable
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
