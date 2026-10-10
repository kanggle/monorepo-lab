import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin operator-invitations proxy route handlers (TASK-MONO-772 S5 —
 * console-integration-contract § 2.4.3 rows 11–14 + the per-endpoint header
 * matrix):
 *   - invite → BOTH `X-Operator-Reason` + `Idempotency-Key`; body has no
 *     password; `tenantId='*'` refused at the boundary (no producer call)
 *   - list → no mutation headers; `tenantId` = active tenant by default;
 *     `status=PENDING` by default
 *   - cancel / resend → `X-Operator-Reason` ONLY (Idempotency-Key ABSENT) on
 *     the producer's `{id}:cancel` / `{id}:resend` custom-method path
 *   - a FAILED_* delivery is a 201/200, not an error
 *   - producer codes pass through (409 NOT_PENDING, 404 NOT_FOUND, 409
 *     ALREADY_PENDING); 503 → 503 (section degrade); 401 → 401
 *   - 🔴 the response never carries a token / link, even if the producer
 *     leaked one (the parse strips unknown keys)
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  GET as listGET,
  POST as invitePOST,
} from '@/app/api/operator-invitations/route';
import { POST as cancelPOST } from '@/app/api/operator-invitations/[invitationId]/cancel/route';
import { POST as resendPOST } from '@/app/api/operator-invitations/[invitationId]/resend/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ITEM = {
  invitationId: 'inv-1',
  tenantId: 'acme-corp',
  email: 'person@example.com',
  displayName: '홍길동',
  roles: ['SUPPORT_LOCK'],
  status: 'PENDING',
  expired: false,
  expiresAt: '2026-10-18T10:00:00Z',
  createdAt: '2026-10-11T10:00:00Z',
  invitedBy: 'op-1',
  delivery: { status: 'SENT', attemptedAt: '2026-10-11T10:00:01Z' },
  acceptedAt: null,
  acceptedOperatorId: null,
  cancelledAt: null,
};

function headersOf(fetchMock: ReturnType<typeof vi.fn>, i = 0) {
  return (fetchMock.mock.calls[i][1] as RequestInit).headers as Record<string, string>;
}

const params = (invitationId: string) => ({
  params: Promise.resolve({ invitationId }),
});

beforeEach(() => {
  cookieJar.clear();
  cookieJar.set(OPERATOR_COOKIE, 'OP');
  cookieJar.set(TENANT_COOKIE, 'acme-corp');
  vi.unstubAllGlobals();
});

describe('POST /api/operator-invitations (invite — § 2.4.3 row 11)', () => {
  it('forwards BOTH X-Operator-Reason + Idempotency-Key and the draft (no password) to the producer path', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...ITEM, auditId: 'a-1' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'person@example.com',
          displayName: '홍길동',
          roles: ['SUPPORT_LOCK'],
          tenantId: 'acme-corp',
          reason: '신규 입사',
          idempotencyKey: 'idem-1',
        }),
      }),
    );
    expect(res.status).toBe(201);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://iam.local/api/admin/operator-invitations');
    expect((init as RequestInit).method).toBe('POST');
    const h = headersOf(fetchMock);
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('신규 입사');
    expect(h['Idempotency-Key']).toBe('idem-1');
    expect(h['X-Tenant-Id']).toBe('acme-corp');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      email: 'person@example.com',
      displayName: '홍길동',
      roles: ['SUPPORT_LOCK'],
      tenantId: 'acme-corp',
    });
  });

  it('a FAILED_TRANSIENT delivery is still 201 with the delivery status (OD-4 — not an error)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          { ...ITEM, delivery: { status: 'FAILED_TRANSIENT', attemptedAt: 'x' }, auditId: 'a' },
          201,
        ),
      ),
    );
    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'person@example.com',
          displayName: '홍길동',
          roles: ['SUPPORT_LOCK'],
          tenantId: 'acme-corp',
          reason: 'r',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(201);
    expect((await res.json()).delivery.status).toBe('FAILED_TRANSIENT');
  });

  it("tenantId '*' → 422 without calling IAM (the platform scope is never invited)", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'p@example.com',
          displayName: 'P',
          roles: [],
          tenantId: '*',
          reason: 'r',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a password (or any unknown key) in the body → 422, never forwarded', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'p@example.com',
          displayName: 'P',
          roles: [],
          tenantId: 'acme-corp',
          password: 'Sup3r!secret',
          reason: 'r',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('an empty reason → 400 REASON_REQUIRED with no producer call (never fabricated)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'p@example.com',
          displayName: 'P',
          roles: [],
          tenantId: 'acme-corp',
          reason: '   ',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('REASON_REQUIRED');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 OPERATOR_INVITATION_ALREADY_PENDING passes through (inline)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'OPERATOR_INVITATION_ALREADY_PENDING', message: 'm' }, 409),
      ),
    );
    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'p@example.com',
          displayName: 'P',
          roles: [],
          tenantId: 'acme-corp',
          reason: 'r',
          idempotencyKey: 'k',
        }),
      }),
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('OPERATOR_INVITATION_ALREADY_PENDING');
  });

  it('🔴 a leaked token / link in the producer response is NOT relayed to the browser', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            ...ITEM,
            auditId: 'a',
            token: 'tok_secret',
            tokenHash: 'abc',
            link: 'https://auth.example/operator-invitations/accept?token=tok_secret',
          },
          201,
        ),
      ),
    );
    const res = await invitePOST(
      new Request('http://console.local/api/operator-invitations', {
        method: 'POST',
        body: JSON.stringify({
          email: 'p@example.com',
          displayName: 'P',
          roles: [],
          tenantId: 'acme-corp',
          reason: 'r',
          idempotencyKey: 'k',
        }),
      }),
    );
    const text = await res.text();
    expect(text).not.toContain('tok_secret');
    expect(text).not.toContain('tokenHash');
    expect(text).not.toContain('accept?token');
  });
});

