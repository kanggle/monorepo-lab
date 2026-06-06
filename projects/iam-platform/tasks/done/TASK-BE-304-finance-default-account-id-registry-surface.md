# Task ID

TASK-BE-304

# Title

GAP registry-surface `operatorContext.defaultAccountId` emission — Phase 1 of platform-console `Operator Overview` finance card `MISSING_PREREQUISITE` resolution (option (a))

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: nothing (self-contained GAP-side capability extension; admin-service `admin_operators` row is the authoritative storage; no cross-service call).
- **origin**: pre-recorded honest gap in `TASK-PC-FE-011 § Honest gaps (a)` ("`operatorDefaultAccountId` GAP registry-surface enhancement — option (a) in § 2.4.9.1 impl guidance; deferred to a separate spec-first GAP change. MVP uses option (b): finance card renders `forbidden / MISSING_PREREQUISITE` when no default account is resolvable.") + `console-integration-contract.md § 2.4.9.1 Implementation guidance` ("either (a) the GAP registry surface (§ 2.2) returns a per-operator `finance.defaultAccountId` claim/attribute, or (b) the console-bff composition use-case skips the finance leg and renders `forbidden / MISSING_PREREQUISITE` when no default is available.") + `OperatorOverviewCompositionUseCase.java:328-330` Javadoc ("finance MVP option (b) per § 2.4.9.1 Implementation guidance: when no `operatorDefaultAccountId` is resolvable from request context, surface `forbidden / MISSING_PREREQUISITE` **without** firing the outbound HTTP call.").
- **prerequisite for**: Phase 2 platform-console-side consumer adoption task (a separate `TASK-PC-*` to be authored after this one's merge); that task wires console-web registry consumer + console-bff `FinanceBalanceReadAdapter` activation. **This Phase 1 task is producer-only**; the consumer side stays MVP option (b) until Phase 2 lands. Sequential — atomic-cross-project is **rejected** (see § Decision authority).
- **spec-first**: spec PR (this file + 2 spec edits + Flyway migration spec note in `data-model.md`) → impl PR (V0028 migration + `ConsoleProduct` shape + `ConsoleRegistryUseCase` emit + IT) → close chore PR (this file `ready → done` + INDEX move).
- **no ADR** (HARDSTOP-09 not triggered): this is an additive optional field on an existing read-only registry surface; no new architecture decision (the architectural decision — "GAP registry is the per-operator profile attribute source" — is pre-recorded in `console-integration-contract.md § 2.4.9.1 Implementation guidance option (a)`; this task is the implementation of that pre-authorized option).

---

# Goal

The MVP `Operator Overview` composition route (TASK-PC-FE-011, § 2.4.9.1) intentionally renders the finance card as `forbidden / MISSING_PREREQUISITE` for every operator — finance v1 has no list/search GET (§ 2.4.7), so the BFF cannot synthesize an account id, and `OperatorOverviewCompositionUseCase` therefore short-circuits the finance leg with no outbound HTTP call (`OperatorOverviewCompositionUseCase.java:328-330` Javadoc). The recorded resolution path is **option (a)** in § 2.4.9.1 Implementation guidance: *the GAP registry surface returns a per-operator `finance.defaultAccountId` claim/attribute*.

Realize option (a) on the **GAP producer side**: extend `console-registry-api.md` `GET /api/admin/console/registry` response shape with an optional `operatorContext` nested object on every product item (extensible carrier for per-operator per-product profile attributes), emit `operatorContext.defaultAccountId` on the **finance** product item when the operator's `admin_operators` row has a `finance_default_account_id` set, and add the `admin_operators.finance_default_account_id VARCHAR(36) NULL` column via Flyway V0028 (forward-only, NULL by default — no operator change required to deploy).

The console consumer adoption (Phase 2) is **out of scope** here; this task lands the producer surface and the storage column, leaving console-bff finance leg behavior untouched (still MVP option (b)) until Phase 2 wires it.

# Decision authority (why option (a) on the finance product item, why `operatorContext` nested, why sequential not atomic)

- **Pre-authorized resolution path**: `console-integration-contract.md § 2.4.9.1 Implementation guidance` records both option (a) and option (b) as acceptable; option (a) is the explicit "follow-up spec-first change in GAP `admin-api.md` registry surface". This task implements option (a) verbatim — no architectural decision is open. **No ADR** (HARDSTOP-09 not triggered).
- **Why `operatorContext` nested, not a top-level `defaultAccountId`**: a top-level `defaultAccountId` field on the finance item would be polymorphic — every future per-operator per-product attribute (e.g. wms's `defaultWarehouseId`, scm's `defaultNodeId`, erp's `defaultDepartmentId`) would need its own top-level field, exploding the per-product item shape. `operatorContext: { defaultAccountId?: string }` is an **extensible carrier**: future per-operator per-product attributes nest under the same object on the relevant product item, with no shape change to other items. This decision is recorded inline in `console-registry-api.md § Item shape` as part of this spec PR.
- **Why GAP-side storage (not finance-side)**: the resolution authority for "which finance account the operator defaults to in their console overview" is operator profile data, not finance domain data. GAP `admin_operators` is the existing operator profile authority (`AdminOperator.tenantId` is already there per ADR-002); a `finance_default_account_id VARCHAR(36) NULL` column extends the same profile row with a single nullable scalar — minimal blast radius. Finance-side storage would require a new cross-service call from GAP → finance on every registry read, which contradicts the established read-path policy ("no audit row" / "consistent with `GetTenantUseCase` / `ListTenantsUseCase` read-path") and adds a circuit-breaker concern to a previously synchronous lookup.
- **Why no validation against finance-platform**: GAP carries the value as opaque (`VARCHAR(36)`, validated only as a non-null finite string when set); GAP does **not** verify the id exists in finance. A stale account id is surfaced honestly by finance returning `404 ACCOUNT_NOT_FOUND` on the eventual Phase 2 BFF call — the console then renders the finance card as `degraded` with the producer error, which is the correct UX (the BFF must not lie about a stale operator profile mapping). This honest decoupling preserves the GAP↔finance non-coupling invariant.
- **Why sequential (Phase 1 producer / Phase 2 consumer), not one atomic cross-project PR**: the platform-console-side adoption (console-web registry consumer shape extension + console-bff `FinanceBalanceReadAdapter` activation + header propagation + IT) is a substantial scope that **does not** become incrementally simpler by being co-located with the GAP-side capability emission. Both halves have independent tests and independent CI; the producer surface being absent today is exactly why option (b) is the MVP — the producer change is independently deployable with zero consumer behavior change (the new optional `operatorContext` object is omitted in current responses, but its presence on every future response is the new contract). The sequential pattern is the established precedent (BE-296 → FE-002a; FIN-BE-005 → FE-009; ERP-BE-002 → FE-010).
- **Why `available: false` for finance/erp in ProductCatalog stays as-is (out of scope here)**: that is a separate reality-alignment finding (finance v1 + erp v1 are both live as of 2026-05-20 — `ADR-MONO-013 § D6 Phase 5/6 COMPLETE`, 5/5 backend domains live), but flipping `available` is the BE-302 reality-alignment pattern, not this task's scope. The finance card MISSING_PREREQUISITE resolution does **not** require `available: true` to land — the `operatorContext.defaultAccountId` field is emitted on every operator's finance product item regardless of the catalog `available` flag (which controls only the console catalog tile rendering, not the BFF fan-out gate). A separate task may flip the flag.

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/global-account-platform/specs/contracts/http/console-registry-api.md`:
  - Add `operatorContext` to the **Response 200 OK** example (on the `finance` product item only — when set; the other items omit it when no value).
  - Add a new **Item shape extension** subsection after `### Item shape` documenting `operatorContext: { defaultAccountId?: string } | undefined` as the extensible per-operator per-product carrier (decision rationale recorded inline; per-product `operatorContext.defaultAccountId` emission rule per product is `finance` = `admin_operators.finance_default_account_id` when set; other products = no emission in v1).
  - Add an entry to **Multi-tenant isolation** confirming `operatorContext` carries no cross-tenant data (it's per-operator-profile, scoped to the calling operator's row).
- `projects/global-account-platform/specs/services/admin-service/data-model.md`:
  - Add `finance_default_account_id VARCHAR(36) NULL` row to the `admin_operators` table column listing (between `tenant_id` and the trailing audit columns; precise placement TBD by impl to preserve other rows verbatim).
  - Add a `### TASK-BE-304 (Flyway V0028__add_finance_default_account_id_to_admin_operators.sql)` block in § Migration Strategy: forward-only, NULL default, no index (point lookup is by operator row PK).
  - Add a one-line classification note: **internal** (it's a foreign-system identifier the operator chose; not PII, not credential; logged as opaque token only when emitted on the registry response — never logged as user-typed text).

**Code (impl PR)**:

- `projects/global-account-platform/apps/admin-service/src/main/resources/db/migration/V0028__add_finance_default_account_id_to_admin_operators.sql` — single `ALTER TABLE admin_operators ADD COLUMN finance_default_account_id VARCHAR(36) NULL`. No index, no backfill.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorEntity.java` — add `@Column(name = "finance_default_account_id") private String financeDefaultAccountId;` field + getter (no setter needed for read path; setter added only if Phase 2 ops mutation requires — out of scope here).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/console/ConsoleProduct.java` — extend record with a new `operatorContext` field; introduce a sibling `OperatorContext` record type with a single optional `defaultAccountId` field. The 5-arg constructor stays compatible via either a compact constructor delegating to a 6-arg form, or all call sites pass `null` for products with no operator context (chosen at impl).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/console/ConsoleRegistryUseCase.java` — when building the `finance` product item, populate `operatorContext` with `new OperatorContext(adminOperator.financeDefaultAccountId())` when the row's value is non-null; pass `null` otherwise. Other 4 products always pass `null` for `operatorContext` in v1.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/console/ConsoleRegistryController.java` — JSON serialization must omit `operatorContext` when `null` (Jackson `@JsonInclude(JsonInclude.Include.NON_NULL)` on the field or response DTO).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/domain/rbac/AdminOperator.java` — add `financeDefaultAccountId` (nullable) to the record; thread it through the existing `resolveOperator` mapper in `ConsoleRegistryUseCase`.
- Tests:
  - **Unit (`ConsoleRegistryUseCaseTest`)**: 3 new cases — (a) operator with `finance_default_account_id = NULL` → finance product `operatorContext` is `null`; (b) operator with `finance_default_account_id = "acc-uuid-7"` → finance product `operatorContext.defaultAccountId == "acc-uuid-7"`; (c) all other products always have `operatorContext == null`.
  - **Slice/Controller**: existing serialization test (or a new one) asserts `operatorContext` is **omitted** from the JSON when null (no `"operatorContext":null` literal in the rendered envelope).
  - **IT (`ConsoleRegistryIntegrationTest`)**: 2 new cases — (a) operator row with NULL: response body for finance item does **not** contain the substring `operatorContext`; (b) operator row with a UUID value: response body for finance item contains `"operatorContext":{"defaultAccountId":"<the uuid>"}` and the **other 4** items do not contain the substring `operatorContext` (regression guard: the field never leaks onto a non-finance product item in v1).

## Out of Scope

- **Phase 2 platform-console-side adoption** (console-web registry consumer parser + session/cookie store + console-bff finance leg activation + `OperatorOverviewCompositionUseCase` finance pre-check + new header `X-Finance-Default-Account-Id` (or equivalent) + IT) — separate `TASK-PC-*` task, authored after this Phase 1 task's merge.
- **ProductCatalog `available: false → true` for finance/erp** — a separate reality-alignment task (BE-302 pattern); this Phase 1 task is producer surface extension only, the catalog `available` flag does not affect the new `operatorContext` emission path.
- **A setter / admin UI / admin-api mutation endpoint to write `finance_default_account_id`** — out of scope. V0028 lands the column NULL by default; an operator profile mutation surface (POST/PATCH on `admin_operators` row) is a future task (admin-api or operator-management feature). For testing, the column is set via a Flyway test seed or `@DataJpaTest` direct insert. **No operator's effective behavior changes when Phase 1 lands alone**.
- **Validation against finance-platform** — GAP carries the value as opaque (`VARCHAR(36)`, basic non-empty trim when set); stale account id surfaces as finance `404 ACCOUNT_NOT_FOUND` in the eventual Phase 2 BFF call. No cross-service verification call on the GAP side.
- **`operatorContext` on non-finance products in v1** — the schema reserves it for future per-operator per-product attributes (wms `defaultWarehouseId`, scm `defaultNodeId`, etc.), but **only the finance item populates it in v1**. Other 4 products always emit `operatorContext: null` (or omit the field via `@JsonInclude.NON_NULL`).
- **An audit row on registry reads** — the registry endpoint is read-only and audit-less today (`ConsoleRegistryUseCase` comment: *"no audit row (consistent with `GetTenantUseCase` / `ListTenantsUseCase` read-path policy)"*); reading `finance_default_account_id` as part of the same row is also read-only with no audit row added.
- **ADR-MONO-013 / ADR-MONO-017 / ADR-MONO-014 / ADR-MONO-015 amendment** — none required; this task does not change any of those ADRs' decisions. ADR-MONO-017 D4 HARD INVARIANT (per-domain credential rule) is **not** affected — `operatorContext.defaultAccountId` is operator profile data, not credential data. ADR-MONO-013 D5 / § 3.3 zero-retrofit is **not** affected — no producer (finance/wms/scm/erp/gap) outside admin-service changes. ADR-MONO-014 (RFC 8693) is **not** affected — the exchanged operator token's claims are unchanged; this carries the new attribute on the registry response, not on the JWT.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: a single spec PR lands `console-registry-api.md` + `admin-service/data-model.md` edits with no production code; impl PR is held separately. (Sequential per the established BE-296 / FIN-BE-005 / ERP-BE-002 spec-first pattern.)
- **AC-2 (response shape — null case)**: for an operator whose `admin_operators.finance_default_account_id IS NULL`, the rendered registry JSON envelope contains **no occurrence** of the substring `operatorContext` (omission by `@JsonInclude.NON_NULL`). Asserted in `ConsoleRegistryIntegrationTest` via `assertThat(body).doesNotContain("operatorContext")`.
- **AC-3 (response shape — set case)**: for an operator whose `admin_operators.finance_default_account_id = "<uuid>"`, the rendered registry JSON envelope contains **exactly one occurrence** of the substring `operatorContext`, on the **finance** product item, with value `{"defaultAccountId":"<uuid>"}`. Asserted via two assertions in `ConsoleRegistryIntegrationTest`: (a) `assertThat(body).contains("\"productKey\":\"finance\"")` and `body.contains("\"operatorContext\":{\"defaultAccountId\":\"<uuid>\"}")`, (b) the substring `operatorContext` count == 1 (regression guard for leakage onto other items).
- **AC-4 (storage)**: V0028 migration runs forward on a fresh DB and on an existing DB with seeded operators; existing operators retain `finance_default_account_id = NULL` (no backfill, no behavior change for unset operators).
- **AC-5 (no cross-tenant leakage)**: an operator with `tenant_id = "wms"` and a `finance_default_account_id` value set in their own row sees that value on their finance item, but **never** sees another operator's `finance_default_account_id` (the column is read from the calling operator's own row, not from a multi-tenant query — already enforced by `resolveOperator(operator).operatorId()` lookup, no scope widening).
- **AC-6 (D4 HARD INVARIANT preserved)**: ADR-MONO-017 D4 (per-domain credential rule, sealed switch in `CredentialSelectionAdapter`) — **0 byte diff** across `projects/platform-console/apps/console-bff/src/**` and the relevant ADR / contract files. This task does not touch console-bff. (Verified by `git diff origin/main -- projects/platform-console/apps/console-bff/` returning empty in the impl PR.)
- **AC-7 (other producers unaffected — § 3.3 zero retrofit)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/` in this PR (verified by `git diff --stat origin/main -- projects/{wms,scm,finance,erp,fan,ecommerce}-platform/`).
- **AC-8 (CI green)**: `Integration (global-account-platform admin-service)` + `Build & Test` PASS; self-CI 20/20 GREEN at merge time. **BE-303 3-dim verified at close chore** per [`CLAUDE.md § Task Rules`](../../../../CLAUDE.md): (a) `gh pr view <impl-PR> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED` AND `statusCheckRollup` shows 0 failing required checks; (b) `git log origin/main` tip matches the squash commit; (c) `gh pr checks <impl-PR>` pre-merge snapshot had 0 failing required checks.

# Related Specs

- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Response + § Item shape (extended in this task).
- `projects/global-account-platform/specs/services/admin-service/data-model.md` § admin_operators + § Migration Strategy (extended in this task).
- `projects/global-account-platform/specs/services/admin-service/architecture.md` — Identity / Service Type Composition unchanged; this task adds no new layer.
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.9.1 Implementation guidance` — option (a) being realized; **byte-unchanged** in this task (the spec already authorizes option (a); the actual surface activation note will be added in the Phase 2 platform-console-side task).

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` — the producer contract being extended.
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.2 Item shape` — the consumer-side item shape table. **NOT modified in this task** — the consumer-side note about `operatorContext.defaultAccountId` is added in Phase 2 (when the console-web parser is wired). This Phase 1 keeps the producer contract authoritative and lets the consumer contract trail by one PR (deliberate decoupling — see § Decision authority "Why sequential").

# Edge Cases

- **`finance_default_account_id` set to an empty string** → treated as NULL (controller / use-case applies `StringUtils.hasText` before emission; AC-2 path). No empty-string `operatorContext.defaultAccountId` ever rendered.
- **`finance_default_account_id` whitespace-only** → same as empty (`hasText` false). No emission.
- **`finance_default_account_id` set to a malformed UUID string** → GAP carries it opaquely (`VARCHAR(36)`); no validation. The eventual Phase 2 BFF call propagates it; finance returns `404 ACCOUNT_NOT_FOUND` honestly. No GAP-side gating.
- **Operator row deleted between session start and registry read** → existing `OperatorUnauthorizedException` → `401 TOKEN_INVALID` (unchanged behavior; not regressed by this task).
- **Multi-tenant operator with `tenant_id = '*'` and `finance_default_account_id` set** → sees their value on finance item (AC-5; column is per-operator-row, not per-tenant; platform-scope operators are still single operator profiles).
- **Existing IT seeds**: a default operator seed with `finance_default_account_id IS NULL` already exists (V0026 platform admin seed). AC-2 covers this case. The set-case IT injects a value via Testcontainers `@Sql` or direct JDBC insert (chosen at impl).
- **JSON serialization order**: Jackson default field order is reflection-based, not alphabetical. `operatorContext` may appear in any position within the finance item; AC-3 uses substring containment, not positional assertion.

# Failure Scenarios

- **A V0028 migration that adds an index** → AC-4 covers it indirectly (forward-runs on existing DB); BE-249-pattern review: lookups are by operator row PK, an index on `finance_default_account_id` is unnecessary and adds write cost. **Reject in review** if added.
- **A second column added beyond `finance_default_account_id`** → scope violation; this task adds exactly one column. **Reject in review** (V0028 must be single-column, single-table).
- **The `operatorContext` field rendered as `"operatorContext":null` instead of omitted** → AC-2 fails (the substring `operatorContext` is present even in the null case). Fix: `@JsonInclude(JsonInclude.Include.NON_NULL)` on the field or on the response DTO. **Re-fail** if the team patches the test to permit the literal — strengthen-only discipline.
- **Cross-tenant leak via a JPA query change widening scope** → AC-5 + the existing `resolveOperator(operator.operatorId())` lookup; if a reviewer suggests reading by tenant, **reject** — the registry is per-operator, not per-tenant.
- **A non-finance product item gets `operatorContext` populated** → AC-3 substring-count-1 regression guard catches it. Fix: only the `finance` branch of `ConsoleRegistryUseCase` populates `operatorContext` in v1.
- **The console-bff finance leg activated in the same PR** — explicitly out of scope (Phase 2). If a reviewer requests it, **reject** — the sequential split is intentional (§ Decision authority). The Phase 1 PR must touch zero files under `projects/platform-console/apps/console-bff/`.
- **`finance_default_account_id` treated as PII / restricted / confidential** → it is the operator's chosen foreign system identifier (a finance account UUID), not personal data, not credential. Classification: **internal** (data-model.md § Data Classification). If treated as confidential, masking would corrupt the value before it reaches the BFF — incorrect.

# Verification

1. Spec PR diff: exactly 2 files (`console-registry-api.md` + `data-model.md`) + 1 task file (this one) — `git diff --stat origin/main` shows ≤ 3 files in the spec PR.
2. Impl PR diff: code + tests under `projects/global-account-platform/apps/admin-service/` only; AC-6 + AC-7 grep zero diff outside.
3. `./gradlew :admin-service:test` (unit) green; `./gradlew :admin-service:integrationTest` (IT) green including the 2 new ConsoleRegistryIntegrationTest cases.
4. Self-CI 20/20 GREEN at impl-PR merge time (`gh pr checks <n>` snapshot pre-merge); BE-303 3-dim verified at close chore start.
5. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-stratum spec + Flyway migration + JPA entity + admin-service composition layer + serialization invariants + multi-tenant isolation + IT — multiple integration seams; deserves Opus judgement) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-2/AC-3/AC-6/AC-7 grep + JSON shape verify + BE-303 3-dim).
