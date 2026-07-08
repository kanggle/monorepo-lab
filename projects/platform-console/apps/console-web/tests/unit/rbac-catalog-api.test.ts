import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `shared/api/rbac-catalog.ts` — the TASK-BE-486 consumption core shared by
 * BOTH TASK-PC-FE-227 (「권한」) and TASK-PC-FE-228 (「권한 세트」).
 *
 * Asserts (console-integration-contract § 2.4.3.2 / IAM admin-api.md
 * §§ `GET /api/admin/roles`, `GET /api/admin/permissions`):
 *   - the bearer is the EXCHANGED operator cookie, NEVER the IAM OIDC
 *     access token (the #569 trust-boundary invariant);
 *   - `X-Tenant-Id` is the active-tenant cookie value (never empty), even
 *     though the catalog itself is global data;
 *   - **NO `X-Operator-Reason` and NO `Idempotency-Key`** are ever sent
 *     (read-only surface — no mutation scaffolding leaks here);
 *   - no-operator-token ⇒ 401 with NO fetch (no silent IAM-token fallback);
 *   - no-active-tenant ⇒ blocked with NO fetch (no cross-tenant/empty);
 *   - 401 → ApiError(401, re-login); 403 PERMISSION_DENIED → ApiError
 *     (inline); 503/timeout → RbacUnavailableError (section degrades only);
 *   - an unknown/future permission key never crashes the parse (tolerant);
 *   - `getRbacCatalogState()` maps noTenant / permissionError / degraded /
 *     success exactly like the operators/audit state mappers.
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

const redirectMock = vi.fn((path: string) => {
  const err = new Error(`NEXT_REDIRECT:${path}`);
  err.name = 'NEXT_REDIRECT';
  throw err;
});
vi.mock('next/navigation', () => ({
  redirect: (path: string) => redirectMock(path),
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
    RBAC_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  getRoleCatalog,
  getPermissionCatalog,
  getRbacCatalogState,
} from '@/shared/api/rbac-catalog';
import { ApiError, RbacUnavailableError } from '@/shared/api/errors';
import { OPERATOR_COOKIE, TENANT_COOKIE, ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ROLES_200 = {
  scope: 'global',
  roles: [
    {
      id: 1,
      name: 'SUPER_ADMIN',
      description: 'Full platform administrator',
      permissions: ['account.read', 'operator.manage'],
    },
    {
      id: 2,
      name: 'SUPPORT_READONLY',
      description: 'Read-only support',
      permissions: [],
    },
  ],
};

const PERMISSIONS_200 = {
  scope: 'global',
  permissions: ['account.read', 'audit.read', 'operator.manage'],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  redirectMock.mockClear();
});

describe('rbac-catalog — operator-token trust boundary (#569 invariant)', () => {
  it('getRoleCatalog sends the OPERATOR cookie as the bearer, NOT the IAM token, with X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'IAM-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ROLES_200));
    vi.stubGlobal('fetch', fetchMock);

    await getRoleCatalog();

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(headers.Authorization).not.toContain(
      'IAM-OIDC-ACCESS-must-not-leak',
    );
    expect(headers['X-Tenant-Id']).toBe('wms');
    expect(String(url)).toContain('/api/admin/roles');
  });

  it('getPermissionCatalog sends the same trust-boundary headers', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PERMISSIONS_200));
    vi.stubGlobal('fetch', fetchMock);

    await getPermissionCatalog();

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(String(url)).toContain('/api/admin/permissions');
  });

  it('throws 401 with NO fetch when the operator token is absent (no IAM fallback)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'IAM-only');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await getRoleCatalog().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('blocks (NO fetch) when no active tenant is selected — never empty X-Tenant-Id', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await getRoleCatalog().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('rbac-catalog — READ-ONLY: no mutation artifacts', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('sends NEITHER X-Operator-Reason NOR Idempotency-Key (roles)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ROLES_200));
    vi.stubGlobal('fetch', fetchMock);

    await getRoleCatalog();

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
  });

  it('sends NEITHER X-Operator-Reason NOR Idempotency-Key (permissions)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PERMISSIONS_200));
    vi.stubGlobal('fetch', fetchMock);

    await getPermissionCatalog();

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
  });
});

