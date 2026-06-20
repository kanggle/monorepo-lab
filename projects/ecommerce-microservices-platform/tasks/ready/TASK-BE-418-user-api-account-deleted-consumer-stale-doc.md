# TASK-BE-418 — Correct stale "not yet implemented" account.deleted consumer note in user-api.md

**Status:** ready
**Type:** docs (spec accuracy)

## Goal

`user-api.md` § "Account withdrawal" still states the IAM→ecommerce `account.deleted` wiring is "not yet implemented (the entry point is currently orphaned — no controller, no consumer triggers it)." This is stale: TASK-BE-388 shipped `AccountDeletedConsumer` (`@KafkaListener(topics = "account.deleted", groupId = "user-service")`, `@Profile("!standalone")`) which invokes `withdrawProfile()` / `anonymizeProfile()`. Correct the paragraph to reflect the live wiring.

## Scope

- `projects/ecommerce-microservices-platform/specs/contracts/http/user-api.md` — rewrite the "ecommerce reaction" paragraph from "forward-declared, not yet wired" to "wired (TASK-BE-388)", referencing `AccountDeletedConsumer` and the sibling `account-lifecycle-subscriptions.md`.

## Acceptance Criteria

- [ ] **AC-1** — The paragraph no longer claims the consumer is unimplemented/orphaned.
- [ ] **AC-2** — It names `AccountDeletedConsumer` + the `@KafkaListener` topic and links the lifecycle-subscriptions contract.
- [ ] **AC-3** — Docs-only; no code touched.

## Related Specs / Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/user-api.md` (corrected)
- `account-lifecycle-subscriptions.md` (sibling — already says CLOSED)
- Impl: `apps/user-service/.../infrastructure/event/AccountDeletedConsumer.java` (TASK-BE-388)

## Edge Cases / Failure Scenarios

- N/A — documentation accuracy correction; no runtime behavior.
