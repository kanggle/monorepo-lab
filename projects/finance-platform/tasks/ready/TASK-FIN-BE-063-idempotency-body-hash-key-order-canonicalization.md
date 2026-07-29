# Task ID

TASK-FIN-BE-063

# Title

Canonicalize the idempotency request-body hash (key-order-independent) to close a false-409 replay bug

# Status

ready

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

`account-service`'s `IdempotentExecution` (fintech F1, `account-api.md`) computes
the request-body hash used to detect an `Idempotency-Key` replay-vs-conflict as
`sha256(objectMapper.writeValueAsString(payload))`, using the application's
default-configured `ObjectMapper`. **Jackson's default map/POJO serialization
does not canonicalize key order.** Two byte-identical logical requests whose
JSON keys happen to serialize in a different order (different client library
version, or a future DTO field reorder) hash differently, so replaying the
*same* logical request under the *same* `Idempotency-Key` incorrectly returns
409 `IDEMPOTENCY_KEY_CONFLICT` instead of the idempotent cached response — a
correctness defect on a fund-moving endpoint (F1 requires the SAME payload to
replay, never conflict).

After this task, `IdempotentExecution`'s payload hash MUST be **key-order
canonical**: two JSON bodies with the same keys/values in a different order
hash identically, while a genuinely different body still hashes differently.

---

# Scope

## In Scope

- `IdempotentExecution.run()`'s payload-hash computation (the one file named
  in the Goal) — replace the raw `sha256(objectMapper.writeValueAsString(...))`
  with a key-order-canonicalizing hash.
- Adopt `libs:java-web-servlet`'s `BodyHashUtil.computeHash(byte[], ObjectMapper)`
  — the monorepo's single shared implementation of this exact canonicalization
  (already used by every other servlet-stack Idempotency-Key surface,
  TASK-MONO-271/273-276) — rather than re-implementing a local canonicalizing
  mapper (see Implementation Notes for the reasoning).
- `account-service/build.gradle`: add the `libs:java-web-servlet` dependency
  (not previously declared).
- `specs/services/account-service/architecture.md`: record the new allowed
  dependency + the canonical-hash requirement in § Idempotency (F1) (doc-only,
  no behavior change to the spec's contract).
- Unit tests proving (a) key-reordered-but-identical bodies hash identically,
  (b) genuinely different bodies still hash differently, (c) existing
  EXECUTE/REPLAY/CONFLICT behavior of `IdempotentExecution` is unchanged.

## Out of Scope

- `ledger-service` or any other service's idempotency/body-hash logic (not
  named in the audit finding; a separate task if the same defect is confirmed
  there).
- Changing the `IdempotencyStore` port shape, the claim-before-execute
  protocol, or the `idempotency_keys` table schema.
- Backfilling/migrating previously-stored idempotency rows whose hash was
  computed with the old (non-canonical) algorithm — see Edge Cases (TTL
  self-heals; no migration needed).
- `account-api.md` contract changes — the HTTP-visible behavior (same key +
  same body → replay; same key + different body → 409) is unchanged, only the
  *definition of "same body"* is corrected to be order-independent, which was
  always the intended contract (fintech F1 as designed, not a new behavior).

---

# Acceptance Criteria

- [ ] Two JSON bodies with identical keys/values but different key-serialization
      order produce the identical payload hash passed to `IdempotencyStore.claim`.
- [ ] Two JSON bodies with a genuinely different value produce different
      payload hashes.
- [ ] `IdempotentExecution`'s existing EXECUTE (run + store), REPLAY (return
      cached, no re-execution), and CONFLICT (409) behaviors are unchanged
      (regression-covered).
- [ ] `account-service`'s full unit+slice test suite (`./gradlew
      :projects:finance-platform:apps:account-service:test`) is GREEN with the
      new tests included.
- [ ] `architecture.md` documents the new `libs:java-web-servlet` dependency
      and the key-order-canonical hash requirement.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (`fintech`; `transactional`, `regulated`, `audit-heavy`). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/testing-strategy.md`
- `platform/shared-library-policy.md` (§ Decision Rule — justifies depending on `libs:java-web-servlet`)
- `specs/services/account-service/architecture.md` § Idempotency (F1), § Allowed dependencies
- `rules/domains/fintech.md` (F1 — idempotent fund ops)

# Related Skills

- `.claude/skills/backend/testing-backend` (unit test conventions, STRICT_STUBS)

---

# Related Contracts

- `specs/contracts/http/account-api.md` (§ Idempotency-Key semantics — `IDEMPOTENCY_KEY_CONFLICT`)

---

