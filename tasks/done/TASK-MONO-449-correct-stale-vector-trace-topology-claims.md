# TASK-MONO-449 — Correct the two artifacts still claiming traces flow through Vector

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (two comment/doc corrections, plus one URL-construction question that needs an actual answer)

> Follow-up to [`TASK-MONO-448`](../done/TASK-MONO-448-adr-record-corrections-025-007a-043.md), which added the
> as-built deviation note to `ADR-MONO-007a` D2 but deliberately left these two consumers alone — one is a
> skill file, one is project code, and neither belonged in a monorepo-level ADR-record task. **448 fixed the
> record first so these can now be corrected against something true.**
>
> **Root-level task** because the change spans a shared path (`.claude/skills/`) and a project path; the shared
> half forces root per `CLAUDE.md` § Task Rules.

---

## Goal

`ADR-MONO-007a` D2 decided traces flow **producer → Vector → VictoriaTraces**. That is not what ships: Vector
0.45 has an `opentelemetry` **source** but no `opentelemetry` **sink**, so traces go **direct to
VictoriaTraces**, bypassing Vector. `ADR-MONO-007a` now carries an additive note recording this
(`TASK-MONO-448`), and `infra/observability/vector.toml` has always said it in place ("No VictoriaTraces sink
here"; "Vector stays the spine for logs + metrics").

**Two artifacts still assert the decided-but-not-shipped topology.** Both verified still stale against `main`
at `7de3a637b`:

1. **`projects/platform-console/apps/console-web/src/otel-node.ts:9`**
   > `* - Exporter: OTLP/HTTP to the Vector OTLP source (ADR-007a D2), URL from`

   The comment cites ADR-007a D2 for an endpoint the code no longer targets. The federation e2e compose sets
   `OTEL_EXPORTER_OTLP_ENDPOINT` to `http://victoriatraces:10428/insert/opentelemetry` — VictoriaTraces
   directly.

2. **`.claude/skills/cross-cutting/observability-query/SKILL.md:126`**
   > "…ingests OTLP via Vector (producers + console-bff + console-web export to the Vector `:4318` OTLP
   > source, which forwards to VictoriaTraces)."

   This is the sentence an agent reads when asked to debug a missing trace. It sends them to inspect a Vector
   pipeline that does not carry traces.

**Why this matters more than tidiness:** a skill file is *operational instruction*, not narrative. An agent
following `/observe trace` guidance will look for traces in the wrong hop and conclude the trace layer is
broken. That is a false-negative generator, and it is the same class of defect as the alert rules watching a
metric nobody emits (`TASK-BE-532`) — a plausible-looking artifact that cannot do its job.

### A real question this task must answer, not assume

`otel-node.ts:28-30` builds the exporter URL as `` `${endpoint}/v1/traces` ``. With the endpoint set to
`http://victoriatraces:10428/insert/opentelemetry`, that yields
`http://victoriatraces:10428/insert/opentelemetry/v1/traces`. **Is that VictoriaTraces' correct OTLP ingest
path?** The comment being wrong does not tell us whether the URL is right — the code may be working, or may be
silently 404-ing with traces quietly lost. Do not assume either; see AC-3.

## Scope

**In scope:**

1. `.claude/skills/cross-cutting/observability-query/SKILL.md` — correct the ingestion-topology sentence so it
   describes the shipped path, and point at the ADR's deviation note for the reason.
2. `projects/platform-console/apps/console-web/src/otel-node.ts` — correct the exporter comment. Keep the
   ADR-007a citation but point it at the deviation note rather than at the superseded D2 diagram.
3. Answer the URL-construction question (AC-3) and act on the answer.
4. Grep for **other** artifacts making the same claim — this task found two by reading one ADR's blast radius,
   which is not a census (AC-1).

**Out of scope:**

- `docs/adr/ADR-MONO-007a-trace-layer.md` — already corrected by `TASK-MONO-448`. Do not re-edit.
- `infra/observability/vector.toml` — already accurate.
- **Changing the actual topology.** Re-pointing producers back through Vector is blocked on a Vector release
  shipping an `opentelemetry` sink; that is the ADR's recorded re-open condition, not this task.
