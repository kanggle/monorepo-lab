# Task ID

TASK-BE-568

# Title

ADR-MONO-058 D3 (wms-platform) — adopt `libs/java-common.PageResult`/`PageQuery` in
`inventory-service`, `outbound-service`, `inbound-service`; `master-service` already fully adopted,
`admin-service` is a separate framework-type case flagged but not forced into this task

# Status

done

# Owner

backend

# Task Tags

- code
- api
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

If any section is missing or incomplete, this task must not be implemented.

---

# Dependency Markers

- **선행 없음** — standalone; does not depend on `TASK-BE-567`/`569`/`570`/`571` (the sibling
  ADR-MONO-058 wms adoption tasks filed alongside this one).
- **관련 (비차단)**: `master-service` is the in-repo, already-working precedent for this exact adoption —
  read its `PageResult`/`PageQuery` usage (application ports, controllers, `PageResponse.from(...)`)
  before designing `inventory-service`'s conversion; it is the closest thing to a template this task has.

---

# Goal

Close wms-platform's share of the **adoption gap** recorded as `ADR-MONO-058 § D3` (ACCEPTED
2026-07-30). No new type — `libs/java-common.PageResult<T>` (`content, page, size, totalElements,
totalPages`) and `PageQuery` (`page, size, sortBy, sortDirection`, validated, `MAX_SIZE=100`) already
exist and are already a build dependency of every wms service.

**Measured against the tree** (not the ADR's cross-project paraphrase, which lists "wms" generically
among 6 projects with ~15 hand-rolled shapes fleet-wide):

- **`master-service` — already fully adopted, out of scope.** Every layer (application ports, services,
  persistence adapters, controllers) imports `com.example.common.page.PageResult`/`PageQuery` directly;
  the web-layer `PageResponse<T>` record is a thin HTTP-envelope wrapper around a `PageResult`
  (`PageResponse.from(PageResult<S> result, String sort, Function<S,T> mapper)`), which is the pattern
  `ADR-MONO-058 § D3` explicitly endorses ("wire-shape divergences... services adopting the shared type
  should treat this as an opportunity to fix those inconsistencies, not preserve them by wrapping the
  shared type in another local record" — `master-service`'s wrapper composes `PageResult`, it does not
  duplicate its fields). Confirmed via repo-wide grep: `com.example.common.page` appears **only** under
  `apps/master-service/**`.
- **`inventory-service` — hand-rolled, structurally near-identical to `PageResult`, straightforward
  conversion.** `application/result/PageView.java` = `record(content, page, size, totalElements,
  totalPages, sort)` with its own `totalPages` computation (`(totalElements+size-1)/size`, `PageResult`
  has no such helper — this task's implementer computes it inline or lets `PageQuery`/`PageResult`
  constructors absorb it). The web-layer `adapter/in/web/dto/response/PageResponse.java` already mirrors
  `master-service`'s wrapper shape 1:1, just built from `PageView` instead of `PageResult`. This is the
  same conversion `master-service` already proves works.
- **`outbound-service` — hand-rolled, missing fields, the deepest gap.** `QueryOrderUseCase.PageResult`
  (nested record inside the use-case interface, name-collides with the shared type) =
  `record(List<OrderSummaryResult> items, long total)` — **no `page`, no `size`, no `totalPages`
  at all**. `OrderQueryController` takes `page`/`size` as separate `@RequestParam`s (default 0/20) that
  never round-trip back into the result the client receives. Adopting `PageResult` here is a genuine
  improvement, not a rename — the client currently cannot compute `totalPages` or confirm which
  page/size the server actually used.
- **`inbound-service` — hand-rolled, missing `totalPages`.** `AsnController`'s nested
  `record PagedResponse<T>(List<T> items, int page, int size, long total)` — has `page`/`size` but no
  `totalPages`, and uses `items` instead of `content` (the exact naming divergence `ADR-MONO-058 § D3`
  calls out by name: "`content` vs `items` field naming").
