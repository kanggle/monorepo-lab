'use client';

import { useEffect } from 'react';

type ToastType = 'success' | 'error';

interface ToastProps {
  message: string;
  type: ToastType;
  onClose: () => void;
  duration?: number;
}

// 🔴 색을 고정값으로 두지 않는다 (TASK-FE-101). 예전 판은 배경만 밝은 연녹·연분홍으로 고정하고 글자색은
//    테마 토큰 `--color-text` 를 썼다 — 다크 테마에서 그 토큰이 거의 흰색이 되어 «장바구니에 추가되었습니다.»
//    가 밝은 배경 위 밝은 글자로 사실상 안 보였다(대비 약 1.1:1). 배경·테두리·글자 셋 다 `globals.css` 토큰이고
//    두 테마의 대비는 `toast-dark-contrast.test.ts` 가 잰다.
const STYLE_MAP: Record<ToastType, { backgroundColor: string; borderColor: string }> = {
  success: { backgroundColor: 'var(--color-success-surface)', borderColor: 'var(--color-success-border)' },
  error: { backgroundColor: 'var(--color-error-surface)', borderColor: 'var(--color-error-border)' },
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
