# Task ID

TASK-SCM-BE-056

# Title

Adopt ADR-MONO-058 D3 — pagination carrier (`libs/java-common.PageResult`/`PageQuery`)

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

`ADR-MONO-058` (ACCEPTED 2026-07-30) § 2 D3 found the fleet re-declaring hand-rolled pagination shapes instead of importing the already-shared `libs/java-common.PageResult`/`PageQuery` (frequently already on the classpath for `UuidV7`, just never imported for paging). Close this adoption gap for scm-platform's services, and — per the ADR's own instruction — treat the wire-shape divergences found as an opportunity to fix them, not preserve them by wrapping the shared type in yet another local record.

---

# Scope

## In Scope

Grep across `projects/scm-platform/apps/` (2026-07-31) found three distinct pagination shapes, not two:

| Service | Current shape | Sources `PageResult`/`PageQuery` today? |
|---|---|---|
| `procurement-service` | `PageResponse<T>(content, page, size, totalElements, totalPages)` — presentation-layer wrapper; `.from(PageResult<T>)` factory | **Yes, already end-to-end**: `infrastructure/persistence/jpa/PageRequests.toPageable(PageQuery)` on the request side, `presentation/dto/PageResponse.from(PageResult<T>)` on the response side. |
| `inventory-visibility-service` | `PageResponse<T>(content, page, size, totalElements)` — **no `totalPages` field** | No — fully hand-rolled, no `com.example.common.page` import. |
| `demand-planning-service` | Split envelope: `ApiEnvelope.of(data: List<T>, meta: PageMeta)` where `PageMeta(page, size, totalElements, totalPages)` is a **separate top-level object**, not embedded content — a structurally different shape (data/meta split), not just field-naming variance. | No — built directly from Spring Data's `Page<T>` (`result.getNumber()`/`getSize()`/`getTotalElements()`/`getTotalPages()`), no `com.example.common.page` import. |
| `logistics-service` | No pagination usage found (grep for `Page`/`paginat` returned zero files) | N/A — no paginated endpoint exists. |

Work per service:

