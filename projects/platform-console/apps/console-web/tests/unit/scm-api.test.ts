import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/scm-ops/api/scm-api.ts` — the security-critical core of
 * TASK-PC-FE-008 (the SECOND non-GAP federated domain; completes
 * ADR-MONO-013 Phase 4). STRICTLY READ-ONLY.
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.6 — REUSE of
 * the § 2.4.5 per-domain credential rule, NOT re-derived; same outcome as
 * wms / the EXACT INVERSE of the FE-002..006 GAP assertion):
 *   - every scm call's bearer is the **GAP OIDC ACCESS token** (the
 *     `console_access_token` cookie), NEVER the exchanged operator token;
 *   - the operator-token path is ABSENT for scm (the scm client does NOT
 *     call `getOperatorToken()` — pinned so a future refactor cannot
 *     blanket-apply one domain's auth to all domains; the #569 invariant
 *     is GAP-domain-scoped and does NOT generalise to scm);
 *   - the console sends NO `X-Tenant-Id` (scm resolves tenant from the
 *     JWT `tenant_id ∈ {scm,*}` claim producer-side — tenant-model
 *     divergence reused from § 2.4.5);
 *   - EVERY call is a pure GET — NO mutation artifacts anywhere (no
 *     Idempotency-Key, no X-Operator-Reason, no body, no PO write);
 *   - the scm FLAT error envelope `{ code, message, timestamp }` is
 *     parsed (NOT wms's NESTED `{ error: { code } }`);
 *   - the S5 `meta.warning` is a REQUIRED, surfaced field of every
 *     inventory-visibility view-model (never stripped);
 *   - 401 → ApiError(401) (whole-session re-login); 403 → ApiError(403)
 *     inline; 404/400/422 → ApiError inline; 429 → ScmRateLimitedError
 *     (ONE bounded backoff, NO storm); 503/timeout → ScmUnavailableError
 *     (section degrades only);
 *   - `X-Cache` is surfaced on the per-SKU result.
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..007 lane).
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

// Spy the session module so we can assert WHICH credential accessor the
// scm client uses (the central per-domain-credential assertion).
import * as sessionModule from '@/shared/lib/session';

import {
  listPurchaseOrders,
  getPurchaseOrder,
  getSnapshot,
  getSkuBreakdown,
  getStaleness,
  getNodes,
} from '@/features/scm-ops/api/scm-api';
import {
  ApiError,
  ScmUnavailableError,
  ScmRateLimitedError,
} from '@/shared/api/errors';
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

/** scm FLAT error envelope (distinct from wms's NESTED shape). */
function scmError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({
      code,
      message,
      timestamp: '2026-05-19T00:00:00.000Z',
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const PO_ENVELOPE = {
  data: {
    content: [
      {
        id: '01HZWX',
        tenantId: 'scm',
        poNumber: 'PO-A1B2C3D4',
        supplierId: 'sup-1',
        status: 'SUBMITTED',
        totalAmount: '125000.00',
        currency: 'KRW',
        createdAt: '2026-05-11T08:30:00Z',
        lines: [
          {
            id: 'l-1',
            lineNo: 1,
            sku: 'SKU-001',
            quantity: '10.0000',
            unitPrice: '12500.00',
            receivedQuantity: '0.0000',
          },
        ],
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  },
  meta: { timestamp: '2026-05-11T08:30:00Z' },
};

const SNAPSHOT_ENVELOPE = {
  data: {
    content: [
      {
        id: 'uuid-1',
        nodeId: 'node-1',
        sku: 'SKU-001',
        quantity: 100,
        lastEventAt: '2026-05-01T10:00:00Z',
        version: 3,
        staleness: 'FRESH',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  },
  meta: {
    timestamp: '2026-05-01T10:05:00Z',
    warning: 'Not for procurement decisions (S5)',
    staleness: 'ALL_FRESH',
  },
};

const SKU_ENVELOPE = {
  data: {
    sku: 'SKU-001',
    nodes: [
      { nodeId: 'uuid-1', quantity: 100, staleness: 'FRESH' },
      { nodeId: 'uuid-2', quantity: 50, staleness: 'STALE' },
    ],
    totalQuantity: 150,
  },
  meta: {
    timestamp: '2026-05-01T10:05:00Z',
    warning: 'Not for procurement decisions (S5)',
  },
};

const STALENESS_ENVELOPE = {
  data: [
    {
      nodeId: 'node-1',
      stalenessStatus: 'UNREACHABLE',
      lastEventAt: null,
      lastCheckedAt: '2026-05-01T10:05:00Z',
    },
  ],
  meta: {
    timestamp: '...',
    warning: 'Not for procurement decisions (S5)',
  },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('scm-api — per-domain credential selection (REUSE of § 2.4.5; the INVERSE of #569)', () => {
  it('sends the GAP OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-scm');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PO_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listPurchaseOrders({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-scm',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain('http://scm.local/api/v1/procurement/po');
  });

  it('uses getDomainFacingToken() (net-zero → base GAP token) and NEVER getOperatorToken() for scm (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    // ADR-MONO-020 D4 / § 2.7: domain-facing token (assumed-when-switched,
    // else base) — STILL never the operator token.
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(PO_ENVELOPE)),
    );

    await listPurchaseOrders();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for scm — same shape as the FE-007
    // wms assertion; a future blanket-apply refactor would break this.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the GAP session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listPurchaseOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (scm resolves tenant from the JWT claim — tenant-model divergence)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PO_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listPurchaseOrders();

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('scm-api — STRICTLY read-only (no mutation artifacts anywhere; § 2.4.6)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('every read is a pure GET with NO mutation artifacts', async () => {
    const calls: Array<[string, RequestInit]> = [];
    const fetchMock = vi.fn((u: string, init?: RequestInit) => {
      calls.push([String(u), init as RequestInit]);
      const us = String(u);
      // PO detail = `/procurement/po/{id}`; PO list = `/procurement/po?…`.
      if (/\/procurement\/po\/[^?]/.test(us))
        return Promise.resolve(
          jsonResponse({
            data: PO_ENVELOPE.data.content[0],
            meta: { timestamp: 'x' },
          }),
        );
      if (us.includes('/procurement/po'))
        return Promise.resolve(jsonResponse(PO_ENVELOPE));
      if (us.includes('/sku/'))
        return Promise.resolve(jsonResponse(SKU_ENVELOPE));
      if (us.includes('/snapshot'))
        return Promise.resolve(jsonResponse(SNAPSHOT_ENVELOPE));
      if (us.includes('/staleness'))
        return Promise.resolve(jsonResponse(STALENESS_ENVELOPE));
      // /nodes → a valid NodesResponse (empty list + the REQUIRED S5 meta).
      return Promise.resolve(
        jsonResponse({
          data: [],
          meta: { warning: 'Not for procurement decisions (S5)' },
        }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    await listPurchaseOrders({ status: 'SUBMITTED' });
    await getPurchaseOrder('01HZWX');
    await getSnapshot({ page: 0 });
    await getSkuBreakdown('SKU-001');
    await getStaleness();
    await getNodes();

    expect(calls.length).toBe(6);
    for (const [, init] of calls) {
      const h = init.headers as Record<string, string>;
      expect(init.method).toBe('GET');
      expect(init.body).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Content-Type']).toBeUndefined();
    }
    // The api module exports NO PO-write / webhook / mutation function.
    const mod = await import('@/features/scm-ops/api/scm-api');
    expect(Object.keys(mod).sort()).toEqual(
      [
        'getNodes',
        'getPurchaseOrder',
        'getSkuBreakdown',
        'getSnapshot',
        'getStaleness',
        'listPurchaseOrders',
      ].sort(),
    );
  });
});

describe('scm-api — S5 meta.warning is REQUIRED + surfaced (never stripped; § 2.4.6)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('snapshot surfaces the producer S5 warning verbatim', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(SNAPSHOT_ENVELOPE)),
    );
    const r = await getSnapshot();
    expect(r.data.meta.warning).toBe(
      'Not for procurement decisions (S5)',
    );
  });

  it('per-SKU surfaces the producer S5 warning + X-Cache', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(SKU_ENVELOPE, 200, { 'X-Cache': 'HIT' }),
      ),
    );
    const r = await getSkuBreakdown('SKU-001');
    expect(r.data.meta.warning).toBe(
      'Not for procurement decisions (S5)',
    );
    expect(r.cache).toBe('HIT');
  });

  it('staleness surfaces the S5 warning AND the UNREACHABLE node honestly', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(STALENESS_ENVELOPE)),
    );
    const r = await getStaleness();
    expect(r.data.meta.warning).toBe(
      'Not for procurement decisions (S5)',
    );
    expect(r.data.data[0].stalenessStatus).toBe('UNREACHABLE');
  });

  it('a producer that OMITS warning (contract breach) still surfaces the S5 string (never silently dropped)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: { content: [], page: 0, size: 20, totalElements: 0 },
          meta: { timestamp: 'x' }, // no `warning`
        }),
      ),
    );
    const r = await getSnapshot();
    // The obligation is to NEVER strip/hide it — the parser falls back to
    // the canonical S5 string rather than dropping the field.
    expect(r.data.meta.warning).toBe(
      'Not for procurement decisions (S5)',
    );
  });
});

describe('scm-api — scm FLAT error envelope (NOT wms NESTED) + § 2.5', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) — whole-session re-login (no partial authed state)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('UNAUTHORIZED', 401)),
    );
    const err = await listPurchaseOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline "not scoped"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('TENANT_FORBIDDEN', 403)),
    );
    const err = await getSnapshot().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('parses the FLAT { code } shape (a wms NESTED parser would yield HTTP_404)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        scmError('PO_NOT_FOUND', 404, 'po missing'),
      ),
    );
    const err = await getPurchaseOrder('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    // The code came from the FLAT top-level `code`, NOT a nested
    // `error.code` — proves the scm-shape parser (a wms-nested parser
    // would yield the synthetic HTTP_404).
    expect(err.code).toBe('PO_NOT_FOUND');
    expect(err.message).toBe('po missing');
  });

  it('a wms-NESTED { error: { code } } body is NOT mis-parsed (falls back synthetic, no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ error: { code: 'WMS_SHAPE', message: 'x' } }),
          { status: 422, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const err = await getSnapshot().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    // The scm parser reads the FLAT top-level `code` — a wms-nested body
    // has none, so it degrades to the synthetic fallback (NOT 'WMS_SHAPE')
    // and never crashes.
    expect(err.code).toBe('HTTP_422');
  });

  it('400 VALIDATION_ERROR → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('VALIDATION_ERROR', 400)),
    );
    const err = await listPurchaseOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
  });

  it('503 → ScmUnavailableError (ONLY the scm section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getSnapshot().catch((e) => e);
    expect(err).toBeInstanceOf(ScmUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → ScmUnavailableError(timeout)', async () => {
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
    const err = await getStaleness().catch((e) => e);
    expect(err).toBeInstanceOf(ScmUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 404,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await getPurchaseOrder('x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404'); // synthetic fallback, no throw
  });
});

describe('scm-api — 429 Retry-After: ONE bounded backoff, NO retry storm (§ 2.4.6 Edge Case)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('429 once → ONE bounded retry honouring Retry-After, then succeeds (no storm)', async () => {
    let n = 0;
    const fetchMock = vi.fn(() => {
      n += 1;
      if (n === 1) {
        return Promise.resolve(
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
        );
      }
      return Promise.resolve(jsonResponse(PO_ENVELOPE));
    });
    vi.stubGlobal('fetch', fetchMock);

    const r = await listPurchaseOrders();
    // Exactly TWO calls — the original + ONE bounded retry. NEVER more.
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(r.data.content).toHaveLength(1);
  });

  it('429 persisting after the bounded retry → ScmRateLimitedError (NO further storm)', async () => {
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

    const err = await getSnapshot().catch((e) => e);
    expect(err).toBeInstanceOf(ScmRateLimitedError);
    // The storm guard: at most the original + ONE retry — NOT a loop.
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((err as ScmRateLimitedError).retryAfterSeconds).toBe(1);
  });
});

describe('scm-api — tolerant parsing (unknown/future PO + node status never throws)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown PO status / extra fields parse without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: {
            content: [
              { id: 'po-9', status: 'FUTURE_STATUS_V2', extra: 'x' },
            ],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          },
          meta: { timestamp: 'x' },
        }),
      ),
    );
    const r = await listPurchaseOrders();
    expect(r.data.content[0].status).toBe('FUTURE_STATUS_V2');
  });

  it('an unknown node status parses without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [
            { id: 'n-1', status: 'FUTURE_NODE_STATUS', name: 'X' },
          ],
          meta: { warning: 'Not for procurement decisions (S5)' },
        }),
      ),
    );
    const r = await getNodes();
    expect(r.data.data[0].status).toBe('FUTURE_NODE_STATUS');
    expect(r.data.meta.warning).toBe(
      'Not for procurement decisions (S5)',
    );
  });
});
