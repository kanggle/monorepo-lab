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
  it('201 with the producer result on a valid body', async () => {
    createTenant.mockResolvedValue(TENANT);
    const res = await createPOST(
      post({
        tenantId: 'acme-corp',
        displayName: 'ACME Corp',
        tenantType: 'B2B_ENTERPRISE',
        reason: '신규 온보딩',
      }),
    );
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual(TENANT);
    expect(createTenant).toHaveBeenCalledWith(
      { tenantId: 'acme-corp', displayName: 'ACME Corp', tenantType: 'B2B_ENTERPRISE' },
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

  it('422 on an invalid tenantType', async () => {
    const res = await createPOST(
      post({ tenantId: 'acme-corp', displayName: 'ACME', tenantType: 'BOGUS', reason: 'r' }),
    );
    expect(res.status).toBe(422);
    expect(createTenant).not.toHaveBeenCalled();
  });

  it('400 TENANT_ID_RESERVED passes through', async () => {
    createTenant.mockRejectedValue(
      new ApiError(400, 'TENANT_ID_RESERVED', 'reserved'),
    );
    const res = await createPOST(
      post({ tenantId: 'admin', displayName: 'X', tenantType: 'B2C_CONSUMER', reason: 'r' }),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('TENANT_ID_RESERVED');
  });

  it('409 TENANT_ALREADY_EXISTS passes through', async () => {
    createTenant.mockRejectedValue(
      new ApiError(409, 'TENANT_ALREADY_EXISTS', 'exists'),
    );
    const res = await createPOST(
      post({ tenantId: 'acme-corp', displayName: 'X', tenantType: 'B2C_CONSUMER', reason: 'r' }),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('TENANT_ALREADY_EXISTS');
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
