# Task ID

TASK-BE-565

# Title

JPA Repository naming convention fix — append `Jpa` to 10 interfaces that extend `JpaRepository` but are named plain `...Repository`

# Status

review

# Owner

backend

# Task Tags

- code
- refactor

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

A naming-convention investigation found a systemic, clear-cut violation of `platform/naming-conventions.md`'s "JPA Repository" row (requires `PascalCase + JpaRepository` for interfaces extending `org.springframework.data.jpa.repository.JpaRepository`) in `projects/ecommerce-microservices-platform`. 34 files in this project already follow the convention correctly (e.g. `UserJpaRepository`), but 10 files directly `extends JpaRepository<...>` while named plain `...Repository` — indistinguishable at a glance from the project's actual `Repository` (domain-port) / `RepositoryImpl` (adapter) pair naming, which is exactly the ambiguity this convention row exists to prevent.

After this task: all 10 listed interfaces are renamed to end in `JpaRepository`, every reference to the old type name (field declarations, constructor parameters, imports, Javadoc/comments where they refer to the type, test doubles) is updated across all 7 affected services, and the codebase compiles and tests pass identically to before the rename (pure mechanical rename — same interface, same extended methods, same behavior).

# Scope

## In Scope

Rename these 10 interfaces (filename + type name), appending `Jpa` immediately before `Repository`:

| # | Service | Old name | New name | Path |
|---|---|---|---|---|
| 1 | order-service | `OrderOutboxRepository` | `OrderOutboxJpaRepository` | `apps/order-service/src/main/java/com/example/order/infrastructure/persistence/` |
| 2 | payment-service | `PaymentOutboxRepository` | `PaymentOutboxJpaRepository` | `apps/payment-service/src/main/java/com/example/payment/adapter/out/event/` |
| 3 | promotion-service | `PromotionOutboxRepository` | `PromotionOutboxJpaRepository` | `apps/promotion-service/src/main/java/com/example/promotion/infrastructure/event/` |
| 4 | settlement-service | `SettlementOutboxRepository` | `SettlementOutboxJpaRepository` | `apps/settlement-service/src/main/java/com/example/settlement/infrastructure/persistence/` |
| 5 | shipping-service | `ShippingOutboxRepository` | `ShippingOutboxJpaRepository` | `apps/shipping-service/src/main/java/com/example/shipping/infrastructure/event/` |
| 6 | review-service | `ReviewOutboxRepository` | `ReviewOutboxJpaRepository` | `apps/review-service/src/main/java/com/example/review/infrastructure/event/` |
| 7 | product-service | `ReservationProcessedEventRepository` | `ReservationProcessedEventJpaRepository` | `apps/product-service/src/main/java/com/example/product/infrastructure/event/` |
| 8 | product-service | `WmsInventoryAvailableRepository` | `WmsInventoryAvailableJpaRepository` | `apps/product-service/src/main/java/com/example/product/infrastructure/reconciliation/` |
| 9 | product-service | `WmsProcessedEventRepository` | `WmsProcessedEventJpaRepository` | `apps/product-service/src/main/java/com/example/product/infrastructure/reconciliation/` |
| 10 | product-service | `WmsSkuSnapshotRepository` | `WmsSkuSnapshotJpaRepository` | `apps/product-service/src/main/java/com/example/product/infrastructure/reconciliation/` |

- Update every reference to the old type names in `main` and `test` sources of the 7 affected services (order-service, payment-service, promotion-service, settlement-service, shipping-service, review-service, product-service): field declarations, constructor injection parameters, imports, `@MockBean`/Mockito `@Mock` declarations, any Javadoc/comment mentioning the old type by name.
- Rename the physical files to match the new type names (`git mv` preferred to preserve history).

## Out of Scope

- Renaming the 34 files that already correctly follow the `JpaRepository` convention — no change needed.
- Renaming domain-port `Repository` interfaces or their `RepositoryImpl` adapters that do **not** directly extend `JpaRepository` (e.g. hexagonal outbound ports) — those correctly follow the plain `Repository`/`RepositoryImpl` rows of the convention table and are unaffected.
- Any behavioral change — method signatures, query semantics, transaction boundaries are unchanged.
- Package moves — files stay in their current package, only the simple name changes.
- `auth-service` (RETIRED, TASK-BE-132 — excluded from `settings.gradle`, out of build scope).

