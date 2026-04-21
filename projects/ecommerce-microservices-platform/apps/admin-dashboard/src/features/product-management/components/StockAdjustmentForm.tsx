'use client';

import { useState } from 'react';
import type { ProductVariant } from '@repo/types';
import { useAsyncAction } from '@/shared/hooks/use-async-action';
import { overlayStyle } from '@/shared/lib/overlay-styles';
import { formStyles } from '@/shared/lib/form-styles';
import { useAdjustStock } from '../hooks/use-adjust-stock';

interface Props {
  productId: string;
  variant: ProductVariant;
  onClose: () => void;
}

const styles = {
  ...formStyles,
  overlay: { ...overlayStyle, backgroundColor: 'rgba(0, 0, 0, 0.5)' as const },
  dialog: { backgroundColor: '#fff', borderRadius: '8px', padding: '24px', maxWidth: '400px', width: '100%' } as const,
  title: { fontSize: '1.125rem', fontWeight: 600, marginBottom: '16px' } as const,
  subtitle: { color: '#6b7280', marginBottom: '16px' } as const,
  error: { color: 'red', marginBottom: '12px' } as const,
  fieldGroup: { marginBottom: '12px' } as const,
  fieldGroupLast: { marginBottom: '16px' } as const,
  buttonRow: { display: 'flex', justifyContent: 'flex-end', gap: '8px' } as const,
  cancelBtn: { padding: '8px 16px', borderRadius: '6px', border: '1px solid #d1d5db', backgroundColor: '#fff', cursor: 'pointer' } as const,
  submitBtn: { padding: '8px 16px', borderRadius: '6px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'pointer', opacity: 1 } as const,
  submitBtnDisabled: { padding: '8px 16px', borderRadius: '6px', border: 'none', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'not-allowed', opacity: 0.5 } as const,
};

export function StockAdjustmentForm({ productId, variant, onClose }: Props) {
  const [quantity, setQuantity] = useState(0);
  const [reason, setReason] = useState('');
  const { error, execute } = useAsyncAction();
  const adjustStock = useAdjustStock();

  const isValid = quantity !== 0 && reason.trim().length > 0;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid) return;

    await execute(async () => {
      await adjustStock.mutateAsync({
        productId,
        data: { variantId: variant.id, quantity, reason: reason.trim() },
      });
      onClose();
    }, '재고 조정에 실패했습니다.');
  }

  return (
    <div role="dialog" aria-modal="true" aria-label="재고 조정" style={styles.overlay}>
      <div style={styles.dialog}>
        <h2 style={styles.title}>재고 조정 — {variant.optionName}</h2>
        <p style={styles.subtitle}>현재 재고: {variant.stock}</p>

        {error && <p role="alert" style={styles.error}>{error}</p>}

        <form onSubmit={handleSubmit}>
          <div style={styles.fieldGroup}>
            <label htmlFor="quantity" style={styles.label}>조정 수량 (양수: 입고, 음수: 출고)</label>
            <input id="quantity" type="number" value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} style={styles.input} />
          </div>
          <div style={styles.fieldGroupLast}>
            <label htmlFor="reason" style={styles.label}>사유</label>
            <input id="reason" type="text" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="재고 조정 사유를 입력하세요" style={styles.input} />
          </div>
          <div style={styles.buttonRow}>
            <button type="button" onClick={onClose} style={styles.cancelBtn}>취소</button>
            <button type="submit" disabled={!isValid || adjustStock.isPending}
              style={isValid ? styles.submitBtn : styles.submitBtnDisabled}>
              {adjustStock.isPending ? '처리 중...' : '조정'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
