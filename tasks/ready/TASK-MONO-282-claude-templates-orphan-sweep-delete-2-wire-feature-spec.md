# TASK-MONO-282 — .claude/templates/ orphan sweep: delete completion-report + use-case templates, wire feature-spec into spec-change workflow

Status: ready

## Goal

Resolve the three remaining unreferenced `.claude/templates/` files surfaced by the 2026-06-16 broad template audit (the follow-up MONO-281 deferred). The audit found each has **zero functional references**, but they are **not the same kind of problem** — disposition differs per file:

1. **`completion-report-template.md`** — genuinely dead: zero references, zero artifacts following it anywhere in the repo, and no completion-report process exists (the repo records completion via `tasks/INDEX.md` done-list one-liners + `tasks/done/` task files). → **delete**.
2. **`use-case-template.md`** — stale/divergent: the 12 real use-case specs use a different structure (multiple `UC-N` per file with Korean section headers — 액터 / 사전조건 / 정상 흐름 / 대안 흐름 / 예외 흐름 / 관련 규칙) than the template's single-use-case English layout. Reality left the template behind; an unwired, non-matching template is misleading. → **delete**.
3. **`feature-spec-template.md`** — canonical but unwired: the 25 real feature specs follow its structure **exactly** (Purpose → Related Services → User Flows → Business Rules → Related Contracts → Related Events). It is the de-facto standard, simply not linked from any workflow — unlike its sibling `service-architecture-template.md`, which `.claude/workflows/service-bootstrap.md` references. → **keep + wire** into the spec-authoring workflow so it is discoverable.

This is doc/template-only and behavior-preserving for the spec types themselves (use-case and feature specs continue to exist and be authored; only the dead/stale template files are removed and the canonical one is linked).

## Scope

In one atomic PR (monorepo-level — `.claude/templates/` + `.claude/workflows/`, neither of which is classifier-hard-blocked; only `.claude/hooks|agents|commands` are):

1. **Delete** `.claude/templates/completion-report-template.md` (re-confirm zero references first).
2. **Delete** `.claude/templates/use-case-template.md` (re-confirm zero functional references first).
3. **Wire** `feature-spec-template.md`: add a reference in `.claude/workflows/spec-change.md` Step 3 ("Modify Specs") — e.g. "When creating a new `specs/features/*.md`, follow `.claude/templates/feature-spec-template.md`" — mirroring how `service-bootstrap.md` references `service-architecture-template.md`. Do not reference the deleted use-case template.

Out of scope: `adr-template.md` and `service-architecture-template.md` (both actively referenced — untouched). `.claude/commands/refactor-spec.md` is a natural secondary wiring point but is classifier-hard-blocked (commands) — if a second reference there is wanted, hand that one-line patch to the user; it is NOT required for this task. No change to any actual spec under `projects/*/specs/`.

## Acceptance Criteria

- `.claude/templates/completion-report-template.md` and `.claude/templates/use-case-template.md` no longer exist; repo-wide grep for each returns zero references (other than historical `tasks/done/` records).
- `.claude/templates/feature-spec-template.md` still exists and is now referenced by `.claude/workflows/spec-change.md` (≥ 1 functional reference); the reference points to the correct path and resolves.
- `.claude/templates/` now contains exactly three files: `adr-template.md`, `service-architecture-template.md`, `feature-spec-template.md` — all three functionally referenced (no orphan remains).
- No actual spec under `projects/*/specs/features|use-cases/` is modified; the use-case and feature spec *types* are untouched.
- No new project-specific content in shared paths (HARDSTOP-03 clean).

## Related Specs

- `.claude/workflows/spec-change.md` (the wiring target — Step 3).
- `.claude/workflows/service-bootstrap.md` (the mirror pattern: how a `.claude/templates/*` file is referenced from a workflow).
- `CLAUDE.md` § Source-of-Truth Priority (`.claude/skills/` and the broader `.claude/` config are shared) + § "Shared vs project boundary".
- Real spec exemplars confirming disposition: `projects/ecommerce-microservices-platform/specs/features/authentication.md` (follows feature-spec-template), `.../specs/use-cases/cart-and-order.md` (diverges from use-case-template).

## Related Contracts

- None (doc/template-only).

## Edge Cases

- Re-confirm at impl time that no new reference to the two deletion targets appeared since the audit (a workflow/agent could have started using one).
- The wiring must reference only `feature-spec-template.md` — referencing the deleted use-case template would create a dangling link.
- `.claude/workflows/spec-change.md` Step 2 already mentions `specs/use-cases/` as a spec *type* in its impact-analysis list — that is the spec type, not the template, and must stay (only the template file is deleted).

## Failure Scenarios

- **Deleting a now-referenced file** — mitigated by the re-confirm-zero-references AC.
- **Dangling wire** — referencing a deleted template from the workflow. Mitigated by the "reference only feature-spec" scope + the resolves-correctly AC.
- **Scope creep into real specs** — rewriting actual feature/use-case specs. Out of scope; this task only touches `.claude/templates/` + the one workflow line.
- **`.claude/` commit gating** — `.claude/templates/` and `.claude/workflows/` are NOT hard-blocked (unlike hooks/agents/commands); the edit/commit should pass like `.claude/config/`. If unexpectedly gated, stage everything and hand the user the `git commit` per `env_classifier_claude_self_mod_block`.

## Notes

The fifth and final item in the 2026-06-16 same-day series (memory optimization → MONO-278 CLAUDE.md catalog trim → MONO-279 skills ADR-032/iam fixes → MONO-281 task-template consolidation → this `.claude/templates/` orphan sweep). After this, `.claude/templates/` holds only actively-referenced templates. The key audit insight: "unreferenced" ≠ "delete" — `feature-spec-template` is the de-facto standard that 25 specs follow and just lacked a link, whereas `completion-report` (no process) and `use-case` (reality diverged) are genuinely dead/stale. MONO-280 was in flight in a concurrent session, so the prior task took 281 and this is 282.
