'use client';

import { getErrorMessage } from '@repo/types/guards';
import type { OrderStatus } from '@repo/types';

interface OrderStatusActionsProps {
  status: OrderStatus;
  isPending: boolean;
  error: Error | null;
  onChangeStatus: (target: OrderStatus) => void;
}

export function OrderStatusActions({
  status, isPending, error, onChangeStatus,
}: OrderStatusActionsProps) {
  const NEXT_STATUS: Partial<Record<OrderStatus, { label: string; target: OrderStatus }>> = {
    PENDING: { label: '주문 확인', target: 'CONFIRMED' },
    CONFIRMED: { label: '배송 시작', target: 'SHIPPED' },
    SHIPPED: { label: '배송 완료', target: 'DELIVERED' },
  };
  const CANCELLABLE: OrderStatus[] = ['PENDING', 'CONFIRMED'];

  const next = NEXT_STATUS[status];
  const canCancel = CANCELLABLE.includes(status);

  if (!next && !canCancel) return null;

  return (
    <>
      <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
        {next && (
          <button
            onClick={() => onChangeStatus(next.target)}
            disabled={isPending}
            style={{
              padding: '8px 20px',
              borderRadius: '8px',
              border: 'none',
              backgroundColor: '#1A1A2E',
              color: '#fff',
              fontSize: '0.8125rem',
              fontWeight: 600,
              cursor: isPending ? 'not-allowed' : 'pointer',
              opacity: isPending ? 0.5 : 1,
            }}
          >
            {isPending ? '처리 중...' : next.label}
          </button>
        )}
        {canCancel && (
          <button
            onClick={() => onChangeStatus('CANCELLED')}
            disabled={isPending}
            style={{
              padding: '8px 20px',
              borderRadius: '8px',
              border: '1px solid #ccc',
              backgroundColor: '#fff',
              color: '#333',
              fontSize: '0.8125rem',
              fontWeight: 500,
              cursor: isPending ? 'not-allowed' : 'pointer',
              opacity: isPending ? 0.5 : 1,
            }}
          >
            주문 취소
          </button>
        )}
      </div>
      {error && (
        <p style={{ color: '#ef4444', fontSize: '0.8125rem', marginTop: '8px' }}>
          {getErrorMessage(error, '상태 변경에 실패했습니다.')}
        </p>
      )}
    </>
  );
}
