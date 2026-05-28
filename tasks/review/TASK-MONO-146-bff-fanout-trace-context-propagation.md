# Task ID

TASK-MONO-146

# Title

console-bff fan-out virtual-thread OTel context propagation — unified federation trace tree (architecture.md § Observability D7.A conformance + ADR-MONO-018 D4 completion), regression-locked via the federation-hardening-e2e `unifiedTreeReached` gate

# Status

review

# Owner

monorepo (root tasks/ — atomic cross-scope: `projects/platform-console/apps/console-bff/` src + root `tests/federation-hardening-e2e/`)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

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

---

# Dependency Markers

- **depends on**: **TASK-MONO-145** (DONE, #912/#913/#914) — the federation trace-tree producer-join gate that observed `unifiedTreeReached=false` (producers join **per-leg** console-bff-rooted trace_ids, not console-web's single trace). Also MONO-143/144 (trace foundation + stack) and the federation-hardening-e2e harness (MONO-139/140).
- **origin**: MONO-145 § Honest-scope note + memory `project_platform_console_adr_013` documented the unified console-web→producer single-trace tree as the "documented ceiling" requiring virtual-thread OTel context propagation in `CompositionEngine` (src). This task closes that ceiling. It is also a **spec-conformance fix**: `console-bff` `specs/services/console-bff/architecture.md` § Observability (D7.A) **already** states "The inbound request's OTel trace context propagates to every outbound leg via W3C `traceparent`" — the virtual-thread fan-out silently violates this (each leg roots a fresh trace_id).
- **prerequisite for**: nothing (completes the ADR-018 D4 / D7.A trace-attribution axis end-to-end). Per-leg span `bff.domain`/`bff.route` attributes (architecture.md D7.A second tracing bullet) remain a separate follow-up candidate — see § Out of Scope.
- **spec-first**: spec PR (this task md + INDEX ready entry, no impl) → impl PR (CompositionEngine src + build.gradle + unit test + e2e gate assertion, ready→in-progress→review + `workflow_dispatch` functional GREEN) → close chore PR (review→done + INDEX).
- **why root (monorepo-level)**: the change is atomic across `projects/platform-console/apps/console-bff/` src (the enabler) AND root `tests/federation-hardening-e2e/` (the regression gate). Splitting would leave a transient window where the fix lands but the gate doesn't lock it, or the gate asserts a behavior the src doesn't yet deliver (red). One atomic PR per CLAUDE.md § Cross-Project.
- **model**: 분석=Opus 4.7 / 구현=Opus 4.7 (concurrency × observability cross-cutting + `workflow_dispatch` diagnostic loop, dispatcher-direct — MONO-144/145 precedent) / 리뷰=Opus 4.7.

---

# Goal

Make `console-bff`'s composition fan-out propagate the inbound OTel trace context to its virtual-thread legs so every outbound per-domain `RestClient` call **continues the inbound trace** — producing one distributed trace spanning console-web SSR → console-bff → all reachable producers (the full ~7-span tree), instead of MONO-145's per-leg console-bff-rooted forks. This (a) conforms the impl to the **existing** architecture.md § Observability D7.A spec claim, and (b) lifts MONO-145's `unifiedTreeReached=false` documented ceiling into a **verified regression gate** (`unifiedTreeReached=true`) in the federation-hardening-e2e suite — with the MONO-145 producer-join gate as the before/after baseline.

**Root cause (MONO-145 analysis)**: `CompositionEngine.fanOut` runs each leg on a Java 21 virtual thread (`Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture.supplyAsync`). The inbound OTel observation/trace scope lives in a ThreadLocal that is **not** propagated to those worker threads, so each leg's outbound `RestClient` client observation roots a fresh trace_id. The fix captures an `io.micrometer.context.ContextSnapshot` on the servlet thread and wraps the fan-out executor so each leg restores the inbound observation scope before its outbound call.

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry. No implementation code.

**Impl PR** (atomic):

- **`projects/platform-console/apps/console-bff/.../application/composition/CompositionEngine.java`** — in `fanOut(...)`, capture `ContextSnapshotFactory.builder().build().captureAll()` on the calling (servlet) thread and wrap the virtual-thread executor via `snapshot.wrapExecutor(executor)`; submit each leg through the wrapped executor so the inbound observation scope is restored on each VT. Update the class javadoc to note trace context is now propagated while request-scoped beans (`@RequestScope` credential context) intentionally remain **not** propagated (no ThreadLocalAccessor registered for them — the pre-resolve-on-servlet-thread discipline is preserved).
- **`projects/platform-console/apps/console-bff/build.gradle`** — declare `io.micrometer:context-propagation` explicitly (direct use of `io.micrometer.context.*`; version managed by the Spring Boot BOM — already transitively present via `micrometer-tracing-bridge-otel`, made explicit).
- **`projects/platform-console/apps/console-bff/.../CompositionEngineTest.java`** — keep the 4 existing tests green; add a unit test that registers a custom `ThreadLocalAccessor`, sets a value on the calling thread, and asserts every fan-out leg observes the propagated value on its virtual thread (proves cross-VT context propagation without a real tracer/e2e).
- **`tests/federation-hardening-e2e/specs/observability-trace-tree.spec.ts`** — add the unified-tree gate: assert `unifiedTreeReached === true` (producers join console-web's single trace_id) AND the unified trace carries ≥1 producer span; keep the MONO-145 floor + producer-join gates.

**Verification**: `gh workflow run federation-hardening-e2e.yml --ref <branch>` → the spec PASSES with `unifiedTreeReached=true`; run id logged. Iterate per cycle if timing tuning is needed (MONO-140/144/145 precedent).

## Out of Scope

- **Per-leg span `bff.domain`/`bff.route` attributes** (architecture.md D7.A second tracing bullet) — distinct concern (custom `ObservationConvention` / per-leg span tagging); separate follow-up.
- **`DomainHealthCompositionUseCase`** — no change needed; it routes through the same `CompositionEngine.fanOut`, so it inherits the fix.
- **architecture.md / ADR / console-integration-contract** — byte-unchanged. architecture.md D7.A already documents the target behavior (this is conformance, not a spec change).
- **Producer src** — byte-unchanged; producers already accept W3C `traceparent`.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this task md + INDEX ready entry land in a spec PR with no implementation code.
- **AC-2 (context propagation implemented + unit-proven)**: `CompositionEngine.fanOut` captures + wraps the executor with a `ContextSnapshot`; a new unit test proves a calling-thread `ThreadLocalAccessor` value is visible in every virtual-thread leg; the 4 existing `CompositionEngineTest` scenarios (all-success / partial-failure / timeout / aggregation-degrade) stay green; `./gradlew :projects:platform-console:apps:console-bff:test` passes.
- **AC-3 (functional GREEN — unified tree)**: an explicit `gh workflow run federation-hardening-e2e.yml` run shows `observability-trace-tree.spec.ts` PASS with **`unifiedTreeReached=true`** and the unified console-web trace carrying ≥1 producer span, no flaky retry; the MONO-145 floor + producer-join gates remain green. **Run id logged.**
- **AC-4 (no behavior regression)**: composition 5s timeout, per-leg degrade classification, cross-leg 401 collapse, fixed leg order, and per-domain credential discipline are unchanged (existing unit + integration tests green). `@RequestScope` credential context is still NOT propagated onto virtual threads (no accessor registered for it) — the pre-resolve discipline is intact.
- **AC-5 (scope-lock)**: the impl PR touches only `CompositionEngine.java` (+ its test) + `build.gradle` + the one `observability-trace-tree.spec.ts`. `git diff origin/main` shows `architecture.md`, `docs/adr/`, `console-integration-contract.md`, the federation compose/workflow, and producer src all byte-unchanged.

# Related Specs

- `projects/platform-console/specs/services/console-bff/architecture.md` § Observability (D7.A) — the existing spec claim this fix conforms to ("inbound trace context propagates to every outbound leg").
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` D7 (per-domain fan-out attribution observability) — the architecture decision realized end-to-end.
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D4 + `docs/adr/ADR-MONO-007a-trace-layer.md` — the federation trace axis.
- `tasks/done/TASK-MONO-145-federation-trace-tree-producer-join.md` — the documented ceiling (`unifiedTreeReached=false`) this task closes; its gate = before/after baseline.

# Related Contracts

- None. Trace propagation is transport-level observability; no API/event contract change. (`console-integration-contract.md` byte-unchanged.)

# Edge Cases

- **@RequestScope must stay off the VTs** — `captureAll()` captures only registered `ThreadLocalAccessor`s (observation/MDC); Spring's `RequestContextHolder` has no default accessor, so request scope is NOT propagated. The use-case still pre-resolves credentials on the servlet thread. Verify no behavior change.
- **Snapshot scope cleanup** — `wrapExecutor` opens the restored scope around each task and closes it in `finally`; no scope leak across pooled threads (VTs are per-task anyway).
- **finance leg short-circuit** — finance without `X-Finance-Default-Account-Id` makes no outbound call → no finance span in the unified tree; the gate requires ≥1 producer (wms/scm/erp), not all.
- **GAP leg** — admin-service has no OTLP exporter → no GAP producer span; does not contribute to the unified producer set.
- **Flush latency** — producer spans join the unified trace after batch export (~5s) + ingest; the existing 110s poll inside `test.setTimeout(180s)` accommodates it (MONO-144 cycle-2 flaky lesson).
- **Multiple operator-overview traces** — SSR page load + the explicit API request may each root a trace; both are unified post-fix; the gate uses the richest unified trace.

# Failure Scenarios

- **Spec authored but never workflow_dispatch-verified** → AC-3 fail (MONO-139 premature-DONE lesson). Functional GREEN requires a logged dispatch run.
- **`@RequestScope` leaks onto VTs (behavior change)** → AC-4 fail. Capture must not introduce request-scope accessors; if a future accessor is added, re-evaluate.
- **Existing `CompositionEngineTest` scenarios regress** → AC-2 fail. The fix is additive to coordination; timeout/degrade/order semantics must be byte-equal.
- **`unifiedTreeReached` still false under a longer poll** → real propagation gap: diagnose (accessor not registered? snapshot not applied?) — do not relax the gate.
- **architecture.md / ADR / contract edited** → AC-5 fail; spec already documents the target — this is conformance only.

# Verification

1. Spec PR: this md + INDEX ready entry; no impl code.
2. Impl PR: CompositionEngine + build.gradle + unit test + the one e2e spec; AC-5 scope grep (architecture.md/ADR/contract/producer-src byte-unchanged).
3. Local: `./gradlew :projects:platform-console:apps:console-bff:test` green (4 existing + 1 new propagation test).
4. Functional: `gh workflow run federation-hardening-e2e.yml --ref <branch>` → spec PASS with `unifiedTreeReached=true`, run id logged (iterate per cycle, MONO-140/144/145 shape).
5. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Opus 4.7 (dispatcher-direct, workflow_dispatch diagnostic loop) / 리뷰=Opus 4.7 (BE-303 3-dim + workflow_dispatch functional GREEN 객관 + AC-2 unit-proven propagation + AC-4 no-regression + AC-5 scope-lock + HARDSTOP-04).
