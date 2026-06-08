import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/wms-outbound-ops/api/outbound-api.ts` — the security-critical
 * core of TASK-PC-FE-057 (the SECOND wms surface — ADR-MONO-022 § D7).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.5.1, inherited
 * from § 2.4.5 — the EXACT INVERSE of the FE-002..006 assertion):
 *   - every outbound call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie when not
 *     tenant-switched), NEVER the exchanged operator token;
 *   - the operator-token path is ABSENT for wms outbound (`getOperatorToken`
 *     NOT called — pinned so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (wms resolves tenant from the JWT
 *     `tenant_id=wms` claim producer-side);
 *   - reads carry NO mutation artifacts (no Idempotency-Key, no
 *     X-Operator-Reason, no body);
 *   - each mutation carries `Idempotency-Key` (caller-supplied, stable per a
 *     confirmed action, fresh per a new one) + NO `X-Operator-Reason`
 *     (asserted absent — drift defect);
 *   - the wms NESTED error envelope `{ error: { code … } }` is parsed (NOT
 *     IAM's flat `{ code }`);
 *   - 401 → ApiError(401); 403 → ApiError(403); 404/422/409 → ApiError inline;
 *     503/timeout → WmsOutboundUnavailableError (section degrades only).
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
    WMS_OUTBOUND_BASE_URL: 'http://wms.local/api/v1/outbound',
    WMS_OUTBOUND_TIMEOUT_MS: 50,
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
  listOrders,
  getOrder,
  getSaga,
  listPickingRequests,
  confirmPick,
  createPackingUnit,
  sealPackingUnit,
  confirmShipping,
} from '@/features/wms-outbound-ops/api/outbound-api';
import {
  ApiError,
  WmsOutboundUnavailableError,
} from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** wms NESTED error envelope (distinct from IAM's flat shape). */
function wmsError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({
      error: { code, message, timestamp: '2026-06-08T00:00:00.000Z' },
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ORDER_PAGE = {
  content: [
    {
      orderId: 'o-1',
      orderNo: 'ORD-1',
      status: 'PICKING',
      sagaState: 'RESERVED',
      lineCount: 1,
      createdAt: '2026-06-08T10:00:00Z',
    },
  ],
  page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
  sort: 'updatedAt,desc',
};

const ORDER_DETAIL = {
  orderId: 'o-1',
  orderNo: 'ORD-1',
  status: 'PACKED',
  sagaState: 'PACKING_CONFIRMED',
  lines: [
    {
      orderLineId: 'ol-1',
      lineNo: 1,
      skuId: 'sku-1',
      lotId: null,
      qtyOrdered: 50,
    },
  ],
  version: 3,
};

const PICKING_LIST = {
  content: [
    {
      pickingRequestId: 'pr-1',
      orderId: 'o-1',
      status: 'SUBMITTED',
      lines: [
        {
          pickingRequestLineId: 'prl-1',
          orderLineId: 'ol-1',
          skuId: 'sku-1',
          lotId: null,
          locationId: 'loc-9',
          qtyToPick: 50,
        },
      ],
      version: 0,
    },
  ],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('outbound-api — per-domain credential selection (§ 2.4.5.1)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-wms');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await listOrders({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-wms',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain('http://wms.local/api/v1/outbound/orders');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(ORDER_PAGE)));

    await listOrders();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (wms resolves tenant from the JWT claim)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    await listOrders();

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
    expect(headers['X-Actor-Id']).toBeUndefined();
  });
});

describe('outbound-api — read vs mutation discipline (§ 2.4.5.1)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('reads carry NO mutation artifacts (no Idempotency-Key / X-Operator-Reason / body)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(ORDER_DETAIL)));
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_DETAIL));
    vi.stubGlobal('fetch', fetchMock);

    await getOrder('o-1');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
  });

  it('confirmPick carries Idempotency-Key + NO X-Operator-Reason; maps planned → confirmed', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ pickingConfirmationId: 'pc-1', orderStatus: 'PICKED' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await confirmPick(
      'pr-1',
      [
        {
          orderLineId: 'ol-1',
          skuId: 'sku-1',
          lotId: null,
          actualLocationId: 'loc-9',
          qtyConfirmed: 50,
        },
      ],
      'idem-pick-1',
    );

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(headers['Idempotency-Key']).toBe('idem-pick-1');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(String(url)).toContain('/picking-requests/pr-1/confirmations');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.lines[0].actualLocationId).toBe('loc-9');
    expect(body.lines[0].qtyConfirmed).toBe(50);
  });

  it('seal (PATCH) carries Idempotency-Key + version + NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ packingUnitId: 'pu-1', version: 1, status: 'SEALED' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await sealPackingUnit('pu-1', 0, 'idem-seal-1');

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('PATCH');
    expect(headers['Idempotency-Key']).toBe('idem-seal-1');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(String(url)).toContain('/packing-units/pu-1');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ seal: true, version: 0 });
  });

  it('ship carries the order version + Idempotency-Key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ shipmentId: 's-1', orderStatus: 'SHIPPED' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await confirmShipping('o-1', 3, 'idem-ship-1');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe('idem-ship-1');
    const body = JSON.parse(init.body as string);
    expect(body.version).toBe(3);
    expect(body.carrierCode).toBe('DEMO-CARRIER');
  });

  it('createPackingUnit posts all order lines with qty = ordered qty', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ packingUnitId: 'pu-1', version: 0, status: 'OPEN' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await createPackingUnit(
      'o-1',
      'BOX-abc',
      [{ orderLineId: 'ol-1', skuId: 'sku-1', lotId: null, qty: 50 }],
      'idem-create-1',
    );

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe('idem-create-1');
    const body = JSON.parse(init.body as string);
    expect(body.packingType).toBe('BOX');
    expect(body.lines[0].qty).toBe(50);
  });
});

describe('outbound-api — reads (saga + picking-requests)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getSaga returns the saga state', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ sagaId: 'sg-1', orderId: 'o-1', state: 'RESERVED' }),
      ),
    );
    const saga = await getSaga('o-1');
    expect(saga.state).toBe('RESERVED');
  });

  it('listPickingRequests parses the planned lines (locationId + qtyToPick)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(PICKING_LIST)));
    const list = await listPickingRequests('o-1');
    expect(list.content[0].lines[0].locationId).toBe('loc-9');
    expect(list.content[0].lines[0].qtyToPick).toBe(50);
  });

  it('listPickingRequests tolerates an empty content array (not yet reserved)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ content: [] })),
    );
    const list = await listPickingRequests('o-1');
    expect(list.content).toHaveLength(0);
  });

  it('an unknown/future status enum parses without throwing (tolerant)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          ...ORDER_DETAIL,
          status: 'FUTURE_STATUS_V2',
          extraField: 'x',
        }),
      ),
    );
    const d = await getOrder('o-1');
    expect(d.status).toBe('FUTURE_STATUS_V2');
  });
});

describe('outbound-api — wms NESTED error envelope + § 2.5 resilience', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)));
    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 → ApiError(403) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)));
    const err = await getOrder('o-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('FORBIDDEN');
  });

  it('parses the NESTED { error: { code } } shape (a flat parser would miss it)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        wmsError('STATE_TRANSITION_INVALID', 422, 'pack before pick'),
      ),
    );
    const err = await sealPackingUnit('pu-1', 0, 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('STATE_TRANSITION_INVALID');
    expect(err.message).toBe('pack before pick');
  });

  it('409 CONFLICT (optimistic lock) → ApiError(409) inline (no silent retry)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('CONFLICT', 409)));
    const err = await confirmShipping('o-1', 1, 'k').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('CONFLICT');
  });

  it('404 ORDER_NOT_FOUND → ApiError(404) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('ORDER_NOT_FOUND', 404)));
    const err = await getOrder('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
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
    const err = await getOrder('o-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → WmsOutboundUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(WmsOutboundUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → WmsOutboundUnavailableError(timeout)', async () => {
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
    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(WmsOutboundUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});
