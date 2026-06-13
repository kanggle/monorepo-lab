import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin proxy route handler at
 * `/api/console/dashboards/operator-overview` — outbound-header
 * invariants for the Option (a) activation (TASK-PC-FE-014 /
 * `console-integration-contract.md § 2.4.9.1 Implementation guidance
 * — Option (a) activation`).
 *
 * Asserts:
 *   - the existing 3 headers (Authorization / X-Operator-Token /
 *     X-Tenant-Id) continue to be forwarded server-side from the
 *     HttpOnly cookie session (regression guard — AC-2);
 *   - the new optional 4th header `X-Finance-Default-Account-Id` is
 *     forwarded ONLY when `getFinanceDefaultAccountId()` returns a
 *     non-blank value (AC-3 happy path);
 *   - the new header is OMITTED entirely (not set to "" / null) when
 *     `getFinanceDefaultAccountId()` returns null (header-absent path
 *     preserves the existing BFF MISSING_PREREQUISITE behavior — AC-2);
 *   - defensive: if a future helper variant ever returned an empty
 *     string, the proxy still omits the header (`if (truthy)` gate).
 *
 * The proxy is server-only; the new header NEVER appears on the
 * inbound (browser → proxy) request — the browser has no JS path to
 * the value (Failure Scenario "header forwarded from client component").
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

vi.mock('@/shared/lib/finance-default-account-id', () => ({
  getFinanceDefaultAccountId: vi.fn(),
}));

import { GET as overviewProxyGET } from '@/app/api/console/dashboards/operator-overview/route';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';
import { getFinanceDefaultAccountId } from '@/shared/lib/finance-default-account-id';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const HAPPY_ENVELOPE = {
  asOf: '2026-05-21T01:30:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { totalElements: 1 } },
    {
      domain: 'wms',
      status: 'ok',
      data: { inventorySnapshot: { totalStockUnits: 1, alertCount: 0 } },
    },
    { domain: 'scm', status: 'ok', data: { nodes: [] } },
    {
      domain: 'finance',
      status: 'ok',
      data: { balance: { amount: '0', currency: 'KRW' } },
    },
    { domain: 'erp', status: 'ok', data: { meta: { totalElements: 0 } } },
    { domain: 'ecommerce', status: 'ok', data: { totalElements: 0 } },
  ],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.mocked(getFinanceDefaultAccountId).mockReset();
});

describe('operator-overview proxy — outbound 4th header (X-Finance-Default-Account-Id)', () => {
  it('forwards X-Finance-Default-Account-Id when the helper returns a non-blank value (AC-3)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'finance');
    vi.mocked(getFinanceDefaultAccountId).mockResolvedValueOnce('acc-uuid-7');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const res = await overviewProxyGET();
    expect(res.status).toBe(200);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Finance-Default-Account-Id']).toBe('acc-uuid-7');
    // The 3 existing headers MUST continue to be forwarded (regression guard).
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['X-Operator-Token']).toBe('OP-TOKEN');
    expect(h['X-Tenant-Id']).toBe('finance');
  });

  it('OMITS X-Finance-Default-Account-Id when the helper returns null (AC-2 header-absent path)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'finance');
    vi.mocked(getFinanceDefaultAccountId).mockResolvedValueOnce(null);

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const res = await overviewProxyGET();
    expect(res.status).toBe(200);

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    // ABSENT, not empty-string — wire-level: the header key is not present.
    expect('X-Finance-Default-Account-Id' in h).toBe(false);
    // Existing 3 headers preserved (regression guard).
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['X-Operator-Token']).toBe('OP-TOKEN');
    expect(h['X-Tenant-Id']).toBe('finance');
  });

  it('defensive: OMITS the header when the helper ever returned an empty string (truthy-gate)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'finance');
    // The helper itself normalizes empty → null, but the proxy must
    // not regress to forwarding a blank value if that ever changed.
    vi.mocked(getFinanceDefaultAccountId).mockResolvedValueOnce(
      '' as unknown as string | null,
    );

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await overviewProxyGET();

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect('X-Finance-Default-Account-Id' in h).toBe(false);
  });

  it('does not call the helper or fetch when the active tenant is absent (400 NO_ACTIVE_TENANT — pre-emptive gate)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-TOKEN');
    // Intentionally no TENANT_COOKIE.

    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await overviewProxyGET();
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('does not call the helper or fetch when the operator session is incomplete (401 TOKEN_INVALID)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'finance');
    // Intentionally no OPERATOR_COOKIE.

    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await overviewProxyGET();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
