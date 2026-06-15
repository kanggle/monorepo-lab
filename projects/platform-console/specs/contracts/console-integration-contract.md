# Console ↔ Domain Integration Contract

> The contract every product must satisfy to be federated by `platform-console`.
> Authoritative skeleton: [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D5. The operator-auth bridge (§ 2.1 server-side exchange step + § 2.6) is decided by [ADR-MONO-014](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (ACCEPTED) § D2/D3/D4 and realised by `TASK-PC-FE-002a`. This document is the full form.
> Status: **v1 skeleton** — element shapes are normative; concrete per-domain endpoint schemas are added as each domain section is built (ADR-MONO-013 Phase 2/4/5/6). The IAM operator surface (§ 2.4.1–§ 2.4.4) is fully bound; **§ 3 is finalized as a VERIFIED parity matrix** (TASK-PC-FE-006 — ADR-MONO-013 Phase 2 = 5/5 COMPLETE, Phase 3 retirement gate satisfied).

---

## 1. Scope

`platform-console` is **Model B** (ADR-MONO-013 D1): the console is the single UI and renders each domain's operational screens by calling that domain's gateway/admin REST API. This contract defines the five integration elements a domain must provide. It does **not** define domain business APIs — those live in each domain's own `specs/contracts/`.

---

## 2. Contract Elements

### 2.1 Identity (OIDC + server-side operator-token exchange)

- The console is a IAM OIDC **public client** (`platform-console-web`), Authorization Code + PKCE.
- One operator login covers all federated domains (SSO). Access token carries `tenant_id`.
- Tokens are held in **HttpOnly cookies only** (per `platform/service-types/frontend-app.md`); refresh via a server route.
- IAM-side registration is a IAM project-internal prerequisite — `TASK-BE-296`.
- **Server-side operator-token exchange step (ADR-MONO-014 D2, `TASK-PC-FE-002a`)**: the IAM OIDC access token is **not** itself an admin-service operator credential. Immediately after OIDC login (`/api/auth/callback`) and on every IAM refresh (`/api/auth/refresh`), the console **server-side** exchanges the IAM OIDC access token for a short-lived **admin-service operator token** (`token_type=admin`, `iss=admin-service`) via the IAM exchange endpoint (§ 2.6). Both the IAM OIDC token **and** the exchanged operator token are held in separate HttpOnly·Secure·SameSite=strict cookies, server-only, never client-readable, never logged.
- **Trust boundary invariant**: the IAM OIDC access token is only ever the `subject_token` input to the exchange (§ 2.6). It is **never** sent to `/api/admin/**`; the operator credential for every `/api/admin/**` call (registry § 2.2 + future operator screens § 2.4) is exclusively the exchanged operator token. There is no path by which the IAM OIDC token reaches an `/api/admin/**` endpoint.

### 2.2 Product / Tenant Registry (catalog source)

- IAM exposes a registry surface the console reads to build the **data-driven** catalog.
- **Authoritative producer endpoint** (TASK-BE-296 — IAM owns the path/auth/envelope; see [`iam-platform/specs/contracts/http/console-registry-api.md`](../../../iam-platform/specs/contracts/http/console-registry-api.md)):
  - **Path**: `GET http://iam.local/api/admin/console/registry` (admin-service, hosted on the IAM operator-auth boundary; the gateway treats `/api/admin/**` as a public-path subtree and delegates operator-JWT verification to admin-service `OperatorAuthenticationFilter` — platform invariant).
  - **Auth model**: `Authorization: Bearer <operator-token>` (`token_type=admin`, `iss=admin-service`) — producer requirement **unchanged**. No `X-Operator-Reason` (read-only catalog lookup). The console calls this **server-side** with the **operator token obtained via the § 2.6 exchange** (held in its own HttpOnly cookie), **not** the IAM OIDC access token — never a browser-direct call (§ 2.3). The IAM OIDC access token is never an `/api/admin/**` credential (§ 2.1 trust boundary invariant); it is only the `subject_token` input to the exchange.
  - **Tenant scoping**: the operator's tenant scope is resolved producer-side from `admin_operators.tenant_id` (ADR-002 `'*'` platform sentinel). The console does **not** send a tenant to the registry; IAM returns only the tenants the operator may select (cross-tenant isolation enforced producer-side — regression-tested, multi-tenant M3/M4).
  - **Response envelope**: `{ "products": [ <item> ] }`. **Errors** use the IAM admin error envelope `{ code, message, timestamp }`: `401 TOKEN_INVALID` / `401 TOKEN_REVOKED` → console forces re-login; `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` → console renders a degraded catalog, never blanks the shell (§ 2.5).
  - Any prior Phase-1 placeholder path (`/internal/console/registry`) is **superseded** by the producer contract above; the console's `CONSOLE_REGISTRY_URL` points at the authoritative path.
- Minimum item shape (normative):

| Field | Type | Meaning |
|---|---|---|
| `productKey` | string | `iam` \| `wms` \| `scm` \| `erp` \| `finance` \| `ecommerce` |
| `displayName` | string | Catalog tile label |
| `available` | boolean | `false` → rendered as "coming soon"; reserved for future product additions (all 6 federated v1 domains — `iam` + `wms` + `scm` + `erp` + `finance` + `ecommerce` — are `available:true`; `ecommerce` added by TASK-MONO-240 2026-06-13 per ADR-MONO-030) |
| `tenants` | string[] | Tenant ids the operator may select for this product |
| `baseRoute` | string | Console-internal route prefix for the product's screens |
| `operatorContext` | `{ defaultAccountId?: string } \| undefined` | **TASK-BE-304 (producer) / TASK-PC-FE-014 (consumer)** — optional extensible per-operator per-product profile attributes carrier. **Omitted entirely** when no attribute is set (not rendered as `null`). v1: only the `finance` product item populates this (with `defaultAccountId` from IAM `admin_operators.finance_default_account_id`); the other 4 items always omit it. Authoritative producer shape + emission rule: [`iam-platform/specs/contracts/http/console-registry-api.md § Per-operator profile attributes`](../../../iam-platform/specs/contracts/http/console-registry-api.md). Consumer-side wiring (parser → session → dashboard proxy header) per § 2.4.9.1 Implementation guidance — Option (a) activation. |

- Flipping `available` / `displayName` / `tenants` of an **existing** catalog member is a **registry change only** — zero `console-web` code change (the catalog renders the dynamic product list verbatim; ADR-MONO-013 § 1.2 / D5). **Adding a NEW `productKey`, however, requires a one-line consumer-side `ProductKeySchema` Zod enum extension** in `console-web` (`src/shared/api/registry-types.ts`) — the fixed-membership guard asserted by `registry-contract.test.ts` "rejects unknown productKey". An unknown `productKey` makes `RegistryResponseSchema.parse` throw → the whole catalog renders `degraded`. So a new-domain catalog addition lands the producer item + this consumer enum in the **same atomic PR** (TASK-MONO-240 added `ecommerce`; ADR-MONO-030 § 6 factual correction). Render is data-driven (0-change); membership is an explicit extension.
- **Subscription-driven `tenants` derivation (TASK-BE-322 / ADR-MONO-019 D2/D4 — envelope shape UNCHANGED, zero console-web change)**: each domain product's `tenants[]` is now derived producer-side from the **ACTIVE tenant↔domain subscriptions** IAM account-service owns (the D2 entitlement authority), instead of the prior fixed `tenantSlug == domain` binding. This is a **producer-internal derivation change only** — the response envelope, item shape, and field semantics are identical. In ADR-019 **step 1** the values are still the domain slugs (a backward-compatible self-subscription seed makes the output byte-identical to the pre-BE-322 catalog); real customer-tenant names surface in a later step (step 2) without any console-web change. `iam` continues to federate **all** registered tenants (it never consults the subscription surface).

### 2.3 Routing

- Each domain is reachable at its Traefik hostname (`iam.local`, `wms.local`, `scm.local`, … ; console at `console.local`).
- The console reaches domains **server-side** (server components / server routes), never via browser-direct calls that bypass the typed API client.
- **Catalog-tile drill-in routes (data-driven `baseRoute`)**: a catalog tile's click navigates to the registry item's `baseRoute` prefix; each domain that surfaces an operations section provides the matching `console-web` route under `(console)/<domain>/`. Existing: `/wms` (§ 2.4.5), `/scm` (§ 2.4.6), `/finance` (§ 2.4.7), `/erp` (§ 2.4.8), IAM screens (§§ 2.4.1–2.4.4). **`/ecommerce`** is added by TASK-MONO-241 (ADR-MONO-030 Step 4 facet a-후속) so the `ecommerce` catalog tile (`baseRoute=/ecommerce`, added by TASK-MONO-240) resolves to an existing route — a **read-only** drill-in mirroring the scm/wms section degrade discipline (eligibility pre-flight on `productKey==='ecommerce'`; not-eligible / forbidden / ratelimited / degrade branches keep the console shell intact). The v1 `/ecommerce` content surfaces the ecommerce **domain-health** summary; the rich operations surface (product/order/seller management) is a deferred follow-up.

### 2.4 Console-facing API surface (per domain)

- Each domain's gateway/admin service exposes the read/ops endpoints the console renders. These are declared per-domain in that domain's `specs/contracts/` and cross-referenced from the console's `specs/services/console-web/` when the domain section is built.
- All calls are **tenant-scoped**: the console propagates the selected tenant (`X-Tenant-Id` header or equivalent honored by the domain gateway); the domain MUST reject cross-tenant requests.
- Operator mutating actions (e.g. account lock/unlock) MUST be idempotent on the domain side; the console sends an idempotency key and renders the result (it owns no domain transaction — `platform-console` is not `transactional`).

#### 2.4.1 IAM accounts surface (TASK-PC-FE-002 — cross-reference, not a redefinition)

The first concrete per-domain binding of § 2.4 (ADR-MONO-013 Phase 2 slice 1 / § 3 parity "accounts" line). The console's `features/accounts` renders, **server-side and tenant-scoped**, the IAM operator account surface. The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning IAM spec.

- **Authoritative producer (owned by IAM, do NOT redefine here)**: [`iam-platform/specs/contracts/http/admin-api.md`](../../../iam-platform/specs/contracts/http/admin-api.md). The console consumes exactly these endpoints (request/response/headers/error tables are canonical there):

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

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every call carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the IAM OIDC access token. An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the IAM token on the `/api/admin/**` boundary — the #569 invariant).
- **Tenant scope (§ 2.4 / multi-tenant)**: the console always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the call** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id`.
- **Mutation audit + idempotency (§ 2.4 / audit-heavy / integration-heavy I4)**: every mutation (lock/unlock/bulk-lock/revoke-session/gdpr-delete) carries a required operator-entered `X-Operator-Reason` (audit reason; producer `400 REASON_REQUIRED` if missing) **and** a client-generated `Idempotency-Key` (`crypto.randomUUID()`), stable across one user-confirmed action and freshly regenerated per a new attempt — no accidental double-mutation, no accidental dedupe of a genuine second action. The console owns no domain transaction; the producer is the idempotency authority (`bulk-lock` `(operator_id, Idempotency-Key)` uniqueness; `409 IDEMPOTENCY_KEY_CONFLICT` on a same-key/different-payload reuse).
- **Resilience (§ 2.5)**: the accounts section reuses the registry-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`403 TOKEN_INVALID|PERMISSION_DENIED` → forced re-login (no partial authed state); `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the accounts section degrades** (the shell stays intact); `400 STATE_TRANSITION_INVALID`/`400 REASON_REQUIRED` / `404 ACCOUNT_NOT_FOUND` / `422 BATCH_SIZE_EXCEEDED` / `409 IDEMPOTENCY_KEY_CONFLICT` → inline actionable error (no crash). `account.read` absent on the **unfiltered list** ⇒ producer returns **`403 PERMISSION_DENIED`** (TASK-MONO-202, `admin-api.md` `GET /api/admin/accounts`) ⇒ the console renders a distinct **권한 없음** state (NOT a forced re-login — a `403 PERMISSION_DENIED` is an authorization denial, not an auth failure; only `401 TOKEN_INVALID` forces re-login). An empty `200` list now unambiguously means **0 accounts** (permission held), rendered as "등록된 계정이 없습니다". The `email` single-lookup needs no `account.read` and is unaffected.
- **Destructive-action UX (security UX, audit-heavy)**: lock/unlock/bulk-lock/revoke-session/gdpr-delete are each reason-gated **and** confirm-gated — the producer call MUST NOT fire until a non-empty operator reason is entered; `gdpr-delete` is irreversible → double-confirm + an explicit typed confirmation; `bulk-lock` is multi-select with per-account result rendering (no all-or-nothing implication). No silent/one-click destructive call.
- **Logging**: structured server-side logs only; operator/IAM tokens and account PII (emails) are never logged (redacted) — § 2.6 logging invariant extended to the accounts surface.
- **PII / export**: `export` returns unmasked PII server-side; the console streams/downloads it without buffering PII into client state (producer meta-audits the access).
- **Producer immutability**: this is a **cross-reference only**. Any change to the accounts producer contract is a IAM project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

#### 2.4.2 IAM audit + security surface (TASK-PC-FE-003 — cross-reference, not a redefinition)

The second concrete per-domain binding of § 2.4 (ADR-MONO-013 Phase 2 slice 2 / § 3 parity "audit: query" + "security: login-history, suspicious"). The console's `features/audit` renders, **server-side and tenant-scoped**, the IAM unified audit + security read surface. This is a **read-only** slice — there is **no mutation**, therefore the § 2.4.1 mutation scaffolding (`X-Operator-Reason`, `Idempotency-Key`, destructive-confirm dialogs) **does not apply and MUST NOT be carried over**. The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning IAM spec.

- **Authoritative producer (owned by IAM, do NOT redefine here)**: [`iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/audit`](../../../iam-platform/specs/contracts/http/admin-api.md). A single unified-view endpoint over `admin_actions` + `login_history` + `suspicious_events`, discriminated by the `source` filter. The console consumes exactly this endpoint (request/response/headers/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-api.md` §) | `source` | Base permission | Kind |
  |---|---|---|---|---|---|
  | 1 | audit query | `GET /api/admin/audit` | `admin` (or unfiltered) | `audit.read` | read |
  | 2 | security: login-history | `GET /api/admin/audit?source=login_history` | `login_history` | `audit.read` **and** `security.event.read` | read |
  | 3 | security: suspicious | `GET /api/admin/audit?source=suspicious` | `suspicious` | `audit.read` **and** `security.event.read` | read |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: the call carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the IAM OIDC access token. An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the IAM token on the `/api/admin/**` boundary — the #569 invariant). There is **no** `X-Operator-Reason` and **no** `Idempotency-Key` on this read-only call (carrying either over from § 2.4.1 is a defect).
- **Tenant scope (§ 2.4 / multi-tenant M3/M4)**: the console always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer resolves the operator's authoritative tenant scope from `admin_operators.tenant_id` and rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the call** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id`. A SUPER_ADMIN may additionally send the producer's optional `tenantId` **query** parameter for an explicit cross-tenant read; a non-SUPER_ADMIN operator sending a foreign `tenantId` → producer `403 TENANT_SCOPE_DENIED` (meta-audited producer-side per `admin-api.md`). The console offers **no free-text tenant override** to non-super operators — only the standard tenant selector.
- **Intersection-permission rule (producer-authoritative)**: `audit.read` is the base permission. `source=login_history` or `source=suspicious` **additionally** requires `security.event.read` (intersection, not union — both permissions). Operators with `audit.read` only (e.g. `SUPPORT_LOCK`) can read `source=admin` but receive `403 PERMISSION_DENIED` on a security source. The console's UX SHOULD pre-disable the `login_history`/`suspicious` source affordances with an explanation when the operator's claims show `security.event.read` is absent, and MUST ALWAYS still handle a server `403 PERMISSION_DENIED` defensively (inline, never a crash). The console never re-derives the producer's authorization — it mirrors it for UX only; the producer is the final authority.
- **Read-query meta-audit awareness (audit-heavy A5)**: the audit query itself is meta-audited producer-side. The console MUST NOT auto-refetch aggressively — one user-initiated query = one producer call (no background polling loop that would flood the producer's meta-audit). A degraded section re-query is an explicit user retry, not an automatic poll.
- **Producer-masked PII (audit-heavy A9 / regulated R4)**: the producer already masks PII in the audit response (IP partially masked, no email). The console MUST NOT attempt to un-mask, derive, or buffer audit-row PII (account ids / masked IPs / geo) beyond render, and MUST NOT log it (server-side structured logs redact it — § 2.6 logging invariant extended to the audit surface). Large result sets are server-side paginated only — never buffered whole into client state.
- **Discriminated rendering tolerance**: rows are rendered discriminated by the `source` value (`admin` vs `login_history` vs `suspicious` columns). An unknown/future `source` value MUST degrade to a generic row — the consumer parser is tolerant and never throws on an unrecognised discriminant.
- **Resilience (§ 2.5)**: the audit section reuses the registry/accounts-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`403 TOKEN_INVALID|PERMISSION_DENIED`/`403 TENANT_SCOPE_DENIED` → `401` forces a clean re-login (no partial authed state); `403 PERMISSION_DENIED`/`403 TENANT_SCOPE_DENIED` → inline actionable (no crash); `422 VALIDATION_ERROR` (from > to, size > 100) → inline field-level error **plus** a client-side guard (from ≤ to, `size` client-capped ≤ 100) that pre-empts the producer 422; `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the audit section degrades** (the console shell stays intact).
- **Producer immutability**: this is a **cross-reference only**. Any change to the audit producer contract is a IAM project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

> **§ 3 parity lines satisfiable**: with `features/audit` bound here, the § 3 "audit: query" and "security: login-history, suspicious" parity lines are **satisfiable**; `FE-006` formally verifies them (ADR-MONO-013 Phase 3 admin-web-retirement gate).

#### 2.4.3 IAM operators surface (TASK-PC-FE-004 — cross-reference, not a redefinition)

The third concrete per-domain binding of § 2.4 (ADR-MONO-013 Phase 2 slice 3 / § 3 parity "operators: create, edit-roles, change-status, change-password"). The console's `features/operators` renders, **server-side and tenant-scoped**, the IAM operator-management surface. This is the **most privilege-sensitive** slice — creating operators and changing roles/status is the operator-privilege-escalation surface. The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning IAM spec.

- **Authoritative producer (owned by IAM, do NOT redefine here)**: [`iam-platform/specs/contracts/http/admin-api.md`](../../../iam-platform/specs/contracts/http/admin-api.md). The console consumes exactly these endpoints (request/response/**per-endpoint headers**/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-api.md` §) | Kind | Required permission |
  |---|---|---|---|---|
  | 1 | list | `GET /api/admin/operators` (`status` filter, `page`/`size`); response items optionally carry `operatorContext.defaultAccountId` per item (producer-side `@JsonInclude.NON_NULL` — omitted when the operator has no value) — **TASK-PC-FE-018** consumes this to pre-populate the admin profile-edit dialog | read | `operator.manage` |
  | 2 | create | `POST /api/admin/operators` (body `tenantId`; `*`=platform-scope) | mutation | `operator.manage` |
  | 3 | edit-roles | `PATCH /api/admin/operators/{operatorId}/roles` (full-replace; `[]` allowed) | mutation | `operator.manage` |
  | 4 | change-status | `PATCH /api/admin/operators/{operatorId}/status` (ACTIVE↔SUSPENDED) | mutation | `operator.manage` |
  | 5 | change-password | `PATCH /api/admin/operators/me/password` (**self only** — no admin-set-other) | mutation (self) | (valid operator token) |
  | 6 | change-profile | `PATCH /api/admin/operators/me/profile` (**self only** — operator profile carrier; v1 = `operatorContext.defaultAccountId`) — **TASK-PC-FE-016** | mutation (self) | (valid operator token) |
  | 7 | admin-set-profile | `PATCH /api/admin/operators/{operatorId}/profile` (**admin-on-behalf-of** — cross-operator counterpart of row 6; self via this path → `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`) — **TASK-PC-FE-017** | mutation | `operator.manage` |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every call carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the IAM OIDC access token. An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the IAM token on the `/api/admin/**` boundary — the #569 invariant).
- **Tenant scope (§ 2.4 / multi-tenant)**: the console always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer resolves the actor's authoritative tenant scope from `admin_operators.tenant_id` and rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the call** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id`. **`create` additionally carries a `tenantId` body field** (the tenant the new operator belongs to); `tenantId='*'` is the SUPER_ADMIN platform-scope sentinel and **only a platform-scope operator may create another `*` operator** → a non-platform operator attempting it gets producer `403 TENANT_SCOPE_DENIED` (meta-audited producer-side). The console MUST NOT offer `*` as a tenant option to non-platform operators (the UI never presents an escalation it cannot perform).
- **Per-endpoint header matrix (the key correctness risk — NOT uniform; do NOT blanket-apply § 2.4.1's `reason`+`idempotency` pair)**:

  | Operation | `X-Operator-Reason` | `Idempotency-Key` | Notes |
  |---|---|---|---|
  | `GET /operators` (list) | — | — | read only; no mutation headers |
  | `POST /operators` (create) | **required** | **required** (`crypto.randomUUID()`) | producer requires both |
  | `PATCH .../{id}/roles` | **required** | **MUST NOT send** | producer does not list `Idempotency-Key` — sending it is a contract deviation; full-replace PATCH is idempotent by the producer |
  | `PATCH .../{id}/status` | **required** | **MUST NOT send** | producer does not list `Idempotency-Key`; idempotent PATCH |
  | `PATCH .../me/password` | — | — | self path; valid operator token only (no `operator.manage`, no audit-reason header per producer) |
  | `PATCH .../me/profile` | — | — | self path; valid operator token only (no `operator.manage`, no audit-reason header per producer); mirrors `me/password` exactly — TASK-PC-FE-016 |
  | `PATCH .../{id}/profile` | **required** | **MUST NOT send** | admin-on-behalf-of; producer requires reason; **`Idempotency-Key` MUST NOT be sent** (producer matrix mirrors `/roles` + `/status` non-uniformity); self via this path → producer `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` (UI gates the per-row button when row is self; producer is the authority) — TASK-PC-FE-017 |

  A retried *confirmed* `create` reuses its `Idempotency-Key`; a fresh create attempt gets a new key. `roles`/`status` carry **no** key — adding one the producer omits is a header-matrix-drift defect (this slice's primary failure mode; pinned by an AC + a test).
- **`operator.manage` / SUPER_ADMIN gating (saas S5 / audit-heavy A5)**: all five operations require `operator.manage`, granted only to `SUPER_ADMIN` (producer-authoritative; the console mirrors it for UX only and never re-derives it). When the operator is not a SUPER_ADMIN the producer returns `403 PERMISSION_DENIED`; the console renders the whole operators section as an inline "not permitted" state (and SHOULD gate the `/operators` nav entry when derivable) — never a crash, never a re-login loop. The console always still handles the server `403` defensively.
- **Mutation audit (§ 2.4 / audit-heavy / saas S5)**: every mutating action (create / edit-roles / change-status / change-password) is **reason-gated and confirm-gated** — the producer call MUST NOT fire until a non-empty operator reason is entered (producer `400 REASON_REQUIRED` if missing on the reason-bearing endpoints). Privilege-high actions — **creating an operator, granting `SUPER_ADMIN`, suspending an operator, removing all roles (`[]`)** — carry explicit **elevated confirm copy**. No silent / one-click create / role-grant / suspend.
- **Password safety (security-rules / saas S1)**: `create` and self `change-password` accept a plaintext password server-side only. The console **client-side mirrors the producer password policy** as a UX pre-check (≥10 chars, ≥1 letter + ≥1 digit + ≥1 special — pre-validates before submit; the producer is the final authority). A password is **never** logged, never echoed into structured logs / events / state beyond the input field, never placed in a query string, and is cleared from memory on submit where practical. There is **no admin-set-other-password endpoint** in the parity line — change-password is exclusively the logged-in operator's own (`/me/`); the console does not invent one.
- **Role tolerance**: role names are the producer's enum (`SUPER_ADMIN`/`SUPPORT_LOCK`/`SUPPORT_READONLY`/`SECURITY_ANALYST`/…). The list view tolerates an unknown/future role (a generic chip, never a crash); the create / edit-roles selectors offer the known enum (a stale `400 ROLE_NOT_FOUND` is handled inline, refreshing the client-cached role source).
- **Resilience (§ 2.5)**: the operators section reuses the registry/accounts/audit-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`TOKEN_INVALID` → forced re-login (no partial authed state); `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` / `409 OPERATOR_EMAIL_CONFLICT` / `400 ROLE_NOT_FOUND`/`VALIDATION_ERROR`/`STATE_TRANSITION_INVALID`/`SELF_SUSPEND_FORBIDDEN`/`CURRENT_PASSWORD_MISMATCH`/`PASSWORD_POLICY_VIOLATION` / `404 OPERATOR_NOT_FOUND` → inline field-level / actionable (no crash); `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the operators section degrades** (the console shell stays intact).
- **Logging**: structured server-side logs only; operator/IAM tokens, operator emails, and passwords are never logged (redacted) — § 2.6 logging invariant extended to the operators surface (passwords never logged or echoed at all).
- **Producer immutability**: this is a **cross-reference only**. Any change to the operators producer contract is a IAM project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

> **§ 3 parity line satisfiable**: with `features/operators` bound here, the § 3 "operators: create, edit-roles, change-status, change-password" parity line is **satisfiable**; `FE-006` formally verifies it (ADR-MONO-013 Phase 3 admin-web-retirement gate).

#### 2.4.4 IAM operator overview (composed) — TASK-PC-FE-005 — cross-reference, **no new producer**

The fourth concrete binding of § 2.4 (ADR-MONO-013 Phase 2 slice 4 / § 3 parity "dashboards" line). The console's `features/dashboards` renders, **server-side and tenant-scoped**, a **composed operator overview** — **not** a Grafana/observability embed. This is governed by [ADR-MONO-015](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) (ACCEPTED, decision **D1-B**): the console "dashboards" parity line is **refined** (ADR-MONO-015 D2 — recorded explicitly, not decided implicitly) to mean *an operator overview composed from the already-integrated read surfaces*, **not** a reproduction of `admin-web`'s Grafana observability iframe. Observability/Grafana metrics dashboards are **out of scope** of the platform-console parity gate (a future observability ADR, never an admin-web-retirement blocker).

This is a **read-only** binding — there is **no mutation**, therefore the § 2.4.1 mutation scaffolding (`X-Operator-Reason`, `Idempotency-Key`, destructive-confirm dialogs) **does not apply and MUST NOT be carried over** (same read discipline as § 2.4.2; carrying it over is a defect). It also introduces **no new IAM producer endpoint** — it is a **composition of the EXISTING reads** already bound in §§ 2.4.1/2.4.2/2.4.3. IAM `admin-api.md` is **unchanged** (ADR-MONO-015 D1: compose existing reads only; cross-reference, never redefine).

- **Composed producers (owned by IAM, do NOT redefine here — the EXISTING reads only, unchanged)**: the overview is a **bounded fan-out** over the three already-integrated read endpoints in [`iam-platform/specs/contracts/http/admin-api.md`](../../../iam-platform/specs/contracts/http/admin-api.md), consumed through the **existing** FE-002/003/004 server clients (no duplicate / new IAM client):

  | # | Overview card | Composed producer endpoint (`admin-api.md` §) | Existing client (reused) | Base permission | Kind |
  |---|---|---|---|---|---|
  | 1 | accounts summary | `GET /api/admin/accounts` (page total / snapshot) | `features/accounts` `searchAccounts` (§ 2.4.1) | `account.read` (absent ⇒ producer returns an empty list, not 403) | read |
  | 2 | audit + security activity | `GET /api/admin/audit` (recent rows) | `features/audit` `queryAudit` (§ 2.4.2) | `audit.read` (+ `security.event.read` for the security subset — intersection per § 2.4.2) | read |
  | 3 | operators summary | `GET /api/admin/operators` (count / status mix) | `features/operators` `listOperators` (§ 2.4.3) | `operator.manage` (SUPER_ADMIN — non-privileged ⇒ producer 403, that card only) | read |

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every fan-out leg carries `Authorization: Bearer <operator token>` — the operator token obtained via the § 2.6 RFC 8693 exchange (`getOperatorToken()`), **never** the IAM OIDC access token (the legs inherit this from the reused FE-002/003/004 clients). An absent operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` → forced re-login (the console never falls back to the IAM token on the `/api/admin/**` boundary — the #569 invariant). There is **no** `X-Operator-Reason` and **no** `Idempotency-Key` on any leg (read-only — carrying either over from § 2.4.1/§ 2.4.3 is a defect).
- **Tenant scope (§ 2.4 / multi-tenant)**: every leg always sends the operator's selected active tenant as `X-Tenant-Id` (from `getActiveTenant()`); the producer resolves the operator's authoritative tenant scope and rejects cross-tenant (isolation enforced producer-side, never weakened here). When no tenant is selected the console **blocks the overview** with an actionable "select a tenant" state — it never sends an empty/absent `X-Tenant-Id` on any leg.
- **Per-source isolation (the key design point — ADR-MONO-015 D3 / § 2.5)**: the fan-out collects a per-card outcome (`ok` / `degraded` / `forbidden`). One leg failing **MUST NOT** fail the whole overview:
  - `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` on a leg → **that card only** renders a "not available to your role" / scoped placeholder (the operators card respects `operator.manage`/SUPER_ADMIN; the audit card reuses the § 2.4.2 intersection-permission behaviour for the security subset). Not a crash, not a re-login.
  - `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` / timeout on a leg → **that card only** degrades; the overview + the console shell stay intact (never blank). All sources down ⇒ an all-degraded overview with a retry affordance, never a hard crash.
  - **`401` on ANY leg → a whole-overview forced re-login** (auth is **not** a per-card degrade — there is no partial authed state; the operator token is shared across all legs, so a 401 on one is a 401 for all).
- **Bounded + producer-meta-audit-respecting (integration-heavy I1 / audit-heavy A5)**: the fan-out is **bounded** — each leg inherits the reused client's explicit AbortController hard timeout (no unbounded default). The audit leg (`GET /api/admin/audit`) is **meta-audited producer-side** (§ 2.4.2); therefore **one overview load issues exactly one bounded set of calls** — no aggressive polling / auto-refetch / N+1 that would flood the producer's meta-audit. A degraded re-query is an explicit user retry, not an automatic interval.
- **Logging**: structured server-side logs only; operator/IAM tokens and source PII (account ids / masked IPs / operator emails) are never logged (redacted) — § 2.6 logging invariant, inherited from the reused FE-002/003/004 clients.
- **Producer immutability**: this is a **cross-reference + composition only**. There is **no** new IAM producer endpoint and **no** change to any composed producer contract — any such change would be a IAM project-internal spec-first change in `admin-api.md`; this section follows the existing reads, never redefines them, never invents a new one (§ 5 Change Rule; ADR-MONO-015 D1).

> **§ 3 parity line satisfiable**: with `features/dashboards` bound here, the ADR-MONO-015-**refined** § 3 "dashboards" parity line (composed operator overview, **not** Grafana) is **satisfiable**; `FE-006` formally verifies the full refined checklist (ADR-MONO-013 Phase 3 admin-web-retirement gate).

#### 2.4.5 wms operations surface (TASK-PC-FE-007 — cross-reference, not a redefinition)

The **first non-IAM** per-domain binding of § 2.4 (ADR-MONO-013 Phase 4
slice 1). The console's `features/wms-ops` renders, **server-side and
tenant-scoped**, the wms `admin-service` **dashboard read-model** surface plus
the single operational mutation that surface exposes (alert acknowledge). The
producer contract is **authoritative and unchanged** — this section only
states the consumer obligation and points at the owning wms spec. This is the
binding that **verifies** ADR-MONO-013 § 3.3's "zero retrofit" assumption: a
non-IAM domain is bound for the first time, and it surfaces a genuine
**auth-model divergence** from the IAM operator surface (§§ 2.4.1–2.4.4).

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
  | IAM (§§ 2.4.1–2.4.4) | the **exchanged operator token** (`token_type=admin`, `iss=admin-service`), `getOperatorToken()` | server-side RFC 8693 token exchange (§ 2.6) | ADR-MONO-014; the **#569 trust-boundary invariant** (§ 2.1) — the IAM OIDC access token is **never** sent to IAM's `/api/admin/**` |
  | **wms (§ 2.4.5, this binding)** | the **IAM OIDC access token** itself (`getAccessToken()`, the IAM-session HttpOnly cookie from FE-001) | sent **directly** as `Authorization: Bearer <IAM OIDC access token>` | wms `admin-service-api.md` § Global Conventions + `iam-integration.md`: RS256 JWT issued by IAM per ADR-001, validated against IAM JWKS by the wms gateway + admin-service; **`tenant_id=wms` enforced producer-side from the JWT claim**. wms has **no** token-exchange and **requires** the IAM OIDC token |

  **The #569 trust-boundary invariant is IAM-domain-scoped and does NOT
  generalise to wms.** #569 forbids the IAM OIDC access token on **IAM's**
  `/api/admin/**` boundary *because IAM requires the § 2.6 exchanged operator
  token there*. wms's gateway, by contrast, *requires* exactly the IAM OIDC
  access token — these are **not in conflict; they are different per-domain
  bindings**. An implementer must therefore neither (a) wrongly carry the
  IAM operator-token-exchange (§ 2.6) to wms (wms would reject it — wrong
  issuer/type — and it would misapply the IAM-domain auth model), nor (b)
  wrongly treat "a IAM token on an admin path" as a universal #569 violation
  (it is the *required* wms credential). The console's `features/wms-ops`
  client uses `getAccessToken()` and **never** `getOperatorToken()`
  (asserted by test — the inverse of the FE-002..006 assertion). Future
  finance/erp console sections (Phase 5/6) inherit **this stated rule**: each
  new § 2.4.x binding declares its credential explicitly, against its
  producer's auth contract — not a guess copied from another domain.

- **Tenant model divergence**: wms resolves the operator's tenant from the
  **JWT `tenant_id` claim** (`=wms`) — **not** an `X-Tenant-Id` header (the
  IAM §§ 2.4.1–2.4.4 mechanism) and **not** a producer-side
  `admin_operators.tenant_id` lookup (the § 2.2/§ 2.6 IAM mechanism). The
  console therefore does **not** send `X-Tenant-Id` to wms; the tenant is
  carried implicitly inside the IAM OIDC access token. The console presents a
  wms session from the data-driven registry (§ 2.2): the `tenants[]` for
  `productKey=wms` drives which tenant the operator may select; when the
  operator's IAM token is not wms-eligible (no `wms` tenant and not a
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
  any) surface; **carrying IAM's § 2.4.1 `X-Operator-Reason` header over to
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
  **distinct from IAM's flat `{ code, message, timestamp }`**; the wms client
  MUST parse the wms (nested-`error`) shape — assuming IAM's flat shape
  mis-renders / crashes (asserted). Mapping: `401`/`UNAUTHORIZED` → forced
  **whole-session IAM re-login** (the IAM OIDC session expired — not a
  per-section degrade, no partial authed state); `403`/`FORBIDDEN`
  (role-insufficient — e.g. a `WMS_VIEWER` attempting the `WMS_OPERATOR`+
  ack, or a non-`WMS_ADMIN` hitting `projection-status`) → inline "not
  available to your role" (no crash, no re-login loop); `503` /
  `CONFLICT`-class `DUPLICATE_REQUEST` `503` / timeout → **only the wms
  section degrades** (the console shell + the IAM sections stay intact);
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

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is the **IAM
  `admin-web` parity matrix**, finalized by TASK-PC-FE-006 (16/16 rows
  VERIFIED — see § 3). wms is **additive domain scope** federated by the
  console — **not** a IAM-`admin-web` parity-gate row. This binding adds
  **no** row to § 3 and
  changes **none**; the Phase 3 `admin-web`-retirement gate is unaffected.

- **Producer immutability**: this is a **cross-reference only**. Any change
  to the wms admin/dashboard producer contract is a wms project-internal
  spec-first change in `admin-service-api.md`; this section follows it, never
  redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: unlike §§ 2.4.1–2.4.4 (whose closing notes mark
> a § 3 parity line satisfiable), § 2.4.5 has **no** § 3 line. § 3 is the
> IAM `admin-web` absorption parity gate (FE-006-finalized); the wms section
> is a federated **domain** section, the first verification of the
> generalised per-domain integration contract, not a IAM parity capability.

#### 2.4.5.1 wms outbound operations surface (TASK-PC-FE-057 — cross-reference, not a redefinition)

A **second wms surface** federated by the console — the outbound fulfillment
**operations** screen. Where § 2.4.5 binds the wms `admin-service` dashboard
read-model (inventory/alerts), this sub-binding renders the wms
**`outbound-service`** order lifecycle so an operator can drive an outbound
order **pick → pack → ship** from inside the console. It is the on-screen
operator leg of the ecommerce↔wms fulfillment loop (**ADR-MONO-022 § D7**): an
ecommerce purchase auto-creates a wms outbound order (`source =
FULFILLMENT_ECOMMERCE`, status `PICKING`); this screen advances it to
`SHIPPED`, which (via the existing return-leg events) flips the ecommerce order
to `SHIPPED`.

This sub-binding **inherits every wms cross-cutting rule already stated in
§ 2.4.5** and does not restate them: the **credential** (the domain-facing IAM
OIDC access token — `getDomainFacingToken()`, **never** `getOperatorToken()`);
the **tenant model** (tenant rides in the JWT `tenant_id=wms` claim — **no**
`X-Tenant-Id` header; registry-`productKey=wms` eligibility gates the section,
non-eligible → actionable "no wms-scoped access", no cross-tenant call
fabricated); the **nested wms error envelope** `{ "error": { "code", "message",
"timestamp", … } }`; the **resilience** taxonomy (401 → whole-session IAM
re-login; 403 → inline "not available to your role"; 503/timeout → only this
section degrades; AbortController hard timeout; tokens/PII never logged); and
the **§ 3 parity matrix is NOT mutated** (additive domain scope, no § 3 row).

- **Authoritative producer (owned by wms, do NOT redefine here)**: wms
  [`outbound-service-api.md`](../../../wms-platform/specs/contracts/http/outbound-service-api.md)
  — **unchanged, consumed only** (incl. **§ 2.4**, the picking-requests-by-order
  read added by TASK-BE-343, which this surface depends on). Consumed via the
  wms gateway at `/api/v1/outbound/**` (base URL `WMS_OUTBOUND_BASE_URL`, default
  `http://wms.local/api/v1/outbound` — the wms gateway hostname, **distinct from
  the § 2.4.5 `WMS_ADMIN_BASE_URL`** `/api/v1/admin` prefix; same gateway, same
  IAM-OIDC credential, different path prefix):

  | # | Operation | Producer endpoint (`outbound-service-api.md` §) | Kind | Role |
  |---|---|---|---|---|
  | 1 | list outbound orders | `GET /orders` (§ 1.3) | read | `OUTBOUND_READ` |
  | 2 | order detail (lines + status + version) | `GET /orders/{id}` (§ 1.2) | read | `OUTBOUND_READ` |
  | 3 | saga state | `GET /orders/{id}/saga` (§ 5.1) | read | `OUTBOUND_READ` |
  | 4 | picking requests + planned lines | `GET /orders/{id}/picking-requests` (§ 2.4 — TASK-BE-343) | read | `OUTBOUND_READ` |
  | 5 | **confirm pick** | `POST /picking-requests/{id}/confirmations` (§ 2.3) | **mutation** | `OUTBOUND_WRITE` |
  | 6 | **create packing unit** | `POST /orders/{id}/packing-units` (§ 3.1) | **mutation** | `OUTBOUND_WRITE` |
  | 7 | **seal packing unit** | `PATCH /packing-units/{id}` (§ 3.2, `seal:true`) | **mutation** | `OUTBOUND_WRITE` |
  | 8 | **confirm shipping** | `POST /orders/{id}/shipments` (§ 4.1) | **mutation** | `OUTBOUND_WRITE` |
  | 9 | **cancel order** | `POST /orders/{id}:cancel` (§ 1.4) | **mutation** | `OUTBOUND_WRITE` (PICKING) / `OUTBOUND_ADMIN` (post-pick) |
  | 10 | **retry TMS notify** | `POST /shipments/{id}:retry-tms-notify` (§ 4.3) | **mutation** | `OUTBOUND_ADMIN` |

  The wms outbound **manual order-create** (`POST /orders`, § 1.1) remains
  **out of v1 console scope** — deferred, not silently dropped (manual create
  contradicts the auto-create-from-ecommerce model). The console outbound
  surface = the read set + the forward pick→pack→ship lifecycle advance + the
  cancel action (op 9, TASK-PC-FE-085) + the TMS-retry recovery action
  (op 10, TASK-PC-FE-087).

  **Cancel (op 9 — the one NON-forward action; TASK-PC-FE-085) mutation shape**
  (consumes producer § 1.4 unchanged; diverges from the reason-free ops 5–8 in
  three producer-defined ways — record what § 1.4 requires, do NOT cargo-cult):
  - **reason is REQUIRED** (3..500 chars) — UNLIKE the reason-free forward
    actions. It rides in the producer JSON body `{ reason, version }`, **NOT** a
    header (the wms surface still has no `X-Operator-Reason`). The console
    validates 3..500 client-side (the producer is still the final authority).
  - **`Idempotency-Key`** (UUID, stable per a confirmed cancel / fresh per a new
    attempt — same posture as ship) + the order **`version`** (optimistic lock;
    the proxy reads `GET /orders/{id}` for it server-side, exactly like ship).
  - **role escalation** — `OUTBOUND_WRITE` for `PICKING` (pre-pick),
    `OUTBOUND_ADMIN` for `PICKED`/`PACKING`/`PACKED` (post-pick). The console
    does **NOT** pre-gate on role (it does not hold the operator's wms role
    catalog) — it attempts and maps a `403 FORBIDDEN` to an inline actionable
    state, plus a pre-emptive "needs admin" hint for post-pick orders.
  - allowed only for `status ∈ {PICKING,PICKED,PACKING,PACKED}`; `SHIPPED → 422
    ORDER_ALREADY_SHIPPED`; a re-cancel with the same `Idempotency-Key` is an
    idempotent no-op, otherwise `STATE_TRANSITION_INVALID`.
  - **async** — the response `sagaState` is `CANCELLATION_REQUESTED` (NOT yet
    terminal `CANCELLED`) when a reservation was held; it transitions to
    `CANCELLED` later once `inventory.released` is consumed. The UI surfaces a
    non-blocking "재고 해제 대기" hint, never asserting a synchronous terminal.

  **TMS retry (op 10 — the recovery admin action; TASK-PC-FE-087) mutation
  shape** (consumes producer § 4.3 unchanged; the recovery sibling to cancel —
  re-triggers the carrier notification for a shipped order whose TMS notify
  failed):
  - **trigger signal** — surfaced ONLY for an order with `status=SHIPPED` AND
    saga **`SHIPPED_NOT_NOTIFIED`** (producer allows § 4.3 only when the
    shipment `tmsStatus == NOTIFY_FAILED`; the order saga state is the
    order-level read signal — the admin `ShipmentSummary` read-model does NOT
    project `tmsStatus`, so the saga (op 3) is authoritative for "needs retry").
  - **shipment-id resolution (the net-new mechanic — NOT a producer change)** —
    § 4.3 is **shipment-keyed**, but the outbound order-centric reads carry no
    `shipmentId` (§ 1.2 order detail = create-response shape; there is no
    `GET /orders/{id}/shipments`). The proxy resolves it server-side from the
    **admin read-model** `GET /api/v1/admin/dashboard/shipments?orderId={id}`
    (admin-service-api.md § 1.3 — the `orderId` filter is contracted) → first
    `shipmentId`. This reads `WMS_ADMIN_BASE_URL` (§ 2.4.5) with the **SAME**
    IAM-OIDC domain-facing credential as the outbound mutation — same wms
    gateway, distinct `/api/v1/admin` vs `/api/v1/outbound` path prefix. No
    shipment resolves → `404 SHIPMENT_NOT_FOUND` inline (NO outbound retry POST
    is fired).
  - **reason-free** — re-notifies the carrier only (stock already consumed),
    UNLIKE cancel's required reason. Empty/`{}` body + an `Idempotency-Key`
    (UUID, stable per a confirmed retry / fresh per a new attempt). NO
    `X-Operator-Reason` (the wms surface still has none).
  - **role** — producer-enforced **`OUTBOUND_ADMIN`** (no escalation matrix,
    UNLIKE cancel). The console does NOT pre-gate on role — it attempts and maps
    a `403 FORBIDDEN` to an inline actionable state, plus a pre-emptive "needs
    OUTBOUND_ADMIN" hint.
  - **outcomes** — success: `tmsStatus → NOTIFIED`, `sagaState → COMPLETED`
    (recovery). Not in `NOTIFY_FAILED` → `422 STATE_TRANSITION_INVALID`. Same
    `Idempotency-Key` re-retry → `409 DUPLICATE_REQUEST` (idempotent no-op — no
    double carrier notification). A still-failing carrier leaves the shipment
    `NOTIFY_FAILED` / saga `SHIPPED_NOT_NOTIFIED` → the action stays available.

  § 3 parity matrix **not** mutated (additive non-IAM domain mutation, like the
  rest of § 2.4.5.1).

- **"Confirm as planned" semantics (the correctness crux — normative)**: the
  console does **not** invent warehouse master data. Each lifecycle-advance
  action pre-fills the producer body from already-read planned/detail data:
  - **Pick**: read `GET /orders/{id}/picking-requests` (op 4); take
    `content[0].lines`; build the § 2.3 confirmation lines as
    `actualLocationId = line.locationId`, `qtyConfirmed = line.qtyToPick`,
    carrying `orderLineId`/`skuId`/`lotId` through verbatim. The operator
    confirms the **system-planned** pick — the console never fabricates a
    `locationId` or quantity. (Reachable only when the order is `PICKING` and
    the saga is `RESERVED`; otherwise the action is disabled with the saga
    state shown.)
  - **Pack**: one `POST /orders/{id}/packing-units` (op 6) with all order
    lines (`qty = order line ordered qty` from op 2), then `PATCH
    /packing-units/{packingUnitId}` (op 7) `seal:true` using the
    `packingUnitId` + `version` from op 6's response → order `PACKED`. Two
    producer calls, **each with its own `Idempotency-Key`**.
  - **Ship**: `POST /orders/{id}/shipments` (op 8) with the order `version`
    from op 2 → `SHIPPED`.

- **Mutation discipline**: every POST/PATCH (ops 5–8) carries an
  `Idempotency-Key` (UUID; producer scope `(Idempotency-Key, method, path)`,
  TTL 24h per `outbound-service-api.md` § Idempotency Semantics), is
  **confirm-gated** in the UI, and is **reason-free** — the wms outbound
  surface does **not** define `X-Operator-Reason` (carrying IAM's § 2.4.1
  reason header is a header-matrix-drift defect, asserted absent). The key is
  `crypto.randomUUID()`, **stable across one user-confirmed action** (a
  replayed confirmed action reuses it → producer replays the cached response)
  and **freshly regenerated per a new confirmed attempt**. The compound Pack
  action's two calls each get their own stable key. All reads (ops 1–4) carry
  **no** mutation artifacts (no `Idempotency-Key`, no body) — asserted.

- **Optimistic-lock honesty**: the seal (op 7) and ship (op 8) — and the
  producer-required `version` on each — assert "I have seen this state". On
  `409 CONFLICT` (stale version) the console **refetches** the order/unit and
  surfaces an actionable "state changed, review and retry" — it does **not**
  silently auto-retry with a bumped version. `422 STATE_TRANSITION_INVALID`
  (e.g. pack attempted before pick-confirm, ship before pack-complete) → inline
  actionable with the current status shown.

- **Producer immutability**: cross-reference only. Any change to the wms
  outbound producer contract is a wms project-internal spec-first change in
  `outbound-service-api.md`; this section follows it, never redefines it (§ 5
  Change Rule).

> **Not a § 3 parity row** (same as § 2.4.5): the wms outbound surface is
> additive federated **domain** scope, not a IAM `admin-web` parity capability;
> it adds no § 3 row and changes none.

#### 2.4.6 scm operations surface (TASK-PC-FE-008 — cross-reference, not a redefinition)

The **second non-IAM** per-domain binding of § 2.4 (ADR-MONO-013 Phase 4
slice 2 — the slice that **completes** Phase 4: `FE-007 wms` → `FE-008 scm`).
The console's `features/scm-ops` renders, **server-side and tenant-scoped**,
the scm gateway's existing **read-only** procurement-PO and
inventory-visibility surface. There is **no operator-mutation parity** for
scm at v1 (scm has no `admin-service` — deferred to scm v2 per
`gateway-public-routes.md`); this section is **strictly read-only**. The
producer contracts are **authoritative and unchanged** — this section only
states the consumer obligation and points at the owning scm specs. This
binding is the second instance that verifies ADR-MONO-013 § 3.3's "zero
retrofit" assumption across a non-IAM domain, and the proof that the
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
  rule with the same outcome as wms**: the scm gateway validates a IAM
  RS256 JWT (ADR-001) against IAM's JWKS, `tenant_id ∈ { scm, * }` enforced
  producer-side from the JWT claim (scm `gateway-public-routes.md`
  § *platform-console operator read consumer* — the merged TASK-SCM-BE-015
  reconciliation that sanctions the console as an external read consumer of
  the existing scm gateway capability: `AllowedIssuersValidator` +
  `TenantClaimValidator` + `X-Token-Type=user`). The credential is
  therefore the operator's **IAM `platform-console-web` OIDC access token**
  itself (`getAccessToken()`, the IAM-session HttpOnly cookie from FE-001),
  sent **directly** as `Authorization: Bearer <IAM OIDC access token>`
  server-side — **never** the IAM § 2.6 exchanged operator token
  (`getOperatorToken()`; that is IAM-domain-scoped — the #569
  trust-boundary invariant does **not** generalise to scm, exactly as
  § 2.4.5 states for wms). The console's `features/scm-ops` client uses
  `getAccessToken()` and **never** `getOperatorToken()` (asserted by test —
  the same shape as the FE-007 assertion; the cross-domain regression is
  extended so IAM = operator-token / wms = IAM-OIDC / scm = IAM-OIDC all
  hold in one place). **Tenant model**: scm resolves the tenant from the
  JWT `tenant_id` claim producer-side — the console does **not** send
  `X-Tenant-Id` (the tenant rides inside the IAM OIDC token, exactly the
  § 2.4.5 wms divergence). When the operator's IAM token is not
  scm-eligible (no `scm` tenant and not a platform-scope `*` operator) the
  console **blocks the section** with an actionable "no scm-scoped access"
  state — no cross-tenant call is ever fabricated; scm rejects cross-tenant
  producer-side regardless (`403 TENANT_FORBIDDEN`, never weakened here).

- **Read-only binding (normative — no mutation scaffolding at all)**: there
  is **no** mutation anywhere in this section. **No** `Idempotency-Key`,
  **no** `X-Operator-Reason`, **no** confirm dialogs, **no** PO write call
  (`/submit|/confirm|/cancel`), **no** procurement webhook. Carrying the
  FE-007 alert-ack mutation scaffolding **or** the IAM § 2.4.1 mutation
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
  nested shape and IAM's)**: the scm gateway/service error envelope is
  **flat** `{ code, message, details?, timestamp }` (per
  `procurement-api.md` / `inventory-visibility-api.md` § Error Codes /
  `platform/error-handling.md`) — **NOT** wms's nested
  `{ error: { code … } }` (§ 2.4.5) and not assumed-identical to IAM's. The
  scm client MUST parse the scm **flat** shape (a wms-nested parser would
  mis-render / crash — asserted). Mapping: `401 UNAUTHORIZED` → forced
  **whole-session IAM re-login** (the IAM OIDC session expired — not a
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
  + the IAM/wms sections stay intact). **Freshness honesty**: the
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

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is the **IAM
  `admin-web` parity matrix**, finalized by TASK-PC-FE-006 (16/16 rows; see
  § 3). scm is **additive domain scope** federated by the console — **not**
  a IAM-`admin-web` parity-gate row. This binding adds **no** row to § 3
  and changes **none**; the Phase 3 `admin-web`-retirement gate is
  unaffected. (This § 2.4.6 prose deliberately does **not** use the § 3.1
  per-row attestation marker phrase, so the FE-006 no-drift guard's count
  of that marker stays exactly 16.)

> **Not a § 3 parity row**: like § 2.4.5 and unlike §§ 2.4.1–2.4.4,
> § 2.4.6 has **no** § 3 line. § 3 is the IAM `admin-web` absorption parity
> gate (FE-006-finalized); the scm section is a federated **domain**
> section — the binding that **completes ADR-MONO-013 Phase 4** (wms +
> scm) and confirms the § 2.4.5 per-domain credential rule generalises.
> Phase 5/6 finance/erp console sections inherit this proven non-IAM
> contract (each new § 2.4.x binding declares its own credential against
> its producer, per the § 2.4.5 rule — not a guess copied from another
> domain).

#### 2.4.6.1 scm demand-planning replenishment-suggestions operator surface (TASK-PC-FE-077 — cross-reference, not a redefinition)

A **second scm service** bound by the console — the
**`demand-planning-service`** alongside the § 2.4.6
procurement/inventory-visibility read surface — exactly as § 2.4.5.1 binds a
**second wms service** (`outbound-service`) alongside the § 2.4.5
`admin-service`, and § 2.4.7.1 binds the finance `ledger-service` alongside the
§ 2.4.7 `account-service`. The console's `features/scm-replenishment` renders,
**server-side and tenant-scoped**, the scm demand-planning gateway's existing
reorder-**suggestion** surface: the operator reviews `SUGGESTED` reorder
suggestions and **approves** (→ a **DRAFT** PO) or **dismisses** them. This is
the on-screen **human operator gate** of the wms→scm replenishment loop
(**ADR-MONO-027 § D2/D5**): a wms low-stock alert auto-creates a `SUGGESTED`
reorder suggestion; this screen is the human gate that turns it into a DRAFT
PO. It is the **FIRST scm operator-MUTATION surface** (the § 2.4.6 read
foundation had none — scm had no `admin-service` at v1). The producer contract
is **authoritative and unchanged** — this section only states the consumer
obligation and points at the owning scm spec.

This sub-binding **inherits every scm cross-cutting rule already stated in
§ 2.4.6** and does not restate them: the **credential** (the domain-facing IAM
OIDC access token — `getDomainFacingToken()`, **never** `getOperatorToken()`;
scm has NO token-exchange — the #569 invariant is GAP-domain-scoped); the
**tenant model** (tenant rides in the JWT `tenant_id ∈ {scm,*}` claim — **no**
`X-Tenant-Id` header; registry-`productKey=scm` eligibility gates the section,
non-eligible → actionable "no scm-scoped access", no cross-tenant call
fabricated); the **flat scm error envelope** `{ code, message, details?,
timestamp }` (DISTINCT from wms's nested `{ error: { code } }`); the **429
`Retry-After` bounded backoff** (the SAME rate-limited scm gateway as § 2.4.6 —
reused verbatim, ONE bounded retry, no storm); the **resilience** taxonomy
(401 → whole-session IAM re-login; 403 → inline "not scoped"; 503/timeout →
only this section degrades; AbortController hard timeout; tokens/PII never
logged); and the **§ 3 parity matrix is NOT mutated** (additive domain scope,
no § 3 row).

- **Authoritative producer (owned by scm, do NOT redefine here — consumed
  unchanged)**: scm
  [`demand-planning-api.md`](../../../scm-platform/specs/contracts/http/demand-planning-api.md)
  — **unchanged, consumed only**. Consumed via the scm gateway at
  `/api/v1/demand-planning/**` (base URL `SCM_GATEWAY_BASE_URL`, default
  `http://scm.local` — the SAME scm gateway as the § 2.4.6 read surface). The
  console consumes exactly these endpoints:

  | # | Operation | Producer endpoint (`demand-planning-api.md` §) | Kind |
  |---|---|---|---|
  | 1 | list reorder suggestions | `GET /api/v1/demand-planning/suggestions` (`?status=SUGGESTED\|APPROVED\|MATERIALIZED\|DISMISSED`, `?skuCode`, paginated) | read |
  | 2 | suggestion detail | `GET /api/v1/demand-planning/suggestions/{id}` | read |
  | 3 | **approve** | `POST /api/v1/demand-planning/suggestions/{id}/approve` (→ resolves `sku_supplier_map` → DRAFT PO → `MATERIALIZED`) | **mutation** |
  | 4 | **dismiss** | `POST /api/v1/demand-planning/suggestions/{id}/dismiss` (`* → DISMISSED`) | **mutation** |

  The demand-planning **`policies`** (`GET\|PUT /policies/{skuCode}`) and
  **`sku-supplier-map`** (`GET\|PUT /sku-supplier-map/{skuCode}`) **seed** routes
  are an admin-seed surface, **NOT** the operator gate — they are **explicitly
  out of scope** (not silently dropped). The console v1 replenishment surface =
  the suggestion read set + the approve/dismiss operator gate.

- **Mutation discipline (the net-new part — record what `demand-planning-api.md`
  ACTUALLY requires, do NOT cargo-cult IAM § 2.4.1)**: approve/dismiss are
  `POST` with an **OPTIONAL** JSON body (`{ note }` / `{ reason }`). The
  producer is **server-side idempotent by suggestion state** (re-approve
  returns the existing `poId`; re-dismiss is a no-op) — so a client
  `Idempotency-Key` header is **NOT** required by the contract and is **NOT**
  attached (do not invent one), and the operator reason rides in the **body**,
  **NOT** an `X-Operator-Reason` header (the producer defines neither header;
  carrying IAM's § 2.4.1 scaffolding over is a defect — a test asserts **both**
  absent). Both actions are **confirm-gated** in the UI (they mutate domain
  state). The same domain-facing IAM OIDC credential serves the reads **and**
  the two actions (no stronger credential — the gate is server-side `tenant_id`
  validation + the producer's DRAFT-PO-only invariant). This mirrors the
  body-carried-reason / no-invented-key discipline of the § 2.4.7.1 finance
  ledger reconciliation *resolve* and the § 2.4.8.1 erp delegation *revoke*.

- **Operator-gate invariant surfaced in UI (normative)**: approve materialises a
  **DRAFT** PO only — the screen MUST show the resulting `poId` + `poStatus:
  DRAFT` and make explicit that submission is a **separate** Procurement step
  (this screen NEVER issues a PO submit/confirm/cancel call — a test asserts
  this; the DRAFT PO is dispatched via procurement's existing `DRAFT →
  SUBMITTED` flow, reachable from the § 2.4.6 scm-ops PO surface). This is the
  ADR-MONO-027 D5 human-gate invariant made visible. Each suggestion row shows
  the `triggerAvailableQty` that explains **why** it was suggested.

- **Resilience (§ 2.5) — action-specific producer errors mapped to actionable
  inline states (flat scm envelope)**:
  - `SKU_SUPPLIER_UNMAPPED` (422) → inline "no supplier mapping; cannot
    reorder"; the suggestion stays `SUGGESTED` (no optimistic transition).
  - `INVALID_SUGGESTION_STATE` (422) → inline (e.g. cannot approve a
    `DISMISSED` one / dismiss a `MATERIALIZED` one); the action button is also
    state-disabled, the inline error is the backstop.
  - `SUGGESTION_ALREADY_MATERIALIZED` (409 / 200-idempotent) → the idempotent
    200 is treated as **success** showing the existing `poId` (no duplicate-PO
    assumption, no error toast); a hard 409 is a benign "already materialized"
    notice with the existing `poId`.
  - `SUGGESTION_NOT_FOUND` (404) → inline.
  - Plus the shared § 2.4.6 mappings: `401 UNAUTHORIZED` → forced whole-session
    IAM re-login; `403 TENANT_FORBIDDEN`/`FORBIDDEN` → inline "not scoped";
    `429 RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → bounded backoff (no storm);
    `503`/timeout → only this section degrades (the console shell + the § 2.4.6
    scm read section stay intact). Successful mutations invalidate the list +
    detail (the `SUGGESTED → MATERIALIZED|DISMISSED` transition reflects without
    a manual reload). Unknown/future suggestion `status` or `source` enum values
    degrade to a generic label — the consumer parser is tolerant and never
    throws (the same tolerant-parser discipline as the § 2.4.6 PO/node status).

- **Producer immutability**: this is a **cross-reference only**. Any change to
  the scm demand-planning producer contract is an scm project-internal
  spec-first change in `demand-planning-api.md`; this section follows it, never
  redefines it (§ 5 Change Rule). The scm-side acknowledgment of this console
  operator-**action** consumer is the merged scm `gateway-public-routes.md`
  § *platform-console operator action consumer* (TASK-SCM-BE-027) — the
  spec-first basis for this binding (the operator-action analog of the § 2.4.6
  read consumer TASK-SCM-BE-015).

> **Not a § 3 parity row** (same as § 2.4.6): the scm replenishment surface is
> additive federated **domain** scope, not a IAM `admin-web` parity capability;
> it adds no § 3 row and changes none. It is the SECOND scm service binding (the
> scm analog of the § 2.4.5 + § 2.4.5.1 wms pair and the § 2.4.7 + § 2.4.7.1
> finance pair) and the **first scm operator mutation** — confirming the
> § 2.4.5/§ 2.4.6 per-domain credential rule holds for a non-IAM **write**
> surface (the gate is server-side `tenant_id` + the producer DRAFT-PO-only
> invariant, NOT a stronger credential).

#### 2.4.6.2 scm demand-planning reorder-policy + sku-supplier-map seed/config operator surface (TASK-PC-FE-080 — cross-reference, not a redefinition)

A **third scm binding** by the console — the `demand-planning-service`'s per-SKU
**seed/config** routes (`policies` + `sku-supplier-map`), the **operator config
arm** of the same `demand-planning-service` whose suggestion-gate § 2.4.6.1
binds. It is the on-screen **operational fix-path** for the § 2.4.6.1 gap: when
approve fails `SKU_SUPPLIER_UNMAPPED` (422, no `sku_supplier_map` row), the
operator today has no console way to add the mapping. The console's
`features/scm-config` renders, **server-side and tenant-scoped**, a
**SKU-code-driven** inspect (GET) + upsert (PUT) surface over those routes, so an
operator can set the per-SKU reorder policy + SKU→supplier mapping that drive
**future** reorder evaluation, then return to 보충 (§ 2.4.6.1) and approve. The
producer contract is **authoritative and unchanged** — this section only states
the consumer obligation and points at the owning scm spec.

This sub-binding **inherits every scm cross-cutting rule already stated in
§ 2.4.6 / § 2.4.6.1** and does not restate them: the **credential** (the
domain-facing IAM OIDC access token — `getDomainFacingToken()`, **never**
`getOperatorToken()`; scm has NO token-exchange — the #569 invariant is
GAP-domain-scoped; same credential as the read + action + config surfaces); the
**tenant model** (tenant rides in the JWT `tenant_id ∈ {scm,*}` claim — **no**
`X-Tenant-Id` header; registry-`productKey=scm` eligibility gates the section,
non-eligible → actionable "no scm-scoped access", no cross-tenant call
fabricated); the **flat scm error envelope** `{ code, message, details?,
timestamp }` (DISTINCT from wms's nested `{ error: { code } }`); the **429
`Retry-After` bounded backoff** (the SAME rate-limited scm gateway — reused
verbatim, ONE bounded retry, no storm); the **resilience** taxonomy (401 →
whole-session IAM re-login; 403 → inline "not scoped"; 503/timeout → only this
section degrades; AbortController hard timeout; tokens/PII never logged); and the
**§ 3 parity matrix is NOT mutated** (additive domain scope, no § 3 row).

- **Authoritative producer (owned by scm, do NOT redefine here — consumed
  unchanged)**: scm
  [`demand-planning-api.md`](../../../scm-platform/specs/contracts/http/demand-planning-api.md)
  — **unchanged, consumed only**. Consumed via the scm gateway at
  `/api/v1/demand-planning/**` (base URL `SCM_GATEWAY_BASE_URL`, default
  `http://scm.local` — the SAME scm gateway as the § 2.4.6 / § 2.4.6.1 surfaces).
  The console consumes exactly these endpoints:

  | # | Operation | Producer endpoint (`demand-planning-api.md` §) | Kind |
  |---|---|---|---|
  | 1 | inspect reorder policy | `GET /api/v1/demand-planning/policies/{skuCode}` (`200` row · `404 POLICY_NOT_FOUND`) | read |
  | 2 | **upsert reorder policy** | `PUT /api/v1/demand-planning/policies/{skuCode}` (body `{ reorderPoint, safetyStock, reorderQty }` → `200` upserted) | **mutation** |
  | 3 | inspect sku→supplier map | `GET /api/v1/demand-planning/sku-supplier-map/{skuCode}` (`200` row · `404 MAPPING_NOT_FOUND`) | read |
  | 4 | **upsert sku→supplier map** | `PUT /api/v1/demand-planning/sku-supplier-map/{skuCode}` (body `{ supplierId, defaultOrderQty, leadTimeDays, currency }` → `200` upserted) | **mutation** |

  **No list route**: the producer exposes ONLY per-`{skuCode}` GET/PUT (there is
  **no** "list all policies/mappings"). The console surface is therefore
  **SKU-code-driven** — the operator enters a SKU code, the screen GETs both rows
  and lets them upsert each. The scm-side acknowledgment of this console operator
  **config (seed)** consumer is the merged scm `gateway-public-routes.md`
  § *platform-console operator config (seed) consumer* (TASK-SCM-BE-028) — the
  spec-first basis for this binding (the seed analog of the § 2.4.6.1 action
  consumer TASK-SCM-BE-027, which deliberately fenced the seed routes OUT).

- **Mutation discipline (the net-new part — record what `demand-planning-api.md`
  ACTUALLY requires, do NOT cargo-cult IAM § 2.4.1 nor the § 2.4.6.1 action
  scaffolding)**: PUT is an **idempotent upsert** — the request **body IS the
  FULL row** (full-row replace). A confirm step is **required UX** (it mutates
  seed state), but there is **NO** invented `Idempotency-Key` header and **NO**
  IAM `X-Operator-Reason` header (the producer defines NEITHER — the body is the
  row; carrying either over is a defect, a test asserts **both** absent on PUT).
  The same domain-facing IAM OIDC credential serves the GET inspect **and** the
  PUT upsert (no stronger credential — the gate is server-side `tenant_id`
  validation). `supplierId` is a **free-text/uuid** input in v1 — there is no
  supplier master to resolve against (the `sku_supplier_map` is the deliberate
  minimal stand-in per ADR-MONO-027 D3); the console validates shape only.

- **Config-surface invariant surfaced in UI (normative)**: editing the seed rows
  affects **future** reorder-suggestion evaluation only — the screen MUST make
  clear it does **not** retroactively change existing suggestions or POs and does
  **not** dispatch anything (a test asserts the screen issues **no**
  suggestion/PO/dispatch call — only `policies` / `sku-supplier-map` GET/PUT). A
  GET `404` (`POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND`) is **not** an error — it is
  "not configured yet", a first-time **create** via PUT (rendered as an
  actionable empty state, NEVER an error toast; a test pins this).

- **Resilience (§ 2.5) — seed-specific producer states mapped to actionable
  inline states (flat scm envelope)**:
  - `POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND` (404) → "not configured yet → create"
    empty state (NOT an error); a subsequent PUT creates the row.
  - `VALIDATION_ERROR` (422, e.g. a negative qty) → inline field errors; the
    screen does not lose the entered values.
  - Plus the shared § 2.4.6 mappings: `401 UNAUTHORIZED` → forced whole-session
    IAM re-login; `403 TENANT_FORBIDDEN`/`FORBIDDEN` → inline "not scoped";
    `429 RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → bounded backoff (no storm);
    `503`/timeout → only this section degrades (the console shell + the § 2.4.6
    운영 read section + the § 2.4.6.1 보충 action section stay intact). A
    successful PUT invalidates the corresponding read (the not-configured →
    configured transition reflects without a manual reload). A
    forward-compatible producer extra field degrades gracefully — the consumer
    parser is tolerant and never throws.

- **Producer immutability**: this is a **cross-reference only**. Any change to
  the scm demand-planning producer contract is an scm project-internal spec-first
  change in `demand-planning-api.md`; this section follows it, never redefines it
  (§ 5 Change Rule). scm remains single-organization — this binding adds no
  multi-tenant declaration to scm.

> **Not a § 3 parity row** (same as § 2.4.6 / § 2.4.6.1): the scm seed/config
> surface is additive federated **domain** scope, not a IAM `admin-web` parity
> capability; it adds no § 3 row and changes none. It is the THIRD scm binding
> (운영 read § 2.4.6 + 보충 action § 2.4.6.1 + 설정 config here) and the **first
> scm config-mutation** — confirming the § 2.4.5/§ 2.4.6 per-domain credential
> rule holds for a non-IAM **upsert** surface (the gate is server-side
> `tenant_id`, NOT a stronger credential).

#### 2.4.7 finance operations surface (TASK-PC-FE-009 — cross-reference, not a redefinition)

The **third non-IAM** per-domain binding of § 2.4 (ADR-MONO-013 Phase 5 —
the slice that **closes** the non-IAM federation cycle: `FE-007 wms` →
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
retrofit" assumption across a non-IAM domain, and the proof that the
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
  at v1). This is the *inverse* of the FE-002 IAM situation (IAM had
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
  `account-service` validates a IAM RS256 JWT (ADR-001) against IAM's
  JWKS, `tenant_id ∈ { finance, * }` enforced producer-side from the
  JWT claim (finance
  [`iam-integration.md`](../../../finance-platform/specs/integration/iam-integration.md)
  § *platform-console Operator Read Consumer* — the merged
  TASK-FIN-BE-005 reconciliation that sanctions the console as an
  external operator IAM-token read consumer of the existing finance
  read surface: `AllowedIssuersValidator` + `TenantClaimValidator`
  + `X-Token-Type=user`). The credential is therefore the operator's
  **IAM `platform-console-web` OIDC access token** itself
  (`getAccessToken()`, the IAM-session HttpOnly cookie from FE-001),
  sent **directly** as `Authorization: Bearer <IAM OIDC access token>`
  server-side — **never** the IAM § 2.6 exchanged operator token
  (`getOperatorToken()`; that is IAM-domain-scoped — the #569
  trust-boundary invariant does **not** generalise to finance, exactly
  as § 2.4.5 states for wms and § 2.4.6 confirms for scm). The
  console's `features/finance-ops` client uses `getAccessToken()` and
  **never** `getOperatorToken()` (asserted by test — the same shape as
  the FE-007/FE-008 assertions; the cross-domain regression is
  extended so IAM = operator-token / wms = IAM-OIDC / scm = IAM-OIDC /
  **finance = IAM-OIDC** all hold in one place). **Tenant model**:
  finance resolves the tenant from the JWT `tenant_id` claim
  producer-side — the console does **not** send `X-Tenant-Id` (the
  tenant rides inside the IAM OIDC token, exactly the § 2.4.5 / § 2.4.6
  divergence). When the operator's IAM token is not finance-eligible
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
  FE-007 alert-ack mutation scaffolding **or** the IAM § 2.4.1
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
  `401 UNAUTHORIZED` → forced **whole-session IAM re-login** (the
  IAM OIDC session expired — not a per-section degrade, no partial
  authed state, consistent with FE-002..008);
  `403 TENANT_FORBIDDEN` / `403 PERMISSION_DENIED` /
  `403 FORBIDDEN` (token not finance-scoped or insufficient scope) →
  inline "not available / not scoped" (no crash, no re-login loop);
  `404 ACCOUNT_NOT_FOUND` → inline actionable "no such account" (no
  crash); `400 VALIDATION_ERROR` / `422` → inline actionable;
  `503 SERVICE_UNAVAILABLE` / timeout / network → **only the finance
  section degrades** (the console shell + the IAM / wms / scm
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
  finance `iam-integration.md` § *platform-console Operator Read
  Consumer* (TASK-FIN-BE-005) — the spec-first basis for this
  binding.

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is the
  **IAM `admin-web` parity matrix**, finalized by TASK-PC-FE-006
  (16/16 rows; see § 3). finance is **additive domain scope**
  federated by the console — **not** a IAM-`admin-web` parity-gate
  row. This binding adds **no** row to § 3 and changes **none**; the
  Phase 3 `admin-web`-retirement gate is unaffected. (This § 2.4.7
  prose deliberately does **not** use the § 3.1 per-row attestation
  marker phrase, so the FE-006 no-drift guard's count of that marker
  stays exactly 16 — the FE-006 guard remains green after this
  binding.)

> **Not a § 3 parity row**: like § 2.4.5 / § 2.4.6 and unlike
> §§ 2.4.1–2.4.4, § 2.4.7 has **no** § 3 line. § 3 is the IAM
> `admin-web` absorption parity gate (FE-006-finalized); the finance
> section is a federated **domain** section — the **third** instance
> that verifies ADR-MONO-013 § 3.3's "zero retrofit" assumption across
> a non-IAM domain, and the second confirmation that the § 2.4.5
> per-domain credential rule generalises (wms → scm → **finance**).
> ADR-MONO-013 Phase 5 = COMPLETE; erp (Phase 6) inherits the proven
> non-IAM contract (third confirmation of § 3.3 zero-retrofit).

#### 2.4.7.1 finance ledger operations surface (TASK-PC-FE-072 — cross-reference, not a redefinition)

The **second finance-product service** bound by the console — the
**`ledger-service`** alongside the § 2.4.7 `account-service` — exactly as
§ 2.4.5.1 binds a **second wms service** (`outbound-service`) alongside the
§ 2.4.5 `admin-service`. The console's `features/ledger-ops` renders,
**server-side and tenant-scoped**, the finance `ledger-service`'s existing
**read-only** double-entry general-ledger surface: the trial balance, the
journal-entry detail, the accounting periods, and the reconciliation
discrepancy review queue. This binding makes the **eleven ledger increments**
(FIN-BE-007…017 — double-entry posting, period close, manual posting,
reconciliation, multi-currency journals, FX revaluation, FX settlement,
multi-currency reconciliation) **operator-visible** for the first time. It is
**strictly read-only**: the ledger's operator **mutation** endpoints
(`POST /entries` manual posting, `POST /revaluations`, `POST /settlements`,
reconciliation `POST /statements` ingest + `…/resolve`) are domain
journal-movement / operator-domain mutations (each `Idempotency-Key`-gated,
fintech F1) — **explicitly out of scope** here (not silently dropped), exactly
as the finance § 2.4.7 account write surface is. The producer contracts are
**authoritative and unchanged** — this section only states the consumer
obligation and points at the owning finance specs.

- **Authoritative producers (owned by finance, do NOT redefine here —
  consumed read-only)**: finance
  [`ledger-api.md`](../../../finance-platform/specs/contracts/http/ledger-api.md)
  and
  [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md)
  — **unchanged, consumed only**. The console consumes exactly these GET
  endpoints (request/response/headers/error tables are canonical there):

  | # | Operation | Producer endpoint (`ledger-api.md` / `reconciliation-api.md` §) | Kind |
  |---|---|---|---|
  | 1 | trial balance | `GET /api/finance/ledger/trial-balance` (per-account debit/credit + base-currency totals; grand base totals; `inBalance`) | read |
  | 2 | accounting periods (list) | `GET /api/finance/ledger/periods` (paginated, most-recent window first) | read |
  | 3 | accounting period (detail) | `GET /api/finance/ledger/periods/{periodId}` (incl. close `snapshot` when CLOSED) | read |
  | 4 | journal entry (detail) | `GET /api/finance/ledger/entries/{entryId}` (lines: `money` + `exchangeRate` + `baseAmount`; `source.sourceType`; `balanced`) | read |
  | 5 | reconciliation discrepancies (queue) | `GET /api/finance/ledger/reconciliation/discrepancies` (`?status=OPEN\|RESOLVED`, paginated) | read |
  | 6 | reconciliation discrepancy (detail) | `GET /api/finance/ledger/reconciliation/discrepancies/{id}` (incl. `resolution` when RESOLVED) | read |
  | 7 | account balance (drill) **(TASK-PC-FE-074)** | `GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance` (`type`, `normalSide`, `debitTotal`, `creditTotal`, `balance`, `balanceSide`) | read |
  | 8 | account entries (drill, paginated) **(TASK-PC-FE-074)** | `GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries` (journal lines posted to one account, most-recent first: `entryId`, `postedAt`, `direction`, `money`, `counterpartyLines?`) | read |
  | 9 | reconciliation statement (detail) **(TASK-PC-FE-075)** | `GET /api/finance/ledger/reconciliation/statements/{id}` (`statementId`, `ledgerAccountCode`, `source`, `statementDate`, `matchedCount`, `discrepancyCount`, `matches[]` {`statementLineExternalRef`, `journalEntryId`, `money`}, `discrepancies[]`) | read |
  | 10 | FX position open-lots (drill) **(TASK-PC-FE-091)** | `GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots` (open FX acquisition lots for one `(account, currency)` position: `lots[]` {`lotId`, `currency`, `acquiredAt`, `seq`, `originalForeignMinor`, `remainingForeignMinor`, `originalBaseMinor`, `carryingBaseMinor`, `sourceJournalEntryId`} + summary `totalRemainingForeignMinor`, `totalCarryingBaseMinor`, `lotCount`; all `*Minor` = F5 minor-units **strings**; empty position → `200` `lots: []` / totals `"0"` / `lotCount 0`, NOT a 404) — consumes `ledger-api.md` § 12 (FIN-BE-028, the 20th increment) | read |

  **Honest ledger read-surface constraint (recorded, not papered over)**:
  the trial balance and the period list are **index-style** browsable reads
  (no input required — tenant-scoped from the JWT claim); the journal-entry
  read is **id-driven** (`GET /entries/{entryId}` — there is **no** list/search
  GET over entries at this increment, the same honest constraint as the
  finance § 2.4.7 account surface), and the discrepancy queue is a
  **status-filtered list**. The **account-level drill** (`GET
  /accounts/{ledgerAccountCode}/{balance,entries}`, rows 7–8) is **surfaced
  (TASK-PC-FE-074)** — id-driven by the account code (no account list/search
  GET; the **trial balance** is the browsable account index; a
  `CUSTOMER_WALLET:{accountId}` colon-form code is **URL-encoded**). The
  reconciliation **statement-detail** read (`GET /reconciliation/statements/{id}`,
  row 9) is **now surfaced (TASK-PC-FE-075)** — also **id-driven** (there is
  **no** statement list/search GET; statement ids originate from the ingest the
  operator's integration ran — ingest is out of console scope). The **FX position
  open-lots** read (`GET /settlements/{ledgerAccountCode}/{currency}/lots`, row
  10) is **now surfaced (TASK-PC-FE-091)** — **id-driven by the
  `(ledgerAccountCode, currency)` pair** (no position list/search GET; the
  colon-form code is **URL-encoded**, the currency is a 3-letter ISO-4217 code),
  consuming the producer read `ledger-api.md` § 12 (FIN-BE-028, the 20th
  increment, added after FE-075). An **empty position** is the producer's `200`
  empty-state (`lots: []` / totals `"0"` / `lotCount 0`) — **rendered as an
  empty-state message, never a 404 / error**; an **unsupported currency** →
  `400 VALIDATION_ERROR` → inline. **The only forward-declared ledger producer
  read that remains is whatever later FIN-BE increments add** — every read the
  producer exposes today (rows 1–10) is surfaced. Only the **non-existent**
  ledger endpoints (a statement/account/position list/search) and the
  **out-of-scope** ledger mutations beyond the FE-073 resolve remain off the
  console. Fabricating any non-existent ledger endpoint is **forbidden**.

- **Per-domain credential selection — reuse of the § 2.4.5 rule via the
  § 2.4.7 finance binding (do NOT re-derive, do NOT diverge)**: the
  `ledger-service` sits behind the **same finance gateway hostname**
  (`finance.local`) as the account-service, on a **distinct path prefix**
  (`/api/finance/ledger/**` vs `/api/finance/accounts/**`), and validates the
  **same** credential: a IAM RS256 JWT (ADR-001) against IAM's JWKS,
  `tenant_id` accepted by the finance dual-accept gate
  (`finance` / `*` / `entitled_domains ∋ finance`), `finance.read` scope,
  responses tenant-scoped (ledger `architecture.md` § Security; the same
  finance `iam-integration.md` § *platform-console Operator Read Consumer*
  basis as § 2.4.7). The credential is therefore the operator's
  **domain-facing IAM OIDC access token** (`getDomainFacingToken()` —
  the assumed tenant-scoped token when the operator has switched, else the
  base access token, ADR-MONO-020 D4 / § 2.7), sent **directly** as
  `Authorization: Bearer <token>` server-side — **never** the IAM § 2.6
  exchanged operator token (`getOperatorToken()`; the #569 invariant is
  IAM-domain-scoped and does **not** generalise to finance, exactly as
  § 2.4.7 establishes). The `features/ledger-ops` client uses
  `getDomainFacingToken()` and **never** `getOperatorToken()` (asserted by
  test). **Tenant model**: the ledger resolves the tenant from the JWT
  `tenant_id` claim producer-side — the console does **not** send
  `X-Tenant-Id` (the tenant rides inside the IAM OIDC token, the § 2.4.5 /
  § 2.4.7 divergence). **Eligibility**: the ledger is part of the **`finance`
  product** (one registry product, two services) — the section reuses the
  **same finance eligibility gate** as § 2.4.7 (`productKey === 'finance'`,
  `available`, `tenants.length > 0`); when the operator's IAM token is not
  finance-eligible the console **blocks the section** with an actionable "no
  finance-scoped access" state — no cross-tenant call is ever fabricated;
  the ledger rejects cross-tenant producer-side regardless
  (`403 TENANT_FORBIDDEN`, never weakened here). A new env pair
  `LEDGER_BASE_URL` (default `http://finance.local` — the shared finance
  gateway) + `LEDGER_TIMEOUT_MS` (default `5000`) parameterises the upstream,
  parallel to the `FINANCE_BASE_URL` / `FINANCE_TIMEOUT_MS` pair (per-service
  base+timeout convention).

- **Read binding + ONE mutation carve-out (normative)**: the six GET reads above
  are **the entire read surface**. The section was **originally strictly
  read-only** (TASK-PC-FE-072); **as of TASK-PC-FE-073 it gains exactly ONE
  operator mutation — the reconciliation discrepancy *resolve*** (see the next
  bullet), consuming the *existing* `reconciliation-api.md` § 2 endpoint, **not**
  a new producer — the same read-only→single-write-pilot evolution the erp
  § 2.4.8 department write followed. **Every OTHER ledger mutation stays out of
  scope**: **no** `POST /entries` (manual posting), **no** `/revaluations`,
  **no** `/settlements`, **no** `/reconciliation/statements` (ingest) — these are
  journal-movement / statement-ingest operations (`Idempotency-Key`-gated,
  fintech F1) that are **not** an operator-parity console surface. Carrying the
  IAM § 2.4.1 destructive mutation scaffolding (typed-confirm, GDPR double-confirm)
  into this section is a **defect** (asserted absent by test). The read calls
  remain pure `GET`; `IDEMPOTENCY_KEY_REQUIRED` / `LEDGER_PERIOD_CLOSED` / the
  `…RATE_INVALID` codes are **other-mutation-only** per `ledger-api.md` — neither
  the reads nor the resolve mutation hit them (recorded, not invented).

- **Reconciliation discrepancy *resolve* mutation (TASK-PC-FE-073 — the ledger
  surface's first and only operator mutation)**: the console consumes the
  *existing* finance
  [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md)
  **§ 2** `POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve`
  (**unchanged, consumed only** — finance owns it). An operator resolves an
  **OPEN** discrepancy (the FX-difference `AMOUNT_MISMATCH` of the 11th increment,
  or any unmatched discrepancy) — request body `{ "resolutionType": <…>, "note":
  <…> }`, `resolutionType ∈ { MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`, `200` →
  the discrepancy with `status: "RESOLVED"` + a `resolution` sub-object. This is
  the F8-sanctioned operator review close — **never** an auto-resolve (the console
  fabricates no auto-close; the discrepancy is closed only by an explicit operator
  action with a recorded `note`).
  - **Credential + tenant**: the **same** domain-facing IAM OIDC token
    (`getDomainFacingToken()`, **never** `getOperatorToken()`) and **no**
    `X-Tenant-Id` as the reads — the resolve is `.authenticated()` + the finance
    dual-accept tenant gate (`reconciliation-api.md` mutation auth: no separate
    scope-authority axis; the operator arrives via the platform-console client).
  - **Header matrix (honest, producer-faithful)**: the reason rides in the
    **body** `note` (a **required**, non-empty operator narrative — the audit
    record), **NOT** an `X-Operator-Reason` header (the resolve producer defines
    none — the same body-reason shape as the erp § 2.4.8 delegation *revoke*).
    **NO `Idempotency-Key`**: `reconciliation-api.md` § 2 does **not** define an
    `Idempotency-Key` for resolve (unlike the ledger `POST /entries`, which
    **requires** one) — the console MUST **not** fabricate a header the producer
    ignores (the same honest-difference discipline as the no-429 rule); the
    **`409 RECONCILIATION_ALREADY_RESOLVED`** state guard is the double-submit
    defence (resolve is idempotent-by-state, not by-key). **No** `X-Tenant-Id`,
    **no** typed/GDPR destructive-confirm.
  - **Confirm-gated + reason-required (normative)**: the resolve is a deliberate,
    confirm-gated action (a `resolutionType` selection + a **required** non-empty
    `note`; an empty `note` → **no** fetch, mirroring the erp delegation-revoke
    reason gate). It is offered **only on an OPEN discrepancy** (a RESOLVED row
    exposes no resolve affordance). On success the queue/detail reflects
    `RESOLVED` + `resolution`.
  - **Resilience (§ 2.5, resolve-specific)**: `200` → reflect RESOLVED;
    `409 RECONCILIATION_ALREADY_RESOLVED` → inline "already resolved — refresh"
    (a concurrent operator resolved it; refetch, do not crash);
    `422 RECONCILIATION_PERIOD_LOCKED` → inline "the discrepancy's period is
    closed — resolve in the next open period" (mirrors the producer's closed-month
    freeze); `404 RECONCILIATION_DISCREPANCY_NOT_FOUND` → inline;
    `400 VALIDATION_ERROR` → inline; `401` → whole-session re-login; `403` →
    inline "not scoped"; `503` / timeout → the ledger section degrades (the
    resolve affordance disabled), shell intact. No 429.

- **Account-level drill reads (TASK-PC-FE-074 — read-only, the forward-declared
  pair now surfaced)**: the console consumes the *existing* finance
  [`ledger-api.md`](../../../finance-platform/specs/contracts/http/ledger-api.md)
  **§ 2** `GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries`
  (paginated journal lines posted to one account, most-recent first —
  `{ entryId, postedAt, direction, money, counterpartyLines? }`) and **§ 3**
  `GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance` (the account's
  running balance — `{ ledgerAccountCode, type, normalSide, debitTotal,
  creditTotal, balance, balanceSide }`, `balance = |debitTotal − creditTotal|`).
  Both are **unchanged, consumed read-only** (finance owns them). This **closes
  the trial-balance UX loop**: a trial-balance row's `ledgerAccountCode` is the
  drill key into that account's balance + ledger lines; the section also offers a
  **direct account-code lookup** (id-driven — the ledger has **no** account
  list/search GET, the same honest constraint as journal entries; the trial
  balance is the browsable account index).
  - **Credential + tenant**: the **same** domain-facing IAM OIDC token
    (`getDomainFacingToken()`, **never** `getOperatorToken()`) and **no**
    `X-Tenant-Id` as the other reads — pure `GET`, **no** body / `Idempotency-Key`
    / `X-Operator-Reason` (this slice adds **no** mutation; the FE-073 discrepancy
    resolve stays the ledger surface's only mutation, asserted by test).
  - **Path encoding**: the `CUSTOMER_WALLET:{accountId}` colon-form
    `{ledgerAccountCode}` is **URL-encoded** on the producer path (the colon is
    encoded — `ledger-api.md` § Common shapes). The drill round-trips the exact
    code.
  - **F5 (multi-currency balance)**: the balance `debitTotal` / `creditTotal` /
    `balance` and each entry `money` are minor-units **strings** rendered
    scale-correct via `formatMoney` — **never** coerced to a float / JS `Number`
    (the same F5 grep-assertion as the other ledger reads). `type` / `normalSide`
    / `balanceSide` / `direction` are surfaced **honestly** (unknown / future
    values degrade to a generic label, never a parser throw).
  - **Resilience (§ 2.5, account-specific)**: `404 LEDGER_ACCOUNT_NOT_FOUND`
    (the account code has no ledger account — typo / never-posted) → inline "no
    such account" (the lookup stays mounted; no crash, no re-login); `401` →
    whole-session re-login; `403` → inline "not scoped"; `503` / timeout → the
    ledger section degrades, shell intact. **No 429** (the honest difference,
    asserted absent). **F7**: the account code is **confidential** — the
    sanitised `logPath` carries **no** account code (only `requestId` + the
    route shape).

- **Reconciliation statement-detail read (TASK-PC-FE-075 — read-only, the last
  forward-declared read now surfaced)**: the console consumes the *existing*
  finance
  [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md)
  **§ 3** `GET /api/finance/ledger/reconciliation/statements/{id}` (statement
  detail + its matches + discrepancies — the § 1 ingest `data` shape:
  `{ statementId, ledgerAccountCode, source, statementDate, matchedCount,
  discrepancyCount, matches: [ { statementLineExternalRef, journalEntryId,
  money } ], discrepancies: [ <discrepancy> ] }`). **Unchanged, consumed
  read-only** (finance owns it). The statement view is the **reconciliation
  source hub**: a matched line's `journalEntryId` drills into the journal-entry
  view (the existing FE-072 entry read), and a recorded discrepancy drills into
  the discrepancy detail (where the FE-073 resolve affordance lives — incl. the
  11th-increment FX-difference `AMOUNT_MISMATCH` carrying both `externalRef` +
  `journalEntryId`).
  - **Credential + tenant**: the **same** domain-facing IAM OIDC token
    (`getDomainFacingToken()`, **never** `getOperatorToken()`) and **no**
    `X-Tenant-Id` as the other reads — pure `GET`, **no** body / `Idempotency-Key`
    / `X-Operator-Reason` (this slice adds **no** mutation; the FE-073 discrepancy
    resolve stays the ledger surface's only mutation, asserted by test).
  - **Id-driven (honest constraint)**: there is **no** statement list/search GET
    — the operator looks a statement up by id (ids originate from the ingest the
    operator's integration ran; **ingest is out of console scope**). The `{id}` is
    **URL-encoded** on the producer path. Fabricating a statement list endpoint is
    **forbidden** (the same honesty as the entry / account id-driven reads).
  - **F5 + honest surfacing**: each match `money` is a minor-units **string**
    rendered scale-correct via `formatMoney` — **never** coerced to a float / JS
    `Number` (the same F5 grep-assertion). The matched/discrepancy counts and the
    `matches` / `discrepancies` arrays are surfaced **honestly** (a fully-reconciled
    statement shows `discrepancyCount: 0`; an all-unmatched statement shows
    `matchedCount: 0`); discrepancy `type` / `status` stay tolerant free strings.
  - **Resilience (§ 2.5, statement-specific)**: `404
    RECONCILIATION_STATEMENT_NOT_FOUND` (the id is unknown / not in tenant) →
    inline "no such statement" (the lookup stays mounted; no crash, no re-login);
    `401` → whole-session re-login; `403` → inline "not scoped"; `503` / timeout →
    the ledger section degrades, shell intact. **No 429** (the honest difference,
    asserted absent). **F7**: the statementId is **confidential** — the sanitised
    `logPath` carries **no** statementId (only `requestId` + the route shape).

- **fintech producer obligations surfacing (finance domain constraint,
  normative — reuses the § 2.4.7 fintech obligations, extended for the ledger
  multi-currency model — contract obligations, NOT UX niceties)**:

  - **F5 money shape — multi-currency ledger form (contract obligation, NOT a
    UX nicety)**: every money value is `{ amount: "<string-integer-minor-units>",
    currency }` (KRW=0, USD=2 scale). A journal line carries **three**
    money/rate fields — the transaction `money`, the `exchangeRate` (an
    exact-decimal **string** factor in minor units, never a float), and the
    `baseAmount` (the line's value in the fixed base currency **KRW**, which is
    **balance-authoritative**). The console **MUST** render all of them
    faithfully from the **string** minor-units (scale-correct display via
    `formatMoney`) and **MUST NOT** coerce any `amount` or `exchangeRate` to a
    float / JS `Number` / lose precision anywhere. A round-trip of a large
    minor-units amount (e.g. KRW `"1234567890123"`) and of an exact decimal
    rate (e.g. `"13.5"`) MUST be **bit-exact** as a string. Asserted by test —
    there is **no** `Number(...)` / `parseFloat(...)` / `parseInt(...)` applied
    to an `amount` or `exchangeRate` value anywhere in `features/ledger-ops/`.
    The trial-balance `inBalance` and the entry `balanced` flags (which hold by
    the posting guard) are surfaced honestly.

  - **confidential + F7 discipline**: finance is `data_sensitivity:
    confidential`. The console **MUST NOT** log ledger balances, journal-entry
    lines, account codes, reconciliation amounts, or the token — only
    `requestId` + the sanitised route shape (no `entryId` / `periodId` /
    `discrepancyId` in the log field).

  - **honest state surfacing**: journal-entry `source.sourceType`
    (`TRANSACTION | MANUAL | REVALUATION | SETTLEMENT`), accounting-period
    `status` (`OPEN | CLOSED`), and reconciliation discrepancy `type`
    (`UNMATCHED_EXTERNAL | UNMATCHED_INTERNAL | AMOUNT_MISMATCH | …`) +
    `status` (`OPEN | RESOLVED`) — surfaced **honestly** (an `OPEN`
    discrepancy, a `CLOSED` period, an `AMOUNT_MISMATCH` FX-difference are
    shown as such, never hidden). The **11th-increment** FX-difference
    discrepancy (`type: AMOUNT_MISMATCH` carrying **both** `externalRef` and
    `journalEntryId`, KRW `expected`/`actual`) is rendered with its matched
    pair (the settlement reconciled, the FX gap flagged — F8 never
    auto-adjusted). Unknown / future `sourceType`, period `status`, or
    discrepancy `type`/`status` enum values degrade to a generic label, never
    a parser throw (the same tolerant-parser discipline as §§ 2.4.6/2.4.7).

- **Resilience (§ 2.5) — finance flat error envelope (reuse of § 2.4.7; the
  ledger is the SAME finance producer family, flat shape, finance error-code
  vocabulary)**: the ledger error envelope is **flat** `{ code, message,
  details?, timestamp }`, success `{ data, meta: { timestamp } }` (per
  `ledger-api.md` / `reconciliation-api.md` envelopes). Mapping:
  `401 UNAUTHORIZED` → forced **whole-session IAM re-login** (no partial
  authed state, consistent with §§ 2.4.5–2.4.8); `403 TENANT_FORBIDDEN`
  (token not finance-scoped) → inline "not available / not scoped";
  `404 JOURNAL_ENTRY_NOT_FOUND` / `404 ACCOUNTING_PERIOD_NOT_FOUND` /
  `404 RECONCILIATION_DISCREPANCY_NOT_FOUND` / `404 LEDGER_ACCOUNT_NOT_FOUND`
  (**TASK-PC-FE-074**, the account drill) / `404 RECONCILIATION_STATEMENT_NOT_FOUND`
  (**TASK-PC-FE-075**, the statement detail) → inline actionable "no such
  entry / period / discrepancy / account / statement" (no crash); `400` / `422` → inline actionable;
  `503 SERVICE_UNAVAILABLE` / timeout / network → **only the ledger section
  degrades** (the console shell + the IAM / wms / scm / finance-account / erp
  sections stay intact). The ledger contracts document **no `429` /
  rate-limit** response — the console MUST NOT fabricate a backoff clause (no
  `Retry-After` branch; the honest difference from § 2.4.6, asserted absent by
  test — the same posture as § 2.4.7).

- **Producer immutability**: this is a **cross-reference only**. Any change to
  the finance `ledger-service` read producer contracts is a finance
  project-internal spec-first change in `ledger-api.md` /
  `reconciliation-api.md`; this section follows it, never redefines it (§ 5
  Change Rule). The finance-side acknowledgment of this console consumer is the
  merged finance `iam-integration.md` § *platform-console Operator Read
  Consumer* (TASK-FIN-BE-005) — the same spec-first basis that sanctions the
  § 2.4.7 account binding (the ledger shares the finance tenant gate).

> **Not a § 3 parity row**: like §§ 2.4.5–2.4.8, § 2.4.7.1 has **no** § 3 line.
> § 3 is the IAM `admin-web` absorption parity gate (FE-006-finalized); the
> ledger section is a federated **domain** section (a second finance-product
> service). This binding adds **no** row to § 3 and changes **none** (the
> § 3.1 per-row attestation-marker count stays exactly 16 — the FE-006
> no-drift guard remains green). It is the proof that a single federated
> product can bind **multiple producer services** under one credential +
> eligibility gate (the finance analog of the wms § 2.4.5 + § 2.4.5.1 pair).

#### 2.4.8 erp operations surface (TASK-PC-FE-010 — cross-reference, not a redefinition)

The **fourth non-IAM** per-domain binding of § 2.4 (ADR-MONO-013
Phase 6 — the **first internal-system-primary** non-IAM federation,
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
`PROJECT.md` § v1 OUT); this section was originally **strictly
read-only** across all 5 masters, closest to the FE-008 scm and
FE-009 finance precedents. **As of TASK-PC-FE-046 the department
master gained a WRITE pilot, and TASK-PC-FE-048 extended write to ALL
FIVE masters** (each: create / update / retire; department additionally
move-parent) — consuming the *existing* `masterdata-service` mutation
endpoints, **not** a new `admin-service` (those stay v2-deferred) —
sanctioned by ADR-MONO-016 § D3.1 (amended). The normative mutation
matrix is in *Masterdata write binding* below. The producer
contract is **authoritative and unchanged** — this section only states
the consumer obligation and points at the owning erp spec. This
binding is the **fourth** instance that verifies ADR-MONO-013 § 3.3's
"zero retrofit" assumption across a non-IAM domain, and the
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
  **not** an operator-parity console surface at v1 (the **masterdata
  write binding** below extends this for all five masters under
  ADR-MONO-016 § D3.1). The forward erp services declared v2 in
  ADR-MONO-016 § D3 have since shipped **first increments** and are now
  consumed by the console: `read-model-service` (the *integrated
  read-model binding* below — TASK-PC-FE-049), `approval-service` (the
  결재함 workflow surface — TASK-PC-FE-051 single-stage, extended by
  TASK-PC-FE-053 to the v2.0/v2.1 backend: **multi-stage routes (`approverIds`)
  + the `IN_REVIEW` status + a stage-progress timeline + delegated-approval
  display (`actingForApproverId`)**, and by TASK-PC-FE-054 with **delegation
  grant management** (create / revoke / list `/api/erp/approval/delegations` —
  대결/위임 grants), consuming `/api/erp/approval/**` under the
  § D3.1 write-affordance model), and `notification-service`
  (the *notification inbox binding* below — TASK-PC-FE-052). The future
  `admin-service` / `permission-service` remain out of scope (still
  v2-deferred per ADR-MONO-016 § D3 / erp `PROJECT.md` § v1 OUT).

- **Per-domain credential selection — reuse of the § 2.4.5 rule (do
  NOT re-derive, do NOT diverge)**: the normative per-domain
  credential rule is **defined in § 2.4.5** (each § 2.4.x binding
  declares its own credential against its producer's auth contract;
  an implementer MUST NOT blanket-apply one domain's auth model to
  another). **erp reuses that rule with the same outcome as wms / scm /
  finance**: the erp `masterdata-service` validates a IAM RS256 JWT
  (ADR-001) against IAM's JWKS, `tenant_id ∈ { erp, * }` enforced
  producer-side from the JWT claim (erp
  [`iam-integration.md`](../../../erp-platform/specs/integration/iam-integration.md)
  § *platform-console Operator Read Consumer* — the merged
  TASK-ERP-BE-002 reconciliation that sanctions the console as an
  external operator IAM-token read consumer of the existing erp read
  surface; the erp "internal-only 경계" #6 / E7 narrative is
  **clarified, not weakened** — boundary scopes non-IAM-SSO traffic;
  IAM-authenticated console traffic routed through internal Traefik
  is within the SSO boundary). The credential is therefore the
  operator's **IAM `platform-console-web` OIDC access token** itself
  (`getAccessToken()`, the IAM-session HttpOnly cookie from FE-001),
  sent **directly** as `Authorization: Bearer <IAM OIDC access token>`
  server-side — **never** the IAM § 2.6 exchanged operator token
  (`getOperatorToken()`; that is IAM-domain-scoped — the #569
  trust-boundary invariant does **not** generalise to erp, exactly
  as § 2.4.5 states for wms, § 2.4.6 confirms for scm, and § 2.4.7
  confirms again for finance). The console's `features/erp-ops`
  client uses `getAccessToken()` and **never** `getOperatorToken()`
  (asserted by test — the same shape as the FE-007/FE-008/FE-009
  assertions; the cross-domain regression is extended so
  IAM = operator-token / wms = IAM-OIDC / scm = IAM-OIDC /
  finance = IAM-OIDC / **erp = IAM-OIDC** all hold in one place — 5
  domains). **Tenant model**: erp resolves the tenant from the JWT
  `tenant_id` claim producer-side — the console does **not** send
  `X-Tenant-Id` (the tenant rides inside the IAM OIDC token, exactly
  the § 2.4.5 / § 2.4.6 / § 2.4.7 divergence). When the operator's
  IAM token is not erp-eligible (no `erp` tenant and not a
  platform-scope `*` operator) the console **blocks the section**
  with an actionable "no erp-scoped access" state — no cross-tenant
  call is ever fabricated; erp rejects cross-tenant producer-side
  regardless (`403 TENANT_FORBIDDEN`, never weakened here).

- **Read surface (normative)**: every master's `list` + `detail` is a
  pure `GET` — **no** `Idempotency-Key`, **no** `X-Operator-Reason`,
  **no** body on the read path (asserted by test). The producer-side
  `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_KEY_CONFLICT` (409) /
  `MASTERDATA_DUPLICATE_KEY` / `MASTERDATA_REFERENCE_VIOLATION` /
  `MASTERDATA_PARENT_CYCLE` (409) + `MASTERDATA_EFFECTIVE_PERIOD_INVALID`
  (422) are **mutation-only** — the read path never hits them. Beyond
  this masterdata surface, the console now also binds the shipped erp
  first increments — `read-model-service` (read), `approval-service`
  (결재함), `notification-service` (inbox bell, below) — while the future
  `admin-service` / `permission-service` surfaces stay out of scope.

- **Integrated read-model binding (TASK-PC-FE-049 — read-only; ADR-MONO-016
  § D3 read-model-service first increment)**: the console renders the erp
  **employee org-view** (employee + resolved department-hierarchy path +
  cost center + job grade) by consuming the **read-model-service** read API
  — `GET /api/erp/read-model/employees` [+ `/{id}`], request/response/error
  owned by [`read-model-api.md`](../../../erp-platform/specs/contracts/http/read-model-api.md)
  (authoritative). **Strictly read-only** (erp E5 — the read-model holds no
  domain logic and re-emits nothing); there is **no** mutation surface on
  this binding. Credential is **unchanged** from the read binding (same
  server-side IAM OIDC domain-facing token; never `getOperatorToken()`).
  The read-model is **eventually consistent** with `masterdata-service` (the
  authoritative source of record) — every response carries `meta.warning:
  "Eventually-consistent read-model"`; an org-view referencing a
  not-yet-projected master returns that reference `null` + `meta.unresolved`,
  surfaced as a "동기화 중" badge, **never fabricated**. `?asOf` (E3) threads
  through verbatim. **Routing**: in v1 (gateway-service deferred) `erp.local`
  routes `/api/erp/read-model/**` to read-model-service via a path-prefix
  Traefik router (priority over the masterdata catch-all); the single-base
  `ERP_BASE_URL` console model is unchanged.
  - **Resilience (§ 2.5 — best-effort leg, parity with the notification
    bell; TASK-PC-FE-069)**: the org-view leg is **independent and
    best-effort**. A `503` / timeout / network failure
    (`ErpUnavailableError`), or any other non-auth error, on the read-model
    leg degrades **only the org-view card** (rendered empty with a "동기화
    중 / 일시적으로 불러올 수 없음" affordance) and **MUST NOT** degrade the
    authoritative `masterdata-service` master reads (the read-model is an
    eventually-consistent *secondary projection* of those authoritative
    masters — its availability MUST NOT gate the authoritative data) or any
    other section. This mirrors the *notification inbox binding*'s
    shell-level best-effort degrade and the `approval` / delegation-fact
    legs' `catch → null` isolation; **only a `masterdata` read failure
    degrades the erp section as a whole**. A `401` on any leg still forces a
    whole-session re-login (the IAM token is shared across all legs — auth is
    never a per-leg degrade; in practice a read-model `401` co-occurs with a
    masterdata `401`, which raises it).

- **Notification inbox binding (TASK-PC-FE-052 — read + idempotent
  acknowledge; ADR-MONO-016 § D3 notification-service first increment)**:
  the console renders a **shell-level notification bell** that consumes the
  erp **notification-service** in-app inbox — `GET /api/erp/notifications`
  (the caller's recipient-scoped inbox, optional `unread` filter),
  `GET /api/erp/notifications/{id}`, and the idempotent
  `POST /api/erp/notifications/{id}/read`; request/response/error owned by
  [`notification-api.md`](../../../erp-platform/specs/contracts/http/notification-api.md)
  (authoritative). This closes the user-visible leg of the
  `approval(event) → notification(fan-out) → console(bell)` loop —
  notification-service consumes the four `erp.approval.*` transitions
  (`notification-subscriptions.md`) and the bell surfaces them.
  - **Strictly read + acknowledge** — the ONLY mutation is the **naturally
    idempotent mark-read** (a state-converging `read = true` assignment, not
    an accumulating create). Per `notification-api.md` § POST `…/read` it
    carries **no `Idempotency-Key`** (unlike the approval transitions /
    masterdata writes — re-marking is a no-op returning the **same**
    `readAt`, never advanced). The console MUST NOT fabricate an
    `Idempotency-Key` header on this surface (asserted by test).
  - **Credential — UNCHANGED from the erp read binding**: the same
    server-side domain-facing IAM OIDC token (`getDomainFacingToken()`);
    **never** `getOperatorToken()`. erp resolves the tenant from the JWT
    `tenant_id ∈ {erp,*}` claim — the console sends **no** `X-Tenant-Id`.
  - **Recipient-scoped (E6, fail-CLOSED — producer-enforced)**: the inbox
    is **personal** — every row is implicitly filtered to `recipient ==
    caller.sub`; there is **no** all-recipients view in v1. A detail /
    mark-read on another recipient's notification returns **404
    `NOTIFICATION_NOT_FOUND`** (existence-leak avoidance — 404 not 403),
    passed through inline-actionably (unreachable on the bell's own-inbox
    happy path). The console never queries another employee's inbox.
  - **NON_NULL absent `readAt`**: `readAt` is **ABSENT** while
    `read == false` (never serialized `null`) → parsed `optional`; the bell
    distinguishes unread by `read === false` / the absent `readAt`, never a
    null value (same tolerant-parser discipline as the read-model /
    masterdata surfaces). Unknown / future `sourceType` (`MASTERDATA` /
    `PERMISSION` — v2) and `type` values degrade to a generic label, never
    a parser throw; deep-link to the source record is offered only for
    `sourceType: APPROVAL` (`sourceId` → the approval request).
  - **Shell-level degrade (integration-heavy resilience — § 2.5)**: the
    bell is a **global** header affordance, but its v1 source is the
    erp-scoped inbox. When the operator's IAM token is not erp-eligible
    (`403 TENANT_FORBIDDEN` / `PERMISSION_DENIED`) or notification-service
    is unavailable (`503` / timeout / network → `ErpUnavailableError`), the
    bell **degrades silently to empty / inert** and **MUST NOT** break the
    console shell or any other section (asserted by test — a non-erp
    operator uses the console normally with an empty bell). No
    `refetchInterval` polling (the same no-tight-loop rule as the erp-ops
    read hooks); the inbox refetches on bell-open + after a mark-read.
  - **Routing**: in v1 (gateway-service deferred) `erp.local` routes
    `/api/erp/notifications/**` to notification-service via a path-prefix
    Traefik router (alongside the read-model `/api/erp/read-model/**` and
    approval `/api/erp/approval/**` routers, priority over the masterdata
    catch-all); the single-base `ERP_BASE_URL` console model is unchanged.

- **Masterdata write binding (TASK-PC-FE-046 department pilot →
  TASK-PC-FE-048 all 5 masters; ADR-MONO-016 § D3.1 amended)**: each of
  the five masters exposes its `masterdata-service` mutations as a
  console write surface — **create / update / retire** for every master,
  plus **move-parent** for department (hierarchy-specific). It consumes
  the **unchanged** producer `masterdata-api.md` § <master>
  (request/response/error tables remain canonical there). The wire
  contract is **uniform** across masters: a same-origin `POST` to the
  collection (create) / `POST .../{id}` → upstream `PATCH` (update) /
  `POST .../{id}/retire` (retire); every mutation carries an
  `Idempotency-Key`; `reason` rides in the **body** ONLY on retire (the
  producer's reason slot for the four non-department masters) and on
  department retire/move-parent — `create`/`update` have no producer
  reason slot, so the console **MUST NOT** fabricate an
  `X-Operator-Reason` header (erp does not read it). FK fields
  (employee → department/job-grade/cost-center; cost-center →
  department) are entity-selector dropdowns, not raw-UUID inputs
  (TASK-PC-FE-047). The normative department mutation matrix (the
  template every master follows):

  | Operation | Same-origin proxy (console) | Upstream (`masterdata-service`) | `Idempotency-Key` | `reason` (body) | effective-dating |
  |---|---|---|---|---|---|
  | create | `POST /api/erp/masterdata/departments` | `POST .../departments` | **required** | — (no producer slot) | `effectiveFrom?` |
  | update | `POST /api/erp/masterdata/departments/{id}` | `PATCH .../{id}` | **required** | — (no producer slot) | `effectiveFrom?` |
  | retire | `POST /api/erp/masterdata/departments/{id}/retire` | `POST .../{id}/retire` | **required** | **required** (≤256) | — |
  | move-parent | `POST /api/erp/masterdata/departments/{id}/move-parent` | `POST .../{id}/move-parent` | **required** | `≤256` | `effectiveFrom` |

  - **Credential — UNCHANGED from the read binding**: the same
    domain-facing IAM OIDC token (`getDomainFacingToken()` /
    `getAccessToken()`) is attached server-side; **never**
    `getOperatorToken()`. erp resolves the tenant from the JWT
    `tenant_id ∈ {erp,*}` claim — the console sends **no**
    `X-Tenant-Id`. The write pilot introduces **no** new credential
    model — it reuses § 2.4.5 exactly as the read does.
  - **`Idempotency-Key` — required on all four** (E1 / transactional
    T1), generated console-side per attempt (the operators
    `crypto.randomUUID()` pattern), forwarded by the same-origin POST
    route to the upstream verbatim. A `400 IDEMPOTENCY_KEY_REQUIRED`
    would be a console defect (the gate always supplies one).
  - **`reason` — body field where (and ONLY where) the producer has a
    slot**: `retire` requires it (≤256), `move-parent` accepts it
    (≤256); `create` / `update` have **no** producer reason slot, so
    the console **MUST NOT** fabricate an `X-Operator-Reason` header
    (the erp producer does not read it — that is a IAM/admin-service
    concept, NOT erp). The append-only E8 `audit_log` is the
    producer's authority for who/what on every mutation regardless.
  - **Confirm-gate UX (audit-heavy)**: every department mutation sits
    behind an explicit confirm dialog; the **destructive** operations
    (retire / move-parent) additionally require a typed `reason`
    before confirm enables (mapped to the body slot). create / update
    confirm without a reason field (no producer slot — capturing a
    phantom reason would be dishonest).
  - **Mutation-only error codes are now REACHABLE for departments**
    (surfaced inline-actionably, never a crash): `409`
    `MASTERDATA_DUPLICATE_KEY` (create) / `MASTERDATA_REFERENCE_VIOLATION`
    (retire — live referents) / `MASTERDATA_PARENT_CYCLE` (move-parent)
    / `IDEMPOTENCY_KEY_CONFLICT` / `CONCURRENT_MODIFICATION`;
    `422 MASTERDATA_EFFECTIVE_PERIOD_INVALID`; `400 IDEMPOTENCY_KEY_REQUIRED`.
    `403 PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN` (the producer's E6
    fail-CLOSED role/scope authz) is rendered honestly inline — the
    console never pre-judges write authority; the producer is the
    authority.

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
  `401 UNAUTHORIZED` → forced **whole-session IAM re-login** (the
  IAM OIDC session expired — not a per-section degrade, no
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
  section degrades** (the console shell + the IAM / wms / scm /
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
  console consumer is the merged erp `iam-integration.md` §
  *platform-console Operator Read Consumer* (TASK-ERP-BE-002) —
  the spec-first basis for this binding.

- **§ 3 parity matrix is NOT mutated by this binding**: § 3 is
  the **IAM `admin-web` parity matrix**, finalized by
  TASK-PC-FE-006 (16/16 rows; see § 3). erp is **additive domain
  scope** federated by the console — **not** a IAM-`admin-web`
  parity-gate row. This binding adds **no** row to § 3 and changes
  **none**; the Phase 3 `admin-web`-retirement gate is unaffected.
  (This § 2.4.8 prose deliberately does **not** use the § 3.1
  per-row attestation marker phrase, so the FE-006 no-drift
  guard's count of that marker stays exactly 16 — the FE-006
  guard remains green after this binding.)

> **Not a § 3 parity row**: like § 2.4.5 / § 2.4.6 / § 2.4.7 and
> unlike §§ 2.4.1–2.4.4, § 2.4.8 has **no** § 3 line. § 3 is the
> IAM `admin-web` absorption parity gate (FE-006-finalized); the
> erp section is a federated **domain** section — the **fourth**
> instance that verifies ADR-MONO-013 § 3.3's "zero retrofit"
> assumption across a non-IAM domain, and the **first
> internal-system-primary** confirmation (wms transactional →
> scm integration-heavy → finance regulated/transactional →
> **erp internal-system + transactional + audit-heavy**) — four
> trait shapes, zero retrofit, zero re-derivation. ADR-MONO-013
> Phase 6 = COMPLETE; Phase 7 (`console-bff` + cross-domain
> dashboards) gate is **ungated to 5/5 domains live**
> (IAM + wms + scm + finance + erp).

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
> (and the § 2.6 RFC 8693 exchanged operator token for the IAM-domain leg) is
> a HARD INVARIANT. `console-bff` is the rule's *credential dispatcher*, never
> its rewriter.** Re-introducing the rejected ADR-MONO-017 D4.B (single
> unified BFF token) or D4.C (operator-token-only across all domains) on any
> future § 2.4.9.X composition route is a contract defect.

- **Inbound auth (console-web → console-bff)**: server-side only — the
  browser never reaches the BFF. console-web's App-Router server route holds
  the two tokens already established at login (per
  [`console-web/architecture.md`](../services/console-web/architecture.md) +
  FE-002a) and forwards them to `console-bff` on every call:
  - `Authorization: Bearer <iam-oidc-access-token>` — the inbound principal,
    RS256 / JWKS = IAM (standard OAuth2 Resource Server validation: issuer
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
  | IAM (`/api/admin/**`) | RFC 8693 exchanged operator token (§ 2.6) | inbound `X-Operator-Token` |
  | wms (`/api/wms/**`) | IAM OIDC access token (§ 2.4.5) | inbound `Authorization` |
  | scm (`/api/scm/**`) | IAM OIDC access token (§ 2.4.6) | inbound `Authorization` |
  | finance (`/api/finance/**`) | IAM OIDC access token (§ 2.4.7) | inbound `Authorization` |
  | erp (`/api/erp/**`) | IAM OIDC access token (§ 2.4.8) | inbound `Authorization` |

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

##### Observability (D7.A — Vector + VictoriaMetrics reuse, [ADR-MONO-007](../../../../docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md))

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
> § 3 line. § 3 is the IAM `admin-web` absorption parity gate (FE-006
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

> **Domain-set symmetry with § 2.4.9.2 (TASK-MONO-241 → TASK-MONO-243, 2026-06-13).**
> This "Operator Overview" route is now **6 legs** `[iam, wms, scm, finance,
> erp, ecommerce]` — symmetric with the § 2.4.9.2 Domain-Health Overview
> 6-card surface. TASK-MONO-241 first added `ecommerce` to the **health**
> surface only (it exposes a public `/actuator/health`, no producer retrofit),
> leaving the overview at 5 because `ecommerce` `AdminProductController` was
> write-only. TASK-MONO-243 landed the ecommerce **overview snapshot leg** (a
> domain metric = tenant product count) by adding a net-new ecommerce
> operator-plane read endpoint (`GET /api/admin/products?page=0&size=1`),
> restoring symmetry. **health = 6, overview = 6** — still two independent
> surfaces (the shared `CompositionEngine` does not couple their card order;
> each use-case owns its own `CARD_ORDER`), now with the same domain set.
>
> **UI routing note (TASK-PC-FE-034, 2026-06-02; additive — composition body byte-unchanged).** This composition route is the console **landing/home** (the authenticated root `/` lands on `/dashboards/overview`; the single "개요" top-nav entry points here). The **IAM card** in the rendered envelope is an accessible **drill-down link to the IAM-only composed overview** (`/dashboards`, § 2.4.4 / ADR-MONO-015 D1-B) — the accounts/audit/operators 3-leg detail. This note governs **only** the consumer-side (`console-web`) landing + nav + IAM-card link wiring; the request headers, response envelope, per-card status discipline, auth flow, resilience, observability labels, and the read-only/no-mutation hard invariant of this route are **unchanged**. See ADR-MONO-017 § D8 amendment + ADR-MONO-015 § 6 amendment.

##### Surface

| # | Method / Path | Purpose | Auth | Producer |
|---|---|---|---|---|
| 1 | `GET /api/console/dashboards/operator-overview` | Single composed cross-domain dashboard envelope; one card per backend domain (IAM + wms + scm + finance + erp + ecommerce); each card carries the per-leg outcome (`ok` / `degraded` / `forbidden`) per § 2.4.9 D5.A discipline | `Authorization: Bearer <iam-oidc-access-token>` (inbound principal, RS256 / IAM issuer) + `X-Operator-Token: <rfc8693-operator-token>` (request-scoped, for IAM leg) + `X-Tenant-Id: <active-tenant>` (forwarded verbatim) — all three set server-side by `console-web` 's SSR route, never by the browser. Absent any of the three → fail-closed (`400 NO_ACTIVE_TENANT` if `X-Tenant-Id` absent; otherwise `401 TOKEN_INVALID`) before any outbound leg | `console-bff` |

> The route is **GET only — read-only**. ADR-MONO-017 § 2.4.9 hard invariant
> "no mutation at MVP" applies; therefore `Idempotency-Key`,
> `X-Operator-Reason`, destructive-confirm scaffolding, and any
> POST/PUT/PATCH/DELETE method MUST NOT appear on this route or any future
> `§ 2.4.9.X` MVP dashboard route. Adding a mutation surface requires a
> fresh ADR amendment to ADR-MONO-017.

##### Composed producers (6 domains, reuse-only — D3.A / § 3.3 zero retrofit)

The composition route fans out across **existing** per-domain read
endpoints — one card per domain, **no producer retrofit** (the ecommerce
6th leg, TASK-MONO-243, adds a net-new producer endpoint but retrofits no
existing one). The producer contracts are authoritative in their respective
files and are **not redefined here**:

| # | Card | Composed producer endpoint | Domain credential (§ 2.4.9 D4) | Producer spec § (authoritative) | Read content surfaced |
|---|---|---|---|---|---|
| 1 | accounts summary | `GET /api/admin/accounts?page=0&size=1` (page total snapshot) | RFC 8693 exchanged **operator** token (§ 2.6) — `getOperatorToken()` | IAM [`admin-api.md`](../../../iam-platform/specs/contracts/http/admin-api.md) § Accounts (already bound by § 2.4.1 / FE-002 + the composed-overview pattern of § 2.4.4 / FE-005) | total account count (snapshot) |
| 2 | wms inventory health | `GET /api/v1/admin/dashboard/inventory` (snapshot) | **IAM OIDC access token** — `getAccessToken()` (per § 2.4.5 verbatim) | wms [`admin-service-api.md`](../../../wms-platform/specs/contracts/http/admin-service-api.md) § 1.1 Dashboard / Read-Model (already bound by § 2.4.5 / FE-007) | inventory snapshot health summary (stock total, alert count) |
| 3 | scm procurement / inventory | `GET /api/inventory-visibility/snapshot` (snapshot) — inventory-visibility-service **direct** producer read (see scm-leg topology note below; § 2.4.6 / FE-008) | **IAM OIDC access token** — `getAccessToken()` (per § 2.4.6 verbatim) | scm [`gateway-public-routes.md`](../../../scm-platform/specs/contracts/http/gateway-public-routes.md) § *platform-console operator read consumer* (already bound by § 2.4.6 / FE-008) | inventory visibility snapshot (the producer-meta-warning S5 "Not for procurement decisions" MUST surface as a non-blocking hint, per § 2.4.6 invariant) |
| 4 | finance balance health | `GET /api/finance/accounts/{operatorDefaultAccountId}/balances` (single account) | **IAM OIDC access token** — `getAccessToken()` (per § 2.4.7 verbatim) | finance [`account-api.md`](../../../finance-platform/specs/contracts/http/account-api.md) § Balances (already bound by § 2.4.7 / FE-009) | balance snapshot for the operator's default account; **honest constraint** (per § 2.4.7) — finance v1 has no list/search GET → an `operatorDefaultAccountId` resolution mechanism is required (registry-side or operator-profile-side; spec-first decided **at MVP impl** — see § Implementation guidance); if absent → that card renders `forbidden` (not a crash) |
| 5 | erp masterdata snapshot | `GET /api/erp/masterdata/departments?active=true&page=0&size=1` (page total snapshot, asOf=now implicit) | **IAM OIDC access token** — `getAccessToken()` (per § 2.4.8 verbatim) | erp [`masterdata-api.md`](../../../erp-platform/specs/contracts/http/masterdata-api.md) § Departments (already bound by § 2.4.8 / FE-010) | active department count (snapshot, asOf=now — E3 effective-dating implicit) |
| 6 | ecommerce product snapshot | `GET http://ecommerce.local/api/admin/products?page=0&size=1` (page total snapshot) — routed **through** the ecommerce gateway (see ecommerce-leg topology note below) | **IAM OIDC access token** — `getAccessToken()` (6-row sealed selector — `ECOMMERCE → IamOidcAccessToken`) | ecommerce [`product-api.md`](../../../ecommerce-microservices-platform/specs/contracts/http/product-api.md) § `GET /api/admin/products` (operator-plane read, TASK-MONO-243) | tenant product count (snapshot — `totalElements`) |

**Producer immutability**: the first 5 producer contracts above are
**byte-unchanged spec-side and impl-side** (ADR-MONO-017 § 3.3 sixth
confirmation). The 6th (ecommerce) leg, TASK-MONO-243, adds a **net-new**
operator-plane read endpoint (`GET /api/admin/products`) to the ecommerce
product-service — it does **not** retrofit an existing producer, and no
`/summary` / `/dashboard-card` aggregating endpoint is added to any producer
(D3.B rejection — the new read mirrors the public `GET /api/products` query
path exactly, on the operator plane). The console-bff composition use-case
calls the existing GETs verbatim.

> **scm-leg topology (TASK-MONO-162 — ADR-MONO-020 D4 reconciliation).** Card 3
> calls the inventory-visibility **producer service directly**
> (`GET /api/inventory-visibility/snapshot`), consistent with every other
> composition leg — wms-admin (`/api/v1/admin/dashboard/inventory`),
> finance-account, and erp-masterdata are all **direct** producer reads; the
> console-bff composition never routes through a domain gateway. The scm
> `gateway-service` `/api/v1/inventory-visibility/**` public route (the § 2.4.6
> producer-endpoint table above, authoritative in `gateway-public-routes.md`)
> remains the **external** public-read surface for non-console consumers — it is
> **not** on the console-bff composition path. The earlier card path
> `/api/scm/inventory/visibility` matched neither the gateway route nor the
> service and was a defect (corrected here). **Rationale**: the scm gateway
> carries its own `required-tenant-id=scm` `TenantClaimValidator` that is **not**
> entitlement-trust-aware (ADR-MONO-019 § D5), so an assume-tenant token
> (`tenant_id=<customer>`, `entitled_domains ∋ scm`) would be rejected **at the
> gateway**; routing the console leg direct-to-producer lets the producer's own
> decode-time validator + `TenantClaimEnforcer` filter (both entitlement-trust
> dual-accepting after MONO-162) enforce tenancy. Reinstating the gateway on the
> console path is a documented follow-up gated on retrofitting the gateway
> validator with the same dual-accept.

> **ecommerce-leg topology (TASK-MONO-243 — ADR-MONO-030 Step 4 facet a-후속-2).**
> Unlike the other 5 overview legs (all **direct-to-producer** reads — see the
> scm-leg topology note above), card 6 routes **through the ecommerce gateway**
> (`ecommerce.local`, the same `ecommerceRestClient` / base-url used by the
> § 2.4.9.2 health leg). Rationale: ecommerce `product-service` is a
> **header-trust** service, **not** a JWT resource server — it reads a trusted
> `X-Tenant-Id` injected upstream and does not itself validate bearer tokens.
> The gateway is therefore the authorization boundary: it validates the IAM
> OIDC access token, enforces `roles ∋ ADMIN` for `/api/admin/**`
> (`AccountTypeEnforcementFilter`), requires a non-blank `tenant_id`
> (`TenantClaimValidator`), injects the trusted `X-Tenant-Id`
> (`JwtHeaderEnrichmentFilter`), and strips inbound client headers. Routing the
> console leg direct-to-`product-service` would force console-bff to fabricate
> `X-Tenant-Id` / `X-User-*` and bypass the gateway's `roles ∋ ADMIN` + JWT
> validation — a security smell. The `product-service` `GET /api/admin/products`
> read is gated at the gateway on `roles ∋ ADMIN`, **uniformly** with the
> `/api/admin/products/**` write endpoints: the operator's IAM OIDC token carries
> the `ADMIN` domain role **derived at assume-tenant** from the selected
> (ecommerce-entitled) tenant (ADR-MONO-035 4a) — no ecommerce-local `ADMIN`
> grant is provisioned, the role rides the token — and the header-trust
> `product-service` applies no additional ecommerce-local RBAC (the gateway is
> the single admission point; authoritative detail in ecommerce `product-api.md`
> § `GET /api/admin/products`). Tenant isolation is the repo `WHERE tenant_id`
> chokepoint (Step 2 / M6). This mirrors the erp/finance overview legs — the
> console operator presents a single federation-issued token and the domain
> service provisions no domain-local admin grant. (ADR-MONO-035 4b removed the
> legacy `account_type=OPERATOR` gateway leg; admission is `roles`-only.)

##### Response schema (`200 OK`)

```json
{
  "asOf": "2026-05-20T10:30:00Z",
  "cards": [
    { "domain": "iam",       "status": "ok",         "data": { "accountCount": 12345 } },
    { "domain": "wms",       "status": "ok",         "data": { "inventorySnapshot": { … } } },
    { "domain": "scm",       "status": "degraded",   "reason": "DOWNSTREAM_ERROR" },
    { "domain": "finance",   "status": "forbidden",  "reason": "TENANT_FORBIDDEN" },
    { "domain": "erp",       "status": "ok",         "data": { "activeDepartmentCount": 87 } },
    { "domain": "ecommerce", "status": "ok",         "data": { "totalElements": 42 } }
  ]
}
```

- `asOf`: composition request server-side timestamp (ISO-8601 UTC). Operators see "data as-of HH:MM:SS" in the UI.
- `cards[]`: **exactly 6 entries** in **fixed order** `[iam, wms, scm, finance, erp, ecommerce]` (UI rendering ordering invariant; never reordered by status).
- `cards[i].status` ∈ `{ "ok", "degraded", "forbidden" }`:
  - `ok` → `data` is the card's composed payload (domain-specific shape, declared per row in the producer endpoint above).
  - `degraded` → `reason` ∈ `{ "DOWNSTREAM_ERROR", "TIMEOUT", "CIRCUIT_OPEN" }`; `data` absent. Card renders "data unavailable, retry pending" placeholder.
  - `forbidden` → `reason` ∈ `{ "PERMISSION_DENIED", "TENANT_FORBIDDEN", "MISSING_PREREQUISITE" }` (last covers e.g. finance's `operatorDefaultAccountId` absent); `data` absent. Card renders "not available to your role / tenant" placeholder.
- **All-down envelope**: every leg can return non-`ok` simultaneously — the route still emits `200` with all 6 cards in `degraded`/`forbidden` states. The route NEVER emits `503` / blanks the response (D5.A discipline; D5.B rejection re-affirmed).

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

- **Inbound** (console-web SSR → console-bff): `Authorization` (IAM OIDC access token, inbound principal) + `X-Operator-Token` (RFC 8693 exchanged operator token, request-scoped via `OperatorCredentialContext`) + `X-Tenant-Id` (operator's selected active tenant). The browser **never** reaches console-bff directly.
- **Outbound** (console-bff → each domain): per-domain credential dispatch (§ 2.4.9 D4 table, **6-row sealed selector** — `IAM → OperatorToken`, `{wms,scm,finance,erp,ecommerce} → IamOidcAccessToken`). NO fallback path. NO unified token. NO operator-token-only across all domains. `X-Tenant-Id` forwarded verbatim on every leg; producer's `TenantClaimValidator` is the authoritative gate. **Note (TASK-MONO-241 → TASK-MONO-243)**: this **Operator Overview** route now fires all **6** legs `{iam,wms,scm,finance,erp,ecommerce}`. The `ECOMMERCE → IamOidcAccessToken` selector row was first added (MONO-241) so the `DomainTarget` sealed switch stayed exhaustive for the § 2.4.9.2 health leg; TASK-MONO-243 now **exercises** it from the overview's ecommerce snapshot leg (the ecommerce leg routes through the ecommerce gateway — see the ecommerce-leg topology note above).

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
| `bff_fanout_latency_seconds{domain,route}` | `domain` ∈ `{iam,wms,scm,finance,erp}` × `route` = `"operator-overview"` |
| `bff_fanout_errors_total{domain,route,code}` | same `domain`/`route` + `code` ∈ `{5xx,timeout,circuit_open,tenant_forbidden,permission_denied,missing_prerequisite}` |
| `bff_aggregation_degrade_count_total{dashboard,degraded_domain}` | `dashboard = "operator-overview"` + `degraded_domain` ∈ `{iam,wms,scm,finance,erp}` (one increment per degraded/forbidden card per response) |

OTel `traceparent` propagates inbound → every outbound leg; per-leg span
carries `bff.domain` + `bff.route="operator-overview"` attributes.

##### Implementation guidance (impl PR scope notes — not contract)

- **`operatorDefaultAccountId` resolution** (finance card prerequisite) is an
  impl-PR decision: either (a) the IAM registry surface (§ 2.2) returns a
  per-operator `finance.defaultAccountId` claim/attribute, or (b) the
  console-bff composition use-case skips the finance leg and renders
  `forbidden / MISSING_PREREQUISITE` when no default is available. Option
  (b) is the **minimal MVP-correct path** — option (a) is a follow-up
  spec-first change in IAM `admin-api.md` registry surface, not in scope
  here. Pick (b) at MVP; (a) is a separately-tracked enhancement.

##### Option (a) activation (Phase 2 — TASK-PC-FE-014)

Both option (a) and option (b) paths are first-class behaviors. With **TASK-BE-304** merged (IAM producer Phase 1: `console-registry-api.md § Per-operator profile attributes` + `admin_operators.finance_default_account_id` column + emission rule), option (a) is activated end-to-end on the consumer side by **TASK-PC-FE-014**. The activation does **not** remove or weaken option (b) — operators whose `admin_operators.finance_default_account_id` is NULL continue to see `forbidden / MISSING_PREREQUISITE`, byte-identical to MVP behavior.

Consumer wiring chain (top-down, all server-side; the browser never sees any of these headers or values):

1. **console-web registry parser**: when the IAM registry response is fetched (at OIDC login `/api/auth/callback` and on every refresh `/api/auth/refresh`), the response is parsed with a zod schema extended to recognize `productItem.operatorContext?.defaultAccountId` (the `finance` product item is the only one populating it in v1; `operatorContext` on any other item parses to `undefined`). The parsed value is stored in the server-side session helper alongside the existing operator-token / IAM-OIDC-token / active-tenant slots.
2. **console-web session helper**: a new server-only helper `getFinanceDefaultAccountId(): Promise<string | null>` (`import 'server-only'`) returns the stored value. Returns `null` when (i) the operator's row has NULL, (ii) the registry was not stored (e.g. registry fetch failed at login), or (iii) the value is an empty/whitespace string after trim.
3. **console-web dashboard proxy route**: `(console)/api/console/dashboards/operator-overview/route.ts` (the same server route that already forwards `Authorization` + `X-Operator-Token` + `X-Tenant-Id`) calls `getFinanceDefaultAccountId()` server-side and, **only when the value is non-blank**, sets a new request header `X-Finance-Default-Account-Id: <value>` on the `fetch` to `console-bff`. **Never** sent from the browser. **Never** set with an empty value.
4. **console-bff controller**: `OperatorOverviewController` accepts the optional header via `@RequestHeader(value = "X-Finance-Default-Account-Id", required = false)` and forwards it to `OperatorOverviewCompositionUseCase.compose(tenantId, financeDefaultAccountId)` (a new 2-arg overload; the 1-arg `compose(tenantId)` stays as a thin pass-through `compose(tenantId, null)` for any direct in-process caller).
5. **console-bff use-case**: `callFinance(tenantId, cred, accountId)` — when `hasText(accountId)`, routes through `FinanceBalanceReadPort.readBalances(tenantId, bearer, accountId)` (a new port method; the existing `read(tenantId, credential)` stays for `DomainReadPort` contract conformance but remains `UnsupportedOperationException`-throwing — the marker that the active path is `readBalances`). When `accountId` is null/blank, the existing MVP option (b) path is preserved verbatim: `forbidden / MISSING_PREREQUISITE`, no outbound HTTP fired.
6. **console-bff adapter**: `FinanceBalanceReadAdapter.readBalances(tenantId, credential, accountId)` is already present (since FE-011, in anticipation of this activation) — `GET /api/finance/accounts/{accountId}/balances` with IAM OIDC bearer (per § 2.4.7 verbatim).

**Hard invariants preserved**:

- **ADR-MONO-017 D4 HARD INVARIANT** (per-domain credential rule, sealed switch in `CredentialSelectionAdapter`): unchanged — `X-Finance-Default-Account-Id` is operator profile data flowing alongside credential, never credential itself. The `bearerFromCred(cred)` sealed switch in the use-case is unchanged.
- **§ 2.4.4 D3 cross-leg 401 discipline**: unchanged — when the finance leg returns 401, composition still emits 401 `TOKEN_INVALID` (auth is not a per-card degrade); the header is irrelevant to the auth boundary.
- **§ 3.3 zero retrofit (5th confirmation in this chain)**: 5 producer specs byte-unchanged in this Phase 2 (Phase 1 already merged IAM-side as TASK-BE-304; wms/scm/finance/erp/fan/ecommerce all byte-unchanged in both phases).
- **No browser-visible header**: `X-Finance-Default-Account-Id` is set only on the server-side `fetch` from the console-web proxy route to console-bff. The browser never sees the inbound or outbound header (same discipline as the existing 3 headers).
- **No logging of the value**: the header value (opaque finance account UUID) is `internal`-classified, not credential / not PII; nevertheless it must not appear in `log.info(...)` literals (finance F7 / `regulated.md` R7 transitive discipline).

**Honest failure modes (no green-wash)**:

- A **stale** `finance_default_account_id` (the finance account was deleted/migrated after the operator profile was set): finance returns `404 ACCOUNT_NOT_FOUND` → leg surfaces as `degraded / DOWNSTREAM_ERROR` (the existing `time()` classification). The console shows the leg failed, not a fabricated `ok`. Adding IAM-side validation against finance is out of scope (cross-service decoupling preserved).
- The **registry response did not store** the value (e.g. login-time registry fetch failed): `getFinanceDefaultAccountId()` returns `null`, header is omitted, finance card → `MISSING_PREREQUISITE`. The console shell continues to render via § 2.5 degraded catalog handling.
- The operator **switches tenants** mid-session: the `finance_default_account_id` is on the operator row (not per-tenant); the header continues to be sent across tenant switches.
- The operator's **value is updated** mid-session: the cached session value is stale until the next registry refresh; same staleness window as `tenants` array changes (accepted).

- **`asOf` field source**: server-side composition-request `Instant.now()`
  at request entry (NOT per-leg response timestamp); operators see the
  composition timestamp, not the slowest leg's freshness. wms's
  `X-Read-Model-Lag-Seconds` (per § 2.4.5) MAY surface as an additional
  card-level hint but is NOT in the v1 envelope schema.

##### console-web side obligations (FE)

- Server route `(console)/api/console/dashboards/operator-overview` (Next.js
  App Router server route) forwards the 3 headers (Authorization /
  X-Operator-Token / X-Tenant-Id) to `console-bff` server-side. **Plus** an
  optional 4th header `X-Finance-Default-Account-Id` per § Option (a)
  activation above (sourced from `getFinanceDefaultAccountId()` server-side
  helper, set **only when non-blank**). Browser never sees any of them.
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

- **No producer retrofit** — 5 producer specs (`{iam-platform, wms-platform, scm-platform, finance-platform, erp-platform}/specs/contracts/`) byte-unchanged.
- **Per-domain credential dispatch** verbatim from §§ 2.4.5/6/7/8 + § 2.6.
- **Read-only** — no `Idempotency-Key` / `X-Operator-Reason` / mutation method.
- **Producer-authoritative tenant gate** — `X-Tenant-Id` pass-through; BFF never re-derives or relaxes.
- **Per-card degrade** discipline — composition never blanks; `401`-cross-leg vs `403`/timeout-per-leg distinction preserved.
- **§ 3 parity matrix byte-unchanged** (attestation-marker count = exactly **16** — `parity-verification.test.ts` no-drift guard).
- **ADR-MONO-017 D1-D8 byte-unchanged** (no ADR amendment in this PR — this is execution under the ACCEPTED frame).

> **Phase 7 dashboard catalog (current)**: § 2.4.9.1 MVP "Operator Overview"
> (TASK-PC-FE-011 DONE 2026-05-20) + § 2.4.9.2 "Domain Health Overview"
> (TASK-PC-FE-013 DONE 2026-05-21) are merged. Subsequent Phase 7 dashboards
> (e.g. throughput per ADR-MONO-017 § 3.3 #4) remain separate future tasks
> following the same `§ 2.4.9.X` additive pattern, each inheriting the hard
> invariants above.

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
> endpoints exposed `permitAll` by every producer's SecurityConfig (IAM
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
| 1 | `GET /api/console/dashboards/domain-health` | Single composed cross-domain health envelope; one card per backend domain (IAM + wms + scm + finance + erp + ecommerce); each card carries the producer's Spring Boot `/actuator/health` status (`UP` / `DOWN` / `OUT_OF_SERVICE` / `UNKNOWN`) wrapped in the per-leg outcome (`ok` / `degraded`) per § 2.4.9 D5.A discipline | `Authorization: Bearer <iam-oidc-access-token>` (inbound principal, RS256 / IAM issuer) + `X-Tenant-Id: <active-tenant>` (forwarded to log MDC and degrade counter — **not** to outbound actuator legs); **`X-Operator-Token` NOT required** for this route (no outbound leg consumes it; the D4 sealed-switch is not invoked). Absent `Authorization` → `401 TOKEN_INVALID` before any outbound leg. Absent `X-Tenant-Id` → `400 NO_ACTIVE_TENANT` (for log/audit traceability, not because legs need it) | `console-bff` |

> The route is **GET only — read-only**. The hard invariant in § 2.4.9.1
> applies verbatim: no `Idempotency-Key`, no `X-Operator-Reason`, no
> destructive-confirm, no POST/PUT/PATCH/DELETE. Adding a mutation surface
> requires a fresh ADR amendment to ADR-MONO-017.

##### Composed producers (6 domains, reuse-only — § 3.3 zero retrofit #7)

The composition route fans out across **existing** public actuator
endpoints — one card per domain, **no producer retrofit**. The 6 endpoints
are Spring Boot actuator standards; the per-producer SecurityConfig
declarations that mark them `permitAll` are authoritative in their
respective service files and are **not redefined here**:

| # | Card | Composed producer endpoint | Outbound auth | Producer SecurityConfig (authoritative permitAll) | Read content surfaced |
|---|---|---|---|---|---|
| 1 | iam health | `GET http://iam.local/actuator/health` (gateway-service primary entry) | **None** (public actuator, no `Authorization`, no `X-Tenant-Id`) | IAM `gateway-service` `application.yml` `public-paths` includes `GET:/actuator/health` | `{"status": "UP" \| "DOWN" \| "OUT_OF_SERVICE" \| "UNKNOWN"}` aggregated status (Spring Boot `management.endpoint.health.show-details: never` per console-bff baseline; no component drill-down) |
| 2 | wms health | `GET http://wms.local/actuator/health` (gateway-service primary entry) | **None** | WMS `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` + `/actuator/health/**` | same aggregated status |
| 3 | scm health | `GET http://scm.local/actuator/health` (gateway-service primary entry) | **None** | SCM `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` | same aggregated status |
| 4 | finance health | `GET http://finance.local/actuator/health` (`account-service` direct — finance has no gateway-service in v1) | **None** | finance `account-service` `SecurityConfig` `permitAll` includes `/actuator/{health,info,prometheus}` | same aggregated status |
| 5 | erp health | `GET http://erp.local/actuator/health` (`masterdata-service` direct — erp has no gateway-service in v1) | **None** | erp `masterdata-service` `SecurityConfig` `permitAll` includes `/actuator/{health,info,prometheus}` | same aggregated status |
| 6 | ecommerce health | `GET http://ecommerce.local/actuator/health` (`gateway-service` primary entry) | **None** | ecommerce `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` + `/actuator/health/**` + `/actuator/info` (WebFlux reactive gateway; verified 2026-06-13 — TASK-MONO-241 AC-8, 0-byte producer change) | same aggregated status |

**Producer immutability**: the 6 producer SecurityConfig declarations above
are **byte-unchanged spec-side and impl-side** (§ 3.3 seventh confirmation;
TASK-MONO-241 added the ecommerce leg without retrofitting any producer).
The console-bff composition use-case calls the existing public actuator
endpoints verbatim. The 5 outbound `RestClient` beans (`gapRestClient` /
`wmsRestClient` / `scmRestClient` / `financeRestClient` / `erpRestClient`)
registered for § 2.4.9.1 are **reused** here (same base URLs, same per-leg
2s timeout); the **6th** `ecommerceRestClient` bean (base-url
`consolebff.outbound.ecommerce.base-url` = `http://ecommerce.local`, same
per-leg 2s timeout) was added by TASK-MONO-241 for the ecommerce health leg.
As of TASK-MONO-243 the `ecommerceRestClient` is **shared** by two distinct
adapters: this credential-LESS `/actuator/health` health leg, and the
credential-FULL `/api/admin/products` operator-overview snapshot leg
(§ 2.4.9.1 row 6) — same base URL / gateway, different path + authorization.
The § 2.4.9.1 overview is now also 6 (symmetry restored; see the
cross-reference note there).

##### Response schema (`200 OK`)

```json
{
  "asOf": "2026-05-21T01:30:00Z",
  "cards": [
    { "domain": "iam",       "status": "ok",       "data": { "status": "UP" } },
    { "domain": "wms",       "status": "ok",       "data": { "status": "UP" } },
    { "domain": "scm",       "status": "degraded", "reason": "DOWNSTREAM_ERROR" },
    { "domain": "finance",   "status": "ok",       "data": { "status": "OUT_OF_SERVICE" } },
    { "domain": "erp",       "status": "ok",       "data": { "status": "UP" } },
    { "domain": "ecommerce", "status": "ok",       "data": { "status": "UP" } }
  ]
}
```

- `asOf`: composition request server-side timestamp (ISO-8601 UTC). Operators see "data as-of HH:MM:SS" in the UI.
- `cards[]`: **exactly 6 entries** in **fixed order** `[iam, wms, scm, finance, erp, ecommerce]` (UI rendering ordering invariant; never reordered by status). `ecommerce` is appended **last** (TASK-MONO-241) — the existing 5 keep their order byte-equal.
- `cards[i].status` ∈ `{ "ok", "degraded" }` — **note**: `forbidden` is **never emitted** on this route (outbound actuator legs are public; HTTP `403` from a leg falls through to `degraded` like any other non-success status, since `403` from a public actuator means a misconfigured producer, not an operator-permission decision; treating it as `forbidden` would mis-signal a producer regression as a per-card-permission UX state).
- `cards[i].data.status` ∈ Spring Boot health enum `{ "UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN" }`:
  - `UP` → green/healthy card visual.
  - `DOWN` → red/critical visual; operator surface is **NOT degraded** (the leg returned a successful health document — the producer is honestly reporting itself as down). Distinction from `degraded` (the BFF could not reach the producer at all).
  - `OUT_OF_SERVICE` → maintenance yellow visual.
  - `UNKNOWN` → grey/inconclusive visual.
- `cards[i].status == "degraded"` → `reason` ∈ `{ "DOWNSTREAM_ERROR", "TIMEOUT", "CIRCUIT_OPEN" }`; `data` absent. Card renders "leg unreachable" placeholder + retry affordance.
- **All-down envelope**: every leg can return non-`ok` simultaneously — the route still emits `200` with all 6 cards in `degraded` states. The route NEVER emits `503` / blanks the response (D5.A discipline; D5.B rejection re-affirmed).

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

- **Inbound** (console-web SSR → console-bff): `Authorization` (IAM OIDC access token, inbound principal — Spring Security validates against IAM JWKS) + `X-Tenant-Id` (operator's selected active tenant, forwarded for log MDC). The browser **never** reaches console-bff directly.
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
| `bff_fanout_latency_seconds{domain,route}` | `domain` ∈ `{iam,wms,scm,finance,erp,ecommerce}` × `route` = `"domain-health"` |
| `bff_fanout_errors_total{domain,route,code}` | same `domain`/`route` + `code` ∈ `{5xx,timeout,circuit_open}` (no `tenant_forbidden` / `permission_denied` / `missing_prerequisite` — those classifications belong to data legs only) |
| `bff_aggregation_degrade_count_total{dashboard,degraded_domain}` | `dashboard = "domain-health"` + `degraded_domain` ∈ `{iam,wms,scm,finance,erp,ecommerce}` (one increment per `degraded` card per response) |

OTel `traceparent` propagates inbound → every outbound leg; per-leg span
carries `bff.domain` + `bff.route="domain-health"` attributes.

##### Implementation guidance (impl PR scope notes — not contract)

- **No credential pre-resolve**: the use case (`DomainHealthCompositionUseCase`) MUST NOT invoke `CredentialSelectionPort.selectFor(...)` on any path. Grep-assert in tests.
- **`asOf` field source**: server-side composition-request `Instant.now()` at request entry (same as § 2.4.9.1).
- **Span attribute reuse**: existing `bff.domain` + new `bff.route="domain-health"` — no new attribute key.

##### console-web side obligations (FE)

- Server route `(console)/api/console/dashboards/domain-health` (Next.js App Router server route) forwards `Authorization` + `X-Tenant-Id` to `console-bff` server-side. **Does NOT forward `X-Operator-Token`** (the BFF route does not require it; sending it would be misleading). Browser never sees the inbound headers.
- `features/domain-health/` (`<DomainHealthScreen>` server component + `<DomainHealthCard>` × 6 + `<DegradeBanner>` if all-down + `<RetryButton>` client-only) renders the composed envelope (the card list is **data-driven** — it maps the BFF envelope `cards[]` array, so the 6th `ecommerce` card renders with zero hardcoded-count change). Per-card UI shape:
  - `ok` + `data.status="UP"` → green-checkmark card.
  - `ok` + `data.status="DOWN"` → red-cross card (producer self-reported critical — NOT a BFF/network failure).
  - `ok` + `data.status="OUT_OF_SERVICE"` → yellow-wrench card (planned maintenance).
  - `ok` + `data.status="UNKNOWN"` → grey-question card.
  - `degraded` → "leg unreachable" placeholder + retry affordance.
- Route `(console)/dashboards/health` is the operator-facing entry; a navigation entry in the in-console nav (`<MainNav>` "도메인 상태") is added alongside the existing "통합 개요".
- No client-side polling, no auto-refresh interval — operator-initiated retry only (matches § 2.4.4 / § 2.4.9 invariant).

##### Hard invariants this route inherits (HARD INVARIANT — ADR-MONO-017 + § 3.3 + § 2.4.9)

- **No producer retrofit** — 6 producer SecurityConfig + actuator wiring byte-unchanged (the ecommerce gateway `/actuator/health` was already `permitAll` — verified, 0-byte producer change; TASK-MONO-241 AC-8).
- **D4 scope clarification** — D4 governs data legs only; this route's actuator legs are explicitly outside D4. The sealed-switch is NOT invoked on these legs (grep-asserted).
- **Read-only** — no `Idempotency-Key` / `X-Operator-Reason` / mutation method.
- **No `Authorization` / `X-Tenant-Id` / `X-Operator-Token` on outbound legs** (grep-asserted).
- **Per-card degrade** discipline — composition never blanks.
- **§ 3 parity matrix byte-unchanged** (attestation-marker count = exactly **16**).
- **ADR-MONO-017 D1-D8 byte-unchanged** (no ADR amendment in this PR).

> **Not a § 3 parity row**: composition routes are additive to the operator
> surface, never replace a § 3 row. § 3 count remains **16** post-merge.

#### 2.4.10 ecommerce operations surface — product/order operator CRUD (TASK-MONO-252 contract / ADR-MONO-031 Phase 0 — first console **write** binding for ecommerce)

The **first ecommerce operations (CRUD) binding** federated by the console, and
the contract base for **sunsetting the standalone `admin-dashboard`**
([ADR-MONO-031](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md),
executing [ADR-MONO-030](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md)
§ 3.4 Step 4 facet a-후속-2). Where § 2.4.9.1/§ 2.4.9.2 bind ecommerce only as a
**console-bff read leg** (operator-overview snapshot + domain-health card), this
sub-binding renders the ecommerce **product** and **order** operator surfaces so
an operator can manage the catalog and drive order lifecycle **from inside the
console** — the console equivalent of the `admin-dashboard` product/order
screens. Per **ADR-MONO-017 D2.A**, this surface is **console-web → ecommerce
gateway direct** (Next.js Route Handlers); it adds **NO console-bff write leg**
(the BFF stays cross-domain-read-aggregation only — the wms-outbound § 2.4.5.1 /
erp-approval / ledger-resolve precedent).

This sub-binding **inherits the non-IAM domain cross-cutting rules** and does
not restate them: the **credential** (the domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`, ADR-MONO-017 D4); the
**tenant model** (tenant rides in the JWT `tenant_id ∈ {ecommerce,*}` claim —
**no** `X-Tenant-Id` header on the direct call; ecommerce gateway
`TenantClaimValidator` is the authoritative gate, ADR-MONO-019 D5 / ADR-MONO-030
Step 2 entitlement-trust); **eligibility** (registry `productKey=ecommerce`
available + tenants, reused from TASK-MONO-240 — **no** new `productKey`/enum);
the **resilience** taxonomy (401 → whole-session IAM re-login; 403 → inline "not
available to your role"; 503/timeout → only this section degrades; tokens/PII
never logged); and the **§ 3 parity matrix is NOT mutated** (additive domain
scope, no § 3 row — attestation count stays **16**).

- **Tenant-isolation precondition (the absorption-order crux — normative)**:
  **only `product-service` + `order-service` carry row-level `tenant_id`**
  (ADR-MONO-030 Step 2/3) and are therefore the **only** ecommerce areas safe to
  federate into a multi-tenant operator console at this contract revision.
  Absorbing a not-yet-isolated area (users/promotions/shippings/notifications)
  would let a tenant-scoped operator read cross-tenant rows (ADR-MONO-030 M1/M6
  violation). Those 4 areas are added in **§ 2.4.10.1+** (one sub-section each),
  **each gated on that area's backend `tenant_id` migration** (ADR-MONO-030
  Step 4) landing first. This is a named staged backlog, not a silent omission.

- **Authoritative producer (owned by ecommerce, consumed only — do NOT redefine
  here)**: ecommerce `product-service` (`AdminProductController` +
  `AdminProductImageController`) and `order-service` (`AdminOrderController`),
  consumed via the ecommerce gateway admin path `/api/admin/**` (base URL
  `ECOMMERCE_ADMIN_BASE_URL`, default the ecommerce gateway hostname e.g.
  `http://ecommerce.local/api/admin` — the same gateway + IAM-OIDC credential the
  § 2.4.9.1 ecommerce snapshot leg already routes through):

  | # | Operation | Producer endpoint | Kind |
  |---|---|---|---|
  | 1 | list products | `GET /admin/products?categoryId&status&page&size` | read |
  | 2 | product detail | `GET /admin/products/{id}` (public `/products/{id}` read path) | read |
  | 3 | **register product** | `POST /admin/products` | mutation |
  | 4 | **update product** | `PATCH /admin/products/{id}` | mutation |
  | 5 | **delete product** | `DELETE /admin/products/{id}` | mutation |
  | 6 | **add variant** | `POST /admin/products/{id}/variants` | mutation |
  | 7 | **update variant** | `PATCH /admin/products/{id}/variants/{variantId}` | mutation |
  | 8 | **delete variant** | `DELETE /admin/products/{id}/variants/{variantId}` | mutation |
  | 9 | **adjust stock** | `PATCH /admin/products/{id}/stock` | mutation |
  | 10 | list images | `GET /admin/products/{id}/images` | read |
  | 11 | **presigned upload url** | `POST /admin/products/{id}/images/upload-url` | mutation |
  | 12 | **register image** | `POST /admin/products/{id}/images` | mutation |
  | 13 | **update image** | `PATCH /admin/products/{id}/images/{imageId}` | mutation |
  | 14 | **delete image** | `DELETE /admin/products/{id}/images/{imageId}` | mutation |
  | 15 | list orders | `GET /admin/orders?status&page&size` | read |
  | 16 | order detail | `GET /admin/orders/{id}` | read |
  | 17 | **change order status** | `POST /admin/orders/{id}/status` | mutation |

  **Out of this binding (deferred, not silently dropped)**: seller admin
  (`AdminSellerController POST /admin/sellers`) and search reindex
  (`SearchAdminController POST /search/admin/reindex`) — separate seller/search
  facets. The `admin-dashboard` **dashboard KPI** widgets are partially covered
  by the § 2.4.9.1 ecommerce overview snapshot (TASK-MONO-243); any residual KPI
  is a later facet.

- **Mutation discipline (Phase-1 producer-verify gated)**: every mutation
  (#3–9, #11–14, #17) is **confirm-gated** in the UI and carries the
  domain-facing OIDC credential. Two ecommerce-specific items **MUST be verified
  against the producer in Phase 1 before the write Route Handlers ship** (the
  ecommerce admin API is less formalised than wms-outbound, so this contract
  names the gaps rather than fabricating semantics):
  1. **Operator role mapping** — `AdminProductController` register/update/delete
     require an `X-User-Role: ADMIN` header today. The console operator carries
     an IAM OIDC token, not `X-User-Role`. Phase 1 MUST resolve how the ecommerce
     gateway maps the OIDC operator identity → the producer's role gate (gateway
     header injection vs producer accepting the OIDC scope) — **no** raw
     client-supplied `X-User-Role` from the browser.
  2. **Idempotency / optimistic concurrency** — unlike wms-outbound (§ 2.4.5.1,
     per-call `Idempotency-Key` + `version`), the ecommerce product/order admin
     API does not document an `Idempotency-Key` or `version`/ETag. Phase 1 MUST
     confirm the producer's double-submit/conflict story; absent a producer
     idempotency key, the console relies on **confirm-gate + producer state
     guards** (e.g. order `status` transition validation → `409/422` surfaced
     actionably), and MUST NOT fabricate an `Idempotency-Key` the producer
     ignores. The write Route Handler shape otherwise mirrors
     `wms/outbound/[orderId]/ship/route.ts` (`runtime='nodejs'`, Zod body parse,
     `makeProxyErrorMapper('ecommerce', …)` → 401/403/404/409/422/503).

- **Error envelope**: the ecommerce producer error shape (product/order
  `GlobalExceptionHandler`) is **consumed, its exact schema pinned in Phase 1**
  with a dedicated parser (do not assume the wms nested or scm/finance flat shape
  — verify). `X-Operator-Reason` is **not** defined by this surface (asserted
  absent, same as wms-outbound).

- **Producer immutability**: cross-reference only. Any change to the ecommerce
  product/order admin contract is an ecommerce project-internal spec-first change
  in its service API spec; this section follows it, never redefines it (§ 5
  Change Rule).

> **Not a § 3 parity row**: the ecommerce operations surface is additive
> federated **domain** scope, not an IAM `admin-web` parity capability; it adds
> no § 3 row and changes none (count stays **16**).

#### 2.4.10.1 ecommerce users operator list/detail (TASK-PC-FE-084 / ADR-MONO-031 Phase 2b — console absorption of the `admin-dashboard` user-management area)

The **second** ecommerce operations sub-binding (the first of the four § 2.4.10
"named staged backlog" areas), unblocked now that **user-service carries
row-level `tenant_id`** (TASK-BE-367, ADR-MONO-030 Step 4 — the
tenant-isolation precondition § 2.4.10 made normative). It renders the
ecommerce **user** operator surface (operator browses the tenant's customer
profiles) — the console equivalent of the `admin-dashboard` user-management
list/detail screens. This sub-binding is **read-only**: the producer exposes no
admin user mutation, and the console adds none (no status-change/delete leg).

This sub-binding **inherits § 2.4.10's cross-cutting rules verbatim** and does
not restate them: the **credential** (domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`); the **tenant model**
(tenant rides in the JWT `tenant_id ∈ {ecommerce,*}` claim — **no**
`X-Tenant-Id` header; the user-service `TenantContextFilter` reads the
gateway-injected `X-Tenant-Id` and the persistence layer filters
`WHERE tenant_id`, TASK-BE-367); **eligibility** (registry `productKey=ecommerce`
available + tenants — **no** new `productKey`/enum); the **resilience** taxonomy
(401 → whole-session IAM re-login; 403 → inline "not available to your role";
404 → notFound empty-state; 503/timeout → only this section degrades; **tokens
and customer PII — email / phone / profileImageUrl — never logged**); and the
**§ 3 parity matrix is NOT mutated** (count stays **16**). Per ADR-MONO-017
D2.A this is **console-web → ecommerce gateway direct** (Next.js Route
Handlers); **NO** console-bff leg.

- **Authoritative producer (owned by ecommerce, consumed read-only — do NOT
  redefine here)**: ecommerce `user-service` `AdminUserController`, consumed via
  the ecommerce gateway admin path `/api/admin/**` (base URL
  `ECOMMERCE_ADMIN_BASE_URL`, same gateway + IAM-OIDC credential as § 2.4.10).
  Producer contract: ecommerce
  [`user-api.md` § Admin endpoints](../../../ecommerce-microservices-platform/specs/contracts/http/user-api.md):

  | # | Operation | Producer endpoint | Kind |
  |---|---|---|---|
  | 1 | list users | `GET /admin/users?status&email&page&size` | read |
  | 2 | user detail | `GET /admin/users/{userId}` | read |

  List item fields: `userId, email, name, nickname, status, createdAt`; detail
  adds `phone, profileImageUrl, updatedAt`. `status ∈ {ACTIVE, SUSPENDED,
  WITHDRAWN}`. Error envelope = the same flat ecommerce shape
  `{code, message, timestamp}` (401 / 403 / 404 `USER_PROFILE_NOT_FOUND`),
  consumed with the § 2.4.10 ecommerce parser.

- **Out of this binding (deferred, not silently dropped)**: user **mutations**
  (status change / suspend / GDPR delete) — the producer exposes no admin write
  endpoint; a later facet if/when one lands. Address & wishlist sub-resources are
  consumer-plane, not operator surfaces. promotions / shippings / notifications
  remain § 2.4.10.2+ (each gated on its own backend `tenant_id` migration).

- **Producer immutability**: cross-reference only — any change to the ecommerce
  user admin contract is an ecommerce project-internal spec-first change; this
  section follows, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: additive federated **domain** scope; adds no § 3 row
> (count stays **16**).

#### 2.4.10.2 ecommerce promotions operator CRUD (TASK-PC-FE-086 / ADR-MONO-031 Phase 3b — console absorption of the `admin-dashboard` promotion-management area)

The **third** ecommerce operations sub-binding (the second of the § 2.4.10 staged
backlog areas), unblocked now that **promotion-service carries row-level
`tenant_id`** (TASK-BE-368, ADR-MONO-030 Step 4). Unlike § 2.4.10.1 (users,
read-only), this is a **full-CRUD** surface — the console equivalent of the
`admin-dashboard` promotion-management screens (list / detail / create / update /
delete + coupon-issue). It mirrors the § 2.4.10 product write binding (the
mutation template), NOT the read-only users binding.

This sub-binding **inherits § 2.4.10's cross-cutting rules verbatim** and does not
restate them: the **credential** (domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`); the **tenant model**
(tenant rides in the JWT `tenant_id ∈ {ecommerce,*}` claim — **no** `X-Tenant-Id`
header; promotion-service `TenantContextFilter` + persistence `WHERE tenant_id`,
TASK-BE-368); **eligibility** (registry `productKey=ecommerce` — **no** new
`productKey`/enum); the **mutation discipline** (confirm-gated UI; **no**
`Idempotency-Key` — the producer defines none; producer state-guards
`409`/`422` surfaced inline); the **resilience** taxonomy (401 → whole-session IAM
re-login; 403 → inline "not available to your role"; 404 → notFound empty-state;
503/timeout → only this section degrades; tokens never logged); and the **§ 3
parity matrix is NOT mutated** (count stays **16**). Per ADR-MONO-017 D2.A this is
**console-web → ecommerce gateway direct** (Next.js Route Handlers); **NO**
console-bff write leg.

- **Authoritative producer (owned by ecommerce, consumed — do NOT redefine
  here)**: ecommerce `promotion-service` `PromotionController`, consumed via the
  ecommerce gateway admin path `/api/**` (base URL `ECOMMERCE_ADMIN_BASE_URL`, same
  gateway + IAM-OIDC credential as § 2.4.10). Producer contract: ecommerce
  [`promotion-api.md`](../../../ecommerce-microservices-platform/specs/contracts/http/promotion-api.md):

  | # | Operation | Producer endpoint | Kind |
  |---|---|---|---|
  | 1 | list promotions | `GET /api/promotions?status&page&size` | read |
  | 2 | promotion detail | `GET /api/promotions/{id}` | read |
  | 3 | **create promotion** | `POST /api/promotions` | mutation |
  | 4 | **update promotion** | `PUT /api/promotions/{id}` (full replace — **PUT**, not PATCH) | mutation |
  | 5 | **delete promotion** | `DELETE /api/promotions/{id}` | mutation |
  | 6 | **issue coupons** | `POST /api/promotions/{id}/coupons/issue` (`{userIds:[]}`) | mutation |

  Promotion fields: `promotionId, name, description, discountType (FIXED|PERCENTAGE),
  discountValue, maxDiscountAmount, maxIssuanceCount, issuedCount, startDate,
  endDate, status (ACTIVE|SCHEDULED|ENDED), createdAt, updatedAt`. Error envelope =
  the same flat ecommerce shape `{code, message, timestamp}` (400 `VALIDATION_ERROR`,
  403 `ACCESS_DENIED`, 404 `PROMOTION_NOT_FOUND`, 422 `PROMOTION_ALREADY_ENDED` /
  `PROMOTION_HAS_ISSUED_COUPONS` / `PROMOTION_NOT_ACTIVE` / `COUPON_LIMIT_EXCEEDED`),
  consumed with the § 2.4.10 ecommerce parser.

- **State-gated mutations**: update is offered only while `status != ENDED`
  (producer `422 PROMOTION_ALREADY_ENDED`); coupon-issue only while
  `status == ACTIVE` (`422 PROMOTION_NOT_ACTIVE`); delete is blocked when coupons
  were issued (`422 PROMOTION_HAS_ISSUED_COUPONS`) — surfaced inline, not crashed.

- **Out of this binding (deferred)**: the consumer coupon surface (`/api/coupons/me`,
  shopper-plane — not an operator surface). shippings / notifications remain
  § 2.4.10.3+ (each gated on its backend `tenant_id` migration).

- **Producer immutability**: cross-reference only — any change to the ecommerce
  promotion admin contract is an ecommerce project-internal spec-first change; this
  section follows, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: additive federated **domain** scope; adds no § 3 row
> (count stays **16**).

#### 2.4.10.3 ecommerce shippings operator surface (TASK-PC-FE-088 / ADR-MONO-031 Phase 4b — console absorption of the `admin-dashboard` shipping-management area)

The **fourth** ecommerce operations sub-binding (the third of the § 2.4.10 staged
backlog areas), unblocked now that **shipping-service carries row-level
`tenant_id`** (TASK-BE-369, ADR-MONO-030 Step 4). This is a **list + status-machine
+ refresh-tracking** surface — the console equivalent of the `admin-dashboard`
shipping-management screens (list / linear status transitions / operator-triggered
carrier sync). It mirrors the § 2.4.10 promotions write binding model (non-admin
path, direct gateway).

This sub-binding **inherits § 2.4.10's cross-cutting rules verbatim** and does not
restate them: the **credential** (domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`); the **tenant model**
(tenant rides in the JWT `tenant_id ∈ {ecommerce,*}` claim — **no** `X-Tenant-Id`
header; shipping-service `TenantContextFilter` + persistence `WHERE tenant_id`,
TASK-BE-369); **eligibility** (registry `productKey=ecommerce` — **no** new
`productKey`/enum); the **mutation discipline** (confirm-gated UI; **no**
`Idempotency-Key` — the producer defines none; producer state-guards `400`/`409`/`422`
surfaced inline); the **resilience** taxonomy (401 → whole-session IAM re-login;
403 → inline "not available to your role"; 404 → notFound empty-state; 503/timeout
→ only this section degrades; tokens never logged); and the **§ 3 parity matrix is
NOT mutated** (count stays **16**). Per ADR-MONO-017 D2.A this is **console-web →
ecommerce gateway direct** (Next.js Route Handlers); **NO** console-bff write leg.

- **Authoritative producer (owned by ecommerce, consumed — do NOT redefine
  here)**: ecommerce `shipping-service` `ShippingController`, consumed via the
  ecommerce gateway **non-admin** path `/api/shippings/**` (base URL
  `ECOMMERCE_PUBLIC_BASE_URL`, same gateway + IAM-OIDC credential as § 2.4.10,
  distinct from the `/api/admin/**` admin subtree — shipping has NO admin path):

  | # | Operation | Producer endpoint | Kind |
  |---|---|---|---|
  | 1 | list shippings | `GET /api/shippings?page=&size=&status=` | read |
  | 2 | **status transition** | `PUT /api/shippings/{shippingId}/status` (`{status, trackingNumber?, carrier?}`) | mutation |
  | 3 | **refresh tracking** | `POST /api/shippings/{shippingId}/refresh-tracking` (empty body, best-effort) | mutation |

  Shipping fields: `shippingId, orderId, userId, status (PREPARING|SHIPPED|IN_TRANSIT|DELIVERED),
  trackingNumber?, carrier?, statusHistory[], createdAt, updatedAt?`. Error envelope =
  the same flat ecommerce shape `{code, message, timestamp}` (400 `InvalidShipping`
  [SHIPPED without carrier/tracking] / `INVALID_STATUS`, 404 `SHIPPING_NOT_FOUND`,
  409/422 `INVALID_TRANSITION`), consumed with the § 2.4.10 ecommerce parser.

- **State-gated mutation (linear machine; SHIPPED requires carrier+tracking)**:
  The state machine is **strictly linear, single successor each**:
  `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`. `DELIVERED` is terminal.
  The UI must expose only the one allowed forward transition per current status
  (no skips, no backward moves). The `PREPARING → SHIPPED` transition opens a
  form dialog requiring **carrier + tracking number** (producer rejects SHIPPED
  without them — 400 `InvalidShipping`; surfaced inline). All other transitions
  are confirm-gated only. `refresh-tracking` is best-effort: the producer returns
  200 with unchanged status when the carrier mode is mock or the carrier is
  unreachable (no error surfaced).

- **Out of this binding (deferred / out of scope)**: create/delete of shipments
  (created by the OrderConfirmed flow — not operator-initiated); the consumer
  tracking surface (shopper-plane — not operator). notifications remain
  § 2.4.10.4+ (gated on its backend `tenant_id` migration).

- **Producer immutability**: cross-reference only — any change to the ecommerce
  shipping contract is an ecommerce project-internal spec-first change; this
  section follows, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: additive federated **domain** scope; adds no § 3 row
> (count stays **16**).

#### 2.4.10.4 ecommerce notifications template operator surface (TASK-PC-FE-089 / ADR-MONO-031 Phase 5b — console absorption of the `admin-dashboard` notification-template-management area)

The **fifth and final** ecommerce operations sub-binding (the fourth and last of
the § 2.4.10 staged backlog areas), unblocked by **TASK-BE-373** (notification-service
row-level `tenant_id` + the `GET /templates/{id}` detail endpoint gap-fill —
ADR-MONO-030 Step 4). This is a **list + create + edit** surface — the console
equivalent of the `admin-dashboard` notification-template management screens.
No delete (producer defines none).

With this binding **all 6 ecommerce operator areas** (products / orders / image /
users / promotions / shippings / notifications) are absorbed into the console.
**ADR-MONO-031 Phase 6 app-deletion gate is now unblocked.**

This sub-binding **inherits § 2.4.10's cross-cutting rules verbatim** and does
not restate them: the **credential** (domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`); the **tenant model**
(JWT `tenant_id` claim — NO `X-Tenant-Id` header); the **error envelope** (flat
`{ code, message, timestamp }`); the **resilience taxonomy** (401 → re-login /
403 → inline / 503 → section degrades only); the **proxy model** (console-web
same-origin route handlers → ecommerce gateway direct, NO console-bff write leg
— ADR-MONO-017 D2.A); **NO `Idempotency-Key`** (producer defines none).

- **Authoritative producer surface** (do NOT redefine here): ecommerce
  `notification-service` `TemplateController`, consumed via the ecommerce
  gateway **non-admin** path `/api/notifications/templates` (base URL
  `ECOMMERCE_PUBLIC_BASE_URL`, same gateway + IAM-OIDC credential as § 2.4.10,
  same model as promotions/shippings — NOT the `/api/admin/**` subtree):

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/notifications/templates?page=&size=` | paginated template list (summary: templateId, type, channel, subject, createdAt) |
| 2 | `GET` | `/api/notifications/templates/{templateId}` | template detail (full, incl. body, createdAt, updatedAt) — **TASK-BE-373 gap-fill endpoint** |
| 3 | `POST` | `/api/notifications/templates` | create; body `{type, channel, subject, body}` → 201 `{templateId}` |
| 4 | `PUT` | `/api/notifications/templates/{templateId}` | update; body `{subject, body}` only — **type/channel are immutable after creation** |

  Error envelope = the same flat ecommerce shape `{code, message, timestamp}`
  (400 `VALIDATION_ERROR`, 403 `ACCESS_DENIED`, 404 `TEMPLATE_NOT_FOUND`,
  409 `TEMPLATE_ALREADY_EXISTS` [duplicate type+channel within tenant]),
  consumed with the § 2.4.10 ecommerce parser.

- **Type/channel immutability**: `type` (ORDER_PLACED / PAYMENT_COMPLETED /
  SHIPPING_STATUS_CHANGED / WELCOME) and `channel` (EMAIL / SMS / PUSH) are
  set at creation and immutable thereafter. The producer PUT body accepts ONLY
  `{ subject, body }`. The UI keeps them read-only on the edit form and NEVER
  sends type/channel in the update request body.

- **No delete** (producer defines none — this is not a silent omission; the
  producer `TemplateController` exposes no DELETE endpoint).

- **Producer immutability**: cross-reference only — any change to the ecommerce
  notification contract is an ecommerce project-internal spec-first change;
  this section follows, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: additive federated **domain** scope; adds no § 3 row
> and changes none (count stays **16**).

#### 2.4.10.5 ecommerce sellers operator surface (TASK-PC-FE-090 / ADR-MONO-031 § 2.4.10 7th area — net-new marketplace seller management, unblocked by TASK-BE-375)

The **seventh ecommerce operations area** (the sixth § 2.4.10 sub-binding, a
net-new operator surface with no `admin-dashboard` parity counterpart — ADR-MONO-030
Step 4 facet f, marketplace seller axis). Unblocked by **TASK-BE-375**
(`AdminSellerController` operator plane: `GET /api/admin/sellers` list +
`GET /api/admin/sellers/{sellerId}` detail + `POST /api/admin/sellers` register).
This is a **list + detail + register** surface (MVP); no deactivate/suspend
(ADR-030 v1 — the backend exposes only those three endpoints).

This sub-binding **inherits § 2.4.10's cross-cutting rules verbatim** and does
not restate them: the **credential** (domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`); the **tenant model**
(JWT `tenant_id` claim — NO `X-Tenant-Id` header); the **error envelope** (flat
`{ code, message, timestamp }`); the **resilience taxonomy** (401 → re-login /
403 → inline / 503 → section degrades only); the **proxy model** (console-web
same-origin route handlers → ecommerce gateway direct, NO console-bff write leg
— ADR-MONO-017 D2.A); **NO `Idempotency-Key`** (producer defines none).

- **Authoritative producer surface** (do NOT redefine here): ecommerce
  `product-service` `AdminSellerController`, consumed via the ecommerce
  gateway **admin** path `/api/admin/sellers` (base URL
  `ECOMMERCE_ADMIN_BASE_URL` = `http://ecommerce.local/api/admin`, same gateway
  + IAM-OIDC credential as § 2.4.10 products/orders/users — **NOT**
  `ECOMMERCE_PUBLIC_BASE_URL` which promotions/notifications/shippings use;
  sellers live under the `/api/admin/**` subtree gated on `roles ∋ ADMIN` — the
  operator's ADR-MONO-035 4a assume-tenant-derived domain role; the legacy
  `account_type=OPERATOR` gateway leg was removed at 4b):

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/admin/sellers?page=&size=` | paginated list (rows: `sellerId, displayName, status, createdAt`) |
| 2 | `GET` | `/api/admin/sellers/{sellerId}` | seller detail (full); missing/cross-tenant → 404 |
| 3 | `POST` | `/api/admin/sellers` | register; body `{sellerId, displayName}` → 201 `{sellerId}` |

  Error envelope = the same flat ecommerce shape `{ code, message, timestamp }`
  (400 `VALIDATION_ERROR`, 403 `ACCESS_DENIED`, 404 `SELLER_NOT_FOUND`,
  409 `CONFLICT` [duplicate `sellerId` within tenant]),
  consumed with the § 2.4.10 ecommerce parser.

- **status=ACTIVE only** (v1). The backend exposes no suspend/deactivate
  endpoint; the console does not attempt any status mutation. Per-tenant
  `default` seller appears in the list as a real ACTIVE row — expected.

- **No update, no delete** (producer defines none — ADR-030 v1; this is not
  a silent omission). The console's register form validates `sellerId` ≤ 64
  chars non-blank, `displayName` non-blank; 409 `CONFLICT` (duplicate
  `sellerId`) surfaced inline.

- **Producer immutability**: cross-reference only — any change to the ecommerce
  seller contract is an ecommerce project-internal spec-first change;
  this section follows, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: additive net-new federated **domain** scope (no
> `admin-dashboard` counterpart for sellers); adds no § 3 row and changes none
> (count stays **16**).

### 2.5 Resilience

- Console/BFF fan-out applies circuit-breaker / retry / timeout per `platform/` baselines (`integration-heavy` trait).
- One domain unavailable MUST degrade only that domain's section — never blank the console shell.

### 2.6 Operator Token Exchange (normative — ADR-MONO-014 D2/D3)

The operator credential the console presents to `/api/admin/**` (§ 2.2 registry + the Phase-2 operator screens § 2.4) is obtained by a **server-side RFC 8693 token exchange**, never by sending the IAM OIDC token directly.

- **Endpoint (authoritative producer)**: `POST http://iam.local/api/admin/auth/token-exchange` (IAM `admin-service`, on the same `/api/admin/**` operator-auth public-path subtree as the registry). The request/response/error contract is owned by IAM [`iam-platform/specs/contracts/http/admin-api.md` § `POST /api/admin/auth/token-exchange`](../../../iam-platform/specs/contracts/http/admin-api.md); the subject-token validation policy is IAM [`admin-service/security.md` § IAM OIDC Subject-Token Validation](../../../iam-platform/specs/services/admin-service/security.md). This file does **not** redefine those — it only states the consumer obligation.
- **Request** (server-side only, `application/json`, RFC 8693 — verbatim per the producer contract):

  ```json
  {
    "grant_type": "urn:ietf:params:oauth:grant-type:token-exchange",
    "subject_token": "<the operator's IAM OIDC platform-console-web access token>",
    "subject_token_type": "urn:ietf:params:oauth:token-type:access_token"
  }
  ```

- **Response 200**: `{ "accessToken": "<operator JWT>", "expiresIn": <seconds>, "tokenType": "admin" }`. The console stores `accessToken` in its own HttpOnly·Secure·SameSite=strict operator cookie with `maxAge = expiresIn`, validates `tokenType === "admin"`, and uses **only** this token for `/api/admin/**`.
- **When**: on session establish (`/api/auth/callback`, immediately after the IAM tokens are stored) **and** on every IAM refresh (`/api/auth/refresh`, after the IAM access token rotates). **Re-exchange model (ADR-MONO-014 D2)**: there is **no operator-refresh token or operator-refresh state** — each IAM refresh triggers a fresh exchange using the rotated IAM access token; the operator cookie's lifetime tracks the response `expiresIn`.
- **Fail-closed mapping** (parity with the § 2.5 resilience posture, but on the operator trust boundary it is fail-**closed**, never degrade-with-fallback):
  - Exchange `401 TOKEN_INVALID` (subject token invalid / OIDC subject not mapped to an active `admin_operators` row — producer fail-closed per `admin-api.md`/`security.md`): the operator is **not provisioned** for operator actions → forced re-login with a distinct reason; the operator cookie is **not** set; on refresh the existing operator cookie is dropped.
  - Exchange `400 BAD_REQUEST`/`VALIDATION_ERROR`, timeout, network failure, or `5xx`: treated as **session-unavailable** → no operator cookie set / existing operator session dropped; the console never falls back to the IAM OIDC token on the `/api/admin/**` boundary (that is the exact #569 latent defect this contract fix closes).
  - An unexpected `tokenType` (≠ `"admin"`) is treated as fail-closed (operator cookie not set).
- **Resilience parity (§ 2.5)**: the exchange call uses the same `integration-heavy` discipline as the registry call — explicit hard timeout (AbortController), structured logging, no unbounded default — but the operator-boundary outcome is fail-closed (no partial authed state), distinct from the registry's degrade-the-section behaviour.
- **Tenant scope**: never derived from the IAM OIDC token. IAM resolves operator tenant scope producer-side from `admin_operators.tenant_id` (ADR-002 `'*'` platform sentinel); the console sends no tenant to the exchange (consistent with § 2.2 registry tenant scoping). Cross-references: IAM [`console-registry-api.md` § Authentication](../../../iam-platform/specs/contracts/http/console-registry-api.md) (operator token now via the exchange; producer requirement unchanged).

### 2.7 Active-Tenant Switcher → Assume-Tenant Exchange (normative — ADR-MONO-020 D4)

The active-tenant switcher re-scopes the operator's **domain-facing** credential to the selected customer. Setting the `console_active_tenant` cookie (X-Tenant-Id) alone does **nothing** — the federated domain entitlement gates (ADR-MONO-019 D5) trust the **signed** IAM OIDC token claims (`tenant_id` + `entitled_domains`), not a header. So on switcher selection the console **server-side** drives a second RFC 8693 exchange (the *assume-tenant* exchange, distinct from the § 2.6 operator exchange) to mint a short-lived IAM OIDC token re-scoped to the selected customer, and uses it as the domain-facing bearer. The BFF (§ 2.4.9) forwards it verbatim (ADR-MONO-017 D6 pass-through — **0-byte console-bff change**).

- **Two server-side exchanges (do NOT conflate)**:
  - **§ 2.6 operator-identity exchange** (ADR-MONO-014): admin **JSON** `POST /api/admin/auth/token-exchange` → operator token for `/api/admin/**`. Unchanged.
  - **assume-tenant exchange** (this section): SAS **form-urlencoded** `POST ${OIDC_ISSUER_URL}/oauth2/token`, `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` + `subject_token=<base IAM OIDC access token>` + `subject_token_type=urn:ietf:params:oauth:token-type:access_token` + `audience=<selected tenant>` + `client_id`. **Consume only** — the request/response/error contract is owned producer-side by IAM [`auth-api.md` § Assume-Tenant Exchange](../../../iam-platform/specs/contracts/http/auth-api.md) (TASK-BE-327); this file states the consumer obligation, it does NOT redefine the wire shape.
  - **Response 200** (SAS shape): `{ access_token, token_type: "Bearer", expires_in }` — **no `refresh_token`** (the assumed token is short-lived and re-minted per selection / IAM refresh). The console stores `access_token` in its own HttpOnly·Secure·SameSite=Lax cookie (`console_assumed_token`) with `maxAge = expires_in`, validates `token_type === "Bearer"`.

- **Domain-facing credential resolution (the central change)**: `getDomainFacingToken()` = **the assumed token if an active-tenant assumption exists, else the base `getAccessToken()`**. Every tenant-scoped domain read uses it for the IAM-OIDC bearer:
  - the cross-domain overview proxy (§ 2.4.9.1) `Authorization: Bearer` (the BFF's non-IAM fan-out legs forward it; the IAM leg keeps using `X-Operator-Token`, § 2.6, unchanged);
  - the 4 non-IAM domain section clients (`features/{wms,scm,finance,erp}-ops`, §§ 2.4.5–2.4.8) — the per-domain credential rule § 2.4.5 is unchanged, only **which** IAM OIDC token.
  - **IAM-domain clients** (`features/{accounts,audit,operators,dashboards}` → `getOperatorToken()`) are **unchanged** — the operator-token boundary (§ 2.1/§ 2.6, the #569 invariant) is untouched; `getDomainFacingToken()` is never a IAM `/api/admin/**` credential.
  - **net-zero**: a non-switched / single-tenant operator has no assumed token, so `getDomainFacingToken()` returns the base token → existing behaviour is byte-identical.

- **Switch route (`POST /api/tenant`)**: after the existing registry-membership allow-check (defence-in-depth — kept), call the assume-tenant exchange (subject = base token, audience = selected tenant); on success set BOTH `console_assumed_token` (maxAge = `expires_in`) and `console_active_tenant=<tenant>` **atomically**. The assumed token is scoped to the current active tenant **by construction** (both set/cleared together; never serve an assumed token for a tenant ≠ the active-tenant cookie).

- **Fail-closed switch** (mirrors the § 2.6 operator boundary — never degrade-with-fallback on the selected-tenant boundary):
  - assume-tenant `denied` (producer `400 invalid_grant`: the D2 assignment gate / subject invalid / the producer's admin-service leg unavailable) → `403 TENANT_FORBIDDEN`, **no cookie change** (the prior selection + assumed token are preserved).
  - `invalid` (producer `400 invalid_request`: blank/malformed audience) → `422`.
  - `unavailable` (5xx / timeout / network / unexpected shape) → `503 DOWNSTREAM_ERROR`.
  - missing base IAM token → `401 TOKEN_INVALID` (no exchange attempted).
  - The base IAM OIDC token is the `subject_token` only — **never logged, never returned**; the console **never** falls back to the base token on the selected-tenant boundary (the silent wrong-tenant-view defect this closes).

- **Clear path**: `tenant=''` deletes **BOTH** `console_active_tenant` and `console_assumed_token` (they are coupled).

- **Refresh re-assume**: the assumed token has no refresh token (the grant issues none). On IAM refresh (`/api/auth/refresh`), after the § 2.6 operator-token re-exchange, when an active tenant is set the console **re-assumes** it from the rotated base token. On re-assume failure it drops BOTH the assumed token and the active tenant (the operator falls back to base/no-tenant — never a stale assumed token); the base IAM + operator session stays valid. A whole-session drop (IAM refresh rejected / operator re-exchange fail-closed) also clears the assumed token + active tenant.

- **Selectable tenants**: the switcher's selectable set is the ConsoleRegistry effective scope (TASK-BE-326 dual-read: assignment rows ∪ legacy home tenant) — a multi-assignment operator surfaces all assigned customers. No console change is needed for that (BE-326 already wired it); D4 is purely the credential re-scope on selection.

---

## 3. IAM `admin-web` absorption — VERIFIED parity matrix (Phase 3 gate)

The console's IAM section must reach functional parity with the existing IAM
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
**exchanged operator token** (`getOperatorToken()`, never the IAM OIDC access
token — the #569 trust-boundary invariant) and sends `X-Tenant-Id` (active
tenant; blocked, never empty, when none selected) — attested for every row.

| # | admin-web operator capability | Console feature module | Contract § | IAM producer endpoint (`admin-api.md` §) | Kind | Mutation headers | Verified |
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
| 17 | operators: change-profile | `features/operators` `updateOwnProfile` | § 2.4.3 | `PATCH /api/admin/operators/me/profile` (admin-api § PATCH `me/profile`, **self only** — operator profile carrier; v1 = `operatorContext.defaultAccountId`) — TASK-BE-306 producer / TASK-PC-FE-016 consumer | M (self) | **no reason, no idem** (self path; valid operator token only — per the producer; mirrors row 15 `me/password`) | verified by TASK-PC-FE-016 |
| 18 | operators: admin-set-profile | `features/operators` `setOperatorProfile` | § 2.4.3 | `PATCH /api/admin/operators/{operatorId}/profile` (admin-api § PATCH `{operatorId}/profile`, **admin-on-behalf-of** — cross-operator counterpart of row 17; self via this path → producer 400 `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`) — TASK-BE-307 producer / TASK-PC-FE-017 consumer | M | **reason ONLY — `Idempotency-Key` MUST NOT be sent** (mirror rows 13 + 14 `{id}/roles` + `{id}/status` header non-uniformity); UI gates the per-row button when row is self (UX layer; producer is the authority) | verified by TASK-PC-FE-017 |

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

Retirement itself is a **separate IAM project-internal spec-first task** (IAM
`PROJECT.md` service map → `admin-web` row removed → app removal), explicitly
**out of scope here**. FE-006 only *satisfies the gate*; it does not retire
anything and touches no IAM code/spec. Merging FE-006 must **not** be read as
authorizing `admin-web` removal — that is a distinct IAM-internal change.

---

## 4. Out of Scope (this contract)

- Domain business logic / domain event contracts (each domain owns these).
- finance/erp domain contracts (governed by ADR-MONO-008 / ADR-MONO-016 — both ACCEPTED 2026-05-19).

---

## 5. Change Rule

Changes to the contract elements (§ 2) require updating this file **before** implementation, and — if they alter a deployed integration — an ADR per [`architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md). The skeleton → full transition (adding concrete per-domain endpoint schemas) is additive and tracked per domain task. The § 2.1/§ 2.6 operator-token-exchange element is governed by ADR-MONO-014 (ACCEPTED); the RFC 8693 request/response/error contract is owned producer-side by IAM `admin-api.md` — a change there is a IAM project-internal spec-first change cross-referenced here, not redefined here. The § 2.7 active-tenant switcher → assume-tenant flow is governed by ADR-MONO-020 (ACCEPTED) D4; the assume-tenant RFC 8693 request/response/error contract is owned producer-side by IAM `auth-api.md § Assume-Tenant Exchange` (TASK-BE-327) — consumed here, not redefined.
