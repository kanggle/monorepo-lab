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
| `productKey` | string | `iam` \| `wms` \| `scm` \| `erp` \| `finance` \| `ecommerce` \| `fan` (TASK-MONO-751 — platform operators only, § 2.4.11) |
| `displayName` | string | Catalog tile label |
| `available` | boolean | `false` → rendered as "coming soon"; reserved for future product additions (all 6 federated v1 domains — `iam` + `wms` + `scm` + `erp` + `finance` + `ecommerce` — are `available:true`; `ecommerce` added by TASK-MONO-240 2026-06-13 per ADR-MONO-030) |
| `tenants` | string[] | Tenant ids the operator may select for this product |
| `baseRoute` | string | Console-internal route prefix for the product's screens |
| `operatorContext` | `{ defaultAccountId?: string } \| undefined` | **TASK-BE-304 (producer) / TASK-PC-FE-014 (consumer)** — optional extensible per-operator per-product profile attributes carrier. **Omitted entirely** when no attribute is set (not rendered as `null`). v1: only the `finance` product item populates this (with `defaultAccountId` from IAM `admin_operators.finance_default_account_id`); the other 4 items always omit it. Authoritative producer shape + emission rule: [`iam-platform/specs/contracts/http/console-registry-api.md § Per-operator profile attributes`](../../../iam-platform/specs/contracts/http/console-registry-api.md). Consumer-side wiring (parser → session → dashboard proxy header) per § 2.4.9.1 Implementation guidance — Option (a) activation. |

- Flipping `available` / `displayName` / `tenants` of an **existing** catalog member is a **registry change only** — zero `console-web` code change (the catalog renders the dynamic product list verbatim; ADR-MONO-013 § 1.2 / D5). **Adding a NEW `productKey`, however, requires a one-line consumer-side `ProductKeySchema` Zod enum extension** in `console-web` (`src/shared/api/registry-types.ts`) — the fixed-membership guard asserted by `registry-contract.test.ts` "rejects unknown productKey". An unknown `productKey` makes `RegistryResponseSchema.parse` throw → the whole catalog renders `degraded`. So a new-domain catalog addition lands the producer item + this consumer enum in the **same atomic PR** (TASK-MONO-240 added `ecommerce`; ADR-MONO-030 § 6 factual correction; TASK-MONO-751 added `fan` the same way). Render is data-driven (0-change); membership is an explicit extension.
- **Subscription-driven `tenants` derivation (TASK-BE-322 / ADR-MONO-019 D2/D4 — envelope shape UNCHANGED, zero console-web change)**: each domain product's `tenants[]` is now derived producer-side from the **ACTIVE tenant↔domain subscriptions** IAM account-service owns (the D2 entitlement authority), instead of the prior fixed `tenantSlug == domain` binding. This is a **producer-internal derivation change only** — the response envelope, item shape, and field semantics are identical. In ADR-019 **step 1** the values are still the domain slugs (a backward-compatible self-subscription seed makes the output byte-identical to the pre-BE-322 catalog); real customer-tenant names surface in a later step (step 2) without any console-web change. `iam` continues to federate **all** registered tenants (it never consults the subscription surface).

### 2.3 Routing

