# Console ↔ Domain Integration Contract

> The contract every product must satisfy to be federated by `platform-console`.
> Authoritative skeleton: [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D5. The operator-auth bridge (§ 2.1 server-side exchange step + § 2.6) is decided by [ADR-MONO-014](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (ACCEPTED) § D2/D3/D4 and realised by `TASK-PC-FE-002a`. This document is the full form.
> Status: **v1 skeleton** — element shapes are normative; concrete per-domain endpoint schemas are added as each domain section is built (ADR-MONO-013 Phase 2/4/5/6).

---

## 1. Scope

`platform-console` is **Model B** (ADR-MONO-013 D1): the console is the single UI and renders each domain's operational screens by calling that domain's gateway/admin REST API. This contract defines the five integration elements a domain must provide. It does **not** define domain business APIs — those live in each domain's own `specs/contracts/`.

---

## 2. Contract Elements

### 2.1 Identity (OIDC + server-side operator-token exchange)

- The console is a GAP OIDC **public client** (`platform-console-web`), Authorization Code + PKCE.
- One operator login covers all federated domains (SSO). Access token carries `tenant_id`.
- Tokens are held in **HttpOnly cookies only** (per `platform/service-types/frontend-app.md`); refresh via a server route.
- GAP-side registration is a GAP project-internal prerequisite — `TASK-BE-296`.
- **Server-side operator-token exchange step (ADR-MONO-014 D2, `TASK-PC-FE-002a`)**: the GAP OIDC access token is **not** itself an admin-service operator credential. Immediately after OIDC login (`/api/auth/callback`) and on every GAP refresh (`/api/auth/refresh`), the console **server-side** exchanges the GAP OIDC access token for a short-lived **admin-service operator token** (`token_type=admin`, `iss=admin-service`) via the GAP exchange endpoint (§ 2.6). Both the GAP OIDC token **and** the exchanged operator token are held in separate HttpOnly·Secure·SameSite=strict cookies, server-only, never client-readable, never logged.
- **Trust boundary invariant**: the GAP OIDC access token is only ever the `subject_token` input to the exchange (§ 2.6). It is **never** sent to `/api/admin/**`; the operator credential for every `/api/admin/**` call (registry § 2.2 + future operator screens § 2.4) is exclusively the exchanged operator token. There is no path by which the GAP OIDC token reaches an `/api/admin/**` endpoint.

### 2.2 Product / Tenant Registry (catalog source)

- GAP exposes a registry surface the console reads to build the **data-driven** catalog.
- **Authoritative producer endpoint** (TASK-BE-296 — GAP owns the path/auth/envelope; see [`global-account-platform/specs/contracts/http/console-registry-api.md`](../../../global-account-platform/specs/contracts/http/console-registry-api.md)):
  - **Path**: `GET http://gap.local/api/admin/console/registry` (admin-service, hosted on the GAP operator-auth boundary; the gateway treats `/api/admin/**` as a public-path subtree and delegates operator-JWT verification to admin-service `OperatorAuthenticationFilter` — platform invariant).
  - **Auth model**: `Authorization: Bearer <operator-token>` (`token_type=admin`, `iss=admin-service`) — producer requirement **unchanged**. No `X-Operator-Reason` (read-only catalog lookup). The console calls this **server-side** with the **operator token obtained via the § 2.6 exchange** (held in its own HttpOnly cookie), **not** the GAP OIDC access token — never a browser-direct call (§ 2.3). The GAP OIDC access token is never an `/api/admin/**` credential (§ 2.1 trust boundary invariant); it is only the `subject_token` input to the exchange.
  - **Tenant scoping**: the operator's tenant scope is resolved producer-side from `admin_operators.tenant_id` (ADR-002 `'*'` platform sentinel). The console does **not** send a tenant to the registry; GAP returns only the tenants the operator may select (cross-tenant isolation enforced producer-side — regression-tested, multi-tenant M3/M4).
  - **Response envelope**: `{ "products": [ <item> ] }`. **Errors** use the GAP admin error envelope `{ code, message, timestamp }`: `401 TOKEN_INVALID` / `401 TOKEN_REVOKED` → console forces re-login; `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` → console renders a degraded catalog, never blanks the shell (§ 2.5).
  - Any prior Phase-1 placeholder path (`/internal/console/registry`) is **superseded** by the producer contract above; the console's `CONSOLE_REGISTRY_URL` points at the authoritative path.
- Minimum item shape (normative):

| Field | Type | Meaning |
|---|---|---|
| `productKey` | string | `gap` \| `wms` \| `scm` \| `erp` \| `finance` |
| `displayName` | string | Catalog tile label |
| `available` | boolean | `false` → rendered as "coming soon" (e.g. `erp`/`finance` pre-bootstrap) |
| `tenants` | string[] | Tenant ids the operator may select for this product |
| `baseRoute` | string | Console-internal route prefix for the product's screens |

- Adding a product (e.g. finance) or flipping `available` is a **registry change only** — zero `console-web` code change (ADR-MONO-013 § 1.2 / D5).

### 2.3 Routing

- Each domain is reachable at its Traefik hostname (`gap.local`, `wms.local`, `scm.local`, … ; console at `console.local`).
- The console reaches domains **server-side** (server components / server routes), never via browser-direct calls that bypass the typed API client.

### 2.4 Console-facing API surface (per domain)

- Each domain's gateway/admin service exposes the read/ops endpoints the console renders. These are declared per-domain in that domain's `specs/contracts/` and cross-referenced from the console's `specs/services/console-web/` when the domain section is built.
- All calls are **tenant-scoped**: the console propagates the selected tenant (`X-Tenant-Id` header or equivalent honored by the domain gateway); the domain MUST reject cross-tenant requests.
- Operator mutating actions (e.g. account lock/unlock) MUST be idempotent on the domain side; the console sends an idempotency key and renders the result (it owns no domain transaction — `platform-console` is not `transactional`).

#### 2.4.1 GAP accounts surface (TASK-PC-FE-002 — cross-reference, not a redefinition)

The first concrete per-domain binding of § 2.4 (ADR-MONO-013 Phase 2 slice 1 / § 3 parity "accounts" line). The console's `features/accounts` renders, **server-side and tenant-scoped**, the GAP operator account surface. The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning GAP spec.

- **Authoritative producer (owned by GAP, do NOT redefine here)**: [`global-account-platform/specs/contracts/http/admin-api.md`](../../../global-account-platform/specs/contracts/http/admin-api.md). The console consumes exactly these endpoints (request/response/headers/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-api.md` §) | Kind |
  |---|---|---|---|
  | 1 | search / list | `GET /api/admin/accounts` (`email` single-lookup OR `page`/`size` list) | read |
  | 2 | detail | derived from the search/list item + (3–8) per-account ops | read |
  | 3 | lock | `POST /api/admin/accounts/{accountId}/lock` | mutation |
  | 4 | unlock | `POST /api/admin/accounts/{accountId}/unlock` | mutation |
  | 5 | bulk-lock | `POST /api/admin/accounts/bulk-lock` (per-account `results[]`, partial-failure) | mutation |
  | 6 | revoke-session | `POST /api/admin/sessions/{accountId}/revoke` | mutation |
  | 7 | gdpr-delete | `POST /api/admin/accounts/{accountId}/gdpr-delete` (irreversible) | mutation |
  | 8 | export | `GET /api/admin/accounts/{accountId}/export` (unmasked PII — producer meta-audits) | read (export) |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every call carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the GAP OIDC access token. An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the GAP token on the `/api/admin/**` boundary — the #569 invariant).
- **Tenant scope (§ 2.4 / multi-tenant)**: the console always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the call** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id`.
- **Mutation audit + idempotency (§ 2.4 / audit-heavy / integration-heavy I4)**: every mutation (lock/unlock/bulk-lock/revoke-session/gdpr-delete) carries a required operator-entered `X-Operator-Reason` (audit reason; producer `400 REASON_REQUIRED` if missing) **and** a client-generated `Idempotency-Key` (`crypto.randomUUID()`), stable across one user-confirmed action and freshly regenerated per a new attempt — no accidental double-mutation, no accidental dedupe of a genuine second action. The console owns no domain transaction; the producer is the idempotency authority (`bulk-lock` `(operator_id, Idempotency-Key)` uniqueness; `409 IDEMPOTENCY_KEY_CONFLICT` on a same-key/different-payload reuse).
- **Resilience (§ 2.5)**: the accounts section reuses the registry-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`403 TOKEN_INVALID|PERMISSION_DENIED` → forced re-login (no partial authed state); `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the accounts section degrades** (the shell stays intact); `400 STATE_TRANSITION_INVALID`/`400 REASON_REQUIRED` / `404 ACCOUNT_NOT_FOUND` / `422 BATCH_SIZE_EXCEEDED` / `409 IDEMPOTENCY_KEY_CONFLICT` → inline actionable error (no crash). `account.read` absent ⇒ producer returns an empty list (not `403`) ⇒ the console renders an empty/insufficient-permission state, not an error crash.
- **Destructive-action UX (security UX, audit-heavy)**: lock/unlock/bulk-lock/revoke-session/gdpr-delete are each reason-gated **and** confirm-gated — the producer call MUST NOT fire until a non-empty operator reason is entered; `gdpr-delete` is irreversible → double-confirm + an explicit typed confirmation; `bulk-lock` is multi-select with per-account result rendering (no all-or-nothing implication). No silent/one-click destructive call.
- **Logging**: structured server-side logs only; operator/GAP tokens and account PII (emails) are never logged (redacted) — § 2.6 logging invariant extended to the accounts surface.
- **PII / export**: `export` returns unmasked PII server-side; the console streams/downloads it without buffering PII into client state (producer meta-audits the access).
- **Producer immutability**: this is a **cross-reference only**. Any change to the accounts producer contract is a GAP project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

#### 2.4.2 GAP audit + security surface (TASK-PC-FE-003 — cross-reference, not a redefinition)

The second concrete per-domain binding of § 2.4 (ADR-MONO-013 Phase 2 slice 2 / § 3 parity "audit: query" + "security: login-history, suspicious"). The console's `features/audit` renders, **server-side and tenant-scoped**, the GAP unified audit + security read surface. This is a **read-only** slice — there is **no mutation**, therefore the § 2.4.1 mutation scaffolding (`X-Operator-Reason`, `Idempotency-Key`, destructive-confirm dialogs) **does not apply and MUST NOT be carried over**. The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning GAP spec.

- **Authoritative producer (owned by GAP, do NOT redefine here)**: [`global-account-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/audit`](../../../global-account-platform/specs/contracts/http/admin-api.md). A single unified-view endpoint over `admin_actions` + `login_history` + `suspicious_events`, discriminated by the `source` filter. The console consumes exactly this endpoint (request/response/headers/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-api.md` §) | `source` | Base permission | Kind |
  |---|---|---|---|---|---|
  | 1 | audit query | `GET /api/admin/audit` | `admin` (or unfiltered) | `audit.read` | read |
  | 2 | security: login-history | `GET /api/admin/audit?source=login_history` | `login_history` | `audit.read` **and** `security.event.read` | read |
  | 3 | security: suspicious | `GET /api/admin/audit?source=suspicious` | `suspicious` | `audit.read` **and** `security.event.read` | read |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: the call carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the GAP OIDC access token. An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the GAP token on the `/api/admin/**` boundary — the #569 invariant). There is **no** `X-Operator-Reason` and **no** `Idempotency-Key` on this read-only call (carrying either over from § 2.4.1 is a defect).
- **Tenant scope (§ 2.4 / multi-tenant M3/M4)**: the console always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer resolves the operator's authoritative tenant scope from `admin_operators.tenant_id` and rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the call** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id`. A SUPER_ADMIN may additionally send the producer's optional `tenantId` **query** parameter for an explicit cross-tenant read; a non-SUPER_ADMIN operator sending a foreign `tenantId` → producer `403 TENANT_SCOPE_DENIED` (meta-audited producer-side per `admin-api.md`). The console offers **no free-text tenant override** to non-super operators — only the standard tenant selector.
- **Intersection-permission rule (producer-authoritative)**: `audit.read` is the base permission. `source=login_history` or `source=suspicious` **additionally** requires `security.event.read` (intersection, not union — both permissions). Operators with `audit.read` only (e.g. `SUPPORT_LOCK`) can read `source=admin` but receive `403 PERMISSION_DENIED` on a security source. The console's UX SHOULD pre-disable the `login_history`/`suspicious` source affordances with an explanation when the operator's claims show `security.event.read` is absent, and MUST ALWAYS still handle a server `403 PERMISSION_DENIED` defensively (inline, never a crash). The console never re-derives the producer's authorization — it mirrors it for UX only; the producer is the final authority.
- **Read-query meta-audit awareness (audit-heavy A5)**: the audit query itself is meta-audited producer-side. The console MUST NOT auto-refetch aggressively — one user-initiated query = one producer call (no background polling loop that would flood the producer's meta-audit). A degraded section re-query is an explicit user retry, not an automatic poll.
- **Producer-masked PII (audit-heavy A9 / regulated R4)**: the producer already masks PII in the audit response (IP partially masked, no email). The console MUST NOT attempt to un-mask, derive, or buffer audit-row PII (account ids / masked IPs / geo) beyond render, and MUST NOT log it (server-side structured logs redact it — § 2.6 logging invariant extended to the audit surface). Large result sets are server-side paginated only — never buffered whole into client state.
- **Discriminated rendering tolerance**: rows are rendered discriminated by the `source` value (`admin` vs `login_history` vs `suspicious` columns). An unknown/future `source` value MUST degrade to a generic row — the consumer parser is tolerant and never throws on an unrecognised discriminant.
- **Resilience (§ 2.5)**: the audit section reuses the registry/accounts-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`403 TOKEN_INVALID|PERMISSION_DENIED`/`403 TENANT_SCOPE_DENIED` → `401` forces a clean re-login (no partial authed state); `403 PERMISSION_DENIED`/`403 TENANT_SCOPE_DENIED` → inline actionable (no crash); `422 VALIDATION_ERROR` (from > to, size > 100) → inline field-level error **plus** a client-side guard (from ≤ to, `size` client-capped ≤ 100) that pre-empts the producer 422; `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the audit section degrades** (the console shell stays intact).
- **Producer immutability**: this is a **cross-reference only**. Any change to the audit producer contract is a GAP project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

> **§ 3 parity lines satisfiable**: with `features/audit` bound here, the § 3 "audit: query" and "security: login-history, suspicious" parity lines are **satisfiable**; `FE-006` formally verifies them (ADR-MONO-013 Phase 3 admin-web-retirement gate).

#### 2.4.3 GAP operators surface (TASK-PC-FE-004 — cross-reference, not a redefinition)

The third concrete per-domain binding of § 2.4 (ADR-MONO-013 Phase 2 slice 3 / § 3 parity "operators: create, edit-roles, change-status, change-password"). The console's `features/operators` renders, **server-side and tenant-scoped**, the GAP operator-management surface. This is the **most privilege-sensitive** slice — creating operators and changing roles/status is the operator-privilege-escalation surface. The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning GAP spec.

- **Authoritative producer (owned by GAP, do NOT redefine here)**: [`global-account-platform/specs/contracts/http/admin-api.md`](../../../global-account-platform/specs/contracts/http/admin-api.md). The console consumes exactly these endpoints (request/response/**per-endpoint headers**/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-api.md` §) | Kind | Required permission |
  |---|---|---|---|---|
  | 1 | list | `GET /api/admin/operators` (`status` filter, `page`/`size`) | read | `operator.manage` |
  | 2 | create | `POST /api/admin/operators` (body `tenantId`; `*`=platform-scope) | mutation | `operator.manage` |
  | 3 | edit-roles | `PATCH /api/admin/operators/{operatorId}/roles` (full-replace; `[]` allowed) | mutation | `operator.manage` |
  | 4 | change-status | `PATCH /api/admin/operators/{operatorId}/status` (ACTIVE↔SUSPENDED) | mutation | `operator.manage` |
  | 5 | change-password | `PATCH /api/admin/operators/me/password` (**self only** — no admin-set-other) | mutation (self) | (valid operator token) |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every call carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the GAP OIDC access token. An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the GAP token on the `/api/admin/**` boundary — the #569 invariant).
- **Tenant scope (§ 2.4 / multi-tenant)**: the console always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer resolves the actor's authoritative tenant scope from `admin_operators.tenant_id` and rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the call** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id`. **`create` additionally carries a `tenantId` body field** (the tenant the new operator belongs to); `tenantId='*'` is the SUPER_ADMIN platform-scope sentinel and **only a platform-scope operator may create another `*` operator** → a non-platform operator attempting it gets producer `403 TENANT_SCOPE_DENIED` (meta-audited producer-side). The console MUST NOT offer `*` as a tenant option to non-platform operators (the UI never presents an escalation it cannot perform).
- **Per-endpoint header matrix (the key correctness risk — NOT uniform; do NOT blanket-apply § 2.4.1's `reason`+`idempotency` pair)**:

  | Operation | `X-Operator-Reason` | `Idempotency-Key` | Notes |
  |---|---|---|---|
  | `GET /operators` (list) | — | — | read only; no mutation headers |
  | `POST /operators` (create) | **required** | **required** (`crypto.randomUUID()`) | producer requires both |
  | `PATCH .../{id}/roles` | **required** | **MUST NOT send** | producer does not list `Idempotency-Key` — sending it is a contract deviation; full-replace PATCH is idempotent by the producer |
  | `PATCH .../{id}/status` | **required** | **MUST NOT send** | producer does not list `Idempotency-Key`; idempotent PATCH |
  | `PATCH .../me/password` | — | — | self path; valid operator token only (no `operator.manage`, no audit-reason header per producer) |

  A retried *confirmed* `create` reuses its `Idempotency-Key`; a fresh create attempt gets a new key. `roles`/`status` carry **no** key — adding one the producer omits is a header-matrix-drift defect (this slice's primary failure mode; pinned by an AC + a test).
- **`operator.manage` / SUPER_ADMIN gating (saas S5 / audit-heavy A5)**: all five operations require `operator.manage`, granted only to `SUPER_ADMIN` (producer-authoritative; the console mirrors it for UX only and never re-derives it). When the operator is not a SUPER_ADMIN the producer returns `403 PERMISSION_DENIED`; the console renders the whole operators section as an inline "not permitted" state (and SHOULD gate the `/operators` nav entry when derivable) — never a crash, never a re-login loop. The console always still handles the server `403` defensively.
- **Mutation audit (§ 2.4 / audit-heavy / saas S5)**: every mutating action (create / edit-roles / change-status / change-password) is **reason-gated and confirm-gated** — the producer call MUST NOT fire until a non-empty operator reason is entered (producer `400 REASON_REQUIRED` if missing on the reason-bearing endpoints). Privilege-high actions — **creating an operator, granting `SUPER_ADMIN`, suspending an operator, removing all roles (`[]`)** — carry explicit **elevated confirm copy**. No silent / one-click create / role-grant / suspend.
- **Password safety (security-rules / saas S1)**: `create` and self `change-password` accept a plaintext password server-side only. The console **client-side mirrors the producer password policy** as a UX pre-check (≥10 chars, ≥1 letter + ≥1 digit + ≥1 special — pre-validates before submit; the producer is the final authority). A password is **never** logged, never echoed into structured logs / events / state beyond the input field, never placed in a query string, and is cleared from memory on submit where practical. There is **no admin-set-other-password endpoint** in the parity line — change-password is exclusively the logged-in operator's own (`/me/`); the console does not invent one.
- **Role tolerance**: role names are the producer's enum (`SUPER_ADMIN`/`SUPPORT_LOCK`/`SUPPORT_READONLY`/`SECURITY_ANALYST`/…). The list view tolerates an unknown/future role (a generic chip, never a crash); the create / edit-roles selectors offer the known enum (a stale `400 ROLE_NOT_FOUND` is handled inline, refreshing the client-cached role source).
- **Resilience (§ 2.5)**: the operators section reuses the registry/accounts/audit-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`TOKEN_INVALID` → forced re-login (no partial authed state); `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` / `409 OPERATOR_EMAIL_CONFLICT` / `400 ROLE_NOT_FOUND`/`VALIDATION_ERROR`/`STATE_TRANSITION_INVALID`/`SELF_SUSPEND_FORBIDDEN`/`CURRENT_PASSWORD_MISMATCH`/`PASSWORD_POLICY_VIOLATION` / `404 OPERATOR_NOT_FOUND` → inline field-level / actionable (no crash); `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the operators section degrades** (the console shell stays intact).
- **Logging**: structured server-side logs only; operator/GAP tokens, operator emails, and passwords are never logged (redacted) — § 2.6 logging invariant extended to the operators surface (passwords never logged or echoed at all).
- **Producer immutability**: this is a **cross-reference only**. Any change to the operators producer contract is a GAP project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

> **§ 3 parity line satisfiable**: with `features/operators` bound here, the § 3 "operators: create, edit-roles, change-status, change-password" parity line is **satisfiable**; `FE-006` formally verifies it (ADR-MONO-013 Phase 3 admin-web-retirement gate).

### 2.5 Resilience

- Console/BFF fan-out applies circuit-breaker / retry / timeout per `platform/` baselines (`integration-heavy` trait).
- One domain unavailable MUST degrade only that domain's section — never blank the console shell.

### 2.6 Operator Token Exchange (normative — ADR-MONO-014 D2/D3)

The operator credential the console presents to `/api/admin/**` (§ 2.2 registry + the Phase-2 operator screens § 2.4) is obtained by a **server-side RFC 8693 token exchange**, never by sending the GAP OIDC token directly.

- **Endpoint (authoritative producer)**: `POST http://gap.local/api/admin/auth/token-exchange` (GAP `admin-service`, on the same `/api/admin/**` operator-auth public-path subtree as the registry). The request/response/error contract is owned by GAP [`global-account-platform/specs/contracts/http/admin-api.md` § `POST /api/admin/auth/token-exchange`](../../../global-account-platform/specs/contracts/http/admin-api.md); the subject-token validation policy is GAP [`admin-service/security.md` § GAP OIDC Subject-Token Validation](../../../global-account-platform/specs/services/admin-service/security.md). This file does **not** redefine those — it only states the consumer obligation.
- **Request** (server-side only, `application/json`, RFC 8693 — verbatim per the producer contract):

  ```json
  {
    "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
    "subject_token": "<the operator's GAP OIDC platform-console-web access token>",
    "subject_token_type": "urn:ietf:params:oauth:token-type:access_token"
  }
  ```

- **Response 200**: `{ "accessToken": "<operator JWT>", "expiresIn": <seconds>, "tokenType": "admin" }`. The console stores `accessToken` in its own HttpOnly·Secure·SameSite=strict operator cookie with `maxAge = expiresIn`, validates `tokenType === "admin"`, and uses **only** this token for `/api/admin/**`.
- **When**: on session establish (`/api/auth/callback`, immediately after the GAP tokens are stored) **and** on every GAP refresh (`/api/auth/refresh`, after the GAP access token rotates). **Re-exchange model (ADR-MONO-014 D2)**: there is **no operator-refresh token or operator-refresh state** — each GAP refresh triggers a fresh exchange using the rotated GAP access token; the operator cookie's lifetime tracks the response `expiresIn`.
- **Fail-closed mapping** (parity with the § 2.5 resilience posture, but on the operator trust boundary it is fail-**closed**, never degrade-with-fallback):
  - Exchange `401 TOKEN_INVALID` (subject token invalid / OIDC subject not mapped to an active `admin_operators` row — producer fail-closed per `admin-api.md`/`security.md`): the operator is **not provisioned** for operator actions → forced re-login with a distinct reason; the operator cookie is **not** set; on refresh the existing operator cookie is dropped.
  - Exchange `400 BAD_REQUEST`/`VALIDATION_ERROR`, timeout, network failure, or `5xx`: treated as **session-unavailable** → no operator cookie set / existing operator session dropped; the console never falls back to the GAP OIDC token on the `/api/admin/**` boundary (that is the exact #569 latent defect this contract fix closes).
  - An unexpected `tokenType` (≠ `"admin"`) is treated as fail-closed (operator cookie not set).
- **Resilience parity (§ 2.5)**: the exchange call uses the same `integration-heavy` discipline as the registry call — explicit hard timeout (AbortController), structured logging, no unbounded default — but the operator-boundary outcome is fail-closed (no partial authed state), distinct from the registry's degrade-the-section behaviour.
- **Tenant scope**: never derived from the GAP OIDC token. GAP resolves operator tenant scope producer-side from `admin_operators.tenant_id` (ADR-002 `'*'` platform sentinel); the console sends no tenant to the exchange (consistent with § 2.2 registry tenant scoping). Cross-references: GAP [`console-registry-api.md` § Authentication](../../../global-account-platform/specs/contracts/http/console-registry-api.md) (operator token now via the exchange; producer requirement unchanged).

---

## 3. GAP `admin-web` absorption (Phase 3 reference)

The console's GAP section must reach functional parity with the existing GAP `admin-web` operator surface before `admin-web` is retired (ADR-MONO-013 D4, parity-gated). Parity checklist (enumerated at ACCEPTED, ADR-MONO-013 § 6 D7.4):

- accounts: search, detail, lock/unlock, bulk-lock, revoke-session, GDPR-delete, export — **implemented by TASK-PC-FE-002** (`features/accounts`, console-facing surface bound in § 2.4.1); formal parity verification is `FE-006`.
- audit: query — **implemented by TASK-PC-FE-003** (`features/audit`, console-facing surface bound in § 2.4.2); formal parity verification is `FE-006`.
- dashboards
- operators: create, edit-roles, change-status, change-password — **implemented by TASK-PC-FE-004** (`features/operators`, console-facing surface bound in § 2.4.3, `operator.manage`/SUPER_ADMIN gating + per-endpoint header matrix); formal parity verification is `FE-006`.
- security: login-history, suspicious — **implemented by TASK-PC-FE-003** (`features/audit` unified-view `source=login_history|suspicious`, bound in § 2.4.2, intersection-permission `security.event.read`); formal parity verification is `FE-006`.

Retirement itself is a GAP project-internal spec-first change (GAP `PROJECT.md` service map), not a `platform-console` task.

---

## 4. Out of Scope (this contract)

- Domain business logic / domain event contracts (each domain owns these).
- `console-bff` aggregation endpoint shapes (ADR-MONO-013 Phase 7 — added when the BFF lands).
- finance/erp domain contracts (governed by ADR-MONO-008 / future erp ADR).

---

## 5. Change Rule

Changes to the contract elements (§ 2) require updating this file **before** implementation, and — if they alter a deployed integration — an ADR per [`architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md). The skeleton → full transition (adding concrete per-domain endpoint schemas) is additive and tracked per domain task. The § 2.1/§ 2.6 operator-token-exchange element is governed by ADR-MONO-014 (ACCEPTED); the RFC 8693 request/response/error contract is owned producer-side by GAP `admin-api.md` — a change there is a GAP project-internal spec-first change cross-referenced here, not redefined here.