- **`procurement-service`**: already fully wired through `PageQuery`/`PageResult` (`PageRequests` at the JPA boundary, `PageResponse.from(PageResult)` at the wire boundary — see `TASK-SCM-BE-016`'s L5 sort-preservation fix, still intact). This is largely a **verification pass**, not a migration: confirm the existing wiring is genuinely complete (no remaining hand-rolled path bypassing `PageQuery`/`PageResult`), and — as a minor, optional cleanup only if trivial — consider whether the local `presentation/dto/PageResponse` wrapper is still pulling its weight versus serializing `PageResult` directly. Do not force a change here if the wrapper exists for a documented reason (check `architecture.md` first).
- **`inventory-visibility-service`**: migrate the hand-rolled `PageResponse` to source from `PageResult` at the wire boundary and `PageQuery` at the request boundary (mirroring `procurement-service`'s already-proven pattern). This **adds** the currently-missing `totalPages` field — a wire-shape fix, and a published-contract change (see Related Contracts).
- **`demand-planning-service`**: migrate `SuggestionController.listSuggestions` off direct `Page<ReorderSuggestion>` field extraction to source from `PageQuery`/`PageResult`. Decide whether to preserve the existing `data`/`meta`-split `ApiEnvelope` wire shape (a different but internally-consistent envelope convention already used across `demand-planning-service`'s other endpoints — check `ApiEnvelope`'s other call sites before assuming this is duplication rather than a deliberate service-wide envelope convention) or normalize to the single-object `content`/`page`/`size`/`totalElements`/`totalPages` shape used by `procurement`/`inventory-visibility`. This is a wire-shape design decision this task must record, analogous to `TASK-SCM-BE-055`'s D2 `details`-field call — the ADR instructs fixing divergence, not which specific shape wins, and `demand-planning-service`'s `ApiEnvelope` convention may be load-bearing for its other (non-paginated) endpoints.

## Out of Scope

- `logistics-service` — confirmed no pagination usage exists; nothing to adopt.
- `gateway-service` — no domain data of its own to paginate (edge routing only).
- Any change to `libs/java-common.PageResult`/`PageQuery` themselves — already shared, no new type needed (this is a pure adoption task per the ADR).
- `demand-planning-service`'s `ApiEnvelope` wrapper for **non-paginated** responses (`getSuggestion`, `approve`, `dismiss`) — unrelated to pagination, untouched.
- `TASK-SCM-BE-052` (destination-addressing seam, blocked in `backlog/`) — unrelated.

---

# Acceptance Criteria

- [x] `procurement-service`'s existing `PageQuery`/`PageResult` wiring verified complete end-to-end (request → JPA → response); any gap found is closed as part of this task, not deferred. — **Evidence**: read `PurchaseOrderController.search` → `PurchaseOrderApplicationService.search` → `PurchaseOrderRepository.search(PageQuery)` (domain port) → `PurchaseOrderRepositoryImpl` (`PageRequests.toPageable` + builds `PageResult` directly from the JPA `Page`) → `PageResponse.from(PageResult)` at the wire boundary. No hand-rolled path found bypassing `PageQuery`/`PageResult`. No gap to close — genuinely a verification-only pass, zero production files changed in `procurement-service`.
- [x] `inventory-visibility-service`'s pagination sourced from `PageQuery`/`PageResult`; response now includes `totalPages` (previously missing). — **Evidence**: `InventorySnapshotRepository.search(String, PageQuery)` (new domain port method, replaces hand-rolled `findAll(tenantId,page,size)`+`countAll(tenantId)`), `InventorySnapshotRepositoryImpl` (+ new `adapter/outbound/persistence/jpa/PageRequests`) builds `PageResult` from a single `Page<InventorySnapshotJpaEntity>` query (was 2 separate queries), `InventoryVisibilityController#getSnapshot` builds `PageQuery.of(page, size, null, null)` and unwraps via `PageResponse.from(PageResult)` (`adapter/inbound/web/dto/PageResponse`, now carries `totalPages`).
- [x] `demand-planning-service`'s pagination sourced from `PageQuery`/`PageResult`; the `data`/`meta`-split-vs-single-object wire-shape decision is explicitly recorded in the PR description with rationale before implementation, consistent with whichever shape is chosen for `demand-planning-service`'s other endpoints' conventions. — **Evidence**: decision recorded in `specs/contracts/http/demand-planning-api.md` (§ wire-shape decision note) and in the PR description — **preserve** the `data`/`meta` split (confirmed load-bearing: `PolicyController`'s `getPolicy`/`upsertPolicy`/`getMapping`/`upsertMapping` all use the same `ApiEnvelope.of(data)` convention). `ReorderSuggestionPort.findAll` now takes `PageQuery`/returns `PageResult` (was raw `Pageable`/`Page`), `SuggestionController.listSuggestions` builds `PageQuery` and sources both `data` (content) and `meta` (`PageMeta.from(PageResult)`, new factory) from one `PageResult` — zero wire-byte change, internal-only migration.
- [x] No service's local pagination DTO independently re-declares fields `PageResult` already provides without sourcing from it (the ADR's "opportunity to fix, not preserve by wrapping" instruction). — **Evidence**: `procurement-service.PageResponse.from`, `inventory-visibility-service.PageResponse.from`, and `demand-planning-service.PageMeta.from` all source every field from a `PageResult` instance (no independent field extraction off raw `Page`/`Pageable` remains anywhere in the three services' pagination paths).
- [x] `projects/scm-platform/specs/contracts/http/{inventory-visibility-api,demand-planning-api}.md` updated to reflect any wire-shape change (per `CLAUDE.md` "Specs win over tasks... update them first") — `procurement-api.md` reviewed and updated only if the verification pass finds and fixes a genuine gap. — **Evidence**: `inventory-visibility-api.md` `/snapshot` response example now shows `totalPages` + an explanatory note; `demand-planning-api.md` carries the wire-shape-preserved decision note. `procurement-api.md` reviewed, no gap found, left unchanged.
- [x] Existing pagination-adjacent tests (`PageRequestsTest` in `procurement-service`, and any list-endpoint slice tests in `inventory-visibility-service`/`demand-planning-service`) pass, updated only where the wire shape genuinely changes. — **Evidence**: `procurement-service`'s `PageRequestsTest` untouched, still passing. `inventory-visibility-service`'s `InventoryVisibilityControllerSliceTest`/`ResourceServerChainAuthPathSliceTest` updated for the new `PageQuery`/`PageResult`-returning mock signature (+ a `totalPages` assertion added); `CrossTenantIsolationIntegrationTest`/`ThirdPartyObservedStockIntegrationTest` updated for the same signature change. `demand-planning-service`'s `SuggestionControllerSliceTest` updated likewise (+ `totalPages` assertion). Local `./gradlew :...:test` (Docker-free) GREEN for all three services (see evidence below).
- [ ] scm-platform Build & Test CI lane GREEN for the three touched services. — pending: confirmed after the impl PR's CI run (local `test` task — the Docker-free equivalent — is GREEN for all three services; see Definition of Done note).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D3, § 6 item 4
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (origin)
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (already documents the `PageQuery`/`PageResult` pattern — use as the reference shape for the other two services)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md`
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md`
- `projects/scm-platform/tasks/done/TASK-SCM-BE-016-refactor-sweep.md` (source of procurement's L5 sort-preservation fix in `PageRequests` — do not regress it while touching pagination in the other two services)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md`
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md`
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md`
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` — **platform-console external read consumer** (per `PROJECT.md § IAM IdP Integration`): platform-console-web server-side-consumes scm's procurement PO read + inventory-visibility read surfaces. Both are paginated list endpoints — adding `totalPages` to `inventory-visibility-service`'s response is additive (safe for a consumer that ignores unknown fields) but must still be checked against platform-console's own DTO/parsing code before merging, not assumed safe.

---

# Target Service

- `procurement-service` (verification pass)
- `inventory-visibility-service` (migration)
- `demand-planning-service` (migration + wire-shape decision)
- `logistics-service`, `gateway-service` — explicitly out of scope (no pagination usage found)

---

# Architecture

Follow each touched service's own architecture doc (listed under Related Specs above).

---

# Implementation Notes

- `procurement-service`'s pattern is the reference implementation for this task: `com.example.scmplatform.procurement.infrastructure.persistence.jpa.PageRequests.toPageable(PageQuery)` converts the framework-free `PageQuery` into Spring Data's `PageRequest` (preserving sort field + direction — the `TASK-SCM-BE-016` L5 fix), and `presentation.dto.PageResponse.from(PageResult<T>)` builds the wire response. Mirror this shape in `inventory-visibility-service`.
- `demand-planning-service`'s `SuggestionController.listSuggestions` currently builds `Page<ReorderSuggestion>` via Spring Data directly (`PageRequest.of(page, Math.min(size, 100))`) and hand-extracts `PageMeta` fields from it — introducing `PageQuery`/`PageResult` here means deciding where the `PageQuery` is constructed (controller boundary, same place `page`/`size` request params are currently read) and where `PageResult` is unwrapped back into the existing `ApiEnvelope`/`PageMeta` shape (if that shape is kept) or the single-object shape (if normalized).
- Before deciding `demand-planning-service`'s wire-shape question, grep `ApiEnvelope` usage across `demand-planning-service`'s other controllers — if `data`/`meta` is a deliberate, consistently-applied envelope convention for that service (not just this one endpoint), normalizing away from it would be a broader service-wide contract change beyond this task's pagination scope, and should probably be preserved with `PageResult`/`PageQuery` adopted underneath it rather than changed at the wire level.

---

# Edge Cases

- `procurement-service`'s `size` clamp (`Math.min(size, 100)`, seen identically in `demand-planning-service`'s controller) should be verified consistent across all three services after this task — if one service's clamp value differs or is missing, that's a pre-existing divergence worth flagging in the PR, not silently unified as a side effect of this task.
- If `inventory-visibility-service`'s current hand-rolled response is already consumed by a test or client asserting the **absence** of `totalPages` (unlikely but possible), that assertion must be updated as part of this change — check before assuming purely additive.

---

# Failure Scenarios

- Wrapping `PageResult` in yet another local record that duplicates its fields instead of sourcing from it would violate the ADR's explicit instruction (§ 2 D3: "not preserve them by wrapping the shared type in another local record") — verify the final DTOs source their values from `PageResult`/`PageQuery`, not merely resemble its shape.
- Normalizing `demand-planning-service`'s wire shape without checking whether `data`/`meta` is a deliberate service-wide `ApiEnvelope` convention (used for non-paginated endpoints too) would silently change a broader contract than this task's stated scope — verify before changing.
- Adding `totalPages` to `inventory-visibility-service`'s response without checking platform-console's consuming code would risk an undisclosed (even if additive) contract surface change for the external operator-read consumer.
- Regressing `procurement-service`'s existing sort-preservation fix (`TASK-SCM-BE-016` L5) while mirroring its pattern into `inventory-visibility-service` would reintroduce a bug already fixed once in this exact codebase.

---

# Test Requirements

- `PageRequestsTest` (`procurement-service`) passes unmodified unless the verification pass finds and fixes a genuine gap, in which case it is extended.
- New/updated list-endpoint slice tests for `inventory-visibility-service` and `demand-planning-service` asserting the new wire shape (including `totalPages` presence for inventory-visibility, and the recorded wire-shape decision for demand-planning).

---

# Definition of Done

- [x] `procurement-service` verified fully wired through `PageQuery`/`PageResult` end-to-end — no production changes needed (verification pass only).
- [x] `inventory-visibility-service` migrated, `totalPages` added
- [x] `demand-planning-service` migrated, wire-shape decision recorded and implemented (data/meta split preserved, sourced from `PageResult` underneath)
- [x] Contracts updated for any wire-shape change (`inventory-visibility-api.md`, `demand-planning-api.md`)
- [x] platform-console external-consumer impact checked — read `scm-inventory-visibility-api.ts`/`types.ts`: `SnapshotPageDataSchema` is a plain (non-`.strict()`) zod `z.object()`, so the new `totalPages` field is silently tolerated (unknown-key strip, not reject/throw) — confirmed safe, not assumed.
- [ ] scm-platform Build & Test CI lane GREEN — pending PR CI run. Local `./gradlew :projects:scm-platform:apps:{inventory-visibility-service,demand-planning-service,procurement-service}:test` (Docker-free unit+slice lane) run 2026-08-01: **inventory-visibility-service 126/126 pass, demand-planning-service 92/92 pass, procurement-service 222/222 pass, 0 skipped/failed/errored across all three.** All three modules also `compileJava`/`compileTestJava` clean (no new warnings beyond pre-existing `@MockBean` deprecation noise).
- [ ] Task moved `ready → done`, referencing `TASK-MONO-495` as origin — deferred to the `review → done` chore PR after this impl PR merges with CI GREEN (per `tasks/INDEX.md` § PR Separation Rule).
