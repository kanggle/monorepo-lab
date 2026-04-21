'use client';

import { useState } from 'react';
import { overlayStyle, dialogStyle } from '@/shared/lib/overlay-styles';

interface ShipFormDialogProps {
  open: boolean;
  isPending: boolean;
  onConfirm: (trackingNumber: string, carrier: string) => void;
  onCancel: () => void;
}

const styles = {
  overlay: { ...overlayStyle, backdropFilter: 'blur(2px)' } as const,
  title: { fontSize: '1.125rem', fontWeight: 600, marginBottom: '8px', color: '#111827' } as const,
  description: { color: '#6b7280', marginBottom: '20px', fontSize: '0.875rem', lineHeight: '1.5' } as const,
  fieldGroup: { marginBottom: '12px' } as const,
  fieldGroupLast: { marginBottom: '20px' } as const,
  label: { display: 'block', fontSize: '0.8125rem', fontWeight: 500, color: '#374151', marginBottom: '4px' } as const,
  input: {
    width: '100%',
    padding: '9px 14px',
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    fontSize: '0.875rem',
    backgroundColor: '#f9fafb',
    outline: 'none',
    boxSizing: 'border-box' as const,
  } as const,
  buttonRow: { display: 'flex', justifyContent: 'flex-end', gap: '8px' } as const,
  cancelBtn: {
    padding: '9px 20px',
    borderRadius: '8px',
    border: '1px solid #e5e7eb',
    backgroundColor: '#fff',
    cursor: 'pointer',
    fontSize: '0.875rem',
    color: '#374151',
    fontWeight: 500,
  } as const,
  submitBtn: {
    padding: '9px 20px',
    borderRadius: '8px',
    border: 'none',
    backgroundColor: '#1A1A2E',
    color: '#fff',
    cursor: 'pointer',
    opacity: 1,
    fontSize: '0.875rem',
    fontWeight: 500,
  } as const,
  submitBtnDisabled: {
    padding: '9px 20px',
    borderRadius: '8px',
    border: 'none',
    backgroundColor: '#1A1A2E',
    color: '#fff',
    cursor: 'not-allowed',
    opacity: 0.5,
    fontSize: '0.875rem',
    fontWeight: 500,
  } as const,
};

export function ShipFormDialog({ open, isPending, onConfirm, onCancel }: ShipFormDialogProps) {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [carrier, setCarrier] = useState('');

  if (!open) return null;

  const isValid = trackingNumber.trim().length > 0 && carrier.trim().length > 0;

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isPending) return;
    onConfirm(trackingNumber.trim(), carrier.trim());
  }

  return (
    <div role="dialog" aria-modal="true" aria-label="발송 처리" style={styles.overlay}>
      <div style={dialogStyle}>
        <h2 style={styles.title}>발송 처리</h2>
        <p style={styles.description}>운송장 번호와 택배사를 입력하세요.</p>
        <form onSubmit={handleSubmit}>
          <div style={styles.fieldGroup}>
            <label htmlFor="carrier" style={styles.label}>택배사</label>
            <input
              id="carrier"
              type="text"
              value={carrier}
              onChange={(e) => setCarrier(e.target.value)}
              placeholder="예: CJ대한통운"
              style={styles.input}
            />
          </div>
          <div style={styles.fieldGroupLast}>
            <label htmlFor="trackingNumber" style={styles.label}>운송장 번호</label>
            <input
              id="trackingNumber"
              type="text"
              value={trackingNumber}
              onChange={(e) => setTrackingNumber(e.target.value)}
              placeholder="운송장 번호를 입력하세요"
              style={styles.input}
            />
          </div>
          <div style={styles.buttonRow}>
            <button type="button" onClick={onCancel} style={styles.cancelBtn}>
              취소
            </button>
            <button
              type="submit"
              disabled={!isValid || isPending}
              style={isValid && !isPending ? styles.submitBtn : styles.submitBtnDisabled}
            >
              {isPending ? '처리 중...' : '발송 처리'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