describe('rbac-catalog — §2.5 resilience error mapping', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('401 → ApiError(401) for forced re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const err = await getRoleCatalog().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 PERMISSION_DENIED → ApiError(403) inline (generic forbiddenMode)', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const err = await getRoleCatalog().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('PERMISSION_DENIED');
  });

  it('503 CIRCUIT_OPEN → RbacUnavailableError(circuit_open) — section degrades only', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const err = await getRoleCatalog().catch((e) => e);
    expect(err).toBeInstanceOf(RbacUnavailableError);
    expect(err.reason).toBe('circuit_open');
  });

  it('timeout → RbacUnavailableError(timeout)', async () => {
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
    const err = await getPermissionCatalog().catch((e) => e);
    expect(err).toBeInstanceOf(RbacUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});

describe('rbac-catalog — response parsing / tolerance', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
  });

  it('parses the role catalog scope + per-role permission sets, incl. an empty-permission role', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(ROLES_200)));
    const resp = await getRoleCatalog();
    expect(resp.scope).toBe('global');
    expect(resp.roles).toHaveLength(2);
    expect(resp.roles[0].permissions).toEqual([
      'account.read',
      'operator.manage',
    ]);
    expect(resp.roles[1].permissions).toEqual([]);
  });

  it('an unknown/future permission key never crashes the parse (tolerant)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          scope: 'global',
          permissions: ['account.read', 'future.permission.v2'],
        }),
      ),
    );
    const resp = await getPermissionCatalog();
    expect(resp.permissions).toContain('future.permission.v2');
  });

  it('does not hard-crash on a non-"global" scope value (display-only, not a closed literal)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ scope: 'tenant', roles: [] }),
      ),
    );
    const resp = await getRoleCatalog();
    expect(resp.scope).toBe('tenant');
  });
});

describe('getRbacCatalogState — SSR resilience state (mirrors operators/audit state mappers)', () => {
  it('noTenant when no active tenant is selected — NO fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getRbacCatalogState();
    expect(state.noTenant).toBe(true);
    expect(state.roles).toBeNull();
    expect(state.permissions).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('redirects to /login on 401 (no partial authed state)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );

    await expect(getRbacCatalogState()).rejects.toThrow(/NEXT_REDIRECT/);
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  it('permissionError when the producer returns 403 PERMISSION_DENIED', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
    // Fresh Response PER call — getRbacCatalogState fetches roles + permissions
    // concurrently (Promise.all); a single shared Response body can only be
    // read once (the second read would fall back to `{}` → HTTP_403), so the
    // code assertion needs an independent body stream per fetch.
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation(() =>
          Promise.resolve(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
        ),
    );

    const state = await getRbacCatalogState();
    expect(state.permissionError).toEqual({
      code: 'PERMISSION_DENIED',
      message: expect.any(String),
    });
    expect(state.roles).toBeNull();
    expect(state.degraded).toBe(false);
  });

  it('degraded when the producer is unavailable (503)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503)),
    );

    const state = await getRbacCatalogState();
    expect(state.degraded).toBe(true);
    expect(state.roles).toBeNull();
    expect(state.noTenant).toBe(false);
    expect(state.permissionError).toBeNull();
  });

  it('success: fetches BOTH endpoints and returns roles + permissions + scope', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn((u: string) => {
      const url = String(u);
      if (url.includes('/api/admin/roles')) {
        return Promise.resolve(jsonResponse(ROLES_200));
      }
      return Promise.resolve(jsonResponse(PERMISSIONS_200));
    });
    vi.stubGlobal('fetch', fetchMock);

    const state = await getRbacCatalogState();
    expect(state.degraded).toBe(false);
    expect(state.noTenant).toBe(false);
    expect(state.permissionError).toBeNull();
    expect(state.scope).toBe('global');
    expect(state.roles).toHaveLength(2);
    expect(state.permissions).toEqual([
      'account.read',
      'audit.read',
      'operator.manage',
    ]);
    // Both endpoints fired concurrently — one page load, no polling.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
