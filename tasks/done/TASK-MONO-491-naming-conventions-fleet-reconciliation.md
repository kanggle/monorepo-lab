# Task ID

TASK-MONO-491

# Title

Reconcile platform/naming-conventions.md with conventions the fleet already converged on

# Status

done

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

A 2026-07-29 monorepo-wide audit (8 projects, parallel per-project surveys against `platform/naming-conventions.md`) found the same handful of "violations" independently, in every project: application-layer output classes named `*View`/`*Page` instead of the documented `{UseCase}Result`; `@Service` beans named `*UseCase`; a 4-segment package root (`com.example.{project}.{service}.{layer}`) instead of the documented 3-segment form; AIP-136 colon-verb endpoints (`/{id}:cancel`) used alongside the documented slash form; and no table row at all for `*Adapter`, `*Properties`, exception-handler classes, or lock/idempotency `*Store` classes. None of these are scattered typos — each is the convention every project's engineers independently reached for. This task updates the naming doc to describe what the fleet actually does, per `platform/naming-conventions.md § Change Rule` ("Changes to naming conventions must be documented here before applying to new code") — it does **not** rename any existing code.

---

# Scope

## In Scope

- `platform/naming-conventions.md`:
  - Split the existing "Application-layer input/output" row into an input row (`{UseCase}Command`, unchanged) and an output row documenting both `{UseCase}Result` (mutation use cases) and `{UseCase}View`/`{UseCase}Page` (read/query use cases) as accepted forms.
  - Add a `@ConfigurationProperties` row (`*Properties` suffix).
  - Add an outbound-port-adapter row (`*Adapter` suffix, for non-repository outbound ports).
  - Add an exception-handler row (`*ExceptionHandler` suffix, covering both per-service `GlobalExceptionHandler`s and the shared `CommonGlobalExceptionHandler` base).
  - Add a lock/idempotency-store row (`*Store` suffix).
  - Update the Service row to accept `*UseCase` as an equally-valid suffix alongside `*Service` for application-layer use-case beans.
  - Update § Packages to document the 4-segment `com.example.{project}.{service}.{layer}` form as the primary convention, with the pre-existing 3-segment form grandfathered for the two projects that predate it (ecommerce, iam) — no repackaging required.
  - Update § Packages to note the Hexagonal `adapter/in|out` (or `adapter/inbound|outbound`) + `config` package set as a valid alternative to the Layered `domain/application/infrastructure/presentation` set, matching what every Hexagonal-declared service in the repo already does.
  - Update § API Endpoints to document the AIP-136 colon-verb form (`/{id}:cancel`) as a second valid action-endpoint form alongside the slash form, scoped to endpoint families that already declare it in a contract.

## Out of Scope

- Renaming any existing class, package, or endpoint — this task documents existing, already-converged-on conventions; it does not change code.
- The Redis-key-prefix inconsistency the same audit found (`{service}:` prefix vs `{function}:` prefix conflicting between `naming-conventions.md` and at least one service's own `redis-keys.md`) — that needs a resolution decision between the two conflicting authorities, not a documentation update accepting one side; out of scope here, worth a separate task.
- Any `libs/`, `rules/`, or `.claude/` file — this task touches exactly one shared platform doc.
- The 8 defect-fix tasks and the fleet-wide shared-library extraction candidates the same audit found — tracked separately (`TASK-MONO-49x`-adjacent tasks and a proposed ADR).

---

# Acceptance Criteria

- [ ] `platform/naming-conventions.md § Classes` table has separate input/output rows for application-layer types, both `{UseCase}Result` and `{UseCase}View`/`{UseCase}Page` documented as valid output forms.
- [ ] New rows exist for `*Properties`, `*Adapter` (non-repository outbound port), `*ExceptionHandler`, `*Store`.
- [ ] Service row documents `*UseCase` as a valid alternative to `*Service`.
- [ ] § Packages documents both the 4-segment and grandfathered 3-segment package forms, and both the Layered and Hexagonal layer-name sets.
- [ ] § API Endpoints documents the AIP-136 colon-verb form as contract-gated (not to be introduced without a contract-first decision).
- [ ] `git diff --stat` touches exactly one file (`platform/naming-conventions.md`) plus this task's own lifecycle files (INDEX.md, task file move) — no application code anywhere in the repo is touched.
- [ ] Doc-lint / dead-ref CI guards GREEN.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/naming-conventions.md` (the file being amended)
- `platform/shared-library-policy.md` (unrelated policy referenced by the audit for other findings — not amended here)
- `tasks/INDEX.md` § Task Types (unaffected — task-ID naming already correctly documented)

---

# Related Contracts

None — this task changes only a naming-guidance document, not any HTTP/event contract.

---

# Target Service

- Monorepo-level (`platform/naming-conventions.md`) — no single service.

---

# Architecture

N/A — documentation-only task.

---

# Implementation Notes

Found as a byproduct of an 8-project parallel commonality/naming audit run in this session. Every specific fact cited in the Goal section (which projects use which form, the "effectively zero `*Result` classes" observation, etc.) was independently confirmed by at least 2 of the 8 per-project audit reports.

---

# Edge Cases

- Do not let this task's own additions contradict each other — e.g. don't document the Hexagonal `config` package note in a way that implies `config` is itself a fifth "layer" on equal footing with `domain`/`application`/`adapter`; it's a cross-cutting package that coexists with the three.

---

# Failure Scenarios

- Silently expanding scope into an actual rename sweep (e.g. "while I'm here, let me rename the 3-segment packages to 4-segment") would touch dozens of files across 2 projects for zero behavioral gain and directly contradicts this task's own Scope — if tempted, stop and file it as a separate, explicitly-scoped task instead.
- Resolving the Redis-key-prefix conflict as a side effect of this task would mean picking a winner between two documents without the cross-project consensus that decision needs — explicitly out of scope, leave both as-is.

---

# Test Requirements

- None (docs-only). Run doc-lint / dead-ref CI guards.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Doc-lint / dead-ref checks passing
- [ ] Contracts unchanged (verified — none applicable)
- [ ] Ready for review
