import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operators/api` tenant-assignment create/remove client
 * (TASK-PC-FE-157 / TASK-BE-347):
 *   - `assignOperatorToTenant(operatorId, tenantId, reason)` → POST
 *     .../assignments/{tenantId}; bearer is the operator token (NOT the GAP
 *     OIDC access token — #569); `X-Tenant-Id` is the active tenant;
 *     `X-Operator-Reason` present (percent-encoded); NO `Idempotency-Key`;
 *     201 body parsed as an assignment (NON_NULL omit → null).
 *   - `unassignOperatorFromTenant(...)` → DELETE .../assignments/{tenantId};
 *     `X-Operator-Reason` present; NO key; 204 no content → void.
 *   - path variables are URL-encoded (path-traversal defence).
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
    OPERATORS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  assignOperatorToTenant,
  unassignOperatorFromTenant,
} from '@/features/operators/api/operators-api';
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

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('assignOperatorToTenant — POST .../assignments/{tenantId}', () => {
  it('POSTs with operator-token bearer + active X-Tenant-Id + reason; NO idempotency key; parses 201 body', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      // 201 with orgScope + permissionSetId OMITTED (NON_NULL) → parse to null.
      .mockResolvedValue(jsonResponse({ tenantId: 'acme-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await assignOperatorToTenant('op-1', 'acme-corp', '온보딩');
    expect(res.tenantId).toBe('acme-corp');
    expect(res.orgScope).toBeNull();
    expect(res.permissionSetId).toBeNull();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/op-1/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('POST');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OPERATOR-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('acme-corp');
    // Korean reason is percent-encoded on the wire (Latin-1 header safety).
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('온보딩');
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);
  });

  it('path variables are URL-encoded (path-traversal defence)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ tenantId: 'x' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    await assignOperatorToTenant('op/../evil', 'ten/../x', 'r');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      `/api/admin/operators/${encodeURIComponent('op/../evil')}/assignments/${encodeURIComponent('ten/../x')}`,
    );
  });

  it('409 ASSIGNMENT_ALREADY_EXISTS surfaces as an ApiError (inline actionable)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'ASSIGNMENT_ALREADY_EXISTS' }, 409)),
    );
    await expect(
      assignOperatorToTenant('op-1', 'acme-corp', 'r'),
    ).rejects.toMatchObject({ status: 409, code: 'ASSIGNMENT_ALREADY_EXISTS' });
  });
});

describe('unassignOperatorFromTenant — DELETE .../assignments/{tenantId}', () => {
  it('DELETEs with reason + NO key; 204 → void', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await unassignOperatorFromTenant('op-1', 'acme-corp', '해제');
    expect(res).toBeUndefined();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/op-1/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('DELETE');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OPERATOR-correct');
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('해제');
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('404 ASSIGNMENT_NOT_FOUND (home-tenant-only operator) surfaces as ApiError', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'ASSIGNMENT_NOT_FOUND' }, 404)),
    );
    await expect(
      unassignOperatorFromTenant('op-1', 'acme-corp', 'r'),
    ).rejects.toMatchObject({ status: 404, code: 'ASSIGNMENT_NOT_FOUND' });
  });
});
