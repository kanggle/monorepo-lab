import { describe, it, expect } from 'vitest';

/**
 * Unit tests for the layout guard `buildLoginRedirect` sanitisation logic
 * (Gap D / F6 — TASK-PC-FE-115).
 *
 * The function lives in `app/(console)/layout.tsx` (a server component) and
 * reads `x-pathname` from `next/headers`. We test the sanitisation rules in
 * isolation here to keep the test in the vitest jsdom environment.
 */

/** Mirrors the sanitisation logic in layout.tsx `buildLoginRedirect()`. */
function buildLoginRedirectFrom(raw: string | null): string {
  const isSameSite =
    raw !== null &&
    raw.startsWith('/') &&
    !raw.startsWith('//') &&
    !raw.startsWith('/login') &&
    !raw.startsWith('/api/');
  if (!isSameSite) return '/login';
  return `/login?redirect=${encodeURIComponent(raw)}`;
}

describe('layout guard buildLoginRedirect sanitisation (Gap D / F6)', () => {
  it('returns bare /login when x-pathname header is absent (null)', () => {
    expect(buildLoginRedirectFrom(null)).toBe('/login');
  });

  it('includes the ?redirect param for a normal console path', () => {
    const result = buildLoginRedirectFrom('/console/ecommerce/orders');
    expect(result).toBe(
      `/login?redirect=${encodeURIComponent('/console/ecommerce/orders')}`,
    );
  });

  it('preserves search params in the redirect destination', () => {
    const path = '/console/wms?page=2&sort=asc';
    expect(buildLoginRedirectFrom(path)).toBe(
      `/login?redirect=${encodeURIComponent(path)}`,
    );
  });

  it('rejects open-redirect via // prefix', () => {
    expect(buildLoginRedirectFrom('//evil.example')).toBe('/login');
  });

  it('rejects absolute URL (http://)', () => {
    expect(buildLoginRedirectFrom('http://evil.example/steal')).toBe('/login');
  });

  it('rejects absolute URL (https://)', () => {
    expect(buildLoginRedirectFrom('https://evil.example')).toBe('/login');
  });

  it('rejects /login as a redirect target (self-redirect loop)', () => {
    expect(buildLoginRedirectFrom('/login')).toBe('/login');
    expect(buildLoginRedirectFrom('/login?error=state_mismatch')).toBe('/login');
  });

  it('rejects /api/** paths (not a page destination)', () => {
    expect(buildLoginRedirectFrom('/api/auth/callback')).toBe('/login');
    expect(buildLoginRedirectFrom('/api/auth/refresh')).toBe('/login');
  });

  it('encodes special characters in the path correctly', () => {
    const path = '/console/search?q=hello world';
    const result = buildLoginRedirectFrom(path);
    expect(result).toContain('redirect=');
    // The encoded value must be decodable back to the original path.
    const encoded = result.split('redirect=')[1];
    expect(decodeURIComponent(encoded)).toBe(path);
  });

  it('?redirect param name matches what login/route.ts reads (searchParams.get("redirect"))', () => {
    const result = buildLoginRedirectFrom('/console/overview');
    expect(result).toMatch(/[?&]redirect=/);
  });
});
