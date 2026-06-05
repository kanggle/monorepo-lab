# Event Contract — erp-approval-events

Kafka events published by `erp-platform/apps/approval-service` through the
**transactional outbox** (`libs/java-messaging` `BaseEventPublisher` +
`ApprovalOutboxPollingScheduler extends OutboxPollingScheduler`).

Authoritative architecture:
[`approval-service/architecture.md`](../../services/approval-service/architecture.md).
HTTP contract: [`approval-api.md`](../http/approval-api.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md)
§ Internal Event Catalog + E3 (상태기계) / E4 (멱등 + 불변 감사) / E8.

## First-increment publication decision

This increment **PUBLISHES** the four approval-transition events. Rationale:

- The skeleton `build.gradle` already wires
  `implementation project(':libs:java-messaging')` (TASK-MONO-119) — the
  transactional outbox is available at zero additional cost, same as
  `masterdata-service` ([`erp-masterdata-events.md`](erp-masterdata-events.md)).
- Publishing now establishes the **forward interface** so the v2 consumers
  (`notification-service`, integrated `read-model-service` full-view) wire
  against a stable contract rather than a retrofit — identical strategy to
  masterdata v1 publishing for the read-model.
- Per `rules/domains/erp.md` § Internal Event Catalog the recommended prefix is
  `<prefix>.approval.{submitted,approved,rejected,withdrawn,delegated}`. This
  increment publishes the **four** finalized/transition facts that exist in the
  single-stage state machine — `submitted` / `approved` / `rejected` /
  `withdrawn`. `delegated` is **not** emitted (delegation is v2 — see
  [`approval-api.md`](../http/approval-api.md) § v2 deferred). The catalog's
  prefix (`erp`) and intent are preserved verbatim.

**Consumers in this increment = none.** `approval-service` is a producer-only
leaf in this increment. Forward consumers:
- `notification-service` **v2** — fan-out approval-state notifications (상신/
  승인/반려 통지).
- `read-model-service` **v2** (integrated full-view) — projects approval state
  into the cross-domain read views (read-only, E5).

This contract is the forward interface for those v2 consumers.

> **v2.0 AMENDMENT (TASK-ERP-BE-012 — multi-stage routing + `IN_REVIEW`;
> additive + backward-compatible, consumers UNCHANGED).** The approval state
> machine now supports 1~N stages (architecture.md § v2.0 amendment). Effect on
> this event contract — **deliberately minimal to keep the v2 consumers
> (`notification-service` TASK-ERP-BE-011, `read-model-service` TASK-ERP-BE-010)
> working without change**:
> - **Same four topics, no new topic.** `erp.approval.delegated.v1` stays
>   v2.1-deferred (delegation).
> - **`erp.approval.approved.v1` fires ONLY on the FINAL-stage approval**
>   (`* → APPROVED`, terminal). An **intermediate-stage approval** (a non-final
>   stage approving, advancing the request to `IN_REVIEW`) writes its immutable
>   audit row but **emits NO outbox event** — there is no bus signal for a stage
>   advance in v2.0 (the next stage's approver is surfaced by the inbox, not an
>   event; event-driven stage-advance fan-out is v2.1). This **preserves the
>   terminal-once contract** (§ Consumer rules): a request still emits `submitted`
>   then exactly one terminal event (`approved` XOR `rejected` XOR `withdrawn`).
> - **Additive payload fields** on every approval payload: **`currentStage`**
>   (0-based index of the stage the event pertains to) + **`totalStages`** (the
>   route length). These are additive; existing consumers deserialize with
>   unknown-property tolerance (Spring Boot `FAIL_ON_UNKNOWN_PROPERTIES=false`) and
>   ignore them → **no consumer change required**. `submitted` carries
>   `currentStage = 0` (stage 0 pending); `approved` carries the final stage index.
> - **`approverId`** in a payload = the **relevant stage's** approver (`submitted`
>   → stage 0's; `approved` → the final stage's). For a 1-stage (legacy /
>   single-approver) request this is the single approver — fully backward-compatible.
>
> The single-stage (N=1) request is the strict subset: `submitted` then one
> terminal event, `currentStage`/`totalStages` = `0`/`1`. Consumers that never read
> the new fields behave identically to the v1.0 contract.

