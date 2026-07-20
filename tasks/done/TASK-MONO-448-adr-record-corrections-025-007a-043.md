# TASK-MONO-448 — Correct three ADR decision records the drift audit found asserting untrue things

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (one is a security-semantics correction; the other two are deviation notes whose wording must avoid both over- and under-claiming)

> **Authorised by explicit user direction, 2026-07-20:** *"025 는 (가)로, 007a·043 은 편차 노트"* — for
> ADR-MONO-025 take option (가) (correct the ADR text to net-zero; do **not** tighten the code to fail-closed),
> and record deviation notes on ADR-MONO-007a and ADR-MONO-043.
>
> These are edits to the decision clauses of **ACCEPTED** ADRs, which an agent may not initiate on its own.
> The direction above names each ADR and its disposition. Note this is **not** an ADR ACCEPT — no status
> transitions; all three ADRs stay ACCEPTED.

---

## Goal

The 70-ADR drift audit (2026-07-20) produced 11 findings. Eight became `TASK-BE-532`…`537` and
`TASK-MONO-446`/`447`. The remaining three were deferred as "owner's call" because discharging them means
editing ACCEPTED decision clauses. This task discharges those three.

### ① ADR-MONO-025 D1 / D2-2b — the ADR contradicted itself, its own contract, and every implementation

D1 (`:41`) and D2-2b (`:57`) declare an empty/absent `data_scope` claim **fail-closed (deny)**. But § 3.1 of
the same ADR says `["*"]`/absent ⟺ unrestricted, and the contract this ADR *produced* —
`platform/abac-data-scope.md` § 2 — carries the heading **"Why empty = net-zero (not fail-closed)"** with the
reasoning spelled out: a base `authorization_code` token and a `client_credentials` machine token carry no
data-scope claim at all and legitimately reach a domain, so fail-closing on absence would deny them. Every
consumer implements net-zero (`AbacDataScope`, wms `DataScopeSupport`, erp `ReadAuthorizationGate`).

**Direction (가): the code is right; the ADR text was wrong.** Correct in place with dated markers.

### ② ADR-MONO-007a D2 — the shipped topology is the one § 3.2 explicitly rejected

D2 decided traces flow **producer → Vector → VictoriaTraces**, and § 3.2 rejected direct-to-backend as
"defeats ADR-007 D1 'Vector is the spine'". What ships is the rejected path: Vector 0.45 has an
`opentelemetry` **source** but no `opentelemetry` **sink**, so the decided diagram is not expressible in the
pinned version. `infra/observability/vector.toml` records this in place; the federation e2e compose points
`OTEL_EXPORTER_OTLP_ENDPOINT` straight at VictoriaTraces.

**Direction: deviation note.** The decision is not reversed — logs and metrics still flow through Vector and
the single-spine rationale still holds for them. What is suspended is D2's *trace leg*, blocked on a Vector
release with an OTLP sink.

### ③ ADR-MONO-043 § 7 — the closeout record asserts an adoption that never happened

§ 7 states *"P1b `libs/java-notification` provides the channel SPI; domains wire their own adapters. No
further decision pending."* Measured: the library has **zero consumers**. `java-notification` appears in
exactly one `build.gradle` — its own; no `projects/**` file imports `com.example.platform.notification`. The
two domains named as conformant (erp, fan) each implement a local `NotificationChannelPort` instead.

**Direction: deviation note.** Record the fact; explicitly do **not** decide whether to wire the library or
retire it.

## Scope

**In scope (all under `docs/adr/`):**

1. `ADR-MONO-025-abac-data-scope-generalization.md` — correct D1 (`:41`), D2-2b (`:57`) and the D4
   parenthetical (`:71`) from fail-closed to net-zero, each with a dated `[CORRECTED …]` marker naming this
   task and stating **why** the original was wrong, not merely that it changed.
2. `ADR-MONO-007a-trace-layer.md` — additive deviation note under D2, following the `ADR-MONO-007 § D1`
   additive-note precedent already in this repo.
3. `ADR-MONO-043-notification-architecture-unification.md` — correct the § 7 D4 closeout entry, including the
   measurement and its method sanity-check.

**Out of scope:**

