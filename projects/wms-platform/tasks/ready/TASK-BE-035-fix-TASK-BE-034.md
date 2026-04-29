# TASK-BE-035 — Fix issues found in TASK-BE-034 (outbound-service bootstrap)

| Field | Value |
|---|---|
| **Task ID** | TASK-BE-035 |
| **Title** | outbound-service: Fix webhook processing order, outbound_outbox schema gaps, and eventdedupe outcome column |
| **Status** | ready |
| **Owner** | backend |
| **Tags** | outbound, fix, schema, webhook |

---

## Goal

Fix issues found in TASK-BE-034 (outbound-service bootstrap review):

1. **[Critical] Webhook processing order deviates from spec** — `ErpOrderWebhookController` checks the timestamp window before resolving the HMAC secret, but `specs/contracts/webhooks/erp-order-webhook.md` § Processing Order mandates: Step 1 = resolve secret, Step 2 = verify timestamp, Step 3 = verify HMAC. Re-order to match the spec exactly.

2. **[Warning] `outbound_outbox` table schema diverges from `domain-model.md` §7** — The V6 migration creates the table with `status`, `retry_count` columns and is missing `aggregate_type`, `event_version`, `partition_key` columns declared in the domain model. Align the table schema with the spec. If the deviation is intentional (publisher-led design), update `domain-model.md` §7 first (spec wins, per CLAUDE.md Source of Truth Priority).

3. **[Warning] `outbound_event_dedupe` missing `outcome` column** — The domain model spec §8 declares an `outcome` enum column (`APPLIED` / `IGNORED_DUPLICATE` / `FAILED`); the V6 migration omits it. Either add the column or update the spec to reflect the intentional omission.

---

## Scope

### In Scope

- `ErpOrderWebhookController.receive()` — reorder validation steps to match the spec's mandated sequence (secret → timestamp → HMAC → body schema → dedupe+inbox)
- `src/main/resources/db/migration/V6__init_outbox_dedupe.sql` — align `outbound_outbox` columns with `domain-model.md` §7 (or update spec first)
- `src/main/resources/db/migration/V6__init_outbox_dedupe.sql` — add `outcome` column to `outbound_event_dedupe` per `domain-model.md` §8 (or update spec first)
- `OutboundEventDedupe` JPA entity — add `outcome` field if column is added
- Update existing controller test `ErpOrderWebhookControllerTest` to verify the correct error code is returned when timestamp check is invoked after a missing/unknown source (if observable via test)

### Out of Scope

- Any domain logic (Order/Saga/Picking/Packing) — deferred to TASK-BE-035 (original plan) / TASK-BE-036+
- Real outbox publisher implementation — TASK-BE-035 (original plan)
- Any new API endpoints

---

## Acceptance Criteria

1. `ErpOrderWebhookController.receive()` validates steps in this exact order: (a) resolve secret from `X-Erp-Source`, (b) verify `X-Erp-Timestamp` window, (c) verify HMAC over raw body, (d) validate body schema, (e) dedupe + inbox write
2. An `X-Erp-Source` header referencing an unknown env results in `401 WEBHOOK_SIGNATURE_INVALID` before the timestamp is even evaluated
3. `outbound_outbox` table columns match `domain-model.md` §7 exactly (or spec is updated with a documented rationale)
4. `outbound_event_dedupe` table includes the `outcome` column (or spec is updated with a documented rationale)
5. `./gradlew :projects:wms-platform:apps:outbound-service:test` passes (zero failures)
6. All 12 webhook failure-mode test cases in `ErpOrderWebhookControllerTest` still pass

---

## Related Specs

- `specs/contracts/webhooks/erp-order-webhook.md` — § Processing Order (authoritative step sequence)
- `specs/services/outbound-service/domain-model.md` — §7 OutboundOutbox, §8 EventDedupe
- `specs/services/outbound-service/architecture.md` — § Webhook Reception

---

## Related Contracts

- `specs/contracts/webhooks/erp-order-webhook.md` — webhook wire format and processing order
- `specs/contracts/events/outbound-events.md` — event type values stored in outbox `event_type`

---

## Edge Cases

1. Unknown source header with a valid-looking timestamp — should receive `401 WEBHOOK_SIGNATURE_INVALID` (not `WEBHOOK_TIMESTAMP_INVALID`), matching the corrected processing order
2. Adding `outcome` column to `outbound_event_dedupe` must not break the `Propagation.MANDATORY` adapter if outcome tracking requires an extra write

---

## Failure Scenarios

1. Migration V6 re-creation not possible (Flyway tracks applied migrations) — must introduce a new migration `V6a` or `V9` for the schema changes, not edit V6 directly
2. Reordering controller steps changes the observable error code for some existing test cases — update assertions to match the corrected spec order
