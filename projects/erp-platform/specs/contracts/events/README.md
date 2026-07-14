# Event Contracts — erp-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: live code (outbox publisher classes, topic/eventType constants, `@KafkaListener` consumers), read alongside the 4 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Consistent** across both producers (approval-service, masterdata-service): `<prefix>.<domain>.<fact>.v1`, dot-separated, lowercase, versioned.

Examples: `erp.approval.submitted.v1`, `erp.approval.delegated.v1`, `erp.approval.delegation.revoked.v1`, `erp.masterdata.department.changed.v1`.

## 2. `eventType` Naming

**Consistent.** Dot-separated lowercase, no version suffix, equal to the topic name minus `.v1`: `erp.approval.submitted`, `erp.masterdata.department.changed`.

## 3. Serialization

**JSON**, via Jackson (`ObjectMapper.writeValueAsString`) — both `OutboxApprovalEventPublisher.java` and `OutboxMasterdataEventPublisher.java` persist a JSON string to the outbox table. No Avro/Protobuf anywhere.

## 4. Schema Registry

**Not used.** Zero hits for `schema.registry`, `SchemaRegistryClient`, Apicurio, or Confluent serializer config anywhere in the project.

## 5. Envelope Shape (informational)

**Diverged from the four spec markdown files, but consistent in the code itself.** The spec files document a 9-field envelope (`eventId, eventType, occurredAt, tenantId, source, aggregateType, aggregateId, traceId, payload`). The actual v2 outbox code in both publishers builds a 7-field envelope: `eventId, eventType, source, occurredAt, schemaVersion, partitionKey, payload` — no `tenantId`/`aggregateType`/`aggregateId`/`traceId` at the envelope level (`tenantId` lives only inside `payload`). Both publishers independently document this in a Javadoc comment as the deliberately-preserved "exact 7-field shape the previous `BaseEventPublisher` path emitted." The spec markdown was never updated to match — treat the code as authoritative for the wire shape until the spec files are corrected (separate doc-fix, not in scope here).

## 6. Contract Index

| File | Producer / Type |
|---|---|
| `erp-approval-events.md` | approval-service — producer |
| `erp-masterdata-events.md` | masterdata-service — producer |
| `notification-subscriptions.md` | notification-service — consumer of approval/masterdata events |
| `read-model-subscriptions.md` | read-model-service — consumer of approval/masterdata events |

---

## Follow-up (not in scope for this README)

- The 4 spec files under this directory describe a 9-field envelope that the code has not emitted since the v2 outbox migration (TASK-ERP-BE-025/026). Recommend a doc-fix task to update `erp-approval-events.md` / `erp-masterdata-events.md` envelope examples to the actual 7-field shape — this README's § 5 is the interim source of truth for the discrepancy.
