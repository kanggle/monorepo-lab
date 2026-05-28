# Task ID

TASK-MONO-143

# Title

Observability trace foundation (ADR-MONO-007a D1/D2/D3/D6) — VictoriaTraces backend + Vector OTLP source/sink + console-web `@opentelemetry` trace origination + `/observe trace` full impl. (Federation e2e 7-span trace-tree assertion = TASK-MONO-144 split, MONO-139→140 precedent.)

# Status

ready

# Owner

monorepo (root tasks/ — cross-product: infra/observability + console-web + tests/federation-hardening-e2e + .claude/skills)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy
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

- **depends on**: **ADR-MONO-007a ACCEPTED on main** (TASK-MONO-142, merged 2026-05-28 squash `a72ec4f2` impl / `d0834976` close). ADR-007a § 6 item 1 names this task as its impl follow-up. Also depends on the federation-hardening-e2e harness (TASK-MONO-139/140, `tests/federation-hardening-e2e/`).
- **origin**: ADR-MONO-018 (ACCEPTED 2026-05-26) Phase 8 D4 (observability federation) execution axis; ADR-MONO-007a D7 phasing step 1.
- **prerequisite for**: **TASK-MONO-144** (federation e2e 7-span trace-tree assertion — the ADR-018 D4 cross-product functional verification, workflow_dispatch-gated). MONO-143 lands the trace foundation (backend + ingestion + origination + skill); MONO-144 asserts the tree assembles. Mirrors the MONO-139 (harness) → MONO-140 (functional GREEN via dispatch iteration) split. The other open Phase 8 axis = ADR-018 D5 (multi-tenant isolation regression IT cohort, Opus) is independent.
- **spec-first**: spec PR (this task md + MONO-144 task md + INDEX) → impl PR (config + console-web code + skill) → close chore PR.
- **model**: Sonnet 4.6 (routine compose / Vector config / OTel SDK wiring per ADR-018 § D6 row 8 + ADR-007a § 6) with Opus dispatcher verify. Executed dispatcher-direct this session.
- **scope split rationale (honest, MONO-139→140 precedent)**: the federation e2e 7-span trace-tree assertion (ADR-018 D4's cross-product verification) fundamentally requires the federation-hardening-e2e Docker stack + `workflow_dispatch` multi-cycle iteration (MONO-140 = 5 cycles). It is split to TASK-MONO-144 so the verifiable trace foundation (console-web `@opentelemetry` build-verified; dev-stack config authored) lands without being blocked on a multi-cycle Docker effort. This is NOT scope reduction — it is the same one-decision-multiple-execution-tasks shape as ADR-018 D6 + the MONO-139/140 harness/verification split.

---

# Goal

Lay the **trace foundation** so a console dashboard fan-out (Operator Overview / Domain Health) can be traced end-to-end as one trace tree (`console-web SSR → console-bff aggregation → 5 producer spans` = 7 spans) assembling in **VictoriaTraces**. Implements ADR-MONO-007a's pinned decisions D1/D2/D3/D6. The cross-product **assertion** of that tree (ADR-018 D4) is split to TASK-MONO-144 (workflow_dispatch-gated).

1. **D1 backend** — add VictoriaTraces to the dev `infra/observability/` ephemeral stack.
2. **D2 ingestion** — Vector gains an `otlp` source binding `:4318` (closes the producers' "no listener" gap) + a VictoriaTraces sink.
3. **D3 origination** — console-web `@opentelemetry/sdk-node` (Next.js 15 `instrumentation.ts`) starts the root SSR span + auto-injects W3C `traceparent` into the outbound console-bff `fetch` (console-bff + producers already adopt it via their existing OTel auto-instrumentation).
4. **D6** — `/observe trace` skill full impl (query VictoriaTraces by `trace_id`, return span tree; `OBSERVE-QUERY-06/07`).

(The federation e2e stack VictoriaTraces wiring + the 7-span Playwright trace-tree assertion + `workflow_dispatch` functional GREEN = **TASK-MONO-144**.)

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry.

**Impl PR**:

- **`infra/observability/docker-compose.yml`** — add `victoriatraces` service (single binary, tmpfs, `--retentionPeriod=1d`, `127.0.0.1::10428` ephemeral port, healthcheck). Vector `depends_on` extended.
- **`infra/observability/vector.toml`** — add `[sources.otlp]` (binds `0.0.0.0:4318`, OTLP/HTTP) + `[sinks.victoriatraces]` (OTLP/HTTP to victoriatraces `/insert/opentelemetry/v1/traces`). `add_worktree_label` transform input extended where applicable (traces keep native resource attrs).
- **`infra/observability/README.md`** — VictoriaTraces port + `/observe trace` note (operator guide additive).
- **console-web `@opentelemetry` instrumentation**:
  - `package.json` — add `@opentelemetry/sdk-node`, `@opentelemetry/auto-instrumentations-node`, `@opentelemetry/exporter-trace-otlp-http`, `@opentelemetry/resources`, `@opentelemetry/semantic-conventions` (pinned versions).
  - `src/instrumentation.ts` (Next.js 15 instrumentation hook) — register the Node SDK with OTLP/HTTP exporter to `OTEL_EXPORTER_OTLP_ENDPOINT` (env, **no-op when unset** — production/`next dev` without the stack unaffected); service name `console-web`; fetch/undici auto-instrumentation so the outbound console-bff `fetch` carries `traceparent`.
  - `next.config` — enable `instrumentationHook` if not already (Next 15.0.3).
  - The 2 BFF-proxy routes (`operator-overview`, `domain-health`) need **no manual `traceparent` code** — auto-instrumentation injects it; `X-Request-Id` retained alongside.
- **`/observe trace` skill** — `.claude/skills/cross-cutting/observability-query/SKILL.md` body: `/observe trace <trace_id>` queries VictoriaTraces Jaeger-compat API, returns span tree; `OBSERVE-QUERY-06` (trace not found) / `07` (incomplete tree / broken span chain) 4-block + a `scripts/query-traces.sh`.
- **`tasks/INDEX.md`** ready→review.

## Out of Scope

- **Federation e2e trace-tree assertion (ADR-018 D4 cross-product verification)** → **TASK-MONO-144** (split). The `tests/federation-hardening-e2e/` stack VictoriaTraces+Vector wiring, the `observability-trace-tree.spec.ts` Playwright 7-span assertion, and the `workflow_dispatch` functional GREEN all live there — they require the federation Docker stack + multi-cycle dispatch iteration (MONO-140 precedent).
- **ADR-018 D5 (multi-tenant isolation regression IT cohort)** — independent axis, no trace dependency. Separate task (Opus).
- **Producer code change** — producers already export OTLP to `:4318` + have `micrometer-tracing-bridge-otel`; this task makes Vector listen there. No `apps/*/src` edit unless a producer is missing the OTLP endpoint env in the federation e2e compose (env-only, not code).
- **VictoriaTraces production persistence** — ephemeral-only (ADR-007a D4 inherited).
- **ADR amendment** — none. ADR-007a/018 are sources, byte-unchanged.
- **Cross-ADR "ADR-006" broken-reference hygiene** — separate task.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR = MONO-143 task md + MONO-144 task md + INDEX. Markdown fast-lane.
- **AC-2 (dev-stack trace layer config authored)**: `infra/observability/docker-compose.yml` has the `victoriatraces` service (image-tag flagged for registry confirmation, docker unavailable locally) + Vector `:4318` port; `vector.toml` has `[sources.otlp]` + `[sinks.victoriatraces]`. Runtime validity (`docker compose config` / `vector validate`) verified when Docker is available (CI / MONO-144 dispatch) — **honestly flagged as locally-unverified** (Rancher not running on the Windows host; CI Linux is authoritative per `project_testcontainers_docker_desktop_blocker`).
- **AC-3 (console-web builds + no-op when unset)** — **the verified core of this task**: `pnpm install` + `pnpm run build` + `pnpm run lint` succeed with the OTel deps + `instrumentation.ts` + `otel-node.ts`; with `OTEL_EXPORTER_OTLP_ENDPOINT` unset the SDK no-ops (structural `if (endpoint)` guard). **Verified locally 2026-05-28: pnpm install (6 OTel deps resolved) + build PASS + lint clean.**
- **AC-4 (`/observe trace` skill authored)**: SKILL.md gains the trace section + `OBSERVE-QUERY-06/07` + `scripts/query-traces.sh`; the "No trace queries" deferral note removed. (Functional query verified when VictoriaTraces runs — MONO-144.)
- **AC-5 (no producer code change)**: `git diff --stat origin/main -- 'projects/*/apps/*/src/**'` = only console-web `instrumentation.ts` + `otel-node.ts` (new console-web files); no Spring producer `src` edit.
- **AC-6 (no ADR amendment)**: `git diff --stat origin/main -- docs/adr/` = empty.
- **AC-7 (footprint re-measure)** → deferred to MONO-144 (needs the stack running with VictoriaTraces).

# Related Specs

- `docs/adr/ADR-MONO-007a-trace-layer.md` § 2 (D1-D7) — the pinned decisions this task implements.
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D4 — the trace-tree requirement.
- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § 2.2 D2 + § 2.4 D4 — inherited topology + `/observe` skill.
- `infra/observability/docker-compose.yml` + `vector.toml` + `README.md` — the dev stack extended.
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` — the e2e stack extended.
- `projects/platform-console/apps/console-web/src/app/api/console/dashboards/{operator-overview,domain-health}/route.ts` — the BFF proxies the SDK auto-instruments.

# Related Contracts

- None. Trace propagation is an observability concern; no API / event contract change. `console-integration-contract.md` byte-unchanged (traceparent is a transport header, not a contract field).

# Edge Cases

- **console-web SDK double-registration** — Next.js may invoke `instrumentation.ts` once per runtime; guard against double-register (SDK `start()` idempotency).
- **OTLP endpoint unset in production** — SDK must no-op (no exporter, no crash); the e2e/observability profile sets the env, prod does not.
- **Vector OTLP source port collision** — `:4318` binds inside the per-worktree network; dev stack uses the wms-platform-bootrun network (ADR-007 D2). Federation e2e uses its own compose network.
- **Trace not fully assembled (timing)** — the e2e poll must allow trace-export flush latency (producers batch-export); poll-with-timeout, not single-shot.
- **VictoriaTraces beta quirks** — if the Jaeger-compat query API shape differs, the spec adapts the query; documented in impl notes.

# Failure Scenarios

- **Impl PR edits producer `src/`** → AC-6 fail. Reject — producers are env-only in the e2e compose; OTLP export already on classpath.
- **Impl PR edits `docs/adr/`** → AC-7 fail. Reject — ADRs byte-unchanged.
- **console-web build fails / SDK crashes when endpoint unset** → AC-3 fail. The no-op-when-unset invariant is mandatory.
- **e2e trace-tree spec authored but never workflow_dispatch-verified** → AC-4 fail (MONO-139 premature-DONE lesson). Functional GREEN requires an explicit dispatch run logged.
- **`console-integration-contract.md` edited** → reject; traceparent is transport, not contract.

# Verification

1. Spec PR: MONO-143 + MONO-144 task mds + INDEX, markdown fast-lane.
2. Impl PR — console-web (VERIFIED locally): `pnpm install && pnpm run build && pnpm run lint` succeed; no-op-when-unset by the `if (endpoint)` guard.
3. Impl PR — dev-stack config (authored, runtime-unverified): docker-compose + vector.toml edits present; `docker compose config` / `vector validate` deferred to Docker-available env (honest flag).
4. AC-5 (no producer src) / AC-6 (no ADR) grep zero.
5. BE-303 3-dim at close chore.
6. Functional trace-tree GREEN (7-span tree in VictoriaTraces via workflow_dispatch) = **TASK-MONO-144**.

분석=Opus 4.7 / 구현=Opus 4.7 (dispatcher-direct; console-web verified, dev-stack authored) / 리뷰=Opus 4.7 (BE-303 3-dim + AC-2/3/4/5/6 + HARDSTOP-04 ADR byte-unchanged).
