# Workflow: Event Change

Workflow for adding or modifying domain events.

## Principle

> Event contract changes must be performed before implementation. (CLAUDE.md Contract Rule)

## Steps

### 1. Identify Event Requirements

- Determine event scenarios from `specs/features/` or `specs/use-cases/`
- Identify publisher and consumer services

### 2. Design Event Contract

- Define event schema in `specs/contracts/`
- Naming: `{Aggregate}.{PastTenseVerb}` (e.g., `Order.Created`)
- Required fields: eventId, eventType, occurredAt, version, payload

### 3. Decide Messaging Patterns

- Outbox pattern applicability
- Idempotency guarantee mechanism
- Retry and DLQ policies
- Ordering guarantee requirements

### 4. Implement Publisher

- Implement domain event publishing logic
- Store events in Outbox within the transaction
- Messaging skills (`.claude/skills/messaging/`) are not yet written. Use `specs/platform/event-driven-policy.md` and the relevant event contract in `specs/contracts/events/` as primary guidance.

### 5. Implement Consumer

- Implement event handler
- Handle idempotency
- Handle failures with retry/DLQ

### 6. Test

- Event publishing tests
- Consumer idempotency tests
- Failure scenario tests

## Related Agents

| Role | Agent |
|---|---|
| Event design | `event-architect` |
| Publisher/consumer implementation | `backend-engineer` |
| Testing | `qa-engineer` |
