# How this is built

This document describes the **development process** — the methodology, not the output. It's written for portfolio evaluators curious about "how was this produced so fast?" and for my own future reference when onboarding the next project onto this framework.

The short version: **rule-driven AI-assisted development with Claude Code, governed by a spec-first task lifecycle and enforced review discipline.** Specs are the source of truth, tasks are the unit of work, AI is a collaborator held to the same standards as a junior engineer.

---

## The core loop

```
spec / contract  →  task (ready)  →  implement (branch + PR)
     ↑                                           ↓
     └──── review (APPROVE | FIX → new ticket) ← task (review)
```

Everything flows through this loop. Features, fixes, and follow-ups. No code lands without a task. No task goes to `done/` without a review pass.

---

## Layer 1 — Rule taxonomy

The root `CLAUDE.md` mandates that AI agents classify the target project before doing anything:

```markdown
# PROJECT.md (in projects/wms-platform/)
domain: wms
traits:
  - transactional
  - integration-heavy
```

This classification activates specific rule bundles:

- `rules/common.md` — always loaded
- `rules/domains/wms.md` — WMS-specific invariants (W1-W6: globally-unique location codes, parent-active guards, referential integrity, etc.)
- `rules/traits/transactional.md` — Idempotency-Key, optimistic locking, outbox, state machines
- `rules/traits/integration-heavy.md` — circuit breakers, bulkhead, retry policy, DLQ

The AI loads only the bundles the project declared. A fintech project with `domain: banking` would load entirely different rules. This keeps context windows tight and reasoning focused.

**Why it matters**: A project without declared classification causes the AI to *stop and report*. Hard stop. No guessing. No implementation. This discipline alone prevents 80% of AI-assistance failure modes.

---

## Layer 2 — Spec-first development

The `platform/entrypoint.md` defines the spec reading order:

1. Platform regulations (error handling, testing strategy, service-type conventions)
2. Domain rules + trait rules (activated by `PROJECT.md`)
3. Project-specific architecture / domain model / contracts
4. Task file
5. Existing code (last — to understand current patterns)

HTTP and event contracts are authored **before** implementation. The `specs/contracts/http/master-service-api.md` exists before a single `@PostMapping` is written. Event envelope schemas in `specs/contracts/events/master-events.md` exist before the outbox publisher code. This is enforced at review time — code that diverges from contract is a fix ticket.

JSON Schema files under `src/test/resources/contracts/` are verified by `HttpContractTest` / `EventContractTest` in CI. The spec is not aspirational; it's tested.

---

## Layer 3 — Task lifecycle

```
backlog → ready → in-progress → review → done → archive
```

Strict rules:
- Only `ready/` items may be implemented.
- A task in `review/` with issues → **new fix ticket in `ready/`**, never edit the original. Original moves to `done/` with the review verdict recorded in `tasks/INDEX.md`.
- Fix tickets reference the original (`"Fix issue found in TASK-BE-004"`).

`tasks/INDEX.md` accumulates an auditable ledger of every task, every review verdict, every follow-up chain. As of v1 complete: 21 tasks across master-service + gateway-service, every verdict recorded.

The ledger reads like an engineering diary:
```
- TASK-BE-004 — SKU CRUD. Review verdict: FIX NEEDED → TASK-BE-011
- TASK-BE-011 — SKU test coverage. Review verdict: APPROVED (2 suggestions)
- TASK-BE-010 — ReferenceIntegrityViolationException. Review verdict: FIX NEEDED → TASK-BE-014
- TASK-BE-014 — Warehouse deactivate active-zones guard. Review verdict: APPROVED
```

Nothing in this ledger is manually curated. It falls out of the process.

---

## Layer 4 — `/process-tasks` pipeline

The flagship command: **runs the entire lifecycle end-to-end in one invocation**.

