# Task ID

TASK-ERP-BE-036

# Title

Fix approval-service `ApiEnvelope.ofList` to carry the TRUE cross-page `totalElements` (not `data.size()`)

# Status

review

# Owner

backend

# Task Tags

- code
- api

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

`ApiEnvelope` (the shared `{ data, meta }` success envelope shape) is duplicated
per-service across `masterdata-service`, `notification-service`,
`read-model-service`, and `approval-service`. `masterdata-api.md` § PageMeta
(mirrored by the other two correct copies) states explicitly that
`meta.totalElements` is the TRUE total-row count for the query **across ALL
pages** — supplied by the repository's count query — NOT the size of the
current page's slice. Three of the four services implement this correctly
(`ofList(List<T> data, int page, int size, long totalElements)` — an explicit
`long totalElements` parameter fed by a real cross-page count).

`approval-service`'s copy instead computes `totalElements` as `data.size()`
inside `ofList(List<T> data, int page, int size)` — i.e. the size of the
**current page's slice**. Any approval-service paginated list response whose
true result set spans more than one page reports a `totalElements` equal to
the returned page size, not the actual total row count, breaking any client
that uses `totalElements` to compute total pages or render "X of Y results".

After this task: `approval-service`'s `ApiEnvelope.ofList` takes an explicit
`long totalElements` parameter (byte-identical signature shape to
masterdata/notification/read-model's copies), and every call site that is
**actually paginated** (`GET /api/erp/approval/requests`,
`GET /api/erp/approval/inbox`) supplies the TRUE cross-page count from a
repository count query — not `data.size()`.

---

# Scope

## In Scope

- `apps/approval-service/.../presentation/dto/ApiEnvelope.java` — `ofList`
  signature gains `long totalElements` (matches masterdata/notification/
  read-model shape + javadoc).
- New `domain/common/PageResult.java` (approval-service) — `record
  PageResult<T>(List<T> content, long totalElements)` + `.map(...)`, mirroring
  masterdata-service's existing type (pure Java, no framework import — domain
  layer boundary rule).
- `domain/request/repository/ApprovalRequestRepository` (outbound port) —
  `findAll` / `findByParticipant` / `findInbox` return `PageResult<ApprovalRequest>`
  instead of `List<ApprovalRequest>`.
- `infrastructure/persistence/jpa/ApprovalRequestJpaRepository` — add the
  matching `count...` query methods (mirrors masterdata's
  `jpa.countFiltered` pattern) alongside the existing `Pageable` finders.
- `infrastructure/persistence/jpa/ApprovalRequestRepositoryImpl` — wire each
  finder's `Pageable` data query + its new count query into a `PageResult`.
- `application/ApprovalApplicationService.list(...)` and `.inbox(...)` —
  return `PageResult<ApprovalSummaryView>` (`.map(ApprovalSummaryView::from)`)
  instead of `List<ApprovalSummaryView>`.
- `presentation/controller/ApprovalRequestController.list(...)` and
  `presentation/controller/ApprovalInboxController.inbox(...)` — pass
  `result.content()` + `result.totalElements()` into `ApiEnvelope.ofList`.
- `presentation/controller/DelegationController.list(...)` — update the call
  site to the new 4-arg `ofList` signature (this endpoint is genuinely
  unpaginated — see Edge Cases — so no behavior change, only a compile-fix).
- New Testcontainers IT coverage: a dataset spanning 2+ pages for both
  `GET /requests` and `GET /inbox`, asserting `totalElements` equals the true
  row count, not the returned page's `data.size()`.

## Out of Scope

- `masterdata-service`, `notification-service`, `read-model-service` — their
  `ApiEnvelope` copies are already correct; do not touch them.
- Any new pagination feature (sort, cursor pagination, filtering) — this is a
  bug fix to an existing documented contract field, not a new capability.
- Adding pagination to `GET /api/erp/approval/delegations` (currently and
  intentionally unpaginated — out of scope per architecture.md § v2.1
  amendment, "the caller's grants" with no `page`/`size` query params
  documented in `approval-api.md`).

---

# Acceptance Criteria