> **v2.1 AMENDMENT (TASK-ERP-BE-013 — 대결/위임 delegation; additive, consumers
> UNCHANGED).** Delegation (architecture.md § v2.1 amendment) adds:
> - **A NEW topic `erp.approval.delegated.v1`** (`rules/domains/erp.md` L101
>   catalog-named), emitted when a `DelegationGrant` is **created**.
>   `aggregateType = "DelegationGrant"`, `aggregateId = <grantId>`, partition key =
>   `grantId`. Payload:
>   ```json
>   { "grantId": "dgr-...", "delegatorId": "emp-A-...", "delegateId": "emp-D-...",
>     "validFrom": "<ISO-8601 UTC>", "validTo": "<ISO-8601 UTC; ABSENT = open-ended>",
>     "reason": "<≤512; ABSENT when none>", "tenantId": "erp",
>     "occurredAt": "<ISO-8601 UTC>", "actor": "<JWT sub of the grant creator>" }
>   ```
>   This was a **producer-only forward interface** at v2.1 — the four existing
>   transition topics are **unchanged**. **TASK-ERP-BE-014 (2026-06-06) added the
>   first consumer**: `notification-service` now subscribes to
>   `erp.approval.delegated.v1` and notifies the **delegate** ("you have been
>   delegated" — [`notification-subscriptions.md`](notification-subscriptions.md)
>   § Consumed topic — delegation). `read-model-service` still does **not**
>   subscribe (a delegation projection is a separate later increment). The producer
>   side and the four transition topics are unchanged by BE-014 (consumer-only
>   addition). Grant **revoke** is audited only — **no** event in v2.1.
> - **Additive `actingForApproverId`** on the transition payloads
>   (`approved` / `rejected`) — present (= the stage approver A) when a **delegate**
>   performed the transition on A's behalf; **ABSENT** (NON_NULL) when the approver
>   acted themselves. Existing consumers ignore the unknown field → **UNCHANGED**.
>   `submitted` / `withdrawn` are unaffected (submit and withdraw are never delegated).
>
> Consumers that read none of the v2.0/v2.1 additive fields and ignore the new
> topic behave **identically to the v1.0 contract**.

> **v2.2 AMENDMENT (TASK-ERP-BE-015 — delegation REVOKE event; additive, the four
> transition topics + the `delegated` topic UNCHANGED).** The v2.1 "grant revoke
> is audited only — no event" rule is **superseded**: a delegation grant **revoke**
> now emits a NEW topic so the read model can reflect the cancellation (a
> create-only projection cannot see a manual revoke).
> - **A NEW topic `erp.approval.delegation.revoked.v1`**, emitted when a
>   `DelegationGrant` is **revoked** (an actual ACTIVE→REVOKED transition only — an
>   idempotent re-revoke of an already-REVOKED grant does **not** re-emit).
>   `aggregateType = "DelegationGrant"`, `aggregateId = <grantId>`, partition key =
>   `grantId` (same aggregate/partition as `erp.approval.delegated.v1`, so a
>   consumer sees grant + revoke on one partition → per-grant ordering). The event
>   is written inside the same `@Transactional` outbox boundary as the revoke audit
>   row (atomic; A7). Payload:
>   ```json
>   { "grantId": "dgr-...", "delegatorId": "emp-A-...", "delegateId": "emp-D-...",
>     "reason": "<≤512; ABSENT when none>", "tenantId": "erp",
>     "occurredAt": "<ISO-8601 UTC>", "actor": "<JWT sub of the revoker>" }
>   ```
>   The validity window (`validFrom`/`validTo`) is **NOT** in the revoke payload (a
>   revoke does not restate the window — the read model keeps what the `delegated`
>   event projected). `reason` follows `@JsonInclude(NON_NULL)` (ABSENT when none).
> - **The four transition topics (`submitted`/`approved`/`rejected`/`withdrawn`)
>   and `erp.approval.delegated.v1` are byte-unchanged.** The grant-create path,
>   the SoD/resolver, and the transition payloads (incl. `actingForApproverId`) are
>   not touched.
> - **Consumer**: `read-model-service` (TASK-ERP-BE-015) subscribes to BOTH
>   `erp.approval.delegated.v1` (grant) and `erp.approval.delegation.revoked.v1`
>   (revoke) → projects a `delegation_fact_proj` (ACTIVE/REVOKED), closing the
>   delegation read-model loop ([`read-model-subscriptions.md`](read-model-subscriptions.md)
>   § Delegation). **`notification-service` also consumes the revoke topic
>   (TASK-ERP-BE-016)** → notifies the delegate "위임 권한 회수됨"
>   ([`notification-subscriptions.md`](notification-subscriptions.md) § Consumed
>   topic — delegation, row 2). Both consumers are additive; the producer side is
>   unchanged.
>
> Consumers that ignore the new topic behave identically to the v2.1 contract.

---

## Envelope (libs/java-messaging standard)

