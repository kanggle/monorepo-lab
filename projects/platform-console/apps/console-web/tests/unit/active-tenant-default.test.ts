import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-292 — the active tenant is NOT derived from the IAM OIDC token.
 *
 * 🔴 The defect: the console client's token carries `tenant_id=iam` (the
 *    client's operational slug — `V0024` renamed `gap`→`iam`), which is not a
 *    customer tenant. The callback and the idle refresh defaulted the active
 *    tenant to it with NO assumed token, so every domain screen sent the base
 *    token, the gateway answered 401, and the console said «세션 만료».
 *
 * The rule now (owner decision 2026-09-16, AC-1): at login and on idle refresh
 * the console asks the registry which tenants this operator may select, then
 *   ① the operator's last selection, if still selectable;
 *   ② else the only selectable tenant, if there is exactly one;
 *   ③ else nothing (the domain sections show «테넌트를 선택하세요»).
 * A chosen tenant is always ASSUMED — the tenant cookie never stands without an
 * assumed token. The token's `tenant_id` is not an input at all, so the next
 * slug rename cannot reopen this (relation, not a value list).
 */

const cookieJar = new Map<string, { value: string; opts: Record<string, unknown> }>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string, opts: Record<string, unknown> = {}) => {
    cookieJar.set(name, { value, opts });
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({
  cookies: async () => cookiesMock,
  headers: async () => new Headers(),
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
  publicOrigin: (e: { CONSOLE_PUBLIC_ORIGIN?: string; NEXT_PUBLIC_APP_URL: string }) =>
    e.CONSOLE_PUBLIC_ORIGIN ?? e.NEXT_PUBLIC_APP_URL,
}));

const fetchRegistryMock = vi.fn();
vi.mock('@/shared/api/registry-client', () => ({
  fetchRegistry: (...args: unknown[]) => fetchRegistryMock(...args),
}));

import { GET as callbackGET } from '@/app/api/auth/callback/route';
import { refreshSessionCookies } from '@/shared/lib/session-refresh';
import { POST as tenantPOST } from '@/app/api/tenant/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  LAST_TENANT_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
} from '@/shared/lib/session';
import {
  chooseDefaultTenant,
  selectableTenants,
  rememberTenantValue,
  noTenantNoticeKind,
} from '@/shared/lib/active-tenant-default';
import { RegistryUnavailableError } from '@/shared/api/errors';

