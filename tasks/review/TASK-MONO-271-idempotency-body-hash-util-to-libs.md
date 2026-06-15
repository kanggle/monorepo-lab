# TASK-MONO-271 — Extract the idempotency body-hash utilities (Family A) into `libs/java-web-servlet`

**Status:** review

**Type:** TASK-MONO (monorepo-level — shared `libs/` change rippling into two `projects/wms-platform/` services; one atomic PR)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (cross-module shared-lib extraction with a correctness-convergence nuance; behavior-equivalence reasoning required)

---

## Goal

Two WMS REST services — `inbound-service` and `outbound-service` — each carry a **byte-near-identical private copy** of the same two generic idempotency primitives:

- `BodyHashUtil` — canonical (sorted-keys) JSON → SHA-256 body hash + `sha256hex` helper.
- `CachedBodyHttpServletRequestWrapper` — a single-read-to-replayable `HttpServletRequest` wrapper so the filter and `DispatcherServlet` can both read the body.

These are **project-agnostic** (zero WMS-domain content: no service names, API paths, or domain entities — only Jackson + `jakarta.servlet`). They belong in `libs/java-web-servlet` (the servlet-stack web-utility module that already hosts `CommonGlobalExceptionHandler`).

This is the F1 finding surfaced during the TASK-BE-391 WMS recon. It is worth doing for two reasons beyond simple dedup:

1. **The two copies have already silently diverged.** `outbound-service`'s `BodyHashUtil` was hardened in **TASK-BE-342** to round-trip through a module-free `CANONICAL_MAPPER` (a transitively-present `jackson-module-scala` made `readValue(Object.class)` return a `scala.collection.immutable.Map`, which a module-less serialiser rendered as a **content-independent** string → every body hashed equal → 409 body-conflict detection silently broke). `inbound-service`'s copy was **never** given that fix — it still parses with the caller-supplied application `ObjectMapper`. A single shared implementation makes that class of drift impossible.
2. **The in-code comments are wrong about why the duplication exists.** Both copies carry a comment asserting the duplication is *forced* by "the Hard Stop on shared-library project-specific content (CLAUDE.md § Hard Stop Rules)." HARDSTOP-03 forbids **project-specific content** in `libs/`, not the *adapter layer* as such. A generic SHA-256 canonical-JSON hasher and a generic servlet body-cache wrapper contain no project-specific content and are exactly what a shared `libs/java-web-servlet` module is for. This task corrects that misreading.

## Scope

**In scope** — one atomic PR:

1. **`libs/java-web-servlet`** — add two `public` classes under a new `com.example.web.idempotency` package:
   - `BodyHashUtil` (`computeHash(byte[], ObjectMapper)`, `normalizedJson(byte[], ObjectMapper)`, `sha256hex(byte[])`), using the **TASK-BE-342-fixed** module-free `CANONICAL_MAPPER` implementation as the single canonical source. The `ObjectMapper` parameter is retained for source-compatibility but ignored (documented).
   - `CachedBodyHttpServletRequestWrapper` (`public` class + `public` constructor + `public byte[] getCachedBody()`).
   - One canonical `BodyHashUtilTest` merging both services' assertions **plus** an explicit content-sensitivity guard (different non-trivial bodies → different hash) that locks in the BE-342 property.
2. **`inbound-service`** — add `implementation project(':libs:java-web-servlet')`; import the two lib classes in `InboundIdempotencyFilter`; **delete** the local `BodyHashUtil.java`, `CachedBodyHttpServletRequestWrapper.java`, and the now-redundant `BodyHashUtilTest.java`; add the lib import to `InboundIdempotencyFilterTest` (it uses `BodyHashUtil` as a static expected-hash helper).
3. **`outbound-service`** — same shape: dependency + imports in `OutboundIdempotencyFilter`; delete the local `BodyHashUtil.java`, `CachedBodyHttpServletRequestWrapper.java`, `BodyHashUtilTest.java`; add the lib import to `OutboundIdempotencyFilterTest`; correct the now-stale "BodyHashUtil is package-private to the filter package" comment in `IdempotencyFilterRedisIT` (cosmetic).

**Out of scope (explicitly deferred, not silent):**

