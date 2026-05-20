import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin Next.js server proxy for the Phase 7 "Domain Health
 * Overview" BFF route (TASK-PC-FE-013 — § 2.4.9.2).
 *
 * Asserts the security-critical header invariants:
 *   - forwards `Authorization: Bearer <gap-access-token>` from the
 *     HttpOnly access cookie (server-side only — never readable by
 *     client JS).
 *   - forwards `X-Tenant-Id: <active-tenant>` for log MDC / audit
 *     traceability.
 *   - **DOES NOT forward `X-Operator-Token`** — intentional divergence
 *     from § 2.4.9.1 (the BFF does not consume it; sending it would be
 *     misleading per § 2.4.9.2 invariant).
 *   - GET only, no body, no `Idempotency-Key`, no `X-Operator-Reason`
 *     (READ-ONLY § 2.4.9 hard invariant).
 *   - passes BFF 200 response shape through verbatim (per-card degrade
 *     lives INSIDE the 200 payload — the proxy never re-classifies).
 *   - 400 NO_ACTIVE_TENANT, 401 TOKEN_INVALID, 502 BAD_GATEWAY mapping.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

import { GET as domainHealthGET } from '@/app/api/console/dashboards/domain-health/route';
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

const HAPPY_ENVELOPE = {
  asOf: '2026-05-21T01:30:00Z',
  cards: [
    { domain: 'gap', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
    { domain: 'finance', status: 'ok', data: { status: 'OUT_OF_SERVICE' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
  ],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/console/dashboards/domain-health proxy — header forwarding', () => {
  it('forwards Authorization + X-Tenant-Id to console-bff (HttpOnly→server-only path)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS-TOKEN-xyz');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-not-used');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const res = await domainHealthGET();
    expect(res.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-ACCESS-TOKEN-xyz');
    expect(headers['X-Tenant-Id']).toBe('wms');
  });

  it('DOES NOT forward X-Operator-Token (BFF does not consume it on this route — § 2.4.9.2)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-leak');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await domainHealthGET();

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['X-Operator-Token']).toBeUndefined();
    // Defensive — the operator token must NOT appear anywhere in the
    // outgoing header values (no accidental Authorization mis-wiring).
    for (const value of Object.values(headers)) {
      expect(value).not.toContain('OPERATOR-TOKEN-must-not-leak');
    }
  });

  it('READ-ONLY: GET only, no body, no Idempotency-Key, no X-Operator-Reason', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'wms');

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await domainHealthGET();

    const [, init] = fetchMock.mock.calls[0];
    const opts = init as RequestInit;
    expect(opts.method).toBe('GET');
    expect(opts.body).toBeUndefined();
    const headers = opts.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
  });
});

describe('GET /api/console/dashboards/domain-health proxy — response shape passthrough', () => {
  it('BFF 200 → proxy 200 with the envelope passed through verbatim', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'wms');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE)));
    const res = await domainHealthGET();
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.asOf).toBe('2026-05-21T01:30:00Z');
    expect(body.cards).toHaveLength(5);
    // per-card degrade lives inside the 200 payload (status="degraded"
    // on scm card) — the proxy did NOT re-classify into 5xx.
    expect(body.cards[2].status).toBe('degraded');
    expect(body.cards[0].status).toBe('ok');
    expect(body.cards[0].data.status).toBe('UP');
  });

  it('BFF 400 NO_ACTIVE_TENANT → proxy 400 NO_ACTIVE_TENANT', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'wms');

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'NO_ACTIVE_TENANT', message: 'x' }, 400),
      ),
    );
    const res = await domainHealthGET();
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('NO_ACTIVE_TENANT');
  });

  it('BFF 401 → proxy 401 (forced re-login on client refresh-fail)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'wms');

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'TOKEN_INVALID', message: 'x' }, 401),
      ),
    );
    const res = await domainHealthGET();
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.code).toBe('TOKEN_INVALID');
  });

  it('BFF non-2xx other → proxy 502 BAD_GATEWAY (BFF never emits 503; reaching here = transport/parse)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'wms');

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'INTERNAL' }, 500)),
    );
    const res = await domainHealthGET();
    expect(res.status).toBe(502);
    const body = await res.json();
    expect(body.code).toBe('BAD_GATEWAY');
  });

  it('fetch throws (network down) → 502 BAD_GATEWAY', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(TENANT_COOKIE, 'wms');

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')));
    const res = await domainHealthGET();
    expect(res.status).toBe(502);
  });
});

describe('GET /api/console/dashboards/domain-health proxy — pre-emptive fail-closed', () => {
  it('no active tenant → 400 NO_ACTIVE_TENANT (NO outbound fetch)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // intentionally no TENANT_COOKIE
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await domainHealthGET();
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no inbound access token → 401 TOKEN_INVALID (NO outbound fetch)', async () => {
    // tenant present but access token absent
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await domainHealthGET();
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.code).toBe('TOKEN_INVALID');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
