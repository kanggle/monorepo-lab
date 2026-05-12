# Task ID

TASK-MONO-066

# Title

Observability query skill + `/observe` slash command + Gradle e2eTest integration (OpenAI Harness gap #3 Phase 2)

# Status

ready

# Owner

monorepo

# Task Tags

- code
- skill
- harness

---

# Required Sections

- Goal
- Scope (In / Out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Convert ADR-MONO-007 § 2.4 D4 + § 2.5 D5 **Phase 2** from policy into running code: a `.claude/skills/cross-cutting/observability-query/` skill that wraps LogQL / PromQL queries against the Phase 1 stack with stable failure semantics (4-block `OBSERVE-QUERY-NN` rule IDs), plus the Gradle `-Pobservability=on` integration that wires the stack into `gateway-service:e2eTest` (the live-pair Testcontainers suite) without breaking the default Docker-free run.

This is the third of four phases. Phase 0 (TASK-MONO-064, ADR-MONO-007 ACCEPTED) is in done/; Phase 1 (TASK-MONO-065, stack scaffolding) is in done/ as of 2026-05-13. Phase 3 (TASK-MONO-067 — coverage + Rancher validation + CI footprint regression) follows after this one.

The skill is the **agent's DX surface** for the stack — without it, the agent must `cat` the per-worktree port file and emit raw `curl` commands every query, defeating the "lint error = next-turn context" pattern that gap A (ADR-MONO-006) put in place. With it, the agent issues `/observe logs '{service="master-service"} |= "PartnerCreated"'` and gets a structured result whose failure cases route through the existing 4-block remediation grammar.

The Gradle integration solves the **testcontainers-network coupling problem** flagged in TASK-MONO-065 § Out of Scope. The solution is a named pre-existing docker network: when `-Pobservability=on` is passed, the e2eTest task creates network `wms-observability-e2e-<worktree-hash>` beforehand, runs the observability stack against it (`scripts/observability/up.sh --network …`), and the modified `E2EBase` resolves a Testcontainers `Network` against that name via `createNetworkCmdModifier`. Without the flag, e2eTest behaviour is byte-identical to Phase 1.

---

# Scope

## In Scope

### A. `.claude/skills/cross-cutting/observability-query/`

A new skill directory with:

- **`SKILL.md`** — frontmatter (`name: observability-query`, `description`, `category: cross-cutting`), invocation surface description, query DSL primer (LogQL fields available — service/level/traceId/worktree/message; PromQL series available — JVM, http_server_requests, Micrometer counters / gauges), failure mode catalog (`OBSERVE-QUERY-NN` 01..05), reference links to ADR-MONO-007 + `infra/observability/README.md`.
- **`scripts/query-logs.sh`** — POSIX bash wrapper around VictoriaLogs `/select/logsql/query`. Sources `.observability/ports.env`, URL-encodes the query argument, paginates if the result exceeds the configured cap (default 100 entries), emits the raw JSON-lines on stdout or a 4-block `OBSERVE-QUERY-NN` error on stderr.
- **`scripts/query-metrics.sh`** — POSIX bash wrapper around VictoriaMetrics `/api/v1/query` (instant) and `/api/v1/query_range` (range, when `--range <start>-<end>` flag is passed). Same failure semantics as `query-logs.sh`.

The skill body documents both as the canonical entry points and pins the rule-id namespace:

| Rule ID | Trigger |
|---|---|
| `OBSERVE-QUERY-01` | Stack not up — `.observability/ports.env` missing (instructs the operator to run `up.sh` first) |
| `OBSERVE-QUERY-02` | Port file present but stack containers not running (instructs `down.sh` + `up.sh` re-cycle) |
| `OBSERVE-QUERY-03` | Query syntax error — VictoriaLogs/VictoriaMetrics returned 400 with parser message (instructs to consult the LogQL/PromQL primer in the skill body) |
| `OBSERVE-QUERY-04` | No matching results within the time window — distinguishes "stack works, no data" from genuine syntax failures (instructs to widen the time window or relax the matcher) |
| `OBSERVE-QUERY-05` | Pagination overflow — result exceeded the configured cap; the operator should refine the query (instructs the `--limit` flag) |

### B. `E2EBase.java` — optional named observability network

Modify `projects/wms-platform/apps/gateway-service/src/e2eTest/java/com/wms/gateway/e2e/E2EBase.java` so that when the system property `wms.e2e.observabilityNetwork` is set (the Gradle integration injects this when `-Pobservability=on`), the e2e suite uses the named docker network instead of `Network.newNetwork()`. Property unset → behaviour identical to today (no regression for the default CI / local Docker-free path).

The mechanism is `Network.builder().createNetworkCmdModifier(cmd -> cmd.withName(System.getProperty("wms.e2e.observabilityNetwork")))` — Testcontainers honours the request and reuses the named network if it already exists (created by `up.sh`).

### C. `gateway-service/build.gradle` — `-Pobservability=on` profile

Extend the existing `tasks.register('e2eTest', Test) { ... }` block: when `project.hasProperty('observability')` is true AND the value is `'on'`, the task:

1. `doFirst { exec { commandLine 'bash', '-c', "docker network create wms-observability-e2e-${workTreeHash} || true" }; exec { commandLine 'bash', "$rootDir/scripts/observability/up.sh", '--network', "wms-observability-e2e-${workTreeHash}" } }`
2. Adds the system property `wms.e2e.observabilityNetwork = "wms-observability-e2e-${workTreeHash}"` for the test JVM.
3. `doLast { exec { commandLine 'bash', "$rootDir/scripts/observability/down.sh" }; exec { commandLine 'bash', '-c', "docker network rm wms-observability-e2e-${workTreeHash} || true" } }`

When the flag is absent, none of the above runs — the e2eTest task is byte-identical to today's behaviour.

### D. `infra/observability/README.md` — Phase 2 usage paths

Append a "## Phase 2: Gradle e2eTest mode" section documenting:

- Invocation: `./gradlew :projects:wms-platform:apps:gateway-service:e2eTest -Pobservability=on`
- What happens behind the scenes (network creation → stack up → e2eTest joins → stack down → network teardown)
- How to query during a long-running e2e using the new skill (`./.claude/skills/cross-cutting/observability-query/scripts/query-logs.sh '{service="gateway-service"} |= "trace"'`)

### E. `infra/observability/README.md` — quick-reference for skill rule IDs

Cross-reference the `OBSERVE-QUERY-NN` table from the skill body in the README's troubleshooting section so operators see both `OBSERVE-SCAFFOLD-NN` (Phase 1) and `OBSERVE-QUERY-NN` (Phase 2) under one roof.

## Out of Scope

- **`OBSERVE-QUERY-06+` extensions** for trace-layer queries. Trace layer is ADR-MONO-007a deferred; until that ADR lands, the skill has no trace query path.
- **Auto-start of the stack from within e2eTest** (Testcontainers ComposeContainer pattern). The named-network handoff between `up.sh` and Testcontainers is the lower-coupling option chosen here; switching to ComposeContainer would entangle the Testcontainers lifecycle with the standalone `up.sh` lifecycle, breaking the "manual mode" entry point that Phase 1 verified.
- **Other services' e2e suites.** Only `gateway-service:e2eTest` is wired in this task. Other suites (`fan-platform`, `scm-platform`, `global-account-platform`) join in Phase 3 (MONO-067) where coverage expansion + footprint regression test land together.
- **Idle teardown daemon** (the 5-min auto-teardown promised by ADR § 2.3 D3). Belongs to Phase 3 — Phase 2 still requires explicit `down.sh` in the operator workflow + automatic teardown in the Gradle path.
- **CI activation.** ADR § 2.3 D3 explicit. The `-Pobservability=on` flag stays off for the CI e2eTest job; CI continues to rely on gradle test reports + artifact upload.
- **Service-side telemetry changes.** Same as Phase 1.

---

# Acceptance Criteria

- [ ] `.claude/skills/cross-cutting/observability-query/SKILL.md` exists with the standard skill frontmatter + the 5 `OBSERVE-QUERY-NN` rule-ID table + LogQL/PromQL primer.
- [ ] `.claude/skills/cross-cutting/observability-query/scripts/query-logs.sh` accepts a LogQL string and returns either ndjson results on stdout or a 4-block error on stderr. Exit codes: 0 success, 1 stack-not-up, 2 syntax error, 3 no results, 4 pagination overflow.
- [ ] `.claude/skills/cross-cutting/observability-query/scripts/query-metrics.sh` mirrors query-logs.sh for PromQL, supporting both instant (`./query-metrics.sh 'up'`) and range (`./query-metrics.sh --range 5m 'rate(...)'`) modes.
- [ ] `E2EBase.java` reads `wms.e2e.observabilityNetwork` system property; when set, uses a named docker network; when unset, behaviour identical to today.
- [ ] `gateway-service/build.gradle` accepts `-Pobservability=on` Gradle property; when set, wraps the e2eTest task with up/down lifecycle hooks and injects the network name as a system property.
- [ ] `infra/observability/README.md` updated with the "Phase 2: Gradle e2eTest mode" section + `OBSERVE-QUERY-NN` quick-reference cross-link.
- [ ] No file under `libs/`, `apps/*/src/main/`, `projects/*/specs/`, or any contract directory modified. The change is confined to `.claude/skills/`, `infra/`, and the e2eTest source set / build.gradle of `gateway-service`.
- [ ] CI green (path-filter — `.claude/**` + `infra/**` + `projects/wms-platform/apps/gateway-service/**` matched; the e2eTest job runs without `-Pobservability=on` so the flag's absence path is exercised).

---

# Related Specs

- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § 2.4 D4 (DX target — skill + slash + `OBSERVE-QUERY-NN`), § 2.5 D5 (Phase 2 deliverable)
- `tasks/done/TASK-MONO-065-observability-stack-scaffolding.md` § Implementation Notes (Phase 1 measurement + 3 gotchas — Phase 2 builds on this)
- `platform/lint-remediation-message-standard.md` (4-block format)
- `.claude/skills/cross-cutting/observability-setup/SKILL.md` (existing sibling skill for setup; this skill is the query counterpart)
- `infra/observability/README.md` (operator workflow — Phase 2 section appended here)

# Related Skills

- This task creates `.claude/skills/cross-cutting/observability-query/` (new).
- Existing `.claude/skills/cross-cutting/observability-setup/` complements it (setup-side).

---

# Related Contracts

None — agent harness configuration + e2eTest build wiring. No HTTP / event contract surface.

---

# Target Service

`gateway-service` (e2eTest source set + build.gradle). The Phase 2 verification path runs against gateway-master live-pair specifically; other services join in Phase 3.

Indirect: every wms service whose container ends up on the named network gets its stdout / metrics scraped by Vector. No code change in any service.

---

# Architecture

**Skill execution model**: agent (in a Claude / Codex session) issues `/observe logs '<query>'` or `/observe metrics '<query>'`. The skill body's invocation surface translates the slash command into a `Bash` tool call that invokes `query-logs.sh` / `query-metrics.sh` with the verbatim query argument. The script sources `.observability/ports.env`, builds the HTTP request, and pipes the response.

Failures route through the 4-block remediation grammar — `OBSERVE-QUERY-NN` IDs distinguish 5 failure modes, each with the same shape as the existing `OBSERVE-SCAFFOLD-NN` from `up.sh` / `down.sh` and the `HARDSTOP-NN` from `hardstop-detect.ps1`. The agent on the next turn reads the structured error and follows the remediation text — the same loop closure that gap A delivered for Hard Stops.

**Gradle integration model**: the `-Pobservability=on` flag is a one-way switch. When absent, the e2eTest task is byte-identical to today. When present, the task takes ownership of: (1) creating a named docker network, (2) running `up.sh --network <name>` before the test JVM starts, (3) injecting `wms.e2e.observabilityNetwork=<name>` as a system property so `E2EBase` uses Testcontainers' `createNetworkCmdModifier` to reuse the same network, (4) running `down.sh` + removing the network in `doLast`. Failure in any step does not silently corrupt — `up.sh` already emits `OBSERVE-SCAFFOLD-NN` errors, Gradle propagates the non-zero exit, and the e2eTest task fails fast.

**Testcontainers `createNetworkCmdModifier` behaviour**: when Testcontainers receives an explicit name for a `Network`, it checks for an existing docker network of that name before creating one. If the named network exists (Gradle's `doFirst` created it), Testcontainers attaches to it; if not, Testcontainers creates it. Either way, all subsequent `container.withNetwork(network)` calls join the same docker network, and the observability stack's Vector container (joined to the same name via `external: true` in compose) sees their stdout.

---

# Implementation Notes

## Why named network instead of ComposeContainer

Testcontainers' `ComposeContainer` would let us spin up the observability stack as part of the e2eTest JVM lifecycle (`@Container` annotation). Two reasons against:

1. **Lifecycle entanglement.** ComposeContainer ties the observability stack to a single test JVM. Phase 1's standalone `up.sh` / `down.sh` lifecycle for manual bootRun mode would diverge — two different ways to bring the stack up means two surfaces to maintain.
2. **Docker-Java pathology** (memory `project_testcontainers_docker_desktop_blocker.md`). Testcontainers' docker-java client hits the Rancher Desktop cold-start `MalformedChunkCodingException` regression on every Test JVM start. ComposeContainer would multiply the cycle count.

Named network from a pre-running stack avoids both. Testcontainers' role is reduced to "attach to this existing network" — no new image pulls, no extra dockerd round-trips inside the test JVM.

## VictoriaLogs query URL shape

Phase 1 used the `/select/logsql/query` endpoint. The query string is `query=<urlencoded>&limit=<n>`. The response is newline-delimited JSON. The skill script uses `jq -s '.'` to slurp into an array for human-readable output, or `jq -c '.'` for compact ndjson when piping to another tool.

## PromQL instant vs range

VictoriaMetrics exposes both `/api/v1/query` (instant) and `/api/v1/query_range` (range). The skill script's `--range <duration>` flag triggers the range endpoint with `start = now - duration` and `end = now`, step = `15s` by default. Without `--range`, the script calls instant — matches typical interactive use of `up`, current heap usage, etc.

## D4 churn-clock interaction

ADR-MONO-003a § D1.3 (Harness gap series scope) applies. Touches:

- `.claude/skills/cross-cutting/observability-query/` (new directory) — additive, no relaxation
- `infra/observability/README.md` — additive section
- `projects/wms-platform/apps/gateway-service/src/e2eTest/java/com/wms/gateway/e2e/E2EBase.java` — modified, but project-internal (not under `libs/`)
- `projects/wms-platform/apps/gateway-service/build.gradle` — modified, project-internal

The shared-path touches (`.claude/skills/` + `infra/`) are cumulative authoring on the Harness gap series surface, same shape as MONO-062 / MONO-064 / MONO-065. Minimal last_churn impact.

## Commit shape

Single commit / single PR pattern. Conventional commit prefix:

```
feat(claude+infra)+task(mono-066): observability query skill + Gradle e2eTest integration (OpenAI Harness gap #3 Phase 2)
```

The closure chore (`ready` → `done`) lands in a separate small PR after this one merges.

---

# Edge Cases

- **`-Pobservability=on` invoked from CI.** The flag is off by default in CI workflows (`ci.yml` does not pass it). If a developer pushes a PR that activates it, the CI e2eTest job will attempt to create a network + run `up.sh`. Acceptable — CI runner is Linux with reliable Docker daemon, so the stack should come up fine; the only cost is added wall-clock time. Phase 3 will decide whether CI ever wants the stack on (footprint regression test specifically).
- **Two concurrent e2eTest runs in the same worktree.** Network name includes `${workTreeHash}` so the same worktree's two parallel runs collide. Acceptable first-iteration — Gradle does not parallelise e2eTest at task-level by default. If parallel test classes attempt to bring up two stacks, the second `up.sh` invocation hits an existing-project-name conflict and fails fast.
- **`query-logs.sh` invoked when the stack has been up for >24h.** VictoriaLogs retention is 1d (per `docker-compose.yml`). Older queries return empty results — same as the no-results path. The skill body documents the 1d retention boundary.
- **VictoriaLogs / VictoriaMetrics binary upgrade between runs.** Phase 1 pinned to specific image tags. A future Phase 3 footprint regression test compares against Phase 1's 26.88 MiB baseline; upgrades that exceed it surface in that test. This task does not touch the image tags.

---

# Failure Scenarios

- **Test JVM cannot attach to the named network** (race: `up.sh` created the compose project but the actual docker network isn't ready yet). Mitigation: `up.sh` already waits ≤ 30 s for all three containers to be healthy; by the time it returns, the network is fully provisioned. If race still occurs, Testcontainers retries the attach 3 times by default — surface as a normal test failure.
- **Skill script invoked outside a git worktree.** Same as Phase 1 — `git rev-parse --show-toplevel` fails, the script emits an `OBSERVE-QUERY-NN` variant (no specific code; the 01 "stack not up" error is the user-visible message). Documented in skill body.
- **Skill script invoked while a different worktree's stack is running.** The `.observability/ports.env` is per-worktree, so the script sources the local one and connects to the local stack. Cross-worktree pollution is impossible by D2's design.

---

# Test Requirements

- **Manual verification (operator)**: run `./gradlew :projects:wms-platform:apps:gateway-service:e2eTest -Pobservability=on` from a worktree with the wms bootRun stack NOT running. Expect: gateway-master e2e suite passes, observability stack spins up briefly, queries from the new skill return data after at least one e2e scenario runs, stack tears down on completion.
- **CI verification**: existing `e2e-tests` job in `.github/workflows/ci.yml` runs without `-Pobservability=on` (default path). Confirms zero regression for the non-observability case. CI does not run the observability path in Phase 2.
- **Skill script unit tests**: not added in this task — the scripts are thin wrappers around `curl` with single-process semantics; their failure modes match the 4-block remediation IDs and the test surface is hard to mock meaningfully. Phase 3 will add a "scripts-shellcheck + bats" lint job if shell-script test coverage becomes a recurring pain.

CI must remain 15/15 SUCCESS — the default e2eTest path is the regression guard.

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] CI green (15/15 SUCCESS).
- [ ] Closure chore PR (`ready` → `done`) opens after this PR merges.
- [ ] Memory `reference_openai_harness_engineering.md` gap #3 row annotated "Phase 2 DELIVERED" on closure chore merge (full closure deferred to Phase 3).

---

# Provenance

ADR-MONO-007 § 2.5 D5 Phase 2 gate met by TASK-MONO-065 (Phase 1) merge — PR #402 commit `dedc4b0e` (2026-05-13).

Memory `reference_openai_harness_engineering.md` § 우선순위 액션 후보 item #3 — gap #3, Phase 2 of the four-phase closure plan.

D4 OVERRIDE applies per ADR-MONO-003a § D1.3 — Harness gap series scope, user-acknowledged 2026-05-12.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (skill body + Java testcontainers integration + Gradle wiring are judgment-heavy; testcontainers `createNetworkCmdModifier` and named-network lifecycle is the main novel coupling).
