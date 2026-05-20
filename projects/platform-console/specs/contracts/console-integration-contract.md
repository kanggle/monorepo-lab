# Console ↔ Domain Integration Contract

> The contract every product must satisfy to be federated by `platform-console`.
> Authoritative skeleton: [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D5. The operator-auth bridge (§ 2.1 server-side exchange step + § 2.6) is decided by [ADR-MONO-014](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (ACCEPTED) § D2/D3/D4 and realised by `TASK-PC-FE-002a`. This document is the full form.
> Status: **v1 skeleton** — element shapes are normative; concrete per-domain endpoint schemas are added as each domain section is built (ADR-MONO-013 Phase 2/4/5/6). The GAP operator surface (§ 2.4.1–§ 2.4.4) is fully bound; **§ 3 is finalized as a VERIFIED parity matrix** (TASK-PC-FE-006 — ADR-MONO-013 Phase 2 = 5/5 COMPLETE, Phase 3 retirement gate satisfied).

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

#### 2.4.4 GAP operator overview (composed) — TASK-PC-FE-005 — cross-reference, **no new producer**

The fourth concrete binding of § 2.4 (ADR-MONO-013 Phase 2 slice 4 / § 3 parity "dashboards" line). The console's `features/dashboards` renders, **server-side and tenant-scoped**, a **composed operator overview** — **not** a Grafana/observability embed. This is governed by [ADR-MONO-015](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) (ACCEPTED, decision **D1-B**): the console "dashboards" parity line is **refined** (ADR-MONO-015 D2 — recorded explicitly, not decided implicitly) to mean *an operator overview composed from the already-integrated read surfaces*, **not** a reproduction of `admin-web`'s Grafana observability iframe. Observability/Grafana metrics dashboards are **out of scope** of the platform-console parity gate (a future observability ADR, never an admin-web-retirement blocker).

This is a **read-only** binding — there is **no mutation**, therefore the § 2.4.1 mutation scaffolding (`X-Operator-Reason`, `Idempotency-Key`, destructive-confirm dialogs) **does not apply and MUST NOT be carried over** (same read discipline as § 2.4.2; carrying it over is a defect). It also introduces **no new GAP producer endpoint** — it is a **composition of the EXISTING reads** already bound in §§ 2.4.1/2.4.2/2.4.3. GAP `admin-api.md` is **unchanged** (ADR-MONO-015 D1: compose existing reads only; cross-reference, never redefine).

- **Composed producers (owned by GAP, do NOT redefine here — the EXISTING reads only, unchanged)**: the overview is a **bounded fan-out** over the three already-integrated read endpoints in [`global-account-platform/specs/contracts/http/admin-api.md`](../../../global-account-platform/specs/contracts/http/admin-api.md), consumed through the **existing** FE-002/003/004 server clients (no duplicate / new GAP client):

  | # | Overview card | Composed producer endpoint (`admin-api.md` §) | Existing client (reused) | Base permission | Kind |
  |---|---|---|---|---|---|
  | 1 | accounts summary | `GET /api/admin/accounts` (page total / snapshot) | `features/accounts` `searchAccounts` (§ 2.4.1) | `account.read` (absent ⇒ producer returns an empty list, not 403) | read |
  | 2 | audit + security activity | `GET /api/admin/audit` (recent rows) | `features/audit` `queryAudit` (§ 2.4.2) | `audit.read` (+ `security.event.read` for the security subset — intersection per § 2.4.2) | read |
  | 3 | operators summary | `GET /api/admin/operators` (count / status mix) | `features/operators` `listOperators` (§ 2.4.3) | `operator.manage` (SUPER_ADMIN — non-privileged ⇒ producer 403, that card only) | read |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every fan-out leg carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the GAP OIDC access token (the legs inherit this from the reused FE-002/003/004 clients). An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the GAP token on the `/api/admin/**` boundary — the #569 invariant). There is **no** `X-Operator-Reason` and **no** `Idempotency-Key` on any leg (read-only — carrying either over from § 2.4.1/§ 2.4.3 is a defect).
- **Tenant scope (§ 2.4 / multi-tenant)**: every leg always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer resolves the operator's authoritative tenant scope and rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the overview** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id` on any leg.
- **Per-source isolation (the key design point — ADR-MONO-015 D3 / § 2.5)**: the fan-out collects a per-card outcome (`ok` / `degraded` / `forbidden`). One leg failing **MUST NOT** fail the whole overview:
  - `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` on a leg → **that card only** renders a "not available to your role" / scoped placeholder (the operators card respects `operator.manage`/SUPER_ADMIN; the audit card reuses the § 2.4.2 intersection-permission behaviour for the security subset). Not a crash, not a re-login.
  - `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` / timeout on a leg → **that card only** degrades; the overview + the console shell stay intact (never blank). All sources down ⇒ an all-degraded overview with a retry affordance, never a hard crash.
  - **`401` on ANY leg → a whole-overview forced re-login** (auth is **not** a per-card degrade — there is no partial authed state; the operator token is shared across all legs, so a 401 on one is a 401 for all).
- **Bounded + producer-meta-audit-respecting (integration-heavy I1 / audit-heavy A5)**: the fan-out is **bounded** — each leg inherits the reused client's explicit AbortController hard timeout (no unbounded default). The audit leg (`GET /api/admin/audit`) is **meta-audited producer-side** (§ 2.4.2); therefore **one overview load issues exactly one bounded set of calls** — no aggressive polling / auto-refetch / N+1 that would flood the producer's meta-audit. A degraded re-query is an explicit user retry, not an automatic interval.
- **Logging**: structured server-side logs only; operator/GAP tokens and source PII (account ids / masked IPs / operator emails) are never logged (redacted) — § 2.6 logging invariant, inherited from the reused FE-002/003/004 clients.
- **Producer immutability**: this is a **cross-reference + composition only**. There is **no** new GAP producer endpoint and **no** change to any composed producer contract — any such change would be a GAP project-internal spec-first change in `admin-api.md`; this section follows the existing reads, never redefines them, never invents a new one (§ 5 Change Rule; ADR-MONO-015 D1).

> **§ 3 parity line satisfiable**: with `features/dashboards` bound here, the ADR-MONO-015-**refined** § 3 "dashboards" parity line (composed operator overview, **not** Grafana) is **satisfiable**; `FE-006` formally verifies the full refined checklist (ADR-MONO-013 Phase 3 admin-web-retirement gate).

#### 2.4.5 wms operations surface (TASK-PC-FE-007 — cross-reference, not a redefinition)

The **first non-GAP** per-domain binding of § 2.4 (ADR-MONO-013 Phase 4
slice 1). The console's `features/wms-ops` renders, **server-side and
tenant-scoped**, the wms `admin-service` **dashboard read-model** surface plus
the single operational mutation that surface exposes (alert acknowledge). The
producer contract is **authoritative and unchanged** — this section only
states the consumer obligation and points at the owning wms spec. This is the
binding that **verifies** ADR-MONO-013 § 3.3's "zero retrofit" assumption: a
non-GAP domain is bound for the first time, and it surfaces a genuine
**auth-model divergence** from the GAP operator surface (§§ 2.4.1–2.4.4).

- **Authoritative producer (owned by wms, do NOT redefine here)**: wms
  [`admin-service-api.md`](../../../wms-platform/specs/contracts/http/admin-service-api.md)
  — **unchanged, consumed only**. The console consumes exactly the **§ 1
  Dashboard / Read-Model** reads and the one operational mutation on that
  surface (request/response/headers/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-service-api.md` §) | Kind |
  |---|---|---|---|
  | 1 | inventory snapshot | `GET /api/v1/admin/dashboard/inventory` (§ 1.1) | read |
  | 2 | inventory by-key | `GET /api/v1/admin/dashboard/inventory/by-key` (§ 1.1) | read |
  | 3 | throughput | `GET /api/v1/admin/dashboard/throughput` (§ 1.2) | read |
  | 4 | orders | `GET /api/v1/admin/dashboard/orders` (§ 1.3) | read |
  | 5 | shipments | `GET /api/v1/admin/dashboard/shipments` (§ 1.3) | read |
  | 6 | asns | `GET /api/v1/admin/dashboard/asns` (§ 1.4) | read |
  | 7 | asn inspection | `GET /api/v1/admin/dashboard/asns/{asnId}/inspection` (§ 1.4) | read |
  | 8 | adjustments audit | `GET /api/v1/admin/dashboard/adjustments` (§ 1.5, **append-only** — no PATCH/DELETE) | read |
  | 9 | alerts | `GET /api/v1/admin/dashboard/alerts` (§ 1.6) | read |
  | 10 | **alert acknowledge** | `POST /api/v1/admin/dashboard/alerts/{alertId}/acknowledge` (§ 1.6) | **mutation** |
  | 11 | master refs | `GET /api/v1/admin/dashboard/refs/{type}` (§ 1.7) | read |
  | 12 | projection status | `GET /api/v1/admin/operations/projection-status` (§ 6.2) | read |

  The wms write-admin surface (`admin-service-api.md` §§ 2–5: User / Role /
  Assignment / Settings, `WMS_ADMIN`+ heavy writes) is **explicitly out of v1
  console scope** — deferred to a later slice, not silently dropped.

- **Per-domain credential selection (the key correctness element — normative)**:
  **each § 2.4.x binding declares which credential it uses, and an
  implementer MUST NOT blanket-apply one domain's auth model to another.**
  The credential is a first-class, per-domain contract element, not an
  implementation detail:

  | Domain binding | `/api/admin/**` credential | Mechanism | Authority |
  |---|---|---|---|
  | GAP (§§ 2.4.1–2.4.4) | the **exchanged operator token** (`token_type=admin`, `iss=admin-service`), `getOperatorToken()` | server-side RFC 8693 token exchange (§ 2.6) | ADR-MONO-014; the **#569 trust-boundary invariant** (§ 2.1) — the GAP OIDC access token is **never** sent to GAP's `/api/admin/**` |
  | **wms (§ 2.4.5, this binding)** | the **GAP OIDC access token** itself (`getAccessToken()`, the GAP-session HttpOnly cookie from FE-001) | sent **directly** as `Authorization: Bearer <GAP OIDC access token>` | wms `admin-service-api.md` § Global Conventions + `gap-integration.md`: RS256 JWT issued by GAP per ADR-001, validated against GAP JWKS by the wms gateway + admin-service; **`tenant_id=wms` enforced producer-side from the JWT claim**. wms has **no** token-exchange and **requires** the GAP OIDC token |

  **The #569 trust-boundary invariant is GAP-domain-scoped and does NOT
  generalise to wms.** #569 forbids the GAP OIDC access token on **GAP's**
  `/api/admin/**` boundary *because GAP requires the § 2.6 exchanged operator
  token there*. wms's gateway, by contrast, *requires* exactly the GAP OIDC
  access token — these are **not in conflict; they are different per-domain
  bindings**. An implementer must therefore neither (a) wrongly carry the
  GAP operator-token-exchange (§ 2.6) to wms (wms would reject it — wrong
  issuer/type — and it would misapply the GAP-domain auth model), nor (b)
  wrongly treat "a GAP token on an admin path" as a universal #569 violation
  (it is the *required* wms credential). The console's `features/wms-ops`
  client uses `getAccessToken()` and **never** `getOperatorToken()`
  (asserted by test — the inverse of the FE-002..006 assertion). Future
  finance/erp console sections (Phase 5/6) inherit **this stated rule**: each
  new § 2.4.x binding declares its credential explicitly, against its
  producer's auth contract — not a guess copied from another domain.

- **Tenant model divergence**: wms resolves the operator's tenant from the
  **JWT `tenant_id` claim** (`=wms`) — **not** an `X-Tenant-Id` header (the
  GAP §§ 2.4.1–2.4.4 mechanism) and **not** a producer-side
  `admin_operators.tenant_id` lookup (the § 2.2/§ 2.6 GAP mechanism). The
  console therefore does **not** send `X-Tenant-Id` to wms; the tenant is
  carried implicitly inside the GAP OIDC access token. The console presents a
  wms session from the data-driven registry (§ 2.2): the `tenants[]` for
  `productKey=wms` drives which tenant the operator may select; when the
  operator's GAP token is not wms-eligible (no `wms` tenant and not a
  platform-scope `*` operator) the console **blocks the section** with an
  actionable "no wms-scoped access" state — **no cross-tenant call is ever
  fabricated**, and wms rejects cross-tenant producer-side regardless (never
  weakened here). The console sends wms's required `X-Request-Id` (the wms
  gateway echoes/generates it); `X-Actor-Id` is set by the wms gateway from
  the JWT — **the console does not forge it**.

- **Mutation discipline (alert-ack only)**:
  `POST /api/v1/admin/dashboard/alerts/{alertId}/acknowledge` requires an
  `Idempotency-Key` (UUID; producer scope `(Idempotency-Key, method, path)`,
  TTL 24h per `admin-service-api.md` § Idempotency Semantics) and
  `WMS_OPERATOR`+ role; the request body is **empty** (the producer sets
  `acknowledged_at = now()`, `acknowledged_by = X-Actor-Id`). It is
  **reason-free** — wms does **not** define `X-Operator-Reason` on this (or
  any) surface; **carrying GAP's § 2.4.1 `X-Operator-Reason` header over to
  the wms alert-ack is a header-matrix-drift defect** (asserted absent by
  test). The `Idempotency-Key` is `crypto.randomUUID()`, **stable across one
  user-confirmed action** (a retried/replayed confirmed ack reuses it →
  producer replays the cached response) and **freshly regenerated per a new
  confirmed attempt**. The action is **confirm-gated in the UI** (no
  one-click ack). **All § 1 dashboard reads are pure reads — they carry NO
  `Idempotency-Key`, NO `X-Operator-Reason`, NO body, and the test asserts
  the absence of every mutation artifact on them.**

- **Resilience (§ 2.5)**: the wms section reuses the registry/accounts-client
  `integration-heavy` discipline (AbortController hard timeout, structured
  logging, no unbounded default). The **wms error envelope is nested** —
  `{ "error": { "code", "message", "timestamp", … } }` (per
  `admin-service-api.md` § Error Envelope / `platform/error-handling.md`),
  **distinct from GAP's flat `{ code, message, timestamp }`**; the wms client
  MUST parse the wms (nested-`error`) shape — assuming GAP's flat shape
  mis-renders / crashes (asserted). Mapping: `401`/`UNAUTHORIZED` → forced
  **whole-session GAP re-login** (the GAP OIDC session expired — not a
  per-section degrade, no partial authed state); `403`/`FORBIDDEN`
  (role-insufficient — e.g. a `WMS_VIEWER` attempting the `WMS_OPERATOR`+
  ack, or a non-`WMS_ADMIN` hitting `projection-status`) → inline "not
  available to your role" (no crash, no re-login loop); `503` /
  `CONFLICT`-class `DUPLICATE_REQUEST` `503` / timeout → **only the wms
  section degrades** (the console shell + the GAP sections stay intact);
  `404` (alert/asn/inventory not found) / `400 VALIDATION_ERROR` (throughput
  range > 90 days, `to < from`) / `422 STATE_TRANSITION_INVALID` (alert
  already acknowledged) / `409 DUPLICATE_REQUEST` → inline actionable (no
  crash). **Read-model lag honesty**: wms dashboard responses may carry
  `X-Read-Model-Lag-Seconds` (set by the producer when the slowest
  contributing projection lags > 5 s); the console surfaces it as a
  **non-blocking "data may lag ~Ns" hint** — the section still renders
  (eventual-consistency honesty, not an error). The console MUST NOT
  aggressively auto-refetch around the lag (the read-model is eventually
  consistent by design; lag is surfaced, not polled-around).

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is the **GAP
  `admin-web` parity matrix**, finalized by TASK-PC-FE-006 (16/16 rows
  VERIFIED — see § 3). wms is **additive domain scope** federated by the
  console — **not** a GAP-`admin-web` parity-gate row. This binding adds
  **no** row to § 3 and
  changes **none**; the Phase 3 `admin-web`-retirement gate is unaffected.

- **Producer immutability**: this is a **cross-reference only**. Any change
  to the wms admin/dashboard producer contract is a wms project-internal
  spec-first change in `admin-service-api.md`; this section follows it, never
  redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: unlike §§ 2.4.1–2.4.4 (whose closing notes mark
> a § 3 parity line satisfiable), § 2.4.5 has **no** § 3 line. § 3 is the
> GAP `admin-web` absorption parity gate (FE-006-finalized); the wms section
> is a federated **domain** section, the first verification of the
> generalised per-domain integration contract, not a GAP parity capability.

#### 2.4.6 scm operations surface (TASK-PC-FE-008 — cross-reference, not a redefinition)

The **second non-GAP** per-domain binding of § 2.4 (ADR-MONO-013 Phase 4
slice 2 — the slice that **completes** Phase 4: `FE-007 wms` → `FE-008 scm`).
The console's `features/scm-ops` renders, **server-side and tenant-scoped**,
the scm gateway's existing **read-only** procurement-PO and
inventory-visibility surface. There is **no operator-mutation parity** for
scm at v1 (scm has no `admin-service` — deferred to scm v2 per
`gateway-public-routes.md`); this section is **strictly read-only**. The
producer contracts are **authoritative and unchanged** — this section only
states the consumer obligation and points at the owning scm specs. This
binding is the second instance that verifies ADR-MONO-013 § 3.3's "zero
retrofit" assumption across a non-GAP domain, and the proof that the
**per-domain credential rule defined in § 2.4.5 generalises** (it is reused
verbatim here, not re-derived).

- **Authoritative producers (owned by scm, do NOT redefine here —
  consumed read-only)**: scm
  [`procurement-api.md`](../../../scm-platform/specs/contracts/http/procurement-api.md)
  (PO read **only** — list + detail) and
  [`inventory-visibility-api.md`](../../../scm-platform/specs/contracts/http/inventory-visibility-api.md)
  (snapshot / per-SKU / staleness / nodes) — **unchanged, consumed only**.
  The console consumes exactly these endpoints (request/response/headers/
  error tables are canonical there):

  | # | Operation | Producer endpoint (scm spec §) | Kind |
  |---|---|---|---|
  | 1 | PO list / search | `GET /api/v1/procurement/po` (`procurement-api.md` § `GET /api/procurement/po`) | read |
  | 2 | PO detail | `GET /api/v1/procurement/po/{poId}` (`procurement-api.md` § `GET /api/procurement/po/{poId}`) | read |
  | 3 | inventory-visibility snapshot | `GET /api/v1/inventory-visibility/snapshot` (cross-node / single-node) | read |
  | 4 | inventory-visibility per-SKU | `GET /api/v1/inventory-visibility/sku/{sku}` (Redis-cached, `X-Cache` header) | read |
  | 5 | inventory-visibility staleness | `GET /api/v1/inventory-visibility/staleness` (FRESH/STALE/UNREACHABLE per node) | read |
  | 6 | inventory-visibility nodes | `GET /api/v1/inventory-visibility/nodes` (node list + status) | read |

  The scm PO **write** surface (`procurement-api.md`
  `POST /api/procurement/po`, `.../{poId}/submit|confirm|cancel`) and the
  procurement webhooks (`/webhooks/supplier-ack`, `/webhooks/asn`) are
  buyer/business mutations + machine ingress, **not** an operator-parity
  surface — **explicitly out of scope** (read-only section), not silently
  dropped. scm's other v2-deferred surfaces (suppliers / demand /
  logistics / settlement / `admin-service`) are likewise out of scope.

- **Per-domain credential selection — reuse of the § 2.4.5 rule (do NOT
  re-derive, do NOT diverge)**: the normative per-domain credential rule is
  **defined in § 2.4.5** (each § 2.4.x binding declares its own credential
  against its producer's auth contract; an implementer MUST NOT
  blanket-apply one domain's auth model to another). **scm reuses that
  rule with the same outcome as wms**: the scm gateway validates a GAP
  RS256 JWT (ADR-001) against GAP's JWKS, `tenant_id ∈ { scm, * }` enforced
  producer-side from the JWT claim (scm `gateway-public-routes.md`
  § *platform-console operator read consumer* — the merged TASK-SCM-BE-015
  reconciliation that sanctions the console as an external read consumer of
  the existing scm gateway capability: `AllowedIssuersValidator` +
  `TenantClaimValidator` + `X-Token-Type=user`). The credential is
  therefore the operator's **GAP `platform-console-web` OIDC access token**
  itself (`getAccessToken()`, the GAP-session HttpOnly cookie from FE-001),
  sent **directly** as `Authorization: Bearer <GAP OIDC access token>`
  server-side — **never** the GAP § 2.6 exchanged operator token
  (`getOperatorToken()`; that is GAP-domain-scoped — the #569
  trust-boundary invariant does **not** generalise to scm, exactly as
  § 2.4.5 states for wms). The console's `features/scm-ops` client uses
  `getAccessToken()` and **never** `getOperatorToken()` (asserted by test —
  the same shape as the FE-007 assertion; the cross-domain regression is
  extended so GAP = operator-token / wms = GAP-OIDC / scm = GAP-OIDC all
  hold in one place). **Tenant model**: scm resolves the tenant from the
  JWT `tenant_id` claim producer-side — the console does **not** send
  `X-Tenant-Id` (the tenant rides inside the GAP OIDC token, exactly the
  § 2.4.5 wms divergence). When the operator's GAP token is not
  scm-eligible (no `scm` tenant and not a platform-scope `*` operator) the
  console **blocks the section** with an actionable "no scm-scoped access"
  state — no cross-tenant call is ever fabricated; scm rejects cross-tenant
  producer-side regardless (`403 TENANT_FORBIDDEN`, never weakened here).

- **Read-only binding (normative — no mutation scaffolding at all)**: there
  is **no** mutation anywhere in this section. **No** `Idempotency-Key`,
  **no** `X-Operator-Reason`, **no** confirm dialogs, **no** PO write call
  (`/submit|/confirm|/cancel`), **no** procurement webhook. Carrying the
  FE-007 alert-ack mutation scaffolding **or** the GAP § 2.4.1 mutation
  scaffolding (reason/idempotency/destructive-confirm) into this section is
  a **defect** (asserted absent by test — same read discipline as
  §§ 2.4.2/2.4.4). Every scm call is a pure `GET`.

- **S5 visibility-warning surfacing (scm trait constraint, normative —
  contract obligation, not a UX nicety)**: every inventory-visibility
  response carries the producer envelope
  `meta.warning: "Not for procurement decisions (S5)"`
  (`inventory-visibility-api.md` — present on snapshot / sku / staleness /
  nodes). The console **MUST render that warning prominently on every
  inventory-visibility view** and **MUST NOT strip, hide, or de-emphasise
  it**. This is a deliberate scm domain constraint (the visibility
  read-model is explicitly *not* a procurement source of truth — S5); the
  warning is a **required, surfaced** field of the view-model, never an
  optional/discardable one (asserted by test on every inventory-visibility
  view). The PO read surface carries no such warning (procurement PO is the
  authoritative procurement record); the S5 obligation is
  inventory-visibility-specific.

- **Resilience (§ 2.5) — scm flat error envelope (DISTINCT from wms's
  nested shape and GAP's)**: the scm gateway/service error envelope is
  **flat** `{ code, message, details?, timestamp }` (per
  `procurement-api.md` / `inventory-visibility-api.md` § Error Codes /
  `platform/error-handling.md`) — **NOT** wms's nested
  `{ error: { code … } }` (§ 2.4.5) and not assumed-identical to GAP's. The
  scm client MUST parse the scm **flat** shape (a wms-nested parser would
  mis-render / crash — asserted). Mapping: `401 UNAUTHORIZED` → forced
  **whole-session GAP re-login** (the GAP OIDC session expired — not a
  per-section degrade, no partial authed state, consistent with
  FE-002..007); `403 TENANT_FORBIDDEN` / `403 PERMISSION_DENIED` /
  `403 FORBIDDEN` (token not scm-scoped or insufficient scope) → inline
  "not available / not scoped" (no crash, no re-login loop);
  `404 PO_NOT_FOUND` / `404 NODE_NOT_FOUND` / `400|422 VALIDATION_ERROR` →
  inline actionable (no crash); **`429 RATE_LIMIT_EXCEEDED`
  (`Retry-After: 1`)** → a **bounded backoff** + an inline
  "rate-limited, retrying" notice — the console MUST NOT auto-retry-storm
  into the gateway (one bounded retry honouring `Retry-After`, then surface
  the notice); `503 SERVICE_UNAVAILABLE` / `503 NODE_UNREACHABLE` /
  timeout / network → **only the scm section degrades** (the console shell
  + the GAP/wms sections stay intact). **Freshness honesty**: the
  inventory-visibility `X-Cache` header (`HIT|MISS|UNAVAILABLE` on the
  per-SKU read) and the `/staleness` per-node status (`FRESH|STALE|
  UNREACHABLE`) MUST be surfaced **honestly** (a `STALE`/`UNREACHABLE` node
  is shown as such, never hidden; the reachable nodes still render; the S5
  warning is shown regardless of node status). The console MUST NOT
  aggressively auto-refetch the rate-limited gateway. Unknown/future PO
  `status` or node `status` enum values degrade to a generic label —
  the consumer parser is tolerant and never throws on an unrecognised
  value.

- **Producer immutability**: this is a **cross-reference only**. Any change
  to the scm procurement / inventory-visibility producer contract is an scm
  project-internal spec-first change in `procurement-api.md` /
  `inventory-visibility-api.md`; this section follows it, never redefines
  it (§ 5 Change Rule). The scm-side acknowledgment of this console
  consumer is the merged scm `gateway-public-routes.md`
  § *platform-console operator read consumer* (TASK-SCM-BE-015) — the
  spec-first basis for this binding.

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is the **GAP
  `admin-web` parity matrix**, finalized by TASK-PC-FE-006 (16/16 rows; see
  § 3). scm is **additive domain scope** federated by the console — **not**
  a GAP-`admin-web` parity-gate row. This binding adds **no** row to § 3
  and changes **none**; the Phase 3 `admin-web`-retirement gate is
  unaffected. (This § 2.4.6 prose deliberately does **not** use the § 3.1
  per-row attestation marker phrase, so the FE-006 no-drift guard's count
  of that marker stays exactly 16.)

> **Not a § 3 parity row**: like § 2.4.5 and unlike §§ 2.4.1–2.4.4,
> § 2.4.6 has **no** § 3 line. § 3 is the GAP `admin-web` absorption parity
> gate (FE-006-finalized); the scm section is a federated **domain**
> section — the binding that **completes ADR-MONO-013 Phase 4** (wms +
> scm) and confirms the § 2.4.5 per-domain credential rule generalises.
> Phase 5/6 finance/erp console sections inherit this proven non-GAP
> contract (each new § 2.4.x binding declares its own credential against
> its producer, per the § 2.4.5 rule — not a guess copied from another
> domain).

#### 2.4.7 finance operations surface (TASK-PC-FE-009 — cross-reference, not a redefinition)

The **third non-GAP** per-domain binding of § 2.4 (ADR-MONO-013 Phase 5 —
the slice that **closes** the non-GAP federation cycle: `FE-007 wms` →
`FE-008 scm` → `FE-009 finance`). The console's `features/finance-ops`
renders, **server-side and tenant-scoped**, the finance `account-service`'s
existing **read-only** account + balances + transactions surface. There
is **no operator-mutation parity** for finance at v1 (finance v1 has
**no `admin-service`** — deferred to finance v2 per ADR-MONO-008 § D3 /
finance `PROJECT.md` v2 Service Map); this section is **strictly
read-only** (closest to the FE-008 scm precedent). The producer contract
is **authoritative and unchanged** — this section only states the
consumer obligation and points at the owning finance spec. This binding
is the **third** instance that verifies ADR-MONO-013 § 3.3's "zero
retrofit" assumption across a non-GAP domain, and the proof that the
**per-domain credential rule defined in § 2.4.5 generalises a second
time** (it is reused verbatim here, not re-derived).

- **Authoritative producer (owned by finance, do NOT redefine here —
  consumed read-only)**: finance
  [`account-api.md`](../../../finance-platform/specs/contracts/http/account-api.md)
  — **unchanged, consumed only**. The console consumes exactly these
  endpoints (request/response/headers/error tables are canonical there):

  | # | Operation | Producer endpoint (`account-api.md` §) | Kind |
  |---|---|---|---|
  | 1 | account by id | `GET /api/finance/accounts/{id}` (account + balances: status, currency, kycLevel) | read |
  | 2 | balances | `GET /api/finance/accounts/{id}/balances` (per-currency `ledger`/`available`/`held` as F5 money) | read |
  | 3 | transactions | `GET /api/finance/accounts/{id}/transactions` (paginated `?page=&size=&type=&status=`; `counterpartyAccountId?`, `reversalOfTransactionId?`) | read |

  **Honest finance read-surface constraint (recorded, not papered over)**:
  finance v1 exposes **no account list/search `GET`** — only
  `GET /accounts/{id}`. The section is therefore **account-id-driven**
  (operator supplies/selects an `accountId`; no searchable account index
  at v1). This is the *inverse* of the FE-002 GAP situation (GAP had
  no GET-by-id and composed a detail view from search; finance has
  GET-by-id but no list) — fabricating a non-existent finance
  list/search endpoint is **forbidden**. A list/search surface, if ever
  needed, is a finance producer-side spec-first change (out of scope
  here). The finance **write/mutation** surface (`POST /accounts`,
  `/kyc/upgrade`, `/holds`, `/holds/{holdId}/capture`,
  `/holds/{holdId}/release`, `/transfers`) is domain fund-movement /
  operator-domain mutation (`Idempotency-Key`, fintech F1) — **not** an
  operator-parity console surface; **explicitly out of scope** (not
  silently dropped). finance's v2 `admin-service` operator surface
  (reconciliation queue / KYC review / limits) is likewise out of
  scope (v2-deferred per ADR-MONO-008 § D3).

- **Per-domain credential selection — reuse of the § 2.4.5 rule (do NOT
  re-derive, do NOT diverge)**: the normative per-domain credential rule
  is **defined in § 2.4.5** (each § 2.4.x binding declares its own
  credential against its producer's auth contract; an implementer MUST
  NOT blanket-apply one domain's auth model to another). **finance
  reuses that rule with the same outcome as wms and scm**: the finance
  `account-service` validates a GAP RS256 JWT (ADR-001) against GAP's
  JWKS, `tenant_id ∈ { finance, * }` enforced producer-side from the
  JWT claim (finance
  [`gap-integration.md`](../../../finance-platform/specs/integration/gap-integration.md)
  § *platform-console Operator Read Consumer* — the merged
  TASK-FIN-BE-005 reconciliation that sanctions the console as an
  external operator GAP-token read consumer of the existing finance
  read surface: `AllowedIssuersValidator` + `TenantClaimValidator`
  + `X-Token-Type=user`). The credential is therefore the operator's
  **GAP `platform-console-web` OIDC access token** itself
  (`getAccessToken()`, the GAP-session HttpOnly cookie from FE-001),
  sent **directly** as `Authorization: Bearer <GAP OIDC access token>`
  server-side — **never** the GAP § 2.6 exchanged operator token
  (`getOperatorToken()`; that is GAP-domain-scoped — the #569
  trust-boundary invariant does **not** generalise to finance, exactly
  as § 2.4.5 states for wms and § 2.4.6 confirms for scm). The
  console's `features/finance-ops` client uses `getAccessToken()` and
  **never** `getOperatorToken()` (asserted by test — the same shape as
  the FE-007/FE-008 assertions; the cross-domain regression is
  extended so GAP = operator-token / wms = GAP-OIDC / scm = GAP-OIDC /
  **finance = GAP-OIDC** all hold in one place). **Tenant model**:
  finance resolves the tenant from the JWT `tenant_id` claim
  producer-side — the console does **not** send `X-Tenant-Id` (the
  tenant rides inside the GAP OIDC token, exactly the § 2.4.5 / § 2.4.6
  divergence). When the operator's GAP token is not finance-eligible
  (no `finance` tenant and not a platform-scope `*` operator) the
  console **blocks the section** with an actionable "no finance-scoped
  access" state — no cross-tenant call is ever fabricated; finance
  rejects cross-tenant producer-side regardless (`403 TENANT_FORBIDDEN`,
  never weakened here).

- **Read-only binding (normative — no mutation scaffolding at all)**:
  there is **no** mutation anywhere in this section. **No**
  `Idempotency-Key`, **no** `X-Operator-Reason`, **no** confirm
  dialogs, **no** finance write call (`POST /accounts`,
  `/kyc/upgrade`, `/holds`, `/holds/{holdId}/capture|release`,
  `/transfers`), **no** v2 `admin-service` surface. Carrying the
  FE-007 alert-ack mutation scaffolding **or** the GAP § 2.4.1
  mutation scaffolding (reason/idempotency/destructive-confirm) into
  this section is a **defect** (asserted absent by test — same read
  discipline as §§ 2.4.2/2.4.4/2.4.6). Every finance call is a pure
  `GET`. `IDEMPOTENCY_STORE_UNAVAILABLE` (503) is **mutation-only** per
  `account-api.md` — reads never hit it (recorded, not invented).

- **fintech producer obligations surfacing (finance domain constraint,
  normative — the finance analog of the scm § 2.4.6 S5 obligation —
  contract obligations, NOT UX niceties)**:

  - **F5 money shape (contract obligation, NOT a UX nicety)**: every
    money value is `{ amount: "<string-integer-minor-units>", currency }`
    with a per-currency minor-unit scale (KRW=0, USD=2; the
    `account-api.md` § Money clause is verbatim). The console **MUST**
    render money faithfully from the **string** minor-units
    (scale-correct display) and **MUST NOT** coerce it to a float / JS
    `Number` / lose precision anywhere (parse / store / arithmetic /
    display). This is a deliberate fintech domain constraint (F5) — the
    money view-model field is a **required, precision-preserving**
    element, never a float, never optional/discardable. A round-trip
    of a large minor-units amount (e.g. KRW `"1234567890123"`) MUST be
    **bit-exact** as a string. Asserted by test — there is **no**
    `Number(...)` / `parseFloat(...)` / `parseInt(...)` applied to an
    `amount` value anywhere in `features/finance-ops/`.

  - **confidential + F7 discipline**: finance is
    `data_sensitivity: confidential`; producer masks PII / regulated
    identifiers (F7). The console **MUST NOT** log balances,
    transactions, account refs, or the token (reinforced no-PII /
    no-token logging for confidential financial data — § 2.6 logging
    invariant extended). Tokens / PII / balances / transactions /
    account refs never appear in structured logs / state / events
    beyond render.

  - **honest regulated-state surfacing**: account status
    (`PENDING_KYC | ACTIVE | RESTRICTED | FROZEN | CLOSED`), KYC level,
    transaction status (incl. `FAILED | REVERSED`, sanction-driven),
    `reversalOfTransactionId`, `counterpartyAccountId?` — surfaced
    **honestly** (a `FROZEN` / `RESTRICTED` / `CLOSED` account or a
    `FAILED` / `REVERSED` txn is shown as such, never hidden /
    de-emphasised). Unknown / future account `status`, txn `status`,
    or txn `type` enum values degrade to a generic label, never a
    parser throw (same tolerant-parser discipline as scm node/PO
    status — § 2.4.6).

- **Resilience (§ 2.5) — finance flat error envelope (SAME flat shape
  as scm but a DISTINCT producer; NOT wms's nested shape)**: the
  finance error envelope is **flat** `{ code, message, details?,
  timestamp }`, success `{ data, meta: { timestamp } }` (per
  `account-api.md` § envelopes / `platform/error-handling.md`
  fintech). The wire shape is **byte-identical to scm's flat
  envelope** (same field names, same nesting) — but **finance is a
  DISTINCT producer** (different domain authority); the client MUST
  parse the finance flat shape against the **finance** error-code
  vocabulary, never blanket-assume scm/wms parser identity. A
  wms-nested `{ error: { code … } }` body MUST NOT be misparsed as
  finance (asserted by test — the finance code path does not
  accidentally go through a wms-nested parser). Mapping:
  `401 UNAUTHORIZED` → forced **whole-session GAP re-login** (the
  GAP OIDC session expired — not a per-section degrade, no partial
  authed state, consistent with FE-002..008);
  `403 TENANT_FORBIDDEN` / `403 PERMISSION_DENIED` /
  `403 FORBIDDEN` (token not finance-scoped or insufficient scope) →
  inline "not available / not scoped" (no crash, no re-login loop);
  `404 ACCOUNT_NOT_FOUND` → inline actionable "no such account" (no
  crash); `400 VALIDATION_ERROR` / `422` → inline actionable;
  `503 SERVICE_UNAVAILABLE` / timeout / network → **only the finance
  section degrades** (the console shell + the GAP / wms / scm
  sections stay intact). **finance has NO documented `429` /
  rate-limit response** (`account-api.md` § Error code → HTTP status
  has none — confirmed honestly); the console MUST NOT fabricate a
  backoff clause for finance (no `Retry-After` branch, no
  rate-limit-storm guard for finance; this is an honest difference
  from § 2.4.6 — recorded, **not cargo-culted from scm**, asserted
  absent by test). `IDEMPOTENCY_STORE_UNAVAILABLE` (503) is
  mutation-only and unreachable on the read surface (recorded).

- **Producer immutability**: this is a **cross-reference only**. Any
  change to the finance `account-service` read producer contract is a
  finance project-internal spec-first change in `account-api.md`;
  this section follows it, never redefines it (§ 5 Change Rule). The
  finance-side acknowledgment of this console consumer is the merged
  finance `gap-integration.md` § *platform-console Operator Read
  Consumer* (TASK-FIN-BE-005) — the spec-first basis for this
  binding.

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is the
  **GAP `admin-web` parity matrix**, finalized by TASK-PC-FE-006
  (16/16 rows; see § 3). finance is **additive domain scope**
  federated by the console — **not** a GAP-`admin-web` parity-gate
  row. This binding adds **no** row to § 3 and changes **none**; the
  Phase 3 `admin-web`-retirement gate is unaffected. (This § 2.4.7
  prose deliberately does **not** use the § 3.1 per-row attestation
  marker phrase, so the FE-006 no-drift guard's count of that marker
  stays exactly 16 — the FE-006 guard remains green after this
  binding.)

> **Not a § 3 parity row**: like § 2.4.5 / § 2.4.6 and unlike
> §§ 2.4.1–2.4.4, § 2.4.7 has **no** § 3 line. § 3 is the GAP
> `admin-web` absorption parity gate (FE-006-finalized); the finance
> section is a federated **domain** section — the **third** instance
> that verifies ADR-MONO-013 § 3.3's "zero retrofit" assumption across
> a non-GAP domain, and the second confirmation that the § 2.4.5
> per-domain credential rule generalises (wms → scm → **finance**).
> ADR-MONO-013 Phase 5 = COMPLETE; erp (Phase 6) inherits the proven
> non-GAP contract (third confirmation of § 3.3 zero-retrofit).

#### 2.4.8 erp operations surface (TASK-PC-FE-010 — cross-reference, not a redefinition)

The **fourth non-GAP** per-domain binding of § 2.4 (ADR-MONO-013
Phase 6 — the **first internal-system-primary** non-GAP federation,
adding the fourth confirmation across a fourth trait shape: FE-007
transactional, FE-008 integration-heavy, FE-009 regulated/transactional,
**FE-010 internal-system + transactional + audit-heavy**). The
console's `features/erp-ops` renders, **server-side and
tenant-scoped**, the erp `masterdata-service`'s existing **read-only**
master surface — 5 masters × {list, detail} = **10 GET endpoints**,
all supporting `?asOf=<ISO-8601>` point-in-time read (architecture.md
E3 with `[effectiveFrom, effectiveTo)` half-open semantics). There is
**no operator-mutation parity** for erp at v1 (erp v1 has **no
`admin-service`** — v2-deferred per ADR-MONO-016 § D3 / erp
`PROJECT.md` § v1 OUT); this section is **strictly read-only**,
closest to the FE-008 scm and FE-009 finance precedents. The producer
contract is **authoritative and unchanged** — this section only states
the consumer obligation and points at the owning erp spec. This
binding is the **fourth** instance that verifies ADR-MONO-013 § 3.3's
"zero retrofit" assumption across a non-GAP domain, and the
**third** confirmation that the per-domain credential rule defined in
§ 2.4.5 generalises (it is reused verbatim here, not re-derived).

- **Authoritative producer (owned by erp, do NOT redefine here —
  consumed read-only)**: erp
  [`masterdata-api.md`](../../../erp-platform/specs/contracts/http/masterdata-api.md)
  — **unchanged, consumed only**. The console consumes exactly these
  endpoints (request/response/headers/error tables are canonical
  there):

  | # | Master | List endpoint (`masterdata-api.md` §) | Detail endpoint | Notes |
  |---|---|---|---|---|
  | 1 | departments | `GET /api/erp/masterdata/departments` (`?asOf=&active=&parentId=&page=&size=`) | `GET /api/erp/masterdata/departments/{id}` (`?asOf=`) | hierarchical (`parentId`) |
  | 2 | employees | `GET /api/erp/masterdata/employees` (`?asOf=&active=&departmentId=&costCenterId=&page=&size=`) | `GET /api/erp/masterdata/employees/{id}` (`?asOf=`) | cross-refs department / jobGrade / costCenter |
  | 3 | job-grades | `GET /api/erp/masterdata/job-grades` (`?asOf=&active=&page=&size=`, ordered by `displayOrder` asc) | `GET /api/erp/masterdata/job-grades/{id}` (`?asOf=`) | leaf |
  | 4 | cost-centers | `GET /api/erp/masterdata/cost-centers` (`?asOf=&active=&departmentId=&page=&size=`) | `GET /api/erp/masterdata/cost-centers/{id}` (`?asOf=`) | references department |
  | 5 | business-partners | `GET /api/erp/masterdata/business-partners` (`?asOf=&active=&partnerType=&page=&size=`) | `GET /api/erp/masterdata/business-partners/{id}` (`?asOf=`) | confidential financial details (paymentTerms) |

  **Honest erp read-surface constraint (recorded, not papered over —
  DIFFERENT from finance)**: erp v1 exposes **both list and detail**
  GETs for every master (10 endpoints), **AND** supports
  `?asOf=<past>` point-in-time read on all of them. This is the
  **inverse** of the FE-009 finance situation (finance had
  `GET /accounts/{id}` only, account-id-driven; erp has full
  list+detail with effective-dating). The honest erp section is
  therefore **list-driven** (browsable index for each master,
  drillable into detail) **with explicit effective-dating** (an
  operator can supply `?asOf=<ISO-8601>` to view historical state —
  first-class UI surface for the E3 invariant). Force-fitting the
  finance account-id-driven shape onto erp is **forbidden**. The erp
  **write/mutation** surface (16 endpoints — 5×`POST` create / 5×`PATCH`
  / 5×`POST /retire` / 1×`POST .../move-parent`) is operator-domain
  mutation requiring `Idempotency-Key` (E1 / transactional T1) +
  role-scoped E6 fail-CLOSED authorization + append-only E8 audit —
  **not** an operator-parity console surface at v1; **explicitly out
  of scope** (not silently dropped). erp's v2 `approval-service` /
  `read-model-service` / future `admin-service` / `notification-service`
  / `permission-service` are likewise out of scope (all v2-deferred
  per ADR-MONO-016 § D3 / erp `PROJECT.md` § v1 OUT).

- **Per-domain credential selection — reuse of the § 2.4.5 rule (do
  NOT re-derive, do NOT diverge)**: the normative per-domain
  credential rule is **defined in § 2.4.5** (each § 2.4.x binding
  declares its own credential against its producer's auth contract;
  an implementer MUST NOT blanket-apply one domain's auth model to
  another). **erp reuses that rule with the same outcome as wms / scm /
  finance**: the erp `masterdata-service` validates a GAP RS256 JWT
  (ADR-001) against GAP's JWKS, `tenant_id ∈ { erp, * }` enforced
  producer-side from the JWT claim (erp
  [`gap-integration.md`](../../../erp-platform/specs/integration/gap-integration.md)
  § *platform-console Operator Read Consumer* — the merged
  TASK-ERP-BE-002 reconciliation that sanctions the console as an
  external operator GAP-token read consumer of the existing erp read
  surface; the erp "internal-only 경계" #6 / E7 narrative is
  **clarified, not weakened** — boundary scopes non-GAP-SSO traffic;
  GAP-authenticated console traffic routed through internal Traefik
  is within the SSO boundary). The credential is therefore the
  operator's **GAP `platform-console-web` OIDC access token** itself
  (`getAccessToken()`, the GAP-session HttpOnly cookie from FE-001),
  sent **directly** as `Authorization: Bearer <GAP OIDC access token>`
  server-side — **never** the GAP § 2.6 exchanged operator token
  (`getOperatorToken()`; that is GAP-domain-scoped — the #569
  trust-boundary invariant does **not** generalise to erp, exactly
  as § 2.4.5 states for wms, § 2.4.6 confirms for scm, and § 2.4.7
  confirms again for finance). The console's `features/erp-ops`
  client uses `getAccessToken()` and **never** `getOperatorToken()`
  (asserted by test — the same shape as the FE-007/FE-008/FE-009
  assertions; the cross-domain regression is extended so
  GAP = operator-token / wms = GAP-OIDC / scm = GAP-OIDC /
  finance = GAP-OIDC / **erp = GAP-OIDC** all hold in one place — 5
  domains). **Tenant model**: erp resolves the tenant from the JWT
  `tenant_id` claim producer-side — the console does **not** send
  `X-Tenant-Id` (the tenant rides inside the GAP OIDC token, exactly
  the § 2.4.5 / § 2.4.6 / § 2.4.7 divergence). When the operator's
  GAP token is not erp-eligible (no `erp` tenant and not a
  platform-scope `*` operator) the console **blocks the section**
  with an actionable "no erp-scoped access" state — no cross-tenant
  call is ever fabricated; erp rejects cross-tenant producer-side
  regardless (`403 TENANT_FORBIDDEN`, never weakened here).

- **Read-only binding (normative — no mutation scaffolding at all)**:
  there is **no** mutation anywhere in this section. **No**
  `Idempotency-Key`, **no** `X-Operator-Reason`, **no** confirm
  dialogs, **no** erp write call (`POST .../departments`,
  `PATCH .../{id}`, `POST .../retire`, `POST .../move-parent`,
  etc.), **no** v2 `approval-service` / `read-model-service` /
  future `admin-service` / `notification-service` / `permission-service`
  surface. Carrying the FE-007 alert-ack mutation scaffolding **or**
  the GAP § 2.4.1 mutation scaffolding (reason / idempotency /
  destructive-confirm) into this section is a **defect** (asserted
  absent by test — same read discipline as §§ 2.4.2 / 2.4.4 / 2.4.6 /
  2.4.7). Every erp call is a pure `GET`. The producer-side
  `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_KEY_CONFLICT` (409) and
  `MASTERDATA_DUPLICATE_KEY` / `MASTERDATA_REFERENCE_VIOLATION` /
  `MASTERDATA_PARENT_CYCLE` (409) + `MASTERDATA_EFFECTIVE_PERIOD_INVALID`
  (422) are **mutation-only** per `masterdata-api.md` — reads never
  hit them (recorded, not invented; surface them only if/when
  surfacing producer audit history downstream).

- **erp internal-system producer obligations surfacing (erp domain
  constraint, normative — the erp analog of the scm § 2.4.6 S5 /
  finance § 2.4.7 F5/F7 obligations — contract obligations, NOT UX
  niceties)**:

  - **E2 effective-dating + E3 point-in-time (UX-first-class, not
    buried)**: every master detail surfaces `effectivePeriod:
    { effectiveFrom, effectiveTo }` **honestly** —
    `effectiveTo: null` (open-ended / currently active) and
    `effectiveTo: <past>` (retired) rendered **visually distinct**
    (retired rows clearly de-emphasised but **not hidden**). The
    console **MUST** expose the `?asOf=<ISO-8601>` query as a
    first-class user-controllable input (an `<AsOfPicker>` shared
    component / URL parameter), and the rendered list/detail
    **MUST** correctly reflect the state-at-that-instant (the E3
    invariant). Substituting "current state" for `?asOf=<past>` is
    the core erp UX defect to avoid — asserted by test (the
    producer client receives `asOf=<past>` verbatim AND the
    rendered state matches the asOf-instant response, NOT the
    current-state response).

  - **E1 reference integrity surfacing**: when the console renders
    a master detail referencing other masters (e.g. employee →
    department / jobGrade / costCenter; cost-center → department;
    department → parent), broken / retired references are
    surfaced **honestly** (a retired-reference badge or similar,
    NOT silently sanitized). When the producer rejects a mutation
    due to E1 (which the console doesn't issue, but might surface
    as a historic audit reason), the producer message is rendered
    faithfully.

  - **confidential + audit-heavy discipline**: erp is
    `data_sensitivity: confidential`; producer enforces it. The
    console **MUST NOT** log employee PII (names / contact info),
    business-partner financial identifiers (`paymentTerms`
    `termDays`/`method`, banking refs), cost-center sensitive
    attributes, or the token (reinforced no-PII / no-token logging
    for confidential internal master data). The architecture E8
    (append-only `audit_log`) is the producer's authority on change
    history; the console renders that history (when surfaced)
    faithfully, never doctored.

  - **honest enum / status surfacing**: master `status` enums
    (`ACTIVE` / `RETIRED` and any future addition) + employee
    employment status (`EMPLOYED` / `ON_LEAVE` / `SEPARATED` per
    architecture.md) — surfaced **honestly** (a `RETIRED` master
    is shown as such, never hidden; a `SEPARATED` employee is
    rendered as such, never filtered). Unknown / future enum
    values degrade to a generic label, never a parser throw (same
    tolerant-parser discipline as scm node/PO status — § 2.4.6 —
    and finance account/txn status — § 2.4.7).

- **Resilience (§ 2.5) — erp flat error envelope (SAME flat shape
  as scm and finance but a DISTINCT producer; NOT wms's nested
  shape)**: the erp error envelope is **flat** `{ code, message,
  details?, timestamp }`, success `{ data, meta: { timestamp,
  page?, size?, totalElements? } }` (per `masterdata-api.md` §
  envelopes / `platform/error-handling.md` erp section). The wire
  shape is **byte-identical to scm's and finance's flat envelope**
  (same field names, same nesting) — but **erp is a DISTINCT
  producer** (different domain authority); the client MUST parse
  the erp flat shape against the **erp** error-code vocabulary,
  never blanket-assume scm/finance/wms parser identity. A
  wms-nested `{ error: { code … } }` body MUST NOT be misparsed as
  erp (asserted by test — the erp code path does not accidentally
  go through a wms-nested parser; each domain owns its own parser
  even when the wire shape is identical). Mapping:
  `401 UNAUTHORIZED` → forced **whole-session GAP re-login** (the
  GAP OIDC session expired — not a per-section degrade, no
  partial authed state, consistent with FE-002..009);
  `403 TENANT_FORBIDDEN` / `403 FORBIDDEN` /
  `403 DATA_SCOPE_FORBIDDEN` / `403 PERMISSION_DENIED` /
  `403 EXTERNAL_TRAFFIC_REJECTED` (token not erp-scoped /
  insufficient scope / outside operator's organization subtree per
  E6 / external traffic at the internal-only boundary per E7) →
  inline "not available / not scoped" (no crash, no re-login loop);
  `404 MASTERDATA_NOT_FOUND` → inline actionable "no such record"
  (no crash); `400 VALIDATION_ERROR` / `422` → inline actionable;
  `503 SERVICE_UNAVAILABLE` / timeout / network → **only the erp
  section degrades** (the console shell + the GAP / wms / scm /
  finance sections stay intact). The mutation-only codes
  (`409 MASTERDATA_DUPLICATE_KEY` / `MASTERDATA_REFERENCE_VIOLATION` /
  `MASTERDATA_PARENT_CYCLE` / `IDEMPOTENCY_KEY_CONFLICT` /
  `CONCURRENT_MODIFICATION`; `400 IDEMPOTENCY_KEY_REQUIRED`;
  `422 MASTERDATA_EFFECTIVE_PERIOD_INVALID`) are unreachable on
  the read surface and recorded for cross-reference completeness
  only (the console MUST NOT special-case them on a GET path).
  **erp has NO documented `429` / rate-limit response**
  (`masterdata-api.md` § Error code → HTTP status has none —
  confirmed honestly, identical to finance § 2.4.7); the console
  MUST NOT fabricate a backoff clause for erp (no `Retry-After`
  branch, no rate-limit-storm guard for erp; this is an honest
  difference from § 2.4.6 — recorded, **not cargo-culted from
  scm**, asserted absent by test).

- **Producer immutability**: this is a **cross-reference only**.
  Any change to the erp `masterdata-service` read producer
  contract is an erp project-internal spec-first change in
  `masterdata-api.md`; this section follows it, never redefines
  it (§ 5 Change Rule). The erp-side acknowledgment of this
  console consumer is the merged erp `gap-integration.md` §
  *platform-console Operator Read Consumer* (TASK-ERP-BE-002) —
  the spec-first basis for this binding.

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is
  the **GAP `admin-web` parity matrix**, finalized by
  TASK-PC-FE-006 (16/16 rows; see § 3). erp is **additive domain
  scope** federated by the console — **not** a GAP-`admin-web`
  parity-gate row. This binding adds **no** row to § 3 and changes
  **none**; the Phase 3 `admin-web`-retirement gate is unaffected.
  (This § 2.4.8 prose deliberately does **not** use the § 3.1
  per-row attestation marker phrase, so the FE-006 no-drift
  guard's count of that marker stays exactly 16 — the FE-006
  guard remains green after this binding.)

> **Not a § 3 parity row**: like § 2.4.5 / § 2.4.6 / § 2.4.7 and
> unlike §§ 2.4.1–2.4.4, § 2.4.8 has **no** § 3 line. § 3 is the
> GAP `admin-web` absorption parity gate (FE-006-finalized); the
> erp section is a federated **domain** section — the **fourth**
> instance that verifies ADR-MONO-013 § 3.3's "zero retrofit"
> assumption across a non-GAP domain, and the **first
> internal-system-primary** confirmation (wms transactional →
> scm integration-heavy → finance regulated/transactional →
> **erp internal-system + transactional + audit-heavy**) — four
> trait shapes, zero retrofit, zero re-derivation. ADR-MONO-013
> Phase 6 = COMPLETE; Phase 7 (`console-bff` + cross-domain
> dashboards) gate is **ungated to 5/5 domains live**
> (GAP + wms + scm + finance + erp).

#### 2.4.9 `console-bff` server-side composition surface (TASK-PC-BE-001 — first BFF surface owner, **not** a cross-reference)

The **first BFF-owned** entry in § 2.4 (ADR-MONO-013 Phase 7, governed by
[ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)
ACCEPTED 2026-05-20). Unlike §§ 2.4.1–2.4.8, **this section is not a
cross-reference to a producer in another project** — it is the contract surface
that the **new `console-bff` service** (this project, `apps/console-bff/`) owns
and exposes. The producer of these routes is `console-bff` itself; the
**consumed-from-this-section** legs (e.g. `GET /api/admin/accounts` count,
wms inventory health, …) remain authoritatively owned by their respective
domains and are **NOT redefined here** (ADR-MONO-017 D3.A / § 3.3 "zero
retrofit", fifth confirmation across the portfolio).

This section is the **architectural frame** authored by the skeleton task
(TASK-PC-BE-001). The first concrete composition route — the MVP "Operator
Overview" cross-domain dashboard (ADR-MONO-017 D8) — is added by the
post-skeleton task TASK-PC-FE-011 via an additive § 2.4.9.1 sub-section.
The skeleton task itself adds **only** the operational `GET /actuator/health`
contract row (smoke-target for the IT harness and Traefik probe).

##### Hard invariants this BFF surface inherits (HARD INVARIANT — ADR-MONO-017 D4, byte-verbatim)

> **The per-domain credential rule defined in §§ 2.4.5 / 2.4.6 / 2.4.7 / 2.4.8
> (and the § 2.6 RFC 8693 exchanged operator token for the GAP-domain leg) is
> a HARD INVARIANT. `console-bff` is the rule's *credential dispatcher*, never
> its rewriter.** Re-introducing the rejected ADR-MONO-017 D4.B (single
> unified BFF token) or D4.C (operator-token-only across all domains) on any
> future § 2.4.9.X composition route is a contract defect.

- **Inbound auth (console-web → console-bff)**: server-side only — the
  browser never reaches the BFF. console-web's App-Router server route holds
  the two tokens already established at login (per
  [`console-web/architecture.md`](../services/console-web/architecture.md) +
  FE-002a) and forwards them to `console-bff` on every call:
  - `Authorization: Bearer <gap-oidc-access-token>` — the inbound principal,
    RS256 / JWKS = GAP (standard OAuth2 Resource Server validation: issuer
    / audience / exp / signature),
  - `X-Operator-Token: <rfc8693-operator-token>` — request-scoped, available
    to outbound clients via a `OperatorCredentialContext`; the inbound auth
    filter MUST NOT treat it as the inbound principal,
  - `X-Tenant-Id: <active-tenant>` — operator's selected active tenant; absent
    is fail-closed `400 NO_ACTIVE_TENANT` before any outbound call.
- **Outbound per-domain credential dispatch (verbatim from §§ 2.4.5/6/7/8 +
  § 2.6)**:

  | Outbound domain | Credential | Source on this request |
  |---|---|---|
  | GAP (`/api/admin/**`) | RFC 8693 exchanged operator token (§ 2.6) | inbound `X-Operator-Token` |
  | wms (`/api/wms/**`) | GAP OIDC access token (§ 2.4.5) | inbound `Authorization` |
  | scm (`/api/scm/**`) | GAP OIDC access token (§ 2.4.6) | inbound `Authorization` |
  | finance (`/api/finance/**`) | GAP OIDC access token (§ 2.4.7) | inbound `Authorization` |
  | erp (`/api/erp/**`) | GAP OIDC access token (§ 2.4.8) | inbound `Authorization` |

  The BFF **never** falls back from one credential to another (#569 invariant
  preserved). The BFF **never** mints its own token. The BFF **never** rewrites
  or expands the per-domain producer-side tenant enforcement (D6.A — producer
  authority preserved).
- **Tenant pass-through (D6.A)**: `X-Tenant-Id` is forwarded **verbatim** on
  every outbound leg; each producer's `TenantClaimValidator`
  (`tenant_id ∈ {<domain>,*}`) remains the authoritative gate. The BFF
  performs no tenant re-derivation, no widening, no central gate (D6.B
  rejection).
- **Read-only at MVP — mutations forbidden**: every Phase 7 dashboard at MVP
  is composition of **reads** (D3.A). No § 2.4.9.X composition route is a
  mutation; therefore **no** `Idempotency-Key` / `X-Operator-Reason` /
  destructive-confirm scaffolding applies at the BFF layer. Adding a
  mutation surface requires a fresh ADR amendment to ADR-MONO-017.
- **No producer retrofit (D3.A / § 3.3 zero retrofit fifth confirmation)**:
  every § 2.4.9.X composition route fans out across **existing** per-domain
  read endpoints unchanged. Aggregating producer endpoints per domain (`/summary`
  / `/dashboard-card`) — ADR-MONO-017 D3.B rejection — are NOT introduced.

##### Resilience (D5.A — per-domain CB inherited from § 2.5)

- Each outbound leg is governed by a circuit-breaker keyed by `(domain, route)`
  via `libs/java-web` Resilience4j primitives; a wms outage does not open the
  breaker for scm.
- **Aggregation degrade discipline** — partial-failure composition rendering:
  every responsive leg's data + per-failed-leg
  `{ status: "degraded", domain, reason }` card. **All-down still returns 200
  with an all-degraded envelope** — composition routes never blank the
  dashboard. ADR-MONO-017 D5.B (all-or-nothing 503) is rejected.
- **`401` discipline (cross-leg)**: `401` on **any** outbound leg surfaces as
  whole-composition `401 TOKEN_INVALID` to console-web (auth is **not** a
  per-card degrade — tokens are shared across legs from the same inbound
  request; mirrors § 2.4.4 D3 invariant).
- **`403` discipline (per-leg)**: `403 PERMISSION_DENIED` /
  `403 TENANT_FORBIDDEN` on a leg renders as a per-card "scope denied"
  placeholder (classification `forbidden`, distinct from degrade
  `degraded`). Mirrors the per-card isolation of § 2.4.4.

##### Observability (D7.A — Vector + VictoriaMetrics reuse, [ADR-MONO-006](../../../../docs/adr/ADR-MONO-006-observability-stack.md))

Mandatory metric set every § 2.4.9.X composition route MUST emit (no
opt-out):

- `bff_fanout_latency_seconds{domain,route}` — histogram per outbound leg.
- `bff_fanout_errors_total{domain,route,code}` — counter per outbound leg
  failure classification (`5xx`, `timeout`, `circuit_open`,
  `tenant_forbidden`, `permission_denied`).
- `bff_aggregation_degrade_count_total{dashboard,degraded_domain}` — counter
  whenever a composition response renders a degraded leg.

OTel `traceparent` propagates inbound → every outbound leg; per-leg span
carries `bff.domain` + `bff.route` attributes (per-domain attribution in the
trace UI). ADR-MONO-017 D7.B (BFF-level aggregate-only metrics) is rejected —
operator must be able to diagnose which leg degraded.

##### Logging discipline (inherited)

Tokens (inbound `Authorization`, `X-Operator-Token`, all outbound bearer
values), PII (account ids, masked IPs, operator emails, money minor-units
strings, employee / business-partner financial fields) MUST NOT appear in
logs. Inherits the § 2.6 logging invariant + the per-domain producer
obligations (e.g. finance F7, erp E7).

##### Edge routing (Local Network Convention)

`console-bff` registers Traefik labels for hostname `console-bff.local` (no
`PORT_PREFIX`, hostname-based routing; the [`infra/traefik/`](../../../../infra/traefik/)
shared stack). The hostname is **internal-only** — `console-web` server-side
routes call it server-side; the browser never reaches it. The BFF therefore
does **not** route through a `gateway-service` (there is none in
`platform-console`); the trust boundary at the Traefik front is the same
`console.local` host that fronts `console-web`. This is identical to the
structural exception `console-web` itself takes from `rest-api.md`'s
"all external traffic enters through gateway-service" requirement.

##### v1 (skeleton task TASK-PC-BE-001) endpoint surface

| # | Method / Path | Purpose | Auth | Producer |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | Liveness / readiness probe; Traefik health-check target; smoke-target for the IT harness | None (Spring Boot Actuator `health` default — unauthenticated readiness only; no detailed components surfaced beyond `status`) | `console-bff` |

> **Phase 7 MVP "Operator Overview" composition route** is added by
> **TASK-PC-FE-011** via the additive `§ 2.4.9.1` sub-section below.

> **Not a § 3 parity row**: like §§ 2.4.5–2.4.8, this BFF section has **no**
> § 3 line. § 3 is the GAP `admin-web` absorption parity gate (FE-006
> finalized, 16 rows, immutable until a future ADR-amendment); composition
> routes are **additive** to the operator surface, never replace a parity
> row.

#### 2.4.9.1 `GET /api/console/dashboards/operator-overview` — MVP "Operator Overview" composition route (TASK-PC-FE-011)

The **first concrete `§ 2.4.9.X` composition route** on top of the
[`console-bff`](../services/console-bff/architecture.md) skeleton landed by
TASK-PC-BE-001. Governed by [ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)
§ D8 (Phase 7 MVP = 1 "Operator Overview"). This sub-section is **additive**
to § 2.4.9 — all hard invariants, auth flow, resilience, observability,
logging, and edge-routing constraints declared in § 2.4.9 apply verbatim
and are **not re-derived** here. ADR-MONO-013 § 3.3 "zero retrofit" — **sixth
confirmation** (Phase 2/4/5/6/7-skeleton/7-MVP across the portfolio).

##### Surface

| # | Method / Path | Purpose | Auth | Producer |
|---|---|---|---|---|
| 1 | `GET /api/console/dashboards/operator-overview` | Single composed cross-domain dashboard envelope; one card per backend domain (GAP + wms + scm + finance + erp); each card carries the per-leg outcome (`ok` / `degraded` / `forbidden`) per § 2.4.9 D5.A discipline | `Authorization: Bearer <gap-oidc-access-token>` (inbound principal, RS256 / GAP issuer) + `X-Operator-Token: <rfc8693-operator-token>` (request-scoped, for GAP leg) + `X-Tenant-Id: <active-tenant>` (forwarded verbatim) — all three set server-side by `console-web` 's SSR route, never by the browser. Absent any of the three → fail-closed (`400 NO_ACTIVE_TENANT` if `X-Tenant-Id` absent; otherwise `401 TOKEN_INVALID`) before any outbound leg | `console-bff` |

> The route is **GET only — read-only**. ADR-MONO-017 § 2.4.9 hard invariant
> "no mutation at MVP" applies; therefore `Idempotency-Key`,
> `X-Operator-Reason`, destructive-confirm scaffolding, and any
> POST/PUT/PATCH/DELETE method MUST NOT appear on this route or any future
> `§ 2.4.9.X` MVP dashboard route. Adding a mutation surface requires a
> fresh ADR amendment to ADR-MONO-017.

##### Composed producers (5 domains, reuse-only — D3.A / § 3.3 zero retrofit)

The composition route fans out across **existing** per-domain read
endpoints — one card per domain, **no producer retrofit**. The producer
contracts are authoritative in their respective files and are **not
redefined here**:

| # | Card | Composed producer endpoint | Domain credential (§ 2.4.9 D4) | Producer spec § (authoritative) | Read content surfaced |
|---|---|---|---|---|---|
| 1 | accounts summary | `GET /api/admin/accounts?page=0&size=1` (page total snapshot) | RFC 8693 exchanged **operator** token (§ 2.6) — `getOperatorToken()` | GAP [`admin-api.md`](../../../global-account-platform/specs/contracts/http/admin-api.md) § Accounts (already bound by § 2.4.1 / FE-002 + the composed-overview pattern of § 2.4.4 / FE-005) | total account count (snapshot) |
| 2 | wms inventory health | `GET /api/v1/admin/dashboard/inventory` (snapshot) | **GAP OIDC access token** — `getAccessToken()` (per § 2.4.5 verbatim) | wms [`admin-service-api.md`](../../../wms-platform/specs/contracts/http/admin-service-api.md) § 1.1 Dashboard / Read-Model (already bound by § 2.4.5 / FE-007) | inventory snapshot health summary (stock total, alert count) |
| 3 | scm procurement / inventory | `GET /api/scm/inventory/visibility` (snapshot) — the existing scm gateway public read (§ 2.4.6 / FE-008) | **GAP OIDC access token** — `getAccessToken()` (per § 2.4.6 verbatim) | scm [`gateway-public-routes.md`](../../../scm-platform/specs/contracts/http/gateway-public-routes.md) § *platform-console operator read consumer* (already bound by § 2.4.6 / FE-008) | inventory visibility snapshot (the producer-meta-warning S5 "Not for procurement decisions" MUST surface as a non-blocking hint, per § 2.4.6 invariant) |
| 4 | finance balance health | `GET /api/finance/accounts/{operatorDefaultAccountId}/balances` (single account) | **GAP OIDC access token** — `getAccessToken()` (per § 2.4.7 verbatim) | finance [`account-api.md`](../../../finance-platform/specs/contracts/http/account-api.md) § Balances (already bound by § 2.4.7 / FE-009) | balance snapshot for the operator's default account; **honest constraint** (per § 2.4.7) — finance v1 has no list/search GET → an `operatorDefaultAccountId` resolution mechanism is required (registry-side or operator-profile-side; spec-first decided **at MVP impl** — see § Implementation guidance); if absent → that card renders `forbidden` (not a crash) |
| 5 | erp masterdata snapshot | `GET /api/erp/masterdata/departments?active=true&page=0&size=1` (page total snapshot, asOf=now implicit) | **GAP OIDC access token** — `getAccessToken()` (per § 2.4.8 verbatim) | erp [`masterdata-api.md`](../../../erp-platform/specs/contracts/http/masterdata-api.md) § Departments (already bound by § 2.4.8 / FE-010) | active department count (snapshot, asOf=now — E3 effective-dating implicit) |

**Producer immutability**: the 5 producer contracts above are **byte-unchanged
spec-side and impl-side** (ADR-MONO-017 § 3.3 sixth confirmation). The
console-bff composition use-case calls the existing GETs verbatim;
no `/summary` / `/dashboard-card` aggregating endpoint is added to any
producer (D3.B rejection).

##### Response schema (`200 OK`)

```json
{
  "asOf": "2026-05-20T10:30:00Z",
  "cards": [
    { "domain": "gap",     "status": "ok",         "data": { "accountCount": 12345 } },
    { "domain": "wms",     "status": "ok",         "data": { "inventorySnapshot": { … } } },
    { "domain": "scm",     "status": "degraded",   "reason": "DOWNSTREAM_ERROR" },
    { "domain": "finance", "status": "forbidden",  "reason": "TENANT_FORBIDDEN" },
    { "domain": "erp",     "status": "ok",         "data": { "activeDepartmentCount": 87 } }
  ]
}
```

- `asOf`: composition request server-side timestamp (ISO-8601 UTC). Operators see "data as-of HH:MM:SS" in the UI.
- `cards[]`: **exactly 5 entries** in **fixed order** `[gap, wms, scm, finance, erp]` (UI rendering ordering invariant; never reordered by status).
- `cards[i].status` ∈ `{ "ok", "degraded", "forbidden" }`:
  - `ok` → `data` is the card's composed payload (domain-specific shape, declared per row in the producer endpoint above).
  - `degraded` → `reason` ∈ `{ "DOWNSTREAM_ERROR", "TIMEOUT", "CIRCUIT_OPEN" }`; `data` absent. Card renders "data unavailable, retry pending" placeholder.
  - `forbidden` → `reason` ∈ `{ "PERMISSION_DENIED", "TENANT_FORBIDDEN", "MISSING_PREREQUISITE" }` (last covers e.g. finance's `operatorDefaultAccountId` absent); `data` absent. Card renders "not available to your role / tenant" placeholder.
- **All-down envelope**: every leg can return non-`ok` simultaneously — the route still emits `200` with all 5 cards in `degraded`/`forbidden` states. The route NEVER emits `503` / blanks the response (D5.A discipline; D5.B rejection re-affirmed).

##### Error envelope (composition-level errors, NOT per-leg)

For the inbound-validation errors **before** any outbound leg fires
(absent tenant / token), the standard console-bff error envelope applies
(`GlobalExceptionHandler` scope = `adapter.inbound.web`, per
[`console-bff/architecture.md`](../services/console-bff/architecture.md)):

| Status | Code | Cause |
|---|---|---|
| `400` | `NO_ACTIVE_TENANT` | `X-Tenant-Id` absent or blank |
| `401` | `TOKEN_INVALID` | `Authorization` bearer absent / invalid; or per § 2.4.4 D3 — `401` from any outbound leg surfaces as composition-level `401` (cross-leg discipline: tokens are shared across legs from the same inbound request; a 401 on one is a 401 for all) |
| `503` | reserved | NEVER emitted at MVP — D5.B is rejected |

##### Auth flow (verbatim from § 2.4.9, restated for cross-reference only)

- **Inbound** (console-web SSR → console-bff): `Authorization` (GAP OIDC access token, inbound principal) + `X-Operator-Token` (RFC 8693 exchanged operator token, request-scoped via `OperatorCredentialContext`) + `X-Tenant-Id` (operator's selected active tenant). The browser **never** reaches console-bff directly.
- **Outbound** (console-bff → each domain): per-domain credential dispatch (§ 2.4.9 D4 table, 5-row sealed selector — `GAP → OperatorToken`, `{wms,scm,finance,erp} → GapOidcAccessToken`). NO fallback path. NO unified token. NO operator-token-only across all domains. `X-Tenant-Id` forwarded verbatim on every leg; producer's `TenantClaimValidator` is the authoritative gate.

##### Resilience (verbatim from § 2.4.9, restated for cross-reference only)

- Per-leg circuit-breaker keyed by `(domain, route)` via `libs/java-web` Resilience4j.
- Per-leg hard timeout bounded so the composition's total fan-out latency budget is not exceeded.
- Aggregation degrade: every responsive leg's `data` + per-failed-leg `{ status: "degraded" / "forbidden", reason }` card.
- All-down still returns 200 with all-degraded/forbidden envelope. D5.B (all-or-nothing 503) is forbidden.

##### Observability (verbatim from § 2.4.9 + MVP-specific label values)

The 3 mandatory BFF metric families emit per-leg samples with the
following label values for this route:

| Metric | Labels per emit |
|---|---|
| `bff_fanout_latency_seconds{domain,route}` | `domain` ∈ `{gap,wms,scm,finance,erp}` × `route` = `"operator-overview"` |
| `bff_fanout_errors_total{domain,route,code}` | same `domain`/`route` + `code` ∈ `{5xx,timeout,circuit_open,tenant_forbidden,permission_denied,missing_prerequisite}` |
| `bff_aggregation_degrade_count_total{dashboard,degraded_domain}` | `dashboard = "operator-overview"` + `degraded_domain` ∈ `{gap,wms,scm,finance,erp}` (one increment per degraded/forbidden card per response) |

OTel `traceparent` propagates inbound → every outbound leg; per-leg span
carries `bff.domain` + `bff.route="operator-overview"` attributes.

##### Implementation guidance (impl PR scope notes — not contract)

- **`operatorDefaultAccountId` resolution** (finance card prerequisite) is an
  impl-PR decision: either (a) the GAP registry surface (§ 2.2) returns a
  per-operator `finance.defaultAccountId` claim/attribute, or (b) the
  console-bff composition use-case skips the finance leg and renders
  `forbidden / MISSING_PREREQUISITE` when no default is available. Option
  (b) is the **minimal MVP-correct path** — option (a) is a follow-up
  spec-first change in GAP `admin-api.md` registry surface, not in scope
  here. Pick (b) at MVP; (a) is a separately-tracked enhancement.
- **`asOf` field source**: server-side composition-request `Instant.now()`
  at request entry (NOT per-leg response timestamp); operators see the
  composition timestamp, not the slowest leg's freshness. wms's
  `X-Read-Model-Lag-Seconds` (per § 2.4.5) MAY surface as an additional
  card-level hint but is NOT in the v1 envelope schema.

##### console-web side obligations (FE)

- Server route `(console)/api/console/dashboards/operator-overview` (Next.js
  App Router server route) forwards the 3 headers (Authorization /
  X-Operator-Token / X-Tenant-Id) to `console-bff` server-side. Browser
  never sees them.
- `features/operator-overview/` (`<OperatorOverviewScreen>` server
  component + `<DomainCard>` × 5 + `<OverviewDegradeBanner>` if all-down)
  renders the composed envelope. Per-card UI shape:
  - `ok` → card-specific summary (count, snapshot, etc.).
  - `degraded` → "data unavailable" placeholder + retry affordance (explicit
    user retry, no auto-poll — § 2.4.4 / § 2.5 invariant).
  - `forbidden` → "not available to your role / tenant" placeholder (no
    re-login loop).
- Route `(console)/dashboards/overview` is the operator-facing entry; a
  navigation entry in the in-console nav (`<MainNav>` "Operator Overview")
  is added.
- No client-side polling, no auto-refresh interval — operator-initiated
  retry only (matches § 2.4.4 / § 2.4.9 invariant — bounded fan-out,
  meta-audit-respecting).

##### Hard invariants this MVP route inherits (HARD INVARIANT — ADR-MONO-017 § D4 + § 3.3 + § 2.4.9)

- **No producer retrofit** — 5 producer specs (`{global-account-platform, wms-platform, scm-platform, finance-platform, erp-platform}/specs/contracts/`) byte-unchanged.
- **Per-domain credential dispatch** verbatim from §§ 2.4.5/6/7/8 + § 2.6.
- **Read-only** — no `Idempotency-Key` / `X-Operator-Reason` / mutation method.
- **Producer-authoritative tenant gate** — `X-Tenant-Id` pass-through; BFF never re-derives or relaxes.
- **Per-card degrade** discipline — composition never blanks; `401`-cross-leg vs `403`/timeout-per-leg distinction preserved.
- **§ 3 parity matrix byte-unchanged** (attestation-marker count = exactly **16** — `parity-verification.test.ts` no-drift guard).
- **ADR-MONO-017 D1-D8 byte-unchanged** (no ADR amendment in this PR — this is execution under the ACCEPTED frame).

> **Phase 7 = MVP-only at this commit**. Subsequent Phase 7 dashboards
> (e.g. domain health, throughput) are separate future tasks (ADR-MONO-017
> § D8); they will be added as `§ 2.4.9.2`, `§ 2.4.9.3`, … sub-sections,
> each inheriting the hard invariants above.

> **Not a § 3 parity row**: composition routes are additive to the operator
> surface, never replace a § 3 row. § 3 count remains **16** post-merge.

#### 2.4.9.2 `GET /api/console/dashboards/domain-health` — Phase 7 "Domain Health Overview" composition route (TASK-PC-FE-013)

The **second concrete `§ 2.4.9.X` composition route**. Governed by
[ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)
§ 3.3 #4 (pre-authorised: *"Subsequent Phase 7 dashboards (domain health,
throughput) — separate tasks"*). This sub-section is **additive** to
§ 2.4.9 — all hard invariants, auth flow, resilience, observability,
logging, and edge-routing constraints declared in § 2.4.9 apply verbatim
**with one explicit clarification** (§ D4 scope, below). ADR-MONO-013
§ 3.3 "zero retrofit" — **seventh confirmation** (Phase 2/4/5/6/7-skeleton/
7-MVP/7-health across the portfolio).

> **§ D4 scope clarification (doc-level only, ADR text byte-unchanged)**:
> ADR-MONO-017 § D4 "per-domain credential rule" governs § 2.4.5/6/7/8
> *data API legs* (each domain's bearer-token-required `/api/…/**`).
> Public no-auth metadata endpoints — specifically Spring Boot actuator
> endpoints exposed `permitAll` by every producer's SecurityConfig (GAP
> gateway-service, WMS gateway-service, SCM gateway-service +
> inventory-visibility-service + procurement-service, finance
> account-service, erp masterdata-service — all verified 2026-05-21) —
> are **outside** D4's scope. Health legs in this route therefore make
> their outbound calls **without any Authorization header** (and without
> X-Tenant-Id, since actuator endpoints are not tenant-scoped). The D4
> sealed-switch in `CredentialSelectionAdapter` is **never invoked** on
> these legs. This clarification narrows D4 — it does not amend it.

##### Surface

| # | Method / Path | Purpose | Auth (inbound) | Producer |
|---|---|---|---|---|
| 1 | `GET /api/console/dashboards/domain-health` | Single composed cross-domain health envelope; one card per backend domain (GAP + wms + scm + finance + erp); each card carries the producer's Spring Boot `/actuator/health` status (`UP` / `DOWN` / `OUT_OF_SERVICE` / `UNKNOWN`) wrapped in the per-leg outcome (`ok` / `degraded`) per § 2.4.9 D5.A discipline | `Authorization: Bearer <gap-oidc-access-token>` (inbound principal, RS256 / GAP issuer) + `X-Tenant-Id: <active-tenant>` (forwarded to log MDC and degrade counter — **not** to outbound actuator legs); **`X-Operator-Token` NOT required** for this route (no outbound leg consumes it; the D4 sealed-switch is not invoked). Absent `Authorization` → `401 TOKEN_INVALID` before any outbound leg. Absent `X-Tenant-Id` → `400 NO_ACTIVE_TENANT` (for log/audit traceability, not because legs need it) | `console-bff` |

> The route is **GET only — read-only**. The hard invariant in § 2.4.9.1
> applies verbatim: no `Idempotency-Key`, no `X-Operator-Reason`, no
> destructive-confirm, no POST/PUT/PATCH/DELETE. Adding a mutation surface
> requires a fresh ADR amendment to ADR-MONO-017.

##### Composed producers (5 domains, reuse-only — § 3.3 zero retrofit #7)

The composition route fans out across **existing** public actuator
endpoints — one card per domain, **no producer retrofit**. The 5 endpoints
are Spring Boot actuator standards; the per-producer SecurityConfig
declarations that mark them `permitAll` are authoritative in their
respective service files and are **not redefined here**:

| # | Card | Composed producer endpoint | Outbound auth | Producer SecurityConfig (authoritative permitAll) | Read content surfaced |
|---|---|---|---|---|---|
| 1 | gap health | `GET http://gap.local/actuator/health` (gateway-service primary entry) | **None** (public actuator, no `Authorization`, no `X-Tenant-Id`) | GAP `gateway-service` `application.yml` `public-paths` includes `GET:/actuator/health` | `{"status": "UP" \| "DOWN" \| "OUT_OF_SERVICE" \| "UNKNOWN"}` aggregated status (Spring Boot `management.endpoint.health.show-details: never` per console-bff baseline; no component drill-down) |
| 2 | wms health | `GET http://wms.local/actuator/health` (gateway-service primary entry) | **None** | WMS `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` + `/actuator/health/**` | same aggregated status |
| 3 | scm health | `GET http://scm.local/actuator/health` (gateway-service primary entry) | **None** | SCM `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` | same aggregated status |
| 4 | finance health | `GET http://finance.local/actuator/health` (`account-service` direct — finance has no gateway-service in v1) | **None** | finance `account-service` `SecurityConfig` `permitAll` includes `/actuator/{health,info,prometheus}` | same aggregated status |
| 5 | erp health | `GET http://erp.local/actuator/health` (`masterdata-service` direct — erp has no gateway-service in v1) | **None** | erp `masterdata-service` `SecurityConfig` `permitAll` includes `/actuator/{health,info,prometheus}` | same aggregated status |

**Producer immutability**: the 5 producer SecurityConfig declarations above
are **byte-unchanged spec-side and impl-side** (§ 3.3 seventh confirmation).
The console-bff composition use-case calls the existing public actuator
endpoints verbatim. The 5 outbound `RestClient` beans (`gapRestClient` /
`wmsRestClient` / `scmRestClient` / `financeRestClient` / `erpRestClient`)
registered for § 2.4.9.1 are **reused** here (same base URLs, same per-leg
2s timeout); no new `RestClient` bean is added.

##### Response schema (`200 OK`)

```json
{
  "asOf": "2026-05-21T01:30:00Z",
  "cards": [
    { "domain": "gap",     "status": "ok",       "data": { "status": "UP" } },
    { "domain": "wms",     "status": "ok",       "data": { "status": "UP" } },
    { "domain": "scm",     "status": "degraded", "reason": "DOWNSTREAM_ERROR" },
    { "domain": "finance", "status": "ok",       "data": { "status": "OUT_OF_SERVICE" } },
    { "domain": "erp",     "status": "ok",       "data": { "status": "UP" } }
  ]
}
```

- `asOf`: composition request server-side timestamp (ISO-8601 UTC). Operators see "data as-of HH:MM:SS" in the UI.
- `cards[]`: **exactly 5 entries** in **fixed order** `[gap, wms, scm, finance, erp]` (UI rendering ordering invariant; never reordered by status).
- `cards[i].status` ∈ `{ "ok", "degraded" }` — **note**: `forbidden` is **never emitted** on this route (outbound actuator legs are public; HTTP `403` from a leg falls through to `degraded` like any other non-success status, since `403` from a public actuator means a misconfigured producer, not an operator-permission decision; treating it as `forbidden` would mis-signal a producer regression as a per-card-permission UX state).
- `cards[i].data.status` ∈ Spring Boot health enum `{ "UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN" }`:
  - `UP` → green/healthy card visual.
  - `DOWN` → red/critical visual; operator surface is **NOT degraded** (the leg returned a successful health document — the producer is honestly reporting itself as down). Distinction from `degraded` (the BFF could not reach the producer at all).
  - `OUT_OF_SERVICE` → maintenance yellow visual.
  - `UNKNOWN` → grey/inconclusive visual.
- `cards[i].status == "degraded"` → `reason` ∈ `{ "DOWNSTREAM_ERROR", "TIMEOUT", "CIRCUIT_OPEN" }`; `data` absent. Card renders "leg unreachable" placeholder + retry affordance.
- **All-down envelope**: every leg can return non-`ok` simultaneously — the route still emits `200` with all 5 cards in `degraded` states. The route NEVER emits `503` / blanks the response (D5.A discipline; D5.B rejection re-affirmed).

##### Error envelope (composition-level errors, NOT per-leg)

| Status | Code | Cause |
|---|---|---|
| `400` | `NO_ACTIVE_TENANT` | `X-Tenant-Id` absent or blank (for log/audit traceability, not because outbound legs need it — the inbound check is preserved for symmetry with § 2.4.9.1 and for log MDC) |
| `401` | `TOKEN_INVALID` | inbound `Authorization` bearer absent / invalid (Spring Security OAuth2 ResourceServer rejection — happens at filter chain, before controller) |
| `503` | reserved | NEVER emitted (D5.B is rejected; same as § 2.4.9.1) |

**No `401 TOKEN_INVALID` cross-leg collapse** — this route's outbound legs
have no `Authorization` header, so a 401 from any leg is itself an
unexpected (producer-side actuator misconfiguration) and is mapped to
`degraded` for that card, not to a composition-level 401. This is an
intentional divergence from § 2.4.9.1 D3 cross-leg rule (which exists
because every leg there shares the inbound operator/OIDC credential — a
401 from one is a 401 for all).

##### Auth flow

- **Inbound** (console-web SSR → console-bff): `Authorization` (GAP OIDC access token, inbound principal — Spring Security validates against GAP JWKS) + `X-Tenant-Id` (operator's selected active tenant, forwarded for log MDC). The browser **never** reaches console-bff directly.
- **Outbound** (console-bff → each domain's `/actuator/health`): **no headers** beyond `Accept: application/json`. No `Authorization`, no `X-Tenant-Id`, no `X-Operator-Token`. D4 sealed-switch is not invoked.

##### Resilience

- Per-leg circuit-breaker keyed by `(domain, route="domain-health")` via `libs/java-web` Resilience4j — sibling circuit instance to § 2.4.9.1's `(domain, "operator-overview")` (independent state, so one dashboard's circuit trip does not bleed into the other).
- Per-leg hard timeout reused (2s, the existing per-leg config in `RestClientConfig.PER_LEG_TIMEOUT`).
- Composition-level 5s budget reused.
- Aggregation degrade: every responsive leg's `data` + per-failed-leg `{ status: "degraded", reason }` card.
- All-down still returns 200 with all-degraded envelope. D5.B (all-or-nothing 503) is forbidden.

##### Observability

The 3 mandatory BFF metric families emit per-leg samples with the
following label values for this route:

| Metric | Labels per emit |
|---|---|
| `bff_fanout_latency_seconds{domain,route}` | `domain` ∈ `{gap,wms,scm,finance,erp}` × `route` = `"domain-health"` |
| `bff_fanout_errors_total{domain,route,code}` | same `domain`/`route` + `code` ∈ `{5xx,timeout,circuit_open}` (no `tenant_forbidden` / `permission_denied` / `missing_prerequisite` — those classifications belong to data legs only) |
| `bff_aggregation_degrade_count_total{dashboard,degraded_domain}` | `dashboard = "domain-health"` + `degraded_domain` ∈ `{gap,wms,scm,finance,erp}` (one increment per `degraded` card per response) |

OTel `traceparent` propagates inbound → every outbound leg; per-leg span
carries `bff.domain` + `bff.route="domain-health"` attributes.

##### Implementation guidance (impl PR scope notes — not contract)

- **No credential pre-resolve**: the use case (`DomainHealthCompositionUseCase`) MUST NOT invoke `CredentialSelectionPort.selectFor(...)` on any path. Grep-assert in tests.
- **`asOf` field source**: server-side composition-request `Instant.now()` at request entry (same as § 2.4.9.1).
- **Span attribute reuse**: existing `bff.domain` + new `bff.route="domain-health"` — no new attribute key.

##### console-web side obligations (FE)

- Server route `(console)/api/console/dashboards/domain-health` (Next.js App Router server route) forwards `Authorization` + `X-Tenant-Id` to `console-bff` server-side. **Does NOT forward `X-Operator-Token`** (the BFF route does not require it; sending it would be misleading). Browser never sees the inbound headers.
- `features/domain-health/` (`<DomainHealthScreen>` server component + `<DomainHealthCard>` × 5 + `<DegradeBanner>` if all-down + `<RetryButton>` client-only) renders the composed envelope. Per-card UI shape:
  - `ok` + `data.status="UP"` → green-checkmark card.
  - `ok` + `data.status="DOWN"` → red-cross card (producer self-reported critical — NOT a BFF/network failure).
  - `ok` + `data.status="OUT_OF_SERVICE"` → yellow-wrench card (planned maintenance).
  - `ok` + `data.status="UNKNOWN"` → grey-question card.
  - `degraded` → "leg unreachable" placeholder + retry affordance.
- Route `(console)/dashboards/health` is the operator-facing entry; a navigation entry in the in-console nav (`<MainNav>` "도메인 상태") is added alongside the existing "통합 개요".
- No client-side polling, no auto-refresh interval — operator-initiated retry only (matches § 2.4.4 / § 2.4.9 invariant).

##### Hard invariants this route inherits (HARD INVARIANT — ADR-MONO-017 + § 3.3 + § 2.4.9)

- **No producer retrofit** — 5 producer SecurityConfig + actuator wiring byte-unchanged.
- **D4 scope clarification** — D4 governs data legs only; this route's actuator legs are explicitly outside D4. The sealed-switch is NOT invoked on these legs (grep-asserted).
- **Read-only** — no `Idempotency-Key` / `X-Operator-Reason` / mutation method.
- **No `Authorization` / `X-Tenant-Id` / `X-Operator-Token` on outbound legs** (grep-asserted).
- **Per-card degrade** discipline — composition never blanks.
- **§ 3 parity matrix byte-unchanged** (attestation-marker count = exactly **16**).
- **ADR-MONO-017 D1-D8 byte-unchanged** (no ADR amendment in this PR).

> **Not a § 3 parity row**: composition routes are additive to the operator
> surface, never replace a § 3 row. § 3 count remains **16** post-merge.

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

## 3. GAP `admin-web` absorption — VERIFIED parity matrix (Phase 3 gate)

The console's GAP section must reach functional parity with the existing GAP
`admin-web` operator surface before `admin-web` is retired (ADR-MONO-013 D4,
parity-gated). The parity checklist (enumerated at ACCEPTED, ADR-MONO-013 § 6
D7.4; the `dashboards` line **refined** by ADR-MONO-015 D2 — composed operator
overview, *not* Grafana) is **finalized below as a verified parity matrix**.

> **Status: VERIFIED by TASK-PC-FE-006** (ADR-MONO-013 Phase 2 slice 5 of 5 —
> the capstone). Each row was attested by the consolidated parity-verification
> test (`apps/console-web/tests/unit/parity-verification.test.ts`), which
> iterates the single machine-readable matrix fixture
> (`apps/console-web/tests/unit/parity-matrix.ts`). The fixture **is** this
> table in executable form — the spec table and the test cannot drift (one
> source). Verification = attestation over the **existing**, unmodified
> FE-002..005 surface (FE-006 implemented no feature/route/producer; it only
> verifies). No real parity gap was found — all 16 rows verified.

### 3.1 Verified parity matrix

Legend: **Kind** `R` = read, `M` = mutation. **Headers** column states the
per-capability mutation-header obligation attested by the test (`reason` =
`X-Operator-Reason`, `idem` = `Idempotency-Key`); read rows assert **no**
mutation artifacts. Every row's server client authenticates with the
**exchanged operator token** (`getOperatorToken()`, never the GAP OIDC access
token — the #569 trust-boundary invariant) and sends `X-Tenant-Id` (active
tenant; blocked, never empty, when none selected) — attested for every row.

| # | admin-web operator capability | Console feature module | Contract § | GAP producer endpoint (`admin-api.md` §) | Kind | Mutation headers | Verified |
|---|---|---|---|---|---|---|---|
| 1 | accounts: search / list | `features/accounts` `searchAccounts` | § 2.4.1 | `GET /api/admin/accounts` (`admin-api.md` § L68) | R | — (no mutation artifacts) | verified by TASK-PC-FE-006 |
| 2 | accounts: detail | `features/accounts` `getAccountByEmail` (composed: search/list item + ops 3–8 — **no fabricated GET-by-id**, consistent with FE-002 / `admin-api.md` having no producer GET-by-id) | § 2.4.1 | composed over `GET /api/admin/accounts` + ops 3–8 (no dedicated producer endpoint) | R | — (no mutation artifacts) | verified by TASK-PC-FE-006 |
| 3 | accounts: lock | `features/accounts` `lockAccount` | § 2.4.1 | `POST /api/admin/accounts/{accountId}/lock` (§ L130) | M | reason + idem; reason+confirm-gated | verified by TASK-PC-FE-006 |
| 4 | accounts: unlock | `features/accounts` `unlockAccount` | § 2.4.1 | `POST /api/admin/accounts/{accountId}/unlock` (§ L244) | M | reason + idem; reason+confirm-gated | verified by TASK-PC-FE-006 |
| 5 | accounts: bulk-lock | `features/accounts` `bulkLockAccounts` | § 2.4.1 | `POST /api/admin/accounts/bulk-lock` (§ L179) | M | reason + idem (single key per confirmed action); multi-select confirm | verified by TASK-PC-FE-006 |
| 6 | accounts: revoke-session | `features/accounts` `revokeSessions` | § 2.4.1 | `POST /api/admin/sessions/{accountId}/revoke` (§ L278) | M | reason + idem; reason+confirm-gated | verified by TASK-PC-FE-006 |
| 7 | accounts: gdpr-delete | `features/accounts` `gdprDeleteAccount` | § 2.4.1 | `POST /api/admin/accounts/{accountId}/gdpr-delete` (§ L739, irreversible) | M | reason + idem; double-confirm + typed confirmation | verified by TASK-PC-FE-006 |
| 8 | accounts: export | `features/accounts` `exportAccount` | § 2.4.1 | `GET /api/admin/accounts/{accountId}/export` (§ L786, unmasked PII — producer meta-audits) | R (audited) | reason **required** (producer-mandated audit reason on a GET); **no idem** (not an idempotency-bearing mutation) | verified by TASK-PC-FE-006 |
| 9 | audit: query | `features/audit` `queryAudit` | § 2.4.2 | `GET /api/admin/audit` (§ L320, `source=admin`/unfiltered) | R | — (no mutation artifacts; reason/idem absent asserted) | verified by TASK-PC-FE-006 |
| 10 | security: login-history | `features/audit` `queryAudit({source:'login_history'})` | § 2.4.2 | `GET /api/admin/audit?source=login_history` (§ L320) | R | — (no mutation artifacts); intersection-permission `audit.read` ∧ `security.event.read` (producer-authoritative) | verified by TASK-PC-FE-006 |
| 11 | security: suspicious | `features/audit` `queryAudit({source:'suspicious'})` | § 2.4.2 | `GET /api/admin/audit?source=suspicious` (§ L320) | R | — (no mutation artifacts); intersection-permission `audit.read` ∧ `security.event.read` | verified by TASK-PC-FE-006 |
| 12 | operators: create | `features/operators` `createOperator` | § 2.4.3 | `POST /api/admin/operators` (§ L907) | M | **reason + idem** (producer requires BOTH); reason+elevated-confirm-gated | verified by TASK-PC-FE-006 |
| 13 | operators: edit-roles | `features/operators` `editOperatorRoles` | § 2.4.3 | `PATCH /api/admin/operators/{operatorId}/roles` (§ L963, full-replace; `[]` allowed) | M | **reason ONLY — `Idempotency-Key` MUST NOT be sent** (FE-004 per-endpoint header non-uniformity; producer does not list it; absence asserted) | verified by TASK-PC-FE-006 |
| 14 | operators: change-status | `features/operators` `changeOperatorStatus` | § 2.4.3 | `PATCH /api/admin/operators/{operatorId}/status` (§ L1008, ACTIVE↔SUSPENDED) | M | **reason ONLY — `Idempotency-Key` MUST NOT be sent** (FE-004 non-uniformity; absence asserted) | verified by TASK-PC-FE-006 |
| 15 | operators: change-password | `features/operators` `changeOwnPassword` | § 2.4.3 | `PATCH /api/admin/operators/me/password` (§ L1056, **self only** — no admin-set-other) | M (self) | **no reason, no idem** (self path; valid operator token only — per the producer) | verified by TASK-PC-FE-006 |
| 16 | dashboards (**ADR-MONO-015-refined composed operator overview, NOT Grafana**) | `features/dashboards` `getOperatorOverview` | § 2.4.4 | **no new producer** — bounded fan-out composing the EXISTING reads `GET /api/admin/accounts` + `GET /api/admin/audit` + `GET /api/admin/operators` (§§ L68/L320/L859), per-source isolated | R | — (no mutation artifacts on ANY leg; reason/idem absent asserted); per-source isolation (403/503/timeout → that card only; 401 on any leg → whole-overview re-login) | verified by TASK-PC-FE-006 |

> **`dashboards` row — explicit ADR-MONO-015 D2 note:** row 16 is the
> **refined** parity line — a composed operator overview built from the
> already-integrated accounts/audit/operators read surfaces, **not** a
> reproduction of `admin-web`'s Grafana observability iframe. Observability /
> Grafana metrics dashboards are **out of scope** of the platform-console
> parity gate and, if ever required, are a separate observability ADR (never
> an `admin-web`-retirement blocker). The Phase 3 retirement decision stays
> defensible because the Grafana observability view is explicitly re-scoped to
> operator/SRE tooling, independent of the console.

### 3.2 Phase 2 parity COMPLETE — Phase 3 gate satisfied

**Phase 2 parity COMPLETE** (ADR-MONO-013 § D6 Phase 2 = 5/5 slices: FE-002
accounts → FE-003 audit+security → FE-004 operators → FE-005 dashboards →
FE-006 parity-verify). All 16 rows of the § 3.1 matrix are verified by
TASK-PC-FE-006; the **ADR-MONO-013 § 6 Phase 3 `admin-web`-retirement gate
('Phase 2 parity verified', § 6 row 3) is satisfied**.

Retirement itself is a **separate GAP project-internal spec-first task** (GAP
`PROJECT.md` service map → `admin-web` row removed → app removal), explicitly
**out of scope here**. FE-006 only *satisfies the gate*; it does not retire
anything and touches no GAP code/spec. Merging FE-006 must **not** be read as
authorizing `admin-web` removal — that is a distinct GAP-internal change.

---

## 4. Out of Scope (this contract)

- Domain business logic / domain event contracts (each domain owns these).
- `console-bff` aggregation endpoint shapes (ADR-MONO-013 Phase 7 — added when the BFF lands).
- finance/erp domain contracts (governed by ADR-MONO-008 / future erp ADR).

---

## 5. Change Rule

Changes to the contract elements (§ 2) require updating this file **before** implementation, and — if they alter a deployed integration — an ADR per [`architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md). The skeleton → full transition (adding concrete per-domain endpoint schemas) is additive and tracked per domain task. The § 2.1/§ 2.6 operator-token-exchange element is governed by ADR-MONO-014 (ACCEPTED); the RFC 8693 request/response/error contract is owned producer-side by GAP `admin-api.md` — a change there is a GAP project-internal spec-first change cross-referenced here, not redefined here.
