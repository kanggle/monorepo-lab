import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/erp-ops/api/delegation-api.ts` — the erp `approval-service`
 * delegation grant client (TASK-PC-FE-054).
 *
 * Pins:
 *   - per-domain credential REUSE of § 2.4.8: the domain-facing GAP OIDC
 *     token, NEVER `getOperatorToken()`; no `X-Tenant-Id` (erp resolves
 *     tenant from the JWT claim);
 *   - NO `X-Operator-Reason` — delegation reason rides in the BODY only;
 *   - list forwards the `?role=` param verbatim; create + revoke carry the
 *     `Idempotency-Key` header; reads do not;
 *   - NON_NULL absent-field parsing (validTo / reason / revokedAt / revokedBy
 *     ABSENT → optional/undefined, never a parser throw);
 *   - delegation-specific error codes surface as `ApiError` (422
 *     DELEGATION_INVALID, 404 DELEGATION_NOT_FOUND, 403, 400 idempotency);
 *   - 503 / timeout → `ErpUnavailableError`.
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
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import * as sessionModule from '@/shared/lib/session';
import {
  listDelegations,
  createDelegation,
  revokeDelegation,
} from '@/features/erp-ops/api/delegation-api';
import { DelegationGrantSchema } from '@/features/erp-ops/api/delegation-types';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

// Minimal grant body — validTo / reason / revokedAt / revokedBy all ABSENT.
const ACTIVE_GRANT = {
  id: 'del-1',
  delegatorId: 'emp-delegator',
  delegateId: 'emp-delegate',
  validFrom: '2026-06-05T00:00:00Z',
  status: 'ACTIVE',
  createdAt: '2026-06-05T00:00:00Z',
  createdBy: 'emp-delegator',
  // validTo ABSENT — open-ended.
  // reason ABSENT.
  // revokedAt / revokedBy ABSENT.
};

// Grant with all optional fields present.
const FULL_GRANT = {
  id: 'del-2',
  delegatorId: 'emp-delegator',
  delegateId: 'emp-delegate',
  validFrom: '2026-06-01T00:00:00Z',
  validTo: '2026-06-30T23:59:59Z',
  reason: '출장 대결',
  status: 'ACTIVE',
  createdAt: '2026-06-01T00:00:00Z',
  createdBy: 'emp-delegator',
};

const REVOKED_GRANT = {
  id: 'del-3',
  delegatorId: 'emp-delegator',
  delegateId: 'emp-delegate',
  validFrom: '2026-06-01T00:00:00Z',
  status: 'REVOKED',
  createdAt: '2026-06-01T00:00:00Z',
  createdBy: 'emp-delegator',
  revokedAt: '2026-06-04T12:00:00Z',
  revokedBy: 'emp-delegator',
};

const LIST_RESPONSE = {
  data: [ACTIVE_GRANT, FULL_GRANT],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  return {
    url: String(url),
    init: init as RequestInit,
    headers: (init as RequestInit).headers as Record<string, string>,
    body:
      (init as RequestInit).body !== undefined
        ? JSON.parse(String((init as RequestInit).body))
        : undefined,
  };
}

// ===========================================================================
// credential + tenant-model.
// ===========================================================================

describe('delegation-api — per-domain credential (REUSE of § 2.4.8)', () => {
  it('sends the domain-facing GAP token, NEVER the operator token / X-Tenant-Id / X-Operator-Reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_RESPONSE));
    vi.stubGlobal('fetch', fetchMock);

    await listDelegations();

    const { headers } = lastCall(fetchMock);
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    // delegation reason rides in the BODY — no X-Operator-Reason on reads.
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the GAP session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listDelegations().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// listDelegations — GET, role query forwarding.
// ===========================================================================

describe('delegation-api — listDelegations (GET; role query forwarding)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('list without role: pure GET to /delegations, no Idempotency-Key, no body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_RESPONSE));
    vi.stubGlobal('fetch', fetchMock);
    const result = await listDelegations();
    const { init, headers, url } = lastCall(fetchMock);
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(url).toBe('http://erp.local/api/erp/approval/delegations');
    expect(result.data).toHaveLength(2);
  });

  it('list with role=DELEGATOR: appends ?role=DELEGATOR to the URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_RESPONSE));
    vi.stubGlobal('fetch', fetchMock);
    await listDelegations('DELEGATOR');
    const { url } = lastCall(fetchMock);
    expect(url).toBe('http://erp.local/api/erp/approval/delegations?role=DELEGATOR');
  });

  it('list with role=DELEGATE: appends ?role=DELEGATE', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_RESPONSE));
    vi.stubGlobal('fetch', fetchMock);
    await listDelegations('DELEGATE');
    const { url } = lastCall(fetchMock);
    expect(url).toBe('http://erp.local/api/erp/approval/delegations?role=DELEGATE');
  });

  it('parses a grant with absent validTo/reason/revokedAt/revokedBy (NON_NULL → undefined, no throw)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_RESPONSE)));
    const result = await listDelegations();
    const g = result.data.find((x) => x.id === 'del-1');
    expect(g).toBeDefined();
    expect(g!.validTo).toBeUndefined();
    expect(g!.reason).toBeUndefined();
    expect(g!.revokedAt).toBeUndefined();
    expect(g!.revokedBy).toBeUndefined();
    // Schema parse without throw.
    expect(() => DelegationGrantSchema.parse(ACTIVE_GRANT)).not.toThrow();
  });

  it('parses a grant with all optional fields present (no throw)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ data: [FULL_GRANT], meta: { page: 0, size: 20, totalElements: 1 } }),
      ),
    );
    const result = await listDelegations();
    const g = result.data[0];
    expect(g.validTo).toBe('2026-06-30T23:59:59Z');
    expect(g.reason).toBe('출장 대결');
  });

  it('parses a REVOKED grant with revokedAt/revokedBy (no throw)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ data: [REVOKED_GRANT], meta: { page: 0, size: 20, totalElements: 1 } }),
      ),
    );
    const result = await listDelegations();
    const g = result.data[0];
    expect(g.status).toBe('REVOKED');
    expect(g.revokedAt).toBe('2026-06-04T12:00:00Z');
    expect(g.revokedBy).toBe('emp-delegator');
    expect(() => DelegationGrantSchema.parse(REVOKED_GRANT)).not.toThrow();
  });
});

// ===========================================================================
// createDelegation — POST, body + Idempotency-Key.
// ===========================================================================

describe('delegation-api — createDelegation (POST + Idempotency-Key)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
  });

  it('POST to /delegations with delegateId + validFrom; Idempotency-Key header; no X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACTIVE_GRANT, 201));
    vi.stubGlobal('fetch', fetchMock);
    await createDelegation(
      { delegateId: 'emp-delegate', validFrom: '2026-06-05T00:00:00Z' },
      'idem-create',
    );
    const { init, url, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/approval/delegations');
    expect(headers['Idempotency-Key']).toBe('idem-create');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(body.delegateId).toBe('emp-delegate');
    expect(body.validFrom).toBe('2026-06-05T00:00:00Z');
    // validTo and reason omitted from input → absent in body.
    expect(body.validTo).toBeUndefined();
    expect(body.reason).toBeUndefined();
  });

  it('create with validTo + reason: both present in the body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(FULL_GRANT, 201));
    vi.stubGlobal('fetch', fetchMock);
    await createDelegation(
      {
        delegateId: 'emp-delegate',
        validFrom: '2026-06-01T00:00:00Z',
        validTo: '2026-06-30T23:59:59Z',
        reason: '출장',
      },
      'idem-full',
    );
    const { body, headers } = lastCall(fetchMock);
    expect(headers['Idempotency-Key']).toBe('idem-full');
    expect(body.validTo).toBe('2026-06-30T23:59:59Z');
    expect(body.reason).toBe('출장');
  });
});

// ===========================================================================
// revokeDelegation — POST /{id}/revoke, reason in body + Idempotency-Key.
// ===========================================================================

describe('delegation-api — revokeDelegation (POST /{id}/revoke)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
  });

  it('POST to /delegations/{id}/revoke with reason + Idempotency-Key; no X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REVOKED_GRANT));
    vi.stubGlobal('fetch', fetchMock);
    await revokeDelegation('del-3', '회수 사유', 'idem-revoke');
    const { init, url, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/approval/delegations/del-3/revoke');
    expect(headers['Idempotency-Key']).toBe('idem-revoke');
    // reason in the body, NOT X-Operator-Reason.
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(body.reason).toBe('회수 사유');
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
  });
});

// ===========================================================================
// error taxonomy.
// ===========================================================================

describe('delegation-api — error taxonomy (graceful, no crash)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('422 DELEGATION_INVALID → ApiError(422)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('DELEGATION_INVALID', 422)));
    const err = await createDelegation(
      { delegateId: 'emp-self', validFrom: '2026-06-05T00:00:00Z' },
      'k',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('DELEGATION_INVALID');
  });

  it('404 DELEGATION_NOT_FOUND → ApiError(404)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('DELEGATION_NOT_FOUND', 404)));
    const err = await revokeDelegation('nope', 'r', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('DELEGATION_NOT_FOUND');
  });

  it('403 PERMISSION_DENIED → ApiError(403)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('PERMISSION_DENIED', 403)));
    const err = await revokeDelegation('del-1', 'r', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('PERMISSION_DENIED');
  });

  it('503 → ErpUnavailableError (delegation section degrades only)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)));
    const err = await listDelegations().catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
  });

  it('timeout → ErpUnavailableError(timeout)', async () => {
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
    const err = await listDelegations().catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});
