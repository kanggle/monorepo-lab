# Task ID

TASK-BE-565

# Title

Fix non-atomic `tryAcquireLock` race in `InMemoryIdempotencyStore` (admin / inventory / master)

# Status

done

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

A monorepo-wide audit found that `InMemoryIdempotencyStore` (the `standalone`-profile,
in-memory fallback implementation of `IdempotencyStore`) exists in all 5 wms
services and has forked into two different `tryAcquireLock` implementations:

- `inbound-service` and `outbound-service` use `locks.compute(key, ...)` — a
  single atomic map operation. Correct: the check-and-set happens under the
  per-key lock `ConcurrentHashMap.compute` holds internally, so no other
  thread can observe or mutate the entry mid-check.
- `admin-service`, `inventory-service`, `master-service` use a
  `locks.get(key)` → check → `locks.put(key, ...)` sequence (`inventory-service`
  and `master-service` additionally have a compensating `locks.put(key, prev)`
  on the losing branch; `admin-service`'s variant omits even that and simply
  overwrites unconditionally once past the check). **All three are non-atomic**:
  two threads can both observe `locks.get(key)` as absent/expired before either
  has called `put`, so both believe they acquired the lock. This defeats the
  entire purpose of `tryAcquireLock` (mutual exclusion for duplicate-request
  suppression).

After this task: `admin-service`, `inventory-service`, and `master-service`'s
`tryAcquireLock` must use the same atomic `ConcurrentHashMap.compute(...)`
pattern already correct in `inbound-service`/`outbound-service`, closing the
race window. This is a **behavior-preserving** fix — the public method
contract (`boolean tryAcquireLock(String key, Duration ttl)`) and all other
methods (`lookup`, `put`, `releaseLock`) are unchanged.

Blast radius is limited: this store is wired only under `@Profile("standalone")`
in each service's config class (`AdminServiceConfig`, `IdempotencyConfig` x2);
the default/production path uses `RedisIdempotencyStore` (`@Profile("!standalone")`),
which is unaffected by this task. `standalone` is used for local dev/demo runs,
not CI's default profile — still worth fixing correctly since demos exercise
concurrent duplicate-submit scenarios.

---

# Scope

## In Scope

- `apps/admin-service/src/main/java/com/wms/admin/infra/idempotency/InMemoryIdempotencyStore.java`
  — `tryAcquireLock` only.
- `apps/inventory-service/src/main/java/com/wms/inventory/adapter/out/idempotency/InMemoryIdempotencyStore.java`
  — `tryAcquireLock` only.
- `apps/master-service/src/main/java/com/wms/master/adapter/out/idempotency/InMemoryIdempotencyStore.java`
  — `tryAcquireLock` only.
- Adding/extending a concurrency unit test (N racing threads on the same key,
  exactly one wins) per fixed service, mirroring the existing
  `outbound-service` `InMemoryIdempotencyStoreTest.concurrentTryAcquireLockYieldsExactlyOneWinner`.

## Out of Scope

- `inbound-service` / `outbound-service` `InMemoryIdempotencyStore` — already
  correct, not touched.
- `RedisIdempotencyStore` (production/default-profile path) in any service —
  untouched, this task is scoped to the in-memory standalone store only.
- Any change to the `IdempotencyStore` interface, `lookup`/`put`/`releaseLock`
  methods, or the filters/config classes that wire the bean.
- Any API/contract change — this is an internal implementation detail of a
  standalone-only in-memory store, not a public contract.

---

# Acceptance Criteria

- [ ] `admin-service`, `inventory-service`, `master-service`'s `tryAcquireLock`
      each use `locks.compute(storageKey, ...)` instead of the
      get-check-put(-compensate) sequence.
- [ ] Method signature (`boolean tryAcquireLock(String storageKey, Duration ttl)`)
      and observable behavior (exclusive acquire until expiry/release) are
      unchanged.
