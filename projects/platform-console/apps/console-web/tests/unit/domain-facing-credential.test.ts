import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Domain-facing credential after an active-tenant switch (ADR-MONO-020 D4 /
 * console-integration-contract § 2.7). Companion to per-domain-credential.test.ts
 * (which pins the NON-switched / net-zero state). Here an ASSUMED token is
 * present (the operator switched to a customer): the 4 non-GAP domain clients
 * (wms/scm/finance/erp) MUST send the ASSUMED token as the GAP-OIDC bearer
 * (the re-scope is real), while the GAP clients keep using the OPERATOR token
 * (§ 2.6 boundary unchanged — the #569 invariant holds).
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
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
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
import { listDepartments } from '@/features/erp-ops/api/erp-api';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  ASSUMED_TOKEN_COOKIE,
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

describe('domain-facing credential after switch (§ 2.7)', () => {
  it('non-GAP domains send the ASSUMED token; GAP still sends the operator token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'BASE-GAP-OIDC');
    cookieJar.set(OPERATOR_COOKIE, 'EXCHANGED-OPERATOR');
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'ASSUMED-FOR-GLOBEX');
    cookieJar.set(TENANT_COOKIE, 'globex-corp');

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
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
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
    const ERP_DEPT_ENV = {
      data: [],
      meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
    };
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      const u = String(url);
      if (u.includes('/api/admin/accounts'))
        return Promise.resolve(jsonResponse(ACCOUNTS_PAGE));
      if (u.includes('/api/v1/procurement/po'))
        return Promise.resolve(jsonResponse(SCM_PO_ENV));
      if (u.includes('/api/finance/accounts/'))
        return Promise.resolve(jsonResponse(FIN_ACCT_ENV));
      if (u.includes('/api/erp/masterdata/'))
        return Promise.resolve(jsonResponse(ERP_DEPT_ENV));
      return Promise.resolve(jsonResponse(WMS_PAGE));
    });
    vi.stubGlobal('fetch', fetchMock);

    // GAP STILL uses the exchanged operator token — NOT the assumed token.
    await searchAccounts({ page: 0, size: 20 });
    const gapHeaders = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(gapHeaders.Authorization).toBe('Bearer EXCHANGED-OPERATOR');
    expect(gapHeaders.Authorization).not.toContain('ASSUMED-FOR-GLOBEX');

    // wms / scm / finance / erp send the ASSUMED (re-scoped) token — NOT the
    // base GAP token, NOT the operator token.
    await listInventory({ page: 0, size: 20 });
    await listPurchaseOrders({ page: 0, size: 20 });
    await getAccount('acct-1');
    await listDepartments({});

    for (const i of [1, 2, 3, 4]) {
      const h = (fetchMock.mock.calls[i][1] as RequestInit).headers as Record<
        string,
        string
      >;
      expect(h.Authorization).toBe('Bearer ASSUMED-FOR-GLOBEX');
      expect(h.Authorization).not.toContain('EXCHANGED-OPERATOR');
      expect(h.Authorization).not.toContain('BASE-GAP-OIDC');
    }
  });
});
