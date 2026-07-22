import { describe, it, expect } from 'vitest';
import { sanitizeReturnPath } from '@/shared/lib/return-path';

/**
 * TASK-PC-FE-253 — the single same-site return-path predicate shared by the
 * login page and the login route. Same case set as the route consumer asserts
 * in auth-routes.test.ts (they call the identical function, so the page can
 * never diverge from the route on redirect safety).
 */
describe('sanitizeReturnPath (open-redirect guard)', () => {
  it('passes through a normal same-site absolute path', () => {
    expect(sanitizeReturnPath('/dashboards/overview')).toBe(
      '/dashboards/overview',
    );
    expect(sanitizeReturnPath('/console/wms?page=2&sort=asc')).toBe(
      '/console/wms?page=2&sort=asc',
    );
  });

  it('passes through the bare root', () => {
    expect(sanitizeReturnPath('/')).toBe('/');
  });

  it('downgrades a protocol-relative // authority to /', () => {
    expect(sanitizeReturnPath('//evil.com')).toBe('/');
  });

  it('downgrades the backslash-normalised /\\ protocol-relative form to /', () => {
    // A browser folds `\` → `/`, so `/\evil.com` reaches the network as
    // `//evil.com`. It must not survive the guard.
    expect(sanitizeReturnPath('/\\evil.com')).toBe('/');
  });

  it('downgrades an absolute URL to /', () => {
    expect(sanitizeReturnPath('https://evil.com')).toBe('/');
    expect(sanitizeReturnPath('http://evil.com/steal')).toBe('/');
  });

  it('downgrades an authority-only value (no leading slash) to /', () => {
    expect(sanitizeReturnPath('evil.com')).toBe('/');
  });

  it('downgrades empty / missing values to /', () => {
    expect(sanitizeReturnPath('')).toBe('/');
    expect(sanitizeReturnPath(null)).toBe('/');
    expect(sanitizeReturnPath(undefined)).toBe('/');
  });
});
