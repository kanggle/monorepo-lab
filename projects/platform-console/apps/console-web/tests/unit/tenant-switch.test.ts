import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `/api/tenant` active-tenant switcher → assume-tenant flow
 * (ADR-MONO-020 D4 / console-integration-contract § 2.7) +
 * the `getDomainFacingToken()` central resolver (net-zero).
 *
 * The switch route (1) keeps the registry allow-check, (2) drives the
 * server-side assume-tenant exchange, and (3) sets the assumed token +
 * active tenant atomically. Fail-closed: denied→403 no cookie change,
 * invalid→422, unavailable→503, missing base→401; clear deletes BOTH.
 *
 * `next/headers` cookies(), `getServerEnv()`, and `fetchRegistry()` are
 * mocked; `fetch` (the assume-tenant exchange) is stubbed per test.
 */

const cookieJar = new Map<
  string,
  { value: string; opts: Record<string, unknown> }
>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string, opts: Record<string, unknown>) => {
    cookieJar.set(name, { value, opts });
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));

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
}));

// fetchRegistry mocked so the switch route's allow-check is deterministic.
const fetchRegistryMock = vi.fn();
vi.mock('@/shared/api/registry-client', () => ({
  fetchRegistry: () => fetchRegistryMock(),
}));

import { POST as tenantPOST } from '@/app/api/tenant/route';
import {
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ACCESS_COOKIE,
  getDomainFacingToken,
} from '@/shared/lib/session';

function req(body: unknown): Request {
  return new Request('http://console.local/api/tenant', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** Registry response listing both customers as selectable for this operator. */
function registryWith(tenants: string[]) {
  return {
    products: [
      { productKey: 'gap', available: true, tenants },
      { productKey: 'finance', available: true, tenants },
    ],
  };
}

function sasOk(accessToken: string, expiresIn = 1800) {
  return new Response(
    JSON.stringify({
      access_token: accessToken,
      token_type: 'Bearer',
      expires_in: expiresIn,
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  fetchRegistryMock.mockReset();
  vi.unstubAllGlobals();
});

describe('POST /api/tenant — switch success (AC-1)', () => {
  it('drives the assume-tenant exchange and sets BOTH cookies atomically', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE-GAP-TOKEN', opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp']));
    const fetchMock = vi.fn().mockResolvedValue(sasOk('ASSUMED-FOR-ACME', 1800));
    vi.stubGlobal('fetch', fetchMock);

    const res = await tenantPOST(req({ tenant: 'acme-corp' }));
    expect(res.status).toBe(200);
    expect((await res.json()).activeTenant).toBe('acme-corp');

    // The exchange was driven with the BASE token as subject + acme audience.
    const sent = new URLSearchParams(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(sent.get('subject_token')).toBe('BASE-GAP-TOKEN');
    expect(sent.get('audience')).toBe('acme-corp');

    // Both cookies set atomically; assumed-token maxAge tracks expires_in.
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('acme-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('ASSUMED-FOR-ACME');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.opts).toMatchObject({
      httpOnly: true,
      secure: true,
      maxAge: 1800,
    });
  });
});

describe('POST /api/tenant — fail-closed switch (AC-3)', () => {
  it('assume-tenant denied (400 invalid_grant) → 403, NO cookie change', async () => {
    // Prior selection present; it must be PRESERVED on a denied switch.
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE-GAP-TOKEN', opts: {} });
    cookieJar.set(TENANT_COOKIE, { value: 'acme-corp', opts: {} });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'OLD-ASSUMED', opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp', 'globex-corp']));
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'invalid_grant' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    const res = await tenantPOST(req({ tenant: 'globex-corp' }));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('TENANT_FORBIDDEN');
    // Prior cookies untouched — no partial/stale state.
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('acme-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('OLD-ASSUMED');
    expect(cookieDeletes).not.toContain(TENANT_COOKIE);
    expect(cookieDeletes).not.toContain(ASSUMED_TOKEN_COOKIE);
  });

  it('assume-tenant invalid (400 invalid_request) → 422', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE', opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp']));
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'invalid_request' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const res = await tenantPOST(req({ tenant: 'acme-corp' }));
    expect(res.status).toBe(422);
  });

  it('assume-tenant unavailable (5xx) → 503', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE', opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp']));
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('{}', { status: 502 })),
    );
    const res = await tenantPOST(req({ tenant: 'acme-corp' }));
    expect(res.status).toBe(503);
  });

  it('missing base GAP token → 401 (no exchange attempted)', async () => {
    // No ACCESS_COOKIE seeded.
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp']));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await tenantPOST(req({ tenant: 'acme-corp' }));
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('tenant not in registry → 403 before any exchange (defence-in-depth)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE', opts: {} });
    fetchRegistryMock.mockResolvedValue(registryWith(['acme-corp']));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await tenantPOST(req({ tenant: 'not-mine' }));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/tenant — clear path', () => {
  it("tenant='' deletes BOTH the tenant and assumed-token cookies", async () => {
    cookieJar.set(TENANT_COOKIE, { value: 'acme-corp', opts: {} });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'ASSUMED', opts: {} });
    const res = await tenantPOST(req({ tenant: '' }));
    expect(res.status).toBe(200);
    expect((await res.json()).activeTenant).toBeNull();
    expect(cookieDeletes).toContain(TENANT_COOKIE);
    expect(cookieDeletes).toContain(ASSUMED_TOKEN_COOKIE);
  });
});

describe('getDomainFacingToken() — net-zero resolver (AC-2)', () => {
  it('returns the ASSUMED token when an assumption exists', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE', opts: {} });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'ASSUMED', opts: {} });
    expect(await getDomainFacingToken()).toBe('ASSUMED');
  });

  it('falls back to the BASE token when no assumption exists (net-zero)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE', opts: {} });
    expect(await getDomainFacingToken()).toBe('BASE');
  });

  it('returns null when neither token is present', async () => {
    expect(await getDomainFacingToken()).toBeNull();
  });
});
