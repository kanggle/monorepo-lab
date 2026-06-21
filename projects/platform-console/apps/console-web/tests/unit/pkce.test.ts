import { describe, it, expect } from 'vitest';
import {
  generateCodeVerifier,
  deriveCodeChallenge,
  generateState,
} from '@/shared/lib/pkce';

/**
 * TASK-PC-FE-116 — PKCE (RFC 7636) + OAuth state primitives. These back the
 * OIDC Auth Code login flow; a regression (wrong digest, retained base64
 * padding, weak/short verifier) would silently break the console login. Uses
 * the real Web Crypto API (Node 20+ global webcrypto under vitest) — no mocks.
 */

const BASE64URL = /^[A-Za-z0-9_-]+$/;

describe('generateCodeVerifier', () => {
  it('is base64url with no padding', () => {
    const v = generateCodeVerifier();
    expect(v).toMatch(BASE64URL);
    expect(v).not.toContain('=');
    expect(v).not.toContain('+');
    expect(v).not.toContain('/');
  });

  it('is a high-entropy verifier of RFC 7636 §4.1 length (43–128 chars)', () => {
    const v = generateCodeVerifier();
    // 32 random bytes → 43 base64url chars (no padding).
    expect(v.length).toBeGreaterThanOrEqual(43);
    expect(v.length).toBeLessThanOrEqual(128);
  });

  it('produces a distinct value on each call', () => {
    expect(generateCodeVerifier()).not.toBe(generateCodeVerifier());
  });
});

describe('deriveCodeChallenge', () => {
  it('matches the RFC 7636 Appendix B known test vector (S256)', async () => {
    // RFC 7636 §B: BASE64URL(SHA256(verifier)).
    const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
    const expected = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM';
    await expect(deriveCodeChallenge(verifier)).resolves.toBe(expected);
  });

  it('is deterministic and base64url-encoded with no padding', async () => {
    const verifier = generateCodeVerifier();
    const a = await deriveCodeChallenge(verifier);
    const b = await deriveCodeChallenge(verifier);
    expect(a).toBe(b);
    expect(a).toMatch(BASE64URL);
    expect(a).not.toContain('=');
    // SHA-256 (32 bytes) → 43 base64url chars.
    expect(a.length).toBe(43);
  });
});

describe('generateState', () => {
  it('is a non-empty base64url anti-CSRF value', () => {
    const s = generateState();
    expect(s).toMatch(BASE64URL);
    expect(s.length).toBeGreaterThan(0);
    expect(s).not.toContain('=');
  });

  it('produces a distinct value on each call', () => {
    expect(generateState()).not.toBe(generateState());
  });
});
