import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/images-api.ts` — the security-critical core of
 * TASK-PC-FE-082 (the ecommerce product-image operator surface — ADR-MONO-031
 * Phase 1b CLOSING facet).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10 — EXACT MIRROR
 * of the products/orders-api tests):
 *   - every image call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()`), NEVER the exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called);
 *   - the console sends NO `X-Tenant-Id` and NO `Idempotency-Key`;
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is parsed;
 *   - 401 → ApiError(401); 403 → ApiError(403); 404/400/422/409 → ApiError
 *     inline; 503/timeout → EcommerceUnavailableError (section degrades only);
 *   - the actual file bytes are NEVER PUT from this module (the byte upload is
 *     a browser→S3 direct PUT, tested separately in the upload helper test).
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
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
    ECOMMERCE_PUBLIC_BASE_URL: 'http://ecommerce.local/api',
    ECOMMERCE_TIMEOUT_MS: 50,
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
  listImages,
  createImageUploadUrl,
  registerImage,
  updateImage,
  deleteImage,
} from '@/features/ecommerce-ops/api/images-api';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-06-14T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const IMAGE = {
  imageId: 'img-1',
  objectKey: 'products/p-1/0-abc.png',
  sortOrder: 0,
  isPrimary: true,
  url: 'http://cdn.local/products/p-1/0-abc.png',
  uploadedAt: '2026-06-14T00:00:00Z',
};
const IMAGE_LIST = { images: [IMAGE] };
const PRESIGNED = {
  uploadUrl: 'http://minio.local/product-images/products/p-1/0-abc.png?sig=x',
  objectKey: 'products/p-1/0-abc.png',
  expiresAt: '2026-06-14T00:15:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('images-api — per-domain credential selection (§ 2.4.10)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(IMAGE_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listImages('p-1');

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ecommerce',
    );
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/products/p-1/images',
    );
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(IMAGE_LIST)));

    await listImages('p-1');

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listImages('p-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id and NO Idempotency-Key on the register mutation', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(IMAGE, 201));
    vi.stubGlobal('fetch', fetchMock);

    await registerImage('p-1', {
      objectKey: 'products/p-1/0-abc.png',
      sortOrder: 0,
      isPrimary: true,
    });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('images-api — endpoint wiring + bodies (§ 2.4.10 #10-14)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('#10 list — GET admin /products/{id}/images', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(IMAGE_LIST));
    vi.stubGlobal('fetch', fetchMock);
    const list = await listImages('p-1');
    expect(list.images[0].imageId).toBe('img-1');
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/products/p-1/images',
    );
  });

  it('#11 upload-url — POST .../images/upload-url with { contentType, contentLength }', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PRESIGNED));
    vi.stubGlobal('fetch', fetchMock);
    const res = await createImageUploadUrl('p-1', {
      contentType: 'image/png',
      contentLength: 2048,
    });
    expect(res.uploadUrl).toContain('minio.local');
    expect(res.objectKey).toBe('products/p-1/0-abc.png');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/products/p-1/images/upload-url',
    );
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ contentType: 'image/png', contentLength: 2048 });
  });

  it('#12 register — POST .../images with { objectKey, sortOrder, isPrimary }', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(IMAGE, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerImage('p-1', {
      objectKey: 'products/p-1/0-abc.png',
      sortOrder: 0,
      isPrimary: true,
    });
    expect(res.imageId).toBe('img-1');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/products/p-1/images',
    );
    expect((init as RequestInit).method).toBe('POST');
  });

  it('#13 update — PATCH .../images/{imageId} with { isPrimary }', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(IMAGE));
    vi.stubGlobal('fetch', fetchMock);
    await updateImage('p-1', 'img-1', { isPrimary: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/products/p-1/images/img-1',
    );
    expect((init as RequestInit).method).toBe('PATCH');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ isPrimary: true });
  });

  it('#14 delete — DELETE .../images/{imageId} (204, no parse)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(deleteImage('p-1', 'img-1')).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/products/p-1/images/img-1',
    );
    expect((init as RequestInit).method).toBe('DELETE');
  });
});

describe('images-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listImages('p-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 → ApiError(403) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)));
    const err = await deleteImage('p-1', 'img-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('404 IMAGE_NOT_FOUND → ApiError(404) inline (parses FLAT shape)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('IMAGE_NOT_FOUND', 404, 'gone')),
    );
    const err = await updateImage('p-1', 'nope', { isPrimary: true }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('IMAGE_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('422 IMAGE_LIMIT_EXCEEDED → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('IMAGE_LIMIT_EXCEEDED', 422)),
    );
    const err = await registerImage('p-1', {
      objectKey: 'products/p-1/0-x.png',
      sortOrder: 10,
      isPrimary: false,
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('IMAGE_LIMIT_EXCEEDED');
  });

  it('400 MEDIA_VALIDATION_FAILED → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('MEDIA_VALIDATION_FAILED', 400)),
    );
    const err = await createImageUploadUrl('p-1', {
      contentType: 'image/gif',
      contentLength: 1,
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('MEDIA_VALIDATION_FAILED');
  });

  it('409 CONFLICT → ApiError(409) inline (no silent retry)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('CONFLICT', 409)));
    const err = await updateImage('p-1', 'img-1', { sortOrder: 1 }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
  });

  it('503 STORAGE_UNAVAILABLE → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('STORAGE_UNAVAILABLE', 503)),
    );
    const err = await listImages('p-1').catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → EcommerceUnavailableError(timeout)', async () => {
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
    const err = await listImages('p-1').catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
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
    const err = await listImages('p-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('an unknown/future field in the list parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ images: [{ ...IMAGE, futureField: 'x' }], extra: 1 }),
      ),
    );
    const list = await listImages('p-1');
    expect(list.images[0].imageId).toBe('img-1');
  });
});