Identical envelope schema to
[`erp-masterdata-events.md`](erp-masterdata-events.md) § Envelope — do not
invent a new shape.

- **Producer source**: `"erp-platform-approval-service"` on every envelope.
- **Topic convention**: `<eventType>` + `.v1`. Breaking change → `.v2`,
  dual-publish during the deprecation window (platform convention).
- **Delivery**: at-least-once (outbox poll). Consumers MUST dedupe on
  `eventId` (`processed_events` pattern). Producer `acks=all`,
  `enable.idempotence=true`.
- **Ordering**: partition key = `aggregateId` (= `approvalRequestId`) →
  **per-request ordering** (e.g. `submitted` then `approved` for request-A
  arrive in publish order). Cross-request ordering is **not** guaranteed
  (consumers treat each request's stream independently).
- **No PII beyond identifiers**: payloads carry the approval request's
  identifiers + the subject/approver/submitter ids + the (operator-entered)
  reason text. No employee names or contact data are placed on the bus — the
  `subjectId` / `approverId` / `submitterId` are opaque master ids; a consumer
  that needs display names resolves them via the masterdata read API (E5
  read-only boundary). If a future revision adds richer payload fields, this
  contract MUST be revisited (E8 audit responsibility).

```json
{
  "eventId": "<uuid>",
  "eventType": "erp.approval.submitted",
  "occurredAt": "<ISO-8601 UTC>",
  "tenantId": "erp",
  "source": "erp-platform-approval-service",
  "aggregateType": "ApprovalRequest",
  "aggregateId": "<approvalRequestId>",
  "traceId": "<w3c-traceparent trace-id>",
  "payload": { ... }
}
```

`aggregateType` is `"ApprovalRequest"` on **every** approval event.

---

## Topics (first increment)

| eventType constant | Kafka topic | Emitted when (state transition) |
|---|---|---|
| `EVENT_APPROVAL_SUBMITTED` | `erp.approval.submitted.v1` | `DRAFT → SUBMITTED` (route + subject validated) |
| `EVENT_APPROVAL_APPROVED` | `erp.approval.approved.v1` | `SUBMITTED → APPROVED` (terminal) |
| `EVENT_APPROVAL_REJECTED` | `erp.approval.rejected.v1` | `SUBMITTED → REJECTED` (terminal, reason required) |
| `EVENT_APPROVAL_WITHDRAWN` | `erp.approval.withdrawn.v1` | `SUBMITTED → WITHDRAWN` (terminal, reason required) |

> `erp.approval.delegated.v1` (catalog recommendation) is **not** published in
> this increment — delegation is v2 (single-stage route has no delegate). The
> `DRAFT` create itself emits **no** event (a draft is not yet a workflow fact;
> the first published fact is `submitted`).

---

## Payload schemas (first increment)

All four payloads share the common header fields below; `finalizedAt` and
`reason` follow the `@JsonInclude(NON_NULL)` absent-field convention (ABSENT
when not applicable, never `null`).

Common to every approval payload:
```json
{ "approvalRequestId": "appr-...",
  "subjectType": "DEPARTMENT|EMPLOYEE",
  "subjectId": "dept-... | emp-...",
  "approverId": "emp-approver-...",
  "submitterId": "emp-submitter-...",
  "tenantId": "erp",
  "occurredAt": "<ISO-8601 UTC>",
  "actor": "<JWT sub of the transition actor>" }
```

`erp.approval.submitted` payload — the common header only (no `finalizedAt`,
no `reason`):
```json
{ "approvalRequestId": "appr-...",
  "subjectType": "DEPARTMENT", "subjectId": "dept-...",
  "approverId": "emp-approver-...", "submitterId": "emp-submitter-...",
  "tenantId": "erp", "occurredAt": "<ISO-8601 UTC>",
  "actor": "emp-submitter-..." }
```

`erp.approval.approved` payload — header + `finalizedAt`; `reason` present only
if the approver supplied one (optional on approve, so usually ABSENT):
```json
{ "approvalRequestId": "appr-...",
  "subjectType": "DEPARTMENT", "subjectId": "dept-...",
  "approverId": "emp-approver-...", "submitterId": "emp-submitter-...",
  "tenantId": "erp", "occurredAt": "<ISO-8601 UTC>",
  "actor": "emp-approver-...",
  "finalizedAt": "<ISO-8601 UTC>",
  "reason": "<ABSENT when approver gave none>" }
```

`erp.approval.rejected` payload — header + `finalizedAt` + **required** `reason`
(E4 — 반려 시 사유 필수, so always present):
```json
{ "approvalRequestId": "appr-...",
  "subjectType": "DEPARTMENT", "subjectId": "dept-...",
  "approverId": "emp-approver-...", "submitterId": "emp-submitter-...",
  "tenantId": "erp", "occurredAt": "<ISO-8601 UTC>",
  "actor": "emp-approver-...",
  "finalizedAt": "<ISO-8601 UTC>",
  "reason": "예산 근거 부족" }
```

`erp.approval.withdrawn` payload — header + `finalizedAt` + **required**
`reason`; `actor` == `submitterId` (only the submitter withdraws):
```json
{ "approvalRequestId": "appr-...",
  "subjectType": "DEPARTMENT", "subjectId": "dept-...",
  "approverId": "emp-approver-...", "submitterId": "emp-submitter-...",
  "tenantId": "erp", "occurredAt": "<ISO-8601 UTC>",
  "actor": "emp-submitter-...",
  "finalizedAt": "<ISO-8601 UTC>",
  "reason": "기안 내용 수정 필요" }
```

`finalizedAt` is ABSENT on `submitted` (the request is not terminal) and
present on `approved` / `rejected` / `withdrawn` (all terminal). `reason` is
ABSENT on `submitted`, ABSENT-or-present on `approved` (optional), and always
present on `rejected` / `withdrawn`.

---

## Outbox atomicity (E3 / E4 — transactional)

Every transition is committed as a **single database transaction** that
atomically writes all three of:
1. the **state change** (`approval_request.status` + `finalized_at` / `submitted_at`),
2. the **immutable audit row** (append-only `history` / `audit_log`: actor +
   timestamp + transition + before/after + reason — E4/E8), and
3. the **outbox row** (the event envelope above).

The `ApprovalOutboxPollingScheduler` polls and publishes committed outbox rows
to Kafka after the transaction commits. There is **no** publish path that
bypasses the transaction — the event, the state change, and the audit record
either all commit or none do (no silent write-loss). This is the
`rules/traits/transactional.md` T2/T3 + `libs/java-messaging` standard
(TASK-MONO-049 `BaseEventPublisher`), identical to
[`erp-masterdata-events.md`](erp-masterdata-events.md) § Relationship to
platform / rules.

Transition idempotency (E4) is enforced on the **HTTP** side via the
`Idempotency-Key` table inside the same transaction
([`approval-api.md`](../http/approval-api.md)) — an idempotent replay does
**not** append a second audit row nor emit a second event (the original
outbox row was already produced on the first call).

---

## Consumer rules (forward — for the v2 consumers)

- **Dedupe on `eventId`** (at-least-once delivery). Per-`approvalRequestId`
  ordering only — a consumer must tolerate `submitted` and its terminal event
  for the **same** request arriving in order, but events for **different**
  requests in any interleaving.
- A request emits **at most one terminal** event (`approved` XOR `rejected`
  XOR `withdrawn`) after its `submitted` — the state machine is terminal-once
  (E3). A consumer that observes a terminal event may treat the request as
  finalized and ignore any later transition event for that `aggregateId` as a
  duplicate / out-of-contract.
- Consumers MUST NOT reconstruct approval business logic from the stream (E5
  responsibility boundary) — they project / notify only. The authoritative
  approval state + history is owned by `approval-service`; the REST detail
  endpoint (`GET /api/erp/approval/requests/{id}`) is the source of record for
  the full `history`.
- Display-name / master enrichment: events carry only ids. A consumer needing
  department/employee names resolves them via the masterdata read API
  (`asOf = event.occurredAt`) — events do not carry resolved master fields to
  avoid contract bloat (same rule as `erp-masterdata-events.md`).

---

## Relationship to platform / rules

- Envelope + outbox + dedupe = `rules/traits/transactional.md` T2 / T3 +
  `libs/java-messaging` standard (TASK-MONO-049 `BaseEventPublisher`).
- Event-catalog naming follows `rules/domains/erp.md` § Internal Event Catalog
  (`<prefix>.approval.{submitted,approved,rejected,withdrawn,...}`), prefix =
  `erp`. `delegated` is forward-declared in the catalog but owned by v2
  (delegation) — not emitted in this increment.
- No event carries regulated PII (`rules/traits/audit-heavy.md` — the full
  before/after audit detail lives in the `audit_log` table, not on the bus).
- This increment does NOT emit `erp.permission.*` / `erp.readmodel.*` events —
  those remain forward-declared in `rules/domains/erp.md` § Internal Event
  Catalog and are owned by other v2 services (see `PROJECT.md` Service Map v2).
