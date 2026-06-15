# TASK-SCM-BE-029 — Align scm service specs to the roles-only operator model (reframe the BUYER/OPERATOR actor as a `roles`-claim derivation)

**Status:** review

**Type:** TASK-SCM-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (security/authorization-adjacent doc alignment — net-zero, but the operator-admission model must be stated exactly)

---

## Goal

Bring the scm-platform service specs into conformance with the **roles-only identity model** that landed on `main` via ADR-MONO-032 (unified identity → `roles` as the sole authorization axis) and ADR-MONO-035 (operator authentication unification), whose gateway leg drop shipped for all four gateways (including scm) in TASK-MONO-262 (ADR-035 4b-2a — drop the dead `account_type` admission legs + `X-Account-Type` injection; role-based admission per ADR-032 D3).

The scm **code is already roles-based** — procurement's `ActorContext.isOperator()` keys purely on the `roles` claim (`roles ∋ {OPERATOR, ADMIN, SUPER_ADMIN}`), `ActorContext.actorType()` maps that to `ActorType.OPERATOR` else `ActorType.BUYER`, and `ActorContextJwtAuthenticationConverter` lifts only `roles`/`role` (+ `sub` + `tenant_id`). The scm gateway `JwtHeaderEnrichmentFilter` no longer injects `X-Account-Type` (MONO-262) and never admitted on `account_type` (scm admits on `tenant_id` via `TenantClaimValidator`). The scm app code contains **zero** `account_type` / `accountType` references.

The drift is **purely in the specs**: `procurement-api.md` frames the PO actor as "BUYER or OPERATOR **scope**", conflating the actor **role** (from the `roles` claim) with the OAuth **scope** (`scm.read`/`scm.write`, the only scopes scm issues). Neither `procurement-service/architecture.md` nor the event contract records that the BUYER/OPERATOR actor is **derived from the `roles` claim** (`ActorContext.isOperator()`), so a future reader cannot tell the actor model is roles-only.

This is a **doc-only, net-zero spec-alignment** task: no code, no contract semantics, no schema, no ADR decision change. It executes the spec-side of the already-ACCEPTED ADR-032/035 (the same "apply the merged identity change to the project's specs" shape as TASK-ERP-BE-021 for erp), so it introduces no new architecture decision (HARDSTOP-09 clean — the decisions live in ADR-032/033/034/035 + the executing task MONO-262).

## Scope

**In scope** — scm spec drift sites:

