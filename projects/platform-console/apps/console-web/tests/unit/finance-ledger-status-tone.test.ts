import { describe, it, expect } from 'vitest';
import {
  accountStatusTone,
  txnStatusTone,
} from '@/features/finance-ops/api/types';
import {
  periodStatusTone,
  discrepancyStatusTone,
} from '@/features/ledger-ops/api/types';

/**
 * TASK-PC-FE-159 — finance + ledger status → shared {@link StatusTone} maps,
 * rendered via the shared `<StatusBadge>`. Regulated states are surfaced
 * HONESTLY (§ 2.4.7): FAILED/REVERSED/FROZEN keep a danger tone, never hidden.
 * Unknown/future status → `neutral` (tolerant — never a crash).
 */

describe('accountStatusTone (finance)', () => {
  it('maps each known account status to its semantic tone', () => {
    expect(accountStatusTone('PENDING_KYC')).toBe('warning');
    expect(accountStatusTone('ACTIVE')).toBe('success');
    expect(accountStatusTone('RESTRICTED')).toBe('warning');
    expect(accountStatusTone('FROZEN')).toBe('danger');
    expect(accountStatusTone('CLOSED')).toBe('neutral');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(accountStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('txnStatusTone (finance)', () => {
  it('maps each known txn status to its semantic tone', () => {
    expect(txnStatusTone('PENDING')).toBe('warning');
    expect(txnStatusTone('COMPLETED')).toBe('success');
    expect(txnStatusTone('FAILED')).toBe('danger');
    expect(txnStatusTone('REVERSED')).toBe('danger');
    expect(txnStatusTone('CAPTURED')).toBe('progress');
    expect(txnStatusTone('RELEASED')).toBe('neutral');
    expect(txnStatusTone('ACTIVE')).toBe('progress');
    expect(txnStatusTone('SETTLED')).toBe('success');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(txnStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('periodStatusTone (ledger)', () => {
  it('maps each known period status to its semantic tone', () => {
    expect(periodStatusTone('OPEN')).toBe('progress');
    expect(periodStatusTone('CLOSED')).toBe('success');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(periodStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('discrepancyStatusTone (ledger)', () => {
  it('maps each known discrepancy status to its semantic tone', () => {
    expect(discrepancyStatusTone('OPEN')).toBe('warning');
    expect(discrepancyStatusTone('RESOLVED')).toBe('success');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(discrepancyStatusTone('FUTURE')).toBe('neutral');
  });
});
