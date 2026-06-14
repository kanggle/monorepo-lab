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
  const data = listQ.data ?? sellers;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  const rows = data.content;
  const totalPages = Math.max(
    1,
    Math.ceil(data.totalElements / (data.size || 20)),
  );

  return (
    <section aria-labelledby="ecommerce-sellers-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1
          id="ecommerce-sellers-heading"
          className="text-2xl font-semibold"
        >
          E-Commerce 셀러 운영
        </h1>
        <Link
          href="/ecommerce/sellers/new"
          data-testid="seller-new-link"
        >
          <Button>셀러 등록</Button>
        </Link>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        셀러 목록 · 상세 · 등록. ecommerce 마켓플레이스 셀러를 콘솔에서 관리합니다.
        (상태: ACTIVE만 · 수정/삭제 없음 — ADR-030 v1)
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
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="seller-empty"
        >
          표시할 셀러가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="seller-table"
          >
            <caption className="sr-only">셀러 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  셀러 ID
                </th>
                <th scope="col" className="p-2">
                  셀러 이름
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  등록일
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s, i) => (
                <tr
                  key={s.sellerId}
                  data-testid={`seller-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2 font-mono text-xs">{s.sellerId}</td>
                  <td className="p-2">{s.displayName}</td>
                  <td className="p-2" data-testid={`seller-row-status-${i}`}>
                    <span className="inline-block rounded bg-green-100 px-2 py-0.5 text-xs font-medium text-green-800">
                      {s.status}
                    </span>
                  </td>
                  <td className="p-2 text-sm text-muted-foreground">
                    {new Date(s.createdAt).toLocaleDateString('ko-KR')}
                  </td>
                  <td className="p-2">
                    <Link
                      href={`/ecommerce/sellers/${s.sellerId}`}
                    >
                      <Button
                        variant="secondary"
                        size="sm"
                        data-testid={`seller-detail-${i}`}
                      >
                        상세
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="flex items-center justify-between"
            aria-label="셀러 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="seller-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="seller-pageinfo"
            >
              {`${data.page + 1} / ${totalPages} 페이지 · 총 ${data.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= totalPages}
              onClick={() =>
                setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="seller-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
