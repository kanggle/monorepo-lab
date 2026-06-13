import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/scm-replenishment/api/replenishment-state.ts` — the server-side
 * section state (TASK-PC-FE-077 / § 2.4.6.1):
 *   - not scm-eligible → `notEligible` block, NO scm call fabricated;
 *   - eligible → seeds the suggestions list (domain-facing IAM OIDC token,
 *     server-side);
 *   - 403 → `forbidden` (inline, no crash);
 *   - 429 → `rateLimited` (degrade with notice; one bounded backoff already
 *     done — no further storm);
 *   - 503/timeout → `degraded` (this section only — shell intact);
 *   - 401 → whole-session re-login (redirect).
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getReplenishmentSectionState } from '@/features/scm-replenishment/api/replenishment-state';
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

const SUGGESTIONS_ENV = {
  data: [{ id: 's-1', skuCode: 'SKU-1', status: 'SUGGESTED' }],
  meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getReplenishmentSectionState — eligibility gate (§ 2.4.6.1)', () => {
  it('not eligible → notEligible block, NO scm call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getReplenishmentSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.suggestions).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds the suggestions list (IAM OIDC token, server-side, no X-Tenant-Id)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUGGESTIONS_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const state = await getReplenishmentSectionState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.suggestions).not.toBeNull();
    expect(state.suggestions!.content).toHaveLength(1);
    const h = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<
      string,
      string
    >;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(scmError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getReplenishmentSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('persisting 429 → rateLimited (one bounded backoff, no storm)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
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
      ),
    );
    const state = await getReplenishmentSectionState(true);
    expect(state.rateLimited).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('503 → degraded (this section only — shell intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(scmError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getReplenishmentSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('401 → whole-session re-login (redirect)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(scmError('UNAUTHORIZED', 401))),
    );
    const err = await getReplenishmentSectionState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
