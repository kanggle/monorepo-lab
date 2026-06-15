# TASK-PC-BE-009 — Align console-integration-contract.md ecommerce-leg admission to the roles-only operator model

**Status:** review

**Type:** TASK-PC-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (security/authorization-adjacent contract-doc alignment — net-zero, but the cross-project admission model must be stated exactly)

---

## Goal

Bring `console-integration-contract.md` into conformance with the **roles-only identity model** (ADR-MONO-032 + ADR-MONO-035) on the **ecommerce overview leg** (§ 2.4.9.1 card 6, TASK-MONO-243) and the **ecommerce seller operator surface** (§ ecommerce sellers, TASK-PC-FE-090). Those sections describe how console-bff reaches the ecommerce `product-service` **through the ecommerce gateway**, and they still frame the gateway's operator admission via the obsolete `account_type=OPERATOR` claim (`AccountTypeEnforcementFilter`), asserting that the operator's IAM OIDC token "carries no ecommerce-local `ADMIN` role claim."

That framing is now **factually inverted**. The ecommerce gateway admission was made roles-only by **TASK-BE-380** (#1596) + ADR-MONO-035 4b (MONO-262/263): `AccountTypeEnforcementFilter` enforces `roles ∋ ADMIN` for `/api/admin/**` (the `account_type=OPERATOR` leg removed), and the platform-console operator's assume-tenant token now **carries** the `ADMIN` domain role, derived by IAM from the selected ecommerce-entitled tenant (ADR-MONO-035 4a). The **authoritative producer doc** — ecommerce `product-api.md` — was already reconciled to this model (PR #1603, `08fb6aa79`): its `GET /api/admin/products` Authorization note and the `AdminProductController` javadoc both now state the gateway gates the read **and** the write endpoints **uniformly** on `roles ∋ ADMIN`, with the service applying no additional ecommerce-local RBAC. `console-integration-contract.md` is the **last consumer-side doc** still carrying the pre-ADR-035 wording.

This is a **doc-only, net-zero, single-file spec-alignment** task: no code, no contract semantics, no schema, no ADR decision change. It executes the consumer-side of the already-ACCEPTED ADR-032/035 + the already-merged BE-380/#1603, converging the console contract with its authoritative producer (no new architecture decision — HARDSTOP-09 clean).

## Scope

**In scope** — `projects/platform-console/specs/contracts/console-integration-contract.md` only:

1. **§ ecommerce-leg topology note** (card 6, ~L2032–2052): reframe the gateway-admission narrative — `AccountTypeEnforcementFilter` enforces `roles ∋ ADMIN` (not `account_type=OPERATOR`) for `/api/admin/**`; the `GET /api/admin/products` read is gated **uniformly with the writes** on `roles ∋ ADMIN`; the operator's IAM OIDC token **carries** the `ADMIN` domain role derived at assume-tenant (ADR-MONO-035 4a, no local grant — the role rides the token); the header-trust `product-service` applies no additional ecommerce-local RBAC; defer per-endpoint detail to the authoritative producer `product-api.md`. Note the legacy `account_type=OPERATOR` gateway leg was removed at ADR-MONO-035 4b.
2. **§ ecommerce seller operator surface** (~L2756): the `/api/admin/sellers` subtree note "requiring `account_type=OPERATOR`" → "gated on `roles ∋ ADMIN` (the operator's ADR-MONO-035 4a derived domain role; the legacy `account_type=OPERATOR` leg removed at 4b)".

**Out of scope (unchanged):**

- **All console-bff / console-web app code** — this task changes **zero** `.java` / `.ts` / config files. The console-bff already presents the operator's federation token unchanged; the gateway-side admission is ecommerce-owned.
- **The authoritative producer** (ecommerce `product-api.md`, `AdminProductController`) — already roles-only on `origin/main` (#1603); this task only converges the consumer description to it (do NOT redefine the producer surface here, per the contract's own "Authoritative producer surface" rule).
- **Ecommerce `iam-integration.md` / `multi-tenancy-and-marketplace.md`** — the residual identity-plane `account_type` framing there is being handled by the in-flight ecommerce / born-unified-identity sessions (iam-integration.md is concurrently edited by `be-381`/`be-382-credentials`/`mono-266`); excluded to avoid collision.
- Other console-integration-contract.md sections (the erp/finance/scm/wms legs), other contract files, `PROJECT.md` frontmatter — no change.

## Acceptance Criteria

- **AC-1** — `console-integration-contract.md` no longer frames the ecommerce operator admission via a **live** `account_type=OPERATOR` requirement. The only residual `account_type` mentions are **explanatory** (each names the claim solely to record that the gateway leg was *removed* at ADR-MONO-035 4b). `X-Account-Type` appears nowhere.
- **AC-2** — The ecommerce-leg + seller notes state the current admission model accurately: gateway `AccountTypeEnforcementFilter` enforces `roles ∋ ADMIN` for `/api/admin/**`; the operator's assume-tenant token carries the IAM-derived `ADMIN` role (ADR-MONO-035 4a); read and write gated uniformly; no additional ecommerce-local RBAC. Consistent with the authoritative producer `product-api.md` (#1603) and `AdminProductController` javadoc.
- **AC-3 (net-zero / doc-only)** — `git diff origin/main -- 'projects/platform-console/apps/**'` is empty (no code change). No contract semantics (the console-bff→gateway call shape, base URLs, headers, endpoint list are unchanged), no Flyway, no ADR decision changed. All edits are under the single file + the task lifecycle/INDEX.
- **AC-4** — Cross-references point to the correct authorities: ADR-MONO-035 (4a operator domain-role derivation, 4b `account_type` gateway-leg removal), TASK-BE-380 (#1596 roles-only ecommerce gateway admission), ecommerce `product-api.md` (authoritative producer, #1603). No redefinition of the producer surface in the console contract.

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` (§ 2.4.9.1 ecommerce-leg topology, § ecommerce seller operator surface)
- `projects/ecommerce-microservices-platform/specs/contracts/http/product-api.md` (authoritative producer — already roles-only, #1603; the reference for the corrected wording)

## Related Contracts

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O5 4a (operator domain-role derivation at assume-tenant; `ecommerce → ADMIN`) / 4b (`account_type` gateway-leg removal).
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § D3 (gateway role-based admission) / D5 (`account_type` removal).
- `platform/contracts/jwt-standard-claims.md` — the contract whose `account_type` removal ADR-035 4b finalized.

## Edge Cases

- **Producer vs consumer ownership** — the console contract explicitly marks the ecommerce `product-service` as the "Authoritative producer surface (do NOT redefine here)." The edit must **defer** the per-endpoint authorization detail to `product-api.md`, stating only the high-level admission fact (roles-only `roles ∋ ADMIN`) from the console's integration perspective — not re-specify the producer's RBAC.
- **read/write uniformity inversion** — the pre-ADR-035 wording singled out the `GET /api/admin/products` read as "not requiring ADMIN." Post-#1603 the gateway gates read **and** write uniformly on `roles ∋ ADMIN`; the read is no longer special. The edit must remove the "read does not require ADMIN" assertion (now false at the gateway) without implying the producer code changed (it did not in this task — the producer was already aligned by #1603).
- **erp/finance "mirror" comparison** — the original note compared the ecommerce leg to the erp/finance overview legs ("gated by federation entitlement-trust without a domain-local admin role"). Post-ADR-035 the ecommerce gateway gates on a derived `ADMIN` role (not a per-read entitlement check), so the comparison must be softened to the still-true shared property (single federation-issued token, no domain-local admin grant provisioned) rather than the now-divergent mechanism.
- **No app-behavior claim** — console-bff's call (base URL, IAM-OIDC credential, header set, endpoint list) is byte-unchanged; only the prose describing the gateway's admission is corrected.

## Failure Scenarios

- **F1 — stale consumer contradicts aligned producer** — leaving `account_type=OPERATOR` in the console contract while `product-api.md` is roles-only (#1603) is an active cross-doc contradiction: a future integrator reading the console contract would build against a removed claim. Guarded by AC-1/AC-2.
- **F2 — over-editing re-specifies the producer** — restating the producer's per-endpoint RBAC (beyond the high-level admission fact) violates the contract's "do NOT redefine here" rule and risks drifting from `product-api.md`. Guarded by AC-4 + the Edge Cases.
- **F3 — collision with in-flight ecommerce identity work** — editing ecommerce `iam-integration.md` (concurrently held by born-unified-identity branches) would collide. Guarded by the single-file scope (platform-console only).
