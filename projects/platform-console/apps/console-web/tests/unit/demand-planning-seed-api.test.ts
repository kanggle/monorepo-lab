import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/scm-config/api/demand-planning-seed-api.ts` — the security-critical
 * core of TASK-PC-FE-080 (the scm seed/config operator surface; the operational
 * fix-path for FE-077's `SKU_SUPPLIER_UNMAPPED` gap).
 *
 * THE CENTRAL ASSERTIONS (console-integration-contract § 2.4.6.2 — REUSE of the
 * § 2.4.6 / § 2.4.6.1 per-domain credential rule, NOT re-derived):
 *   - every policy/mapping call's bearer is the **domain-facing IAM OIDC ACCESS
 *     token**, NEVER the exchanged operator token (operator-token path ABSENT —
 *     on GET AND PUT — pinned);
 *   - the console sends NO `X-Tenant-Id` (scm resolves tenant from the JWT
 *     `tenant_id ∈ {scm,*}` claim producer-side);
 *   - PUT is an idempotent upsert — the body IS the FULL row + NO
 *     `Idempotency-Key` + NO `X-Operator-Reason` (asserted absent — the producer
 *     defines NEITHER header);
 *   - the seed client issues NO suggestion/PO/dispatch call (only policies +
 *     sku-supplier-map GET/PUT — config affects FUTURE evaluation only);
 *   - GET 404 (POLICY_NOT_FOUND / MAPPING_NOT_FOUND) is a typed `{ found:false }`
 *     not-found, NOT a thrown error;
 *   - the scm FLAT error envelope `{ code, message, timestamp }` is parsed (NOT
 *     wms's NESTED `{ error: { code } }`);
 *   - 401 → ApiError(401) (whole-session re-login); 403 → ApiError(403) inline;
 *     422 VALIDATION_ERROR → ApiError inline; 429 → ScmRateLimitedError (ONE
 *     bounded backoff, NO storm); 503/timeout → ScmReplenishmentUnavailableError.
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

import * as sessionModule from '@/shared/lib/session';

import {
  getPolicy,
  putPolicy,
  getSupplierMap,
  putSupplierMap,
} from '@/features/scm-config/api/demand-planning-seed-api';
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
    JSON.stringify({ code, message, timestamp: '2026-06-13T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const POLICY_ENVELOPE = {
  data: { reorderPoint: 10, safetyStock: 5, reorderQty: 100 },
  meta: {},
};
const MAP_ENVELOPE = {
  data: {
    supplierId: 'sup-uuid-1',
    defaultOrderQty: 100,
    leadTimeDays: 7,
    currency: 'KRW',
  },
  meta: {},
};

const POLICY_INPUT = { reorderPoint: 10, safetyStock: 5, reorderQty: 100 };
const MAP_INPUT = {
  supplierId: 'sup-uuid-1',
  defaultOrderQty: 100,
  leadTimeDays: 7,
  currency: 'KRW',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('seed-api — per-domain credential (REUSE of § 2.4.6; the INVERSE of #569)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer on a GET (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-scm');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi
      .fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
      );
    vi.stubGlobal('fetch', fetchMock);

    await getPolicy('SKU-1');

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-scm',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain(
      'http://scm.local/api/v1/demand-planning/policies/SKU-1',
    );
  });

  it('sends the IAM OIDC ACCESS bearer on a PUT too (upsert) — same credential for GET and PUT', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await putPolicy('SKU-1', POLICY_INPUT);

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (GET AND PUT; pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');

    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(jsonResponse(MAP_ENVELOPE)),
      ),
    );
    await getSupplierMap('SKU-1');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(jsonResponse(MAP_ENVELOPE)),
      ),
    );
    await putSupplierMap('SKU-1', MAP_INPUT);

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for scm on BOTH GET and PUT.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await getPolicy('SKU-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (scm resolves tenant from the JWT claim)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await getPolicy('SKU-1');

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('seed-api — mutation discipline (§ 2.4.6.2: full-row PUT, no invented headers)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('GET reads are pure GET with NO mutation artifacts (no body, no headers)', async () => {
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await getPolicy('SKU-1');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
  });

  it('putPolicy PUTs the right route with the FULL-row body + NO Idempotency-Key + NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await putPolicy('SKU-1', POLICY_INPUT);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('PUT');
    expect(String(url)).toContain(
      '/api/v1/demand-planning/policies/SKU-1',
    );
    // The body IS the full row.
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(
      POLICY_INPUT,
    );
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('putSupplierMap PUTs the right route with the FULL-row body + NO invented headers', async () => {
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(MAP_ENVELOPE)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await putSupplierMap('SKU-1', MAP_INPUT);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('PUT');
    expect(String(url)).toContain(
      '/api/v1/demand-planning/sku-supplier-map/SKU-1',
    );
    expect(JSON.parse((init as RequestInit).body as string)).toEqual(MAP_INPUT);
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
  });

  it('the api module exports ONLY the 2 policy + 2 mapping seed fns (no suggestion/PO/dispatch)', async () => {
    const mod = await import(
      '@/features/scm-config/api/demand-planning-seed-api'
    );
    expect(Object.keys(mod).sort()).toEqual(
      ['getPolicy', 'getSupplierMap', 'putPolicy', 'putSupplierMap'].sort(),
    );
  });

  it('config edits issue NO suggestion/PO/dispatch call — only policies + sku-supplier-map GET/PUT', async () => {
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await getPolicy('SKU-1');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(jsonResponse(MAP_ENVELOPE)),
      ),
    );
    await putSupplierMap('SKU-1', MAP_INPUT);

    // Across BOTH calls' URLs: only the two seed routes, NEVER a suggestion /
    // approve / dismiss / submit / confirm / cancel / dispatch endpoint.
    const allUrls = [
      ...fetchMock.mock.calls.map((c) => String(c[0])),
    ];
    for (const u of allUrls) {
      expect(u).toMatch(/\/(policies|sku-supplier-map)\//);
      expect(u).not.toMatch(
        /\/suggestions|\/approve|\/dismiss|\/submit|\/confirm|\/cancel|\/dispatch/,
      );
    }
  });
});

describe('seed-api — 404-as-empty-state (typed not-found, NOT a thrown error)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('GET policy 404 POLICY_NOT_FOUND → { found: false } (not configured yet), NOT a throw', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('POLICY_NOT_FOUND', 404)),
      ),
    );
    const result = await getPolicy('SKU-NOPE');
    expect(result.found).toBe(false);
  });

  it('GET supplier-map 404 MAPPING_NOT_FOUND → { found: false }, NOT a throw', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('MAPPING_NOT_FOUND', 404)),
      ),
    );
    const result = await getSupplierMap('SKU-NOPE');
    expect(result.found).toBe(false);
  });

  it('GET 200 → { found: true, value } (a configured row)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(jsonResponse(POLICY_ENVELOPE)),
      ),
    );
    const result = await getPolicy('SKU-1');
    expect(result.found).toBe(true);
    if (result.found) {
      expect(result.value.reorderPoint).toBe(10);
      expect(result.value.reorderQty).toBe(100);
    }
  });
});

describe('seed-api — validation + scm FLAT error envelope (NOT wms NESTED) + § 2.5', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('PUT 422 VALIDATION_ERROR → ApiError(422) inline (e.g. negative qty rejected producer-side)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('VALIDATION_ERROR', 422)),
      ),
    );
    const err = await putPolicy('SKU-1', POLICY_INPUT).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('VALIDATION_ERROR');
  });

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('UNAUTHORIZED', 401)),
      ),
    );
    const err = await getPolicy('SKU-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline "not scoped"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('TENANT_FORBIDDEN', 403)),
      ),
    );
    const err = await getSupplierMap('SKU-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('parses the FLAT { code } shape on a non-404 error (a wms NESTED parser would yield HTTP_422)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('VALIDATION_ERROR', 422, 'negative qty')),
      ),
    );
    const err = await putPolicy('SKU-1', POLICY_INPUT).catch((e) => e);
    expect(err.code).toBe('VALIDATION_ERROR');
    expect(err.message).toBe('negative qty');
  });

  it('a wms-NESTED { error: { code } } body is NOT mis-parsed (falls back synthetic, no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(
          new Response(
            JSON.stringify({ error: { code: 'WMS_SHAPE', message: 'x' } }),
            { status: 422, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
      ),
    );
    const err = await putPolicy('SKU-1', POLICY_INPUT).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('HTTP_422'); // synthetic — NOT 'WMS_SHAPE'
  });

  it('503 → ScmReplenishmentUnavailableError (ONLY this section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(scmError('SERVICE_UNAVAILABLE', 503)),
      ),
    );
    const err = await getPolicy('SKU-1').catch((e) => e);
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
    const err = await getPolicy('SKU-1').catch((e) => e);
    expect(err).toBeInstanceOf(ScmReplenishmentUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});

describe('seed-api — 429 Retry-After: ONE bounded backoff, NO storm (reuse of § 2.4.6)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('429 once → ONE bounded retry honouring Retry-After, then succeeds (no storm)', async () => {
    let n = 0;
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) => {
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
      return Promise.resolve(jsonResponse(POLICY_ENVELOPE));
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await getPolicy('SKU-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(result.found).toBe(true);
  });

  it('429 persisting after the bounded retry → ScmRateLimitedError (NO further storm)', async () => {
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
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

    const err = await putPolicy('SKU-1', POLICY_INPUT).catch((e) => e);
    expect(err).toBeInstanceOf(ScmRateLimitedError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((err as ScmRateLimitedError).retryAfterSeconds).toBe(1);
  });
});

describe('seed-api — tolerant parsing (forward-compatible extra fields never throw)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an extra/unknown field on the row parses without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, _init?: RequestInit) =>
        Promise.resolve(
          jsonResponse({
            data: {
              supplierId: 'sup-9',
              defaultOrderQty: 50,
              leadTimeDays: 3,
              currency: 'USD',
              futureField: 'x',
            },
            meta: {},
          }),
        ),
      ),
    );
    const result = await getSupplierMap('SKU-9');
    expect(result.found).toBe(true);
    if (result.found) {
      expect(result.value.supplierId).toBe('sup-9');
      expect(result.value.currency).toBe('USD');
    }
  });
});
