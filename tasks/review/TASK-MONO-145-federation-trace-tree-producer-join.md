# Task ID

TASK-MONO-145

# Title

Federation trace-tree producer-join regression gate (ADR-MONO-018 D4 follow-up) — strengthen observability-trace-tree.spec.ts to wait for + assert the console-bff→producer trace join, lifting the MONO-144 "observed ceiling" into a workflow_dispatch-verified regression gate (tests/ only)

# Status

review

# Owner

monorepo (root tasks/ — tests/federation-hardening-e2e/)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

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

- **depends on**: **TASK-MONO-144** (DONE, impl PR #904 / chore #905) — the federation-hardening-e2e trace stack (VictoriaTraces v0.9.0 + per-producer `MANAGEMENT_OTLP_TRACING_ENDPOINT` + `observability-trace-tree.spec.ts`). Also TASK-MONO-143 (trace foundation) and TASK-MONO-139/140 (the harness).
- **origin**: MONO-144 closed its gate at the honest floor — "one `trace_id` co-assembling the console-web SSR root + console-bff aggregation span (≥2 services)" — and **reported** the producer set (`producerServiceCount=0` on the single richest trace) as the *observed ceiling*, not gated. This task re-examines that ceiling: MONO-144's spec only inspected the single richest trace and never searched for the per-leg `console-bff→producer` traces the virtual-thread fan-out produces. The `console-bff→producer` W3C `traceparent` propagation is **already wired in src** (`RestClientConfig` injects the `ObservationRegistry` into every per-domain `RestClient.Builder` → micrometer-tracing-bridge-otel auto-injects `traceparent`), so producer server spans should share a `trace_id` with a console-bff client span. This task lifts that into a verified regression gate **without any console-bff src change**.
- **prerequisite for**: nothing (extends the ADR-018 D4 observability-federation axis; the unified console-web→producer 7-span tree remains a documented follow-up — see § Honest-scope note).
- **spec-first**: spec PR (this task md + INDEX ready entry, impl code 0) → impl PR (the `observability-trace-tree.spec.ts` poll/assertion change, ready→in-progress→review + `workflow_dispatch` functional GREEN) → close chore PR (review→done + INDEX).
- **model**: 분석=Opus 4.7 / 구현=Opus 4.7 (cross-cutting observability + `workflow_dispatch` diagnostic loop, dispatcher-direct — MONO-144 precedent) / 리뷰=Opus 4.7.

---

# Goal

Strengthen `tests/federation-hardening-e2e/specs/observability-trace-tree.spec.ts` so it **waits for and asserts** the `console-bff → producer` distributed-trace join — one `trace_id` co-assembling a console-bff span and ≥1 producer server span — turning the MONO-144 "observed ceiling" into a deterministic regression gate that proves **"console-bff→producer W3C `traceparent` propagation already works"**. This is a `tests/` (root shared path) change only; **no `projects/*/apps/*/src/**` is touched** (propagation is already wired in `RestClientConfig`).

This closes the empirical question MONO-144 left open: MONO-144 observed `producerServiceCount=0` on the single richest (console-web→console-bff) trace, but that spec never searched for the **per-leg** `console-bff→producer` traces. The fan-out runs each leg on its own Java 21 virtual thread (`CompositionEngine.fanOut` → `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture.supplyAsync`); the inbound OTel context is a ThreadLocal not propagated to those worker threads, so each leg's outbound `RestClient` client observation roots a **fresh** trace carrying the console-bff client span + the producer server span (same `trace_id`). That per-leg trace is the deterministic proof of the console-bff→producer hop.

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry. No implementation code.

**Impl PR** (`tests/federation-hardening-e2e/specs/observability-trace-tree.spec.ts` only):

- Replace the early-`break`-at-2-services poll with a **full-window** poll that collects **all** console-bff-bearing traces and waits for a producer to join a console-bff `trace_id` (poll deadline raised to fit inside one `test.setTimeout`).
- Compute, across **all** returned traces (not just the single richest): (a) the best console-web↔console-bff unified trace, (b) the best `console-bff + ≥1 producer` join trace, (c) the union of producer services seen sharing a `trace_id` with console-bff.
- **Report** (log + Playwright artifact) the full picture before asserting: ingested services, both trace shapes, per-service span counts, the joined-producer set, and whether the unified console-web→producer tree was reached (the residual ceiling).
- **Gate** on two proven, deterministic invariants: (i) the MONO-144 floor — one `trace_id` with console-web (SSR root) + console-bff; (ii) the new gate — one `trace_id` co-assembling a console-bff span + ≥1 producer server span (`console-bff→producer` propagation).

**Verification**: `gh workflow run federation-hardening-e2e.yml --ref <branch>` → the strengthened spec PASSES; run id logged. Multi-cycle iteration expected (poll-window / fan-out-driving / honest-gate tuning) — MONO-140/144 precedent (push self-CI is insufficient for a dispatch-only workflow).

## Out of Scope

- **console-bff src change** — `RestClientConfig` already wires `traceparent` propagation; AC-3 forbids `projects/*/apps/*/src/**` edits. Unifying the producer spans under console-web's *single* `trace_id` (the literal 7-span tree) would require virtual-thread OTel context propagation in `CompositionEngine` (src) — documented as a follow-up candidate, **not** done here.
- **Trace stack / compose / workflow wiring** — MONO-144 (reused as-is).
- **Producer src / ADR / contracts** — byte-unchanged.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this task md + INDEX ready entry land in a spec PR with **no** implementation code.
- **AC-2 (full-window producer-join poll authored)**: `observability-trace-tree.spec.ts` no longer breaks at the 2-service floor; it polls the full flush window across **all** console-bff traces, tracking the best unified trace AND the best `console-bff + ≥1 producer` join, and reports the joined-producer set + the residual unified-tree ceiling into the test log + Playwright artifact.
- **AC-3 (no src change)**: `git diff --stat origin/main -- 'projects/*/apps/*/src/**'` = empty. `docs/adr/`, `console-integration-contract.md`, and the federation compose/workflow are byte-unchanged (this is a single-spec-file change).
- **AC-4 (functional GREEN — producer-join gate)**: an explicit `gh workflow run federation-hardening-e2e.yml` run shows the strengthened spec PASS with the `console-bff→producer` join asserted (one `trace_id` carrying a console-bff span + ≥1 producer server span), no flaky retry. **Run id logged** (MONO-140/144 lesson). The report records which producers joined and whether the unified console-web→producer tree was reached.
- **AC-5 (honest-scope fidelity)**: the gate reflects CI reality per the § Honest-scope note. If CI shows producers join only **per-leg** (console-bff-rooted), the gate is the per-leg `console-bff→producer` invariant and the unified tree stays documented-ceiling. If CI shows producers join the **unified** console-web `trace_id`, the gate captures that stronger reality. If CI shows **no** producer join under a longer poll, that is a real propagation gap → STOP and file a console-bff src diagnosis task (do not green-wash) — but `RestClientConfig` makes per-leg join the expected outcome.

# Related Specs

- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D4 — the trace-tree requirement; this task strengthens its verification.
- `docs/adr/ADR-MONO-007a-trace-layer.md` § 2 D1/D2/D3 — the trace foundation.
- `tasks/done/TASK-MONO-144-federation-e2e-trace-tree-assertion.md` — the gate this task lifts (honest-scope note + observed ceiling).
- `projects/platform-console/apps/console-bff/src/main/java/.../infrastructure/config/RestClientConfig.java` — the already-wired `ObservationRegistry` propagation (read-only evidence; not edited).
- `projects/platform-console/apps/console-bff/src/main/java/.../application/composition/CompositionEngine.java` — the virtual-thread fan-out that explains per-leg vs unified join (read-only evidence; not edited).

# Related Contracts

- None. Trace propagation is transport-level observability; no API/event contract change.

# Edge Cases

- **GAP leg has no producer span** — the GAP leg targets admin-service, which has no OTLP exporter (no span). GAP never contributes a producer span; the join must come from wms/scm/erp.
- **Finance leg short-circuits** — without `X-Finance-Default-Account-Id`, the finance leg returns `MISSING_PREREQUISITE` with **no** outbound HTTP call → no console-bff client span, no finance span. Do not require finance in the joined set.
- **Wildcard tenant** — the SUPER_ADMIN session carries `tenant_id='*'`, accepted by all producers (login.ts / BE-312). Even a producer 403/4xx still forms a receiving server span sharing the console-bff client `trace_id`, so the join holds regardless of the leg's HTTP outcome.
- **Per-leg vs unified `trace_id`** — the gate searches **all** console-bff traces for producer co-location; it must not assume the producer rides the single richest (console-web) trace.
- **Flush latency** — producers batch-export (~5s) + ingest; the poll deadline must fit inside `test.setTimeout` (MONO-144 cycle-2 flaky lesson: deadline > test timeout → killed mid-poll → flaky pass-on-retry).
- **Multi-cycle iteration expected** — MONO-140 = 5 cycles, MONO-144 = 3; budget for poll-window / timing tuning per cycle.

# Failure Scenarios

- **Spec strengthened but never workflow_dispatch-verified** → AC-4 fail (MONO-139 premature-DONE lesson). Functional GREEN requires a logged dispatch run.
- **`projects/*/apps/*/src/**` edited** → AC-3 fail. Propagation is already wired; this task is tests/ only.
- **Producers never join even per-leg under a longer poll** → AC-5 STOP: real propagation gap, file a console-bff src diagnosis task; do not relax the gate to hide it.
- **Gate over-claims the unified 7-span tree when CI only shows per-leg join** → AC-5 fail (honest-scope). Gate the per-leg invariant; keep the unified tree as documented ceiling.

# Verification

1. Spec PR: this md + INDEX ready entry; no impl code.
2. Impl PR: single-file spec change; AC-3 grep zero (`projects/*/apps/*/src/**`), compose/workflow/ADR byte-unchanged.
3. Functional: `gh workflow run federation-hardening-e2e.yml --ref <branch>` → strengthened spec PASS, run id logged (iterate per cycle, MONO-140/144 shape).
4. Honest-scope: report records the joined-producer set + unified-tree residual; gate matches CI reality (AC-5).
5. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Opus 4.7 (dispatcher-direct, workflow_dispatch diagnostic loop) / 리뷰=Opus 4.7 (BE-303 3-dim + workflow_dispatch functional GREEN 객관 + AC-3 no-src-change + AC-5 honest-scope + HARDSTOP-04).