- [ ] `ApiEnvelope.ofList` in approval-service requires an explicit
      `long totalElements` argument (same shape as masterdata-service's copy).
- [ ] `GET /api/erp/approval/requests?page=0&size=<n>` returns
      `meta.totalElements` equal to the TRUE total number of matching rows
      across all pages when the result set spans more than one page (not
      equal to `data.size()`).
- [ ] `GET /api/erp/approval/inbox?page=0&size=<n>` returns the same true-total
      guarantee.
- [ ] `GET /api/erp/approval/delegations` continues to compile and return the
      caller's full (unpaginated) grant list unchanged in behavior.
- [ ] A new Testcontainers integration test seeds 3+ approval requests
      scoped to a single deterministic participant/approver, requests page 0
      with `size` < total, and asserts `meta.totalElements` == the true count
      (3) while `data.size()` == the page size (< 3) — for both the list and
      inbox endpoints.
- [ ] `./gradlew :projects:erp-platform:apps:approval-service:test` is GREEN.
- [ ] No behavior change to `masterdata-service`, `notification-service`,
      `read-model-service`.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (`domain: erp`, `traits: [internal-system, transactional, audit-heavy]`). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/testing-strategy.md`
- `specs/services/approval-service/architecture.md`
- `specs/services/masterdata-service/architecture.md` (reference — correct
  `PageResult` / `ApiEnvelope.ofList` precedent this task mirrors)
- `rules/domains/erp.md`

# Related Skills

- `.claude/skills/backend/` (per `.claude/skills/INDEX.md`)
- `.claude/skills/testing-backend/` (Testcontainers IT pattern)

---

# Related Contracts

- `specs/contracts/http/approval-api.md` § Common shapes → `PageMeta`
  (`{ "page", "size", "totalElements", "timestamp" }` — this task makes the
  implementation conform to the already-documented contract; no contract text
  change required).
- `specs/contracts/http/masterdata-api.md` § PageMeta (reference — the
  already-correct sibling contract text this task's fix aligns approval-service
  behavior with).

---

# Target Service

- `approval-service`

---

# Architecture

Follow:

- `specs/services/approval-service/architecture.md`

Layer path for this fix: `presentation/controller` → `application/` →
`domain/request/repository` (port) → `infrastructure/persistence/jpa` (adapter).
No architecture-style change — Hexagonal Ports & Adapters is unchanged; this
task only widens an existing port signature (`List<T>` → `PageResult<T>`) the
same way masterdata-service's `DepartmentRepository` etc. already do.

---

# Implementation Notes

- Mirror `masterdata-service`'s `DepartmentRepositoryImpl.findAll` pattern
  exactly: one `Pageable`-driven data query + one separate `count...` query,
  combined into a `PageResult`.
- `ApprovalRequestJpaRepository.findAllByTenantId` / `findAllByTenantIdAndStatus`
  / `findByParticipant` / `findByParticipantAndStatus` / `findInboxPending`
  each need a matching `countByTenantId` / `countByTenantIdAndStatus` /
  a `@Query`-based participant count (with/without status) / an inbox count.
- `DelegationController.list(...)` is the one call site that does NOT need a
  new count query — `DelegationApplicationService.listDelegations` has no
  `Pageable` anywhere in its repository path (`findByDelegator` /
  `findByDelegate` / `findByDelegatorOrDelegate` all return the full
  unfiltered `List`), so `data.size()` passed as the explicit `totalElements`
  argument is already the true total. Only the call site's argument list
  changes (3-arg → 4-arg), not its semantics.
- Do not introduce `Page<T>` (Spring Data) into the `domain/` or
  `application/` layer — `domain/` stays framework-free per architecture.md
  Boundary rules; `PageResult<T>` (plain record) is the crossing type, exactly
  like masterdata-service.

---

# Edge Cases

- **`GET /api/erp/approval/delegations` is not truly paginated** — the
  contract (`approval-api.md` § v2.1 amendment) documents no `page`/`size`
  query params for this endpoint; it always returns the caller's complete
  grant list. Before this fix, `ApiEnvelope.ofList(data, 0, data.size())`
  reported a `totalElements` that happened to equal the true total by
  accident (there is no slicing to be wrong about). This task's signature
  change requires updating this call site to pass `data.size()` as the
  explicit 4th argument too — a compile-fix only, not a behavior fix (already
  correct here).
- **Single-page results were accidentally correct before.** Any existing
  approval-service list/inbox response whose true result set is `<= size`
  (fits on page 0) had `data.size() == totalElements` even under the old buggy
  code — this bug is invisible until a caller's result set spans 2+ pages.
  This is why the new IT must seed **3+ rows with a page `size` < 3** to
  actually exercise the fix (a 1-page fixture would pass under both the old
  buggy code and the fix, proving nothing).
- **No existing test currently pins the buggy value** — a repo-wide search
  found no existing unit/slice/integration test asserting
  `meta.totalElements` in approval-service, so there is no pre-existing
  incorrect assertion to correct; the new IT is purely additive.
- **Response bytes change for any real multi-page approval-service list/inbox
  caller** — a caller who was silently relying on the old (wrong)
  `totalElements == data.size()` value will now see the true total. This is a
  bug fix restoring documented contract behavior, not a contract-breaking
  change (the contract has always documented the true-total semantics).

---

# Failure Scenarios

- If the count query and the data query diverge (e.g. one applies a filter
  the other omits), `totalElements` could disagree with the actual number of
  rows retrievable by paging through — mirror masterdata's pattern exactly
  (same predicate in both queries) to avoid this.
- If a future call site is added to `ApiEnvelope.ofList` without a real
  cross-page count (e.g. reusing `data.size()` again), the bug reappears —
  the new `long totalElements` signature makes this a compile-time-visible
  choice (the caller must supply a value) rather than a silent default, but
  reviewers should still check that the supplied value is not itself
  `data.size()` in disguise for a genuinely paginated query.

---

# Test Requirements

- Integration test (Testcontainers MySQL, authoritative — `H2 forbidden` per
  architecture.md): seed 3 approval requests scoped to one deterministic
  participant (submitter or approver id unique to the test, to stay
  independent of any other test's rows in the shared container), request page
  0 with `size=2`, assert `data` has 2 elements AND `meta.totalElements == 3`
  — for both `GET /api/erp/approval/requests?role=APPROVER` and
  `GET /api/erp/approval/inbox`.
- Existing test suite (`ApprovalLifecycleIntegrationTest`,
  `DelegationIntegrationTest`, slice tests, unit tests) must remain GREEN
  unchanged — this is a net-new-plus-signature-widening change, not a
  behavior change to any other endpoint.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed (not needed — implementation now conforms to
      already-documented `approval-api.md` § PageMeta)
- [ ] Specs updated first if required (not required)
- [ ] Ready for review
