'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
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
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Create / update promotion form (TASK-PC-FE-086 — ADR-031 Phase 3b).
 * Used by both the `new` and `[id]/edit` pages.
 *
 *   - CREATE (no `existing`): all fields required per producer contract.
 *     On success → `/ecommerce/promotions`.
 *   - UPDATE (`existing` set): PUT full replace (all fields required).
 *     On success → `/ecommerce/promotions/{id}`.
 *
 * Confirm-gated submit; 422 family surfaced inline (NO `Idempotency-Key`).
 * Producer uses PUT (full replace) for updates — NOT PATCH.
 */

export interface PromotionFormProps {
  /** When set, the form is in UPDATE mode for this promotion. */
  existing?: PromotionDetail;
}

export function PromotionForm({ existing }: PromotionFormProps) {
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
  const [description, setDescription] = useState(
    existing?.description ?? '',
  );
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
  const [startDate, setStartDate] = useState(existing?.startDate ?? '');
  const [endDate, setEndDate] = useState(existing?.endDate ?? '');

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
      startDate: startDate.trim(),
      endDate: endDate.trim(),
    };

    if (isEdit) {
      update.mutate(
        { id: existing!.promotionId, body },
        {
          onSuccess: () => {
            setConfirmOpen(false);
            router.push(
              `/ecommerce/promotions/${existing!.promotionId}`,
            );
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

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
  const labelCls = 'block text-sm font-medium text-foreground';

  return (
    <form
      onSubmit={onSubmit}
      className="max-w-2xl space-y-5"
      data-testid="promotion-form"
    >
      <div>
        <label htmlFor={nameId} className={labelCls}>
          이름 <span className="text-destructive">*</span>
        </label>
        <input
          id={nameId}
          value={name}
          onChange={(e) => setName(e.target.value)}
          className={inputCls}
          data-testid="promotion-form-name"
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
          data-testid="promotion-form-description"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={discountTypeId} className={labelCls}>
            할인 유형 <span className="text-destructive">*</span>
          </label>
          <select
            id={discountTypeId}
            value={discountType}
            onChange={(e) => setDiscountType(e.target.value)}
            className={inputCls}
            data-testid="promotion-form-discount-type"
          >
            {DISCOUNT_TYPE_VALUES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={discountValueId} className={labelCls}>
            할인값{' '}
            {discountType === 'FIXED' ? '(₩)' : '(%)'}
            {' '}
            <span className="text-destructive">*</span>
          </label>
          <input
            id={discountValueId}
            inputMode="numeric"
            value={discountValue}
            onChange={(e) =>
              setDiscountValue(e.target.value.replace(/[^0-9]/g, ''))
            }
            className={inputCls}
            data-testid="promotion-form-discount-value"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={maxDiscountAmountId} className={labelCls}>
            최대 할인 금액(₩) <span className="text-destructive">*</span>
          </label>
          <input
            id={maxDiscountAmountId}
            inputMode="numeric"
            value={maxDiscountAmount}
            onChange={(e) =>
              setMaxDiscountAmount(e.target.value.replace(/[^0-9]/g, ''))
            }
            className={inputCls}
            data-testid="promotion-form-max-discount-amount"
          />
        </div>
        <div>
          <label htmlFor={maxIssuanceCountId} className={labelCls}>
            최대 발급 수 <span className="text-destructive">*</span>
          </label>
          <input
            id={maxIssuanceCountId}
            inputMode="numeric"
            value={maxIssuanceCount}
            onChange={(e) =>
              setMaxIssuanceCount(e.target.value.replace(/[^0-9]/g, ''))
            }
            className={inputCls}
            data-testid="promotion-form-max-issuance-count"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={startDateId} className={labelCls}>
            시작일 <span className="text-destructive">*</span>
          </label>
          <input
            id={startDateId}
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className={inputCls}
            data-testid="promotion-form-start-date"
          />
        </div>
        <div>
          <label htmlFor={endDateId} className={labelCls}>
            종료일 <span className="text-destructive">*</span>
          </label>
          <input
            id={endDateId}
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className={inputCls}
            data-testid="promotion-form-end-date"
          />
        </div>
      </div>

      {error && !confirmOpen && (
        <p
          role="alert"
          className="text-sm text-destructive"
          data-testid="promotion-form-error"
        >
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <Button
          type="submit"
          disabled={!formValid || pending}
          data-testid="promotion-form-submit"
        >
          {isEdit ? '변경 저장' : '프로모션 등록'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
          data-testid="promotion-form-cancel"
        >
          취소
        </Button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title={isEdit ? '프로모션 변경을 저장할까요?' : '프로모션을 등록할까요?'}
        description={
          isEdit
            ? '입력한 내용으로 프로모션을 수정합니다.'
            : '입력한 내용으로 새 프로모션을 등록합니다.'
        }
        confirmLabel={isEdit ? '저장' : '등록'}
        pending={pending}
        errorMessage={error}
        onConfirm={confirmSubmit}
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
        }}
      />
    </form>
  );
}
