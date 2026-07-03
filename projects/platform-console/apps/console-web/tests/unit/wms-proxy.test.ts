import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin wms-ops proxy route handlers (TASK-PC-FE-007 — § 2.4.5):
 *   - read GET (inventory/alerts): IAM OIDC access token attached
 *     server-side (NOT the operator token); no mutation artifacts.
 *   - alert-ack POST: Idempotency-Key forwarded, EMPTY upstream body, NO
 *     X-Operator-Reason (reason-free wms surface).
 *   - 401 → 401 (whole-session re-login signal; no partial authed state).
 *   - 403 → 403 (role-insufficient inline, no crash).
 *   - 422 STATE_TRANSITION_INVALID / 409 DUPLICATE_REQUEST / 404 →
 *     passthrough (inline actionable).
 *   - 503 / timeout → 503 (wms section degrades only; shell intact).
 *   - bad ack body (no idempotencyKey) → 422 (no upstream call).
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

import { GET as inventoryGET } from '@/app/api/wms/inventory/route';
import { GET as inventoryByKeyGET } from '@/app/api/wms/inventory/by-key/route';
import { GET as shipmentsGET } from '@/app/api/wms/shipments/route';
import { GET as alertsGET } from '@/app/api/wms/alerts/route';
import { POST as ackPOST } from '@/app/api/wms/alerts/[alertId]/acknowledge/route';
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

