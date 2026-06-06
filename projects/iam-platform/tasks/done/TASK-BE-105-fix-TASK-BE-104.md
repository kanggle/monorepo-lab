---
id: TASK-BE-105
title: "Fix TASK-BE-104 — integration test wrong assertion for unknown-account dedup-first behavior"
status: ready
priority: high
target_service: account-service
tags: [event, kafka, consumer, idempotency, test]
created_at: 2026-04-26
---

# TASK-BE-105: Fix issues found in TASK-BE-104

## Goal

Fix the incorrect assertion in `LoginSucceededConsumerIntegrationTest.consume_unknownAccount_skipsWithoutSideEffects`
found during the TASK-BE-104 review.

The test asserts `processedEventRepository.existsByEventId(eventId)).isFalse()` for an
unknown-account event. However, the TASK-BE-104 implementation introduced a **dedup-first**
ordering: `processedEventRepository.saveAndFlush()` is called BEFORE `accountRepository.findById()`.
As a result, a `processed_events` row IS written even when the account does not exist.
The `isFalse()` assertion is therefore wrong — it will fail against a real database.

The unit test `execute_accountNotFound_returnsWithoutSideEffects` already correctly reflects this
behavior (`verify(processedEventRepository).saveAndFlush(any())`). The integration test must be
aligned with the same expectation.

## Scope

### In
- `LoginSucceededConsumerIntegrationTest.consume_unknownAccount_skipsWithoutSideEffects`:
  Change the assertion from `existsByEventId(eventId)).isFalse()` to
  `existsByEventId(eventId)).isTrue()` to match the dedup-first behavior.
- Update the test display name and comment to reflect the corrected expectation: the
  unknown-account path still safely skips the account update, but the dedup row IS written
  so that any redelivery of the same event short-circuits on `existsByEventId`.

### Out
- Any changes to `UpdateLastLoginUseCase` implementation
- Any changes to other integration test scenarios
- Any changes to unit tests (already correct)
- Any schema or migration changes

## Acceptance Criteria

1. `consume_unknownAccount_skipsWithoutSideEffects` asserts
   `processedEventRepository.existsByEventId(eventId)).isTrue()` (dedup row IS written).
2. The test still verifies that no account update occurred (the `accounts` table has no
   `last_login_succeeded_at` value for the unknown account ID).
3. All `LoginSucceededConsumerIntegrationTest` tests pass.
4. All `UpdateLastLoginUseCaseTest` unit tests continue to pass.

## Related Specs

- `specs/services/account-service/architecture.md` — layered architecture rules
- `rules/traits/transactional.md` — T3 idempotency and inbox pattern
- `platform/testing-strategy.md` — test requirements

## Related Contracts

- `specs/contracts/events/auth-events.md` — `auth.login.succeeded` payload

## Edge Cases

- A redelivery of the same unknown-account event must short-circuit on the
  `existsByEventId` fast-path (row already present from first delivery), not
  re-walk the full code path.

## Failure Scenarios

- If the assertion is left as `isFalse()`, the integration test will fail against
  a real database because the dedup row is unconditionally written before the
  account lookup.

## Test Requirements

### Integration tests
- `consume_unknownAccount_skipsWithoutSideEffects`: fix `isFalse()` → `isTrue()`.
- Optionally assert that `accountRepository.findById(unknownAccountId)` is empty
  (i.e. no account row was created as a side effect) to document the non-account-update
  guarantee explicitly.

## Implementation Notes

The only change required is in `LoginSucceededConsumerIntegrationTest`:

```java
// Before (wrong — test was written before dedup-first ordering was introduced):
assertThat(processedEventRepository.existsByEventId(eventId)).isFalse();

// After (correct — dedup row is written before account lookup under dedup-first):
assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();
```

The display name of the test should also be updated to remove the misleading claim
"processed_events 미적재" (not persisted) and replace it with a correct description
such as "processed_events 1행 적재, account 미갱신" (dedup row written, account not updated).
