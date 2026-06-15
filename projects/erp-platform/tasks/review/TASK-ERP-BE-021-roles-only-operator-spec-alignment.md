# TASK-ERP-BE-021 — Align erp service specs to the roles-only operator model (drop the `account_type=OPERATOR` framing)

**Status:** review

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (security/authorization-adjacent doc alignment — net-zero, but the operator-admission model must be stated exactly)

---

## Goal

Bring the erp-platform service specs into conformance with the **roles-only identity model** that landed on `main` via ADR-MONO-032 (unified identity → `roles` as the sole authorization axis) and ADR-MONO-035 (operator authentication unification + operator domain-role issuance). Those ADRs **fully removed** the `account_type` claim/column/gateway-leg (ADR-032 D5 step 4 + step 5, COMPLETE 2026-06-15) and made an operator's domain authorization ride the JWT `roles` claim, with the operator's domain role **derived at assume-tenant** from the selected tenant's entitled domains (`erp → ERP_OPERATOR`, `OperatorRoleDerivation`, ADR-035 O1 / step 4a, gated fail-closed by the assignment check).

The erp **code is already roles-based** — every `isOperator()` predicate keys on `ERP_OPERATOR` / `ERP_ADMIN` / `SUPER_ADMIN` (masterdata `ActorContext`, approval `ActorContext`, notification/read-model `ReadAuthorizationGate`), and the erp app code contains **zero** `account_type` / `accountType` references. The drift is **purely in the specs**, which still describe the operator token via the obsolete `account_type=OPERATOR` (ADR-MONO-020 D4) claim shape and, in one place, give a rationale that ADR-035 has since **inverted**.

This is a **doc-only, net-zero spec-alignment** task: no code, no contract semantics, no schema, no ADR decision change. It executes the spec-side of the already-ACCEPTED ADR-032/035 (the same "apply the merged identity change to the project's specs" shape as the ecommerce/iam drift sweeps), so it introduces no new architecture decision (HARDSTOP-09 clean — the decisions live in ADR-032/033/034/035).

## Scope

**In scope** — erp spec drift sites:

1. `specs/services/masterdata-service/architecture.md` — **(a)** the v1-bridge paragraph (~L472-480, TASK-BE-337) referencing the console assume-tenant operator token as `account_type=OPERATOR` (ADR-MONO-020 D4): reframe the token's **authorization** shape to `roles ∋ ERP_OPERATOR` derived at assume-tenant (ADR-035 O1/4a), noting the `account_type=OPERATOR` claim was removed at ADR-032 D5 step 4. Preserve the `org_scope` enrichment narrative (that is a separate claim, ADR-MONO-020 D3/D4, still valid). **(b)** the entitlement-trust READ dual-accept rationale (~L512-517): the parenthetical "the assume-tenant token … carries no `erp.read`/operator role, yet the tenant gate already admits them" is now **factually inverted** — post-ADR-035 4a that token **does** carry `ERP_OPERATOR`, so `isOperator()` already admits it. Update the rationale so it reflects the new reality while preserving the MONO-161 dual-accept mechanism description (the code is unchanged; the dual-accept is retained as defense-in-depth and for any entitled-but-non-operator token shape — net-zero).
2. `specs/services/approval-service/architecture.md` — Security section (~L780-785): the "console assume-tenant operator token … v1.0 callers" bullet carries no literal `account_type`, but add a one-clause cross-ref stating the operator's domain authorization rides `roles ∋ ERP_OPERATOR` (derived at assume-tenant, ADR-035), so the operator-admission story is explicit and consistent with the code's `ActorContext.isOperator()`.
3. `specs/services/notification-service/architecture.md` — Security section (~L655-665): same one-clause cross-ref as approval (the `isOperator()` READ gate is already roles-based; document the role source).
4. `specs/services/read-model-service/architecture.md` — the org_scope read-filter paragraph (~L384-394) correctly references ADR-MONO-020 D3 for `org_scope` (a separate concern, unchanged); add a brief cross-ref that the operator role itself (`ERP_OPERATOR`) is the ADR-035-derived role, so the BE-008 `isOperator()` source is documented.
5. `specs/integration/iam-integration.md` — already free of `account_type`; add a short note (in the Token-validation or platform-console-operator section) documenting that an operator's domain `roles` (`ERP_OPERATOR`) are **derived by IAM at assume-tenant** from the selected tenant's entitled domains (ADR-035 O1), since no erp spec currently records where `ERP_OPERATOR` originates. Keep cross-reference-only (no producer/auth-model redefinition).

**Out of scope (unchanged):**

- **All erp app code** — already roles-based; no `account_type` reference exists. This task changes **zero** `.java` / `.yml` / Flyway / `docker-compose` files.
- The IAM issuance side (`OperatorRoleDerivation`, `TenantClaimTokenCustomizer`, `AssumeTenantAuthenticationProvider`) — owned by iam-platform; erp specs only cross-reference it.
- `org_scope` data-scope semantics (ADR-MONO-020 D3 / ERP-BE-008) — a separate claim, not affected by the `account_type` drop.
- Contract files (`masterdata-api.md`, `approval-api.md`, etc.), ADR docs, `PROJECT.md` frontmatter — no change (frontmatter `domain=erp` / `traits=[internal-system,transactional,audit-heavy]` / `service_types=[rest-api,event-consumer]` byte-unchanged).

