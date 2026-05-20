import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/domain-health/api/domain-health-api.ts` —
 * TASK-PC-FE-013 client + server-side fetch helpers for the BFF-routed
 * Phase 7 "Domain Health Overview" composition route (§ 2.4.9.2).
 *
 * Asserts (mirrors the BE Javadoc on `DomainHealthController` +
 * `DomainHealthCompositionUseCase`):
 *   - the same-origin Next.js proxy URL is used (browser never reaches
 *     console-bff directly; tokens stay HttpOnly on the server side);
 *   - GET only, no body, no `Idempotency-Key`, no `X-Operator-Reason`
 *     (READ-ONLY § 2.4.9 hard invariant);
 *   - parses the 5-card envelope per `DomainHealthSchema`;
 *   - schema rejects a `'forbidden'` literal — § 2.4.9.2 invariant:
 *     `forbidden` is NEVER emitted on this route;
 *   - schema rejects an invalid `data.status` enum (the producer's
 *     Spring Boot health enum is fixed: UP/DOWN/OUT_OF_SERVICE/UNKNOWN);
 *   - non-2xx → ApiError(status, code);
 *   - `getDomainHealthState` maps the 3 fail modes (no tenant /
 *     unauthorized / bff unavailable) to the discriminated state the
 *     SSR page consumes.
 */

import {
  DomainHealthSchema,
  fetchDomainHealth,
  getDomainHealthState,
} from '@/features/domain-health';
import { ApiError } from '@/shared/api/errors';

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
    { domain: 'gap', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
    { domain: 'finance', status: 'ok', data: { status: 'OUT_OF_SERVICE' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
  ],
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('DomainHealthSchema — runtime validation', () => {
  it('accepts the canonical 5-card envelope (mixed ok + degraded)', () => {
    const parsed = DomainHealthSchema.safeParse(HAPPY_ENVELOPE);
    expect(parsed.success).toBe(true);
  });

  it('accepts all 4 Spring Boot health enum values for ok cards', () => {
    const envelope = {
      asOf: '2026-05-21T01:30:00Z',
      cards: [
        { domain: 'gap', status: 'ok', data: { status: 'UP' } },
        { domain: 'wms', status: 'ok', data: { status: 'DOWN' } },
        { domain: 'scm', status: 'ok', data: { status: 'OUT_OF_SERVICE' } },
        { domain: 'finance', status: 'ok', data: { status: 'UNKNOWN' } },
        { domain: 'erp', status: 'ok', data: { status: 'UP' } },
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
        c.domain === 'gap' ? { domain: 'gap', status: 'ok', data: { status: 'HEALTHY' } } : c,
      ),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects a 4-card envelope (missing wms)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'wms'),
    };
    expect(DomainHealthSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects a 5-card envelope with a duplicate domain (no erp)', () => {
    const bad = {
      ...HAPPY_ENVELOPE,
      cards: [
        ...HAPPY_ENVELOPE.cards.filter((c) => c.domain !== 'erp'),
        { domain: 'gap', status: 'ok', data: { status: 'UP' } },
      ],
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
    expect(String(url)).toBe(
      'http://console.local/api/console/dashboards/domain-health',
    );
    const opts = init as RequestInit;
    expect(opts.method).toBe('GET');
    expect(opts.body).toBeUndefined();
    expect(opts.credentials).toBe('include');
    const headers = opts.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();

    expect(health.asOf).toBe('2026-05-21T01:30:00Z');
    expect(health.cards).toHaveLength(5);
    expect(health.cards.map((c) => c.domain)).toEqual([
      'gap',
      'wms',
      'scm',
      'finance',
      'erp',
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
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(HAPPY_ENVELOPE)));
    const state = await getDomainHealthState();
    expect(state.health).not.toBeNull();
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
    const state = await getDomainHealthState();
    expect(state.health).toBeNull();
    expect(state.noTenant).toBe(true);
  });

  it('401 → { unauthorized: true }', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'TOKEN_INVALID', message: 'x' }, 401),
      ),
    );
    const state = await getDomainHealthState();
    expect(state.unauthorized).toBe(true);
  });

  it('502 BAD_GATEWAY → { bffUnavailable: true }', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'BAD_GATEWAY', message: 'x' }, 502),
      ),
    );
    const state = await getDomainHealthState();
    expect(state.bffUnavailable).toBe(true);
  });

  it('network failure (fetch throws) → { bffUnavailable: true }', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('network down')),
    );
    const state = await getDomainHealthState();
    expect(state.bffUnavailable).toBe(true);
  });
});