# Target Service

- `account-service`

---

# Architecture

Follow:

- `specs/services/account-service/architecture.md`

---

# Implementation Notes

- **Library choice**: `libs/java-web-servlet`'s `BodyHashUtil` already exists
  for exactly this purpose and its javadoc documents this precise historical
  failure mode ("previously copied per-service; the copies silently
  diverged"). `account-service` is already a servlet-stack `rest-api` service
  (matches the module's documented consumer set — "servlet services"), and the
  shared-library-policy Decision Rule checks all pass (used by ≥1 other
  service already via the same idempotency-filter pattern in wms/iam;
  technical, not domain; stable API; reduces duplication without adding
  coupling). Building a second local canonicalizing `ObjectMapper` inside
  account-service would re-create the exact "silently diverged copy" problem
  the shared utility exists to prevent, so the library dependency is the
  correct choice over a local reimplementation, even though it is the only
  consumer of `BodyHashUtil` in this file.
- `BodyHashUtil.computeHash(byte[] bodyBytes, ObjectMapper mapper)` operates on
  raw bytes (its usual caller is a servlet filter reading the raw request
  body) and **ignores** the `mapper` parameter, always parsing+re-serializing
  with its own module-free `CANONICAL_MAPPER` (sorted keys). `IdempotentExecution`
  does not have raw bytes — it has a `Supplier`-erased `Object requestPayload` —
  so the fix first serializes the payload with the existing application
  `ObjectMapper` (`toJson(requestPayload)`) to get a JSON string, UTF-8-encodes
  it, and passes those bytes to `BodyHashUtil.computeHash`. `BodyHashUtil`
  re-parses that JSON text with its own vanilla mapper and re-serializes with
  sorted keys before hashing, which correctly canonicalizes key order
  regardless of which mapper produced the initial JSON text.
- `IdempotencyStore` javadoc + `RedisOrDbIdempotencyStore` both confirm the
  idempotency record TTL is `financeplatform.account.idempotency.ttl-seconds`
  (default `86400` = 24h) for both the Redis primary and DB fallback — see
  Edge Cases below for why this means no backfill/migration is required.

---

# Edge Cases

- **Hash-value change for previously-non-canonical bodies**: any body whose
  JSON key order was non-canonical before this change will now hash
  differently than a row already stored under the old (non-canonical) hash
  for the same `Idempotency-Key`. This is **not** a migration hazard here:
  the idempotency store is TTL-bounded (`ttl-seconds` default `86400` = 24h,
  both Redis and the DB fallback per `RedisOrDbIdempotencyStore`), not a
  long-lived audit store — old-hash rows self-expire within 24h of the
  deploy, and until they expire a genuine same-key retry with the
  now-differently-hashed value would surface as `CONFLICT` (409) rather than
  silently misbehaving (fail-safe direction: worst case is an extra 409 on a
  retry that was ALREADY ambiguous under the old bug, never a false replay of
  the wrong body). No backfill/migration script needed.
- Empty/`null` request payload (`toJson(null) == "null"`) must still hash
  consistently (both before and after this fix — the `"null"` literal has no
  key ordering to canonicalize).
- Deeply nested payload objects (maps within maps) — `BodyHashUtil`'s
  `CANONICAL_MAPPER` sorts recursively at every level via
  `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` +
  `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`, not just the top level.

---

# Failure Scenarios

- Two client requests carrying the same `Idempotency-Key` and semantically
  identical bodies (same keys/values, different serialization order) MUST
  both resolve to REPLAY of the single stored response — never CONFLICT.
- A genuinely different body reusing the same `Idempotency-Key` MUST still
  resolve to CONFLICT (409 `IDEMPOTENCY_KEY_CONFLICT`) — the fix must not
  weaken conflict detection to "anything goes."

---

# Test Requirements

- Unit test (`IdempotentExecutionTest`, Mockito `STRICT_STUBS`):
  - key-reordered-but-identical bodies → identical hash passed to
    `IdempotencyStore.claim`.
  - genuinely different bodies → different hash.
  - EXECUTE outcome → action runs once, `complete()` stores the 2xx response.
  - REPLAY outcome → stored response returned, action NOT re-invoked.
  - CONFLICT outcome → `IdempotencyKeyConflictException` thrown.
- No integration-test change required — the existing
  `IdempotencyConcurrencyIntegrationTest` (Testcontainers) already drives the
  production `IdempotentExecution` end-to-end and remains valid unchanged.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed (not needed — see Scope)
- [ ] Specs updated first if required (architecture.md § Idempotency / Allowed dependencies)
- [ ] Ready for review
