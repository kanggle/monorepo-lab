# Task ID

TASK-MONO-144

# Title

Federation e2e 7-span trace-tree assertion (ADR-MONO-018 D4 cross-product verification) — extend federation-hardening-e2e stack with VictoriaTraces + Vector OTLP + Playwright trace-tree spec + workflow_dispatch functional GREEN

# Status

ready

# Owner

monorepo (root tasks/ — tests/federation-hardening-e2e/)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
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

- **depends on**: **TASK-MONO-143** (trace foundation — VictoriaTraces backend + Vector OTLP source/sink in the dev stack + console-web `@opentelemetry` origination + `/observe trace` skill) merged on main. Also the federation-hardening-e2e harness (TASK-MONO-139/140, `tests/federation-hardening-e2e/`).
- **origin**: split from TASK-MONO-143 (honest scope adjustment, MONO-139→140 precedent). ADR-018 D4's cross-product trace-tree **assertion** requires the federation Docker stack + multi-cycle `workflow_dispatch` iteration; separated so the verifiable trace foundation (MONO-143) lands without being blocked.
- **prerequisite for**: nothing (closes the ADR-018 D4 observability federation axis). Remaining Phase 8 axis = ADR-018 D5 (isolation regression cohort, Opus) is independent.
- **spec-first**: spec PR (this task md + INDEX — landed together with MONO-143 spec) → impl PR (federation-e2e stack + Playwright spec) → close chore PR.
- **model**: Sonnet 4.6 (compose wiring + Playwright assertion) — but the `workflow_dispatch` diagnostic loop (MONO-140 = 5 cycles) benefits from main-session iteration; dispatcher-direct acceptable.

---

# Goal

Assert that a console dashboard fan-out (Operator Overview) produces **one trace tree** — a single `trace_id` spanning `console-web SSR → console-bff aggregation → 5 per-domain producer spans` (7 spans) — assembled in VictoriaTraces, verified GREEN by an explicit federation-hardening-e2e `workflow_dispatch` run. This is the ADR-MONO-018 D4 cross-product functional verification, building on the MONO-143 trace foundation.

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry (landed with the MONO-143 spec PR).

**Impl PR**:

- **`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`** — add `victoriatraces` + `vector` (OTLP source binding `:4318` + VictoriaTraces sink, mirroring `infra/observability/` from MONO-143); set `OTEL_EXPORTER_OTLP_ENDPOINT` (→ Vector) + `management.tracing.sampling.probability=1.0` env on each producer + console-bff + console-web; phased start so the trace backend is up before the producers.
- **`tests/federation-hardening-e2e/specs/observability-trace-tree.spec.ts`** — new Playwright spec: drive an Operator Overview load (operator login → `/dashboards/overview`), capture the request's `trace_id` (response header correlation or VictoriaTraces lookup by recent operator-overview span), poll VictoriaTraces (Jaeger-compat query API) with timeout for trace-export flush latency, assert the trace contains ≥ the 7 expected span names sharing one `trace_id`.
- **`tests/federation-hardening-e2e/` workflow / config** — wire the new spec into the e2e job; ensure VictoriaTraces health-gate before Playwright.

**Verification**: `gh workflow run federation-hardening-e2e.yml` → the trace-tree spec PASSES (run id logged). Footprint re-measure (MONO-143 AC-7 deferred here).

## Out of Scope

- **Trace foundation** (VictoriaTraces backend choice, Vector OTLP config shape, console-web instrumentation, `/observe trace` skill) — all MONO-143 (this task reuses them).
- **Producer code change** — env-only in the e2e compose; producers already export OTLP.
- **ADR amendment** — ADR-007a/018 byte-unchanged.
- **ADR-018 D5 isolation regression cohort** — separate axis.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: landed with MONO-143 spec PR (this md + INDEX).
- **AC-2 (e2e stack trace-capable)**: federation-e2e compose has `victoriatraces` + `vector` (OTLP source/sink); producers/console-bff/console-web carry `OTEL_EXPORTER_OTLP_ENDPOINT` + sampling 1.0.
- **AC-3 (trace-tree spec authored)**: `observability-trace-tree.spec.ts` exists, asserts one `trace_id` / 7 spans, polls with flush-latency timeout.
- **AC-4 (functional GREEN)**: an explicit `gh workflow run federation-hardening-e2e.yml` produces the 7-span single-`trace_id` tree in VictoriaTraces and the spec PASSES. **Run id logged (MONO-140 lesson: push self-CI insufficient for dispatch-only workflows — functional AC needs an explicit dispatch run).**
- **AC-5 (Vector OTLP egress confirmed or fallback applied)**: the MONO-143-flagged Vector→VictoriaTraces OTLP forwarding either works (confirmed by AC-4) or the documented ADR-007a D2 fallback (producers/console-web → VictoriaTraces direct, traces-only) is applied with a one-line ADR-007a D2 revisit note.
- **AC-6 (no producer src change)**: `git diff --stat origin/main -- 'projects/*/apps/*/src/**'` = empty.
- **AC-7 (footprint)**: federation-e2e + dev-stack footprint with VictoriaTraces re-measured; reported.

# Related Specs

- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D4 — the trace-tree requirement this task verifies.
- `docs/adr/ADR-MONO-007a-trace-layer.md` § 2 D1/D2 + § 5 Verification — the foundation + the verification this task delivers.
- `tests/federation-hardening-e2e/` (MONO-139/140) — the harness extended.
- `infra/observability/` (MONO-143) — the dev-stack trace config mirrored into the e2e stack.

# Related Contracts

- None. Trace propagation is transport-level observability; no API/event contract change.

# Edge Cases

- **Trace-export flush latency** — producers batch-export; the poll must allow time + retry, not single-shot.
- **Vector OTLP trace egress fails in 0.45** — apply ADR-007a D2 fallback (direct OTLP to VictoriaTraces for traces); AC-5.
- **VictoriaTraces image tag** — confirm the MONO-143-flagged tag against the registry on the first dispatch (fails fast if wrong — MONO-140 cycle-1-style finding).
- **Partial tree (a producer span missing)** — distinguish "domain not called for this dashboard" vs "span dropped"; assert the spans that the Operator Overview fan-out actually invokes.
- **Multi-cycle iteration expected** — MONO-140 took 5 cycles; budget for path/schema/timing fixes per cycle.

# Failure Scenarios

- **Spec authored but never workflow_dispatch-verified** → AC-4 fail (MONO-139 premature-DONE lesson). Functional GREEN requires a logged dispatch run.
- **Producer `src` edited** → AC-6 fail. Env-only in the e2e compose.
- **`docs/adr/` edited** → reject; ADRs byte-unchanged.
- **`console-integration-contract.md` edited** → reject; traceparent is transport, not contract.

# Verification

1. Spec PR: landed with MONO-143.
2. Impl PR: federation-e2e compose + Playwright spec present; AC-6 grep zero.
3. Functional: `gh workflow run federation-hardening-e2e.yml` → trace-tree spec PASS, run id logged (iterate per cycle as needed, MONO-140 shape).
4. Footprint re-measured.
5. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Sonnet 4.6 권장 (or dispatcher-direct, workflow_dispatch diagnostic loop) / 리뷰=Opus 4.7 (BE-303 3-dim + workflow_dispatch functional GREEN 객관 + AC-5 Vector-egress-or-fallback + HARDSTOP-04).
