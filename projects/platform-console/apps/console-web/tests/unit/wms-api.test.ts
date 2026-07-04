import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/wms-ops/api/wms-api.ts` — the security-critical core of
 * TASK-PC-FE-007 (the FIRST non-IAM federated domain).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.5 per-domain
 * credential selection — the EXACT INVERSE of the FE-002..006 assertion):
 *   - every wms call's bearer is the **IAM OIDC ACCESS token** (the
 *     `console_access_token` cookie), NEVER the exchanged operator token;
 *   - the operator-token path is ABSENT for wms (the wms client does NOT
 *     call `getOperatorToken()` — pinned so a future refactor cannot
 *     blanket-apply one domain's auth to all domains; the #569 invariant
 *     is GAP-domain-scoped and does NOT generalise to wms);
 *   - the console sends NO `X-Tenant-Id` (wms resolves tenant from the
 *     JWT `tenant_id` claim producer-side — tenant-model divergence);
 *   - reads carry NO mutation artifacts (no Idempotency-Key, no
 *     X-Operator-Reason, no body);
 *   - alert-ack carries `Idempotency-Key` (caller-supplied, stable per a
 *     confirmed action) + EMPTY body + NO `X-Operator-Reason` (wms does
 *     not define it — carrying GAP's § 2.4.1 reason header is a drift
 *     defect, asserted absent);
 *   - the wms NESTED error envelope `{ error: { code … } }` is parsed
 *     (NOT GAP's flat `{ code }`);
 *   - 401 → ApiError(401) (whole-session re-login); 403 → ApiError(403)
 *     inline; 404/422/409 → ApiError inline; 503/timeout →
 *     WmsUnavailableError (section degrades only);
 *   - `X-Read-Model-Lag-Seconds` is surfaced on the result.
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
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

// Spy the session module so we can assert WHICH credential accessor the
// wms client uses (the central per-domain-credential assertion). The
// real cookie reads still flow through the mocked next/headers jar.
import * as sessionModule from '@/shared/lib/session';

import {
  listInventory,
  listAlerts,
  acknowledgeAlert,
  getThroughput,
  getProjectionStatus,
} from '@/features/wms-ops/api/wms-api';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
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

/** wms NESTED error envelope (distinct from GAP's flat shape). */
function wmsError(
  code: string,
  status: number,
  message = 'err',
) {
  return new Response(
    JSON.stringify({
      error: { code, message, timestamp: '2026-05-19T00:00:00.000Z' },
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const INV_PAGE = {
  content: [
    {
      locationId: 'loc-1',
      skuId: 'sku-1',
      lotId: null,
      warehouseId: 'wh-1',
      locationCode: 'WH01-A-01',
      skuCode: 'SKU-1',
      availableQty: 80,
      reservedQty: 20,
      onHandQty: 100,
      lowStockFlag: false,
      lastEventAt: '2026-05-09T10:00:00Z',
      version: 5,
    },
  ],
  page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
  sort: 'lastEventAt,desc',
};

const ALERT_PAGE = {
  content: [
    {
      alertId: 'al-1',
      alertType: 'LOW_STOCK',
      warehouseId: 'wh-1',
      message: 'stock low',
      detectedAt: '2026-05-09T10:00:00Z',
      acknowledged: false,
    },
  ],
  page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
  sort: 'detectedAt,desc',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('wms-api — per-domain credential selection (the INVERSE of #569; § 2.4.5)', () => {
  it('sends the IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-wms');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(INV_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await listInventory({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-wms',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain(
      'http://wms.local/api/v1/admin/dashboard/inventory',
    );
  });

  it('uses getDomainFacingToken() (net-zero → base IAM token) and NEVER getOperatorToken() for wms (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    // ADR-MONO-020 D4 / § 2.7: the credential is now the DOMAIN-FACING token
    // (assumed-when-switched, else the base IAM token). With no assumed token
    // it resolves to the base token — net-zero. It is STILL never the
    // operator token (the per-domain rule / #569 boundary holds).
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(INV_PAGE)));

    await listInventory();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for wms — the inverse of the
    // FE-002..006 assertion; a future blanket-apply refactor would break
    // this.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listInventory().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (wms resolves tenant from the JWT claim — tenant-model divergence)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(INV_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await listInventory();

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    // wms gateway echoes/generates X-Request-Id (sent); X-Actor-Id is set
    // by the wms gateway from the JWT — the console does NOT forge it.
    expect(headers['X-Request-Id']).toBeTruthy();
    expect(headers['X-Actor-Id']).toBeUndefined();
  });
});

describe('wms-api — read vs mutation discipline (§ 2.4.5)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('reads carry NO mutation artifacts (no Idempotency-Key / X-Operator-Reason / body)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(INV_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await listInventory({ warehouseId: 'wh-1' });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
  });

  it('alert-ack carries Idempotency-Key + EMPTY body + NO X-Operator-Reason', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...ALERT_PAGE.content[0], acknowledged: true }));
    vi.stubGlobal('fetch', fetchMock);

    await acknowledgeAlert('al-1', 'idem-key-confirmed-1');

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(headers['Idempotency-Key']).toBe('idem-key-confirmed-1');
    // Reason-free: wms does NOT define X-Operator-Reason — carrying GAP's
    // § 2.4.1 reason header over is a drift defect.
    expect(headers['X-Operator-Reason']).toBeUndefined();
    // Empty body per admin-service-api.md § 1.6.
    expect((init as RequestInit).body).toBeUndefined();
    expect(String(url)).toContain(
      '/dashboard/alerts/al-1/acknowledge',
    );
  });

  it('alert-ack idempotency is stable per a passed key and fresh per a new one', async () => {
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(
        jsonResponse({ ...ALERT_PAGE.content[0], acknowledged: true }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await acknowledgeAlert('al-1', 'key-A');
    await acknowledgeAlert('al-1', 'key-A'); // same confirmed action retried
    await acknowledgeAlert('al-1', 'key-B'); // a new confirmed attempt

    const h0 = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    const h1 = (fetchMock.mock.calls[1][1] as RequestInit)
      .headers as Record<string, string>;
    const h2 = (fetchMock.mock.calls[2][1] as RequestInit)
      .headers as Record<string, string>;
    expect(h0['Idempotency-Key']).toBe('key-A');
    expect(h1['Idempotency-Key']).toBe('key-A');
    expect(h2['Idempotency-Key']).toBe('key-B');
  });
});

describe('wms-api — wms NESTED error envelope parsing (NOT IAM flat shape) + § 2.5', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) — whole-session re-login (no partial authed state)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)),
    );
    const err = await listInventory().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('403 → ApiError(403) inline "not available to your role"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)),
    );
    const err = await getProjectionStatus().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('FORBIDDEN');
  });

  it('parses the NESTED { error: { code } } shape (a IAM flat parser would miss it)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        wmsError('STATE_TRANSITION_INVALID', 422, 'already acknowledged'),
      ),
    );
    const err = await acknowledgeAlert('al-1', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    // The code came from `error.code`, NOT a flat `code` — proves the
    // wms-shape parser (a IAM flat parser would yield HTTP_422).
    expect(err.code).toBe('STATE_TRANSITION_INVALID');
    expect(err.message).toBe('already acknowledged');
  });

  it('409 DUPLICATE_REQUEST → ApiError(409) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('DUPLICATE_REQUEST', 409)),
    );
    const err = await acknowledgeAlert('al-1', 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
  });

  it('404 NOT_FOUND → ApiError(404) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('NOT_FOUND', 404)),
    );
    const err = await listAlerts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
  });

  it('400 VALIDATION_ERROR (throughput range) → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('VALIDATION_ERROR', 400)),
    );
    const err = await getThroughput({
      warehouseId: 'wh-1',
      from: '2026-01-01',
      to: '2026-12-31',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
  });

  it('a malformed / flat error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 404,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await listAlerts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404'); // synthetic fallback, no throw
  });

  it('503 → WmsUnavailableError (ONLY the wms section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listInventory().catch((e) => e);
    expect(err).toBeInstanceOf(WmsUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('503 CIRCUIT_OPEN → WmsUnavailableError(circuit_open)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('CIRCUIT_OPEN', 503)),
    );
    const err = await listAlerts().catch((e) => e);
    expect(err).toBeInstanceOf(WmsUnavailableError);
    expect(err.reason).toBe('circuit_open');
  });

  it('timeout → WmsUnavailableError(timeout)', async () => {
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
    const err = await listInventory().catch((e) => e);
    expect(err).toBeInstanceOf(WmsUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});

describe('wms-api — read-model lag honesty (§ 2.4.5)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('surfaces X-Read-Model-Lag-Seconds on the result (non-blocking hint)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(INV_PAGE, 200, { 'X-Read-Model-Lag-Seconds': '7.5' }),
      ),
    );
    const r = await listInventory();
    expect(r.lagSeconds).toBe(7.5);
    expect(r.data.content).toHaveLength(1);
  });

  it('lagSeconds is null when the header is absent (no lag)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(INV_PAGE)));
    const r = await listInventory();
    expect(r.lagSeconds).toBeNull();
  });
});

describe('wms-api — tolerant parsing (unknown/future enum never throws)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown alertType / extra fields parse without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [
            {
              alertId: 'al-9',
              alertType: 'FUTURE_ALERT_TYPE_V2',
              someNewField: 'x',
              detectedAt: '2026-06-01T00:00:00Z',
            },
          ],
          page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
        }),
      ),
    );
    const r = await listAlerts();
    expect(r.data.content[0].alertType).toBe('FUTURE_ALERT_TYPE_V2');
  });

  it('an inventory row with NULL locationCode/skuCode parses — master ref not yet projected, must NOT throw → no whole-section degrade (TASK-PC-FE-185)', async () => {
    // Regression: the admin read model leaves the denormalized master codes
    // NULL until admin_{location,sku}_ref projects. A `z.string().optional()`
    // rejects JSON null → InventoryPageSchema.parse throws → the wms 재고
    // section falsely degrades even though HTTP was 200. The table already
    // renders `code ?? id`, so null must parse (nullable), like `lotNo`.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [
            {
              locationId: 'loc-x',
              skuId: 'sku-x',
              lotId: null,
              warehouseId: 'wh-1',
              locationCode: null,
              skuCode: null,
              lotNo: null,
              availableQty: 85,
              reservedQty: 15,
              onHandQty: 100,
              lowStockFlag: false,
              lastEventAt: '2026-07-02T10:00:00Z',
              version: 4,
            },
          ],
          page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
        }),
      ),
    );
    const r = await listInventory();
    expect(r.data.content[0].locationCode).toBeNull();
    expect(r.data.content[0].skuCode).toBeNull();
    // sanity: the numeric buckets still parse alongside the null codes.
    expect(r.data.content[0].availableQty).toBe(85);
  });
});
