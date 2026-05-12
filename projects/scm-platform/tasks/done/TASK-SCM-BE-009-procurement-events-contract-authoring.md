# Task ID

TASK-SCM-BE-009

# Title

`scm-procurement-events.md` event contract authoring (7 published topics)

# Status

done

# Owner

backend

# Task Tags

- spec
- event

---

# Goal

Author the missing `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` event contract spec. `procurement-service` publishes 7 Kafka topics (`scm.procurement.{po.submitted, po.acknowledged, po.confirmed, po.canceled, po.received, po.closed, asn.received}.v1`) but no formal payload-schema document exists. Consumers (current: cross-project wms-platform inventory-side may subscribe in v2; future: scm settlement-service / notification-service) have no canonical reference for envelope or per-event payload shape.

After this task: every procurement publish topic has a formal contract entry with envelope + payload schema + consumer guidance.

---

# Scope

## In Scope

- Create `projects/scm-platform/specs/contracts/events/scm-procurement-events.md`.
- Document the common envelope shape used by all `scm.procurement.*` topics — derived from `BaseEventPublisher.writeEvent` (libs/java-messaging) output.
- Per-topic sections for each of the 7 topics:
  - `scm.procurement.po.submitted.v1`
  - `scm.procurement.po.acknowledged.v1`
  - `scm.procurement.po.confirmed.v1`
  - `scm.procurement.po.canceled.v1`
  - `scm.procurement.po.received.v1`
  - `scm.procurement.po.closed.v1`
  - `scm.procurement.asn.received.v1`
- Each section: trigger condition, payload field table (name, type, nullable, description), example JSON, consumer guidance (idempotency key, ordering expectation, retention).
- Cross-reference to `procurement-service/architecture.md` § PO State Machine for trigger semantics.
- Mark `po.closed` and (where unreachable in v1) any v2-only events with a `Status` column annotation.
- Consumer-side conventions section (mirror `inventory-visibility-subscriptions.md` if it has one).

## Out of Scope

- Production code changes (events are already published correctly).
- Schema registry adoption (v2 — current pipeline uses raw JSON).
- Adding new events.
- Cross-project consumer wiring (e.g., wms-platform inventory subscribing) — that is a wms-side task.

---

# Acceptance Criteria

- [ ] `scm-procurement-events.md` exists at the contracts path.
- [ ] Common envelope section matches the literal output of `BaseEventPublisher.writeEvent` (libs/java-messaging).
- [ ] All 7 topics enumerated with trigger + payload schema + example.
- [ ] `po.closed` marked as v1-unreachable (settlement-service deferred).
- [ ] Cross-reference back to `procurement-service/architecture.md` § Outbox + audit_log invariants table.
- [ ] No production code changes.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/scm-platform/PROJECT.md`
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (§ Outbox + audit_log invariants — has the topic + event-type mapping table)
- `projects/scm-platform/specs/services/procurement-service/data-model.md` (PO + ASN schemas referenced from payload fields)
- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` (pattern reference for cross-project subscription conventions, if applicable)
- `platform/event-driven-policy.md`
- `rules/traits/transactional.md` § T2/T3 (outbox invariants)

# Related Skills

- `.claude/skills/messaging/event-contract-authoring/SKILL.md` (if exists)
- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

- N/A new (this task creates the contract). Reference existing event contracts in OTHER projects for shape/style:
  - `projects/wms-platform/specs/contracts/events/master-events.md`
  - `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` (post-TASK-BE-134 Topics-table style)

---

# Target Service

- procurement-service (spec only — events already in production)

---

# Implementation Notes

- Read `apps/procurement-service/src/main/java/com/example/scmplatform/procurement/application/event/ProcurementEventPublisher.java` for the exact `writeEvent` calls — `base(po)` builds the common payload; per-publish methods append event-specific fields.
- Read `libs/java-messaging` `BaseEventPublisher.writeEvent` for the envelope shape (eventId / eventType / occurredAt / source / payload).
- Use `ProcurementOutboxPollingScheduler.resolveTopic` switch to confirm event-type → topic mapping.
- Cross-check payload fields against the `PurchaseOrder` aggregate (id, poNumber, tenantId, supplierId, buyerAccountId, totalAmount, currency).
- ASN-received payload is asymmetric (built directly via `LinkedHashMap` in `publishAsnReceived` rather than from a domain object) — document the field set explicitly.

---

# Edge Cases

- `po.closed` topic constant exists but no v1 publish call drives it (only `settlement-service` v2 will issue SETTLED→CLOSED). Document with `Status: v2-deferred` annotation and explain the unreachable-but-declared situation.
- Some payload fields use `Instant.toString()` directly — document as ISO 8601 strings, not numeric epoch.
- Source field is a literal string `"scm-platform-procurement-service"` (not a constant — fixed in publisher).

---

# Failure Scenarios

- Discovery: a payload field name diverges from `data-model.md` (e.g., publisher uses `buyerAccountId` while data-model uses `buyer_account_id` differently). Document with a finding + rename in the contract spec to match the canonical name.

---

# Test Requirements

- N/A (spec-only). Existing publisher unit tests already verify the envelope shape.

---

# Definition of Done

- [ ] `scm-procurement-events.md` authored
- [ ] No production code changes
- [ ] Cross-references verified
- [ ] Ready for review

---

# Provenance

Surfaced from TASK-SCM-BE-006 finding #2 (2026-05-11 procurement-service architecture.md retroactive authoring, PR #331). Filed as a separate task because event-contract authoring requires payload-schema enumeration per topic — distinct scope from architecture.md authoring.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (7-topic catalogue + envelope reference; medium-effort spec authoring with code-evidence grounding).
