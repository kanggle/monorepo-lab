import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/scm-replenishment/api/demand-planning-api.ts` — the
 * security-critical core of TASK-PC-FE-077 (the FIRST scm operator-MUTATION
 * surface; the ADR-MONO-027 replenishment loop's console operator gate).
 *
 * THE CENTRAL ASSERTIONS (console-integration-contract § 2.4.6.1 — REUSE of
 * the § 2.4.5/§ 2.4.6 per-domain credential rule, NOT re-derived):
 *   - every demand-planning call's bearer is the **domain-facing IAM OIDC
 *     ACCESS token**, NEVER the exchanged operator token (operator-token path
 *     ABSENT for scm — pinned);
 *   - the console sends NO `X-Tenant-Id` (scm resolves tenant from the JWT
 *     `tenant_id ∈ {scm,*}` claim producer-side);
 *   - reads (list/detail) carry NO mutation artifacts;
 *   - approve/dismiss POST with an OPTIONAL note/reason in the BODY +
 *     NO `Idempotency-Key` + NO `X-Operator-Reason` (asserted absent — the
 *     producer defines NEITHER header; idempotency is server-side by state);
 *   - approve issues NO procurement submit/confirm/cancel call (DRAFT-only);
 *   - the scm FLAT error envelope `{ code, message, timestamp }` is parsed
 *     (NOT wms's NESTED `{ error: { code } }`);
 *   - 401 → ApiError(401) (whole-session re-login); 403 → ApiError(403) inline;
 *     404/422/409 → ApiError inline; 429 → ScmRateLimitedError (ONE bounded
 *     backoff, NO storm); 503/timeout → ScmReplenishmentUnavailableError.
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
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
// demand-planning client uses (the central per-domain-credential assertion).
import * as sessionModule from '@/shared/lib/session';

import {
  listSuggestions,
  getSuggestion,
  approveSuggestion,
  dismissSuggestion,
} from '@/features/scm-replenishment/api/demand-planning-api';
import {
  ApiError,
  ScmReplenishmentUnavailableError,
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
    JSON.stringify({ code, message, timestamp: '2026-06-11T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const SUGGESTIONS_ENVELOPE = {
  data: [
    {
      id: '0192-abc',
      skuCode: 'SKU-APPLE-001',
      warehouseId: 'wh-1',
      supplierId: 'sup-1',
      suggestedQty: 100,
      status: 'SUGGESTED',
      source: 'ALERT',
      triggerAvailableQty: 5,
      materializedPoId: null,
      createdAt: '2026-06-11T10:05:00Z',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
};

const APPROVE_ENVELOPE = {
  data: {
    id: '0192-abc',
    status: 'MATERIALIZED',
    poId: 'po-uuid-1',
    poStatus: 'DRAFT',
  },
  meta: {},
};

const DISMISS_ENVELOPE = {
  data: { id: '0192-abc', status: 'DISMISSED' },
  meta: {},
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('demand-planning-api — per-domain credential (REUSE of § 2.4.5/§ 2.4.6; the INVERSE of #569)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer on a READ (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-scm');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(SUGGESTIONS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuggestions({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-scm',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain(
      'http://scm.local/api/v1/demand-planning/suggestions',
    );
  });

  it('sends the IAM OIDC ACCESS bearer on an ACTION too (approve) — same credential for reads and actions', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(APPROVE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await approveSuggestion('0192-abc');

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() for scm (read AND action; pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(SUGGESTIONS_ENVELOPE)),
    );

    await listSuggestions();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(DISMISS_ENVELOPE)));
    await dismissSuggestion('0192-abc');

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for scm on BOTH read and action.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listSuggestions().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (scm resolves tenant from the JWT claim)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(SUGGESTIONS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listSuggestions();

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('demand-planning-api — read vs mutation discipline (§ 2.4.6.1)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('reads (list + detail) are pure GET with NO mutation artifacts', async () => {
    const calls: RequestInit[] = [];
    const fetchMock = vi.fn((u: string, init?: RequestInit) => {
      calls.push(init as RequestInit);
      const us = String(u);
      if (/\/suggestions\/[^?]/.test(us))
        return Promise.resolve(
          jsonResponse({ data: SUGGESTIONS_ENVELOPE.data[0], meta: {} }),
        );
      return Promise.resolve(jsonResponse(SUGGESTIONS_ENVELOPE));
    });
    vi.stubGlobal('fetch', fetchMock);

    await listSuggestions({ status: 'SUGGESTED' });
    await getSuggestion('0192-abc');

    expect(calls.length).toBe(2);
    for (const init of calls) {
      const h = init.headers as Record<string, string>;
      expect(init.method).toBe('GET');
      expect(init.body).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Content-Type']).toBeUndefined();
    }
  });

  it('approve POSTs with an OPTIONAL note in the BODY + NO Idempotency-Key + NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(APPROVE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await approveSuggestion('0192-abc', '저재고 보충 승인');

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toContain(
      '/api/v1/demand-planning/suggestions/0192-abc/approve',
    );
    // The reason rides in the BODY — NOT a header.
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ note: '저재고 보충 승인' });
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('approve WITHOUT a note sends NO body at all (the note is OPTIONAL)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(APPROVE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await approveSuggestion('0192-abc');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    // No body ⇒ no Content-Type either.
    expect(h['Content-Type']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('dismiss POSTs with an OPTIONAL reason in the BODY + NO Idempotency-Key + NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DISMISS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await dismissSuggestion('0192-abc', '중복 추천');

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    expect(String(url)).toContain(
      '/api/v1/demand-planning/suggestions/0192-abc/dismiss',
    );
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ reason: '중복 추천' });
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('approve returns the DRAFT poId/poStatus and issues NO procurement submit/confirm/cancel call (DRAFT-only invariant)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(APPROVE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const result = await approveSuggestion('0192-abc');
    expect(result.poId).toBe('po-uuid-1');
    expect(result.poStatus).toBe('DRAFT');
    // EXACTLY one call — the approve. No follow-on /submit|/confirm|/cancel.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    for (const call of fetchMock.mock.calls) {
      const u = String(call[0]);
      expect(u).not.toMatch(/\/submit|\/confirm|\/cancel/);
    }
  });

  it('the api module exports ONLY the read + 2 action fns (no PO-write/webhook)', async () => {
    const mod = await import(
      '@/features/scm-replenishment/api/demand-planning-api'
    );
    expect(Object.keys(mod).sort()).toEqual(
      [
        'approveSuggestion',
        'dismissSuggestion',
        'getSuggestion',
        'listSuggestions',
      ].sort(),
    );
  });
});

describe('demand-planning-api — idempotency / state handling (§ 2.4.6.1 Edge Cases)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('idempotent re-approve (already MATERIALIZED 200) returns the EXISTING poId — success, no duplicate', async () => {
    // The producer's idempotent path returns a 200 with the existing poId.
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          id: '0192-abc',
          status: 'MATERIALIZED',
          poId: 'po-existing-1',
          poStatus: 'DRAFT',
        },
        meta: {},
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const result = await approveSuggestion('0192-abc');
    expect(result.status).toBe('MATERIALIZED');
    expect(result.poId).toBe('po-existing-1');
    // No second (duplicate) call.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('SKU_SUPPLIER_UNMAPPED (422) → ApiError inline (suggestion stays SUGGESTED — no optimistic transition)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SKU_SUPPLIER_UNMAPPED', 422)),
    );
    const err = await approveSuggestion('0192-abc').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('SKU_SUPPLIER_UNMAPPED');
  });

  it('INVALID_SUGGESTION_STATE (422, approve a DISMISSED one) → ApiError inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('INVALID_SUGGESTION_STATE', 422)),
    );
    const err = await approveSuggestion('0192-abc').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('INVALID_SUGGESTION_STATE');
  });

  it('hard 409 SUGGESTION_ALREADY_MATERIALIZED → ApiError(409) inline (benign "already materialized")', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SUGGESTION_ALREADY_MATERIALIZED', 409)),
    );
    const err = await approveSuggestion('0192-abc').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('SUGGESTION_ALREADY_MATERIALIZED');
  });

  it('404 SUGGESTION_NOT_FOUND → ApiError(404) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SUGGESTION_NOT_FOUND', 404)),
    );
    const err = await getSuggestion('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('SUGGESTION_NOT_FOUND');
  });
});

describe('demand-planning-api — scm FLAT error envelope (NOT wms NESTED) + § 2.5', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('UNAUTHORIZED', 401)),
    );
    const err = await listSuggestions().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline "not scoped"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('TENANT_FORBIDDEN', 403)),
    );
    const err = await listSuggestions().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('parses the FLAT { code } shape (a wms NESTED parser would yield HTTP_404)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SUGGESTION_NOT_FOUND', 404, 'missing')),
    );
    const err = await getSuggestion('x').catch((e) => e);
    expect(err.code).toBe('SUGGESTION_NOT_FOUND');
    expect(err.message).toBe('missing');
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
    const err = await approveSuggestion('x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('HTTP_422'); // synthetic — NOT 'WMS_SHAPE'
  });

  it('503 → ScmReplenishmentUnavailableError (ONLY this section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(scmError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listSuggestions().catch((e) => e);
    expect(err).toBeInstanceOf(ScmReplenishmentUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → ScmReplenishmentUnavailableError(timeout)', async () => {
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
    const err = await listSuggestions().catch((e) => e);
    expect(err).toBeInstanceOf(ScmReplenishmentUnavailableError);
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
    const err = await getSuggestion('x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });
});

describe('demand-planning-api — 429 Retry-After: ONE bounded backoff, NO storm (reuse of § 2.4.6)', () => {
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
      return Promise.resolve(jsonResponse(SUGGESTIONS_ENVELOPE));
    });
    vi.stubGlobal('fetch', fetchMock);

    const page = await listSuggestions();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(page.content).toHaveLength(1);
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

    const err = await listSuggestions().catch((e) => e);
    expect(err).toBeInstanceOf(ScmRateLimitedError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((err as ScmRateLimitedError).retryAfterSeconds).toBe(1);
  });
});

describe('demand-planning-api — tolerant parsing (unknown/future status + source never throws)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown status / source / extra fields parse without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [
            {
              id: 's-9',
              skuCode: 'SKU-9',
              status: 'FUTURE_STATUS_V2',
              source: 'FUTURE_SOURCE',
              extra: 'x',
            },
          ],
          meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
        }),
      ),
    );
    const page = await listSuggestions();
    expect(page.content[0].status).toBe('FUTURE_STATUS_V2');
    expect(page.content[0].source).toBe('FUTURE_SOURCE');
  });
});
