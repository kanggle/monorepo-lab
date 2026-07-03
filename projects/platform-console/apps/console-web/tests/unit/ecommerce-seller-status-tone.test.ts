import { describe, it, expect } from 'vitest';
import {
  SELLER_STATUS_VALUES,
  sellerStatusTone,
  sellerActionsFor,
} from '@/features/ecommerce-ops/api/seller-types';

/**
 * TASK-PC-FE-154 — seller status tone + lifecycle-action gating (ADR-MONO-042).
 * The seller domain has no update/delete (CRUD); its mutation surface is the
 * state-transition set, which the detail UI derives from the current status.
 */

describe('SELLER_STATUS_VALUES', () => {
  it('covers the full ADR-042 lifecycle', () => {
    expect(SELLER_STATUS_VALUES).toEqual([
      'PENDING_PROVISIONING',
      'ACTIVE',
      'SUSPENDED',
      'CLOSED',
    ]);
  });
});

describe('sellerStatusTone', () => {
  it('maps each known status to its semantic StatusBadge tone (TASK-PC-FE-158)', () => {
    // ACTIVE stays "success" = green in the shared palette (byte-preserved
    // from the v1 badge); the rest follow the lifecycle semantics.
    expect(sellerStatusTone('ACTIVE')).toBe('success');
    expect(sellerStatusTone('PENDING_PROVISIONING')).toBe('warning');
    expect(sellerStatusTone('SUSPENDED')).toBe('neutral');
    expect(sellerStatusTone('CLOSED')).toBe('danger');
  });

  it('maps an unknown/future status to the neutral tone (no crash)', () => {
    expect(sellerStatusTone('FUTURE_STATE')).toBe('neutral');
  });
});

describe('sellerActionsFor', () => {
  it('PENDING_PROVISIONING → [provision]', () => {
    expect(sellerActionsFor('PENDING_PROVISIONING')).toEqual(['provision']);
  });
  it('ACTIVE → [suspend, close]', () => {
    expect(sellerActionsFor('ACTIVE')).toEqual(['suspend', 'close']);
  });
  it('SUSPENDED → [close] (no producer reactivation path)', () => {
    expect(sellerActionsFor('SUSPENDED')).toEqual(['close']);
  });
  it('CLOSED (terminal) → no actions', () => {
    expect(sellerActionsFor('CLOSED')).toEqual([]);
  });
  it('unknown status → no actions', () => {
    expect(sellerActionsFor('FUTURE_STATE')).toEqual([]);
  });
});
