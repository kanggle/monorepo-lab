# Task ID

TASK-MONO-147

# Title

console-bff per-outbound-leg span attributes (`bff.domain` / `bff.route`) — architecture.md § Observability D7.A tracing conformance (per-domain trace attribution), regression-locked via the federation-hardening-e2e span-tag gate

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

- **depends on**: **TASK-MONO-146** (DONE, #915/#916/#917) — the virtual-thread OTel context propagation that produced the unified federation trace tree. Per-leg span attributes are only meaningful once the leg spans are part of the unified trace (MONO-146). Also MONO-143/144/145 (trace foundation + gates) and the federation-hardening-e2e harness.
- **origin**: MONO-146 § Out of Scope + memory `project_platform_console_adr_013` named this as the remaining follow-up. It is a **spec-conformance fix**: `console-bff` `specs/services/console-bff/architecture.md` § Observability (D7.A) **already** states "Per-outbound-leg span carries `bff.domain` and `bff.route` attributes for per-domain attribution in the trace UI" — but the impl only tags **metrics** (`bff_fanout_latency{domain,route}`); no span carries these attributes.
- **prerequisite for**: nothing (completes architecture.md § Observability D7.A tracing — both bullets: MONO-146 = propagation, this = per-leg attribution).
- **spec-first**: spec PR (this task md + INDEX ready entry, no impl) → impl PR (CompositionEngine leg span + use-case wiring + test + e2e gate, ready→in-progress→review + `workflow_dispatch` functional GREEN) → close chore PR (review→done + INDEX).
- **why root (monorepo-level)**: atomic across `projects/platform-console/apps/console-bff/` src (the attribution) + root `tests/federation-hardening-e2e/` (the gate). One atomic PR per CLAUDE.md § Cross-Project — MONO-146 precedent.
- **model**: 분석=Opus 4.7 / 구현=Opus 4.7 (observability + `workflow_dispatch` diagnostic loop, dispatcher-direct — MONO-144/145/146 precedent; mechanical enough that Sonnet 4.6 is acceptable) / 리뷰=Opus 4.7.

---

# Goal

Make each `console-bff` outbound fan-out leg emit a trace span carrying `bff.domain` and `bff.route` attributes, so the operator can attribute each span in the unified federation trace (MONO-146) to its domain/route in the trace UI. This conforms the impl to the **existing** architecture.md § Observability D7.A tracing claim (second bullet) and lifts it into a verified regression gate in the federation-hardening-e2e suite.

**Approach**: `CompositionEngine.time(...)` wraps each leg call in a leg-level span (`bff.fanout.leg`) created via the injected `io.micrometer.tracing.Tracer`, tagged `bff.domain=<lowercase domain>` + `bff.route=<route label>`. This span is a child of the inbound server span (propagated onto the virtual thread by MONO-146) and the parent of the outbound `RestClient` client span. No extra metric is introduced (the explicit `bff_fanout_latency` timer stays the sole latency metric — Tracer span, not Observation, to avoid a side meter).

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry. No implementation code.

**Impl PR** (atomic):

- **`CompositionEngine.java`** — inject `io.micrometer.tracing.Tracer` (new constructor param); in `time(...)` start a `bff.fanout.leg` span tagged `bff.domain`/`bff.route`, run the leg call inside `tracer.withSpan(legSpan)`, end the span in `finally`. Behavior of the latency timer + classifier path byte-equal. Update javadoc.
- **`OperatorOverviewCompositionUseCase.java`** + **`DomainHealthCompositionUseCase.java`** — inject `Tracer` (Spring bean) and pass it to the `new CompositionEngine(meterRegistry, tracer, ROUTE_LABEL)` constructor.
- **`CompositionEngineTest.java`** — construct the engine with `Tracer.NOOP`; the 5 existing scenarios (all-success / partial-failure / timeout / aggregation-degrade / context-propagation) stay green.
- **`tests/federation-hardening-e2e/specs/observability-trace-tree.spec.ts`** — extend the Jaeger span shape with `tags`; add a gate asserting the unified trace contains ≥1 span carrying both `bff.domain` and `bff.route` tags (the per-leg attribution); report the set of `bff.domain` values observed.

**Verification**: `gh workflow run federation-hardening-e2e.yml --ref <branch>` → the spec PASSES with the span-tag gate green; run id logged. Iterate per cycle if the OTLP→VictoriaTraces→Jaeger tag round-trip needs key tuning.

## Out of Scope

- **Metric changes** — `bff_fanout_latency`/`bff_fanout_errors`/`bff_aggregation_degrade_count` byte-unchanged. The leg span must NOT introduce a 4th metric family (use `Tracer`, not `Observation`).
- **Per-leg span error tagging / events** — D7.A only requires `bff.domain`/`bff.route`; richer span enrichment is a separate concern.
- **architecture.md / ADR / contract** — byte-unchanged (architecture.md D7.A already documents the target).
- **Producer src** — byte-unchanged.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this task md + INDEX ready entry land in a spec PR with no implementation code.
- **AC-2 (leg span implemented + tests green)**: `CompositionEngine.time(...)` emits a `bff.fanout.leg` span tagged `bff.domain`/`bff.route` via the injected `Tracer`; the 5 existing `CompositionEngineTest` scenarios pass with `Tracer.NOOP`; `./gradlew :projects:platform-console:apps:console-bff:test` passes; `console-bff` Testcontainers IT stays green.
- **AC-3 (functional GREEN — span attribution)**: an explicit `gh workflow run federation-hardening-e2e.yml` run shows `observability-trace-tree.spec.ts` PASS with the unified trace carrying ≥1 span tagged `bff.domain` + `bff.route`, no flaky retry; the MONO-145/146 floor + producer-join + unified-tree gates remain green. **Run id logged.** The report records the observed `bff.domain` set.
- **AC-4 (no metric/behavior regression)**: the 3 mandatory metric families are unchanged (no 4th family); composition timeout/degrade/credential/order semantics byte-equal. `@RequestScope` still off the virtual threads.
- **AC-5 (scope-lock)**: the impl PR touches only `CompositionEngine.java` (+ test) + the two use-cases + the one `observability-trace-tree.spec.ts`. `git diff origin/main` shows `architecture.md`, `docs/adr/`, `console-integration-contract.md`, the federation compose/workflow, `ObservabilityConfig.java`, and producer src all byte-unchanged.

# Related Specs

- `projects/platform-console/specs/services/console-bff/architecture.md` § Observability (D7.A), second tracing bullet — the spec claim this fix conforms to.
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` D7 (per-domain fan-out attribution observability).
- `tasks/done/TASK-MONO-146-bff-fanout-trace-context-propagation.md` — the unified tree this attribution layers onto; its Out of Scope named this follow-up.

# Related Contracts

- None. Span attributes are transport-level observability; no API/event contract change.

# Edge Cases

- **All 5 legs emit a leg span** — with a full credential set every leg calls `time(...)` (even the finance short-circuit and the GAP leg, which run inside `time`), so 5 `bff.fanout.leg` spans appear (console-bff-created; independent of whether the producer exports). GAP's producer (admin-service) has no OTLP exporter, but the GAP *leg* span is still emitted by console-bff.
- **Tag key round-trip** — `span.tag("bff.domain", v)` → OTel attribute `bff.domain` → VictoriaTraces → Jaeger-compat tag key `bff.domain`. If the key is transformed in transit, the e2e gate diagnoses (report all tags) and the key is tuned.
- **Tracer.NOOP in unit tests** — no tracer in `CompositionEngineTest`; `Tracer.NOOP.nextSpan()` is a no-op, the leg call still runs and returns normally.
- **try-with-resources + catch ordering** — the `SpanInScope` closes before the catch runs; the classifier emits only metrics (no span interaction), so the ordering is safe; `legSpan.end()` in `finally`.

# Failure Scenarios

- **Spec authored but never workflow_dispatch-verified** → AC-3 fail (MONO-139 premature-DONE lesson).
- **A 4th metric family appears** (e.g. via `Observation` instead of `Tracer`) → AC-4 fail. Use the Tracer span path.
- **Existing `CompositionEngineTest` scenarios regress** → AC-2 fail. The span wrap is additive; timeout/degrade/order byte-equal.
- **`bff.domain` tag absent from the unified trace under a longer poll** → real gap: diagnose the tag round-trip (key transform? span not exported?) — do not relax the gate.
- **architecture.md / ADR / contract / ObservabilityConfig edited** → AC-5 fail; this is conformance only.

# Verification

1. Spec PR: this md + INDEX ready entry; no impl code.
2. Impl PR: CompositionEngine + 2 use-cases + test + the one e2e spec; AC-5 scope grep.
3. Local: `./gradlew :projects:platform-console:apps:console-bff:test` green (5 scenarios with `Tracer.NOOP`).
4. Functional: `gh workflow run federation-hardening-e2e.yml --ref <branch>` → spec PASS with the span-tag gate green, run id logged (iterate per cycle, MONO-144/145/146 shape).
5. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Opus 4.7 (dispatcher-direct, workflow_dispatch diagnostic loop) / 리뷰=Opus 4.7 (BE-303 3-dim + workflow_dispatch functional GREEN 객관 + AC-2 tests-green + AC-4 no-metric-regression + AC-5 scope-lock + HARDSTOP-04).
