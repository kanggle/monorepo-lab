'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useRegisterProduct,
  useUpdateProduct,
} from './use-ecommerce-products';
import {
  type ProductDetail,
  type RegisterProductBody,
  type UpdateProductBody,
} from '../api/types';

export interface VariantDraft {
  optionName: string;
  stock: string;
  additionalPrice: string;
}

/**
 * Form state + validation + confirm-gated submit for {@link ProductForm}
 * (TASK-PC-FE-139 — extracted from the former fat container, behavior-preserving).
 *
 * Owns all field state, the register/update mutations and the 409/422 error
 * surfacing. The component consumes the returned values/handlers and only
 * renders — no logic change vs the pre-split single file.
 */
export function useProductForm(existing?: ProductDetail) {
  const router = useRouter();
  const isEdit = existing !== undefined;
  const nameId = useId();
  const descId = useId();
  const priceId = useId();
  const statusId = useId();
  const thumbId = useId();

  const [name, setName] = useState(existing?.name ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [price, setPrice] = useState(existing ? String(existing.price) : '');
  const [status, setStatus] = useState(existing?.status ?? 'ON_SALE');
  const [thumbnailUrl, setThumbnailUrl] = useState(existing?.thumbnailUrl ?? '');
  const [variants, setVariants] = useState<VariantDraft[]>([
    { optionName: '', stock: '', additionalPrice: '' },
  ]);

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  // Idempotency-Key for the create path, minted ONCE per confirmed submit
  // (TASK-PC-FE-252). Because the fields are locked behind the confirm dialog,
  // an edited resubmit must cancel → re-submit, which re-fires onSubmit and
  // mints a fresh key; retrying the SAME open confirm reuses it. Update (PATCH)
  // is idempotent producer-side and needs no key.
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

  const register = useRegisterProduct();
  const update = useUpdateProduct();
  const pending = register.isPending || update.isPending;

  const priceNum = Number(price);
  const priceValid =
    price !== '' && Number.isInteger(priceNum) && priceNum >= (isEdit ? 0 : 1);
  const variantsValid =
    isEdit ||
    (variants.length > 0 &&
      variants.every(
        (v) =>
          v.optionName.trim() !== '' &&
          Number.isInteger(Number(v.stock)) &&
          Number(v.stock) >= 0 &&
          Number.isInteger(Number(v.additionalPrice)) &&
          Number(v.additionalPrice) >= 0,
      ));
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
    // A fresh confirmed create attempt → a fresh key. An edited resubmit passes
    // through here again (the confirm was cancelled first), so it gets a new key.
    if (!isEdit) setIdempotencyKey(crypto.randomUUID());
    setConfirmOpen(true);
  }

  function handleError(err: unknown) {
    const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
    const httpStatus = err instanceof ApiError ? err.status : 0;
    if (httpStatus === 409 && code === 'CONFLICT') {
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
    // onSubmit always mints the key before opening the confirm; guard for types.
    const key = idempotencyKey ?? crypto.randomUUID();
    register.mutate(
      { body, idempotencyKey: key },
      {
        onSuccess: () => {
          setConfirmOpen(false);
          router.push('/ecommerce/products');
          router.refresh();
        },
        onError: handleError,
      },
    );
  }

  function cancelConfirm() {
    setConfirmOpen(false);
    setError(null);
    setConflict(false);
  }

  return {
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
  };
}
