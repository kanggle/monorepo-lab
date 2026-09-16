/**
 * The six gateway cores in sample mode (ADR-MONO-074 A2 — TASK-PC-FE-282 AC-2).
 *
 * Per core:
 *   ① sample visitor → global `fetch` spy **0 calls**, and the sample Response
 *     goes through the core's EXISTING handling: a `ready` answer is parsed and
 *     returned; a pending GET becomes the profile's section-degrade error carrying
 *     `SAMPLE_NOT_READY`; a write becomes an `ApiError(403)` carrying
 *     `SAMPLE_READ_ONLY` (the message is whatever the core always wrote — the
 *     copy is not in it, AC-3).
 *   ② an authenticated operator → `fetch` IS called (the gate did not swallow
 *     the real path). The unmodified pre-existing core tests are the full control
 *     group (AC-13); this cell only proves the branch is a branch.
 *   ③ a half session → the existing 401 path, not a sample.
 *
 * Real `session.ts` + real sample router; only `next/headers`, env and `fetch`
 * are replaced. The router is wrapped (not replaced) so one cell can force a
 * `ready` answer for a surface whose real fixture does not exist yet.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

vi.mock('@/shared/config/env', () => ({
  getServerEnv: () => ({
    IAM_ADMIN_API_BASE: 'http://iam.local',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    ECOMMERCE_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
  }),
}));

vi.mock('@/shared/sample/router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/sample/router')>();
  return { ...actual, sampleResponse: vi.fn(actual.sampleResponse) };
});

import { callAdminGateway, type AdminGatewayProfile } from '@/shared/api/iam-gateway';
import { fetchRegistry } from '@/shared/api/registry-client';
import { callWmsGateway, type WmsGatewayProfile } from '@/shared/api/wms-gateway';
import {
  callEcommerceGateway,
  type EcommerceGatewayProfile,
} from '@/shared/api/ecommerce-gateway';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';
import { callScmGateway, type ScmGatewayProfile } from '@/shared/api/scm-gateway';
import { ApiError } from '@/shared/api/errors';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_NOT_READY, SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { ACCESS_COOKIE, OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

class TestUnavailable extends Error {
  constructor(
    readonly reason: string,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

const unavailable = {
  makeUnavailable: (reason: 'timeout' | 'circuit_open' | 'downstream', code: string, message: string) =>
    new TestUnavailable(reason, code, message),
  isUnavailable: (err: unknown) => err instanceof TestUnavailable,
  messages: { degraded: 'degraded', timeout: 'timeout', network: 'network' },
};

const IAM_PROFILE: AdminGatewayProfile = {
  logPrefix: 'accounts',
  requestFailedLabel: 'accounts request failed',
  resolveTimeoutMs: () => 50,
  forbiddenMode: 'auth',
  forceMutationHeaders: true,
  ...unavailable,
};
const WMS_PROFILE: WmsGatewayProfile = {
  logPrefix: 'wms',
  requestFailedLabel: 'wms request failed',
  resolveDefaults: () => ({ baseUrl: 'http://wms.local/api/v1/admin', timeoutMs: 50 }),
  ...unavailable,
};
const ECOMMERCE_PROFILE: EcommerceGatewayProfile = {
  logPrefix: 'ecommerce_order',
  requestFailedLabel: 'ecommerce order request failed',
  ...unavailable,
};
const FLAT_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'erp',
  requestFailedLabel: 'erp request failed',
  resolveDefaults: () => ({ baseUrl: 'http://erp.local', timeoutMs: 50 }),
  ...unavailable,
};
const SCM_PROFILE: ScmGatewayProfile = {
  logPrefix: 'scm',
  requestFailedLabel: 'scm request failed',
  ...unavailable,
};

const parse = (json: unknown) => (json as { value: number }).value;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Force the next sample answer to be a `ready` 200 (surface fixtures come in 283…288). */
function nextSampleIsReady(body: unknown) {
  vi.mocked(sampleResponse).mockImplementationOnce(() => json(200, body));
}

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  cookieJar.clear();
  vi.mocked(sampleResponse).mockClear();
  fetchSpy = vi.fn(async () => json(200, { value: 1 }));
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function authenticate() {
  cookieJar.set(ACCESS_COOKIE, 'access');
  cookieJar.set(OPERATOR_COOKIE, 'operator');
  cookieJar.set(TENANT_COOKIE, 'acme');
}

