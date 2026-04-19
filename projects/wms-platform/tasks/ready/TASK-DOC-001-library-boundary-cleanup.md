# Task ID

TASK-DOC-001

# Title

Clean project-specific references leaked into the shared `libs/` layer

# Status

ready

# Owner

backend

# Task Tags

- code
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

# Goal

Honor the Library vs Project boundary declared in `CLAUDE.md`. A grep across
`libs/`, `platform/`, `rules/`, `.claude/` turned up four Java files in
`libs/` that still cite prior-project service names and task numbers in
Javadoc. This is a Hard Stop per `CLAUDE.md`:

> "Shared library file (under `platform/`, `rules/`, `.claude/`, `libs/`,
> `tasks/templates/`, `docs/guides/`) contains project-specific content
> (service names, API paths, domain entities) — the Library vs Project
> boundary is broken."

This task removes those leaks. It does **not** touch production behavior —
every change is a Javadoc / comment rewrite to generic language.

Investigation also found that `platform/architecture.md`,
`platform/service-boundaries.md`, and `platform/api-gateway-policy.md` — the
three files previously flagged in BE-001 / BE-002 / BE-003 review notes — are
**already clean** as of commit `09e7e95` (the STEP 1 `.claude/` sweep). The
project-memory "Platform Doc Debt" entry is stale; this task also removes
that stale entry.

---

# Scope

## In Scope

- `libs/java-security/src/main/java/com/gap/security/redis/RedisKeyHelper.java`
  — Javadoc references `specs/services/auth-service/redis-keys.md` (a spec
  from an earlier project that does not exist here). Rewrite generically.
- `libs/java-common/src/main/java/com/example/common/id/UuidV7.java`
  — Javadoc lists specific uses under `auth-service` and `admin-service` with
  pointers into `specs/services/.../` and a reference to `TASK-BE-028c`.
  Rewrite generically.
- `libs/java-common/src/test/java/com/example/common/id/UuidV7Test.java`
  — Javadoc references `TASK-BE-028c`. Drop the task reference; keep the
  RFC citation.
- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxJpaConfig.java`
  — comment references `TASK-BE-047`. Drop the task reference; keep the
  technical explanation.
- Update `MEMORY.md` to remove / replace the stale "Platform Doc Debt" entry
  (`project_platform_doc_debt.md`).

## Out of Scope

- Any behavioral changes. Every edit is prose-only.
- Renaming packages (`com.gap.security`, `com.example.*`) — these are
  orthogonal to the boundary rule and were accepted naming at library
  promotion time.
- New domain-agnostic conventions. The current `platform/` docs already
  express the conventions well.

---

# Acceptance Criteria

- [ ] Grep across all shared paths (`libs/`, `platform/`, `rules/`,
      `.claude/`, `tasks/templates/`, `docs/guides/`) returns zero matches
      for: `auth-service`, `admin-service`, `order-service`,
      `payment-service`, `checkout-service`, `user-service`,
      `product-catalog-service`, `cart-service`, `TASK-BE-028`,
      `TASK-BE-047`, `device_sessions`, `admin_operators`
- [ ] `./gradlew check` still passes — no behavioral change expected, but
      the tests validate this
- [ ] `MEMORY.md` no longer lists the stale platform doc debt entry, OR the
      entry is rewritten to reflect the real remaining library debt (this task)
- [ ] Review note records the scope finding: platform/* docs were already
      clean; only `libs/` needed the sweep

---

# Related Specs

- `CLAUDE.md` — Hard Stop Rules and Shared-vs-Project boundary
- `TEMPLATE.md` — Discovery → Distribution strategy (the reason the boundary
  matters for future Template extraction)
- `platform/shared-library-policy.md`

---

# Related Contracts

None. Doc/comment sweep only.

---

# Target Service

None. This is a library-layer cleanup.

---

# Architecture

No architectural change.

---

# Implementation Notes

- Keep Javadoc useful after the sweep. Do not just delete the "Used for"
  block — replace with a generic description of what `UuidV7` is for (e.g.,
  "time-ordered identifiers for aggregate IDs and event IDs in services that
  require ordering guarantees").
- `TASK-BE-028c` and `TASK-BE-047` are task numbers from an earlier project.
  A comment referencing a task number is fine in general, but only if the
  task file lives in the current project. In this monorepo those files do
  not exist, so the references are dangling. Delete the citation; keep the
  technical explanation.

---

# Edge Cases

- After the sweep, if a reviewer later needs to understand why `UuidV7` was
  promoted, the promotion context is already in the commit history
  (`git log libs/java-common`). No need to encode it in Javadoc.
- `RedisKeyHelper` is an interface with no implementations in `libs/`.
  Services provide their own. The rewritten Javadoc should make that
  arrangement explicit so future readers don't look for a non-existent
  default impl.

---

# Failure Scenarios

- If a CI Javadoc-lint stage exists and enforces something about the changed
  text, it will complain — address and re-commit. None such exists today.

---

# Test Requirements

- `./gradlew check` — no new tests authored. Existing tests cover the
  behavior (nothing behavioral changed).

---

# Definition of Done

- [ ] Four Javadoc sweeps landed
- [ ] Acceptance-criteria grep returns zero matches for the listed tokens
- [ ] `./gradlew check` passes
- [ ] Review note records the scope finding
- [ ] `MEMORY.md` updated
- [ ] Ready for review