- [ ] A concurrency unit test exists in each of the 3 fixed services proving
      exactly one of two threads racing `tryAcquireLock` on the same key wins.
- [ ] `./gradlew :projects:wms-platform:apps:admin-service:test
      :projects:wms-platform:apps:inventory-service:test
      :projects:wms-platform:apps:master-service:test` passes GREEN.
- [ ] `inbound-service` and `outbound-service` `InMemoryIdempotencyStore.java`
      files are byte-for-byte unchanged.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/services/master-service/idempotency.md`
- `specs/services/outbound-service/idempotency.md`
- `platform/testing-strategy.md` (unit test pyramid — concurrency unit test)

# Related Skills

- `.claude/skills/backend/` (per `.claude/skills/INDEX.md`)

---

# Related Contracts

- None — no HTTP/event contract changes. This is an internal, non-contractual
  implementation detail of a `standalone`-only in-memory adapter.

---

# Target Service

- `admin-service`
- `inventory-service`
- `master-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md` (Layered — per `PROJECT.md` § Overrides)
- `specs/services/inventory-service/architecture.md` (Hexagonal)
- `specs/services/master-service/architecture.md` (Hexagonal)

---

# Implementation Notes

- Mirror the `inbound-service`/`outbound-service` `compute()`-based pattern.
  Either of their two equivalent shapes is acceptable (return-value comparison
  vs. a captured mutable boolean flag) as long as the check-and-set is inside
  the `compute` lambda so it runs under `ConcurrentHashMap`'s per-key lock.
- Use primitive `long`/`.longValue()` comparisons inside the lambda, not
  `Long.equals()` — sidesteps the autoboxing-cache trap (see
  `outbound-service`'s existing Javadoc on this point).
- Do not change `lookup`, `put`, or `releaseLock` — only `tryAcquireLock`.
- `admin-service`'s current variant has no compensating `put(key, prev)` step
  (it commits unconditionally once past the initial check) — do not try to
  preserve that shape; replace the whole method body with the atomic
  `compute()` version, consistent with the other two fixed services.

---

# Edge Cases

- Two threads race `tryAcquireLock` on the same key at the same instant —
  exactly one must return `true`.
- A thread calls `tryAcquireLock` on a key whose lock has already expired
  (`existing <= now`) — must succeed and overwrite.
- A thread calls `tryAcquireLock` on a key with no prior entry — must succeed.
- Sequential (non-concurrent) acquire/release behavior must be unchanged
  (existing single-threaded tests must still pass unmodified).

---

# Failure Scenarios

- If the fix accidentally always returns `true` (e.g. inverted condition),
  the concurrency test's "exactly one winner" assertion fails — caught by
  the new test.
- If the fix breaks TTL expiry (lock never becomes acquirable again after
  expiry), the existing `tryAcquireLockIsExclusive_untilExpiryOrRelease` /
  `releaseLockLetsNextCallerAcquire`-style tests fail.

---

# Test Requirements

- Unit test: concurrency race test (2 threads, N rounds, exactly one winner
  per round) added to `admin-service`, `inventory-service`, `master-service`
  (mirroring `outbound-service`'s existing test).
- Unit test: existing sequential acquire/release/expiry tests continue to
  pass unmodified (add an `InMemoryIdempotencyStoreTest` class where one does
  not already exist — `admin-service` and `inventory-service` currently lack
  one; `master-service` already has one and only needs the new race test
  method added).

---

# Definition of Done

- [ ] Implementation completed (3 files' `tryAcquireLock` fixed)
- [ ] Tests added (concurrency race test x3, plus baseline tests for services
      that lacked an `InMemoryIdempotencyStoreTest` class)
- [ ] Tests passing (`admin-service`, `inventory-service`, `master-service`
      Gradle `test` task GREEN)
- [ ] Contracts updated if needed — N/A, no contract change
- [ ] Specs updated first if required — N/A, no spec change
- [ ] Ready for review