describe('callAdminGateway (iam)', () => {
  it('① ready → parsed value, fetch 0', async () => {
    nextSampleIsReady({ value: 42 });
    await expect(
      callAdminGateway({ method: 'GET', path: '/api/admin/accounts' }, parse, IAM_PROFILE),
    ).resolves.toBe(42);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① pending GET → section degrade carrying SAMPLE_NOT_READY, fetch 0', async () => {
    // TASK-PC-FE-283 — the DoD it closes is "IAM 원장의 `pending` 0", so by the
    // time this suite runs there is no longer a real IAM surface this cell can
    // point at (`accounts` — the profile this test used before — is now
    // `ready`, per this very task). This cell tests the CORE's generic
    // ready/pending BRANCH mechanics, not any one surface's content, so a
    // `logPrefix` absent from the ledger entirely exercises the identical
    // branch (`findSurfaceCoverage` → `undefined` → same "not ready" path a
    // real `pending` row takes) without depending on a surface staying
    // unimplemented forever.
    const NO_SUCH_SURFACE_PROFILE: AdminGatewayProfile = {
      ...IAM_PROFILE,
      logPrefix: 'no-such-surface',
    };
    const err = await callAdminGateway(
      { method: 'GET', path: '/api/admin/no-such-surface' },
      parse,
      NO_SUCH_SURFACE_PROFILE,
    ).catch((e) => e);
    expect(err).toBeInstanceOf(TestUnavailable);
    expect((err as TestUnavailable).code).toBe(SAMPLE_NOT_READY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① write → ApiError 403 SAMPLE_READ_ONLY (message overwritten as always), fetch 0 — even with no reason/key', async () => {
    const err = await callAdminGateway(
      { method: 'POST', path: '/api/admin/accounts/a/lock' },
      parse,
      IAM_PROFILE,
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(403);
    expect((err as ApiError).code).toBe(SAMPLE_READ_ONLY);
    expect((err as ApiError).message).toBe('not permitted');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('② authenticated → the real path calls fetch', async () => {
    authenticate();
    await expect(
      callAdminGateway({ method: 'GET', path: '/api/admin/accounts' }, parse, IAM_PROFILE),
    ).resolves.toBe(1);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(sampleResponse).not.toHaveBeenCalled();
  });

  it('③ half session (access only) → the existing 401, not a sample', async () => {
    cookieJar.set(ACCESS_COOKIE, 'access');
    const err = await callAdminGateway(
      { method: 'GET', path: '/api/admin/accounts' },
      parse,
      IAM_PROFILE,
    ).catch((e) => e);
    expect((err as ApiError).status).toBe(401);
    expect(sampleResponse).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('fetchRegistry (registry — ready fixture)', () => {
  it('① sample visitor → the sample registry, parsed; fetch 0', async () => {
    const reg = await fetchRegistry();
    expect(reg.products).toHaveLength(6);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('② authenticated → fetch', async () => {
    authenticate();
    fetchSpy.mockResolvedValueOnce(json(200, { products: [] }));
    await fetchRegistry();
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});

describe('callWmsGateway (wms — NESTED envelope)', () => {
  it('① ready → { data, lagSeconds: null }, fetch 0', async () => {
    nextSampleIsReady({ value: 7 });
    await expect(
      callWmsGateway({ method: 'GET', path: '/dashboard/alerts' }, parse, WMS_PROFILE),
    ).resolves.toEqual({ data: 7, lagSeconds: null });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① pending GET → SAMPLE_NOT_READY survives the NESTED parser, fetch 0', async () => {
    const err = await callWmsGateway(
      { method: 'GET', path: '/dashboard/alerts' },
      parse,
      WMS_PROFILE,
    ).catch((e) => e);
    expect((err as TestUnavailable).code).toBe(SAMPLE_NOT_READY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① write (no idempotency key) → 403 SAMPLE_READ_ONLY, fetch 0', async () => {
    const err = await callWmsGateway(
      { method: 'POST', path: '/dashboard/alerts/1/acknowledge' },
      parse,
      WMS_PROFILE,
    ).catch((e) => e);
    expect((err as ApiError).status).toBe(403);
    expect((err as ApiError).code).toBe(SAMPLE_READ_ONLY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('the sample router is asked with the origin-less path', async () => {
    await callWmsGateway({ method: 'GET', path: '/dashboard/alerts' }, parse, WMS_PROFILE).catch(
      () => undefined,
    );
    expect(sampleResponse).toHaveBeenCalledWith({
      core: 'wms',
      surface: 'wms',
      method: 'GET',
      path: '/api/v1/admin/dashboard/alerts',
    });
  });

  it('② authenticated → fetch', async () => {
    authenticate();
    await callWmsGateway({ method: 'GET', path: '/dashboard/alerts' }, parse, WMS_PROFILE);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});

describe('callEcommerceGateway (ecommerce)', () => {
  it('① ready → parsed value, fetch 0', async () => {
    nextSampleIsReady({ value: 9 });
    await expect(
      callEcommerceGateway(
        { method: 'GET', base: 'http://ecommerce.local/api/admin', path: '/orders' },
        parse,
        ECOMMERCE_PROFILE,
      ),
    ).resolves.toBe(9);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① pending GET → SAMPLE_NOT_READY, fetch 0', async () => {
    // TASK-PC-FE-284 — this suite's DoD is "ecommerce 원장의 `pending` 0", so
    // `ecommerce_order` (this cell's original profile) is now `ready`, exactly
    // as TASK-PC-FE-283's identical note on the `iam` describe block recorded
    // for `accounts`. This cell tests the CORE's generic ready/pending BRANCH
    // mechanics, not any one surface's content, so a `logPrefix` absent from
    // the ledger entirely exercises the identical branch without depending on
    // a surface staying unimplemented forever.
    const NO_SUCH_SURFACE_PROFILE: EcommerceGatewayProfile = {
      ...ECOMMERCE_PROFILE,
      logPrefix: 'no-such-surface',
    };
    const err = await callEcommerceGateway(
      { method: 'GET', base: 'http://ecommerce.local/api/admin', path: '/orders' },
      parse,
      NO_SUCH_SURFACE_PROFILE,
    ).catch((e) => e);
    expect((err as TestUnavailable).code).toBe(SAMPLE_NOT_READY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① write → 403 SAMPLE_READ_ONLY, fetch 0', async () => {
    const err = await callEcommerceGateway(
      {
        method: 'POST',
        base: 'http://ecommerce.local/api/admin',
        path: '/orders/o-1/status',
        body: { status: 'SHIPPED' },
      },
      undefined,
      ECOMMERCE_PROFILE,
    ).catch((e) => e);
    expect((err as ApiError).code).toBe(SAMPLE_READ_ONLY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('② authenticated → fetch', async () => {
    authenticate();
    await callEcommerceGateway(
      { method: 'GET', base: 'http://ecommerce.local/api/admin', path: '/orders' },
      parse,
      ECOMMERCE_PROFILE,
    );
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});

describe('callFlatEnvelopeGateway (erp/finance/ledger) + callScmGateway shim', () => {
  it('① ready → { raw }, fetch 0', async () => {
    nextSampleIsReady({ value: 5 });
    const out = await callFlatEnvelopeGateway(
      { path: '/api/erp/masterdata/departments' },
      parse,
      FLAT_PROFILE,
    );
    expect(out.raw).toBe(5);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① pending GET → SAMPLE_NOT_READY, fetch 0', async () => {
    // TASK-PC-FE-285 — this suite's DoD is "erp 원장의 `pending` 0", so `erp`
    // (this cell's original profile) is now `ready`, exactly as
    // TASK-PC-FE-283/284's identical note on the `iam`/`ecommerce` describe
    // blocks recorded for `accounts`/`ecommerce_order`. This cell tests the
    // CORE's generic ready/pending BRANCH mechanics, not any one surface's
    // content, so a `logPrefix` absent from the ledger entirely exercises the
    // identical branch (`findSurfaceCoverage` → `undefined` → the same "not
    // ready" path a real `pending` row takes) without depending on a surface
    // staying unimplemented forever.
    const NO_SUCH_SURFACE_PROFILE: FlatEnvelopeGatewayProfile = {
      ...FLAT_PROFILE,
      logPrefix: 'no-such-surface',
    };
    const err = await callFlatEnvelopeGateway(
      { path: '/api/erp/masterdata/departments' },
      parse,
      NO_SUCH_SURFACE_PROFILE,
    ).catch((e) => e);
    expect((err as TestUnavailable).code).toBe(SAMPLE_NOT_READY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① write → 403 SAMPLE_READ_ONLY, fetch 0', async () => {
    const err = await callFlatEnvelopeGateway(
      { path: '/api/erp/masterdata/departments', method: 'POST', body: {} },
      parse,
      { ...FLAT_PROFILE, requireIdempotencyKeyOnMutation: true },
    ).catch((e) => e);
    expect((err as ApiError).status).toBe(403);
    expect((err as ApiError).code).toBe(SAMPLE_READ_ONLY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('① scm shim — pending GET → SAMPLE_NOT_READY, fetch 0', async () => {
    const err = await callScmGateway({ path: '/api/v1/procurement/po' }, parse, SCM_PROFILE).catch(
      (e) => e,
    );
    expect((err as TestUnavailable).code).toBe(SAMPLE_NOT_READY);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('② authenticated → fetch', async () => {
    authenticate();
    await callFlatEnvelopeGateway({ path: '/api/erp/masterdata/departments' }, parse, FLAT_PROFILE);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});
