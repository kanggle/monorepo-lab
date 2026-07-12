# ADR-MONO-038 — Lift the REST Idempotency-Key filter skeleton, storage port, and stored-response DTO into `libs/java-web-servlet` as a configurable shared abstraction

**Status:** ACCEPTED

**Date:** 2026-06-15

**History:** PROPOSED 2026-06-15 (TASK-MONO-272 — records the **shared servlet Idempotency-Key filter model**: how the four WMS services that implement the REST Idempotency-Key lifecycle [`inbound`, `outbound`, `master`, `admin`] collapse their ~80%-identical per-service filters onto one configurable abstraction in `libs/java-web-servlet`, after TASK-MONO-271 already lifted the two *pure-utility* primitives [`BodyHashUtil` + `CachedBodyHttpServletRequestWrapper`] for the `readValue` family. The remaining duplication — the filter control flow, the `IdempotencyStore(Port)`, and the `StoredResponse` record — cannot be lifted by a plain mechanical extraction: **moving an application-layer port abstraction into a shared library is a cross-service architecture decision** [hexagonal ownership, canonicalizer unification, error-contract stance], so a task that did it without a record would bake those postures in code → HARDSTOP-09. This ADR records the six decisions [I1–I6]. **Doc-only; ACCEPTED + implementation are separate user-explicit-intent-gated tasks [staged-child pattern, ADR-019/020/021/023/024/032/033/034/035/036/037]. Self-ACCEPT prohibited.**) · ACCEPTED 2026-06-15 (TASK-MONO-272 — user-explicit *"accept 할게"* after the PROPOSED decisions [I1–I6] were presented for the explicitly-required ACCEPT gate; the gate was honored — the PROPOSED record was presented and review awaited before any flip, **NOT a self-ACCEPT**. I1–I6 CHOSEN-PROPOSED direction **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical to the PROPOSED draft; flip = Status + this clause + § 6 ACCEPTED row + § 3.3 PAUSED→UNPAUSED. Delivered in the same PR as the PROPOSED record [the staged-child governance trail is preserved *within* the PR: both § 6 rows + ADR-003a audit rows #45 PROPOSED / #46 ACCEPTED, mirroring ADR-033/034/035/036/037]. TASK-MONO-271 deferral discharged into the M1–M4 roadmap.)

**Builds on:** TASK-MONO-271 (2026-06-15, PR #1662) — extracted `com.example.web.idempotency.BodyHashUtil` + `CachedBodyHttpServletRequestWrapper` into `libs/java-web-servlet` (Family A: `inbound` + `outbound`). That task **explicitly deferred** the filter/store-port/DTO unification "as a larger contract change across each service's application layer" and the Family-B (`master`/`admin`) canonicalizer convergence "as a behavior change requiring per-service IT." ADR-038 is the design record for those deferrals.

**Related:**
- `libs/java-web-servlet` — the servlet-stack web-utility module the abstraction lands in (already hosts `CommonGlobalExceptionHandler` [TASK-MONO-044a] + the MONO-271 idempotency utilities). Reactive gateways (Spring Cloud Gateway / WebFlux) MUST NOT depend on it.
- `projects/wms-platform/specs/services/{inbound,outbound,master,admin}-service/idempotency.md` — the per-service Idempotency-Key lifecycle the shared filter must preserve byte-for-byte (replay / 409 `DUPLICATE_REQUEST` / 503 `PROCESSING`).
- `platform/shared-library-policy.md` — the project-agnostic test the lifted abstraction must satisfy (the abstraction carries no WMS-domain content; service-specific concerns stay service-side via configuration/strategy).
- TASK-BE-342 — the `jackson-module-scala` content-independent-hash bug whose fix is the canonical `BodyHashUtil` impl (now in lib).

---

## 1. Context

### 1.1 As-built: four services, four near-identical idempotency filters (code-verified 2026-06-15)

All four WMS write services implement the same REST Idempotency-Key lifecycle, each with its own private copy of the machinery:

```
 SERVICE     FILTER                       STORE PORT             DTO              CANONICALIZER (algorithm)         WRAPPER
 inbound     InboundIdempotencyFilter     IdempotencyStorePort   StoredResponse   BodyHashUtil  (readValue) ──┐     (lib, MONO-271)
 outbound    OutboundIdempotencyFilter    IdempotencyStore       StoredResponse   BodyHashUtil  (readValue) ──┤A    (lib, MONO-271)
 master      IdempotencyFilter            IdempotencyStore       StoredResponse   RequestBodyCanonicalizer ───┐     CachedBodyHttpServletRequest
 admin       IdempotencyFilter            IdempotencyStore       StoredResponse   RequestBodyCanonicalizer ───┤B    CachedBodyHttpServletRequest
                                                                                  (readTree tree-sort)
```

The filter control flow is **~80% identical** across all four: skip non-mutating / webhook requests → require `Idempotency-Key` header → cache the body → compute a canonical body hash → look up `{METHOD}:{sha256(uri)}:{key}` → hit-same-hash replay / hit-different-hash 409 / miss → acquire lock (503 if held) → proceed, cache the 2xx, release the lock, flush. Each service re-derives this independently.

### 1.2 Two canonicalizer families, and a within-family divergence

- **Family A (`readValue` → module-free `CANONICAL_MAPPER`)** — `inbound` + `outbound`. Lifted to `libs/java-web-servlet` by MONO-271. Carries the TASK-BE-342 fix (a transitive `jackson-module-scala` otherwise makes `readValue(Object.class)` content-independent → broken 409 detection).
- **Family B (`readTree` recursive tree-sort)** — `master` + `admin`. Immune to the BE-342 scala bug (readTree always yields `JsonNode`), but produces a **different canonical string** than Family A, and the two copies have **themselves diverged**: `master`'s `RequestBodyCanonicalizer` catches `JsonProcessingException | RuntimeException` (malformed/non-JSON → raw-byte UTF-8 fallback); `admin`'s catches only `JsonProcessingException`. The cached-body wrappers also differ cosmetically (field names, formatting, visibility).

This is the maintenance hazard a single shared abstraction removes — and exactly why it needs a decision, not a mechanical lift: **unifying the canonicalizer changes ≥1 service's hash output**, and reconciling master-vs-admin requires choosing a fallback.

### 1.3 Per-service filter divergences (must become configuration, not be lost)

- **Applicable methods** — `inbound`: POST only; `outbound`: POST/PATCH/PUT/DELETE; `master`/`admin`: their own sets.
- **Key-length guard** — `outbound` rejects keys > 255 chars with 400 `VALIDATION_ERROR`; the others don't.
- **Metrics** — `outbound` emits Micrometer counters/timer (`outbound.idempotency.lookup.{count,duration}`, `…store.failure`); the others don't.
- **API path prefix** — each service scopes to its own `/api/v1/<svc>/`.
- **Error envelope** — each service writes its own `ApiErrorEnvelope` (different packages, same shape).
- **Fail-open posture** — all four log + proceed when the store is unavailable (WMS availability-over-correctness); domain-layer unique constraints backstop double effects.

### 1.4 Why this is HARDSTOP-09 (architecture decision, not a refactor)

Lifting the filter alone is impossible without also lifting (or abstracting) the `IdempotencyStore` **port** and the `StoredResponse` **DTO** it returns — both currently live in each service's *application* layer (hexagonal `port.out`). Moving a port abstraction into a shared library is an ownership decision: the lib would define the contract the services' Redis/in-memory adapters implement. Doing that in code without a record would silently bake (a) the hexagonal-ownership stance, (b) the canonicalizer-unification stance (and its behavior change), and (c) whether the error envelope becomes shared. Each is a posture worth recording. This ADR decides them; implementation is post-ACCEPTED.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; to be finalised (byte-unchanged) at ACCEPTED per the staged-child pattern. **No code / port / DTO change in this ADR.** Grounded in the 2026-06-15 code investigation (four filters; Family A/B canonicalizer split; master↔admin exception-handling divergence; per-service method/key-length/metrics/prefix/envelope divergences; fail-open posture).

### I1 — A configurable shared `IdempotencyKeyFilter` in `libs/java-web-servlet` (composition over inheritance)

Provide one concrete `OncePerRequestFilter` subclass in the lib, parameterized by an `IdempotencyFilterConfig` value object: applicable HTTP methods, a path-applicability predicate, optional max-key-length, an optional metrics binder (I5), a body-canonicalizer strategy (I3), and an error-response writer (I4). Services register it with their own config. **Rejected:** an abstract base class requiring per-service subclassing (keeps a boilerplate subclass per service); leave-as-is (duplication persists, and the Family-B divergence festers).

### I2 — Lift the storage contract into the lib: a lib-owned `IdempotencyStore` interface + `StoredResponse` record

The lib defines `IdempotencyStore` (`lookup(key) → Optional<StoredResponse>`, `tryAcquireLock(key, ttl) → boolean`, `put(key, StoredResponse, ttl)`, `releaseLock(key)`) and the `StoredResponse` record (`requestHash`, `status`, `bodyJson`, `contentType`, `storedAt`). Each service keeps its **adapter** (Redis / in-memory) but implements the lib interface; the per-service `IdempotencyStorePort` / `IdempotencyStore` / `StoredResponse` duplicates are removed. This is project-agnostic — the interface names a generic key→stored-response store with locking, no WMS-domain content (satisfies `shared-library-policy.md`). **Rejected:** keep per-service ports and share only the filter via generics (`<S extends …>`) — leaky, every service still declares the port + DTO; share nothing (status quo).

### I3 — Canonicalizer = pluggable strategy; converge Family B's two copies onto the lenient superset; defer single-algorithm unification

The filter takes a `BodyCanonicalizer` strategy. Family A injects the existing lib `BodyHashUtil` (net-zero). Family B's `readTree` canonicalizer is lifted to the lib **once**, reconciling the master↔admin divergence by adopting **master's lenient fallback** (`JsonProcessingException | RuntimeException` → raw-byte hash) as the shared behavior — a superset that subsumes admin's narrower catch (a tiny, safe behavior addition for admin, effectively a bugfix). **Unifying Family A and Family B onto a single algorithm is explicitly deferred** (it changes inbound/outbound *and* master/admin hash output; low runtime risk under 24h-TTL self-compared entries, but a follow-up gated on per-service IT). **Rejected:** force single-algorithm unification now (largest behavior change, weakest local verifiability — Testcontainers IT is CI-Linux-only); keep canonicalizers fully per-service (the Family-B divergence persists).

### I4 — Error-response shape stays service-owned via a writer strategy (no shared error contract)

The lib does **not** impose a shared error envelope. The filter takes an `IdempotencyErrorWriter` functional strategy (`writeConflict`, `writeProcessing`, `writeKeyTooLong`) that each service implements with its own `ApiErrorEnvelope`. Keeps the HTTP error contract service-owned and avoids a cross-service error-DTO change. **Rejected:** a shared lib error envelope (forces an error-contract change across four services + couples them to a lib DTO).

### I5 — Metrics optional via an injected binder

Outbound's Micrometer instrumentation becomes an optional `IdempotencyMetrics` binder injected through the config; services without metrics pass a no-op. The filter's hot path checks for null/no-op once. **Rejected:** mandatory metrics (forces a `MeterRegistry` dependency on services that don't instrument); drop outbound's metrics (a behavior/observability regression).

### I6 — Staged, additive, net-zero-where-possible migration; preserve safety invariants

Migrate per service behind the abstraction in stages, additive and net-zero per service except the one acknowledged behavior step (I3 Family-B reconciliation, gated on IT). Invariants the implementation must hold: (1) the Idempotency-Key HTTP behavior (replay / 409 / 503 / 400-key-too-long where present) is byte-identical per service; (2) the fail-open posture is preserved (store-unavailable → log + proceed); (3) per-service method/path/key-length/metrics config is preserved exactly; (4) `libs/java-web-servlet` stays servlet-only — no reactive gateway gains the dependency; (5) no idempotency *spec*/contract change.

---

## 3. Consequences

### 3.1 Positive

- One filter control flow, one store contract, one stored-response DTO — the ~80% filter duplication and the per-service port/DTO triplication collapse to a single tested implementation.
- The Family-B master↔admin divergence is resolved (I3), and future drift is structurally prevented (the MONO-271 lesson generalized to the whole filter).
- New servlet services needing idempotency get it by configuration, not by copying ~250 LOC.
- Strong portfolio signal: a shared-lib abstraction that correctly keeps service-specific concerns (error envelope, metrics, path/method policy) at the edges via strategy/config.

### 3.2 Negative / cost

- A non-trivial refactor touching four services' application + adapter layers; each needs its idempotency IT (Testcontainers, CI-Linux-authoritative) to confirm behavior preservation.
- The I3 Family-B reconciliation is a (small) behavior change for `admin`; must be called out in the implementing PR, not shipped as silent net-zero.
- One more lib abstraction to own; the config surface must stay small enough that it's genuinely simpler than four copies.

### 3.3 Execution roadmap — **UNPAUSED (ACCEPTED 2026-06-15)**

> ACCEPTED — implementation is now unblocked. The steps below are implement-ready tasks (separate user-gated tasks; each preserves the safety invariants in I6).

- **M1** — lib: `IdempotencyStore` interface + `StoredResponse` record + `IdempotencyFilterConfig` + `IdempotencyKeyFilter` + `BodyCanonicalizer` strategy (Family-A `BodyHashUtil` adapter + lifted Family-B `readTree` canonicalizer on the lenient superset) + `IdempotencyErrorWriter` + optional `IdempotencyMetrics`. Unit-tested in the lib.
- **M2** — migrate `inbound` + `outbound` (Family A, net-zero) onto the shared filter; per-service IT GREEN.
- **M3** — migrate `master` + `admin` (Family B); reconcile the canonicalizer (I3); per-service IT GREEN; call out admin's fallback addition. **Implementation finding (TASK-MONO-275):** the Family-B *filters* are **not** force-unified onto the Family-A `IdempotencyKeyFilter` — see § 5 "M3 implementation finding". master/admin keep their own `IdempotencyFilter`; only the **leaf** abstractions (`BodyCanonicalizer`/`JsonTreeBodyCanonicalizer`, `CachedBodyHttpServletRequestWrapper`, `IdempotencyStore`, `StoredResponse`) are shared. Net-zero, behavior-preserved.
- **M4 (TASK-MONO-276)** — final residual sweep + spec/README cross-references. **Finding:** a **fifth** idempotency consumer was discovered — `inventory-service` — which uses the store **programmatically** (`ReserveStockService`, no REST filter), carrying its own byte-identical `IdempotencyStore` port + `StoredResponse` record. M4 migrated those to the lib `IdempotencyStore` / `StoredResponse` too (net-zero; inventory's programmatic usage + Redis adapter unchanged), completing the I2 store/DTO unification across all five wms idempotency consumers. Sweep confirmed **zero** remaining duplicate `StoredResponse` records, `IdempotencyStore` interfaces, `RequestBodyCanonicalizer`, or `CachedBodyHttpServletRequest` classes; only the two Family-B `IdempotencyFilter` classes + the per-service Redis/in-memory adapters + per-service filter config/error-writer/metrics legitimately remain. Stale spec references to the deleted `OutboundIdempotencyFilter` + master's illustrative interface sketch were corrected; the master spec's own "promote to `libs/` once 3+ services adopt it" prediction is now marked fulfilled.
- **Deferred follow-up** — single-algorithm canonicalizer unification across Family A + B (separate, IT-gated). (The Family-B *filter* unification is **declined**, not deferred — see § 5.)

---

## 4. Alternatives considered (rejected)

- **A — Leave as-is.** Four filters, four ports, four DTOs, a live Family-B divergence, and a latent drift hazard. Rejected: the duplication is real and MONO-271 already proved the drift is not hypothetical.
- **B — Abstract base filter (inheritance).** A lib base class each service subclasses. Rejected in favor of composition (I1): subclassing keeps a per-service class and couples services to the base's protected surface; a configured concrete filter is cleaner.
- **C — Share only the filter, keep per-service ports/DTOs.** Generic the filter over each service's store/DTO. Rejected (I2): leaky — every service still declares the port + DTO; the triplication remains.
- **D — Shared lib error envelope.** Lift the error DTO too. Rejected (I4): forces an HTTP-error-contract change across four services and couples them to a lib DTO; the writer strategy keeps the contract service-owned.
- **E — Force single-canonicalizer unification now.** Collapse Family A + B onto one algorithm immediately. Rejected (I3): largest behavior change, weakest local verifiability; staged as a deferred IT-gated follow-up instead.

---

## 5. Relationship to existing decisions / code

- **TASK-MONO-271** — direct parent: lifted the two pure utilities for Family A and explicitly deferred this filter/port/DTO unification + Family-B convergence. ADR-038 is that deferral's design record.
- **TASK-MONO-044a / ADR context for `libs/java-web-servlet`** — the servlet-only module boundary (reactive gateways must not depend on it) is reaffirmed as invariant (4).
- **TASK-BE-342** — the canonical `BodyHashUtil` (Family A, in lib) carries this fix; I3 keeps it and lifts the Family-B canonicalizer alongside as a separate strategy.
- **`shared-library-policy.md` / HARDSTOP-03** — the lifted abstraction (filter + generic store interface + DTO) is project-agnostic; service-specific concerns (error envelope, metrics, path/method policy) stay at the edges. ADR-038 does not relax HARDSTOP-03; it satisfies it by construction.
- **WMS idempotency specs** (`idempotency.md` ×4) — unchanged; the implementation must preserve their behavior byte-for-byte.

### M3 implementation finding (TASK-MONO-275) — Family-B filters stay service-specific

During M3, the `master`/`admin` (Family B) filters were found to diverge from the Family-A `IdempotencyKeyFilter` on **five behavioral axes**, each spec'd in their `idempotency.md` and pinned by master's `IdempotencyFilterTest`:

| Axis | Family A (`IdempotencyKeyFilter`) | Family B (`master`/`admin`) |
|---|---|---|
| Key validation | none (absent header → controller 400) | **UUID-required** → 400 `VALIDATION_ERROR` |
| Store-error posture | **fail-open** (proceed) | **fail-closed** → 503 `SERVICE_UNAVAILABLE` |
| Lock | single-try → 503 `PROCESSING` | **bounded wait-poll** → 409 `CONFLICT` on timeout |
| Cache policy | 2xx only | **< 500** (4xx cached too) |
| Storage key | `{METHOD}:sha256(uri):{key}` | `sha256({key}:{method}:{uri})` |

Forcing Family B onto the Family-A filter would **break these documented, tested behaviors** — i.e. it is **not** behavior-preserving (it would be Alternative B "change master/admin to Family-A semantics", which this ADR did not choose). Generalizing the Family-A filter with config knobs for all five axes (Alternative-A-style) was judged to over-complicate the shared filter beyond the value of unifying two more services.

**Decision (M3):** unify only the **leaf** abstractions for Family B — the `JsonTreeBodyCanonicalizer` (I3 lenient superset, resolving the master↔admin divergence), `CachedBodyHttpServletRequestWrapper`, `IdempotencyStore`, and `StoredResponse`. master/admin **retain their own `IdempotencyFilter`** (distinct control flow), now delegating to the shared leaf abstractions. This is net-zero and behavior-preserving, removes the real leaf duplication, and keeps the lib filter focused on the Family-A shape. The full Family-B filter unification (via filter generalization) is **declined** as not worth the config complexity; the canonical-algorithm unification across Family A+B remains the only deferred follow-up.

This refines — does not contradict — I1/I6: I1's "one configurable filter" holds for Family A; Family B shares the leaf strategies the ADR introduced (I2/I3/I4) while keeping a service-specific filter, which I6's "behavior byte-identical per service" invariant requires.

---

This ADR does not re-decide any prior ADR; it is a self-contained infrastructure decision in the `libs/` governance lineage (sibling in *form* to the staged-child ADRs, unrelated in *topic* to the ADR-032 identity family).

---

## 6. Status history

| Date | Status | Task | Note |
|---|---|---|---|
| 2026-06-15 | PROPOSED | TASK-MONO-272 | I1–I6 CHOSEN-PROPOSED published for the explicitly-required ACCEPT gate. Doc-only. § 3.3 PAUSED. Self-ACCEPT prohibited. |
| 2026-06-15 | ACCEPTED | TASK-MONO-272 | User-explicit *"accept 할게"* after the gate was presented (not self-ACCEPT). I1–I6 finalised byte-unchanged; flip = Status + History clause + this row + § 3.3 UNPAUSED. § 3.3 M1–M4 roadmap now implement-ready (separate user-gated tasks). |

---

## 7. Provenance

- **Trigger** — TASK-MONO-271 (F1 idempotency utility extraction) surfaced that the remaining filter/port/DTO duplication is a HARDSTOP-09 architecture decision, and the recommended next step was "author the ADR" (user-explicit *"진행"* 2026-06-15).
- **Evidence** — 2026-06-15 code investigation of the four WMS idempotency filters (`InboundIdempotencyFilter`, `OutboundIdempotencyFilter`, master/admin `IdempotencyFilter`), their store ports (`IdempotencyStorePort` / `IdempotencyStore`), `StoredResponse` records, canonicalizers (`BodyHashUtil` readValue vs `RequestBodyCanonicalizer` readTree), and the master↔admin exception-handling divergence.
- **Scope discipline** — doc-only; ACCEPTED + implementation (M1–M4 + deferred unification) are separate user-explicit-intent-gated tasks (staged-child pattern). Self-ACCEPT prohibited.
