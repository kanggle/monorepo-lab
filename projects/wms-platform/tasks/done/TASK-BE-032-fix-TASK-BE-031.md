# TASK-BE-032 — Fix issues found in TASK-BE-031

| Field | Value |
|---|---|
| **Task ID** | TASK-BE-032 |
| **Title** | inbound-service: Error code granularity + REST Idempotency-Key wiring |
| **Status** | review |
| **Owner** | backend |
| **Tags** | inbound, error-handling, idempotency, fix |

---

## Goal

Fix two pre-existing non-blocking issues flagged in the TASK-BE-031 review that affect the entire `inbound-service` (not just the putaway additions):

1. **Error code granularity** — `GlobalExceptionHandler` maps all 422 exceptions to a single generic code. The API contract (`inbound-service-api.md` §"Error Codes") requires each exception to surface a distinct machine-readable `error.code` string (e.g. `LOT_REQUIRED`, `PUTAWAY_QUANTITY_EXCEEDED`, `STATE_TRANSITION_INVALID`) so API consumers can branch on the code.

2. **REST Idempotency-Key wiring** — `Idempotency-Key` header is validated as non-blank by controllers but never consulted against `IdempotencyStore`. The idempotency spec (`idempotency.md` §REST) defines a PENDING / COMPLETE / FAILED lifecycle backed by Redis; without it, replay-cache and body-hash mismatch detection are absent. Domain uniqueness constraints (unique ASN no, PK on confirmation) are the current sole backstop.

---

## Scope

### Part 1 — Error Code Granularity

**Domain exceptions** (`domain/exception/`):

Add a `errorCode()` default method to a new `InboundDomainException` base class (extend all existing domain exceptions from it). Each exception overrides `errorCode()` to return the contract-defined string:

| Exception | `errorCode()` |
|---|---|
| `AsnNotFoundException` | `ASN_NOT_FOUND` |
| `InspectionNotFoundException` | `INSPECTION_NOT_FOUND` |
| `PutawayInstructionNotFoundException` | `PUTAWAY_INSTRUCTION_NOT_FOUND` |
| `PutawayLineNotFoundException` | `PUTAWAY_LINE_NOT_FOUND` |
| `AsnNoDuplicateException` | `ASN_NO_DUPLICATE` |
| `StateTransitionInvalidException` | `STATE_TRANSITION_INVALID` |
| `AsnAlreadyClosedException` | `ASN_ALREADY_CLOSED` |
| `InspectionQuantityMismatchException` | `INSPECTION_QUANTITY_MISMATCH` |
| `InspectionIncompleteException` | `INSPECTION_INCOMPLETE` |
| `PartnerInvalidTypeException` | `PARTNER_INVALID_TYPE` |
| `SkuInactiveException` | `SKU_INACTIVE` |
| `LotRequiredException` | `LOT_REQUIRED` |
| `WarehouseNotFoundInReadModelException` | `WAREHOUSE_NOT_FOUND` |
| `PutawayQuantityExceededException` | `PUTAWAY_QUANTITY_EXCEEDED` |
| `LocationInactiveException` | `LOCATION_INACTIVE` |
| `WarehouseMismatchException` | `WAREHOUSE_MISMATCH` |

**GlobalExceptionHandler** (`adapter/in/web/advice/`):

- For each handler method that accepts a typed domain exception, call `exception.errorCode()` as the first argument to `ApiErrorEnvelope.of(...)`.
- For 409 conflict handlers: `AsnNoDuplicateException` → `ASN_NO_DUPLICATE`; `OptimisticLockingFailureException` → `CONFLICT`.
- For 403: `AccessDeniedException` / `AuthorizationDeniedException` → `FORBIDDEN`.
- For 400 framework exceptions: `MethodArgumentNotValidException` → `VALIDATION_ERROR`; `MethodArgumentTypeMismatchException` → `VALIDATION_ERROR`; `IllegalArgumentException` → `VALIDATION_ERROR`.
- For 500 fallback: `INTERNAL_ERROR`.

### Part 2 — REST Idempotency-Key Wiring

**`StoredResponse`** (new record in `application/port/out/` or `adapter/out/idempotency/`):

```java
public record StoredResponse(int statusCode, String body, String requestBodyHash) {}
```

If a `StoredResponse` record already exists, add the `requestBodyHash` field if absent.

