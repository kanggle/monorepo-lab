# TASK-BE-033 — Fix issues found in TASK-BE-032

| Field | Value |
|---|---|
| **Task ID** | TASK-BE-033 |
| **Title** | inbound-service: GlobalExceptionHandler test gap + InMemoryIdempotencyStore race condition |
| **Status** | ready |
| **Owner** | backend |
| **Tags** | inbound, testing, idempotency, fix |

---

## Goal

Fix two issues flagged during the TASK-BE-032 review:

1. **GlobalExceptionHandlerTest missing test** — `MethodArgumentNotValidException` is handled by `GlobalExceptionHandler.handleBadInput()` but has no corresponding test case in `GlobalExceptionHandlerTest`. AC-3 of TASK-BE-032 requires one test per exception mapping. The missing test leaves the `MethodArgumentNotValidException → 400 VALIDATION_ERROR` path unverified.

2. **InMemoryIdempotencyStore race condition** — `tryAcquireLock()` uses a non-atomic check-then-put pattern on a `ConcurrentHashMap`. Two concurrent threads can both pass the `existing == null` check and both succeed in acquiring the lock simultaneously. This violates the exclusive-lock contract and can cause duplicate request processing in concurrent test scenarios. The fix is to use an atomic `putIfAbsent`-based approach.

---

## Scope

### Part 1 — `GlobalExceptionHandlerTest` missing test case

**File**: `src/test/java/com/wms/inbound/adapter/in/web/advice/GlobalExceptionHandlerTest.java`

Add a test method for `MethodArgumentNotValidException`:

```java
@Test
void methodArgumentNotValid_returns400_withCode_VALIDATION_ERROR() {
    // MethodArgumentNotValidException requires a BindingResult;
    // use a mock or construct via MethodArgumentNotValidException(MethodParameter, ...).
    // Simplest: instantiate with a mocked BindingResult.
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    when(ex.getMessage()).thenReturn("Validation failed");

    ResponseEntity<ApiErrorEnvelope> resp = handler.handleBadInput(ex);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
}
```

The test must verify:
- HTTP status is `400 Bad Request`
- `ApiErrorEnvelope.code()` equals `"VALIDATION_ERROR"`

### Part 2 — `InMemoryIdempotencyStore.tryAcquireLock()` atomicity fix

**File**: `src/main/java/com/wms/inbound/adapter/out/idempotency/InMemoryIdempotencyStore.java`

Replace the non-atomic check-then-put with a `ConcurrentHashMap.compute()` or `putIfAbsent` approach that is atomic:

```java
@Override
public boolean tryAcquireLock(String storageKey, Duration ttl) {
    long now = clock.millis();
    long expiresAt = now + ttl.toMillis();
    Long result = locks.compute(storageKey, (k, existing) -> {
        if (existing != null && existing > now) {
            return existing;  // lock is held — do not overwrite
        }
        return expiresAt;  // acquire or renew expired lock
    });
    // If result == expiresAt, we just wrote it → acquired.
    // If result != expiresAt, another thread held the lock → not acquired.
    return result == expiresAt;
}
```

Note: `ConcurrentHashMap.compute()` is atomic per-key. The lambda must not have side effects other than returning the new value.

### Out of scope

- Redis-backed `RedisIdempotencyStore` (uses `setIfAbsent` — already atomic)
- Any changes to the `IdempotencyStore` interface
- `ApiErrorEnvelope` shape alignment (pre-existing TASK-BE-029 issue, separate scope)

---

## Acceptance Criteria

1. `GlobalExceptionHandlerTest` contains a test for `MethodArgumentNotValidException → 400 VALIDATION_ERROR`.
2. All existing `GlobalExceptionHandlerTest` tests continue to pass.
3. `InMemoryIdempotencyStore.tryAcquireLock()` uses an atomic ConcurrentHashMap operation (no separate get + put for the same key).
4. Existing `InboundIdempotencyFilterTest` tests continue to pass with the updated store.
5. `./gradlew :projects:wms-platform:apps:inbound-service:test` passes (zero failures).

---

## Related Specs

- `specs/services/inbound-service/idempotency.md` §1 (REST idempotency, PENDING lock semantics)

---

## Related Contracts

- `specs/contracts/http/inbound-service-api.md` §"Error Codes" — `VALIDATION_ERROR` 400 mapping

---

## Edge Cases

1. `MethodArgumentNotValidException` constructor requires a `BindingResult` — use Mockito mock for the unit test.
2. `tryAcquireLock` atomicity: `ConcurrentHashMap.compute` with `==` equality check on `Long` may have autoboxing/caching issues for values outside -128..127. Use `.longValue()` comparison or store `long` primitives to avoid autoboxing traps.
3. Lock expiry boundary: a lock with `existing == now` (exactly at TTL boundary) should be treated as expired (use `existing >= now` check appropriately).

---

## Failure Scenarios

1. If `ConcurrentHashMap.compute` is used with a throwing lambda → the entry is removed per ConcurrentHashMap contract. Ensure the lambda is non-throwing.
2. If `MethodArgumentNotValidException` mock causes a `UnnecessaryStubbingException` with Mockito strict stubs — use `lenient()` stubbing or switch to a real instance if possible.

---

## Implementation Notes

- `MethodArgumentNotValidException` extends `BindException`. Its no-arg constructor is not public; use `Mockito.mock(MethodArgumentNotValidException.class)` and stub `getMessage()`.
- The `tryAcquireLock` fix should maintain backward-compatibility with all existing `InboundIdempotencyFilterTest` test cases.

---

## Target Service

`projects/wms-platform/apps/inbound-service`

## Architecture

Both fixes are in the adapter and test layers. No domain or port changes.

## Test Requirements

- Unit: add `methodArgumentNotValid_returns400_withCode_VALIDATION_ERROR` to `GlobalExceptionHandlerTest`
- Unit: verify `tryAcquireLock` behavior under simulated concurrency (optional — the compute() fix is self-evidently atomic; a concurrency stress test is not required for a portfolio project)

## Definition of Done

- All 5 acceptance criteria satisfied
- `./gradlew :projects:wms-platform:apps:inbound-service:test` passes (zero failures)
- No TODO / FIXME left in production code
- Task moved to `review/` with `Status: review`
