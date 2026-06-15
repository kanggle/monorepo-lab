# TASK-BE-383 — Align wms service specs to the roles-only operator model (document the `WMS_OPERATOR` domain-role source)

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (security/authorization-adjacent doc alignment — net-zero, but the operator-admission model must be stated exactly)

---

## Goal

Bring the wms-platform service specs into conformance with the **roles-only identity model** that landed on `main` via ADR-MONO-032 (unified identity → `roles` as the sole authorization axis) and ADR-MONO-035 (operator authentication unification + operator domain-role issuance). Those ADRs **fully removed** the `account_type` claim/column/gateway-leg (ADR-032 D5 step 4 + step 5, COMPLETE 2026-06-15) and made an operator's domain authorization ride the JWT `roles` claim, with the operator's domain role **derived at assume-tenant** from the selected tenant's entitled domains (`wms → WMS_OPERATOR`, the operator-role mirror of `RoleSeedPolicy`, ADR-035 O1 / step 4a, gated fail-closed by the operator-assignment check; the cross-domain e2e assertion `AssumeTenantExchangeIntegrationTest` in TASK-MONO-265 verifies an operator's assumed token derives `roles = WMS_OPERATOR/ADMIN`, roles-only, no `account_type`).

Unlike the erp drift (TASK-ERP-BE-021), wms specs **never carried** the literal `account_type=OPERATOR` operator-token framing (there was no masterdata-style v1-bridge paragraph), and the wms gateway + service Security sections are **already roles-based** (`role`/`roles` claim → `X-User-Role`; `TenantClaimValidator`; entitlement-trust `entitled_domains` dual-accept). The drift is a **documentation gap**: no wms spec records **where an operator's `WMS_OPERATOR` domain role comes from** post-ADR-035 (the assume-tenant derivation), nor that operators no longer ride the now-removed `account_type` leg. The admin-service entitlement-trust narrative (which synthesises a READ-only `WMS_VIEWER` from `entitled_domains`) is still accurate, but it predates ADR-035 and does not relate itself to the new operator-role path.

This is a **doc-only, net-zero spec-alignment** task: no code, no contract semantics, no schema, no ADR decision change. It executes the spec-side of the already-ACCEPTED ADR-032/035 (the same "apply the merged identity change to the project's specs" shape as the erp/ecommerce/iam drift sweeps), so it introduces no new architecture decision (HARDSTOP-09 clean — the decisions live in ADR-032/033/034/035).

## Scope

**In scope** — wms spec drift sites:

1. `specs/services/admin-service/architecture.md` — § Security / Authorisation:
   - **(a)** Add an **operator domain-role source** bullet: a console operator's WMS domain roles (`WMS_OPERATOR` etc.) are derived by IAM at assume-tenant from the selected tenant's entitled domains (`wms → WMS_OPERATOR`, ADR-035 O1/4a, fail-closed by the assignment check); with ADR-032 D5 step 4+5 the `account_type` claim/column/gateway-leg was fully removed, so operators ride the `roles` claim only (gateway forwards `X-User-Role`, `@PreAuthorize` consumes it).
   - **(b)** Augment the existing **entitlement-trust READ dual-accept** bullet to relate it to ADR-035: post-step-4a a console operator's assume-tenant token for a wms-entitled tenant **also** carries `roles ∋ WMS_OPERATOR` directly, so the `WMS_VIEWER` synthesis now primarily serves **non-operator** wms-entitled token shapes; retained **net-zero** as defense-in-depth, the live `jwtAuthenticationConverter` unchanged. Preserve the existing ADR-MONO-019 §D5 / ADR-MONO-020 D4 / TASK-MONO-162 lineage references (the `entitled_domains` dual-accept decision is unchanged by ADR-035).
2. `specs/integration/iam-integration.md` — add a short note (after the § Token 검증 규칙 list) documenting the operator domain-role derivation source (`wms → WMS_OPERATOR` at assume-tenant, ADR-035 O1) and the `account_type` removal, since no wms spec currently records where `WMS_OPERATOR` originates. Cross-reference-only (no producer/auth-model redefinition); note the entitlement-trust `WMS_VIEWER` path is separate.

**Out of scope (unchanged):**

- **All wms app code** — already roles-based; no `account_type` reference exists in wms specs or code. This task changes **zero** `.java` / `.yml` / Flyway / `docker-compose` files.
- The IAM issuance side (`OperatorRoleDerivation` / `RoleSeedPolicy` operator mirror, `TenantClaimTokenCustomizer`, `AssumeTenantAuthenticationProvider`) — owned by iam-platform; wms specs only cross-reference it.
- The tenant-gate entitlement-trust dual-accept (gateway `TenantClaimValidator`, ADR-MONO-019 §D5 / TASK-BE-323) and the admin-service `WMS_VIEWER` synthesis **mechanism** — unchanged (they key on `entitled_domains`, a claim ADR-035 did **not** remove). Only the surrounding operator-role narrative is augmented.
- Scope semantics (`wms.*.read/write`), contract files, ADR docs, `PROJECT.md` frontmatter — no change (frontmatter byte-unchanged).