- **Family B — `master-service` + `admin-service`.** These use a *different* canonicalization algorithm (`RequestBodyCanonicalizer`: recursive `JsonNode` tree-sort via `readTree`) and a differently-named wrapper (`CachedBodyHttpServletRequest`). The `readTree` approach is immune to the BE-342 scala bug but produces a **different canonical string** than the `readValue`-based Family A. Migrating Family B onto the Family-A lib impl would change those services' canonical-hash output — a behavior change requiring per-service idempotency IT to validate. It is a legitimate follow-up (F1b) but is **not** net-zero and is kept separate.
- **The idempotency filter / store-port / `StoredResponse` DTO themselves.** Each service wires its own hexagonal `IdempotencyStore(Port)` + `StoredResponse` record + service-specific `ApiErrorEnvelope`, and the two Family-A filters genuinely diverge (POST-only vs POST/PATCH/PUT/DELETE; outbound's `MAX_KEY_LENGTH` 400 guard; outbound's Micrometer metrics). Unifying the filter would lift the store port + DTO into `libs/` — a much larger contract change across each service's application layer. Deferred.

## Acceptance Criteria

- **AC-1** — `libs/java-web-servlet` contains `com.example.web.idempotency.BodyHashUtil` and `…CachedBodyHttpServletRequestWrapper` (both `public`), using the BE-342-fixed `CANONICAL_MAPPER` round-trip. The canonical `BodyHashUtilTest` passes and includes: empty/null body stability, key-order normalization, **different-bodies → different-hash**, non-JSON fallback to raw-byte hash.
- **AC-2** — `inbound-service` and `outbound-service` no longer contain their own `BodyHashUtil.java` / `CachedBodyHttpServletRequestWrapper.java` / `BodyHashUtilTest.java`; both filters import the lib classes; both `build.gradle` declare `implementation project(':libs:java-web-servlet')`.
- **AC-3 (behavior equivalence)** — For both services on the *current* classpath (no `jackson-module-scala` declared in any WMS `build.gradle`), the canonical hash output is unchanged: outbound already used `CANONICAL_MAPPER` verbatim (pure net-zero); inbound's previous caller-mapper round-trip produced an identical `LinkedHashMap`-based sorted string absent the scala module, so converging to `CANONICAL_MAPPER` is net-zero today **and** removes the latent hazard. No spec/contract change (`idempotency.md` behavior identical).
- **AC-4** — `:libs:java-web-servlet:test`, `:inbound-service:test`, `:outbound-service:test` GREEN locally (unit level; the `@Tag("integration")` Testcontainers IT runs on CI-Linux). Full required CI checks GREEN (0 failing) before merge.
- **AC-5** — `libs/java-web-servlet/README.md` notes the new idempotency utilities; the misleading "forced duplication per HARDSTOP-03" comments are removed (their reasoning was incorrect — these utilities are project-agnostic).

## Related Specs

- `projects/wms-platform/specs/services/inbound-service/idempotency.md` (§1 — behavior **unchanged**; referenced for the lifecycle the filter implements).
- `projects/wms-platform/specs/services/outbound-service/idempotency.md` (§1, §1.6 — behavior **unchanged**).
- `platform/shared-library-policy.md` — the project-agnostic test the extracted classes satisfy.

## Related Contracts

None. No HTTP or event contract changes — the Idempotency-Key request/response behavior (replay / 409 DUPLICATE_REQUEST / 503 PROCESSING) is byte-identical.

## Edge Cases

- **Empty / null body** → `sha256hex("")` (`e3b0…b855`); preserved by the lib impl.
- **Non-JSON body** (multipart, plain text) → falls back to hashing raw bytes; preserved.
- **`{}` empty JSON object vs empty body** → distinct hashes; preserved.
- **Key-order / whitespace variance** (`{"b":1,"a":2}` vs `{"a":2,"b":1}`) → same canonical hash; preserved and asserted.
- **Latent scala-module reintroduction** — if a future dependency transitively pulls `jackson-module-scala`, the single lib impl (module-free `CANONICAL_MAPPER`) keeps both services correct; the pre-extraction inbound copy would have silently broken.

## Failure Scenarios

- **Reactive gateway classpath contamination** — `libs/java-web-servlet` carries servlet API; only servlet apps may depend on it. `inbound`/`outbound` are Spring MVC servlet apps (they already have `spring-web` + `OncePerRequestFilter`), so adding the dependency is safe. No reactive gateway gains this dependency.
- **Cross-deploy idempotency window** — because AC-3 establishes the canonical output is unchanged on the current classpath, no in-flight 24h-TTL idempotency entry is invalidated by the deploy.
