import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin wms-outbound-ops proxy route handlers (TASK-PC-FE-057 —
 * § 2.4.5.1):
 *   - reads (list / drill): domain-facing IAM OIDC token attached server-side
 *     (NOT the operator token); no X-Tenant-Id; no mutation artifacts.
 *   - Pick POST: COMPOUND — listPickingRequests → confirm with planned lines
 *     (actualLocationId = locationId, qtyConfirmed = qtyToPick).
 *   - Pack POST: COMPOUND — create packing-unit THEN seal (two upstream calls,
 *     each its own Idempotency-Key, seal uses the create-response version).
 *   - Ship POST: getOrder for version → confirm shipping.
 *   - NO X-Operator-Reason on any mutation.
 *   - 401 → no upstream call when the IAM session is absent.
 *   - bad action body (no idempotencyKey) → 422 (no upstream call).
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

import { GET as ordersGET } from '@/app/api/wms/outbound/route';
import { GET as drillGET } from '@/app/api/wms/outbound/[orderId]/route';
import { POST as pickPOST } from '@/app/api/wms/outbound/[orderId]/pick/route';
import { POST as packPOST } from '@/app/api/wms/outbound/[orderId]/pack/route';
import { POST as shipPOST } from '@/app/api/wms/outbound/[orderId]/ship/route';
import { POST as cancelPOST } from '@/app/api/wms/outbound/[orderId]/cancel/route';
import { POST as retryPOST } from '@/app/api/wms/outbound/[orderId]/retry-tms/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function wmsError(code: string, status: number) {
  return new Response(
    JSON.stringify({ error: { code, message: 'e', timestamp: 't' } }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ORDER_PAGE = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};
const ORDER_DETAIL = {
  orderId: 'o-1',
  orderNo: 'ORD-1',
  status: 'PACKED',
  sagaState: 'PACKING_CONFIRMED',
  lines: [{ orderLineId: 'ol-1', skuId: 'sku-1', lotId: null, qtyOrdered: 50 }],
  version: 3,
};
const SAGA = { sagaId: 'sg-1', orderId: 'o-1', state: 'PACKING_CONFIRMED' };
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

function actionReq(body: unknown) {
  return new Request('http://console.local/api/wms/outbound/o-1/pick', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/wms/outbound (list) proxy', () => {
  it('attaches the domain-facing IAM OIDC token (NOT the operator token), forwards filters', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_PAGE));
    vi.stubGlobal('fetch', fetchMock);

    const res = await ordersGET(
      new Request('http://console.local/api/wms/outbound?status=PICKING&size=999'),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    const upstream = new URL(String(url));
    expect(upstream.searchParams.get('status')).toBe('PICKING');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await ordersGET(
      new Request('http://console.local/api/wms/outbound'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await ordersGET(
      new Request('http://console.local/api/wms/outbound'),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/wms/outbound/{orderId} (drill) proxy', () => {
  it('composes { detail, saga } from two reads with the domain-facing token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).endsWith('/saga')
          ? jsonResponse(SAGA)
          : jsonResponse(ORDER_DETAIL),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await drillGET(
      new Request('http://console.local/api/wms/outbound/o-1'),
      { params: Promise.resolve({ orderId: 'o-1' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.detail.orderId).toBe('o-1');
    expect(body.saga.state).toBe('PACKING_CONFIRMED');
    // Both legs carry the domain-facing token, no mutation artifacts.
    for (const call of fetchMock.mock.calls) {
      const h = (call[1] as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
      expect(h['Idempotency-Key']).toBeUndefined();
    }
  });
});

describe('POST /api/wms/outbound/{orderId}/pick (compound: list → confirm)', () => {
  it('reads picking-requests then confirms with planned lines mapped', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/picking-requests') &&
          !String(url).includes('/confirmations')
          ? jsonResponse(PICKING_LIST)
          : jsonResponse({ pickingConfirmationId: 'pc-1', orderStatus: 'PICKED' }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await pickPOST(actionReq({ idempotencyKey: 'idem-pick-1' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(200);

    // First call = list picking-requests (GET); second = confirm (POST).
    const confirmCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/confirmations'),
    )!;
    const init = confirmCall[1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(init.method).toBe('POST');
    expect(h['Idempotency-Key']).toBe('idem-pick-1');
    expect(h['X-Operator-Reason']).toBeUndefined();
    const body = JSON.parse(init.body as string);
    // Confirm-as-planned: locationId → actualLocationId, qtyToPick → qtyConfirmed.
    expect(body.lines[0].actualLocationId).toBe('loc-9');
    expect(body.lines[0].qtyConfirmed).toBe(50);
    expect(body.lines[0].orderLineId).toBe('ol-1');
  });

  it('empty picking-requests content → 422 actionable (no confirm call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ content: [] }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await pickPOST(actionReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('OUTBOUND_NO_PICKING_REQUEST');
    // No confirmation call was fired.
    expect(
      fetchMock.mock.calls.some((c) => String(c[0]).includes('/confirmations')),
    ).toBe(false);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await pickPOST(actionReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a body without idempotencyKey → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await pickPOST(actionReq({}), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/wms/outbound/{orderId}/pack (compound: create → seal)', () => {
  it('creates the packing-unit THEN seals it (two calls, own keys, version threaded)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (init?.method === 'PATCH') {
        // PATCH seal → 200 SEALED
        return Promise.resolve(
          jsonResponse({ packingUnitId: 'pu-1', version: 1, status: 'SEALED' }),
        );
      }
      if (String(url).includes('/packing-units') && init?.method === 'POST') {
        // create → 201 with packingUnitId + version
        return Promise.resolve(
          jsonResponse({ packingUnitId: 'pu-1', version: 0, status: 'OPEN' }),
        );
      }
      // GET /orders/{id} for the order lines
      return Promise.resolve(jsonResponse(ORDER_DETAIL));
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await packPOST(actionReq({ idempotencyKey: 'idem-pack-1' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(200);

    const createCall = fetchMock.mock.calls.find(
      (c) =>
        (c[1] as RequestInit).method === 'POST' &&
        String(c[0]).includes('/packing-units'),
    )!;
    const sealCall = fetchMock.mock.calls.find(
      (c) => (c[1] as RequestInit).method === 'PATCH',
    )!;
    expect(createCall).toBeDefined();
    expect(sealCall).toBeDefined();

    const createH = (createCall[1] as RequestInit).headers as Record<string, string>;
    const sealH = (sealCall[1] as RequestInit).headers as Record<string, string>;
    // Each compound call gets its OWN stable idempotency key (derived).
    expect(createH['Idempotency-Key']).not.toBe(sealH['Idempotency-Key']);
    expect(createH['Idempotency-Key']).toContain('idem-pack-1');
    expect(sealH['Idempotency-Key']).toContain('idem-pack-1');
    expect(createH['X-Operator-Reason']).toBeUndefined();
    expect(sealH['X-Operator-Reason']).toBeUndefined();
    // The seal body carries the CREATE-response version (0), not a fabricated one.
    const sealBody = JSON.parse((sealCall[1] as RequestInit).body as string);
    expect(sealBody).toEqual({ seal: true, version: 0 });
    // The create body carries all order lines (qty = ordered qty).
    const createBody = JSON.parse((createCall[1] as RequestInit).body as string);
    expect(createBody.lines[0].qty).toBe(50);
    expect(createBody.packingType).toBe('BOX');
  });

  it('409 CONFLICT on seal → 409 passthrough (no silent auto-retry)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (init?.method === 'PATCH') {
        return Promise.resolve(wmsError('CONFLICT', 409));
      }
      if (String(url).includes('/packing-units') && init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({ packingUnitId: 'pu-1', version: 0, status: 'OPEN' }),
        );
      }
      return Promise.resolve(jsonResponse(ORDER_DETAIL));
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await packPOST(actionReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('CONFLICT');
    // Exactly one create + one (failed) seal — no silent retry of the seal.
    expect(
      fetchMock.mock.calls.filter((c) => (c[1] as RequestInit).method === 'PATCH'),
    ).toHaveLength(1);
  });
});

describe('POST /api/wms/outbound/{orderId}/ship', () => {
  it('reads the order version then confirms shipping with it', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (String(url).includes('/shipments') && init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({ shipmentId: 's-1', orderStatus: 'SHIPPED' }),
        );
      }
      return Promise.resolve(jsonResponse(ORDER_DETAIL));
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await shipPOST(actionReq({ idempotencyKey: 'idem-ship-1' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(200);

    const shipCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/shipments'),
    )!;
    const init = shipCall[1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBe('idem-ship-1');
    expect(h['X-Operator-Reason']).toBeUndefined();
    const body = JSON.parse(init.body as string);
    expect(body.version).toBe(3); // from the order detail
  });

  it('409 CONFLICT (stale version) → 409 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (String(url).includes('/shipments') && init?.method === 'POST') {
        return Promise.resolve(wmsError('CONFLICT', 409));
      }
      return Promise.resolve(jsonResponse(ORDER_DETAIL));
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await shipPOST(actionReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(409);
  });
});

describe('POST /api/wms/outbound/{orderId}:cancel (TASK-PC-FE-085)', () => {
  function cancelReq(body: unknown) {
    return new Request('http://console.local/api/wms/outbound/o-1/cancel', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    });
  }

  it('reads the order version then cancels with { reason, version } + Idempotency-Key (domain-facing token, no X-Operator-Reason)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (String(url).includes(':cancel') && init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({
            orderId: 'o-1',
            status: 'CANCELLED',
            sagaState: 'CANCELLATION_REQUESTED',
          }),
        );
      }
      return Promise.resolve(jsonResponse(ORDER_DETAIL));
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await cancelPOST(
      cancelReq({ reason: '고객 주문 취소 요청', idempotencyKey: 'idem-cancel-1' }),
      { params: Promise.resolve({ orderId: 'o-1' }) },
    );
    expect(res.status).toBe(200);

    const cancelCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes(':cancel'),
    )!;
    const init = cancelCall[1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(init.method).toBe('POST');
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['Idempotency-Key']).toBe('idem-cancel-1');
    expect(h['X-Operator-Reason']).toBeUndefined();
    const body = JSON.parse(init.body as string);
    expect(body.reason).toBe('고객 주문 취소 요청');
    expect(body.version).toBe(3); // from the order detail (optimistic lock)
  });

  it('a body without a reason → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(cancelReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a too-short reason (<3 chars) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(
      cancelReq({ reason: 'ab', idempotencyKey: 'k' }),
      { params: Promise.resolve({ orderId: 'o-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(
      cancelReq({ reason: '취소 사유입니다', idempotencyKey: 'k' }),
      { params: Promise.resolve({ orderId: 'o-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('422 ORDER_ALREADY_SHIPPED → 422 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) =>
      String(url).includes(':cancel') && init?.method === 'POST'
        ? Promise.resolve(wmsError('ORDER_ALREADY_SHIPPED', 422))
        : Promise.resolve(jsonResponse(ORDER_DETAIL)),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(
      cancelReq({ reason: '취소 사유', idempotencyKey: 'k' }),
      { params: Promise.resolve({ orderId: 'o-1' }) },
    );
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('ORDER_ALREADY_SHIPPED');
  });

  it('403 FORBIDDEN (post-pick needs OUTBOUND_ADMIN) → 403 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) =>
      String(url).includes(':cancel') && init?.method === 'POST'
        ? Promise.resolve(wmsError('FORBIDDEN', 403))
        : Promise.resolve(jsonResponse(ORDER_DETAIL)),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(
      cancelReq({ reason: '취소 사유', idempotencyKey: 'k' }),
      { params: Promise.resolve({ orderId: 'o-1' }) },
    );
    expect(res.status).toBe(403);
  });
});

describe('POST /api/wms/outbound/{orderId}/retry-tms (TASK-PC-FE-087)', () => {
  function retryReq(body: unknown) {
    return new Request('http://console.local/api/wms/outbound/o-1/retry-tms', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    });
  }
  const SHIPMENT_PAGE = {
    content: [{ shipmentId: 'shp-1' }],
    page: { number: 0, size: 1, totalElements: 1, totalPages: 1 },
  };

  it('resolves the shipmentId from the admin read-model THEN POSTs the retry (domain-facing token, Idempotency-Key, reason-free)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (String(url).includes(':retry-tms-notify') && init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({
            shipmentId: 'shp-1',
            tmsStatus: 'NOTIFIED',
            sagaState: 'COMPLETED',
          }),
        );
      }
      // admin read-model shipment lookup
      return Promise.resolve(jsonResponse(SHIPMENT_PAGE));
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await retryPOST(retryReq({ idempotencyKey: 'idem-retry-1' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(200);

    // (1) the admin lookup hits /api/v1/admin/dashboard/shipments?orderId=o-1
    const adminCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/dashboard/shipments'),
    )!;
    expect(adminCall).toBeDefined();
    const adminUrl = new URL(String(adminCall[0]));
    expect(adminUrl.pathname).toContain('/api/v1/admin/dashboard/shipments');
    expect(adminUrl.searchParams.get('orderId')).toBe('o-1');
    const adminH = (adminCall[1] as RequestInit).headers as Record<string, string>;
    expect(adminH.Authorization).toBe('Bearer GAP-ACCESS');
    expect(adminH['Idempotency-Key']).toBeUndefined(); // it's a read

    // (2) the retry hits the OUTBOUND base, shipment-keyed, with the key
    const retryCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes(':retry-tms-notify'),
    )!;
    expect(retryCall).toBeDefined();
    expect(String(retryCall[0])).toContain(
      '/api/v1/outbound/shipments/shp-1:retry-tms-notify',
    );
    const init = retryCall[1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(init.method).toBe('POST');
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['Idempotency-Key']).toBe('idem-retry-1');
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('no shipment resolves for the order → 404 SHIPMENT_NOT_FOUND (NO outbound retry POST)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ content: [] }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await retryPOST(retryReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('SHIPMENT_NOT_FOUND');
    expect(
      fetchMock.mock.calls.some((c) =>
        String(c[0]).includes(':retry-tms-notify'),
      ),
    ).toBe(false);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await retryPOST(retryReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a body without idempotencyKey → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await retryPOST(retryReq({}), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 FORBIDDEN (needs OUTBOUND_ADMIN) → 403 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((url: string, init?: RequestInit) =>
      String(url).includes(':retry-tms-notify') && init?.method === 'POST'
        ? Promise.resolve(wmsError('FORBIDDEN', 403))
        : Promise.resolve(jsonResponse(SHIPMENT_PAGE)),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await retryPOST(retryReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ orderId: 'o-1' }),
    });
    expect(res.status).toBe(403);
  });
});
