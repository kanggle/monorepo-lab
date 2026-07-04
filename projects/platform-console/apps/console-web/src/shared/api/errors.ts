/**
 * API error envelope. GAP/admin-service uses `{ code, message, timestamp }`
 * (console-registry-api.md § Errors → admin-api.md Common Error Format).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly timestamp?: string;

  constructor(status: number, code: string, message: string, timestamp?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.timestamp = timestamp;
  }
}

/** Thrown when the registry call times out or the breaker is open. */
export class RegistryUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream' | 'unauthorized';
  constructor(reason: RegistryUnavailableError['reason'], message: string) {
    super(message);
    this.name = 'RegistryUnavailableError';
    this.reason = reason;
  }
}

/**
 * Operator-token exchange (RFC 8693) failure taxonomy
 * (console-integration-contract § 2.6 fail-closed mapping / ADR-MONO-014):
 *
 *   - `fail_closed` — IAM returned `401 TOKEN_INVALID`: the subject token is
 *     invalid OR the OIDC subject is not mapped to an active operator
 *     (not provisioned). The caller MUST force re-login (`not_provisioned`);
 *     NO operator cookie is set / an existing one is dropped. The IAM token
 *     is never accepted as an `/api/admin/**` credential.
 *   - `unavailable` — `400`/`5xx`/timeout/network/unexpected `tokenType`:
 *     session-unavailable. NO operator cookie; the console never falls back
 *     to the IAM OIDC token on the operator boundary (the #569 defect).
 *
 * No `subject_token`/operator token value is ever placed in this error or
 * logged (security invariant).
 */
