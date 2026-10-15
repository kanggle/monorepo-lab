/**
 * TASK-MONO-773 S6 — console-integration-contract § 2.6.4:
 *   - «`/onboarding` stays as an address: `GET /onboarding` → `307 → /new-tenant` for every visitor (the
 *     `(console)` guard then decides).» (AC-5 — a direct visit lands on the new screen)
 *   - the non-operator shell is a private session view → `Cache-Control: no-store` like the operator shell
 *     (the middleware is the one place that reads the request per visit — TASK-PC-FE-299).
 */
import { describe, it, expect } from 'vitest';
import { NextRequest } from 'next/server';
import { middleware } from '@/middleware';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const MARKER = 'console_non_operator';

function request(path: string, cookies: Record<string, string> = {}): NextRequest {
  const cookie = Object.entries(cookies)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
  return new NextRequest(`http://console.local${path}`, {
    headers: cookie ? { cookie } : undefined,
  });
}

describe('GET /onboarding → 307 /new-tenant (every visitor)', () => {
  it.each([
    ['no cookies (sample visitor)', {}],
    ['non-operator session', { [ACCESS_COOKIE]: 'a', [MARKER]: '1' }],
    ['operator session', { [ACCESS_COOKIE]: 'a', [OPERATOR_COOKIE]: 'o' }],
  ])('🔴 %s', (_label, cookies) => {
    const res = middleware(request('/onboarding', cookies));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toBe('http://console.local/new-tenant');
  });

  it('🔴 a query string on the old address does not change the landing', () => {
    const res = middleware(request('/onboarding?from=mail'));
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toBe('http://console.local/new-tenant');
  });

  it('🔵 control — /new-tenant itself is not redirected by the middleware', () => {
    const res = middleware(request('/new-tenant', { [ACCESS_COOKIE]: 'a', [MARKER]: '1' }));
    expect(res.headers.get('location')).toBeNull();
  });

  it('🔵 control — a path that merely starts with the letters is untouched', () => {
    const res = middleware(request('/onboardingx'));
    expect(res.headers.get('location')).toBeNull();
  });
});

describe('Cache-Control for the non-operator shell', () => {
  it('🔴 access + marker → no-store, private', () => {
    const res = middleware(request('/new-tenant', { [ACCESS_COOKIE]: 'a', [MARKER]: '1' }));
    expect(res.headers.get('Cache-Control')).toBe('no-store, private');
  });

  it('🔵 control — marker without access → nothing added (not a session)', () => {
    const res = middleware(request('/new-tenant', { [MARKER]: '1' }));
    expect(res.headers.get('Cache-Control')).toBeNull();
  });
});
