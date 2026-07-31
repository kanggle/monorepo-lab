# Task ID

TASK-FAN-BE-043

# Title

ADR-MONO-058 D3 (fan-platform only) — adopt `libs/java-common.PageResult`/`PageQuery` in place of
community-service's, artist-service's, and notification-service's hand-rolled pagination carriers

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

Close fan-platform's share of `ADR-MONO-058 § D3` (ACCEPTED 2026-07-30). `§ D3`: "No new type. This is
purely an adoption gap — the lib type is frequently already on the consuming service's classpath (used for
`UuidV7`) and simply never imported for paging. Wire-shape divergences already exist in the wild (`content`
vs `items` field naming; some hand-rolled shapes omit `totalPages`) — services adopting the shared type
should treat this as an opportunity to fix those inconsistencies, not preserve them by wrapping the shared
type in another local record."

No new shared code — `libs/java-common.PageResult<T>` (`content, page, size, totalElements, totalPages`,
plus a `map()` transform) and `PageQuery` (`page, size, sortBy, sortDirection`, with `MAX_SIZE=100`
validation) already exist and are **already a declared dependency** (`libs:java-common`) in every
fan-platform servlet service's `build.gradle`.

---

## Measured against the tree — what is actually duplicated, and which services are actually affected

Grep for hand-rolled page/pagination carriers across `projects/fan-platform/apps` found **three** distinct
local shapes, in **three** services — not four:

| Service | Local type(s) | Shape | Divergence from `libs/java-common.PageResult` |
|---|---|---|---|
| `community-service` | `domain/post/PageResult<T>` (framework-free domain record) **and** `application/FeedPage` (application-layer record) | Both: `content, page, size, totalElements, totalPages` — `FeedPage` additionally carries a precomputed `boolean hasNext` | Field-for-field identical to the shared type except the extra `hasNext` boolean. **Two separate local types for the same concept in one service** — an additional duplication the ADR's cross-project paraphrase didn't call out, found by reading the actual tree. |
| `artist-service` | `adapter/in/web/dto/response/PageMeta` | A `static Map<String,Object> of(page,size,totalElements,totalPages)` helper (not a record — builds the `meta` block directly) | Same four fields, but expressed as a `Map` builder instead of a typed carrier. `artist-api.md` already documents the wire shape `{timestamp, page, size, totalElements, totalPages}` — matches `PageResult`'s fields exactly. |
| `notification-service` | `domain/notification/NotificationPage` | `content, page, size, totalElements` — **no `totalPages` field** | This is the exact "some hand-rolled shapes omit `totalPages`" divergence `§ D3` names by example. Traced end-to-end: `ApiEnvelope.ofList(data, page, size, totalElements)` also has no `totalPages` parameter — the wire response genuinely lacks the field today, not just the domain type. |

`membership-service` is **not affected** — grep confirms no hand-rolled `Page`/`PageResult`-shaped carrier
type exists there; its only pagination-adjacent usage is Spring Data `Pageable` consumed internally by two
JPA repository methods (`findActiveExpiringBefore`, `MembershipOutboxJpaRepository.findPending`), neither of
which backs a public paginated list endpoint. `MembershipController` has no `GET .../memberships?page=`
list-with-paging-metadata endpoint. **Do not add pagination scaffolding to membership-service — there is
no adoption gap there to close**, only an internal batch-size cap unrelated to D3.

`gateway-service` is a pure edge proxy — no pagination carrier, untouched.

---

# Scope

## In Scope

### `community-service`

