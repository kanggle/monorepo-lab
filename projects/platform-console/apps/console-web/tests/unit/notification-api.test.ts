import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/notifications/api/notification-api.ts` — the erp
 * `notification-service` in-app inbox client (TASK-PC-FE-052).
 *
 * Pins:
 *   - per-domain credential REUSE of § 2.4.8: the domain-facing GAP OIDC
 *     token, NEVER `getOperatorToken()`; no `X-Tenant-Id` (erp resolves
 *     tenant from the JWT claim);
 *   - NO `Idempotency-Key` on any call (mark-read is naturally idempotent —
 *     state-converging assignment, NOT an accumulating mutation);
 *   - NO `X-Operator-Reason` on any call (notification-service has no reason
 *     slot);
 *   - mark-read POSTs NO body;
 *   - NON_NULL absent-field parsing: `readAt` ABSENT while `read === false` →
 *     parses to `undefined` without throwing; `read === true` → `readAt` present;
 *   - `unread` query param forwarded ONLY when explicitly set (both true/false);
 *     OMITTED when absent from params (producer default = all);
 *   - 403 → `ApiError`; 503 → `ErpUnavailableError`; timeout → `ErpUnavailableError`.
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
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://gap.local/api/admin/auth/token-exchange',
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

import * as sessionModule from '@/shared/lib/session';
import {
  listNotifications,
  getNotification,
  markNotificationRead,
} from '@/features/notifications/api/notification-api';
import { NotificationSchema } from '@/features/notifications/api/notification-types';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

// A notification where read === false — readAt is ABSENT (NON_NULL convention).
const UNREAD_NOTIFICATION = {
  id: 'ntf-1',
  type: 'APPROVAL_SUBMITTED',
  title: '결재 상신 통지',
  body: '조직개편 결재가 상신되었습니다.',
  sourceType: 'APPROVAL',
  sourceId: 'appr-1',
  read: false,
  createdAt: '2026-06-05T00:00:00Z',
  // readAt ABSENT — NON_NULL (the key is omitted, not null).
};

// A notification where read === true — readAt is present.
const READ_NOTIFICATION = {
  id: 'ntf-2',
  type: 'APPROVAL_APPROVED',
  title: '결재 승인 통지',
  body: '조직개편 결재가 승인되었습니다.',
  sourceType: 'APPROVAL',
  sourceId: 'appr-2',
  read: true,
  createdAt: '2026-06-05T01:00:00Z',
  readAt: '2026-06-05T02:00:00Z',
};

const LIST_ENVELOPE = {
  data: [UNREAD_NOTIFICATION, READ_NOTIFICATION],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

const DETAIL_ENVELOPE = {
  data: UNREAD_NOTIFICATION,
  meta: { timestamp: 'x' },
};

const READ_DETAIL_ENVELOPE = {
  data: READ_NOTIFICATION,
  meta: { timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  return {
    url: String(url),
    init: init as RequestInit,
    headers: (init as RequestInit).headers as Record<string, string>,
  };
}

// ===========================================================================
// credential + tenant-model (reuse of § 2.4.8).
// ===========================================================================

describe('notification-api — per-domain credential (REUSE of § 2.4.8)', () => {
  it('sends the domain-facing GAP token, NEVER the operator token / X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listNotifications({});

    const { headers, url } = lastCall(fetchMock);
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(url).toContain('http://erp.local/api/erp/notifications');
    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the GAP session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listNotifications({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// reads — GET; query forwarding; NON_NULL absent-field parsing.
// ===========================================================================

describe('notification-api — reads (GET; absent readAt; unread query forwarding)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('absent readAt (read===false) parses to undefined — no parser throw (NON_NULL convention)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE)));
    const r = await listNotifications({});
    const unread = r.data.find((n) => !n.read);
    expect(unread).toBeDefined();
    expect(unread!.read).toBe(false);
    // readAt is ABSENT — must parse to undefined, NEVER throw.
    expect(unread!.readAt).toBeUndefined();
    // Also verify the zod schema directly — the missing key is not a parser error.
    expect(() => NotificationSchema.parse(UNREAD_NOTIFICATION)).not.toThrow();
    const parsed = NotificationSchema.parse(UNREAD_NOTIFICATION);
    expect(parsed.readAt).toBeUndefined();
  });

  it('present readAt (read===true) parses correctly', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE)));
    const r = await listNotifications({});
    const read = r.data.find((n) => n.read);
    expect(read).toBeDefined();
    expect(read!.readAt).toBe('2026-06-05T02:00:00Z');
  });

  it('unread=true forwards unread param when explicitly set to true', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listNotifications({ unread: true });
    const { url, init } = lastCall(fetchMock);
    expect(init.method).toBe('GET');
    const u = new URL(url);
    expect(u.searchParams.get('unread')).toBe('true');
  });

  it('unread=false forwards unread param when explicitly set to false', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listNotifications({ unread: false });
    const { url } = lastCall(fetchMock);
    const u = new URL(url);
    expect(u.searchParams.get('unread')).toBe('false');
  });

  it('unread omitted → NOT in the query string (producer default = all)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listNotifications({});
    const { url } = lastCall(fetchMock);
    const u = new URL(url);
    expect(u.searchParams.has('unread')).toBe(false);
  });

  it('page/size forwarded correctly', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listNotifications({ page: 2, size: 10 });
    const { url } = lastCall(fetchMock);
    const u = new URL(url);
    expect(u.searchParams.get('page')).toBe('2');
    expect(u.searchParams.get('size')).toBe('10');
  });

  it('detail GET: is a pure GET (no idempotency key / reason)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    const n = await getNotification('ntf-1');
    const { url, init, headers } = lastCall(fetchMock);
    expect(init.method).toBe('GET');
    expect(url).toBe('http://erp.local/api/erp/notifications/ntf-1');
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(n.id).toBe('ntf-1');
    expect(n.readAt).toBeUndefined();
  });
});

// ===========================================================================
// mark-read — naturally idempotent; no body, no Idempotency-Key, no Reason.
// ===========================================================================

describe('notification-api — mark-read (POST; no body; no Idempotency-Key; no X-Operator-Reason)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('POSTs to the correct URL with NO body, NO Idempotency-Key, NO X-Operator-Reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(READ_DETAIL_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    const n = await markNotificationRead('ntf-2');
    const { url, init, headers } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/notifications/ntf-2/read');
    expect(init.body).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Content-Type']).toBeUndefined();
    // The returned notification now has readAt present (read===true).
    expect(n.read).toBe(true);
    expect(n.readAt).toBe('2026-06-05T02:00:00Z');
  });

  it('uses domain-facing GAP token (NOT operator token) on mark-read', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(READ_DETAIL_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await markNotificationRead('ntf-2');
    const { headers } = lastCall(fetchMock);
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
  });
});

// ===========================================================================
// error taxonomy.
// ===========================================================================

describe('notification-api — error taxonomy (graceful, no crash)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('403 TENANT_FORBIDDEN → ApiError(403)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('TENANT_FORBIDDEN', 403)));
    const err = await listNotifications({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('403 PERMISSION_DENIED → ApiError(403)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('PERMISSION_DENIED', 403)));
    const err = await listNotifications({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('PERMISSION_DENIED');
  });

  it('404 NOTIFICATION_NOT_FOUND → ApiError(404)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('NOTIFICATION_NOT_FOUND', 404)));
    const err = await getNotification('ntf-unknown').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('NOTIFICATION_NOT_FOUND');
  });

  it('503 → ErpUnavailableError (notification bell section degrades only)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)));
    const err = await listNotifications({}).catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
  });

  it('timeout → ErpUnavailableError(timeout)', async () => {
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
    const err = await listNotifications({}).catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});
