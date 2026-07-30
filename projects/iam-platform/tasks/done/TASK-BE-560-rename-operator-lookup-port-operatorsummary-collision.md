# Task ID

TASK-BE-560

# Title

Rename `OperatorLookupPort.OperatorSummary` to `OperatorLookupRef` — resolve same-name/different-concept collision with `OperatorQueryService.OperatorSummary` in admin-service's application layer

# Status

done

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`admin-service` declares **two structurally unrelated types with the identical
simple name `OperatorSummary`** inside the same architectural layer
(`application/`, per `specs/services/admin-service/architecture.md`
§ Internal Structure Rule):

1. `com.example.admin.application.port.OperatorLookupPort.OperatorSummary`
   — `record OperatorSummary(Long internalId, String operatorId, String tenantId)`.
   A 3-field **internal-PK lookup projection** returned by the outbound
   `OperatorLookupPort` (external UUID → `admin_operators.id` BIGINT + tenant
   scope). Introduced by TASK-BE-030-fix / TASK-BE-040-fix, extended with
   `tenantId` by TASK-BE-249. Consumed by audit FK resolution, refresh-token
   ownership checks, and tenant-scope gates.
2. `com.example.admin.application.OperatorQueryService.OperatorSummary`
   — `record OperatorSummary(String operatorId, String email, String displayName,
   String status, List<String> roles, boolean totpEnrolled, Instant lastLoginAt,
   Instant createdAt, String financeDefaultAccountId)`. The 9-field
   **API-facing listing DTO** behind `GET /api/admin/operators` and
   `GET /api/admin/operators/me` (mapped to `OperatorSummaryResponse`,
   mirrored by platform-console's `OperatorSummarySchema`).

Both are actively used and both are referenced by their *qualified nested* form
in call sites (`OperatorLookupPort.OperatorSummary` vs
`OperatorQueryService.OperatorSummary`), which is the only thing currently
preventing a wrong-type import: a single-token change of the qualifier — or a
future single-type `import ...OperatorSummary;` in a file that already deals
with operators — silently switches concepts. Two of the sibling test classes
already sit in the same package and construct *different* `OperatorSummary`
records (`OperatorQueryServiceTest` vs `CreateOperatorUseCaseTest`), so the
identifier `OperatorSummary` means two things within one package's test source
directory today.

After this task, the **lookup projection** is named `OperatorLookupRef` and the
**listing DTO keeps `OperatorSummary`** — the API-facing name is the prominent
one (contract-visible via `OperatorSummaryResponse` and the console's
`OperatorSummarySchema`), so renaming it would ripple into the presentation DTO
and the console mirror for no benefit.

This is a pure rename. No behavior change, no contract change.

## Name selection rationale (checked against the codebase)

- **`OperatorLookupRef` (chosen)** — 0 pre-existing occurrences anywhere in the
  repo (verified by grep). Echoes its declaring port (`OperatorLookupPort`) so
  the qualified and unqualified forms both read as "the lookup port's operator
  reference", and `Ref` states what the record actually is: an identity
  reference (internal PK + external UUID + tenant scope), not a data view.
- **`OperatorRef` (rejected)** — grep-hostile: `OperatorRef` is a substring of
  the service's existing `AdminOperatorRefreshToken*` family
  (`AdminOperatorRefreshTokenJpaEntity` / `...JpaRepository`, plus a Flyway
  migration comment), so every future search for the type name returns
  false positives.
- **`OperatorView` (rejected)** — would be a **new** same-package collision:
  `com.example.admin.application.port.AdminOperatorPort` already declares
  `record OperatorView(...)` in that exact package.
- **`OperatorLookupView` (considered, rejected)** — `*View` is this service's
  suffix for *rich read-model projections* (`AdminOperatorPort.OperatorView`,
  `OperatorOidcSubjectView`, `RoleView`, `TenantPartnershipPort.PartnershipView`,
  `OperatorTenantAssignmentPort.AssignmentView`) and matches
  `platform/naming-conventions.md` § Java Classes read/query row. Rejected
  because putting the identity-resolution record back into the read-model
  suffix family preserves the exact confusion class this task removes (the
  collision arose from naming a PK-resolution result like a read DTO), and the
  `View` framing invites field accretion onto a record whose javadoc
  explicitly forbids it ("Never includes credentials, status, or other
  persistence-only fields"). `platform/naming-conventions.md` does not define a
  suffix for outbound-port identity projections, so no convention row is
  violated by `Ref`.

---

# Scope

## In Scope

- Rename the nested record
  `com.example.admin.application.port.OperatorLookupPort.OperatorSummary`
  → `OperatorLookupRef` (declaration, both canonical and legacy 2-arg
  constructors, and the enclosing interface's javadoc/`{@link}` references and
  method signature `Optional<OperatorSummary> findByOperatorId(...)`).
- Update every call site of that type inside `admin-service` main sources:
  - `infrastructure/persistence/OperatorLookupPortImpl.java` (return type,
    mapping lambda, class javadoc `{@link}`)
  - `application/AdminActionAuditWriter.java` (`resolveOperatorOrFail`)
  - `application/AdminRefreshTokenService.java`
  - `application/CreateOperatorUseCase.java` (method reference
    `OperatorLookupPort.OperatorSummary::tenantId`)
  - `application/QueryTenantScopeGate.java`
- Update the compile-forced type references in `admin-service` test sources
  (identifier-only; no assertion, fixture value, or stub-behavior change):
  `AdminRefreshTokenServiceTest`, `AdminLogoutServiceTest`,
  `QueryTenantScopeGateTest`, `AdminActionAuditWriterTest`,
  `AdminActionDenyWriterTest`, `CreateOperatorUseCaseTest`.
- Rename local variables whose identifier echoes the old type name
  (`summary` / `opSummary` → `operatorRef`) at the touched call sites, so the
  "summary" vocabulary no longer points at the lookup path.
- `specs/services/admin-service/architecture.md`: only if it names the record
  literally (checked at task authoring time: it names `OperatorLookupPort` in
  the § Internal Structure Rule port list but **never** the nested record, so
  no spec edit is expected).

## Out of Scope

- `com.example.admin.application.OperatorQueryService.OperatorSummary` — the
  listing DTO **keeps its name**. Not touched.
- `presentation/OperatorAdminController.java` and its
  `import com.example.admin.application.OperatorQueryService.OperatorSummary;`
  — that is the *other* type; untouched.
- `presentation/dto/OperatorSummaryResponse.java`, `OperatorListResponse.java`,
  `GrantableRolesResponse.java` javadoc — HTTP DTO layer, unrelated name family,
  untouched.
- `OperatorQueryServiceTest`, `OperatorAdminControllerSliceTest` — construct the
  listing DTO; untouched.
- platform-console's `OperatorSummarySchema` / `OperatorSummary` TS types — mirror
  the listing DTO (which is unchanged); no cross-project change, no atomic
  cross-project PR needed.
- `AdminOperatorPort.OperatorView` and every other port projection — untouched.
- Any behavior change: the legacy 2-arg constructor defaulting `tenantId` to
  `"fan-platform"`, the defensive `null → "fan-platform"` fallback in
  `AdminActionAuditWriter`, fail-closed `AuditFailureException` semantics, and
  the `'*'` platform-scope sentinel handling all stay byte-equivalent.
- Contract or event-payload changes of any kind.

---

# Acceptance Criteria

- [ ] AC-0 (착수 = 재측정): before editing, re-grep both `OperatorSummary`
      declarations and confirm the collision still exists as described (two
      records, 3-field lookup projection vs 9-field listing DTO) and that the
      call-site population matches the file list in § In Scope. Code wins over
      this ticket's prose.
- [ ] `OperatorLookupPort` declares `record OperatorLookupRef(Long internalId,
      String operatorId, String tenantId)` with field order, the 2-arg legacy
      constructor, and javadoc semantics preserved; no `OperatorSummary`
      identifier remains anywhere under `application/port/`.
- [ ] `findByOperatorId` returns `Optional<OperatorLookupRef>`;
      `OperatorLookupPortImpl` overrides it with the new type and the mapping
      lambda is otherwise unchanged (`e.getId(), e.getOperatorId(),
      e.getTenantId()`).
- [ ] All 5 main-source call sites compile against the new name; grep for
      `OperatorLookupPort.OperatorSummary` returns 0 hits in `apps/`.
- [ ] `OperatorQueryService.OperatorSummary` is byte-unchanged, and
      `git diff --stat` shows no change to `OperatorQueryService.java`,
      `OperatorAdminController.java`, `presentation/dto/**`,
      `OperatorQueryServiceTest.java`, `OperatorAdminControllerSliceTest.java`.
- [ ] The 6 affected test classes changed **only** in type identifiers /
      variable names — no `assert*`, `verify`, `thenReturn` value, or fixture
      constant differs (reviewable line-by-line in the diff).
- [ ] Baseline captured first: `./gradlew
      :projects:iam-platform:apps:admin-service:test` GREEN **before** any edit,
      with the suite/test count recorded in the PR body.
- [ ] Post-change `./gradlew :projects:iam-platform:apps:admin-service:test`
      GREEN with the **same** test count as the recorded baseline (rename ⇒ no
      test population change).
- [ ] No files outside `projects/iam-platform/apps/admin-service/` and
      `projects/iam-platform/tasks/` are modified (`libs/`, other services, other
      projects: 0 diff).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/services/admin-service/architecture.md` § Internal Structure Rule
  (`application/port/` — "operator·totp·role 영속성 격리, TASK-BE-288"),
  § Allowed Dependencies, § Tenant Scope Enforcement (TASK-BE-249 — the
  `tenantId` field this record carries and the `'*'` sentinel)
- `platform/naming-conventions.md` § Java / Classes (canonical suffix table)
- `platform/refactoring-policy.md` (Rename category, green-baseline precondition)
- `platform/service-types/rest-api.md` (admin-service declared Service Type)

# Related Skills

- `.claude/skills/backend/` (implementation conventions)
- `.claude/skills/` testing guidance for the unchanged-behavior verification

---

# Related Contracts

- None changed. `specs/contracts/http/admin-api.md` describes the
  `GET /api/admin/operators` payload, which is produced by the **other**
  (unrenamed) `OperatorSummary` → `OperatorSummaryResponse`; the renamed type is
  never serialized and appears in no contract.

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md` (Thin Layered / Command Gateway;
  the renamed record stays a nested projection of an `application/port/`
  interface — no new file, no layer move, JPA types stay out of the application
  layer's import graph)

---

# Implementation Notes

- Precedent: `TASK-BE-558` (same project, same service) renamed admin-service's
  local `JwtSigner` → `OperatorJwtSigner` to clear a classname collision. Same
  shape of change, same verification command; reuse its discipline.
- The record is referenced in three syntactic forms — plain nested type
  (`OperatorLookupPort.OperatorSummary x = ...`), generic parameter
  (`Optional<OperatorSummary>`), and method reference
  (`OperatorLookupPort.OperatorSummary::tenantId` in `CreateOperatorUseCase`).
  Grep must cover all three; a missed method reference fails compilation, which
  is the safety net.
- Inside `OperatorLookupPort` and `OperatorLookupPortImpl` the type is referenced
  **unqualified** (`OperatorSummary`), so a blind qualified-form-only
  search-and-replace would miss the declaration site and the impl's javadoc.
- The 2-arg legacy constructor (`tenantId` defaults to `"fan-platform"`) is used
  by tests (`new OperatorLookupPort.OperatorSummary(OP_PK, OP_UUID)` in
  `AdminRefreshTokenServiceTest`, `AdminLogoutServiceTest`) — keep it; deleting
  it would be a behavior/scope change, not a rename.
- `platform/refactoring-policy.md` § Prohibited says "refactoring production code
  and test code in the same change". A type rename makes that physically
  impossible — the module would not compile with stale test references. The test
  edits here are therefore treated as part of the single Rename operation and are
  constrained to identifiers only (the same reading applied in `TASK-BE-558`,
  which renamed `JwtSignerTest` → `OperatorJwtSignerTest` in the impl commit).
  No test *logic* is refactored in this change.
- No test file needs renaming: no test class is named after the renamed record.

---

# Edge Cases

- **Wrong-type edit risk (the whole point of the task)**: 5 of the 12 touched
  files sit in packages that also touch the listing DTO family
  (`application/`), and `CreateOperatorUseCaseTest` /
  `OperatorQueryServiceTest` are siblings in the same test package. Every
  replacement must be verified to belong to the `OperatorLookupPort` context —
  qualified-form matches are unambiguous, unqualified matches must be checked
  against the enclosing file's imports.
- `{@link OperatorSummary}` javadoc references exist in **both**
  `OperatorLookupPort.java` (2 hits, incl. the TASK-BE-249 note) and
  `OperatorLookupPortImpl.java` (1 hit). A stale `{@link}` does not fail
  javac under default settings, so grep — not the compiler — is the guard here.
- Lombok/`@RequiredArgsConstructor` and Spring wiring are name-agnostic for a
  nested record; no bean name, `@Qualifier`, or configuration string literal
  references the old simple name (verify by grep for the quoted string
  `"OperatorSummary"`).
- Jackson never serializes the renamed record (no controller returns it), so no
  JSON field/shape can change. Confirm no `ObjectMapper` reference targets it.
- `docs/`, `knowledge/`, and `specs/` mention neither nested record (verified at
  authoring time — grep returned 0 hits in `specs/`); if the implementation grep
  finds a new mention, update the doc in the same PR.

---

# Failure Scenarios

- **Missed call site** → `admin-service` fails to compile; caught by
  `./gradlew :projects:iam-platform:apps:admin-service:test` before the task may
  move to `review/`.
- **Wrong `OperatorSummary` renamed** (the listing DTO) → either compilation
  failure in `OperatorAdminController`/`OperatorSummaryResponse` mapping, or a
  silent break of the platform-console `OperatorSummarySchema` mirror. Guard:
  the AC that requires 0 diff on `OperatorQueryService.java`,
  `OperatorAdminController.java`, and `presentation/dto/**`.
- **Test-count drift** between baseline and post-change run → something other
  than a rename happened (a test was dropped or newly excluded); STOP and
  reconcile before opening the PR. Per this project's prior finding, do not
  inherit a baseline number without re-counting the source population if the
  baseline run itself was polluted (`TASK-BE-559` note).
- **`:test` alone under-verifies** — iam's `build.gradle` applies
  `excludeTags 'integration'`, so context-booting classes are excluded from
  `:test` (recorded in `TASK-BE-559`). A rename cannot change Spring wiring for a
  nested record, so `:test` is sufficient here; if any excluded IT references the
  renamed type, compile it too (`:compileTestJava` / the `integrationTest`
  source set) before review.

---

# Test Requirements

- No new test scenarios — behavior is unchanged by definition.
- Existing coverage is the regression mechanism: `AdminActionAuditWriterTest`
  and `AdminActionDenyWriterTest` (fail-closed FK resolution),
  `AdminRefreshTokenServiceTest` (operator/token ownership mismatch),
  `AdminLogoutServiceTest`, `QueryTenantScopeGateTest` (`'*'` platform scope +
  per-tenant scope), `CreateOperatorUseCaseTest` (actor tenant resolution incl.
  the `orElse("fan-platform")` default) must pass with only type identifiers
  changed.
- Verification command (baseline + post-change):
  `./gradlew :projects:iam-platform:apps:admin-service:test`

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated (identifier-only) — no new scenarios
- [ ] Tests passing (`./gradlew :projects:iam-platform:apps:admin-service:test` GREEN, identical test count to baseline)
- [ ] Contracts updated if needed (N/A)
- [ ] Specs updated first if required (N/A — no architecture change; verified the spec does not name the record)
- [ ] Ready for review
