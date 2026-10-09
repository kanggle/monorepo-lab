import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-771 S5 — the entry-policy BFF routes + api functions
 * (console-integration-contract § 2.4.3.3; admin-api.md § Tenant Entry Policy):
 *   - `app/api/tenants/[tenantId]/entry-policy/route.ts` (GET, PUT)
 *   - `app/api/tenants/[tenantId]/entry-policy/enrolment-summary/route.ts` (GET)
 * `callEntryPolicy` is mocked: what the api functions hand the gateway core
 * (method · path · reason · body — and NO idempotency key) is asserted.
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const callEntryPolicy = vi.fn();
vi.mock('@/features/tenant-entry-policy/api/entry-policy-client', () => ({
  ENTRY_POLICY_PATH: (t: string) => `/api/admin/tenants/${encodeURIComponent(t)}/entry-policy`,
  callEntryPolicy: (...a: unknown[]) => callEntryPolicy(...a),
}));

import { GET, PUT } from '@/app/api/tenants/[tenantId]/entry-policy/route';
import { GET as summaryGET } from '@/app/api/tenants/[tenantId]/entry-policy/enrolment-summary/route';
import { ApiError, TenantsUnavailableError } from '@/shared/api/errors';

const params = (tenantId: string) => ({ params: Promise.resolve({ tenantId }) });
const POLICY = { tenantId: 'acme', requireMfa: true, updatedAt: '2026-10-09T00:00:00Z', updatedBy: 'op' };

function put(body: unknown) {
  return new Request('http://console.local/api/tenants/acme/entry-policy', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  callEntryPolicy.mockReset();
});

describe('PUT /api/tenants/[tenantId]/entry-policy', () => {
  it('🔴 sends { requireMfa } as the producer body, the reason as X-Operator-Reason input, NO idempotency key', async () => {
    callEntryPolicy.mockImplementation((_o, parse) => parse(POLICY));
    const res = await PUT(put({ requireMfa: true, reason: '회사 정책' }), params('acme'));
    expect(res.status).toBe(200);
    expect((await res.json()).requireMfa).toBe(true);
    const opts = callEntryPolicy.mock.calls[0][0];
    expect(opts).toEqual({
      method: 'PUT',
      path: '/api/admin/tenants/acme/entry-policy',
      reason: '회사 정책',
      body: { requireMfa: true },
    });
    expect(opts).not.toHaveProperty('idempotencyKey');
  });

  it.each([
    ['missing requireMfa', { reason: 'r' }],
    ['string requireMfa', { requireMfa: 'true', reason: 'r' }],
    ['missing reason', { requireMfa: true }],
    ['extra key', { requireMfa: true, reason: 'r', idempotencyKey: 'k' }],
  ])('422 on %s — never forwarded', async (_label, body) => {
    const res = await PUT(put(body), params('acme'));
    expect(res.status).toBe(422);
    expect(callEntryPolicy).not.toHaveBeenCalled();
  });

  it.each([
    [403, 'PERMISSION_DENIED'],
    [403, 'TENANT_SCOPE_DENIED'],
    [400, 'REASON_REQUIRED'],
    [400, 'VALIDATION_ERROR'],
    [404, 'TENANT_NOT_FOUND'],
    [409, 'OPTIMISTIC_LOCK_CONFLICT'],
  ])('%s %s passes through verbatim', async (status, code) => {
    callEntryPolicy.mockRejectedValue(new ApiError(status, code, 'x'));
    const res = await PUT(put({ requireMfa: true, reason: 'r' }), params('acme'));
    expect(res.status).toBe(status);
    expect((await res.json()).code).toBe(code);
  });

  it('401 → 401 (forced re-login) · 503 TenantsUnavailableError → 503', async () => {
    callEntryPolicy.mockRejectedValueOnce(new ApiError(401, 'TOKEN_INVALID', 'x'));
    expect((await PUT(put({ requireMfa: true, reason: 'r' }), params('acme'))).status).toBe(401);
    callEntryPolicy.mockRejectedValueOnce(new TenantsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'));
    expect((await PUT(put({ requireMfa: true, reason: 'r' }), params('acme'))).status).toBe(503);
  });
});

describe('GET /api/tenants/[tenantId]/entry-policy', () => {
  it('reads with no reason (read path) and encodes the tenant id', async () => {
    callEntryPolicy.mockImplementation((_o, parse) => parse({ ...POLICY, tenantId: 'a b' }));
    const res = await GET(new Request('http://console.local/x'), params('a b'));
    expect(res.status).toBe(200);
    expect(callEntryPolicy.mock.calls[0][0]).toEqual({
      method: 'GET',
      path: '/api/admin/tenants/a%20b/entry-policy',
    });
  });

  it('rejects a response that breaks the producer shape (zod) instead of rendering it', async () => {
    callEntryPolicy.mockImplementation((_o, parse) => parse({ tenantId: 'acme', requireMfa: 'yes' }));
    const res = await GET(new Request('http://console.local/x'), params('acme'));
    expect(res.status).toBe(503);
  });
});

describe('GET /api/tenants/[tenantId]/entry-policy/enrolment-summary', () => {
  it('forwards to the producer pre-check path and returns the counts', async () => {
    const SUMMARY = { tenantId: 'acme', operators: 3, enrolled: 1, notEnrolled: 1, unlinked: 1 };
    callEntryPolicy.mockImplementation((_o, parse) => parse(SUMMARY));
    const res = await summaryGET(new Request('http://console.local/x'), params('acme'));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(SUMMARY);
    expect(callEntryPolicy.mock.calls[0][0]).toEqual({
      method: 'GET',
      path: '/api/admin/tenants/acme/entry-policy/enrolment-summary',
    });
  });

  it('🔴 a producer 503 stays a 503 — the proxy never substitutes a count', async () => {
    callEntryPolicy.mockRejectedValue(new TenantsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'));
    const res = await summaryGET(new Request('http://console.local/x'), params('acme'));
    expect(res.status).toBe(503);
    expect(await res.json()).not.toHaveProperty('notEnrolled');
  });
});
