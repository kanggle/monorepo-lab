import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/notifications-api.ts` — security-critical core of
 * TASK-PC-FE-089 (ecommerce notifications template operator surface — ADR-031 Phase 5b).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10.4, inheriting the
 * non-IAM § 2.4.10 rules — EXACT MIRROR of the promotions/shippings-api tests):
 *   - every notification call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie), NEVER
 *     the exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called — pinned
 *     so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (ecommerce resolves tenant from the
 *     JWT `tenant_id` claim producer-side);
 *   - NO `Idempotency-Key` on any mutation (the producer defines none — § 2.4.10);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is parsed;
 *   - 401 → ApiError(401); 403 → ApiError(403); 404/409 → ApiError inline;
 *     503/timeout → EcommerceUnavailableError (section degrades only);
 *   - all requests go to ECOMMERCE_PUBLIC_BASE_URL + `/notifications/templates/**`
 *     (NOT ECOMMERCE_ADMIN_BASE_URL — notification-service sits at /api/notifications,
 *     not /api/admin/notifications);
 *   - create requires type+channel; update uses PUT with ONLY subject+body
 *     (type/channel immutable — NEVER sent on update).
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
  listTemplates,
  getTemplate,
  createTemplate,
  updateTemplate,
} from '@/features/ecommerce-ops/api/notifications-api';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** ecommerce FLAT error envelope (distinct from wms's nested shape). */
function ecomError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-06-14T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const TEMPLATE_LIST = {
  content: [
    {
      templateId: 'tmpl-1',
      type: 'ORDER_PLACED',
      channel: 'EMAIL',
      subject: '주문이 완료되었습니다',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const TEMPLATE_DETAIL = {
  templateId: 'tmpl-1',
  type: 'ORDER_PLACED',
  channel: 'EMAIL',
  subject: '주문이 완료되었습니다',
  body: '안녕하세요 {{name}}님, 주문 {{orderId}}가 접수되었습니다.',
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
};

const VALID_CREATE_BODY = {
  type: 'ORDER_PLACED' as const,
  channel: 'EMAIL' as const,
  subject: '주문이 완료되었습니다',
  body: '안녕하세요 {{name}}님, 주문이 완료되었습니다.',
};

const VALID_UPDATE_BODY = {
  subject: '주문이 완료되었습니다 (업데이트)',
  body: '안녕하세요 {{name}}님, 주문 {{orderId}}가 접수되었습니다.',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('notifications-api — per-domain credential selection (§ 2.4.10.4)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listTemplates({ page: 0, size: 20 });

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ecommerce',
    );
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_LIST)));

    await listTemplates();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listTemplates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id and NO Idempotency-Key on create mutation', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ templateId: 'tmpl-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    await createTemplate(VALID_CREATE_BODY);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('notifications-api — endpoint wiring + base URL (ECOMMERCE_PUBLIC_BASE_URL + /notifications/templates/**)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('list — GET PUBLIC base /notifications/templates?... (NOT /api/admin/notifications)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listTemplates({ size: 999, page: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    // Must use /api/notifications/templates (public base), NOT /api/admin/notifications
    expect(u.pathname).toBe('/api/notifications/templates');
    expect(u.searchParams.get('size')).toBe('100'); // capped at 100
    expect(u.searchParams.get('page')).toBe('2');
    expect(String(url)).not.toContain('/api/admin/notifications');
  });

  it('detail — GET PUBLIC base /notifications/templates/{id} (BE-373 endpoint)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(TEMPLATE_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const d = await getTemplate('tmpl-1');
    expect(d.subject).toBe('주문이 완료되었습니다');
    expect(d.body).toBeTruthy();
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/notifications/templates/tmpl-1',
    );
  });

  it('create — POST PUBLIC base /notifications/templates with type+channel+subject+body', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ templateId: 'tmpl-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await createTemplate(VALID_CREATE_BODY);
    expect(res.templateId).toBe('tmpl-9');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/notifications/templates',
    );
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.type).toBe('ORDER_PLACED');
    expect(body.channel).toBe('EMAIL');
    expect(body.subject).toBeTruthy();
    expect(body.body).toBeTruthy();
  });

  it('update — PUT PUBLIC base /notifications/templates/{id} with ONLY subject+body (type/channel immutable — NOT sent)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ templateId: 'tmpl-1' }));
    vi.stubGlobal('fetch', fetchMock);
    await updateTemplate('tmpl-1', VALID_UPDATE_BODY);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/notifications/templates/tmpl-1',
    );
    // CRITICAL: must be PUT
    expect((init as RequestInit).method).toBe('PUT');
    const body = JSON.parse((init as RequestInit).body as string);
    // CRITICAL: type and channel must NOT be in the update body
    expect(body.type).toBeUndefined();
    expect(body.channel).toBeUndefined();
    expect(body.subject).toBeTruthy();
    expect(body.body).toBeTruthy();
  });

  it('create requires type+channel — they ARE present in the create body', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ templateId: 'tmpl-10' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    await createTemplate({
      type: 'WELCOME',
      channel: 'SMS',
      subject: '환영합니다',
      body: '가입을 환영합니다!',
    });
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.type).toBe('WELCOME');
    expect(body.channel).toBe('SMS');
  });
});

describe('notifications-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listTemplates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 ACCESS_DENIED → ApiError(403) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)));
    const err = await createTemplate(VALID_CREATE_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('parses the FLAT { code, message } shape (404 TEMPLATE_NOT_FOUND)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('TEMPLATE_NOT_FOUND', 404, 'gone')),
    );
    const err = await getTemplate('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('TEMPLATE_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('400 VALIDATION_ERROR → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('VALIDATION_ERROR', 400)),
    );
    const err = await createTemplate(VALID_CREATE_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_ERROR');
  });

  it('409 TEMPLATE_ALREADY_EXISTS → ApiError(409) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('TEMPLATE_ALREADY_EXISTS', 409)),
    );
    const err = await createTemplate(VALID_CREATE_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('TEMPLATE_ALREADY_EXISTS');
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
    const err = await getTemplate('tmpl-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listTemplates().catch((e) => e);
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
    const err = await listTemplates().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('an unknown/future type enum parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          ...TEMPLATE_DETAIL,
          type: 'FUTURE_NOTIFICATION_TYPE',
          extra: 'x',
        }),
      ),
    );
    const d = await getTemplate('tmpl-1');
    expect(d.type).toBe('FUTURE_NOTIFICATION_TYPE');
  });
});
