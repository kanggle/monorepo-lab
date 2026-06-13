'use client';

import type { ShippingSummary, ShippingStatus } from '@repo/types';

// TASK-FE-073 — operator on-demand "carrier sync" affordance. Offered ONLY when
// there is a waybill to pull and the shipment is not terminal:
//   status ∈ {SHIPPED, IN_TRANSIT} AND a non-blank trackingNumber.
// PREPARING has no waybill yet; DELIVERED is forward-only terminal (a pull could
// only no-op). The carrier pull itself is best-effort server-side (BE-293/364).
const SYNCABLE_STATUSES: ReadonlySet<ShippingStatus> = new Set<ShippingStatus>([
  'SHIPPED',
  'IN_TRANSIT',
]);

interface Props {
  shipping: ShippingSummary;
  isPending: boolean;
  onSync: (shipping: ShippingSummary) => void;
}

export function RefreshTrackingButton({ shipping, isPending, onSync }: Props) {
  const hasWaybill = !!shipping.trackingNumber && shipping.trackingNumber.trim().length > 0;
  if (!SYNCABLE_STATUSES.has(shipping.status) || !hasWaybill) return null;

  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onSync(shipping);
      }}
      disabled={isPending}
      style={{
        padding: '4px 12px',
        borderRadius: '6px',
        border: '1px solid #1A1A2E',
        backgroundColor: '#fff',
        color: '#1A1A2E',
        fontSize: '0.75rem',
        fontWeight: 500,
        cursor: isPending ? 'not-allowed' : 'pointer',
        opacity: isPending ? 0.5 : 1,
        whiteSpace: 'nowrap',
      }}
    >
      택배사 동기화
    </button>
  );
}