describe('GET /api/operator-invitations (list — § 2.4.3 row 12)', () => {
  it('defaults tenantId to the active tenant and status to PENDING; no mutation headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ content: [ITEM], totalElements: 1, page: 0, size: 20, totalPages: 1 }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await listGET(new Request('http://console.local/api/operator-invitations'));
    expect(res.status).toBe(200);
    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.pathname).toBe('/api/admin/operator-invitations');
    expect(url.searchParams.get('tenantId')).toBe('acme-corp');
    expect(url.searchParams.get('status')).toBe('PENDING');
    const h = headersOf(fetchMock);
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('passes an explicit tenantId / status / page through', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ content: [], totalElements: 0, page: 1, size: 20, totalPages: 0 }),
    );
    vi.stubGlobal('fetch', fetchMock);
    await listGET(
      new Request(
        'http://console.local/api/operator-invitations?tenantId=globex&status=CANCELLED&page=1&size=20',
      ),
    );
    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('tenantId')).toBe('globex');
    expect(url.searchParams.get('status')).toBe('CANCELLED');
    expect(url.searchParams.get('page')).toBe('1');
  });

  it('503 → 503 (only the invitations section degrades)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)));
    const res = await listGET(new Request('http://console.local/api/operator-invitations'));
    expect(res.status).toBe(503);
  });

  it('401 → 401 (forced re-login)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)));
    const res = await listGET(new Request('http://console.local/api/operator-invitations'));
    expect(res.status).toBe(401);
  });
});

describe('POST cancel / resend (rows 13–14) — reason ONLY', () => {
  it('cancel → producer `{id}:cancel` with X-Operator-Reason and NO Idempotency-Key, no body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...ITEM, status: 'CANCELLED', cancelledAt: 'x' }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(
      new Request('http://console.local/api/operator-invitations/inv-1/cancel', {
        method: 'POST',
        body: JSON.stringify({ reason: '오입력' }),
      }),
      params('inv-1'),
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://iam.local/api/admin/operator-invitations/inv-1:cancel',
    );
    const h = headersOf(fetchMock);
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('오입력');
    expect(h['Idempotency-Key']).toBeUndefined();
    expect((fetchMock.mock.calls[0][1] as RequestInit).body).toBeUndefined();
  });

  it('resend → producer `{id}:resend` with X-Operator-Reason and NO Idempotency-Key; FAILED_* is a 200', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...ITEM, delivery: { status: 'FAILED_PERMANENT', attemptedAt: 'x' } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await resendPOST(
      new Request('http://console.local/api/operator-invitations/inv-1/resend', {
        method: 'POST',
        body: JSON.stringify({ reason: '재발송' }),
      }),
      params('inv-1'),
    );
    expect(res.status).toBe(200);
    expect((await res.json()).delivery.status).toBe('FAILED_PERMANENT');
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://iam.local/api/admin/operator-invitations/inv-1:resend',
    );
    expect(headersOf(fetchMock)['Idempotency-Key']).toBeUndefined();
  });

  it('an invitation id with path characters is encoded (the `:` method separator stays literal)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ITEM));
    vi.stubGlobal('fetch', fetchMock);
    await resendPOST(
      new Request('http://console.local/api/operator-invitations/x/resend', {
        method: 'POST',
        body: JSON.stringify({ reason: 'r' }),
      }),
      params('a/b'),
    );
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://iam.local/api/admin/operator-invitations/a%2Fb:resend',
    );
  });

  it('an idempotencyKey in the cancel body → 422 (strict body; the header matrix forbids it)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await cancelPOST(
      new Request('http://console.local/api/operator-invitations/inv-1/cancel', {
        method: 'POST',
        body: JSON.stringify({ reason: 'r', idempotencyKey: 'k' }),
      }),
      params('inv-1'),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 OPERATOR_INVITATION_NOT_PENDING / 404 OPERATOR_INVITATION_NOT_FOUND pass through', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(jsonResponse({ code: 'OPERATOR_INVITATION_NOT_PENDING' }, 409))
        .mockResolvedValueOnce(jsonResponse({ code: 'OPERATOR_INVITATION_NOT_FOUND' }, 404)),
    );
    const a = await cancelPOST(
      new Request('http://console.local/api/operator-invitations/inv-1/cancel', {
        method: 'POST',
        body: JSON.stringify({ reason: 'r' }),
      }),
      params('inv-1'),
    );
    expect(a.status).toBe(409);
    expect((await a.json()).code).toBe('OPERATOR_INVITATION_NOT_PENDING');
    const b = await resendPOST(
      new Request('http://console.local/api/operator-invitations/inv-1/resend', {
        method: 'POST',
        body: JSON.stringify({ reason: 'r' }),
      }),
      params('inv-1'),
    );
    expect(b.status).toBe(404);
    expect((await b.json()).code).toBe('OPERATOR_INVITATION_NOT_FOUND');
  });
});
