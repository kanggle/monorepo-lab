# Task ID

TASK-BE-025

# Title

inventory-service bootstrap — fill DLT routing test, JWT 401 test, audit Redis key prefix

# Status

review

# Owner

backend

# Task Tags

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

# Goal

Close three gaps found in the TASK-BE-021 review (verdict 2026-04-28 FIX NEEDED).
The bootstrap implementation is otherwise correct; this task only adds the
two missing tests and reconciles one subtle Redis-key prefix mismatch:

1. **DLT routing Testcontainers test (AC-11 unmet).** `architecture.md` §Event
   Consumption and `idempotency.md:215` mandate that a non-parseable / poison
   record on `wms.master.location.v1` lands on the matching `.DLT` topic after
   the configured retries. No test exercises this path today.
2. **`@WebMvcTest`/smoke 401-on-missing-JWT test (AC-13 unmet).** The current
   smoke only asserts `SecurityFilterChain` bean presence; the AC explicitly
   requires a test that issues an unauthenticated request and asserts 401.
3. **Redis idempotency-key prefix audit.** `idempotency.md:49` declares the
   canonical storage key as `inventory:idempotency:{method}:{path_hash}:{key}`
   while `RedisIdempotencyStore.java:20–21` prepends `inventory:idem:` inside
   the adapter. Confirm whether this is a real defect (filter callers will
   double-prefix to `inventory:idem:inventory:idempotency:…`) or already
   reconciled by callers in BE-022/BE-023, and bring the code into spec
   conformance.

---

# Scope

## In Scope

- New Testcontainers Kafka test that publishes a non-deserialisable record to
  `wms.master.location.v1` and asserts:
  - the `MasterLocationConsumer` does not commit the offset for that record
    (or commits with retry exhausted)
  - after exactly 3 attempts (per `KafkaConsumerConfig` retry settings) the
    record appears on `wms.master.location.v1.DLT`
  - the DLT record's headers retain original `topic`, `partition`, `offset`,
    `exception-class`, `exception-message`
- One `@WebMvcTest` (or equivalent slice test) on a representative inventory
  endpoint asserting:
  - request without `Authorization` header → 401
  - request with malformed bearer → 401 (or 403 per `SecurityConfig` mapping —
    document whichever the spec mandates and assert that)
- Audit `RedisIdempotencyStore` prefix vs `IdempotencyStore` port Javadoc and
  the `idempotency.md` canonical pattern. Either:
  - confirm callers (filter / interceptor in later tasks) construct the full
    `inventory:idempotency:…` key and pass it as `storageKey` (in which case
    the adapter prefix is double — fix by removing the adapter prefix and
    documenting that the port treats `storageKey` as the literal Redis key);
    OR
  - confirm callers pass only the suffix `{method}:{path_hash}:{key}` (in
    which case rename the adapter prefix from `inventory:idem:` to
    `inventory:idempotency:` to match the spec).
- Add a unit test on `RedisIdempotencyStore` that pins down the final Redis
  key shape so future drift is caught.

## Out of Scope

- Any change to the consumer retry semantics (3 retries, exponential backoff
  with jitter is correct per spec).
- Any change to the JWT issuer / `oauth2ResourceServer` configuration.
- The actual `IdempotencyFilter` HTTP integration (still owned by future
  tasks if not already wired in BE-022/BE-023).

---

# Acceptance Criteria

- [ ] New Testcontainers Kafka test exists at
      `apps/inventory-service/src/test/java/com/wms/inventory/integration/MasterLocationDltRoutingIntegrationTest.java`
      (or similar) and passes; covers the poison-record → DLT path with
      attempt count assertion.
- [ ] DLT topic name asserted is `wms.master.location.v1.DLT`; if Spring
      Kafka's default suffix differs in the project's config, the assertion
      reflects the actual configured suffix.
- [ ] New 401-on-missing-JWT test exists (slice test or smoke test) and
      passes; asserts the precise status the `SecurityConfig` is documented
      to return.
- [ ] `RedisIdempotencyStore` final Redis key shape is conformant with
      `idempotency.md:49`. The choice (adapter-prefixes vs caller-prefixes)
      is documented in the adapter's class-level Javadoc; existing call sites
      (if any) updated accordingly; new pinning unit test passes.
- [ ] `./gradlew :apps:inventory-service:check` passes locally and on CI.
- [ ] Original BE-021 INDEX.md entry remains untouched (per the no-edit-after-
      done rule); review notes in this task summarise the before/after state.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md` and `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`.

- `platform/error-handling.md`
- `specs/services/inventory-service/architecture.md` — §Event Consumption (DLT), §Security
- `specs/services/inventory-service/idempotency.md` — §Storage (key shape on line ~49) + §Consumer Failure Handling (DLT on line ~215)

# Related Skills

- `.claude/skills/backend/messaging/kafka-consumer-dlt`
- `.claude/skills/backend/security/spring-jwt-resource-server`

---

# Related Contracts

- `specs/contracts/events/inventory-events.md` — for envelope shape / error mode

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md`

---

# Implementation Notes

- The poison-record test must publish raw bytes that fail deserialisation in
  the consumer factory — sending a JSON object that simply fails schema
  validation in the handler is not sufficient because retry semantics for
  deserialisation errors and handler errors differ.
- For the DLT topic name, prefer reading the `DefaultErrorHandler` /
  `DeadLetterPublishingRecoverer` configuration directly rather than
  hard-coding the suffix.
- Keep the slice test minimal — one `@RestController` endpoint exercised with
  `MockMvc` is enough; the goal is to prove the security chain rejects
  unauthenticated requests, not to exercise business logic.
- For the Redis prefix audit, search the codebase for every
  `IdempotencyStore.put(` and `IdempotencyStore.lookup(` call site; if no
  callers exist yet (the filter is not implemented), treat this as a
  pre-emptive fix and align the adapter prefix with the spec rather than
  forcing future callers to compensate.

---

# Edge Cases

- DLT publish itself fails (broker down): error handler logs and rethrows;
  test should cover the happy path only — failure-mode of DLT publish is a
  separate concern.
- 401 vs 403 ambiguity: Spring Security defaults to 403 for missing `WWW-
  Authenticate` resolution; the AC must reflect what `SecurityConfig`
  actually returns, not the conventional answer.
- Redis adapter test: use Testcontainers Redis or in-process mock — pin the
  literal key shape, do not just assert prefix.

---

# Failure Scenarios

- Test infrastructure (Docker / Testcontainers) flakiness: existing project
  pattern uses Awaitility with `atMost(30s)` and `pollInterval(500ms)` — keep
  to that pattern.
- DLT topic missing in test config: assert and fail loudly; do not auto-create
  the DLT topic implicitly via the test (production config governs auto-create
  policy).

---

# Test Requirements

- One Testcontainers Kafka integration test (DLT routing).
- One slice or smoke test (JWT 401 path).
- One unit test on `RedisIdempotencyStore` pinning the final Redis key shape.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (3)
- [ ] Tests passing locally and in CI
- [ ] `RedisIdempotencyStore` Javadoc explains the prefix policy
- [ ] PR description references TASK-BE-021 review verdict
- [ ] Ready for review
