/**
 * `src/middleware.ts` — `Cache-Control: no-store` (TASK-PC-FE-299 AC-1).
 *
 * The route this middleware guards is shared, byte-for-byte, by an
 * authenticated operator AND a sample visitor (ADR-MONO-074 — same path,
 * `(console)/layout.tsx` gates on `isSampleVisitor() || isAuthenticated()`),
 * so `next.config.mjs` `headers()` (purely path-based) cannot tell them
 * apart. This suite pins that the middleware — the one place that reads the
 * request per-visit — adds `no-store` ONLY when both session cookies
 * `isAuthenticated()` requires are present, mirroring that predicate exactly.
 */

import { describe, it, expect } from 'vitest';
import { NextRequest } from 'next/server';
import { middleware } from '@/middleware';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function requestWithCookies(cookiePairs: Record<string, string>): NextRequest {
  const cookieHeader = Object.entries(cookiePairs)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
  return new NextRequest('http://console.local/dashboards/overview', {
    headers: cookieHeader ? { cookie: cookieHeader } : undefined,
  });
}

describe('middleware Cache-Control (TASK-PC-FE-299 AC-1)', () => {
  it('🔴 both session cookies present (authenticated) → no-store, private', () => {
    const res = middleware(
      requestWithCookies({ [ACCESS_COOKIE]: 'a', [OPERATOR_COOKIE]: 'b' }),
    );
    expect(res.headers.get('Cache-Control')).toBe('no-store, private');
  });

  it('🔵 no cookies at all (sample visitor, ADR-MONO-074) → no Cache-Control added', () => {
    const res = middleware(requestWithCookies({}));
    expect(res.headers.get('Cache-Control')).toBeNull();
  });

  it('🔵 access cookie only (half session, pre-operator) → no Cache-Control added', () => {
    const res = middleware(requestWithCookies({ [ACCESS_COOKIE]: 'a' }));
    expect(res.headers.get('Cache-Control')).toBeNull();
  });

  it('🔵 operator cookie only (never happens by construction, but the predicate is exact) → no Cache-Control added', () => {
    const res = middleware(requestWithCookies({ [OPERATOR_COOKIE]: 'b' }));
    expect(res.headers.get('Cache-Control')).toBeNull();
  });

  it('🔵 still injects x-pathname regardless (existing behaviour unchanged)', () => {
    const res = middleware(
      requestWithCookies({ [ACCESS_COOKIE]: 'a', [OPERATOR_COOKIE]: 'b' }),
    );
    expect(res.headers.get('x-middleware-request-x-pathname')).toBe(
      '/dashboards/overview',
    );
  });
});