**`InboundIdempotencyFilter`** (new class in `adapter/in/web/filter/`, extends `OncePerRequestFilter`):

Processing flow per the spec (`idempotency.md` §1.4 REST Idempotency-Key):

1. Skip if method is not POST or if path is `/webhooks/**` (webhooks use `X-Erp-Event-Id` instead).
2. Extract `Idempotency-Key` header. If blank/absent → skip (controller `@NotBlank` validation will catch it and return 400).
3. Wrap request with `ContentCachingRequestWrapper`; wrap response with `ContentCachingResponseWrapper`.
4. Compute `requestBodyHash`:
   - Parse request body bytes as UTF-8.
   - If body is empty → hash of empty string.
   - Otherwise: parse as JSON via `ObjectMapper`, re-serialize with sorted keys (use `ObjectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)`), SHA-256 hex.
5. Build storage key: `POST:{sha256hex(requestURI)}:{idempotencyKey}`.
6. `idempotencyStore.lookup(storageKey)`:
   - **Present + `storedBodyHash.equals(requestBodyHash)`** → replay: write cached `statusCode` + `body` string to the wrapped response, set `Content-Type: application/json`, **return** (do not call `filterChain.doFilter`).
   - **Present + hash mismatch** → write 409 `DUPLICATE_REQUEST` error envelope, **return**.
7. **Not present** → `idempotencyStore.tryAcquireLock(storageKey, Duration.ofSeconds(30))`:
   - `false` (lock already held = PENDING) → write 503 `{"error":{"code":"PROCESSING","message":"Request is being processed"}}` + `Retry-After: 1` header, **return**.
   - `true` → proceed.
8. Call `filterChain.doFilter(wrappedRequest, wrappedResponse)`.
9. After the chain (`try/finally`):
   - 2xx status → cache: `idempotencyStore.put(storageKey, new StoredResponse(status, responseBodyAsString, requestBodyHash), Duration.ofHours(24))`.
   - Non-2xx or exception → do not cache (allow retry).
   - Always: `idempotencyStore.releaseLock(storageKey)`.

**Register the filter** in `IdempotencyConfig.java` (or a new `WebConfig.java`) via `FilterRegistrationBean<InboundIdempotencyFilter>`:
- `setOrder(Ordered.HIGHEST_PRECEDENCE + 20)` (after Spring Security, before DispatcherServlet).
- `addUrlPatterns("/api/v1/inbound/*")`.

### Out of scope

- Kafka consumer idempotency (already wired via `EventDedupePort`)
- Webhook idempotency (already wired via `erp_webhook_dedupe` table)
- Idempotency changes to `inventory-service` or other services
- Changing the `IdempotencyStore` interface contract

---

## Acceptance Criteria

1. Every domain exception's `errorCode()` matches the string in `inbound-service-api.md` §Error Codes table exactly (case-sensitive).
2. `ApiErrorEnvelope` `code` field in all error responses equals the exception's `errorCode()` — no more generic `UNPROCESSABLE_ENTITY` or similar.
3. `GlobalExceptionHandlerTest` (unit): one test per exception mapping verifying that the returned `ResponseEntity` carries the correct HTTP status AND the correct `code` string in the body.
4. `POST /api/v1/inbound/asns` with `Idempotency-Key: K1` and body B1 → 201; repeat with same K1 + same B1 → 201 replayed from cache (no second ASN created, response body identical).
5. `POST /api/v1/inbound/asns` with `Idempotency-Key: K1` but body B2 (different `asnNo`) → 409 `DUPLICATE_REQUEST`.
6. `InboundIdempotencyFilter` does NOT intercept `/webhooks/**` paths.
7. `InboundIdempotencyFilterTest` (unit / `@WebMvcTest` slice): AC-4, AC-5, lock-held → 503, 2xx cached → replayed, 4xx not cached.
8. Redis key shape written by the filter matches `inbound:idempotency:POST:{pathHash}:{idempotencyKey}` (assert in an integration test or unit test with a real `InMemoryIdempotencyStore`).
9. All existing tests continue to pass (`./gradlew :projects:wms-platform:apps:inbound-service:test`).

---

## Related Specs

- `specs/services/inbound-service/idempotency.md` §1 (REST surface — full flow, body hash, key shape, PENDING/COMPLETE/FAILED lifecycle)
- `specs/services/inbound-service/architecture.md` §"Idempotency strategy"