## Acceptance Criteria

- **AC-1** — No wms spec frames the operator token's authorization via `account_type` / `X-Account-Type` (the repo-wide `grep` over `projects/wms-platform/specs` for `account_type`/`X-Account-Type` stays at **0** matches, both before and after — wms never carried it; the only new mentions are **explanatory**, naming the claim solely to record that it was *removed*).
- **AC-2** — The operator domain-role source (`wms → WMS_OPERATOR`, derived at assume-tenant from the selected tenant's entitled domains, ADR-035 O1/4a, fail-closed by the assignment check) is documented in at least one wms spec (admin-service architecture + iam-integration), and the admin-service operator-admission narrative is consistent with the code path (`roles` claim → `X-User-Role` → `@PreAuthorize`).
- **AC-3** — The admin-service entitlement-trust bullet still describes the `WMS_VIEWER` synthesis accurately (READ-only, never WRITE, fail-closed) and now additionally notes that an operator's assume-tenant token carries `WMS_OPERATOR` directly, the dual-accept retained net-zero as defense-in-depth.
- **AC-4 (net-zero / doc-only)** — `git diff origin/main -- 'projects/wms-platform/apps/**'` is empty (no code change). No contract semantics, no Flyway, no ADR decision changed. All edits are under `projects/wms-platform/specs/**` + the task lifecycle/INDEX.
- **AC-5** — Cross-references point to the correct authorities: ADR-MONO-032 (D5 step 4/5 — `account_type` removal), ADR-MONO-035 (O1/4a — operator domain-role derivation). The pre-existing ADR-MONO-019 §D5 / ADR-MONO-020 D4 references for the `entitled_domains` dual-accept are preserved.

## Related Specs

- `projects/wms-platform/specs/services/admin-service/architecture.md` (§ Security — Roles / Authorisation / entitlement-trust READ dual-accept)
- `projects/wms-platform/specs/integration/iam-integration.md` (§ Token 검증 규칙)
- `projects/wms-platform/specs/services/gateway-service/architecture.md` (§ JWT Validation — already roles-based; cross-ref only, unchanged)

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the contract whose `account_type` deprecation→removal ADR-035 4b finalized (the authority for "the claim no longer exists").
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § D5 step 4/5 (the `account_type` removal).
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O1 / § O5 4a (operator domain-role derivation at assume-tenant; the `wms → WMS_OPERATOR` mapping).
- `docs/adr/ADR-MONO-019-*.md` § D5 (entitlement-trust dual-accept — preserved cross-ref only).

## Edge Cases

- **Entitlement-trust vs operator-role separation** — the admin-service `WMS_VIEWER` synthesis grants READ-only visibility from `entitled_domains`; it is **orthogonal** to the operator's `WMS_OPERATOR` role (which grants read-everywhere + WRITE in inventory/inbound/outbound). ADR-035 adds the operator role; it does **not** change the VIEWER synthesis mechanism. Do not conflate the two — the spec must keep them distinct.
- **`entitled_domains` is not `account_type`** — ADR-035/032 removed `account_type`; `entitled_domains` is a different claim and is **retained**. Do not state or imply that the entitlement-trust dual-accept was removed.
- **Non-operator entitled token shape** — a cross-domain token entitled to `wms` but holding no WMS role still passes READ via the VIEWER synthesis; that path is unchanged and remains the net-zero defense-in-depth case the spec must preserve.
- **WRITE invariant** — entitlement-trust never grants WRITE; an operator's WRITE authority comes from `WMS_OPERATOR` (now derived at assume-tenant). This invariant is unchanged and the spec's "never mutation authority" wording stands.

## Failure Scenarios

- **F1 — stale spec misleads a future implementer** — leaving the operator-role source undocumented could cause a future change to re-introduce or depend on the removed `account_type` claim, or to assume operators get WMS authority only via the entitlement-trust VIEWER path (READ-only) and miss the `WMS_OPERATOR` WRITE path. Guarded by AC-2/AC-3.
- **F2 — over-editing introduces a real (non-net-zero) claim** — incorrectly stating that the entitlement-trust branch was removed, that the VIEWER synthesis now grants WRITE, or that `entitled_domains` was dropped, would mis-describe live behavior. Guarded by AC-3/AC-4 and the Edge Cases (code is byte-unchanged).
- **F3 — drift re-accumulates elsewhere** — a missed wms spec carrying an `account_type`-era operator framing. Guarded by AC-1's repo-wide `grep` over `projects/wms-platform/specs`.
