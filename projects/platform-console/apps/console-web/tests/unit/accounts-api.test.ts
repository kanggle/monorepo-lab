import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/accounts/api/accounts-api.ts` — the security-critical core of
 * TASK-PC-FE-002.
 *
 * Asserts (console-integration-contract § 2.4.1 / IAM admin-api.md):
 *   - the bearer is the EXCHANGED operator cookie, NEVER the IAM OIDC
 *     access token (the #569 trust-boundary invariant);
 *   - `X-Tenant-Id` is the active-tenant cookie value (never empty);
 *   - mutations carry a non-empty operator-entered `X-Operator-Reason`
 *     AND a non-empty `Idempotency-Key`;
 *   - no-operator-token ⇒ 401 with NO fetch (no silent GAP-token fallback);
 *   - no-active-tenant ⇒ blocked with NO fetch (no cross-tenant/empty);
 *   - 401/403 → ApiError (re-login); 503/timeout → AccountsUnavailableError
 *     (section degrades only); 400/404/409/422 → ApiError (inline).
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001/FE-002a lane).
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  searchAccounts,
  lockAccount,
  unlockAccount,
  bulkLockAccounts,
  revokeSessions,
  gdprDeleteAccount,
  exportAccount,
} from '@/features/accounts/api/accounts-api';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const PAGE_200 = {
  content: [
    { id: 'acc-1', email: 'a@x.com', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};

const LOCK_200 = {
  accountId: 'acc-1',
  previousStatus: 'ACTIVE',
  currentStatus: 'LOCKED',
  operatorId: 'op-1',
  lockedAt: '2026-04-12T10:00:00Z',
  auditId: 'aud-1',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('accounts-api — operator-token trust boundary (#569 invariant)', () => {
  it('search sends the OPERATOR cookie as the bearer, NOT the IAM token, with X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE_200));
    vi.stubGlobal('fetch', fetchMock);

    await searchAccounts({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(headers.Authorization).not.toContain('GAP-OIDC-ACCESS-must-not-leak');
    expect(headers['X-Tenant-Id']).toBe('wms');
    expect(String(url)).toContain('/api/admin/accounts');
  });

  it('throws 401 with NO fetch when the operator token is absent (no IAM fallback)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-only');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await searchAccounts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('blocks (NO fetch) when no active tenant is selected — never empty X-Tenant-Id', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await searchAccounts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('accounts-api — mutations carry reason + idempotency-key', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('lock sends X-Operator-Reason + a non-empty Idempotency-Key + body reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LOCK_200));
    vi.stubGlobal('fetch', fetchMock);

    await lockAccount('acc-1', { reason: 'fraud investigation' }, 'idem-key-1');

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    // TASK-MONO-176: reason percent-encoded on the wire (ByteString header
    // safety); round-trips via decodeURIComponent.
    expect(decodeURIComponent(headers['X-Operator-Reason'])).toBe(
      'fraud investigation',
    );
    expect(headers['Idempotency-Key']).toBe('idem-key-1');
    expect(headers['Idempotency-Key'].length).toBeGreaterThan(0);
    expect(JSON.parse((init as RequestInit).body as string)).toMatchObject({
      reason: 'fraud investigation',
    });
  });

  it('rejects a mutation with an empty reason BEFORE any fetch (fail-safe gate)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await lockAccount('acc-1', { reason: '   ' }, 'idem-key-1').catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('REASON_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('unlock / revoke / gdpr-delete each carry reason + idempotency-key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ ...LOCK_200, currentStatus: 'ACTIVE', unlockedAt: 'x', previousStatus: 'LOCKED' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ accountId: 'acc-1', revokedSessionCount: 2, operatorId: 'op', revokedAt: 'x', auditId: 'a' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ accountId: 'acc-1', status: 'DELETED', maskedAt: 'x', auditId: 'a' }),
      );
    vi.stubGlobal('fetch', fetchMock);

    await unlockAccount('acc-1', { reason: 'r1' }, 'k1');
    await revokeSessions('acc-1', { reason: 'r2' }, 'k2');
    await gdprDeleteAccount('acc-1', { reason: 'r3' }, 'k3');

    for (const call of fetchMock.mock.calls) {
      const h = (call[1] as RequestInit).headers as Record<string, string>;
      expect(h['X-Operator-Reason']).toBeTruthy();
      expect(h['Idempotency-Key']).toBeTruthy();
      expect(h.Authorization).toBe('Bearer OPERATOR-TOKEN');
    }
  });

  it('bulk-lock posts accountIds + reason + a single Idempotency-Key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        results: [
          { accountId: 'acc-1', outcome: 'LOCKED' },
          { accountId: 'acc-2', outcome: 'NOT_FOUND', error: { code: 'ACCOUNT_NOT_FOUND', message: 'x' } },
        ],
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const r = await bulkLockAccounts(
      ['acc-1', 'acc-2'],
      { reason: 'incident response' },
      'bulk-key-1',
    );
    expect(r.results).toHaveLength(2);
    expect(r.results[1].outcome).toBe('NOT_FOUND');

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe('bulk-key-1');
    expect(JSON.parse((init as RequestInit).body as string)).toMatchObject({
      accountIds: ['acc-1', 'acc-2'],
      reason: 'incident response',
    });
  });

  it('export sends X-Operator-Reason (GET, audited) and rejects an empty reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        accountId: 'acc-1',
        email: 'a@x.com',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        exportedAt: '2026-04-18T10:00:00Z',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const empty = await exportAccount('acc-1', '  ').catch((e) => e);
    expect(empty).toBeInstanceOf(ApiError);
    expect(empty.code).toBe('REASON_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();

    await exportAccount('acc-1', 'gdpr portability request');
    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    // TASK-MONO-176: percent-encoded on the wire; round-trips.
    expect(decodeURIComponent(headers['X-Operator-Reason'])).toBe(
      'gdpr portability request',
    );
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN');
  });
});

describe('accounts-api — §2.5 resilience error mapping', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('401 → ApiError(401) for forced re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const err = await searchAccounts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 PERMISSION_DENIED → ApiError(403)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const err = await lockAccount('acc-1', { reason: 'r' }, 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('PERMISSION_DENIED');
  });

  it('503 CIRCUIT_OPEN → AccountsUnavailableError(circuit_open) — section degrades only', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const err = await searchAccounts().catch((e) => e);
    expect(err).toBeInstanceOf(AccountsUnavailableError);
    expect(err.reason).toBe('circuit_open');
  });

  it('timeout → AccountsUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await searchAccounts().catch((e) => e);
    expect(err).toBeInstanceOf(AccountsUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('400 STATE_TRANSITION_INVALID → ApiError(400) inline actionable (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'STATE_TRANSITION_INVALID', message: 'already LOCKED' }, 400),
      ),
    );
    const err = await lockAccount('acc-1', { reason: 'r' }, 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('STATE_TRANSITION_INVALID');
  });

  it('404 ACCOUNT_NOT_FOUND → ApiError(404) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'ACCOUNT_NOT_FOUND' }, 404)),
    );
    const err = await unlockAccount('missing', { reason: 'r' }, 'k').catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
  });

  it('409 IDEMPOTENCY_KEY_CONFLICT (bulk-lock) → ApiError(409) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'IDEMPOTENCY_KEY_CONFLICT' }, 409),
      ),
    );
    const err = await bulkLockAccounts(['a'], { reason: 'incident' }, 'k').catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
  });

  it('422 BATCH_SIZE_EXCEEDED (bulk-lock) → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'BATCH_SIZE_EXCEEDED' }, 422)),
    );
    const err = await bulkLockAccounts(['a'], { reason: 'incident' }, 'k').catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
  });
});
