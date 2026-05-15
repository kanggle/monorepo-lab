/**
 * Typed environment accessor for console-web.
 *
 * Pattern mirrors admin-web's env.ts (zod-validated at module load time).
 * Server-only secrets (OIDC_*) are validated in getServerEnv() — never
 * exposed as NEXT_PUBLIC_ per frontend-app.md § Environment Variables.
 *
 * TODO(TASK-PC-FE-001): wire clientEnv / getServerEnv() into the real OIDC
 * Auth Code + PKCE flow and BFF request pipeline.
 */

// ---------------------------------------------------------------------------
// Client env (safe to send to browser — NEXT_PUBLIC_ prefix only)
// ---------------------------------------------------------------------------

const NEXT_PUBLIC_APP_URL =
  process.env.NEXT_PUBLIC_APP_URL ?? 'http://console.local';

export const clientEnv = {
  NEXT_PUBLIC_APP_URL,
} as const;

// ---------------------------------------------------------------------------
// Server env (server runtime only — never serialised to the client)
// ---------------------------------------------------------------------------

/**
 * Returns validated server-side environment.
 * Call only from Server Components, Route Handlers, or Middleware.
 * Throws at startup if a required variable is missing.
 */
export function getServerEnv() {
  const OIDC_ISSUER_URL = process.env.OIDC_ISSUER_URL;
  const OIDC_CLIENT_ID = process.env.OIDC_CLIENT_ID;
  const OIDC_REDIRECT_URI = process.env.OIDC_REDIRECT_URI;
  const CONSOLE_REGISTRY_URL = process.env.CONSOLE_REGISTRY_URL;

  if (!OIDC_ISSUER_URL) throw new Error('OIDC_ISSUER_URL is required');
  if (!OIDC_CLIENT_ID) throw new Error('OIDC_CLIENT_ID is required');
  if (!OIDC_REDIRECT_URI) throw new Error('OIDC_REDIRECT_URI is required');
  if (!CONSOLE_REGISTRY_URL) throw new Error('CONSOLE_REGISTRY_URL is required');

  return {
    OIDC_ISSUER_URL,
    OIDC_CLIENT_ID,
    OIDC_REDIRECT_URI,
    CONSOLE_REGISTRY_URL,
    NEXT_PUBLIC_APP_URL,
  } as const;
}
