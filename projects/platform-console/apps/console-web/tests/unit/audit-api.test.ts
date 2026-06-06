import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/audit/api/audit-api.ts` — the security-critical core of
 * TASK-PC-FE-003 (READ-ONLY slice).
 *
 * Asserts (console-integration-contract § 2.4.2 / IAM admin-api.md
 * § GET /api/admin/audit):
 *   - the bearer is the EXCHANGED operator cookie, NEVER the IAM OIDC
 *     access token (the #569 trust-boundary invariant);
 *   - `X-Tenant-Id` is the active-tenant cookie value (never empty);
 *   - filter / `source` / pagination params are serialised correctly;
 *   - `size` is client-capped ≤ 100 (pre-empts the producer 422);
 *   - `tenantId` query defaults to the active tenant (TASK-PC-FE-043); an
 *     explicit `tenantId` overrides (SUPER_ADMIN cross-tenant);
 *   - **NO `X-Operator-Reason` and NO `Idempotency-Key`** are ever sent
 *     (read-only — the FE-002 mutation scaffolding must NOT leak here);
 *   - no-operator-token ⇒ 401 with NO fetch (no silent GAP-token fallback);
 *   - no-active-tenant ⇒ blocked with NO fetch (no cross-tenant/empty);
 *   - from > to ⇒ 422 with NO fetch (client guard);
 *   - 401 → ApiError(401, re-login); 403 PERMISSION_DENIED /
 *     403 TENANT_SCOPE_DENIED / 422 → ApiError (inline); 503/timeout →
 *     AuditUnavailableError (section degrades only);
 *   - an unknown future `source` row parses to a generic row (no throw).
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
    AUDIT_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { queryAudit } from '@/features/audit/api/audit-api';
import { ApiError, AuditUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const PAGE_200 = {
  content: [
    {
      source: 'admin',
      auditId: 'aud-1',
      actionCode: 'ACCOUNT_LOCK',
      operatorId: 'op-1',
      targetId: 'acc-1',
      reason: 'fraud',
      outcome: 'SUCCESS',
      occurredAt: '2026-04-12T10:00:00Z',
    },
    {
      source: 'login_history',
      eventId: 'ev-1',
      accountId: 'acc-9',
      outcome: 'FAILURE',
      ipMasked: '192.168.*.*',
      geoCountry: 'KR',
      occurredAt: '2026-04-12T09:58:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('audit-api — operator-token trust boundary (#569 invariant)', () => {
  it('sends the OPERATOR cookie as the bearer, NOT the IAM token, with X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE_200));
    vi.stubGlobal('fetch', fetchMock);

    await queryAudit({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(headers.Authorization).not.toContain(
      'GAP-OIDC-ACCESS-must-not-leak',
    );
    expect(headers['X-Tenant-Id']).toBe('wms');
    expect(String(url)).toContain('/api/admin/audit');
  });

  it('throws 401 with NO fetch when the operator token is absent (no IAM fallback)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-only');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await queryAudit().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('blocks (NO fetch) when no active tenant is selected — never empty X-Tenant-Id', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await queryAudit().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('audit-api — READ-ONLY: no mutation artifacts', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('sends NEITHER X-Operator-Reason NOR Idempotency-Key (read-only slice)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE_200));
    vi.stubGlobal('fetch', fetchMock);

    await queryAudit({ source: 'admin' });

    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    // sanity: the request really is the audit GET
    expect(String(url)).toContain('/api/admin/audit?');
  });
});

describe('audit-api — filter / source / pagination serialization + size cap', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('serialises accountId / actionCode / from / to / source / page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE_200));
    vi.stubGlobal('fetch', fetchMock);

    await queryAudit({
      accountId: 'acc-7',
      actionCode: 'ACCOUNT_LOCK',
      from: '2026-04-01T00:00:00Z',
      to: '2026-04-30T00:00:00Z',
      source: 'login_history',
      page: 2,
      size: 50,
    });

    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('accountId')).toBe('acc-7');
    expect(url.searchParams.get('actionCode')).toBe('ACCOUNT_LOCK');
    expect(url.searchParams.get('from')).toBe('2026-04-01T00:00:00Z');
    expect(url.searchParams.get('to')).toBe('2026-04-30T00:00:00Z');
    expect(url.searchParams.get('source')).toBe('login_history');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('size')).toBe('50');
  });

  it('client-caps size ≤ 100 (pre-empts the producer 422)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE_200));
    vi.stubGlobal('fetch', fetchMock);

    await queryAudit({ size: 5000 });

    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('size')).toBe('100');
  });

  it('defaults tenantId to the active-tenant cookie; an explicit tenantId overrides (TASK-PC-FE-043)', async () => {
    // Fresh Response per call — a Response body can only be read once.
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(PAGE_200)),
    );
    vi.stubGlobal('fetch', fetchMock);

    // No explicit tenantId → scoped to the active tenant (cookie = 'wms'), so
    // the audit view follows the tenant switcher (not the operator home).
    await queryAudit({ source: 'admin' });
    let url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('tenantId')).toBe('wms');

    // Explicit tenantId (SUPER_ADMIN cross-tenant) overrides the default.
    await queryAudit({ source: 'admin', tenantId: 'other-tenant' });
    url = new URL(String(fetchMock.mock.calls[1][0]));
    expect(url.searchParams.get('tenantId')).toBe('other-tenant');
  });

  it('rejects from > to with 422 and NO fetch (client guard)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await queryAudit({
      from: '2026-05-10T00:00:00Z',
      to: '2026-05-01T00:00:00Z',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('AUDIT_RANGE_INVALID');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('audit-api — §2.5 resilience error mapping', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('401 → ApiError(401) for forced re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const err = await queryAudit().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 PERMISSION_DENIED → ApiError(403) inline (intersection-permission)', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const err = await queryAudit({ source: 'suspicious' }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('PERMISSION_DENIED');
  });

  it('403 TENANT_SCOPE_DENIED → ApiError(403) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'TENANT_SCOPE_DENIED' }, 403)),
    );
    const err = await queryAudit({ tenantId: 'foreign' }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_SCOPE_DENIED');
  });

  it('422 VALIDATION_ERROR (producer) → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'VALIDATION_ERROR' }, 422)),
    );
    const err = await queryAudit().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
  });

  it('503 CIRCUIT_OPEN → AuditUnavailableError(circuit_open) — section degrades only', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const err = await queryAudit().catch((e) => e);
    expect(err).toBeInstanceOf(AuditUnavailableError);
    expect(err.reason).toBe('circuit_open');
  });

  it('timeout → AuditUnavailableError(timeout)', async () => {
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
    const err = await queryAudit().catch((e) => e);
    expect(err).toBeInstanceOf(AuditUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});

describe('audit-api — discriminated union tolerance', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('an unknown/future source row parses to a generic row (no throw)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [
            {
              source: 'future_source_v2',
              occurredAt: '2026-06-01T00:00:00Z',
              someNewField: 'x',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
    );
    const page = await queryAudit();
    expect(page.content).toHaveLength(1);
    expect((page.content[0] as { source: string }).source).toBe(
      'future_source_v2',
    );
  });
});
