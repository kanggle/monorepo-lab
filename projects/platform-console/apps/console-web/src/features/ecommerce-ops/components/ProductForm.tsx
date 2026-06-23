'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useRegisterProduct,
  useUpdateProduct,
} from '../hooks/use-ecommerce-products';
import {
  PRODUCT_STATUS_VALUES,
  type ProductDetail,
  type RegisterProductBody,
  type UpdateProductBody,
} from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';

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
 */

interface VariantDraft {
  optionName: string;
  stock: string;
  additionalPrice: string;
}

export interface ProductFormProps {
  /** When set, the form is in UPDATE mode for this product. */
  existing?: ProductDetail;
}

export function ProductForm({ existing }: ProductFormProps) {
  const router = useRouter();
  const isEdit = existing !== undefined;
  const nameId = useId();
  const descId = useId();
  const priceId = useId();
  const statusId = useId();
  const thumbId = useId();

  const [name, setName] = useState(existing?.name ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [price, setPrice] = useState(
    existing ? String(existing.price) : '',
  );
  const [status, setStatus] = useState(existing?.status ?? 'ON_SALE');
  const [thumbnailUrl, setThumbnailUrl] = useState(
    existing?.thumbnailUrl ?? '',
  );
  const [variants, setVariants] = useState<VariantDraft[]>([
    { optionName: '', stock: '', additionalPrice: '' },
  ]);

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  const register = useRegisterProduct();
  const update = useUpdateProduct();
  const pending = register.isPending || update.isPending;

  const priceNum = Number(price);
  const priceValid = price !== '' && Number.isInteger(priceNum) && priceNum >= (isEdit ? 0 : 1);
  const variantsValid =
    isEdit ||
    variants.length > 0 &&
      variants.every(
        (v) =>
          v.optionName.trim() !== '' &&
          Number.isInteger(Number(v.stock)) &&
          Number(v.stock) >= 0 &&
          Number.isInteger(Number(v.additionalPrice)) &&
          Number(v.additionalPrice) >= 0,
      );
  const nameValid = isEdit || name.trim() !== '';
  const formValid = nameValid && priceValid && variantsValid;

  function setVariant(i: number, patch: Partial<VariantDraft>) {
    setVariants((vs) => vs.map((v, idx) => (idx === i ? { ...v, ...patch } : v)));
  }
  function addVariantRow() {
    setVariants((vs) => [...vs, { optionName: '', stock: '', additionalPrice: '' }]);
  }
  function removeVariantRow(i: number) {
    setVariants((vs) => (vs.length > 1 ? vs.filter((_, idx) => idx !== i) : vs));
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formValid) return;
    setError(null);
    setConflict(false);
    setConfirmOpen(true);
  }

  function handleError(err: unknown) {
    const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
    const status = err instanceof ApiError ? err.status : 0;
    if (status === 409 && code === 'CONFLICT') {
      setConflict(true);
      setError(messageForCode('CONFLICT', '상품 상태가 변경되었습니다.'));
      return;
    }
    setConflict(false);
    setError(messageForCode(code, '저장하지 못했습니다.'));
  }

  function confirmSubmit() {
    if (isEdit) {
      const body: UpdateProductBody = {
        name: name.trim() || undefined,
        description: description.trim() || undefined,
        price: price !== '' ? priceNum : undefined,
        status: status as UpdateProductBody['status'],
        thumbnailUrl: thumbnailUrl.trim() || undefined,
      };
      update.mutate(
        { id: existing!.id, body },
        {
          onSuccess: () => {
            setConfirmOpen(false);
            router.push(`/ecommerce/products/${existing!.id}`);
            router.refresh();
          },
          onError: handleError,
        },
      );
      return;
    }
    const body: RegisterProductBody = {
      name: name.trim(),
      description: description.trim() || undefined,
      price: priceNum,
      thumbnailUrl: thumbnailUrl.trim() || undefined,
      variants: variants.map((v) => ({
        optionName: v.optionName.trim(),
        stock: Number(v.stock),
        additionalPrice: Number(v.additionalPrice),
      })),
    };
    register.mutate(body, {
      onSuccess: () => {
        setConfirmOpen(false);
        router.push('/ecommerce/products');
        router.refresh();
      },
      onError: handleError,
    });
  }

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
  const labelCls = 'block text-sm font-medium text-foreground';

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
        <fieldset className="rounded-md border border-border p-4" data-testid="product-form-variants">
          <legend className="px-1 text-sm font-medium text-foreground">
            옵션(variant) <span className="text-destructive">*</span>
          </legend>
          <p className="mb-3 text-xs text-muted-foreground">
            상품 등록에는 최소 1개의 옵션이 필요합니다. 재고·추가 가격을 비워두면 0으로 등록됩니다.
          </p>
          <div
            className="mb-1 hidden gap-2 px-1 text-xs font-medium text-muted-foreground sm:grid sm:grid-cols-[1fr_6rem_8rem_auto]"
            data-testid="product-form-variant-header"
            aria-hidden="true"
          >
            <span>옵션명</span>
            <span>재고</span>
            <span>추가 가격</span>
            <span />
          </div>
          <div className="space-y-3">
            {variants.map((v, i) => (
              <div
                key={i}
                className="grid grid-cols-1 gap-2 sm:grid-cols-[1fr_6rem_8rem_auto]"
                data-testid={`product-form-variant-${i}`}
              >
                <input
                  aria-label={`옵션명 ${i + 1}`}
                  placeholder="옵션명"
                  value={v.optionName}
                  onChange={(e) => setVariant(i, { optionName: e.target.value })}
                  className={inputCls}
                  data-testid={`product-form-variant-name-${i}`}
                />
                <input
                  aria-label={`재고 ${i + 1}`}
                  inputMode="numeric"
                  placeholder="재고"
                  value={v.stock}
                  onChange={(e) =>
                    setVariant(i, { stock: e.target.value.replace(/[^0-9]/g, '') })
                  }
                  className={inputCls}
                  data-testid={`product-form-variant-stock-${i}`}
                />
                <input
                  aria-label={`추가 가격 ${i + 1}`}
                  inputMode="numeric"
                  placeholder="추가 가격"
                  value={v.additionalPrice}
                  onChange={(e) =>
                    setVariant(i, {
                      additionalPrice: e.target.value.replace(/[^0-9]/g, ''),
                    })
                  }
                  className={inputCls}
                  data-testid={`product-form-variant-addprice-${i}`}
                />
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => removeVariantRow(i)}
                  disabled={variants.length <= 1}
                  data-testid={`product-form-variant-remove-${i}`}
                >
                  삭제
                </Button>
              </div>
            ))}
          </div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={addVariantRow}
            className="mt-3"
            data-testid="product-form-variant-add"
          >
            옵션 추가
          </Button>
        </fieldset>
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
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
          setConflict(false);
        }}
      />
    </form>
  );
}
