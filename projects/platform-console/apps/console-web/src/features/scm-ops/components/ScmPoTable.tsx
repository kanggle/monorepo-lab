'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import { messageForCode } from '@/shared/api/errors';
import type { PoPage, PurchaseOrder, PoQueryParams } from '../api/types';
import { KNOWN_PO_STATUSES, type PoFilterState } from './scm-ops-helpers';

/**
 * Procurement PO list region of the scm ops screen (TASK-PC-FE-144 split) —
 * the status/supplier filter form + forbidden / rate-limited / degraded /
 * empty notices + the PO table + pagination nav + the per-row read-only
 * detail affordance. Pure presentation: all state + handlers live in the
 * `ScmOpsScreen` container and arrive via props. STRICTLY READ-ONLY.
 */
export interface ScmPoTableProps {
  statusFid: string;
  supplierFid: string;
  filters: PoFilterState;
  onFiltersChange: Dispatch<SetStateAction<PoFilterState>>;
  onSubmit: (e: FormEvent) => void;
  forbidden: boolean;
  rateLimited: boolean;
  degraded: boolean;
  data: PoPage;
  query: PoQueryParams;
  onDetail: (po: PurchaseOrder) => void;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function ScmPoTable({
  statusFid,
  supplierFid,
  filters,
  onFiltersChange,
  onSubmit,
  forbidden,
  rateLimited,
  degraded,
  data,
  query,
  onDetail,
  onPrevPage,
  onNextPage,
}: ScmPoTableProps) {
  const poRows = data.content;
  const poTotalPages = Math.max(1, data.totalPages ?? 1);

  return (
    <>
      {/* ── Procurement: PO list (read-only) ──────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        조달 — 발주(PO) 목록
      </h2>
      <form
        onSubmit={onSubmit}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="발주 목록 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={filters.status}
            onChange={(e) =>
              onFiltersChange((f) => ({ ...f, status: e.target.value }))
            }
            data-testid="scm-po-filter-status"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체</option>
            {KNOWN_PO_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label
            htmlFor={supplierFid}
            className="block text-sm font-medium text-foreground"
          >
            공급사 ID
          </label>
          <input
            id={supplierFid}
            type="text"
            value={filters.supplierId}
            onChange={(e) =>
              onFiltersChange((f) => ({ ...f, supplierId: e.target.value }))
            }
            data-testid="scm-po-filter-supplier"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="scm-po-filter-submit">
            조회
          </Button>
        </div>
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="scm-po-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : rateLimited ? (
        <div
          role="status"
          data-testid="scm-po-ratelimited"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('RATE_LIMIT_EXCEEDED')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="scm-po-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          scm 발주 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : poRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="scm-po-empty"
        >
          표시할 발주가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="scm-po-table"
          >
            <caption className="sr-only">발주 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  PO 번호
                </th>
                <th scope="col" className="p-2">
                  공급사
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  총액
                </th>
                <th scope="col" className="p-2">
                  생성 (UTC)
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {poRows.map((p, i) => (
                <tr
                  key={p.id}
                  data-testid={`scm-po-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{p.poNumber ?? p.id}</td>
                  <td className="p-2">{p.supplierId ?? '—'}</td>
                  <td className="p-2">{p.status ?? '—'}</td>
                  <td className="p-2">
                    {p.totalAmount ?? '—'} {p.currency ?? ''}
                  </td>
                  <td className="p-2">{p.createdAt ?? '—'}</td>
                  <td className="p-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onDetail(p)}
                      data-testid={`scm-po-detail-${i}`}
                    >
                      상세
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="발주 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={onPrevPage}
              data-testid="scm-po-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="scm-po-pageinfo"
            >
              {`${data.page + 1} / ${poTotalPages} 페이지 · 총 ${data.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={data.page + 1 >= poTotalPages}
              onClick={onNextPage}
              data-testid="scm-po-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </>
  );
}
