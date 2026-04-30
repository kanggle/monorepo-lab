---
name: design-event
description: Design a new domain event contract or modify an existing one
---

# design-event

Design a new domain event contract or modify an existing one.

## Usage

```
/design-event <description>                                # design a new event contract
/design-event add <EventName> event to <service>-events    # add event to existing contract
```

Examples:

```
/design-event product review created event design
/design-event add OrderShipped event to order-events
```

## Procedure

1. Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` that match the declared classification. Trait files such as `transactional.md`, `integration-heavy.md`, or `real-time.md` contain event-specific mandates (idempotency, DLQ, ordering) that must be honored in the contract design.
2. Read `platform/event-driven-policy.md` (envelope, naming, consumer rules)
3. Read `platform/naming-conventions.md` (event topic naming)
4. Read `platform/versioning-policy.md` (event versioning)
5. Read existing `specs/contracts/events/` contract files to understand current patterns
6. Read `specs/services/<service>/overview.md` for the related service (ownership check)
7. Design the event contract following the format below
8. Write to `specs/contracts/events/<service>-events.md`
9. Update related feature/use-case specs and service overview Related Events

## Contract Format

Per event:
- Event Name
- Topic: `{service}.{entity}.{event}`
- Publisher / Consumers
- Trigger (when the event is published)
- Payload fields (camelCase)

## Standard Envelope

```json
{
  "event_id": "UUID",
  "event_type": "EventName",
  "occurred_at": "ISO-8601",
  "source": "service-name",
  "payload": { }
}
```

## Rules

- Envelope fields: snake_case, payload fields: camelCase
- Events represent past facts — immutable after publication
- Topic naming: `{service}.{entity}.{event}` (kebab-case)
- Consumers: idempotent processing required, DLQ required
- Producers: publish after transaction commit (outbox pattern)
- Breaking changes: create new version as `{EventName}V{n}`
