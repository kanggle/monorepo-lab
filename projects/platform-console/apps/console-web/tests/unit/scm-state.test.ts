import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/scm-ops/api/scm-state.ts` — the server-side section state
 * (TASK-PC-FE-008 / § 2.4.6):
 *   - not scm-eligible → `notEligible` block, NO scm call fabricated
 *     (no cross-tenant call; the console never sends a tenant — scm
 *     resolves it from the JWT claim);
 *   - eligible → seeds PO list + snapshot + staleness (GAP OIDC token,
 *     server-side);
 *   - 403 → `forbidden` (inline, no crash);
 *   - 429 → `rateLimited` (degrade with notice; api client already did
 *     ONE bounded backoff — no further storm);
 *   - 503/timeout → `degraded` (scm section only — shell intact);
 *   - the S5 meta.warning rides through the snapshot view-model.
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
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getScmSectionState } from '@/features/scm-ops/api/scm-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function scmError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const PO_ENV = {
  data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
  meta: { timestamp: 'x' },
};
const SNAP_ENV = {
  data: { content: [], page: 0, size: 20, totalElements: 0 },
  meta: { warning: 'Not for procurement decisions (S5)' },
};
const STALE_ENV = {
  data: [],
  meta: { warning: 'Not for procurement decisions (S5)' },
};

function routed() {
  return vi.fn((url: string) => {
    const u = String(url);
    if (u.includes('/procurement/po'))
      return Promise.resolve(jsonResponse(PO_ENV));
    if (u.includes('/snapshot'))
      return Promise.resolve(jsonResponse(SNAP_ENV));
    if (u.includes('/staleness'))
      return Promise.resolve(jsonResponse(STALE_ENV));
    return Promise.resolve(jsonResponse({ data: [], meta: {} }));
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getScmSectionState — eligibility gate (§ 2.4.6)', () => {
  it('not eligible → notEligible block, NO scm call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getScmSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.poList).toBeNull();
    expect(state.snapshot).toBeNull();
    expect(state.staleness).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds PO + snapshot + staleness (GAP OIDC token, server-side)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routed();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getScmSectionState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.poList).not.toBeNull();
    expect(state.snapshot).not.toBeNull();
    expect(state.staleness).not.toBeNull();
    // The S5 warning rides through (never stripped).
    expect(state.snapshot!.meta.warning).toBe(
      'Not for procurement decisions (S5)',
    );
    // Every seeded read carries the GAP OIDC access token.
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
      vi.fn(() => Promise.resolve(scmError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getScmSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('persisting 429 → rateLimited (degrade w/ notice; ONE bounded backoff, no storm)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn(() =>
      Promise.resolve(
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
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getScmSectionState(true);
    expect(state.rateLimited).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('503 → degraded (scm section only — shell intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(scmError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getScmSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('401 → whole-session re-login (redirect, not a per-section degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(scmError('UNAUTHORIZED', 401))),
    );
    const err = await getScmSectionState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