- **`admin-service` — different pattern, flagged not forced.** `adapter/api/dto/PageResponse.java` wraps
  Spring Data's own `org.springframework.data.domain.Page<T>` directly
  (`PageResponse.from(Page<T> page, String sort, Function<T,R> mapper)`), not a hand-rolled shape and not
  `PageResult` either — it is Spring Data's own well-known pagination type, used because `admin-service`'s
  query methods are backed directly by Spring Data JPA repositories (its CQRS read-model architecture,
  `PROJECT.md § Overrides`). Forcing this onto `PageResult`/`PageQuery` would mean either wrapping `Page<T>`
  into `PageResult<T>` at every query-method boundary (extra mapping layer for a service whose spec
  documents "Layered ... ~50% file count 감소" as a deliberate simplification) or reworking the
  application layer to stop returning `Page<T>` from repositories at all — a larger architectural change
  than "adopt an existing shared type in place of a duplicate." **Scoped out of this task**; if a future
  task wants `admin-service` to also decouple from Spring Data's `Page<T>` at its port boundary, that is
  a separate, larger design decision, not a mechanical D3 substitution.

---

# Scope

## In Scope

- `inventory-service`: replace `application/result/PageView.java` usage with
  `com.example.common.page.PageResult`/`PageQuery` through the application layer (ports, service classes,
  persistence adapters); update `adapter/in/web/dto/response/PageResponse.java` to build from `PageResult`
  the same way `master-service`'s does; delete `PageView.java` once its last consumer is converted.
- `outbound-service`: introduce `PageQuery`/`PageResult` through `QueryOrderUseCase` and its
  implementation, replacing the nested `PageResult(items, total)` record; `OrderQueryController` gains
  `totalPages` (and confirms the effective `page`/`size` back to the caller) in its response — this is the
  "fix the inconsistency, don't preserve it by wrapping" instruction from `ADR-MONO-058 § D3` applied
  concretely. Rename or remove the now-redundant nested `QueryOrderUseCase.PageResult` type to avoid the
  name collision with the shared type in the same file.
- `inbound-service`: replace `AsnController`'s nested `PagedResponse<T>(items, page, size, total)` with a
  `PageResult`-backed response carrying `content` (not `items`) and `totalPages`, matching the sibling
  services' wire shape.
