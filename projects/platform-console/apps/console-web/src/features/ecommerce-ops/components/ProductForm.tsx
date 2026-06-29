'use client';

import { Button } from '@/shared/ui/Button';
import { PRODUCT_STATUS_VALUES, type ProductDetail } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { ProductVariantsFieldset } from './ProductVariantsFieldset';
import { useProductForm } from './use-product-form';

/**
 * Register / update product form (TASK-PC-FE-081 — § 2.4.10 #3/#4). Used by
 * both the `new` and `[id]/edit` pages.
 *
 *   - REGISTER (no `existing`): name + price + ≥1 variant are required
 *     (producer RegisterProductRequest). On success → `/ecommerce/products`.
 *   - UPDATE (`existing` set): partial PATCH (UpdateProductRequest, all
 *     optional) — name/description/price/status/thumbnailUrl. Variants are
 *     managed inline on the detail page (VariantEditor), NOT here. On success
 *     → `/ecommerce/products/{id}`.
 *
 * Confirm-gated submit; 409/422 surfaced inline (NO `Idempotency-Key`).
 *
 * TASK-PC-FE-139: form state/validation/submit live in {@link useProductForm};
 * the register-mode variant editor in {@link ProductVariantsFieldset}. This
 * container only wires the hook to the markup (behavior-preserving split).
 */

export interface ProductFormProps {
  /** When set, the form is in UPDATE mode for this product. */
  existing?: ProductDetail;
}

const inputCls =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
const labelCls = 'block text-sm font-medium text-foreground';

export function ProductForm({ existing }: ProductFormProps) {
  const {
    router,
    isEdit,
    ids: { nameId, descId, priceId, statusId, thumbId },
    fields: {
      name,
      setName,
      description,
      setDescription,
      price,
      setPrice,
      status,
      setStatus,
      thumbnailUrl,
      setThumbnailUrl,
    },
    variants,
    setVariant,
    addVariantRow,
    removeVariantRow,
    confirmOpen,
    error,
    conflict,
    pending,
    formValid,
    onSubmit,
    confirmSubmit,
    cancelConfirm,
  } = useProductForm(existing);

  return (
    <form onSubmit={onSubmit} className="max-w-2xl space-y-5" data-testid="product-form">
      <div>
        <label htmlFor={nameId} className={labelCls}>
          상품명 {!isEdit && <span className="text-destructive">*</span>}
        </label>
        <input
          id={nameId}
          value={name}
          onChange={(e) => setName(e.target.value)}
          className={inputCls}
          data-testid="product-form-name"
        />
      </div>

      <div>
        <label htmlFor={descId} className={labelCls}>
          설명
        </label>
        <textarea
          id={descId}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          className={inputCls}
          data-testid="product-form-description"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={priceId} className={labelCls}>
            가격(원) {!isEdit && <span className="text-destructive">*</span>}
          </label>
          <input
            id={priceId}
            inputMode="numeric"
            value={price}
            onChange={(e) => setPrice(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
            data-testid="product-form-price"
          />
        </div>
        <div>
          <label htmlFor={thumbId} className={labelCls}>
            썸네일 URL
          </label>
          <input
            id={thumbId}
            value={thumbnailUrl}
            onChange={(e) => setThumbnailUrl(e.target.value)}
            className={inputCls}
            data-testid="product-form-thumbnail"
          />
        </div>
      </div>

      {isEdit && (
        <div>
          <label htmlFor={statusId} className={labelCls}>
            상태
          </label>
          <select
            id={statusId}
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className={inputCls}
            data-testid="product-form-status"
          >
            {PRODUCT_STATUS_VALUES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
      )}

      {!isEdit && (
        <ProductVariantsFieldset
          variants={variants}
          setVariant={setVariant}
          addVariantRow={addVariantRow}
          removeVariantRow={removeVariantRow}
          inputCls={inputCls}
        />
      )}

      {error && !confirmOpen && (
        <p role="alert" className="text-sm text-destructive" data-testid="product-form-error">
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <Button type="submit" disabled={!formValid || pending} data-testid="product-form-submit">
          {isEdit ? '변경 저장' : '상품 등록'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
          data-testid="product-form-cancel"
        >
          취소
        </Button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title={isEdit ? '상품 변경을 저장할까요?' : '상품을 등록할까요?'}
        description={
          isEdit
            ? '입력한 내용으로 상품을 수정합니다.'
            : '입력한 내용으로 새 상품을 등록합니다. 옵션과 재고가 함께 생성됩니다.'
        }
        confirmLabel={isEdit ? '저장' : '등록'}
        pending={pending}
        errorMessage={error}
        conflict={conflict}
        onConfirm={confirmSubmit}
        onCancel={cancelConfirm}
      />
    </form>
  );
}
