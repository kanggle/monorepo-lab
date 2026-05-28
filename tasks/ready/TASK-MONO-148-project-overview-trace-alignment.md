# Task ID

TASK-MONO-148

# Title

docs/project-overview.md reality-alignment — record ADR-MONO-007a (Trace Layer) + Phase 8 D4 observability / D5 isolation completion (MONO-142~147 trace series + PC-BE-006/ERP-BE-004)

# Status

ready

# Owner

monorepo (root tasks/ — docs/project-overview.md)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

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

- **depends on**: the ADR-018 D4/D5 work merged on main — ADR-MONO-007a (MONO-142), trace foundation/stack (MONO-143/144), trace gates + unified tree + per-leg attribution (MONO-145/146/147), and D5 isolation (PC-BE-006 console-bff + ERP-BE-004 erp). All DONE 2026-05-28.
- **origin**: docs reality-alignment (MONO-141 precedent — 6th in the recurring series, `project_refactor_sweep_status`). Verified gaps: (a) **ADR-MONO-007a is absent from the project-overview ADR table** (rows go 007 → 008; no 007a); (b) the **Phase 8 status row is stale** — it still reads "MVP COMPLETE 2026-05-26 / TASK-MONO-139/140 / 7/7 Playwright specs", predating the D4 observability federation (MONO-142~147) + D5 isolation (PC-BE-006/ERP-BE-004) completion 2026-05-28 and the 7→8 spec count.
- **prerequisite for**: nothing (documentation hygiene).
- **spec-first**: spec PR (this task md + INDEX ready entry, no doc edit) → impl PR (the project-overview.md edit, ready→in-progress→review) → close chore PR (review→done + INDEX).
- **model**: 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (docs reality-alignment, single-file, low complexity) — dispatcher-direct acceptable / 리뷰=Opus 4.7.

---

# Goal

Bring `docs/project-overview.md` back into alignment with `origin/main` reality after the 2026-05-28 ADR-018 D4/D5 completion burst: add the missing ADR-MONO-007a (Trace Layer) row to the ADR table, and update the Phase 8 status to reflect that observability federation (D4) and multi-tenant isolation regression (D5) are COMPLETE — not just the MONO-139/140 cross-product e2e MVP. Documentation-only; no code, no behavior.

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry. No doc edit.

**Impl PR** — `docs/project-overview.md` only:

- **ADR table**: insert an `ADR-MONO-007a` row between `ADR-MONO-007` and `ADR-MONO-008` — "Trace Layer (VictoriaTraces + OTLP direct/Vector) — resolves ADR-018 D4 ↔ ADR-007 D1; console-web `@opentelemetry` origination + console-bff → producer W3C propagation."
- **Phase 8 status row** (the "8. federation hardening" milestone row): update to reflect D4 observability federation (MONO-142~147 trace series — VictoriaTraces stack + propagation gate + unified 62-span tree + per-leg `bff.domain`/`bff.route` attribution) and D5 multi-tenant isolation regression (PC-BE-006 console-bff + ERP-BE-004 erp) **COMPLETE 2026-05-28**; e2e spec count 7 → 8.
- **현재 단계 summary** (top of doc): optionally note D4/D5 종결 2026-05-28 if it sharpens the one-line status (keep additive, no decision restatement).

## Out of Scope

- **ADR files / specs / code / tasks lifecycle docs** — byte-unchanged. This is the portfolio-overview narrative only.
- **Restating ADR decisions** — the ADR table row is a one-line pointer (HARDSTOP-04 — do not paraphrase decisions).
- **Other stale spots** unrelated to the ADR-007a + Phase 8 D4/D5 gap (scope-limited; broader audits are separate).

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this task md + INDEX ready entry land in a spec PR with no doc edit.
- **AC-2 (ADR-007a recorded)**: the ADR table contains an `ADR-MONO-007a` row (correctly placed after 007, before 008) linking `adr/ADR-MONO-007a-trace-layer.md` with a one-line summary.
- **AC-3 (Phase 8 status current)**: the Phase 8 milestone row reflects D4 observability (MONO-142~147) + D5 isolation (PC-BE-006/ERP-BE-004) COMPLETE 2026-05-28 + 8 specs (no longer "7/7 / MONO-139/140 only").
- **AC-4 (docs-only, no decision drift)**: `git diff origin/main` touches only `docs/project-overview.md` (+ the task lifecycle files); no ADR/spec/code change; the ADR-007a row is a pointer, not a decision paraphrase (HARDSTOP-04).
- **AC-5 (links valid)**: `adr/ADR-MONO-007a-trace-layer.md` exists at the linked path (the row link resolves).

# Related Specs

- `docs/adr/ADR-MONO-007a-trace-layer.md` — the ADR being recorded.
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § 2 D4/D5 — the phase whose status is updated.
- `tasks/done/TASK-MONO-141-project-overview-md-2026-05-28-reality-alignment.md` — the precedent (additive, pointer-only reality-alignment pattern).

# Related Contracts

- None. Documentation only.

# Edge Cases

- **ADR-007a link path** — confirm the file name (`ADR-MONO-007a-trace-layer.md`) before linking (AC-5).
- **MONO-141 was same-day (2026-05-28)** — it aligned post-Phase-8 but predated MONO-142~147; this task layers the trace-series + D5 onto that, not a redo.
- **Pointer discipline** — the ADR row summary must not restate ADR-007a's D-decisions verbatim (HARDSTOP-04).

# Failure Scenarios

- **ADR/spec/code edited** → AC-4 fail; docs-overview narrative only.
- **ADR-007a row paraphrases decisions** → AC-4 fail (HARDSTOP-04); keep a one-line pointer.
- **Broken ADR-007a link** → AC-5 fail.

# Verification

1. Spec PR: this md + INDEX ready entry; no doc edit.
2. Impl PR: `docs/project-overview.md` only; `git diff origin/main` scope check; ADR-007a link resolves.
3. CI `changes` fast-lane (docs/task) GREEN.
4. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (docs reality-alignment) / 리뷰=Opus 4.7 (AC-2/3 reality match + AC-4 docs-only no-decision-drift + AC-5 link valid + BE-303 3-dim).