---

## Related Contracts

- `specs/contracts/http/inbound-service-api.md` §"Error Codes" (authoritative code strings), §"Idempotency-Key" semantics

---

## Edge Cases

1. **Empty body POST** (e.g. `{}`): body hash of empty JSON must be stable; `{"version":0}` and `{}` are different hashes.
2. **JSON key ordering**: `{"b":1,"a":2}` and `{"a":2,"b":1}` must produce the same hash (alphabetically sorted keys).
3. **Lock timeout**: lock TTL is 30 s; if the original request takes longer, the lock expires and a second request proceeds independently. Downstream domain uniqueness constraints catch true duplicates.
4. **Filter on non-JSON POST** (multipart, plain text): hash the raw body bytes as-is (no JSON parse); still cache and compare by hash.
5. **503 path**: concurrent duplicate while lock is held gets 503 + `Retry-After: 1`. Client should retry after ≥ 1 s to get the cached COMPLETE response.
6. **No Spring Security interference**: filter runs at `HIGHEST_PRECEDENCE + 20`; Security filter chain runs earlier; JWT validation happens before idempotency lookup.

---

## Failure Scenarios

1. **Redis unavailable** during filter lookup → `IdempotencyStore` must fail-closed: propagate exception → filter catches → skip idempotency (proceed without caching) and log a warning at WARN level. Domain backstops remain active.
2. **Exception during chain execution** → `finally` block releases lock but does not cache. The next request (same key) will find no entry and re-execute cleanly.
3. **Response body read failure** (ContentCachingResponseWrapper returns empty) → treat as non-cacheable; release lock; proceed.

---

## Implementation Notes

- **`InboundDomainException` base class**: extend `RuntimeException`, add `public abstract String errorCode()`. All 16 existing exceptions must extend it.
- **Body hash utility**: extract to a package-private `BodyHashUtil` in `adapter/in/web/filter/` — `static String sha256hex(String normalizedJson)` — avoids coupling to Spring beans.
- **`ContentCachingRequestWrapper` caveat**: the cached content is only populated after `doFilter` is called. For pre-chain lookup, read the InputStream directly in the filter before wrapping OR use the wrapper's `getContentAsByteArray()` *after* `doFilter`. Instead: read body via `request.getInputStream()` once before calling `doFilter`, then pass a re-readable `CachedBodyHttpServletRequest` (custom wrapper storing the byte array). Alternatively use `ContentCachingRequestWrapper` and read `getContentAsByteArray()` at the end — but then you cannot compare hashes pre-chain.  
  **Recommended**: use a `CachedBodyServletInputStream` pattern — store body bytes in the filter before `doFilter`, compute hash immediately, wrap the request to replay those bytes for Spring's message converters.
- **`ContentCachingResponseWrapper`**: after `filterChain.doFilter`, call `wrappedResponse.copyBodyToResponse()` to flush the cached bytes to the real response.
- **Filter registration order**: Security chain is at `HIGHEST_PRECEDENCE`; `OncePerRequestFilter` with order `HIGHEST_PRECEDENCE + 20` runs after auth but before DispatcherServlet (`DEFAULT_FILTER_ORDER`).

---

## Target Service

`projects/wms-platform/apps/inbound-service`

## Architecture

Hexagonal (Ports & Adapters). Error code changes touch: `domain/exception/` (new base class) + `adapter/in/web/advice/` (handler update). Idempotency filter lives in `adapter/in/web/filter/` — permitted in the Hexagonal in-adapter layer.

## Test Requirements

- Unit: `BodyHashUtilTest` (key ordering, empty body, SHA-256 determinism), `GlobalExceptionHandlerTest` (all 16+ exception → code mappings), `InboundIdempotencyFilterTest` (all AC-4/5/6/7 paths using `InMemoryIdempotencyStore`)
- No new Testcontainers test required (unit-level `InMemoryIdempotencyStore` is sufficient for filter tests; Redis key shape can be asserted with the in-memory store's internal state or with a fake Redis)

## Definition of Done

- All 9 acceptance criteria satisfied
- `./gradlew :projects:wms-platform:apps:inbound-service:test` passes (zero failures)
- No TODO / FIXME left in production code
- Task moved to `review/` with `Status: review`
