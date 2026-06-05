import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin composed-overview read proxy route handler (TASK-PC-FE-005 —
 * READ-ONLY composed fan-out):
 *   - GET only — no body schema, no mutation branch; the operator token +
 *     tenant are attached server-side inside the reused FE-002/003/004
 *     clients; NO X-Operator-Reason / Idempotency-Key are ever sent.
 *   - per-card 503/403 is INSIDE the 200 payload (a card status, never an
 *     HTTP error) — per-source isolation; the shell never blanks.
 *   - 401 on ANY leg → 401 (whole-overview re-login; no partial state).
 *   - no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; never empty).
 *   - whole-fan-out failure → 503 (overview degrades; shell intact).
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

import { GET as overviewGET } from '@/app/api/dashboards/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ACCOUNTS_PAGE = {
  content: [],
  totalElements: 9,
  page: 0,
  size: 20,
  totalPages: 1,
};
const AUDIT_PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};
const OPERATORS_PAGE = {
  content: [],
  totalElements: 3,
  page: 0,
  size: 20,
  totalPages: 1,
};

function routedFetch(
  overrides: Partial<{ accounts: Response; audit: Response; operators: Response }> = {},
) {
  return vi.fn((url: string, _init?: RequestInit) => {
    const u = String(url);
    if (u.includes('/api/admin/accounts'))
      return Promise.resolve(overrides.accounts ?? jsonResponse(ACCOUNTS_PAGE));
    if (u.includes('/api/admin/audit'))
      return Promise.resolve(overrides.audit ?? jsonResponse(AUDIT_PAGE));
    if (u.includes('/api/admin/operators'))
      return Promise.resolve(
        overrides.operators ?? jsonResponse(OPERATORS_PAGE),
      );
    return Promise.resolve(jsonResponse({}, 500));
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/dashboards proxy', () => {
  it('composes the bounded fan-out and returns the overview (operator token, no reason/idempotency)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);

    const res = await overviewGET();
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.accounts.status).toBe('ok');
    expect(body.audit.status).toBe('ok');
    expect(body.operators.status).toBe('ok');

    expect(fetchMock).toHaveBeenCalledTimes(3);
    for (const [, init] of fetchMock.mock.calls) {
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer OP');
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
    }
  });

  it('per-card 503 stays a 200 payload card status (per-source isolation — shell never blanks)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      routedFetch({ accounts: jsonResponse({ code: 'CIRCUIT_OPEN' }, 503) }),
    );
    const res = await overviewGET();
    expect(res.status).toBe(200); // NOT a 503 — one source down ≠ whole fail
    const body = await res.json();
    expect(body.accounts.status).toBe('degraded');
    expect(body.audit.status).toBe('ok');
    expect(body.operators.status).toBe('ok');
  });

  it('401 on a leg → 401 (whole-overview forced re-login, no partial authed state)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      routedFetch({ operators: jsonResponse({ code: 'TOKEN_INVALID' }, 401) }),
    );
    const res = await overviewGET();
    expect(res.status).toBe(401);
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT (tenant gate, never empty, NO fetch)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);
    const res = await overviewGET();
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
