---
id: TASK-BE-505
type: TASK-BE
title: order-service /api/internal/** accepts any valid IAM token, not just the declared system credential
status: review
service: order-service
domain: ecommerce
traits: [transactional, multi-tenant]
created: 2026-07-17
---

# TASK-BE-505 — order-service internal endpoint accepts any valid token (declared-not-enforced system-credential gate)

## Goal

Enforce the `order-confirm-paid-stale.md` contract's promise that the internal
`/api/internal/orders/**` endpoints "NEVER execute without a valid **system**
credential". Today the security chain validates signature + timestamps + issuer +
(optional) audience and then does `.anyRequest().authenticated()` — but every
ecommerce token (system client_credentials AND ordinary CUSTOMER access tokens)
is minted by the **same** Spring Authorization Server issuer, so "authenticated"
does not distinguish a system credential from a user token. Pin the token `sub`
to the reserved internal client-id(s) so only a genuine system credential passes.

## AC-0 — Finding confirmation (re-measured against `main` @ 234698c12)

1. **Declaration** — `specs/contracts/http/internal/order-confirm-paid-stale.md:35-49`:
   "`client_credentials` Bearer JWT … minted via the reserved IAM OAuth client
   **`ecommerce-internal-services-client`** … the endpoint **NEVER executes the
   sweep without a valid system credential**."
2. **Enforcement gap** — `OrderSecurityConfig.internalFilterChain` does
   `.securityMatcher("/api/internal/**")` + `.anyRequest().authenticated()`. The
   `internalJwtDecoder` pins signature + timestamps + issuer + audience only.
   **No `sub`/`azp`/`client_id`/scope/authority check** distinguishes a system
   token from a user token. No scope→authority converter exists in order-service.
3. **Shared issuer confirmed** — `auth-service` `TenantClaimTokenCustomizer`:
   `client_credentials` and `authorization_code` tokens come from the same SAS
   issuer. A `client_credentials` token's `sub` = the client-id (never overridden
   to the account UUID — that override is `authorization_code`/`refresh_token`
   only) and carries no `roles`; a CUSTOMER token's `sub` = account UUID + `roles`.
   So `sub` is the reliable discriminator.
4. **Audience pin is also vacuous by default** — `AudienceValidator` returns
   success when the expected audience is blank and `application.yml` defaults
   `order.internal.oauth2.audience:` to empty — absent an env override, even the
   audience check admits any token. (The `sub` pin closes the hole regardless; the
   audience remains an optional per-env defence-in-depth.)
5. **Blast radius** — the same chain also fronts `POST /api/internal/orders/{orderId}/cancel`
   (TASK-BE-428), which cancels via `findByIdAcrossTenants` with **no
   ownership/tenant/role check** → a valid non-system token could cancel an
   arbitrary order in any tenant and trigger the refund + coupon-restore fan-out.
   Severity MED because `/api/internal/**` is **not gateway-routed**, so
   exploitation needs an internal-network foothold — a broken defence-in-depth
   promise, not an open external path.
6. **Vacuous test (green-CI-hides-it)** — `ConfirmPaidStaleIT` issues **every**
   token as `jwt.issueToken("ecommerce-internal-services-client", …)`; it tests
   no-bearer/wrong-issuer/wrong-audience → 401 and valid → 200, but **never** mints
   a valid token with a *different* subject and asserts rejection.

## Direction (chosen: Option A)

- **Option A (IMPLEMENTED) — decoder-level subject allow-list.** Add
  `SystemClientSubjectValidator` to the `internalJwtDecoder` validator chain
  (alongside `AudienceValidator`), requiring `sub ∈ order.internal.oauth2.allowed-client-ids`
  (default `ecommerce-internal-services-client`). Fail-closed → **401** via the
  existing entry point, matching the contract's 401 framing. Minimal, mirrors
  `AudienceValidator`, no cross-repo IAM seed change, robust against user tokens
  (sub=UUID) and other platforms' clients (sub=their client-id).
- **Option B (rejected) — dedicated OAuth scope** (`SCOPE_ecommerce.internal`):
  needs the GAP client seed to grant it (cross-repo) + a scope→authority converter;
  wider blast radius, no security gain over A.

## Acceptance Criteria

1. A valid token (correct issuer + audience, unexpired) whose `sub` is NOT an
   allow-listed client-id → **401 `UNAUTHORIZED`**; the sweep/cancel never runs.
2. A genuine system `client_credentials` token (`sub = ecommerce-internal-services-client`)
   → unchanged behaviour (200 sweep / cancel).
3. Existing fail-closed cases (no/expired/malformed/wrong-issuer/wrong-audience)
   still 401.
4. The allow-list is env-configurable; an **empty** allow-list fails closed.
5. Regression tests exist that fail against pre-change `main`. Fast-lane unit test
   is the authority (`ConfirmPaidStaleIT` is a Testcontainers lane); the IT adds
   the end-to-end cases. Mutation-checked (no-op validator → the guard cases RED).
6. The contract's Authentication section documents the subject pin.

## Related Specs / Contracts

- `specs/contracts/http/internal/order-confirm-paid-stale.md` § Authentication (updated)
- `specs/integration/iam-integration.md` § OAuth Clients (`ecommerce-internal-services-client`)
- `iam-platform` `TenantClaimTokenCustomizer` (token-shape source of truth)

## Edge Cases

- `{orderId}/cancel` (BE-428) shares the chain → also gated. Its only intended
  caller is a system relay (gateway-excluded path); no non-system caller exists
  today (grep: only batch-worker calls `/api/internal/**`, via a cc token).
- `sub == null` → rejected (no NPE).
- A future second system client → add its id to `allowed-client-ids` (comma-separated).

## Failure Scenarios

- **Cross-tenant order cancel:** internal-network actor with a valid CUSTOMER token
  → today cancels an arbitrary tenant's order → refund + coupon fan-out. After: 401.
- **Mass forward-confirm:** same actor → mass-confirms every tenant's PENDING+paid
  orders. After: 401.

## Notes

- Recommend annotation: `(분석=Opus 4.8 / 구현=Opus — security authz gate,
  token-shape reasoning, mutation-checked)`.
- Lean ecommerce lifecycle: impl PR carries task(ready→review) + code + tests +
  contract; a follow-up chore PR moves review→done + INDEX note.
