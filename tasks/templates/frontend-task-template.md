# Task ID

TASK-FE-XXX

# Title

Short and clear task title

# Status

ready

# Owner

frontend

# Task Tags

<!-- Select all that apply. Used by entrypoint.md to determine which auxiliary specs to read. -->
<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api

# Goal

Describe the user-visible result that must be achieved.

# Scope

## In Scope

- item 1
- item 2
- item 3

## Out of Scope

- item 1
- item 2
- item 3

# Acceptance Criteria

- [ ] Screen behavior is testable
- [ ] Error handling is testable
- [ ] Loading/empty state is testable

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/...`
- `specs/features/...`
- `specs/use-cases/...`
- `specs/services/<service>/api.md`

# Related Skills

<!-- Reference .claude/skills/INDEX.md to find relevant skills. List only what this task needs. -->
- `.claude/skills/frontend/...`

# Related Contracts

- `specs/contracts/http/...`

# Target App

- `apps/web` or `apps/admin`

# Implementation Notes

Describe UI/UX constraints, routing constraints, permission assumptions, or data dependencies.

# Edge Cases

- empty state
- loading state
- partial data
- invalid input
- unauthorized access

# Failure Scenarios

- API error
- timeout
- validation failure
- stale data
- permission denied

# Test Requirements

- component test
- page/flow test
- e2e if task scope requires it

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review