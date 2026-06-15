# TASK-MONO-273 — ADR-MONO-038 M1: build the shared Idempotency-Key filter abstraction in `libs/java-web-servlet`

**Status:** done

**Type:** TASK-MONO (monorepo-level — additive `libs/java-web-servlet` change; lib-only, no service migration)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (the shared abstraction + control-flow port; behavior-equivalence reasoning)

---

## Goal

Implement **M1** of the ADR-MONO-038 execution roadmap: the shared, configurable Idempotency-Key filter abstraction in `libs/java-web-servlet`, **without** migrating any service (M2/M3 do that). This step is **additive** — it introduces new lib classes + lib unit tests and changes no service, so it is net-zero for `inbound`/`outbound`/`master`/`admin`.

Per ADR-MONO-038 § 3.3 M1, the lib gains: `IdempotencyStore` interface (I2), `StoredResponse` record (I2), `BodyCanonicalizer` strategy + the two implementations (I3 — `JsonValueBodyCanonicalizer` for Family A via the existing `BodyHashUtil`; `JsonTreeBodyCanonicalizer` for Family B via `readTree` tree-sort on master's lenient superset fallback), `IdempotencyErrorWriter` strategy (I4), `IdempotencyMetrics` SPI with a no-op (I5, Micrometer-free), `IdempotencyFilterConfig` value object, and the `IdempotencyKeyFilter` (I1, composition) carrying the unified control flow.

## Scope

**In scope (lib-only):**

1. `com.example.web.idempotency.StoredResponse` — record `(requestHash, status, bodyJson, contentType, storedAt)`.
2. `…IdempotencyStore` — interface: `lookup`, `tryAcquireLock`, `put`, `releaseLock`.
3. `…BodyCanonicalizer` — functional interface `String hash(byte[] body)`; `JsonValueBodyCanonicalizer` (delegates to `BodyHashUtil.computeHash`) + `JsonTreeBodyCanonicalizer` (readTree recursive sort → `sha256hex`, lenient `JsonProcessingException | RuntimeException | IOException` → raw-byte fallback = master's superset, which subsumes admin's narrower catch).
4. `…IdempotencyErrorWriter` — interface: `writeConflict` (409), `writeProcessing` (503 + Retry-After), `writeKeyTooLong` (400; default throws `IllegalStateException` so a misconfigured key-length guard fails loudly).
5. `…IdempotencyMetrics` — SPI: `recordLookup(result, durationNanos)`, `recordStoreFailure()`, `NO_OP` constant. Micrometer-free (the lib must not gain a Micrometer dependency).
6. `…IdempotencyFilterConfig` — value object (builder): applicable methods, path-applicability predicate, `maxKeyLength` (≤0 = unlimited), `lockTtl`, `entryTtl`; `shouldApply(HttpServletRequest)`.
7. `…IdempotencyKeyFilter` extends `OncePerRequestFilter` — the unified lifecycle (skip-not-applicable → require header → optional key-length guard → cache body → canonical hash → `{METHOD}:sha256(uri):key` storage key → lookup hit-same-replay / hit-diff-409 / miss → lock (503-if-held) → proceed, cache 2xx, release, flush), fail-open on store errors, metrics at each terminal decision.
8. Lib unit tests for the canonicalizers + the filter (in-memory store + Spring mock servlet objects). Add `testImplementation 'org.springframework:spring-test'` to `libs/java-web-servlet/build.gradle`.
9. `libs/java-web-servlet/README.md` — list the new idempotency abstraction classes.

**Out of scope (later M-steps):** migrating `inbound`/`outbound` (M2), `master`/`admin` (M3), removing per-service duplicates (M4), single-algorithm canonicalizer unification (deferred). No service code changes in this task.

## Acceptance Criteria

- **AC-1** — All nine classes/interfaces above exist under `com.example.web.idempotency`, project-agnostic (no WMS-domain content; service concerns stay behind the strategies/config).
- **AC-2** — `IdempotencyKeyFilter` reproduces the inbound/outbound control flow: replay on same-key+same-hash, 409 on same-key+different-hash, 503 on lock-held, 400 on over-length key (when configured), fail-open (store error → log + proceed), always release the lock + `copyBodyToResponse`.
- **AC-3** — `JsonTreeBodyCanonicalizer` reproduces master's `RequestBodyCanonicalizer` byte-for-byte semantics (empty/null → `sha256("")`; sorted-keys canonical JSON; non-JSON/malformed → raw-byte hash) — the lenient superset that also covers admin.
- **AC-4** — `JsonValueBodyCanonicalizer` delegates to the existing `BodyHashUtil` (Family A) unchanged.
- **AC-5** — `IdempotencyMetrics` carries no Micrometer import; `NO_OP` is the filter default.
- **AC-6** — `:libs:java-web-servlet:test` GREEN locally. No service module changed (verify `git status` touches only `libs/java-web-servlet` + the task file).

## Related Specs

- `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` (§ 2 I1–I6, § 3.3 M1).
- `projects/wms-platform/specs/services/{inbound,outbound,master,admin}-service/idempotency.md` (the lifecycle the abstraction must preserve when M2/M3 migrate).
- `platform/shared-library-policy.md` (project-agnostic test).

## Related Contracts

None. No HTTP/event contract change — this is an internal lib abstraction; nothing consumes it yet.

## Edge Cases

- **Empty / null body** → `sha256("")` for both canonicalizers (parity preserved).
- **Non-JSON / malformed body** → raw-byte hash (the lenient `JsonTreeBodyCanonicalizer` superset; `JsonValueBodyCanonicalizer` already falls back via `BodyHashUtil`).
- **Over-length key without a writer** → `writeKeyTooLong` default throws `IllegalStateException` (loud misconfiguration, not silent).
- **Store unavailable** → fail-open (log + proceed), metrics `recordStoreFailure`.
- **No metrics configured** → `IdempotencyMetrics.NO_OP` (single null/no-op check, no hot-path cost).

## Failure Scenarios

- **Reactive-classpath contamination** — abstraction lives in `libs/java-web-servlet` (servlet-only); reactive gateways never depend on it. M1 adds no new runtime dependency to the module beyond what it already has (spring-web/webmvc, jakarta.servlet, jackson) + a test-only `spring-test`.
- **Unused-API concern** — the classes are public lib API consumed by M2/M3; M1 exercises them via lib unit tests so they are not dead.
