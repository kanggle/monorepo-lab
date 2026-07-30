import { describe, it, expect } from 'vitest';
import {
  financeAccountStatusTone,
  txnStatusTone,
} from '@/features/finance-ops/api/types';
import {
  ledgerPeriodStatusTone,
  discrepancyStatusTone,
} from '@/features/ledger-ops/api/types';

/**
 * TASK-PC-FE-159 — finance + ledger status → shared {@link StatusTone} maps,
 * rendered via the shared `<StatusBadge>`. Regulated states are surfaced
 * HONESTLY (§ 2.4.7): FAILED/REVERSED/FROZEN keep a danger tone, never hidden.
 * Unknown/future status → `neutral` (tolerant — never a crash).
 */

describe('financeAccountStatusTone (finance)', () => {
  it('maps each known account status to its semantic tone', () => {
    expect(financeAccountStatusTone('PENDING_KYC')).toBe('warning');
    expect(financeAccountStatusTone('ACTIVE')).toBe('success');
    expect(financeAccountStatusTone('RESTRICTED')).toBe('warning');
    expect(financeAccountStatusTone('FROZEN')).toBe('danger');
    expect(financeAccountStatusTone('CLOSED')).toBe('neutral');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(financeAccountStatusTone('FUTURE')).toBe('neutral');
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

describe('ledgerPeriodStatusTone (ledger)', () => {
  it('maps each known period status to its semantic tone', () => {
    expect(ledgerPeriodStatusTone('OPEN')).toBe('progress');
    expect(ledgerPeriodStatusTone('CLOSED')).toBe('success');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(ledgerPeriodStatusTone('FUTURE')).toBe('neutral');
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
