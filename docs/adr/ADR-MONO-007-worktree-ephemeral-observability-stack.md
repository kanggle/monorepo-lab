# ADR-MONO-007 — Worktree-isolated Ephemeral Observability Stack

**Status:** ACCEPTED
**Date:** 2026-05-12
**History:** PROPOSED 2026-05-12 (TASK-MONO-064) → ACCEPTED 2026-05-12 (same PR — meta-policy ADR pinning stack choice + topology before any impl commit; no implementation gating beyond what this ADR itself specifies).
**Decision driver:** TASK-MONO-064, sourced from memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" gap #3 ("워크트리당 ephemeral 관측 스택") and § "다이어그램 3개 = 에이전트의 3감각" ('귀' — Vector → VictoriaLogs/Metrics/Traces, "지루한 표준 쿼리어").
**Supersedes:** none — first ADR formalising observability topology for agent-driven verification flows.
**Related:** [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D1.3 (Harness gap series scope, D4 OVERRIDE authority), [ADR-MONO-006](ADR-MONO-006-lint-remediation-as-agent-context.md) (gap A precedent — meta-policy ADR + 4-block remediation), memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" item #3.

---

## 1. Context

### 1.1 The gap

OpenAI's Harness Engineering report (Lopopolo, 2025) frames the productive agent loop as a three-sense system: **눈** (UI visibility, Chrome DevTools MCP), **귀** (system signals via Vector → VictoriaLogs/Metrics/Traces with LogQL/PromQL/TraceQL), **기억** (markdown-encoded decision context). monorepo-lab has equivalents for "눈" (Playwright e2e + screenshot artifacts) and "기억" (tasks/ + rules/ + memory/ + ADRs), but **"귀" is unstaffed**.

Today, when an agent runs a master-service e2e against `:projects:wms-platform:tests:e2e` or the gateway-master live-pair Testcontainers job, the only feedback channel is the gradle test report and stdout / stderr lines that the agent has to grep visually. There is:

- no structured log query (each service emits Logback to console, no aggregator)
- no metrics dashboard for transient e2e runs (Micrometer counters exist in code but are never queried because there is no scrape target during e2e)
- no trace correlation across saga boundaries (outbox publishers emit `traceId` headers, but no collector picks them up)

The OpenAI pattern stipulates that this layer be **ephemeral and per-worktree** — not a shared production stack, not a permanent dev-machine service. Each agent loop spins its own observability spine, queries it during verification, and tears it down. Footprint goal: under 200 MB resident, under 30 s cold start.

memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" ranks this as **gap #3** — the last open item in the harness gap closure series, after gap A (lint remediation injection, ADR-MONO-006 ACCEPTED 2026-05-12) and gap #2 (doc-gardening recurrence, TASK-MONO-062 ACCEPTED 2026-05-12).

### 1.2 Why a policy ADR before any compose file

This decision has three irreversible-once-set elements that an implementation-first approach would either prematurely lock in or silently ratify:

1. **Stack family.** Vector + VictoriaMetrics / VictoriaLogs vs. Grafana stack (Loki / Prometheus / Tempo) vs. ELK vs. cloud-hosted. Footprint, query DX, and downstream tool integrations differ by an order of magnitude. Choosing "whatever was easiest first" creates a 6-month migration tax.
2. **Topology.** Single shared stack vs. one per worktree. The shared variant is cheaper to operate but defeats the agent loop isolation — concurrent agent runs would cross-contaminate logs / metrics, and an agent could "verify" against another worktree's state. Per-worktree is the OpenAI premise; this ADR pins it.
3. **Lifecycle ownership.** Always-on dev convenience vs. test-scoped ephemeral. Always-on inflates resident memory across the dev machine and conflates "did my e2e produce this log?" with "is that log from yesterday's run?". Ephemeral is the OpenAI premise and avoids both issues; this ADR pins it.

This ADR pins all three. Implementation files (compose templates, skill bodies, sample e2e wirings) follow in separate task files (Phase 1 / 2 / 3 enumerated in § 6) once the policy is on disk.

### 1.3 Why now, not later

Two factors converged in May 2026:

- **e2e accumulation.** As of 2026-05-12, the CI pipeline runs 3 E2E Testcontainers jobs (gateway-master live-pair, fan-platform v1 live-trio, scm-platform v1 cross-service) plus 3 Integration jobs (master+notification, global-account-platform, scm-platform). Each new e2e adds a verification surface the agent cannot currently introspect beyond gradle stdout.
- **Phase 5 trigger proximity.** ADR-MONO-003a § D4 redefines Phase 5 launch as "user-explicit + ADR-MONO-003b", which means template extraction will fork each project into a standalone repo. Standalone repos need to be reproducible by an agent in isolation — an observability stack the agent can query is a precondition for "this standalone build verifies itself", not a nice-to-have.

Waiting until either e2e count doubles or Phase 5 launches makes the decision retroactive — the wrong shape of stack would already be wired into N e2e jobs. Pinning the choice now keeps the cost linear.

---

## 2. Decision

### 2.1 D1 — Stack: Vector + VictoriaLogs + VictoriaMetrics

The observability spine MUST be composed of three components, each chosen for footprint and stable query language:

| Layer | Component | Query language | Footprint (resident, idle) | Rationale |
|---|---|---|---|---|
| Collector | [Vector](https://vector.dev/) | (configuration as data — no runtime query) | ~35 MB | Single binary, no JVM, no Python runtime. Source / transform / sink pipeline in TOML. Stable since 2021. |
| Log store | [VictoriaLogs](https://docs.victoriametrics.com/victorialogs/) | LogQL (Grafana-compatible subset) | ~50 MB | Same family as VictoriaMetrics — operationally homogenous. Single binary. LogQL is the de-facto standard for log queries. |
| Metric store | [VictoriaMetrics](https://victoriametrics.com/) | PromQL (full compatibility) | ~50 MB | Drop-in Prometheus replacement, 4-5× more memory-efficient than Prometheus. Single binary. |

Traces (VictoriaTraces / Tempo equivalent) are explicitly **deferred to a follow-up ADR** (ADR-MONO-007a, condition: gap #3 Phase 2 closure has validated the LogQL/PromQL query DX on the agent side, and saga-spanning traces become the next gating signal). The trace layer is the riskiest of the three on stability + footprint and adding it before the simpler two pay off would dilute the ROI window.

> **Additive note (2026-05-28, [ADR-MONO-007a](ADR-MONO-007a-trace-layer.md) ACCEPTED):** the trace deferral above is **RESOLVED**. The gate ("saga-spanning traces become the next gating signal") was satisfied by [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) Phase 8 D4 (cross-product trace-tree demand). ADR-MONO-007a pins the trace backend = **VictoriaTraces** (VM-family homogeneity) + ingestion = producer/console-web OTLP → Vector OTLP source → VictoriaTraces sink + console-web `@opentelemetry` SDK trace origination. The D1 Vector + VictoriaLogs + VictoriaMetrics choice above is byte-unchanged; ADR-007a is purely additive (the 3rd component of the same stack). Impl lands in TASK-MONO-143.

Per-component resident-memory budgets sum to ~135 MB; the 200 MB target leaves a 65 MB headroom for service-side OTel exporters that emit into Vector. Phase 1 verification quantifies actual footprint before Phase 2 expands consumption sites.

### 2.2 D2 — Topology: per-worktree ephemeral

Each git worktree gets its own observability stack instance, with the following properties:

- **One docker-compose project per worktree.** Project name derived from `git rev-parse --show-toplevel | sha256 | head -c 8` so concurrent worktrees do not collide on docker resource names.
- **Dynamic port allocation.** Vector / VictoriaLogs / VictoriaMetrics bind on `127.0.0.1:0` (OS-assigned ephemeral port). The chosen ports are written to `.observability/ports.env` inside the worktree (gitignored), and the skill that issues queries reads from this file.
- **Per-worktree docker network.** Services under test (gateway / master / etc.) on the same compose project join this network so emitting telemetry needs no external DNS or IP coordination.
- **No volume persistence.** All storage is tmpfs / anonymous volumes — when the compose project is torn down, history is gone. This is intentional: the agent's record of a session belongs in `tasks/` / commit message / PR body, not in a log store that outlives the session.

This means an agent running `task/be-143-partner-bootstrap` and another running `task/mono-065-observability-phase-1` in parallel see *different* logs, metrics, and ports — there is no shared state to misattribute.

### 2.3 D3 — Lifecycle: opt-in, e2e-scoped

The stack is **off by default**. Activation gates:

- **e2e Gradle task profile.** Tests under `projects/<project>/tests/e2e/` and Testcontainers integration suites under `apps/<service>/src/integrationTest/` declare an `observability` Gradle attribute. When `-Pobservability=on` is passed (or `gradle.properties` enables it per-project), the Gradle initialisation script invokes `scripts/observability/up.sh` before the test classpath starts, and `down.sh` after.
- **Manual override.** `scripts/observability/up.sh` / `down.sh` can be invoked directly without Gradle for ad-hoc debugging (e.g., spin up the stack, run `./gradlew bootRun` for a single service, query logs).
- **Idle teardown.** If no service binds for 5 minutes after `up.sh`, the stack auto-tears-down. Prevents zombie stacks if a test crashes between `up` and any service start.

CI explicitly **does not** activate the stack — CI relies on gradle test reports + artifact upload, which is the right shape for non-interactive verification. The stack exists for interactive agent loops (Cursor / Claude Code / Codex sessions) where the operator (human or agent) wants to query mid-flight.

### 2.4 D4 — DX target: skill-mediated query, not raw HTTP

Agents query the stack through a dedicated skill `.claude/skills/cross-cutting/observability-query/` (created in Phase 2). The skill exposes a single command surface:

```
/observe logs '{service="master-service"} |= "PartnerCreated"'         # LogQL
/observe metrics 'rate(outbound_tms_request_count_total[1m])'           # PromQL
/observe trace --saga <sagaId>                                          # (Phase 2 stub, full impl after ADR-MONO-007a)
```

The skill resolves the worktree's `.observability/ports.env`, builds the HTTP request, paginates the result, and returns it in a shape the agent can chain into the next turn. Without this skill, the agent would need to know the dynamic port (impossible without `cat`-ing the env file) and emit raw `curl` commands — defeating the "agent's next-turn context" pattern the gap A ADR (MONO-006) put in place.

The skill output MUST follow the 4-block remediation format when reporting query failures (no data / port not bound / parse error) — same precedent as `hardstop-detect.ps1` and the scheduled doc-gardening routines. Rule-id namespace: `OBSERVE-QUERY-NN` (01 = stack not up, 02 = port file missing, 03 = query syntax error, 04 = no matching results, 05 = pagination overflow).

### 2.5 D5 — Phasing: Phase 0 (this ADR) → Phase 1 → Phase 2 → Phase 3

The implementation is decomposed into three follow-up phases, each filed as a separate `tasks/ready/` candidate after this ADR lands:

| Phase | Task ID (planned) | Scope | Gate |
|---|---|---|---|
| 0 (this ADR) | TASK-MONO-064 | Stack + topology + lifecycle + DX target pinned. No implementation files. | This PR. |
| 1 | TASK-MONO-065 | Docker-compose template under `infra/observability/` + `scripts/observability/{up,down}.sh` + Vector pipeline config (Logback → VictoriaLogs + Micrometer → VictoriaMetrics) + **1 reference wiring** (gateway-master live-pair). Footprint + start-time measured and reported. | Phase 1 task ready/ when ADR-MONO-007 ACCEPTED (this PR merges). |
| 2 | TASK-MONO-066 | `.claude/skills/cross-cutting/observability-query/SKILL.md` + skill body + `/observe` slash-command wiring + agent DX validation (one sample e2e where the skill is invoked mid-test). | Phase 2 task ready/ when Phase 1 (MONO-065) merges. |
| 3 | TASK-MONO-067 | Remaining e2e suites wired (fan-platform v1 live-trio, scm-platform cross-service, all Integration jobs) + Rancher Desktop compatibility validation (memory `project_testcontainers_docker_desktop_blocker.md`) + footprint regression test in CI. | Phase 3 task ready/ when Phase 2 (MONO-066) merges. |

Each phase is independently mergeable — Phase 1 alone delivers a working stack for one e2e, Phase 2 adds the agent DX, Phase 3 expands coverage. The phasing matches the gap A precedent (MONO-059 ADR + standard → MONO-060 hook → MONO-061 dogfooding fix).

---

## 3. Alternatives Considered

### 3.1 Stack family

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Vector + VictoriaLogs/Metrics (D1) | ~135 MB resident, stable query languages (LogQL + PromQL), single-binary per component, Grafana-compatible queries | VictoriaTraces still beta (deferred to ADR-MONO-007a anyway) | **Chosen** |
| Grafana stack: Loki + Prometheus + Tempo | Industry-standard, abundant ecosystem | Loki + Prometheus + Tempo + Grafana ≈ 600 MB resident — exceeds 200 MB target. Per-worktree multiplication makes this infeasible on dev machines. | Rejected |
| ELK (Elasticsearch + Logstash + Kibana) | Very mature, rich query DSL | JVM-based, 1+ GB resident easily — fully exceeds target. JVM cold start ~60 s. Operational complexity high. | Rejected |
| Datadog / NewRelic / cloud-hosted | Zero local footprint, professional UX | Violates local-first principle (offline must work). Recurring cost. Outbound traffic from arbitrary worktrees is a privacy / leak concern (logs may contain test seed data). | Rejected |
| OpenTelemetry Collector + custom backend | Vendor-neutral telemetry transport | Collector alone doesn't store / query — still need backends. Adding it on top of D1 would be additive but premature; Vector handles the same transport role with simpler config. Revisit if multi-backend needed. | Deferred (Phase 1 may revisit if Vector turns out to limit transport flexibility) |

### 3.2 Topology

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Per-worktree ephemeral (D2) | Agent loop isolation; no cross-session contamination; matches OpenAI premise | Per-worktree footprint multiplies under concurrent loops (2 × 135 MB = 270 MB) | **Chosen** |
| Single shared dev-machine stack | Lower aggregate footprint; familiar dev pattern | Cross-worktree contamination — agent on worktree A sees worktree B's logs. Defeats the entire premise. | Rejected — premise violation |
| Per-service stack (one per worktree × N services) | Service-level isolation | Footprint multiplies further; LogQL aggregation across services breaks; no reason to isolate at service level when worktree is the natural unit | Rejected |
| Co-locate in service JVM (e.g., embedded Prometheus exposition) | No extra processes | Already partial today (Micrometer exposes `/actuator/prometheus`) but there's no aggregator — the agent still can't run cross-service queries. Doesn't solve the gap. | Already partial; insufficient |

### 3.3 Lifecycle ownership

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Opt-in, e2e-scoped (D3) | Zero footprint when idle; explicit ownership; clean session boundary | Operator (agent or human) must remember to enable; idle teardown is a safety net not a guarantee | **Chosen** |
| Always-on (start with shell, persist) | No activation friction | 135+ MB resident permanently; risk of stale session data masquerading as current | Rejected |
| Per-test JUnit lifecycle (Testcontainers-style) | Atomic with test; no idle window at all | Test-class start time dominated by stack cold-start (~30 s × N test classes); test parallelism breaks observability cross-references | Rejected (test-class is wrong granularity; suite-level is right) |
| External daemon (systemd / launchd) | Mature lifecycle management | Adds OS-level surface; not portable across dev OSes (memory `feedback_*.md` records team uses both Windows + macOS) | Rejected |

### 3.4 DX surface

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Skill-mediated `/observe` (D4) | Stable contract; port resolution hidden; output shape can include the 4-block format for failure cases; matches "agent's next-turn context" pattern | One more skill to maintain | **Chosen** |
| Raw HTTP / curl | Zero abstraction | Agent must `cat` port file then build URL; verbose; error handling becomes the agent's problem each invocation | Rejected |
| Embedded query in stdout (test reports include log/metric snapshots) | Surfaces relevant signals automatically | Bloats test reports; arbitrarily selects what to surface; defeats interactive query | Rejected (could complement, not replace) |
| Grafana UI port-forward | Visual DX | Agent can't read pixels (no Chrome DevTools MCP in monorepo-lab yet — that's gap #4); humans can but humans aren't the primary audience | Rejected as primary; viable as complement for humans |

---

## 4. Consequences

### 4.1 Positive

- **Agent self-verification surface expands.** A failing e2e can be triaged by the agent itself via LogQL / PromQL queries, not by surfacing the entire gradle output to the human. Closes the "에이전트의 귀" gap.
- **Phase 5 standalone reproducibility precondition met.** Standalone project repos extracted in Phase 5 inherit the same compose template — they ship with their own observability story rather than requiring the user to wire one up.
- **CI / local parity.** The same Vector pipeline config is reused for production observability scaffolding when projects reach Operations maturity (admin-service already has the runbook precedent). Local-first does not mean local-only.
- **Drift detection.** Once Phase 2 lands, the doc-gardening routine (gap #2) can extend its scope to query the stack's metric series and flag stale dashboards / missing labels. Cross-gap reinforcement.

### 4.2 Negative / risks

- **Operational learning curve.** Vector pipeline syntax + LogQL + PromQL is three new query surfaces for the team. Mitigation: Phase 2 skill body documents the ~5 canonical queries each agent will reuse; the agent itself does not need to "learn" LogQL beyond pattern-matching against examples.
- **Footprint creep.** 200 MB per active worktree is the cap; concurrent worktrees multiply. Mitigation: D3 idle teardown + D2 per-worktree project naming means the stack is gone when not needed. CI footprint test in Phase 3 catches regressions.
- **Trace deferral lock-in.** ADR-MONO-007a may not happen for months; saga-spanning debugging continues to rely on `traceId` log greps in the interim. Acceptable cost — trace UX adds risk Phase 2 doesn't yet need.
- **Compose-only assumption.** Some team members use Rancher Desktop (memory blocker), some use Docker Desktop. Phase 3 validates against both; if Rancher cannot host the stack reliably, the validation surfaces it before Phase 5.

### 4.3 Mitigation owner

The skill in Phase 2 owns the DX failure surface (rule IDs `OBSERVE-QUERY-NN`). Footprint regressions land in `tests/observability-footprint/` in Phase 3. Trace deferral is tracked in this ADR's § 6 Outstanding follow-ups and revisited at Phase 2 closure.

---

## 5. Verification

This ADR is a meta-policy ADR. Verification belongs to the follow-up phases:

- **Phase 1 (MONO-065 — proposed):** stack starts on `scripts/observability/up.sh`, gateway-master live-pair test emits logs/metrics that VictoriaLogs/VictoriaMetrics ingest, manual curl against either endpoint returns expected records. Footprint + start-time logged into the task's Implementation Notes.
- **Phase 2 (MONO-066 — proposed):** `/observe logs ...` and `/observe metrics ...` skill invocations return parseable results during a sample e2e; failure cases emit 4-block messages with `OBSERVE-QUERY-NN` IDs.
- **Phase 3 (MONO-067 — proposed):** all 3 E2E + 3 Integration Testcontainers jobs adopt the stack opt-in; footprint regression test added to CI; Rancher Desktop compatibility documented.

No verification work is required by this ADR's own merge — the policy lives or dies on whether the phases adopt it. ADR-MONO-006 used the same pattern (the standard ADR did not verify itself; the hook + dogfooding fix in MONO-060/061 verified the standard's claim).

---

## 6. Outstanding follow-ups

1. **TASK-MONO-065 — Phase 1 stack scaffolding.** To be filed in `tasks/ready/` after this ADR merges. Owner: monorepo. Estimate: ~1 spec PR + 1 impl PR.
2. **TASK-MONO-066 — Phase 2 skill + slash command.** Filed after MONO-065 merges.
3. **TASK-MONO-067 — Phase 3 coverage expansion + footprint regression CI.** Filed after MONO-066 merges.
4. **ADR-MONO-007a — Trace layer.** ~~Filed when MONO-066 closes and a saga-spanning bug emerges that the LogQL / PromQL surfaces cannot reach.~~ → **FILED + ACCEPTED 2026-05-28 ([ADR-MONO-007a](ADR-MONO-007a-trace-layer.md), TASK-MONO-142).** Gate satisfied by ADR-MONO-018 Phase 8 D4 cross-product trace-tree demand (not a bug — a federation hardening requirement). Trace backend = VictoriaTraces; impl = TASK-MONO-143.
5. **gap #4 — Chrome DevTools MCP integration** (memory `reference_openai_harness_engineering.md` § "다이어그램 3개" '눈' row). Separate ADR (ADR-MONO-008+) when frontend visual verification becomes a recurring blocker. Out of scope for this ADR.

---

## 7. Provenance

Memory `reference_openai_harness_engineering.md` (2026-05-07 receipt) § "monorepo-lab 갭 매핑" — gap #3 (워크트리당 ephemeral 관측 스택) flagged as the third-priority gap after gap A (ADR-MONO-006) and gap #2 (TASK-MONO-062). Both prior gaps closed 2026-05-12; gap #3 is the final open item in the harness gap series.

D4 OVERRIDE applies per ADR-MONO-003a § D1.3 — Harness gap series scope, user-acknowledged 2026-05-12 (canonicalised by ADR-MONO-003a § D1).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (ADR drafting + stack-choice judgment surface; Phase 1+ implementations may downgrade to Sonnet 4.6 for routine compose / script authoring).
