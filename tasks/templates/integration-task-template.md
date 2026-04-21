# Task ID

TASK-INT-XXX

# Title

Short and clear task title

# Status

ready

# Owner

integration

# Task Tags

<!-- Select all that apply. Used by entrypoint.md to determine which auxiliary specs to read. -->
<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- event

# Goal

Describe the cross-service or cross-component flow that must be implemented or changed.

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

- [ ] Flow is testable end-to-end
- [ ] Contract usage is consistent
- [ ] Failure handling is defined
- [ ] Retry or recovery behavior is defined where needed

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/platform/...`
- `specs/services/<service>/...`
- `specs/features/...`
- `specs/use-cases/...`

# Related Skills

<!-- Reference .claude/skills/INDEX.md to find relevant skills. List only what this task needs. -->
- `.claude/skills/messaging/...`

# Related Contracts

- `specs/contracts/http/...`
- `specs/contracts/events/...`

# Participating Components

- component/service 1
- component/service 2
- component/service 3

# Trigger

Describe what starts the flow.

# Expected Flow

1. step 1
2. step 2
3. step 3

# Edge Cases

- duplicate event/request
- partial failure
- delayed response
- missing dependent data

# Failure Scenarios

- upstream failure
- downstream failure
- contract mismatch
- timeout
- retry exhaustion

# Test Requirements

- integration test
- contract test
- end-to-end verification if needed

# Definition of Done

- [ ] Integration flow implemented
- [ ] Contracts updated first if needed
- [ ] Failure handling covered
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review