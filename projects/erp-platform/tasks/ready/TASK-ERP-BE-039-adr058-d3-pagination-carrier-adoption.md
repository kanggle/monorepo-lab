# Task ID

TASK-ERP-BE-039

# Title

ADR-MONO-058 D3 (erp-platform, all four servlet services) — adopt
`libs/java-common.PageResult`/`PageQuery` in place of five hand-rolled page-result records, fixing
the `page`/`size`/`totalPages` omission along the way

# Status

ready

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

# Goal

Close erp-platform's share of the adoption gap recorded as `ADR-MONO-058 § D3` (ACCEPTED
2026-07-30). No new shared type is created — `libs/java-common.PageResult`/`PageQuery` already
exist and are already on the classpath of at least two of erp's four services (used for
`UuidV7`-adjacent technical DTOs per the ADR's own note that the lib is "frequently already on the
consuming service's classpath and simply never imported for paging"); erp instead independently
declared **five** hand-rolled page-result records across its four services, none of which import
the shared type.

## Measured against the tree — what erp actually has (not the ADR's cross-project paraphrase)

| Service | Local page type | Package | Shape |
|---|---|---|---|
| `masterdata-service` | `PageResult<T>` | `domain.common` | `(List<T> content, long totalElements)` |
| `approval-service` | `PageResult<T>` | `domain.common` | `(List<T> content, long totalElements)` — javadoc explicitly says "Mirrors masterdata-service's `PageResult`" |
| `notification-service` | `InboxPage` | `application.query` | `(List<Notification> content, int page, int size, long totalElements)` |
| `read-model-service` | `EmployeeOrgViewPage` | `application.query` | `(List<EmployeeOrgView> content, int page, int size, long totalElements)` |
| `read-model-service` | `ApprovalFactPage` | `application.query` | `(List<ApprovalFactView> content, int page, int size, long totalElements)` |
| `read-model-service` | `DelegationFactPage` | `application.query` | `(List<DelegationFactProjection> content, int page, int size, long totalElements)` |

(`read-model-service` alone declares three non-generic page records instead of one generic type —
its own internal duplication, independent of the cross-service D3 finding.)

