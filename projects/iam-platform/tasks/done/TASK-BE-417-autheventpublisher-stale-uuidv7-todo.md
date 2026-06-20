# TASK-BE-417 — Remove stale UUID-v7 TODO in AuthEventPublisher

**Status:** done
**Type:** code cleanup (stale marker)

## Goal

`AuthEventPublisher.write(...)` carries `// TODO: TASK-BE-015 switch to UUID v7 when Java 21+ UUID v7 support is added`. The migration already shipped: `BaseEventPublisher.writeEvent()` (`libs/java-messaging`) sets `eventId` via `UuidV7.randomString()`, and auth events flow through `writeEvent()`. The TODO is a false "outstanding work" signal.

## Scope

- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/application/event/AuthEventPublisher.java` — delete the one stale TODO comment line. No behavior change.

## Acceptance Criteria

- [ ] **AC-1** — The stale TODO line is removed; `write(...)` still delegates to `writeEvent(...)` unchanged.
- [ ] **AC-2** — auth-service build + tests GREEN.

## Related Specs / Contracts

- None (comment-only cleanup). Lib reference: `libs/java-messaging/.../BaseEventPublisher.java` (`UuidV7.randomString()`).

## Edge Cases / Failure Scenarios

- N/A — pure comment removal, no runtime path affected.
