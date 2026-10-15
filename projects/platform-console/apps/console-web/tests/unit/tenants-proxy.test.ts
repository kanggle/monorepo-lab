import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin tenant-management proxy routes
 * (`app/api/tenants/route.ts` GET+POST + `.../[tenantId]/route.ts`
 * GET+PATCH). The api layer (listTenants / getTenant / createTenant /
 * updateTenant) is mocked; body/param validation + error mapping are
 * asserted in isolation (mirrors `subscriptions-proxy.test.ts` /
 * `operators-proxy.test.ts`).
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const listTenants = vi.fn();
const getTenant = vi.fn();
const createTenant = vi.fn();
const updateTenant = vi.fn();
vi.mock('@/features/tenants/api/tenants-api', () => ({
  listTenants: (...a: unknown[]) => listTenants(...a),
  getTenant: (...a: unknown[]) => getTenant(...a),
  createTenant: (...a: unknown[]) => createTenant(...a),
  updateTenant: (...a: unknown[]) => updateTenant(...a),
}));

import { GET as listGET, POST as createPOST } from '@/app/api/tenants/route';
import {
  GET as detailGET,
  PATCH as updatePATCH,
} from '@/app/api/tenants/[tenantId]/route';
import { ApiError, TenantsUnavailableError } from '@/shared/api/errors';

function get(url: string) {
  return new Request(url);
}
function post(body: unknown) {
  return new Request('http://console.local/api/tenants', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
function patch(body: unknown) {
  return new Request('http://console.local/api/tenants/acme-corp', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
const params = (tenantId: string) => ({ params: Promise.resolve({ tenantId }) });

const TENANT = {
  tenantId: 'acme-corp',
  displayName: 'ACME Corp',
  tenantType: 'B2B_ENTERPRISE',
  status: 'ACTIVE',
  createdAt: '2026-07-08T00:00:00Z',
  updatedAt: '2026-07-08T00:00:00Z',
};

/** TASK-MONO-773 S4 — `firstAdmin` is required by IAM `POST /api/admin/tenants` (admin-api.md, breaking). */
const FIRST_ADMIN = { email: 'owner@acme.example', displayName: '홍길동' };

beforeEach(() => {
  listTenants.mockReset();
  getTenant.mockReset();
  createTenant.mockReset();
  updateTenant.mockReset();
});

describe('GET /api/tenants (list)', () => {
  it('200 with the producer page', async () => {
    listTenants.mockResolvedValue({
      items: [TENANT],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    const res = await listGET(get('http://console.local/api/tenants?page=0&size=20'));
    expect(res.status).toBe(200);
    expect((await res.json()).items).toEqual([TENANT]);
  });

  it('403 TENANT_SCOPE_DENIED (not SUPER_ADMIN) passes through', async () => {
    listTenants.mockRejectedValue(
      new ApiError(403, 'TENANT_SCOPE_DENIED', 'no'),
    );
    const res = await listGET(get('http://console.local/api/tenants'));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('TENANT_SCOPE_DENIED');
  });

  it('503 on TenantsUnavailableError', async () => {
    listTenants.mockRejectedValue(
      new TenantsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await listGET(get('http://console.local/api/tenants'));
    expect(res.status).toBe(503);
  });
});

describe('POST /api/tenants (create)', () => {
  it('201 with the producer result on a valid body (TASK-MONO-773 S4 — firstAdmin rides to the api layer)', async () => {
    createTenant.mockResolvedValue(TENANT);
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        reason: '신규 온보딩',
      }),
    );
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual(TENANT);
    expect(createTenant).toHaveBeenCalledWith(
      {
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
      },
      '신규 온보딩',
      undefined,
    );
  });

  it('forwards an idempotencyKey when supplied', async () => {
    createTenant.mockResolvedValue(TENANT);
    await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
        idempotencyKey: 'idem-1',
      }),
    );
    expect(createTenant).toHaveBeenCalledWith(
      expect.anything(),
      'r',
      'idem-1',
    );
  });

  it('TASK-PC-FE-312 — forwards a chosen orgNodeId to the api layer', async () => {
    createTenant.mockResolvedValue(TENANT);
    await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
        orgNodeId: 'node-1',
      }),
    );
    expect(createTenant).toHaveBeenCalledWith(
      {
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        orgNodeId: 'node-1',
      },
      'r',
      undefined,
    );
  });

  it('TASK-PC-FE-312 — orgNodeId: null stays ABSENT downstream (무소속 = the pre-312 create)', async () => {
    createTenant.mockResolvedValue(TENANT);
    await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
        orgNodeId: null,
      }),
    );
    expect(createTenant).toHaveBeenCalledTimes(1);
    const input = createTenant.mock.calls[0][0] as Record<string, unknown>;
    expect(input).not.toHaveProperty('orgNodeId');
  });

  it('TASK-PC-FE-312 — 404 ORG_NODE_NOT_FOUND on a create with a node passes through', async () => {
    createTenant.mockRejectedValue(new ApiError(404, 'ORG_NODE_NOT_FOUND', 'no'));
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'X',
        tenantType: 'B2C_CONSUMER',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
        orgNodeId: 'node-x',
      }),
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('ORG_NODE_NOT_FOUND');
  });

  it('422 on an invalid tenantType', async () => {
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME',
        tenantType: 'BOGUS',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
      }),
    );
    expect(res.status).toBe(422);
    expect(createTenant).not.toHaveBeenCalled();
  });

  it('400 TENANT_ID_RESERVED passes through', async () => {
    createTenant.mockRejectedValue(
      new ApiError(400, 'TENANT_ID_RESERVED', 'reserved'),
    );
    const res = await createPOST(
      post({
        tenantId: 'admin',
        displayName: 'X',
        tenantType: 'B2C_CONSUMER',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
      }),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('TENANT_ID_RESERVED');
  });

  it('409 TENANT_ALREADY_EXISTS passes through', async () => {
    createTenant.mockRejectedValue(
      new ApiError(409, 'TENANT_ALREADY_EXISTS', 'exists'),
    );
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'X',
        tenantType: 'B2C_CONSUMER',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
      }),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('TENANT_ALREADY_EXISTS');
  });
});

