# master-service — Idempotency Strategy

Detailed idempotency-key handling for all mutating endpoints of `master-service`.
Required artifact per `rules/traits/transactional.md` rule T1.

Read after `architecture.md` and `specs/contracts/http/master-service-api.md`.

---

## Scope

Every mutating endpoint of `master-service` **MUST** honor the `Idempotency-Key`
header. This includes:

- `POST` create endpoints (e.g., `POST /api/v1/master/warehouses`)
- `PATCH` update endpoints
- `POST /{id}/deactivate` and `POST /{id}/reactivate` endpoints

Read-only endpoints (`GET`) are naturally idempotent and do **not** require a key.

---

## Key Contract

### Client Responsibility

- Client generates a **UUID (v4 or v7 recommended)** for every new logical operation
  and sends it in the `Idempotency-Key` HTTP header.
- If the client retries (network timeout, 5xx, intermediate proxy error), it MUST
  reuse the **same key with the same body**. Reusing the key with a different body
  is a client bug and will be rejected.
- Keys are scoped to a single logical operation. Do not reuse the same key across
  different endpoints.

### Server Contract

- Missing header on a mutating endpoint → `400 VALIDATION_ERROR` with
  `message: "Idempotency-Key header is required on mutating endpoints"`.
- Malformed key (not a UUID, empty string, > 64 chars) → `400 VALIDATION_ERROR`.
- Same key + same body hash → replay **first** call's full response (status, body).
- Same key + different body hash → `409 DUPLICATE_REQUEST`.
- First-time key → process normally and persist the response for TTL.

---

## Key Scope

The uniqueness scope is the tuple:

```
(idempotencyKey, method, path)
```

Examples:

- Same key on `POST /api/v1/master/warehouses` and `POST /api/v1/master/skus` →
  treated as **two independent** operations (different path).
- Same key on `POST /.../deactivate` and `POST /.../reactivate` → treated as
  independent (different path).
- Same key on `POST /api/v1/master/warehouses` twice with same body → second call
  replays the first response.

Rationale: narrowing the scope to `(key, method, path)` prevents a retry of one
endpoint from accidentally blocking or masking a different endpoint. The tradeoff
is that the client may reuse the same key across different endpoints; we accept
that in exchange for isolation.

Actor/tenant are intentionally **not** part of the scope in v1. If two different
users coincidentally pick the same UUID within the TTL and hit the same endpoint,
the later caller sees the first caller's response. UUIDs make this effectively
impossible (collision probability ≈ zero at our scale); v2 may add `actorId` to
the scope if the assumption breaks.

---

## Storage

**Store**: Redis.

**Key format**:

```
master:idem:{SHA-256(idempotencyKey || ":" || method || ":" || path)}
```

The stored hash obscures the raw `Idempotency-Key` from anyone with Redis `KEYS`
access, avoids length issues, and flattens key shape.

**Value** (JSON):

```json
{
  "requestHash": "sha256-hex of the normalized request body",
  "status": 201,
  "responseBody": { "id": "...", "warehouseCode": "WH01", "..." },
  "contentType": "application/json",
  "createdAt": "2026-04-18T10:00:00Z"
}
```

**TTL**: **24 hours** (86,400 s). Minimum mandated by `rules/traits/transactional.md`
T1. No renewal on read — original TTL stands.

**Eviction**: natural TTL expiry; no LRU relied upon. Alert threshold:
`master.idempotency.redis.memory.bytes > 80%` of allocated.

---

## Request Hashing

- **Canonicalize** the JSON body before hashing: sort keys alphabetically at every
  level; omit whitespace. Use a library such as Jackson `ObjectMapper` with
  `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`.
- Hash algorithm: **SHA-256**, hex-encoded.
- Empty body (endpoints that accept none, e.g., `/reactivate`) hashes as the hash
  of the empty string.
- Ignore:
  - headers (except `Idempotency-Key` itself, which is part of the storage key)
  - query string (mutating endpoints in this service have none that affect state)
  - transport artifacts (`Content-Length`, TLS session)
- Include:
  - request body (canonicalized)

Client-side timestamps inside the body (if any) **are** part of the hash. Clients
should not include "now" in the request body unless they accept that a retry with
the same key but a re-generated timestamp will hit `DUPLICATE_REQUEST`.

---

## Control Flow

### Request Entry

1. Extract `Idempotency-Key` header. If missing on a mutating endpoint → `400`.
2. Compute storage key = `SHA-256("{key}:{method}:{path}")`.
3. Compute request body hash = `SHA-256(canonicalize(body))`.
4. `GET` storage key from Redis.
   - **Hit**, stored `requestHash == current requestHash` → return stored status
     + body. Do **not** re-execute the domain logic. Stop.
   - **Hit**, stored `requestHash != current requestHash` → return `409
     DUPLICATE_REQUEST`. Stop.
   - **Miss** → continue to step 5.
5. Acquire a short-lived Redis distributed lock keyed by the storage key
   (`SET NX EX 30s`) to prevent concurrent duplicates from racing into the domain.
6. Re-`GET` storage key (cover the narrow window between step 4 and step 5). If
   now hit → same branch as step 4. If still miss → continue.
7. Execute the endpoint's domain logic (controller → application → persistence).
8. On successful commit (HTTP 2xx): write the storage entry with TTL 24h, then
   release the lock, then return the response.
9. On domain error (4xx): write the storage entry anyway — a client retry with
   the same body must see the same error, not a fresh attempt. Release the lock.
