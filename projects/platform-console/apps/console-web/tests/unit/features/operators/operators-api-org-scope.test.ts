import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operators/api/operators-api.ts` org_scope client (TASK-PC-FE-050
 * / TASK-BE-339):
 *   - `listOperatorAssignments(operatorId)` → GET .../assignments; an ABSENT
 *     `orgScope` key (producer NON_NULL omit) PARSES to `null` (전체); an
 *     explicit `[]` (차단) is PRESERVED (NOT coerced to null); a populated
 *     array is preserved verbatim.
 *   - `setOperatorOrgScope(...)` → PUT
 *     .../assignments/{tenantId}/org-scope; the tri-state body is exactly
 *     `{ orgScope: null }` (clear / 전체) | `{ orgScope: [] }` (차단) |
 *     `{ orgScope: [ids] }`; the bearer is the operator token (NOT the GAP
 *     OIDC access token — #569); `X-Tenant-Id` is the active tenant;
 *     `X-Operator-Reason` present; NO `Idempotency-Key`.
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
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
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
  listOperatorAssignments,
  setOperatorOrgScope,
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

describe('listOperatorAssignments — GET .../assignments parse (NON_NULL omit → null)', () => {
  it('ABSENT orgScope key parses to null (전체 / net-zero); permissionSetId absent → null', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        // orgScope + permissionSetId OMITTED (producer @JsonInclude.NON_NULL).
        jsonResponse({ assignments: [{ tenantId: 'acme-corp' }] }),
      ),
    );
    const res = await listOperatorAssignments('op-1');
    expect(res.assignments).toHaveLength(1);
    expect(res.assignments[0].tenantId).toBe('acme-corp');
    expect(res.assignments[0].orgScope).toBeNull();
    expect(res.assignments[0].permissionSetId).toBeNull();
  });

  it('explicit [] (차단 / zero-scope) is PRESERVED (NOT coerced to null)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          assignments: [{ tenantId: 'acme-corp', orgScope: [], permissionSetId: 7 }],
        }),
      ),
    );
    const res = await listOperatorAssignments('op-1');
    expect(res.assignments[0].orgScope).toEqual([]);
    expect(res.assignments[0].permissionSetId).toBe(7);
  });

  it('populated subtree array preserved verbatim; empty array → no assignment row', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          assignments: [
            { tenantId: 'acme-corp', orgScope: ['dept-sales', 'dept-eng'] },
          ],
        }),
      ),
    );
    const res = await listOperatorAssignments('op-1');
    expect(res.assignments[0].orgScope).toEqual(['dept-sales', 'dept-eng']);

    // empty assignments (home-tenant-only operator).
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ assignments: [] })),
    );
    const res2 = await listOperatorAssignments('op-2');
    expect(res2.assignments).toHaveLength(0);
  });

  it('uses the operator token bearer (NOT the GAP OIDC access token) + active X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ assignments: [] }));
    vi.stubGlobal('fetch', fetchMock);
    await listOperatorAssignments('op-1');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/op-1/assignments');
    expect((init as RequestInit).method).toBe('GET');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OPERATOR-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('acme-corp');
    // read path — no mutation headers.
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });
});

describe('setOperatorOrgScope — PUT tri-state payload + header matrix', () => {
  it('null (clear / 전체) → body { orgScope: null } as the JSON null literal; reason header; NO key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ tenantId: 'acme-corp' }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await setOperatorOrgScope(
      'op-1',
      'acme-corp',
      { orgScope: null },
      'reset to full scope',
    );
    // PUT response: orgScope omitted → parsed back to null.
    expect(res.orgScope).toBeNull();

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/admin/operators/op-1/assignments/acme-corp/org-scope',
    );
    expect((init as RequestInit).method).toBe('PUT');
    const rawBody = (init as RequestInit).body as string;
    // explicit JSON null literal (clear intent) — NOT omitted.
    expect(rawBody).toContain('"orgScope":null');
    expect(JSON.parse(rawBody)).toEqual({ orgScope: null });

    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OPERATOR-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('acme-corp');
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('reset to full scope');
    // NO idempotency key (idempotent full-replace PUT).
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);
  });

  it('[] (차단 / zero-scope) → body { orgScope: [] } (distinct from null)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ tenantId: 'acme-corp', orgScope: [] }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await setOperatorOrgScope(
      'op-1',
      'acme-corp',
      { orgScope: [] },
      'lock out',
    );
    expect(res.orgScope).toEqual([]);
    const rawBody = (fetchMock.mock.calls[0][1] as RequestInit).body as string;
    expect(JSON.parse(rawBody)).toEqual({ orgScope: [] });
    // distinct from null on the wire.
    expect(rawBody).not.toContain('"orgScope":null');
  });

  it('[ids] (subtree) → body { orgScope: [ids] } verbatim', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        tenantId: 'acme-corp',
        orgScope: ['dept-sales', 'dept-eng'],
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await setOperatorOrgScope(
      'op-1',
      'acme-corp',
      { orgScope: ['dept-sales', 'dept-eng'] },
      'scope to sales+eng',
    );
    expect(res.orgScope).toEqual(['dept-sales', 'dept-eng']);
    const rawBody = (fetchMock.mock.calls[0][1] as RequestInit).body as string;
    expect(JSON.parse(rawBody)).toEqual({
      orgScope: ['dept-sales', 'dept-eng'],
    });
  });

  it('tenantId is URL-encoded in the path (path-traversal defence)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ tenantId: 'acme-corp' }));
    vi.stubGlobal('fetch', fetchMock);
    await setOperatorOrgScope('op/../evil', 'ten/../x', { orgScope: null }, 'r');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      `/api/admin/operators/${encodeURIComponent('op/../evil')}/assignments/${encodeURIComponent('ten/../x')}/org-scope`,
    );
  });
});
