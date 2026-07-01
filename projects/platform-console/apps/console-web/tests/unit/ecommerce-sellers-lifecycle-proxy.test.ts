import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops seller LIFECYCLE proxy route handlers
 * (TASK-PC-FE-154 — ADR-MONO-042 D3/D4): POST /{id}/{provision|suspend|close}.
 *   - Bodyless POST → producer 204 → the proxy returns 204.
 *   - Domain-facing IAM OIDC token (NOT the operator token); NO X-Tenant-Id / NO
 *     Idempotency-Key; targets ECOMMERCE_ADMIN_BASE_URL/sellers/{id}/{action}.
 *   - 403 → 403, 404 SELLER_NOT_FOUND → 404 passthrough, 503 → 503 (degrade),
 *     no IAM session → 401 (no upstream call).
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

import { POST as provisionPOST } from '@/app/api/ecommerce/sellers/[id]/provision/route';
import { POST as suspendPOST } from '@/app/api/ecommerce/sellers/[id]/suspend/route';
import { POST as closePOST } from '@/app/api/ecommerce/sellers/[id]/close/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function noContent() {
  return new Response(null, { status: 204 });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ROUTES = [
  { name: 'provision', handler: provisionPOST },
  { name: 'suspend', handler: suspendPOST },
  { name: 'close', handler: closePOST },
] as const;

function call(
  handler: (typeof ROUTES)[number]['handler'],
  id = 'acme-corp',
) {
  return handler(
    new Request(`http://console.local/api/ecommerce/sellers/${id}/x`, {
      method: 'POST',
    }),
    { params: Promise.resolve({ id }) },
  );
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

for (const { name, handler } of ROUTES) {
  describe(`POST /api/ecommerce/sellers/{id}/${name}`, () => {
    it('204 producer → 204, targets the admin lifecycle path, domain token, no X-Tenant-Id / Idempotency-Key', async () => {
      cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
      cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
      const fetchMock = vi.fn().mockResolvedValue(noContent());
      vi.stubGlobal('fetch', fetchMock);

      const res = await call(handler);
      expect(res.status).toBe(204);

      const [url, init] = fetchMock.mock.calls[0];
      expect(String(url)).toBe(
        `http://ecommerce.local/api/admin/sellers/acme-corp/${name}`,
      );
      expect((init as RequestInit).method).toBe('POST');
      // Bodyless POST.
      expect((init as RequestInit).body).toBeUndefined();
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
      expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
      expect(h['X-Tenant-Id']).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
    });

    it('encodes a non-ASCII sellerId exactly once (no StrictHttpFirewall double-encode)', async () => {
      cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
      const fetchMock = vi.fn().mockResolvedValue(noContent());
      vi.stubGlobal('fetch', fetchMock);

      await call(handler, '셀러 1');
      const [url] = fetchMock.mock.calls[0];
      // encodeURIComponent('셀러 1') once — never a %25 (double-encode).
      expect(String(url)).toContain(encodeURIComponent('셀러 1'));
      expect(String(url)).not.toContain('%25');
    });

    it('403 → 403', async () => {
      cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)),
      );
      const res = await call(handler);
      expect(res.status).toBe(403);
    });

    it('404 SELLER_NOT_FOUND → 404 passthrough', async () => {
      cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(ecomError('SELLER_NOT_FOUND', 404)),
      );
      const res = await call(handler, 'nope');
      expect(res.status).toBe(404);
      const body = await res.json();
      expect(body.code).toBe('SELLER_NOT_FOUND');
    });

    it('503 → 503 (section degrades only)', async () => {
      cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
      );
      const res = await call(handler);
      expect(res.status).toBe(503);
    });

    it('no IAM session → 401 (no upstream call)', async () => {
      const fetchMock = vi.fn();
      vi.stubGlobal('fetch', fetchMock);
      const res = await call(handler);
      expect(res.status).toBe(401);
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });
}