- **Any code change.** In particular, tightening the data-scope filter to fail-closed — that was option (나),
  explicitly not chosen. erp's existing domain-local fail-closed hardening for department-targeted writes
  stays exactly as it is.
- **`platform/abac-data-scope.md`** — already correct; it is the reference the ADR is corrected *to*.
- **The two stale ADR-007a consumers**: `.claude/skills/cross-cutting/observability-query/SKILL.md` and
  `projects/platform-console/apps/console-web/src/otel-node.ts`, both still describing the Vector-mediated
  trace path. One is a skill file, one is project code — neither belongs in a monorepo-level ADR-record task.
  **Follow-up required**; this task fixes the record first so they can be corrected against something true.
- **Deciding ADR-043's open question** (wire `libs/java-notification` vs retire it). The note states the fact
  and names the choice; making it is a fresh owner decision.
- The other eight audit findings — already filed.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Re-verify all three findings still hold before editing. If
  any has been corrected in the meantime, drop it and say so.
- **AC-1** — ADR-MONO-025 contains no surviving claim that the *empty/absent* case is fail-closed **by
  default**. Grep the file for `fail-clos` and confirm every remaining hit is either the corrected text
  explaining why the original was wrong, or the domain-local-hardening carve-out.
- **AC-2** — Every correction says **why** the original was wrong, not just that it changed. A dated marker
  with no reasoning reproduces the defect one layer up: the next reader cannot tell whether the decision moved
  or the record was fixed.
- **AC-3** — ADR-MONO-007a's note states plainly that § 3.2's rejection of direct-OTLP now describes the
  *target* state rather than the current one, and carries a re-open condition. A note that only says "we did
  something else" leaves the next design argument citing § 3.2 as though it were live.
- **AC-4** — ADR-MONO-043's note records the measurement **and its method sanity-check** (the `java-messaging`
  comparison proving the zero is real, not a broken pattern), and does not decide the open question.
- **AC-5** — No file outside `docs/adr/` and `tasks/` is modified.
- **AC-6** — `docs/adr/INDEX.md` rows for all three ADRs still match their headers; `Status` and `Date` are
  untouched (a correction is not a status transition), so `scripts/check-adr-index-drift.sh` still passes.

## Related Specs

- `platform/abac-data-scope.md` § 2 (the contract ADR-025 is corrected *to* — unchanged)
- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § D1 (the additive-note precedent)
- `docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md` § 5 M3 (the "share the leaf shape, keep the
  divergent engine" precedent ADR-043 § 7 already cites)

## Related Contracts

- None changed. `platform/abac-data-scope.md` is read as authority, not edited.

## Edge Cases

- **Correcting a record can look like reversing a decision.** ADR-025's semantics never changed — code and
  contract always said net-zero; only the ADR was wrong. The markers must say so explicitly.
- **ADR-007a's note must not read as a reversal either.** Vector remains the spine for logs and metrics; only
  the trace leg is suspended, and on a tool limitation rather than a design change.
- **Two stale ADR-007a consumers survive this task** and will keep asserting the Vector-mediated path until
  separately corrected. That is a recorded gap, not an oversight, and the note names both.
- **ADR-043's open question is load-bearing.** "Zero consumers" is a finding, not a verdict; a reader who
  treats the note as authorising deletion of `libs/java-notification` would be overreading it.

## Failure Scenarios

- **F1 — silently rewriting history.** Editing the clauses without dated markers leaves no trace the record
  ever said otherwise, making the audit that found this unreproducible. Guarded by AC-2.
- **F2 — over-claiming in the deviation notes.** Writing ADR-007a's note as "D2 reversed", or ADR-043's as
  "retire the library", substitutes an agent's inference for an owner's decision. Guarded by AC-3/AC-4.
- **F3 — scope creep into option (나).** Tightening the data-scope filter to fail-closed was explicitly not
  chosen; doing it "while here" is a breaking change for callers whose tokens carry no data-scope claim.
  Guarded by § Scope.
- **F4 — leaving the ADR-007a consumers uncorrected and unrecorded.** Fixing only the ADR moves the untrue
  sentence rather than removing it. Guarded by the explicit follow-up in § Scope.
