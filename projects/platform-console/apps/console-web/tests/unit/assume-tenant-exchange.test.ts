import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Server-only RFC 8693 **assume-tenant** exchange
 * (`shared/lib/assume-tenant-exchange.ts`) — ADR-MONO-020 D4 / § 2.7.
 *
 * Authoritative producer contract (verbatim):
 *   iam/specs/contracts/http/auth-api.md
 *   § Assume-Tenant Exchange (form-urlencoded `POST /oauth2/token`,
 *   token-exchange grant + `audience`, SAS response, NO refresh_token).
 *
 * `fetch` + `getServerEnv()` mocked (same lane as the operator-exchange test).
 */

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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

const logged: string[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    debug: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    info: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    warn: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    error: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
  },
  newRequestId: () => 'req-test',
}));

import { exchangeForAssumedToken } from '@/shared/lib/assume-tenant-exchange';
import { AssumeTenantError } from '@/shared/api/errors';

const SUBJECT = 'base.gap.oidc.access.token.SECRET';

function sasOk(body: Record<string, unknown>, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  logged.length = 0;
  vi.unstubAllGlobals();
});

describe('exchangeForAssumedToken — success', () => {
  it('returns { accessToken, expiresIn } on a 200 SAS Bearer response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        sasOk({
          access_token: 'assumed.jwt.value',
          issued_token_type:
            'urn:ietf:params:oauth:token-type:access_token',
          token_type: 'Bearer',
          expires_in: 1800,
        }),
      ),
    );
    const out = await exchangeForAssumedToken(SUBJECT, 'acme-corp');
    expect(out).toEqual({ accessToken: 'assumed.jwt.value', expiresIn: 1800 });
  });

  it('POSTs the EXACT form-urlencoded RFC 8693 body (audience) to /oauth2/token', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        sasOk({ access_token: 'x', token_type: 'Bearer', expires_in: 60 }),
      );
    vi.stubGlobal('fetch', fetchMock);
    await exchangeForAssumedToken(SUBJECT, 'globex-corp');

    const [url, init] = fetchMock.mock.calls[0];
    // SAS /oauth2/token, derived from OIDC_ISSUER_URL (NOT the admin JSON URL).
    expect(url).toBe('http://iam.local/oauth2/token');
    expect((init as RequestInit).method).toBe('POST');
    // form-urlencoded, NOT JSON (the § 2.6 admin shape).
    expect(
      ((init as RequestInit).headers as Record<string, string>)[
        'Content-Type'
      ],
    ).toBe('application/x-www-form-urlencoded');

    const sent = new URLSearchParams((init as RequestInit).body as string);
    expect(sent.get('grant_type')).toBe(
      'urn:ietf:params:oauth:grant-type:token-exchange',
    );
    expect(sent.get('subject_token')).toBe(SUBJECT);
    expect(sent.get('subject_token_type')).toBe(
      'urn:ietf:params:oauth:token-type:access_token',
    );
    expect(sent.get('audience')).toBe('globex-corp');
    expect(sent.get('client_id')).toBe('platform-console-web');
  });

  it('never logs the subject token or the minted assumed token', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        sasOk({
          access_token: 'assumed.SUPER.SECRET.jwt',
          token_type: 'Bearer',
          expires_in: 1800,
        }),
      ),
    );
    await exchangeForAssumedToken(SUBJECT, 'acme-corp');
    const all = logged.join('\n');
    expect(all).not.toContain(SUBJECT);
    expect(all).not.toContain('assumed.SUPER.SECRET.jwt');
  });
});

describe('exchangeForAssumedToken — fail-closed (denied)', () => {
  it('400 invalid_grant (assignment-denied / subject-invalid) → denied', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(sasOk({ error: 'invalid_grant' }, 400)),
    );
    await expect(
      exchangeForAssumedToken(SUBJECT, 'globex-corp'),
    ).rejects.toMatchObject({
      name: 'AssumeTenantError',
      reason: 'denied',
    });
  });

  it('400 with no OAuth error field → denied (default fail-closed)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sasOk({}, 400)));
    await expect(
      exchangeForAssumedToken(SUBJECT, 'globex-corp'),
    ).rejects.toMatchObject({ reason: 'denied' });
  });
});

describe('exchangeForAssumedToken — invalid (bad audience)', () => {
  it('400 invalid_request → invalid', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(sasOk({ error: 'invalid_request' }, 400)),
    );
    await expect(
      exchangeForAssumedToken(SUBJECT, 'acme-corp'),
    ).rejects.toMatchObject({ reason: 'invalid' });
  });

  it('blank selected tenant → invalid WITHOUT any network call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(exchangeForAssumedToken(SUBJECT, '   ')).rejects.toMatchObject(
      { reason: 'invalid', code: 'AUDIENCE_REQUIRED' },
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('exchangeForAssumedToken — unavailable (never falls back)', () => {
  it('503 → unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(sasOk({ error: 'server_error' }, 503)),
    );
    await expect(
      exchangeForAssumedToken(SUBJECT, 'acme-corp'),
    ).rejects.toMatchObject({ reason: 'unavailable' });
  });

  it('network failure → unavailable, no token in the error message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new Error('ECONNREFUSED iam.local')),
    );
    const err = await exchangeForAssumedToken(SUBJECT, 'acme-corp').catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(AssumeTenantError);
    expect(err.reason).toBe('unavailable');
    expect(err.message).not.toContain(SUBJECT);
  });

  it('AbortController hard timeout → unavailable (TIMEOUT)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            reject(e);
          });
        });
      }),
    );
    await expect(
      exchangeForAssumedToken(SUBJECT, 'acme-corp'),
    ).rejects.toMatchObject({ reason: 'unavailable', code: 'TIMEOUT' });
  });

  it('unexpected token_type (≠ Bearer) → unavailable, NOT stored', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        sasOk({
          access_token: 'something',
          token_type: 'admin',
          expires_in: 1800,
        }),
      ),
    );
    await expect(
      exchangeForAssumedToken(SUBJECT, 'acme-corp'),
    ).rejects.toMatchObject({
      reason: 'unavailable',
      code: 'BAD_RESPONSE_SHAPE',
    });
  });
});
