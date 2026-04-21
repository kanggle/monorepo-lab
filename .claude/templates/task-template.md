# Task ID

TASK-{TYPE}-{NUMBER}

# Title

Short and clear task title

# Status

ready

# Owner

backend | frontend | integration

# Task Tags

<!-- Select all that apply. Used by entrypoint.md to determine which auxiliary specs to read. -->
<!-- api | event | deploy | code | test | adr | onboarding -->

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

Describe the exact implementation goal.

This must explain what should become true after the task is complete.

---

# Scope

## In Scope

- item 1
- item 2

## Out of Scope

- item 1
- item 2

---

# Acceptance Criteria

- [ ] Criterion 1 is testable
- [ ] Criterion 2 is testable
- [ ] Criterion 3 is testable

---

# Related Specs

- `specs/platform/...`
- `specs/services/<service>/...`
- `specs/features/...`

# Related Skills

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

---

# Failure Scenarios

- failure scenario 1
- failure scenario 2

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
