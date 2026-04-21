'use client';

const STATUS_STYLES: Record<string, { backgroundColor: string; color: string; border?: string }> = {
  ON_SALE: { backgroundColor: '#10b981', color: '#fff' },
  SOLD_OUT: { backgroundColor: '#ef4444', color: '#fff' },
  HIDDEN: { backgroundColor: '#6b7280', color: '#fff' },
  ACTIVE: { backgroundColor: '#10b981', color: '#fff' },
  INACTIVE: { backgroundColor: '#6b7280', color: '#fff' },
  DRAFT: { backgroundColor: '#fff', color: '#666', border: '1px solid #d0d0d0' },
  PREPARING: { backgroundColor: '#f59e0b', color: '#fff' },
  IN_TRANSIT: { backgroundColor: '#3b82f6', color: '#fff' },
  PENDING: { backgroundColor: '#f59e0b', color: '#fff' },
  CONFIRMED: { backgroundColor: '#3b82f6', color: '#fff' },
  SHIPPED: { backgroundColor: '#8b5cf6', color: '#fff' },
  DELIVERED: { backgroundColor: '#10b981', color: '#fff' },
  CANCELLED: { backgroundColor: '#ef4444', color: '#fff' },
  COMPLETED: { backgroundColor: '#1A1A2E', color: '#fff' },
  FAILED: { backgroundColor: '#f5f5f5', color: '#999', border: '1px solid #e0e0e0' },
  REFUNDED: { backgroundColor: '#fff', color: '#666', border: '1px solid #d0d0d0' },
  SUSPENDED: { backgroundColor: '#f59e0b', color: '#fff' },
  WITHDRAWN: { backgroundColor: '#ef4444', color: '#fff' },
};

const STATUS_LABELS: Record<string, string> = {
  ON_SALE: '판매중',
  SOLD_OUT: '품절',
  HIDDEN: '숨김',
  ACTIVE: '활성',
  INACTIVE: '비활성',
  DRAFT: '임시저장',
  PREPARING: '준비중',
  IN_TRANSIT: '운송중',
  PENDING: '대기',
  CONFIRMED: '확인',
  SHIPPED: '배송중',
  DELIVERED: '배송완료',
  CANCELLED: '취소',
  COMPLETED: '완료',
  FAILED: '실패',
  REFUNDED: '환불',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
};

interface StatusBadgeProps {
  status: string;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const style = STATUS_STYLES[status] ?? { backgroundColor: '#f5f5f5', color: '#999', border: '1px solid #e0e0e0' };
  const label = STATUS_LABELS[status] ?? status;

  return (
    <span
      style={{
        ...style,
        display: 'inline-flex',
        alignItems: 'center',
        padding: '3px 10px',
        borderRadius: '9999px',
        fontSize: '0.75rem',
        fontWeight: 600,
        lineHeight: '1.25rem',
        letterSpacing: '0.01em',
      }}
    >
      {label}
    </span>
  );
}
