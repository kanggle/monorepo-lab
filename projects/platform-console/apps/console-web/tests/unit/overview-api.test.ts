import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/dashboards/api/overview-api.ts` — the security-critical core of
 * TASK-PC-FE-005 (READ-ONLY composed overview, ADR-MONO-015 D1-B).
 *
 * Asserts (console-integration-contract § 2.4.4 / ADR-MONO-015 D1/D2/D3):
 *   - the fan-out reuses the EXISTING FE-002/003/004 server clients —
 *     every leg's bearer is the EXCHANGED operator cookie, NEVER the GAP
 *     OIDC access token (the #569 trust-boundary invariant, inherited);
 *   - `X-Tenant-Id` is the active-tenant cookie value on EVERY leg;
 *   - **NO `X-Operator-Reason` and NO `Idempotency-Key`** on ANY leg
 *     (read-only — no mutation artifacts; the FE-002/004 mutation
 *     scaffolding must NOT leak here);
 *   - the fan-out is bounded — each leg has a timeout (no unbounded
 *     default) and ONE overview load = ONE bounded set of calls (exactly
 *     3 fetches, no auto-refetch storm into the meta-audited audit leg);
 *   - per-source isolation: accounts 503 → accounts card `degraded`,
 *     audit + operators still `ok`; operators 403 → operators card
 *     `forbidden`, others `ok`;
 *   - **401 on ANY leg → the WHOLE fan-out rejects with ApiError(401)**
 *     (whole-overview re-login — auth is NOT a per-card degrade);
 *   - all sources down → all cards `degraded` (no throw, no crash).
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..004 lane).
 * The reused clients are NOT mocked — the real `searchAccounts` /
 * `queryAudit` / `listOperators` run against a mocked `fetch`, so this
 * also proves the composition does not duplicate / bypass them.
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
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

import { getOperatorOverview } from '@/features/dashboards/api/overview-api';
import { ApiError } from '@/shared/api/errors';
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

const ACCOUNTS_PAGE = {
  content: [
    { id: 'a-1', email: 'a@x.io', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
  ],
  totalElements: 150,
  page: 0,
  size: 20,
  totalPages: 8,
};
const AUDIT_PAGE = {
  content: [
    {
      source: 'admin',
      auditId: 'aud-1',
      actionCode: 'ACCOUNT_LOCK',
      operatorId: 'op-1',
      targetId: 'acc-1',
      reason: 'fraud',
      outcome: 'SUCCESS',
      occurredAt: '2026-04-12T10:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 42,
  totalPages: 3,
};
const OPERATORS_PAGE = {
  content: [
    {
      operatorId: 'op-1',
      email: 'op@x.io',
      displayName: 'Op One',
      status: 'ACTIVE',
      roles: ['SUPER_ADMIN'],
      createdAt: '2026-01-01T00:00:00Z',
    },
    {
      operatorId: 'op-2',
      email: 'op2@x.io',
      displayName: 'Op Two',
      status: 'SUSPENDED',
      roles: ['SUPPORT_LOCK'],
      createdAt: '2026-01-02T00:00:00Z',
    },
  ],
  totalElements: 7,
  page: 0,
  size: 20,
  totalPages: 1,
};

/** Routes the mocked fetch to the right page by URL — proves the fan-out
 *  hits exactly the three EXISTING producer reads. */
function routedFetch(
  overrides: Partial<{
    accounts: Response | (() => Response | Promise<never>);
    audit: Response | (() => Response | Promise<never>);
    operators: Response | (() => Response | Promise<never>);
  }> = {},
) {
  return vi.fn((url: string, init?: RequestInit) => {
    const u = String(url);
    const pick = (
      def: unknown,
      o?: Response | (() => Response | Promise<never>),
    ) => {
      if (o === undefined) return Promise.resolve(jsonResponse(def));
      if (typeof o === 'function') {
        const r = o();
        return r instanceof Response ? Promise.resolve(r) : r;
      }
      return Promise.resolve(o);
    };
    if (u.includes('/api/admin/accounts'))
      return pick(ACCOUNTS_PAGE, overrides.accounts);
    if (u.includes('/api/admin/audit'))
      return pick(AUDIT_PAGE, overrides.audit);
    if (u.includes('/api/admin/operators'))
      return pick(OPERATORS_PAGE, overrides.operators);
    // timeout simulation
    return new Promise((_res, rej) => {
      init?.signal?.addEventListener('abort', () => {
        const e = new Error('aborted');
        e.name = 'AbortError';
        rej(e);
      });
    });
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('overview-api — composition over the EXISTING reads (no new producer)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('fans out to exactly the 3 existing IAM read endpoints, one bounded set per load', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    const overview = await getOperatorOverview();

    // exactly 3 calls — one bounded set, NO auto-refetch storm
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const urls = fetchMock.mock.calls.map((c) => String(c[0]));
    expect(urls.some((u) => u.includes('/api/admin/accounts'))).toBe(true);
    expect(urls.some((u) => u.includes('/api/admin/audit'))).toBe(true);
    expect(urls.some((u) => u.includes('/api/admin/operators'))).toBe(true);

    expect(overview.accounts.status).toBe('ok');
    expect(overview.accounts.totalElements).toBe(150);
    expect(overview.audit.status).toBe('ok');
    expect(overview.audit.totalElements).toBe(42);
    expect(overview.audit.latestOccurredAt).toBe('2026-04-12T10:00:00Z');
    expect(overview.operators.status).toBe('ok');
    expect(overview.operators.totalElements).toBe(7);
    expect(overview.operators.activeCount).toBe(1);
    expect(overview.operators.suspendedCount).toBe(1);
  });

  it('every leg sends the OPERATOR cookie as the bearer, NOT the IAM token, with X-Tenant-Id', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    await getOperatorOverview();

    expect(fetchMock.mock.calls).toHaveLength(3);
    for (const [, init] of fetchMock.mock.calls) {
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
      expect(h.Authorization).not.toContain('GAP-OIDC-ACCESS-must-not-leak');
      expect(h['X-Tenant-Id']).toBe('wms');
    }
  });

  it('READ-ONLY: NO X-Operator-Reason / Idempotency-Key on ANY leg, every leg is a GET with no body', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    await getOperatorOverview();

    expect(fetchMock.mock.calls).toHaveLength(3);
    for (const [, init] of fetchMock.mock.calls) {
      expect((init as RequestInit).method).toBe('GET');
      expect((init as RequestInit).body).toBeUndefined();
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
    }
  });

  it('every leg is bounded (an AbortController signal is attached — no unbounded default)', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    await getOperatorOverview();

    expect(fetchMock.mock.calls).toHaveLength(3);
    for (const [, init] of fetchMock.mock.calls) {
      expect((init as RequestInit).signal).toBeInstanceOf(AbortSignal);
    }
  });

  it('blocks (NO fetch) when no active tenant — never an empty X-Tenant-Id on any leg', async () => {
    cookieJar.delete(TENANT_COOKIE);
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    // The reused clients each raise NO_ACTIVE_TENANT before any fetch;
    // the overview isolates that as a per-card non-ok status, no fetch.
    const overview = await getOperatorOverview();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(overview.accounts.status).not.toBe('ok');
    expect(overview.audit.status).not.toBe('ok');
    expect(overview.operators.status).not.toBe('ok');
  });
});

describe('overview-api — per-source isolation (the key design point)', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('accounts 503 → accounts card degraded; audit + operators still ok', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        accounts: () => jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503),
      }),
    );
    const overview = await getOperatorOverview();
    expect(overview.accounts.status).toBe('degraded');
    expect(overview.accounts.totalElements).toBeNull();
    expect(overview.audit.status).toBe('ok');
    expect(overview.operators.status).toBe('ok');
  });

  it('operators 403 → operators card forbidden; accounts + audit still ok', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        operators: () => jsonResponse({ code: 'PERMISSION_DENIED' }, 403),
      }),
    );
    const overview = await getOperatorOverview();
    expect(overview.operators.status).toBe('forbidden');
    expect(overview.accounts.status).toBe('ok');
    expect(overview.audit.status).toBe('ok');
  });

  it('audit 403 (intersection-permission) → audit card forbidden; others ok', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        audit: () => jsonResponse({ code: 'PERMISSION_DENIED' }, 403),
      }),
    );
    const overview = await getOperatorOverview();
    expect(overview.audit.status).toBe('forbidden');
    expect(overview.accounts.status).toBe('ok');
    expect(overview.operators.status).toBe('ok');
  });

  it('all sources down → ALL cards degraded (no throw, no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        accounts: () => jsonResponse({ code: 'CIRCUIT_OPEN' }, 503),
        audit: () => jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503),
        operators: () => jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503),
      }),
    );
    const overview = await getOperatorOverview();
    expect(overview.accounts.status).toBe('degraded');
    expect(overview.audit.status).toBe('degraded');
    expect(overview.operators.status).toBe('degraded');
  });

  it('a leg timeout degrades ONLY that card (bounded — others ok)', async () => {
    // accounts hangs → AbortController fires → AccountsUnavailableError
    vi.stubGlobal(
      'fetch',
      routedFetch({
        accounts: () =>
          new Promise((_res, rej) => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            setTimeout(() => rej(e), 80); // > ACCOUNTS_TIMEOUT_MS (50)
          }) as unknown as Promise<never>,
      }),
    );
    const overview = await getOperatorOverview();
    expect(overview.accounts.status).toBe('degraded');
    expect(overview.audit.status).toBe('ok');
    expect(overview.operators.status).toBe('ok');
  });
});

describe('overview-api — 401 on ANY leg = whole-overview re-login (NOT a per-card degrade)', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('a 401 on the accounts leg rejects the WHOLE fan-out with ApiError(401)', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        accounts: () => jsonResponse({ code: 'TOKEN_INVALID' }, 401),
      }),
    );
    const err = await getOperatorOverview().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('a 401 on the operators leg also rejects the WHOLE fan-out (not just that card)', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        operators: () => jsonResponse({ code: 'TOKEN_INVALID' }, 401),
      }),
    );
    const err = await getOperatorOverview().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('a 401 is NEVER returned as a per-card status (no partial authed state)', async () => {
    vi.stubGlobal(
      'fetch',
      routedFetch({
        audit: () => jsonResponse({ code: 'TOKEN_INVALID' }, 401),
      }),
    );
    // It must THROW, not resolve with audit:'degraded'|'forbidden'.
    await expect(getOperatorOverview()).rejects.toBeInstanceOf(ApiError);
  });
});
