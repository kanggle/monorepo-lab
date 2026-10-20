import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-354 — the per-call `X-Tenant-Id` override (`tenantOverride: '*'`)
 * of the shared IAM gateway core, as the 업무 권한 grant / revoke use it.
 *
 *   - `{ allTenants: true }` ⇒ `X-Tenant-Id: *` (the `'*'` target's «모든
 *     테넌트» row) — and nothing else about the request changes;
 *   - no option / `false` ⇒ the active tenant;
 *   - 🔴 every caller that does not set the override sends the SAME header set
 *     it sent before (pinned below for the read, `/me`, a role PATCH and a
 *     tenant assignment — the gateway is the shared core of five clients);
 *   - the override never bypasses the «no active tenant» block.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  grantOperatorDomainSet,
  revokeOperatorDomainSet,
} from '@/features/operators/api/operators-domain-access-api';
import {
  assignOperatorToTenant,
  editOperatorRoles,
} from '@/features/operators/api/operators-api';
import { listOperators, getSelfOrNull } from '@/shared/api/iam-operators-read';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

const GRANT_RESULT = {
  operatorId: 'op-star',
  tenantId: '*',
  setId: 'preset-1',
  source: 'DIRECT',
  grantedAt: '2026-10-20T00:00:00Z',
  grantedBy: 'op-self',
  selfGrant: false,
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function headersOf(fetchMock: ReturnType<typeof vi.fn>, i = 0): Record<string, string> {
  return (fetchMock.mock.calls[i][1] as RequestInit).headers as Record<string, string>;
}

/** The header set minus the per-call random request id. */
function stable(h: Record<string, string>) {
  const rest = { ...h };
  expect(rest['X-Request-Id']).toBeTruthy();
  delete rest['X-Request-Id'];
  return rest;
}

beforeEach(() => {
  cookieJar.clear();
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-tok');
  cookieJar.set(TENANT_COOKIE, 'demo-corp');
  vi.unstubAllGlobals();
});

describe("grant — X-Tenant-Id selection", () => {
  it("{ allTenants: true } ⇒ X-Tenant-Id '*' and otherwise the same request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(GRANT_RESULT, 201));
    vi.stubGlobal('fetch', fetchMock);
    await grantOperatorDomainSet('op-star', 'preset-1', '플랫폼', { allTenants: true });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://iam.local/api/admin/operators/op-star/domain-permission-sets');
    expect((init as RequestInit).method).toBe('POST');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ setId: 'preset-1' });
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': '*',
      'X-Operator-Reason': encodeURIComponent('플랫폼'),
      'Content-Type': 'application/json',
    });
  });

  it('no option ⇒ the active tenant (pre-354 header set, byte-identical)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ ...GRANT_RESULT, tenantId: 'demo-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    await grantOperatorDomainSet('op-1', 'set-1', 'r');
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': 'demo-corp',
      'X-Operator-Reason': 'r',
      'Content-Type': 'application/json',
    });
  });

  it('{ allTenants: false } ⇒ the active tenant', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ ...GRANT_RESULT, tenantId: 'demo-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    await grantOperatorDomainSet('op-1', 'set-1', 'r', { allTenants: false });
    expect(headersOf(fetchMock)['X-Tenant-Id']).toBe('demo-corp');
  });

  it('the override never bypasses «no active tenant» — 400, no fetch', async () => {
    cookieJar.delete(TENANT_COOKIE);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      grantOperatorDomainSet('op-star', 'preset-1', 'r', { allTenants: true }),
    ).rejects.toMatchObject({ status: 400, code: 'NO_ACTIVE_TENANT' });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('revoke — X-Tenant-Id selection (per source row)', () => {
  it("the '*' source ⇒ { allTenants: true } ⇒ X-Tenant-Id '*'", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    await revokeOperatorDomainSet('op-star', 'set-legacy', '정리', { allTenants: true });
    const h = headersOf(fetchMock);
    expect(h['X-Tenant-Id']).toBe('*');
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('정리');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('DELETE');
  });

  it("a T source of the same '*' target ⇒ no option ⇒ the active tenant", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    await revokeOperatorDomainSet('op-star', 'set-1', 'r');
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': 'demo-corp',
      'X-Operator-Reason': 'r',
    });
  });
});

describe('🔴 callers that never set the override — header sets pinned (byte-identical to pre-354)', () => {
  it('GET /operators (list)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(json({ content: [], totalElements: 0, page: 0, size: 20, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);
    await listOperators();
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': 'demo-corp',
    });
  });

  it("the all-tenants listing (tenantId '*' query) still sends the ACTIVE tenant header", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(json({ content: [], totalElements: 0, page: 0, size: 20, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);
    await listOperators({ tenantId: '*' });
    expect(String(fetchMock.mock.calls[0][0])).toContain('?tenantId=*&');
    expect(headersOf(fetchMock)['X-Tenant-Id']).toBe('demo-corp');
  });

  it('GET /me', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      json({
        operatorId: 'op-self',
        email: 'a@x.com',
        displayName: 'A',
        status: 'ACTIVE',
        roles: [],
        createdAt: '2026-01-01T00:00:00Z',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    await getSelfOrNull();
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': 'demo-corp',
    });
  });

  it('PATCH roles', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(json({ operatorId: 'op-1', roles: ['SUPPORT_LOCK'], auditId: 'a1' }));
    vi.stubGlobal('fetch', fetchMock);
    await editOperatorRoles('op-1', ['SUPPORT_LOCK'], 'r').catch(() => undefined);
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': 'demo-corp',
      'X-Operator-Reason': 'r',
      'Content-Type': 'application/json',
    });
  });

  it('POST assignment (a body-less mutation)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ tenantId: 'demo-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    await assignOperatorToTenant('op-1', 'demo-corp', 'r');
    expect(stable(headersOf(fetchMock))).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer OPERATOR-tok',
      'X-Tenant-Id': 'demo-corp',
      'X-Operator-Reason': 'r',
    });
  });
});
