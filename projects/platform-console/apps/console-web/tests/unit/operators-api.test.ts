import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operators/api/operators-api.ts` — the security-critical core
 * of TASK-PC-FE-004 (the MOST privilege-sensitive Phase-2 slice).
 *
 * Asserts (console-integration-contract § 2.4.3 / IAM admin-api.md
 * §§ operators):
 *   - the bearer is the EXCHANGED operator cookie, NEVER the IAM OIDC
 *     access token (the #569 trust-boundary invariant);
 *   - `X-Tenant-Id` is the active-tenant cookie value (never empty);
 *   - **PER-ENDPOINT HEADER MATRIX** (the key correctness risk):
 *       · `create`  → `X-Operator-Reason` AND `Idempotency-Key`;
 *       · `roles`   → `X-Operator-Reason` ONLY, **NO `Idempotency-Key`**;
 *       · `status`  → `X-Operator-Reason` ONLY, **NO `Idempotency-Key`**;
 *       · `password`→ the SELF path, NO reason, NO key;
 *   - reason-empty (reason-bearing mutation) ⇒ request NOT sent;
 *   - no-operator-token ⇒ 401 with NO fetch (no silent GAP-token fallback);
 *   - no-active-tenant ⇒ blocked with NO fetch (no cross-tenant/empty);
 *   - a password is never placed in any logged payload;
 *   - 401 / 403 PERMISSION_DENIED / 403 TENANT_SCOPE_DENIED / 409
 *     OPERATOR_EMAIL_CONFLICT / 400 ROLE_NOT_FOUND / 404 / 503 mapped.
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001/002a/002/003 lane).
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

// Capture every structured log payload so we can assert a password is
// NEVER serialised into one (security invariant).
const logged: unknown[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    debug: (_m: string, f?: unknown) => logged.push(f),
    info: (_m: string, f?: unknown) => logged.push(f),
    warn: (_m: string, f?: unknown) => logged.push(f),
    error: (_m: string, f?: unknown) => logged.push(f),
  },
  newRequestId: () => 'test-req-id',
}));

import {
  listOperators,
  createOperator,
  editOperatorRoles,
  changeOperatorStatus,
  changeOwnPassword,
} from '@/features/operators/api/operators-api';
import {
  ApiError,
  OperatorsUnavailableError,
} from '@/shared/api/errors';
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
function noContent() {
  return new Response(null, { status: 204 });
}

const LIST_200 = {
  content: [
    {
      operatorId: 'op-1',
      email: 'op@x.com',
      displayName: 'Op One',
      status: 'ACTIVE',
      roles: ['SUPPORT_LOCK'],
      totpEnrolled: false,
      lastLoginAt: '2026-04-24T10:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};
const CREATE_201 = {
  operatorId: 'op-9',
  email: 'new@x.com',
  displayName: 'New Op',
  status: 'ACTIVE',
  roles: ['SUPPORT_LOCK'],
  totpEnrolled: false,
  createdAt: '2026-04-24T10:00:00Z',
  auditId: 'aud-1',
  tenantId: 'wms',
};

const SECRET_PW = 'Sup3rSecret!pw';

beforeEach(() => {
  cookieJar.clear();
  logged.length = 0;
  vi.unstubAllGlobals();
});

describe('operators-api — operator-token trust boundary (#569 invariant)', () => {
  it('list sends the OPERATOR cookie as the bearer, NOT the IAM token, with X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_200));
    vi.stubGlobal('fetch', fetchMock);

    await listOperators({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(headers.Authorization).not.toContain(
      'GAP-OIDC-ACCESS-must-not-leak',
    );
    expect(headers['X-Tenant-Id']).toBe('wms');
    expect(String(url)).toContain('/api/admin/operators');
  });

  it('throws 401 with NO fetch when the operator token is absent (no IAM fallback)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-only');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listOperators().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('blocks (NO fetch) when no active tenant is selected — never empty X-Tenant-Id', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listOperators().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('operators-api — PER-ENDPOINT HEADER MATRIX (the key correctness risk)', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('GET list sends NEITHER X-Operator-Reason NOR Idempotency-Key (read)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_200));
    vi.stubGlobal('fetch', fetchMock);

    await listOperators({ status: 'ACTIVE' });

    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(new URL(String(url)).searchParams.get('status')).toBe('ACTIVE');
  });

  it('CREATE sends BOTH X-Operator-Reason AND Idempotency-Key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(CREATE_201, 201));
    vi.stubGlobal('fetch', fetchMock);

    await createOperator(
      {
        email: 'new@x.com',
        displayName: 'New Op',
        password: SECRET_PW,
        roles: ['SUPPORT_LOCK'],
        tenantId: 'wms',
      },
      'onboarding new support operator',
      'idem-create-1',
    );

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('POST');
    const h = (init as RequestInit).headers as Record<string, string>;
    // TASK-MONO-176: the reason is percent-encoded on the wire (ByteString
    // header safety); it round-trips via decodeURIComponent.
    expect(h['X-Operator-Reason']).toBe('onboarding%20new%20support%20operator');
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe(
      'onboarding new support operator',
    );
    expect(h['Idempotency-Key']).toBe('idem-create-1');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toMatchObject({
      email: 'new@x.com',
      tenantId: 'wms',
      password: SECRET_PW,
    });
  });

  it('CREATE with a non-ASCII (Korean) reason percent-encodes it so fetch does not throw on the ByteString header — TASK-MONO-176', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(CREATE_201, 201));
    vi.stubGlobal('fetch', fetchMock);

    // The bug: a raw Korean reason in the X-Operator-Reason header made
    // undici fetch throw `TypeError: Cannot convert argument to a ByteString`
    // (surfaced as "operators unavailable"). Encoding it keeps the header ASCII.
    await createOperator(
      {
        email: 'globex-1@test.com',
        displayName: 'Globex One',
        password: SECRET_PW,
        roles: ['SUPPORT_READONLY'],
        tenantId: 'globex-corp',
      },
      '테스트 1',
      'idem-create-ko',
    );

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    // ASCII-only on the wire …
    expect(/^[\x00-\x7F]*$/.test(h['X-Operator-Reason'])).toBe(true);
    expect(h['X-Operator-Reason']).toBe(encodeURIComponent('테스트 1'));
    // … and the original Korean text round-trips for the producer.
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('테스트 1');
  });

  it('EDIT-ROLES sends X-Operator-Reason and asserts Idempotency-Key is ABSENT', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        operatorId: 'op-1',
        roles: ['SUPPORT_READONLY'],
        auditId: 'a',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await editOperatorRoles('op-1', ['SUPPORT_READONLY'], 'role realignment');

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('PATCH');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('role realignment');
    // CONTRACT FIDELITY: the producer does NOT list Idempotency-Key here.
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);
  });

  it('EDIT-ROLES allows an empty [] (remove all roles) and still no key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ operatorId: 'op-1', roles: [], auditId: 'a' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await editOperatorRoles('op-1', [], 'revoke all operator privileges');

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      roles: [],
    });
  });

  it('CHANGE-STATUS sends X-Operator-Reason and asserts Idempotency-Key is ABSENT', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        operatorId: 'op-1',
        previousStatus: 'ACTIVE',
        currentStatus: 'SUSPENDED',
        auditId: 'a',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await changeOperatorStatus('op-1', 'SUSPENDED', 'policy violation');

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('PATCH');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('policy violation');
    // CONTRACT FIDELITY: the producer does NOT list Idempotency-Key here.
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);
  });

  it('CHANGE-PASSWORD is the self path with NO reason and NO key (204)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await changeOwnPassword({
      currentPassword: 'Old1!pass',
      newPassword: SECRET_PW,
    });

    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('PATCH');
    expect(String(url)).toContain('/api/admin/operators/me/password');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });
});

describe('operators-api — reason fail-safe + password never logged', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('rejects a reason-bearing mutation with an empty reason BEFORE any fetch', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await editOperatorRoles('op-1', ['SUPPORT_LOCK'], '   ').catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('REASON_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a password is NEVER serialised into any structured log payload', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(CREATE_201, 201));
    vi.stubGlobal('fetch', fetchMock);

    await createOperator(
      {
        email: 'new@x.com',
        displayName: 'New Op',
        password: SECRET_PW,
        roles: [],
        tenantId: 'wms',
      },
      'onboarding',
      'idem-1',
    );
    await changeOwnPassword({
      currentPassword: 'Old1!pass',
      newPassword: SECRET_PW,
    }).catch(() => undefined);

    const serialised = JSON.stringify(logged);
    expect(serialised).not.toContain(SECRET_PW);
    expect(serialised).not.toContain('Old1!pass');
  });
});

describe('operators-api — §2.5 resilience error mapping', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('401 → ApiError(401) for forced re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const err = await listOperators().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 PERMISSION_DENIED (not SUPER_ADMIN) → ApiError(403) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const err = await listOperators().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('PERMISSION_DENIED');
  });

  it('403 TENANT_SCOPE_DENIED (non-platform creating tenantId=*) → ApiError(403)', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'TENANT_SCOPE_DENIED' }, 403)),
    );
    const err = await createOperator(
      {
        email: 'a@x.com',
        displayName: 'A',
        password: SECRET_PW,
        roles: [],
        tenantId: '*',
      },
      'reason',
      'k',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_SCOPE_DENIED');
  });

  it('409 OPERATOR_EMAIL_CONFLICT → ApiError(409) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ code: 'OPERATOR_EMAIL_CONFLICT' }, 409),
        ),
    );
    const err = await createOperator(
      {
        email: 'dup@x.com',
        displayName: 'Dup',
        password: SECRET_PW,
        roles: [],
        tenantId: 'wms',
      },
      'reason',
      'k',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('OPERATOR_EMAIL_CONFLICT');
  });

  it('400 ROLE_NOT_FOUND → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'ROLE_NOT_FOUND' }, 400)),
    );
    const err = await editOperatorRoles(
      'op-1',
      ['STALE_ROLE'],
      'reason',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('ROLE_NOT_FOUND');
  });

  it('404 OPERATOR_NOT_FOUND → ApiError(404) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'OPERATOR_NOT_FOUND' }, 404)),
    );
    const err = await changeOperatorStatus(
      'missing',
      'SUSPENDED',
      'reason',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
  });

  it('400 CURRENT_PASSWORD_MISMATCH (self change-password) → ApiError(400)', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ code: 'CURRENT_PASSWORD_MISMATCH' }, 400),
        ),
    );
    const err = await changeOwnPassword({
      currentPassword: 'wrong',
      newPassword: SECRET_PW,
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('CURRENT_PASSWORD_MISMATCH');
  });

  it('503 CIRCUIT_OPEN → OperatorsUnavailableError(circuit_open) — section degrades only', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const err = await listOperators().catch((e) => e);
    expect(err).toBeInstanceOf(OperatorsUnavailableError);
    expect(err.reason).toBe('circuit_open');
  });

  it('timeout → OperatorsUnavailableError(timeout)', async () => {
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
    const err = await listOperators().catch((e) => e);
    expect(err).toBeInstanceOf(OperatorsUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});

describe('operators-api — role tolerance (unknown future role)', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('an unknown/future role in a list row parses (generic — no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [
            {
              operatorId: 'op-1',
              email: 'op@x.com',
              displayName: 'Op',
              status: 'ACTIVE',
              roles: ['FUTURE_ROLE_V2', 'SUPPORT_LOCK'],
              createdAt: '2026-01-01T00:00:00Z',
            },
          ],
          totalElements: 1,
          page: 0,
          size: 20,
          totalPages: 1,
        }),
      ),
    );
    const page = await listOperators();
    expect(page.content[0].roles).toContain('FUTURE_ROLE_V2');
  });
});
