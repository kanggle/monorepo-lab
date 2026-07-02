import { describe, it, expect } from 'vitest';
import { orderStatusTone } from '@/features/ecommerce-ops/api/order-types';
import { shippingStatusTone } from '@/features/ecommerce-ops/api/shipping-types';
import {
  productStatusTone,
  promotionStatusTone,
} from '@/features/ecommerce-ops/api/types';

/**
 * TASK-PC-FE-159 — the ecommerce status → shared {@link StatusTone} maps
 * (orders / shipping / product / promotion), rendered via the shared
 * `<StatusBadge>`. Locks each domain's semantic mapping and the TOLERANCE
 * fallback (unknown/future status → `neutral`, never a crash).
 */

describe('orderStatusTone', () => {
  it('maps each known order status to its semantic tone', () => {
    expect(orderStatusTone('PENDING')).toBe('warning');
    expect(orderStatusTone('CONFIRMED')).toBe('progress');
    expect(orderStatusTone('SHIPPED')).toBe('progress');
    expect(orderStatusTone('DELIVERED')).toBe('success');
    expect(orderStatusTone('CANCELLED')).toBe('danger');
    expect(orderStatusTone('STUCK_RECOVERY_FAILED')).toBe('danger');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(orderStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('shippingStatusTone', () => {
  it('maps each known shipping status to its semantic tone', () => {
    expect(shippingStatusTone('PREPARING')).toBe('warning');
    expect(shippingStatusTone('SHIPPED')).toBe('progress');
    expect(shippingStatusTone('IN_TRANSIT')).toBe('progress');
    expect(shippingStatusTone('DELIVERED')).toBe('success');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(shippingStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('productStatusTone', () => {
  it('maps each known product status to its semantic tone', () => {
    expect(productStatusTone('ON_SALE')).toBe('success');
    expect(productStatusTone('SOLD_OUT')).toBe('warning');
    expect(productStatusTone('HIDDEN')).toBe('neutral');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(productStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('promotionStatusTone', () => {
  it('maps each known promotion status to its semantic tone', () => {
    expect(promotionStatusTone('ACTIVE')).toBe('success');
    expect(promotionStatusTone('SCHEDULED')).toBe('progress');
    expect(promotionStatusTone('ENDED')).toBe('neutral');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(promotionStatusTone('FUTURE')).toBe('neutral');
  });
});
