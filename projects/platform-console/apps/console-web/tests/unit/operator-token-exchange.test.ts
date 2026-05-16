import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Server-only RFC 8693 operator-token exchange
 * (`shared/lib/operator-token-exchange.ts`).
 *
 * Authoritative producer contract (verbatim):
 *   global-account-platform/specs/contracts/http/admin-api.md
 *   § POST /api/admin/auth/token-exchange
 *   + console-integration-contract.md § 2.6 (ADR-MONO-014 fail-closed map).
 *
 * `fetch` is mocked; getServerEnv() is mocked (same lane as FE-001 tests).
 */

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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

// Capture all logger output so we can assert no token value is ever logged.
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

import { exchangeForOperatorToken } from '@/shared/lib/operator-token-exchange';
import { OperatorExchangeError } from '@/shared/api/errors';

const SUBJECT = 'gap.oidc.access.token.value.SECRET';

beforeEach(() => {
  logged.length = 0;
  vi.unstubAllGlobals();
});

describe('exchangeForOperatorToken — success', () => {
  it('returns { accessToken, expiresIn } on a 200 admin response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            accessToken: 'op.jwt.value',
            expiresIn: 3600,
            tokenType: 'admin',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const out = await exchangeForOperatorToken(SUBJECT);
    expect(out).toEqual({ accessToken: 'op.jwt.value', expiresIn: 3600 });
  });

  it('POSTs exactly the RFC 8693 body shape to the producer URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ accessToken: 'x', expiresIn: 60, tokenType: 'admin' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    await exchangeForOperatorToken(SUBJECT);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://gap.local/api/admin/auth/token-exchange');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    // Exactly the three RFC 8693 fields — no extras.
    expect(Object.keys(body).sort()).toEqual([
      'grant_type',
      'subject_token',
      'subject_token_type',
    ]);
    expect(body.grant_type).toBe(
      'urn:ietf:params:oauth:grant-type:token-exchange',
    );
    expect(body.subject_token).toBe(SUBJECT);
    expect(body.subject_token_type).toBe(
      'urn:ietf:params:oauth:token-type:access_token',
    );
  });

  it('never logs the subject token or the minted operator token', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            accessToken: 'op.SUPER.SECRET.jwt',
            expiresIn: 3600,
            tokenType: 'admin',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    await exchangeForOperatorToken(SUBJECT);
    const all = logged.join('\n');
    expect(all).not.toContain(SUBJECT);
    expect(all).not.toContain('op.SUPER.SECRET.jwt');
  });
});

describe('exchangeForOperatorToken — fail-closed (401)', () => {
  it('throws OperatorExchangeError(fail_closed) on 401 TOKEN_INVALID', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(exchangeForOperatorToken(SUBJECT)).rejects.toMatchObject({
      name: 'OperatorExchangeError',
      reason: 'fail_closed',
      code: 'TOKEN_INVALID',
    });
  });
});

describe('exchangeForOperatorToken — unavailable', () => {
  it('400 BAD_REQUEST → unavailable (no fallback)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'BAD_REQUEST' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(exchangeForOperatorToken(SUBJECT)).rejects.toMatchObject({
      name: 'OperatorExchangeError',
      reason: 'unavailable',
    });
  });

  it('503 → unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'DOWNSTREAM_ERROR' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(exchangeForOperatorToken(SUBJECT)).rejects.toMatchObject({
      reason: 'unavailable',
    });
  });

  it('network failure → unavailable (no token in the error message)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new Error('ECONNREFUSED gap.local')),
    );
    const err = await exchangeForOperatorToken(SUBJECT).catch((e) => e);
    expect(err).toBeInstanceOf(OperatorExchangeError);
    expect(err.reason).toBe('unavailable');
    expect(err.message).not.toContain(SUBJECT);
  });

  it('AbortController hard timeout → unavailable (TIMEOUT)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          const signal = init?.signal;
          signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            reject(e);
          });
        });
      }),
    );
    await expect(exchangeForOperatorToken(SUBJECT)).rejects.toMatchObject({
      name: 'OperatorExchangeError',
      reason: 'unavailable',
      code: 'TIMEOUT',
    });
  });

  it('unexpected tokenType (≠ admin) → unavailable, NOT stored', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            accessToken: 'something',
            expiresIn: 3600,
            tokenType: 'bearer',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    await expect(exchangeForOperatorToken(SUBJECT)).rejects.toMatchObject({
      reason: 'unavailable',
      code: 'BAD_RESPONSE_SHAPE',
    });
  });
});