1. `specs/contracts/http/procurement-api.md` — **(a)** the per-endpoint auth notes that read "(BUYER or OPERATOR **scope**)" / "OPERATOR actor only" / "BUYER or OPERATOR": reframe so BUYER/OPERATOR are named as the **actor** derived from the `roles` claim (not an OAuth scope; scm's only scopes are `scm.read`/`scm.write`). **(b)** add a brief "Actor model" note (after the top-of-file auth bullets) stating that the actor is derived from the verified `roles` claim — `roles ∋ {OPERATOR, ADMIN, SUPER_ADMIN}` → OPERATOR actor, otherwise BUYER — matching `ActorContext.isOperator()`, consistent with the roles-only identity model (ADR-032/035; `account_type` fully removed, never read by scm).
2. `specs/services/procurement-service/architecture.md` — § PO State Machine ("Allowed transitions per actor") and/or § Security: add a one-paragraph note documenting that the `ActorType` (BUYER/OPERATOR) is **derived from the JWT `roles` claim** via `ActorContext.isOperator()` (`ActorContextJwtAuthenticationConverter` → `ActorContext`), making the roles-only model explicit (no `account_type`; aligns with ADR-032 D3 / ADR-035, executed at the gateways by MONO-262). SUPPLIER/SYSTEM actors are not token-derived (webhook / internal).
3. `specs/contracts/events/scm-procurement-events.md` — the `actorAccountId` field descriptions on `po.confirmed` / `po.canceled` name the OPERATOR/BUYER actor; add a one-line note (envelope/consumer-rules area or inline) that the actor **type** behind `actorAccountId` is the roles-derived `ActorType` (no separate actor-type field is emitted; the value is the actor's `sub`). Net-zero — no payload field added/changed.
4. `specs/contracts/http/demand-planning-api.md` — the L6 "Operator-authenticated." line: clarify that the demand-planning operator surface is **tenant-gated** (`tenant_id=scm` + entitlement-trust dual-accept) and consumed by the platform-console operator (IAM `platform-console-web` token); scm enforces **no** operator/admin role split on these routes (per `gateway-public-routes.md` § operator-action/config consumer — the gate is server-side `tenant_id` + the DRAFT-PO-only invariant, not a stronger credential). Keep it net-zero with the canonical `gateway-public-routes.md`.
5. `specs/integration/iam-integration.md` — already free of `account_type`; add a short note (Token-validation rules or the platform-console operator section) recording the roles-only model: scm admits on `tenant_id` (+ entitlement), the `account_type` claim/header is fully removed (ADR-032 D5 step 4 / ADR-035 4b, executed at scm's gateway by MONO-262 — scm never admitted on it), and where a downstream service distinguishes operator vs buyer it keys on the `roles` claim. Cross-reference-only (no producer/auth-model redefinition).

**Out of scope (unchanged):**

- **All scm app code** — already roles-based; no `account_type` reference exists. This task changes **zero** `.java` / `.yml` / Flyway / `docker-compose` files.
- The IAM issuance side (assume-tenant role derivation, token customizers) — owned by iam-platform; scm specs only cross-reference it.
- The entitlement-trust dual-accept tenant gate (ADR-MONO-019 § D5 / TASK-SCM-BE-019) — a separate `tenant_id`/`entitled_domains` concern, not the `account_type`→roles axis; its narrative stays unchanged.
- `gateway-service/architecture.md` — already lists the enrichment headers without `X-Account-Type` (roles-only-consistent); no change required (verify only).
- `gateway-public-routes.md` — already canonical and roles-only/tenant-gated for the operator surface; no change (it is the authority the other files defer to).
- `PROJECT.md` frontmatter (`domain=scm` / `traits=[transactional,integration-heavy,batch-heavy]` / `service_types=[rest-api,event-consumer,batch-job]`) — byte-unchanged. scm stays single-org (`multi-tenant` non-declaration unaffected).

## Acceptance Criteria

- **AC-1** — No scm spec frames the PO actor (BUYER/OPERATOR) as an OAuth **scope**. The actor is named as a `roles`-claim derivation. The only OAuth scopes named in scm specs remain `scm.read`/`scm.write`.
- **AC-2** — At least one scm spec (procurement-api.md actor-model note **and** procurement-service/architecture.md) explicitly documents that the `ActorType` (BUYER/OPERATOR) is derived from the JWT `roles` claim via `ActorContext.isOperator()` (`roles ∋ {OPERATOR, ADMIN, SUPER_ADMIN}` → OPERATOR, else BUYER), consistent with the live code.
- **AC-3** — The roles-only model is recorded: where any scm spec references the obsolete `account_type`/`X-Account-Type`, it appears **only** to note the claim/header was removed (ADR-032 D5 step 4 / ADR-035 4b / MONO-262) — never as a live/required claim. (Pre-task baseline: scm specs already contain zero `account_type`/`X-Account-Type` mentions; AC-3 guards against re-introducing them as live.)
- **AC-4 (net-zero / doc-only)** — `git diff origin/main -- 'projects/scm-platform/apps/**'` is empty (no code change). No contract semantics (no request/response field, error code, route, idempotency, or event payload field added/changed), no Flyway, no ADR decision changed. All edits are under `projects/scm-platform/specs/**` + the task lifecycle/INDEX.
- **AC-5** — Cross-references point to the correct authorities: ADR-MONO-032 (D3 role-based admission / D5 step 4 `account_type` removal), ADR-MONO-035 (operator auth unification), TASK-MONO-262 (the gateway leg drop that executed it). The entitlement-trust (ADR-MONO-019) references are preserved where still accurate.

## Related Specs

- `projects/scm-platform/specs/contracts/http/procurement-api.md` (per-endpoint auth notes + new Actor model note)
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (§ PO State Machine / § Security — actor derivation)
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` (`actorAccountId` descriptions)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (operator-surface auth note)
- `projects/scm-platform/specs/integration/iam-integration.md` (Token-validation / platform-console operator section)

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the contract whose `account_type` deprecation→removal ADR-035 4b finalized (the authority for "the claim no longer exists").
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § D3 (role-based gateway admission, `X-Account-Type` dropped) / § D5 step 4 (the `account_type` removal).
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (operator auth unification; § O5 4b gateway leg drop).
- `tasks/done/TASK-MONO-262-gateway-roles-only-admission.md` — the 4-gateway code change (incl. scm) that executed the leg drop + `X-Account-Type` removal.
- `docs/adr/ADR-MONO-019-...` § D5 (entitlement-trust dual-accept — preserved cross-refs only).

## Edge Cases

- **Actor ≠ tenant gate.** scm's tenant gate (`tenant_id ∈ {scm,*}` ∪ signed `entitled_domains ∋ scm`, ADR-019) is a **separate** axis from the BUYER/OPERATOR actor (roles claim). The edits must not conflate them — admission is tenant-based; the actor split is roles-based and only decides which PO transitions a token may drive.
- **scm has no operator/admin role split on the demand-planning surface.** Per `gateway-public-routes.md`, approve/dismiss/seed ride the same operator IAM token with no role check — the gate is `tenant_id` + DRAFT-PO-only. The demand-planning-api.md edit must say this (do not invent an SCM_OPERATOR role requirement there). Procurement `confirm` **does** require `roles ∋ OPERATOR` via `isOperator()` — that asymmetry is real and must be preserved exactly.
- **BUYER is a fallback, not an account_type.** `actorType()` returns BUYER for any authenticated caller lacking an operator role — it is not a registered account type/scope. The spec must describe it as the default actor mapping, not a credential.
- **No domain-prefixed role.** Unlike erp (`ERP_OPERATOR`), scm's `isOperator()` checks the **generic** `OPERATOR`/`ADMIN`/`SUPER_ADMIN`. Describe what the code actually checks — do not import erp's `ERP_OPERATOR` naming.

## Failure Scenarios

- **F1 — stale spec misleads a future implementer** — leaving "BUYER or OPERATOR scope" would lead a future change to add an OAuth scope or `account_type` for the actor split, re-introducing the dead axis ADR-035 removed. Guarded by AC-1/AC-2.
- **F2 — over-editing introduces a real (non-net-zero) change** — e.g. asserting demand-planning enforces an OPERATOR role, or adding an actor-type event field, would mis-describe live behavior. Guarded by AC-4 and the Edge Cases (code byte-unchanged; demand-planning is tenant-gated; no payload change).
- **F3 — drift re-accumulates elsewhere** — a missed scm spec still framing the actor as a scope/account_type. Guarded by AC-1/AC-3's repo-wide `grep` over `projects/scm-platform/specs`.
