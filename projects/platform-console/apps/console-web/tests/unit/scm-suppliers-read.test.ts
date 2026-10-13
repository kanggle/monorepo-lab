import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `shared/api/scm-suppliers-read.ts` (TASK-PC-FE-337) — the cross-feature
 * `shared/` promotion of the scm procurement supplier-master list read,
 * consumed by both `features/scm-ops` (PO filter, picks `id`) and
 * `features/scm-config` (supplier-code suggestions, picks `code`).
 *
 * Built directly on `shared/api/scm-gateway.ts`'s `callScmGateway` — the
 * SAME credential / tenant / error-envelope / resilience narrative as every
 * other scm client (`scm-api.test.ts`'s central assertion), reused here
 * rather than re-derived:
 *   - the bearer is the IAM OIDC ACCESS token, NEVER the operator token;
 *   - no `X-Tenant-Id` (scm resolves tenant from the JWT claim);
 *   - the scm FLAT error envelope is parsed;
 *   - 401/403/404/400/422 → ApiError; 429 → ScmRateLimitedError (ONE
 *     bounded backoff); 503/timeout → ScmUnavailableError (section
 *     degrade only);
 *   - a pure GET — no mutation artifacts.
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import * as sessionModule from '@/shared/lib/session';
import { listSuppliers } from '@/shared/api/scm-suppliers-read';
import {
  ApiError,
  ScmUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function scmError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-05-19T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const SUPPLIERS_ENVELOPE = {
  data: {
    content: [
      {
        id: '9b1d4a8c-1f2c-7a90-b1d4-3e6f8a2c9d10',
        tenantId: 'scm',
        code: 'SUP-ACME-001',
        name: 'ACME Components Co.',
        status: 'ACTIVE',
        createdAt: '2026-08-08T08:30:00Z',
        updatedAt: '2026-08-08T08:30:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  },
  meta: { timestamp: '2026-08-08T08:30:00Z' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('scm-suppliers-read — per-domain credential selection (reuse of § 2.4.5)', () => {
  it('sends the IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuppliers({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
    expect(String(url)).toContain(
      'http://scm.local/api/v1/procurement/suppliers',
    );
    expect(String(url)).toContain('page=0');
    expect(String(url)).toContain('size=20');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken()', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE)));

    await listSuppliers();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('401 with NO IAM session → ApiError(401), no fetch (whole-session re-login)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-ONLY-HALF-SESSION');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listSuppliers().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (scm resolves tenant from the JWT claim)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuppliers();

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
  });
});

describe('scm-suppliers-read — unwraps the envelope + clamps pagination', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('unwraps { data: { content, page, size, totalElements, totalPages } }', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE)));
    const page = await listSuppliers();
    expect(page.content).toHaveLength(1);
    expect(page.content[0].code).toBe('SUP-ACME-001');
    expect(page.content[0].name).toBe('ACME Components Co.');
    expect(page.totalElements).toBe(1);
  });

  it('clamps an oversize `size` request to the producer MAX_SIZE (100)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuppliers({ size: 999 });

    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('size')).toBe('100');
  });

  it('forwards an optional `status` filter', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuppliers({ status: 'ACTIVE' });

    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('status')).toBe('ACTIVE');
  });

  it('a pure GET — no mutation artifacts', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SUPPLIERS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuppliers();

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
  });
});

describe('scm-suppliers-read — scm FLAT error envelope + resilience (§ 2.5)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(scmError('TENANT_FORBIDDEN', 403)));
    const err = await listSuppliers().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('503 → ScmUnavailableError (ONLY the calling section degrades)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(scmError('SERVICE_UNAVAILABLE', 503)));
    const err = await listSuppliers().catch((e) => e);
    expect(err).toBeInstanceOf(ScmUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('429 persisting after the bounded retry → ScmRateLimitedError (NO storm)', async () => {
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

    const err = await listSuppliers().catch((e) => e);
    expect(err).toBeInstanceOf(ScmRateLimitedError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 500,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await listSuppliers().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(500);
  });
});
