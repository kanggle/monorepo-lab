# Workflow: Task Implementation

End-to-end workflow for implementing a task.

## Prerequisites

- Task exists in `tasks/ready/`
- Task has all required sections (Goal, Scope, Acceptance Criteria, Related Specs, Related Contracts, Edge Cases, Failure Scenarios)

## Steps

### 1. Read CLAUDE.md

- Read `CLAUDE.md` to understand core principles, source of truth priority, hard stop rules, and required workflow

### 2. Read and Validate Task

- Read `tasks/ready/<task>.md`
- Verify all required sections exist
- Verify related specs and contracts exist
- **Hard Stop**: If anything is missing, stop and report

### 3. Read Specs

Follow the reading order from `platform/entrypoint.md`:

1. `platform/` — platform policies (core specs first, then tag-matched auxiliary specs)
2. `specs/contracts/` — related API/event contracts
3. `specs/services/<service>/` — service architecture
4. `specs/features/` — feature specs
5. `specs/use-cases/` — use cases

### 4. Read Skills

- Find matching skills in `.claude/skills/INDEX.md` for the task type
- Read only matched skill files (skip unrelated ones)

### 5. Reference Design Context

- If needed for design judgment, review relevant files in `knowledge/`
- Use `knowledge/` only to understand design rationale and trade-offs, not as source of truth
- Specs always take priority over `knowledge/` documents

### 6. Understand Existing Code

- Read current code in the target service to understand patterns, conventions, and structure
- Maintain consistency with existing patterns

### 7. Implement

- Implement within the scope defined by specs and contracts
- Follow layer dependency rules
- Match contract field names exactly

### 8. Write Tests

- Follow Required Tests Per Task in `platform/testing-strategy.md`
- Cover all acceptance criteria items with tests
- Test edge cases and failure scenarios

### 9. Self-Review

- [ ] No layer dependency violations
- [ ] Contract field names match exactly
- [ ] All tests pass
- [ ] All acceptance criteria met
- [ ] No changes outside spec scope

### 10. Move Task

- `tasks/ready/` → `tasks/in-progress/` → after completion `tasks/review/`

## Related Agents

| Step | Agent |
|---|---|
| Architecture decisions | `architect` |
| Backend implementation | `backend-engineer` |
| Frontend implementation | `frontend-engineer` |
| Test writing/verification | `qa-engineer` |
| Code review | `code-reviewer` |
| Contract changes needed | `api-designer`, `event-architect` |
