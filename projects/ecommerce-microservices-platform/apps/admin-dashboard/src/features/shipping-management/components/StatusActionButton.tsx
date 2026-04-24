'use client';

import type { ShippingSummary, ShippingStatus } from '@repo/types';

const NEXT_STATUS: Partial<Record<ShippingStatus, { label: string; target: ShippingStatus }>> = {
  PREPARING: { label: '발송 처리', target: 'SHIPPED' },
  SHIPPED: { label: '배송중 전환', target: 'IN_TRANSIT' },
  IN_TRANSIT: { label: '배송완료 처리', target: 'DELIVERED' },
};

interface Props {
  shipping: ShippingSummary;
  isPending: boolean;
  onAction: (shipping: ShippingSummary, target: ShippingStatus) => void;
}

export function StatusActionButton({ shipping, isPending, onAction }: Props) {
  const next = NEXT_STATUS[shipping.status];
  if (!next) return null;

  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onAction(shipping, next.target);
      }}
      disabled={isPending}
      style={{
        padding: '4px 12px',
        borderRadius: '6px',
        border: 'none',
        backgroundColor: '#1A1A2E',
        color: '#fff',
        fontSize: '0.75rem',
        fontWeight: 500,
        cursor: isPending ? 'not-allowed' : 'pointer',
        opacity: isPending ? 0.5 : 1,
        whiteSpace: 'nowrap',
      }}
    >
      {next.label}
    </button>
  );
}
