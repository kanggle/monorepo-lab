# TASK-BE-534 — Mark ecommerce ADR-003 superseded by ADR-MONO-031, and correct ADR-004's stale trait claim

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Haiku 4.5 (two ADR header/prose edits + one index row)

> Split from an ADR↔implementation drift audit (2026-07-20). Documentation-truth only — no application code.

---

## Goal

Two of this project's ADRs assert things the repo has since contradicted, and neither carries a marker saying so.

**① ADR-003 (frontend architecture dual strategy) is STALE-ACCEPTED.** It is filed `Status: Accepted` and
describes a live dual strategy — `web-store` (FSD) and `admin-dashboard` (Layered by Feature). But
`docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md` (repo-root, **ACCEPTED 2026-06-13**)
decided to sunset admin-dashboard and consolidate the operator UI into platform-console — and that decision is
**already executed**: `specs/services/admin-dashboard/architecture.md:1-6` carries
*"**RETIRED (ADR-MONO-031 Phase 6, TASK-MONO-259).** Absorbed into platform-console… Do not implement new
features against this spec."*, and `apps/admin-dashboard/` contains no application source (only leftover
`.next/`, `.turbo/`, `node_modules/` build cache).

This project's own ADR convention (`docs/adr/README.md`) says a reversed ADR gets `Superseded by ADR-XXX`.
ADR-003 never got one, and `docs/adr/README.md:9` still lists it as Accepted.

Why this matters beyond tidiness: `CLAUDE.md`'s Source-of-Truth chain puts ADR-referenced decisions above
existing code. An agent that reads ADR-003 as live can start work against a spec whose own header says RETIRED —
the spec-level marker exists, the ADR-level one does not, so the chain is broken exactly one link up.

**② ADR-004 (taxonomy-based rule system) carries a now-false claim about this project.** Its Consequences
section states *"본 프로젝트는 `regulated`·`audit-heavy`·`multi-tenant`를 선언하지 않음"* — but
`PROJECT.md:4` declares `traits: [transactional, content-heavy, read-heavy, integration-heavy, multi-tenant]`.
`multi-tenant` was added by the later ADR-MONO-030 multivendor-marketplace decision. The taxonomy *mechanism*
is fine (it correctly loads `rules/traits/multi-tenant.md` now that the trait is declared — arguably proof the
mechanism works); it is the ADR's illustrative sentence about this project that went stale.

## Scope

**In scope:**

1. `docs/adr/ADR-003-frontend-architecture-dual-strategy.md` — flip Status per the local convention
   (`Superseded by ADR-MONO-031`), and add a short note stating what remains true (the `web-store` FSD half is
   still live) versus what was reversed (the admin-dashboard half), with the pointers to ADR-MONO-031,
   `TASK-MONO-259`, and the RETIRED spec header.
2. `docs/adr/README.md` — update the ADR-003 index row to match the new status.
3. `docs/adr/ADR-004-taxonomy-based-rule-system.md` — correct the trait claim to reflect the current
   `PROJECT.md` declaration, with a dated note naming ADR-MONO-030 as the reason it changed. Do not delete the
   original sentence's intent (the point it was illustrating — that undeclared traits are not loaded — remains
   valid); correct the example, not the principle.

**Out of scope:**

- Deleting the residual `apps/admin-dashboard/` build-cache directories (`.next/`, `.turbo/`, `node_modules/`).
  Real, but a separate hygiene chore — and removing directories under a live worktree tree has its own hazards.
- The broken relative reference links noted in passing during the audit (ADR-001 → `specs/platform/service-types/INDEX.md`,
  ADR-004 → `specs/rules/…`, which actually live at repo root). File a separate dead-link chore if wanted;
  bundling them here widens the diff past what a reviewer can check at a glance.
- **Any change to a monorepo-level ADR under repo-root `docs/adr/`.** ADR-MONO-031 is correct as written; this
  task only makes the project-level record agree with it.
- Re-opening the decision itself. This task **records** a supersession that an ACCEPTED, already-executed ADR
  performed; it does not make a new architecture decision.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's evidence. Re-verify at start:
  (a) `ADR-MONO-031` is still `ACCEPTED`; (b) `specs/services/admin-dashboard/architecture.md` still carries the
  RETIRED header; (c) `apps/admin-dashboard/` still has no application source (check for `package.json` and any
  `.ts`/`.tsx` under a source dir — sanity-check the glob against `apps/web-store/`, which does have source, so
  an empty result is proven to mean absence); (d) `PROJECT.md` still declares `multi-tenant`. If any premise has
  changed, **STOP and report** — the supersession claim would no longer be sound.
- **AC-1** — ADR-003's status follows the convention documented in this project's `docs/adr/README.md`. Use the
  exact marker form that README specifies; do not invent a new one.
- **AC-2** — ADR-003 states precisely which half was reversed. A blanket "superseded" would wrongly imply the
  `web-store` FSD decision is dead too — it is not.
- **AC-3** — `docs/adr/README.md`'s ADR-003 row matches the file. (The repo-root ADR index is guarded by
  `scripts/check-adr-index-drift.sh`; this project's README is not, so the row must be updated by hand.)
- **AC-4** — ADR-004's trait sentence matches `PROJECT.md` as re-read in AC-0, and names ADR-MONO-030 as the
  cause of the change. The principle the sentence illustrates is preserved, not deleted.
- **AC-5** — No file under repo-root `docs/adr/` or `platform/` is modified by this task.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md` (RETIRED header — the evidence)
- `projects/ecommerce-microservices-platform/PROJECT.md` (trait declaration — the evidence for ②)
- `docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md` (the superseding decision — read-only here)
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` (why `multi-tenant` was declared — read-only here)

## Related Contracts

- None. No API or event contract is touched.

## Edge Cases

- **Partial supersession** — ADR-003 decided two things and only one was reversed. The convention's vocabulary
  may not have a "partially superseded" state; if it does not, say so in prose within the ADR rather than
  bending the status vocabulary. AC-2 exists for this.
- **README convention ambiguity** — if `docs/adr/README.md` does not actually specify a marker form (the audit
  read it as `Superseded by ADR-XXX`), follow the form already used by any other superseded ADR in the repo
  (repo-root `ADR-MONO-003` and `ADR-MONO-021` are both SUPERSEDED and can serve as the precedent).
- **Someone reads this as authority to delete ADR-003** — superseded ADRs are kept, not removed; the record of
  a reversed decision is the point of the archive.

## Failure Scenarios

- **F1 — over-broad supersession** — marking all of ADR-003 dead would strand the `web-store` FSD architecture
  with no live ADR backing it, inverting the defect instead of fixing it. Guarded by AC-2.
- **F2 — the record is corrected while the premise moved** — if admin-dashboard were revived, or MONO-031
  amended, this edit would itself become the false statement. Guarded by AC-0.
- **F3 — index/file disagreement** — editing the ADR but not `README.md` (or vice versa) reproduces exactly the
  class of drift this task exists to remove. Guarded by AC-3.
