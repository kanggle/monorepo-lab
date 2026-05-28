# Task ID

TASK-MONO-142

# Title

Author ADR-MONO-007a (Trace Layer — VictoriaTraces + OTLP-via-Vector) — resolve the ADR-MONO-007 D1 trace deferral + reconcile the ADR-MONO-018 D4 VictoriaTraces false-premise (unblock TASK-MONO-143 observability federation impl)

# Status

review

# Owner

monorepo (root tasks/, doc-only — docs/adr/)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- adr

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

- **depends on**: nothing. The gate is satisfied on `origin/main`:
  - ADR-MONO-007 (ACCEPTED 2026-05-12) § 2.1 D1 deferred the trace layer to ADR-MONO-007a + § 6 item 4 gated it on "a saga-spanning trace signal."
  - ADR-MONO-018 (ACCEPTED 2026-05-26) Phase 8 D4 cross-product trace-tree demand **is** that gate signal.
- **origin**: surfaced 2026-05-28 while scoping the ADR-MONO-018 D4 execution task. The cross-product e2e trace-tree (console-web → console-bff → 5 producers, assembled in VictoriaTraces) cannot be implemented because the trace backend does not exist — ADR-018 D4 presumed "VictoriaTraces per the ADR-006 topology" but ADR-007 (the real observability ADR; "ADR-006" is the known broken reference) **deferred** VictoriaTraces. = ADR-018 ↔ ADR-007 conflict (HARDSTOP-09). Reported; user selected Option A ("ADR-MONO-007a 선작성, 정석 — trace layer ACCEPTED").
- **prerequisite for**: **TASK-MONO-143** (ADR-MONO-018 D4 observability federation impl — VictoriaTraces deploy + Vector OTLP source/sink + console-web `@opentelemetry` + federation e2e trace-tree assertion). TASK-MONO-143 may not be authored to `ready/` until this ADR is ACCEPTED on main.
- **spec-first**: spec PR (this task md + root INDEX entry) → impl PR (new `ADR-MONO-007a-trace-layer.md` + 2 additive reconcile notes in ADR-007 + ADR-018, lifecycle ready→review) → close chore PR.
- **PROPOSED+ACCEPTED same PR** (meta-policy follow-up ADR pattern, **per parent ADR-MONO-007 precedent** which was itself PROPOSED+ACCEPTED same PR as a stack/topology meta-policy "before any compose file"): ADR-007a pins the trace backend + ingestion + origination — a stack-choice meta-policy structurally identical to ADR-007, not a platform-console phase-gate (so NOT the ADR-014/015/017/018 2-PR staged-child pattern). ACCEPTED basis = user-explicit Option A selection 2026-05-28 + ADR-007 meta-policy precedent.

---

# Goal

Resolve the ADR-MONO-007 trace deferral by authoring **ADR-MONO-007a** (ACCEPTED) so the ADR-MONO-018 D4 observability federation impl (TASK-MONO-143) has a dependency-correct base. The ADR pins three irreversible-once-set choices (parent ADR-007 § 1.2 rationale):

1. **Trace backend** = VictoriaTraces (VM-family homogeneity with the deployed VictoriaLogs + VictoriaMetrics; ~50 MB; OTLP + Jaeger ingest).
2. **Ingestion topology** = producer / console-web OTLP → **Vector** OTLP source (binds the `:4318` producers already export to — closes the "no listener" gap) → VictoriaTraces sink. Preserves ADR-007 D1 "Vector is the single spine."
3. **Trace origination** = console-web `@opentelemetry/sdk-node` to start the root SSR span + inject W3C `traceparent` into the outbound console-bff fetch (the missing tree root; console-bff + producers already speak W3C trace context).

Plus reconcile the ADR-018 D4 false premise (VictoriaTraces was deferred, not deployed) via an **additive** note to ADR-018 — D1-D8 bodies byte-unchanged (HARDSTOP-04).

# Decision authority (why this ADR, why ACCEPTED in one PR, why root tasks/)