# Acceptance Criteria

- [ ] **AC-1** All 10 files renamed (filename + `interface` declaration) exactly per the mapping table above.
- [ ] **AC-2** Zero remaining references to any of the 10 old type names anywhere under each affected service's `src/main` and `src/test` (verified by exhaustive grep per service, before/after).
- [ ] **AC-3** Each of the 7 affected services compiles (`compileJava` + `compileTestJava`) with zero errors.
- [ ] **AC-4** Existing unit/slice/integration tests for the 7 affected services pass at the same rate as the pre-change baseline (no new failures introduced by the rename).
- [ ] **AC-5** No behavioral/method-signature change — `git diff` shows only identifier renames (type name, file name, and references), not logic changes.

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (domain=`ecommerce`, traits=`transactional, content-heavy, read-heavy, integration-heavy, multi-tenant`). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/naming-conventions.md` § Java > Classes > "JPA Repository" row (authoritative naming rule for this task)
- `specs/services/order-service/architecture.md`
- `specs/services/payment-service/architecture.md`
- `specs/services/promotion-service/architecture.md`
- `specs/services/settlement-service/architecture.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/review-service/architecture.md`
- `specs/services/product-service/architecture.md`

# Related Skills

- `.claude/skills/backend/` (general backend implementation conventions)

---

# Related Contracts

- N/A — all 10 renamed types are internal persistence-layer implementation details (outbox tables, reconciliation staging tables). No HTTP or event contract references these type names; API/event payload shapes are unaffected.

---

# Target Service

- `order-service`
- `payment-service`
- `promotion-service`
- `settlement-service`
- `shipping-service`
- `review-service`
- `product-service`

---

# Architecture

Follow each affected service's own `specs/services/<service>/architecture.md` for its declared layering (Layered vs Hexagonal) — this task does not change any service's architecture, only aligns interface naming with `platform/naming-conventions.md`.

---

# Implementation Notes

- Pure mechanical rename. Do not touch method bodies, query derivation, transaction annotations, or any other logic.
- Use `git mv` for each file rename to preserve file history where the tool supports it, followed by editing the `interface` declaration name inside.
- After renaming a type, grep the **whole service module** (not just the immediate package) for the old simple name — Spring `@Autowired`/constructor-injection call sites, Mockito test doubles, and Javadoc `{@link}` references can live in unrelated packages.
- Product-service has 3 of the 10 renames in the same `infrastructure/reconciliation` package plus 1 in `infrastructure/event` — verify no name collisions and that all 4 renames are applied together before compiling.
- Run each service's Gradle module build/test independently (`:projects:ecommerce-microservices-platform:apps:<service>:test` or module-equivalent) to isolate any compile failure to the correct service.

---

# Edge Cases

- Old type names that are substrings of other unrelated identifiers in the same service (e.g. `ReviewOutboxRepository` vs `ReviewOutboxPublisher`, `ReviewOutboxRelayIntegrationTest`) — grep must match the exact identifier boundary, not a substring, to avoid over- or under-renaming.
- Test files using Mockito `@Mock`/`@InjectMocks` or manual `mock(OldRepository.class)` calls referencing the old type by class literal.
- Any `@ExtendWith(MockitoExtension.class)` unit test where the outbox/reconciliation repository is a constructor-injected collaborator — the field type and constructor parameter type both need updating.

---

# Failure Scenarios

- A stale reference surviving the rename (e.g. in a test file in a different package than the interface) is caught by `compileTestJava` failing for that module — must be fixed before proceeding, not deferred.
- If a service's baseline tests were already failing before this change (pre-existing unrelated flake/failure), that must be distinguished from a rename-introduced regression by comparing against a pre-change baseline run.

---

# Test Requirements

- Per affected service: `compileJava` + `compileTestJava` GREEN.
- Per affected service: existing unit/slice/integration test suite passes at the same rate as pre-change baseline.
- No new tests required — this is a pure identifier rename with no new behavior to cover.

---

# Definition of Done

- [ ] All 10 files renamed and all references updated across 7 services
- [ ] Exhaustive per-service grep confirms zero remaining references to old names
- [ ] All 7 affected services' builds/tests verified (or full project build if faster)
- [ ] Ready for review
