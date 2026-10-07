import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-312 — the tenant placement proxies + api functions
 * (`TASK-BE-625`, admin-api.md § «테넌트 소속»):
 *   - `app/api/tenants/[tenantId]/org-node/route.ts` (PUT)
 *   - `app/api/tenants/[tenantId]/org-node/preview/route.ts` (GET)
 *   - `previewTenantPlacement` / `placeTenant` (producer path, query, body).
 *
 * Part 1 mocks the api layer (route validation + error passthrough, the same
 * isolation as `tenants-proxy.test.ts`); part 2 mocks `callOrgNodes` and reads
 * what the api functions hand it.
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const callOrgNodes = vi.fn();
vi.mock('@/features/org-hierarchy/api/org-nodes-client', () => ({
  ORG_NODES_PREFIX: '/api/admin/org-nodes',
  callOrgNodes: (...a: unknown[]) => callOrgNodes(...a),
}));

import { PUT } from '@/app/api/tenants/[tenantId]/org-node/route';
import { GET as previewGET } from '@/app/api/tenants/[tenantId]/org-node/preview/route';
import { ApiError, OrgNodesUnavailableError } from '@/shared/api/errors';

const params = (tenantId: string) => ({ params: Promise.resolve({ tenantId }) });

function put(body: unknown) {
  return new Request('http://console.local/api/tenants/acme/org-node', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

const EFFECT = {
  tenantId: 'acme',
  fromOrgNodeId: 'n-a',
  toOrgNodeId: 'n-b',
  domainsBefore: ['wms', 'finance'],
  domainsAfter: ['wms'],
  lostDomains: ['finance'],
  gainedDomains: [],
};

beforeEach(() => {
  callOrgNodes.mockReset();
});

describe('PUT /api/tenants/[tenantId]/org-node', () => {
  it('sends { orgNodeId } as the producer body and the reason as X-Operator-Reason input', async () => {
    callOrgNodes.mockImplementation((_opts, parse) => parse({ ...EFFECT, changed: true }));
    const res = await PUT(put({ orgNodeId: 'n-b', reason: '개편' }), params('acme'));
    expect(res.status).toBe(200);
    expect((await res.json()).lostDomains).toEqual(['finance']);
    expect(callOrgNodes).toHaveBeenCalledTimes(1);
    const opts = callOrgNodes.mock.calls[0][0];
    expect(opts).toEqual({
      method: 'PUT',
      path: '/api/admin/tenants/acme/org-node',
      reason: '개편',
      body: { orgNodeId: 'n-b' },
    });
  });

  it('detach: orgNodeId null travels as an explicit null key', async () => {
    callOrgNodes.mockImplementation((_opts, parse) =>
      parse({ ...EFFECT, toOrgNodeId: null, lostDomains: [], changed: true }),
    );
    await PUT(put({ orgNodeId: null, reason: '분리' }), params('acme'));
    const body = callOrgNodes.mock.calls[0][0].body as Record<string, unknown>;
    expect(body).toHaveProperty('orgNodeId', null);
  });

  it('422 when the orgNodeId KEY is missing — a missing key must never mean «detach»', async () => {
    const res = await PUT(put({ reason: '분리' }), params('acme'));
    expect(res.status).toBe(422);
    expect(callOrgNodes).not.toHaveBeenCalled();
  });

  it('422 on an empty-string orgNodeId', async () => {
    const res = await PUT(put({ orgNodeId: '', reason: 'r' }), params('acme'));
    expect(res.status).toBe(422);
    expect(callOrgNodes).not.toHaveBeenCalled();
  });

  it.each([
    [404, 'TENANT_NOT_FOUND'],
    [404, 'ORG_NODE_NOT_FOUND'],
    [409, 'TENANT_ORG_NODE_CONFLICT'],
    [403, 'PERMISSION_DENIED'],
  ])('%s %s passes through verbatim', async (status, code) => {
    callOrgNodes.mockRejectedValue(new ApiError(status, code, 'x'));
    const res = await PUT(put({ orgNodeId: 'n-b', reason: 'r' }), params('acme'));
    expect(res.status).toBe(status);
    expect((await res.json()).code).toBe(code);
  });

  it('503 on OrgNodesUnavailableError', async () => {
    callOrgNodes.mockRejectedValue(
      new OrgNodesUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await PUT(put({ orgNodeId: 'n-b', reason: 'r' }), params('acme'));
    expect(res.status).toBe(503);
  });
});

describe('GET /api/tenants/[tenantId]/org-node/preview', () => {
  it('forwards the destination as ?orgNodeId= and returns the effect', async () => {
    callOrgNodes.mockImplementation((_opts, parse) => parse(EFFECT));
    const res = await previewGET(
      new Request('http://console.local/api/tenants/acme/org-node/preview?orgNodeId=n-b'),
      params('acme'),
    );
    expect(res.status).toBe(200);
    expect((await res.json()).lostDomains).toEqual(['finance']);
    expect(callOrgNodes.mock.calls[0][0]).toEqual({
      method: 'GET',
      path: '/api/admin/tenants/acme/org-node/preview?orgNodeId=n-b',
    });
  });

  it.each([
    ['absent', 'http://console.local/api/tenants/acme/org-node/preview'],
    ['empty', 'http://console.local/api/tenants/acme/org-node/preview?orgNodeId='],
  ])('detach (%s orgNodeId) omits the parameter upstream', async (_label, url) => {
    callOrgNodes.mockImplementation((_opts, parse) =>
      parse({ ...EFFECT, toOrgNodeId: null, lostDomains: [] }),
    );
    await previewGET(new Request(url), params('acme'));
    expect(callOrgNodes.mock.calls[0][0].path).toBe(
      '/api/admin/tenants/acme/org-node/preview',
    );
  });

  it('carries no reason (read path)', async () => {
    callOrgNodes.mockImplementation((_opts, parse) => parse(EFFECT));
    await previewGET(
      new Request('http://console.local/api/tenants/acme/org-node/preview?orgNodeId=n-b'),
      params('acme'),
    );
    expect(callOrgNodes.mock.calls[0][0]).not.toHaveProperty('reason');
  });

  it('404 passes through (the screen, not the proxy, chooses the copy)', async () => {
    callOrgNodes.mockRejectedValue(new ApiError(404, 'TENANT_NOT_FOUND', 'x'));
    const res = await previewGET(
      new Request('http://console.local/api/tenants/acme/org-node/preview?orgNodeId=n-b'),
      params('acme'),
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('TENANT_NOT_FOUND');
  });
});
