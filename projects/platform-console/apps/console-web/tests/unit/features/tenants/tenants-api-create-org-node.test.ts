import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-312 AC-4 at the producer edge — `createTenant` builds the
 * `POST /api/admin/tenants` body. A chosen node adds `orgNodeId`; no node keeps
 * the pre-312 body byte-for-byte (regression) — which, since TASK-MONO-773 S4,
 * includes the required `firstAdmin`.
 */

const callGapTenants = vi.fn();
vi.mock('@/features/tenants/api/tenants-client', () => ({
  TENANTS_PREFIX: '/api/admin/tenants',
  callGapTenants: (...a: unknown[]) => callGapTenants(...a),
}));

import { createTenant } from '@/features/tenants/api/tenants-api';

/** TASK-MONO-773 S4 — every create carries the required first admin. */
const FIRST_ADMIN = { email: 'owner@acme.example', displayName: '홍길동' };

/** Typed through `createTenant`'s own parameter, so this file compiles before and after S4 widens the input. */
const input = (o: Record<string, unknown>) => o as unknown as Parameters<typeof createTenant>[0];

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
      input({
        tenantId: 'acme',
        displayName: 'ACME',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        orgNodeId: 'n-1',
      }),
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
        firstAdmin: FIRST_ADMIN,
        orgNodeId: 'n-1',
      },
    });
  });

  it('regression — no node: the body is exactly the pre-312 fields + TASK-MONO-773 S4 firstAdmin', async () => {
    await createTenant(
      input({
        tenantId: 'acme',
        displayName: 'ACME',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
      }),
      '온보딩',
    );
    const body = callGapTenants.mock.calls[0][0].body as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(['displayName', 'firstAdmin', 'tenantId', 'tenantType']);
    expect(body.firstAdmin).toEqual(FIRST_ADMIN);
  });

  it('an empty-string node is treated as «none» (never sent as "")', async () => {
    await createTenant(
      input({
        tenantId: 'acme',
        displayName: 'ACME',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        orgNodeId: '',
      }),
      '온보딩',
    );
    const body = callGapTenants.mock.calls[0][0].body as Record<string, unknown>;
    expect(body).not.toHaveProperty('orgNodeId');
  });
});
