# Workflow: Production Fix

Workflow for emergency production fixes.

## Principles

- **Minimal scope**: Change only what is necessary to resolve the issue
- **Tests required**: Never deploy without tests, even in emergencies
- **Root cause follow-up**: Create a separate task for root cause resolution after the hotfix

## Steps

### 1. Identify the Problem

- Confirm symptoms, impact scope, and time of occurrence
- Analyze logs, metrics, and error traces

### 2. Isolate Root Cause

- Trace the relevant code paths
- Check recent deployment changes (`git log`)
- Identify reproducible scenarios

### 3. Implement Minimal Fix

- Apply the minimum code change that resolves the issue
- Do NOT refactor, improve, or add features
- Maintain existing patterns and conventions

### 4. Test

- Write a test that reproduces the bug first (confirm it fails)
- Confirm the test passes after the fix
- Confirm all existing tests still pass

### 5. Deploy

- Work on a hotfix branch
- Get code review
- Monitor after deployment

### 6. Follow-Up

- Create a separate task in `tasks/ready/` for root cause resolution
- Record the incident

## Related Agents

| Role | Agent |
|---|---|
| Root cause analysis | `architect`, `backend-engineer` |
| Fix implementation | `backend-engineer`, `frontend-engineer` |
| Testing | `qa-engineer` |
| Deployment | `devops-engineer` |
