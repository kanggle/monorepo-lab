'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useProduct, useDeleteProduct } from '../hooks/use-ecommerce-products';
import type { ProductDetail as ProductDetailType, Variant } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { VariantEditor } from './VariantEditor';
import { StockAdjustDialog } from './StockAdjustDialog';

/**
 * ecommerce product detail section (TASK-PC-FE-081 — § 2.4.10 #2). The console
 * equivalent of the `admin-dashboard` product detail screen: product header +
 * inline variant CRUD (VariantEditor, #6/#7/#8) + per-variant stock adjust
 * (StockAdjustDialog, #9) + edit/delete entry points.
 *
 * Server-seeded detail is passed in; the client query keeps it fresh after a
 * variant/stock mutation invalidation.
 */
export interface ProductDetailProps {
  product: ProductDetailType;
}

export function ProductDetail({ product }: ProductDetailProps) {
  const router = useRouter();
  const detailQ = useProduct(product.id, product);
  const data = detailQ.data ?? product;

  const del = useDeleteProduct();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [delError, setDelError] = useState<string | null>(null);

  const [stockVariant, setStockVariant] = useState<Variant | null>(null);

  function doDelete() {
    setDelError(null);
    del.mutate(data.id, {
      onSuccess: () => {
        setConfirmDelete(false);
        router.push('/ecommerce/products');
        router.refresh();
      },
      onError: (e) => {
        const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
        setDelError(messageForCode(code, '상품을 삭제하지 못했습니다.'));
      },
    });
  }

  return (
    <section aria-labelledby="product-detail-heading" data-testid="product-detail">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 id="product-detail-heading" className="text-2xl font-semibold">
          {data.name}
        </h1>
        <div className="flex gap-2">
          <Link href={`/ecommerce/products/${data.id}/edit`}>
            <Button variant="secondary" data-testid="product-detail-edit">
              수정
            </Button>
          </Link>
          <Button
            variant="secondary"
            onClick={() => {
              setDelError(null);
              setConfirmDelete(true);
            }}
            data-testid="product-detail-delete"
          >
            삭제
          </Button>
          <Link href="/ecommerce/products">
            <Button variant="ghost" data-testid="product-detail-back">
              목록
            </Button>
          </Link>
        </div>
      </div>

      <dl className="mb-8 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="product-detail-status">{data.status}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">가격</dt>
          <dd data-testid="product-detail-price">{data.price.toLocaleString('ko-KR')}원</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상품 ID</dt>
          <dd className="break-all text-xs">{data.id}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">셀러 ID</dt>
          <dd className="break-all text-xs">{data.sellerId ?? '—'}</dd>
        </div>
        {data.description && (
          <div className="col-span-2 sm:col-span-4">
            <dt className="text-muted-foreground">설명</dt>
            <dd>{data.description}</dd>
          </div>
        )}
      </dl>

      {/* Inline variant CRUD (#6/#7/#8). */}
      <div className="mb-8">
        <VariantEditor productId={data.id} variants={data.variants} />
      </div>

      {/* Per-variant stock adjust (#9). */}
      <div className="mb-8" data-testid="stock-adjust-section">
        <h3 className="mb-2 text-base font-medium text-foreground">재고 조정</h3>
        {data.variants.length === 0 ? (
          <p className="text-sm text-muted-foreground">조정할 옵션이 없습니다.</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {data.variants.map((v) => (
              <li key={v.id}>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setStockVariant(v)}
                  data-testid={`stock-adjust-open-${v.id}`}
                >
                  {v.optionName} 재고 조정 (현재 {v.stock})
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <StockAdjustDialog
        open={stockVariant !== null}
        productId={data.id}
        variant={stockVariant}
        onClose={() => setStockVariant(null)}
        onAdjusted={() => setStockVariant(null)}
      />

      <ConfirmDialog
        open={confirmDelete}
        title="상품을 삭제할까요?"
        description={`"${data.name}" 상품을 삭제합니다. 이 작업은 되돌릴 수 없습니다.`}
        confirmLabel="삭제"
        tone="destructive"
        pending={del.isPending}
        errorMessage={delError}
        onConfirm={doDelete}
        onCancel={() => {
          setConfirmDelete(false);
          setDelError(null);
        }}
      />
    </section>
  );
}
