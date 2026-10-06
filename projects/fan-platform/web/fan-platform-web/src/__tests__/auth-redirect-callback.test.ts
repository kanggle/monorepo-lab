/**
 * TASK-FAN-FE-031 — the header logout must be allowed to leave for the IdP
 * `end_session`, and nothing else cross-origin.
 */
import { describe, it, expect } from 'vitest';
import { redirectCallback } from '@/shared/auth/auth-callbacks';

const baseUrl = 'https://fan.hubwang.com';
const issuer = 'https://auth.hubwang.com';
const cb = (url: string, iss = issuer) => redirectCallback({ url, baseUrl }, iss);

describe('redirectCallback (TASK-FAN-FE-031)', () => {
  it('lets the IdP end_session URL through unchanged (the fix)', () => {
    const endSession = `${issuer}/connect/logout?id_token_hint=x&post_logout_redirect_uri=${encodeURIComponent(baseUrl + '/')}&client_id=c`;
    expect(cb(endSession)).toBe(endSession);
  });

  it('honours an issuer with a path prefix', () => {
    const iss = 'https://idp.example.com/realm';
    expect(cb(`${iss}/connect/logout?id_token_hint=x`, iss)).toBe(`${iss}/connect/logout?id_token_hint=x`);
  });

  it('keeps the default behaviour for relative and same-origin URLs (control)', () => {
    expect(cb('/login')).toBe(`${baseUrl}/login`);
    expect(cb(`${baseUrl}/me`)).toBe(`${baseUrl}/me`);
  });

  it('still refuses any other cross-origin URL — not an open redirect', () => {
    expect(cb('https://evil.example.com/connect/logout')).toBe(baseUrl);
    expect(cb(`${issuer}/oauth2/authorize?x=1`)).toBe(baseUrl);
    expect(cb(`${issuer}/connect/logout/../admin`)).toBe(baseUrl);
    expect(cb('not a url')).toBe(baseUrl);
  });
});