const INV = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};
const ALERT = {
  alertId: 'al-1',
  alertType: 'LOW_STOCK',
  acknowledged: true,
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/wms/inventory proxy', () => {
  it('attaches the IAM OIDC access token (NOT the operator token), forwards filters', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(INV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await inventoryGET(
      new Request(
        'http://console.local/api/wms/inventory?warehouseId=wh-1&lowStockOnly=true&size=999',
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
    const upstream = new URL(String(url));
    expect(upstream.searchParams.get('warehouseId')).toBe('wh-1');
    expect(upstream.searchParams.get('lowStockOnly')).toBe('true');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it('401 from wms → 401 (whole-session re-login signal)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)));
    const res = await inventoryGET(
      new Request('http://console.local/api/wms/inventory'),
    );
    expect(res.status).toBe(401);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await inventoryGET(
      new Request('http://console.local/api/wms/inventory'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 from wms → 403 (role-insufficient inline)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)));
    const res = await inventoryGET(
      new Request('http://console.local/api/wms/inventory'),
    );
    expect(res.status).toBe(403);
  });

  it('503 from wms → 503 (wms section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await inventoryGET(
      new Request('http://console.local/api/wms/inventory'),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/wms/inventory/by-key proxy (TASK-PC-FE-173)', () => {
  const ITEM = { locationId: 'loc-1', skuId: 'sku-1', warehouseId: 'wh-1' };

  it('attaches the IAM OIDC access token (NOT the operator token), forwards the composite key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ITEM));
    vi.stubGlobal('fetch', fetchMock);

    const res = await inventoryByKeyGET(
      new Request(
        'http://console.local/api/wms/inventory/by-key?locationId=loc-1&skuId=sku-1&lotId=lot-1',
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
    const upstream = new URL(String(url));
    expect(upstream.pathname).toContain('/dashboard/inventory/by-key');
    expect(upstream.searchParams.get('locationId')).toBe('loc-1');
    expect(upstream.searchParams.get('skuId')).toBe('sku-1');
    expect(upstream.searchParams.get('lotId')).toBe('lot-1');
  });

  it('a missing locationId/skuId → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await inventoryByKeyGET(
      new Request('http://console.local/api/wms/inventory/by-key?locationId=loc-1'),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a 404 (zero stock at the key) → 404 passthrough (distinguished from a degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('NOT_FOUND', 404)),
    );
    const res = await inventoryByKeyGET(
      new Request(
        'http://console.local/api/wms/inventory/by-key?locationId=loc-1&skuId=sku-1',
      ),
    );
    expect(res.status).toBe(404);
  });

  it('401 from wms → 401 (whole-session re-login signal)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)));
    const res = await inventoryByKeyGET(
      new Request(
        'http://console.local/api/wms/inventory/by-key?locationId=loc-1&skuId=sku-1',
      ),
    );
    expect(res.status).toBe(401);
  });

  it('503 from wms → 503 (wms section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await inventoryByKeyGET(
      new Request(
        'http://console.local/api/wms/inventory/by-key?locationId=loc-1&skuId=sku-1',
      ),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/wms/shipments proxy (TASK-PC-FE-079)', () => {
  it('attaches the IAM OIDC access token (NOT the operator token), forwards warehouse + carrier filters, caps size, no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(INV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await shipmentsGET(
      new Request(
        'http://console.local/api/wms/shipments?warehouseId=wh-1&carrierCode=CJ-LOGISTICS&size=999',
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
    const upstream = new URL(String(url));
    expect(upstream.pathname).toContain('/dashboard/shipments');
    expect(upstream.searchParams.get('warehouseId')).toBe('wh-1');
    expect(upstream.searchParams.get('carrierCode')).toBe('CJ-LOGISTICS');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await shipmentsGET(
      new Request('http://console.local/api/wms/shipments'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 from wms → 503 (wms section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await shipmentsGET(
      new Request('http://console.local/api/wms/shipments'),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/wms/alerts proxy', () => {
  it('forwards alert filters with the IAM OIDC token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await alertsGET(
      new Request(
        'http://console.local/api/wms/alerts?acknowledged=false&alertType=LOW_STOCK',
      ),
    );
    expect(res.status).toBe(200);
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('acknowledged')).toBe('false');
    expect(upstream.searchParams.get('alertType')).toBe('LOW_STOCK');
  });
});

describe('POST /api/wms/alerts/{id}/acknowledge proxy', () => {
  function ackReq(body: unknown) {
    return new Request(
      'http://console.local/api/wms/alerts/al-1/acknowledge',
      {
        method: 'POST',
        body: JSON.stringify(body),
        headers: { 'Content-Type': 'application/json' },
      },
    );
  }

  it('forwards Idempotency-Key, EMPTY upstream body, NO X-Operator-Reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ALERT));
    vi.stubGlobal('fetch', fetchMock);

    const res = await ackPOST(ackReq({ idempotencyKey: 'idem-1' }), {
      params: Promise.resolve({ alertId: 'al-1' }),
    });
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['Idempotency-Key']).toBe('idem-1');
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).body).toBeUndefined();
    expect(String(url)).toContain('/dashboard/alerts/al-1/acknowledge');
  });

  it('a body without idempotencyKey → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await ackPOST(ackReq({}), {
      params: Promise.resolve({ alertId: 'al-1' }),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a reason field in the body is NOT forwarded as a header (drift guard)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ALERT));
    vi.stubGlobal('fetch', fetchMock);
    // Even if a (wrong) client sent a reason, the proxy schema strips it
    // and the api client never sets X-Operator-Reason for wms.
    const res = await ackPOST(
      ackReq({ idempotencyKey: 'idem-1', reason: 'should be ignored' }),
      { params: Promise.resolve({ alertId: 'al-1' }) },
    );
    expect(res.status).toBe(200);
    const h = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('422 STATE_TRANSITION_INVALID (already acknowledged) → 422 inline', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('STATE_TRANSITION_INVALID', 422)),
    );
    const res = await ackPOST(ackReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ alertId: 'al-1' }),
    });
    expect(res.status).toBe(422);
    const b = await res.json();
    expect(b.code).toBe('STATE_TRANSITION_INVALID');
  });

  it('409 DUPLICATE_REQUEST → 409 inline', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('DUPLICATE_REQUEST', 409)),
    );
    const res = await ackPOST(ackReq({ idempotencyKey: 'k' }), {
      params: Promise.resolve({ alertId: 'al-1' }),
    });
    expect(res.status).toBe(409);
  });
});