- Collapse **both** local shapes (`domain.post.PageResult<T>` and `application.FeedPage`) into a single
  use of `com.example.common.page.PageResult<T>` at the layer boundary where the domain-layer type is
  currently used (the domain repository port `PostRepository`), so the application layer's `GetFeedUseCase`
  works with `com.example.common.page.PageResult<FeedItemView>` directly instead of mapping into a second,
  field-identical `FeedPage` record. Preserve the `hasNext()` convenience — either as a method on a thin
  service-owned wrapper, or compute it at the one remaining call site (`page + 1 < totalPages`, exactly
  `FeedPage`'s existing derivation) — do not lose the convenience silently.
- Verify `domain.post.PageResult`'s framework-free constraint (`architecture.md`'s "keeps the domain layer
  independent of Spring Data" reasoning) is preserved: `com.example.common.page.PageResult` must itself stay
  framework-free (it does — `java.util.List` + `java.util.function.Function` only) before wiring it into the
  domain repository port's return type.
- `FeedResponse` (the wire DTO) keeps its current field names/shape (`content, page, size, totalElements,
  totalPages, hasNext`) — this is a call-site mapping change, not a contract change; `community-api.md`
  needs no edit if the wire shape is unchanged (verify, don't assume).

### `artist-service`

- Rewrite `ArtistDirectoryService`'s pagination-meta construction to source `page`/`size`/`totalElements`/
  `totalPages` from a `com.example.common.page.PageResult<T>` value returned by the repository port
  (`ArtistRepository`), instead of the current ad hoc int/long parameters passed into `PageMeta.of(...)`.
  `PageMeta.of(...)` may stay as the `Map`-building helper (its wire-shape role), now reading its four values
  off the shared type rather than off separately-threaded method parameters — or be replaced by a direct
  reference into `com.example.common.page.PageResult`'s fields, whichever keeps `ArtistDirectoryController`'s
  response body byte-identical (verify against `artist-api.md`'s documented example response).
- No contract change expected — `artist-api.md` already documents exactly `PageResult`'s field set; this is
  a call-site simplification, not a wire-shape change.

### `notification-service`

- Replace `domain.notification.NotificationPage` with `com.example.common.page.PageResult<Notification>`.
  Per `§ D3`'s explicit instruction, this is "an opportunity to fix" the missing-`totalPages` inconsistency,
  **not** to preserve it by wrapping the shared type in another local record that also omits the field.
- This **is** an additive wire-contract change: `NotificationInboxController`'s
  `ApiEnvelope.ofList(data, page, size, totalElements)` call must gain a `totalPages` argument, and
  `ApiEnvelope.ofList(...)`'s own signature must be extended to accept and emit it in the `meta` map. The
  `GET /api/fan/notifications` response's `meta` block gains a new field (`data`/existing `meta` fields
  unchanged — additive, non-breaking per `platform/error-handling.md`'s "permitted to extend" precedent
  `TASK-FAN-BE-038` already established for this project).
- **Document the contract change**: no `notification-api.md` exists under
  `projects/fan-platform/specs/contracts/http/` today (confirmed by directory listing — only
  `artist-api.md`, `community-api.md`, `membership-api.md` exist) — this task must either create a minimal
  `notification-api.md` documenting the `GET /api/fan/notifications` response shape (including the new
  `totalPages` field), or, if the notification inbox's HTTP shape is intentionally documented elsewhere
  (check `specs/services/notification-service/architecture.md` and any cross-domain aggregator contract
  referenced there, e.g. the `notification-inbox-contract.md` mentioned in
  `NotificationInboxController`'s own code comment — locate its actual path first), extend that file
  instead. `CLAUDE.md`: "Specs win over tasks. If implementation requires spec or contract changes, update
  them first."
- Verify this addition does not break `TASK-FAN-BE-023`'s ADR-MONO-043 REST-shape conformance work — that
  task's contract additions (`sourceDomain`, `deepLink`, `unread`) are unrelated fields; confirm no
  collision, and confirm the aggregator contract (if `notification-inbox-contract.md` governs the shared
  cross-domain shape) doesn't forbid additive fields.

### All three services

- One PR (`refactor(lib)` is not the right scope here since `libs/java-common` is unmodified — use
  `refactor(fan)` or equivalent per-project scope; no shared-path file changes in this task).

## Out of Scope

- **`membership-service`.** No hand-rolled pagination carrier exists there (verified above) — do not add
  one, do not touch `Pageable` usage in `MembershipJpaRepository`/`MembershipOutboxJpaRepository`/
  `BillingKeyEnrollmentJpaRepository` (internal batch-size caps, unrelated to D3's public-pagination
  concern).
- **`gateway-service`.** No pagination carrier.
- **Every other project.** finance/erp/scm/wms/ecommerce carry the same D3 gap per `§ 1.1`'s audit table
  (`content` vs `items` naming divergence, `totalPages` omission) — their adoption is separate future work.
  `§ 6`: one project, one PR.
- **`libs/java-common.PageResult`/`PageQuery` themselves.** No new shared code (`§ D3`'s own text: "No new
  type"). If implementation finds the shared type genuinely cannot represent a service's current shape
  without a wire-visible change, that is itself a finding to report, not a reason to fork the shared type.
- **`PageQuery` adoption on the request side.** `§ D3`'s title and body name the response carrier
  (`PageResult`) as the primary target; none of the three services currently validates `page`/`size` through
  a shared request-side type (each does its own inline `page < 0` / `size > MAX_SIZE` checks). Adopting
  `PageQuery` for request validation is a reasonable follow-up but is **not required** by this task —
  implementer's judgment whether to fold it in atomically or leave a follow-up note; do not let it block the
  `PageResult` adoption.

---

# Acceptance Criteria

- [ ] `community-service`: `application.FeedPage` deleted; `GetFeedUseCase`/`PostRepository` work in terms
      of `com.example.common.page.PageResult<T>`. `FeedResponse`'s wire shape (field names, types, JSON
      output) verified byte-identical before/after by a contract-level test.
- [ ] `artist-service`: `ArtistDirectoryService`'s meta construction sources its four values from a
      `com.example.common.page.PageResult<T>` returned by `ArtistRepository`. `artist-api.md`'s documented
      example response for `GET /api/artists?...` verified byte-identical before/after.
- [ ] `notification-service`: `domain.notification.NotificationPage` replaced by
      `com.example.common.page.PageResult<Notification>`. `ApiEnvelope.ofList(...)` extended to include
      `totalPages` in the `meta` block. The contract documenting `GET /api/fan/notifications`'s response
      shape is updated (new file or extension of an existing one, located per the Scope note) **before or
      in the same PR as** the code change, per `CLAUDE.md`'s "specs win over tasks."
- [ ] Repo-wide grep for `record NotificationPage`, `record FeedPage`, and the domain-layer
      `community…domain.post.PageResult` class definitions → **0 hits** after this task (the community
      domain `PageResult` name may be kept only if it becomes a direct type-alias/re-export of the shared
      type rather than a second field-identical record — verify which, don't assume).
- [ ] `membership-service` untouched — `git diff --stat` for this task shows **zero** files under
      `projects/fan-platform/apps/membership-service/`.
- [ ] No `build.gradle` gains a new dependency (`libs:java-common` already declared in all four servlet
      services).
- [ ] Test-count parity recorded per service (before/after); no test lost.
- [ ] `./gradlew :community-service:check :artist-service:check :notification-service:check` GREEN. CI
      `Integration (fan-platform, Testcontainers)` lane GREEN — authoritative (local Windows Docker is not,
      `project_testcontainers_docker_desktop_blocker`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D3, § 1.1, § 6 item 4
  (ACCEPTED 2026-07-30)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the tracking task this splits from)
- `libs/java-common/src/main/java/com/example/common/page/PageResult.java`,
  `…/page/PageQuery.java` (the shared types — read in full before implementing)
- `platform/shared-library-policy.md` § Decision Rule
- `platform/error-handling.md` § permitted-to-extend precedent (for the notification `totalPages` addition)
- `projects/fan-platform/specs/contracts/http/artist-api.md` § pagination meta shape (already documents the
  target shape)
- `projects/fan-platform/specs/contracts/http/community-api.md`
- `projects/fan-platform/specs/services/notification-service/architecture.md` § Idempotency /
  the inbox endpoint's current documented shape (locate before editing)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-023-notification-inbox-shape-conformance.md` (the prior
  ADR-MONO-043 conformance work on this same endpoint's response shape — confirm no collision)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`,
  `…/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md`,
  `…/TASK-FAN-BE-040-adr058-d1-actor-jwt-claim-cluster.md` — prior art for this project's `ADR-MONO-058`
  adoption-task governance shape (before/after test-count table, guard mutation-check, explicit statement
  of observable-behaviour deltas — including, per `TASK-FAN-BE-038`, how this project has previously
  documented an intentional wire-shape change resulting from an ADR-058 adoption)

---

# Related Contracts

- `projects/fan-platform/specs/contracts/http/artist-api.md` — read-only input for `artist-service` (shape
  already matches target; verify, don't edit unless a mismatch is found)
- `projects/fan-platform/specs/contracts/http/community-api.md` — read-only input for `community-service`
  (verify `FeedResponse`'s documented shape, if any, matches post-change output)
- **New or extended**: whichever spec documents `notification-service`'s `GET /api/fan/notifications`
  response shape — must be updated additively (new `totalPages` field) as part of this task, before/with
  the code change

---

# Target Service

- `community-service`, `artist-service`, `notification-service` (fan-platform)
- `membership-service`, `gateway-service` — explicitly untouched (see Scope)
- Consumes `libs/java-common` (already a declared dependency in all four servlet services; not modified by
  this task)

---

# Architecture

Follow each target service's own `architecture.md`. No layer moves: community's pagination carrier stays at
the domain-repository-port boundary (Layered convention); artist's stays in the `adapter/in/web` response
layer (Hexagonal convention — `PageMeta` is already an adapter-layer construct); notification's stays in
the `domain` package boundary between `NotificationRepository` and the presentation layer. Only the concrete
type backing each boundary changes (local record/Map-builder → `com.example.common.page.PageResult`).

---

# Implementation Notes

- Order of work that keeps the diff reviewable and isolates the one genuinely wire-visible change:
  (1) `artist-service` first — no wire-shape change, smallest risk, proves the shared type slots cleanly
  into a repository-port return type; (2) `community-service` second — collapses two local shapes into one,
  still no wire-shape change, slightly larger diff (two files deleted/merged instead of one); (3)
  `notification-service` last, specifically because it is the one with an actual additive wire-contract
  change — do this after the other two are green so the PR's "what changed" summary can isolate the one
  behavioral delta clearly.
- For `notification-service`, locate `notification-inbox-contract.md` (referenced in
  `NotificationInboxController`'s own javadoc comment, "notification-inbox-contract.md § 2.1") before
  deciding whether a new `notification-api.md` is needed or an existing shared/cross-domain contract file
  should be extended instead — grepping `projects/fan-platform/specs` alone did not locate this file during
  this task's own investigation, so it may live outside `projects/fan-platform/` (a cross-project aggregator
  contract, per `TASK-FAN-BE-023`'s ADR-MONO-043 reference) — resolve this before writing the contract
  addition, don't guess a path.
- `com.example.common.page.PageResult.map(Function<T,R>)` is directly useful for the
  domain-entity-to-response-DTO mapping step at each of the three call sites (e.g.
  `pageResult.map(FeedItemResponse::from)`) — prefer it over hand-writing the same stream-map-collect
  pattern three times.

---

# Edge Cases

- **`hasNext` convenience (community-service only).** `FeedPage.hasNext()` and the domain
  `PageResult.hasNext()` both derive `page + 1 < totalPages`. `com.example.common.page.PageResult` has no
  such method. Decide where this derivation now lives (call site, or a thin service-owned extension) — do
  not silently drop `FeedResponse.hasNext` from the wire response (that would be a contract-breaking
  removal, not a promotion).
- **`totalPages` addition to notification's wire response (the one intentional behavioral delta).** Confirm
  no existing consumer (fan-platform-web's `features/notification` — check
  `projects/fan-platform/apps/fan-platform-web`) parses the `meta` object in a way that would break on an
  unexpected additional key (unlikely with typical JSON parsing, but verify rather than assume, especially
  if any FE type uses a strict/exact-shape validator).
- **`artist-service`'s `PageMeta.of(...)` timestamp field.** `PageMeta` also emits a `timestamp` field not
  present in `libs/java-common.PageResult`. That field is unrelated to D3 (it's an envelope-level concern,
  matching `ApiEnvelope`'s pattern in other services) and must be preserved — `PageResult` supplies the four
  paging fields only, `timestamp` continues to be added at the meta-block-construction call site.

---

# Failure Scenarios

- **Silent wire-shape change in `artist-service`/`community-service`.** Both are expected to be
  zero-wire-delta promotions (matching `artist-api.md`'s already-documented shape and, presumably,
  `community-api.md`'s). If implementation finds either actually differs once the shared type's exact
  field order/naming is wired through, that is a genuine contract question — stop and update the contract
  first per `CLAUDE.md`, do not silently ship a changed response shape.
- **Documenting the `notification-service` field addition after the fact instead of before/with.**
  `CLAUDE.md`: "Specs win over tasks. If implementation requires spec or contract changes, update them
  first." Shipping the `totalPages` field without a contract update (or without locating and extending
  whatever aggregator contract already governs this endpoint) would repeat the exact "declaration outlives
  the truth" failure class this repo already documents for stale ADR/task status brackets.
- **Reintroducing a second local wrapper "to be safe."** The ADR is explicit: "not preserve them by wrapping
  the shared type in another local record." A `NotificationPage` that now merely delegates to
  `com.example.common.page.PageResult` internally, instead of being deleted and replaced by direct use of
  the shared type, would technically compile but defeat the point of the adoption (still N local types
  across the fleet, just one layer thinner).
- **Touching `membership-service` "for consistency."** No adoption gap exists there — adding a pagination
  carrier where none was needed would be unrequested scope and an unjustified diff.

---

# Test Requirements

- `community-service`: `GetFeedUseCaseTest`, `FeedQueryIntegrationTest`, `FeedPremiumGateIntegrationTest`,
  `CommunityApiContractTest` — updated for the type change, assertions on `FeedResponse`'s wire shape
  preserved unmodified (the regression net for "no wire-shape change").
- `artist-service`: `ArtistDirectoryControllerSliceTest` — updated for the type change, assertions on the
  documented `meta` shape preserved unmodified.
- `notification-service`: a new/updated test asserting `meta.totalPages` is present and correct in the
  `GET /api/fan/notifications` response (the one intentional new assertion), plus existing
  `InboxApiIntegrationTest`/slice tests passing with all pre-existing assertions unmodified.
- Before/after test-count table per service, 0 failures/errors/skipped both sides.
- `./gradlew :community-service:check :artist-service:check :notification-service:check` GREEN. CI
  `Integration (fan-platform, Testcontainers)` GREEN authoritative.

---

# Definition of Done

- [ ] Three services' hand-rolled pagination carriers replaced by `com.example.common.page.PageResult`
- [ ] `community-service`'s two local shapes collapsed to one shared-type usage
- [ ] `artist-service`/`community-service` wire shapes verified byte-identical (no contract edit needed,
      confirmed not assumed)
- [ ] `notification-service`'s `totalPages` addition documented in a contract (new or extended file) before
      or with the code change
- [ ] `membership-service`/`gateway-service` confirmed untouched (zero diff)
- [ ] Test-count parity recorded; three services' `:check` + CI Integration lane GREEN
- [ ] Ready for review
