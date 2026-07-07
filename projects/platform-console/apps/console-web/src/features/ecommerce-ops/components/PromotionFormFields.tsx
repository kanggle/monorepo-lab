'use client';

import { showPickerOnClick } from '@/shared/lib/show-picker';
import { DISCOUNT_TYPE_VALUES } from '../api/types';
import type { usePromotionForm } from './use-promotion-form';

type PromotionFormHook = ReturnType<typeof usePromotionForm>;

interface PromotionFormFieldsProps {
  ids: PromotionFormHook['ids'];
  fields: PromotionFormHook['fields'];
}

const inputCls =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
const labelCls = 'block text-sm font-medium text-foreground';

/**
 * Promotion form field grid (TASK-PC-FE-216 — extracted from {@link PromotionForm},
 * presentational only). Renders the basic-info / discount-rule / limit / period
 * fieldset region; the `ids`/`fields` bundles are the hook's own cohesive groups
 * so the container assembles them without per-field prop-drilling. All
 * `data-testid`·DOM·className·aria·text are byte-identical to the pre-split file.
 */
export function PromotionFormFields({ ids, fields }: PromotionFormFieldsProps) {
  const {
    nameId,
    descId,
    discountTypeId,
    discountValueId,
    maxDiscountAmountId,
    maxIssuanceCountId,
    startDateId,
    endDateId,
  } = ids;
  const {
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
  } = fields;

  return (
    <>
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
    </>
  );
}
