'use client';

import { useRouter } from 'next/navigation';
import type { PromotionDetail } from '@repo/types';
import { usePromotionForm } from '../hooks/use-promotion-form';
import { Section } from '@/shared/ui';
import { formStyles } from '@/shared/lib/form-styles';
import { PromotionPeriodFields } from './PromotionPeriodFields';

interface Props {
  promotion?: PromotionDetail;
}

const styles = {
  ...formStyles,
  input: {
    width: '100%',
    padding: '10px 14px',
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    fontSize: '0.875rem',
    color: '#111827',
    background: '#fff',
    outline: 'none',
  } as const,
  submitBtn: {
    padding: '10px 28px',
    borderRadius: '8px',
    border: 'none',
    backgroundColor: '#1A1A2E',
    color: '#fff',
    fontWeight: 600,
    fontSize: '0.875rem',
    cursor: 'pointer',
    opacity: 1,
  } as const,
  submitBtnDisabled: {
    padding: '10px 28px',
    borderRadius: '8px',
    border: 'none',
    backgroundColor: '#1A1A2E',
    color: '#fff',
    fontWeight: 600,
    fontSize: '0.875rem',
    cursor: 'not-allowed',
    opacity: 0.5,
  } as const,
  cancelBtn: {
    padding: '10px 28px',
    borderRadius: '8px',
    border: '1px solid #e5e7eb',
    backgroundColor: '#fff',
    color: '#374151',
    fontWeight: 500,
    fontSize: '0.875rem',
    cursor: 'pointer',
  } as const,
};

export function PromotionForm({ promotion }: Props) {
  const router = useRouter();
  const {
    name, setName,
    description, setDescription,
    discountType, setDiscountType,
    discountValue, setDiscountValue,
    maxDiscountAmount, setMaxDiscountAmount,
    maxIssuanceCount, setMaxIssuanceCount,
    startDate, setStartDate,
    endDate, setEndDate,
    error,
    isSubmitting,
    isEdit,
    isValid,
    handleSubmit,
  } = usePromotionForm(promotion);

  const dateError = startDate && endDate && startDate >= endDate;

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {error && (
        <div role="alert" style={styles.errorAlert}>
          {error}
        </div>
      )}

      <Section title="기본 정보">
        <div style={{ display: 'grid', gap: '16px', maxWidth: '560px' }}>
          <Field htmlFor="name" label="프로모션명" required>
            <input id="name" type="text" value={name} onChange={(e) => setName(e.target.value)}
              placeholder="예: 봄맞이 10% 할인" required style={styles.input} />
          </Field>
          <Field htmlFor="description" label="설명">
            <textarea id="description" value={description} onChange={(e) => setDescription(e.target.value)}
              rows={3} placeholder="프로모션에 대한 간단한 설명을 입력하세요"
              style={{ ...styles.input, resize: 'vertical' }} />
          </Field>
        </div>
      </Section>

      <Section title="할인 설정">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px 24px', maxWidth: '400px' }}>
          <Field htmlFor="discountType" label="할인 유형" required>
            <select id="discountType" value={discountType}
              onChange={(e) => setDiscountType(e.target.value as 'FIXED' | 'PERCENTAGE')}
              style={styles.input}>
              <option value="FIXED">정액</option>
              <option value="PERCENTAGE">정률 (%)</option>
            </select>
          </Field>
          <Field htmlFor="discountValue" label="할인값" required>
            <input id="discountValue" type="number" value={discountValue}
              onChange={(e) => setDiscountValue(Number(e.target.value))}
              min={0} max={discountType === 'PERCENTAGE' ? 100 : undefined}
              required style={styles.input} />
          </Field>
          <Field htmlFor="maxDiscountAmount" label="최대 할인금액" hint="0 = 무제한">
            <input id="maxDiscountAmount" type="number" value={maxDiscountAmount}
              onChange={(e) => setMaxDiscountAmount(Number(e.target.value))}
              min={0} style={styles.input} />
          </Field>
          <Field htmlFor="maxIssuanceCount" label="최대 발급 수량" required>
            <input id="maxIssuanceCount" type="number" value={maxIssuanceCount}
              onChange={(e) => setMaxIssuanceCount(Number(e.target.value))}
              min={1} required style={styles.input} />
          </Field>
        </div>
      </Section>

      <Section title="기간 설정">
        <PromotionPeriodFields
          startDate={startDate}
          endDate={endDate}
          onStartDateChange={setStartDate}
          onEndDateChange={setEndDate}
          isEdit={isEdit}
          dateError={!!dateError}
        />
      </Section>

      <div style={{ display: 'flex', gap: '10px', paddingTop: '8px' }}>
        <button type="submit" disabled={!isValid || isSubmitting || !!dateError}
          style={isValid && !dateError ? styles.submitBtn : styles.submitBtnDisabled}>
          {isSubmitting ? '저장 중...' : isEdit ? '수정' : '등록'}
        </button>
        <button type="button" onClick={() => router.back()} style={styles.cancelBtn}>
          취소
        </button>
      </div>

    </form>
  );
}

function Field({ htmlFor, label, required, hint, children }: {
  htmlFor?: string;
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label htmlFor={htmlFor} style={{
        display: 'block', marginBottom: '6px', fontSize: '0.8125rem',
        fontWeight: 600, color: '#374151',
      }}>
        {required ? `${label} *` : label}
      </label>
      {children}
      {hint && (
        <p style={{ margin: '4px 0 0', fontSize: '0.75rem', color: '#9ca3af' }}>{hint}</p>
      )}
    </div>
  );
}
