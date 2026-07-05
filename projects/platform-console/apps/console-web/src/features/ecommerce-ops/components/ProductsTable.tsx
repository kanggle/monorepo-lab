'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { productStatusTone, type ProductList } from '../api/types';

interface ProductsTableProps {
  rows: ProductList['content'];
  onDelete: (product: { id: string; name: string }) => void;
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Product list table + pagination (TASK-PC-FE-199 — extracted from
 * {@link ProductsScreen}, presentational only). Per-row actions: 상세(drill) /
 * 수정(edit) / 삭제(delegates to the container's confirm-gate via `onDelete`).
 * Query/filter/delete state stays owned by `ProductsScreen`; all `data-testid`s
 * are unchanged.
 */
export function ProductsTable({ rows, onDelete, pagination }: ProductsTableProps) {
  return (
    <>
      <table className="mb-3 data-table" data-testid="product-table">
        <caption className="sr-only">상품 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">상품명</th>
            <th scope="col" className="p-2">상태</th>
            <th scope="col" className="p-2">가격</th>
            <th scope="col" className="p-2">작업</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((p, i) => (
            <tr key={p.id} data-testid={`product-row-${i}`} className="border-b border-border">
              <td className="p-2">{p.name}</td>
              <td className="p-2" data-testid={`product-row-status-${i}`}>
                <StatusBadge tone={productStatusTone(p.status)}>
                  {p.status}
                </StatusBadge>
              </td>
              <td className="p-2">{p.price.toLocaleString('ko-KR')}원</td>
              <td className="p-2">
                <div className="flex gap-2">
                  <Link href={`/ecommerce/products/${p.id}`}>
                    <Button variant="secondary" size="sm" data-testid={`product-detail-${i}`}>
                      상세
                    </Button>
                  </Link>
                  <Link href={`/ecommerce/products/${p.id}/edit`}>
                    <Button variant="secondary" size="sm" data-testid={`product-edit-${i}`}>
                      수정
                    </Button>
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onDelete({ id: p.id, name: p.name })}
                    data-testid={`product-delete-${i}`}
                  >
                    삭제
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav className="flex items-center justify-between" aria-label="상품 페이지 이동">
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="product-prev"
        >
          이전
        </Button>
        <span className="text-sm text-muted-foreground" data-testid="product-pageinfo">
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="product-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
