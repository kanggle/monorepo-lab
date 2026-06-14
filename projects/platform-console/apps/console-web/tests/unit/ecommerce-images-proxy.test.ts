import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops **image** proxy route handlers (TASK-PC-FE-082 —
 * § 2.4.10 #10-14):
 *   - GET list / POST register (`…/images/route`);
 *   - POST presigned upload-url (`…/images/upload-url/route`);
 *   - PATCH update / DELETE (`…/images/[imageId]/route`).
 *
 * Pins: domain-facing IAM OIDC token attached server-side (NOT the operator
 * token); NO X-Tenant-Id / Idempotency-Key; Zod body validation (bad/empty body
 * → 422, no upstream call); 401 when the IAM session is absent (no upstream);
 * producer 400/404/422/409 → passthrough inline; 503 → 503 (section degrades).
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

import {
  GET as imagesGET,
  POST as registerPOST,
} from '@/app/api/ecommerce/products/[id]/images/route';
import { POST as uploadUrlPOST } from '@/app/api/ecommerce/products/[id]/images/upload-url/route';
import {
  PATCH as imagePATCH,
  DELETE as imageDELETE,
} from '@/app/api/ecommerce/products/[id]/images/[imageId]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const IMAGE = {
  imageId: 'img-1',
  objectKey: 'products/p-1/0-abc.png',
  sortOrder: 0,
  isPrimary: true,
  url: 'http://cdn.local/0-abc.png',
  uploadedAt: '2026-06-14T00:00:00Z',
};
const idParams = { params: Promise.resolve({ id: 'p-1' }) };
const imageParams = { params: Promise.resolve({ id: 'p-1', imageId: 'img-1' }) };

function postReq(path: string, body: unknown) {
  return new Request(`http://console.local${path}`, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}
function patchReq(path: string, body: unknown) {
  return new Request(`http://console.local${path}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/products/{id}/images (list proxy)', () => {
  it('attaches the domain-facing token (NOT operator), NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ images: [IMAGE] }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await imagesGET(
      new Request('http://console.local/api/ecommerce/products/p-1/images'),
      idParams,
    );
    expect(res.status).toBe(200);
    const h = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<
      string,
      string
    >;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await imagesGET(
      new Request('http://console.local/api/ecommerce/products/p-1/images'),
      idParams,
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 STORAGE_UNAVAILABLE → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('STORAGE_UNAVAILABLE', 503)));
    const res = await imagesGET(
      new Request('http://console.local/api/ecommerce/products/p-1/images'),
      idParams,
    );
    expect(res.status).toBe(503);
  });
});

describe('POST /api/ecommerce/products/{id}/images (register proxy)', () => {
  it('valid body → 201; domain-facing token, NO Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(IMAGE, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerPOST(
      postReq('/api/ecommerce/products/p-1/images', {
        objectKey: 'products/p-1/0-abc.png',
        sortOrder: 0,
        isPrimary: true,
      }),
      idParams,
    );
    expect(res.status).toBe(201);
    const h = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<
      string,
      string
    >;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('invalid body (missing objectKey) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerPOST(
      postReq('/api/ecommerce/products/p-1/images', { sortOrder: 0, isPrimary: true }),
      idParams,
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('422 IMAGE_LIMIT_EXCEEDED → 422 passthrough (inline)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('IMAGE_LIMIT_EXCEEDED', 422)));
    const res = await registerPOST(
      postReq('/api/ecommerce/products/p-1/images', {
        objectKey: 'products/p-1/0-x.png',
        sortOrder: 11,
        isPrimary: false,
      }),
      idParams,
    );
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('IMAGE_LIMIT_EXCEEDED');
  });

  it('404 MEDIA_NOT_FOUND (object not uploaded yet) → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('MEDIA_NOT_FOUND', 404)));
    const res = await registerPOST(
      postReq('/api/ecommerce/products/p-1/images', {
        objectKey: 'products/p-1/0-x.png',
        sortOrder: 0,
        isPrimary: false,
      }),
      idParams,
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('MEDIA_NOT_FOUND');
  });
});

describe('POST /api/ecommerce/products/{id}/images/upload-url (presigned proxy)', () => {
  it('valid body → 200 with { uploadUrl, objectKey }', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ uploadUrl: 'http://minio.local/x?sig', objectKey: 'products/p-1/0-x.png' }),
      ),
    );
    const res = await uploadUrlPOST(
      postReq('/api/ecommerce/products/p-1/images/upload-url', {
        contentType: 'image/png',
        contentLength: 1024,
      }),
      idParams,
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.uploadUrl).toContain('minio.local');
    expect(body.objectKey).toBe('products/p-1/0-x.png');
  });

  it('invalid body (contentLength 0) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await uploadUrlPOST(
      postReq('/api/ecommerce/products/p-1/images/upload-url', {
        contentType: 'image/png',
        contentLength: 0,
      }),
      idParams,
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 MEDIA_VALIDATION_FAILED → 400 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('MEDIA_VALIDATION_FAILED', 400)));
    const res = await uploadUrlPOST(
      postReq('/api/ecommerce/products/p-1/images/upload-url', {
        contentType: 'image/png',
        contentLength: 99999999,
      }),
      idParams,
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('MEDIA_VALIDATION_FAILED');
  });
});

describe('PATCH/DELETE /api/ecommerce/products/{id}/images/{imageId}', () => {
  it('PATCH valid body → 200', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(IMAGE)));
    const res = await imagePATCH(
      patchReq('/api/ecommerce/products/p-1/images/img-1', { isPrimary: true }),
      imageParams,
    );
    expect(res.status).toBe(200);
  });

  it('PATCH empty body (no sortOrder / no isPrimary) → 422 (refine fail, no upstream)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await imagePATCH(
      patchReq('/api/ecommerce/products/p-1/images/img-1', {}),
      imageParams,
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('PATCH 404 IMAGE_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('IMAGE_NOT_FOUND', 404)));
    const res = await imagePATCH(
      patchReq('/api/ecommerce/products/p-1/images/img-1', { sortOrder: 2 }),
      imageParams,
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('IMAGE_NOT_FOUND');
  });

  it('DELETE → 204', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    const res = await imageDELETE(
      new Request('http://console.local/api/ecommerce/products/p-1/images/img-1', {
        method: 'DELETE',
      }),
      imageParams,
    );
    expect(res.status).toBe(204);
  });

  it('DELETE 403 → 403 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)));
    const res = await imageDELETE(
      new Request('http://console.local/api/ecommerce/products/p-1/images/img-1', {
        method: 'DELETE',
      }),
      imageParams,
    );
    expect(res.status).toBe(403);
  });

  it('DELETE no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await imageDELETE(
      new Request('http://console.local/api/ecommerce/products/p-1/images/img-1', {
        method: 'DELETE',
      }),
      imageParams,
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
