import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Per-domain credential selection — the FE-007 regression that pins BOTH
 * sides of the divergence in one place (console-integration-contract
 * § 2.4.5 normative rule):
 *
 *   - the GAP surface STILL authenticates with the EXCHANGED operator
 *     token (`getOperatorToken()`) — FE-002..006 unchanged, NOT regressed
 *     by FE-007 (the divergence is ADDITIVE);
 *   - the wms surface authenticates with the GAP OIDC ACCESS token
 *     (`getAccessToken()`) and NEVER the operator token.
 *
 * Asserting both in one test makes a future refactor that blanket-applies
 * one domain's auth to the other fail loudly (the failure mode § 2.4.5
 * exists to prevent).
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
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { searchAccounts } from '@/features/accounts/api/accounts-api';
import { listInventory } from '@/features/wms-ops/api/wms-api';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('per-domain credential divergence (§ 2.4.5) — both sides pinned', () => {
  it('GAP uses the EXCHANGED operator token; wms uses the GAP OIDC access token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'EXCHANGED-OPERATOR');
    cookieJar.set(TENANT_COOKIE, 'wms');

    // GAP accounts uses `AccountPageSchema`; wms uses its own page meta.
    // Route each parser-valid body by URL so both calls succeed.
    const ACCOUNTS_PAGE = {
      content: [
        {
          id: 'acc-1',
          email: 'a@x.io',
          status: 'ACTIVE',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
      totalPages: 1,
    };
    const WMS_PAGE = {
      content: [],
      page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
    };
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        jsonResponse(
          String(url).includes('/api/admin/accounts')
            ? ACCOUNTS_PAGE
            : WMS_PAGE,
        ),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    // GAP accounts (FE-002 path — STILL the operator token).
    await searchAccounts({ page: 0, size: 20 });
    const gapHeaders = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(gapHeaders.Authorization).toBe('Bearer EXCHANGED-OPERATOR');
    expect(gapHeaders.Authorization).not.toContain('GAP-OIDC-ACCESS');
    // GAP still scopes by X-Tenant-Id (its mechanism — unchanged).
    expect(gapHeaders['X-Tenant-Id']).toBe('wms');

    // wms ops (FE-007 path — the GAP OIDC access token, NOT the operator
    // token; NO X-Tenant-Id — wms resolves the tenant from the JWT claim).
    await listInventory({ page: 0, size: 20 });
    const wmsHeaders = (fetchMock.mock.calls[1][1] as RequestInit)
      .headers as Record<string, string>;
    expect(wmsHeaders.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(wmsHeaders.Authorization).not.toContain('EXCHANGED-OPERATOR');
    expect(wmsHeaders['X-Tenant-Id']).toBeUndefined();

    // The two domains used DIFFERENT credentials — the divergence is real
    // and additive (FE-002..006 unchanged).
    expect(gapHeaders.Authorization).not.toBe(wmsHeaders.Authorization);
  });
});
