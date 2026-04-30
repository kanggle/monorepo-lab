---
id: TASK-BE-104
title: "Fix issues found in TASK-BE-103 — idempotency catch-inside-transaction bug"
status: ready
priority: high
target_service: account-service
tags: [event, kafka, consumer, idempotency]
created_at: 2026-04-26
---

# TASK-BE-104: Fix issues found in TASK-BE-103

## Goal

Fix the idempotency race-condition handler in `UpdateLastLoginUseCase` that was
identified during the TASK-BE-103 review. The current implementation wraps
`processedEventRepository.save()` in a `try/catch (DataIntegrityViolationException)`
block, but in real Spring Data JPA the `persist()` call does not execute SQL
immediately — the INSERT is deferred to the flush phase (transaction commit).
As a result, a `DataIntegrityViolationException` thrown during concurrent
duplicate delivery is raised OUTSIDE the try/catch (during commit), causing the
transaction to fail with `UnexpectedRollbackException` instead of being silently
swallowed. This defeats the race-condition guard and may cause the Kafka consumer
to rethrow the exception, triggering unnecessary retries.

## Scope

### In
- `UpdateLastLoginUseCase.execute()` — fix idempotency ordering to ensure
  `processedEventRepository.save()` + explicit `flush()` happen inside the
  `try/catch`, OR restructure as a two-transaction model, OR add
  `@Transactional(noRollbackFor = DataIntegrityViolationException.class)` with
  explicit flush so the catch actually fires.
- Recommended fix: move `processedEventRepository.save()` BEFORE
  `accountRepository.save()` (dedup-first pattern), flush the ProcessedEvent
  insert inside the try block, and catch `DataIntegrityViolationException` to
  skip on duplicates. The `accountRepository.save()` for the account update
  can then be conditional on the dedup succeeding.
- Update unit test `execute_dedupRaceCondition_doesNotPropagate` to reflect
  the corrected flow (if the fix changes method ordering).
- Integration test for true concurrent duplicate delivery is optional; existing
  sequential dedup test is sufficient for this fix.

### Out
- Changes to `LoginSucceededConsumer`, `Account.recordLoginSuccess`, or any
  other part of TASK-BE-103
- Changes to Kafka configuration or consumer group settings
- New Flyway migrations

## Acceptance Criteria

1. A `DataIntegrityViolationException` thrown by the `processed_events` UNIQUE
   constraint during concurrent duplicate delivery is correctly caught and
   suppressed — i.e. the `@Transactional` method completes without rethrowing
   and without leaving the transaction in a rollback-only state.
2. The account's `last_login_succeeded_at` is NOT rolled back when a concurrent
   duplicate is detected — the update is either committed before the dedup
   attempt, or the dedup is attempted first with early-exit if the row already
   exists.
3. The unit test `execute_dedupRaceCondition_doesNotPropagate` remains green and
   accurately reflects real JPA behavior (explicit flush or noRollbackFor).
4. All existing `UpdateLastLoginUseCaseTest` and `LoginSucceededConsumerTest`
   tests continue to pass.
5. All existing `LoginSucceededConsumerIntegrationTest` tests continue to pass.

## Related Specs

- `specs/services/account-service/architecture.md` — layered architecture rules
- `rules/traits/transactional.md` — T3 idempotency and inbox pattern
- `platform/testing-strategy.md` — test requirements

## Related Contracts

- `specs/contracts/events/auth-events.md` — `auth.login.succeeded` payload

## Edge Cases

- Concurrent threads both pass the initial `existsByEventId` check: the dedup
  row insert must be the discriminator, not the account update.
- The transaction must not be left in rollback-only state after catching the
  constraint violation.

## Failure Scenarios

- If explicit `flush()` is used inside the try block, any other pending entity
  flush should NOT be affected; only the `processedEvent` INSERT needs to be
  flushed early.
- If `@Transactional(noRollbackFor = ...)` is used, ensure that legitimate
  (non-idempotency) `DataIntegrityViolationException`s from `accountRepository.save()`
  are still allowed to propagate correctly.

## Test Requirements

### Unit tests
- Update `execute_dedupRaceCondition_doesNotPropagate` to test the fixed
  behavior accurately (mock flush if needed, or assert transaction does not
  mark rollback-only).
- All existing test scenarios (normal, duplicate-skip, account-not-found,
  max-semantics) must remain covered.

## Implementation Notes

### Recommended approach — dedup-first with explicit flush

```java
@Transactional(noRollbackFor = DataIntegrityViolationException.class)
public void execute(String eventId, String accountId, Instant occurredAt) {
    if (processedEventRepository.existsByEventId(eventId)) {
        log.info("Duplicate auth.login.succeeded event skipped: eventId={}", eventId);
        return;
    }

    try {
        processedEventRepository.saveAndFlush(
            ProcessedEventJpaEntity.create(eventId, EVENT_TYPE));
    } catch (DataIntegrityViolationException e) {
        // Concurrent redelivery won the race.
        log.info("ProcessedEvent insert lost a redelivery race: eventId={}", eventId);
        return;  // Exit without updating the account — the concurrent winner already did it.
    }

    Optional<Account> maybeAccount = accountRepository.findById(accountId);
    if (maybeAccount.isEmpty()) {
        log.warn("auth.login.succeeded for unknown accountId={}, eventId={} — skipping",
                accountId, eventId);
        return;
    }
    Account account = maybeAccount.get();
    account.recordLoginSuccess(occurredAt);
    accountRepository.save(account);
}
```

Key changes:
- Use `saveAndFlush()` so the INSERT SQL is executed immediately within the
  try block, before `persist()` defers it to commit.
- Add `noRollbackFor = DataIntegrityViolationException.class` so the transaction
  can be committed even after a caught constraint violation.
- Move the dedup save before the account update (dedup-first) so catching the
  exception allows clean return without partial state.
