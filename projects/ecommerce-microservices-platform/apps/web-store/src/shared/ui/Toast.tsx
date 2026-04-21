'use client';

import { useEffect } from 'react';

type ToastType = 'success' | 'error';

interface ToastProps {
  message: string;
  type: ToastType;
  onClose: () => void;
  duration?: number;
}

const STYLE_MAP: Record<ToastType, { backgroundColor: string; borderColor: string }> = {
  success: { backgroundColor: '#f0fdf4', borderColor: '#22c55e' },
  error: { backgroundColor: '#fef2f2', borderColor: '#ef4444' },
};

export function Toast({ message, type, onClose, duration = 3000 }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(onClose, duration);
    return () => clearTimeout(timer);
  }, [onClose, duration]);

  const style = STYLE_MAP[type];

  return (
    <div
      role={type === 'error' ? 'alert' : 'status'}
      style={{
        position: 'fixed',
        top: 'calc(var(--header-height) + var(--space-4))',
        right: 'var(--space-6)',
        padding: 'var(--space-3) var(--space-5)',
        borderRadius: 'var(--radius-md)',
        border: `1px solid ${style.borderColor}`,
        backgroundColor: style.backgroundColor,
        color: 'var(--color-text)',
        fontSize: 'var(--font-size-sm)',
        zIndex: 9999,
        boxShadow: 'var(--shadow-lg)',
      }}
    >
      {message}
    </div>
  );
}
