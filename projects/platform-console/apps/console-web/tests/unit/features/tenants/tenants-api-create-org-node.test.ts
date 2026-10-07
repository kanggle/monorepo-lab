import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-312 AC-4 at the producer edge — `createTenant` builds the
 * `POST /api/admin/tenants` body. A chosen node adds `orgNodeId`; no node keeps
 * the pre-312 three-field body byte-for-byte (regression).
 */

const callGapTenants = vi.fn();
vi.mock('@/features/tenants/api/tenants-client', () => ({
  TENANTS_PREFIX: '/api/admin/tenants',
  callGapTenants: (...a: unknown[]) => callGapTenants(...a),
}));

import { createTenant } from '@/features/tenants/api/tenants-api';

const TENANT = {
  tenantId: 'acme',
  displayName: 'ACME',
  tenantType: 'B2B_ENTERPRISE',
  status: 'ACTIVE',
  createdAt: '2026-10-08T00:00:00Z',
  updatedAt: '2026-10-08T00:00:00Z',
};

beforeEach(() => {
  callGapTenants.mockReset();
  callGapTenants.mockImplementation((_opts, parse) => parse(TENANT));
});

describe('createTenant — orgNodeId (TASK-PC-FE-312 / TASK-BE-625)', () => {
  it('a chosen node rides in the producer body', async () => {
    await createTenant(
      { tenantId: 'acme', displayName: 'ACME', tenantType: 'B2B_ENTERPRISE', orgNodeId: 'n-1' },
      '온보딩',
      'idem-1',
    );
    expect(callGapTenants.mock.calls[0][0]).toEqual({
      method: 'POST',
      path: '/api/admin/tenants',
      reason: '온보딩',
      idempotencyKey: 'idem-1',
      body: {
        tenantId: 'acme',
        displayName: 'ACME',
        tenantType: 'B2B_ENTERPRISE',
        orgNodeId: 'n-1',
      },
    });
  });

  it('regression — no node: the body is exactly the three pre-312 fields', async () => {
    await createTenant(
      { tenantId: 'acme', displayName: 'ACME', tenantType: 'B2B_ENTERPRISE' },
      '온보딩',
    );
    const body = callGapTenants.mock.calls[0][0].body as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(['displayName', 'tenantId', 'tenantType']);
  });

  it('an empty-string node is treated as «none» (never sent as "")', async () => {
    await createTenant(
      { tenantId: 'acme', displayName: 'ACME', tenantType: 'B2B_ENTERPRISE', orgNodeId: '' },
      '온보딩',
    );
    const body = callGapTenants.mock.calls[0][0].body as Record<string, unknown>;
    expect(body).not.toHaveProperty('orgNodeId');
  });
});
