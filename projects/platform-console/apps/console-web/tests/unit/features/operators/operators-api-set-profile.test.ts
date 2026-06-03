import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operators/api/operators-api.ts` `setOperatorProfile(...)` —
 * TASK-PC-FE-017 call-shape contract + AC-7 header-matrix-drift defense.
 *
 *   - (a) `setOperatorProfile("op-target", "uuid-7", "reason")` →
 *         method=PATCH, path `/api/admin/operators/op-target/profile`,
 *         body `{ operatorContext: { defaultAccountId: "uuid-7" } }`,
 *         `X-Operator-Reason: reason` present,
 *         **NO `Idempotency-Key` header** (producer matrix non-uniformity
 *         mirror of `/roles` + `/status`).
 *   - (b) `setOperatorProfile("op-target", null, "clear it")` →
 *         body `{ operatorContext: { defaultAccountId: null } }`
 *         (explicit `null` — clear semantics; the producer rejects
 *         empty string).
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { setOperatorProfile } from '@/features/operators/api/operators-api';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';

function noContent() {
  return new Response(null, { status: 204 });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('operators-api setOperatorProfile — call shape + AC-7 header-matrix-drift defense', () => {
  it('(a) string value → PATCH {id}/profile, reason header present, NO Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await setOperatorProfile('op-target', 'uuid-7', 'onboarding');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/admin/operators/op-target/profile',
    );
    expect((init as RequestInit).method).toBe('PATCH');

    const h = (init as RequestInit).headers as Record<string, string>;
    // Operator token bearer, NOT the GAP OIDC token (#569 invariant).
    expect(h.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-ACCESS-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('wms');
    // Per-endpoint header matrix row 7 — reason ONLY.
    expect(h['X-Operator-Reason']).toBe('onboarding');
    // AC-7 header-matrix-drift defense — Idempotency-Key MUST NOT be sent
    // (producer matrix mirrors /roles + /status non-uniformity). Absence
    // pinned via both the `undefined` check AND the explicit
    // `'Idempotency-Key' in h` falsity check.
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);

    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({
      operatorContext: { defaultAccountId: 'uuid-7' },
    });
  });

  it('(b) explicit null → body { operatorContext: { defaultAccountId: null } } (NOT "")', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await setOperatorProfile('op-target', null, 'clear it');

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('PATCH');

    const h = (init as RequestInit).headers as Record<string, string>;
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('clear it'); // TASK-MONO-176: encoded on wire
    // AC-7 header-matrix-drift defense holds for the `null` case too.
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('Idempotency-Key' in h).toBe(false);

    const rawBody = (init as RequestInit).body as string;
    // Producer rejects empty string — assert we sent JSON null literal.
    expect(rawBody).toContain('"defaultAccountId":null');
    const body = JSON.parse(rawBody);
    expect(body).toEqual({
      operatorContext: { defaultAccountId: null },
    });
  });

  it('operatorId is URL-encoded (defence against operatorId containing path-traversal chars)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await setOperatorProfile('op/../evil', 'uuid-7', 'r');

    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      `/api/admin/operators/${encodeURIComponent('op/../evil')}/profile`,
    );
    expect(String(url)).not.toContain('op/../evil/profile');
  });
});
