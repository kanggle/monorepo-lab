import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operator-overview/api/operator-overview-api.ts` —
 * TASK-PC-FE-011 client + server-side fetch helpers for the BFF-routed
 * MVP "Operator Overview" composition route (§ 2.4.9.1).
 *
 * Asserts (mirrors the BE Javadoc on `OperatorOverviewController` +
 * `OperatorOverviewCompositionUseCase`):
 *   - the same-origin Next.js proxy URL is used (browser never reaches
 *     console-bff directly; tokens stay HttpOnly on the server side);
 *   - GET only, no body, no `Idempotency-Key`, no `X-Operator-Reason`
 *     (READ-ONLY § 2.4.9 hard invariant);
 *   - parses the 6-card envelope per `OperatorOverviewSchema`;
 *   - schema validation rejects a malformed envelope (wrong card
 *     count / missing domain / unknown status) — defensive against
 *     a BE drift;
 *   - non-2xx → ApiError(status, code);
 *   - `getOperatorOverviewState` maps the 3 fail modes (no tenant /
 *     unauthorized / bff unavailable) to the discriminated state the
 *     SSR page consumes.
 */

// TASK-PC-FE-030 — `getOperatorOverviewState()` lazy-imports `next/headers`
// `cookies()` and forwards `(await cookies()).toString()` as the Cookie
// header to the in-process proxy fetch. Outside a request scope the real
// `cookies()` throws, so it must be mocked here; the `toString()` shape is
// what the wrapper reads (the discriminated-state assertions exercise the
// proxy response, not the cookie contents).
vi.mock('next/headers', () => ({
  cookies: async () => ({
    toString: () => 'gap_at=a; gap_op=op; gap_tenant=wms',
    get: () => undefined,
  }),
}));

import {
  OperatorOverviewSchema,
  fetchOperatorOverview,
  getOperatorOverviewState,
} from '@/features/operator-overview';
import { ApiError } from '@/shared/api/errors';

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
  asOf: '2026-05-20T10:30:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { totalElements: 12345 } },
    {
      domain: 'wms',
      status: 'ok',
      data: { inventorySnapshot: { totalStockUnits: 99000, alertCount: 3 } },
    },
    { domain: 'scm', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
    {
      domain: 'finance',
      status: 'forbidden',
      reason: 'MISSING_PREREQUISITE',
    },
    {
      domain: 'erp',
      status: 'ok',
      data: { meta: { totalElements: 87 } },
    },
    {
      domain: 'ecommerce',
      status: 'ok',
      data: { totalElements: 42 },
    },
  ],
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('OperatorOverviewSchema — runtime validation', () => {
  it('accepts the canonical 6-card envelope (mixed statuses)', () => {
    const parsed = OperatorOverviewSchema.safeParse(HAPPY_ENVELOPE);
    expect(parsed.success).toBe(true);
  });

  it('accepts an ecommerce card with totalElements: 0 (empty catalog)', () => {
    const env = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c) =>
        c.domain === 'ecommerce' ? { ...c, data: { totalElements: 0 } } : c,
      ),
    };
    expect(OperatorOverviewSchema.safeParse(env).success).toBe(true);
  });

  it('rejects a 5-card envelope (missing ecommerce)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'ecommerce'),
    };
    expect(OperatorOverviewSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects a 4-card envelope (missing wms)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'wms'),
    };
    expect(OperatorOverviewSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects a 6-card envelope with a duplicate domain (no erp)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: [
        ...HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'erp'),
        { domain: 'iam', status: 'ok', data: {} },
      ],
    };
    expect(OperatorOverviewSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects an unknown card status', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c, i) =>
        i === 0 ? { ...c, status: 'pending' } : c,
      ),
    };
    expect(OperatorOverviewSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects an unknown degraded reason', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.map((c) =>
        c.domain === 'scm' ? { ...c, reason: 'WHO_KNOWS' } : c,
      ),
    };
    expect(OperatorOverviewSchema.safeParse(bad).success).toBe(false);
  });
});

describe('fetchOperatorOverview — happy path', () => {
  it('GETs the same-origin proxy URL with no mutation artifacts and parses the envelope', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const overview = await fetchOperatorOverview();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    // Same-origin via NEXT_PUBLIC_APP_URL.
    expect(String(url)).toBe(
      // TASK-MONO-358 — relative in the browser. The absolute base used to come
      // from `NEXT_PUBLIC_APP_URL`, which Next inlines at BUILD time, pinning a
      // prebuilt image to the build host instead of the serving host. A
      // same-origin call needs no base.
      '/api/console/dashboards/operator-overview',
    );
    const opts = init as RequestInit;
    expect(opts.method).toBe('GET');
    expect(opts.body).toBeUndefined();
    // Browser-side credentials posture (HttpOnly cookies ride).
    expect(opts.credentials).toBe('include');
    // READ-ONLY: no mutation header anywhere.
    const headers = opts.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();

    // Envelope shape pass-through.
    expect(overview.asOf).toBe('2026-05-20T10:30:00Z');
    expect(overview.cards).toHaveLength(6);
    expect(overview.cards.map((c) => c.domain)).toEqual([
      'iam',
      'wms',
      'scm',
      'finance',
      'erp',
      'ecommerce',
    ]);
  });
});

describe('fetchOperatorOverview — error mapping', () => {
  it('maps 401 to ApiError(401, TOKEN_INVALID)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'TOKEN_INVALID', message: 'expired' }, 401),
      ),
    );
    const err = await fetchOperatorOverview().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect((err as ApiError).code).toBe('TOKEN_INVALID');
  });

  it('maps 400 NO_ACTIVE_TENANT to ApiError(400, NO_ACTIVE_TENANT)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { code: 'NO_ACTIVE_TENANT', message: 'no tenant' },
          400,
        ),
      ),
    );
    const err = await fetchOperatorOverview().catch((e) => e);
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
    const err = await fetchOperatorOverview().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(502);
  });
});

describe('getOperatorOverviewState — discriminated server-side state', () => {
  it('200 → { overview }', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE)));
    const state = await getOperatorOverviewState();
    expect(state.overview).not.toBeNull();
    expect(state.noTenant).toBe(false);
    expect(state.unauthorized).toBe(false);
    expect(state.bffUnavailable).toBe(false);
  });

  it('400 NO_ACTIVE_TENANT → { noTenant: true }', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'NO_ACTIVE_TENANT', message: 'x' }, 400),
      ),
    );
    const state = await getOperatorOverviewState();
    expect(state.overview).toBeNull();
    expect(state.noTenant).toBe(true);
  });

  it('401 → { unauthorized: true }', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'TOKEN_INVALID', message: 'x' }, 401),
      ),
    );
    const state = await getOperatorOverviewState();
    expect(state.unauthorized).toBe(true);
  });

  it('502 BAD_GATEWAY → { bffUnavailable: true } + 사유가 status:502 다 (TASK-MONO-711 ②)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'BAD_GATEWAY', message: 'x' }, 502),
      ),
    );
    const state = await getOperatorOverviewState();
    expect(state.bffUnavailable).toBe(true);
    expect(state.unavailableCause).toEqual({
      kind: 'status',
      status: 502,
      code: 'BAD_GATEWAY',
    });
  });

  it('network failure (fetch throws) → { bffUnavailable: true } + 사유가 transport 다 (TASK-MONO-711 ②)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('network down')),
    );
    const state = await getOperatorOverviewState();
    expect(state.bffUnavailable).toBe(true);
    // 🔴 «응답이 나빴다» 가 아니라 «응답에 닿지도 못했다» — Vercel 콘솔이 상시 머무는 상태다.
    expect(state.unavailableCause?.kind).toBe('transport');
  });

  // 🔴🔴 TASK-MONO-711 ② 의 본체. 위 두 칸은 각각 자기 사유를 단언하지만, 둘이
  //    **서로 다른가** 는 아무도 안 묻는다 — 그리고 그것이 이 티켓의 질문이다.
  //    «기록된 영구 한계» 와 «진짜 장애» 가 같은 값으로 나오면 신호가 죽는다.
  //    이 칸은 누가 사유를 다시 하나로 뭉개면(예: 전부 'unknown') 빨개진다.
  it('🔴 서로 다른 실패는 서로 다른 사유를 낸다 — 하나로 뭉개지지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'BAD_GATEWAY', message: 'x' }, 502)),
    );
    const bad = await getOperatorOverviewState();

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network down')));
    const down = await getOperatorOverviewState();

    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(Object.assign(new Error('aborted'), { name: 'AbortError' })),
    );
    const aborted = await getOperatorOverviewState();

    // 셋 다 같은 플래그다 — 그 플래그만으로는 못 가른다는 것이 이 티켓의 전제다.
    expect([bad, down, aborted].map((s) => s.bffUnavailable)).toEqual([true, true, true]);

    const kinds = [bad, down, aborted].map((s) => s.unavailableCause?.kind);
    expect(kinds).toEqual(['status', 'transport', 'timeout']);
    expect(new Set(kinds).size).toBe(3);
  });

  it('🔵 대조군 — 성공하면 사유를 지어내지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE)));
    const state = await getOperatorOverviewState();
    expect(state.bffUnavailable).toBe(false);
    expect(state.unavailableCause).toBeUndefined();
  });
});
