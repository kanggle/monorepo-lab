# Event Contracts — iam-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: live code (outbox publisher classes, `TOPIC_*`/eventType constants, `@KafkaListener` consumers), read alongside the 7 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Consistent.** Bare dot-separated string, no service/domain prefix — the topic name equals the `eventType` literally. Code comments in multiple publishers explicitly document this as intentional: *"iam topics are bare (no `<prefix>`)"* (`AuthOutboxPublisher.java`, `SecurityOutboxPublisher.java`, `AdminOutboxPublisher.java`, `AccountOutboxPublisher.java`).

Examples: `account.created`, `auth.login.attempted`, `tenant.suspended`, `partnership.invited`.

## 2. `eventType` Naming

**Consistent.** Dot-separated lowercase, same string used as both the Kafka topic and the envelope `eventType` field: `account.locked`, `partnership.participant_added`, `tenant.updated`.

## 3. Serialization

**JSON**, via Jackson (`ObjectMapper.writeValueAsString`). Payload carried as a `JsonNode` inside the envelope. No `.avsc`/`.proto` files anywhere in the project.

## 4. Schema Registry

**Not used.** Zero hits for `schema.registry`, `SchemaRegistryClient`, Apicurio, or Confluent config anywhere. Schema versioning is handled manually via an integer `schemaVersion` field on the envelope, not a registry.

## 5. Envelope Shape (informational)

**Consistent.** `{eventId (UUIDv7), eventType, source, occurredAt, schemaVersion, partitionKey, payload}`, self-built per-service as a `LinkedHashMap`/`Map<String,Object>` — not a shared serializer class; each `Outbox*EventPublisher` reconstructs it independently (a code comment in `OutboxTenantEventPublisher.java` flags this as inherited v1-compat debt, not new design, but the actual field set is uniform across services).

Partition key convention is consistent *per family* but the underlying field differs by aggregate: `account_id` (account/auth/security/session), `target_id` (admin.action.performed), `tenantId` (tenant.*), `partnershipId` (partnership.*) — with one documented exception, `tenant.subscription.changed`, which uses `"<tenantId>:<domainKey>"` instead of the aggregate id despite living in `account-events.md`.

## 6. Contract Index

| File | Producer / Type |
|---|---|
| `account-events.md` | account-service — producer. Documents that community-service/membership-service consumers of these events were **RETIRED** (TASK-MONO-394) — a consumer-side retirement, not an event-side one; the events themselves are still live. |
| `admin-events.md` | admin-service — producer |
| `auth-events.md` | auth-service — producer, including `auth.session.revoked` |
| `partnership-events.md` | partnership-service — producer |
| `security-events.md` | security-service — producer |
| `session-events.md` | **stale/aspirational** — see Follow-up below |
| `tenant-events.md` | tenant-service — producer |

---

## Follow-up (not in scope for this README)

- **`session-events.md` describes an event that does not exist in code.** It documents a topic literally named `session.revoked` with fields `revokeReason`/`totalRevoked`. No producer publishes that literal string (0 grep hits). What actually ships is `auth.session.revoked` (`AuthOutboxPublisher.TOPIC_SESSION_REVOKED`), already documented — with different field names (`revokedJtis`, `actor.type`) — in `auth-events.md`. The two files currently describe what look like two different events for what the code treats as one. Recommend a follow-up doc-fix task to either retarget `session-events.md` to `auth.session.revoked`'s actual shape or explicitly mark it as a design note that was never implemented. Not fixed here — this README only declares conventions, per AC-2/Out of Scope.
