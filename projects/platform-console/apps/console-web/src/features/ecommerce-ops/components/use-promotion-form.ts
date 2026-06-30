'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useCreatePromotion,
  useUpdatePromotion,
} from '../hooks/use-ecommerce-promotions';
import {
  DISCOUNT_TYPE_VALUES,
  type PromotionDetail,
  type CreatePromotionBody,
  type UpdatePromotionBody,
} from '../api/types';

/**
 * `<input type="date">` yields `YYYY-MM-DD`; the promotion-service parses
 * startDate/endDate as java.time.Instant (ISO-8601, trailing `Z` required), so a
 * bare date is rejected with INVALID_PROMOTION_REQUEST. Widen the picked day to a
 * UTC instant: start → 00:00:00Z, end → 23:59:59Z (end-of-day inclusive window).
 */
function dayToInstant(day: string, edge: 'start' | 'end'): string {
  return `${day}T${edge === 'end' ? '23:59:59' : '00:00:00'}Z`;
}

/**
 * Form state + validation + confirm-gated submit for {@link PromotionForm}
 * (TASK-PC-FE-141 — extracted from the former fat container, behavior-preserving).
 *
 * Owns all field state, the create/update mutations (producer uses PUT full
 * replace for updates — NOT PATCH) and inline error surfacing. The component
 * consumes the returned values/handlers and only renders — no logic change vs
 * the pre-split single file (validation predicate, day→Instant widening and
 * wire body are identical).
 */
export function usePromotionForm(existing?: PromotionDetail) {
  const router = useRouter();
  const isEdit = existing !== undefined;
  const nameId = useId();
  const descId = useId();
  const discountTypeId = useId();
  const discountValueId = useId();
  const maxDiscountAmountId = useId();
  const maxIssuanceCountId = useId();
  const startDateId = useId();
  const endDateId = useId();

  const [name, setName] = useState(existing?.name ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [discountType, setDiscountType] = useState<string>(
    existing?.discountType ?? 'FIXED',
  );
  const [discountValue, setDiscountValue] = useState(
    existing ? String(existing.discountValue) : '',
  );
  const [maxDiscountAmount, setMaxDiscountAmount] = useState(
    existing?.maxDiscountAmount != null
      ? String(existing.maxDiscountAmount)
      : '0',
  );
  const [maxIssuanceCount, setMaxIssuanceCount] = useState(
    existing ? String(existing.maxIssuanceCount) : '',
  );
  // The producer returns Instant strings (e.g. 2026-07-01T00:00:00Z); the
  // `type="date"` inputs need a bare YYYY-MM-DD, so slice the date part for edit.
  const [startDate, setStartDate] = useState((existing?.startDate ?? '').slice(0, 10));
  const [endDate, setEndDate] = useState((existing?.endDate ?? '').slice(0, 10));

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = useCreatePromotion();
  const update = useUpdatePromotion();
  const pending = create.isPending || update.isPending;

  const discountValueNum = Number(discountValue);
  const maxDiscountAmountNum = Number(maxDiscountAmount);
  const maxIssuanceCountNum = Number(maxIssuanceCount);

  const formValid =
    name.trim() !== '' &&
    DISCOUNT_TYPE_VALUES.includes(discountType as 'FIXED' | 'PERCENTAGE') &&
    discountValue !== '' &&
    Number.isInteger(discountValueNum) &&
    discountValueNum > 0 &&
    maxDiscountAmount !== '' &&
    Number.isInteger(maxDiscountAmountNum) &&
    maxDiscountAmountNum >= 0 &&
    maxIssuanceCount !== '' &&
    Number.isInteger(maxIssuanceCountNum) &&
    maxIssuanceCountNum > 0 &&
    startDate.trim() !== '' &&
    endDate.trim() !== '';

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formValid) return;
    setError(null);
    setConfirmOpen(true);
  }

  function handleError(err: unknown) {
    const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
    setError(messageForCode(code, '저장하지 못했습니다.'));
  }

  function confirmSubmit() {
    const body: CreatePromotionBody | UpdatePromotionBody = {
      name: name.trim(),
      description: description.trim() || undefined,
      discountType: discountType as 'FIXED' | 'PERCENTAGE',
      discountValue: discountValueNum,
      maxDiscountAmount: maxDiscountAmountNum,
      maxIssuanceCount: maxIssuanceCountNum,
      startDate: dayToInstant(startDate.trim(), 'start'),
      endDate: dayToInstant(endDate.trim(), 'end'),
    };

    if (isEdit) {
      update.mutate(
        { id: existing!.promotionId, body },
        {
          onSuccess: () => {
            setConfirmOpen(false);
            router.push(`/ecommerce/promotions/${existing!.promotionId}`);
            router.refresh();
          },
          onError: handleError,
        },
      );
      return;
    }
    create.mutate(body, {
      onSuccess: () => {
        setConfirmOpen(false);
        router.push('/ecommerce/promotions');
        router.refresh();
      },
      onError: handleError,
    });
  }

  function cancelConfirm() {
    setConfirmOpen(false);
    setError(null);
  }

  return {
    router,
    isEdit,
    ids: {
      nameId,
      descId,
      discountTypeId,
      discountValueId,
      maxDiscountAmountId,
      maxIssuanceCountId,
      startDateId,
      endDateId,
    },
    fields: {
      name,
      setName,
      description,
      setDescription,
      discountType,
      setDiscountType,
      discountValue,
      setDiscountValue,
      maxDiscountAmount,
      setMaxDiscountAmount,
      maxIssuanceCount,
      setMaxIssuanceCount,
      startDate,
      setStartDate,
      endDate,
      setEndDate,
    },
    confirmOpen,
    error,
    pending,
    formValid,
    onSubmit,
    confirmSubmit,
    cancelConfirm,
  };
}
