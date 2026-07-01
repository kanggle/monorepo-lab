# Task ID

TASK-MONO-320

# Title

Remove a dangling session-memory file reference from the shared spec `platform/error-handling.md` (found by `/validate-rules`)

# Status

done

# Owner

platform

# Task Tags

- docs
- governance

---

# Goal

A `/validate-rules` scan (2026-07-01) flagged one **Critical** finding: the shared,
project-agnostic spec `platform/error-handling.md` cited a session-memory artifact by
filename as a policy authority.

At [`platform/error-handling.md`](../../platform/error-handling.md) § Auth `[domain: ecommerce]`,
the text read:

> Owned by `auth-service` — ecommerce-local credential and OAuth flow (distinct from IAM
> IdP; the ecommerce standalone repo retains its own auth flow **per
> `project_gap_idp_promotion.md` § standalone frozen policy**).

`project_gap_idp_promotion.md` is a **personal Claude session-memory file** (archived under
the agent's `memory/` store) — it does **not** exist anywhere in the repository. Any agent
reading the spec as source-of-truth cannot follow the citation, and a shared library file
should never depend on a personal memory artifact. This task removes the dangling citation
while preserving the factual statement.

# Scope

## In Scope

- Delete the `per project_gap_idp_promotion.md § standalone frozen policy` clause from the
  Auth section of `platform/error-handling.md`. The surrounding factual statement
  ("the ecommerce standalone repo retains its own auth flow") is retained.

## Out of Scope

- The other `/validate-rules` findings (all Warning/Info): `review-checklist` skill
  `category: root`, command-file duplication (`implement-task`↔`process-tasks`), and the
  `console-integration-contract.md` inline-path Warning. The last one is **intentionally not
  "fixed"** — adding the repo-relative project path to a shared spec is itself blocked by
  HARDSTOP-03 (project path-token in a shared file), so the bare filename is the correct form.

---

# Acceptance Criteria

- [x] **AC-1** — The `project_gap_idp_promotion.md` citation is removed from
  `platform/error-handling.md`; `grep -r project_gap_idp_promotion platform/` returns no match.
- [x] **AC-2** — The factual claim (ecommerce auth-service is IdP-distinct, standalone-local)
  is preserved; only the non-navigable memory-file citation is dropped.
- [x] **AC-3** — No new project path-token is introduced into the shared file (HARDSTOP-03 clean;
  verified by the hardstop hook allowing the edit).

---

# Related Specs

- [`platform/error-handling.md`](../../platform/error-handling.md) — the shared HTTP-error-code
  registry (the edited file).
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Forbidden in
  Shared Libraries — the boundary rule the dangling reference violated.

# Related Contracts

- 없음 (documentation-only correction; no API/event contract change).

---

# Edge Cases

- The Auth section is `[domain: ecommerce]`-tagged, so naming `auth-service` and "ecommerce"
  in prose is permitted (domain-specific sub-sections are allowed in `error-handling.md`); the
  defect was strictly the citation of a non-existent memory file, not the domain content.

# Failure Scenarios

- 없음 — a single-line prose deletion in a Markdown spec; no build/runtime surface. Correctness
  is self-evident from the `grep` check in AC-1.

---

# Definition of Done

- [x] AC-1…AC-3 satisfied
- [x] Shared-file change lands via a monorepo-level branch + PR (this task)
