import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-780 — the wms gateway bearer follows the ACTIVE tenant.
 *
 * ADR-MONO-022 § D9 rests on «the console already forwards the assumed
 * tenant-scoped token». Measured before this file: the switched case was
 * pinned for the wms ADMIN read-model only (`domain-facing-credential.test.ts`
 * — `listInventory`); the OUTBOUND client — the «WMS 출고» list the 24th demo
 * window judged — had cells for the no-switch case only (`outbound-api.test.ts`),
 * and nothing pinned «switch back» or the 403 of a tenant wms does not admit.
 * The proxy header comment had meanwhile drifted to «the IAM OIDC token, NOT
 * the exchanged one», which is how that window read the gap as a token defect.
 * These cells pin the switched case on BOTH wms order surfaces, through the
 * real `callWmsGateway` core.
 *
 * Bite: in `shared/api/wms-gateway.ts` `prepareWmsHeaders`, replace
 * `getDomainFacingToken()` with `getAccessToken()` ⇒ the «assumed» and
 * «back to demo-corp» cells go red (they receive the base login token).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
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
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    WMS_OUTBOUND_BASE_URL: 'http://wms.local/api/v1/outbound',
    WMS_OUTBOUND_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { listOrders as listOutboundOrders } from '@/features/wms-outbound-ops/api/outbound-api';
import { listOrders as listDashboardOrders } from '@/features/wms-ops/api/wms-api';
import { ApiError } from '@/shared/api/errors';
import {
  ACCESS_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';

const EMPTY_PAGE = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Both wms surfaces the console reads orders from. */
const SURFACES = [
  {
    name: 'outbound-service GET /orders (WMS 출고)',
    call: () => listOutboundOrders({}),
    url: 'http://wms.local/api/v1/outbound/orders',
  },
  {
    name: 'admin-service GET /dashboard/orders (WMS 개요)',
    call: () => listDashboardOrders({}),
    url: 'http://wms.local/api/v1/admin/dashboard/orders',
  },
] as const;

function loggedIn() {
  // Base login token: `tenant_id=iam` for this client (§ 2.7 «net-zero»
  // withdrawn by TASK-PC-FE-292) — never what a switched wms read should carry.
  cookieJar.set(ACCESS_COOKIE, 'BASE-LOGIN-TOKEN');
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-never-for-wms');
}

function switchedTo(tenant: string) {
  // What `POST /api/tenant` sets atomically after a successful assume-tenant
  // exchange (app/api/tenant/route.ts — TENANT_COOKIE + ASSUMED_TOKEN_COOKIE).
  cookieJar.set(TENANT_COOKIE, tenant);
  cookieJar.set(ASSUMED_TOKEN_COOKIE, `ASSUMED-${tenant}`);
}

function bearerOf(fetchMock: ReturnType<typeof vi.fn>): string {
  const init = fetchMock.mock.calls[0][1] as RequestInit;
  return (init.headers as Record<string, string>).Authorization;
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe.each(SURFACES)('wms bearer follows the active tenant — $name', (s) => {
  it('switched to ecommerce ⇒ the ecommerce ASSUMED token rides (not the login token)', async () => {
    loggedIn();
    switchedTo('ecommerce');
    const fetchMock = vi.fn().mockResolvedValue(json(EMPTY_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await s.call();

    expect(String(fetchMock.mock.calls[0][0])).toContain(s.url);
    expect(bearerOf(fetchMock)).toBe('Bearer ASSUMED-ecommerce');
    // Still no tenant header — the tenant rides only inside the signed token.
    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
  });

  it('switched back to demo-corp ⇒ the demo-corp token (follows the selection, not «ecommerce fixed»)', async () => {
    loggedIn();
    switchedTo('ecommerce');
    switchedTo('demo-corp');
    const fetchMock = vi.fn().mockResolvedValue(json(EMPTY_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await s.call();

    expect(bearerOf(fetchMock)).toBe('Bearer ASSUMED-demo-corp');
  });

  it('no active tenant ⇒ the login token, exactly as before (current behaviour unchanged)', async () => {
    loggedIn();
    const fetchMock = vi.fn().mockResolvedValue(json(EMPTY_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await s.call();

    expect(bearerOf(fetchMock)).toBe('Bearer BASE-LOGIN-TOKEN');
  });

  it('a tenant wms does not admit (gateway 403) ⇒ the existing inline 403, never a re-login', async () => {
    loggedIn();
    switchedTo('tenant-without-wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json({ error: { code: 'FORBIDDEN', message: 'tenant_mismatch' } }, 403),
      ),
    );

    const err = await s.call().catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(403);
  });
});