- The logs/metrics pipelines, which genuinely do flow through Vector and whose descriptions are correct.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Re-verify both artifacts still carry the stale claim, and that
  the shipped topology is still direct-to-VictoriaTraces (check `infra/observability/vector.toml` and the
  federation e2e compose's `OTEL_EXPORTER_OTLP_ENDPOINT`). If Vector has since gained an OTLP sink and the
  topology was re-pointed, **STOP** — then the ADR's re-open condition fired and this task is obsolete.
- **AC-1 (census, not spot-fix)** — Grep the repo for other statements of the Vector-mediated *trace* path
  before editing: at minimum `.claude/skills/**`, `docs/**`, `projects/**/*.ts`, `projects/**/*.md`,
  `infra/**`, and any `README`. Report the full hit list and correct all of them, or say explicitly why one is
  left. Sanity-check the search against `otel-node.ts:9`, a known-positive, so an empty result is proven to
  mean absence. **Two known artifacts is the starting point, not the population.**
- **AC-2** — Each corrected artifact describes the *shipped* path and names the *reason* (Vector 0.45 has no
  `opentelemetry` sink), with a pointer to `ADR-MONO-007a`'s deviation note. A correction that says only "goes
  direct" invites someone to "fix" it back to the ADR's diagram.
- **AC-3 (the substantive one)** — Establish whether
  `http://victoriatraces:10428/insert/opentelemetry/v1/traces` is a valid VictoriaTraces OTLP ingest path.
  **Answer it by observation, not by reading docs alone**: bring up the stack, emit a console-web trace, and
  confirm it is queryable — or confirm the request 404s. Report which. If traces are being lost, that is a
  *defect*, not a comment fix: file it separately and say so in the PR body rather than widening this task.
- **AC-4** — `/observe trace` guidance in the skill is executable end-to-end as written after the edit. A
  skill file is operational instruction; correctness of prose is necessary but not sufficient.
- **AC-5** — No behaviour change. `otel-node.ts` keeps its no-endpoint no-op (ADR-007a D4) and its
  `traceparent` propagation exactly as-is; only the comment moves.

## Related Specs

- `docs/adr/ADR-MONO-007a-trace-layer.md` § 2.2 D2 + its as-built deviation note (the authority)
- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § D1 (the "Vector is the spine" premise,
  which still holds for logs + metrics)
- `infra/observability/vector.toml` (already-accurate in-place record of the same deviation)

## Related Contracts

- None. No API or event contract is touched.

## Edge Cases

- **`.claude/skills/` edits have needed explicit per-action authorization in this repo.** `hooks/` and
  `settings.json` are hard-blocked; `skills/` has passed with per-action approval. Attempt once and hand the
  patch over only on an actual block — do not pre-emptively assume one.
- **The skill file may be read by an agent mid-correction.** Its wrongness is currently *load-bearing* for
  anyone debugging traces today, which argues for correcting the skill first rather than last.
- **console-bff and the Java producers** may carry equivalent comments; AC-1's census exists to catch them.
  The audit that spawned this task read one ADR's consumers, not the whole fleet.
- **A "fix" that re-points the endpoint back to Vector would break tracing entirely** — Vector cannot sink
  OTLP in the pinned version. AC-2's requirement to state the reason is what prevents that.

## Failure Scenarios

- **F1 — correcting the two known artifacts and stopping.** The pair was found by reading one ADR's blast
  radius, so it is a starting point, not a census. Guarded by AC-1.
- **F2 — treating AC-3 as a documentation question.** If the ingest URL is wrong, traces are being dropped
  silently right now and the comment fix would paper over a live defect. Guarded by AC-3's
  observe-don't-assume requirement.
- **F3 — an empty grep read as absence.** Guarded by AC-1's mandatory sanity-check against a known-positive.
- **F4 — "aligning" the code to the ADR's D2 diagram.** The ADR's decided state is not the shipped state and
  cannot be until Vector ships an OTLP sink; someone reading D2 without its deviation note could reasonably
  make exactly this mistake. Guarded by AC-2 and by § Scope's out-of-scope entry.
