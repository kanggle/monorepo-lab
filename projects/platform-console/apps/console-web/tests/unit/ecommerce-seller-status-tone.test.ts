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
  it('maps each known status to a distinct label + className', () => {
    for (const s of SELLER_STATUS_VALUES) {
      const t = sellerStatusTone(s);
      expect(t.label).toBe(s);
      expect(t.className).toMatch(/\S/);
    }
    // ACTIVE stays green (byte-preserved from the v1 badge).
    expect(sellerStatusTone('ACTIVE').className).toContain('green');
  });

  it('renders an unknown/future status with a neutral tone and the raw label (no crash)', () => {
    const t = sellerStatusTone('FUTURE_STATE');
    expect(t.label).toBe('FUTURE_STATE');
    expect(t.className).toContain('muted');
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
