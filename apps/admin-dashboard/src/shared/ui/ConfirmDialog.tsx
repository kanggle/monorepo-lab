'use client';

import { overlayStyle, dialogStyle } from '@/shared/lib/overlay-styles';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmDisabled?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = '확인',
  cancelLabel = '취소',
  confirmDisabled = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      style={{ ...overlayStyle, backdropFilter: 'blur(2px)' }}
    >
      <div style={dialogStyle}>
        <h2 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: '8px', color: '#111827' }}>
          {title}
        </h2>
        <p style={{ color: '#6b7280', marginBottom: '28px', fontSize: '0.875rem', lineHeight: '1.5' }}>
          {message}
        </p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
          <button
            onClick={onCancel}
            style={{
              padding: '9px 20px',
              borderRadius: '8px',
              border: '1px solid #e5e7eb',
              backgroundColor: '#fff',
              cursor: 'pointer',
              fontSize: '0.875rem',
              color: '#374151',
              fontWeight: 500,
            }}
          >
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            disabled={confirmDisabled}
            style={{
              padding: '9px 20px',
              borderRadius: '8px',
              border: 'none',
              backgroundColor: '#1A1A2E',
              color: '#fff',
              cursor: confirmDisabled ? 'not-allowed' : 'pointer',
              opacity: confirmDisabled ? 0.5 : 1,
              fontSize: '0.875rem',
              fontWeight: 500,
            }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
