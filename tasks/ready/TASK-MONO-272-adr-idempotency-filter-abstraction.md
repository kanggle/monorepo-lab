# TASK-MONO-272 — ADR-MONO-038: a shared servlet Idempotency-Key filter abstraction in `libs/java-web-servlet`

**Status:** ready

**Type:** TASK-MONO (monorepo-level — authors a cross-service architecture decision record; doc-only)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (cross-service architecture decision; lifting an application-layer port into a shared lib)

---

## Goal

Author **ADR-MONO-038** recording the decision to lift the REST Idempotency-Key **filter skeleton + storage port + stored-response DTO** into `libs/java-web-servlet`, so the four WMS services (`inbound`, `outbound`, `master`, `admin`) stop each carrying a ~80%-identical idempotency filter.

This is the design-first step the **TASK-MONO-271** F1 follow-up requires. MONO-271 already lifted the two *pure utility* primitives (`BodyHashUtil` + `CachedBodyHttpServletRequestWrapper`) for Family A. The remaining duplication — the filter control flow, the `IdempotencyStore(Port)`, and the `StoredResponse` record — cannot be lifted by a plain mechanical extraction because **moving an application-layer port abstraction into a shared library is a cross-service architecture decision** (HARDSTOP-09): a task that did it without a recorded decision would bake the hexagonal-ownership stance, the canonicalizer-unification stance, and the error-contract stance in code.

ADR-MONO-038 is that record. It is **doc-only**; ACCEPTED + the implementation task(s) are separate, user-explicit-intent-gated (staged-child pattern, sibling ADR-019/020/021/023/024/032/033/034/035/036/037). **Self-ACCEPT prohibited.**

## Scope

**In scope (this task):**

1. `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` — PROPOSED, with the as-built duplication analysis (4 services, the Family A/B canonicalizer split, the master↔admin exception-handling divergence, the per-service filter divergences) and decisions I1–I6 (CHOSEN-PROPOSED).
2. `docs/adr/ADR-MONO-003a-…` § 3 audit log — append row #45 (ADR-038 PROPOSED publish). Rows #1–#44 byte-unchanged.
3. On the explicitly-required ACCEPT gate (user-gated, same PR per the ADR-036/037 precedent): flip Status PROPOSED → ACCEPTED + History clause + § 6 ACCEPTED row + § 3.3 UNPAUSED + audit row #46.

**Out of scope (post-ACCEPTED implementation tasks):** the actual `libs/java-web-servlet` filter/port/DTO classes, the per-service migration, the Family-B canonicalizer reconciliation, and the per-service idempotency IT. Those are separate WMS/MONO tasks gated on this ADR's ACCEPT.

## Acceptance Criteria

- **AC-1** — ADR-MONO-038 exists at PROPOSED with: § 1 Context (the as-built duplication, code-verified), § 2 Decision (I1–I6 CHOSEN-PROPOSED), § 3 Consequences (incl. § 3.3 execution roadmap PAUSED-until-ACCEPTED), § 4 Alternatives, § 5 Relationship (to MONO-271 + the WMS idempotency specs), § 6 status-history table, § 7 Provenance.
- **AC-2** — ADR-003a audit row #45 appended; rows #1–#44 byte-identical.
- **AC-3** — Doc-only PR; `changes` (+ any doc smoke) GREEN; no code/contract change.
- **AC-4 (ACCEPT gate)** — The PROPOSED decisions are *presented* to the user for the explicitly-required ACCEPT gate; only on the user's explicit ACCEPT is the flip applied (Status + History + § 6 + § 3.3 + audit #46) — **not a self-ACCEPT**.

## Related Specs

- `projects/wms-platform/specs/services/{inbound,outbound,master,admin}-service/idempotency.md` — the per-service Idempotency-Key lifecycle the shared filter must preserve (behavior-unchanged constraint).
- `platform/shared-library-policy.md` — the project-agnostic test the lifted abstraction must satisfy.
- `libs/java-web-servlet/README.md` — the module the abstraction lands in (already hosts `CommonGlobalExceptionHandler` + the MONO-271 idempotency utilities).

## Related Contracts

None. The ADR records an internal architecture decision; the Idempotency-Key request/response behavior (replay / 409 DUPLICATE_REQUEST / 503 PROCESSING) is to be preserved by the eventual implementation.

## Edge Cases

- **Family A vs Family B canonicalizers** — `readValue`+CANONICAL_MAPPER (inbound/outbound, in lib since MONO-271) vs `readTree` tree-sort (master/admin). The ADR must decide whether to unify onto one algorithm (behavior change) or keep a pluggable canonicalizer strategy (net-zero).
- **master ↔ admin divergence** — master's `RequestBodyCanonicalizer` catches `JsonProcessingException | RuntimeException` (raw-byte fallback); admin's catches only `JsonProcessingException`. Any shared Family-B canonicalizer must reconcile this (the ADR proposes the lenient superset).
- **Per-service filter divergences** — POST-only (inbound) vs POST/PATCH/PUT/DELETE (outbound); outbound's `MAX_KEY_LENGTH` 400 guard + Micrometer metrics; per-service API path prefixes + error-envelope types. These must become filter configuration, not lost.

## Failure Scenarios

- **HARDSTOP-09 if skipped** — implementing the filter/port lift without this ADR would bake the hexagonal-ownership + canonicalizer + error-contract decisions silently. The ADR is the remediation: decide first, PAUSE implementation until ACCEPTED.
- **Reactive-classpath contamination** — the abstraction lands in `libs/java-web-servlet` (servlet-only); the ADR must reaffirm reactive gateways never depend on it.