```
Phase 1 — IMPLEMENT (parallel rounds)
  ├─ Discovery: list everything in tasks/ready/
  ├─ Analysis: classify each task (simple-code, code-with-event, contract-change, …)
  ├─ Dependency resolution → topological rounds
  └─ Execute via worktree-isolated subagents
       - backend-engineer for Spring Boot work
       - frontend-engineer for Next.js work
       - qa-engineer for test-only tasks
     Each subagent runs in its own git worktree. Parallel tasks run simultaneously.

Phase 2 — REVIEW (all parallel)
  └─ For every task in tasks/review/, launch a code-reviewer subagent
     in an isolated worktree. APPROVE → move to done/. FIX NEEDED →
     create fix ticket, move original to done/.

Phase 3 — SUMMARY
  └─ Pipeline-wide report: what landed, what got approved, what opened.
```

A single `/process-tasks` invocation has landed 8+ tasks in parallel (see PR #17 — "Phase 3 — close 8 reviews, author 6 fix tickets"). The subagents don't share context; each one reads only what its task requires. The main conversation coordinates.

---

## Layer 5 — Review discipline

Every implementation gets an **independent** review pass. The reviewer reads the task spec, checks each acceptance criterion, and produces one of:

- **APPROVE** — all AC satisfied; non-blocking suggestions noted.
- **FIX NEEDED** — creates new ticket in `ready/` describing the critical; original task moves to `done/` with verdict.

No inline fixes. No "let me just tweak that." Critical issues become trackable follow-up work. This prevents hidden TODOs and keeps every change reviewable.

Real example chain from this repo:
```
TASK-BE-001 (bootstrap) → FIX NEEDED → TASK-BE-008 (error envelope)
TASK-BE-002 (zone)       → FIX NEEDED → TASK-BE-009 (adapter cleanup)
TASK-BE-003 (location)   → FIX NEEDED → TASK-BE-010 (ref integrity)
TASK-BE-010              → FIX NEEDED → TASK-BE-014 (warehouse active-zones guard)
TASK-BE-014              → APPROVED
```

The chain reads as "we iteratively improved until no critical remained." Reviews become the primary forcing function for quality.

---

## Layer 6 — Shared library extraction

A strict boundary: shared content (`platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`) must stay **project-agnostic**. No service names, API paths, or domain entities leak in. Violating this is a Hard Stop — enforced by the CLAUDE.md rules.

This discipline enables the "Discovery → Distribution" strategy (see [TEMPLATE.md](../../TEMPLATE.md)): the library matures in this monorepo across multiple projects, then gets extracted into a standalone Template repo once stable. The `scripts/sync-portfolio.sh` already demonstrates the extraction mechanics for individual project repos — the same pattern applies to Template extraction.

---

## What the AI actually does

- Implements tasks from `ready/`, following specs as source of truth.
- Writes tests commensurate with the task scope (unit + slice + H2 + Testcontainers where appropriate).
- Reviews others' tasks against AC, flags criticals.
- Adapts patterns from 80+ skill files under `.claude/skills/` (Hexagonal structure, outbox pattern, idempotent consumer, testing strategy, etc.).
- Stops and reports when specs are missing / conflicting / ambiguous — never guesses.

## What the AI does NOT do

- Decide architecture without a spec.
- Skip review.
- Edit tasks in `review/` or `done/`.
- Introduce new dependencies or patterns without justification in the task's Implementation Notes.
- Break the Library-vs-Project boundary.

---

## Concrete artifacts

- **60+ commits** on `main` — each commit references its task ID and verdict.
- **21 tasks** in `tasks/done/` — every one with its review verdict documented.
- **5 aggregates** (Warehouse · Zone · Location · SKU · Lot) shipped with full test pyramid.
- **80+ reusable skills** under `.claude/skills/` — each a focused implementation pattern.
- **Contract harness**: JSON Schema validation for 20+ HTTP endpoints and 5 event types.
- **Live-pair e2e suite** with 5 scenarios on gateway↔master using Testcontainers.

All of this is reachable by reading the git history. `git log --oneline main` tells the story of how the system grew task by task.

---

## Why this works

The discipline exists because AI is capable enough to land work fast, but NOT capable enough to make architectural decisions under ambiguity. The rule/spec/task framework constrains the decisions to where they belong (a human in the loop for architecture, review, acceptance criteria) and releases the AI to do what it's genuinely good at: implementing well-specified work.

The result is a portfolio that **looks like a small team built it over months**, generated instead by one person over a week. Not by one person "cranking out code" — by one person operating a well-designed process where the AI is a force multiplier, not a cowboy.
