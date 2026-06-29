'use client';

import { Button } from '@/shared/ui/Button';
import { showPickerOnClick } from '@/shared/lib/show-picker';
import { DISCOUNT_TYPE_VALUES, type PromotionDetail } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { usePromotionForm } from './use-promotion-form';

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
 *
 * TASK-PC-FE-141: form state/validation/submit (incl. the day→Instant widening)
 * live in {@link usePromotionForm}; this container only wires the hook to the
 * markup (behavior-preserving split). The fields are a flat, non-repeating set
 * so no presentational sub-component is extracted — that would only add
 * prop-drilling without reuse.
 */

export interface PromotionFormProps {
  /** When set, the form is in UPDATE mode for this promotion. */
  existing?: PromotionDetail;
}

const inputCls =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
const labelCls = 'block text-sm font-medium text-foreground';

export function PromotionForm({ existing }: PromotionFormProps) {
  const {
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
  } = usePromotionForm(existing);

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
            onClick={showPickerOnClick}
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
            onClick={showPickerOnClick}
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
        onCancel={cancelConfirm}
      />
    </form>
  );
}
