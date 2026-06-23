import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-120 — `/api/auth/refresh` fires the operator re-exchange (§ 2.6)
 * and the assume-tenant re-exchange (§ 2.7) CONCURRENTLY off the rotated base
 * token, instead of sequentially. Both are independent RFC 8693 grants, so the
 * refresh latency drops from `oidc + operator + assume` to
 * `oidc + max(operator, assume)`.
 *
 * Concurrency proof: hold the operator-exchange fetch PENDING and assert the
 * assume-exchange fetch is still issued. The old sequential shape would not
 * call assume until operator resolved — so with operator hanging, assume would
 * never fire.
 */

const cookieJar = new Map<string, { value: string }>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string) => {
    cookieJar.set(name, { value });
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 5000,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { POST as refreshPOST } from '@/app/api/auth/refresh/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
const tick = () => new Promise((r) => setTimeout(r, 0));

/** Classify a refresh-route fetch call by URL + body. */
function classify(url: string, init?: RequestInit): 'operator' | 'assume' | 'refresh' {
  if (String(url).includes('/api/admin/auth/token-exchange')) return 'operator';
  const body = String(init?.body ?? '');
  if (body.includes('grant-type%3Atoken-exchange')) return 'assume';
  return 'refresh';
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('POST /api/auth/refresh — concurrent operator + assume re-exchange (TASK-PC-FE-120)', () => {
  it('issues the assume-tenant exchange while the operator exchange is still pending (no waterfall)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    cookieJar.set(TENANT_COOKIE, { value: 'acme-corp' });

    let resolveOperator!: (r: Response) => void;
    const operatorDeferred = new Promise<Response>((r) => {
      resolveOperator = r;
    });
    let assumeIssued = false;

    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: RequestInit) => {
        switch (classify(url, init)) {
          case 'operator':
            return operatorDeferred; // hangs until we resolve it
          case 'assume':
            assumeIssued = true;
            return Promise.resolve(
              json({ access_token: 'new.assumed', token_type: 'Bearer', expires_in: 1800 }),
            );
          default:
            return Promise.resolve(
              json({
                access_token: 'new.acc',
                token_type: 'Bearer',
                expires_in: 1800,
                refresh_token: 'new.ref',
              }),
            );
        }
      }),
    );

    const pending = refreshPOST();
    await tick();

    // Operator is still pending, yet assume has already been issued — proves
    // the two exchanges run concurrently (a waterfall would gate assume behind
    // the operator response and assumeIssued would be false here).
    expect(assumeIssued).toBe(true);

    // Let the operator exchange complete so the route finishes cleanly (also
    // clears its AbortController timer — no lingering timer across tests).
    resolveOperator(json({ accessToken: 'new.op', expiresIn: 600, tokenType: 'admin' }));
    const res = await pending;

    expect(res.status).toBe(200);
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('new.op');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('new.assumed');
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('acme-corp');
  });

  it('operator re-exchange 401 with an active tenant: drops the whole session, the speculative assume is ignored (no unhandled rejection, no stale assumed token)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    cookieJar.set(OPERATOR_COOKIE, { value: 'stale.op' });
    cookieJar.set(TENANT_COOKIE, { value: 'acme-corp' });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'stale.assumed' });

    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: RequestInit) => {
        switch (classify(url, init)) {
          case 'operator':
            return Promise.resolve(json({ code: 'TOKEN_INVALID' }, 401));
          case 'assume':
            // Speculatively fired; its result must be discarded. Even a reject
            // must not surface as an unhandled rejection (guarded by the
            // up-front .catch in the route).
            return Promise.resolve(json({ error: 'invalid_grant' }, 400));
          default:
            return Promise.resolve(
              json({
                access_token: 'new.acc',
                token_type: 'Bearer',
                expires_in: 1800,
                refresh_token: 'new.ref',
              }),
            );
        }
      }),
    );

    const res = await refreshPOST();

    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
    // Whole session dropped — no stale operator/assumed token survives.
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
    expect(cookieDeletes).toContain(REFRESH_COOKIE);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
    expect(cookieDeletes).toContain(ASSUMED_TOKEN_COOKIE);
    expect(cookieDeletes).toContain(TENANT_COOKIE);
    // The speculative assume never set a fresh assumed cookie.
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });

  it('no active tenant: only the operator exchange runs (assume not fired)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });

    let assumeIssued = false;
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: RequestInit) => {
        const kind = classify(url, init);
        if (kind === 'assume') assumeIssued = true;
        if (kind === 'operator') {
          return Promise.resolve(json({ accessToken: 'new.op', expiresIn: 600, tokenType: 'admin' }));
        }
        return Promise.resolve(
          json({ access_token: 'new.acc', token_type: 'Bearer', expires_in: 1800, refresh_token: 'new.ref' }),
        );
      }),
    );

    const res = await refreshPOST();
    expect(res.status).toBe(200);
    expect(assumeIssued).toBe(false);
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('new.op');
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });
});
