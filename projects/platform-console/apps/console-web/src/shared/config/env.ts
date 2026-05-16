import { z } from 'zod';

/**
 * Typed environment accessor for console-web.
 *
 * - Client env: only `NEXT_PUBLIC_`-prefixed values, safe to ship to browser.
 * - Server env: OIDC public-client config + the GAP product/tenant registry
 *   URL. Validated at access time (server runtime only — never serialised to
 *   the client), per `platform/service-types/frontend-app.md`
 *   § Environment Variables (server-only secrets injected at runtime, not
 *   build time; build artifacts must work across environments without rebuild).
 *
 * The GAP OIDC client is a PUBLIC client (`platform-console-web`,
 * Authorization Code + PKCE, no client secret — auth-service
 * V0015 seed / ADR-003 public-client lineage), so there is no
 * `OIDC_CLIENT_SECRET`.
 *
 * `CONSOLE_REGISTRY_URL` points at the authoritative TASK-BE-296 producer
 * path: `http://gap.local/api/admin/console/registry` (admin-service,
 * operator-auth boundary — `console-registry-api.md`).
 *
 * `CONSOLE_TOKEN_EXCHANGE_URL` points at the authoritative TASK-BE-298
 * producer path: `http://gap.local/api/admin/auth/token-exchange`
 * (admin-service, RFC 8693 — `admin-api.md` / ADR-MONO-014). The console
 * server-side exchanges the GAP OIDC access token for an operator token
 * here (console-integration-contract § 2.6).
 */

// ---------------------------------------------------------------------------
// Client env (safe to send to browser — NEXT_PUBLIC_ prefix only)
// ---------------------------------------------------------------------------

const ClientEnvSchema = z.object({
  NEXT_PUBLIC_APP_URL: z.string().url().default('http://console.local'),
});

export const clientEnv = ClientEnvSchema.parse({
  NEXT_PUBLIC_APP_URL: process.env.NEXT_PUBLIC_APP_URL,
});

// ---------------------------------------------------------------------------
// Server env (server runtime only — never serialised to the client)
// ---------------------------------------------------------------------------

const ServerEnvSchema = z.object({
  /** GAP OIDC issuer base (e.g. http://gap.local). OAuth2/OIDC endpoints are
   *  `${OIDC_ISSUER_URL}/oauth2/{authorize,token,revoke}` (auth-api.md). */
  OIDC_ISSUER_URL: z.string().url(),
  /** Public client id registered by TASK-BE-296 (V0015 seed). */
  OIDC_CLIENT_ID: z.string().min(1).default('platform-console-web'),
  /** Exact pre-registered redirect URI (no wildcard). */
  OIDC_REDIRECT_URI: z.string().url(),
  /** OIDC scopes — must be a subset of the V0015-seeded client scopes
   *  (`openid profile email tenant.read`). */
  OIDC_SCOPE: z.string().min(1).default('openid profile email tenant.read'),
  /** GAP product/tenant registry surface (TASK-BE-296 authoritative path). */
  CONSOLE_REGISTRY_URL: z
    .string()
    .url()
    .default('http://gap.local/api/admin/console/registry'),
  /** Outbound timeout (ms) for the registry call (integration-heavy I1). */
  REGISTRY_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** GAP admin-service RFC 8693 operator-token exchange endpoint
   *  (TASK-BE-298 / ADR-MONO-014 authoritative path — admin-api.md). */
  CONSOLE_TOKEN_EXCHANGE_URL: z
    .string()
    .url()
    .default('http://gap.local/api/admin/auth/token-exchange'),
  /** Outbound timeout (ms) for the operator-token exchange call
   *  (integration-heavy I1 — same convention as REGISTRY_TIMEOUT_MS). */
  TOKEN_EXCHANGE_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
  NEXT_PUBLIC_APP_URL: z.string().url().default('http://console.local'),
});

export type ServerEnv = z.infer<typeof ServerEnvSchema>;

/**
 * Returns validated server-side environment.
 * Call only from Server Components, Route Handlers, or Middleware.
 * Throws if a required variable is missing/invalid.
 */
export function getServerEnv(): ServerEnv {
  return ServerEnvSchema.parse({
    OIDC_ISSUER_URL: process.env.OIDC_ISSUER_URL,
    OIDC_CLIENT_ID: process.env.OIDC_CLIENT_ID,
    OIDC_REDIRECT_URI: process.env.OIDC_REDIRECT_URI,
    OIDC_SCOPE: process.env.OIDC_SCOPE,
    CONSOLE_REGISTRY_URL: process.env.CONSOLE_REGISTRY_URL,
    REGISTRY_TIMEOUT_MS: process.env.REGISTRY_TIMEOUT_MS,
    CONSOLE_TOKEN_EXCHANGE_URL: process.env.CONSOLE_TOKEN_EXCHANGE_URL,
    TOKEN_EXCHANGE_TIMEOUT_MS: process.env.TOKEN_EXCHANGE_TIMEOUT_MS,
    LOG_LEVEL: process.env.LOG_LEVEL,
    NEXT_PUBLIC_APP_URL: process.env.NEXT_PUBLIC_APP_URL,
  });
}