describe('TASK-MONO-773 S4 — POST /api/tenants firstAdmin · PENDING', () => {
  it('a body WITHOUT firstAdmin → 422 (IAM would 400 — the proxy fails fast) · nothing forwarded', async () => {
    const res = await createPOST(
      post({ tenantId: 'acme-corp', displayName: 'ACME', tenantType: 'B2B_ENTERPRISE', reason: 'r' }),
    );
    expect(res.status).toBe(422);
    expect(createTenant).not.toHaveBeenCalled();
  });

  it.each([
    ['email missing', { displayName: '홍길동' }],
    ['email not an address', { email: 'not-an-email', displayName: '홍길동' }],
    ['displayName empty', { email: 'owner@acme.example', displayName: '' }],
    ['displayName over 64', { email: 'owner@acme.example', displayName: '가'.repeat(65) }],
    ['an unknown key', { email: 'owner@acme.example', displayName: '홍길동', roles: ['SUPER_ADMIN'] }],
  ])('firstAdmin with %s → 422 · nothing forwarded', async (_label, firstAdmin) => {
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME',
        tenantType: 'B2B_ENTERPRISE',
        firstAdmin,
        reason: 'r',
      }),
    );
    expect(res.status).toBe(422);
    expect(createTenant).not.toHaveBeenCalled();
  });

  it('the producer 201 (status PENDING + firstAdmin summary + firstAdminInvitation) passes through as-is', async () => {
    const created = {
      ...TENANT,
      status: 'PENDING',
      firstAdmin: {
        state: 'INVITED',
        email: 'owner@acme.example',
        invitationId: 'inv-1',
        expiresAt: '2026-10-22T09:00:00Z',
      },
    };
    createTenant.mockResolvedValue(created);
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2C_CONSUMER',
        firstAdmin: FIRST_ADMIN,
        reason: 'r',
      }),
    );
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual(created);
  });

  it('GET ?status=PENDING forwards the PENDING filter (admin-api § GET /api/admin/tenants)', async () => {
    listTenants.mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await listGET(get('http://console.local/api/tenants?status=PENDING'));
    expect(listTenants).toHaveBeenCalledWith(expect.objectContaining({ status: 'PENDING' }));
  });

  it('control — PATCH status=PENDING stays a 422 (PENDING is never a request value — only POST makes it)', async () => {
    const res = await updatePATCH(patch({ status: 'PENDING', reason: 'r' }), params('acme-corp'));
    expect(res.status).toBe(422);
    expect(updateTenant).not.toHaveBeenCalled();
  });
});

describe('GET /api/tenants/[tenantId] (detail)', () => {
  it('200 with the producer tenant', async () => {
    getTenant.mockResolvedValue(TENANT);
    const res = await detailGET(get('http://console.local/api/tenants/acme-corp'), params('acme-corp'));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(TENANT);
    expect(getTenant).toHaveBeenCalledWith('acme-corp');
  });

  it('404 TENANT_NOT_FOUND passes through', async () => {
    getTenant.mockRejectedValue(new ApiError(404, 'TENANT_NOT_FOUND', 'missing'));
    const res = await detailGET(get('http://console.local/api/tenants/missing'), params('missing'));
    expect(res.status).toBe(404);
  });
});

describe('PATCH /api/tenants/[tenantId] (update)', () => {
  it('200 on a valid partial update (status only)', async () => {
    updateTenant.mockResolvedValue({ ...TENANT, status: 'SUSPENDED' });
    const res = await updatePATCH(
      patch({ status: 'SUSPENDED', reason: '점검' }),
      params('acme-corp'),
    );
    expect(res.status).toBe(200);
    expect(updateTenant).toHaveBeenCalledWith(
      'acme-corp',
      { displayName: undefined, status: 'SUSPENDED' },
      '점검',
    );
  });

  it('422 when neither displayName nor status is present', async () => {
    const res = await updatePATCH(patch({ reason: 'r' }), params('acme-corp'));
    expect(res.status).toBe(422);
    expect(updateTenant).not.toHaveBeenCalled();
  });

  it('422 on an invalid status enum', async () => {
    const res = await updatePATCH(
      patch({ status: 'BOGUS', reason: 'r' }),
      params('acme-corp'),
    );
    expect(res.status).toBe(422);
    expect(updateTenant).not.toHaveBeenCalled();
  });

  it('404 TENANT_NOT_FOUND passes through', async () => {
    updateTenant.mockRejectedValue(new ApiError(404, 'TENANT_NOT_FOUND', 'missing'));
    const res = await updatePATCH(
      patch({ displayName: 'New name', reason: 'r' }),
      params('missing'),
    );
    expect(res.status).toBe(404);
  });

  it('503 on TenantsUnavailableError', async () => {
    updateTenant.mockRejectedValue(
      new TenantsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await updatePATCH(
      patch({ displayName: 'New name', reason: 'r' }),
      params('acme-corp'),
    );
    expect(res.status).toBe(503);
  });
});
