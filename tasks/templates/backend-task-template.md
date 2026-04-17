# Task ID

TASK-BE-XXX

# Title

Short and clear task title

# Status

ready

# Owner

backend

# Task Tags

<!-- Select all that apply. Used by entrypoint.md to determine which auxiliary specs to read. -->
<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api

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

Describe the exact implementation goal.

This must explain what should become true after the task is complete.

---

# Scope

## In Scope

- item 1
- item 2
- item 3

## Out of Scope

- item 1
- item 2
- item 3

---

# Acceptance Criteria

- [ ] Criterion 1 is testable
- [ ] Criterion 2 is testable
- [ ] Criterion 3 is testable

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/...`
- `specs/services/<service>/...`
- `specs/features/...`
- `specs/use-cases/...`

# Related Skills

<!-- Reference .claude/skills/INDEX.md to find relevant skills. List only what this task needs. -->
- `.claude/skills/backend/...`

---

# Related Contracts

- `specs/contracts/http/...`
- `specs/contracts/events/...`

---

# Target Service

- `<service-name>`

---

# Architecture

Follow:

- `specs/services/<service>/architecture.md`

---

# Implementation Notes

Describe important implementation constraints only.

Do not restate general knowledge here.

---

# Edge Cases

- edge case 1
- edge case 2
- edge case 3

---

# Failure Scenarios

- failure scenario 1
- failure scenario 2
- failure scenario 3

---

# Test Requirements

- unit test
- integration test
- contract-related test if applicable

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review