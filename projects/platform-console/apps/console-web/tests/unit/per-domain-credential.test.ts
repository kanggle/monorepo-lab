import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Per-domain credential selection — the cross-domain regression that pins
 * ALL FOUR domains' credentials in one place (console-integration-
 * contract § 2.4.5 normative rule, REUSED by § 2.4.6 for scm AND
 * § 2.4.7 for finance — NOT re-derived). Extended by TASK-PC-FE-009 to
 * cover finance (Phase 5 COMPLETE):
 *
 *   - GAP (§§ 2.4.1–2.4.4) STILL authenticates with the EXCHANGED
 *     operator token (`getOperatorToken()`) — FE-002..006 unchanged, NOT
 *     regressed by FE-007/FE-008/FE-009 (the divergence is ADDITIVE);
 *   - wms (§ 2.4.5) authenticates with the GAP OIDC ACCESS token
 *     (`getAccessToken()`) and NEVER the operator token;
 *   - scm (§ 2.4.6) ALSO authenticates with the GAP OIDC ACCESS token
 *     (`getAccessToken()`) and NEVER the operator token — the § 2.4.5
 *     rule generalises (the #569 invariant is GAP-domain-scoped);
 *   - finance (§ 2.4.7) ALSO authenticates with the GAP OIDC ACCESS
 *     token (`getAccessToken()`) and NEVER the operator token — the
 *     § 2.4.5 rule generalises a SECOND time.
 *
 * Asserting all four in one test makes a future refactor that
 * blanket-applies one domain's auth to another fail loudly (the failure
 * mode § 2.4.5/§ 2.4.6/§ 2.4.7 exists to prevent — the per-domain
 * credential rule holds across GAP / wms / scm / finance).
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
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
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
import { listPurchaseOrders } from '@/features/scm-ops/api/scm-api';
import { getAccount } from '@/features/finance-ops/api/finance-api';
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

describe('per-domain credential divergence (§ 2.4.5 / § 2.4.6 / § 2.4.7) — all 4 domains pinned', () => {
  it('GAP uses the EXCHANGED operator token; wms AND scm AND finance use the GAP OIDC access token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'EXCHANGED-OPERATOR');
    cookieJar.set(TENANT_COOKIE, 'scm');

    // GAP accounts uses `AccountPageSchema`; wms uses its own page meta;
    // scm uses the procurement-PO envelope; finance uses its
    // account-by-id envelope. Route each parser-valid body by URL so
    // all four calls succeed.
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
    const SCM_PO_ENV = {
      data: {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
      meta: { timestamp: 'x' },
    };
    const FIN_ACCT_ENV = {
      data: {
        accountId: 'acct-1',
        status: 'ACTIVE',
        currency: 'KRW',
        kycLevel: 'BASIC',
      },
      meta: { timestamp: 'x' },
    };
    const fetchMock = vi.fn((url: string) => {
      const u = String(url);
      if (u.includes('/api/admin/accounts'))
        return Promise.resolve(jsonResponse(ACCOUNTS_PAGE));
      if (u.includes('/api/v1/procurement/po'))
        return Promise.resolve(jsonResponse(SCM_PO_ENV));
      if (u.includes('/api/finance/accounts/'))
        return Promise.resolve(jsonResponse(FIN_ACCT_ENV));
      return Promise.resolve(jsonResponse(WMS_PAGE));
    });
    vi.stubGlobal('fetch', fetchMock);

    // GAP accounts (FE-002 path — STILL the operator token).
    await searchAccounts({ page: 0, size: 20 });
    const gapHeaders = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(gapHeaders.Authorization).toBe('Bearer EXCHANGED-OPERATOR');
    expect(gapHeaders.Authorization).not.toContain('GAP-OIDC-ACCESS');
    // GAP still scopes by X-Tenant-Id (its mechanism — unchanged).
    expect(gapHeaders['X-Tenant-Id']).toBe('scm');

    // wms ops (FE-007 path — the GAP OIDC access token, NOT the operator
    // token; NO X-Tenant-Id — wms resolves the tenant from the JWT claim).
    await listInventory({ page: 0, size: 20 });
    const wmsHeaders = (fetchMock.mock.calls[1][1] as RequestInit)
      .headers as Record<string, string>;
    expect(wmsHeaders.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(wmsHeaders.Authorization).not.toContain('EXCHANGED-OPERATOR');
    expect(wmsHeaders['X-Tenant-Id']).toBeUndefined();

    // scm ops (FE-008 path — the § 2.4.5 rule REUSED: the GAP OIDC access
    // token, NOT the operator token; NO X-Tenant-Id — scm resolves the
    // tenant from the JWT `tenant_id ∈ {scm,*}` claim).
    await listPurchaseOrders({ page: 0, size: 20 });
    const scmHeaders = (fetchMock.mock.calls[2][1] as RequestInit)
      .headers as Record<string, string>;
    expect(scmHeaders.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(scmHeaders.Authorization).not.toContain('EXCHANGED-OPERATOR');
    expect(scmHeaders['X-Tenant-Id']).toBeUndefined();

    // finance ops (FE-009 path — the § 2.4.5 rule REUSED a second time:
    // the GAP OIDC access token, NOT the operator token; NO X-Tenant-Id
    // — finance resolves the tenant from the JWT `tenant_id ∈
    // {finance,*}` claim).
    await getAccount('acct-1');
    const finHeaders = (fetchMock.mock.calls[3][1] as RequestInit)
      .headers as Record<string, string>;
    expect(finHeaders.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(finHeaders.Authorization).not.toContain('EXCHANGED-OPERATOR');
    expect(finHeaders['X-Tenant-Id']).toBeUndefined();

    // GAP uses a DIFFERENT credential from wms/scm/finance — the
    // divergence is real and additive (FE-002..006 unchanged). wms,
    // scm, AND finance share the SAME GAP-OIDC credential (the
    // § 2.4.5 rule generalises to scm and finance).
    expect(gapHeaders.Authorization).not.toBe(wmsHeaders.Authorization);
    expect(gapHeaders.Authorization).not.toBe(scmHeaders.Authorization);
    expect(gapHeaders.Authorization).not.toBe(finHeaders.Authorization);
    expect(wmsHeaders.Authorization).toBe(scmHeaders.Authorization);
    expect(scmHeaders.Authorization).toBe(finHeaders.Authorization);
  });
});