10. On internal error (5xx) or uncaught exception: **do not** write the storage
    entry. Client retries should re-attempt. Release the lock.

### Lock Semantics

- The lock is **narrow-window**: only held while the domain logic is running.
- Lock TTL: 30 seconds (longer than any expected request). If the process crashes
  mid-request, the lock auto-releases and the next retry may proceed.
- Two near-simultaneous requests with the same key will serialize: the second
  waits (or immediately returns `423 Locked` — decision: **wait up to 5s, then
  reject with `CONFLICT`**) so the first's response is available for replay.

### Read-Only Endpoints

No idempotency check. Clients MAY send `Idempotency-Key` on `GET` and the server
MUST ignore it.

---

## Failure Modes

| Failure | Behavior |
|---|---|
| Redis unreachable at entry | **Fail closed**: return `503 SERVICE_UNAVAILABLE`. Do not bypass idempotency. T1 is mandatory |
| Redis unreachable during write (post-commit) | Log the failure with alert; return the 2xx response to the client; a retry with the same key may run again (accepted risk — domain will reject duplicates via DB unique constraints for create, version mismatch for update) |
| Storage lock acquisition fails due to concurrent hold | Wait up to 5s, poll every 200ms for either lock availability or storage entry appearance. On timeout → `409 CONFLICT` with message `"Idempotent retry in progress, please retry later"` |
| Stored entry malformed (version skew after a deployment) | Treat as cache miss; overwrite on success |
| Client retries with a new key (different UUID) instead of reusing | Treated as a fresh operation. The server cannot detect this misuse. Clients are responsible |
| Clock skew between app nodes | TTL is relative to Redis node time; skew does not affect correctness |

---

## Observability

Metrics (via Micrometer):

| Metric | Tags | Meaning |
|---|---|---|
| `master.idempotency.check.count` | `outcome={first, replay, conflict, lock-timeout}` | Request classification at entry |
| `master.idempotency.replay.latency.ms` | | Time to return a cached response |
| `master.idempotency.lock.wait.ms` | | Time spent waiting for a competing lock |
| `master.idempotency.store.failure.count` | `phase={read, write}` | Redis interaction failures |
| `master.idempotency.entry.ttl.remaining.seconds` | — | Histogram of observed TTLs on replays. Useful to tune TTL |

Logs:

- On `DUPLICATE_REQUEST`: log at `WARN` with `idempotencyKey` (hashed, not raw),
  method, path, and `actorId`. Never log the raw key.
- On `lock-timeout`: log at `WARN`.
- On `Redis unavailable`: log at `ERROR` + emit alert.

Tracing:

- Every replay adds span attribute `idempotency.replayed = true`.
- Fresh execution adds `idempotency.replayed = false`.

---

## Testing Requirements

Every idempotency-sensitive test must cover:

1. **First call** — stores and returns fresh response.
2. **Replay (same body)** — returns identical response, no side effects (verify
   by event count and DB row count both unchanged).
3. **Conflict (different body)** — returns `409 DUPLICATE_REQUEST`.
4. **Key missing** — returns `400 VALIDATION_ERROR`.
5. **Key malformed** — returns `400 VALIDATION_ERROR`.
6. **TTL expiry** — after 24h simulated elapse, same key + same body re-executes
   (verify with side-effect assertion and Redis time manipulation, e.g.
   `Testcontainers` Redis with `TIME` override or manual key expiry).
7. **Concurrent same-key** — two threads send the same key + body simultaneously.
   One executes, the other replays (or waits then replays). No double-execute.
8. **Redis outage** — simulated with Testcontainers pause. Mutating endpoint
   returns `503 SERVICE_UNAVAILABLE`. No domain side effects.

Reference: `.claude/skills/testing/testcontainers/SKILL.md` for Redis
Testcontainer wiring.

---

## Implementation Notes

- Implementation lives in `application/` layer as a reusable component, invoked
  from the controller (or from a filter if the service adopts a filter-based
  approach later). The domain layer is unaware of idempotency.
- The component should expose a small interface:
  ```java
  public interface IdempotencyStore {
      Optional<StoredResponse> lookup(String key, String method, String path,
                                       String requestHash);
      void store(String key, String method, String path, String requestHash,
                 int status, Object responseBody, String contentType);
  }
  ```
- Provide an `InMemoryIdempotencyStore` for Spring profile `standalone` (per
  `.claude/skills/backend/standalone-profile/SKILL.md`) so local development
  without Redis still works.
- Generic — the interface is reusable across other wms services later
  (`inventory`, `inbound`, `outbound`). If three or more services adopt an
  identical implementation, promote it to `libs/java-web` per
  `platform/shared-library-policy.md`.

---

## Not In v1

- Purging entries older than TTL via a background sweeper (Redis handles it)
- Client-facing API for introspecting idempotency state
- Sharing keys across services (each service has its own Redis namespace)
- Tenant-scoped keys (single-tenant in v1)

---

## References

- `rules/traits/transactional.md` T1
- `specs/services/master-service/architecture.md` § Idempotency
- `specs/contracts/http/master-service-api.md` § Global Conventions → Idempotency Semantics
- `platform/error-handling.md` § Transactional Trait (`DUPLICATE_REQUEST`)
- `.claude/skills/backend/redis-session/SKILL.md` (Redis client patterns, reuse where appropriate)
- `.claude/skills/backend/standalone-profile/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