- **Why a trace ADR (not just impl)**: deploying a trace backend + choosing ingestion topology + console-web instrumentation are architecture decisions ADR-007 explicitly reserved for ADR-007a. Authoring TASK-MONO-143 impl without this would silently bake the architecture (HARDSTOP-09).
- **Why PROPOSED+ACCEPTED same PR**: ADR-007a is a stack/topology meta-policy structurally identical to its parent ADR-007 (the deferred 3rd component of the SAME stack decision), and ADR-007 itself used PROPOSED+ACCEPTED same PR with the "policy ADR before any compose file" rationale (§ 1.2). User authorized Option A ("trace layer ACCEPTED") 2026-05-28. This is NOT a platform-console phase-gate (ADR-014/015/017/018 2-PR staged-child pattern does not apply).
- **Why root `tasks/`**: ADR authoring + `docs/adr/` + (downstream) `infra/observability/` are monorepo-level shared paths (CLAUDE.md § Repository Layout). Root task lifecycle.
- **Why additive-only to ADR-007 + ADR-018**: HARDSTOP-04 ADR-immutability — ACCEPTED ADR decision bodies are byte-unchanged; only additive notes (the BE-302/305/MONO-128 reality-alignment discipline + ADR-018's own additive-note-to-ADR-013 precedent).

---

# Scope

## In Scope

**Spec PR**:

- `tasks/ready/TASK-MONO-142-adr-mono-007a-trace-layer.md` — this task md.
- `tasks/INDEX.md` — ready entry.

**Impl PR (doc-only)**:

- **NEW** `docs/adr/ADR-MONO-007a-trace-layer.md` — the trace-layer ADR. Sections: header (Status ACCEPTED / Date / History PROPOSED→ACCEPTED same PR / Decision driver / Supersedes / Amends / Reconciles / Related) + § 1 Context + § 2 Decision (D1 backend / D2 ingestion / D3 origination / D4 topology+lifecycle inherit / D5 ADR-018 reconcile / D6 `/observe trace` stub→full / D7 phasing) + § 3 Alternatives + § 4 Consequences + § 5 Verification (belongs to MONO-143) + § 6 Outstanding follow-ups + § 7 Provenance.
- **ADDITIVE** `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` — § 2.1 D1 trace-deferral RESOLVED note (blockquote, D1 choice byte-unchanged) + § 6 item 4 FILED+ACCEPTED status.
- **ADDITIVE** `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` — § header reconcile note (blockquote after Related:) clarifying D4's VictoriaTraces false premise is now satisfied by ADR-007a; D1-D8 bodies byte-unchanged.
- `tasks/INDEX.md` — ready→review move.

## Out of Scope

- **Any implementation** — no `infra/observability/docker-compose.yml` / `vector.toml` edit, no console-web `@opentelemetry` wiring, no e2e trace-tree assertion, no `/observe trace` skill body. All of that = TASK-MONO-143 (gated on this ADR ACCEPTED).
- **ADR-007 / ADR-018 D-decision body edits** — additive notes only (HARDSTOP-04). The Vector+VictoriaLogs+VictoriaMetrics D1 choice + the ADR-018 D1-D8 bodies are byte-unchanged.
- **ADR-MONO-018 D5 (multi-tenant isolation regression cohort)** — independent Phase 8 axis, no trace dependency, separate task.
- **Code, tests, specs/, projects/** — byte-unchanged.
- **VictoriaTraces production persistence** — ephemeral-only per inherited ADR-007 D2; persistence = future ADR if Operations maturity demands.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR = exactly 2 files (this task md + root INDEX entry). No `docs/adr/` edit in spec PR.
- **AC-2 (impl PR doc-only, scope-locked)**: impl PR `git diff --stat origin/main` = exactly 3 doc files (`ADR-MONO-007a` new + `ADR-MONO-007` additive + `ADR-MONO-018` additive) + lifecycle (task md ready→review + INDEX). No code, no test, no `infra/`, no `projects/`.
- **AC-3 (ADR-007/018 additive-only, HARDSTOP-04)**: `git diff origin/main -- docs/adr/ADR-MONO-007-*.md docs/adr/ADR-MONO-018-*.md` shows **only insertions** (additive blockquote / strikethrough-annotation); no D-decision body line deleted/rewritten. ADR-007 D1 stack choice + ADR-018 D1-D8 bodies byte-unchanged.
- **AC-4 (no impl)**: `git diff --stat origin/main -- infra/ projects/ apps/ libs/ tests/` = **empty**.
- **AC-5 (ADR-007a completeness)**: ADR-MONO-007a has all 7 sections + Status ACCEPTED + the 3 pinned decisions (D1 VictoriaTraces / D2 Vector OTLP / D3 console-web OTel) + § 6 names TASK-MONO-143 as the impl follow-up.
- **AC-6 (CI green)**: markdown fast-lane (`changes` pass; all Gradle/Integration jobs skipped). BE-303 3-dim verified at close chore.

# Related Specs

- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § 2.1 D1 + § 2.4 D4 + § 6 item 4 — the deferral this ADR resolves.
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D4 — the consumer + the false premise this ADR reconciles.
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` D7 — per-domain fan-out attribution (trace span attribution aligns).
- `infra/observability/docker-compose.yml` + `infra/observability/vector.toml` — the stack the trace layer extends (read-reference; not edited here).

# Related Contracts

- None. ADR authoring only; no API / event contract change.

# Edge Cases

- **ADR-018 reviewer requests D4 body rewrite** (not additive note) → reject; HARDSTOP-04 immutability — D4's intent is preserved, only its dependency satisfied; additive note is the correct mechanism.
- **"ADR-006" broken-reference temptation** → ADR-007a names the broken reference honestly (per TASK-MONO-137 meta) but does NOT fix ADR-018's internal "ADR-006" links (that's a separate cross-ADR reference-hygiene task; out of scope — additive reconcile note only).
- **PROPOSED-only expectation** → if the user intended PROPOSED-only (not ACCEPTED), the ADR Status flips trivially; but Option A explicitly said "ACCEPTED" + ADR-007 meta-policy precedent justifies same-PR.
- **VictoriaTraces vs Tempo re-litigation** → § 3.1 records the rejection rationale (ADR-007 D1 already rejected Grafana stack on footprint+homogeneity); not re-opened absent new evidence.

# Failure Scenarios

- **Impl PR edits `infra/` or `infra/observability/`** → AC-4 fail. **Reject** — stack edit is TASK-MONO-143, gated on this ADR.
- **Impl PR deletes/rewrites an ADR-007 D1 or ADR-018 D-decision line** → AC-3 fail (HARDSTOP-04). **Reject** — additive only.
- **Impl PR touches console-web or any code** → AC-4 fail. **Reject** — no impl in the ADR task.
- **ADR-007a omits the TASK-MONO-143 follow-up pointer** → AC-5 fail; the impl gate would be undocumented.

# Verification

1. Spec PR diff: exactly 2 files (task md + INDEX). `git diff --stat origin/main -- docs/adr/` empty in spec PR.
2. Impl PR diff: 3 doc files + lifecycle. `git diff --stat origin/main -- infra/ projects/ apps/ libs/ tests/` empty.
3. `git diff origin/main -- docs/adr/ADR-MONO-007-*.md docs/adr/ADR-MONO-018-*.md` = insertions only (additive verification).
4. ADR-007a § 2 has D1-D7; Status ACCEPTED; § 6 names TASK-MONO-143.
5. Markdown fast-lane CI green; BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현=Opus 4.7 (ADR drafting + trace-backend/topology judgment + cross-ADR additive reconcile precision — doc-only governance, dispatcher-direct per ADR-007/137/138 sibling precedent) / 리뷰=Opus 4.7 (HARDSTOP-04 additive-only 객관 검증 + AC-2/3/4 scope-lock + BE-303 3-dim).