`libs/java-common.PageResult<T>` = `record(List<T> content, int page, int size, long
totalElements, int totalPages)`. **Every one of erp's six local records is missing `totalPages`,
and the two `domain.common.PageResult` copies (approval/masterdata) are additionally missing
`page`/`size` entirely** — they carry only `content` + `totalElements`. `masterdata-api.md` § Common
response shapes documents pagination metadata as `page` / `size` / `totalElements` (confirmed by
reading the contract directly) — i.e. **the contract already promises a shape the two
`domain.common.PageResult` records cannot fully supply on their own**; whatever currently supplies
`page`/`size` in masterdata/approval's actual HTTP responses does so from a source outside
`PageResult` itself (likely the controller composing the response from the original `PageQuery`-shaped
request parameters, not from the page-result object) — **verify this composition point before
assuming a straightforward swap-in**, since adopting `libs/java-common.PageResult` (which carries
`page`/`size` as first-class fields) may let the controller drop that separate composition step, a
genuine simplification the ADR anticipates ("adopting services should treat this as an opportunity
to fix those inconsistencies").

`libs/java-common.PageQuery(int page, int size, String sortBy, String sortDirection)` is a
**request**-side type with self-validating constraints (`page >= 0`, `1 <= size <= 100`) and a
clamping `of(...)` factory — confirm whether any of erp's four services has an equivalent
hand-rolled request-side pagination type to retire alongside the four result-side records (grep for
a local `PageQuery`/`PageRequest`/`PageParams`-shaped class before assuming there is none).

---

# Scope

## In Scope

- Delete `approval-service`'s and `masterdata-service`'s local `domain.common.PageResult<T>` and
  replace all use sites with `com.example.common.page.PageResult<T>`, populating the newly-required
  `page`, `size`, and `totalPages` fields (`totalPages = (int) Math.ceil((double) totalElements /
  size)`, or whatever equivalent computation the query-side pagination already performs — locate
  the actual page/size values at each construction call site rather than inventing them).
- Delete `notification-service`'s `InboxPage`, `read-model-service`'s `EmployeeOrgViewPage` /
  `ApprovalFactPage` / `DelegationFactPage`, and replace all use sites with
  `com.example.common.page.PageResult<T>`, adding the now-required `totalPages` field at each
  construction site.
- If any of the four services has a local request-side pagination type, retire it in favor of
  `com.example.common.page.PageQuery` where the shapes genuinely match (do not force a fit if a
  service's request-side parameters carry fields `PageQuery` does not, e.g. filter parameters —
  those stay separate, only the page/size/sort mechanics move).
- `build.gradle` for all four services — add `implementation project(':libs:java-common')` if not
  already declared (verify each; erp's `UuidV7` usage the ADR references may already pull the
  dependency in for some services, not necessarily all four).
- Update each affected controller's response-building code to source `page`/`size`/`totalPages`
  directly off the adopted `PageResult` rather than composing them separately, where that
  simplification is genuinely available (per the ADR's "opportunity to fix inconsistencies, not
  preserve them by wrapping" guidance) — but do not change any JSON key name or nesting the
  contract documents (`content`, `page`, `size`, `totalElements` are all contract-stable names per
  `masterdata-api.md`; confirm `totalPages` is either already contract-documented or add it as a
  new additive field, not a breaking rename).
- Spec reconciliation: each affected `architecture.md § Dependencies` gains `libs:java-common` if
  not already documented; each affected `specs/contracts/http/*.md` § Common response shapes gains
  `totalPages` if it is being newly emitted (additive field — confirm additive-only per
  `platform/api-versioning-policy.md` or equivalent before treating it as safe by assumption).

## Out of Scope

- **Every other project** — `§ 6` forbids a cross-project mega-PR; finance/scm/wms/ecommerce/fan's
  D3 adoption (finance/scm/wms/ecommerce not yet done per the ADR's audit table) are separate
  tasks.
- **`libs/java-common.PageResult`/`PageQuery` themselves** — no new shared code; this is adoption
  only. If implementation finds the shared type genuinely cannot represent one of erp's six local
  shapes (e.g. a required field the shared type has no room for), that is a finding to report, not
  a library change to make unilaterally inside a project-scoped task.
- **`gateway-service`** — reactive; no pagination surface of this kind. Untouched.
- **Any filter/query parameter beyond page/size/sort** — e.g. `asOf`/`active`/`parentId` on
  masterdata's list endpoints (`TASK-ERP-BE-033`) stay exactly as they are; only the
  page-size-sort mechanics and the page-result carrier move.
- **ADR-MONO-058 D1 / D2 / D4 / D5** — separate tasks.

---

# Acceptance Criteria

- [ ] **AC-1 (adoption, all local page types retired)** — repo-wide grep under
      `projects/erp-platform/apps/*/src/main` finds zero declarations of `PageResult` (the local
      `domain.common` copies), `InboxPage`, `EmployeeOrgViewPage`, `ApprovalFactPage`,
      `DelegationFactPage`; all use sites reference `com.example.common.page.PageResult<T>`.
- [ ] **AC-2 (`totalPages` correctly computed at every construction site)** — for each of the (at
      least) six former local-type construction sites, a test asserts `totalPages` matches the
      expected ceiling division for a non-exact-multiple `totalElements`/`size` pair (e.g.
      `totalElements=25, size=10 → totalPages=3`) and for `totalElements=0` (`totalPages=0`, not
      `1` and not a division error).
- [ ] **AC-3 (contract shape preserved or explicitly extended)** — `content`/`page`/`size`/
      `totalElements` JSON keys and nesting are byte-identical to today's wire output for every
      endpoint touched; `totalPages`, if newly emitted, is additive (present, does not replace or
      rename any existing key) and is added to the relevant contract file(s) in the same PR.
- [ ] **AC-4 (masterdata/approval `page`/`size` composition reconciled)** — the mechanism that
      currently supplies `page`/`size` in masterdata/approval's HTTP responses (found via the
      Implementation Notes' investigation step) is either subsumed by the adopted `PageResult`
      (preferred, per the ADR's "opportunity to fix" framing) or left in place with a stated reason
      if subsuming it is not straightforward — not silently duplicated.
- [ ] **AC-5 (baseline parity)** — before/after test counts recorded per module; no test lost; all
      four `:check` GREEN; CI `Integration (erp-platform, Testcontainers)` GREEN authoritative.
- [ ] **AC-6 (specs reconciled)** — `architecture.md` dependency lines and contract § Common
      response shapes updated wherever `libs:java-common` or `totalPages` is newly present.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`,
> then load `rules/common.md` plus `rules/domains/erp.md` and `rules/traits/{internal-system,
> transactional,audit-heavy}.md`. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D3, § 6
  (ACCEPTED 2026-07-30)
- `platform/shared-library-policy.md` § Decision Rule
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` — origin tracking task
- `projects/erp-platform/tasks/done/TASK-ERP-BE-033-masterdata-list-filters-total-elements-events-doc.md`
  — the task that made masterdata's `totalElements` a TRUE cross-page count (not
  `content.size()`) — this task's `totalPages` computation must be derived from that same TRUE
  count, not re-introduce a page-slice-size bug at one remove.
- `projects/erp-platform/tasks/done/TASK-ERP-BE-036-approval-apienvelope-totalelements-fix.md` —
  the equivalent fix for approval-service's `ApiEnvelope.ofList` — read before touching
  approval-service's pagination path, since this task's adoption must not regress that fix.
- `projects/erp-platform/specs/contracts/http/{approval,masterdata,notification,read-model}-api.md`
  § Common response shapes / § Pagination
- `projects/erp-platform/specs/services/{approval,masterdata,notification,read-model}-service/architecture.md`
  § Dependencies

---

# Related Contracts

- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Common response shapes — documents
  `page`/`size`/`totalElements`; gains `totalPages` if newly emitted.
- `projects/erp-platform/specs/contracts/http/{approval,notification,read-model}-api.md` § Common
  response shapes / list-endpoint sections — same additive-field treatment as needed.

These are **read-write inputs**: if `totalPages` is newly emitted, the contract file must be
updated in the same PR before/alongside implementation, per `CLAUDE.md`. If any existing field's
name, nesting, or semantics would have to change to adopt the shared type, that is a genuine
contract change — stop and report rather than ship it silently.

---

# Target Service

- `approval-service`, `masterdata-service`, `notification-service`, `read-model-service`
  (erp-platform)
- Consumes `libs/java-common.PageResult`/`PageQuery` — no shared-library code authored by this
  task.

---

# Architecture

Follow each target service's own `architecture.md`. The page-result type stays wherever the domain
layer boundary rule places it today (`domain.common` for approval/masterdata, `application.query`
for notification/read-model) — adopting the shared type does not itself require moving it to a
different layer, but note that `com.example.common.page.PageResult` is a `libs/` type, so a service
whose `domain` layer is documented as framework/dependency-free (masterdata's own `PageResult`
javadoc: "Pure Java — no framework imports (domain layer boundary rule)") must confirm the shared
type is equally framework-free (it is — `com.example.common.page.PageResult` has no Spring/JPA
imports) before importing a `libs/` type into a `domain` package; if the project's own layer
convention forbids any `libs/` import in `domain` regardless of the imported type's own purity,
resolve the page-result construction at the boundary between `domain` and `application` instead of
inside `domain`, and record that placement decision.

---

# Implementation Notes

- **Investigate the `page`/`size` composition question (AC-4) before writing any adoption code.**
  Read `masterdata-service`'s and `approval-service`'s controller/response-DTO code for whatever
  currently populates `page`/`size` in the HTTP response despite the local `PageResult` carrying
  neither field — this is very likely composed from the inbound `PageQuery`-shaped request
  parameters at the controller boundary, not stored on the page-result object at all. If so,
  adopting `com.example.common.page.PageResult` (which does carry `page`/`size`) is a genuine
  simplification: the controller can populate them once, at construction, instead of twice
  (once on the query side, once on the response side) — but only if the two are always
  guaranteed to agree; if there is any daylight between the requested page/size and what the
  result actually reflects (e.g. a clamped/defaulted value), read from the result object's own
  fields, not the raw request, once adopted.
- Order of work that keeps the diff reviewable: (1) `notification-service`
  (`InboxPage` → shared type — single call site, smallest surface); (2) `read-model-service`
  (three local types → one shared generic type — the biggest internal-duplication win); (3)
  `masterdata-service` (the AC-4 investigation payoff, and the one with the most-referenced
  `PageResult.map(...)` call sites); (4) `approval-service` (mirrors masterdata, do last so the
  masterdata pattern is proven first); (5) specs.
- `com.example.common.page.PageResult.map(...)` signature differs slightly from erp's local
  `map(...)` (`Function<T,R>` vs. `Function<? super T, ? extends R>`) — check call sites compile
  against the shared type's exact generic bounds; do not assume a silent drop-in.

---

# Edge Cases

- **`totalElements = 0`.** `totalPages` must compute to `0`, not `1` (an empty result is zero
  pages, not one empty page) and not throw a divide-by-zero — `size` is always `>= 1` per
  `PageQuery`'s own invariant, so the ceiling-division formula is safe, but verify the actual
  construction sites use a `size` that is genuinely never zero (some erp use sites may resolve
  `size` from a different source than a validated `PageQuery`).
- **`read-model-service`'s three page types collapsing into one generic type.** Confirm no two of
  `ApprovalFactPage`/`DelegationFactPage`/`EmployeeOrgViewPage`'s current consumers rely on the
  type name itself (e.g. an overloaded method resolving by parameter type) — a generic
  `PageResult<ApprovalFactView>` vs. `PageResult<DelegationFactProjection>` are different
  parameterized types at the call-site level but the same erasure at reflection/serialization
  boundaries if any exist; check before assuming zero risk.
- **`masterdata-api.md`'s documented pagination shape** must still validate against whatever JSON
  schema/contract test exists after `totalPages` is added — additive fields should not break an
  existing "exact shape" assertion if one exists (check for `strict`-mode JSON assertions in
  existing slice tests, which a purely-additive field could still break if the test asserts an
  exact key set rather than presence of specific keys).

---

# Failure Scenarios

- **Silently changing `totalElements` semantics while touching the same call sites.**
  `TASK-ERP-BE-033`/`TASK-ERP-BE-036` both fixed `totalElements` bugs (page-slice-size vs. true
  cross-page count) in masterdata/approval respectively — this adoption touches the exact same
  construction sites and must not regress either fix. Verify both fixes' regression tests still
  pass unmodified after the swap.
- **Assuming `page`/`size` composition (AC-4) is safe to delete without checking for
  clamping/defaulting divergence** — if the controller currently defaults or clamps
  page/size differently from how the adopted `PageResult` would be populated, collapsing the two
  paths could silently change what a client sees for an out-of-range request.
- **A "strict shape" test breaking on the additive `totalPages` field** — treat a break here as a
  real signal to update the specific assertion (to allow the new key) rather than as evidence the
  adoption is unsafe; but do not skip investigating why it broke.
- **Scope leak into the other seven projects** — `§ 6` forbids a cross-project mega-PR.

---

# Test Requirements

- Unit: `totalPages` ceiling-division correctness (exact multiple, non-exact multiple, zero
  elements) per adopted call site.
- Slice/integration (per service, ×4): existing list-endpoint tests pass unmodified except for the
  addition of a `totalPages` JSON-path assertion; `TASK-ERP-BE-033`/`036`'s regression tests
  re-run and pass.
- `./gradlew :libs:java-common:test` (if touched — expected untouched) and the four erp `:check`
  tasks GREEN. CI `Integration (erp-platform, Testcontainers)` GREEN authoritative.

---

# Definition of Done

- [ ] All six local page-result types retired; `com.example.common.page.PageResult<T>` adopted
      throughout
- [ ] `totalPages` correctly computed and tested at every former construction site
- [ ] AC-4's `page`/`size` composition question investigated and resolved (subsumed or explicitly
      left in place with a stated reason)
- [ ] Tests passing; per-service before/after counts recorded; no test lost;
      `TASK-ERP-BE-033`/`036` regressions re-verified green
- [ ] Contracts updated for any newly-emitted field (`totalPages`)
- [ ] Specs updated (`architecture.md` dependency lines as needed)
- [ ] Ready for review
