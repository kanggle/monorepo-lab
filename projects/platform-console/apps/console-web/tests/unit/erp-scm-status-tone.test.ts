import { describe, it, expect } from 'vitest';
import {
  approvalStatusTone,
} from '@/features/erp-ops/api/approval-types';
import {
  masterStatusTone,
  employmentStatusTone,
} from '@/features/erp-ops/api/types';
import {
  poStatusTone,
  stalenessTone,
} from '@/features/scm-ops/components/scm-ops-helpers';
import { suggestionStatusTone } from '@/features/scm-replenishment/api/types';

/**
 * TASK-PC-FE-159 — erp + scm status → shared {@link StatusTone} maps, rendered
 * via the shared `<StatusBadge>` (erp approval / stage keep their own span via
 * `statusToneClass` for the data-* attributes). Honest surfacing: RETIRED /
 * SEPARATED / DISMISSED stay visible with a neutral tone, CANCELED / REJECTED
 * a danger tone. Unknown/future/absent status → `neutral` (tolerant).
 */

describe('approvalStatusTone (erp)', () => {
  it('maps each known approval status to its semantic tone', () => {
    expect(approvalStatusTone('DRAFT')).toBe('neutral');
    expect(approvalStatusTone('SUBMITTED')).toBe('warning');
    expect(approvalStatusTone('IN_REVIEW')).toBe('progress');
    expect(approvalStatusTone('APPROVED')).toBe('success');
    expect(approvalStatusTone('REJECTED')).toBe('danger');
    expect(approvalStatusTone('WITHDRAWN')).toBe('neutral');
  });
  it('maps an unknown/future status to neutral', () => {
    expect(approvalStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('masterStatusTone (erp)', () => {
  it('maps ACTIVE→success and honestly surfaces RETIRED→neutral', () => {
    expect(masterStatusTone('ACTIVE')).toBe('success');
    expect(masterStatusTone('RETIRED')).toBe('neutral');
    expect(masterStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('employmentStatusTone (erp)', () => {
  it('maps each known employment status; absent → neutral', () => {
    expect(employmentStatusTone('EMPLOYED')).toBe('success');
    expect(employmentStatusTone('ON_LEAVE')).toBe('warning');
    expect(employmentStatusTone('SEPARATED')).toBe('neutral');
    expect(employmentStatusTone(undefined)).toBe('neutral');
    expect(employmentStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('poStatusTone (scm)', () => {
  it('maps the PO lifecycle to semantic tones; unknown/absent → neutral', () => {
    expect(poStatusTone('DRAFT')).toBe('neutral');
    expect(poStatusTone('SUBMITTED')).toBe('warning');
    expect(poStatusTone('CONFIRMED')).toBe('progress');
    expect(poStatusTone('RECEIVED')).toBe('success');
    expect(poStatusTone('SETTLED')).toBe('success');
    expect(poStatusTone('CANCELED')).toBe('danger');
    expect(poStatusTone(undefined)).toBe('neutral');
    expect(poStatusTone('FUTURE')).toBe('neutral');
  });
});

describe('stalenessTone (scm)', () => {
  it('maps node freshness to semantic tones; unknown/absent → neutral', () => {
    expect(stalenessTone('FRESH')).toBe('success');
    expect(stalenessTone('STALE')).toBe('warning');
    expect(stalenessTone('UNREACHABLE')).toBe('danger');
    expect(stalenessTone('UNKNOWN')).toBe('neutral');
    expect(stalenessTone(undefined)).toBe('neutral');
  });
});

describe('suggestionStatusTone (scm replenishment)', () => {
  it('maps each known suggestion status; unknown/absent → neutral', () => {
    expect(suggestionStatusTone('SUGGESTED')).toBe('warning');
    expect(suggestionStatusTone('APPROVED')).toBe('progress');
    expect(suggestionStatusTone('MATERIALIZED')).toBe('success');
    expect(suggestionStatusTone('DISMISSED')).toBe('neutral');
    expect(suggestionStatusTone(undefined)).toBe('neutral');
    expect(suggestionStatusTone('FUTURE')).toBe('neutral');
  });
});
