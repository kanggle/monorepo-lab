# TASK-BE-382 — Align ecommerce admin-controller javadoc to the roles-only operator model (drop `account_type=OPERATOR` framing)

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (mechanical comment-only alignment — net-zero behavior, but the operator-admission rationale must be inverted correctly)

---

## Goal

Bring the ecommerce operator-plane admin controllers' javadoc into conformance with the **roles-only identity model** that landed on `main` via ADR-MONO-032 (unified identity → `roles` as the sole authorization axis) and ADR-MONO-035 (operator authentication unification + operator domain-role issuance, step 4 COMPLETE 2026-06-15). Those ADRs **fully removed** the `account_type` claim/column/gateway-leg/`X-Account-Type` header, and made an operator's ecommerce-domain authorization ride the JWT `roles` claim — the operator's `ADMIN` domain role is **derived at assume-tenant** from the selected tenant's entitled domains (`ecommerce → ADMIN`, ADR-035 O1 / step 4a, gated fail-closed by the assignment check).

The ecommerce gateway **code is already roles-only** — [`AccountTypeEnforcementFilter`](../../apps/gateway-service/src/main/java/com/example/gateway/filter/AccountTypeEnforcementFilter.java) admits `/api/admin/**` on `roles ∋ ADMIN` (the legacy `account_type` OR-branch was removed at ADR-035 4b-2a), and [`JwtHeaderEnrichmentFilter`](../../apps/gateway-service/src/main/java/com/example/gateway/filter/JwtHeaderEnrichmentFilter.java) no longer injects `X-Account-Type`. The drift is **purely in the controller javadoc**, which still (a) frames gateway admission as `account_type=OPERATOR`, and (b) carries a rationale ADR-035 has since **inverted** — "the platform-console operator presents a token with **no** ecommerce-local ADMIN role, so RBAC is not applied"; post-4a that token **does** carry `ADMIN` (derived).

This is a **comment-only, net-zero** alignment task: no production logic, no contract, no schema, no ADR change. It executes the code-comment side of the already-ACCEPTED ADR-032/035 (sibling of the spec sweep PR #1603 and the erp spec sweep TASK-ERP-BE-021), so it introduces no new architecture decision (HARDSTOP-09 clean — the decisions live in ADR-032/035).

## Scope

**In scope** — admin-controller javadoc drift sites (comment text only):

1. [`product-service/.../AdminProductController.java`](../../apps/product-service/src/main/java/com/example/product/presentation/controller/AdminProductController.java) — `list()` javadoc (the TASK-MONO-243 read-leg rationale) + `register()` javadoc + the repeated short "authorization at the gateway (OPERATOR + tenant_id …)" comments on `update`/`delete`/`addVariant`/`updateVariant`/`deleteVariant`/`adjustStock`.
2. [`product-service/.../AdminProductImageController.java`](../../apps/product-service/src/main/java/com/example/product/presentation/controller/AdminProductImageController.java) — class javadoc.
3. [`order-service/.../AdminOrderController.java`](../../apps/order-service/src/main/java/com/example/order/presentation/AdminOrderController.java) — class javadoc.

Each must read: gateway `AccountTypeEnforcementFilter` requires `roles ∋ ADMIN` for `/api/admin/**`; the platform-console operator carries the `ADMIN` domain role via the ADR-035 4a assume-tenant derivation (ecommerce-entitled tenant → `ADMIN`); the service applies **no additional ecommerce-local RBAC** — the gateway is the single admission point (header-trust service). `TenantClaimValidator` + the repository `WHERE tenant_id` chokepoint (Step 2 / M6) narrative is preserved unchanged.

**Out of scope (unchanged):**

- **All production logic** — the controllers are header-trust pass-throughs; method bodies, mappings, signatures byte-unchanged. Zero `.java` *logic* change; only javadoc comment text.
- **Gateway filters** — already roles-only (this task documents their behavior, does not change it).
- **e2e / frontend `account_type` residue** — handled by the sibling TASK-INT-025 (web-store `accountType` field + the account_type e2e specs).
- Contracts, ADRs, `PROJECT.md`, Flyway, docker-compose — no change. (`product-api.md` operator-plane Authorization note was already corrected in PR #1603.)

## Acceptance Criteria

- **AC-1** — No ecommerce controller javadoc frames the operator token's **current** gateway admission via `account_type=OPERATOR`. The only residual `account_type` mention (if any) is **explanatory** — naming the claim solely to record it was *removed* (ADR-032 D5 step 4 / ADR-035 4b). repo-wide grep over `apps/**/*.java` for `account_type` returns only such explanatory mentions (or zero).
- **AC-2** — The "operator presents a token with no ecommerce-local ADMIN role" rationale is corrected everywhere: each javadoc states the operator **carries** `ADMIN` (ADR-035 4a derivation) and the gateway admits on `roles ∋ ADMIN`, with the service applying no *additional* RBAC (gateway = single admission point).
- **AC-3 (net-zero / behavior-unchanged)** — `git diff origin/main -- 'projects/ecommerce-microservices-platform/apps/**'` touches **only javadoc/comment lines** — no statement, signature, annotation, or import changes. `./gradlew :product-service:compileJava :order-service:compileJava` GREEN.
- **AC-4** — Cross-references point to the correct authorities: ADR-MONO-032 (D5 step 4 — `account_type` removal), ADR-MONO-035 (O1/4a — operator `ADMIN` derivation; 4b — gateway leg removal). The `TenantClaimValidator` / `WHERE tenant_id` (Step 2 / M6) narrative is preserved.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/contracts/http/product-api.md` (operator-plane Authorization note — already corrected, PR #1603; the javadoc must match it)
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` (§6 Role 강제 — roles-only; §7 Header Enrichment — no `X-Account-Type`)
- `projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md` (§4 plane = `roles`)

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the contract whose `account_type` deprecation→removal ADR-035 4b finalized.
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § D5 step 4 (the `account_type` removal).
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O1 / § O5 4a (operator `ADMIN` derivation at assume-tenant) + 4b (gateway leg + `X-Account-Type` removal).

## Edge Cases

- **Read-leg vs write-leg collapse** — the old javadoc drew a read (account_type-only) vs write (ADMIN-role) distinction. Post-ADR-035 the gateway gates *all* `/api/admin/**` uniformly on `roles ∋ ADMIN`, and the operator carries `ADMIN`, so the distinction collapses. The corrected javadoc must NOT re-assert a read/write admission asymmetry that no longer exists.
- **Header-trust framing preserved** — product/order-service are **not** JWT resource servers; they trust gateway-injected `X-User-Role`/`X-Tenant-Id`. The "no additional ecommerce-local RBAC at the service" statement stays true (the service still applies none); only the *gateway* admission predicate changes from `account_type=OPERATOR` to `roles ∋ ADMIN`.
- **Comment-only invariant** — an accidental code-line edit would break AC-3's net-zero guarantee. Keep edits inside `/** … */` / `//` regions.

## Failure Scenarios

- **F1 — stale comment misleads a future implementer** — leaving `account_type=OPERATOR` in the javadoc would cause a future change to re-introduce or depend on a claim that no longer exists, re-opening the mis-authorization gap ADR-035 closed. Guarded by AC-1.
- **F2 — inverted rationale left intact** — leaving "operator has no ADMIN role" would mislead someone into adding an ecommerce-local `ADMIN` grant the model no longer needs (operators get `ADMIN` via derivation). Guarded by AC-2.
- **F3 — accidental behavior change** — editing a statement instead of a comment. Guarded by AC-3 (`compileJava` GREEN + diff-is-comments-only review).