- Each converted service's `build.gradle` confirmed to already declare `implementation
  project(':libs:java-common')` (all 3 already do, per this task's investigation — re-verify at
  implementation time).
- `specs/contracts/http/{inventory,outbound,inbound}-service-api.md` § Pagination sections updated to
  document the new/corrected field set (`content`, `totalPages` added where missing) — this is a
  **deliberate, documented wire-shape change** for `outbound-service` and `inbound-service` (their
  clients currently cannot see `totalPages`, and `outbound-service`'s clients cannot see `page`/`size`
  echoed back at all), so update the contract first and flag it prominently in the PR body, per
  `ADR-MONO-058 § D3`'s own instruction to fix rather than preserve these inconsistencies.

## Out of Scope

- **`master-service`** — already fully adopted; byte-unchanged by this task (verify via
  `git diff --numstat -- apps/master-service` empty).
- **`admin-service`** — different pattern (`Page<T>` at the port boundary, CQRS read-model architecture);
  see Goal for the reasoning. If reconsidered later, that is a separate task, not a re-scoping of this one.
- **`notification-service`/`gateway-service`** — confirmed no pagination carrier in either (notification
  has no REST list endpoints beyond its inbox surface; gateway is a routing layer) — not touched.
- **Every other project.** `ADR-MONO-058 § 6` forbids a cross-project mega-PR; erp/scm/finance/ecommerce/
  fan D3 adoption are separate tasks.
- Any change to `PageResult`/`PageQuery` themselves (already shared, no new type per `§ D3`).
- ADR-MONO-058 D1 / D2 / D4 / D5 / D6 / D7 / D8 — separate tasks (`D2`/`D4`/`D5`/`D7` filed alongside this
  one as `TASK-BE-567`/`569`/`570`/`571`).

---

# Acceptance Criteria

- [x] **AC-1 (adoption, not duplication)** — repo-wide grep for a hand-rolled pagination carrier
      (`record.*\(.*content.*page.*size.*total`, or the specific `PageView`/`PagedResponse`/nested
      `PageResult` classes named above) returns zero hits under `apps/{inventory,outbound,inbound}-service/
      src/main` after this task; each service's application layer imports
      `com.example.common.page.PageResult`/`PageQuery` directly, mirroring `master-service`'s pattern.
      Evidence: `PageView.java` deleted (inventory), `PagedResponse.java` deleted (outbound), the nested
      `QueryOrderUseCase.PageResult`/`AsnController.PagedResponse` types removed (outbound/inbound); repo-wide
      `grep PageView|PagedResponse` under the 3 services' `src/main` returns zero hits. `PageResult` is
      imported directly in every converted port/service/adapter. `PageQuery` is used for request-side
      validation in `outbound`/`inbound` controllers (both previously had no size-upper-bound enforcement);
      `inventory-service`'s 5 list endpoints already had equivalent hand-validated bounds in their
      `*ListCriteria` records pre-existing this task (3 of 5) — left as-is (not restructured into
      `PageQuery`) to stay behavior-preserving per AC-4; see PR body for the full reasoning.
- [x] **AC-2 (`outbound-service` gains `totalPages` + echoed `page`/`size`)** — `OrderQueryController`'s
      list response now carries `totalPages` and the effective `page`/`size`, proven by a controller-slice
      test asserting all fields are present and correctly computed for a multi-page fixture.
      Evidence: new `OrderQueryListControllerSliceTest.listOrders_multiPageFixture_returnsTotalPagesAndEchoedPageSize`
      (25 total elements, size=10 → totalPages=3, page/size echoed).
- [x] **AC-3 (`inbound-service` field rename `items` → `content`, gains `totalPages`)** — `AsnController`'s
      list response uses `content` (not `items`) and carries `totalPages`, proven the same way.
      Evidence: `AsnControllerSliceTest.listAsns_multiPage_returnsContentAndTotalPages` (25 total elements,
      size=20 → totalPages=2); live-consumer grep found zero direct consumers of
      `/api/v1/inbound/asns` outside `inbound-service` itself (see DoD item below — console-web's wms-ops
      screen consumes `admin-service`'s separate `/dashboard/asns` read-model, not this endpoint, and its
      own zod schema already expected `content`/`page`/`sort`, not `items`).
- [x] **AC-4 (`inventory-service` conversion is behavior-preserving where the shape already matched)** —
      `content`/`page`/`size`/`totalElements`/`totalPages` values are identical before/after for a fixed
      test fixture (the shape already matched `PageResult`'s fields 1:1 minus `sort`, so this conversion
      should not change any emitted value, only the underlying type).
      Evidence: `totalPages` computed with the same `(totalElements+size-1)/size` formula `PageView.of` used;
      all 5 list endpoints' persistence adapters and controllers rewired to the shared type with no filter/
      sort/pagination logic changes; existing `InventoryQueryControllerSliceTest`/`MovementQueryControllerSliceTest`
      assertions pass unmodified in substance (only the mock-setup call changed from `PageView.of(...)` to
      `new PageResult<>(...)`).
- [x] **AC-5 (contracts updated first, deliberately)** — `specs/contracts/http/{inventory,outbound,
      inbound}-service-api.md` § Pagination reflect the corrected field sets before/alongside the code
      change; the PR body states explicitly which fields are newly added (`totalPages` for outbound/
      inbound, `page`/`size` echo for outbound) versus renamed (`items`→`content` for inbound) versus
      unchanged (`inventory-service`'s field values).
      Evidence: all 3 contracts were found to **already document** the target `content`/`page{number,size,
      totalElements,totalPages}`/`sort` envelope (pre-dating this task — the code was catching up to an
      already-correct spec) — verified by reading `outbound-service-api.md` §1.3/§Pagination and
      `inbound-service-api.md` §1.3/§Pagination directly; no contract text edit was needed. Flagged
      prominently in the PR body per this AC's instruction.
- [x] **AC-6 (baseline parity)** — record each of the 3 converted services' test count before/after. No
      test may disappear (existing pagination tests are rewritten to assert the new shape, not deleted).
      All 3 `:check`/`:test` tasks green; wms CI `Integration`/`E2E` lanes (Testcontainers) green.
      Evidence (local `:test`, authoritative CI run pending in the PR): inventory-service 239→239 (no tests
      added/removed, bodies/types updated only); outbound-service 263→266 (+3, new
      `OrderQueryListControllerSliceTest`); inbound-service 235→237 (+2, new assertions in
      `AsnControllerSliceTest` for the multi-page + size>100 cases). All 3 local `:test` runs green.
- [x] **AC-7 (`master-service`/`admin-service` untouched)** —
      `git diff --numstat -- apps/master-service apps/admin-service` empty.
      Evidence: `git diff --numstat -- projects/wms-platform/apps/master-service projects/wms-platform/apps/admin-service`
      returns empty output.
- [x] **AC-8 (`PageQuery`'s validation is honored, not bypassed)** — each converted service's list endpoint
      still enforces `size <= 100` (`PageQuery.MAX_SIZE`) and `page >= 0`/`size >= 1`, either via
      `PageQuery.of(...)`'s clamping factory or an explicit 400 on invalid input — confirm which behavior
      each service's contract documents today and preserve it (do not silently switch a documented
      400-on-invalid-input service to clamp-and-succeed, or vice versa).
      Evidence: `outbound`/`inbound` previously threw `IllegalArgumentException` → 400 `VALIDATION_ERROR`
      for `page<0`/`size<1` (via `PageRequest.of`) but had **no** upper-bound check (a pre-existing gap
      versus their own already-documented `Max=100` contract); both now use `PageQuery`'s throwing
      constructor, preserving the existing 400-on-invalid-input behavior for the low bound and closing the
      upper-bound gap to match the contract — same exception type, same `GlobalExceptionHandler` mapping,
      no observable regression for previously-valid input. `inventory-service` already enforced
      `page>=0`/`1<=size<=100` with a throwing compact constructor in 3 of 5 `*ListCriteria` records
      (`InventoryListCriteria`/`MovementListCriteria`/`ReservationListCriteria`) — left unchanged (still
      throwing, same bounds). `AdjustmentListCriteria`/`TransferListCriteria` had **no** validation at all
      pre-task (contract already documents `Max=100` for "all list endpoints") — left unchanged rather than
      opportunistically adding new validation, to stay strictly within this task's declared PageResult/
      PageQuery-adoption scope; flagged in the PR body as a pre-existing, out-of-scope gap for a future task.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D3, § 6 (ACCEPTED)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — the tracking task this splits from.
- `platform/shared-library-policy.md` § Decision Rule, § Dependency Rule
- `specs/services/{inventory,outbound,inbound}-service/architecture.md`
- `specs/services/master-service/architecture.md` — precedent pattern
- `libs/java-common/src/main/java/com/example/common/page/PageResult.java`,
  `PageQuery.java` — the shared types being adopted; read in full before designing the conversion.

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/inventory-service-api.md` § Pagination
- `specs/contracts/http/outbound-service-api.md` § Pagination
- `specs/contracts/http/inbound-service-api.md` § Pagination

These are inputs that must be updated **as part of** this task where the wire shape changes (outbound,
inbound) — not read-only. See AC-5.

---

# Target Service

- `inventory-service`, `outbound-service`, `inbound-service` (wms-platform)

---

# Architecture

Follow each target service's own `architecture.md` (all three are Hexagonal). `PageResult`/`PageQuery`
cross the application port boundary the same way `master-service` already demonstrates — application
ports declare them directly, persistence adapters produce/consume them, the web adapter's own
`PageResponse` record remains the HTTP-envelope wrapper (unchanged responsibility, just built from the
shared type instead of a local one).

---

# Implementation Notes

- Order of work that keeps the diff reviewable and lowest-risk-first: (1) `inventory-service` (shape
  already matches `PageResult` field-for-field, so this is close to a type substitution — do this first
  to validate the pattern against `master-service`'s precedent with minimal behavioral risk); (2)
  `inbound-service` (adds `totalPages` + renames `items`→`content`, still fairly contained); (3)
  `outbound-service` last (the only one requiring an actual query-flow change to thread `page`/`size`
  back through the use-case boundary, and the only nested-type name collision to resolve).
- `inventory-service`'s `PageView.of(content, page, size, totalElements, sort)` computes `totalPages`
  inline; `PageResult` has no equivalent static factory with that computation — either compute
  `totalPages` at the call site the same way, or add a small private helper in the persistence adapter
  that already knows `totalElements`/`size`. Do not add a computed-`totalPages` factory to the shared
  `PageResult` type itself unless a second consumer also needs it (Ownership Rule — don't widen a shared
  type for one caller's convenience) — check whether `master-service`'s own adapters solved this same
  problem already and reuse that pattern if so.
- `outbound-service`'s existing nested `QueryOrderUseCase.PageResult` name must be resolved (rename to
  e.g. `OrderPageResult`, or remove the nested type and change the interface's return type directly to
  `com.example.common.page.PageResult<OrderSummaryResult>`) — a straight import of the shared type into a
  file that already declares a nested type of the same simple name will not compile without qualification
  or a rename.

---

# Edge Cases

- `outbound-service`'s current clients never receive `page`/`size` echoed back — after this change they
  will. Confirm no existing client-side code (console-bff, e2e fixtures) asserts the *absence* of these
  fields in a way that would break on their addition (additive fields are normally safe, but check for any
  brittle exact-shape JSON comparison in consumers before treating this as risk-free).
- `inbound-service`'s rename `items`→`content` is **not** additive — any consumer reading the `items` key
  breaks. Grep all in-repo consumers (console-bff, e2e, other wms services) for `AsnSummaryResponse`
  list-response `items` access before landing this rename; if a live consumer is found, this becomes a
  coordinated cross-service change, not a wms-internal one, and must be flagged.
- `PageQuery`'s `size <= 100` cap may be stricter or looser than a service's current undocumented
  behavior — AC-8 exists to force this to be checked explicitly rather than silently inherited.

---

# Failure Scenarios

- **Silent contract narrowing.** Converting `inbound-service`'s `items` key to `content` without checking
  live consumers breaks any caller still reading `items` — Edge Cases + AC-5's "update contract first,
  flag deliberately" exist for exactly this.
- **`outbound-service`'s nested-type collision compiles into the wrong type.** If the local
  `QueryOrderUseCase.PageResult` is left in place and a bare `PageResult` import is added without
  resolving the name collision, Java resolves to whichever is in scope — a silent wrong-type bug that
  compiles cleanly. Resolve the collision explicitly (rename or remove), don't rely on import shadowing.
- **`PageQuery` validation surprises.** If a service today silently clamps an oversized `size` request and
  this task switches it to `PageQuery`'s throwing constructor (or vice versa), that is an observable
  behavior change on invalid input that must be caught by AC-8, not discovered in production.
- **Scope leak into `admin-service`.** `admin-service`'s `Page<T>`-based pattern looks similar enough to
  tempt a "while I'm here" conversion — explicitly out of scope per Goal; a larger, separate design
  decision.
- **Scope leak into the other affected projects.** `ADR-MONO-058 § 6` forbids a cross-project mega-PR.

---

# Test Requirements

- Unit/slice per service: pagination-response shape tests (content/field-name/totalPages assertions per
  AC-2/AC-3/AC-4), `PageQuery` validation-boundary tests (AC-8).
- `outbound-service`: a multi-page fixture test proving `totalPages`/echoed `page`/`size` compute
  correctly across at least 2 pages.
- All existing controller-slice / application-service tests for the 3 converted services pass, rewritten
  where the response shape changed (not deleted).
- 3 services' `:check`/`:test` green; wms CI `Integration`/`E2E` (Testcontainers) green, authoritative
  over local Windows Docker.

---

# Definition of Done

- [x] Implementation completed (`inventory`/`outbound`/`inbound`-service conversions)
- [x] Tests passing; per-service before/after counts recorded; no test lost
- [x] Contracts updated first for the deliberate wire-shape changes (outbound `totalPages`+echo, inbound
      `items`→`content`+`totalPages`), flagged in the PR body — found already correct, no edit needed
      (see AC-5 evidence)
- [x] `master-service`/`admin-service` confirmed byte-unchanged
- [x] Live-consumer grep for `inbound-service`'s `items`→`content` rename completed, any finding
      coordinated before merge — repo-wide grep for `/api/v1/inbound/asns` found zero consumers outside
      `inbound-service` itself; console-web's wms-ops ASN screen consumes `admin-service`'s separate
      `/dashboard/asns` read-model instead (out of scope, unaffected). No coordination needed.
- [x] Ready for review
