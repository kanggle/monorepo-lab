import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin scm-ops proxy route handlers (TASK-PC-FE-008 — § 2.4.6):
 *   - read GET (po list/detail, snapshot, sku, staleness, nodes): GAP
 *     OIDC access token attached server-side (NOT the operator token);
 *     no mutation artifacts; STRICTLY READ-ONLY (GET-only routes).
 *   - 401 → 401 (whole-session re-login signal; no partial authed state).
 *   - 403 → 403 (token not scm-scoped inline, no crash).
 *   - 429 → 429 + Retry-After (one bounded backoff already done; the
 *     client must NOT re-storm).
 *   - 404 / 400 / 422 → passthrough (inline actionable).
 *   - 503 / timeout → 503 (scm section degrades only; shell intact).
 *   - the S5 meta.warning survives to the client (never stripped).
 *   - the per-SKU X-Cache header is surfaced honestly.
 *
 * There is NO mutation proxy route at all (no PO write, no webhook).
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
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

import { GET as poGET } from '@/app/api/scm/po/route';
import { GET as poDetailGET } from '@/app/api/scm/po/[poId]/route';
import { GET as snapshotGET } from '@/app/api/scm/snapshot/route';
import { GET as skuGET } from '@/app/api/scm/sku/[sku]/route';
import { GET as stalenessGET } from '@/app/api/scm/staleness/route';
import { GET as nodesGET } from '@/app/api/scm/nodes/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}
function scmError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const PO_ENV = {
  data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
  meta: { timestamp: 'x' },
};
const SNAP_ENV = {
  data: { content: [], page: 0, size: 20, totalElements: 0 },
  meta: { warning: 'Not for procurement decisions (S5)' },
};
const SKU_ENV = {
  data: { sku: 'SKU-1', nodes: [], totalQuantity: 0 },
  meta: { warning: 'Not for procurement decisions (S5)' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/scm/po proxy (read-only)', () => {
  it('attaches the GAP OIDC access token (NOT the operator token), forwards filters', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PO_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await poGET(
      new Request(
        'http://console.local/api/scm/po?status=SUBMITTED&size=999',
      ),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    const upstream = new URL(String(url));
    expect(upstream.searchParams.get('status')).toBe('SUBMITTED');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it('no GAP session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await poGET(new Request('http://console.local/api/scm/po'));
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 TENANT_FORBIDDEN → 403 (inline not scoped)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('TENANT_FORBIDDEN', 403)),
    );
    const res = await poGET(new Request('http://console.local/api/scm/po'));
    expect(res.status).toBe(403);
  });

  it('503 from scm → 503 (scm section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await poGET(new Request('http://console.local/api/scm/po'));
    expect(res.status).toBe(503);
  });
});

describe('GET /api/scm/po/{poId} proxy (read-only — no PO write route)', () => {
  it('forwards the detail read with the GAP OIDC token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ data: { id: 'po-1', status: 'DRAFT' }, meta: {} }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await poDetailGET(
      new Request('http://console.local/api/scm/po/po-1'),
      { params: Promise.resolve({ poId: 'po-1' }) },
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toContain(
      '/api/v1/procurement/po/po-1',
    );
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('GET');
  });

  it('404 PO_NOT_FOUND → 404 inline', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('PO_NOT_FOUND', 404)),
    );
    const res = await poDetailGET(
      new Request('http://console.local/api/scm/po/nope'),
      { params: Promise.resolve({ poId: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('PO_NOT_FOUND');
  });
});

describe('GET /api/scm/snapshot proxy — S5 meta.warning survives', () => {
  it('forwards the FULL envelope so meta.warning reaches the client', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(SNAP_ENV)),
    );
    const res = await snapshotGET(
      new Request('http://console.local/api/scm/snapshot'),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    // The S5 warning is NOT stripped by the proxy.
    expect(body.meta.warning).toBe('Not for procurement decisions (S5)');
  });
});

describe('GET /api/scm/sku/{sku} proxy — S5 + X-Cache surfaced', () => {
  it('forwards meta.warning AND the X-Cache header honestly', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse(SKU_ENV, 200, { 'X-Cache': 'MISS' })),
    );
    const res = await skuGET(
      new Request('http://console.local/api/scm/sku/SKU-1'),
      { params: Promise.resolve({ sku: 'SKU-1' }) },
    );
    expect(res.status).toBe(200);
    expect(res.headers.get('X-Cache')).toBe('MISS');
    const body = await res.json();
    expect(body.meta.warning).toBe('Not for procurement decisions (S5)');
  });
});

describe('GET /api/scm/staleness + /nodes proxies (read-only)', () => {
  it('staleness forwards meta.warning + the honest node status', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [{ nodeId: 'n-1', stalenessStatus: 'STALE' }],
          meta: { warning: 'Not for procurement decisions (S5)' },
        }),
      ),
    );
    const res = await stalenessGET();
    expect(res.status).toBe(200);
    const b = await res.json();
    expect(b.meta.warning).toBe('Not for procurement decisions (S5)');
    expect(b.data[0].stalenessStatus).toBe('STALE');
  });

  it('nodes 429 → 429 + Retry-After (bounded; client must not re-storm)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // Persisting 429 → the api client does ONE bounded retry then surfaces
    // ScmRateLimitedError, which the proxy maps to a 429 + Retry-After.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
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
    const res = await nodesGET();
    expect(res.status).toBe(429);
    expect(res.headers.get('Retry-After')).toBe('1');
  });
});
