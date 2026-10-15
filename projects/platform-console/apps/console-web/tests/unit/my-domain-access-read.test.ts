import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-338 c-2 — `getMyDomainRolesOrNull()` (contract § 2.4.3.4 row 12,
 * `GET /api/admin/me/domain-access`): the input of the per-menu «업무 권한 없음» signal and the
 * write-button gate.
 *   - operator token (never the IAM OIDC token) + `X-Tenant-Id` = active tenant;
 *   - success → the `roles[]` rows verbatim;
 *   - EVERY failure (401 · 503 · timeout · bad body · no active tenant) → `null`, which every caller
 *     reads as «don't know ⇒ no badge, buttons shown» (an outage must never read as «no permission»).
 */
const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    throw new Error(`NEXT_REDIRECT:${path}`);
  },
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
    RBAC_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getMyDomainRolesOrNull } from '@/shared/api/iam-operators-read';
import { OPERATOR_COOKIE, TENANT_COOKIE, ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const BODY = {
  operatorId: 'op-1',
  tenantId: 'acme',
  roles: [
    { domainKey: 'wms', role: 'INVENTORY_READ' },
    { domainKey: 'wms', role: 'INVENTORY_WRITE' },
  ],
  sets: [],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

function signedIn() {
  cookieJar.set(ACCESS_COOKIE, 'IAM-OIDC-must-not-leak');
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
  cookieJar.set(TENANT_COOKIE, 'acme');
}

describe('getMyDomainRolesOrNull — row 12', () => {
  it('calls /api/admin/me/domain-access with the operator token + X-Tenant-Id and returns roles[]', async () => {
    signedIn();
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);

    const out = await getMyDomainRolesOrNull();

    expect(out).toEqual(BODY.roles);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/me/domain-access');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN');
    expect(headers['X-Tenant-Id']).toBe('acme');
  });

  it('an empty roles[] is returned as [] (known: no business role), not null', async () => {
    signedIn();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...BODY, roles: [] })));
    expect(await getMyDomainRolesOrNull()).toEqual([]);
  });

  it.each([
    ['401', () => jsonResponse({ code: 'TOKEN_INVALID', message: 'x' }, 401)],
    ['503', () => jsonResponse({ code: 'DOWNSTREAM_ERROR', message: 'x' }, 503)],
    ['bad body', () => jsonResponse({ nope: true })],
  ])('%s → null (fail-open)', async (_name, make) => {
    signedIn();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(make()));
    expect(await getMyDomainRolesOrNull()).toBeNull();
  });

  it('no active tenant → null with NO fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    expect(await getMyDomainRolesOrNull()).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
