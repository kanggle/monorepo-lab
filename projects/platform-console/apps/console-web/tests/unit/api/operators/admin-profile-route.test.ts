import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin admin-on-behalf-of profile-edit proxy
 * (`PATCH /api/admin/operators/{operatorId}/profile` — TASK-PC-FE-017):
 *
 *   - (a) POST `{ defaultAccountId: "<uuid>", reason: "<r>" }` → 204
 *     (forwards to IAM `PATCH /api/admin/operators/{id}/profile` with
 *     body `{ operatorContext: { defaultAccountId } }` + `X-Operator-Reason`
 *     ONLY — NO `Idempotency-Key` per the producer matrix);
 *   - (b) POST missing `reason` → 422 VALIDATION_ERROR (proxy zod fails
 *     first; IAM not called);
 *   - (c) downstream throws (IAM returns 503) → 503 via `mapError`.
 *
 * Plus: explicit `null` clears; unknown top-level keys rejected
 * (`.strict()` mirror of FAIL_ON_UNKNOWN_PROPERTIES); over-36-char value
 * rejected; self-via-this-path producer 400 surfaces as 400 passthrough.
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..017 lane).
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { POST as adminProfilePOST } from '@/app/api/operators/[operatorId]/profile/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function noContent() {
  return new Response(null, { status: 204 });
}

function call(operatorId: string, body: unknown) {
  return adminProfilePOST(
    new Request(
      `http://console.local/api/operators/${operatorId}/profile`,
      {
        method: 'POST',
        body: JSON.stringify(body),
      },
    ),
    { params: Promise.resolve({ operatorId }) },
  );
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('POST /api/operators/[operatorId]/profile proxy (a) — valid body → 204', () => {
  it('forwards to IAM {id}/profile with reason header + NO Idempotency-Key, returns 204', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
      reason: 'onboarding',
    });

    expect(res.status).toBe(204);

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/admin/operators/op-target/profile',
    );
    expect((init as RequestInit).method).toBe('PATCH');
    const h = (init as RequestInit).headers as Record<string, string>;
    // Per-endpoint header matrix row 7 — reason ONLY, NO idempotency.
    expect(h['X-Operator-Reason']).toBe('onboarding');
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);

    // Body shape on the IAM wire mirrors me/profile verbatim.
    const sentBody = JSON.parse((init as RequestInit).body as string);
    expect(sentBody).toEqual({
      operatorContext: {
        defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
      },
    });
  });

  it('explicit null clears the column (body carries JSON null, not "")', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: null,
      reason: 'clearing per ticket',
    });

    expect(res.status).toBe(204);
    const [, init] = fetchMock.mock.calls[0];
    const raw = (init as RequestInit).body as string;
    expect(raw).toContain('"defaultAccountId":null');
    const body = JSON.parse(raw);
    expect(body).toEqual({
      operatorContext: { defaultAccountId: null },
    });
  });
});

describe('POST /api/operators/[operatorId]/profile proxy (b) — malformed body → 422', () => {
  it('missing reason → 422 VALIDATION_ERROR, NO fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: 'uuid-7',
    });

    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('whitespace-only reason → 422 (zod `.trim().min(1)`)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: 'uuid-7',
      reason: '   ',
    });

    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('over-36-char defaultAccountId → 422', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: 'x'.repeat(37),
      reason: 'r',
    });

    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('unknown top-level key → 422 (`.strict()` mirror of FAIL_ON_UNKNOWN_PROPERTIES)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: 'uuid-7',
      reason: 'r',
      extra: 'forbidden',
    });

    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/operators/[operatorId]/profile proxy (c) — downstream error mapping', () => {
  it('IAM 503 CIRCUIT_OPEN → proxy 503 (operators section degrades only)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );

    const res = await call('op-target', {
      defaultAccountId: 'uuid-7',
      reason: 'r',
    });

    expect(res.status).toBe(503);
  });

  it('IAM 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH → proxy 400 (passthrough)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse(
            { code: 'SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH' },
            400,
          ),
        ),
    );

    const res = await call('op-self', {
      defaultAccountId: 'uuid-7',
      reason: 'r',
    });

    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe(
      'SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH',
    );
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; NO fetch)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await call('op-target', {
      defaultAccountId: 'uuid-7',
      reason: 'r',
    });

    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