export class OperatorExchangeError extends Error {
  readonly reason: 'fail_closed' | 'unavailable';
  readonly code: string;
  constructor(
    reason: OperatorExchangeError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'OperatorExchangeError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * Assume-tenant exchange (RFC 8693, ADR-MONO-020 D2/D4) failure taxonomy
 * (console-integration-contract § 2.7 fail-closed switch mapping). The
 * assume-tenant exchange (`assume-tenant-exchange.ts`) re-scopes the
 * operator's domain-facing credential to the **selected** customer tenant —
 * the active-tenant switcher's server-side driver. The producer is the SAS
 * `POST ${OIDC_ISSUER_URL}/oauth2/token` token-exchange grant (auth-api.md
 * § Assume-Tenant Exchange — BE-327), DISTINCT from the § 2.6 admin JSON
 * exchange (form-urlencoded `audience` vs admin JSON shape).
 *
 *   - `denied` — producer `400 invalid_grant`: the operator is NOT assigned
 *     to the selected tenant (the D2 fail-CLOSED assignment gate), OR the
 *     subject token is invalid/expired, OR admin-service was unavailable at
 *     the gate (the producer itself fails closed on its admin-service leg).
 *     The switch is REJECTED → `/api/tenant` 403; the prior selection +
 *     assumed token are preserved (no cookie change). NEVER fall back to the
 *     base token on the selected-tenant boundary.
 *   - `invalid` — producer `400 invalid_request`: `audience` missing/malformed
 *     or `subject_token`/`subject_token_type` missing. A client-side request
 *     defect → `/api/tenant` 422.
 *   - `unavailable` — `5xx` / timeout / network / unexpected response shape
 *     (`token_type` ≠ `Bearer`, absent `access_token`): the exchange could not
 *     complete → `/api/tenant` 503. No partial/stale assumed-token state.
 *
 * No `subject_token` / assumed token value is ever placed in this error or
 * logged (security invariant — mirrors {@link OperatorExchangeError}).
 */
export class AssumeTenantError extends Error {
  readonly reason: 'denied' | 'invalid' | 'unavailable';
  readonly code: string;
  constructor(
    reason: AssumeTenantError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'AssumeTenantError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * IAM accounts surface degrade signal (console-integration-contract § 2.4.1 /
 * § 2.5). Mirrors {@link RegistryUnavailableError}: a `503 DOWNSTREAM_ERROR` /
 * `503 CIRCUIT_OPEN` / timeout on a IAM accounts call must degrade ONLY the
 * accounts section — the console shell stays intact. Auth failures
 * (401/403) are raised as {@link ApiError} so the caller forces a clean
 * re-login (no partial authed state). Inline-recoverable producer errors
 * (`400 STATE_TRANSITION_INVALID`/`REASON_REQUIRED`, `404 ACCOUNT_NOT_FOUND`,
 * `409 IDEMPOTENCY_KEY_CONFLICT`, `422 BATCH_SIZE_EXCEEDED`) are raised as
 * {@link ApiError} too so the UI can render an inline actionable message
 * without crashing. No token / PII is ever placed in this error.
 */
export class AccountsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: AccountsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'AccountsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * IAM audit + security read surface degrade signal
 * (console-integration-contract § 2.4.2 / § 2.5). Sibling of
 * {@link AccountsUnavailableError} — identical resilience posture for the
 * READ-ONLY audit slice: a `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` /
 * timeout on a IAM audit call degrades ONLY the audit section (the console
 * shell stays intact). Auth failures (401) are raised as {@link ApiError}
 * so the caller forces a clean re-login (no partial authed state).
 * Inline-recoverable producer errors (`403 PERMISSION_DENIED` — incl. the
 * intersection-permission rule, `403 TENANT_SCOPE_DENIED`,
 * `422 VALIDATION_ERROR`) are raised as {@link ApiError} so the UI renders
 * an inline actionable message without crashing. No token / audit-row PII
 * is ever placed in this error.
 */
export class AuditUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: AuditUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'AuditUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * IAM operators-management surface degrade signal
 * (console-integration-contract § 2.4.3 / § 2.5). Sibling of
 * {@link AccountsUnavailableError} / {@link AuditUnavailableError} —
 * identical resilience posture for the most privilege-sensitive Phase-2
 * slice: a `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` / timeout on a GAP
 * operators call degrades ONLY the operators section (the console shell
 * stays intact). Auth failures (401) are raised as {@link ApiError} so the
 * caller forces a clean re-login (no partial authed state). Inline-
 * recoverable producer errors (`403 PERMISSION_DENIED` — not SUPER_ADMIN /
 * lacks `operator.manage`, `403 TENANT_SCOPE_DENIED`,
 * `409 OPERATOR_EMAIL_CONFLICT`, `400 ROLE_NOT_FOUND`/`VALIDATION_ERROR`/
 * `STATE_TRANSITION_INVALID`/`SELF_SUSPEND_FORBIDDEN`/
 * `CURRENT_PASSWORD_MISMATCH`/`PASSWORD_POLICY_VIOLATION`,
 * `404 OPERATOR_NOT_FOUND`) are raised as {@link ApiError} so the UI
 * renders an inline actionable message without crashing. No token /
 * operator email / password is ever placed in this error.
 */
export class OperatorsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: OperatorsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'OperatorsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * wms `admin-service` operations surface degrade signal
 * (console-integration-contract § 2.4.5 / § 2.5). Sibling of
 * {@link AccountsUnavailableError} / {@link AuditUnavailableError} /
 * {@link OperatorsUnavailableError} — identical resilience posture for the
 * first **non-GAP** federated domain section: a `503 SERVICE_UNAVAILABLE` /
 * timeout / network failure on a wms call degrades ONLY the wms section (the
 * console shell + the IAM sections stay intact). Auth failures (`401` —
 * the IAM OIDC session expired) are raised as {@link ApiError} so the caller
 * forces a clean WHOLE-SESSION re-login (no partial authed state — this is
 * NOT a per-section degrade). Inline-recoverable producer errors
 * (`403 FORBIDDEN` role-insufficient, `404` not-found,
 * `400 VALIDATION_ERROR`, `422 STATE_TRANSITION_INVALID` alert already
 * acknowledged, `409 DUPLICATE_REQUEST`) are raised as {@link ApiError} so
 * the UI renders an inline actionable message without crashing.
 *
 * NOTE the credential divergence: unlike the IAM siblings, the wms
 * credential is the IAM OIDC access token itself (the wms gateway requires
 * it; the #569 invariant is GAP-domain-scoped — § 2.4.5). No token / PII is
 * ever placed in this error.
 */
export class WmsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: WmsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'WmsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * wms `outbound-service` operations surface degrade signal
 * (console-integration-contract § 2.4.5.1 / § 2.5). The SECOND wms surface
 * (after § 2.4.5 admin/dashboard) — the outbound pick → pack → ship operator
 * leg (ADR-MONO-022 § D7). Identical resilience posture + nested wms error
 * envelope `{ error: { code … } }` to {@link WmsUnavailableError}: a
 * `503 SERVICE_UNAVAILABLE` / timeout / network failure on a wms outbound call
 * degrades ONLY the wms outbound section (the console shell + every other
 * section stay intact). Auth failures (`401` — the IAM OIDC session expired)
 * are raised as {@link ApiError} so the caller forces a clean WHOLE-SESSION
 * re-login (no partial authed state — NOT a per-section degrade).
 * Inline-recoverable producer errors (`403 FORBIDDEN` role-insufficient —
 * e.g. lacking `OUTBOUND_WRITE`, `404` not-found, `400 VALIDATION_ERROR`,
 * `422 STATE_TRANSITION_INVALID` / `PICKING_INCOMPLETE` / `PACKING_INCOMPLETE`,
 * `409 CONFLICT` optimistic-lock stale version, `409 DUPLICATE_REQUEST`) are
 * raised as {@link ApiError} so the UI renders an inline actionable message
 * (and the `409 CONFLICT` path drives a refetch + retry-prompt, never a silent
 * auto-retry).
 *
 * NOTE the credential reuse: like {@link WmsUnavailableError} (the § 2.4.5
 * admin surface), the credential is the domain-facing IAM OIDC access token
 * (`getDomainFacingToken()`) — NEVER the IAM operator token (the wms gateway
 * requires the IAM OIDC token; the #569 invariant is GAP-domain-scoped —
 * § 2.4.5.1). No token / PII is ever placed in this error.
 */
export class WmsOutboundUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: WmsOutboundUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'WmsOutboundUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * scm gateway operations surface degrade signal
 * (console-integration-contract § 2.4.6 / § 2.5). Sibling of
 * {@link WmsUnavailableError} — the SECOND **non-GAP** federated domain
 * section (read-only). A `503 SERVICE_UNAVAILABLE` / `503 NODE_UNREACHABLE`
 * / timeout / network failure on an scm call degrades ONLY the scm section
 * (the console shell + the GAP/wms sections stay intact). Auth failures
 * (`401` — the IAM OIDC session expired) are raised as {@link ApiError} so
 * the caller forces a clean WHOLE-SESSION re-login (no partial authed
 * state — NOT a per-section degrade). Inline-recoverable producer errors
 * (`403 TENANT_FORBIDDEN`/`FORBIDDEN`/`PERMISSION_DENIED`,
 * `404 PO_NOT_FOUND`/`NODE_NOT_FOUND`, `400|422 VALIDATION_ERROR`) are
 * raised as {@link ApiError} so the UI renders an inline actionable
 * message without crashing.
 *
 * `429 RATE_LIMIT_EXCEEDED` carries a `Retry-After` and is modeled by
 * {@link ScmRateLimitedError} (a bounded backoff, NOT a degrade) — the
 * console must not auto-retry-storm the rate-limited gateway.
 *
 * NOTE the credential reuse: like the wms sibling (NOT the IAM ones), the
 * scm credential is the IAM OIDC access token itself — the § 2.4.5
 * per-domain credential rule reused verbatim (the #569 invariant is
 * GAP-domain-scoped — § 2.4.6). The scm error envelope is **flat**
 * `{ code, message, timestamp }` (DISTINCT from wms's nested
 * `{ error: { code } }`). No token / PII is ever placed in this error.
 */
export class ScmUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: ScmUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ScmUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * scm `429 RATE_LIMIT_EXCEEDED` signal (console-integration-contract
 * § 2.4.6 / task Edge Case "429 Retry-After"). The scm gateway returns
 * `Retry-After: 1`; the console honours it with ONE bounded backoff +
 * an inline "rate-limited, retrying" notice — it MUST NOT auto-retry-storm
 * into the gateway. `retryAfterSeconds` is the parsed `Retry-After`
 * (defaulted to a small bound when absent/invalid). No token / PII here.
 */
export class ScmRateLimitedError extends Error {
  readonly code = 'RATE_LIMIT_EXCEEDED';
  readonly retryAfterSeconds: number;
  constructor(retryAfterSeconds: number, message: string) {
    super(message);
    this.name = 'ScmRateLimitedError';
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/**
 * scm `demand-planning-service` replenishment operator surface degrade signal
 * (console-integration-contract § 2.4.6.1 / § 2.5). The SECOND scm service
 * section (the `demand-planning-service` alongside the § 2.4.6
 * procurement/inventory-visibility read surface) — and the FIRST scm
 * operator-MUTATION surface (approve / dismiss), mirroring how
 * {@link WmsOutboundUnavailableError} (§ 2.4.5.1) binds a second wms service.
 * A `503 SERVICE_UNAVAILABLE` / timeout / network failure on a demand-planning
 * call degrades ONLY the replenishment section (the console shell + the GAP /
 * wms / scm-ops read / finance / erp sections stay intact). Auth failures
 * (`401` — the IAM OIDC session expired) are raised as {@link ApiError} so the
 * caller forces a clean WHOLE-SESSION re-login (no partial authed state — NOT a
 * per-section degrade). Inline-recoverable producer errors
 * (`403 TENANT_FORBIDDEN`/`FORBIDDEN`, `404 SUGGESTION_NOT_FOUND`,
 * `422 SKU_SUPPLIER_UNMAPPED` / `INVALID_SUGGESTION_STATE`,
 * `409 SUGGESTION_ALREADY_MATERIALIZED`, `400|422 VALIDATION_ERROR`) are raised
 * as {@link ApiError} so the UI renders an inline actionable message without
 * crashing.
 *
 * `429 RATE_LIMIT_EXCEEDED` carries a `Retry-After` and is modeled by
 * {@link ScmRateLimitedError} (a bounded backoff, NOT a degrade) — reused
 * verbatim from the § 2.4.6 scm read surface (the same rate-limited scm
 * gateway; the console must not auto-retry-storm it).
 *
 * NOTE the credential reuse: like the § 2.4.6 scm read surface (NOT the IAM
 * ones), the demand-planning credential is the domain-facing IAM OIDC access
 * token itself (`getDomainFacingToken()`) — the § 2.4.5 / § 2.4.6 per-domain
 * credential rule reused verbatim (the #569 invariant is GAP-domain-scoped —
 * § 2.4.6.1; scm has NO operator-token exchange). The scm error envelope is
 * **flat** `{ code, message, details?, timestamp }` (DISTINCT from wms's nested
 * `{ error: { code } }`). No token / PII is ever placed in this error.
 */
export class ScmReplenishmentUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: ScmReplenishmentUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ScmReplenishmentUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * finance `account-service` operations surface degrade signal
 * (console-integration-contract § 2.4.7 / § 2.5). Sibling of
 * {@link ScmUnavailableError} / {@link WmsUnavailableError} — the THIRD
 * **non-GAP** federated domain section (read-only). A
 * `503 SERVICE_UNAVAILABLE` / timeout / network failure on a finance
 * call degrades ONLY the finance section (the console shell + the
 * IAM / wms / scm sections stay intact). Auth failures (`401` — the
 * IAM OIDC session expired) are raised as {@link ApiError} so the
 * caller forces a clean WHOLE-SESSION re-login (no partial authed
 * state — NOT a per-section degrade). Inline-recoverable producer
 * errors (`403 TENANT_FORBIDDEN`/`FORBIDDEN`/`PERMISSION_DENIED`,
 * `404 ACCOUNT_NOT_FOUND`, `400|422 VALIDATION_ERROR`) are raised as
 * {@link ApiError} so the UI renders an inline actionable message
 * without crashing.
 *
 * NOTE — **no 429**: finance has NO documented rate-limit response
 * (`account-api.md` § Error code → HTTP status carries no `429`); the
 * console does NOT fabricate a backoff path for finance (an honest
 * difference from scm — § 2.4.6 — recorded, not cargo-culted). A
 * `RateLimitedError` is intentionally absent for finance.
 *
 * NOTE the credential reuse: like the wms + scm siblings (NOT the GAP
 * ones), the finance credential is the IAM OIDC access token itself —
 * the § 2.4.5 per-domain credential rule reused verbatim (the #569
 * invariant is GAP-domain-scoped — § 2.4.7). The finance error envelope
 * is **flat** `{ code, message, details?, timestamp }` — same wire shape
 * as scm but a DISTINCT producer (own parser, finance error-code
 * vocabulary; NOT wms's nested `{ error: { code } }`). No token / PII /
 * balance / txn / account-ref is ever placed in this error
 * (confidential + F7).
 */
export class FinanceUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: FinanceUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'FinanceUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * finance `ledger-service` operations surface degrade signal
 * (console-integration-contract § 2.4.7.1 / § 2.5). The SECOND finance-
 * product service section (the `ledger-service` alongside the § 2.4.7
 * `account-service`) — read-only, mirroring {@link FinanceUnavailableError}
 * exactly (the ledger is the SAME finance producer family, behind the same
 * `finance.local` gateway, on a distinct `/api/finance/ledger/**` path). A
 * `503 SERVICE_UNAVAILABLE` / timeout / network failure on a ledger call
 * degrades ONLY the ledger section (the console shell + the IAM / wms / scm /
 * finance-account / erp sections stay intact). Auth failures (`401` — the
 * IAM OIDC session expired) are raised as {@link ApiError} so the caller
 * forces a clean WHOLE-SESSION re-login (no partial authed state — NOT a
 * per-section degrade). Inline-recoverable producer errors
 * (`403 TENANT_FORBIDDEN`/`FORBIDDEN`/`PERMISSION_DENIED`,
 * `404 JOURNAL_ENTRY_NOT_FOUND`/`ACCOUNTING_PERIOD_NOT_FOUND`/
 * `RECONCILIATION_DISCREPANCY_NOT_FOUND`, `400|422 VALIDATION_ERROR`) are
 * raised as {@link ApiError} so the UI renders an inline actionable message
 * without crashing.
 *
 * NOTE — **no 429**: the ledger contracts (`ledger-api.md` /
 * `reconciliation-api.md`) document NO rate-limit response (identical to
 * finance § 2.4.7); the console does NOT fabricate a backoff path for the
 * ledger (an honest difference from scm — § 2.4.6 — recorded, not
 * cargo-culted). A `RateLimitedError` is intentionally absent for the ledger.
 *
 * NOTE the credential reuse: like the finance + wms + scm + erp siblings
 * (NOT the IAM ones), the ledger credential is the domain-facing IAM OIDC
 * access token itself (`getDomainFacingToken()`) — the § 2.4.5 per-domain
 * credential rule reused via the § 2.4.7 finance binding (the #569 invariant
 * is GAP-domain-scoped — § 2.4.7.1). The ledger error envelope is **flat**
 * `{ code, message, details?, timestamp }` — same wire shape + finance
 * error-code family as the account-service (own parser; NOT wms's nested
 * `{ error: { code } }`). No token / PII / ledger balance / journal line /
 * account code / reconciliation amount is ever placed in this error
 * (confidential + F7).
 */
export class LedgerUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: LedgerUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'LedgerUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * erp `masterdata-service` operations surface degrade signal
 * (console-integration-contract § 2.4.8 / § 2.5). Sibling of
 * {@link FinanceUnavailableError} / {@link ScmUnavailableError} /
 * {@link WmsUnavailableError} — the FOURTH **non-GAP** federated
 * domain section and the **first internal-system-primary**
 * (read-only). A `503 SERVICE_UNAVAILABLE` / timeout / network
 * failure on an erp call degrades ONLY the erp section (the
 * console shell + the IAM / wms / scm / finance sections stay
 * intact). Auth failures (`401` — the IAM OIDC session expired)
 * are raised as {@link ApiError} so the caller forces a clean
 * WHOLE-SESSION re-login (no partial authed state — NOT a
 * per-section degrade). Inline-recoverable producer errors
 * (`403 TENANT_FORBIDDEN`/`FORBIDDEN`/`PERMISSION_DENIED`/
 * `DATA_SCOPE_FORBIDDEN`/`EXTERNAL_TRAFFIC_REJECTED`,
 * `404 MASTERDATA_NOT_FOUND`, `400|422 VALIDATION_ERROR`) are
 * raised as {@link ApiError} so the UI renders an inline actionable
 * message without crashing.
 *
 * NOTE — **no 429**: erp has NO documented rate-limit response
 * (`masterdata-api.md` § Error code → HTTP status carries no `429`,
 * identical to finance § 2.4.7); the console does NOT fabricate a
 * backoff path for erp (an honest difference from scm — § 2.4.6 —
 * recorded, not cargo-culted). A `RateLimitedError` is
 * intentionally absent for erp.
 *
 * NOTE the credential reuse: like the wms + scm + finance siblings
 * (NOT the IAM ones), the erp credential is the IAM OIDC access
 * token itself — the § 2.4.5 per-domain credential rule reused
 * verbatim (the #569 invariant is GAP-domain-scoped — § 2.4.8).
 * The erp error envelope is **flat** `{ code, message, details?,
 * timestamp }` — same wire shape as scm and finance but a DISTINCT
 * producer (own parser, erp error-code vocabulary; NOT wms's
 * nested `{ error: { code } }`). No token / PII (employee names /
 * contact) / business-partner financial details (paymentTerms) /
 * cost-center sensitive attrs is ever placed in this error
 * (confidential + audit-heavy).
 */
export class ErpUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: ErpUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ErpUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * ecommerce `product-service` operations surface degrade signal
 * (console-integration-contract § 2.4.10 / § 2.5). The FIRST ecommerce
 * **write** federated domain section (where § 2.4.9.1/§ 2.4.9.2 bind
 * ecommerce only as console-bff READ legs). Sibling of
 * {@link WmsOutboundUnavailableError} (the wms-outbound § 2.4.5.1 precedent:
 * console-web → domain gateway DIRECT, NO console-bff write leg, ADR-MONO-017
 * D2.A). A `503 SERVICE_UNAVAILABLE` / `503 STORAGE_UNAVAILABLE` / timeout /
 * network failure on an ecommerce call degrades ONLY the ecommerce section
 * (the console shell + every other section stay intact). Auth failures
 * (`401` — the IAM OIDC session expired) are raised as {@link ApiError} so the
 * caller forces a clean WHOLE-SESSION re-login (no partial authed state — NOT
 * a per-section degrade). Inline-recoverable producer errors
 * (`403 FORBIDDEN`/`ACCESS_DENIED`/`TENANT_FORBIDDEN`,
 * `404 PRODUCT_NOT_FOUND`/`VARIANT_NOT_FOUND`,
 * `400|422 VALIDATION_ERROR`/`INVALID_CATEGORY`/`INSUFFICIENT_STOCK`,
 * `409 CONFLICT` optimistic-lock concurrent modification) are raised as
 * {@link ApiError} so the UI renders an inline actionable message without
 * crashing (the `409 CONFLICT` path drives a refetch + retry-prompt, never a
 * silent auto-retry).
 *
 * NOTE the credential reuse: like the wms + scm + finance + erp siblings (NOT
 * the IAM ones), the ecommerce credential is the domain-facing IAM OIDC access
 * token itself (`getDomainFacingToken()`) — NEVER the IAM operator token (the
 * ecommerce gateway requires `account_type=OPERATOR` on the IAM OIDC token;
 * the #569 invariant is GAP-domain-scoped — § 2.4.10). The console sends NO
 * `X-Tenant-Id` (the gateway `TenantClaimValidator` injects the trusted tenant
 * from the JWT claim). The ecommerce error envelope is **flat**
 * `{ code, message, timestamp }` (the shared `ErrorResponse.of` shape — same
 * wire shape as scm/finance/erp, a DISTINCT producer error-code vocabulary;
 * NOT wms's nested `{ error: { code } }`). The producer defines NO
 * `Idempotency-Key`/`version` — confirm-gate + producer state guards are the
 * double-submit defence. No token / PII is ever placed in this error.
 */
export class EcommerceUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: EcommerceUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'EcommerceUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * Self-service tenant-onboarding surface degrade signal (ADR-MONO-044 §3.4 /
 * TASK-PC-FE-182 — `onboarding-api.md`). Sibling of the section
 * `*UnavailableError`s but for the pre-operator "create organization" call: a
 * `5xx DOWNSTREAM_ERROR`/`CIRCUIT_OPEN` / timeout / network failure on the
 * onboarding endpoint could-not-complete → the onboarding form shows a
 * retryable inline notice (the IAM login session is NOT destroyed — the
 * visitor may retry). Auth failures (`401 TOKEN_INVALID`) and inline-
 * recoverable producer errors (`400 VALIDATION_ERROR`,
 * `409 TENANT_ALREADY_EXISTS` / `OPERATOR_EMAIL_CONFLICT`) are raised as
 * {@link ApiError} so the form renders an actionable message without crashing.
 * No `subjectToken` value is ever placed in this error or logged (the § 2.1
 * invariant — the token is only the onboarding `subjectToken` input).
 */
export class OnboardingUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: OnboardingUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'OnboardingUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * IAM tenant domain-subscription surface degrade signal (TASK-PC-FE-183 /
 * ADR-MONO-023 / admin-api.md § Subscription Management). Sibling of the
 * section `*UnavailableError`s for the entitlement-plane subscribe/status
 * mutations: a `503 DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` / timeout on the
 * subscription call (admin-service → account-service delegate) degrades ONLY
 * the subscription surface (the console shell stays intact). Auth failures
 * (`401 TOKEN_INVALID`) are raised as {@link ApiError} so the caller forces a
 * clean re-login. Inline-recoverable producer errors (`403 PERMISSION_DENIED`
 * — lacks `subscription.manage`, `400 REASON_REQUIRED`, `404 TENANT_NOT_FOUND`
 * / `SUBSCRIPTION_NOT_FOUND`, `409 SUBSCRIPTION_ALREADY_EXISTS` /
 * `SUBSCRIPTION_TRANSITION_INVALID`) are raised as {@link ApiError} so the UI
 * renders an inline actionable message (the `409 ALREADY_EXISTS` path drives
 * the resume affordance — a suspended/cancelled row exists). No token / tenant
 * PII is ever placed in this error.
 */
export class SubscriptionsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: SubscriptionsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'SubscriptionsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

const MESSAGES: Record<string, string> = {
  TOKEN_INVALID: '세션이 만료되었습니다. 다시 로그인해주세요.',
  TOKEN_REVOKED: '세션이 종료되었습니다. 다시 로그인해주세요.',
  DOWNSTREAM_ERROR: '하위 서비스 호출에 실패했습니다.',
  CIRCUIT_OPEN: '서비스가 일시적으로 응답할 수 없습니다.',
  TENANT_FORBIDDEN: '선택한 테넌트에 대한 권한이 없습니다.',
  PERMISSION_DENIED: '이 작업을 수행할 권한이 없습니다.',
  STATE_TRANSITION_INVALID:
    '현재 계정 상태에서는 이 작업을 수행할 수 없습니다.',
  REASON_REQUIRED: '감사 사유를 입력해야 합니다.',
  ACCOUNT_NOT_FOUND: '대상 계정을 찾을 수 없습니다.',
  BATCH_SIZE_EXCEEDED: '한 번에 처리할 수 있는 계정 수를 초과했습니다 (최대 100).',
  IDEMPOTENCY_KEY_CONFLICT:
    '이전 요청과 다른 내용으로 같은 작업이 재시도되었습니다.',
  VALIDATION_ERROR: '입력값이 올바르지 않습니다.',
  NO_ACTIVE_TENANT: '테넌트를 먼저 선택해주세요.',
  // --- self-service onboarding (TASK-PC-FE-182 / ADR-MONO-044) --------------
  TENANT_ALREADY_EXISTS:
    '이미 사용 중인 조직 ID 입니다. 다른 조직 ID 를 입력하세요.',
  // --- domain subscriptions (TASK-PC-FE-183 / ADR-MONO-023) ----------------
  SUBSCRIPTION_MANAGE_REQUIRED:
    '도메인 구독 관리는 subscription.manage 권한이 필요합니다 (조직 관리자 TENANT_BILLING_ADMIN).',
  SUBSCRIPTION_ALREADY_EXISTS:
    '이미 구독 이력이 있는 도메인입니다 (중지·해지됨). "재개"로 다시 활성화하세요.',
  SUBSCRIPTION_TRANSITION_INVALID:
    '현재 구독 상태에서는 이 전이를 수행할 수 없습니다. 목록을 새로고침한 뒤 확인하세요.',
  SUBSCRIPTION_NOT_FOUND: '대상 구독을 찾을 수 없습니다. 목록을 새로고침하세요.',
  TENANT_NOT_FOUND: '대상 테넌트를 찾을 수 없습니다.',
  TENANT_SCOPE_DENIED: '해당 테넌트에 대한 조회 권한이 없습니다.',
  AUDIT_RANGE_INVALID: '시작 시각이 종료 시각보다 늦을 수 없습니다.',
  SECURITY_EVENT_READ_REQUIRED:
    '로그인 이력·의심 활동을 조회하려면 보안 이벤트 조회 권한(security.event.read)이 필요합니다.',
  // --- operators (TASK-PC-FE-004 / §2.4.3) -------------------------------
  OPERATOR_MANAGE_REQUIRED:
    '운영자 관리는 operator.manage 권한이 필요합니다 (SUPER_ADMIN 또는 자기 테넌트 TENANT_ADMIN).',
  TENANT_SCOPE_DENIED_CREATE:
    "플랫폼 스코프(*) 운영자는 플랫폼 스코프 운영자만 생성할 수 있습니다. 이 작업을 수행할 권한이 없습니다.",
  OPERATOR_EMAIL_CONFLICT:
    '같은 테넌트에 동일한 이메일의 운영자가 이미 존재합니다.',
  ROLE_NOT_FOUND:
    '존재하지 않는 역할이 포함되어 있습니다. 역할 목록을 새로고침한 뒤 다시 시도하세요.',
  OPERATOR_NOT_FOUND: '대상 운영자를 찾을 수 없습니다.',
  SELF_SUSPEND_FORBIDDEN: '본인 계정은 정지할 수 없습니다.',
  CURRENT_PASSWORD_MISMATCH: '현재 비밀번호가 일치하지 않습니다.',
  PASSWORD_POLICY_VIOLATION:
    '새 비밀번호가 정책을 충족하지 않습니다 (10자 이상, 영문·숫자·특수문자 각 1자 이상).',
  PASSWORD_CONFIRM_MISMATCH: '새 비밀번호와 확인 입력이 일치하지 않습니다.',
  // --- operator org_scope (TASK-PC-FE-050 / TASK-BE-339) -----------------
  TENANT_SCOPE_MISMATCH:
    '자기 활성 테넌트 밖의 배정은 편집할 수 없습니다. 테넌트를 다시 선택한 뒤 시도하세요.',
  ASSIGNMENT_NOT_FOUND:
    '이 테넌트에 대상 운영자의 명시 배정이 없습니다. (org_scope 는 배정 행에만 설정할 수 있습니다.)',
  INVALID_REQUEST:
    '입력한 부서 ID가 올바르지 않습니다 (빈 값 포함 또는 256개 초과).',
  // --- wms operations (TASK-PC-FE-007 / §2.4.5) --------------------------
  FORBIDDEN: '이 작업을 수행할 권한이 없습니다. (역할 확인 필요)',
  NOT_FOUND: '대상을 찾을 수 없습니다.',
  DUPLICATE_REQUEST:
    '이전 요청과 다른 내용으로 같은 작업이 재시도되었습니다.',
  WMS_NOT_ELIGIBLE:
    'wms 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  ALERT_ALREADY_ACKNOWLEDGED:
    '이미 확인 처리된 알림입니다. 목록을 새로고침하세요.',
  // --- wms outbound operations (TASK-PC-FE-057 / §2.4.5.1) ----------------
  ORDER_NOT_FOUND: '대상 출고 주문을 찾을 수 없습니다.',
  PICKING_REQUEST_NOT_FOUND: '대상 피킹 요청을 찾을 수 없습니다.',
  PACKING_UNIT_NOT_FOUND: '대상 패킹 단위를 찾을 수 없습니다.',
  CONFLICT:
    '주문 상태가 다른 작업으로 변경되었습니다. 최신 상태를 확인한 뒤 다시 시도하세요.',
  PICKING_INCOMPLETE:
    '아직 피킹 확정이 완료되지 않아 패킹을 진행할 수 없습니다.',
  PACKING_INCOMPLETE:
    '아직 패킹이 완료되지 않아 출고를 진행할 수 없습니다.',
  ORDER_ALREADY_SHIPPED: '이미 출고 완료된 주문입니다.',
  OUTBOUND_NO_PICKING_REQUEST:
    '아직 재고 예약(피킹 요청)이 생성되지 않았습니다. saga 상태가 RESERVED 가 된 뒤 다시 시도하세요.',
  WMS_OUTBOUND_NOT_ELIGIBLE:
    'wms 출고 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- scm operations (TASK-PC-FE-008 / §2.4.6) --------------------------
  PO_NOT_FOUND: '대상 발주(PO)를 찾을 수 없습니다.',
  NODE_NOT_FOUND: '대상 노드를 찾을 수 없습니다.',
  NODE_UNREACHABLE:
    '해당 노드는 이벤트를 보고한 적이 없어 조회할 수 없습니다.',
  RATE_LIMIT_EXCEEDED:
    'scm 게이트웨이 요청이 일시적으로 제한되었습니다. 잠시 후 자동으로 다시 시도합니다.',
  SCM_NOT_ELIGIBLE:
    'scm 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- scm demand-planning replenishment (TASK-PC-FE-077 / §2.4.6.1) -------
  SKU_SUPPLIER_UNMAPPED:
    '이 SKU 에 매핑된 공급사가 없어 보충 발주를 생성할 수 없습니다. (sku_supplier_map 미설정) 추천은 SUGGESTED 상태로 유지됩니다.',
  INVALID_SUGGESTION_STATE:
    '현재 추천 상태에서는 이 작업을 수행할 수 없습니다. 목록을 새로고침한 뒤 확인하세요.',
  SUGGESTION_ALREADY_MATERIALIZED:
    '이미 발주(DRAFT PO)가 생성된 추천입니다. 기존 PO 를 확인하세요.',
  SUGGESTION_NOT_FOUND: '대상 보충 추천을 찾을 수 없습니다. 목록을 새로고침하세요.',
  SCM_REPLENISHMENT_NOT_ELIGIBLE:
    'scm 보충 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- scm demand-planning seed/config (TASK-PC-FE-080 / §2.4.6.2) ---------
  // POLICY_NOT_FOUND / MAPPING_NOT_FOUND are surfaced as a "not configured yet
  // → create" EMPTY STATE (not an error toast); these messages back any other
  // path that needs a label.
  POLICY_NOT_FOUND:
    '이 SKU 에는 아직 재주문 정책이 설정되어 있지 않습니다. 새로 생성하세요.',
  MAPPING_NOT_FOUND:
    '이 SKU 에는 아직 공급사 매핑이 설정되어 있지 않습니다. 새로 생성하세요.',
  SCM_CONFIG_NOT_ELIGIBLE:
    'scm 보충 설정 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- finance operations (TASK-PC-FE-009 / §2.4.7) -----------------------
  // (`ACCOUNT_NOT_FOUND` is shared verbatim with the IAM accounts surface —
  // finance reuses the existing entry; the producer code is identical.)
  FINANCE_NOT_ELIGIBLE:
    'finance 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- finance ledger operations (TASK-PC-FE-072 / §2.4.7.1) --------------
  JOURNAL_ENTRY_NOT_FOUND:
    '대상 분개(journal entry)를 찾을 수 없습니다.',
  ACCOUNTING_PERIOD_NOT_FOUND: '대상 회계 기간을 찾을 수 없습니다.',
  RECONCILIATION_DISCREPANCY_NOT_FOUND:
    '대상 대사 차이(reconciliation discrepancy)를 찾을 수 없습니다.',
  // --- finance ledger account-drill reads (TASK-PC-FE-074 / §2.4.7.1) ------
  LEDGER_ACCOUNT_NOT_FOUND: '해당 계정을 찾을 수 없습니다.',
  // --- finance ledger statement-detail read (TASK-PC-FE-075 / §2.4.7.1) ----
  RECONCILIATION_STATEMENT_NOT_FOUND:
    '해당 대사 statement 를 찾을 수 없습니다.',
  // --- finance ledger reconciliation RESOLVE (TASK-PC-FE-073 / §2.4.7.1) ---
  RECONCILIATION_ALREADY_RESOLVED:
    '이미 해소된 대사 차이입니다. 새로고침 후 확인하세요.',
  RECONCILIATION_PERIOD_LOCKED:
    '해당 기간이 마감되어 대사 차이를 해소할 수 없습니다. 다음 open 기간에 처리하세요.',
  LEDGER_NOT_ELIGIBLE:
    'finance ledger 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- erp operations (TASK-PC-FE-010 / §2.4.8) ---------------------------
  MASTERDATA_NOT_FOUND: '대상 마스터 레코드를 찾을 수 없습니다.',
  DATA_SCOPE_FORBIDDEN:
    '해당 조직 범위에 대한 조회 권한이 없습니다. (E6 데이터 스코프)',
  EXTERNAL_TRAFFIC_REJECTED:
    'erp 는 내부 전용 경계입니다. 콘솔의 SSO 세션을 통해서만 조회할 수 있습니다.',
  ERP_NOT_ELIGIBLE:
    'erp 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- ecommerce product operations (TASK-PC-FE-081 / §2.4.10) -------------
  PRODUCT_NOT_FOUND: '대상 상품을 찾을 수 없습니다. 목록을 새로고침하세요.',
  VARIANT_NOT_FOUND: '대상 옵션(variant)을 찾을 수 없습니다. 상세를 새로고침하세요.',
  INVALID_CATEGORY: '존재하지 않는 카테고리입니다. 카테고리를 다시 선택하세요.',
  INSUFFICIENT_STOCK:
    '재고가 부족하여 요청한 수량만큼 차감할 수 없습니다. 현재 재고를 확인하세요.',
  ACCESS_DENIED: '이 작업을 수행할 권한이 없습니다.',
  ECOMMERCE_NOT_ELIGIBLE:
    'ecommerce 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  // --- ecommerce product images (TASK-PC-FE-082 / §2.4.10 #10-14) ----------
  IMAGE_NOT_FOUND: '대상 이미지를 찾을 수 없습니다. 목록을 새로고침하세요.',
  IMAGE_LIMIT_EXCEEDED:
    '상품당 등록할 수 있는 이미지 수를 초과했습니다. 기존 이미지를 삭제한 뒤 추가하세요.',
  MEDIA_NOT_FOUND:
    '업로드된 파일을 찾을 수 없습니다. 파일 업로드가 완료된 뒤 다시 시도하세요.',
  MEDIA_VALIDATION_FAILED:
    '이미지 형식 또는 크기가 올바르지 않습니다 (JPEG·PNG·WebP, 5MB 이하).',
  STORAGE_UNAVAILABLE:
    '이미지 저장소가 일시적으로 응답할 수 없습니다. 잠시 후 다시 시도하세요.',
  // --- ecommerce notification templates (TASK-PC-FE-089 / §2.4.10.4) --------
  // The form submit (create/update) surfaces producer codes via messageForCode;
  // without these entries a 409/404 falls through to the generic save-failed
  // fallback ("저장하지 못했습니다.") — actionable text instead (TASK-PC-FE-125).
  TEMPLATE_ALREADY_EXISTS:
    '같은 유형·채널의 알림 템플릿이 이미 있습니다. 기존 템플릿을 수정하세요.',
  TEMPLATE_NOT_FOUND: '대상 알림 템플릿을 찾을 수 없습니다. 목록을 새로고침하세요.',
  // --- ecommerce promotions (TASK-PC-FE-128 / §2.4.10.2) -------------------
  // The promotion form (create/update via PromotionForm) + coupon issuance
  // surface producer codes via messageForCode. Without these, a 400
  // INVALID_PROMOTION_REQUEST (the producer's date-order / percentage>100 /
  // bad-date guard — GlobalExceptionHandler) or a 422 state guard falls through
  // to the generic save-failed fallback ("저장하지 못했습니다.") so the operator
  // can't tell WHAT was wrong — actionable text instead. (`VALIDATION_ERROR`,
  // `ACCESS_DENIED` are already mapped above; coupon redemption/restore codes
  // are web-store/customer paths, not console-reachable, so out of scope.)
  INVALID_PROMOTION_REQUEST:
    '프로모션 정보가 올바르지 않습니다. 기간(종료일이 시작일보다 이후)과 할인값(퍼센트 할인은 1~100)을 확인하세요.',
  PROMOTION_NOT_FOUND: '대상 프로모션을 찾을 수 없습니다. 목록을 새로고침하세요.',
  PROMOTION_ALREADY_ENDED:
    '이미 종료된 프로모션입니다. 종료된 프로모션은 수정할 수 없습니다.',
  PROMOTION_HAS_ISSUED_COUPONS:
    '이미 쿠폰이 발급된 프로모션이라 삭제할 수 없습니다.',
  PROMOTION_NOT_ACTIVE:
    '진행 중인 프로모션이 아니어서 쿠폰을 발급할 수 없습니다.',
  COUPON_LIMIT_EXCEEDED:
    '최대 발급 수량을 초과하여 쿠폰을 발급할 수 없습니다. 남은 수량을 확인하세요.',
};

export function messageForCode(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
