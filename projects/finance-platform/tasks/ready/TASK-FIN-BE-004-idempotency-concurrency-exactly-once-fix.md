# Task ID

TASK-FIN-BE-004

# Title

account-service: idempotency exactly-once under concurrent same-key contention — make the finance Testcontainers IT 12/12 green (Fix issue found in TASK-FIN-BE-001)

# Status

ready

# Owner

backend

# Task Tags

- code
- bug
- concurrency

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

**Fix issue found in TASK-FIN-BE-001**, surfaced by the **TASK-MONO-115** `finance-integration-tests` CI job run [`26071043853`](https://github.com/kanggle/monorepo-lab/actions/runs/26071043853) once **TASK-FIN-BE-003** (#607, `83c8b398`) cleared the owner_ref round-trip + JWKS defects. The finance IT suite is now **11/12 green**; the **single remaining failure** is:

```
IdempotencyConcurrencyIntegrationTest > F1: same key + identical payload concurrently
  → funds move exactly once  FAILED
  java.lang.AssertionError  at IdempotencyConcurrencyIntegrationTest.java:92
```

Line 92 = `assertThat(performed).isLessThanOrEqualTo(1)` → **`performed > 1`**: under 8-thread concurrent same-Idempotency-Key contention, **more than one thread completed the fund movement** (`placeHold` succeeded + idempotency `store` succeeded for ≥2 threads) — i.e. fintech **F1 exactly-once** is not demonstrated for this scenario.

This is a **distinct concurrency defect class** (not the FIN-BE-003 PII round-trip / JWKS defects) and **not a regression**: this test had never run to completion before — it was masked by the prior context-load + decrypt cascade and only executes its assertions now that FIN-BE-002/003 unblocked it. Per FIN-BE-003's own **Failure Scenario A** it was scoped out and tracked here (user-approved **strategy A**). Resolving it makes `main`'s `finance-integration-tests` go **RED→GREEN** and is the **true terminal** of the honest green-wash chain (FIN-BE-001 gap → MONO-115 CI → FIN-BE-002 schema → FIN-BE-003 behavioural → **FIN-BE-004 concurrency**).

## Two hypotheses — the implementer MUST determine which (do not assume)

The test ([`IdempotencyConcurrencyIntegrationTest.java`](../../apps/account-service/src/test/java/com/example/finance/account/integration/IdempotencyConcurrencyIntegrationTest.java)) drives a **hand-rolled** `findExisting(...)` → `service.placeHold(...)` → `idempotencyStore.store(...)` sequence across 8 threads. It **bypasses the production idempotency wrapper** `presentation/support/IdempotentExecution.java` (the component controllers actually use). So:

- **H1 — test asserts at the wrong layer (test-design defect).** Production exactly-once is enforced by `IdempotentExecution` (atomic claim/replay) + the `idempotency_keys` PK/unique constraint + `RedisOrDbIdempotencyStore`. The test's non-atomic `findExisting`-then-`store` cannot reproduce that guarantee because it never invokes the real wrapper; the assertion is therefore testing a property the exercised code path was never designed to provide. Fix = make the IT drive the **real** `IdempotentExecution` path (or the controller) so it actually exercises the production guarantee — **only if** production genuinely enforces atomic exactly-once (must be proven, see H2).
- **H2 — genuine production concurrency gap.** `IdempotentExecution` / `RedisOrDbIdempotencyStore.store` does not atomically first-writer-win (e.g. upsert instead of insert-if-absent, or no DB unique constraint actually enforced, or the Redis primary path races), AND/OR `Balance` `@Version` optimistic locking does not serialize concurrent `placeHold` on the same balance (so ≥2 holds commit). Fix = close the real atomicity/locking gap so concurrent same-key fund movement is exactly-once (F1) and the balance single-writer (F2) holds.

The correct outcome is **production F1/F2 correctness must be real and CI-proven**. If H1 (test-design) is the truth, the fix is to correct the IT to exercise the production `IdempotentExecution` path — **but green-wash is prohibited**: the assertion may only be satisfied by a genuine production exactly-once guarantee, never by weakening/deleting the assertion or asserting something trivially true. If H2, fix the production atomicity. A mix is possible (fix the test to use the real wrapper **and** close a real gap it then exposes).

---

# Scope

## In Scope

1. Determine H1 vs H2 with evidence: inspect `IdempotentExecution`, `RedisOrDbIdempotencyStore.store/findExisting`, `IdempotencyKeyJpaEntity` (PK/unique constraint), `Balance` `@Version` + the `placeHold` write path under concurrency. State the finding explicitly in the impl PR.
2. Apply the corresponding fix:
   - H1 → rework `IdempotencyConcurrencyIntegrationTest.concurrentSameKeyMovesFundsOnce` to drive the **production** idempotency path (`IdempotentExecution` or the controller via MockMvc), asserting exactly-once on the real guarantee. Test-only change.
   - H2 → close the production atomicity/locking gap (`infrastructure/idempotency/**` and/or the `placeHold`/balance write path / a DB unique constraint via a **new forward migration** if and only if a missing constraint is the root cause — never edit `V1__init.sql`).
3. Make the **MONO-115 `finance-integration-tests` job fully green: 5 IT classes / 12 tests** on real MySQL+Redis+Kafka.
4. Keep `:projects:finance-platform:apps:account-service:check` at **117/0/0/0** (no unit/slice regression).

## Out of Scope

- No change to `V1__init.sql` or the enum `@JdbcTypeCode` mapping (FIN-BE-002) or the owner_ref adapter / JWKS harness (FIN-BE-003) — all merged & CI-proven; do not regress them.
- No change to the MONO-115 `.github/workflows/ci.yml` job.
- No API/event **contract** change. `architecture.md`/ADR change only if the analysis shows the spec itself under-specifies the F1 concurrency guarantee (then update the spec **first**, minimally, and note it) — otherwise none.
- No weakening/removal of any existing assertion to force green (green-wash prohibited).

---

# Acceptance Criteria

1. MONO-115 `finance-integration-tests` **green: 5 IT classes / 12 tests pass** on real MySQL+Redis+Kafka (cite the green run id). main `finance-integration-tests` flips RED→GREEN on merge.
2. `:account-service:check` stays **117/0/0/0**.
3. The impl PR states the **H1/H2 finding with evidence** and the fix follows it; production F1 exactly-once + F2 single-writer remain genuinely enforced (not asserted-away).
4. Diff scope matches the finding: test-only (H1) or `infrastructure/idempotency/**` + optional new forward migration (H2) — explicitly **no** V1 edit, no FIN-BE-002/003 regression, no contract/ci.yml change.
5. Goal cites **TASK-FIN-BE-001** + the surfacing run (project INDEX Review Rule).

---

# Related Specs

- [TASK-FIN-BE-001](../done/TASK-FIN-BE-001-account-service-bootstrap.md) — original impl (defect origin; `done/`)
- [TASK-FIN-BE-003](../done/TASK-FIN-BE-003-pii-roundtrip-and-it-jwks-fix.md) — prerequisite; its Failure Scenario A scoped this out & tracked it here
- [TASK-MONO-115](../../../../tasks/done/TASK-MONO-115-finance-integration-ci-job.md) — the CI job that surfaces & verifies (authoritative; `:check` ≠ sufficient)
- [specs/services/account-service/architecture.md](../../specs/services/account-service/architecture.md) — fintech F1 (idempotent fund movement) / F2 (balance single-writer)

---

# Related Contracts

- None expected (idempotency is a cross-cutting execution concern; no API/event payload change). If the analysis shows the contract under-specifies idempotency semantics, update `specs/contracts/...` first and note it.

---

# Target Service / Component

- `projects/finance-platform/apps/account-service` — `presentation/support/IdempotentExecution`, `infrastructure/idempotency/**`, the `placeHold`/balance write path, and/or `src/test/java/.../integration/IdempotencyConcurrencyIntegrationTest.java` (per the H1/H2 finding).

---

# Edge Cases

1. **Redis primary vs DB fallback**: `RedisOrDbIdempotencyStore` is fail-CLOSED with Redis primary + DB fallback. Exactly-once must hold on **both** the Redis path and the DB-fallback path (and the failover boundary). The IT runs real Redis (Testcontainer) — verify which path it exercises.
2. **`@Version` vs row creation**: if `placeHold` lazily creates the balance/hold rows, concurrent threads must not each create a divergent row; the serialization point (optimistic `@Version` on `Balance`, or a unique constraint, or the idempotency claim) must be identified precisely.
3. **Test concurrency harness**: `pool.invokeAll(nCopies(8, task))` — all 8 likely `findExisting` → MISS before any `store`. If the production guarantee is the `store` unique-insert, the test's `catch (RuntimeException dup) → false` must actually catch the constraint violation the production store throws (verify the store throws, not upserts).
4. **`@DirtiesContext(AFTER_CLASS)`** + shared static MySQL/Redis/JWKS: ensure the fix doesn't rely on per-test DB cleanup that the suite doesn't do.

---

# Failure Scenarios

## A. Fix makes this test green but a different IT then fails

Same chain pattern. If a genuinely different defect class surfaces, **STOP and surface a separate follow-up fix-task** — do not silently expand scope or green-wash. If it's the same concurrency/idempotency class, fix within this task.

## B. Temptation to weaken the assertion

`assertThat(performed).isLessThanOrEqualTo(1)` (+ `held == 3000`, `available == 7000`) encodes the F1/F2 guarantee. Making it pass by relaxing the bound, deleting the assertion, or asserting a tautology is **green-wash and prohibited**. Green is only valid if a genuine production exactly-once guarantee is exercised and holds.

## C. Local Docker blocker

Local Testcontainers IT can't run (known blocker). The MONO-115 CI job on the impl PR is authoritative; cite the green run id. `:check` is necessary but NOT sufficient — it never exercises real concurrent MySQL+Redis (exactly why this hid through TASK-FIN-BE-001).

---

# Test Requirements

- MONO-115 `finance-integration-tests`: 5 IT / 12 tests PASS (run id cited).
- `:account-service:check`: 117/0/0/0.
- Impl PR documents H1/H2 evidence + that F1/F2 remain genuinely enforced.
- Diff-scope assertion in the impl PR.

---

# Definition of Done

- [ ] H1/H2 determined with evidence (stated in impl PR)
- [ ] Fix applied per the finding; F1 exactly-once + F2 single-writer genuinely enforced (not asserted-away)
- [ ] MONO-115 `finance-integration-tests` green (5 IT / 12 tests) — run id cited
- [ ] `:account-service:check` 117/0/0/0
- [ ] Diff scope matches finding; no V1 edit, no FIN-BE-002/003 regression, no contract/ci.yml change
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — concurrency correctness under real MySQL+Redis, fintech F1 exactly-once / F2 single-writer, Docker-blocker → CI-only verification = highest cycle-burn risk; correctness-critical, not routine.
- **Critical verification discipline**: MONO-115 CI (real MySQL+Redis) is the only authoritative proof — `:check` is necessary but NOT sufficient (the gate that hid this through TASK-FIN-BE-001). Dispatcher independently re-verifies (BE-301) before commit/push/PR. Do not declare done on `:check` alone (green-wash prohibited).
- **dependency**:
  - `선행`: **TASK-FIN-BE-003 #607 merged** (`83c8b398`) — D1/D2 cleared so this test runs to its assertion; the MONO-115 job exists & is the verifier.
  - `후속`: none. Merging this = main `finance-integration-tests` RED→GREEN; finance v1 behavioural-proof gap fully closed; the honest chain terminates here.
- **green-wash chain (terminal)**: FIN-BE-001 honest gap → MONO-115 CI built → FIN-BE-002 schema → FIN-BE-003 behavioural → **FIN-BE-004 concurrency**. Each step surfaced, tracked, user-approved (strategy A), never dropped, never green-washed. This task ends it — honestly (real F1/F2), or it spawns the next honest follow-up (Failure Scenario A), never a faked green.
