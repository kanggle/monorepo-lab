import { describe, it, expect } from 'vitest';
import type { ShippingResponse, ShippingStatus } from '@repo/types';
import {
  SHIPPING_STEPS,
  getStepIndex,
  getDeliveredDate,
  isShippingCancelled,
  SHIPPING_CANCELLED_MESSAGE,
} from '@/features/order/lib/shipping-steps';

/**
 * TASK-FE-077 — shipping progress derivation. A regression here shows the wrong
 * tracker step or silently drops the delivered date.
 */

function createShipping(overrides: Partial<ShippingResponse> = {}): ShippingResponse {
  return {
    shippingId: 'ship-1',
    orderId: 'order-1',
    status: 'PREPARING',
    trackingNumber: null,
    carrier: null,
    statusHistory: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('SHIPPING_STEPS', () => {
  it('lists the four ordered shipping stages', () => {
    expect(SHIPPING_STEPS.map((s) => s.status)).toEqual([
      'PREPARING',
      'SHIPPED',
      'IN_TRANSIT',
      'DELIVERED',
    ]);
  });
});

describe('getStepIndex', () => {
  it('returns the ordered index for each known status', () => {
    const expected: Array<[ShippingStatus, number]> = [
      ['PREPARING', 0],
      ['SHIPPED', 1],
      ['IN_TRANSIT', 2],
      ['DELIVERED', 3],
    ];
    for (const [status, index] of expected) {
      expect(getStepIndex(status)).toBe(index);
    }
  });

  it('returns -1 for an unknown status', () => {
    expect(getStepIndex('UNKNOWN' as ShippingStatus)).toBe(-1);
  });
});

describe('CANCELLED shipping', () => {
  it('is not one of the progress steps and has no step index', () => {
    expect(SHIPPING_STEPS.map((s) => s.status)).not.toContain('CANCELLED');
    expect(getStepIndex('CANCELLED')).toBe(-1);
  });

  it('isShippingCancelled is true only for CANCELLED', () => {
    expect(isShippingCancelled(createShipping({ status: 'CANCELLED' }))).toBe(true);
    expect(isShippingCancelled(createShipping({ status: 'PREPARING' }))).toBe(false);
    expect(isShippingCancelled(createShipping({ status: 'DELIVERED' }))).toBe(false);
  });

  it('exposes the buyer-facing message', () => {
    expect(SHIPPING_CANCELLED_MESSAGE).toBe('주문이 취소되어 배송이 취소되었습니다.');
  });

  it('getDeliveredDate returns null', () => {
    expect(getDeliveredDate(createShipping({ status: 'CANCELLED' }))).toBeNull();
  });
});

describe('getDeliveredDate', () => {
  it('returns the changedAt of the DELIVERED history entry when delivered', () => {
    const shipping = createShipping({
      status: 'DELIVERED',
      statusHistory: [
        { status: 'SHIPPED', changedAt: '2026-01-02T00:00:00Z' },
        { status: 'DELIVERED', changedAt: '2026-01-05T09:30:00Z' },
      ],
    });
    expect(getDeliveredDate(shipping)).toBe('2026-01-05T09:30:00Z');
  });

  it('returns null when the shipment is not yet delivered', () => {
    const shipping = createShipping({
      status: 'IN_TRANSIT',
      statusHistory: [{ status: 'DELIVERED', changedAt: '2026-01-05T09:30:00Z' }],
    });
    expect(getDeliveredDate(shipping)).toBeNull();
  });

  it('returns null when delivered but no DELIVERED history entry exists', () => {
    const shipping = createShipping({
      status: 'DELIVERED',
      statusHistory: [{ status: 'SHIPPED', changedAt: '2026-01-02T00:00:00Z' }],
    });
    expect(getDeliveredDate(shipping)).toBeNull();
  });
});