/** An unsigned JWT-shaped token (the console only decodes, never verifies). */
function jwt(claims: Record<string, unknown>): string {
  return `h.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.s`;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** The live 2026-09-16 token: the console client's operational slug. */
const IAM_SLUG_TOKEN = jwt({ tenant_id: 'iam', sub: 'op-1' });

function registryWith(tenants: string[]) {
  return {
    products: [
      { productKey: 'iam', displayName: 'IAM', available: true, tenants, baseRoute: '/accounts' },
      { productKey: 'ecommerce', displayName: 'E', available: true, tenants, baseRoute: '/ecommerce' },
      { productKey: 'wms', displayName: 'W', available: false, tenants: [], baseRoute: '/wms' },
    ],
  };
}

type AssumeReply = Response | Error;

/**
 * fetch stub for the login/refresh chain: the IAM `/oauth2/token` code or
 * refresh grant, the operator exchange, and the assume-tenant exchange (also
 * `/oauth2/token`, told apart by its `grant_type`).
 */
function stubChain(
  accessToken: string,
  assume: (audience: string) => AssumeReply = (a) =>
    json({ access_token: `ASSUMED-FOR-${a}`, token_type: 'Bearer', expires_in: 1800 }),
) {
  const fn = vi.fn((url: string, init?: RequestInit) => {
    const u = String(url);
    if (u.includes('/api/admin/auth/token-exchange')) {
      return Promise.resolve(json({ accessToken: 'op.jwt', expiresIn: 900, tokenType: 'admin' }));
    }
    const body = new URLSearchParams(String(init?.body ?? ''));
    if (body.get('grant_type') === 'urn:ietf:params:oauth:grant-type:token-exchange') {
      const reply = assume(body.get('audience') ?? '');
      return reply instanceof Error ? Promise.reject(reply) : Promise.resolve(reply);
    }
    return Promise.resolve(
      json({
        access_token: accessToken,
        token_type: 'Bearer',
        expires_in: 1800,
        refresh_token: 'ref.jwt',
      }),
    );
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

function assumeCalls(fn: ReturnType<typeof stubChain>): string[] {
  return fn.mock.calls
    .map(([, init]) => new URLSearchParams(String((init as RequestInit | undefined)?.body ?? '')))
    .filter((b) => b.get('grant_type') === 'urn:ietf:params:oauth:grant-type:token-exchange')
    .map((b) => b.get('audience') ?? '');
}

async function login() {
  cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v', opts: {} });
  cookieJar.set(OAUTH_STATE_COOKIE, { value: 's1|/ecommerce', opts: {} });
  return callbackGET(new Request('http://console.local/api/auth/callback?code=C&state=s1'));
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  fetchRegistryMock.mockReset();
  vi.unstubAllGlobals();
});

describe('callback — the active tenant is not the token tenant_id (AC-2 / AC-3)', () => {
  it('🔴 tenant_id=iam + two selectable tenants → NO tenant cookie, NO assumed token, login still succeeds', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp', 'ecommerce']));
    const fn = stubChain(IAM_SLUG_TOKEN);

    const res = await login();

    expect(res.headers.get('location')).toBe('http://console.local/ecommerce');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op.jwt');
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe(IAM_SLUG_TOKEN);
    expect(cookieJar.get(TENANT_COOKIE)?.value).not.toBe('iam');
    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
    expect(assumeCalls(fn)).toEqual([]);
  });

  it('🔴 tenant_id=iam + ONE selectable tenant → that tenant is ASSUMED (cookie + assumed token together)', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp']));
    const fn = stubChain(IAM_SLUG_TOKEN);

    await login();

    expect(assumeCalls(fn)).toEqual(['demo-corp']);
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('demo-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('ASSUMED-FOR-demo-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.opts.maxAge).toBe(1800);
  });

  it('the registry is asked with the operator token just minted (not a cookie read the request does not carry)', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp']));
    stubChain(IAM_SLUG_TOKEN);

    await login();

    expect(fetchRegistryMock).toHaveBeenCalledWith({ operatorToken: 'op.jwt' });
  });

  it('🔴 the last selection, still selectable, wins over «more than one»', async () => {
    cookieJar.set(LAST_TENANT_COOKIE, { value: rememberTenantValue('op-1', 'ecommerce'), opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp', 'ecommerce']));
    const fn = stubChain(IAM_SLUG_TOKEN);

    await login();

    expect(assumeCalls(fn)).toEqual(['ecommerce']);
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('ecommerce');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('ASSUMED-FOR-ecommerce');
  });

  it('a last selection made by ANOTHER operator in this browser is ignored', async () => {
    cookieJar.set(LAST_TENANT_COOKIE, { value: rememberTenantValue('op-2', 'ecommerce'), opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp', 'ecommerce']));
    const fn = stubChain(IAM_SLUG_TOKEN);

    await login();

    expect(assumeCalls(fn)).toEqual([]);
    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
  });

  it('customer-tenant token, one selectable tenant → same tenant as before (net-zero), now with an assumed token', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp']));
    stubChain(jwt({ tenant_id: 'acme-corp', sub: 'op-9' }));

    await login();

    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('acme-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('ASSUMED-FOR-acme-corp');
  });

  it('platform sentinel `*` token, many tenants → no default (unchanged)', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp', 'globex-corp']));
    stubChain(jwt({ tenant_id: '*', sub: 'op-p' }));

    await login();

    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });

  it('🔴 registry degraded → login succeeds with no tenant (never the token tenant_id)', async () => {
    fetchRegistryMock.mockRejectedValue(new RegistryUnavailableError('timeout', 'x'));
    stubChain(IAM_SLUG_TOKEN);

    const res = await login();

    expect(res.headers.get('location')).toBe('http://console.local/ecommerce');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op.jwt');
    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });

  it('assume refused → login succeeds with no tenant (never a tenant cookie without its assumed token)', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp']));
    stubChain(IAM_SLUG_TOKEN, () => json({ error: 'invalid_grant' }, 400));

    const res = await login();

    expect(res.headers.get('location')).toBe('http://console.local/ecommerce');
    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });
});

describe('idle refresh — the same function as the callback (Failure Scenario 1)', () => {
  function idle() {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
  }

  it('🔴 tenant_id=iam + two selectable tenants → no tenant cookie after refresh', async () => {
    idle();
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp', 'ecommerce']));
    stubChain(IAM_SLUG_TOKEN);

    const out = await refreshSessionCookies(cookiesMock as never, { requestId: 'r', via: 'api' });

    expect(out.kind).toBe('ok');
    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });

  it('🔴 one selectable tenant → assumed after refresh', async () => {
    idle();
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp']));
    const fn = stubChain(IAM_SLUG_TOKEN);

    await refreshSessionCookies(cookiesMock as never, { requestId: 'r', via: 'api' });

    expect(assumeCalls(fn)).toEqual(['demo-corp']);
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('demo-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('ASSUMED-FOR-demo-corp');
  });

  it('an existing selection takes the re-assume branch and never asks the registry', async () => {
    idle();
    cookieJar.set(TENANT_COOKIE, { value: 'ecommerce', opts: {} });
    const fn = stubChain(IAM_SLUG_TOKEN);

    await refreshSessionCookies(cookiesMock as never, { requestId: 'r', via: 'api' });

    expect(fetchRegistryMock).not.toHaveBeenCalled();
    expect(assumeCalls(fn)).toEqual(['ecommerce']);
  });
});

describe('/api/tenant remembers the selection for the next login', () => {
  it('a successful switch writes «operator|tenant» into the long-lived last-tenant cookie', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: IAM_SLUG_TOKEN, opts: {} });
    cookieJar.set(OPERATOR_COOKIE, { value: 'op.jwt', opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp', 'ecommerce']));
    stubChain(IAM_SLUG_TOKEN);

    const res = await tenantPOST(
      new Request('http://console.local/api/tenant', {
        method: 'POST',
        body: JSON.stringify({ tenant: 'ecommerce' }),
      }),
    );

    expect(res.status).toBe(200);
    const last = cookieJar.get(LAST_TENANT_COOKIE);
    expect(last?.value).toBe(rememberTenantValue('op-1', 'ecommerce'));
    expect(last?.opts).toMatchObject({ httpOnly: true, path: '/' });
    expect(Number(last?.opts.maxAge)).toBeGreaterThan(24 * 3600);
  });

  it('clearing the selection forgets it too', async () => {
    cookieJar.set(LAST_TENANT_COOKIE, { value: rememberTenantValue('op-1', 'ecommerce'), opts: {} });

    await tenantPOST(
      new Request('http://console.local/api/tenant', {
        method: 'POST',
        body: JSON.stringify({ tenant: '' }),
      }),
    );

    expect(cookieDeletes).toContain(LAST_TENANT_COOKIE);
  });
});

describe('chooseDefaultTenant — the rule itself', () => {
  it('remembered and selectable → remembered', () => {
    expect(chooseDefaultTenant(['a', 'b'], 'b')).toBe('b');
  });
  it('remembered but no longer selectable → falls through to the single-tenant rule', () => {
    expect(chooseDefaultTenant(['a'], 'gone')).toBe('a');
    expect(chooseDefaultTenant(['a', 'b'], 'gone')).toBeNull();
  });
  it('exactly one selectable → it; zero or many → null', () => {
    expect(chooseDefaultTenant(['a'], null)).toBe('a');
    expect(chooseDefaultTenant([], null)).toBeNull();
    expect(chooseDefaultTenant(['a', 'b'], null)).toBeNull();
  });
  it('selectableTenants = distinct tenants of AVAILABLE products (the same set /api/tenant allows)', () => {
    const r = registryWith(['demo-corp', 'ecommerce']);
    expect(selectableTenants(r as never)).toEqual(['demo-corp', 'ecommerce']);
  });
});

describe('noTenantNoticeKind — TASK-PC-FE-301 (owner decision ⓒ, 2026-09-26 UTC)', () => {
  it('0 selectable tenants (viewer@demo.com shape, 0 roles → 0 products) → zero', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith([]));
    expect(await noTenantNoticeKind()).toBe('zero');
  });

  it('exactly 1 selectable tenant → select (it auto-selects before any gate is asked, but the raw judgement is still "something to select")', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp']));
    expect(await noTenantNoticeKind()).toBe('select');
  });

  it('≥2 selectable tenants, none chosen → select (today’s unchanged copy)', async () => {
    fetchRegistryMock.mockResolvedValue(registryWith(['demo-corp', 'ecommerce']));
    expect(await noTenantNoticeKind()).toBe('select');
  });

  it('🔴 registry failure is NOT read as zero (Edge Case: 없음 ≠ 못 읽음) → falls back to select', async () => {
    fetchRegistryMock.mockRejectedValue(new RegistryUnavailableError('timeout', 'x'));
    expect(await noTenantNoticeKind()).toBe('select');
  });

  it('🔴 a degraded/empty-products registry is legitimately zero, not a failure — distinguish thrown vs resolved-empty', async () => {
    // A resolved (not thrown) empty registry is the true "0 available products"
    // shape — must still read as zero, not silently fall back to select.
    fetchRegistryMock.mockResolvedValue({ products: [] });
    expect(await noTenantNoticeKind()).toBe('zero');
  });
});