## Acceptance Criteria

- **AC-1** — No erp spec frames the operator token's **current authorization** via `account_type=OPERATOR`. The only residual `account_type` mentions are **explanatory** — each names the claim solely to record that it was *removed* (e.g. "the legacy `account_type=OPERATOR` claim … was removed at ADR-MONO-032 D5 step 4"), the irreducible way to document a migration. No spec presents `account_type` as a live/required claim, and `X-Account-Type` appears nowhere in erp specs.
- **AC-2** — The masterdata entitlement-trust rationale no longer asserts that the assume-tenant operator token "carries no operator role"; it reflects that post-ADR-035 the token carries `ERP_OPERATOR` and the dual-accept is retained net-zero (defense-in-depth).
- **AC-3** — The operator domain-role source (`erp → ERP_OPERATOR`, derived at assume-tenant from the selected tenant's entitled domains, ADR-035 O1/4a, fail-closed by the assignment check) is documented in at least one erp spec, and each of the 4 service specs' operator-admission narrative is consistent with the code's `isOperator()` (`ERP_OPERATOR`/`ERP_ADMIN`/`SUPER_ADMIN`).
- **AC-4 (net-zero / doc-only)** — `git diff origin/main -- 'projects/erp-platform/apps/**'` is empty (no code change). No contract semantics, no Flyway, no ADR decision changed. All edits are under `projects/erp-platform/specs/**` + the task lifecycle/INDEX.
- **AC-5** — Cross-references point to the correct authorities: ADR-MONO-032 (D5 step 4 — `account_type` removal), ADR-MONO-035 (O1/4a — operator domain-role derivation). ADR-MONO-020 references for `org_scope` (D3/D4) are preserved where still accurate.

## Related Specs

- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (§ 3-stage authorization, entitlement-trust READ dual-accept)
- `projects/erp-platform/specs/services/approval-service/architecture.md` (§ Security)
- `projects/erp-platform/specs/services/notification-service/architecture.md` (§ Security)
- `projects/erp-platform/specs/services/read-model-service/architecture.md` (§ org_scope read filter)
- `projects/erp-platform/specs/integration/iam-integration.md` (§ Token 검증 규칙 / platform-console Operator Read Consumer)

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the contract whose `account_type` deprecation→removal ADR-035 4b finalized (the authority for "the claim no longer exists").
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § D5 step 4 (the `account_type` removal).
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O1 / § O5 4a (operator domain-role derivation at assume-tenant; the `erp → ERP_OPERATOR` mapping).
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § D3/D4 (`org_scope` — preserved cross-refs only).

## Edge Cases

- **Entitlement-trust branch redundancy** — post-ADR-035 4a, an assume-tenant token for an erp-entitled tenant carries `entitled_domains ∋ erp` **and** `roles ∋ ERP_OPERATOR`, so `isOperator()` already admits READ; the `isEntitledTo("erp")` branch becomes a redundant-but-harmless defense-in-depth OR. The spec must say this **without** implying the code changed (the dual-accept code stays; this task does not remove it).
- **`org_scope` vs `account_type` separation** — `org_scope` (data-scope subtree roots, ADR-020 D3) is an orthogonal claim and is **not** removed by ADR-032/035. Do not conflate the two: only the `account_type` authorization framing is stale; the `org_scope` narrative stays.
- **Historical-narrative preservation** — the masterdata v1-bridge paragraph (TASK-BE-337) is already noted as superseded by the v2 membership-derived path (TASK-BE-338/ERP-BE-008). Keep the historical structure; only correct the token's claim shape inside it.
- **WRITE path** — masterdata WRITE = `hasScope(erp.write) || isOperator()`; with `ERP_OPERATOR` now present, an assigned operator's assume-tenant token authorizes WRITE via `isOperator()` (entitlement never widened WRITE — that invariant is unchanged). Ensure the spec's WRITE narrative remains accurate.

## Failure Scenarios

- **F1 — stale spec misleads a future implementer** — leaving `account_type=OPERATOR` in the spec would cause a future change to re-introduce or depend on a claim that no longer exists, re-opening the mis-authorization gap ADR-035 closed. Guarded by AC-1.
- **F2 — over-editing introduces a real (non-net-zero) claim** — incorrectly stating that the entitlement-trust branch was removed, or that WRITE now widens on entitlement, would mis-describe live behavior. Guarded by AC-2/AC-4 and the Edge Cases (code is byte-unchanged; the dual-accept and WRITE invariants stand).
- **F3 — drift re-accumulates elsewhere** — a missed erp spec still carrying the old framing. Guarded by AC-1's repo-wide `grep` over `projects/erp-platform/specs`.
