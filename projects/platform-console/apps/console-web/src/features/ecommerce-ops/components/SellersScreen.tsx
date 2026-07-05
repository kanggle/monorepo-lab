'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useSellers } from '../hooks/use-ecommerce-sellers';
import {
  SELLER_DEFAULT_PAGE_SIZE,
  type SellerList,
  type SellerListParams,
} from '../api/seller-types';
import { SellersTable } from './SellersTable';

/**
 * ecommerce seller operations list section (TASK-PC-FE-090 — ADR-MONO-031
 * § 2.4.10 7th area).
 *
 * Server-rendered initial page is passed in; client re-query handles
 * pagination. "셀러 등록" links to /new. Per-row action: 상세(drill).
 *
 * status=ACTIVE only (v1). READ display only — no delete/update/deactivate.
 *
 * Resilience (§ 2.5): 403 → inline; 503/timeout → this section degrades only.
 *
 * TASK-PC-FE-200: the list table + pagination are extracted into
 * {@link SellersTable} (presentational); this container keeps ALL state —
 * pagination query, seed fallback, and list-state branching.
 */

export interface SellersScreenProps {
  sellers: SellerList;
}

export function SellersScreen({ sellers }: SellersScreenProps) {
  const [query, setQuery] = useState<SellerListParams>({
    page: 0,
    size: sellers.size || SELLER_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0;
  const listQ = useSellers(query, seeded ? sellers : undefined);
  // Only the seeded (page 0) query may fall back to the server-rendered `sellers`
  // seed. For a paginated query, falling back to the seed would flash the first
  // page while the next page is still in flight — instead we render a loading
  // placeholder until the real result lands.
  const data = seeded ? listQ.data ?? sellers : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  return (
    <section aria-labelledby="ecommerce-sellers-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1
          id="ecommerce-sellers-heading"
          className="text-2xl font-semibold"
        >
          E-Commerce 셀러
        </h1>
        <Link
          href="/ecommerce/sellers/new"
          data-testid="seller-new-link"
        >
          <Button>셀러 등록</Button>
        </Link>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        셀러 목록 · 상세 · 등록. 라이프사이클 관리(프로비저닝 · 정지 · 폐점)는 상세
        화면에서. (수정/삭제 대신 상태 전이 — ADR-042)
      </p>

      {forbidden ? (
        <div
          role="status"
          data-testid="seller-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="seller-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 셀러 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="seller-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="seller-empty"
        >
          표시할 셀러가 없습니다.
        </p>
      ) : (
        <SellersTable
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
            onNext: () =>
              setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
          }}
        />
      )}
    </section>
  );
}
