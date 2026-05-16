/**
 * PKCE (RFC 7636) + OAuth state helpers for the GAP Authorization Code flow.
 *
 * The console is a PUBLIC OIDC client (`platform-console-web`, no client
 * secret — auth-service V0015 seed, `settings.client.require-proof-key=true`).
 * The `code_verifier` and `state` are generated server-side in the
 * `/api/auth/login` route handler, stored in short-lived HttpOnly cookies, and
 * consumed/cleared in `/api/auth/callback`. They are NEVER exposed to client
 * JavaScript (frontend-app.md § Authentication; architecture.md § Auth Flow).
 *
 * Uses the Web Crypto API (available in the Next.js node runtime).
 */

function base64UrlEncode(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let str = '';
  for (const b of view) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomBytes(len: number): Uint8Array {
  const arr = new Uint8Array(len);
  crypto.getRandomValues(arr);
  return arr;
}

/** RFC 7636 § 4.1 — 43–128 char high-entropy verifier. */
export function generateCodeVerifier(): string {
  return base64UrlEncode(randomBytes(32));
}

/** RFC 7636 § 4.2 — S256 challenge = BASE64URL(SHA256(verifier)). */
export async function deriveCodeChallenge(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(digest);
}

/** Opaque anti-CSRF state value bound to the auth request. */
export function generateState(): string {
  return base64UrlEncode(randomBytes(16));
}
