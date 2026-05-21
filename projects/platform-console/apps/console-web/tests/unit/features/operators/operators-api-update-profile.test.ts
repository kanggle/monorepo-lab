import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operators/api/operators-api.ts` `updateOwnProfile(...)` —
 * TASK-PC-FE-016 call-shape contract.
 *
 *   - (a) `updateOwnProfile({ defaultAccountId: "uuid-7" })` →
 *         method=PATCH, path `/api/admin/operators/me/profile`,
 *         body `{ operatorContext: { defaultAccountId: "uuid-7" } }`,
 *         NO `X-Operator-Reason`, NO `Idempotency-Key`.
 *   - (b) `updateOwnProfile({ defaultAccountId: null })` →
 *         body `{ operatorContext: { defaultAccountId: null } }`
 *         (explicit `null` — clear semantics; the producer rejects
 *         empty string).
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..010 lane).
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

import { updateOwnProfile } from '@/features/operators/api/operators-api';
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

describe('operators-api updateOwnProfile — call shape (TASK-PC-FE-016)', () => {
  it('(a) string value → PATCH me/profile with correct body, no reason / no key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await updateOwnProfile({ defaultAccountId: 'uuid-7' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/me/profile');
    expect((init as RequestInit).method).toBe('PATCH');
    const h = (init as RequestInit).headers as Record<string, string>;
    // Operator token bearer, NOT the GAP OIDC token (#569 invariant).
    expect(h.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-ACCESS-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('wms');
    // Per-endpoint header matrix row 6 — NO reason, NO idem on self path.
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect('X-Operator-Reason' in h).toBe(false);
    expect('Idempotency-Key' in h).toBe(false);

    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({
      operatorContext: { defaultAccountId: 'uuid-7' },
    });
  });

  it('(b) explicit null clears the column (body carries JSON null, NOT empty string)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await updateOwnProfile({ defaultAccountId: null });

    const [, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('PATCH');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();

    const rawBody = (init as RequestInit).body as string;
    // Producer rejects empty string — assert we sent JSON null literal.
    expect(rawBody).toContain('"defaultAccountId":null');
    const body = JSON.parse(rawBody);
    expect(body).toEqual({
      operatorContext: { defaultAccountId: null },
    });
  });
});
