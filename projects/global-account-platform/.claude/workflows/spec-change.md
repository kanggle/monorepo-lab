# Workflow: Spec Change

Workflow for adding or modifying specs.

## Principle

> Specs are the source of truth. When implementation conflicts with specs, specs win. (CLAUDE.md)

## Steps

### 1. Identify Change Scope

- Determine which spec files are the change target
- Clarify the reason (new feature, requirement change, error correction)

### 2. Impact Analysis

Analyze downstream impact according to spec priority:

1. `platform/` change → affects all services
2. `specs/contracts/` change → affects services using that contract
3. `specs/services/` change → affects that service
4. `specs/features/` change → affects related tasks
5. `specs/use-cases/` change → affects related features

### 3. Modify Specs

- Edit the relevant spec files
- Record change history (date, reason)
- Verify no conflicts with higher-priority specs

### 4. Sync Downstream Documents

- Check downstream specs/contracts that reference the changed spec
- Update if inconsistencies are found
- Report or update affected tasks

### 5. Verify Existing Implementation

- Check if already-implemented code aligns with the new spec
- Create fix tasks if inconsistencies are found

## Related Agents

| Role | Agent |
|---|---|
| Architecture specs | `architect` |
| API contract specs | `api-designer` |
| Event contract specs | `event-architect` |
| DB specs | `database-designer` |
