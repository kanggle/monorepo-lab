'use client';

interface DeleteConfirmationProps {
  isDeleting: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function DeleteConfirmation({ isDeleting, onConfirm, onCancel }: DeleteConfirmationProps) {
  return (
    <div
      style={{
        marginTop: 'var(--space-3)',
        padding: 'var(--space-3) var(--space-4)',
        backgroundColor: 'rgba(231, 76, 60, 0.06)',
        borderRadius: 'var(--radius-md)',
        border: '1px solid rgba(231, 76, 60, 0.15)',
      }}
    >
      <p style={{ marginBottom: 'var(--space-2)', fontSize: 'var(--font-size-sm)' }}>
        이 배송지를 삭제하시겠습니까?
      </p>
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <button
          onClick={onConfirm}
          disabled={isDeleting}
          aria-label="삭제 확인"
          className="btn"
          style={{
            backgroundColor: 'var(--color-error)',
            color: 'var(--color-white)',
            borderColor: 'var(--color-error)',
          }}
        >
          {isDeleting ? '삭제 중...' : '삭제'}
        </button>
        <button
          onClick={onCancel}
          aria-label="삭제 취소"
          className="btn"
        >
          취소
        </button>
      </div>
    </div>
  );
}
