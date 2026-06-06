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
 * path: `http://iam.local/api/admin/console/registry` (admin-service,
 * operator-auth boundary — `console-registry-api.md`).
 *
 * `CONSOLE_TOKEN_EXCHANGE_URL` points at the authoritative TASK-BE-298
 * producer path: `http://iam.local/api/admin/auth/token-exchange`
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
  /** GAP OIDC issuer base (e.g. http://iam.local). OAuth2/OIDC endpoints are
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
    .default('http://iam.local/api/admin/console/registry'),
  /** Outbound timeout (ms) for the registry call (integration-heavy I1). */
  REGISTRY_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** GAP admin-service RFC 8693 operator-token exchange endpoint
   *  (TASK-BE-298 / ADR-MONO-014 authoritative path — admin-api.md). */
  CONSOLE_TOKEN_EXCHANGE_URL: z
    .string()
    .url()
    .default('http://iam.local/api/admin/auth/token-exchange'),
  /** Outbound timeout (ms) for the operator-token exchange call
   *  (integration-heavy I1 — same convention as REGISTRY_TIMEOUT_MS). */
  TOKEN_EXCHANGE_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** GAP admin-service base for the accounts operator surface (TASK-PC-FE-002
   *  / TASK-BE-296 operator-auth boundary). The 8 account/session endpoints
   *  hang off `${IAM_ADMIN_API_BASE}/api/admin/...` — request/response/error
   *  owned by GAP `admin-api.md` (authoritative, consumed only). */
  IAM_ADMIN_API_BASE: z.string().url().default('http://iam.local'),
  /** Outbound timeout (ms) for GAP accounts calls (integration-heavy I1 —
   *  same convention as REGISTRY_TIMEOUT_MS). */
  ACCOUNTS_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** Outbound timeout (ms) for the GAP unified audit read call
   *  (TASK-PC-FE-003 / integration-heavy I1 — same convention as
   *  ACCOUNTS_TIMEOUT_MS). The audit endpoint is `GET
   *  ${IAM_ADMIN_API_BASE}/api/admin/audit` — request/response/error owned
   *  by GAP `admin-api.md` (authoritative, consumed only, read-only). */
  AUDIT_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** Outbound timeout (ms) for GAP operators-management calls
   *  (TASK-PC-FE-004 / integration-heavy I1 — same convention as
   *  ACCOUNTS_TIMEOUT_MS). The 5 operator endpoints hang off
   *  `${IAM_ADMIN_API_BASE}/api/admin/operators...` — request/response/
   *  per-endpoint headers/error owned by GAP `admin-api.md` (authoritative,
   *  consumed only). */
  OPERATORS_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** wms `admin-service` base for the operations surface (TASK-PC-FE-007 /
   *  § 2.4.5). The dashboard read-model + alert-ack endpoints hang off
   *  `${WMS_ADMIN_BASE_URL}/dashboard/...` (+ `/operations/...`) —
   *  request/response/error owned by wms `admin-service-api.md`
   *  (authoritative, consumed only). Aligned with the registry `baseRoute`
   *  for `productKey=wms`; the wms gateway hostname is `wms.local`. NOTE:
   *  unlike the GAP surface this is reached with the GAP OIDC access token
   *  directly (the wms gateway requires it — § 2.4.5 per-domain credential
   *  divergence; the #569 invariant is GAP-domain-scoped). */
  WMS_ADMIN_BASE_URL: z
    .string()
    .url()
    .default('http://wms.local/api/v1/admin'),
  /** Outbound timeout (ms) for wms operations calls (integration-heavy I1 —
   *  same convention as ACCOUNTS_TIMEOUT_MS). */
  WMS_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** scm gateway base for the operations surface (TASK-PC-FE-008 /
   *  § 2.4.6). The read-only procurement-PO + inventory-visibility
   *  endpoints hang off `${SCM_GATEWAY_BASE_URL}/api/v1/procurement/...`
   *  and `.../api/v1/inventory-visibility/...` — request/response/error
   *  owned by scm `procurement-api.md` / `inventory-visibility-api.md`
   *  (authoritative, consumed read-only). Aligned with the registry
   *  `baseRoute` for `productKey=scm`; the scm gateway hostname is
   *  `scm.local`. NOTE: like wms (NOT GAP) this is reached with the GAP
   *  OIDC access token DIRECTLY — the § 2.4.5 per-domain credential rule
   *  reused (the #569 invariant is GAP-domain-scoped; scm's gateway
   *  validates the GAP RS256 token + `tenant_id ∈ {scm,*}` claim
   *  producer-side per TASK-SCM-BE-015). */
  SCM_GATEWAY_BASE_URL: z.string().url().default('http://scm.local'),
  /** Outbound timeout (ms) for scm operations calls (integration-heavy I1 —
   *  same convention as WMS_TIMEOUT_MS). */
  SCM_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** finance `account-service` base for the operations surface
   *  (TASK-PC-FE-009 / § 2.4.7). The read-only account + balances +
   *  transactions endpoints hang off `${FINANCE_BASE_URL}/api/finance/...`
   *  — request/response/error owned by finance `account-api.md`
   *  (authoritative, consumed read-only). Aligned with the registry
   *  `baseRoute` for `productKey=finance`; the finance hostname is
   *  `finance.local`. NOTE: like wms + scm (NOT GAP) this is reached
   *  with the GAP OIDC access token DIRECTLY — the § 2.4.5 per-domain
   *  credential rule reused (the #569 invariant is GAP-domain-scoped;
   *  finance validates the GAP RS256 token + `tenant_id ∈ {finance,*}`
   *  claim producer-side per TASK-FIN-BE-005). */
  FINANCE_BASE_URL: z.string().url().default('http://finance.local'),
  /** Outbound timeout (ms) for finance operations calls
   *  (integration-heavy I1 — same convention as SCM_TIMEOUT_MS). */
  FINANCE_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
  /** erp `masterdata-service` base for the operations surface
   *  (TASK-PC-FE-010 / § 2.4.8). The read-only 5 master × {list,
   *  detail} = 10 GET endpoints hang off
   *  `${ERP_BASE_URL}/api/erp/masterdata/...` — request/response/error
   *  owned by erp `masterdata-api.md` (authoritative, consumed
   *  read-only). Aligned with the registry `baseRoute` for
   *  `productKey=erp`; the erp hostname is `erp.local`. NOTE: like
   *  wms + scm + finance (NOT GAP) this is reached with the GAP OIDC
   *  access token DIRECTLY — the § 2.4.5 per-domain credential rule
   *  reused (the #569 invariant is GAP-domain-scoped; erp validates
   *  the GAP RS256 token + `tenant_id ∈ {erp,*}` claim producer-side
   *  per TASK-ERP-BE-002). */
  ERP_BASE_URL: z.string().url().default('http://erp.local'),
  /** Outbound timeout (ms) for erp operations calls
   *  (integration-heavy I1 — same convention as FINANCE_TIMEOUT_MS). */
  ERP_TIMEOUT_MS: z.coerce.number().int().positive().default(5000),
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
    IAM_ADMIN_API_BASE: process.env.IAM_ADMIN_API_BASE,
    ACCOUNTS_TIMEOUT_MS: process.env.ACCOUNTS_TIMEOUT_MS,
    AUDIT_TIMEOUT_MS: process.env.AUDIT_TIMEOUT_MS,
    OPERATORS_TIMEOUT_MS: process.env.OPERATORS_TIMEOUT_MS,
    WMS_ADMIN_BASE_URL: process.env.WMS_ADMIN_BASE_URL,
    WMS_TIMEOUT_MS: process.env.WMS_TIMEOUT_MS,
    SCM_GATEWAY_BASE_URL: process.env.SCM_GATEWAY_BASE_URL,
    SCM_TIMEOUT_MS: process.env.SCM_TIMEOUT_MS,
    FINANCE_BASE_URL: process.env.FINANCE_BASE_URL,
    FINANCE_TIMEOUT_MS: process.env.FINANCE_TIMEOUT_MS,
    ERP_BASE_URL: process.env.ERP_BASE_URL,
    ERP_TIMEOUT_MS: process.env.ERP_TIMEOUT_MS,
    LOG_LEVEL: process.env.LOG_LEVEL,
    NEXT_PUBLIC_APP_URL: process.env.NEXT_PUBLIC_APP_URL,
  });
}
