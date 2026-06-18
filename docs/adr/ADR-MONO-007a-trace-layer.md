# ADR-MONO-007a — Trace Layer (VictoriaTraces + OTLP-via-Vector)

**Status:** ACCEPTED
**Date:** 2026-05-28
**History:** PROPOSED 2026-05-28 (TASK-MONO-142) → ACCEPTED 2026-05-28 (same PR — meta-policy follow-up ADR pinning the trace backend + ingestion topology before any trace-impl commit, identical rationale to its parent [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) § 1.2 "a policy ADR before any compose file"; decision direction CHOSEN-PROPOSED, finalised at ACCEPTED on user-explicit intent 2026-05-28 — "(b) Observability federation impl" 선택 후 ADR 충돌 보고에 대해 Option A "ADR-MONO-007a 선작성 (정석) — trace layer ACCEPTED" 명시 선택).
**Decision driver:** [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) § 2 D4 (Phase 8 observability federation) requires that "a single dashboard fan-out (Operator Overview / Domain Health) is traceable end-to-end as one trace tree ... The cross-product e2e suite asserts the trace tree assembles in **VictoriaTraces**." This presumes a deployed trace backend, but [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) § 2.1 D1 explicitly **deferred** the trace layer to this follow-up ADR (ADR-MONO-007a) and § 6 item 4 gated it on "a saga-spanning [trace] signal." ADR-MONO-018 Phase 8's cross-product trace-tree demand **is** that gating signal. This ADR resolves the deferral: it pins the trace backend, the ingestion path, and the console-web trace-origination instrumentation so the ADR-MONO-018 D4 execution task (TASK-MONO-143) lands on a dependency-correct base instead of silently baking the architecture (HARDSTOP-09).
**Supersedes:** none. **Amends:** [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) § 2.1 D1 (trace-deferral resolved — additive: D1's deferral sentence is annotated, not rewritten; the Vector + VictoriaLogs + VictoriaMetrics D1 choice is byte-unchanged) + § 6 item 4 (ADR-MONO-007a filed — status note) + § 2.4 D4 (`/observe trace` Phase-2-stub → full-impl gate reference). HARDSTOP-04 discipline: ADR-007 D1/D2/D3/D4 decision bodies byte-unchanged; only additive notes.
**Reconciles:** [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) § 2 D4 — D4's "(+ VictoriaLogs / VictoriaTraces per the ADR-006 topology)" parenthetical carried a **false premise** (VictoriaTraces was NOT in the deployed ADR-007 topology — it was deferred; "ADR-006" was itself the known broken reference recorded in TASK-MONO-137 meta, the observability ADR is ADR-007). This ADR supplies the VictoriaTraces topology D4 presumed; ADR-018 D4's intent is preserved unchanged, its dependency is now satisfied. ADR-018 receives an additive reconcile note (no D1-D8 body change — HARDSTOP-04).
**Related:** [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) (parent — Vector + VictoriaLogs + VictoriaMetrics stack, per-worktree ephemeral topology, opt-in e2e-scoped lifecycle, `/observe` skill DX; all inherited here), [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) (Phase 8 D4 — the consumer of this trace layer), [ADR-MONO-017](ADR-MONO-017-platform-console-bff-architecture.md) (D7 per-domain fan-out attribution metrics — the trace tree's BFF span attribution aligns with these), `infra/observability/docker-compose.yml` + `infra/observability/vector.toml` (the stack this ADR extends), memory `reference_openai_harness_engineering.md` § "다이어그램 3개 = 에이전트의 3감각" ('귀' — Vector → VictoriaLogs/Metrics/**Traces**).

---

## 1. Context

### 1.1 The deferred third sense

ADR-MONO-007 staffed two of the three observability senses ("귀"): **logs** (VictoriaLogs) and **metrics** (VictoriaMetrics), both via Vector. It explicitly deferred the third — **traces** — with a documented condition (§ 2.1 D1 + § 6 item 4):

> "Traces (VictoriaTraces / Tempo equivalent) are explicitly deferred to a follow-up ADR (ADR-MONO-007a), condition: ... saga-spanning traces become the next gating signal. The trace layer is the riskiest of the three on stability + footprint and adding it before the simpler two pay off would dilute the ROI window."

The deferral was correct at 2026-05-12: no consumer needed a trace tree, and VictoriaTraces was beta. Two facts changed by 2026-05-28:

1. **A concrete consumer exists.** ADR-MONO-018 (ACCEPTED 2026-05-26) Phase 8 D4 requires an end-to-end trace tree spanning `console-web SSR → console-bff aggregation → 5 per-domain producer spans` (7 spans) so an operator can attribute a dashboard fan-out degrade to a specific domain. This is precisely the "saga-spanning [cross-product] signal" ADR-007 § 6 item 4 named as the gate.
2. **VictoriaTraces reached GA.** The VictoriaMetrics family shipped VictoriaTraces as a stable single-binary trace store, homogeneous with the already-deployed VictoriaLogs / VictoriaMetrics — eliminating the stability concern that drove the original deferral.

### 1.2 Why a policy ADR before the trace compose file

Identical to ADR-MONO-007 § 1.2. Three irreversible-once-set elements would be silently ratified by an implementation-first approach:

1. **Trace backend choice.** VictoriaTraces vs Tempo vs Jaeger. Footprint, query language (TraceQL family vs Jaeger UI), and operational homogeneity with the existing stack differ materially.
2. **Ingestion topology.** Producer OTLP → Vector (single collector) → VictoriaTraces, vs producer OTLP → VictoriaTraces direct (bypass Vector). The collector-mediated path keeps Vector as the single telemetry spine (ADR-007 D1 premise); the direct path is simpler but fragments the pipeline.
3. **Trace origination point.** Where the root span is born. Producers already auto-instrument inbound HTTP (Spring Boot + `micrometer-tracing-bridge-otel`), and console-bff already propagates `traceparent` on outbound RestClient calls (ObservationRegistry-wired). But **console-web (Next.js) has no OTel runtime** — so the trace tree currently has no root span; the BFF call arrives without a `traceparent`, and each producer starts a disconnected trace. Pinning the console-web instrumentation approach is an architecture decision, not a config detail.

This ADR pins all three. The compose-file edit (add VictoriaTraces service + Vector OTLP source/sink), the console-web `@opentelemetry` wiring, and the e2e trace-tree assertion land in **TASK-MONO-143** (the ADR-MONO-018 D4 execution task) once this policy is on disk.

### 1.3 Current-state ground truth (verified 2026-05-28)

| Layer | Trace state today | Gap |
|---|---|---|
| producers (5 domains) | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` on classpath; `management.otlp.tracing.endpoint: …:4318/v1/traces` configured; `MdcTraceFilter` puts `traceId` in MDC | export target (`:4318`) has **no listener** |
| console-bff | RestClient wired with `ObservationRegistry` → auto-injects W3C `traceparent` outbound; D7 attribution metrics live | inherits no parent span (console-web sends none) → starts its own trace root |
| console-web | server routes forward `Authorization` / `X-Tenant-Id` / `X-Request-Id` | **no `@opentelemetry/*`**, **no `traceparent` generation** → no tree root |
| stack | Vector + VictoriaMetrics + VictoriaLogs | **no VictoriaTraces, no Vector OTLP source/sink** |

The trace tree fails at two seams: (a) **no root** (console-web emits no `traceparent`), and (b) **no backend** (nothing ingests producer OTLP exports). This ADR pins the resolution for both.

---

## 2. Decision

### 2.1 D1 — Trace backend: VictoriaTraces

The trace store MUST be **VictoriaTraces** (single binary, VictoriaMetrics family), for the same footprint + operational-homogeneity reasons ADR-MONO-007 D1 chose VictoriaLogs + VictoriaMetrics.

| Property | Value |
|---|---|
| Component | VictoriaTraces (single binary) |
| Ingest protocol | OTLP/HTTP (`/insert/opentelemetry/v1/traces`) + Jaeger-compatible |
| Query | TraceQL-family + Jaeger JSON API (e2e assertion reads trace tree by `trace_id`) |
| Footprint budget | ~50 MB resident idle (homogeneous with VictoriaLogs/Metrics); raises the ADR-007 stack budget from ~135 MB to ~185 MB — still inside the 200 MB per-worktree cap (§ 4.2 risk) |
| Storage | tmpfs / anonymous volume, `--retentionPeriod=1d` (inherits ADR-007 D2 no-persistence) |

### 2.2 D2 — Ingestion: producer OTLP → Vector OTLP source → VictoriaTraces sink

Traces flow through **Vector as the single collector** (preserving ADR-007 D1's "Vector is the spine" premise), not direct producer→VictoriaTraces:

```
producer / console-bff  ──OTLP/HTTP :4318──▶  Vector (otlp source)  ──▶  VictoriaTraces (otlp sink)
console-web (Node OTel)  ──OTLP/HTTP :4318──▶  Vector (otlp source)  ──▶  VictoriaTraces
```

- Vector gains an `otlp` source (binds the `:4318` the producers already target — closing the "no listener" gap) + a VictoriaTraces sink. No producer config change (they already export to `:4318`).
- Rationale over direct OTLP: one telemetry spine to operate, one place to add sampling / redaction transforms later, consistent with the logs + metrics pipelines already in Vector.

### 2.3 D3 — Trace origination: console-web `@opentelemetry` Node SDK

console-web MUST originate the root span so the tree has a top. The server-side BFF-calling routes (`operator-overview/route.ts`, `domain-health/route.ts`) run under a Node OTel SDK (`@opentelemetry/sdk-node` + HTTP/fetch auto-instrumentation) that:

- starts a root span for the inbound operator request (SSR route handler),
- injects W3C `traceparent` into the outbound `fetch` to console-bff (so console-bff's existing ObservationRegistry instrumentation adopts it as parent instead of starting a new root),
- exports via OTLP/HTTP to the Vector `:4318` source.

The existing `X-Request-Id` correlation header is **retained** (log correlation for non-traced paths); `traceparent` is **added alongside**, not a replacement.

### 2.4 D4 — Topology + lifecycle: inherit ADR-MONO-007 D2 + D3 verbatim

No new topology decision. VictoriaTraces joins the **per-worktree ephemeral** compose project (ADR-007 D2) and the **opt-in, e2e-scoped** lifecycle (ADR-007 D3): off by default, activated by the observability Gradle profile / `up.sh`, idle-teardown, no CI activation. console-web's OTel SDK exports only when the OTLP endpoint env var resolves (stack up); otherwise it no-ops (no hard dependency on the stack for normal `next dev` / production).

### 2.5 D5 — Reconcile ADR-MONO-018 D4 (no D1-D8 body change)

ADR-MONO-018 D4 option A's "(+ VictoriaLogs / **VictoriaTraces** per the ADR-006 topology)" presumed a topology that did not exist. This ADR supplies it. ADR-018 D4's **decision is unchanged** — reuse the stack + add `trace_id` propagation strengthening; this ADR simply makes "the stack" actually include traces. ADR-018 receives a single additive reconcile note (§ History blockquote) pointing to ADR-007a as the trace-backend prerequisite; D1-D8 bodies byte-unchanged (HARDSTOP-04). The TASK-MONO-143 execution task depends on **this ADR ACCEPTED** as its dependency-correct base.

### 2.6 D6 — `/observe trace` skill: stub → full impl

ADR-MONO-007 § 2.4 D4 shipped `/observe trace --saga <sagaId>` as a Phase-2 stub "(full impl after ADR-MONO-007a)". This ADR is that gate. The full `/observe trace` implementation (query VictoriaTraces by `trace_id` / tag, return the span tree) lands in TASK-MONO-143 (or a dedicated skill-update task), rule-id namespace `OBSERVE-QUERY-NN` extended (06 = trace not found, 07 = incomplete tree / broken span chain).

### 2.7 D7 — Phasing: this ADR → TASK-MONO-143 execution

| Step | Task | Scope | Gate |
|---|---|---|---|
| 0 (this ADR) | TASK-MONO-142 | Trace backend + ingestion + origination + reconcile pinned. No impl files. | This PR. |
| 1 | TASK-MONO-143 | `infra/observability/` VictoriaTraces service + Vector OTLP source/sink; console-web `@opentelemetry` SDK wiring; federation e2e trace-tree assertion (7-span tree by `trace_id` in VictoriaTraces); footprint re-measure. | TASK-MONO-143 ready/ when this ADR ACCEPTED (this PR merges). |

---

## 3. Alternatives Considered

### 3.1 Trace backend

| Option | Pros | Cons | Decision |
|---|---|---|---|
| VictoriaTraces (D1) | Single binary; VictoriaMetrics family homogeneity (same ops, same image registry, same retention flags); ~50 MB; OTLP + Jaeger ingest; GA 2026 | Newest of the three VM components | **Chosen** |
| Grafana Tempo | Mature; TraceQL native | Pulls the Grafana stack operational model into a Vector/VM shop; heavier; cross-family | Rejected — ADR-007 D1 already rejected Grafana stack on footprint + homogeneity |
| Jaeger (all-in-one) | Ubiquitous; rich UI | Cassandra/ES backend for non-toy retention; all-in-one is memory-only/ephemeral but adds a 4th storage idiom | Rejected — non-homogeneous; the agent queries by API not UI (ADR-007 § 3.4) |
| Keep deferred (no trace backend) | Zero added footprint | Leaves ADR-018 D4 partially-implementable indefinitely; the gate signal has arrived | Rejected — the deferral condition is now satisfied |

### 3.2 Ingestion topology

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Producer OTLP → Vector → VictoriaTraces (D2) | One spine; future sampling/redaction in one place; consistent with logs+metrics pipelines | One more hop | **Chosen** |
| Producer OTLP → VictoriaTraces direct | Fewer hops | Fragments the pipeline; VictoriaTraces would need its own OTLP listener config separate from Vector; two telemetry ingress idioms | Rejected — defeats ADR-007 D1 "Vector is the spine" |
| OpenTelemetry Collector (separate from Vector) | Vendor-neutral trace transport | Adds a 4th process; ADR-007 § 3.1 already deferred OTel Collector ("Vector handles the same transport role with simpler config") | Rejected — same reasoning as ADR-007 |

### 3.3 console-web trace origination

| Option | Pros | Cons | Decision |
|---|---|---|---|
| `@opentelemetry/sdk-node` + auto-instrumentation (D3) | Standards-based; auto-injects `traceparent` into outbound fetch; minimal hand-written code; the BFF + producers already speak W3C trace context | One Node dependency + an SDK bootstrap file | **Chosen** |
| Manual `traceparent` synthesis in route handlers | No dependency | Hand-rolled W3C trace-context generation is error-prone (version/flags/sampling bits); no span export; reinvents the SDK | Rejected — re-implements a solved standard (the BE-301 "re-implement = behavior-drift origin" lesson) |
| Skip console-web; root the tree at console-bff | No frontend change | Loses the SSR span — operator can't see the console-web → bff latency segment; the "7-span tree" ADR-018 D4 names becomes a 6-span tree missing the entry point | Rejected — ADR-018 D4 explicitly names console-web SSR as the tree root |

---

## 4. Consequences

### 4.1 Positive

- **ADR-MONO-018 D4 unblocked** on a dependency-correct base — TASK-MONO-143 can implement + assert the trace tree without baking an undocumented backend choice.
- **Third observability sense staffed** — the "귀" gap (ADR-007 § 1.1) fully closed (logs + metrics + traces); `/observe trace` graduates from stub to real.
- **Zero producer change** — producers already export OTLP to `:4318`; this ADR makes something listen there. The "producers already emit traceId headers" claim in ADR-018 D4 becomes operationally true end-to-end.
- **Trace = strongest fan-out attribution** — an Operator Overview degrade now resolves to a specific producer span, complementing the D7 per-domain metrics with causal ordering.

### 4.2 Negative / risks

- **Footprint +50 MB** — stack budget ~135 MB → ~185 MB resident, inside the 200 MB per-worktree cap but with thinner headroom. Mitigation: `--retentionPeriod=1d` + tmpfs (inherits ADR-007 D2); TASK-MONO-143 re-measures footprint and reports (ADR-007 Phase 1 precedent).
- **console-web gains a runtime dependency** — `@opentelemetry/sdk-node` bootstrap runs in the Next.js server runtime. Mitigation: D4 no-op-when-endpoint-unset means production/`next dev` without the stack is unaffected; the SDK only activates under the e2e/observability profile.
- **VictoriaTraces newest of the VM trio** — lowest operational track record. Mitigation: ephemeral + e2e-scoped (a flaky trace store degrades only an interactive verification surface, never CI or production); fallback = the deferral can be re-instated by reverting TASK-MONO-143 without touching logs/metrics.

### 4.3 Mitigation owner

TASK-MONO-143 owns footprint re-measurement + the `/observe trace` failure surface (`OBSERVE-QUERY-06/07`). The console-web SDK no-op-when-unset invariant is a TASK-MONO-143 acceptance criterion.

---

## 5. Verification

This is a meta-policy ADR (like its parent ADR-MONO-007). Verification belongs to TASK-MONO-143:

- VictoriaTraces joins the ephemeral stack; `up.sh` starts it; footprint re-measured ≤ 200 MB.
- A federation e2e Operator Overview run produces **one** trace tree (single `trace_id`) spanning console-web SSR → console-bff aggregation → 5 producer spans, queryable from VictoriaTraces by `trace_id`.
- console-web with the OTLP endpoint env unset boots + serves normally (no-op invariant).
- `/observe trace` returns the span tree; failure cases emit 4-block `OBSERVE-QUERY-06/07`.

No verification by this ADR's own merge — the policy lives or dies on whether TASK-MONO-143 adopts it (ADR-MONO-007 / ADR-MONO-006 same self-non-verifying precedent).

---

## 6. Outstanding follow-ups

1. **TASK-MONO-143 — ADR-MONO-018 D4 observability federation impl.** Filed in `tasks/ready/` after this ADR merges. Scope: VictoriaTraces + Vector OTLP source/sink + console-web `@opentelemetry` + federation e2e trace-tree assertion + footprint re-measure + `/observe trace` full impl. Owner: monorepo. Model: Sonnet 4.6 (routine compose / SDK wiring / TS assertion per ADR-018 § D6 row 8) with Opus dispatcher verify.
2. **ADR-MONO-018 D5 (multi-tenant isolation regression IT cohort)** — ✅ **CLOSED** (2026-06-18 audit). Satisfied end-to-end by the producer-side per-domain cross-tenant-deny ITs (wms `OidcAuthIntegrationTest`, scm `MultiTenantIsolationIntegrationTest`, finance `CrossTenantHttpIntegrationTest`, GAP/iam `AdminAuditTenantScopeIntegrationTest`, erp `CrossTenantHttpIntegrationTest` via TASK-ERP-BE-004) + the console-bff D6 pass-through deny IT (`CrossTenantDenyIntegrationTest`, TASK-PC-BE-006). A fresh cohort task (MONO-296) was opened then **dropped** as duplicate — every D5 slice already has a green `@Tag("integration")` IT on `main`. (Was previously listed here as "the other open Phase 8 execution axis"; that wording was stale.)
3. **VictoriaTraces production-maturity revisit** — if a project reaches Operations maturity and needs a persistent (non-ephemeral) trace store, a follow-up ADR addresses persistence + retention (out of scope here; ephemeral-only per D4).

---

## 7. Provenance

ADR-MONO-007 § 2.1 D1 deferral + § 6 item 4 gate ("filed when ... a saga-spanning bug emerges that the LogQL / PromQL surfaces cannot reach"). The gate signal = ADR-MONO-018 (ACCEPTED 2026-05-26) Phase 8 D4 cross-product trace-tree demand. Surfaced 2026-05-28 during TASK-MONO-142 scoping of the ADR-018 D4 execution task, when the missing trace backend was detected as an ADR-018 ↔ ADR-007 conflict (HARDSTOP-09) and reported; user selected Option A ("ADR-MONO-007a 선작성, 정석") authorizing this ADR.

D4 OVERRIDE applies per ADR-MONO-003a § D1.3 — Harness gap series scope (ADR-007a is the trace continuation of the gap #3 harness observability series; same OVERRIDE authority as ADR-007).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (ADR drafting + trace-backend/topology judgment surface; TASK-MONO-143 impl may downgrade to Sonnet 4.6 for compose / SDK / assertion authoring).
