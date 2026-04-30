import { describe, it, expect } from 'vitest';
import {
  LockResponseSchema,
  UnlockResponseSchema,
  RevokeResponseSchema,
  AuditPageSchema,
} from '@/shared/api/admin-api';

// Sample payloads taken verbatim from specs/contracts/http/admin-api.md.

describe('admin-api contract schemas', () => {
  it('parses a LockResponse matching the spec', () => {
    const sample = {
      accountId: 'acc-123',
      previousStatus: 'ACTIVE',
      currentStatus: 'LOCKED',
      operatorId: 'op-1',
      lockedAt: '2026-04-12T10:00:00Z',
      auditId: 'aud-9',
    };
    expect(LockResponseSchema.parse(sample)).toEqual(sample);
  });

  it('parses an UnlockResponse matching the spec', () => {
    const sample = {
      accountId: 'acc-123',
      previousStatus: 'LOCKED',
      currentStatus: 'ACTIVE',
      operatorId: 'op-1',
      unlockedAt: '2026-04-12T10:00:00Z',
      auditId: 'aud-10',
    };
    expect(UnlockResponseSchema.parse(sample)).toEqual(sample);
  });

  it('parses a RevokeResponse matching the spec', () => {
    const sample = {
      accountId: 'acc-123',
      revokedSessionCount: 3,
      operatorId: 'op-1',
      revokedAt: '2026-04-12T10:00:00Z',
      auditId: 'aud-11',
    };
    expect(RevokeResponseSchema.parse(sample)).toEqual(sample);
  });

  it('parses an AuditPage with mixed sources (admin + login_history)', () => {
    const sample = {
      content: [
        {
          source: 'admin',
          auditId: 'a1',
          actionCode: 'ACCOUNT_LOCK',
          operatorId: 'op-1',
          targetId: 'acc-123',
          reason: 'abuse',
          outcome: 'SUCCESS',
          occurredAt: '2026-04-12T10:00:00Z',
        },
        {
          source: 'login_history',
          eventId: 'e1',
          accountId: 'acc-123',
          outcome: 'FAILURE',
          ipMasked: '192.168.1.***',
          geoCountry: 'KR',
          occurredAt: '2026-04-12T09:58:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 150,
      totalPages: 8,
    };
    const parsed = AuditPageSchema.parse(sample);
    expect(parsed.content).toHaveLength(2);
    expect(parsed.content[0].source).toBe('admin');
    expect(parsed.content[1].source).toBe('login_history');
  });
});
