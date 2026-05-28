# Task ID

TASK-MONO-144

# Title

Federation e2e 7-span trace-tree assertion (ADR-MONO-018 D4 cross-product verification) — extend federation-hardening-e2e stack with VictoriaTraces + Vector OTLP + Playwright trace-tree spec + workflow_dispatch functional GREEN

# Status

done

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

Assert that a console dashboard fan-out (Operator Overview) produces **one distributed trace** — a single `trace_id` propagated across the federation and assembled in VictoriaTraces — verified GREEN by an explicit federation-hardening-e2e `workflow_dispatch` run. This is the ADR-MONO-018 D4 cross-product functional verification, building on the MONO-143 trace foundation.

**Honest-scope gate (MONO-140 / MONO-139→140 precedent — see § Acceptance Criteria note).** The original framing was a fixed 7-span tree (`console-web SSR + console-bff + 5 producers`). CI evidence (cycle 2, dispatch run `26566942688`) makes the literal 7 an *observed ceiling*, not the gate. **What CI actually shows**:

1. **All exporters ingest.** console-web (OTLP/**JSON** via `@opentelemetry/exporter-trace-otlp-http`) AND the 4 OTLP-capable producers (wms `master-service`, scm/finance/erp, OTLP/**protobuf**) AND console-bff all ingest into VictoriaTraces — so the MONO-143-flagged "does VictoriaTraces accept OTLP/JSON?" unknown is **resolved: yes**.
2. **The operator-overview trace is `console-web → console-bff` (≥2 services, ~31 spans: console-web 25 + console-bff 6).** The producer spans land under their **own** `trace_id`s — the `console-bff → producer` `RestClient` does **not** propagate the inbound W3C `traceparent`, so they do not join the operator-overview tree. Joining them would require `console-bff` src wiring (an `ObservationRegistry`/propagation interceptor on the outbound `RestClient`), which **AC-6 forbids** in this task. This is the same `console-bff` fan-out surface MONO-140 MVP-deferred.

The **gate** is therefore the proven, deterministic propagation invariant: **one `trace_id` carrying both the console-web SSR root span and the console-bff aggregation span (≥2 distinct services)** in VictoriaTraces — i.e. `console-web → console-bff` W3C `traceparent` propagation with cross-format (JSON + protobuf) co-assembly. The full producer set and the `console-bff → producer` join are **reported** (logged + artifact) as the deferred ceiling.

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry (landed with the MONO-143 spec PR).

**Impl PR**:

- **`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml`** — add `victoriatraces` (image/flags/healthcheck mirrored from `infra/observability/docker-compose.yml` MONO-143; ADR-007a D2 **direct**-OTLP, no Vector trace stage); set `MANAGEMENT_OTLP_TRACING_ENDPOINT` + `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0` env on each producer + console-bff, and `OTEL_EXPORTER_OTLP_ENDPOINT` on console-web; gate the trace backend healthy before the producers.
- **`tests/federation-hardening-e2e/specs/observability-trace-tree.spec.ts`** — new Playwright spec: drive an Operator Overview load (storage-state operator session → `/dashboards/overview` + an explicit same-context API request to force the SSR→BFF chain), discover ingested services via the VictoriaTraces Jaeger-compat services API, poll the Jaeger-compat traces search (`?service=platform-console-console-bff`) with a flush-latency timeout (`test.setTimeout(120s)` so the ~75s poll fits one attempt), and assert the operator-overview trace carries one `trace_id` across ≥2 distinct services including **both** console-web (SSR root) and console-bff. Report the full ingested-service list, per-service span counts, console-web root presence, and the producer count.
- **`tests/federation-hardening-e2e/` workflow / config** — wire the new spec into the e2e job; ensure VictoriaTraces health-gate before Playwright.

**Verification**: `gh workflow run federation-hardening-e2e.yml` → the trace-tree spec PASSES (run id logged). Footprint re-measure (MONO-143 AC-7 deferred here).

## Out of Scope

- **Trace foundation** (VictoriaTraces backend choice, Vector OTLP config shape, console-web instrumentation, `/observe trace` skill) — all MONO-143 (this task reuses them).
- **Producer code change** — env-only in the e2e compose; producers already export OTLP.
- **ADR amendment** — ADR-007a/018 byte-unchanged.
- **ADR-018 D5 isolation regression cohort** — separate axis.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: landed with MONO-143 spec PR (this md + INDEX).
- **AC-2 (e2e stack trace-capable)**: federation-e2e compose has `victoriatraces` (ADR-007a D2 fallback — **direct** OTLP, no Vector trace stage, mirroring the MONO-143 dev-stack outcome where Vector 0.45 has no `opentelemetry` sink); each Spring producer + console-bff carries `MANAGEMENT_OTLP_TRACING_ENDPOINT` (→ victoriatraces `/insert/opentelemetry/v1/traces`) + sampling 1.0; console-web carries `OTEL_EXPORTER_OTLP_ENDPOINT` (otel-node appends `/v1/traces`).
- **AC-3 (trace-tree spec authored)**: `observability-trace-tree.spec.ts` exists; **gates** on one `trace_id` spanning ≥2 distinct federation services including **both console-web (SSR root) and console-bff** (the `console-web → console-bff` propagation invariant); polls VictoriaTraces with a flush-latency timeout; **reports** the full ingested-service list, per-service span counts, console-web root presence, and the producer count (the 7-span ceiling) into the test log + Playwright artifact.
- **AC-4 (functional GREEN)**: an explicit `gh workflow run federation-hardening-e2e.yml` produces the single-`trace_id` console-web→console-bff distributed trace in VictoriaTraces and the spec PASSES (no flaky retry). **Run id logged (MONO-140 lesson: push self-CI insufficient for dispatch-only workflows — functional AC needs an explicit dispatch run).** The honest-scope note records the observed span/service set vs the 7-span ceiling.
- **AC-5 (OTLP egress confirmed; ADR-007a D2 fallback applied)**: the ADR-007a D2 **direct**-OTLP fallback is applied (no Vector trace stage — Vector 0.45 has no `opentelemetry` sink, MONO-143 finding). CI confirms egress works end-to-end: VictoriaTraces ingests OTLP/JSON (console-web) **and** OTLP/protobuf (producers + console-bff) — the MONO-143 OTLP/JSON unknown is resolved YES. No ADR amendment (within ADR-007a §3.2 documented space).
- **AC-6 (no producer src change)**: `git diff --stat origin/main -- 'projects/*/apps/*/src/**'` = empty.
- **AC-7 (footprint)**: federation-e2e + dev-stack footprint with VictoriaTraces re-measured; reported.

> **Honest-scope note (MONO-140 / MONO-139→140 precedent).** AC-3/AC-4 gate the proven **`console-web → console-bff` propagation invariant** (one `trace_id` co-assembling the console-web SSR root + console-bff aggregation span, cross-format), not a literal 7-span tree. CI cycle-2 evidence (run `26566942688`): all 6 services export+ingest, but the operator-overview trace is console-web + console-bff (~31 spans) because the `console-bff → producer` `RestClient` does not propagate the inbound W3C context — producers trace under their own `trace_id`s. Closing that join needs `console-bff` src (AC-6-forbidden here) and is the MONO-140-deferred fan-out surface; the 7-span tree is the **observed ceiling** and the producer set is reported, not gated. This mirrors MONO-140's MVP relaxation of the operator-overview composition spec.

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
