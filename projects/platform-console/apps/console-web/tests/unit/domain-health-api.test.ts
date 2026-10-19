import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/domain-health/api/domain-health-api.ts` —
 * TASK-PC-FE-013 client + server-side fetch helpers for the BFF-routed
 * Phase 7 "Domain Health Overview" composition route (§ 2.4.9.2).
 *
 * Asserts (mirrors the BE Javadoc on `DomainHealthController` +
 * `DomainHealthCompositionUseCase`):
 *   - the same-origin Next.js proxy URL is used (browser never reaches
 *     a domain directly; tokens stay HttpOnly on the server side);
 *   - GET only, no body, no `Idempotency-Key`, no `X-Operator-Reason`
 *     (READ-ONLY § 2.4.9 hard invariant);
 *   - parses the 6-card envelope per `DomainHealthSchema` (ecommerce 6th
 *     card added by TASK-MONO-241);
 *   - schema rejects a `'forbidden'` literal — § 2.4.9.2 invariant:
 *     `forbidden` is NEVER emitted on this route;
 *   - schema rejects an invalid `data.status` enum (the producer's
 *     Spring Boot health enum is fixed: UP/DOWN/OUT_OF_SERVICE/UNKNOWN);
 *   - non-2xx → ApiError(status, code);
 *   - `getDomainHealthState` maps the 3 fail modes (no tenant /
 *     unauthorized / bff unavailable) of the in-process composition answer
 *     to the discriminated state the SSR page consumes (TASK-PC-FE-350).
 */

// TASK-PC-FE-350 — `getDomainHealthState()` no longer fetches the route at this
// app's own origin; it calls `composeDomainHealth()` in-process (the function the
// route itself runs). THAT is the seam the state tests below mock: each case
// hands the state function the answer the route would have put on the wire —
// the same inputs these cases used to feed through a stubbed self-fetch. The
// composition itself (session reads via `next/headers`, sample gate, legs) runs
// un-mocked in `tests/unit/dashboard-ssr-direct-composition.test.ts`.
// (Before 350: the TASK-PC-FE-037 `next/headers` mock + forwarded Cookie header.)
vi.mock('@/shared/composition/domain-health-composition', () => ({
  composeDomainHealth: vi.fn(),
}));

import {
  DomainHealthSchema,
  fetchDomainHealth,
  getDomainHealthState,
} from '@/features/domain-health';
import { ApiError } from '@/shared/api/errors';
import { composeDomainHealth } from '@/shared/composition/domain-health-composition';

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
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const HAPPY_ENVELOPE = {
  asOf: '2026-05-21T01:30:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
    { domain: 'finance', status: 'ok', data: { status: 'OUT_OF_SERVICE' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
    { domain: 'ecommerce', status: 'ok', data: { status: 'UP' } },
  ],
};

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.mocked(composeDomainHealth).mockReset();
});

/** The answer the route would put on the wire — what the old self-fetch returned. */
function answer(body: unknown, status = 200) {
  vi.mocked(composeDomainHealth).mockResolvedValue({ status, body });
}

describe('DomainHealthSchema — runtime validation', () => {
  it('accepts the canonical 6-card envelope (mixed ok + degraded)', () => {
    const parsed = DomainHealthSchema.safeParse(HAPPY_ENVELOPE);
    expect(parsed.success).toBe(true);
  });

  it('accepts all 4 Spring Boot health enum values for ok cards', () => {
    const envelope = {
      asOf: '2026-05-21T01:30:00Z',
      cards: [
        { domain: 'iam', status: 'ok', data: { status: 'UP' } },
        { domain: 'wms', status: 'ok', data: { status: 'DOWN' } },
        { domain: 'scm', status: 'ok', data: { status: 'OUT_OF_SERVICE' } },
        { domain: 'finance', status: 'ok', data: { status: 'UNKNOWN' } },
        { domain: 'erp', status: 'ok', data: { status: 'UP' } },
        { domain: 'ecommerce', status: 'ok', data: { status: 'UP' } },
      ],
    };
    expect(DomainHealthSchema.safeParse(envelope).success).toBe(true);
  });

  it('REJECTS a card.status = "forbidden" literal (NEVER emitted on this route — § 2.4.9.2 invariant)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c) =>
        c.domain === 'finance'
          ? { domain: 'finance', status: 'forbidden', reason: 'MISSING_PREREQUISITE' }
          : c,
      ),
    };
    const parsed = DomainHealthSchema.safeParse(bad);
    expect(parsed.success).toBe(false);
  });

  it('REJECTS an invalid data.status enum (not in UP/DOWN/OUT_OF_SERVICE/UNKNOWN)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c) =>
        c.domain === 'iam' ? { domain: 'iam', status: 'ok', data: { status: 'HEALTHY' } } : c,
      ),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects a 5-card envelope (missing wms — must be exactly 6)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'wms'),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects a 6-card envelope with a duplicate domain (no erp, iam twice)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: [
        ...HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'erp'),
        { domain: 'iam', status: 'ok', data: { status: 'UP' } },
      ],
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects an unknown domain key (e.g. "payments")', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c) =>
        c.domain === 'ecommerce'
          ? { domain: 'payments', status: 'ok', data: { status: 'UP' } }
          : c,
      ),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects an unknown card status (e.g. "pending")', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c, i) =>
        i === 0 ? { ...c, status: 'pending' } : c,
      ),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects an unknown degraded reason', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c) =>
        c.domain === 'scm' ? { ...c, reason: 'WHO_KNOWS' } : c,
      ),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });
});

describe('fetchDomainHealth — happy path', () => {
  it('GETs the same-origin proxy URL with no mutation artifacts and parses the envelope', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const health = await fetchDomainHealth();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    // TASK-MONO-358 — in the browser this is a RELATIVE same-origin path, not
    // an absolute one. It used to be built from `NEXT_PUBLIC_APP_URL`, which
    // Next inlines at BUILD time: a prebuilt image would send the browser to
    // whatever host the build knew about (`console.local`) rather than the host
    // it is actually being served from. The on-demand demo, whose hostname is
    // derived from the instance IP at boot, made that fatal. Same-origin needs
    // no base at all — this suite runs under jsdom, so it exercises the browser
    // branch. (The SSR branch still resolves an absolute origin; fetch() there
    // requires one.)
    expect(String(url)).toBe('/api/console/dashboards/domain-health');
    const opts = init as RequestInit;
    expect(opts.method).toBe('GET');
    expect(opts.body).toBeUndefined();
    expect(opts.credentials).toBe('include');
    const headers = opts.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();

    expect(health.asOf).toBe('2026-05-21T01:30:00Z');
    expect(health.cards).toHaveLength(6);
    expect(health.cards.map((c) => c.domain)).toEqual([
      'iam',
      'wms',
      'scm',
      'finance',
      'erp',
      'ecommerce',
    ]);
  });
});

describe('fetchDomainHealth — error mapping', () => {
  it('maps 401 to ApiError(401, TOKEN_INVALID)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'TOKEN_INVALID', message: 'expired' }, 401),
      ),
    );
    const err = await fetchDomainHealth().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect((err as ApiError).code).toBe('TOKEN_INVALID');
  });

  it('maps 400 NO_ACTIVE_TENANT to ApiError(400, NO_ACTIVE_TENANT)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'NO_ACTIVE_TENANT', message: 'no tenant' }, 400),
      ),
    );
    const err = await fetchDomainHealth().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(400);
    expect((err as ApiError).code).toBe('NO_ACTIVE_TENANT');
  });

  it('maps 502 BAD_GATEWAY (BFF unavailable) to ApiError(502, …)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'BAD_GATEWAY', message: 'down' }, 502),
      ),
    );
    const err = await fetchDomainHealth().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(502);
  });
});

describe('getDomainHealthState — discriminated server-side state', () => {
  it('200 → { health }', async () => {
    answer(HAPPY_ENVELOPE);
    const state = await getDomainHealthState();
    expect(state.health).not.toBeNull();
    expect(state.noTenant).toBe(false);
    expect(state.unauthorized).toBe(false);
    expect(state.bffUnavailable).toBe(false);
  });

  it('400 NO_ACTIVE_TENANT → { noTenant: true }', async () => {
    answer({ code: 'NO_ACTIVE_TENANT', message: 'x' }, 400);
    const state = await getDomainHealthState();
    expect(state.health).toBeNull();
    expect(state.noTenant).toBe(true);
  });

  // TASK-PC-FE-037 regression, re-stated for TASK-PC-FE-350. The bug 037 closed:
  // the SSR call carried no session, so the route read an empty cookie jar and
  // answered NO_ACTIVE_TENANT on every load. 037 fixed it by forwarding the
  // Cookie header over the self-fetch; 350 removes the hop, so the session is
  // read by the composition from THIS request's `cookies()`. What must stay true:
  // the SSR path runs the route's own composition (not a copy that could read the
  // session differently) and does not go over HTTP. The «real cookie jar → not
  // noTenant» half is asserted un-mocked in dashboard-ssr-direct-composition.test.ts.
  it('runs the SAME composition the route runs, in-process — no fetch, no forwarded Cookie header (TASK-PC-FE-037 → TASK-PC-FE-350)', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    answer(HAPPY_ENVELOPE);

    await getDomainHealthState();

    expect(composeDomainHealth).toHaveBeenCalledTimes(1);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('401 → { unauthorized: true }', async () => {
    answer({ code: 'TOKEN_INVALID', message: 'x' }, 401);
    const state = await getDomainHealthState();
    expect(state.unauthorized).toBe(true);
  });

  it('502 BAD_GATEWAY → { bffUnavailable: true }', async () => {
    answer({ code: 'BAD_GATEWAY', message: 'x' }, 502);
    const state = await getDomainHealthState();
    expect(state.bffUnavailable).toBe(true);
  });

  it('the composition throws → { bffUnavailable: true }', async () => {
    vi.mocked(composeDomainHealth).mockRejectedValue(new TypeError('boom'));
    const state = await getDomainHealthState();
    expect(state.bffUnavailable).toBe(true);
  });

  // 🔴🔴 TASK-MONO-711 ② — 이 파일에도 위 두 칸이 **같은 단언**을 하고 있었다
  //    (502 와 «닿지도 못했다» 가 구별되지 않는다). 개요 쪽만 고치면 이 사본이
  //    혼자 뒤처진다 — 이 저장소의 «한 사실이 두 집을 갖는다» 축이다. 그래서 같은
  //    칸을 여기에도 둔다. 🔵 분류기는 두 기능이 **한 모듈**(«shared/api/unavailable-cause»)
  //    을 공유하므로, 이 칸은 그 배선이 이쪽에도 걸려 있는지를 문다.
  //    🔵 TASK-PC-FE-350: 프로세스 안 예외는 'transport' 가 아니라 'unknown' 이다
  //    (개요 쪽과 같은 `classifyInProcessFailure`).
  it('🔴 서로 다른 실패는 서로 다른 사유를 낸다 — 개요 쪽과 같은 분류기를 쓴다', async () => {
    answer({ code: 'BAD_GATEWAY', message: 'x' }, 502);
    const bad = await getDomainHealthState();

    vi.mocked(composeDomainHealth).mockRejectedValue(new TypeError('boom'));
    const broken = await getDomainHealthState();

    expect(bad.bffUnavailable).toBe(true);
    expect(broken.bffUnavailable).toBe(true);
    expect(bad.unavailableCause).toEqual({
      kind: 'status',
      status: 502,
      code: 'BAD_GATEWAY',
    });
    expect(broken.unavailableCause).toEqual({ kind: 'unknown', name: 'TypeError' });
    expect(bad.unavailableCause?.kind).not.toBe(broken.unavailableCause?.kind);
  });
});