- Each domain is reachable at its Traefik hostname (`iam.local`, `wms.local`, `scm.local`, … ; console at `console.local`).
- The console reaches domains **server-side** (server components / server routes), never via browser-direct calls that bypass the typed API client.
- **Catalog-tile drill-in routes (data-driven `baseRoute`)**: a catalog tile's click navigates to the registry item's `baseRoute` prefix; each domain that surfaces an operations section provides the matching `console-web` route under `(console)/<domain>/`. Existing: `/wms` (§ 2.4.5), `/scm` (§ 2.4.6), `/finance` (§ 2.4.7), `/erp` (§ 2.4.8), IAM screens (§§ 2.4.1–2.4.4). **`/ecommerce`** is added by TASK-MONO-241 (ADR-MONO-030 Step 4 facet a-후속) so the `ecommerce` catalog tile (`baseRoute=/ecommerce`, added by TASK-MONO-240) resolves to an existing route — a drill-in mirroring the scm/wms section degrade discipline (eligibility pre-flight on `productKey==='ecommerce'`; not-eligible / forbidden / ratelimited / degrade branches keep the console shell intact). The `/ecommerce` index (운영) surfaces the ecommerce **domain-health** summary as the parent landing; the **rich operations surface is delivered** — `features/ecommerce-ops` binds 7 operator areas (products/orders/users/promotions/shippings/notifications/sellers) as child routes under `/ecommerce/**` per **§ 2.4.10–§ 2.4.10.5** (TASK-PC-FE-081…090).

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
- **Role-based degrade to search-only, never the unfiltered-list 403 (TASK-PC-FE-326)**: a caller who lacks `account.read` but holds at least one of `account.lock`/`account.unlock`/`account.force_logout` (e.g. `SUPPORT_LOCK` — CS L2 — or `SECURITY_ANALYST`) MUST NOT even attempt the unfiltered-list call (it would only 403). The console pre-flight-checks the caller's roles (`GET /api/admin/me`, already promoted to `shared/api/iam-operators-read.ts` `getSelfRolesOrNull`) against the SAME `/accounts` permission set the sidebar gate uses, and when `account.read` is absent but at least one of the other three is held, renders an **email-search-only** screen instead: the same `searchAccounts` call, used with `email` only (`admin-api.md` — `email` needs no permission key, only the tenant-scope gate, TASK-BE-357) — no unfiltered list, no pagination. The held action (lock/unlock/revoke-session) then runs exactly as it does in the full-list screen; an action the caller's role does NOT hold still 403s at the producer (unchanged — the console never re-derives per-action authorization). A caller holding NONE of the four permissions keeps today's behaviour verbatim: hidden from the sidebar, and a direct URL still attempts the unfiltered list and renders the **권한 없음** state above. An unresolved/failed `GET /api/admin/me` fails OPEN to the full-list attempt (today's behaviour) — an unrelated outage must never newly degrade this screen.
- **Destructive-action UX (security UX, audit-heavy)**: lock/unlock/bulk-lock/revoke-session/gdpr-delete are each reason-gated **and** confirm-gated — the producer call MUST NOT fire until a non-empty operator reason is entered; `gdpr-delete` is irreversible → double-confirm + an explicit typed confirmation; `bulk-lock` is multi-select with per-account result rendering (no all-or-nothing implication). No silent/one-click destructive call.
- **gdpr-delete result scope (iam TASK-BE-619)**: the producer response carries `scope` (`ACCOUNT` | `SITE_MEMBERSHIP`, optional for an older producer) and `siteTenantId`; `maskedAt` is `null` for `SITE_MEMBERSHIP`. A site operator's request on a consumer-pool account (one IAM account shared by the fan and store sites) erases nothing — it ends only that site's membership (iam owner decision 2026-10-03). The console MUST say so after the call (a `role="status"` notice «… 사이트에서만 탈퇴 처리했습니다 …»), never let it read as «erased», and the confirm dialog states the rule up front. Only a platform-scope operator's request erases the pool account itself.
- **Security-event read of a site tenant (iam TASK-BE-619)**: a consumer-pool account's login / suspicious-activity events are recorded under the tenant `consumer-pool`, not the site, so a site-tenant `source=login_history|suspicious` list does NOT contain them. The console MUST say so on that view (a `role="note"` under the filters) — an empty list must not read as «no activity».
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
  | 2 | create | `POST /api/admin/operators` (body `tenantId`; `*`=platform-scope). 🔴 **TASK-MONO-772: platform-scope (`*`) only** — a company operator is no longer created here (producer `422 OPERATOR_INVITATION_REQUIRED` for a non-`*` tenant from 772 S7); the console offers this form **only** for `tenantId='*'` and only to a platform-scope operator (judged from `GET /api/admin/me`: `homeTenantId='*'` and `operator.manage` among `roles` — `TASK-PC-FE-354`; an absent `homeTenantId` or a failed `/me` hides it). Company operators come from rows 11–14 | mutation | `operator.manage` |
  | 3 | edit-roles | `PATCH /api/admin/operators/{operatorId}/roles` (full-replace; `[]` allowed) | mutation | `operator.manage` |
  | 4 | change-status | `PATCH /api/admin/operators/{operatorId}/status` (ACTIVE↔SUSPENDED) | mutation | `operator.manage` |
  | 5 | change-password | `PATCH /api/admin/operators/me/password` (**self only** — no admin-set-other) | mutation (self) | (valid operator token) |
  | 6 | change-profile | `PATCH /api/admin/operators/me/profile` (**self only** — operator profile carrier; v1 = `operatorContext.defaultAccountId`) — **TASK-PC-FE-016** | mutation (self) | (valid operator token) |
  | 7 | admin-set-profile | `PATCH /api/admin/operators/{operatorId}/profile` (**admin-on-behalf-of** — cross-operator counterpart of row 6; self via this path → `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`) — **TASK-PC-FE-017** | mutation | `operator.manage` |
  | 8 | assign-tenant | `POST /api/admin/operators/{operatorId}/assignments/{tenantId}` (create the `operator_tenant_assignment` row — "내 직원에게 내 테넌트 접근 부여"; target = active tenant; whole-tenant `org_scope=null` ⟺ `["*"]`, later refined via row 10 org-scope) — **TASK-PC-FE-157 / TASK-BE-347 (ADR-MONO-024 D3-i)** | mutation | `operator.manage` (target-tenant-confined) |
  | 9 | unassign-tenant | `DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}` (remove the assignment row; home-tenant-only operator → `404 ASSIGNMENT_NOT_FOUND`) — **TASK-PC-FE-157 / TASK-BE-347**. 🔴 **TASK-MONO-784: this becomes «이 회사에서 내보내기»** — the producer removes the whole company facet (that tenant's roles, group memberships, partnership participation) in one transaction; see the «한 사람 · 여러 회사» bullet below | mutation | `operator.manage` (target-tenant-confined) |
  | 10 | set-org-scope | `PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope` (per-assignment 데이터-스코프 tri-state) — **TASK-PC-FE-050 / TASK-BE-339** | mutation | `operator.manage` |
  | 11 | invite | `POST /api/admin/operator-invitations` (body `email` · `displayName` · `roles` · `tenantId` — never `*`) — **TASK-MONO-772** (`admin-api.md` § Operator Invitation) | mutation | `operator.manage` (target-tenant-confined; no-escalation) |
  | 12 | list pending invitations | `GET /api/admin/operator-invitations` (`tenantId` = active tenant, `status` default `PENDING`, `page`/`size`) — **TASK-MONO-772** | read | `operator.manage` |
  | 13 | cancel invitation | `POST /api/admin/operator-invitations/{invitationId}:cancel` — **TASK-MONO-772** | mutation | `operator.manage` |
  | 14 | resend invitation | `POST /api/admin/operator-invitations/{invitationId}:resend` (new token on the **same** invitation; the old link dies) — **TASK-MONO-772** | mutation | `operator.manage` |

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
  | `POST .../{id}/assignments/{tenantId}` (assign) | **required** | **MUST NOT send** | producer requires reason; NO key (the `(operator, tenant)` PK is the natural dedupe — a re-create is `409 ASSIGNMENT_ALREADY_EXISTS`); the console carries the reason in the same-origin body → `X-Operator-Reason` server-side — TASK-PC-FE-157 |
  | `DELETE .../{id}/assignments/{tenantId}` (unassign) | **required** | **MUST NOT send** | producer requires reason; NO key; DELETE carries the reason in the request body (→ `X-Operator-Reason` server-side) — TASK-PC-FE-157 |
  | `PUT .../{id}/assignments/{tenantId}/org-scope` | **required** | **MUST NOT send** | producer requires reason; NO key (idempotent full-replace PUT) — TASK-PC-FE-050 |
  | `POST /operator-invitations` (invite) | **required** | **required** (`crypto.randomUUID()`) | producer requires both — same as `POST /operators` create — TASK-MONO-772 |
  | `GET /operator-invitations` (list) | — | — | read only — TASK-MONO-772 |
  | `POST .../{invitationId}:cancel` | **required** | **MUST NOT send** | producer requires reason; NO key (cancel of an already-cancelled invitation is a 200 no-op) — TASK-MONO-772 |
  | `POST .../{invitationId}:resend` | **required** | **MUST NOT send** | producer requires reason; NO key (a retried resend just rotates the token again — only the newest mail's link works) — TASK-MONO-772 |

  A retried *confirmed* `create` reuses its `Idempotency-Key`; a fresh create attempt gets a new key. `roles`/`status` carry **no** key — adding one the producer omits is a header-matrix-drift defect (this slice's primary failure mode; pinned by an AC + a test).
- **`operator.manage` gating (saas S5 / audit-heavy A5)**: the mutating operations require `operator.manage`, granted to `SUPER_ADMIN` (platform-scope `'*'`) **and** `TENANT_ADMIN` (tenant-confined — ADR-MONO-024). The assign/unassign/create/edit-roles surfaces are **additionally target-tenant confined producer-side** (a `TENANT_ADMIN @ acme` may only administer acme; `403 TENANT_SCOPE_DENIED` otherwise), and role-grant obeys **no-escalation** (`403 ROLE_GRANT_FORBIDDEN` when granting a role whose permissions the actor lacks — e.g. `TENANT_BILLING_ADMIN` without `subscription.manage`). All of this is producer-authoritative; the console mirrors it for UX only and never re-derives it. When the operator is not a SUPER_ADMIN the producer returns `403 PERMISSION_DENIED`; the console renders the whole operators section as an inline "not permitted" state (and SHOULD gate the `/operators` nav entry when derivable) — never a crash, never a re-login loop. The console always still handles the server `403` defensively.
- **Mutation audit (§ 2.4 / audit-heavy / saas S5)**: every mutating action (create / edit-roles / change-status / change-password) is **reason-gated and confirm-gated** — the producer call MUST NOT fire until a non-empty operator reason is entered (producer `400 REASON_REQUIRED` if missing on the reason-bearing endpoints). Privilege-high actions — **creating an operator, granting `SUPER_ADMIN`, suspending an operator, removing all roles (`[]`)** — carry explicit **elevated confirm copy**. No silent / one-click create / role-grant / suspend.
- **Password safety (security-rules / saas S1)**: `create` and self `change-password` accept a plaintext password server-side only. The console **client-side mirrors the producer password policy** as a UX pre-check (≥10 chars, ≥1 letter + ≥1 digit + ≥1 special — pre-validates before submit; the producer is the final authority). A password is **never** logged, never echoed into structured logs / events / state beyond the input field, never placed in a query string, and is cleared from memory on submit where practical. There is **no admin-set-other-password endpoint** in the parity line — change-password is exclusively the logged-in operator's own (`/me/`); the console does not invent one.
- **«등록» → «초대» (TASK-MONO-772 — ADR-MONO-080 D6, owner decisions OD-3 · OD-4; console slice 772 S5)**: the operators screen's company-operator action is **«초대»**, not «등록».
  - **Invite form** (row 11): email · display name · roles (from `GET /api/admin/operators/grantable-roles`, as today) · tenant = the **active tenant** (never `*` — the platform scope is not offered here; a platform-scope operator creating another platform operator still uses row 2). Reason-gated and confirm-gated like create; elevated confirm copy when the roles include `TENANT_ADMIN`/`SUPER_ADMIN`-class roles is unchanged. The confirm copy says what happens next: «초대 메일이 {email}로 갑니다. 그 주소를 **인증한** 개인(IAM) 계정으로 로그인해 수락하면 운영자가 됩니다(7일 안에).»
  - **Pending invitations list** (row 12) on the same screen, active-tenant scoped: email · roles · invited by · expires (an `expired=true` row reads «만료됨» and offers only resend/cancel) · **delivery status** (`delivery.status`): `SENT` → «보냄», `FAILED_TRANSIENT` → «보내지 못함 — 다시 보내기», `FAILED_PERMANENT` → «이 주소로는 보낼 수 없음» (resend still offered; the copy does not promise it will work). An invite/resend response with `FAILED_*` is shown **inline as a failure of delivery, not of the invitation** (OD-4 — «발송 실패는 화면에»): the invitation exists and is listed.
  - **Cancel** (row 13) and **resend** (row 14) per row, reason-gated + confirm-gated. Resend confirm copy: «새 링크를 보냅니다 — 전에 보낸 링크는 더 이상 쓸 수 없습니다.»
  - 🔴 **The console never shows, stores or logs an invitation token or link** — the producer never returns one (OD-4: delivery is by mail only). There is no «링크 복사».
  - The accept page is **not** a console page — it lives on the IdP (`auth-api.md` § IdP 브라우저 화면 — 운영자 초대 수락). The console has no invitation-accept route in 772.
  - Errors (inline, no crash): `409 OPERATOR_INVITATION_ALREADY_PENDING` → «이미 대기 중인 초대가 있습니다» + jump to that row's resend · `409 OPERATOR_EMAIL_CONFLICT` → «이미 이 테넌트의 운영자입니다» · `409 OPERATOR_INVITATION_NOT_PENDING` → refresh the list (it was accepted/cancelled meanwhile) · `404 OPERATOR_INVITATION_NOT_FOUND` → refresh the list · `404 TENANT_NOT_FOUND` · `403 TENANT_SCOPE_DENIED` / `ROLE_GRANT_FORBIDDEN` / `PERMISSION_DENIED` · `400 ROLE_NOT_FOUND` / `VALIDATION_ERROR` / `REASON_REQUIRED` as today; `503`/timeout → only the invitations section degrades.
  - The console sends `X-Tenant-Id` (active tenant) on all four calls as on every operators call; the producer judges invite/list scope from the body/query `tenantId`, and cancel/resend from the invitation's own tenant.
- **«한 사람 · 여러 회사» (TASK-MONO-784 — ADR-MONO-082 b, owner decisions OD-1 · OD-2 · OD-3; console slice 784 S6, after the producer slices S3 · S4 are on `main`)**: one operator row can now be a member of several companies, with **per-company roles** (`admin-api.md` § 행동 테넌트 is canonical). Consumer obligations:
  - **Edit-roles (row 3) is per company.** The producer replaces only the **active tenant's** direct roles (`X-Tenant-Id`, already sent) and leaves the operator's other companies untouched. The dialog says so («이 회사({tenant})에서의 역할») and shows only the active tenant's roles — the list response's `roles` is already scoped to the queried tenant. `roles: []` copy changes from «역할 전부 제거» to «이 회사의 역할 전부 제거 — 소속은 남습니다». New producer outcomes rendered inline: `404 OPERATOR_NOT_FOUND` (the operator is not a member of the active tenant — refresh the list) · `400 VALIDATION_ERROR`.
  - **Grant menu per company**: `GET /api/admin/operators/grantable-roles` is answered for the active tenant (`X-Tenant-Id`, already sent by the BFF route) — the menu can differ between companies; the console never caches it across a tenant switch.
  - **«이 회사에서 내보내기» (row 9)**: the per-row action label changes from «배정 해제» to «이 회사에서 내보내기». Elevated confirm copy: «{name}님을 {tenant}에서 내보냅니다 — 이 회사의 역할 · 그룹 · 협력사 참여가 함께 사라집니다. 다른 회사의 소속과 개인 계정(스토어 · 팬 로그인)은 그대로입니다.» Reason-gated as today (header matrix unchanged: reason required, no `Idempotency-Key`).
  - **Change-status / admin-set-profile / identity link (rows 4 · 7)** are **row-wide**: a company administrator gets `403 OPERATOR_HAS_OTHER_MEMBERSHIPS` when the target is a member of another company too → inline «다른 회사에도 소속된 운영자입니다 — 정지 대신 ‘이 회사에서 내보내기’를 쓰세요» with a jump to the row-9 action. Never a crash, never a re-login. The suspend confirm copy for a platform-scope (`'*'`) actor says the suspension applies to **every** company.
  - **Nav exposure follows the active company**: `GET /api/admin/me` `roles` is answered for the `X-Tenant-Id` sent, and a new additive `memberships[{tenantId, roles[]}]` lists every company; the nav exposure (`console-nav-exposure`) reads the active tenant's roles — an operator who is `TENANT_ADMIN` in A and has no role in B does not see the operators menu in B (and the producer would `403` it anyway — rider R2). `homeTenantId` is shown, if at all, as «처음 소속된 회사», never as «소속».
    - **Domain menus are a different axis (ADR-MONO-083 D9 — TASK-MONO-791)**: the roles above are **IAM admin roles** and hide only `admin`-gated items. Domain items are never hidden by them; they carry two separate badges — «구독 필요» (registry) and «업무 권한 없음» (`GET /api/admin/me/domain-access`, P4) — defined in § 2.4.3.4 «Domain nav — two signals».
  - **Group «멤버 추가» picker**: every operator in the active tenant's list is a candidate (membership, not home, is the producer predicate) — the TASK-PC-FE-319 «배정만 됨» greying by `homeTenantId` is removed.
  - **Omitted-tenant `400 VALIDATION_ERROR`**: the console always sends the active tenant (§ 2.4), so the producer's «tenant required for a multi-company operator» refusal is unreachable from the console; if seen, it is a client defect — log it and render the section's generic error.
  - **Invite (row 11) to someone who already operates another company** now succeeds on acceptance (the same operator row gains this company); `409 OPERATOR_EMAIL_CONFLICT` at issue time now means «already a member of **this** company». No console copy change for the invite form.
- **Account-existence pre-gate — RETIRED by TASK-MONO-772** (removed in 772 S5 together with the non-`*` create path; the producer's matching check goes in 772 S7). The bullet below is the pre-772 record. ~~**Account-existence pre-gate (TASK-MONO-334 — supersedes the TASK-PC-FE-179 advisory)**~~: `create` may target **only an email that already owns a signed-up account in the selected tenant** (the operator's PRIMARY login is that account's unified IAM credential). The console pre-flight-probes existence via the same-origin accounts-search BFF and **gates submit** — the create button is enabled only when the email definitively **exists** (a definitively-absent email shows a blocking error; an unavailable/unknown probe keeps submit disabled — fail-closed). A break-glass `password` does **not** bypass this (it is only a secondary login for an operator whose account exists). **`tenantId='*'` (platform-scope) is exempt** — there is no tenant account to probe (SUPER_ADMIN bootstrap). This is a UX pre-gate; the producer's **`422 OPERATOR_ACCOUNT_NOT_FOUND`** (and `503 DOWNSTREAM_ERROR` when account-service is unavailable) stays the final authority — the console renders it inline (no crash).
- **Role tolerance**: role names are the producer's enum (`SUPER_ADMIN`/`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`/`SUPPORT_LOCK`/`SUPPORT_READONLY`/`SECURITY_ANALYST`/…). The list view tolerates an unknown/future role (a generic chip, never a crash); the create / edit-roles selectors offer the known enum — **including the two tenant-scoped delegation roles (`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`, TASK-PC-FE-157)** so a SUPER_ADMIN can appoint a tenant admin through the UI (the first step of the ADR-MONO-024 delegation chain). No-escalation stays producer-enforced (`403 ROLE_GRANT_FORBIDDEN`), and a stale `400 ROLE_NOT_FOUND` is handled inline (refreshing the client-cached role source).
- **Resilience (§ 2.5)**: the operators section reuses the registry/accounts/audit-client `integration-heavy` discipline (AbortController hard timeout, structured logging, no unbounded default). `401`/`TOKEN_INVALID` → forced re-login (no partial authed state); `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` / `409 OPERATOR_EMAIL_CONFLICT` / `422 OPERATOR_ACCOUNT_NOT_FOUND` (TASK-MONO-334 — email has no tenant account; pre-772-S7 only) / `422 OPERATOR_INVITATION_REQUIRED` (TASK-MONO-772 — a non-`*` create; the console does not offer it, so seeing it means a stale form → point to «초대») / the invitation codes listed in the «초대» bullet above / `400 ROLE_NOT_FOUND`/`VALIDATION_ERROR`/`STATE_TRANSITION_INVALID`/`SELF_SUSPEND_FORBIDDEN`/`CURRENT_PASSWORD_MISMATCH`/`PASSWORD_POLICY_VIOLATION` / `404 OPERATOR_NOT_FOUND` → inline field-level / actionable (no crash); `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` / timeout → **only the operators section degrades** (the console shell stays intact).
- **Logging**: structured server-side logs only; operator/IAM tokens, operator emails, and passwords are never logged (redacted) — § 2.6 logging invariant extended to the operators surface (passwords never logged or echoed at all).
- **Producer immutability**: this is a **cross-reference only**. Any change to the operators producer contract is a IAM project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

> **§ 3 parity line satisfiable**: with `features/operators` bound here, the § 3 "operators: create, edit-roles, change-status, change-password" parity line is **satisfiable**; `FE-006` formally verifies it (ADR-MONO-013 Phase 3 admin-web-retirement gate).

#### 2.4.3.1 IAM domain **overview snapshot** — `/iam` landing (TASK-PC-FE-180 — splits the live overview from the static guide)

The `/iam` landing (formerly the static RBAC guide of TASK-PC-FE-163) is split: the static guide relocates to `/iam/guide` (unchanged content), and `/iam` becomes a **live domain overview snapshot** — the IAM sibling of the ecommerce § 2.4.10.6 snapshot. It reuses the three IAM admin surfaces' existing consumed **list** endpoints (§ 2.4.1 accounts / § 2.4.2 audit / § 2.4.3 operators) — **no new producer endpoint, no producer retrofit, no § 2.4.9 composition leg**.

- **Read model (console-web DIRECT fan-out).** Unlike the console-wide operator overview (§ 2.4.4 / § 2.4.9.1, the bounded cross-domain composition), this is a **domain-internal** snapshot: it runs server-side in the console-web landing over the three IAM list endpoints already consumed, each authenticated with the **exchanged operator token** (`getOperatorToken()`, § 2.1/§ 2.6 trust boundary — never the IAM OIDC token) and scoped to the active tenant (§ 2.4).
- **Counts.** `GET /api/admin/operators?page=0&size=1`.`totalElements` (총계) + the `status=ACTIVE` / `status=SUSPENDED` filtered totals (split); `GET /api/admin/accounts?page=0&size=1`.`totalElements` (계정 총계) + `?status=LOCKED&page=0&size=1`.`totalElements` (잠금 현황 — TASK-PC-FE-181, via the TASK-BE-475 producer `status` filter; its own sub-leg so a degrade never blanks the total); `GET /api/admin/audit?page=0&size=5` → `totalElements` (스코프 내 총 이벤트) + `content` (recent 5 rows).
- **Ratio bars (TASK-PC-FE-362) — zero new reads.** The 운영자 card draws a 활성 · 정지 stacked bar and the 계정 card a 잠금 / 전체 bar from the numbers above, which are all **full-population** `totalElements` of status-filtered `size=1` reads (never a first page or a bounded slice). Rules: the 운영자 bar is drawn only when total, 활성 and 정지 are all `ok` and total > 0 (the whole is the 총계; any remainder — operators in neither status — leaves the track empty, an overshoot scales the bar to the sum); the 계정 bar only when total and 잠금 are `ok` and total > 0 (잠금 / 총계). Any `null` / non-`ok` number → no bar (numbers still print). Account statuses other than `LOCKED` are not read, so the 계정 bar is «잠금 vs 나머지», never an active/dormant split.
- **No aggregation endpoint (ADR-MONO-017 D3.B).** There is deliberately **no** producer `/summary` endpoint; deriving counts from `totalElements` is the sealed pattern (same discipline as § 2.4.10.6). Re-introducing an aggregation endpoint for this snapshot is a contract defect. (The «실적» strip below is **not** such an endpoint: it is the ADR-MONO-084 performance read — period-windowed event counts the producer aggregates by D4 — and it replaces none of the card counts above.)
- **Per-cell status.** Derived from each fan-out cell's outcome — `ok` / `403 → 권한 없음` / `503|timeout|network → 점검 필요`. A `403` (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED`) is a **normal forbidden cell for a narrow role** (the rbac.md access matrix — e.g. `SUPPORT_LOCK` lacks `account.read`, `SECURITY_ANALYST` lacks `operator.manage`), NOT an outage. NB the IAM error taxonomy differs from ecommerce's single `ApiError`: 503/timeout surface as the per-surface `OperatorsUnavailableError` / `AccountsUnavailableError` / `AuditUnavailableError`, so the cell resolver keys off both those and `ApiError`.
- **Tenant gate.** All three legs require an active tenant (the api fns raise `400 NO_ACTIVE_TENANT` before fetch). The snapshot short-circuits to ONE page-level tenant-gate note (not three duplicate per-cell messages), mirroring the sibling IAM screens.
- **Resilience (§ 2.5).** Per-cell degrade is cell-local (one leg's failure never blanks the snapshot); a `401` in ANY leg triggers a whole-session `redirect('/login')` (no partial authed state). Read-only; no auto-refetch.
- **Performance metrics strip — ADR-MONO-084 P6 / TASK-MONO-811.**
  `GET /api/admin/metrics?period=week|month&tenantId=<active tenant>` on admin-service (the same `callAdminGateway` core as the § 2.4.2 audit read: exchanged operator token, `X-Tenant-Id`, and — like the audit read — the active tenant **also** as the `tenantId` query param, which is what admin-service scopes this read by) feeds the «실적» strip, the **first** block of the snapshot: 로그인 성공 (`loginSuccess`) · 로그인 실패 (`loginFailure`) · 계정 잠금 (`accountLocked`) · 신규 계정 (`newAccount`), all `unit=count`, in that order, current vs the same-length previous KST window, with a daily series (the chart draws `loginSuccess`). Meaning per key is the producer's (`iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/metrics`); the strip carries a one-line scope note, verbatim:
  «로그인 성공 · 실패와 계정 잠금에는 소비자 풀 계정이 빠집니다(그 기록은 이 사이트가 아니라 풀에 남습니다). 신규 계정에는 이 사이트로 가입한 소비자가 포함됩니다.»
  Shape, windows and error handling are
  [`platform/contracts/performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md); the body is the bare shape. Parsed by `shared/api/performance-metrics.ts` (integer strings — F5) as its **own cell** inside `getIamOverviewState`: `403` (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED`) → «권한 없음» in the strip, `5xx` / timeout / any other failure or an unparseable body → «실적을 불러올 수 없습니다» (never a `0`), while the cards and the recent audit list render; a `401` → the same whole-session re-login; no active tenant → the page-level tenant gate and no call. The page reads `?period=` (default `month`; absent or unknown → the default, never a 400 page). The cards are **not** links (no list filters by these event times). Only aggregate numbers cross to the console — no account id, e-mail or IP.
  - **Sample mode.** The `iam:iam_metrics` fixture answers `/api/admin/metrics` from the same seed rows the 계정 and 감사 screens show: 로그인 성공 / 실패 = `login_history` audit rows by outcome, 계정 잠금 = `ACCOUNT_LOCK` admin audit rows, 신규 계정 = accounts by `createdAt` (every seed account predates the sample windows, so it is honestly `0`).

> **Not a § 3 parity row**: consumes only already-listed § 2.4.1/.2/.3 endpoints in a new read composition, plus the ADR-MONO-084 performance read above (an overview-only read, not an admin-web parity function); adds no § 3 row and changes none (count stays **16**). The static guide's relocation to `/iam/guide` is a nav/route change only.

#### 2.4.3.2 IAM RBAC catalog surface — 「권한」 / «IAM 관리 역할» (formerly 「권한 세트」) (TASK-BE-486 producer / TASK-PC-FE-227 + TASK-PC-FE-228 — cross-reference, not a redefinition)

> 🔴 **Renamed by ADR-MONO-083 D9 (TASK-MONO-791 contract; console slice P4 `TASK-PC-FE-338`)**: the screen this section calls 「권한 세트」 (`/permission-sets`) shows **IAM administrative roles** (`admin_roles` — bundles of admin-plane permission keys), **not** business permissions in any domain. Its visible name becomes **«IAM 관리 역할»** (nav label, page title, and any copy that says «권한 세트» for this view). The route may stay `/permission-sets` or move — the name is the obligation, the path is P4's choice. The tenant-scoped **domain business permission bundles** that operators and groups receive are a separate entity with a separate producer and a separate screen — **«업무 권한 세트»**, § 2.4.3.4. The two must never share a label: an administrator reading «권한 세트» must not believe an IAM admin role grants WMS/SCM work, or vice versa (ADR-MONO-035 O1-C — separate namespaces). Everywhere below, «「권한 세트」» refers to this IAM-admin-role view under its old name.

TASK-PC-FE-225's orthodox IAM-taxonomy nav split introduced two workforce-plane sub-surfaces alongside § 2.4.3 operators: 「권한」 (Action/Permission equivalent) and 「권한 세트」 (IAM Policy/Role equivalent). TASK-BE-486 added exactly the **two read endpoints** both consume; TASK-PC-FE-227 renders the role→permission-key catalog, and TASK-PC-FE-228 **reframes the SAME role catalog** as "permission sets" (`permission_set_id` — the `operator_tenant_assignment` per-assignment role-set narrowing field, ADR-MONO-020 § D5 — physically **is** `admin_roles.id`; no new backend entity). Both features consume ONE shared client, `shared/api/rbac-catalog.ts` (architecture.md § Forbidden Dependencies bars `features/A → features/B`, so the shared value is promoted to `shared/`, exactly as `shared/api/iam-gateway.ts` already centralises the HTTP core). The producer contract is **authoritative and unchanged** — this section only states the consumer obligation and points at the owning IAM spec.

- **Authoritative producer (owned by IAM, do NOT redefine here)**: [`iam-platform/specs/contracts/http/admin-api.md`](../../../iam-platform/specs/contracts/http/admin-api.md). The console consumes exactly these endpoints (request/response/error tables are canonical there):

  | # | Operation | Producer endpoint (`admin-api.md` §) | Kind | Required permission | Consumed by |
  |---|---|---|---|---|---|
  | 1 | role catalog | `GET /api/admin/roles` (all `admin_roles` rows + each role's permission-key set, `admin_roles.id` ASC, `permissions[]` sorted) | read | `operator.manage` | TASK-PC-FE-227 (role view) + TASK-PC-FE-228 (permission-set view — same response, reframed) |
  | 2 | permission-key catalog | `GET /api/admin/permissions` (canonical permission-key catalog from `Permission.catalog()` — includes keys not yet granted to any role) | read | `operator.manage` | TASK-PC-FE-227 only |

  There is **no** `GET /api/admin/permission-sets` endpoint — BE-486 deliberately omitted it as a second framing of the same `admin_roles` table (a duplicate producer surface); TASK-PC-FE-228 reuses row 1 verbatim.

- **Auth (§ 2.1/§ 2.6 trust-boundary invariant)**: every call carries `Authorization: Bearer <operator token>` (`getOperatorToken()`), **never** the IAM OIDC access token. Absent ⇒ `401 TOKEN_INVALID` → forced re-login (the #569 invariant, unchanged).
- **Tenant scope**: the console always sends the active tenant as `X-Tenant-Id` (`getActiveTenant()`), even though `scope: "global"` on both responses means the catalog data itself is tenant-independent — `admin-api.md` documents no tenant-scope exemption for this surface, so the standard multi-tenant header discipline (never an empty/absent `X-Tenant-Id`; a "select a tenant" gate when none is selected) applies verbatim.
- **`scope` display obligation (task Edge Case)**: both responses carry `scope: "global"`. The console reflects this value **verbatim** (never a hard-assumed literal) so a role catalog is never visually mislabelled as tenant-scoped — `RolesResponseSchema`/`PermissionsResponseSchema` parse `scope` as `z.string()`, not a closed `z.literal('global')`, precisely so a future producer value renders defensively instead of hard-crashing the parse.
- **`operator.manage` gating (saas S5)**: both reads require `operator.manage` — the SAME decision as the sibling `GET /api/admin/operators/grantable-roles` (BE-486 introduced no new `role.read`/`permission.read` key). A non-privileged operator gets producer `403 PERMISSION_DENIED`; the console renders the whole 「권한」/「권한 세트」 section as an inline "not permitted" state — never a crash, never a re-login loop.
- **READ-ONLY (v1, task Out of Scope)**: NO `X-Operator-Reason`, NO `Idempotency-Key`, no mutation scaffolding at all — role/permission definitions change ONLY via seed + Flyway (same constraint as the sibling grantable-roles read). No edit/create/delete affordance renders (absence conveys the read-only posture).
- **Resilience (§ 2.5)**: AbortController hard timeout (`RBAC_TIMEOUT_MS`, shared by both endpoints via the one client); `401` → forced re-login (no partial authed state); `403 PERMISSION_DENIED` → inline "not permitted"; `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN`/timeout → `RbacUnavailableError` — **only** the 「권한」/「권한 세트」 sections degrade (the console shell + every other IAM section stay intact).
- **Assignment usage count (TASK-PC-FE-228 task AC, deferred)**: neither endpoint returns a per-role/per-set assignment usage count. 「권한 세트」 renders "—" for this figure rather than computing it via an N+1 fan-out over `operator_tenant_assignment` rows (task Failure Scenario — explicitly disallowed); surfacing a real count is a follow-up that needs either a BE-486 extension or a dedicated aggregation endpoint.
- **`permission_set_id` NULL semantics (task Edge Case)**: 「권한 세트」 carries an explanatory legend distinct from any data row — when an assignment's `permissionSetId` is absent (§ 2.4.3 `GET /api/admin/operators/{operatorId}/assignments` `NON_NULL`-omit semantics), the assignment is **not** narrowed to any set and inherits the operator's own role verbatim ("operator-level 상속"). This legend keeps the two screens' vocabulary aligned; it does not fetch or display assignment rows itself (that stays on `/operators`).
- **`KNOWN_OPERATOR_ROLES` absorption decision (TASK-PC-FE-227 task In-Scope, DE-RISK finding)**: the pre-existing `features/operators/api/types.ts` `KNOWN_OPERATOR_ROLES` static role-name list (grant-role checkbox pre-filter/fallback for `CreateOperatorForm`/`OperatorConfirmDialog`, pinned by 3 existing test files) and the `features/iam-guide` static `SEED_ROLES`/`PERMISSION_KEYS` reference data (a hand-authored screen×role access matrix + Korean descriptions that BE-486 cannot derive) are **both left unchanged** — removing either would break an existing, still-load-bearing consumer for no functional gain. 「권한」/「권한 세트」 are **additive** live-API screens; they do not replace either static surface.
- **Producer immutability**: this is a **cross-reference only**. Any change to the RBAC-catalog producer contract is an IAM project-internal spec-first change in `admin-api.md`; this section follows it, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: TASK-BE-486 is additive to the already-verified § 2.4.3 operators parity line (a new READ pair, `operator.manage`-gated like the sibling grantable-roles read); it adds no § 3 row and changes none (count stays **16**).

#### 2.4.3.3 IAM tenant entry policy surface — «2단계 인증 필수» toggle (TASK-MONO-771 / ADR-MONO-080 D4 · R2, owner decision OD-1 — cross-reference, not a redefinition)

- **Authoritative producer (owned by IAM, do NOT redefine here)**: [`iam-platform/specs/contracts/http/admin-api.md` § Tenant Entry Policy](../../../iam-platform/specs/contracts/http/admin-api.md) — `GET` / `PUT /api/admin/tenants/{tenantId}/entry-policy`, permission `tenant.security.manage` (`SUPER_ADMIN` any tenant · `TENANT_ADMIN` its own grant's tenant only).
- **Two places, one producer (OD-1)**:
  1. **Tenant detail** (`(console)/tenants/[tenantId]`, the `SUPER_ADMIN` screen) — a «운영자 진입 2단계 인증» row with the toggle.
  2. **«보안 설정»** — a new `(console)` page for a `TENANT_ADMIN`, scoped to the **active tenant** (`X-Tenant-Id` = active tenant; path `tenantId` = the same value). Shown in the nav only when the operator's claims show `tenant.security.manage`; the producer `403` stays the authority.
- **Headers**: `GET` — operator token + `X-Tenant-Id`, no mutation headers. `PUT` — `X-Operator-Reason` **required**; **`Idempotency-Key` MUST NOT be sent** (idempotent full-replace PUT — same non-uniformity as the org-scope PUT in § 2.4.3).
- **UX obligations**: the toggle is reason-gated and confirm-gated. Turning it **on** carries elevated confirm copy stating the transition rule (owner decision OD-4 — no grace period): «켜면 이 테넌트에 운영자로 들어올 때 2단계 인증이 필요합니다. 아직 등록하지 않은 운영자는 다음 진입 때 IAM 의 등록 화면으로 안내됩니다. 이미 열린 세션은 다음 갱신 때 적용됩니다.» Copy MUST NOT say operators will be locked out — they are routed to enrolment (§ 2.6 step-up). Wording that names the two second factors keeps them apart: the IAM account second factor is the primary path; the admin break-glass second factor is not involved.
- **Self-effect**: a `TENANT_ADMIN` who turns the policy on without a second factor will be refused at the next exchange/assume and routed to step-up — the console does not block the toggle for that, but the confirm copy says so when the current session's IAM token lacks `mfa` (derivable server-side from the session's IAM token).
- **Resilience / errors**: `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` → inline «권한 없음»; `400 REASON_REQUIRED` / `VALIDATION_ERROR` → inline; `404 TENANT_NOT_FOUND` → inline; `503 DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` / timeout → only this control degrades (shell intact); `401` → forced re-login (§ 2.6).
- **Pre-check (S5 — producer now defined)**: before the «켜기» confirm, the console reads [`GET /api/admin/tenants/{tenantId}/entry-policy/enrolment-summary`](../../../iam-platform/specs/contracts/http/admin-api.md) (same key, same scope) and the confirm copy names the count: «이 테넌트 운영자 중 2단계 미등록 **N**명은 다음 진입 때 등록 화면으로 안내됩니다.» The read is **advisory**: on any failure (`503`, timeout, `403`) the confirm shows the copy **without** a number — it never shows a made-up `0` and never blocks the toggle. Turning **off** needs no pre-check.
- **Console surfaces (S5 implementation)**: BFF `GET|PUT /api/tenants/{tenantId}/entry-policy` + `GET /api/tenants/{tenantId}/entry-policy/enrolment-summary` (same-origin; the reason travels in the body and leaves as `X-Operator-Reason`, no `Idempotency-Key`). «보안 설정» = route `/security-settings` in the sidebar's «조직 설정» group (a company-level setting next to 도메인 구독 · 파트너십), nav-hidden for roles whose seed matrix lacks `tenant.security.manage` (the same role-exposure gate as every other `admin`-gated item); with the platform scope `*` active it points the operator to 테넌트 상세 instead (`'*'` cannot hold a policy).
- **Producer immutability**: cross-reference only (§ 5 Change Rule).

#### 2.4.3.4 «업무 권한 세트» — tenant domain business permission sets (ADR-MONO-083 D9 · TASK-MONO-791 contract — producer P2 `TASK-BE-631`, token effect P3 `TASK-BE-632`, console P4 `TASK-PC-FE-338` — cross-reference, not a redefinition)

- **Authoritative producer (owned by IAM, do NOT redefine here)**: [`iam-platform/specs/contracts/http/admin-api.md` § 도메인 업무 권한 세트](../../../iam-platform/specs/contracts/http/admin-api.md) — request/response/error tables, audit codes, the source/revoke rules and the catalog are canonical there. The console consumes exactly:

  | # | Operation | Producer endpoint | Permission | `X-Operator-Reason` | `Idempotency-Key` |
  |---|---|---|---|---|---|
  | 1 | role catalog | `GET /api/admin/domain-roles` | `domain_access.manage` | — | — |
  | 2 | list / get sets | `GET /api/admin/domain-permission-sets` · `GET …/{setId}` | `domain_access.manage` | — | — |
  | 3 | create set | `POST /api/admin/domain-permission-sets` | `domain_access.manage` | **required** | **required** |
  | 4 | edit set | `PATCH …/{setId}` | `domain_access.manage` | **required** | **MUST NOT send** |
  | 5 | delete set | `DELETE …/{setId}` | `domain_access.manage` | **required** | **MUST NOT send** |
  | 6 | operator business access | `GET /api/admin/operators/{operatorId}/domain-access` | `domain_access.manage` | — | — |
  | 7 | grant set to operator | `POST /api/admin/operators/{operatorId}/domain-permission-sets` | `domain_access.manage` | **required** | **MUST NOT send** (a repeat is `409`) |
  | 8 | personal revoke | `DELETE /api/admin/operators/{operatorId}/domain-permission-sets/{setId}` | `domain_access.manage` | **required** | **MUST NOT send** |
  | 9 | group sets | `GET /api/admin/groups/{groupId}/domain-permission-sets` | `domain_access.manage` | — | — |
  | 10 | grant set to group | `POST …/groups/{groupId}/domain-permission-sets` | `domain_access.manage` | **required** | **required** (same as the group grant surface) |
  | 11 | revoke set from group | `DELETE …/groups/{groupId}/domain-permission-sets/{setId}` | `domain_access.manage` | **required** | **MUST NOT send** |
  | 12 | my business access (nav) | `GET /api/admin/me/domain-access` | none (self) | — | — |

  Every call carries the operator token (§ 2.1/§ 2.6) and `X-Tenant-Id` = the active tenant (row 12 requires it) — **one exception** (`TASK-PC-FE-354`): rows 7–8 on a platform-scope (`'*'`) target, issued by a platform-scope actor, send `X-Tenant-Id: *` when they address the target's «모든 테넌트» row (see screen 2's platform-target bullet below). None of these is an IAM OIDC / domain-facing call.
- 🔴 **Status honesty**: until P3 is deployed the assumed token's `roles` are still **subscription-derived** (every operator of a tenant gets the same domain roles) — P2 alone changes no token. Any console copy that says a set «limits» what an operator can do is only true from P3; P4 ships after P3, or its copy says «적용 예정» until then. **P3 is deployed** (`TASK-BE-632` — live from the 27th demo window, 2026-10-14): the assumed token's `roles` = assigned sets ∩ the tenant's subscribed domains. The «적용 예정» note is therefore **retired** (`TASK-PC-FE-338` c-2 PR 1). The three set surfaces (screens 1–3 below) instead carry a persistent effect note: «운영자가 테넌트를 고르면, 배정된 세트의 역할 중 그 테넌트가 구독한 도메인의 역할만 도메인 토큰에 실립니다.»
- **Screens (D9)**:
  1. **«업무 권한 세트»** (a new IAM screen, nav label exactly «업무 권한 세트», never «권한 세트»): presets first (badge «시스템» · read-only — no edit/delete buttons; offer «복제해서 새 세트» which pre-fills the create form with the same roles), then the active tenant's sets with `grantCount` / `groupCount`. Create/edit form: the role picker comes from row 1 grouped by `domainKey`; an entry with `fineGrained=false` is labelled with its producer `label` («… 전체 업무(세분 미지원)») and is never split or described as narrower than it is (ADR-083 D3). Domains the active tenant has not subscribed to are shown disabled with «구독 없음» (the producer's `422 DOMAIN_NOT_SUBSCRIBED` stays final).
  2. **Operator detail → «업무 권한»** (row 6): per set, its **sources** — «직접» (`DIRECT`) · «이전 호환» (`MIGRATION`) · «그룹: {groupName}» (`GROUP`) · and for a platform (`'*'`) operator whether the row applies to «모든 테넌트» (`tenantId='*'`); then the summed effective roles grouped by domain, with roles of a domain the active tenant has not subscribed to marked «구독 없음 — 토큰에 실리지 않음» (the producer value is pre-subscription by contract). **The revoke button appears only on sets that have a `DIRECT` or `MIGRATION` source** — it removes those and the confirm copy says group-sourced copies stay («그룹 {name} 에서 받은 같은 세트는 남습니다 — 그룹에서 빼야 사라집니다»). A set with only group sources shows no revoke button, only a link to that group.
  3. **Group detail → «업무 권한 세트»** (rows 9–11): add/remove; the add confirm states the fan-out count after success (`fannedOutRows`).
  - Platform-scope (`'*'`) target operators appear only to a platform-scope actor, and only in the **all-tenants listing** `GET /api/admin/operators?tenantId=*` — they hold no `operator_tenant_assignment` row, so no real tenant's listing contains them (`TASK-PC-FE-354` AC-0 ②). The operator screen offers that listing as a **«모든 테넌트» view** to a caller whose `GET /api/admin/me` says `homeTenantId='*'` and who holds `operator.manage`; any other caller sees no toggle and the unchanged active-tenant list. In that view the per-row actions that act on the active tenant's membership (조직 스코프 · 이 회사에서 내보내기, and for a non-`'*'` row also 역할 변경 · 업무 권한) are hidden — they are done from that company's own listing; a `'*'` row's 역할 변경 confirm names «모든 테넌트(*)» (the producer replaces a `'*'` target's `'*'` roles whatever the header — `PatchOperatorRoleUseCase`).
  - On such a `'*'` target, opened by such an actor, the «업무 권한» dialog sends `X-Tenant-Id: *` for (a) a grant the actor chose to apply to «모든 테넌트» — that choice offers **presets only** (the producer answers a tenant set with `404`) — and (b) the revoke of a source whose `tenantId='*'`. Revoke is per source tenant: a `'*'`-target set holding both a `T` and a `'*'` personal source shows one revoke per tenant. Everything else — the read, a «현재 테넌트» grant, the revoke of a `T` source, and every call on a non-`'*'` target or by a non-`'*'` actor — sends the active tenant (a `'*'` header on a company target is the producer's `400`; from a non-`'*'` actor it is `403` + a DENIED audit row, so the console never sends it there).
  - **Self-grant** (actor = target, OD-4) is allowed; the confirm copy says «자기 자신에게 업무 권한을 줍니다 — 감사 기록에 자기 배정으로 남습니다.»
- 🔴 **Revocation-latency copy (ADR-083 D7 — verbatim meaning, never «즉시»)**: every revoke / group-remove / set-edit / set-delete confirm says the change applies **from the next tenant exchange** and that an already-issued domain token keeps working until it expires — «다음 테넌트 재교환부터 적용됩니다. 이미 발급된 도메인 토큰은 만료(최대 약 6분)까지 그대로 쓰입니다.» (before P3: «최대 30분»). «약 6분» = the assume token's TTL of 300 s plus the resource servers' 60 s expiry skew. This is the canonical IAM statement, «`exp` + 60 s — 최대 약 360초» ([`auth-api.md` § 회수 지연](../../../iam-platform/specs/contracts/http/auth-api.md) · `admin-api.md` § 회수 지연, measured by `TASK-BE-632` AC-6, owner decision 2026-10-15). The earlier wording «최대 5분» named the TTL alone and is superseded.
- **Read-only sets hide write buttons — the server is final**: ~~on a `fineGrained` domain screen, when the operator's business roles for that domain (row 12, intersected with the subscribed domains) contain only `access=READ` entries, the screen hides its mutation buttons.~~ → **per screen (`TASK-PC-FE-338` c-2, 2026-10-15 — follows the per-menu owner decision below):**
  - A screen's mutation buttons hide when the operator's business roles (row 12 ∩ subscribed domains — the same input as the nav signal below) hold **none** of the roles the server requires for **those mutations**. The required roles are declared per screen as `writeRequires`.
  - The struck rule could not be built from row 12, which carries `{domainKey, role}` and no `access`. It is also coarser than the server: an `INVENTORY_WRITE` holder is not «READ-only», yet `/wms/outbound`'s writes need `OUTBOUND_WRITE`.
  - A READ-only set never holds a write role, so it still hides every declared button (AC-2 unchanged).
  - Declared today:

    | Screen | Buttons | `writeRequires` | Server source |
    |---|---|---|---|
    | `/wms` | alert «확인 처리» | `WMS_OPERATOR` | wms admin-service `AlertDashboardController` `hasRole('WMS_OPERATOR')` |
    | `/wms/outbound` | 피킹 확정 · 패킹 진행 · 출고 확정 · 주문 취소 | `OUTBOUND_WRITE` | outbound-service `SecurityConfig` mutations = `OUTBOUND_WRITE ∨ OUTBOUND_ADMIN` (`OUTBOUND_ADMIN` is not in the catalog) |
    | `/wms/outbound` (button-level) | 발송 재시도 | `SCM_LOGISTICS_WRITE` | scm logistics-service `SecurityConfig` `POST /dispatches/{id}:retry` = `SCM_LOGISTICS_WRITE` (`TASK-MONO-792` Q3 «strict»; 28th window live: outbound set only → `403`) |
    | `/scm/replenishment` | 승인 · 기각 | `SCM_DEMAND_PLANNING_WRITE` | demand-planning-service `SecurityConfig` non-GET = `SCM_DEMAND_PLANNING_WRITE`; the approve's draft PO (`POST /api/procurement/po/from-suggestion`) = `SCM_PROCUREMENT_WRITE ∨ SCM_DEMAND_PLANNING_WRITE` (`TASK-MONO-792` Q2), so the demand-planning role alone completes an approve |
    | `/scm/config` | 정책 저장 · 매핑 저장 | `SCM_DEMAND_PLANNING_WRITE` | demand-planning-service `PUT /policies/{sku}` · `PUT /sku-supplier-map/{sku}` = `SCM_DEMAND_PLANNING_WRITE` |
    | `/ledger` | 대사 해소 · FX 환율 «새로고침»(`POST /fx-rates/refresh`) | `FINANCE_LEDGER_WRITE` | ledger-service `SecurityConfig` — the 6 business writes (resolve and fx refresh among them) = `FINANCE_LEDGER_WRITE`. 🔴 `FINANCE_LEDGER_CONTROL` does **not** open them (independent — `TASK-MONO-794` Q2); it opens only the 7 control writes, which the console never calls. Decide by role **name**, never by catalog `access` (CONTROL's `access` is `WRITE`) |
    | `/ecommerce/products` (`/new` · `/[id]` · `/[id]/edit`) | 상품 등록 · 수정 · 삭제 · 옵션 추가/수정/삭제 · 재고 조정 · 이미지 업로드/대표 지정/삭제 | `ECOMMERCE_PRODUCT_WRITE` | product-service `ProductOperatorAccess.requireWrite` on every `/api/admin/products/**` mutation (`AdminProductController` POST/PATCH/DELETE · variants · `:stock`; `AdminProductImageController` upload-url/register/update/delete); reads = `PRODUCT_READ ∨ PRODUCT_WRITE` (`TASK-MONO-795`) |
    | `/ecommerce/sellers` (`/new` · `/[id]`) | 셀러 등록 · 프로비저닝 · 정지 · 폐점 · 구성원 초대 | `ECOMMERCE_PRODUCT_WRITE` | 🔵 sellers are served by product-service and use the **product** role (795 Q1) — `AdminSellerController` POST · `/provision` · `/suspend` · `/close` and `AdminSellerMemberController` `/invitations` all call `ProductOperatorAccess.requireWrite`. There is no seller role |
    | `/ecommerce/orders` (`/[id]`) | 주문 상태 전이(확인 · 취소) | `ECOMMERCE_ORDER_WRITE` | order-service `AdminOrderController` `POST /{id}/status` → `OrderOperatorAccess.requireWrite`. The list screen has no mutation control |
    | `/ecommerce/shippings` | 배송 상태 전이 · 출고(운송장 입력) · 추적 동기화 | `ECOMMERCE_SHIPPING_WRITE` | shipping-service `OperatorRoleGuard.requireWrite` in `ShippingCommandService.updateStatus` (`PUT /{id}/status`) and `RefreshTrackingService.refreshFromCarrier` (`POST /{id}/refresh-tracking`) |
    | `/ecommerce/promotions` (`/new` · `/[id]` · `/[id]/edit`) | 프로모션 등록 · 수정 · 삭제 · 쿠폰 발급 | `ECOMMERCE_PROMOTION_WRITE` | promotion-service `OperatorRoleGuard.requireWrite` in `PromotionCommandService` (create · update · delete) and `CouponCommandService.issueCoupons` |
    | `/ecommerce/settlements` (`/periods/[id]`) | 수수료율 설정 · 정산 기간 개시 · 마감 · 지급 실행 | `ECOMMERCE_SETTLEMENT_WRITE` | settlement-service `OperatorRoleGuard.requireWrite` on `PUT /commission-rates/{sellerId}`, `POST` (open) · `/{id}/close` · `/{id}/payouts/execute`. 🔵 payout execution has **no** separate control role (795 Q2) |
    | `/ecommerce/notifications/templates` (`/new` · `/[id]/edit`) | 템플릿 등록 · 수정 | `ECOMMERCE_NOTIFICATION_WRITE` | notification-service `TemplateController.requireWrite` on `POST` and `PUT /{templateId}` |
    | `/erp/masters` | 부서·직원·직급·비용센터·거래처 등록 · 수정 · 폐기 · 부서 이동 · 직원 계정 연결 제안/철회/해제 | `ERP_MASTERDATA_WRITE` | masterdata-service `MasterdataApplicationService` — every create/update/retire/move and `proposeAccountLink` · `revokeAccountLink` = `authorize(RequiredScope.WRITE)`, which `RoleScopeAuthorizationAdapter` opens with `ERP_MASTERDATA_WRITE ∨ erp.write` only (`TASK-MONO-793`). 🔵 `MyAccountLinkProposalsCard` accept/reject are **personal** (`RequiredScope.SELF`: any ERP business role) and are **not** hidden. 🔴 The approval-target lookups (`GET /departments/{id}` · `/employees/{id}` = `SUBJECT_READ`, which `ERP_APPROVAL_WRITE` also opens) are reads and are never gated by this role |
    | `/erp/approval` | 결재 요청 작성 · 상신 · 승인 · 반려 · 회수 | `ERP_APPROVAL_WRITE` | approval-service `ApprovalApplicationService.authorizeWrite` on createDraft · submit · approve · reject · withdraw = `ActorContext.canWriteErp()` = `ERP_APPROVAL_WRITE ∨ erp.write`; list · detail · inbox = read. Approver eligibility (separation of duties, step approver) is a separate aggregate guard — a holder of the role can still be refused inline |
    | `/erp/delegation` | 위임 등록 · 회수 | `ERP_APPROVAL_WRITE` | approval-service `DelegationApplicationService.authorizeWrite` on createDelegation · revokeDelegation = `canWriteErp()`; the delegation list and the read-model delegation facts are reads |

  - **A button whose server differs from the rest of the screen's mutations** declares its own requirement (`buttonWriteRequires`, keyed by button); it replaces the screen's `writeRequires` for that button only. Today the one case is «발송 재시도» (`TASK-PC-FE-342`): it goes to scm logistics-service, not outbound-service, so `OUTBOUND_WRITE` neither opens nor closes it. A WMS outbound operator needs the SCM logistics set to retry. Its roles input is row 12 ∩ the active tenant's subscriptions to **wms and scm** (the button's server is scm — a tenant without scm cannot retry, as the server's token roles carry no scm role). ~~«발송 재시도» goes to scm logistics-service, which is `authenticated()` only. It carries no requirement and stays visible.~~ (true until `TASK-MONO-792`.)
  - A screen with no declaration hides nothing.
  - Row 12 failure, no active tenant, or a sample visitor → **buttons visible** (fail-open, as for the nav signal).
  - A direct URL or API call is still refused by the domain server with `403` — the console keeps the existing inline «권한 없음» mapping for that `403`. 🔵 This **supersedes**, from P4, the «does NOT pre-gate on role» statements of the domain sections (§ 2.4.5 et al.) for **button visibility only**; the attempt-and-map-403 handling stays, because hiding is a convenience and the producer is the gate.
- **Errors (inline, no crash)**: `404 DOMAIN_PERMISSION_SET_NOT_FOUND` → refresh the list · `409 DOMAIN_PERMISSION_SET_READ_ONLY` → «시스템 세트는 바꿀 수 없습니다 — 복제해서 새 세트를 만드세요» · `409 DOMAIN_PERMISSION_SET_IN_USE` → «아직 배정돼 있는 세트입니다 — 먼저 회수하세요» · `409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED` → «이미 직접 배정돼 있습니다» · `422 DOMAIN_ROLE_NOT_ASSIGNABLE` → refresh the catalog · `422 DOMAIN_NOT_SUBSCRIBED` → «이 테넌트가 구독하지 않은 도메인이 들어 있습니다» · `404 OPERATOR_NOT_FOUND` (not a member of the active tenant) → refresh the operator list · `403 PERMISSION_DENIED` / `TENANT_SCOPE_DENIED` → inline «권한 없음» · `400 REASON_REQUIRED` / `VALIDATION_ERROR` as elsewhere · `503 DOWNSTREAM_ERROR` / `CIRCUIT_OPEN` / timeout → only this section degrades · `401` → forced re-login.

##### Domain nav — two signals, kept apart (ADR-083 D9)

Domain menus are never hidden by either signal; each shows its own badge. Hiding is a convenience; the domain server is the gate.

| Signal | Source | Rule | On failure / unknown |
|---|---|---|---|
| **«구독 필요»** (subscription missing) — *documented here for the first time; the behaviour exists since TASK-PC-FE-314* | the registry catalog the `(console)` layout already fetches (§ 2.2) → the **active tenant's subscribed domains** | a nav node whose children all gate on the **same** domain (`permission-map` `domain` gate) gets the badge when the active tenant has not subscribed to that domain. A node with mixed child domains is not badged. `admin`-gated items are hidden by role (§ 2.4.3 «Nav exposure»); `domain`-gated items are **never hidden** by this signal | no active tenant, or registry failure → **no badge** («don't know ⇒ don't badge») |
| **«업무 권한 없음»** (no business permission) — new, P4; **per menu** (owner decision 2026-10-15, `TASK-PC-FE-338` c-2) | `GET /api/admin/me/domain-access` (row 12) with `X-Tenant-Id` = active tenant, **∩ the active tenant's subscribed domains** — 🔴 **never a decode of the domain token**. After P3 (`TASK-BE-632`) the assumed token's `roles` = assigned sets ∩ subscribed domains, so this input **equals the token's `roles`** without reading the token, and the signal predicts what the domain server decides | **per nav leaf**: each `domain`-gated leaf declares `serverRequires`, the roles of which the domain server needs **at least one** for that screen's **primary read**. The leaf gets the badge when the active tenant has subscribed to the leaf's domain and the input holds none of them. **A leaf the server opens on subscription alone declares nothing and is never badged.** A drill parent gets the badge only when **every** `domain`-gated child is badged (one cause per item). A domain without a subscription shows **only** «구독 필요» (the first signal wins) | API failure / timeout / `401`-after-refresh / no active tenant → **no badge, menu open** (an outage must never read as «no permission») |

- **What is declared (measured 2026-10-15, `TASK-PC-FE-338` c-2 AC-0 · ADR-083 ③ · BE-632 AC-1 live):**

  | Leaf | `serverRequires` | Why |
  |---|---|---|
  | `/wms/outbound` | `OUTBOUND_READ` · `OUTBOUND_WRITE` | outbound-service `GET /api/**` = `OUTBOUND_READ ∨ WRITE ∨ ADMIN` (live: inventory set → 403) |
  | `/ecommerce` (개요) | the READ · WRITE roles of order · product · member · promotion · shipping · notification | its cards read those six areas' summaries (sellers = product area); each card degrades to `forbidden` on its own `403` (existing per-cell rule), so any one area opens the screen. Settlement is not read here |
  | `/ecommerce/products` · `/ecommerce/sellers` | `ECOMMERCE_PRODUCT_READ` · `ECOMMERCE_PRODUCT_WRITE` | product-service `ProductOperatorAccess` (sellers are the product area — `TASK-MONO-795` Q1) |
  | `/ecommerce/orders` | `ECOMMERCE_ORDER_READ` · `ECOMMERCE_ORDER_WRITE` | order-service `OrderOperatorAccess` |
  | `/ecommerce/shippings` | `ECOMMERCE_SHIPPING_READ` · `ECOMMERCE_SHIPPING_WRITE` | shipping-service `OperatorRoleGuard` |
  | `/ecommerce/promotions` | `ECOMMERCE_PROMOTION_READ` · `ECOMMERCE_PROMOTION_WRITE` | promotion-service `OperatorRoleGuard` |
  | `/ecommerce/users` | `ECOMMERCE_MEMBER_READ` | user-service `AdminUserController` (member area is read-only — no WRITE role exists) |
  | `/ecommerce/settlements` | `ECOMMERCE_SETTLEMENT_READ` · `ECOMMERCE_SETTLEMENT_WRITE` | settlement-service `OperatorRoleGuard` |
  | `/ecommerce/notifications/templates` | `ECOMMERCE_NOTIFICATION_READ` · `ECOMMERCE_NOTIFICATION_WRITE` | notification-service `TemplateController` |
  | ~~every `ecommerce` leaf~~ | ~~`ECOMMERCE_OPERATOR`~~ | ~~the ecommerce gateway admits operators … only with `ECOMMERCE_OPERATOR`~~ — 🔴 **wrong since `TASK-MONO-795` (live from the 28th window, 2026-10-16)**: `ECOMMERCE_OPERATOR` became an auth-derived entry role and V0059 deleted it from every set, so row 12 never carries it and this row badged **every** E-commerce menu for every operator of an ecommerce-subscribed tenant. Replaced by the rows above (`TASK-PC-FE-345`). The gateway is a coarse door; the per-area service check decides, so it is what the signal predicts |
  | `/wms` · `/wms/inventory` · `/wms/inbound` · `/wms/master` | — | wms admin-service dashboards: `WMS_VIEWER` is synthesised from `entitled_domains ∋ wms` (subscription alone) |
  | `/wms/operations` | — | `WMS_ADMIN` for every operator — no catalog role can satisfy it, so it is not a set question |
  | `/scm` (개요) | `SCM_PROCUREMENT_READ` · `SCM_PROCUREMENT_WRITE` · `SCM_INVENTORY_VISIBILITY_READ` · `SCM_INVENTORY_VISIBILITY_WRITE` | its two cards read procurement `GET /po` and inventory-visibility `GET /snapshot` (each service: `GET` = READ ∨ WRITE, `TASK-MONO-792`); either role family opens the screen, and a missing one degrades only its own card (existing per-card rule) |
  | `/scm/procurement` | `SCM_PROCUREMENT_READ` · `SCM_PROCUREMENT_WRITE` | procurement-service `GET /api/procurement/**` = READ ∨ WRITE |
  | `/scm/inventory` | `SCM_INVENTORY_VISIBILITY_READ` · `SCM_INVENTORY_VISIBILITY_WRITE` | inventory-visibility-service `GET` = READ ∨ WRITE |
  | `/scm/replenishment` · `/scm/config` | `SCM_DEMAND_PLANNING_READ` · `SCM_DEMAND_PLANNING_WRITE` | demand-planning-service `GET` = READ ∨ WRITE. 🔵 `/scm/config`'s supplier picker reads procurement (`GET /procurement/suppliers`) — a demand-planning-only operator keeps the screen and loses only that picker's suggestions |
  | `/erp` (개요) | `ERP_MASTERDATA_READ` · `ERP_MASTERDATA_WRITE` · `ERP_APPROVAL_READ` · `ERP_APPROVAL_WRITE` | its tiles read masterdata (5 counts) and approval (결재 대기 · 활성 위임); either family opens the screen, a missing one degrades only its own tiles (existing per-tile rule) |
  | `/erp/masters` · `/erp/orgview` | `ERP_MASTERDATA_READ` · `ERP_MASTERDATA_WRITE` | masterdata reads and read-model `employees*` = `MD_READ ∨ MD_WRITE` (erp `iam-integration.md` § 업무 역할, `TASK-MONO-793` Q1 · Q6). 🔵 `/erp/orgview`'s delegation-facts card reads approval (`AP_READ ∨ AP_WRITE`) — a masterdata-only operator keeps the screen and loses only that card |
  | `/erp/approval` · `/erp/delegation` | `ERP_APPROVAL_READ` · `ERP_APPROVAL_WRITE` | approval reads (요청 목록/상세 · 결재함 · 위임 목록) = `AP_READ ∨ AP_WRITE` |
  | `/finance` (개요) | `FINANCE_LEDGER_READ` · `FINANCE_LEDGER_WRITE` · `FINANCE_LEDGER_CONTROL` | the ledger tiles (시산표 · 기간 · 대사 큐 · 환율) are read on every load and any ledger role opens ledger `GET` (`TASK-MONO-794` Q2, CONTROL included). 🔵 the default-account snapshot is read only when a default account is configured and needs `FINANCE_ACCOUNT_READ ∨ _WRITE`; today a `403` on **either** leg turns the whole screen into `forbidden` (`finance-overview` `overview-state.ts:280-286`), so a ledger-only operator with a default account sees no badge yet a refused screen — the safe direction (under-signal); per-leg degrade is a separate change |
  | `/finance/accounts` | `FINANCE_ACCOUNT_READ` · `FINANCE_ACCOUNT_WRITE` | account-service `SecurityConfig` `GET` = AC_READ ∨ AC_WRITE (console calls no account write) |
  | `/ledger` | `FINANCE_LEDGER_READ` · `FINANCE_LEDGER_WRITE` · `FINANCE_LEDGER_CONTROL` | ledger-service `GET` (16 paths) = LG_READ ∨ LG_WRITE ∨ LG_CONTROL — trial balance readable by CONTROL alone (live, 28th window) |
  | ~~Finance leaves~~ | ~~—~~ | ~~Finance synthesises `FINANCE_VIEWER`~~ — removed by `TASK-MONO-794` (live from the 28th window): no subscription-synthesised read. The empty declaration only under-signalled. Replaced by the Finance rows above (`TASK-PC-FE-344`) |
  | ~~ERP leaves~~ | ~~—~~ | ~~ERP reads are open to `entitled_domains ∋ erp`~~ — not true since `TASK-MONO-793` (live from the 28th window, 2026-10-16: «구독 합성 READ 는 없다»). The empty declaration never produced a false badge (it under-signalled: an operator without ERP roles saw no badge on menus the server refused). Replaced by the ERP rows above (`TASK-PC-FE-343`) |
  | fan leaves | — | not measured (ADR-083 ③ ⚪) — no declaration until measured |

  🔴 **An entry role is never declared** (`serverRequires` · `writeRequires` · `buttonWriteRequires`): `SCM_OPERATOR` (`TASK-MONO-792` Q4), `ERP_OPERATOR` (793), `FINANCE_OPERATOR` (794) and `ECOMMERCE_OPERATOR` (795) are **derived by auth** when the token carries ≥1 business role of that domain — they are not in the catalog and never appear in row 12, so declaring one badges (or hides buttons from) every operator of that domain. `console-nav-business-permission.test.ts` fails on any declared entry role.
- **Scales with P5–P8 (`TASK-MONO-792`~`795`).** When a domain gains fine-grained server checks, that phase adds `serverRequires` (and `writeRequires`) to that domain's leaves — data only, no new rule. **P5 (SCM) — done, `TASK-PC-FE-342`** (the SCM rows above + the `/wms/outbound` «발송 재시도» button-level row). **P8 (E-commerce) — done, `TASK-PC-FE-345` (menus) + `TASK-PC-FE-346` (buttons)** (the E-commerce `serverRequires` rows above + the seven E-commerce write rows in `Declared today`). **P6 (ERP) — done, `TASK-PC-FE-343` (menus + bell) + `TASK-PC-FE-347` (buttons)** (the ERP rows above + § 2.4.8 «ERP leg skipped» + the three ERP write rows in `Declared today`). **P7 (Finance) — done, `TASK-PC-FE-344`** (the Finance rows above + the `/ledger` write row). With P5–P8 in, no row declares an entry role and no domain row relies on a subscription-synthesised read.
- **Badge.** The text is «업무 권한 없음», and the leaf keeps its link (never hidden). The `(console)` layout reads row 12 once per render, together with the registry.

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
  | 13 | settings list | `GET /api/v1/admin/settings` (§ 5.1) | read |
  | 14 | settings get | `GET /api/v1/admin/settings/{key}` (§ 5.2) | read |
  | 15 | performance metrics («실적» strip — sits with #3 in § 1.2) | `GET /api/v1/admin/dashboard/metrics?period=week\|month[&warehouseId=]` (§ 1.2) | read |

  The wms write-admin surface (`admin-service-api.md` §§ 2–4: User / Role /
  Assignment, `WMS_ADMIN`+ heavy writes) plus **settings write**
  (`PUT /api/v1/admin/settings/{key}`, § 5.3, also `WMS_ADMIN`+) is
  **explicitly out of v1 console scope** — deferred to a later slice, not
  silently dropped. Only the **settings read** (§ 5.1/5.2, ops #13–14,
  `WMS_VIEWER`+) is in v1 scope (TASK-PC-FE-224 below) — a narrower carve-out
  than the rest of §§ 2–5, granted because settings read is operationally
  readable at the same role floor as every other § 1 dashboard read.

  **Console-side routing note (TASK-PC-FE-173, non-normative — no producer
  change)**: operations #1 (inventory snapshot) and #2 (inventory by-key) are
  served on the console's dedicated `/wms/inventory` surface (split off the
  `/wms` 개요's query table — a glance-overview is unfit for a filtered/
  paginated table, same principle as PC-FE-170). That surface also exposes
  filters (`locationId`, `lotId`, `minOnHand`) and columns (`damagedQty`,
  `lastAdjustedAt`) already defined by op #1's request/response shape but
  previously unexposed in the console UI, and a row-level "상세" panel that
  consumes op #2 (`getInventoryByKey`, composite key location+sku+lot — no
  single `[id]` route fits). Consumed read-only; no producer contract change.

  **Console-side routing note (TASK-PC-FE-175, non-normative — no producer
  change)**: operation #5 (shipments, `GET /dashboard/shipments`) — the
  택배/출고 read table (carrier code / tracking no / shipped-at, filters +
  pagination) — is served on the console's existing `/wms/outbound` 출고
  surface, as a read-side section below the outbound-order operations, rather
  than on the `/wms` 개요 (same glance-overview principle as PC-FE-173: a
  filtered/paginated query table is unfit for the landing). The 개요 keeps only
  the shipments **count tile** (오늘/주간/월간/전체, § 2.4.5.2 + PC-FE-174) and
  the **recent-shipments glance**. Consumed read-only; no producer contract
  change.

  **Console-side routing note (TASK-PC-FE-222, non-normative — no producer
  change)**: operations #6 (asns, `GET /dashboard/asns`) and #7 (asn
  inspection, `GET /dashboard/asns/{asnId}/inspection`) — the console's
  `features/wms-ops` client (`wms-shipments-api.ts`) had exported these two
  read functions since their addition alongside op #5 (TASK-PC-FE-146 split)
  but with **zero consumers** — are surfaced on a **new dedicated
  `/wms/inbound`** surface (WMS 입고, nav `WMS ▸ 입고`, between 가이드 and
  재고 — the physical inbound→inventory→outbound flow). This closes the wms
  domain's largest UI asymmetry: 출고 (op-set §2.4.5.1) had a dedicated
  screen, 입고 had none. The `/wms/inbound` screen renders op #6 as a
  filtered/paginated ASN table (filters: `status`, `warehouseId`,
  `supplierPartnerId`, `expectedArriveDateFrom/To` — all already defined by
  op #6's producer query-param contract) and op #7 as a per-row "검수" inline
  detail panel (mirrors the `/wms/inventory` composite-key "상세" pattern —
  no single `[id]` route). Op #7's `404` (no `inbound.inspection.completed`
  projected yet for that ASN) is rendered as a distinguishing "검수 내역
  없음" state, **not** a degrade. **Explicitly out of this surface's scope**
  (raw `inbound-service` write ops, not on the admin read-model this section
  consumes): putaway instruct/confirm, ASN creation, inspection confirmation
  — read-only. Consumed read-only; no producer contract change.

  **Console-side routing note (TASK-PC-FE-223, non-normative — no producer
  change)**: operation #11 (master refs, `GET /dashboard/refs/{type}`) — the
  console's `features/wms-ops` client (`wms-refs-api.ts`) had exported
  `listRefs` since the § 1.7 client split but with **zero consumers** — is
  surfaced on a **new dedicated `/wms/master`** surface (WMS 마스터, nav
  `WMS ▸ 마스터`, after 출고 — a reference/settings-flavoured surface placed
  after the physical inbound→inventory→outbound flow, not inside it). The
  `/wms/master` screen renders op #11 as a **ref-type tabbed**, filtered/
  paginated table: one tab per producer-documented `{type}`
  (`admin-service-api.md` § 1.7: `warehouses | zones | locations | skus |
  lots | partners`) — the console never invents a type the read-model
  doesn't document. Each row is the producer's generic `*Ref` projection
  (`admin-service` `domain-model.md` § 5 — denormalised `id`/type-specific
  code field/`name`(absent on `LotRef`)/`status`/`lastEventAt`); the table
  renders the common fields tolerantly (a `RefPageSchema` generic-row parse,
  never a per-type hardcoded schema). **Filter params (`q`/`status`) are a
  console-adopted convention, not enumerated by § 1.7** ("Query parameters
  vary by `{type}`" — the producer contract does not name them); the console
  reuses the same `q` (substring, mirrors § 2.2 `GET /users`) + `status`
  filter shape already established elsewhere on this producer's list
  endpoints, forwarded tolerantly. Only the default tab (`locations`) is
  server-seeded (page 0, no filters); every other tab / a filter or page
  change is a client-side re-query via the same-origin proxy. **Explicitly
  out of this surface's scope** (task § Out of Scope): master data
  create/update/delete — the admin read-model this section consumes is
  read-only; the raw `master-service` owns that SoT. Consumed read-only; no
  producer contract change.

  **Console-side routing note (TASK-PC-FE-224, non-normative — no producer
  change)**: operations #12 (projection status, `GET /operations/
  projection-status`) — exported by `wms-refs-api.ts` since the § 6.2 client
  split but with **zero consumers** — plus **new** operations #13–14
  (settings list/get, `GET /settings` + `GET /settings/{key}`, § 5.1/5.2 —
  the console's first settings client, `wms-settings-api.ts`) are surfaced
  together on a **new dedicated `/wms/operations`** surface (WMS 운영설정,
  nav `WMS ▸ 운영설정`, after 마스터 — the last child, an operational-
  parameter surface placed after even the reference/settings-flavoured
  마스터). The `/wms/operations` screen renders TWO independent sections:
  (a) **운영 설정** — a fixed, console-owned key→label catalog (예약 TTL
  `inventory.reservation.ttl_hours` + 저재고 기본 임계치 `inventory.
  low_stock.default_threshold_qty`, the two settings the 개요 저재고/재고
  배지 derive from) filtered against op #13's `content` array; a
  producer-absent key renders **no row** (never a forced-blank row); (b)
  **프로젝션 상태** — op #12's per-topic lag/last-processed table + the
  `worstLagSeconds` summary. The two sections fan out **in parallel** and
  degrade **independently** (mirrors the `/wms` 개요's `cell()` pattern,
  § 2.4.5.2) — one section's 403/503 never blanks the other. **Explicitly
  out of this surface's scope** (task § Out of Scope): settings **write**
  (`PUT /settings/{key}`, § 5.3, `WMS_ADMIN`+) and operator RBAC (users /
  roles / assignments, §§ 2–4, `WMS_ADMIN`+) — both outside this task's
  `WMS_VIEWER` read-only range. Consumed read-only; no producer contract
  change.

  **Console-side routing note (TASK-PC-FE-358, non-normative — no producer
  change)**: operation #3 (throughput, `GET /dashboard/throughput`, § 1.2) —
  exported by `wms-inventory-api.ts` (`getThroughput`) since the § 1 client
  split but with **zero consumers** — is surfaced as a **daily inbound/outbound
  card on the `/wms` 개요** (§ 2.4.5.2 below), not on a dedicated route. The
  server render reads the default warehouse's window; switching warehouse is a
  client re-query through a **new same-origin read proxy
  `GET /api/wms/throughput?warehouseId=<uuid>&from=<YYYY-MM-DD>&to=<YYYY-MM-DD>`**
  (READ-ONLY — GET only, no `Idempotency-Key`, no `X-Operator-Reason`, no
  body; the domain-facing IAM OIDC token is attached server-side exactly like
  every other `/api/wms/**` read). The proxy **validates before any upstream
  call** and answers `400 VALIDATION_ERROR` when `warehouseId` is not a UUID
  (§ 1.2 types it `UUID`), `from`/`to` is not a real ISO `LocalDate`,
  `to < from`, or the inclusive span exceeds 90 days (§ 1.2's own limits — the
  console never sends a request the producer is documented to reject). A
  valid request is forwarded verbatim; the `200` body is the producer's § 1.2
  shape, parsed (unknown extra fields pass through). Errors map through the
  shared `mapWmsError` (§ 2.5: 401 → 401, 403 → 403, 400/404 passthrough,
  503/timeout/network → 503). Warehouse candidates come from op #11
  (`GET /dashboard/refs/warehouses?status=ACTIVE`). Consumed read-only; no
  producer contract change.

- **Per-domain credential selection (the key correctness element — normative)**:
  **each § 2.4.x binding declares which credential it uses, and an
  implementer MUST NOT blanket-apply one domain's auth model to another.**
  The credential is a first-class, per-domain contract element, not an
  implementation detail:

  | Domain binding | `/api/admin/**` credential | Mechanism | Authority |
  |---|---|---|---|
  | IAM (§§ 2.4.1–2.4.4) | the **exchanged operator token** (`token_type=admin`, `iss=admin-service`), `getOperatorToken()` | server-side RFC 8693 token exchange (§ 2.6) | ADR-MONO-014; the **#569 trust-boundary invariant** (§ 2.1) — the IAM OIDC access token is **never** sent to IAM's `/api/admin/**` |
  | **wms (§ 2.4.5, this binding)** | an **IAM OIDC access token** — specifically the **domain-facing** one, `getDomainFacingToken()` (§ 2.7): the **active tenant's assumed token** when a tenant is selected, else the login token (`getAccessToken()`, the IAM-session HttpOnly cookie from FE-001) | sent **directly** as `Authorization: Bearer <IAM OIDC access token>` | wms `admin-service-api.md` § Global Conventions + `iam-integration.md`: RS256 JWT issued by IAM per ADR-001, validated against IAM JWKS by the wms gateway + admin-service; the tenant gate admits **`tenant_id=wms`, or any tenant whose signed `entitled_domains` contains `wms`** (dual-accept, ADR-MONO-019 § D5) and wms then scopes reads to that tenant (ADR-MONO-022 § D9). wms has **no** token-exchange of its own and **requires** an IAM OIDC token |

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
  and `features/wms-outbound-ops` clients use `getDomainFacingToken()` and
  **never** `getOperatorToken()` (asserted by test — the inverse of the
  FE-002..006 assertion). 🔴 **Which IAM OIDC token matters** (TASK-MONO-780):
  after a tenant switch the bearer is the **assumed token of the active
  tenant**, so the wms rows follow the selection (switch to `ecommerce` ⇒
  ecommerce's fulfillment orders; back to `demo-corp` ⇒ demo-corp's) —
  pinned by `tests/unit/wms-active-tenant-token.test.ts`. This sentence
  previously named `getAccessToken()`, which stopped being true at
  TASK-MONO-158 (§ 2.7) and was read by the 24th demo window as «wms always
  gets the login token». Client-side caches must follow the switch as well
  (`useTenantSwitch` drops the wms query roots — the server render alone does
  not reach an already-seeded React Query entry). Future
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
  - **Customer-tenant outbound scoping (TASK-MONO-304 / ADR-MONO-022 § D9)**:
    when an operator has assumed a customer tenant (the domain-facing token
    carries `tenant_id=<customer>`, e.g. `ecommerce`, admitted to wms via the
    `entitled_domains` dual-accept), wms scopes the **outbound** surface
    (§ 2.4.5.1) to that tenant's own `FULFILLMENT_ECOMMERCE` orders: `GET /orders`
    returns only those rows, and any single-order read/mutation on a foreign /
    B2B order returns `403 TENANT_SCOPE_DENIED` (mapped by the console to the
    inline non-crashing "not available" state, § 2.5 — never a re-login loop).
    This is derived entirely producer-side from the signed `tenant_id` claim;
    the console still sends **no** `X-Tenant-Id`. Native wms (`tenant_id=wms`)
    operators are unrestricted (full visibility), unchanged. *(Corrected by
    TASK-MONO-780: this line also listed platform (`*`) as unrestricted;
    ADR-MONO-064 § D3 made a `*` caller `restrictedTo("*")`, i.e. it sees
    nothing — `SecurityContextCallerScopeProvider`.)*

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
  | 10 | **retry dispatch** (repointed — TASK-PC-FE-258) | logistics `POST /api/v1/logistics/dispatches/{id}:retry` (scm gateway) via the wms-admin shipment resolve → logistics `by-shipment` dispatch resolve | **mutation** | producer-enforced (scm-tenant JWT) |

  The wms outbound **manual order-create** (`POST /orders`, § 1.1) remains
  **out of v1 console scope** — deferred, not silently dropped (manual create
  contradicts the auto-create-from-ecommerce model). The console outbound
  surface = the read set + the forward pick→pack→ship lifecycle advance + the
  cancel action (op 9, TASK-PC-FE-085) + the dispatch-retry recovery action
  (op 10, TASK-PC-FE-087 → repointed to logistics by TASK-PC-FE-258).

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

  **Dispatch retry (op 10 — the recovery action; TASK-PC-FE-087, REPOINTED to
  logistics by TASK-PC-FE-258 / ADR-MONO-053 §D8) mutation shape.** Carrier
  dispatch moved from the wms TMS side-channel to **`logistics-service`**
  (ADR-053 Phase 1, live) on the **scm gateway**; this action now re-drives a
  failed **carrier dispatch** instead of the retired wms
  `:retry-tms-notify`. The operator mental model ("re-send this shipment to the
  carrier") is preserved — only the backend it hits changed. The wms
  `:retry-tms-notify` endpoint + TMS subsystem retirement is a **separate
  wms-internal follow-up task** (unblocked once this repoint lands); no
  `projects/wms-platform/` change is part of this binding.
  - **trigger signal** — surfaced for an order with `status=SHIPPED` (a carrier
    dispatch exists only once the shipment is confirmed). It **no longer depends
    on the wms `tmsStatus` / `SHIPPED_NOT_NOTIFIED` saga state** (retired D8
    side-channel). The real "does a retry apply?" signal is the **logistics
    dispatch status** (`DISPATCH_FAILED` → retry meaningful), resolved
    server-side at action time; the action is otherwise the reason-free
    always-available recovery affordance.
  - **two-hop resolution (orderId → shipmentId → dispatchId), two 404s** — the
    logistics `:retry` is **dispatch-id-keyed**, but the outbound order-centric
    reads carry no `shipmentId` (§ 1.2 order detail = create-response shape; no
    `GET /orders/{id}/shipments`) and no `dispatchId`. The proxy resolves in two
    hops: (1) `shipmentId` from the wms **admin read-model**
    `GET /api/v1/admin/dashboard/shipments?orderId={id}` (admin-service-api.md
    § 1.3 — `WMS_ADMIN_BASE_URL`, § 2.4.5 credential); (2) the dispatch from
    logistics `GET /api/v1/logistics/dispatches/by-shipment/{shipmentId}`
    (gateway-public-routes.md § logistics-service — BE-045). No shipment →
    `404 SHIPMENT_NOT_FOUND` inline ("발송 정보 없음"); a shipment but no dispatch
    yet (the wms `outbound.shipping.confirmed` seam event not consumed) →
    `404 DISPATCH_NOT_FOUND` inline ("아직 발송 접수 전"). **Both are inline
    actionable states — NO `:retry` POST is fired on either.**
  - **gateway + credential REUSE (the crux — NOT a new credential)** — the
    logistics resolve + retry go through the **scm gateway**
    (`SCM_GATEWAY_BASE_URL`, default `http://scm.local`) reusing the **same
    domain-facing IAM OIDC credential + `callScmGateway` client the console
    already uses for the § 2.4.6 / § 2.4.6.1 scm demand-planning mutations**
    (the scm gateway validates the IAM RS256 token + `tenant_id ∈ {scm,*}`
    claim producer-side). This binding adds **no new env var, no new IAM
    entitlement, no new error code**. The logistics calls are NOT routed through
    the wms `callOutbound` client (a distinct gateway/base + nested error
    envelope). A logistics outage degrades ONLY the wms-outbound section (reuses
    `WmsOutboundUnavailableError` → `mapOutboundError` → 503).
  - **reason-free** — re-drives the carrier dispatch only (stock already
    consumed), UNLIKE cancel's required reason. Empty `{}` body + an
    `Idempotency-Key` (UUID, stable per a confirmed retry / fresh per a new
    attempt). NO `X-Operator-Reason`, NO `X-Tenant-Id`.
  - **role** — producer-enforced (a valid scm-tenant JWT). The console does NOT
    pre-gate on role — it attempts and maps a `403 FORBIDDEN` / `TENANT_FORBIDDEN`
    to an inline actionable state.
  - **outcomes** — success: the re-driven dispatch (`status → DISPATCHED`). The
    logistics `:retry` is naturally idempotent (an already-`DISPATCHED` dispatch
    returns a cached ack, no vendor call). A non-`DISPATCH_FAILED` dispatch is
    handled inline by the producer.

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

- **Order-status distribution — pipeline bar (TASK-PC-FE-362).** The `/wms/outbound`
  page's server state (`getOutboundSectionState`) additionally counts the orders
  per status with **7 extra reads of op 1** — `GET /orders?status=<S>&page=0&size=1`.`totalElements`
  for `S ∈ {PICKING, PICKED, PACKING, PACKED, SHIPPED}` (the normal flow) and
  `S ∈ {CANCELLED, BACKORDERED}` (side branches). Normative points:
  - **Why here and not on the `/wms` overview (§ 2.4.5.2, unchanged).** The overview
    reads the admin-service order read model (`GET /dashboard/orders`), which only
    ever holds `RECEIVED` / `SHIPPED` / `CANCELLED` (picking and packing events do not
    change its `status`); `status=PICKING` there is always 0. Op 1 filters on the
    real order status, so it is the only source for the five flow states.
  - **Order of the bar** = the producer state machine
    (`outbound-service/state-machines/order-status.md` § Transitions):
    `PICKING → PICKED → PACKING → PACKED → SHIPPED`. `RECEIVED` is transient (same
    TX to `PICKING`) and is not counted. `CANCELLED` / `BACKORDERED` are terminal
    off-flow states — shown as plain numbers **outside** the bar, never on it.
  - **Counted once per page load (SSR)**, in the same server state as the page-0
    list, with the **same credential, role (`OUTBOUND_READ`) and tenant scope** as
    op 1. A list filter or paging change in the client does **not** re-run them.
  - **Tenant scope (producer `outbound-service-api.md` § Tenant scoping)**: a native
    `wms` operator is unrestricted and counts every order; a customer-tenant
    operator counts only its own tenant's orders (`orders.tenant_id` is forced
    producer-side; B2B / `null`-tenant orders are not visible to it). The screen
    states this in one sentence next to the bar.
  - **Independent cells.** Each status is its own read: `401` in any → whole-session
    `redirect('/login')`; `403` / `503` / timeout / other → that cell only is
    non-`ok`. If **any** cell is non-`ok` the bar is not drawn (a partial
    distribution would read as the whole) and that number shows `—`; the other
    numbers stay. All seven `0` → an empty note instead of a bar. The page-0 list
    read keeps its existing page-level rules.
  - No producer change, no aggregation endpoint (ADR-MONO-017 D3.B — counts are
    `totalElements`). An unknown/future status is not counted (the bar is the known
    lifecycle; the list stays authoritative).

> **Not a § 3 parity row** (same as § 2.4.5): the wms outbound surface is
> additive federated **domain** scope, not a IAM `admin-web` parity capability;
> it adds no § 3 row and changes none.

#### 2.4.5.2 wms operator **overview snapshot** — `/wms` landing (TASK-PC-FE-166 — first bff-domain reference of the domain-landing overview series)

The `/wms` section landing is elevated with an **operator overview snapshot**
band above the alerts table (the inventory query table moved to `/wms/inventory`
per PC-FE-173, and the 택배/출고 table to `/wms/outbound` per PC-FE-175): per-area
counts, an alert-acknowledgement distribution, and a recent-shipments glance.
This is the **first bff-domain reference implementation** of the console
domain-landing overview series (the analogue of the ecommerce § 2.4.10.6
snapshot for the wms/scm/finance/erp read-leg domains); the shared read-leg
decision is recorded in [`console-web/architecture.md` § 도메인 랜딩 운영 개요
스냅샷](../services/console-web/architecture.md) (TASK-PC-FE-168).

- **Read model (console-web DIRECT fan-out).** Like ecommerce § 2.4.10.6 — and
  contrary to the "bff read-leg" framing — the wms section already reaches its
  producer server-side via `getDomainFacingToken()` (§ 2.4.5 direct client),
  so this snapshot is a **domain-internal** console-web fan-out reusing the
  feature's own `list*` reads. **No § 2.4.9 composition leg** (§ 2.4.9.1/.2 is
  the console-HOME cross-domain surface, not a single-domain landing).
- **Counts.** Each area count = the existing § 2.4.5 read's `totalElements`
  with `?page=0&size=1`, for the **operational-scale areas only**: inventory
  (§ 1.1 `/dashboard/inventory`) and shipments (§ 1.3 `/dashboard/shipments`).
  The 재고 tile also carries a **저재고 attention sub-count** (PC-FE-177) — the
  same read with `lowStockOnly=true`.`totalElements` (SKU-locations below their
  reorder point); its own cell degrades independently (the tile stays `ok` on
  its total read). No client-side quantity aggregation (D3.B — no `Σ onHandQty`).
- **Order (PC-FE-177, non-normative).** The 개요 reads 규모 → 주의 → 활동: the
  count tiles + alert distribution, then the alerts table, then the 최근 출고
  glance LAST (so the alert distribution + table are contiguous).
  Alerts are **not** a count tile — a total-alerts count is a derived
  attention-signal, not an operational-scale total, and merely duplicates the
  (미확인 + 확인) sum of the distribution below; alerts are represented solely by
  the alert-acknowledgement distribution, and **no no-filter total-alerts read
  is issued** (TASK-PC-FE-170).
- **Alert-acknowledgement distribution.** `GET /dashboard/alerts?acknowledged=false&page=0&size=1`.`totalElements`
  (미확인) + `acknowledged=true` (확인) — the operator's key wms signal, and the
  sole overview representation of alerts.
- **Recent activity.** `GET /dashboard/shipments?page=0&size=5`.`content`.
- **No aggregation endpoint (ADR-MONO-017 D3.B).** Counts derive from
  `totalElements`; there is deliberately **no** producer `/summary` and **no
  producer retrofit** (the wms bff-domain does NOT get the § 2.4.10.2–.6
  ecommerce `/summary` treatment — that is a producer retrofit D3.B forbids for
  a non-absorbed federation). Re-introducing an aggregation endpoint here is a
  contract defect.
- **Per-domain deviation vs ecommerce (§ 2.4.10.6).** The count tiles are
  **read-only stat tiles, NOT nav links**: `/wms` is a single-route ops screen
  (no per-area drill-in sub-routes like `/ecommerce/products`), so the tiles
  summarize the tables rendered directly below rather than acting as
  quick-launch links.
- **Per-area brief status.** Derived from each fan-out cell's outcome
  (`ok` / `403 → 권한 없음` / `503|timeout|other → 점검 필요`) — reachability is
  the honest, zero-backend signal (same rule as § 2.4.10.6).
- **Resilience (§ 2.5).** Per-cell degrade is cell-local (one area's failure
  never blanks the snapshot); a `401` in ANY leg triggers a whole-session
  `redirect('/login')` (no partial authed state — mirrors `getWmsSectionState`).
  Read-only; no auto-refetch. `WmsUnavailableError` (503/timeout/network)
  degrades the cell, never a re-login.
- **Performance metrics strip (#15) — ADR-MONO-084 P3 / TASK-MONO-808.**
  `GET /dashboard/metrics?period=week|month` feeds the «실적» strip, the **first**
  child of the 운영 개요 band (above the count tiles): 입고 수량 (`qtyReceived`) ·
  출고 수량 (`qtyShipped`) · 출고 건수 (`shipmentCount`), current vs the
  same-length previous KST window, with a daily series (the chart draws
  `qtyShipped`). Shape, windows and error handling are
  [`platform/contracts/performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md);
  what each key counts is `admin-service-api.md` § 1.2 `GET /dashboard/metrics`.
  **Warehouse-global** (§ 1.0 of the producer contract, like #3): the console
  sends **no `warehouseId`** — the 개요 has no single selected warehouse (the
  throughput card's selector is local to that card), so the strip describes the
  whole operation. Parsed by `shared/api/performance-metrics.ts` (integer
  strings — F5) as its **own cell** inside `getWmsOverviewState`: `403` →
  «권한 없음» in the strip, a non-200 (incl. `400 VALIDATION_ERROR`) or an
  unparseable body → «실적을 불러올 수 없습니다», while every other block renders;
  a `401` → the same whole-session re-login. The page reads `?period=` (default
  `month`; an absent or unknown value falls back to the default — never a 400
  page). 🔵 It does **not** replace the 배송 tile's 오늘/주간/월간: those are the
  caller's **tenant-owned** shipment counts (`shippedAt` windows), a different
  question from the warehouse-global strip, and both stay.
- **Daily throughput card (TASK-PC-FE-358).** A separate server state
  (`getWmsThroughputState`), fanned out **in parallel with, and independent
  of**, the cells above — its failure never degrades the count tiles, the
  alert distribution or the recent glances, and theirs never degrades it.
  **Placement**: the 활동 band, first (above 최근 재고 조정 / 최근 출고) — a trend
  over days is activity, and placing it with the counts would split the alert
  distribution from the alerts table (the Order rule above).
  Two reads, in order:
  1. **Warehouse list** — op #11 `GET /dashboard/refs/warehouses?status=ACTIVE&page=0&size=100`;
     the console keeps only rows whose `status` is `ACTIVE` (or absent —
     tolerant), and sorts them by warehouse code ascending (`code`, the
     producer's `MasterRefResponse` field; `warehouseCode` / `id` as tolerant
     fallbacks). **0 warehouses** → the card shows a "no warehouse" notice and
     **no throughput read is issued**; **1** → that warehouse, no selector;
     **≥ 2** → a selector, default = the first after sorting.
  2. **Throughput** — op #3 `GET /dashboard/throughput?warehouseId=<default>&from=<to−13d>&to=<KST today>`:
     a **14-day window ending at the KST calendar date of the request**. 🔴
     **KST, not UTC** (TASK-MONO-808 / TASK-MONO-815): the producer buckets each
     event by its KST calendar day (wms admin V6, `admin-service-api.md § 1.2`
     «Day boundary = KST»), so the window's `to` is the KST date of `now`
     (`kstDateOf`, `shared/lib/kst-period`) — a UTC `to` would omit the newest
     day between 00:00 and 09:00 KST. The card labels the window «한국 시간 기준»
     on screen. The window is built from day buckets, not from instants, so it
     is not the `kstPeriodBounds()` instant window the 배송 tile uses — they only
     share the KST offset. 🔵 **Deploy coupling**: this switch ships in the same
     window as the AMI bake carrying admin V6 (the console is Vercel and goes
     live on merge); an old volume's inbound days before V6 are still UTC days
     until the `read-model-rebuild.md` § KST day rebuild has run.
  - **Zero-fill (normative).** § 1.2 lists **only days that have a row** (a
    day with no putaway and no shipment is absent, not `0`). The console fills
    every date `from..to` (inclusive) that the response omits with
    `putawayCount = shipmentCount = 0` before rendering, so the horizontal
    axis never skips a day (a skipped day would draw a false trend). A day
    present in the response but outside `from..to` is ignored.
  - **What is drawn.** Per day, a pair of bars: inbound `putawayCount` vs
    outbound `shipmentCount` (counts, not quantities), scaled to the window's
    max count (divide-by-zero guarded; an all-zero window renders the empty
    notice instead of flat bars). `totals.inbound.qtyReceived` /
    `totals.outbound.qtyShipped` appear as text only. Bars are
    decorative (`aria-hidden`); every number is also text.
  - **Degrade (per card, § 2.5).** Either read `403` → the card shows
    "권한 없음"; `503`/timeout/network/parse/other → "점검 필요"-class notice;
    a `401` in either read → the same whole-session `redirect('/login')` as
    the other cells (no partial authed state). A client re-query (warehouse
    switch, through the § 2.4.5 proxy above) that fails replaces only the
    chart body with the notice; a `401` there follows the client api-client's
    whole-session re-login.
  - **Out of scope.** Multi-warehouse aggregation (one call per warehouse is a
    separate decision), quantity-mode bars, any producer change (including the
    upsert-guard undercount noted in the ticket — a wms-side ticket).

> **Not a § 3 parity row**: consumes only already-listed § 2.4.5 endpoints in a
> new read composition; adds no § 3 row and changes none.

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
  | 7 | PO performance metrics («실적» strip — ADR-MONO-084 P5, TASK-MONO-810) | `GET /api/v1/procurement/po/metrics?period=week\|month` (`procurement-api.md` § `GET /api/procurement/po/metrics`) | read |

  **PO list filters (TASK-MONO-810).** Row 1 is called with `status`,
  `supplierId` and — added with the «실적» strip — `submittedFrom` /
  `submittedTo` (ISO-8601 instants, half-open `[submittedFrom, submittedTo)` on
  `submittedAt`, the same rule as the metrics window) and `excludeStatus` (one
  PoStatus left out); all combinable, ANDed. The `/api/scm/po` proxy forwards
  all five verbatim; the `/scm/procurement` page reads `?status=` /
  `?submittedFrom=` / `?submittedTo=` / `?excludeStatus=` from its URL (the
  strip's card links) and **drops a value it can tell is malformed** — an
  unknown status / excluded status, or a bound that is not an offset-qualified
  instant — naming it in a notice instead of sending it (a producer `400` would
  surface as the list's transient «일시적으로 불러올 수 없습니다», wrong for a bad
  link). An applied card filter shows as one line («제출 기간: … · 취소 제외», in
  KST) with a 해제 button that clears the window and the exclusion together and
  removes both from the address. **The list a 발주 건수 / 발주 금액 card opens
  (`submittedFrom=<current.from>&submittedTo=<current.to>&excludeStatus=CANCELED`)
  has `totalElements` equal to that card's `poCount`** — same window, same
  exclusion.

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
  validation). `supplierId` is a **free-text** input; the console validates
  shape only (the `sku_supplier_map` is the deliberate minimal stand-in per
  ADR-MONO-027 D3 and demand-planning does not resolve the value). A procurement
  supplier master **does** exist (TASK-SCM-BE-059 / ADR-SCM-001); per
  `scm-procurement-events.md` § inbound-expected (ADR-MONO-050 D9) this value is
  that master's supplier **CODE**. (TASK-MONO-677 corrected the former "there is
  no supplier master to resolve against".)

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

#### 2.4.6.3 scm operator **overview snapshot** — `/scm` landing (TASK-PC-FE-167 — bff-domain overview, follows the wms § 2.4.5.2 reference)

The `/scm` landing gains an **operator overview snapshot** band above the ops
tables: per-area counts (발주 / 재고 스냅샷), a PO-status distribution, and a
recent-PO glance. Follows the PC-FE-168 shared read-leg decision
(`console-web/architecture.md § 도메인 랜딩 운영 개요 스냅샷`) and the wms
§ 2.4.5.2 reference.

- **Read model (console-web DIRECT fan-out).** Reuses the existing § 2.4.6
  `listPurchaseOrders` / `getSnapshot` reads server-side (`getDomainFacingToken()`);
  **no § 2.4.9 composition leg.** Counts = `totalElements` with `?page=0&size=1`: 발주
  (`GET /api/v1/procurement/po`), 재고 스냅샷
  (`GET /api/v1/inventory-visibility/snapshot`).
- **PO-status distribution.** `GET /api/v1/procurement/po?status=<S>&page=0&size=1`.`totalElements`
  for each known PO status.
- **Recent activity.** `GET /api/v1/procurement/po?page=0&size=5`.`content`.
- **S5 (§ 2.4.6, NORMATIVE).** The 재고 스냅샷 count comes from inventory-visibility,
  which carries the REQUIRED `meta.warning`; the snapshot surfaces it PROMINENTLY
  via `<S5Warning>` whenever the count is shown (never stripped). The PO surface
  has no such warning.
- **No aggregation endpoint (ADR-MONO-017 D3.B).** Counts from `totalElements`;
  no producer `/summary`, no producer retrofit.
- **Deviation vs ecommerce (§ 2.4.10.6).** Count tiles are **read-only stat
  tiles, NOT nav links** (`/scm` is a single-route ops screen).
- **Resilience (§ 2.5).** Per-cell degrade cell-local; a `401` in ANY leg →
  whole-session `redirect('/login')`. Read-only; no auto-refetch. `429` is
  already bounded by the § 2.4.6 client backoff (the overview does not re-storm).
- **Performance metrics strip (§ 2.4.6 row 7) — ADR-MONO-084 P5 / TASK-MONO-810.**
  `GET /api/v1/procurement/po/metrics?period=week|month` feeds the «실적» strip,
  the **first** block of the overview band: 발주 건수 (`poCount`) · 발주 금액
  (`poAmount:<CUR>` — **one card per currency**, labelled «발주 금액 (KRW)»; never
  added across currencies) · 입고 완료 (`receivedCount`), current vs the
  same-length previous KST window, with a daily series (the chart draws
  `poCount`). Meaning per key is the producer's (`procurement-api.md`): 건수 · 금액
  = `submittedAt` in the window and not `CANCELED` now (a later cancel lowers a
  past value); 입고 완료 = POs whose `→ RECEIVED` transition happened in the window.
  The strip carries a one-line scope note saying so. Shape, windows and error
  handling are
  [`platform/contracts/performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md);
  the body is the bare shape (a `{ data, meta }` envelope is unwrapped too).
  Parsed by `shared/api/performance-metrics.ts` (integer strings — F5) as its
  **own cell** inside `getScmOverviewState`: `403` → «권한 없음» in the strip, any
  other failure or an unparseable body → «실적을 불러올 수 없습니다», while the
  tiles, the distribution and the recent list render; a `401` → the same
  whole-session re-login; not eligible → no call. The page reads `?period=`
  (default `month`; absent or unknown → the default, never a 400 page).
  - **Card links (the one deviation from the tiles above).** The strip's cards
    ARE links (`PerformanceStrip`'s optional `cardHref`; strips without it are
    unchanged): 발주 건수 and every 발주 금액 card → `/scm/procurement?
    submittedFrom=<current.from>&submittedTo=<current.to>&excludeStatus=CANCELED`
    (the response's own window strings, URL-encoded — the list then holds exactly
    the counted POs: its `totalElements` = the card's `poCount`); 입고 완료 →
    `/scm/procurement?status=RECEIVED` (receipt time is not a list filter, so no
    submit window is put on it — that would list a different set of POs).
  - **Sample mode.** The `flat:scm` fixture answers `/po/metrics` from the same
    seed POs the list shows (by the rules above, KRW minor units = won); the seed
    has no status history, so 입고 완료 is honestly `0`. Its `/po` list honours
    `submittedFrom` / `submittedTo` / `excludeStatus`.

> **Not a § 3 parity row**: consumes only § 2.4.6 endpoints in a new read
> composition; adds no § 3 row and changes none.

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
    **Sole exception — chart geometry (TASK-PC-FE-359, owner decision
    2026-10-22 KST)**: one function, `shared/lib/chart-scale.ts`
    `chartMagnitude(decimalString)`, may turn a money / rate decimal string
    into a JS `Number`, and its result may feed **only** chart geometry (bar
    width, line y). It never feeds displayed text, totals or comparisons —
    every visible figure stays the original string / `formatMoney`. Canonical
    wording and the guard test: `console-web/architecture.md` § Integration
    Rules «F5 의 유일한 예외». The same exception covers § 2.4.7.1.

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
    The one exception is chart geometry via `shared/lib/chart-scale.ts`
    `chartMagnitude` only (§ 2.4.7 «Sole exception», TASK-PC-FE-359) — e.g.
    the FX history line's y coordinates; the rate text stays verbatim.
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

#### 2.4.7.2 finance domain **overview + guide** — `/finance` landing, `/finance/guide` (TASK-PC-FE-229 — orthodox nav parity; supersedes the PARKED TASK-PC-FE-160)

Finance nav gains the **개요 → 가이드 → 계좌 → 원장** child order every other
domain already has (IAM/WMS/SCM/E-Commerce: 개요 → 가이드 → 기능). The domain
root `/finance` — formerly the § 2.4.7 account surface — is **repointed** at a
live overview snapshot; the account surface **relocates** to
`/finance/accounts` (label `운영` → `계좌`, nav testid `nav-finance-ops` →
`nav-finance-accounts`; the `/api/finance/accounts/**` proxy and
`features/finance-ops` are **unchanged** — a pure route relocation). A static
`/finance/guide` reference (finance-platform domain service map + the 4
console screens' meaning + regulated states/KYC/F5/double-entry/reconciliation
concepts) is added, mirroring `features/scm-guide` (TASK-PC-FE-188) /
`features/wms-guide` / `features/iam-guide`.

- **PC-FE-160 supersession (honesty preserved, not relaxed).** § 2.4.9 "도메인
  랜딩 운영 개요 스냅샷" classified finance as the **degenerate case**: v1 has
  no account `list`/`search` GET, so a count fan-out over accounts is
  **impossible** and a synthetic ₩ aggregate is **forbidden** — PC-FE-160
  declined a landing on that basis. This overview does **not** reopen that
  constraint: it aggregates ONLY (a) the **ledger-service** browsable index
  reads already consumed by § 2.4.7.1 (trial balance `inBalance`, the
  `periods` page filtered client-side to `status==='OPEN'` — an honestly
  BOUNDED count captioned by its sample size, **not** a producer-filtered
  exhaustive total — the periods list has no status query param;
  `listDiscrepancies({status:'OPEN'}).meta.totalElements` — an accurate
  producer-filtered total; and the FX rates cache freshness/stale count) and
  (b) the operator's **own** default finance account — resolved via the
  EXISTING `getFinanceDefaultAccountId()` (§ 2.2 registry
  `operatorContext.defaultAccountId`, TASK-PC-FE-014/016) — as a **single**
  `getAccount`/`getBalances` pair (§ 2.4.7). **No** account list/search call is
  ever made; a test pins this (the overview state seed calls `getAccount`
  exactly once, never a list endpoint).
- **Read model (console-web DIRECT, reused clients).** No new producer
  endpoint for the tiles and the snapshot, no § 2.4.9 composition leg — the
  overview composes the SAME `features/ledger-ops` + `features/finance-ops`
  server clients already bound by §§ 2.4.7/2.4.7.1, exactly as the § 2.4.3.1
  IAM overview snapshot composes the existing IAM list clients. (The «실적»
  strip below is the one ADR-MONO-084 producer read added on top.)
- **Independent degrade (the decisive resilience rule).** The ledger leg and
  the account leg run in their OWN try/catch — a `503`/timeout in ONE leg
  degrades ONLY that tile; the sibling tile renders normally (`ledgerDegraded`
  / `accountDegraded` are separate flags). A `403` in EITHER leg (the SAME
  domain-facing IAM OIDC token / finance tenant scope drives both § 2.4.7 and
  § 2.4.7.1) collapses to ONE whole-overview `forbidden` block, mirroring the
  `/finance/accounts` + `/ledger` page-level waterfall. A `401` in EITHER leg
  triggers ONE whole-session `redirect('/login')` (no partial authed state,
  no per-leg re-login loop). No default finance account configured
  (`getFinanceDefaultAccountId()` → `null`) is a normal "not set up" state
  (`defaultAccountMissing`), NOT a degrade — the ledger tiles still render.
- **F5 (unchanged obligation).** The default-account snapshot's balances are
  rendered via the SAME `formatMoney(...)` string-based scale-correct path as
  § 2.4.7 — never `Number()`/`parseFloat()`/`parseInt()`.
- **Nav.** `ConsoleSidebarNav` Finance drill children reorder to `{ href:
  '/finance', label: '개요' }, { href: '/finance/guide', label: '가이드' }, {
  href: '/finance/accounts', label: '계좌' }, { href: '/ledger', label:
  '원장' }`. The existing longest-match `activeHref` logic (unchanged code)
  keeps `/finance`(개요) from lighting up on `/finance/accounts`(계좌) — a nav
  test pins this.
- **Catalog tile unaffected/improved.** `resolveConsoleRoute` still sends
  non-IAM products to their registry `baseRoute` (unchanged, finance
  `baseRoute` stays `/finance`); the destination now being an overview
  landing instead of an empty account-lookup form is a UX improvement, not a
  contract change.
- **Performance metrics strip — ADR-MONO-084 P7 / TASK-MONO-812.**
  `GET /api/finance/accounts/metrics?period=week|month` on account-service
  (`features/finance-overview/api/finance-metrics-api.ts`, through the SAME
  `callFinance` hardened call site as § 2.4.7: the domain-facing IAM OIDC token,
  never the operator token, no `X-Tenant-Id` — finance resolves the tenant from
  the token) feeds the «실적» strip, the **first** block under the heading:
  충전 건수 (`topupCount`) · 충전 금액 (`topupAmount:<CUR>`) · 이체 건수
  (`transferCount`) · 이체 금액 (`transferAmount:<CUR>`) · 캡처 건수
  (`captureCount`) · 캡처 금액 (`captureAmount:<CUR>`), in that order with
  **one card per currency** (KRW and USD are separate cards, never added or
  converted — ADR-MONO-084 OD-3), current vs the same-length previous KST
  window, with a daily series (the chart draws `topupCount`). Meaning per key
  is the producer's (`finance-platform/specs/contracts/http/account-api.md`
  § `GET /api/finance/accounts/metrics`); the strip carries a one-line scope
  note, verbatim:
  «확정된 거래만 셉니다 — 보류는 캡처된 금액만 들어가고, 해제된 보류와 실패한 거래는 0입니다. 이체는 한 번만 셉니다. 금액은 통화마다 따로이며 서로 더하거나 환산하지 않습니다. 테넌트 전체 합계이고 계좌별로 나누지 않습니다.»
  Shape, windows and error handling are
  [`platform/contracts/performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md);
  the body is the bare shape. Parsed by `shared/api/performance-metrics.ts`
  (integer strings — F5) as its **own leg** inside `getFinanceOverviewState`.
  🔴 **It does not take part in the shared-credential `forbidden` rule above**:
  a `403` on it → «권한 없음» in the strip only; `5xx` / timeout / any other
  failure or an unparseable body → «실적을 불러올 수 없음» (never a `0`); in
  both cases the ledger tiles and the account snapshot render. A `401` → the
  same whole-session re-login; not eligible → no call. The page reads
  `?period=` (default `month`; absent or unknown → the default, never a 400
  page). The cards are **not** links (no list filters by settle time, and
  there is no account list). **Honest constraint preserved**: this is not an
  account list/search and not a console-side sum — the producer aggregates the
  tenant's settled transactions and returns tenant-wide totals only; no
  account id crosses to the console.
  - **Sample mode.** The `flat:finance` fixture answers
    `/api/finance/accounts/metrics` (matched before the `/accounts/{id}`
    detail pattern) from the SAME transaction rows the 계좌 screen lists, by
    the producer's rule — so the month shows the one 80,000 원 top-up and the
    ACTIVE hold and the FAILED transfer move nothing. That seed row's type was
    `CREDIT`, which the producer never emits; it is now `TOPUP` (and
    `features/finance-ops` `KNOWN_TXN_TYPES` gained the producer's `TOPUP` /
    `WITHDRAW`).

> **Not a § 3 parity row**: like § 2.4.7 / § 2.4.7.1, this is an additive
> nav/route composition over already-listed §§ 2.4.7/2.4.7.1 endpoints, plus
> the ADR-MONO-084 performance read above (an overview-only read, not an
> admin-web parity function) — adds no § 3 row and changes none (count stays
> exactly 16 — the FE-006 no-drift guard remains green).

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
  - **ERP leg skipped for an operator known to hold no ERP role (`TASK-PC-FE-343` · `TASK-MONO-793`
    Q2/Q6)**: since 793 the notification endpoints are personal endpoints that need **any ERP
    business role** (or `erp.read` · `tenant_id='*'`) — an operator with none gets `403`, which the
    aggregator records as `degradedDomains ∋ erp`. So, before calling a domain's inbox, the console
    asks row 12 of § 2.4.3.4 (`GET /api/admin/me/domain-access`) and **does not call** that domain when
    **all** of these hold: an active tenant is selected, it is not `'*'` (the `'*'` token opens the
    endpoint without a role), row 12 answered, and it carries **no** role of that domain. The domain
    is then neither in `items` nor in `degradedDomains` (nothing was asked; nothing failed).
    Row 12 failure / timeout, no active tenant, or `'*'` → the domain is called as before
    (fail-open — an outage must never hide an operator's notifications).
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

- **Employee ↔ IAM account link binding (TASK-PC-FE-318 — `TASK-MONO-774`
  S4; producer `masterdata-api.md` § Employee ↔ IAM account link, v1.1
  additive)**: the five link operations sit **outside** the master write matrix
  above — they are not create/update/retire of a master, and two of them are
  **not `erp.write` writes at all**. The producer is authoritative (request /
  response / error tables canonical there); this binding states the console
  obligation only.

  | Operation | Who may do it (producer-enforced) | Same-origin proxy (console) | Upstream (`masterdata-service`) | `Idempotency-Key` | `reason` (body) |
  |---|---|---|---|---|---|
  | propose | `erp.write` + the employee's department data scope (same rule as any employee write) | `POST /api/erp/masterdata/employees/{id}/account-link-proposals` | `POST .../employees/{id}/account-link-proposals` (`{ accountId, reason? }`) | **required** | `≤256`, optional |
  | accept | 🔴 **the account owner** (caller `sub == proposal.accountId`, `erp.read` or more, **no** data scope, **not** `erp.write`) — and never the proposer (two-person rule) | `POST /api/erp/masterdata/account-link-proposals/{proposalId}/accept` | `POST .../account-link-proposals/{proposalId}/accept` (`{}`) | **required** | — (no producer slot) |
  | decline | 🔴 **the account owner** (same gate as accept) | `POST /api/erp/masterdata/account-link-proposals/{proposalId}/decline` | `POST .../{proposalId}/decline` | **required** | `≤256`, optional |
  | revoke | `erp.write` + the employee's department data scope (need not be the proposer) | `POST /api/erp/masterdata/account-link-proposals/{proposalId}/revoke` | `POST .../{proposalId}/revoke` | **required** | **required** (≤256) |
  | unlink | `erp.write` + department data scope, **or** the linked account owner | `POST /api/erp/masterdata/employees/{id}/account-link/unlink` | `POST .../employees/{id}/account-link/unlink` | **required** | **required** (≤256) |

  Reads consumed alongside: `GET .../employees/{id}/account-link-proposals`
  (one employee's proposal history, department data scope) and
  `GET .../account-link-proposals/mine` (proposals addressed to the caller,
  no data scope) — same-origin GET proxies at the same paths. Employee list /
  detail responses carry `accountId` (ABSENT = not linked); the console renders
  it as «연결됨» / «연결된 계정 없음» and **never prints the raw account UUID as
  a reference label** (`TASK-PC-FE-309`; the id rides in `title` only, and as an
  explicit identifier field in the link-management dialog).

  - **Credential — UNCHANGED**: the same domain-facing IAM OIDC token as every
    erp call; **never** `getOperatorToken()`; no `X-Tenant-Id`. Accept /
    decline are authorised by the token's own `sub`, so the console needs no
    second credential for the account-owner writes.
  - **Account selection (TASK-MONO-777 — supersedes the TASK-PC-FE-318
    «typed input only» reading)**: the dialog offers **«이메일로 찾기»** over the
    IAM read `GET /api/admin/operators/lookup?email=&tenantId=<active tenant>`
    (iam `admin-api.md` § same name — owner decision 2026-10-08 UTC «L2»: no
    permission key, the same tenant gate as the accounts e-mail lookup). It
    returns the operators of the active tenant with that e-mail as
    `{ accountId, displayName, tenantId }` — `accountId` is the operator's
    `oidc_subject`, i.e. the `sub` the person carries into the console, which
    is exactly the value the link needs. (The IAM accounts e-mail lookup was
    measured NOT to answer this: it returns `account_db` rows of the tenant,
    and operators whose console credential lives in the `iam` tenant or the
    consumer pool have none there — root `TASK-MONO-777` § AC-0 (b).) Picking a
    result fills the account-id field; **typed input stays** as the fallback
    (form-checked only: non-blank, ≤64 — the producer checks form only). The
    dialog still states that existence is not checked at proposal time and the
    account owner must accept.

    | Operation | Who | Same-origin proxy (console) | Upstream (iam `admin-service`) | Credential |
    |---|---|---|---|---|
    | operator e-mail lookup | any console operator (no permission key) | `GET /api/operators/lookup?email=` | `GET /api/admin/operators/lookup?email=&tenantId=` | exchanged operator token (`getOperatorToken()`) + active tenant — the IAM `/api/admin/**` credential, NOT the domain-facing erp token |

    - **Existence non-disclosure**: the producer answers «no such operator»
      and «tenant out of scope» with the same `200 {"content":[]}`; the
      dialog renders both with ONE copy and never says which.
    - **No logout from the lookup**: a `403` (any code) or `401` from this read
      renders inline (typed input stays usable) — the proxy passes `403`
      through as `403` (never rewritten to `401`), and the client calls it
      with `skipAuthRetry` so even a `401` does not run the refresh → `/login`
      redirect. A real session expiry still surfaces on the next ordinary call.
    - **Several hits**: each row shows its `tenantId` (the operator's HOME
      tenant — an operator assigned from another tenant can appear beside a
      home operator with the same e-mail).
    - **Sample visitors**: the lookup answers `{ content: [] }` from the
      operators fixture (no sample operator carries a console `sub`).
  - **Errors rendered inline, each with its own copy** (never a crash):
    `403 EMPLOYEE_LINK_SELF_ACCEPT` (two-person rule — on propose: «your own
    account»; on accept: «the proposer cannot accept»), `403
    EMPLOYEE_LINK_NOT_ADDRESSEE`, `409 EMPLOYEE_LINK_CONFLICT` worded by
    `details.cause` (`employee_already_linked` / `account_already_linked` /
    `proposal_pending` / `proposal_not_pending` / `not_linked`), `422
    EMPLOYEE_LINK_INVALID` (`employee_not_active`), `404
    EMPLOYEE_LINK_PROPOSAL_NOT_FOUND`. `details` is passed through the
    same-origin proxy unchanged so the cause-specific copy is reachable.
  - **Sample visitors**: the buttons stay visible; every non-GET is refused
    server-side with `403 SAMPLE_READ_ONLY` (no client-side hiding). The two
    GET reads answer from the erp fixture.
  - **Approval v2.4 consumer obligations (same ticket)**: the inbox's
    `meta.actorEmployeeId` — **ABSENT** means «the caller's account is linked
    to no employee», which the console says in different words from «present,
    zero rows» (nothing to process). `403 APPROVAL_ACTOR_NOT_LINKED`, `422
    APPROVAL_APPROVER_UNLINKED` (worded with `details.stageIndex` — the stage
    whose approver has no linked account), `422 APPROVAL_ROUTE_INVALID` worded
    by `details.cause` (`approver_unresolved` / `self_approval` / …) each get
    their own copy; a `503` («could not ask who the caller is») is worded as a
    temporary outage and **never** as a defect of the user's data or link. The
    approver route input is an employee selector that marks employees without
    `accountId` «연결된 계정 없음» (selection is not blocked — the producer's
    submit-time refusal is the authority).

- **Employee pickers read the picker list (TASK-ERP-BE-045, 2026-10-20 UTC)**: the
  four employee selectors (`ApprovalCreateDialog` 대상 · 결재선, `DelegationScreen`
  대결자, `DelegationFactCard` 위임자 · 대행자) read masterdata
  `GET /employees/picker` (erp `masterdata-api.md` § employees/picker) through the
  BFF `GET /api/erp/masterdata/employees/picker` — **not** `GET /employees`, which
  stays the 직원 master screen's list. The picker opens to `ERP_MASTERDATA_READ ∨
  _WRITE ∨ ERP_APPROVAL_WRITE`, so the default «결재 운영» set gets a list; the
  «연결된 계정 없음» mark reads the element's `accountLinked` boolean.
  - The list is **scoped to the operator's department subtree** (`meta.scope =
    "SUBTREE"`), and an approver or delegate normally sits outside it. Every picker
    therefore offers **«ID 직접 입력»** — a toggle that swaps the select for the raw
    id input under the **same** `data-testid`. It is shown when the list is scoped
    or truncated; a `403` still degrades straight to the input (unchanged).
  - `403` now means «neither 기준정보 조회 nor 결재 변경» — the inline copy names
    both, not only 기준정보 조회.

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
> Phase 6 = COMPLETE; Phase 7 (cross-domain composition +
> dashboards) gate is **ungated to 5/5 domains live**
> (IAM + wms + scm + finance + erp).

#### 2.4.8.1 erp domain **overview + guide** — `/erp` landing, `/erp/guide` (TASK-PC-FE-232 — orthodox nav parity; PROMOTES + EXPANDS the TASK-PC-FE-161 masters-embedded overview)

erp nav gains the **개요 → 가이드 → 마스터 → 통합 조회 → 결재함 → 위임** child
order every other domain already has (IAM/WMS/SCM/E-Commerce/Finance: 개요 →
가이드 → 기능). The domain root `/erp` — formerly the § 2.4.8 masters surface
— is **repointed** at a live overview snapshot; the masters surface
**relocates** to `/erp/masters` (label `마스터` unchanged, nav testid
`nav-erp-masters` unchanged; the `/api/erp/masterdata/**` proxy and
`features/erp-ops` masters components are **unchanged** — a pure route
relocation). A static `/erp/guide` reference (erp-platform domain service map
+ the 6 console screens' meaning + effective-dating/결재 상태머신/위임/
read-model/알림 concepts) is added, mirroring `features/finance-guide`
(TASK-PC-FE-229) / `features/scm-guide` (TASK-PC-FE-188).

- **PC-FE-161 promotion (relocation, not deprecation).** The former embedded
  overview snapshot (5 masterdata counts, `meta.totalElements` with
  `?page=0&size=1`, `?asOf=` threading, per-cell degrade — THINNEST of the 4
  bff-domain overviews) is promoted verbatim into the standalone `/erp`
  landing and **expanded** with two new counts, both reusing EXISTING § 2.4.8
  reads (no new producer endpoint, ADR-MONO-017 D3.B):
  - **결재 대기** — the CALLER's own pending approval inbox
    (`GET /api/erp/approval/inbox`, already scoped to the caller's SUBMITTED
    queue) `meta.totalElements` with `?page=0&size=1`.
  - **활성 위임** — ACTIVE delegation-fact grants
    (`GET /api/erp/read-model/delegations?status=ACTIVE`, org-scope aware)
    `meta.totalElements` with `?page=0&size=1`.
  - **결재 상태 분포 (TASK-PC-FE-360)** — `GET /api/erp/approval/requests?status=<S>&page=0&size=1`
    `meta.totalElements` for each of the 6 statuses (`DRAFT` · `SUBMITTED` · `IN_REVIEW` · `APPROVED` ·
    `REJECTED` · `WITHDRAWN`) — exactly **6** extra reads of the existing § 2.4.8 approval list (the
    `status` filter is already bound). The 결재 대기 tile above stays on `/inbox` (the caller's own
    queue) and is NOT reused: `/requests?status=SUBMITTED` is a different population. The producer
    scopes `/requests` without `role` to the **tenant-wide** list for an operator / platform actor, and to
    the caller's own submitter-or-approver rows otherwise (`ApprovalApplicationService.list`), so for a
    non-operator caller the distribution is «내가 참여한 결재» — the overview says so. Each status is its
    own cell; the stacked bar is drawn ONLY when all 6 are `ok`; current-time counts (no `asOf`).
  - **결재 실적 («실적» strip) — ADR-MONO-084 P4 / TASK-MONO-809** — one more read, op row:

    | # | Op | Endpoint (erp `approval-api.md`) | Kind |
    |---|---|---|---|
    | A1 | approval performance metrics («실적» strip) | `GET /api/erp/approval/metrics?period=week\|month` | read |

- **Performance metrics strip (A1) — ADR-MONO-084 P4 / TASK-MONO-809.**
  `GET /api/erp/approval/metrics?period=week|month` feeds the «실적» strip, the
  **first** block of the `/erp` overview (above the title and the count tiles): 상신
  (`submitted`) · 승인 (`approved` — final approval only) · 반려 (`rejected`), all
  `count`, current vs the same-length previous KST window, with a daily series (the
  chart draws `submitted`). Shape, windows and error handling are
  [`platform/contracts/performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md).
  **Scope follows the approval list rule** (producer-side): operator / platform →
  the whole tenant, otherwise only requests the caller takes part in (submitter or
  approver), an unlinked caller → zeros; the strip carries a one-line scope note in
  the same words as the 결재 상태 block. Parsed by `shared/api/performance-metrics.ts`
  (integer strings — F5) as its **own cell** inside `getErpOverviewState`: `403` →
  «권한 없음» in the strip, a non-200 (incl. `400 VALIDATION_ERROR`) or an
  unparseable body → «실적을 불러올 수 없습니다», while every other block renders; a
  `401` → the same whole-session re-login; not eligible → no call. The page reads
  `?period=` (default `month`; an absent or unknown value falls back to the default
  — never a 400 page) and passes it as the third argument
  (`getErpOverviewState(eligible, asOf, period)`).
  **`?asOf=` rule (TASK-MONO-809 AC-0):** `asOf` applies **only** to the masterdata
  counts; the strip is a *period* axis and always shows the «up to now» windows. The
  period links keep an existing `asOf` (`/erp?asOf=…&period=…`), and when `asOf` is
  present the strip shows the note «실적 줄은 기준일(asOf)과 관계없이 오늘까지의
  기간입니다.» (`erp-performance-asof-note`); absent otherwise. Sample mode answers
  the same path from the approval seed (`submittedAt` · final status at `finalizedAt`).
- **Read model (console-web DIRECT fan-out, unchanged model).** Reuses the
  existing § 2.4.8 master `list*` reads + the approval inbox read + the
  read-model delegation-facts read server-side (`getDomainFacingToken()`);
  **no § 2.4.9 composition leg.**
- **E3 (§ 2.4.8) — masterdata legs only.** An optional `?asOf=` threads
  through every MASTERDATA count leg verbatim (state-at-that-instant,
  matching the masters section state); `active` is omitted so retired
  masters are counted too (E2 honesty — the true total). The approval /
  delegation counts are **current-time only** — no asOf concept (a pending
  inbox / an active grant has no historical point-in-time reading in the
  console's read surface).
- **Independent degrade (the decisive resilience rule, unchanged shape).**
  Each of the 7 count legs (and each of the 6 status-distribution legs) runs in its OWN try/catch (the `cell()` pattern) —
  a `503`/timeout/`403` in ANY ONE leg (e.g. approval-service down) degrades
  ONLY that tile; every sibling tile renders normally. A `401` in ANY leg
  triggers a whole-session `redirect('/login')` (no partial authed state).
  Unlike the finance § 2.4.7.2 overview (whose two legs share ONE
  domain-facing credential across TWO producer services and therefore
  collapse a 403 to one whole-overview `forbidden`), erp's 7 legs are all
  the SAME masterdata/approval/read-model producers already bound by
  § 2.4.8 under the SAME credential — the per-cell shape is kept
  (unchanged from PC-FE-161) rather than introducing a page-level
  `forbidden`/`degraded` branch.
- **No aggregation endpoint (ADR-MONO-017 D3.B).** Counts from
  `totalElements`; no producer `/summary`, no producer retrofit.
- **Deviation vs ecommerce (§ 2.4.10.6).** Count tiles are **read-only stat
  tiles, NOT nav links** — the overview's shortcut nav (separate from the
  tiles) links to 가이드/마스터/통합 조회/결재함/위임.
- **Legacy `/erp` bookmark honesty.** Since masters relocated to
  `/erp/masters`, the overview always renders a `마스터` shortcut link so the
  relocation is never a dead end (mirrors PC-FE-229's `/finance?accountId=`
  treatment, without needing to carry a query param — erp masters have no
  id-driven bookmark state to preserve).
- **Nav.** `ConsoleSidebarNav` ERP drill children reorder to `{ href: '/erp',
  label: '개요' }, { href: '/erp/guide', label: '가이드' }, { href:
  '/erp/masters', label: '마스터' }, { href: '/erp/orgview', label: '통합
  조회' }, { href: '/erp/approval', label: '결재함' }, { href:
  '/erp/delegation', label: '위임' }`. The existing longest-match
  `activeHref` logic (unchanged code) keeps `/erp`(개요) from lighting up on
  `/erp/masters`(마스터) — a nav test pins this.
- **Resilience (§ 2.5).** Per-cell degrade cell-local; a `401` in ANY leg →
  whole-session `redirect('/login')`. Read-only; no auto-refetch.

> **Not a § 3 parity row**: consumes only already-listed § 2.4.8 endpoints in a
> new read composition; adds no § 3 row and changes none.

#### 2.4.9 Cross-domain composition surface — produced in the `console-web` server (TASK-PC-BE-001 → [ADR-MONO-081](../../../../docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md); first composition surface owner, **not** a cross-reference)

The **first composition-owned** entry in § 2.4 (ADR-MONO-013 Phase 7, governed by
ADR-MONO-017 (`docs/adr/`) ACCEPTED 2026-05-20; producer placed by ADR-MONO-081 ACCEPTED 2026-10-02, A).
Unlike §§ 2.4.1–2.4.8, **this section is not a cross-reference to a producer in
another project** — it is the contract surface this project owns and exposes.
The producer of these routes is the **`console-web` server**: the App Router
route handlers under `apps/console-web/src/app/api/console/dashboards/` compose
the response themselves through `src/shared/composition/console-composition.ts`.
There is no second hop — the browser calls the same-origin route, and that route
calls the domains. **The server-rendered pages do not call the route at all**
(TASK-PC-FE-350): the SSR entry points (`getOperatorOverviewState`,
`getDomainHealthState`) call the **same server-only composition function** the
route handler calls (`src/shared/composition/{operator-overview,domain-health}-composition.ts`),
in-process, reading the same session cookies through `next/headers`. The route
stays for the browser (`<RetryButton>` / React Query) and its wire shape is the
one this section defines; the SSR path is not a second wire — it is the route's
own body without the HTTP round trip. (Before TASK-PC-FE-350 the pages fetched
the route over HTTP at this app's own origin; that inherited hop added a name
lookup and undici's 10 s connect timeout, which stalled behind a saturated libuv
threadpool — measured in TASK-PC-FE-350 § AC-0.) The **consumed-from-this-section** legs (e.g.
`GET /api/admin/accounts` count, wms inventory health, …) remain authoritatively
owned by their respective domains and are **NOT redefined here**
(ADR-MONO-017 D3.A / § 3.3 "zero retrofit", fifth confirmation across the
portfolio).

> **History (why the producer sits where it does).** These routes were first
> produced by a separate Spring Boot BFF service in this project (skeleton
> TASK-PC-BE-001, routes TASK-PC-FE-011 / TASK-PC-FE-013). That service had no
> public hostname (TASK-MONO-362 removed its edge router — a backend service may
> not sit on the edge, `platform/api-gateway-policy.md` L13/L14), so the
> Vercel-hosted console could never reach it and the composed panels always
> showed «unavailable» there. ADR-MONO-081 moved the producer into the
> console-web server (`TASK-PC-FE-302` for the two dashboards, `TASK-PC-FE-303`
> for the notification inbox) and `TASK-MONO-757` retired the former BFF.
> **The wire shape did not change** (ADR-MONO-081 D1): request
> path and method, the `200` envelope (`asOf`, `cards[]` in fixed order,
> per-card `status` ∈ `{ok, degraded, forbidden}`, `reason` vocabularies,
> `data` = the producer's body verbatim), the composition-level error envelope
> and the all-down `200` rule are what §§ 2.4.9.1 and 2.4.9.2 state. The
> machine-readable leg bodies in
> [`fixtures/operator-overview-leg-bodies.json`](fixtures/operator-overview-leg-bodies.json)
> are the single source both the card renderer and the composer are tested
> against.

##### Hard invariants this composition surface inherits (HARD INVARIANT — ADR-MONO-017 D4, carried across the move by ADR-MONO-081 D2)

> **The per-domain credential rule defined in §§ 2.4.5 / 2.4.6 / 2.4.7 / 2.4.8
> (and the § 2.6 RFC 8693 exchanged operator token for the IAM-domain leg) is
> a HARD INVARIANT. The composer is the rule's *credential dispatcher*, never
> its rewriter.** Re-introducing the rejected ADR-MONO-017 D4.B (single
> unified composition token) or D4.C (operator-token-only across all domains)
> on any future § 2.4.9.X composition route is a contract defect.

- **Inbound**: the browser's same-origin request to the route — or, for the
  server-rendered page, that page's own request (TASK-PC-FE-350, above) —
  carrying only the console session cookie. Every credential is read **server-side** from the
  session established at login (per
  [`console-web/architecture.md`](../services/console-web/architecture.md) +
  FE-002a) — `getActiveTenant()`, `getAccessToken()`, `getOperatorToken()`,
  `getDomainFacingToken()`. No token is accepted from, or exposed to, the
  browser.
- **Before any leg is called** (each asserted by a test that counts **zero**
  leg calls — `tests/unit/dashboard-composition-routes.test.ts`):
  - no active tenant → `400 NO_ACTIVE_TENANT`;
  - a session token the route needs is absent → `401 TOKEN_INVALID`.
- **Outbound per-domain credential dispatch (verbatim from §§ 2.4.5/6/7/8/10 +
  § 2.6)** — `operatorOverviewLegs()` in `console-composition.ts`:

  | Outbound leg | Credential | Tenant header | Session source |
  |---|---|---|---|
  | IAM | RFC 8693 exchanged **operator** token (§ 2.6) | `X-Tenant-Id: <active tenant>` | `getOperatorToken()` + `getActiveTenant()` |
  | wms | domain-facing IAM OIDC access token (§ 2.4.5) | **none** — tenant from the token's `tenant_id` claim | `getDomainFacingToken()` |
  | scm | domain-facing IAM OIDC access token (§ 2.4.6) | none | `getDomainFacingToken()` |
  | finance | domain-facing IAM OIDC access token (§ 2.4.7) | none | `getDomainFacingToken()` |
  | erp | domain-facing IAM OIDC access token (§ 2.4.8) | none | `getDomainFacingToken()` |
  | ecommerce (via ecommerce gateway) | domain-facing IAM OIDC access token (§ 2.4.10) | none | `getDomainFacingToken()` |

  Every leg also sends `Accept: application/json` and the route's
  `X-Request-Id`. The composer **never** falls back from one credential to
  another (#569 invariant preserved), **never** mints its own token, and
  **never** rewrites or expands the per-domain producer-side tenant enforcement
  (D6.A — producer authority preserved). Asserted per leg by
  `tests/unit/shared/console-composition.test.ts` («IAM = operator token +
  X-Tenant-Id; every other leg = domain-facing token, no X-Tenant-Id»). Health
  legs (§ 2.4.9.2) send **no** credential at all.
- **Tenant authority (D6.A)**: each producer's `TenantClaimValidator`
  (`tenant_id ∈ {<domain>,*}`, entitlement-trust dual-accept where the producer
  has it) remains the authoritative gate. The composer performs no tenant
  re-derivation, no widening, no central gate (D6.B rejection); the only tenant
  header it sends is the IAM leg's `X-Tenant-Id`, exactly as the IAM console
  screens send it.
- **Which address each leg uses.** A leg calls the domain through the **same
  base URL + path the domain's own console screen already uses**
  (§§ 2.4.1–2.4.10 — e.g. scm through `SCM_GATEWAY_BASE_URL` +
  `/api/v1/procurement/po`; finance's ledger read through `LEDGER_BASE_URL`), and the route's `fetch` resolves it
  through the demo backend resolver (`resolveBackendUrl`) at the call site,
  exactly like those clients do. Those addresses are the ones proven to reach
  the domains from where console-web actually runs, including Vercel
  (ADR-MONO-067). 🔴 Docker-network-only direct-to-service addresses are **not**
  reachable from Vercel and must not be used for a leg. Each leg's `data` body
  must still equal the fixture shape — a gateway path is not automatically the
  same body as a service path, so it is asserted per leg.
- **Read-only at MVP — mutations forbidden**: every Phase 7 dashboard at MVP
  is composition of **reads** (D3.A). No § 2.4.9.X composition route is a
  mutation; therefore **no** `Idempotency-Key` / `X-Operator-Reason` /
  destructive-confirm scaffolding applies at the composition layer. Adding a
  mutation surface requires a fresh ADR amendment to ADR-MONO-017.
- **No producer retrofit (D3.A / § 3.3 zero retrofit fifth confirmation)**:
  every § 2.4.9.X composition route fans out across **existing** per-domain
  read endpoints unchanged. Aggregating producer endpoints per domain (`/summary`
  / `/dashboard-card`) — ADR-MONO-017 D3.B rejection — are NOT introduced.
- **Sample visitor (ADR-MONO-074 A2)**: each route asks `sampleGate(` **first**,
  before any tenant or token read. A sample visitor gets the sample envelope and
  **no leg is called**; an unexpected sample answer is `502 BAD_GATEWAY`.

##### Resilience (D5.A — per-domain isolation; ADR-MONO-081 R2)

- **Per-leg isolation**: legs run in parallel and independently
  (`Promise.allSettled`); one leg failing (timeout / 5xx / network / unreadable
  body) degrades **that card only** — `{ status: "degraded", domain, reason }`
  with `reason` ∈ `{ "TIMEOUT", "DOWNSTREAM_ERROR" }`. A leg that rejects
  unexpectedly degrades its own card (`DOWNSTREAM_ERROR`).
- **Per-leg timeout `LEG_TIMEOUT_MS = 4000`** (`console-composition.ts`). Legs
  run in parallel, so it also bounds the composition.
  - **What was measured**: Vercel's documented function limit — 300 s default
    on every plan with fluid compute (Vercel docs «Maximum Duration», fetched
    2026-10-02, page last updated 2026-08-24).
  - 🔴 **What was not**: this project's dashboard override — no Vercel CLI or
    API access from the repo, and `console-web` sets no `maxDuration`. The value
    is therefore sized to hold under the most conservative limit Vercel has
    shipped (10 s) as well, leaving room for the session reads and the response.
  - No read retry: one 4 s attempt is inside the former BFF's worst case
    (2 s leg × bounded retry inside a 5 s composition budget = 4.15 s), and a
    retried slow leg would only stretch the response. No retry on any leg that
    writes.
- **No circuit-breaker (ADR-MONO-081 R2)**: a serverless function holds no
  state between requests, so there is nowhere to keep breaker state.
  `reason: "CIRCUIT_OPEN"` therefore stays in the vocabulary but is **never
  produced** (asserted — `console-composition.test.ts` «never produces
  CIRCUIT_OPEN»); consumers must not break on its absence or its presence.
- **Aggregation degrade discipline** — partial-failure composition rendering:
  every responsive leg's data + per-failed-leg card. **All-down still returns
  200 with an all-degraded envelope** (and logs one
  `console_composition_all_down` warning) — composition routes never blank the
  dashboard. ADR-MONO-017 D5.B (all-or-nothing 503) is rejected.
- **`401` discipline (cross-leg)**: `401` on **any data** leg surfaces as
  whole-composition `401 TOKEN_INVALID` (auth is **not** a per-card degrade —
  an expired session must not hide as a partial outage; mirrors § 2.4.4 D3).
  The `401` is classified **before** the per-card degrade path.
- **`403` discipline (per-leg)**: `403` on a data leg renders as a per-card
  `forbidden` with `reason` `TENANT_FORBIDDEN` when the producer's body says
  so, otherwise `PERMISSION_DENIED` (classification `forbidden`, distinct from
  `degraded`). Mirrors the per-card isolation of § 2.4.4.
- **Health legs**: any non-success, `401`/`403` included, degrades that card —
  except a `503` whose body is a real health document, which is `ok`
  (§ 2.4.9.2).

##### Observability (ADR-MONO-017 D7 per-leg attribution; ADR-MONO-081 R1)

Per-leg attribution is mandatory on every § 2.4.9.X composition route (no
opt-out) — ADR-MONO-017 D7.B (aggregate-only signals) stays rejected: an
operator must be able to diagnose which leg degraded. It is carried by two
signals:

- **One structured log line per leg** (R1 — the console-web server has no
  metrics scrape path and none is added, so no metric family is emitted):

  | Field | Value |
  |---|---|
  | `msg` | `console_composition_leg` (level `info` when `ok`, `warn` otherwise) |
  | `route` | `operator-overview` \| `domain-health` \| `notifications-inbox` \| `notifications-read` |
  | `domain` | `iam` \| `wms` \| `scm` \| `finance` \| `erp` \| `ecommerce` \| (notification `sourceDomain`) |
  | `status` | the leg outcome: `ok` \| `degraded` \| `forbidden` \| `unauthorized` |
  | `reason` | the card `reason` when not `ok` (mark-read: `HTTP_<status>`, `TIMEOUT`, `DOWNSTREAM_ERROR`) |
  | `latencyMs` | integer |
  | `requestId` | the route's request id |

- **One trace span per leg**: each leg runs inside an active span
  `console.composition.leg` with attributes `composition.domain`,
  `composition.route` and, on completion, `composition.outcome`
  (`ok` \| `degraded` \| `forbidden` \| `unauthorized`; mark-read `ok` \|
  `http_<status>`). The auto-instrumented `fetch` client span and the
  producer's server span join it under the request's trace
  (`tests/federation-hardening-e2e` gates this). With no OTel SDK registered the
  API is a no-op.

##### Logging discipline (inherited)

Tokens (session tokens, all outbound bearer values), PII (account ids, masked
IPs, operator emails, money minor-units strings, employee / business-partner
financial fields) MUST NOT appear in logs — the per-leg line carries no token,
no account id and no body. Inherits the § 2.6 logging invariant + the
per-domain producer obligations (e.g. finance F7, erp E7).

##### Edge routing (Local Network Convention)

The composer is `console-web` itself, behind the Traefik-fronted `console.local`
host (locally) or Vercel. A browser-facing frontend **is** the external client,
so it is not "a backend directly exposed" — no exception to `rest-api.md`'s
"all external traffic enters through gateway-service" is needed or claimed, and
no backend service of this project sits on the edge (`platform/api-gateway-policy.md`
L13/L14). `platform-console` owns no `gateway-service` (ADR-MONO-013 Model B)
and needs none. Backend services anywhere in the monorepo holding a Traefik
router are caught by `scripts/check-gateway-drift.sh` (I2).

> **Phase 7 MVP "Operator Overview" composition route** is § 2.4.9.1 below
> (TASK-PC-FE-011); "Domain Health Overview" is § 2.4.9.2 (TASK-PC-FE-013).

> **Not a § 3 parity row**: like §§ 2.4.5–2.4.8, this composition section has
> **no** § 3 line. § 3 is the IAM `admin-web` absorption parity gate (FE-006
> finalized, 16 rows, immutable until a future ADR-amendment); composition
> routes are **additive** to the operator surface, never replace a parity
> row.

#### 2.4.9.1 `GET /api/console/dashboards/operator-overview` — MVP "Operator Overview" composition route (TASK-PC-FE-011)

The **first concrete `§ 2.4.9.X` composition route**. Governed by
ADR-MONO-017 § D8 (Phase 7 MVP = 1 "Operator Overview"); produced by the console-web route
`src/app/api/console/dashboards/operator-overview/route.ts` (§ 2.4.9). This
sub-section is **additive** to § 2.4.9 — all hard invariants, auth flow,
resilience, observability, logging, and edge-routing constraints declared in
§ 2.4.9 apply verbatim and are **not re-derived** here. ADR-MONO-013 § 3.3
"zero retrofit" — **sixth confirmation** (Phase 2/4/5/6/7-skeleton/7-MVP across
the portfolio).

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
> surfaces with their own leg lists (`operatorOverviewLegs()` /
> `domainHealthLegs()`), sharing one fixed card order (`CARD_ORDER`).
>
> **UI routing note (TASK-PC-FE-034, 2026-06-02; additive — composition body byte-unchanged).** This composition route is the console **landing/home** (the authenticated root `/` lands on `/dashboards/overview`; the single "개요" top-nav entry points here). The **IAM card** in the rendered envelope is an accessible **drill-down link to the IAM-only composed overview** (`/dashboards`, § 2.4.4 / ADR-MONO-015 D1-B) — the accounts/audit/operators 3-leg detail. This note governs **only** the consumer-side (`console-web`) landing + nav + IAM-card link wiring; the response envelope, per-card status discipline, auth flow, resilience, observability, and the read-only/no-mutation hard invariant of this route are **unchanged**. See ADR-MONO-017 § D8 amendment + ADR-MONO-015 § 6 amendment.

##### Surface

| # | Method / Path | Purpose | Auth | Producer |
|---|---|---|---|---|
| 1 | `GET /api/console/dashboards/operator-overview` | Single composed cross-domain dashboard envelope; one card per backend domain (IAM + wms + scm + finance + erp + ecommerce); each card carries the per-leg outcome (`ok` / `degraded` / `forbidden`) per § 2.4.9 D5.A discipline | Same-origin request with the console session; the route reads the active tenant, the IAM OIDC access token, the RFC 8693 operator token (IAM leg) and the domain-facing token (other legs) **server-side** — never from the browser. No active tenant → `400 NO_ACTIVE_TENANT`; any of the three tokens absent → `401 TOKEN_INVALID`; both before any outbound leg | `console-web` server |

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

| # | Card | Composed producer endpoint (the address the domain's own console screen uses) | Domain credential (§ 2.4.9 D4) | Producer spec § (authoritative) | Read content surfaced |
|---|---|---|---|---|---|
| 1 | locked accounts | `GET ${IAM_ADMIN_API_BASE}/api/admin/accounts?status=LOCKED&page=0&size=1&tenantId={activeTenant}` (page total) | RFC 8693 exchanged **operator** token (§ 2.6) — `getOperatorToken()` + `X-Tenant-Id` (header **and** `tenantId` query param — TASK-PC-FE-304: the producer's `AccountAdminController#search` resolves the scope tenant EXCLUSIVELY from the `tenantId` query param, `QueryTenantScopeGate` falling back to the operator's HOME tenant when it is absent; the header alone is not read by this endpoint) | IAM [`admin-api.md`](../../../iam-platform/specs/contracts/http/admin-api.md) § Accounts (already bound by § 2.4.1 / FE-002 + the composed-overview pattern of § 2.4.4 / FE-005; `status` filter = TASK-BE-475) | **account-level** `LOCKED` count of the active tenant (its own + pool-member end-user accounts; excludes `admin_operators`). A pool account locked out of one **site** (membership `LOCKED`, account `ACTIVE`) is **not** counted — the filter is `a.status`, not the membership status (`AccountJpaRepository` site query) |
| 2 | unshipped orders | `GET ${WMS_ADMIN_BASE_URL}/dashboard/orders?status=RECEIVED&page=0&size=1` (default `http://wms.local/api/v1/admin/dashboard/orders`) | **domain-facing IAM OIDC access token** — `getDomainFacingToken()` (per § 2.4.5 verbatim) | wms [`admin-service-api.md`](../../../wms-platform/specs/contracts/http/admin-service-api.md) § 1.3 Orders (already bound by § 2.4.5 / FE-007) | read-model orders in `RECEIVED` = received and neither shipped nor cancelled (the projection folds the order lifecycle to `RECEIVED` / `SHIPPED` / `CANCELLED`). **Tenant-owned** dashboard (§ 1.0, ADR-MONO-065 D1): the native wms plane sees every row; a customer tenant sees only its own rows, so `0` for it is a real answer |
| 3 | draft purchase orders | `GET ${SCM_GATEWAY_BASE_URL}/api/v1/procurement/po?status=DRAFT&page=0&size=1` — through the scm gateway, the same route the § 2.4.6 procurement screen uses (see scm-leg topology note below) | **domain-facing IAM OIDC access token** — `getDomainFacingToken()` (per § 2.4.6 verbatim) | scm [`procurement-api.md`](../../../scm-platform/specs/contracts/http/procurement-api.md) § `GET /po` (already bound by § 2.4.6 / FE-008) | POs in `DRAFT` (not yet submitted to a supplier) of the **caller's** tenant — the service scopes by the JWT `tenant_id` claim (`ActorClaims` → `ActorContext.tenantId()` → `poRepository.search(tenantId, …)`); there is no fixed tenant on this read |
| 4 | open reconciliation discrepancies | `GET ${LEDGER_BASE_URL}/api/finance/ledger/reconciliation/discrepancies?status=OPEN&page=0&size=1` | **domain-facing IAM OIDC access token** — `getDomainFacingToken()` (per § 2.4.7 verbatim) | finance [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) § discrepancy queue (already bound by § 2.4.7.1 / FE-075) | `OPEN` discrepancies of the caller's tenant (`meta.totalElements`). 🔵 **No operator prerequisite**: this read needs no default account, so the card is called for every operator (TASK-PC-FE-364 retired the `MISSING_PREREQUISITE` path on this route) |
| 5 | my approval inbox | `GET ${ERP_BASE_URL}/api/erp/approval/inbox?page=0&size=1` | **domain-facing IAM OIDC access token** — `getDomainFacingToken()` (per § 2.4.8 verbatim) | erp [`approval-api.md`](../../../erp-platform/specs/contracts/http/approval-api.md) § `GET /inbox` (already bound by § 2.4.8 / FE-051) | requests whose **current** stage's approver is the caller's linked employee, status ∈ {`SUBMITTED`, `IN_REVIEW`} (`meta.totalElements`). 🔴 **`meta.actorEmployeeId` absent = the caller's account is linked to no employee** (approval-api § v2.4): the page is empty but that is «not linked», never «nothing waiting» — the card renders a distinct state and **never `0`** for it |
| 6 | orders to ship | `GET ${ECOMMERCE_ADMIN_BASE_URL}/orders?status=CONFIRMED&page=0&size=1` (default `http://ecommerce.local/api/admin/orders`) — routed **through** the ecommerce gateway (see ecommerce-leg topology note below) | **domain-facing IAM OIDC access token** — `getDomainFacingToken()` | ecommerce [`order-api.md`](../../../ecommerce-microservices-platform/specs/contracts/http/order-api.md) § `GET /api/admin/orders` (already bound by § 2.4.10 / FE-083) | orders in `CONFIRMED` (payment confirmed, shipping not started) of the tenant (`totalElements`). `STUCK_RECOVERY_FAILED` is **not** used — no code path records it today, so it would always read 0 |

**Producer immutability**: every producer contract above is **byte-unchanged
spec-side and impl-side** (ADR-MONO-017 § 3.3). Each row is an **existing**
list read that the domain's own console screen already calls, asked with
`size=1` so only its page total is used; no `/summary` / `/dashboard-card`
aggregating endpoint is added to any producer (D3.B rejection). The composer
calls the existing GETs verbatim.

> **Card metrics swapped one-for-one (TASK-PC-FE-364, 2026-10-22 UTC — owner
> decision 2026-10-22 KST).** Before 364 the six cards counted rows the
> operator could not act on — accounts · inventory snapshot rows · scm
> snapshot rows · «balance available» for the default account · active
> departments · products. Each leg was **replaced**, not added to: still **one
> call per card**, still six calls in total, and the same credential per leg
> (§ 2.4.9 table). That is the «one call per card» rule below (owner,
> `TASK-PC-FE-296`) applied unchanged — 364 changed which call, never how many.
> What did change per operator is the **business permission** each producer
> asks for (ADR-MONO-083): the scm, finance, erp and ecommerce reads are gated
> on a different role than the reads they replaced, so an operator holding a
> narrow permission set can see a card turn `forbidden` that used to be `ok`,
> or the reverse. The per-set table is recorded in `TASK-PC-FE-364`
> (§ 권한별 «권한 없음» 변화); a `403` still renders per card as `forbidden`,
> exactly as before.

> **accounts-leg tenant scoping fix (TASK-PC-FE-304, 2026-10-04 — additive,
> card 1 query string only).** `AccountAdminController#search` resolves the
> scope tenant **exclusively** from the `tenantId` query parameter —
> `QueryTenantScopeGate` falls back to the operator's HOME tenant when it is
> absent (it does **not** read `X-Tenant-Id`). Card 1 sent only the header, so
> switching the active tenant moved cards 2–6 but left the accounts card
> pinned to the operator's home tenant (e.g. a `demo-corp`-home operator
> switched to `ecommerce` still saw the `demo-corp` count). The accounts
> screen's own read (`searchAccounts`, TASK-BE-357) already appends
> `tenantId`; this leg now matches it. `0` for a tenant that owns no
> end-user accounts (operators live in `admin_operators`, consumers in the
> shared pool) remains a correct, **by-design** answer — the defect was the
> wrong tenant being counted, not the zero itself. The `X-Tenant-Id` header
> is kept (harmless, and other IAM reads on this leg's credential path do
> consult it) — this is additive, not a header removal.

> **scm-leg topology (ADR-MONO-081 «Which address each leg uses»; supersedes the
> TASK-MONO-162 direct-to-producer reading).** 🔵 Since TASK-PC-FE-364 card 3
> reads `/api/v1/procurement/po` through the same scm gateway, the route the
> § 2.4.6 procurement screen uses; the paragraph below is the history of the
> previous inventory-visibility leg and its reasoning holds for this one too
> (gateway, never the docker-only service address). Card 3 went **through the scm
> gateway** (`/api/v1/inventory-visibility/snapshot`), the route § 2.4.6 and
> `gateway-public-routes.md` already bind for the console. The former BFF called
> the inventory-visibility service directly (`/api/inventory-visibility/snapshot`)
> because the scm gateway's `TenantClaimValidator` was then not
> entitlement-trust-aware (ADR-MONO-019 § D5) and would have rejected an
> assume-tenant token at the gateway. That reason no longer holds — the scm
> gateway's tenant gate now dual-accepts entitled domains
> (`OAuth2ResourceServerConfig.tenantGate()` `.trustEntitledDomains()`) — and the
> direct service address is docker-network-only, so it cannot be reached from
> Vercel. The leg's `data` must still be the scm body in the fixture (whose
> `producer` string names the service path the shape was transcribed from — the
> gateway route is a pass-through to that service).

> **ecommerce-leg topology (TASK-MONO-243 — ADR-MONO-030 Step 4 facet a-후속-2).**
> 🔵 Since TASK-PC-FE-364 card 6 reads `/api/admin/orders` (order-service) through
> the same gateway; the gateway admits on the derived entry role and
> **order-service re-checks `ECOMMERCE_ORDER_READ ∨ _WRITE`** (or the platform
> wildcard tenant `*`) itself (`OrderOperatorAccess`, TASK-MONO-795) — the
> «gateway is the single admission point» wording below predates that. The
> rest of this note is the product-leg history.
> Card 6 routes **through the ecommerce gateway** (`ecommerce.local`, the same
> gateway origin the § 2.4.9.2 health leg uses and the same
> `ECOMMERCE_ADMIN_BASE_URL` the § 2.4.10 product screens use). Rationale:
> ecommerce `product-service` is a
> **header-trust** service, **not** a JWT resource server — it reads a trusted
> `X-Tenant-Id` injected upstream and does not itself validate bearer tokens.
> The gateway is therefore the authorization boundary: it validates the IAM
> OIDC access token, enforces `roles ∋ ECOMMERCE_OPERATOR` for `/api/admin/**`
> (`AccountTypeEnforcementFilter`), requires a non-blank `tenant_id`
> (`TenantClaimValidator`), injects the trusted `X-Tenant-Id`
> (`JwtHeaderEnrichmentFilter`), and strips inbound client headers. Routing the
> console leg direct-to-`product-service` would force the composer to fabricate
> `X-Tenant-Id` / `X-User-*` and bypass the gateway's `roles ∋ ECOMMERCE_OPERATOR` + JWT
> validation — a security smell. The `product-service` `GET /api/admin/products`
> read is gated at the gateway on `roles ∋ ECOMMERCE_OPERATOR`, **uniformly** with the
> `/api/admin/products/**` write endpoints: the operator's IAM OIDC token carries
> the `ECOMMERCE_OPERATOR` domain role **derived at assume-tenant** from the selected
> (ecommerce-entitled) tenant (ADR-MONO-035 4a) — no ecommerce-local `ECOMMERCE_OPERATOR`
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
    { "domain": "iam",       "status": "ok",         "data": { "content": [ … ], "totalElements": 3, "page": 0, "size": 1, "totalPages": 3 } },
    { "domain": "wms",       "status": "ok",         "data": { "content": [ … ], "page": { "number": 0, "size": 1, "totalElements": 14, "totalPages": 14 }, "sort": "receivedAt,desc" } },
    { "domain": "scm",       "status": "degraded",   "reason": "DOWNSTREAM_ERROR" },
    { "domain": "finance",   "status": "forbidden",  "reason": "PERMISSION_DENIED" },
    { "domain": "erp",       "status": "ok",         "data": { "data": [ … ], "meta": { "page": 0, "size": 1, "totalElements": 2, "totalPages": 2, "timestamp": "…", "actorEmployeeId": "emp-…" } } },
    { "domain": "ecommerce", "status": "ok",         "data": { "content": [ … ], "page": 0, "size": 1, "totalElements": 5 } }
  ]
}
```

> 🔵 Rewritten for TASK-PC-FE-364 from the six producers' controllers/DTOs
> (the bodies in the fixture file below), following the rule in the next
> paragraph. `ecommerce` has **no** `totalPages` (`AdminOrderListResponse`
> carries `content, page, size, totalElements` only).

> 🔴🔴 **This example was wrong until TASK-PC-FE-295, and the wrongness shipped.**
> It previously showed `data: { "accountCount": 12345 }` for iam,
> `data: { "inventorySnapshot": { … } }` for wms and
> `data: { "activeDepartmentCount": 87 }` for erp — **three shapes no producer
> has ever emitted**. console-web's card schemas were written against this
> example rather than against the producers, so `WmsDataSchema` read
> `inventorySnapshot.totalStockUnits` and `FinanceDataSchema` read
> `balance.amount`, and the cards rendered «—» / «잔액 정보 없음» for a logged-in
> operator whose domains were healthy. The consumer implemented the contract
> faithfully; the contract was the defect. **An illustrative body in this file
> is normative for shape** — write it from the producer's controller/DTO, never
> from what a card would find convenient.

- `asOf`: composition request server-side timestamp (ISO-8601 UTC). Operators see "data as-of HH:MM:SS" in the UI.
- `cards[]`: **exactly 6 entries** in **fixed order** `[iam, wms, scm, finance, erp, ecommerce]` (UI rendering ordering invariant; never reordered by status).
- `cards[i].status` ∈ `{ "ok", "degraded", "forbidden" }`:
  - `ok` → `data` is the card's composed payload (domain-specific shape, declared per row in the producer endpoint above).
  - `degraded` → `reason` ∈ `{ "DOWNSTREAM_ERROR", "TIMEOUT", "CIRCUIT_OPEN" }` (`CIRCUIT_OPEN` stays in the vocabulary but is never produced — § 2.4.9 Resilience); `data` absent. Card renders "data unavailable, retry pending" placeholder.
  - `forbidden` → `reason` ∈ `{ "PERMISSION_DENIED", "TENANT_FORBIDDEN", "MISSING_PREREQUISITE" }`; `data` absent. Card renders "not available to your role / tenant" placeholder. `MISSING_PREREQUISITE` stays in the vocabulary but this route **no longer produces it** (TASK-PC-FE-364 — it used to mean «finance `operatorDefaultAccountId` absent», and the finance card no longer needs one); consumers must not break on its absence or its presence.
- `cards[i].headline` (optional, TASK-PC-FE-368): the card's headline performance metric and its own outcome — `ok` / `degraded` / `forbidden`, independent of `status`. See «Card headline metric» below.
##### `ok` card `data` shapes per leg (TASK-PC-FE-295)

`data` on an `ok` card is **the producer's response body, verbatim** — the
composer parses each `ok` leg's JSON and puts it on the card without
reshaping (`console-composition.ts`). That is the contract, not an
implementation detail: a consumer may rely on the producer's own documented
shape, and a composer that started composing or renaming fields would break it.

🔵 The shapes below are transcribed from each producer's controller + DTO (not
from prose), and the same set lives as machine-readable sample bodies in
[`fixtures/operator-overview-leg-bodies.json`](fixtures/operator-overview-leg-bodies.json),
which **both** sides read — the card census
(`leg-body-contract.test.tsx`) and the composer's pass-through tests
(`console-composition.test.ts`, `dashboard-composition-routes.test.ts`). Before
295 each side seeded its own invented body, so both were green while three
cards were blank on screen.

| # | Card | Producer body (the leg's `data`) | Field the card surfaces |
|---|---|---|---|
| 1 | iam | `{ content[], totalElements, page, size, totalPages }` (`AccountServiceClient.AccountSearchResponse`) | `totalElements` — account-level `LOCKED` count |
| 2 | wms | `{ content[ OrderSummaryResponse ], page: { number, size, totalElements, totalPages }, sort }` (`PageResponse`) | `page.totalElements` — `RECEIVED` (unshipped) order count |
| 3 | scm | `{ data: { content[ PurchaseOrderResponse ], page, size, totalElements, totalPages }, meta: { timestamp } }` (`ApiEnvelope<PageResponse>`) | `data.totalElements` — `DRAFT` PO count |
| 4 | finance | `{ data: [ DiscrepancyResponse ], meta: { page, size, totalElements, totalPages, timestamp } }` — every money field (`expectedMinor`, `actualMinor`) a **minor-units string** (F5) | `meta.totalElements` — `OPEN` discrepancy count. The card shows **only the count**; no amount is read or rendered |
| 5 | erp | `{ data: [ ApprovalSummaryView ], meta: { page, size, totalElements, totalPages, timestamp, actorEmployeeId? } }` (`ApiEnvelope.ofList`) | `meta.totalElements` — my pending approvals, **only when `meta.actorEmployeeId` is present**; absent ⇒ the «직원 연결 없음» state (never `0`) |
| 6 | ecommerce | `{ content[ AdminOrderSummaryItem ], page, size, totalElements }` (`AdminOrderListResponse` — no `totalPages`) | `totalElements` — `CONFIRMED` order count |

🔵 **Each card links to the screen where that work is done** (TASK-PC-FE-364):
iam → `/accounts`, wms → `/wms/outbound`, scm → `/scm/procurement`, finance →
`/ledger`, erp → `/erp/approval`, ecommerce → `/ecommerce/orders`. 🔴 The links
carry **no filter parameter** on purpose: none of those screens seeds its
status filter from the URL today, and a `?status=LOCKED` the destination drops
would land on the unfiltered list with nothing reporting it (the
`TASK-PC-FE-296` lesson below). A filtered link needs the destination to honour
the param first — a separate change. Zero calls either way.

> **History (TASK-PC-FE-295 → 364).** Until 364 two cards were labelled
> «행 수» (wms inventory snapshot rows, scm visibility snapshot rows) because a
> page total of one page of rows can only honestly answer «how many rows». 364
> replaced both legs with filtered list reads whose page total *is* the business
> figure, so neither card counts rows any more. The same rule still binds:
> never sum or de-duplicate one page and present the result as a whole-set
> total.

🟢 **The wms alert count is absent BY DECISION — this is settled, not pending**
(owner, 2026-09-22, `TASK-PC-FE-296` AC-1 ⓒ). It needs a second query
(`lowStockOnly=true&size=1`), i.e. a second call on this route. AC-0 measured
the cost on the producer of that time (the former BFF, retired — ADR-MONO-081):
the second call could not be a 7th parallel leg, the bounded retry would have
repeated both calls, and the worst case would have moved from 4.15 s (inside the
5 s composition budget) to 8.15 s (outside it). The structure that decided it
holds for the current composer too:

- the composition keeps **one card per domain** (`compose()` keys results by
  domain), so the second call **cannot be a 7th parallel leg** — it would run
  sequentially inside the wms leg;
- both calls would share the wms leg's **single** `LEG_TIMEOUT_MS` (4 s);
- exceeding it degrades the card to `TIMEOUT`, so paying for the alert count
  **risks losing the 재고 행 수 tile that works today**.

⇒ the card shows **no alert tile and no number**; it answers the question with a
**link** to `/wms/inventory?lowStockOnly=true`, where the operator sees the
exact count instead of the overview's approximation. 🔵 Zero calls, so the
verbatim invariant below stays intact for all six legs.

🔴 **Do not reopen this as "the card is missing a metric".** If a future
change makes a second call affordable (a raised `LEG_TIMEOUT_MS` is a
six-leg shared bound, not a wms decision), it needs a new owner decision and a
row in the producer table above — not a rendering fix.

🔴 **The link is only honest because the destination honours the param.**
`wms/inventory/page.tsx` seeds its 저재고 filter from `?lowStockOnly=true`
(strict `=== 'true'`). Before `TASK-PC-FE-296` the proxy route honoured that
param but the **screen dropped it**, so such a link would have landed on the
unfiltered list with nothing reporting an error.

> 🔵 **TASK-PC-FE-364 — what the decision above means now.** The wms card's
> number became **미출고 주문** (`dashboard/orders?status=RECEIVED`), replacing
> the 재고 행 수 call one-for-one — still one call. The reasoning above is
> unchanged and still decides the alert count: a low-stock number would be a
> second call on the wms leg. The low-stock **link** stays on the wms card
> (zero calls), next to the card's link to `/wms/outbound`.

> **History — finance multi-currency (until TASK-PC-FE-364).** The balances body
> carried one row per currency and no "account currency" field, so a
> multi-currency account got the availability statement without a currency
> chip. 364 replaced that leg with the discrepancy queue (count only), so the
> card reads no balance at all.

- **All-down envelope**: every leg can return non-`ok` simultaneously — the route still emits `200` with all 6 cards in `degraded`/`forbidden` states. The route NEVER emits `503` / blanks the response (D5.A discipline; D5.B rejection re-affirmed).

##### Card headline metric (TASK-PC-FE-368 — ADR-MONO-084 D7)

Each card also carries ONE headline performance metric of its domain: **this week's
value and the change against the previous same-length window**. It is **additive**:
no existing field, status, reason or card order changes — a `headline` field is
appended to each card. Producer contracts are byte-unchanged; each read is the
domain's own `…/metrics` (ADR-MONO-084 D2, [`performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md)),
the same address and credential that domain's own «실적» strip uses.

| Card | Headline metric (response `key`) | Unit | Metrics read (`period=week`) | Credential |
|---|---|---|---|---|
| iam | `loginFailure` (로그인 실패) | count | `GET ${IAM_ADMIN_API_BASE}/api/admin/metrics?period=week&tenantId={activeTenant}` | operator token + `X-Tenant-Id` (as card 1) |
| wms | `qtyShipped` (출고 수량) | count | `GET ${WMS_ADMIN_BASE_URL}/dashboard/metrics?period=week` | domain-facing token |
| scm | every `poAmount:<CUR>` (발주 금액) | money | `GET ${SCM_GATEWAY_BASE_URL}/api/v1/procurement/po/metrics?period=week` (bare body, or `{ data, meta }` unwrapped) | domain-facing token |
| finance | every `transferAmount:<CUR>` (이체 금액) | money | `GET ${FINANCE_BASE_URL}/api/finance/accounts/metrics?period=week` — the **account** service base, not the ledger base card 4 uses | domain-facing token |
| erp | `approved` (승인 — final approvals) | count | `GET ${ERP_BASE_URL}/api/erp/approval/metrics?period=week` | domain-facing token |
| ecommerce | `revenue` (매출) | money | `GET ${ECOMMERCE_ADMIN_BASE_URL}/orders/metrics?period=week` | domain-facing token |

Wire (appended last on every card, whatever its `status`):

```json
"headline": { "status": "ok", "period": "week",
              "metrics": [ { "key": "loginFailure", "unit": "count", "currency": null, "current": "7", "previous": "4" } ] }
"headline": { "status": "degraded" }
"headline": { "status": "forbidden" }
```

- `metrics` holds ONLY the selected metric(s): one entry, or **one per currency** for money (scm, finance) — currencies are never added together. Values are the producer's **integer strings** (F5), never coerced; `unit` MUST match the table (a drifted unit is `degraded`, not a number to show). The daily `series` is not carried.
- Fan has no card and no metric (ADR-MONO-084 D6).
- `headline` is **optional on the wire** (a payload from before this field, or a sample/test envelope, still parses); the console renders nothing for an absent one.
- Fixtures: [`fixtures/operator-overview-leg-bodies.json`](fixtures/operator-overview-leg-bodies.json) `headlineMetrics` (one metrics response per domain). `legs` is byte-unchanged.

**Composition (ADR-MONO-081).** The six metrics reads are a **separate `compose()` pass** run **in parallel** with the six card legs (`operator-overview-headline.ts`; latency = the slower pass, not the sum), each with its **own** `LEG_TIMEOUT_MS`. 🔴 They are NOT a second call inside a card's leg: «one call per card» (above, `TASK-PC-FE-296`) stands, because a second call inside a leg would share that leg's one timeout and could cost the card its only number. Total outbound calls per request: **12** (6 card + 6 metrics). The metrics `compose()` logs `console_composition_leg` with the same `route = "operator-overview"` and the request id suffixed `:headline`.

**Per-cell failure (the headline is its own cell):**

| Metrics read | `headline` | The card |
|---|---|---|
| `200` + a valid response carrying the expected key(s) | `ok` | unchanged |
| `403` | `forbidden` (rendered «실적을 볼 권한이 없습니다.») | unchanged |
| `401` | — | **whole-session `401`** (cross-leg rule, § 2.4.4 D3), same as a card leg |
| timeout · network · `5xx` · any other status · a body that is not JSON · not a valid metrics response · expected key absent · wrong unit | `degraded` (rendered «실적을 불러올 수 없습니다.») | unchanged |

A failed or forbidden headline is **never rendered or serialised as `0`** — a `0` would read as «nothing happened». The reverse also holds: a card leg that is `degraded` / `forbidden` keeps a healthy `headline`. A failure of the headline pass as a whole degrades all six headlines and nothing else. Anonymous (sample) visitors get `headline` from the sample fixtures' `…/metrics?period=week` answers (ADR-MONO-074); no outbound call is made.

Direction of change is not colour-coded (a rise is good for revenue, bad for failed logins).

##### Error envelope (composition-level errors, NOT per-leg)

For the inbound checks **before** any outbound leg fires (absent tenant /
token), and for the cross-leg `401`, the route answers the console's standard
`{ code, message }` error envelope:

| Status | Code | Cause |
|---|---|---|
| `400` | `NO_ACTIVE_TENANT` | no active tenant in the session — no leg called |
| `401` | `TOKEN_INVALID` | a session token the route needs (IAM OIDC access token, operator token, domain-facing token) is absent — no leg called; or per § 2.4.4 D3 — `401` from any outbound data leg surfaces as composition-level `401` (cross-leg discipline: the legs act for the same session; a 401 on one is a 401 for all) |
| `502` | `BAD_GATEWAY` | sample visitor only: the sample surface answered something other than a readable `200` (ADR-MONO-074) |
| `503` | reserved | NEVER emitted at MVP — D5.B is rejected |

##### Auth flow (verbatim from § 2.4.9, restated for cross-reference only)

- **Inbound**: same-origin browser request with the console session; the route
  reads the active tenant, IAM OIDC access token, operator token and
  domain-facing token server-side. The browser never holds or sends a domain
  credential.
- **Outbound**: per-domain credential dispatch (§ 2.4.9 table) —
  `IAM → operator token + X-Tenant-Id`,
  `{wms,scm,finance,erp,ecommerce} → domain-facing IAM OIDC token, no X-Tenant-Id`.
  NO fallback path. NO unified token. NO operator-token-only across all
  domains. Each producer's `TenantClaimValidator` is the authoritative gate.
  This route fires all **6** legs `{iam,wms,scm,finance,erp,ecommerce}`
  (TASK-MONO-241 → TASK-MONO-243; the ecommerce leg routes through the ecommerce
  gateway — see the ecommerce-leg topology note above).

##### Resilience (verbatim from § 2.4.9, restated for cross-reference only)

- Per-leg timeout `LEG_TIMEOUT_MS = 4000`; legs in parallel, so it also bounds
  the composition. No retry. No circuit-breaker (`CIRCUIT_OPEN` never produced).
- Aggregation degrade: every responsive leg's `data` + per-failed-leg
  `{ status: "degraded" / "forbidden", reason }` card.
- All-down still returns 200 with all-degraded/forbidden envelope. D5.B
  (all-or-nothing 503) is forbidden.

##### Observability (verbatim from § 2.4.9 + route-specific values)

Per leg: one `console_composition_leg` log line with `route = "operator-overview"`,
`domain` ∈ `{iam,wms,scm,finance,erp,ecommerce}`, `status` ∈
`{ok,degraded,forbidden,unauthorized}` and the card `reason`
(`TIMEOUT`, `DOWNSTREAM_ERROR`, `TENANT_FORBIDDEN`, `PERMISSION_DENIED`). Since
TASK-PC-FE-364 every leg is a real call (no leg is decided up front), so
`MISSING_PREREQUISITE` no longer appears on this route's lines. One
`console.composition.leg` span per leg with
`composition.route = "operator-overview"`.

##### Finance card prerequisite — `operatorDefaultAccountId` (history — this route no longer reads it)

> 🔵 **TASK-PC-FE-364 retired this prerequisite on this route.** The finance
> card now reads the reconciliation discrepancy queue (producer table row 4),
> which needs no account id, so the composition no longer calls
> `getFinanceDefaultAccountId()` and no leg is decided as `MISSING_PREREQUISITE`.
> The value itself, the registry wiring and the self-serve profile field are
> **unchanged** — the `/finance` overview (§ 2.4.7) still reads the default
> account. The text below records how this route used it from TASK-PC-FE-011 to
> TASK-PC-FE-364.

Two paths, both first-class:

- **Option (b) — MVP fallback**: when the operator has no default finance
  account, the composer **does not call** finance and renders the card
  `forbidden / MISSING_PREREQUISITE`.
- **Option (a) — activated by TASK-PC-FE-014** (IAM producer Phase 1 =
  **TASK-BE-304**: `console-registry-api.md § Per-operator profile attributes` +
  `admin_operators.finance_default_account_id` column + emission rule). It does
  **not** remove or weaken option (b) — operators whose
  `admin_operators.finance_default_account_id` is NULL continue to see
  `forbidden / MISSING_PREREQUISITE`, byte-identical to MVP behavior.

##### Option (a) activation (Phase 2 — TASK-PC-FE-014)

Consumer wiring chain (top-down, all in the console-web server; the browser never sees any of these values):

1. **Registry parser**: when the IAM registry response is fetched (at OIDC login `/api/auth/callback` and on every refresh `/api/auth/refresh`), the response is parsed with a zod schema extended to recognize `productItem.operatorContext?.defaultAccountId` (the `finance` product item is the only one populating it in v1; `operatorContext` on any other item parses to `undefined`). The parsed value is stored in the server-side session helper alongside the existing operator-token / IAM-OIDC-token / active-tenant slots.
2. **Session helper**: a server-only helper `getFinanceDefaultAccountId(): Promise<string | null>` (`import 'server-only'`) returns the stored value. Returns `null` when (i) the operator's row has NULL, (ii) the registry was not stored (e.g. registry fetch failed at login), or (iii) the value is an empty/whitespace string after trim.
3. **Composition route**: `src/app/api/console/dashboards/operator-overview/route.ts` calls `getFinanceDefaultAccountId()` and passes the value to `operatorOverviewLegs()`.
4. **Leg builder** (`console-composition.ts`): when the trimmed value is non-blank, the finance leg is `GET ${FINANCE_BASE_URL}/api/finance/accounts/{accountId}/balances` (id URL-encoded) with the domain-facing IAM OIDC bearer (per § 2.4.7 verbatim). When it is null/blank, the leg is decided up front as `forbidden / MISSING_PREREQUISITE` — no outbound HTTP fired.

**Hard invariants preserved**:

- **ADR-MONO-017 D4 HARD INVARIANT** (per-domain credential rule): unchanged — the account id is operator profile data flowing alongside the credential, never the credential itself.
- **§ 2.4.4 D3 cross-leg 401 discipline**: unchanged — when the finance leg returns 401, the composition still emits 401 `TOKEN_INVALID` (auth is not a per-card degrade).
- **§ 3.3 zero retrofit (5th confirmation in this chain)**: 5 producer specs byte-unchanged in this Phase 2 (Phase 1 already merged IAM-side as TASK-BE-304; wms/scm/finance/erp/fan/ecommerce all byte-unchanged in both phases).
- **Never browser-visible**: the account id is read and used only in the console-web server; it appears only in the outbound finance URL.
- **No logging of the value**: the account id (opaque finance account UUID) is `internal`-classified, not credential / not PII; nevertheless it must not appear in logs (finance F7 / `regulated.md` R7 transitive discipline) — the per-leg log line carries no URL and no account id.

**Honest failure modes (no green-wash)**:

- A **stale** `finance_default_account_id` (the finance account was deleted/migrated after the operator profile was set): finance returns `404 ACCOUNT_NOT_FOUND` → leg surfaces as `degraded / DOWNSTREAM_ERROR`. The console shows the leg failed, not a fabricated `ok`. Adding IAM-side validation against finance is out of scope (cross-service decoupling preserved).
- The **registry response did not store** the value (e.g. login-time registry fetch failed): `getFinanceDefaultAccountId()` returns `null`, no finance call, finance card → `MISSING_PREREQUISITE`. The console shell continues to render via § 2.5 degraded catalog handling.
- The operator **switches tenants** mid-session: the `finance_default_account_id` is on the operator row (not per-tenant); the value continues to be used across tenant switches.
- The operator's **value is updated** mid-session: the cached session value is stale until the next registry refresh; same staleness window as `tenants` array changes (accepted).

- **`asOf` field source**: the composition request's server-side timestamp
  taken at request entry (NOT per-leg response timestamp); operators see the
  composition timestamp, not the slowest leg's freshness. wms's
  `X-Read-Model-Lag-Seconds` (per § 2.4.5) MAY surface as an additional
  card-level hint but is NOT in the v1 envelope schema.

##### console-web screen obligations (FE)

- `features/operator-overview/` (`<OperatorOverviewScreen>` server
  component + `<DomainCard>` per card + `<OverviewDegradeBanner>` if all-down)
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
- **Producer-authoritative tenant gate** — the composer never re-derives or relaxes tenant; only the IAM leg carries `X-Tenant-Id`, the others rely on the token's `tenant_id` claim.
- **Per-card degrade** discipline — composition never blanks; `401`-cross-leg vs `403`/timeout-per-leg distinction preserved.
- **§ 3 parity matrix byte-unchanged** (attestation-marker count = exactly **16** — `parity-verification.test.ts` no-drift guard).
- **ADR-MONO-017 D1-D8 byte-unchanged** (ADR-MONO-081 moved the producer; it did not amend ADR-MONO-017's rules).

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
ADR-MONO-017 § 3.3 #4 (pre-authorised: *"Subsequent Phase 7 dashboards (domain health,
throughput) — separate tasks"*); produced by the console-web route
`src/app/api/console/dashboards/domain-health/route.ts` (§ 2.4.9). This sub-section is **additive** to
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
> X-Tenant-Id, since actuator endpoints are not tenant-scoped). No
> credential is selected for these legs (`domainHealthLegs()` builds only
> `Accept` + `X-Request-Id`). This clarification narrows D4 — it does not amend it.

##### Surface

| # | Method / Path | Purpose | Auth (inbound) | Producer |
|---|---|---|---|---|
| 1 | `GET /api/console/dashboards/domain-health` | Single composed cross-domain health envelope; one card per backend domain (IAM + wms + scm + finance + erp + ecommerce); each card carries the producer's Spring Boot `/actuator/health` status (`UP` / `DOWN` / `OUT_OF_SERVICE` / `UNKNOWN`) wrapped in the per-leg outcome (`ok` / `degraded`) per § 2.4.9 D5.A discipline | Same-origin request with the console session; the route checks the active tenant and the IAM OIDC access token **server-side** (neither is sent on the outbound actuator legs); the operator token and domain-facing token are **not required** (no outbound leg consumes them). No access token → `401 TOKEN_INVALID` before any outbound leg. No active tenant → `400 NO_ACTIVE_TENANT` (for log/audit traceability, not because legs need it) | `console-web` server |

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
| 1 | iam health | `GET http://iam.local/actuator/health` (gateway-service primary entry) | **None** (public actuator, no `Authorization`, no `X-Tenant-Id`) | IAM `gateway-service` `application.yml` `public-paths` includes `GET:/actuator/health` | `{"status": "UP" \| "DOWN" \| "OUT_OF_SERVICE" \| "UNKNOWN"}` aggregated status (the console reads only the top-level `status`; no component drill-down) |
| 2 | wms health | `GET http://wms.local/actuator/health` (gateway-service primary entry) | **None** | WMS `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` + `/actuator/health/**` | same aggregated status |
| 3 | scm health | `GET http://scm.local/actuator/health` (gateway-service primary entry) | **None** | SCM `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` | same aggregated status |
| 4 | finance health | `GET http://finance.local/actuator/health` (`account-service` direct — finance has no gateway-service in v1) | **None** | finance `account-service` `SecurityConfig` `permitAll` includes `/actuator/{health,info,prometheus}` | same aggregated status |
| 5 | erp health | `GET http://erp.local/actuator/health` (`masterdata-service` direct — erp has no gateway-service in v1) | **None** | erp `masterdata-service` `SecurityConfig` `permitAll` includes `/actuator/{health,info,prometheus}` | same aggregated status |
| 6 | ecommerce health | `GET http://ecommerce.local/actuator/health` (`gateway-service` primary entry) | **None** | ecommerce `gateway-service` `SecurityConfig.PUBLIC_PATHS` includes `/actuator/health` + `/actuator/health/**` + `/actuator/info` (WebFlux reactive gateway; verified 2026-06-13 — TASK-MONO-241 AC-8, 0-byte producer change) | same aggregated status |

**Producer immutability**: the 6 producer SecurityConfig declarations above
are **byte-unchanged spec-side and impl-side** (§ 3.3 seventh confirmation;
TASK-MONO-241 added the ecommerce leg without retrofitting any producer).
The composer calls the existing public actuator endpoints verbatim:
`domainHealthLegs()` takes the **origin** of each domain's console base URL
(`IAM_ADMIN_API_BASE`, `WMS_ADMIN_BASE_URL`, `SCM_GATEWAY_BASE_URL`,
`FINANCE_BASE_URL`, `ERP_BASE_URL`, `ECOMMERCE_ADMIN_BASE_URL` — the same bases
the § 2.4.9.1 legs and the domains' own console screens use) and appends
`/actuator/health`, with the same per-leg `LEG_TIMEOUT_MS`. The ecommerce
gateway origin is therefore **shared** by two distinct legs: this
credential-LESS `/actuator/health` health leg, and the credential-FULL
`/api/admin/products` operator-overview snapshot leg (§ 2.4.9.1 row 6) — same
gateway, different path + authorization. The § 2.4.9.1 overview is also 6
(symmetry restored; see the cross-reference note there).

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
  - `DOWN` → red/critical visual; operator surface is **NOT degraded** (the leg returned a successful health document — the producer is honestly reporting itself as down). Distinction from `degraded` (the console could not reach the producer at all). Spring Boot answers this with HTTP `503`; a `503` whose body is a health document is still `ok` (§ Resilience below).
  - `OUT_OF_SERVICE` → maintenance yellow visual.
  - `UNKNOWN` → grey/inconclusive visual.
- `cards[i].status == "degraded"` → `reason` ∈ `{ "DOWNSTREAM_ERROR", "TIMEOUT", "CIRCUIT_OPEN" }` (`CIRCUIT_OPEN` never produced — § 2.4.9 Resilience); `data` absent. Card renders "leg unreachable" placeholder + retry affordance.
- **All-down envelope**: every leg can return non-`ok` simultaneously — the route still emits `200` with all 6 cards in `degraded` states. The route NEVER emits `503` / blanks the response (D5.A discipline; D5.B rejection re-affirmed).

##### Error envelope (composition-level errors, NOT per-leg)

| Status | Code | Cause |
|---|---|---|
| `400` | `NO_ACTIVE_TENANT` | no active tenant in the session (for log/audit traceability, not because outbound legs need it — the inbound check is preserved for symmetry with § 2.4.9.1) — no leg called |
| `401` | `TOKEN_INVALID` | no IAM OIDC access token in the session — no leg called |
| `502` | `BAD_GATEWAY` | sample visitor only: the sample surface answered something other than a readable `200` (ADR-MONO-074) |
| `503` | reserved | NEVER emitted (D5.B is rejected; same as § 2.4.9.1) |

**No `401 TOKEN_INVALID` cross-leg collapse** — this route's outbound legs
have no `Authorization` header, so a 401 from any leg is itself an
unexpected (producer-side actuator misconfiguration) and is mapped to
`degraded` for that card, not to a composition-level 401. This is an
intentional divergence from § 2.4.9.1 D3 cross-leg rule (which exists
because every leg there acts for the same session — a 401 from one is a 401
for all).

##### Auth flow

- **Inbound**: same-origin browser request with the console session; the route
  checks the active tenant and the IAM OIDC access token server-side. It does
  **not** read the operator token or the domain-facing token — no leg needs
  them.
- **Outbound** (each domain's `/actuator/health`): **no headers** beyond
  `Accept: application/json` and `X-Request-Id`. No `Authorization`, no
  `X-Tenant-Id`. The per-domain credential dispatch is not invoked.

##### Resilience

- Per-leg timeout `LEG_TIMEOUT_MS = 4000`, the same bound as § 2.4.9.1; legs in
  parallel. No retry. No circuit-breaker (`CIRCUIT_OPEN` never produced).
- A `503` whose body is a real health document (`status` a string ∈
  `UP|DOWN|OUT_OF_SERVICE|UNKNOWN`) is an `ok` card carrying that status —
  Spring Boot answers `/actuator/health` with HTTP 503 when the aggregate is
  `DOWN` / `OUT_OF_SERVICE`, and the producer reporting itself down is not the
  console failing to reach it. Any other `503` body (e.g. a gateway error
  envelope) stays `degraded`. (Finding of `TASK-PC-FE-302`: the former BFF
  degraded every 503, so a self-reported DOWN looked like an unreachable
  producer.)
- Aggregation degrade: every responsive leg's `data` + per-failed-leg `{ status: "degraded", reason }` card.
- All-down still returns 200 with all-degraded envelope. D5.B (all-or-nothing 503) is forbidden.

##### Observability

Per leg: one `console_composition_leg` log line with `route = "domain-health"`,
`domain` ∈ `{iam,wms,scm,finance,erp,ecommerce}`, `status` ∈ `{ok,degraded}`
and the card `reason` (`TIMEOUT`, `DOWNSTREAM_ERROR`) — no `forbidden` /
`unauthorized` outcome exists on health legs. One `console.composition.leg`
span per leg with `composition.route = "domain-health"` (same attribute keys as
§ 2.4.9.1 — no new key).

- **`asOf` field source**: the composition request's server-side timestamp at
  request entry (same as § 2.4.9.1).

##### console-web screen obligations (FE)

- `features/domain-health/` (`<DomainHealthScreen>` server component + `<DomainHealthCard>` × 6 + `<DegradeBanner>` if all-down + `<RetryButton>` client-only) renders the composed envelope (the card list is **data-driven** — it maps the envelope's `cards[]` array, so the 6th `ecommerce` card renders with zero hardcoded-count change). Per-card UI shape:
  - `ok` + `data.status="UP"` → green-checkmark card.
  - `ok` + `data.status="DOWN"` → red-cross card (producer self-reported critical — NOT a network failure).
  - `ok` + `data.status="OUT_OF_SERVICE"` → yellow-wrench card (planned maintenance).
  - `ok` + `data.status="UNKNOWN"` → grey-question card.
  - `degraded` → "leg unreachable" placeholder + retry affordance.
- Route `(console)/dashboards/health` is the operator-facing entry; a navigation entry in the in-console nav (`<MainNav>` "도메인 상태") is added alongside the existing "통합 개요".
- No client-side polling, no auto-refresh interval — operator-initiated retry only (matches § 2.4.4 / § 2.4.9 invariant).

##### Hard invariants this route inherits (HARD INVARIANT — ADR-MONO-017 + § 3.3 + § 2.4.9)

- **No producer retrofit** — 6 producer SecurityConfig + actuator wiring byte-unchanged (the ecommerce gateway `/actuator/health` was already `permitAll` — verified, 0-byte producer change; TASK-MONO-241 AC-8).
- **D4 scope clarification** — D4 governs data legs only; this route's actuator legs are explicitly outside D4. No credential is selected for these legs.
- **Read-only** — no `Idempotency-Key` / `X-Operator-Reason` / mutation method.
- **No `Authorization` / `X-Tenant-Id` / `X-Operator-Token` on outbound legs** (asserted — `console-composition.test.ts` «six public /actuator/health calls, no Authorization, no X-Tenant-Id» and `dashboard-composition-routes.test.ts`).
- **Per-card degrade** discipline — composition never blanks.
- **§ 3 parity matrix byte-unchanged** (attestation-marker count = exactly **16**).
- **ADR-MONO-017 D1-D8 byte-unchanged** (ADR-MONO-081 moved the producer; it did not amend ADR-MONO-017's rules).

> **Not a § 3 parity row**: composition routes are additive to the operator
> surface, never replace a § 3 row. § 3 count remains **16** post-merge.

#### 2.4.10 ecommerce operations surface — product/order operator CRUD (TASK-MONO-252 contract / ADR-MONO-031 Phase 0 — first console **write** binding for ecommerce)

The **first ecommerce operations (CRUD) binding** federated by the console, and
the contract base for **sunsetting the standalone `admin-dashboard`**
([ADR-MONO-031](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md),
executing [ADR-MONO-030](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md)
§ 3.4 Step 4 facet a-후속-2). Where § 2.4.9.1/§ 2.4.9.2 bind ecommerce only as a
**§ 2.4.9 composition read leg** (operator-overview snapshot + domain-health card), this
sub-binding renders the ecommerce **product** and **order** operator surfaces so
an operator can manage the catalog and drive order lifecycle **from inside the
console** — the console equivalent of the `admin-dashboard` product/order
screens. Per **ADR-MONO-017 D2.A**, this surface is **console-web → ecommerce
gateway direct** (Next.js Route Handlers); it adds **NO composition write leg**
(the § 2.4.9 composition stays cross-domain-read-aggregation only — the wms-outbound § 2.4.5.1 /
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
  | 2 | product detail | `GET /admin/products/{id}` (operator-plane detail, TASK-MONO-703 — NOT the public `/products/{id}`, which the gateway closes to `ECOMMERCE_OPERATOR`) | read |
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
  | 18 | products period summary | `GET /admin/products/summary` | read |
  | 19 | sellers period summary | `GET /admin/sellers/summary` | read |
  | 20 | orders period summary | `GET /admin/orders/summary` | read |
  | 21 | order insights (rankings) | `GET /admin/orders/insights[?period=week\|month]` | read |
  | 22 | order performance metrics («실적» strip) | `GET /admin/orders/metrics?period=week\|month` | read |

  **Operator-overview period summaries (#18–20 here + the per-area equivalents
  in §§ 2.4.10.2–.6) — TASK-BE-468 / TASK-PC-FE-164**: each operator area exposes
  a dedicated `GET .../summary` read that returns the KST **calendar-period-to-date**
  counts consumed by the § 2.4.9.1 운영 개요 (`getEcommerceOverviewState`) — chosen
  over adding date-range params to the list reads (one round-trip per area, list
  contract untouched; revisits ADR-MONO-017 D3.B "no producer /summary" for the
  overview specifically). Uniform response shape across all 7 endpoints:

  ```json
  { "today": 3, "week": 12, "month": 40, "total": 47 }
  ```

  All values `long`, tenant-scoped, never negative; `total` = cumulative row count
  (the pre-existing overview number), `today`/`week`/`month` = rows whose
  `createdAt` ≥ the KST period start (오늘 = Asia/Seoul 자정, 주간 = 이번 주 월요일
  00:00 KST, 월간 = 이달 1일 00:00 KST) through now. Invariant `today ≤ week ≤
  month ≤ total`. The 7 endpoints (per hosting service): `/admin/products/summary`
  & `/admin/sellers/summary` (product-service), `/admin/orders/summary`
  (order-service), `/shippings/summary` (shipping-service), `/promotions/summary`
  (promotion-service), `/admin/users/summary` (user-service),
  `/notifications/templates/summary` (notification-service). Same admission +
  tenant chokepoint as each area's list read; the console degrades a non-200
  summary cell to "점검 필요"/"권한 없음" (never blanks the section).

  **Operator-overview ranking insights (#21) — TASK-BE-469 / TASK-PC-FE-172**:
  a single `GET /admin/orders/insights` read on `order-service` returns four
  **top-5 rankings** derived from the order line-items, consumed by the 운영 개요
  as horizontal bar charts (상품별 주문횟수 / 상품별 매출 / 셀러별 주문횟수 /
  셀러별 매출). Response shape (tenant-scoped):

  ```json
  {
    "topProductsByOrderCount": [ { "id": "prod-1", "label": "베이직 티셔츠", "value": 42 } ],
    "topProductsByRevenue":    [ { "id": "prod-1", "label": "베이직 티셔츠", "value": 1284000 } ],
    "topSellersByOrderCount":  [ { "id": "seller-1", "label": "seller-1", "value": 30 } ],
    "topSellersByRevenue":     [ { "id": "seller-1", "label": "seller-1", "value": 980000 } ]
  }
  ```

  Each array holds ≤ 5 entries sorted by `value` DESC (ties by `id` ASC). `value`
  is a non-negative `long`: order-count = `COUNT(DISTINCT order)`, revenue =
  `SUM(unit_price × quantity)` over that product's / seller's lines. **CANCELLED
  orders are excluded** from both metrics. 🔴 **TASK-MONO-807 superseded that
  basis**: only realised sales count (`CONFIRMED` / `SHIPPED` / `DELIVERED`, not
  refunded — `PENDING` and `BACKORDERED` are out), attributed to `paid_at`, and a
  bound seller scope narrows to that seller's lines; `?period=` keeps only orders
  paid in that period's current window (the console passes the strip's period).
  The normative rule is `order-api.md` § `GET /api/admin/orders/insights`. Product `label` = the line's
  denormalized `product_name`; seller `label` = the raw `seller_id` (order-service
  holds no seller display name — the console overlays the product-service seller
  `displayName` via the seller list, falling back to this id). Same admission +
  `WHERE tenant_id` chokepoint as the sibling `/admin/orders/**` reads; the console
  degrades a non-200 insights leg to a per-chart "데이터를 불러올 수 없습니다" /
  "권한 없음" placeholder (never blanks the section). All four rankings share one
  source (`order_items`) so they are one endpoint on the order-line owner — no
  product-service join (name is denormalized) and revenue lives only on the line.

  **Operator-overview performance metrics (#22) — ADR-MONO-084 P2 / TASK-MONO-807**:
  `GET /admin/orders/metrics?period=week|month` on `order-service` feeds the «실적»
  strip at the top of the 운영 개요 (revenue · units · orders, current vs the
  same-length previous window, daily series). Shape, windows and error handling are
  [`platform/contracts/performance-metrics-contract.md`](../../../../platform/contracts/performance-metrics-contract.md);
  what each key counts is `order-api.md` § `GET /api/admin/orders/metrics`. Parsed by
  `shared/api/performance-metrics.ts` (values are integer strings — F5); its own cell —
  a non-200 or an unparseable body shows «실적을 불러올 수 없습니다» / «권한 없음» in the
  strip while every other block renders. The page reads `?period=` (default `month`;
  an unknown value falls back to the default) and passes the same period to #21.

  **Out of this binding (deferred, not silently dropped)**: seller admin
  (`AdminSellerController POST /admin/sellers`) and search reindex
  (`SearchAdminController POST /search/admin/reindex`) — separate seller/search
  facets. The `admin-dashboard` **dashboard KPI** widgets are partially covered
  by the § 2.4.9.1 ecommerce overview snapshot (TASK-MONO-243); any residual KPI
  is a later facet.

  **Order status transitions (#17) — operator-initiated set (TASK-MONO-303)**: the
  console offers only `PENDING → CONFIRMED` and `PENDING|CONFIRMED → CANCELLED`.
  `SHIPPED` and `DELIVERED` are **not** operator-settable from the order surface —
  the Order reaches them solely via the shipping return-leg (the producer's
  `ShippingStatusChanged` event flips `CONFIRMED → SHIPPED → DELIVERED`,
  ADR-MONO-022 §D7). The order detail therefore **displays** SHIPPED/DELIVERED
  read-only; `배송 시작`/`배송 완료` are initiated from the shipping surface
  (#2.4.10.3 `PUT /shippings/{id}/status`), not here. Submitting
  `status: SHIPPED|DELIVERED` to `POST /admin/orders/{id}/status` returns
  `400 INVALID_ORDER_REQUEST` (producer guard).

- **Mutation discipline (Phase-1 producer-verify gated)**: every mutation
  (#3–9, #11–14, #17) is **confirm-gated** in the UI and carries the
  domain-facing OIDC credential. Two ecommerce-specific items **MUST be verified
  against the producer in Phase 1 before the write Route Handlers ship** (the
  ecommerce admin API is less formalised than wms-outbound, so this contract
  names the gaps rather than fabricating semantics):
  1. **Operator role mapping** — `AdminProductController` register/update/delete
     require an `X-User-Role: ECOMMERCE_OPERATOR` header today. The console operator carries
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
Handlers); **NO** § 2.4.9 composition leg.

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
composition write leg.

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
ecommerce gateway direct** (Next.js Route Handlers); **NO** composition write leg.

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
same-origin route handlers → ecommerce gateway direct, NO composition write leg
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
This started as a **list + detail + register** surface (MVP, TASK-PC-FE-090); the
**operator lifecycle actions** (provision / suspend / close) were added by
**TASK-PC-FE-154** once the producer shipped them (ADR-MONO-042 D3/D4). The seller
domain has **no** update/delete (CRUD); its mutation surface is **state transitions**
(`PENDING_PROVISIONING → ACTIVE`, `ACTIVE → SUSPENDED`, `→ CLOSED`), so provision/
suspend/close ARE the seller "edit/delete" equivalent.

This sub-binding **inherits § 2.4.10's cross-cutting rules verbatim** and does
not restate them: the **credential** (domain-facing IAM OIDC access token —
`getDomainFacingToken()`, **never** `getOperatorToken()`); the **tenant model**
(JWT `tenant_id` claim — NO `X-Tenant-Id` header); the **error envelope** (flat
`{ code, message, timestamp }`); the **resilience taxonomy** (401 → re-login /
403 → inline / 503 → section degrades only); the **proxy model** (console-web
same-origin route handlers → ecommerce gateway direct, NO composition write leg
— ADR-MONO-017 D2.A); **NO `Idempotency-Key`** (producer defines none).

- **Authoritative producer surface** (do NOT redefine here): ecommerce
  `product-service` `AdminSellerController`, consumed via the ecommerce
  gateway **admin** path `/api/admin/sellers` (base URL
  `ECOMMERCE_ADMIN_BASE_URL` = `http://ecommerce.local/api/admin`, same gateway
  + IAM-OIDC credential as § 2.4.10 products/orders/users — **NOT**
  `ECOMMERCE_PUBLIC_BASE_URL` which promotions/notifications/shippings use;
  sellers live under the `/api/admin/**` subtree gated on `roles ∋ ECOMMERCE_OPERATOR` — the
  operator's ADR-MONO-035 4a assume-tenant-derived domain role; the legacy
  `account_type=OPERATOR` gateway leg was removed at 4b):

| # | Method | Path | Purpose |
|---|--------|------|---------|
| 1 | `GET` | `/api/admin/sellers?page=&size=` | paginated list (rows: `sellerId, displayName, status, createdAt`) |
| 2 | `GET` | `/api/admin/sellers/{sellerId}` | seller detail (full); missing/cross-tenant → 404 |
| 3 | `POST` | `/api/admin/sellers` | register; body `{sellerId, displayName}` → 201 `{sellerId}` |
| 4 | `POST` | `/api/admin/sellers/{sellerId}/provision` | re-provision a `PENDING_PROVISIONING` seller (ADR-042 D3 retry; idempotent — already-ACTIVE is a no-op) → 204 / 404 |
| 5 | `POST` | `/api/admin/sellers/{sellerId}/suspend` | `ACTIVE → SUSPENDED` + lock the backing account (ADR-042 D4; idempotent, null-safe) → 204 / 404 |
| 6 | `POST` | `/api/admin/sellers/{sellerId}/close` | `→ CLOSED` terminal + deactivate the backing account (ADR-042 D4; idempotent, null-safe) → 204 / 404 |
| 7 | `GET` | `/api/admin/sellers/{sellerId}/members` | TASK-MONO-752 (ADR-MONO-079 D5) — `{ members[{accountId, role, status, joinedAt}], invitations[{invitationId, email, status, expired, expiresAt, createdAt, acceptedAt}] }`; never a token → 200 / 404 |
| 8 | `POST` | `/api/admin/sellers/{sellerId}/invitations` | TASK-MONO-752 — body `{email}` → 201 `{invitationId, email, expiresAt, token}`; the token is shown to the operator **once** (no mail path) · 409 `SELLER_NOT_ACTIVE` on a non-ACTIVE seller |

  TASK-MONO-752: #5 / #6 also revoke the members' store `SELLER` site role (never a lock of the person);
  the console refreshes the members list after a lifecycle action. Proxies:
  `/api/ecommerce/sellers/{id}/members` (GET) · `/api/ecommerce/sellers/{id}/invitations` (POST, zod-validated → 422 before the upstream).

  Error envelope = the same flat ecommerce shape `{ code, message, timestamp }`
  (400 `VALIDATION_ERROR`, 403 `ACCESS_DENIED`, 404 `SELLER_NOT_FOUND`,
  409 `CONFLICT` [duplicate `sellerId` within tenant]),
  consumed with the § 2.4.10 ecommerce parser. The lifecycle POSTs (#4–#6)
  carry **no request body** and return **204 No Content** (consumed as a void
  mutation — the same `callEcommerce(..., undefined, ...)` / proxy `204` path as
  the product DELETE binding).

- **Seller statuses** = `PENDING_PROVISIONING` · `ACTIVE` · `SUSPENDED` · `CLOSED`.
  The list/detail render each with a status-tone badge (no hard-coded ACTIVE
  green). The per-tenant `default` seller appears as a real ACTIVE row — expected.
  Unknown/future producer status strings pass through (`.passthrough()`) and
  render with a neutral tone — never a crash.

- **No update/delete (CRUD); lifecycle = state transitions.** The producer
  defines no `PUT`/`DELETE`; seller mutation is the transition set above. The
  console surfaces exactly the **status-valid** transitions per seller
  (PENDING → provision; ACTIVE → suspend / close; SUSPENDED → close; CLOSED →
  none), each **confirm-gated** (`ConfirmDialog`, `pending` + inline error). There
  is **no producer reactivation path** (`SUSPENDED → ACTIVE` is not an endpoint;
  provision only targets PENDING). The register form still validates `sellerId`
  ≤ 64 chars non-blank, `displayName` non-blank; 409 `CONFLICT` surfaced inline.

- **Producer immutability**: cross-reference only — any change to the ecommerce
  seller contract is an ecommerce project-internal spec-first change;
  this section follows, never redefines it (§ 5 Change Rule).

> **Not a § 3 parity row**: additive net-new federated **domain** scope (no
> `admin-dashboard` counterpart for sellers); adds no § 3 row and changes none
> (count stays **16**).

#### 2.4.10.6 ecommerce operator **overview snapshot** — `/ecommerce` landing (TASK-PC-FE-156 / ADR-MONO-031 — realizes the deferred overview leg)

The `/ecommerce` section landing (TASK-MONO-241 drill-in; PC-FE-155 quick-links)
is elevated into an **operator overview snapshot**: per-area entity counts,
order-status distribution, and a recent-activity glance. It reuses the domain's
existing consumed list endpoints — **no new producer endpoint, no producer
retrofit, no § 2.4.9 composition leg**.

- **Read model (console-web DIRECT fan-out).** Unlike the console-wide operator
  overview (§ 2.4.9.1, the bounded cross-domain composition), this is a **domain-internal**
  snapshot, so it runs server-side in the console-web landing over the same
  `§ 2.4.10 / .1 / .2 / .3 / .4 / .5` list endpoints already consumed
  (credential = domain-facing IAM OIDC token, `getDomainFacingToken()`; NO
  `X-Tenant-Id` — the JWT `tenant_id` claim carries it; gateway-routed).
- **Counts.** Each area count = the list endpoint's `totalElements` read with
  `?page=0&size=1` (products/orders/users/promotions/shippings/sellers/notification-templates).
- **Order-status distribution.** `GET /orders?status=<S>&page=0&size=1`.`totalElements`
  for each `OrderStatus` bucket.
- **Shipping-status distribution (TASK-PC-FE-360).** `GET /shippings?status=<S>&page=0&size=1`.`totalElements`
  for each of the 4 `ShippingStatus` buckets (`PREPARING` · `SHIPPED` · `IN_TRANSIT` · `DELIVERED`) —
  exactly **4** extra reads (§ 2.4.10.3 list endpoint, `status` filter already bound; no new endpoint).
  Each bucket is its own cell (own `ok` / `403` / degraded); a stacked bar is drawn ONLY when all 4
  buckets are `ok` (a partial distribution would read as the whole) — the per-status numbers stay as
  text either way. The distribution is shown on the overview only, never on the `/ecommerce/shippings` list.
- **Recent activity.** `GET /orders?page=0&size=5` + `GET /sellers?page=0&size=5` `content`.
- **No aggregation endpoint (ADR-MONO-017 D3.B).** There is deliberately **no**
  producer `/summary` / `/dashboard-card` endpoint; deriving counts from
  `totalElements` is the sealed pattern. Re-introducing an aggregation endpoint
  for this snapshot is a contract defect.
- **Per-area brief status ("각 서비스별 간략 상태").** Derived from each fan-out
  cell's outcome (`ok` / `403 → 권한 없음` / `503|timeout|other → 점검 필요`); true
  per-microservice actuator health is not reachable from console-web direct
  calls, so reachability is the honest, zero-backend signal. The aggregate
  ecommerce `/actuator/health` card (§ 2.4.9.2) stays on top of the snapshot.
- **Resilience (§ 2.5).** Per-cell degrade is cell-local (one area's failure
  never blanks the snapshot); a `401` in ANY leg triggers a whole-session
  `redirect('/login')` (no partial authed state). Read-only; no auto-refetch.

> **Not a § 3 parity row**: consumes only already-listed endpoints in a new
> read composition; adds no § 3 row and changes none (count stays **16**).

#### 2.4.11 fan **directory** operator surface — agencies · artists · groups (TASK-MONO-751 / ADR-MONO-079 D4-A — platform operators only)

The first console binding to the fan platform, and **directory-only**: the platform
operator manages agencies, artists and artist groups in fan **artist-service**. Fan
community, membership and notifications stay closed to operator tokens
(ADR-MONO-059 § 부분 개정 · TASK-MONO-750 — those services refuse `FAN_OPERATOR`).
Producer contract (authoritative, consumed only):
[`fan-platform/specs/contracts/http/artist-api.md`](../../../fan-platform/specs/contracts/http/artist-api.md)
(§ Artists · § Artist groups · § Agencies).

- **Who** (ADR-MONO-079 rider R3). Only a **platform operator** (`admin_operators.tenant_id='*'`)
  may assume `fan-platform`. The registry lists the `fan` product with `tenants: ["fan-platform"]`
  for such an operator and `tenants: []` for every customer-tenant operator — even one holding an
  assignment row naming it (§ 2.2; `console-registry-api.md` § Tenant selection rule (4)). The
  console renders the 「팬 디렉터리」 nav parent **only** when `fan` has a selectable tenant
  (`ConsoleSidebarNav` `availableProductKeys` — the first registry-gated nav entry).
- **Credential** (§ 2.7). The domain-facing token — after the switch to `fan-platform` that is
  the ASSUMED token (`tenant_id=fan-platform`, `roles=["FAN_OPERATOR"]`, `aud ∋ platform-console-web`),
  admitted by artist-service by `tenant_id` **equality** (not entitlement). NEVER the operator
  token; **NO `X-Tenant-Id`**.
- **«Switch, then ask»** (ticket Failure Scenario 1). The `(console)/fan` layout is a
  `DomainTenantGate productKey="fan"`: before a tenant is assumed it shows «테넌트를 선택하세요»;
  with another tenant assumed it shows the mismatch note — the page never calls artist-service
  with a token it would refuse (`403 TENANT_FORBIDDEN`).
- **Transport.** console-web server → fan gateway `FAN_GATEWAY_BASE_URL`
  (`http://fan-platform.local`, demo: `fan-platform.${DEMO_DOMAIN}`) + external `/api/v1/**`
  (the gateway rewrites to `/api/**`). Through the shared FLAT-envelope core
  (`callFlatEnvelopeGateway`, profile `fan` — sample branch first, ADR-MONO-074; the `fan`
  sample surface is `pending`). The fan gateway's audience allowlist lists
  `platform-console-web` (TASK-MONO-751; mode still SHADOW).
- **Same-origin routes** (console-web `src/app/api/fan/**`, ADR-MONO-081 — no console-bff):

| Console route | Producer (external path) |
|---|---|
| `GET/POST /api/fan/agencies` | `GET/POST /api/v1/agencies` |
| `GET/PATCH /api/fan/agencies/{id}` | `GET/PATCH /api/v1/agencies/{id}` (rename) |
| `PATCH /api/fan/agencies/{id}/status` | `PATCH /api/v1/agencies/{id}/status` (`ARCHIVED` only) |
| `PATCH /api/fan/agencies/{id}/store-seller` | `PATCH /api/v1/agencies/{id}/store-seller` |
| `GET/POST /api/fan/artists` | `GET/POST /api/v1/artists` (list = PUBLISHED only) |
| `GET/PATCH /api/fan/artists/{id}` | `GET/PATCH /api/v1/artists/{id}` |
| `PATCH /api/fan/artists/{id}/status` | `PATCH /api/v1/artists/{id}/status` |
| `PATCH /api/fan/artists/{id}/agency` | `PATCH /api/v1/artists/{id}/agency` |
| `POST /api/fan/artist-groups` | `POST /api/v1/artist-groups` |
| `GET /api/fan/artist-groups/{id}` | `GET /api/v1/artist-groups/{id}` |
| `PATCH /api/fan/artist-groups/{id}/agency` | `PATCH /api/v1/artist-groups/{id}/agency` |

  The routes unwrap the producer's `{ data, meta }` (a list → `{ content, page, size,
  totalElements, totalPages }`) and validate bodies before the upstream (`422 VALIDATION_ERROR`,
  no upstream call). Errors: FLAT envelope passthrough; `503` keeps the producer `code`.
- **Store-seller link** (ADR-MONO-079 D2). The field is rendered; `422 STORE_SELLER_NOT_FOUND`
  / `STORE_SELLER_CLOSED` are shown as validation errors; `503 STORE_SELLER_LOOKUP_UNAVAILABLE`
  is rendered as its own «nothing was saved» state — today **every** value gets it, because
  artist-service's store lookup has no transport yet (TASK-MONO-759). Clearing (`null`) works.
- **Producer gaps the screens state rather than hide.** No group **list** endpoint (groups are
  opened by id or created); the artist list returns PUBLISHED only (DRAFT/ARCHIVED by id).

> **Not a § 3 parity row**: a net-new surface with no `admin-web` counterpart; count stays **16**.

### 2.5 Resilience

- Console fan-out applies bounded timeouts (and, where a caller keeps state, circuit-breaker / retry) per `platform/` baselines (`integration-heavy` trait). The cross-domain composition (§ 2.4.9) is timeout-only by decision — a serverless function holds no breaker state (ADR-MONO-081 R2).
- One domain unavailable MUST degrade only that domain's section — never blank the console shell.
- **Idle session expiry is not a backend failure** (TASK-MONO-674). The session cookies expire in the browser (access/id_token/home-tenant cookies `maxAge = expires_in`, 1800s; operator cookie `maxAge = expiresIn`) while the refresh cookie lives 30 days. When the `(console)` guard finds the session incomplete **and** a refresh cookie is present, it MUST attempt a silent server-side refresh (§ 2.6.1) before sending the operator to `/login`; when that refresh fails the operator lands on `/login?error=session_expired` (reason shown), never on a reason-less `/login?redirect=…`. A visitor with **no** refresh cookie (never logged in, or logged out) keeps the plain `/login?redirect=<path>` bounce, with no network call.

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
  - Exchange `401 TOKEN_INVALID` (subject token invalid / OIDC subject not mapped to an active `admin_operators` row — producer fail-closed per `admin-api.md`/`security.md`): the operator is **not provisioned** for operator actions → ~~forced re-login with a distinct reason~~ (stale since TASK-PC-FE-182: the callback sends the visitor to `/onboarding`, IAM cookies kept) → **from TASK-MONO-773: the console's non-operator state (§ 2.6.4)** — IAM cookies kept, the explicit non-operator marker set, landing on the tenant-creation screen. The operator cookie is **not** set; on refresh the existing operator cookie is dropped. The IAM token is still never an `/api/admin/**` credential (#569).
  - Exchange **`403 MFA_REQUIRED`** (TASK-MONO-771 — the caller **is** a resolved operator, but a second factor is required and the IAM token's `amr` does not contain `mfa`; producer rule in `admin-api.md` § token-exchange): **step-up, never onboarding.** The operator cookie is not set (on refresh the existing one is dropped); the IAM cookies are **kept**; the browser is sent to the console's step-up route **`GET /api/auth/step-up?redirect=<path>`**, which starts a fresh IAM authorization exactly as login does (PKCE, `state`) with **`acr_values=mfa`** added (IAM `auth-api.md` § IdP 브라우저 화면 — 2단계 인증 § 단계 상승). IAM then shows its second-factor challenge, or its enrolment page when the account has none (enrolment needs a verified email — IAM's page says so and links to its email-verification page); the callback that follows performs this same exchange again. `<path>` = the § 2.6.1 produce-side predicate.
    - 🔴 **Only a `401` means «not an operator».** The callback's «not an operator → `/onboarding`» branch (from TASK-MONO-773: «not an operator → non-operator state, § 2.6.4») (and every later «not an operator» shell branch) MUST be predicated on `401` alone. Routing a `403 MFA_REQUIRED` there would send a `SUPER_ADMIN` to the tenant-creation shell instead of to a second factor.
    - **Loop bound**: one automatic step-up per login. The step-up route marks its authorization request (`state`-bound, server-side); a callback for a marked request that still gets `403 MFA_REQUIRED` does **not** step up again — it lands on `/login?error=mfa_required` (reason shown: «2단계 인증을 마쳐야 콘솔에 들어올 수 있습니다»). IAM's own cancel (`error=access_denied`, `error_description=mfa_cancelled`) lands on the same page.
    - The response carries no indication of *why* (role flag vs a tenant's entry policy) — the console shows one generic step-up reason.
  - Exchange `400 BAD_REQUEST`/`VALIDATION_ERROR`, timeout, network failure, or `5xx`: treated as **session-unavailable** → no operator cookie set / existing operator session dropped; the console never falls back to the IAM OIDC token on the `/api/admin/**` boundary (that is the exact #569 latent defect this contract fix closes).
  - An unexpected `tokenType` (≠ `"admin"`) is treated as fail-closed (operator cookie not set).
- **Resilience parity (§ 2.5)**: the exchange call uses the same `integration-heavy` discipline as the registry call — explicit hard timeout (AbortController), structured logging, no unbounded default — but the operator-boundary outcome is fail-closed (no partial authed state), distinct from the registry's degrade-the-section behaviour.
- **Tenant scope**: never derived from the IAM OIDC token. IAM resolves operator tenant scope producer-side from `admin_operators.tenant_id` (ADR-002 `'*'` platform sentinel); the console sends no tenant to the exchange (consistent with § 2.2 registry tenant scoping). Cross-references: IAM [`console-registry-api.md` § Authentication](../../../iam-platform/specs/contracts/http/console-registry-api.md) (operator token now via the exchange; producer requirement unchanged).

#### 2.6.1 Idle-expiry refresh at the `(console)` guard (normative — TASK-MONO-674)

Owner decision (TASK-MONO-674 AC-1): **refresh silently, and show the reason when refresh fails**. Token lifetimes are unchanged (access 1800s, refresh 2592000s, `reuse-refresh-tokens=false` → the refresh token **rotates** on every use; IAM `V0015`).

- **Why a redirect hop, not an in-layout refresh.** Server Components/layouts cannot set cookies (Next allows it only in Route Handlers, Server Actions, middleware). The guard therefore **redirects** to a route handler that refreshes, sets the cookies and redirects back. The guard makes **no network call of its own** for this hop. *(TASK-PC-FE-306 — the guard's **authenticated** branch now also reads the demo-state snapshot, § 2.6.2 «Live session». It does so **only in a demo deployment**, and it reads the **same** per-server-instance 15 s snapshot (`@demo/backend-resolver`: at most one control-plane `/status` round trip per instance per 15 s, 2 s timeout) that `DemoBackendNotice` already reads on every render of that shell — so the guard adds **no round trip beyond the shell's existing read**. In a non-demo deployment (no control plane configured) the guard makes **zero** control-plane calls and does not read the § 2.6.2 marker cookie.)*
- **Guard (`(console)/layout.tsx`)**: `isAuthenticated()` false →
  - refresh cookie **present** → `GET /api/auth/refresh?redirect=<path>` (`<path>` = the same produce-side predicate as the login redirect: same-site absolute path, not `//…`, not `/login…`, not `/api/…`; otherwise the param is omitted);
  - refresh cookie **absent** → `/login?redirect=<path>` (unchanged).
- **`GET /api/auth/refresh`** (navigation counterpart of the `POST`; same IAM refresh + § 2.6 operator re-exchange + § 2.7 re-assume):
  - `redirect` is attacker-controllable → consume-side sanitised (`sanitizeReturnPath`) **and** the guard predicate (`/login…`, `/api/…` rejected); anything rejected becomes `/`. The handler only ever redirects to same-origin paths under the public origin.
  - Session already complete (access **and** operator cookies present) → `307` to the target, **no IAM call** (another tab already refreshed; also bounds a cross-site-triggered GET to sessions that are already expired).
  - No refresh cookie → `/login?redirect=<target>` (never logged in / logged out), no IAM call.
  - Refresh + operator re-exchange OK → rotated access/refresh/id_token + operator (+ assumed, if an active tenant cookie survived) cookies set → `307` to the target. When **no** active-tenant cookie survived, the default active tenant is chosen and assumed by the **same function** `/api/auth/callback` uses (§ 2.7 «Default active tenant»; both `GET` and `POST` refresh). *(TASK-MONO-674 re-defaulted the token's `tenant_id` here; TASK-PC-FE-292 replaced that — see § 2.7.)*
  - **Rotation race** — IAM `400 invalid_grant` on the first attempt may mean another tab already rotated the token. No cookie is deleted (the deletes could land after — and erase — the other tab's fresh cookies); the handler waits a short grace period and redirects **once** to `/api/auth/refresh?redirect=<target>&retry=1`, which carries the browser's *current* cookies. `retry=1` with a complete session → target; `retry=1` without → `/login?error=session_expired&redirect=<target>`, no IAM call, no cookie deletes.
  - Other IAM `4xx`, or operator re-exchange **unavailable** → whole session cleared (§ 2.6 fail-closed) → `/login?error=session_expired&redirect=<target>`.
  - IAM `5xx` / network / unparseable response → `/login?error=session_expired&redirect=<target>`, **cookies kept** (transient; the next guard visit may refresh successfully).
  - Operator re-exchange **fail-closed** (`401`, not an operator of any tenant) → callback parity: operator session dropped, rotated IAM cookies kept → ~~`/onboarding`~~ → **TASK-MONO-773: the non-operator marker is set and the handler redirects to `<target>` if it is on the non-operator allow-list (§ 2.6.4), otherwise to `/new-tenant`.** The browser `POST /api/auth/refresh` answers **`403 { "code": "NOT_AN_OPERATOR" }`** (was: `401 TOKEN_INVALID` with the whole session cleared) — rotated IAM cookies kept, marker set — and the browser client navigates to `/new-tenant`.
  - Operator re-exchange **`403 MFA_REQUIRED`** (TASK-MONO-771 — e.g. a tenant's entry policy was switched on mid-session) → callback parity: operator session dropped, rotated IAM cookies kept → `GET /api/auth/step-up?redirect=<target>` (§ 2.6). Never `/onboarding` (from 773: never the non-operator state). The browser `POST /api/auth/refresh` answers `403 { "code": "MFA_REQUIRED" }` and the browser client navigates to the same step-up route.
- **Loop bound**: one guard bounce reaches the handler at most **twice** (first attempt + one `retry=1`); every failure destination (`/login…`, `/onboarding`) is outside the `(console)` guard (🔵 TASK-MONO-773: the non-operator landing `/new-tenant` is **inside** the guard, but the guard admits it in the non-operator state without a refresh hop — § 2.6.4 «Loop bound»), and `/login?error=session_expired` does not short-circuit back to the console (TASK-PC-FE-278). A success whose cookies the browser refuses to store cannot cycle: the old refresh token was rotated away, so the next attempt is rejected.
- **Unchanged**: the browser `POST /api/auth/refresh` after a `401` (§ 2.6 "When") and its JSON responses; the 53 server-side `401` sites (`/login?error=session_expired`).
- **Superseded in one case by § 2.6.2**: when the forced re-login lands while the demo backend is confirmed stopped, `/login?error=session_expired` is no longer the visitor's final destination.

#### 2.6.2 Session end while the demo backend is stopped → sample shell (normative — TASK-PC-FE-305)

Owner report (2026-10-04): a visitor logged in, the demo backend shut down, and on returning the console showed the login page with «데모 서버가 종료되어 다시 로그인해야 합니다…». Logging in at that point cannot succeed — the IdP is down with the backend — so the login wall leads nowhere. The owner expects the no-login sample shell (ADR-MONO-074) instead.

- **One decision point, not 53.** Every forced re-login — the § 2.6.1 refresh-failure redirects, the server-side `401` sites, the `(onboarding)` guard (removed by TASK-MONO-773 S6 — `/onboarding` becomes a redirect, § 2.6.4) — already converges on `/login?error=session_expired` (the `SESSION_EXPIRED` marker). The `/login` page asks the demo-state signal **only when that marker is present** and makes the decision there. The `401` sites and the § 2.6.1 handler are not edited.
- **The signal**: `resolveDemoBackendState()` (`shared/config/demo-backend.ts` → `@demo/backend-resolver`) — the same function `DemoBackendNotice` and the TASK-PC-FE-299 copy use. The refresh error alone is never the signal.
  - `unavailable` → **sample**. 🔴 This value covers both «the control plane answered `state≠running`» and «`/status` failed» (`infra/demo/backend-resolver/README.md` table). The console cannot distinguish them through the shared resolver; this decision inherits the TASK-PC-FE-299 copy's reading of `unavailable`. Splitting it is a change to the shared resolver, outside this contract.
  - `running` · `starting` · `not-demo` → **login**, exactly as § 2.6.1 (`/login?error=session_expired`, generic copy).
- **Sample branch**: `/login` redirects to `GET /api/auth/demo-ended?redirect=<target>` (a page cannot delete cookies). That handler:
  - re-reads the signal itself (a GET that deletes cookies must not trust its caller);
  - still `unavailable` → `clearFullSession` (access / refresh / id_token / operator / tenant / assumed; `console_last_tenant` is kept, as on logout) → redirect to `<target>?signed_out=demo_stopped`, `Cache-Control: no-store`. With no session cookie left, the `(console)` guard admits the visitor as a sample visitor (ADR-MONO-074 A1 — the predicate is unchanged; the cookies are gone, not reinterpreted);
  - anything else → `/login?error=session_expired&demo_checked=1[&redirect=<target>]`, cookies untouched. `/login` does not forward again when `demo_checked=1` (loop bound: one extra hop), and renders the TASK-PC-FE-299 copy for whatever it now sees.
  - `<target>`: the same consume-side predicate as § 2.6.1 (`sanitizeReturnPath` + guard predicate), plus `/onboarding…` and (TASK-MONO-773) `/new-tenant…` rejected (sample mode shows only a static «cannot create here» notice there — § 2.6.4 «Sample mode», TASK-PC-FE-376); anything rejected becomes `/`.
- **Notice**: the sample shell shows «데모 서버가 종료되어 로그아웃되었습니다…» (`data-testid="demo-signed-out-notice"`) when `signed_out=demo_stopped` is on the URL, and clears the TanStack Query cache on that landing (the TASK-PC-FE-299 AC-5 obligation, carried over from the forced re-login landing it replaces).
- **Live session (TASK-PC-FE-306)** — access **and** operator cookies still alive when the backend stops (no refresh, no `401`; the `(console)` guard sees `isAuthenticated()`). Owner decisions (2026-10-04): such a session also ends in the sample shell with the same notice, but **only after two consecutive, genuinely distinct `unavailable` readings** — a single reading (including a single failed `/status`, which the resolver also reports as `unavailable`) never logs out a working session.
  - **Signal and decision**: the same `resolveDemoBackendState()` snapshot the shell's `DemoBackendNotice` reads, and the same decision (`sessionEndDestination(state) === 'sample'`) — no second judge. The shared resolver is not changed.
  - **«Distinct»**: the resolver caches one snapshot per server instance for 15 s, so two readings less than 15 s apart may be the same snapshot. The second reading counts only when it **starts more than 15 s after** the first reading settled — then its snapshot was necessarily fetched after the first reading (on any instance). A first reading older than 10 min is stale and starts the count again.
  - **Marker cookie** `console_demo_stop_check` = `<firstUnavailableAt>.<checkedAt>` (epoch ms; `0` = no pending reading): `HttpOnly`, `SameSite=Lax`, `Secure` as the session cookies, `Path=/` (the reader is the guard on every console path), `Max-Age=600`. Only the route below writes it (a layout cannot set cookies). It is not a credential: a forged or replayed marker can at most make the visitor end **their own** session after one genuine `unavailable` reading (the route still re-reads the signal), or postpone that end — it never ends a session while the signal says anything else, and never touches another visitor.
  - **Guard** (`(console)/layout.tsx`, authenticated branch, demo deployment only): reading `unavailable` → `GET /api/auth/demo-ended?live=check[&redirect=<path>]`, unless the route answered within the last 15 s (marker `checkedAt`; when the browser holds no marker, `demo_checked=1` on the URL) — then the shell renders as before. Any other reading while the marker holds a first `unavailable` → `GET /api/auth/demo-ended?live=clear[&redirect=<path>]`. Otherwise the shell renders unchanged (`running` · `starting` · `not-demo`: no redirect, no cookie change). `<path>` = the § 2.6.1 produce-side predicate.
  - **Route, live mode** (`live` present **and** access + operator cookies present; otherwise the handler is exactly the TASK-PC-FE-305 handler above): `live=clear` → marker's first reading reset (no signal read). `live=check` → re-reads the signal: not `unavailable` → first reading reset; `unavailable` with a first reading 15 s–10 min old → `clearFullSession` + marker deleted → `<target>?signed_out=demo_stopped` (the sample landing above); `unavailable` otherwise → first reading recorded (kept if already pending). Every non-ending outcome sets `checkedAt` and returns to **the page the visitor asked for** with `demo_checked=1` — never `/login` (the session works). `Cache-Control: no-store` on every response.
  - **Loop bound**: a returned page does not hop on `unavailable` within 15 s of the route's answer (marker `checkedAt`, or `demo_checked=1` when the browser kept no marker), and a `clear` hop always leaves no pending reading, so one navigation reaches the route at most twice (`check` then `clear`).
  - **Accepted cost**: the session ends on the first navigation that starts more than 15 s after the first `unavailable` reading; until then the visitor sees the authenticated shell with `DemoBackendNotice`, as before. A `/status` outage that persists across two readings is read as a stop (the TASK-PC-FE-305 owner decision ① limit, now behind two readings).
- **Not covered**: (b) the browser API client's refresh failure (`shared/api/client.ts` → `/login?redirect=…`, no marker) — the marker is absent, so no decision is made. *(Former item (a), live session, is covered above — TASK-PC-FE-306.)*

#### 2.6.3 IdP token-endpoint refusals of a consumer-pool session (normative — TASK-PC-FE-324 · TASK-MONO-772)

Before the § 2.6 operator exchange there is the IAM **token** step itself (`authorization_code` at `/api/auth/callback`, `refresh_token` at `/api/auth/refresh`). For a session whose IAM principal is a **personal (consumer-pool) account**, IAM's answer depends on whether that account has a live operator facet (IAM [`auth-api.md` § 풀 계정의 콘솔 토큰](../../../iam-platform/specs/contracts/http/auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6), 772 S4). The console tells three outcomes apart — **by the IAM error body, never by guessing**:

| IAM token response | Meaning | `/api/auth/callback` | `/api/auth/refresh` (GET · POST) |
|---|---|---|---|
| `200` (pool account **with** an ACTIVE operator facet — `tenant_id=iam`) | an invited operator logging in with their personal account | normal path: store tokens → § 2.6 exchange (the exchange resolves the operator by `sub` exactly as for any other operator) | normal path |
| `400 invalid_grant`, `error_description` contains the literal `'consumer-pool'` (**unchanged** IAM wording) | a personal account with **no** live operator facet | `sso_wrong_account` — **TASK-PC-FE-324 unchanged** (the discriminator, its copy and the logout widget stay byte-identical; 772 does not change the IAM wording it matches) | whole session cleared → `/login?error=session_expired` (the § 2.6.1 «other IAM 4xx» row — e.g. an operator suspended mid-session; owner decision OD-6: no dedicated «운영자 권한이 없습니다» copy in 772, that wording is TASK-MONO-773's non-operator shell) |
| ↳ **from TASK-MONO-773 S5** (owner decision OD-6) | IAM **issues** the console token to a personal account whatever its facet state (none · not `ACTIVE`) — this row is no longer produced by the console-token issuer for a pool principal; that person now takes the `200` row, and the § 2.6 exchange answers `401` → § 2.6.4 non-operator state | the `sso_wrong_account` discriminator **stays** (byte-identical — other emitters of the wording are unmeasured, 773 AC-0 § 3 ⚪); its reachable population through this issuer is 0 — the closure basis for TASK-PC-FE-331 (773 AC-7) | an operator suspended mid-session no longer lands on `/login?error=session_expired`: the refresh succeeds, the re-exchange answers `401` → § 2.6.1 non-operator row |
| `400 invalid_grant`, `error_description` **exactly** `operator_eligibility_unavailable` (TASK-MONO-772 — whole-value equality, checked **before** the `'consumer-pool'` row) | IAM could not ask admin-service whether the account is an operator (fail-closed) — **not** «wrong account» | **new code `operator_check_unavailable`** → `/login` with «운영자 권한을 확인하지 못했습니다. 잠시 후 다시 시도해주세요.» — no logout widget (the account may well be right) | **transient**: cookies **kept** (IAM did not rotate the refresh token — the grant failed before issuance) → `/login?error=operator_check_unavailable&redirect=<target>`; the browser `POST` answers `503 { "code": "OPERATOR_CHECK_UNAVAILABLE" }`. Not the rotation-race retry (`retry=1`) — that branch is for a plain `invalid_grant` |

- 🔴 **Owner decision (2026-10-15 UTC, TASK-MONO-773 S1 open question 1)**: IAM S5 **removes** the issuer's operator-facet lookup and the `operator_eligibility_unavailable` constant — an admin-service outage no longer blocks console login. The row above (and the console's `operator_check_unavailable` / `OPERATOR_CHECK_UNAVAILABLE` mapping) becomes unreachable after S5; S6 may keep the mapping as dead-but-harmless or remove it in the same PR (the registry row stays until no emitter remains). → **TASK-MONO-773 S6 removed it** (callback branch · the refresh outcome and both refresh answers · the browser-client branch · the `/login` copy and its short-circuit exception · `shared/lib/iam-token-refusal.ts`). A body carrying the retired value is now an ordinary `invalid_grant` (callback `token_exchange_failed`; refresh: the rotation-race retry); a stale `/login?error=operator_check_unavailable` bookmark renders the generic copy. With no emitter left, the `OPERATOR_CHECK_UNAVAILABLE` registry row is marked **RETIRED** and struck (`platform/error-handling.md` — kept so the name is not reused, like the retired IAM constant). The row above and the bullets below that describe the mapping are kept as history (struck where they state current behaviour). Operator-ness is decided **only** by the § 2.6 exchange (`401` → § 2.6.4 marker); an exchange outage is § 2.6 «session-unavailable», never the non-operator state.

- 🔴 **Why a distinct code (772 AC-0 F5)**: mapping the lookup failure onto `sso_wrong_account` would tell a real operator «you are logged in with a consumer account — log out», which is false and sends them in circles during an admin-service outage. Mapping it onto `token_exchange_failed` would lose the distinction from a network failure the console already reports; `operator_check_unavailable` says what failed.
- Matching rules: `operator_eligibility_unavailable` by **whole-value equality** on `error_description` (the IAM fixed-constant convention, like `insufficient_user_authentication` in § 2.7); the `'consumer-pool'` row keeps TASK-PC-FE-324's substring predicate. Any other `invalid_grant` keeps its current mapping (`token_exchange_failed` at the callback; § 2.6.1 rows at refresh).
- **Only the exchange's `401` means «not an operator → `/onboarding`»** (§ 2.6) — unchanged. ~~A pool account that reaches the exchange **has** a live facet by construction (IAM would not have issued the token otherwise), so a `401` there means the facet vanished between token and exchange (race) — the existing `/onboarding` branch applies; 772 does not open `/onboarding` to facet-less pool accounts (that is TASK-MONO-773).~~ → **TASK-MONO-773**: «has a live facet by construction» no longer holds after S5 — a facet-less or non-`ACTIVE`-facet pool account reaches the exchange with a valid console token and gets `401`; that `401` is the **only** entry to the non-operator state (§ 2.6.4). The rule «only `401`» is unchanged.
- ~~Browser API client (`shared/api/client.ts`): on the refresh `503 OPERATOR_CHECK_UNAVAILABLE` it navigates to `/login?error=operator_check_unavailable&redirect=<current>` (the reason travels; a `503` with any other code keeps the reasonless `/login?redirect=…`). The `/login` page shows `operator_check_unavailable` **even when cookies are still complete** (refresh kept them) — like `mfa_required`, it is a reason that must be shown, not swallowed by the «already authenticated» short-circuit.~~ → retired by TASK-MONO-773 S6 (see the owner-decision bullet above). The browser client's refresh answers are now `403 MFA_REQUIRED` → step-up and `403 NOT_AN_OPERATOR` → `/new-tenant` (§ 2.6.4); every other failure → `/login?redirect=<current>`.
- Console implementation slice: **772 S4** (callback · refresh · client · `/login` copy — moved forward from S5 together with the IAM issuer so the new refusal never reaches a console that reads it as `token_exchange_failed`); the operators-screen «초대» change stays S5.

#### 2.6.4 The «logged in · not an operator» shell state (normative — TASK-MONO-773 · ADR-MONO-080 D9 = T1)

Owner decisions OD-6 · OD-7 (2026-10-15 UTC). Implementation slice **773 S6** (console-web), on top of IAM S3 (self-service trust gate) and S5 (IAM issues the console token to personal accounts whatever their operator facet — IAM [`auth-api.md` § 풀 계정의 콘솔 토큰](../../../iam-platform/specs/contracts/http/auth-api.md#풀-계정의-콘솔-토큰--운영자-측면이-있을-때만-task-mono-772--adr-mono-080-d6)). 🔴 S6 ships after S2 – S5 are on the demo backend (773 AC-0 § 10). Before 773 a valid IAM login that is not an operator left the `(console)` shell for the standalone `/onboarding` page (TASK-PC-FE-182); from 773 it **stays inside the shell**, with a two-item sidebar and **no operator token**.

**The state is set only from an explicit marker — never inferred (773 AC-0 R2).**

- **Marker cookie `console_non_operator`** = `1`: `HttpOnly`, `Secure` and `SameSite` exactly as the IAM access cookie, `Path=/`, `Max-Age` = the IAM access token's `expires_in` (the marker and the access cookie expire together). Not a credential: it only selects which shell renders; every server call in that shell still authenticates with the IAM access cookie, and the marker can never make an operator call succeed.
- **Set** by exactly three places, and only on the § 2.6 exchange answering **`401`**: `/api/auth/callback` (instead of the old `/onboarding` redirect), `GET /api/auth/refresh` and `POST /api/auth/refresh` (§ 2.6.1 rows). A `403 MFA_REQUIRED`, a `400` / `5xx` / timeout of the exchange, or any IAM token-step failure never sets it.
- **Cleared** whenever an exchange succeeds (operator cookie set → marker deleted in the same response), by `clearFullSession` (logout, § 2.6.1 «whole session cleared», § 2.6.2 demo-ended), and by the tenant-creation success path below.
- 🔴 «operator cookie absent» is **not** the predicate. An IAM session whose operator cookie expired, or whose exchange was unavailable, has no marker and takes the § 2.6.1 refresh hop exactly as today (operator TTL 3600 > access 1800 today, but one setting flips that — IAM `admin-api.md` § token-exchange).

**`(console)` guard — branch order** (`(console)/layout.tsx`; rows 1, 2 and 4 are unchanged):

| # | Cookies | Shell |
|---|---|---|
| 1 | access · operator · refresh all absent | sample shell (ADR-MONO-074 — unchanged) |
| 2 | access **and** operator present | operator shell (unchanged — a stray marker is ignored and dropped by the next route handler) |
| 3 | access **and** `console_non_operator` present, operator absent | **non-operator shell** (below) |
| 4 | anything else | § 2.6.1: refresh cookie present → `GET /api/auth/refresh?redirect=<path>`; absent → `/login?redirect=<path>` (unchanged) |

**Non-operator shell — what it renders and what it never calls.**

| Shell element | Operator shell | Non-operator shell |
|---|---|---|
| Registry `getCatalog()` (operator token — `401` → `RE_LOGIN_PATH`) | called | **skipped** — the sidebar is the static two-item list below |
| `getSelfRolesOrNull()` (`/api/admin/me`) | called | **skipped** |
| `listOrgNodes()` · `getOrgNodeTenants()` | called | **skipped** |
| `NotificationBell` (browser polling through `apiClient`) | mounted | **not mounted** — its `401` → browser refresh → exchange `401` was the loop of 773 Failure Scenario 1 |
| `DemoHeartbeat` | mounted | **not mounted** |
| Tenant switcher · `needsReassume` · assume (§ 2.7) | as today | **not rendered / not called** — there is no tenant to act in |
| § 2.6.2 «Live session» demo-stop check | as today (demo deployments) | **not run** — accepted gap: a non-operator session survives a demo stop until its next IAM refresh fails, which then reaches the § 2.6.2 sample landing |
| `AccountMenu` (logout) · `DemoBackendNotice` | mounted | mounted |

🔴 S6 asserts «**zero** operator-token calls in the non-operator state» with a unit test of the same shape as the sample visitor's fetch allow-list test (`sample-fetch-allowlist.test.ts`): render the non-operator shell and each allowed page, and fail on any call to an `/api/admin/**` proxy or an operator-token helper.

**Path allow-list (non-operator state).** Exactly two pages: **`/new-tenant`** and **`/guide`** (path equality; query strings allowed). Every other `(console)` path — including `/` and direct URLs to operator screens — answers `307 → /new-tenant` from the guard, so no operator page is ever rendered to call its operator API (773 AC-0 § 3: without this, each direct URL bounced through `401 → /login?error=session_expired → silent SSO → back`).

**Sidebar (non-operator state)**: exactly two items, in this order — «가이드» (`/guide`) · «테넌트 생성» (`/new-tenant`). No switcher, no domain menus. The operator shell's sidebar gains the same «테넌트 생성» item for every operator (a company operator's second company · a `SUPER_ADMIN`'s tenant creation); `/tenants` keeps its list.

**The tenant-creation screen `/new-tenant` — one screen, three modes** (773 Goal — «조직 만들기» `/onboarding` and «테넌트 등록» `/tenants` become this one screen). The path is deliberately **not** under `/tenants/…` so it cannot shadow `/tenants/[tenantId]` (no tenant slug needs to be reserved — 773 AC-0 § 9).

| Shell state | Mode | Form | Console BFF → IAM producer |
|---|---|---|---|
| non-operator | self (first company) | `tenantId` (slug) · `organizationName` | `POST /api/onboarding/organizations` → IAM `POST /api/admin/onboarding/organizations` with the **IAM access cookie** as `subjectToken` (unchanged BFF — TASK-PC-FE-182) |
| operator without a `'*'` `tenant.manage` grant | self (second company — ADR-MONO-082 D6 b) | same | same BFF, same IAM access cookie (the operator token is **not** used — the producer resolves the creator by `sub`) |
| operator with a `'*'` `tenant.manage` grant (`SUPER_ADMIN` — the predicate `/tenants` uses today for its SUPER_ADMIN-only form) | first admin by invitation | `tenantId` · `displayName` · `tenantType` (`B2B_ENTERPRISE` · `B2C_CONSUMER`) · **`firstAdmin.email`** · **`firstAdmin.displayName`** · optional `orgNodeId` | `POST /api/tenants` → IAM `POST /api/admin/tenants` (operator token). The proxy body (`api/tenants/_proxy.ts`) gains `firstAdmin`; its response schema gains `status: PENDING`, `firstAdmin`, `firstAdminInvitation`. 🔴 **This proxy/form change ships in the IAM S4 PR itself** (owner decision 2026-10-15 UTC, 773 S1 open question 7 — one cross-project atomic PR: IAM `firstAdmin` required + the existing `/tenants` create form and proxy gain `firstAdmin.email` · `firstAdmin.displayName` + the `PENDING` enum), so there is no window in which the console form gets `400`. S6 later moves the form onto `/new-tenant` |

- **Self-mode success** (`201` + the BFF's re-exchange `ready: true`): the BFF already sets the operator cookie and the active-tenant cookie to the new tenant (TASK-PC-FE-182 route); S6 adds — **delete the marker**, run the § 2.7 default-assume for the new tenant with the **same function** callback/refresh use, then the client navigates to `/` (the overview). `ready: false` (re-exchange failed) keeps the existing «created — sign in again to enter» message; the marker stays (the session is still a non-operator one until an exchange succeeds).
- **`SUPER_ADMIN`-mode success**: no re-exchange (nothing about the caller changed) → navigate to `/tenants`, where the new row shows «관리자 대기».
- **Error copy** (both self modes; codes are IAM's — `admin-api.md` / `onboarding-api.md`):

  | IAM answer | Screen |
  |---|---|
  | `403 EMAIL_NOT_VERIFIED` | «회사를 만들려면 먼저 이메일을 인증해야 합니다» + link to the IAM email-verification page |
  | `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` | «이 계정으로는 회사를 만들 수 없습니다. 사이트나 회사용 계정이라면 **개인 계정으로 로그인해 만드세요**» + logout. Who still gets this after IAM S3 (owner decision 2026-10-15 UTC, 773 S3 option A — supersedes the S1 open-question-2 «accepted + guidance» row): accounts that are not an `ACTIVE` personal account (site · B2B · non-`ACTIVE` personal accounts) and identities with neither an account record nor an operator facet. Operators signing in with an `iam` credential are **no longer** in this population — they are exempt from the email check (the cap and the rate limit still apply), so the copy no longer addresses them |
  | `409 OPERATOR_ALREADY_PROVISIONED` | «이 계정은 지금 회사를 새로 만들 수 없습니다 (플랫폼 관리자 또는 정지된 운영자)» |
  | `422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED` | «관리하는 회사 수가 한도에 도달했습니다» (TASK-MONO-784 S6 copy — unchanged) |
  | `409 TENANT_ALREADY_EXISTS` | slug-taken message on the `tenantId` field |
  | `429 RATE_LIMITED` | «잠시 후 다시 시도하세요» (honour `Retry-After`) |

**Empty state on `/new-tenant` (non-operator state only)** — top to bottom (773 Edge Case 2 «받은 초대 먼저»):

1. **«받은 초대»** — the console BFF `POST /api/onboarding/received-invitations` (server-side, IAM access cookie as `subjectToken`) → IAM [`POST /api/admin/onboarding/received-invitations`](../../../iam-platform/specs/contracts/http/onboarding-api.md#post-apiadminonboardingreceived-invitations--받은-초대-task-mono-773-od-7--구현-s3) (owner decision OD-7). Each item: company (`tenantDisplayName` ?? `tenantId`), roles, a «첫 관리자» badge when `firstAdmin`, expiry, and the instruction «초대 메일의 링크로 수락하세요 — 메일이 없다면 초대한 분께 다시 보내 달라고 요청하세요». 🔴 **Read-only** (owner decision 2026-10-15 UTC, 773 S1 open question 6): the console has no link and no accept button, and no «accept without the link» path exists — acceptance stays the TASK-MONO-772 email-link flow at the IdP, unchanged (IAM R4 — the mailbox is the proof). `accountState = EMAIL_NOT_VERIFIED` → «이메일을 인증하면 받은 초대와 회사 만들기가 열립니다» + link · `NOT_ELIGIBLE` → the `ONBOARDING_ACCOUNT_NOT_ELIGIBLE` copy above · IAM `503` → «받은 초대를 불러오지 못했습니다» (never rendered as «초대 없음»).
2. «회사에 관리자가 있다면 그 관리자에게 초대를 요청하세요» (static copy — 773 Scope).
3. The self-mode form.

**`/onboarding` stays as an address**: `GET /onboarding` → `307 → /new-tenant` for every visitor (the `(console)` guard then decides). The standalone `(onboarding)` guard and form are removed; callback and refresh no longer send anyone there (773 AC-5).

**`/tenants` (SUPER_ADMIN list)**: its `status` enum gains `PENDING` (`z.enum` widened in the same S6 PR — an unknown value fails the parse, 773 AC-0 R5). A `PENDING` row reads «관리자 대기» + the `firstAdmin.state` suffix (`EXPIRED` → «· 초대 만료», `NONE` → «· 초대 없음», `ACCEPTED` → «· 활성화 재시도»), with two row actions: **다시 초대** (`EXPIRED`/`INVITED` same address → that invitation's `:resend`; new address or `NONE` → `POST /api/admin/tenants/{tenantId}/first-admin-invitation`) and **정리** (`PATCH status=SUSPENDED`, confirm dialog «초대를 취소하고 테넌트를 정지합니다 — 되돌릴 수 없습니다»); a `PENDING` row with `firstAdmin.state = ACCEPTED` instead offers **활성화 마치기** (`PATCH status=ACTIVE` — the IAM recovery path). The old «테넌트 등록» form is replaced by a link to `/new-tenant`.

**Logout from the non-operator shell (773 AC-7)**: `AccountMenu` → the existing logout route — IAM revoke, `clearFullSession` (marker included), and IdP `end_session` with `id_token_hint`. IAM S5 issues an `id_token` with the non-operator console token, so the IdP session **ends** and the next `/login` shows the IdP login form — another account (an operator) can sign in without a store tab. Without the `id_token` the logout would be local only (`logout/route.ts` local branch) and the IdP session would silently sign the same account back in (773 AC-0 R9).

**Loop bound**: the non-operator landing `/new-tenant` is inside the guard but reaches it through branch 3 with no network hop; branch 3 renders no page that calls an operator API (allow-list + skipped calls). The only refresh hops are § 2.6.1's (when the access cookie — and with it the marker — has expired), bounded as there.

**Sample mode**: a sample visitor (branch 1) asking for `/new-tenant` gets a static notice on `/new-tenant` itself — no redirect and no network call (TASK-PC-FE-376, owner decision 2026-10-26 UTC: the sidebar item stays visible, so a silent bounce to `/` read as a broken menu). The notice says the sample shell cannot create a tenant and offers 로그인 = `/login?redirect=%2Fnew-tenant` (the shell's own `buildLoginRedirectFor`), which returns to this screen after sign-in, plus a link back to 개요. No creation form and no «받은 초대» read is mounted. `/new-tenant…` is rejected as a § 2.6.2 demo-ended target.

### 2.7 Active-Tenant Switcher → Assume-Tenant Exchange (normative — ADR-MONO-020 D4)

The active-tenant switcher re-scopes the operator's **domain-facing** credential to the selected customer. Setting the `console_active_tenant` cookie (X-Tenant-Id) alone does **nothing** — the federated domain entitlement gates (ADR-MONO-019 D5) trust the **signed** IAM OIDC token claims (`tenant_id` + `entitled_domains`), not a header. So on switcher selection the console **server-side** drives a second RFC 8693 exchange (the *assume-tenant* exchange, distinct from the § 2.6 operator exchange) to mint a short-lived IAM OIDC token re-scoped to the selected customer, and uses it as the domain-facing bearer. The cross-domain composition (§ 2.4.9) sends it verbatim on its non-IAM legs (ADR-MONO-017 D6 pass-through — no composition-specific change was needed).

- **Two server-side exchanges (do NOT conflate)**:
  - **§ 2.6 operator-identity exchange** (ADR-MONO-014): admin **JSON** `POST /api/admin/auth/token-exchange` → operator token for `/api/admin/**`. Unchanged.
  - **assume-tenant exchange** (this section): SAS **form-urlencoded** `POST ${OIDC_ISSUER_URL}/oauth2/token`, `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` + `subject_token=<base IAM OIDC access token>` + `subject_token_type=urn:ietf:params:oauth:token-type:access_token` + `audience=<selected tenant>` + `client_id`. **Consume only** — the request/response/error contract is owned producer-side by IAM [`auth-api.md` § Assume-Tenant Exchange](../../../iam-platform/specs/contracts/http/auth-api.md) (TASK-BE-327); this file states the consumer obligation, it does NOT redefine the wire shape.
  - **Response 200** (SAS shape): `{ access_token, token_type: "Bearer", expires_in }` — **no `refresh_token`** (the assumed token is short-lived and re-minted per selection / IAM refresh). The console stores `access_token` in its own HttpOnly·Secure·SameSite=Lax cookie (`console_assumed_token`) with `maxAge = expires_in`, validates `token_type === "Bearer"`.

- **Domain-facing credential resolution (the central change)**: `getDomainFacingToken()` = **the assumed token if an active-tenant assumption exists, else the base `getAccessToken()`**. Every tenant-scoped domain read uses it for the IAM-OIDC bearer:
  - the cross-domain overview composition (§ 2.4.9.1) — its non-IAM legs' `Authorization: Bearer`; the IAM leg keeps using the operator token, § 2.6, unchanged;
  - the 4 non-IAM domain section clients (`features/{wms,scm,finance,erp}-ops`, §§ 2.4.5–2.4.8) — the per-domain credential rule § 2.4.5 is unchanged, only **which** IAM OIDC token.
  - **IAM-domain clients** (`features/{accounts,audit,operators,dashboards}` → `getOperatorToken()`) are **unchanged** — the operator-token boundary (§ 2.1/§ 2.6, the #569 invariant) is untouched; `getDomainFacingToken()` is never a IAM `/api/admin/**` credential.
  - ~~**net-zero**: a non-switched / single-tenant operator has no assumed token, so `getDomainFacingToken()` returns the base token → existing behaviour is byte-identical.~~ 🔴 **Withdrawn by TASK-PC-FE-292 (2026-09-16).** The premise — «the base token is already scoped to the operator's home tenant» — is false for this client: `platform-console-web` tokens carry the client's operational slug `tenant_id=iam` (not a customer tenant; `entitled_domains` omitted), so every domain gateway rejects the base token. The resolver's fallback is unchanged in code, but a domain section no longer **reaches** it: see «Domain section gate» below.

- **Switch route (`POST /api/tenant`)**: after the existing registry-membership allow-check (defence-in-depth — kept), call the assume-tenant exchange (subject = base token, audience = selected tenant); on success set BOTH `console_assumed_token` (maxAge = `expires_in`) and `console_active_tenant=<tenant>` **atomically**. The assumed token is scoped to the current active tenant **by construction** (both set/cleared together; never serve an assumed token for a tenant ≠ the active-tenant cookie). On success it also records `console_last_tenant=<operator sub>|<tenant>` (HttpOnly, 30 days — a **preference, not a credential**; not cleared by logout) for the next login.

- **Default active tenant — login and idle refresh (TASK-PC-FE-292, owner decision 2026-09-16)**: the active tenant is **never** read from the IAM OIDC token (this is § 2.6's «tenant scope: never derived from the IAM OIDC token» — 🔴 the TASK-PC-FE-036 / TASK-MONO-674 default read the token's `tenant_id` and so contradicted it; for this client that value is `iam`, and the live result was every domain screen answering «세션 만료»). `/api/auth/callback` (after the § 2.6 exchange) and the refresh (when no active-tenant cookie survived) call one function over the registry's **selectable tenants** (§ 2.2 — the distinct `tenants` of `available` products, the same set the switch allow-checks):
  1. `console_last_tenant` for **this** operator (`sub` match), if still selectable;
  2. else the only selectable tenant, if there is exactly one;
  3. else none.
  A chosen tenant is **assumed** exactly as a switch (active-tenant session cookie + `console_assumed_token`, `maxAge = expires_in`) — the active-tenant cookie never stands without its assumed token. **Never fatal**: registry degraded / assume refused or unavailable / no choice ⇒ the login or refresh still succeeds with no active tenant. An `mfa_required` refusal here (TASK-MONO-771) is also not fatal and triggers **no** automatic step-up — the login lands with no active tenant and the step-up offer appears when that tenant is chosen — on the switcher **and** on the catalog tenant rows (a single-tenant operator's switcher is a static label, so the catalog row is the only choosing surface; owner decision 2026-10-09 UTC, TASK-MONO-771 S3). Judged by **relation** (in the registry's choices?), not by a list of non-customer slugs, so a future slug rename cannot reopen it.

- **Domain section gate (TASK-PC-FE-292)**: each domain section (`ecommerce` · `wms` · `scm` · `erp` · `finance` · `ledger`) renders only when an **assumed token** is present (a sample visitor passes — ADR-MONO-074); otherwise its layout shows «테넌트를 먼저 선택하세요» (`data-testid="domain-no-tenant"`) instead of calling a gateway with the base token and turning the 401 into `/login?error=session_expired`. Asked per section layout (the `(console)` shell layout is not re-rendered between sections). The section's guide pages sit behind the same gate.

- **Fail-closed switch** (mirrors the § 2.6 operator boundary — never degrade-with-fallback on the selected-tenant boundary):
  - assume-tenant `mfa_required` (producer `400 invalid_grant` **with `error_description` exactly `insufficient_user_authentication`** — TASK-MONO-771: the selected tenant requires a second factor, or the operator holds a `require_2fa` role, and the base token's `amr` lacks `mfa`) → `403 MFA_REQUIRED`, **no cookie change**. The switcher (and the catalog tenant row — the only choosing surface for a single-tenant operator) offers «2단계 인증 후 들어가기», which navigates to `GET /api/auth/step-up?redirect=<current path>` (§ 2.6); after the step-up the operator selects the tenant again. 🔴 Matched by **whole-value equality** on `error_description` — this is checked **before** the `denied` row below, and nothing else is read as it (a substring/case-insensitive match would turn an unassigned tenant into a step-up loop).
  - assume-tenant `denied` (producer `400 invalid_grant` with any other `error_description`: the D2 assignment gate / subject invalid / the producer's admin-service leg unavailable) → `403 TENANT_FORBIDDEN`, **no cookie change** (the prior selection + assumed token are preserved).
  - `invalid` (producer `400 invalid_request`: blank/malformed audience) → `422`.
  - `unavailable` (5xx / timeout / network / unexpected shape) → `503 DOWNSTREAM_ERROR`.
  - missing base IAM token → `401 TOKEN_INVALID` (no exchange attempted).
  - The base IAM OIDC token is the `subject_token` only — **never logged, never returned**; the console **never** falls back to the base token on the selected-tenant boundary (the silent wrong-tenant-view defect this closes).

- **Clear path**: `tenant=''` deletes **BOTH** `console_active_tenant` and `console_assumed_token` (they are coupled), and `console_last_tenant` (an explicit «no tenant» is what the next login respects — TASK-PC-FE-292).

- **Refresh re-assume**: the assumed token has no refresh token (the grant issues none). On IAM refresh (`/api/auth/refresh`), after the § 2.6 operator-token re-exchange, when an active tenant is set the console **re-assumes** it from the rotated base token. On re-assume failure it drops BOTH the assumed token and the active tenant (the operator falls back to base/no-tenant — never a stale assumed token); the base IAM + operator session stays valid. A whole-session drop (IAM refresh rejected / operator re-exchange fail-closed) also clears the assumed token + active tenant.

- 🔴 **Assumed-token TTL becomes 300 s (ADR-MONO-083 D7 · OD-2 default — producer P3 `TASK-BE-632`; console obligation P4 `TASK-PC-FE-338`)**: from P3 the assume-tenant `expires_in` is **300** (today 1800 — IAM [`auth-api.md` § Assume-Tenant Exchange](../../../iam-platform/specs/contracts/http/auth-api.md)); the base IAM token and refresh TTLs do not change. The assumed token is therefore likely to expire **between** IAM refreshes, and re-assuming only on IAM refresh (the bullet above) is no longer enough. The console MUST re-assume the active tenant **before** the assumed token's expiry (e.g. when a domain-facing call finds less than a safety margin left on `console_assumed_token`, or on a timer), from the current base token — the same exchange, the same fail-closed rows above. An assumed token that has expired must not turn into `/login?error=session_expired` while the base session is valid: re-assume first, and drop to «테넌트를 먼저 선택하세요» only if the re-assume itself is refused. Each re-assume re-runs the assignment and business-permission judgement — that is what makes revocation reach a live session within one TTL (D7: «다음 재교환부터 · 기존 토큰은 `exp` 까지»).

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

> **Row 12 («operators: create») after TASK-MONO-772:** the producer endpoint, the
> client export and the header obligation (`reason + idem`) are unchanged, so the
> row and its fixture stay as they are — but from 772 S7 the producer accepts
> only `tenantId='*'` (platform-scope create); a company operator is made by
> **invitation → verified self-acceptance** (§ 2.4.3 rows 11–14). The invitation
> calls are additive to the already-verified § 2.4.3 operators parity line and
> add **no** § 3 row (count stays **16**) — the admin-web parity gate this
> matrix records was satisfied before 772 and is not re-opened by it.

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
