# Workflow: Contract Change

Workflow for changing API or event contracts.

## Principle

> Contract changes must be performed before implementation. (CLAUDE.md Contract Rule)

## Steps

### 1. Confirm Change Necessity

- Identify the rationale for the contract change from the task or spec
- Read the existing contract to understand the current state

### 2. Impact Analysis

- Identify all services that use the contract
- Determine whether the change is breaking
  - Field deletion/rename = breaking
  - Field addition (optional) = non-breaking
  - Type change = breaking

### 3. Update Contract

- Modify the relevant file in `specs/contracts/`
- Update version information
- Record the reason for the change

### 4. Sync Related Specs

- Verify that service specs referencing the contract align with the new contract
- Update specs if inconsistencies are found

### 5. Reflect in Implementation

- Proceed with code implementation only after the contract is finalized
- Verify Request/Response field names match the contract exactly

## Related Agents

| Role | Agent |
|---|---|
| API contract changes | `api-designer` |
| Event contract changes | `event-architect` |
| Impact analysis | `architect` |
