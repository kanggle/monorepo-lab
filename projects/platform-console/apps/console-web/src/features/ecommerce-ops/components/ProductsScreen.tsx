'use client';

import { useId, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useProducts,
  useDeleteProduct,
} from '../hooks/use-ecommerce-products';
import {
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_STATUS_VALUES,
  type ProductList,
  type ProductListParams,
} from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { ProductsTable } from './ProductsTable';

/**
 * ecommerce product operations list section (TASK-PC-FE-081 — § 2.4.10 #1).
 * The console equivalent of the `admin-dashboard` product list screen.
 *
 * Server-rendered initial page is passed in; client re-query handles
 * status-filter / pagination. Per-row actions: 상세(drill) / 수정(edit) /
 * 삭제(confirm-gated delete, #5). Register entry-point links to `new`.
 *
 * Resilience (§ 2.5): 401 handled by the server route (whole-session
 * re-login); 403/404/409/422 → inline actionable; 503/timeout → this section
 * degrades only.
 */

export interface ProductsScreenProps {
  products: ProductList;
}

const STATUS_FILTER_OPTIONS = ['', ...PRODUCT_STATUS_VALUES] as const;

export function ProductsScreen({ products }: ProductsScreenProps) {
  const router = useRouter();
  const statusFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [query, setQuery] = useState<ProductListParams>({
    page: 0,
    size: products.size || PRODUCT_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0 && !query.status && !query.categoryId;
  const listQ = useProducts(query, seeded ? products : undefined);
  // Only the seeded (page 0, no filter) query may fall back to the server-rendered
  // `products` seed. For a filtered/paginated query, falling back to the seed would
  // flash the full unfiltered list while the new query is still in flight — instead
  // we render a loading placeholder until the real result lands.
  const data = seeded ? listQ.data ?? products : listQ.data;
  const loading = data === undefined;

  const apiError = listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  // --- delete --------------------------------------------------------------
  const del = useDeleteProduct();
  const [toDelete, setToDelete] = useState<{ id: string; name: string } | null>(
    null,
  );
  const [delError, setDelError] = useState<string | null>(null);

  function confirmDelete() {
    if (!toDelete) return;
    setDelError(null);
    del.mutate(toDelete.id, {
      onSuccess: () => {
        setToDelete(null);
        router.refresh();
      },
      onError: (e) => {
        const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
        setDelError(messageForCode(code, '상품을 삭제하지 못했습니다.'));
      },
    });
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      page: 0,
      size: products.size || PRODUCT_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  return (
    <section aria-labelledby="ecommerce-products-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1 id="ecommerce-products-heading" className="text-2xl font-semibold">
          E-Commerce 상품
        </h1>
        <Link href="/ecommerce/products/new" data-testid="product-new-link">
          <Button>상품 등록</Button>
        </Link>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        상품 목록 · 상세(옵션 + 재고) · 등록 / 수정 / 삭제.
      </p>

      <form
        onSubmit={submitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="상품 필터"
      >
        <div>
          <label htmlFor={statusFid} className="block text-sm font-medium text-foreground">
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="product-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="product-filter-submit">
          조회
        </Button>
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="product-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="product-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 상품 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p className="text-sm text-muted-foreground" data-testid="product-loading">
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="product-empty">
          표시할 상품이 없습니다.
        </p>
      ) : (
        <ProductsTable
          rows={rows}
          onDelete={(product) => {
            setDelError(null);
            setToDelete(product);
          }}
          pagination={{
            prevDisabled: (query.page ?? 0) <= 0,
            nextDisabled: (data?.page ?? 0) + 1 >= totalPages,
            pageInfo: `${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`,
            onPrev: () =>
              setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) })),
            onNext: () => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
          }}
        />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="상품을 삭제할까요?"
        description={
          toDelete
            ? `"${toDelete.name}" 상품을 삭제합니다. 이 작업은 되돌릴 수 없습니다.`
            : ''
        }
        confirmLabel="삭제"
        tone="destructive"
        pending={del.isPending}
        errorMessage={delError}
        onConfirm={confirmDelete}
        onCancel={() => {
          setToDelete(null);
          setDelError(null);
        }}
      />
    </section>
  );
}
