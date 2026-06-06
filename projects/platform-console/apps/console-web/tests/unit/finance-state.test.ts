import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/finance-ops/api/finance-state.ts` — the server-side
 * section state (TASK-PC-FE-009 / § 2.4.7):
 *   - not finance-eligible → `notEligible` block, NO finance call
 *     fabricated (no cross-tenant call; the console never sends a
 *     tenant — finance resolves it from the JWT claim);
 *   - no accountId supplied → empty (initial) state, NO finance call
 *     fabricated (account-id-driven; finance v1 has no list/search
 *     GET — the honest constraint);
 *   - eligible + accountId → seeds account + balances + first-page
 *     transactions (IAM OIDC token, server-side);
 *   - 403 → `forbidden` (inline, no crash);
 *   - 404 ACCOUNT_NOT_FOUND → `notFound` (inline actionable, no
 *     crash);
 *   - 503 / timeout → `degraded` (finance section only — shell
 *     intact);
 *   - **no 429 / Retry-After branch** — a stray 429 lands as a
 *     generic ApiError → `degraded` (no fabricated backoff path).
 *
 * 401 → whole-session re-login (redirect) is exercised here too.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getFinanceSectionState } from '@/features/finance-ops/api/finance-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function financeError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ACCOUNT_ENV = {
  data: {
    accountId: 'acct-1',
    status: 'ACTIVE',
    currency: 'KRW',
    kycLevel: 'BASIC',
  },
  meta: { timestamp: 'x' },
};
const BALANCES_ENV = {
  data: [
    {
      currency: 'KRW',
      ledger: '100',
      available: '100',
      held: '0',
    },
  ],
  meta: { timestamp: 'x' },
};
const TXNS_ENV = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
};

function routed() {
  return vi.fn((url: string, _init?: RequestInit) => {
    const u = String(url);
    if (u.includes('/balances'))
      return Promise.resolve(jsonResponse(BALANCES_ENV));
    if (u.includes('/transactions'))
      return Promise.resolve(jsonResponse(TXNS_ENV));
    return Promise.resolve(jsonResponse(ACCOUNT_ENV));
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getFinanceSectionState — eligibility + account-id gates (§ 2.4.7)', () => {
  it('not eligible → notEligible block, NO finance call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getFinanceSectionState(false, 'acct-1');
    expect(state.notEligible).toBe(true);
    expect(state.account).toBeNull();
    expect(state.balances).toBeNull();
    expect(state.transactions).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible but NO accountId → empty (initial) state, NO finance call (no list/search fabricated)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getFinanceSectionState(true, null);
    expect(state.notEligible).toBe(false);
    expect(state.account).toBeNull();
    expect(state.balances).toBeNull();
    expect(state.transactions).toBeNull();
    expect(state.degraded).toBe(false);
    expect(state.forbidden).toBe(false);
    expect(state.notFound).toBe(false);
    // The honest finance constraint: no fabricated list/search call.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible + accountId → seeds account + balances + transactions (IAM OIDC token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routed();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getFinanceSectionState(true, 'acct-1');
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.account).not.toBeNull();
    expect(state.balances).not.toBeNull();
    expect(state.transactions).not.toBeNull();
    // Every seeded read carries the IAM OIDC access token, no
    // X-Tenant-Id.
    for (const [, init] of fetchMock.mock.calls) {
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
      expect(h['X-Tenant-Id']).toBeUndefined();
    }
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(financeError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getFinanceSectionState(true, 'acct-1');
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('404 ACCOUNT_NOT_FOUND → notFound (inline actionable, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(financeError('ACCOUNT_NOT_FOUND', 404))),
    );
    const state = await getFinanceSectionState(true, 'nope');
    expect(state.notFound).toBe(true);
    expect(state.degraded).toBe(false);
    expect(state.forbidden).toBe(false);
  });

  it('503 → degraded (finance section only — shell + GAP/wms/scm sections intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(financeError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getFinanceSectionState(true, 'acct-1');
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('a stray 429 (no documented finance 429) → degraded (NOT a fabricated backoff)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // ONE fetch only — no client retry / Retry-After honour.
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
        {
          status: 429,
          headers: {
            'Content-Type': 'application/json',
            'Retry-After': '1',
          },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getFinanceSectionState(true, 'acct-1');
    // A 429 lands as an ApiError, which finance-state treats as a
    // degrade (any-other-error → degrade), NOT a retry storm.
    expect(state.degraded).toBe(true);
    expect(state.notFound).toBe(false);
    // ONE upstream fetch per leg — Promise.all 3 = 3 fetches max, but
    // each leg attempted exactly once (no retry from a 429 honour).
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(3);
    for (const [, init] of fetchMock.mock.calls) {
      expect((init as RequestInit).method).toBe('GET');
    }
  });

  it('401 → whole-session re-login (redirect, not a per-section degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(financeError('UNAUTHORIZED', 401))),
    );
    const err = await getFinanceSectionState(true, 'acct-1').catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
