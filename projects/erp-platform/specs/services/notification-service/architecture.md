# notification-service — Architecture

This document declares the internal architecture of
`erp-platform/apps/notification-service`. All implementation tasks targeting this
service must follow this declaration, `platform/architecture-decision-rule.md`,
and the rule files indexed by `PROJECT.md`'s declared `domain` (`erp`) and
`traits` (`internal-system`, `transactional`, `audit-heavy`).

> **Provenance**: `notification-service` was **forward-declared as a v2 service**
> in [ADR-MONO-016](../../../../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md)
> § D3 (the v2 Service Map row "결재 상신·승인·반려, 마스터 변경, 권한 변경 알림
> fanout"; `PROJECT.md` § Service Map v2). `masterdata-service` (v1),
> `read-model-service` (first increment, TASK-ERP-BE-007), and `approval-service`
> (first increment) have since shipped and exercised the `internal-system` +
> `transactional` + `audit-heavy` stack on the erp library, and
> `approval-service` now **publishes** the four `erp.approval.*` transition events
> ([`erp-approval-events.md`](../../contracts/events/erp-approval-events.md))
> whose recorded "Consumers in this increment = none". **This spec is the FIRST
> INCREMENT (v1.0) of `notification-service`** — it realises the already-recorded
> D3 forward-declaration as a constrained increment: **consume the four
> `erp.approval.*` events → resolve recipient → render → persist an in-app
> notification + deliver it in-app**, exposing a read-only inbox. It **closes the
> `approval-service` → notification event loop** (the approval events previously
> had zero consumers), mirroring the masterdata → read-model loop and the
> read-model-service first-increment § D3 amendment pattern: an additive first
> increment of a forward-declared service introduces **no new architecture
> decision** (HARDSTOP-09 is satisfied by this `architecture.md`, authored
> **before** implementation). The full notification-service (external channels,
> masterdata/permission/delegation notifications, preferences/routing, digest)
> stays v2-deferred — see § Out-of-Scope. It does **not** reopen the ADR-016 § D3
> decision — it executes it.

> **v1.1 AMENDMENT (TASK-ERP-BE-014 — delegation-granted notification; additive,
> the four transition consumers UNCHANGED).** `approval-service` v2.1
> (TASK-ERP-BE-013) added a producer-only topic `erp.approval.delegated.v1`
> ([`erp-approval-events.md`](../../contracts/events/erp-approval-events.md)
> § v2.1 amendment) that had **zero consumers**. This increment adds the **fifth
> consumer**: consume `erp.approval.delegated.v1` → resolve the **delegate**
> (`payload.delegateId`) → render a "결재 권한 위임됨" notification → persist + deliver
> in-app, **closing the approval → notification delegation leg**. It is a **parallel
> additive path** — the delegation event has a different aggregate + payload shape
> (`aggregateType = DelegationGrant`, no `approverId`/`submitterId`), so it adds a
> `DelegationEvent` render record + a `NotifyOnDelegationCommand` + a
> `RecipientResolver`/`NotificationFactory` overload + a `SourceRef.DELEGATION`
> source type + `NotificationType.DELEGATION_GRANTED` + an `ApprovalDelegatedConsumer`,
> while the four transition consumers + `ApprovalEvent` + the existing
> `NotifyOnApprovalCommand` path stay **byte-unchanged**. `NotificationType` /
> `SourceRef.SourceType` are `@Enumerated(STRING)` (VARCHAR(32)), so the columns need
> no type change — **but** the V1 `ck_notification_type` / `ck_notification_source_type`
> CHECK constraints pin the allowed value set, so **V2 extends both allow-lists**
> (`DELEGATION_GRANTED` / `DELEGATION`). The Docker-free `:check` slice does not
> exercise the DB CHECK, so this was caught only by the Testcontainers IT. Grant
> **revoke** still emits no event (audit only), so there
> is no revoke notification. This realises the § Out-of-Scope "Delegation
> notifications" row as an additive increment — **no new ADR** (a forward-declared
> topic's Nth consumer; this amendment is authored before implementation,
> HARDSTOP-09 satisfied). The `read-model-service` delegation projection remains a
> separate later increment.

> **v1.2 AMENDMENT (TASK-ERP-BE-016 — delegation-revoked notification; additive,
> the five existing consumers UNCHANGED).** Once TASK-ERP-BE-015 added the
> producer event `erp.approval.delegation.revoked.v1` (emitted on an ACTIVE→REVOKED
> transition), this increment adds the **sixth consumer**: consume it → resolve the
> **delegate** (`payload.delegateId`, who LOSES the authority) → render a "위임 권한
> 회수됨" notification → persist + deliver in-app. The revoke payload has **no
> validity window** (`grantId` / `delegatorId` / `delegateId` / `reason?`), so it
> uses a separate render model `DelegationRevokedEvent` + a
> `NotifyOnDelegationRevokedCommand` + a `RecipientResolver`/`NotificationFactory`
> overload + `NotificationType.DELEGATION_REVOKED` + an
> `ApprovalDelegationRevokedConsumer`, while the four transition consumers + the
> delegated consumer + `ApprovalEvent`/`DelegationEvent` stay **byte-unchanged**.
> `NotificationType` is `@Enumerated(STRING)` so **V3 extends the
> `ck_notification_type` CHECK allow-list** for `DELEGATION_REVOKED` (the ERP-BE-014
> lesson applied up-front; `source_type DELEGATION` already allowed by V2). This is
> the revoke symmetry of the v1.1 grant notification — **no new ADR**.

> **v2.0 AMENDMENT (TASK-ERP-BE-020 — external channel + exercised Category C
> `DeliveryRetryScheduler`; additive, the 6 consumers + IN_APP path UNCHANGED).**
> This increment **exercises** the v2 external-channel delivery that this spec
> reserved as deferred (§ Out-of-Scope "External channels", Failure Mode 7, the
> Category C table). It **executes** the already-recorded ADR-MONO-005 Category C
> escalation contract + the ADR-MONO-016 § D3 forward-declaration — **no new
> ADR-level decision** (authored before implementation, HARDSTOP-09 satisfied;
> mirrors the v1.1/v1.2 amendments). It introduces:
> 1. A **real `SlackWebhookChannelAdapter`** (`infrastructure/channel`,
>    `@ConditionalOnProperty(...external.mode=slack)`) — POSTs a rendered message to
>    a configured Slack webhook via a `ResilienceClientFactory` RestClient,
>    **best-effort / never-throw** (green-wash discipline: reports
>    `DeliveryOutcome.ofDelivered()` **only** on a 2xx, else a non-delivered outcome
>    carrying the error). `NoopExternalChannelAdapter` becomes the
>    `@ConditionalOnProperty(mode=noop, matchIfMissing=true)` default (exactly one
>    `SLACK` `NotificationChannelPort` per mode).
> 2. A **`DeliveryRetryScheduler`** (`@ConditionalOnProperty(...retry.enabled)` +
>    `@Scheduled(fixedDelay)`) → `RetryDeliveryService` (find due
>    `status=PENDING ∧ scheduled_retry_at ≤ now` ids) → `DeliveryAttemptProcessor`
>    (`@Transactional` **per delivery**: load → deliver (best-effort) →
>    `markDelivered` / `markRetryable(backoff)` → save). Backoff is exponential
>    `initial·2^(n-1)` capped, **±20% jitter** (`RetryBackoffPolicy`), terminal
>    `FAILED` + `DELIVERY_RETRY_EXHAUSTED` at **cap 5** — the existing
>    `NotificationDelivery` Category C machine, now exercised.
> 3. **Gated external-delivery creation**: when
>    `erpplatform.notification.external.enabled=true`, `NotifyOnApprovalEventUseCase.dispatch`
>    **additionally** persists one PENDING `SLACK` delivery (`scheduledRetryAt=now`,
>    immediately due) alongside the unchanged IN_APP DELIVERED row, in the **same**
>    consume transaction (A7). External I/O is **never** in the consume transaction —
>    the scheduler performs all webhook calls in its own per-delivery transaction
>    (the async split keeps a slow/failed webhook from rolling back the in-app
>    notification).
>
> **Default OFF = production net-zero**: `external.enabled` defaults `false`, so
> dispatch creates only the IN_APP delivery exactly as v1 (the 6 consumers, the
> dedupe, the inbox, and the IN_APP synchronous-DELIVERED path are byte-unchanged);
> the scheduler bean is absent; the no-op SLACK adapter is the (unused) default.
> **No schema migration** — the V1 schema already reserves `SLACK`/`SMTP` + the
> Category C columns (`status`/`attempt_count`/`scheduled_retry_at`/`version`) + the
> CHECK allow-lists. **Concurrency**: the single-instance `fixedDelay` scheduler is
> non-reentrant; the persisted `version` (T5) column remains the seam for a future
> multi-instance optimistic-lock (`@Version`/conditional-update) or ShedLock
> enforcement — **not wired in this increment** (documented follow-on). **SMTP**,
> notification preferences/routing, and the console bell UI stay deferred.

---

## Identity

| Field | Value |
|---|---|
| Service Name | `notification-service` |
| Project | `erp-platform` |
| Service Type | `event-consumer` (primary) + `rest-api` (in-app inbox read) — dual-type, see Service Type Composition |
| Architecture Style | **Hexagonal** (Ports & Adapters) |
| Domain | erp |
| Traits | internal-system, transactional, audit-heavy (project-level; this service exercises `transactional` T8 event idempotency + `internal-system` I1/I2/I6 boundaries + `audit-heavy` A2/A3/A7 dispatch traceability. It is **not** an approval/master state-owning write surface — see § Scope discipline) |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Audit / Operations — notification fan-out (`rules/domains/erp.md` § Bounded Contexts: "결재 상신/승인/반려 … 알림"). **Notification logic only** (recipient resolution + message rendering); **no domain business logic** (no approval/master state machine), and **no authoritative-fact re-emission** (E5-adjacent boundary, see § Scope discipline) |
| Deployable unit | `apps/notification-service/` |
| Data store | MySQL `erp_db` (same instance as `masterdata-service` / `approval-service`, **separate tables** `notification` / `notification_delivery` / `processed_events`; no shared tables, no cross-service JOIN) |
| Event publication | **None** — notification-service is a terminal consumer. It runs **no transactional outbox** and publishes **no** `erp.notification.*` topic (`rules/domains/erp.md` § Internal Event Catalog has **no** `erp.notification.*` entry — notification is a CONSUMER, not a producer). `OutboxAutoConfiguration` is **excluded**, see § Outbox + audit_log invariants |
| Event consumption | Kafka topics from `approval-service` (same project): the 4 transition topics `erp.approval.{submitted,approved,rejected,withdrawn}.v1` + `erp.approval.delegated.v1` (TASK-ERP-BE-014 — delegation-granted) + `erp.approval.delegation.revoked.v1` (TASK-ERP-BE-016 — delegation-revoked); both delegation topics `aggregateType = DelegationGrant`; `processed_events` dedupe, T8; consumer group `erp-notification-v1` |

### Service Type Composition

`notification-service` combines two service types in one deployable unit (the same
documented exception as `read-model-service` / scm `inventory-visibility-service`):

- `event-consumer` (**primary**) for asynchronous **inbound** subscription to the
  four approval-transition topics — the core workload (every notification
  originates from an approval event, never from a REST mutation):
  - `erp.approval.submitted.v1` → notify the **approver** ("결재 요청 도착")
  - `erp.approval.approved.v1`  → notify the **submitter** (승인 통지)
  - `erp.approval.rejected.v1`  → notify the **submitter** (반려 통지 + reason)
  - `erp.approval.withdrawn.v1` → notify the **approver** (회수 통지 + reason — the pending approver is told it was withdrawn)
  - `erp.approval.delegated.v1` → notify the **delegate** ("결재 권한 위임됨" — TASK-ERP-BE-014; parallel path, different aggregate/payload shape)
  - `erp.approval.delegation.revoked.v1` → notify the **delegate** ("위임 권한 회수됨" — TASK-ERP-BE-016; parallel path, revoke payload has no validity window)
- `rest-api` for synchronous **read-only** in-app inbox queries + a single
  idempotent mark-read mutation (the recipient reads / clears their own inbox).
  There is **no notification-creating REST** — notifications are created **only**
  by the event consumer (the inbox surface never writes a new notification).

Both surfaces share the same domain core (`Notification` +
`NotificationDelivery` + `EventDedupeRecord`) and persistence. Read **both**
[`platform/service-types/rest-api.md`](../../../../../platform/service-types/rest-api.md)
and [`platform/service-types/event-consumer.md`](../../../../../platform/service-types/event-consumer.md)
when implementing — documented exception to the "read exactly one service-type
file" rule, justified by the consumer-with-read-surface role (read-model-service /
inventory-visibility-service precedent). This **differs from `approval-service`**
(which is `rest-api` only because it *publishes* events as a side effect of REST
mutations); notification-service's **primary** input is the inbound topic, so it
**is** an `event-consumer`.

---

## Responsibilities

`notification-service` owns the v1.0 **approval-notification fan-out** for
erp-platform — the Audit/Operations bounded context's first notification
realisation. It MUST:

- **Consume** the four `erp.approval.{submitted,approved,rejected,withdrawn}.v1`
  events (`approval-service`,
  [`notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md))
  with `@RetryableTopic` (3 retries + DLT) + **manual ACK** + a `processed_events`
  dedupe keyed on the envelope `eventId` (T8 — duplicate `eventId` → skip without
  mutation). An invalid envelope (null `eventId` / null `payload`) routes
  **immediately to `<topic>.DLT`** without retry (cannot key the dedupe table) —
  mirrors the read-model-service consumer resilience exactly.
- **Resolve the recipient + render the message** (notification logic — this is
  what makes the service NOT a pure read-model): per the § Recipient resolution
  mapping, `submitted` notifies the `approverId`; `approved` / `rejected` notify
  the `submitterId`; `withdrawn` notifies the `approverId`. The recipient is an **employee id**. The
  title/body are rendered from the event payload (ids + reason). reject/withdraw
  bodies include the operator-supplied `reason`.
- **Persist + deliver in-app**: persist one `Notification` row (recipient,
  type, title, body, `sourceType = APPROVAL`, `sourceId = approvalRequestId`,
  `read` flag, `createdAt`) **and** one `NotificationDelivery` row carrying the
  **Category C structure** (ADR-MONO-005 § D5: `status`, `attempt_count`,
  `scheduled_retry_at`). In this first increment the channel is **IN_APP**:
  delivery **is** the persist itself, so the delivery transitions
  `PENDING → DELIVERED` synchronously with `attempt_count = 1`, in the **same
  transaction** as the `Notification` insert + the `processed_events` dedupe
  write (T2 single-aggregate atomic boundary, A7 atomicity).
- **Serve a read-only in-app inbox** (`rest-api`): the current recipient
  (employee id = JWT `sub`) lists their own notifications and marks one read
  (idempotent set). A caller sees / marks **only their own** notifications
  (recipient == caller; § Security inbox scoping).
- **Record dispatch + read traceability** (E8 / I6 + A2/A3/A7): every
  notification dispatch and inbox-read is operationally traceable via a
  lightweight append-only record (§ Dispatch traceability) — internal-system I6
  "최소한의 구조화 기록".
- Validate IAM RS256 JWT (OAuth2 Resource Server) on the inbox surface and
  fail-closed on `tenant_id ∉ {erp, *}` ∧ `entitled_domains ∌ erp`
  (entitlement-trust dual-accept, § Multi-tenancy, mirrors masterdata /
  read-model / approval). Reject external traffic at the network boundary
  (E7 / I2 — `EXTERNAL_TRAFFIC_REJECTED`).

### Scope discipline (the E5-adjacent boundary — read precisely)

notification-service is **not** an E5 read-model: it holds **notification logic**
(recipient resolution + message rendering), which a read-model does not. **But**
it also does **not** hold **domain business logic** and does **not** re-emit
authoritative facts:

- **No domain state machine.** It owns no approval state machine and no master
  state machine. The `NotificationDelivery` state machine (§ Delivery model) is a
  *delivery-lifecycle* machine (Category C), **not** a domain-fact machine — it
  governs "did this notification reach the recipient", never "is the approval
  approved". The authoritative approval state + history is owned by
  `approval-service` (`GET /api/erp/approval/requests/{id}` is the source of
  record); this service never reconstructs approval business logic from the
  stream (the `erp-approval-events.md` § Consumer rules forbid it).
- **No authoritative-fact re-emission (no outbox, no `erp.notification.*`).** The
  service publishes **no** event. `rules/domains/erp.md` § Internal Event Catalog
  lists `erp.masterdata.*` / `erp.approval.*` / `erp.permission.*` /
  `erp.readmodel.*` but **no** `erp.notification.*` — notification is a terminal
  consumer leaf. There is no state-of-record change to relay, so the
  transactional outbox (`OutboxAutoConfiguration`) is **excluded** (§ Outbox +
  audit_log invariants).
- **Ids-only display, name enrichment optional (deferred).** Events carry only
  ids (`approverId` / `submitterId` / `subjectId` are opaque master ids —
  `erp-approval-events.md` § Envelope). The first increment stores the recipient
  **id** + a rendered template **without** display-name resolution (the body
  references ids / the subject). Display-name enrichment via the masterdata read
  API is **optional, read-time, v2-deferred** (E5 read-only boundary; never
  fabricated when absent).

It MUST NOT:

- Re-emit or publish **any** event (no outbox, no `erp.notification.*` — § Internal
  Event Catalog has no such topic).
- Own or mutate **approval / master / permission** state (those live in
  `approval-service` / `masterdata-service` / v2 `permission-service`).
- Reconstruct approval **business logic** from the event stream (E5 boundary;
  `erp-approval-events.md` § Consumer rules) — it notifies only.
- Expose a notification-**creating** REST endpoint — notifications are created
  only by consuming an approval event.
- Couple to messaging / HTTP-client / vendor SDKs in `domain/` or `application/`
  — must stay behind `infrastructure/` ports.
- Expose any public, self-signup, or anonymous endpoint surface (E7 / I2) —
  `/actuator/{health,info}` is the only unauthenticated path; `/actuator/prometheus`
  is network-isolated.

---

## Architecture Style Rationale

**Hexagonal (Ports & Adapters)** chosen because:

1. **Multiple inbound adapters coexist naturally** — the four Kafka consumers
   (`event-consumer`) and the inbox REST controller (`rest-api`) share the same
   domain core without coupling (read-model-service precedent).
2. **Recipient resolution + message rendering is framework-free + unit-testable**
   — `NotificationFactory.from(approvalEvent)` (the submitted→approver /
   terminal→submitter mapping + title/body rendering) is pure Java, so the
   recipient-mapping and rendering invariants are provable by fast unit tests.
3. **The delivery channel is a swappable outbound port** — a
   `NotificationChannelPort` abstracts delivery; v1.0 ships an **IN_APP** adapter
   (the persist itself) + a **no-op/stub external adapter** placeholder, and the
   v2 external channel (Slack/SMTP) + its full Category C retry scheduler wire
   against the same port without touching the domain (wms notification-service
   `ChannelPort` precedent).
4. **Testability** — domain unit (recipient mapping + rendering + the
   `NotificationDelivery` state machine, no Spring) + application unit (mock ports
   + STRICT_STUBS) + `@WebMvcTest` inbox slice + Testcontainers MySQL + Kafka
   integration (**H2 forbidden**).

Aligns with `platform/architecture-decision-rule.md` and the
read-model-service / masterdata-service / approval-service erp canonical-form
precedent (ADR-MONO-012).

---

## Layer Structure

Hexagonal variant — `presentation/` is the inbound web adapter (inbox),
`infrastructure/` aggregates outbound adapters + config. Root package
`com.example.erp.notification` (mirrors the
`com.example.erp.{masterdata,approval}` convention).

```
com.example.erp.notification/
├── NotificationServiceApplication.java     ← @SpringBootApplication (excludes OutboxAutoConfiguration)
├── domain/                                 ← pure Java, no framework
│   ├── notification/
│   │   ├── Notification.java               ← aggregate (recipient, type, title, body, source, read, createdAt)
│   │   ├── NotificationId.java
│   │   ├── NotificationType.java           ← enum: APPROVAL_SUBMITTED, APPROVED, REJECTED, WITHDRAWN, DELEGATION_GRANTED, DELEGATION_REVOKED
│   │   ├── SourceRef.java                  ← VO (sourceType=APPROVAL|DELEGATION + sourceId=approvalRequestId|grantId)
│   │   └── repository/NotificationRepository.java   ← outbound port
│   ├── delivery/
│   │   ├── NotificationDelivery.java       ← delivery-lifecycle aggregate (Category C; pure)
│   │   ├── DeliveryStatus.java             ← enum: PENDING, DELIVERED, FAILED (terminal: DELIVERED|FAILED)
│   │   └── repository/NotificationDeliveryRepository.java
│   ├── recipient/
│   │   ├── Recipient.java                  ← employee-id VO
│   │   └── RecipientResolver.java          ← submitted→approver / terminal→submitter (pure; § Recipient resolution)
│   ├── render/
│   │   ├── ApprovalEvent.java              ← pure approval transition (4 transition topics)
│   │   ├── DelegationEvent.java            ← pure delegation-granted event (TASK-ERP-BE-014)
│   │   └── NotificationFactory.java        ← approvalEvent|delegationEvent → Notification (title/body render; pure, overloaded)
│   ├── dedupe/EventDedupeRecord.java       ← processed_events VO
│   └── error/                              ← domain exceptions (erp codes)
├── application/                            ← use cases + outbound ports
│   ├── NotifyOnApprovalEventUseCase.java   ← @Transactional consume boundary (resolve+render+persist+deliver+dedupe)
│   ├── QueryInboxUseCase.java              ← read-only inbox list (current recipient)
│   ├── MarkNotificationReadUseCase.java    ← idempotent mark-read (recipient-scoped)
│   └── port/outbound/
│       ├── NotificationChannelPort.java    ← delivery boundary (IN_APP adapter v1; external stub v1/v2)
│       └── ClockPort.java
├── infrastructure/                         ← outbound adapters + config
│   ├── persistence/jpa/                    ← Spring Data + adapter beans (notification, notification_delivery, processed_events)
│   ├── channel/
│   │   ├── InAppChannelAdapter.java        ← v1: delivery = persist → DELIVERED (attempt_count=1)
│   │   └── NoopExternalChannelAdapter.java ← v1 stub; v2 = Slack/SMTP + DeliveryRetryScheduler (Category C)
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   ├── ServiceLevelOAuth2Config.java
│   │   ├── AllowedIssuersValidator.java
│   │   ├── TenantClaimValidator.java       ← decode-time entitlement-trust dual-accept
│   │   └── ActorContextResolver.java       ← JWT sub → recipient (employee id)
│   ├── messaging/                          ← @KafkaListener consumers (4 transition + 1 delegated) + @RetryableTopic + manual ACK
│   └── config/ (KafkaConsumerConfig, JpaConfig, ClockConfig)
└── presentation/                           ← inbound web adapter (inbox, read-only + mark-read)
    ├── controller/NotificationInboxController.java  ← /api/erp/notifications/**
    ├── advice/GlobalExceptionHandler.java
    ├── dto/
    ├── filter/TenantClaimEnforcer.java     ← service-level fail-closed
    └── security/PublicPaths.java
```

### Allowed dependencies

- `spring-boot-starter-{web,data-jpa,validation,actuator,security,oauth2-resource-server}`.
- `org.springframework.kafka:spring-kafka` (consumer; transitive through
  `libs:java-messaging`). **`OutboxAutoConfiguration` is excluded** — no publish
  side (§ Outbox + audit_log invariants).
- `org.flywaydb:flyway-core`, `flyway-mysql`, `com.mysql:mysql-connector-j` (runtime).
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`,
  `io.opentelemetry:opentelemetry-exporter-otlp`.
- `com.fasterxml.jackson.{core:jackson-databind, datatype:jackson-datatype-jsr310}`.
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`
  (consumer + dedupe scaffolding only — outbox excluded), `libs:java-observability`,
  `libs:java-security`.

### Forbidden dependencies

- Messaging / HTTP-client / vendor SDKs in `domain/` or `application/` — must be
  behind `infrastructure/` ports (the `NotificationChannelPort` is the only
  outbound delivery boundary).
- The transactional **outbox** (`OutboxAutoConfiguration` /
  `OutboxPollingScheduler`) — explicitly **not** wired (terminal consumer; no
  `erp.notification.*` topic exists — `rules/domains/erp.md` § Internal Event
  Catalog). This mirrors the `feedback_spring_boot_diagnostic_patterns` § 13
  "no-outbox consumer = explicit OutboxAutoConfiguration exclude" lesson (lib↔spec
  `processed_events` table-name reuse).
- Direct cross-tenant repository methods that omit `tenant_id` — every repository
  signature carries `tenant_id` (defense-in-depth; mirrors masterdata / approval /
  read-model).
- Direct write paths into `approval-service` / `masterdata-service` tables or any
  DB-level read of their schema (E5) — events are the only inbound; the inbox is
  the only outbound REST.

### Boundary rules

- `domain/` MUST NOT depend on Spring (JPA annotations on entities are the single
  allowed exception; `RecipientResolver`, `NotificationFactory`, and the
  `NotificationDelivery` state machine are pure).
- `application/NotifyOnApprovalEventUseCase` is the **only** `@Transactional`
  consume boundary; the inbox use cases are read / idempotent-set. Controllers
  MUST NOT carry `@Transactional`.
- The consume use case MUST write the `Notification`, the `NotificationDelivery`
  (`PENDING → DELIVERED` for IN_APP), AND the `processed_events` dedupe row in the
  **same** `@Transactional` boundary (T2 / A7 atomicity — no "delivered but not
  deduped" and no "deduped but not delivered").
- The inbox controller MUST scope every query / mark-read to the caller's own
  recipient id (recipient == JWT `sub`); cross-recipient access is structurally
  impossible (§ Security inbox scoping).

---

## Recipient resolution (the notification logic — read precisely)

This is the logic that makes notification-service NOT a pure read-model: it maps
each approval event to **exactly one recipient employee id** and renders a
message. `RecipientResolver` is a pure module.

| Consumed event | `NotificationType` | Recipient = | Title (rendered) | Body includes |
|---|---|---|---|---|
| `erp.approval.submitted.v1` | `APPROVAL_SUBMITTED` | `payload.approverId` | "결재 요청 도착" | `approvalRequestId`, `subjectType`/`subjectId`, `submitterId` |
| `erp.approval.approved.v1` | `APPROVED` | `payload.submitterId` | "결재 승인됨" | `approvalRequestId`, `approverId`, `finalizedAt`; `reason` if present |
| `erp.approval.rejected.v1` | `REJECTED` | `payload.submitterId` | "결재 반려됨" | `approvalRequestId`, `approverId`, `finalizedAt`, **`reason` (required)** |
| `erp.approval.withdrawn.v1` | `WITHDRAWN` | `payload.approverId` | "결재 회수됨" | `approvalRequestId`, `submitterId`, `finalizedAt`, **`reason` (required)** |
| `erp.approval.delegated.v1` | `DELEGATION_GRANTED` | `payload.delegateId` | "결재 권한 위임됨" | `delegatorId`, `validFrom`, `validTo` (ABSENT → "무기한"), `reason` if present (TASK-ERP-BE-014) |
| `erp.approval.delegation.revoked.v1` | `DELEGATION_REVOKED` | `payload.delegateId` | "위임 권한 회수됨" | `delegatorId`, `reason` if present (no validity window — TASK-ERP-BE-016) |

Rules:

- **delegated → delegate** (TASK-ERP-BE-014): the employee who **received** the
  delegation authority (`payload.delegateId`) is notified they may now act on the
  delegator's behalf. This is a **parallel path** — the delegation event carries a
  different aggregate + payload (`aggregateType = DelegationGrant`, partition key =
  `grantId`, NO `approverId`/`submitterId`/`subjectId`), mapped by a `DelegationEvent`
  render record + a `RecipientResolver.resolve(DelegationEvent)` / `NotificationFactory.from(DelegationEvent)`
  overload; the four transition rows above and `ApprovalEvent` are byte-unchanged.
  `sourceType = DELEGATION`, `sourceId = grantId`. `delegateId` null/blank → DLT.

- **submitted → approver** (the request just arrived in the approver's queue);
  **approved / rejected → submitter** (the requester is told the outcome);
  **withdrawn → approver** (the submitter withdrew their *own* request, so they
  already know — the approver who had it **pending** is the one to be told it is
  no longer awaiting their action). The approver and submitter ids are both
  present on every payload (`erp-approval-events.md` § Payload schemas), so
  resolution is a pure pick — no outbound call needed.
- The `reason` field is **present** on `rejected` / `withdrawn` (required by E4)
  and **absent-or-present** on `approved` (`@JsonInclude(NON_NULL)` — ABSENT, never
  `null`); the renderer includes it only when present.
- Recipient ids are opaque **employee ids** — the body references ids + the
  subject (no display-name resolution in v1; § Scope discipline). If a future
  increment adds name enrichment it is a **read-time** masterdata lookup,
  eventually-consistent, never fabricated when absent (E5).
- The mapping is total over the four consumed topics; an unrecognized
  `eventType` (out-of-contract) routes to DLT (§ Failure Modes), never silently
  dropped.

---

## Delivery model (Category C state machine + IN_APP-first split)

`NotificationDelivery` is the delivery-lifecycle aggregate carrying the
**ADR-MONO-005 Category C** structure (`status` + `attempt_count` +
`scheduled_retry_at`). It is **not** a domain-fact machine — it governs delivery,
not approval state (§ Scope discipline).

### State machine

```
PENDING ─[deliver() IN_APP succeeds]──────────────────→ DELIVERED ★   (attempt_count=1, synchronous)
PENDING ─[deliver() external transient fail, attempt < max]→ PENDING  (scheduled_retry_at set)   ← v2 path
PENDING ─[deliver() external permanent fail OR attempt == max]→ FAILED ★                          ← v2 path
```

★ terminal (`DELIVERED` / `FAILED`) — immutable; any further transition →
`DELIVERY_STATE_TRANSITION_INVALID`. Mirrors the wms notification-service
`NotificationDelivery` shape (PENDING → SUCCEEDED/FAILED), renamed
`SUCCEEDED → DELIVERED` for the in-app increment.

### First-increment channel = IN_APP (the only exercised path)

In v1.0 the channel is **IN_APP**: "delivery" is the in-app notification persist
itself. The `InAppChannelAdapter` succeeds as soon as the `Notification` row
commits, so the delivery is created **already `DELIVERED`** with
`attempt_count = 1`, in the same transaction as the `Notification` + dedupe write
— there is no asynchronous retry loop on the v1 path (an in-app notification
cannot transiently fail the way an external vendor call can). The Category C
**structure** is present (the columns + the state enum) so the v2 external path
slots in without a schema migration.

### Category C declaration (Saga / Long-running Flow — ADR-MONO-005)

notification-service owns no aggregate state machine and makes no multi-step
distributed flow → **no Category A, no Category B**. One ADR-MONO-005 category
applies (notification-service is the canonical **Category C** reference — single-step
retry-with-DLT, ADR-MONO-005 § 1.1 row 6 / § 2.5 D5 / § 6):

| Flow | Category | Resilience config | Fail behavior | Metrics | Status |
|---|---|---|---|---|---|
| approval event → in-app notification delivery | **C** (single-step, persistent retry budget + DLT terminal) | **v1 (IN_APP)**: synchronous persist, `attempt_count=1`, no retry loop (in-app cannot transiently fail). **Inbound consume**: manual ACK; `@RetryableTopic` 3 retries (exponential backoff) → `<topic>.DLT`; invalid envelope → immediate DLT. **v2 (external channel)**: `DeliveryRetryScheduler` poll + exponential backoff `±20%` jitter, **cap 5** → terminal `FAILED` + `DELIVERY_RETRY_EXHAUSTED` | duplicate `eventId` skipped via `processed_events`; retry exhaustion (consume side) → `<topic>.DLT` (no silent discard); v2 external exhaustion → terminal `FAILED` + structured `outcome` | `notification_event_dedupe_skipped_total`, `notification_event_dlt_total{topic}`, `notification_consumer_lag{topic}`, `notification_dispatched_total{type}`, `notification_delivery_status_total{status}` | v1 (IN_APP) Target; v2 external = deferred |

The Category C escalation contract (cap 5 + structured terminal `outcome`,
ADR-MONO-005 § 2.5 D5) is satisfied **structurally** by the
`attempt_count`/`scheduled_retry_at`/terminal-`FAILED` columns now; the
**exercised** retry scheduler is the v2 external-channel path. No saga row,
no compensation (single-step).

---

## REST endpoints (v1.0 — in-app inbox, read-only + mark-read)

All under `/api/erp/notifications/**`. The current recipient's employee id is the
JWT `sub`. Every business endpoint requires a JWT satisfying the entitlement-trust
dual-accept gate (`tenant_id ∈ {erp, *}` ∪ signed `entitled_domains ∋ erp`,
§ Multi-tenancy) **and** the READ authorization gate (`erp.read` scope ∨
`isOperator()` ∨ entitled — mirrors the masterdata / read-model READ gate so the
platform-console operator token reads the inbox too). A caller only ever
sees / marks **their own** notifications (recipient == `sub`). Formal request /
response shapes live in
[`notification-api.md`](../../contracts/http/notification-api.md).

| Method | Path | Public/Internal | Controller | Purpose |
|---|---|---|---|---|
| GET | `/api/erp/notifications` | internal | `NotificationInboxController#list` | current recipient's inbox (paginated; `?unread=&page=&size=`) — recipient-scoped to JWT `sub` |
| POST | `/api/erp/notifications/{id}/read` | internal | `NotificationInboxController#markRead` | mark one notification read — **idempotent set** (already-read → no-op 200); 404 `MASTERDATA_NOT_FOUND`-analog `NOTIFICATION_NOT_FOUND` when the id is not the caller's own |

There is **no** notification-creating REST endpoint (notifications are created
only by the event consumer). `mark-read` is an idempotent `read = true` set, so it
does **not** require an `Idempotency-Key` (T1 applies to non-idempotent mutations;
a set-to-true is naturally idempotent).

### Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe |
| `GET /actuator/info` | none | build info |
| `GET /actuator/prometheus` | network-isolated | metrics scrape (internal docker network only) |

---

## event-consumer

- Consumer group: `erp-notification-v1`
- 4 transition topics from `approval-service` (same project):
  `erp.approval.{submitted,approved,rejected,withdrawn}.v1`
  + `erp.approval.delegation.revoked.v1` (TASK-ERP-BE-016 — delegation-revoked; parallel path, recipient = `delegateId`)
  + `erp.approval.delegated.v1` (TASK-ERP-BE-014 — delegation-granted; parallel
  mapper path, `aggregateType = DelegationGrant`, recipient = `delegateId`).
- Manual ACK mode.
- Retry: `@RetryableTopic` 3 attempts (exponential backoff) + DLT (`<topic>.DLT`).
- Idempotency: `processed_events` table keyed on envelope `eventId` (T8).
- Invalid envelope (null `eventId` / null `payload`) → immediate `<topic>.DLT`,
  no retry (cannot key the dedupe table).
- Per-`approvalRequestId` ordering (partition key, guaranteed by the producer
  contract — `erp-approval-events.md` § Envelope) — `submitted` then the terminal
  event for one request arrive in order; cross-request interleaving tolerated.
  Terminal-once: a consumer that observed a terminal event may ignore a later
  transition for the same `aggregateId` as a duplicate (`erp-approval-events.md`
  § Consumer rules).

---

## Outbox + audit_log invariants

### Transactional outbox

**N/A — notification-service is a terminal consumer.** It runs **no**
transactional outbox and publishes **no** events. `rules/domains/erp.md`
§ Internal Event Catalog has **no** `erp.notification.*` topic — notification is a
consumer leaf, never an authoritative-fact producer (§ Scope discipline). The
`libs/java-messaging` `OutboxAutoConfiguration` is **explicitly excluded**
(`@SpringBootApplication(exclude = OutboxAutoConfiguration.class)` or the
equivalent `spring.autoconfigure.exclude`) — wiring it would create an unused
`outbox` table (and the lib↔spec `processed_events` table-name reuse hazard,
`feedback_spring_boot_diagnostic_patterns` § 13). Only the **consumer** + dedupe
side of `libs:java-messaging` is used.

### Dispatch + read traceability (E8 / I6 + A2 / A3 / A7)

erp E8 / internal-system I6 require operational traceability of "who did what
operational action". notification-service has **no operator-driven domain
mutation** (notifications are created by events, read by their own recipient), so
the heavy approval/master `audit_log` (E2/E4) does **not** apply here. The
first-increment traceability is **lightweight + structured** (I6 "최소한의 구조화
기록"):

- Every **dispatch** (an approval event → a created notification + delivery) is
  recorded with the standard A2 minimal shape: `event_id` (the source envelope
  `eventId`) / `occurred_at` (UTC ISO-8601, server clock — A6) / `actor`
  (`{type: system, id: notification-service}` — the dispatch is system-driven,
  not operator-driven) / `action` (`notification.dispatched`) / `target`
  (`{type: notification, id}`) / `outcome` (DELIVERED | FAILED). The
  `processed_events` row + the `NotificationDelivery` row together are the durable
  provenance (every dispatched notification is traceable to its source envelope +
  its delivery outcome).
- Every **inbox read / mark-read** is a recipient self-action (not a privileged
  operator action), so it is captured by the standard observability log (MDC
  `userId` = `sub`), **not** a separate immutable audit store — the meta-audit
  (A5) burden applies to **operator** access to **others'** audit data, which this
  increment has no surface for (the inbox is self-scoped, no `GET /audit`).
- **Append-only (A3)**: the dispatch-trace + `processed_events` rows are written
  via `save(...)`/`append(...)` only — no UPDATE/DELETE path is exposed by any
  adapter (application-layer guard, masterdata / approval precedent). The
  `Notification.read` flag flip is the **one** mutable field (an in-app read
  receipt is a UI state, not an audit fact) — it is not part of the immutable
  dispatch trace.
- **Atomicity (A7)**: the dispatch trace (`processed_events` +
  `NotificationDelivery` outcome) commits in the **same** transaction as the
  `Notification` insert — no "notification created without a recorded dispatch".

This service is **not** an `audit-heavy` heavyweight write surface (the project
trait's `audit_log` applies to masterdata / approval); the conservative
first-increment posture is the lightweight structured dispatch trace above.

---

## Idempotency (T8)

notification-service has **no non-idempotent mutating REST endpoint** (the only
inbox mutation, `mark-read`, is a naturally-idempotent set-to-true), so the
`Idempotency-Key` header pattern (T1) does **not** apply. Idempotency lives
entirely on the **event-consumer** side (T8):

- **Dedupe store**: `processed_events` keyed on the envelope `eventId`. A
  duplicate `eventId` is skipped without creating a second `Notification` /
  `NotificationDelivery` — re-delivering the same approval event produces no
  duplicate inbox item.
- **Idempotent dispatch**: the consume use case is an "insert-if-not-deduped"
  per `eventId`; replaying an applied `eventId` is a no-op.
- **Ordering**: producer partitions by `approvalRequestId` → per-request
  publish-order delivery; the single consumer group serialises per request.

Invalid envelopes (null `eventId` / `payload`) bypass dedupe → DLT.

---

## Multi-tenancy

**N/A as SaaS row-level isolation — single-tenant by project classification.**
`erp-platform` does not declare `multi-tenant` (`PROJECT.md` § Out of Scope; IAM
is the multi-tenant IdP). All notifications belong to the `erp` tenant.

The domain claim is still **fail-closed enforced** on the inbox surface via
**entitlement-trust dual-accept** (ADR-MONO-019 § D5, single-tenant gate,
defense-in-depth — identical to masterdata / read-model / approval). A token is
accepted when **either** `tenant_id ∈ {erp, *}` (`*` = SUPER_ADMIN platform-scope)
**or** the IAM-signed `entitled_domains ∋ erp`; rejection (403 `TENANT_FORBIDDEN`)
requires **both** branches to fail (fail-closed; entitlement only *widens* the
READ set). `entitled_domains` is read only from an RS256/JWKS-verified token
(unforgeable — IAM is the entitlement authority). The decode validator and the
`TenantClaimEnforcer` filter are **independent gates, both dual-accept**. While
IAM has not populated `entitled_domains` the claim is absent → only the legacy
path applies → **production net-zero** (ADR-MONO-019 dual-accept window).

Config keys (mirrors masterdata / approval `application.yml`):
`erpplatform.oauth2.allowed-issuers` + `.required-tenant-id=erp`. Every
persistence table carries `tenant_id VARCHAR(64) NOT NULL DEFAULT 'erp'`;
repository methods always embed `tenant_id` in `WHERE`.

The Kafka consumer trusts the producer's `tenantId = erp` envelope field
(same-project internal topic) and persists it on each row; a non-`erp` envelope
(out-of-contract) routes to DLT.

---

## Security

- **JWT (RS256)** on the inbox surface: `oauth2-resource-server` against
  `${OIDC_ISSUER_URL:http://iam.local}/oauth2/jwks`; RS256 only;
  `JwtTimestampValidator` + `AllowedIssuersValidator` + `TenantClaimValidator`
  (decode-time entitlement-trust dual-accept). The console assume-tenant operator
  token + the employee SSO token are the v1.0 inbox callers (E7 / I1 — SSO single
  auth, no self-credential store).
- **READ authorization gate** (`RoleScopeAuthorizationAdapter`-equivalent): READ =
  `erp.read` scope ∨ `isOperator()` ∨ entitled. The mark-read set is a self-scoped
  READ-adjacent write (the recipient clears their own receipt); it requires the
  same READ gate + recipient-ownership, not a separate WRITE scope (there is no
  org-wide notification mutation).
- **Inbox scoping (E6 fail-closed)** — every inbox query and mark-read is filtered
  to `recipient_id == JWT.sub`. A caller **cannot** read or mark another
  employee's notifications; an id that resolves to a notification not owned by the
  caller returns 404 `NOTIFICATION_NOT_FOUND` (not 403 — non-ownership is
  indistinguishable from non-existence, avoiding an enumeration oracle). This is
  the E6 data-scope applied at the recipient grain (per-person, the narrowest
  scope) — no broader `org_scope` read in this increment (an inbox is inherently
  self-scoped).
- **External-traffic rejection (E7 / I2)** — `EXTERNAL_TRAFFIC_REJECTED` at two
  layers: (1) Docker Compose `erp.local` Traefik label on an internal network
  (shared Traefik accepts only internal-LAN / platform-console traffic); (2) the
  `PublicPaths` filter rejects any non-actuator path without a valid JWT
  (`UNAUTHORIZED`; `EXTERNAL_TRAFFIC_REJECTED` reserved for a future debug-path
  bypass surface).
- **Public paths**: `/actuator/{health,info}` only; `/actuator/prometheus`
  network-isolated. **No self-signup, no anonymous endpoints** (E7 / I2). No
  webhook surface — internal-only.

---

## Dependencies

| Dir | Target | Protocol | Notes |
|---|---|---|---|
| In | erp `gateway-service` (v1 deferred) → direct JWT until then | HTTP `/api/erp/notifications/**` | tenant-validated JWT (entitlement-trust dual-accept) |
| In | erp `approval-service` Kafka | Consumer subscribed to `erp.approval.{submitted,approved,rejected,withdrawn}.v1` | `processed_events` (T8) idempotent; closes the approval → notification loop |
| Out | MySQL `erp_db` | JDBC | `notification` / `notification_delivery` / `processed_events` (separate tables; no shared-table JOIN with approval / masterdata) |
| Out | IAM `/oauth2/jwks` | HTTPS | RS256 JWT verification (libs/java-security) |
| Out (obs) | OTLP collector | HTTPS | `${OTLP_ENDPOINT}` traces |
| Out (v2) | external channel (Slack/SMTP) | HTTPS/SMTP | **deferred** — `NoopExternalChannelAdapter` stub in v1; v2 wires the real adapter + Category C retry scheduler behind the same `NotificationChannelPort` |

No event publication; no outbox; no write-back to `approval-service` /
`masterdata-service` (terminal consumer; § Scope discipline). The masterdata
read (optional display-name enrichment) is **not** wired in v1.

---

## Data Model

Denormalized notification store. Two tables + the dedupe table.

```
notification(id PK, tenant_id, recipient_id, type, title, body,
             source_type, source_id, read, created_at, read_at)
notification_delivery(id PK, tenant_id, notification_id FK, event_id,
             channel, status, attempt_count, scheduled_retry_at,
             last_error, version, created_at, updated_at)
processed_events(event_id VARCHAR PK, topic, aggregate_id, processed_at)
```

- `type` ∈ {`APPROVAL_SUBMITTED`, `APPROVED`, `REJECTED`, `WITHDRAWN`,
  `DELEGATION_GRANTED`, `DELEGATION_REVOKED`} (`@Enumerated(STRING)`, length 32 —
  V2 extends the `ck_notification_type` CHECK allow-list for `DELEGATION_GRANTED`,
  V3 for `DELEGATION_REVOKED`).
- `source_type` ∈ {`APPROVAL` (→ `source_id = approvalRequestId`), `DELEGATION`
  (→ `source_id = grantId`, TASK-ERP-BE-014)} — opaque back-reference; the
  authoritative state is in `approval-service`.
- `read` is the **one** mutable field (the in-app read receipt); `read_at` set on
  the first mark-read, idempotent thereafter.
- `channel` = `IN_APP` in v1 (the enum reserves `SLACK` / `SMTP` for v2).
- `notification_delivery` carries the Category C columns
  (`status`/`attempt_count`/`scheduled_retry_at`/`version`) even though the v1
  IN_APP path commits straight to `DELIVERED` with `attempt_count = 1`.
- `version` (T5) on `notification_delivery` guards the v2 retry-scheduler /
  concurrent-tick contention (no concurrent writer on the v1 IN_APP path).

A dedicated `data-model.md` is a low-priority follow-up if the model grows
(masterdata / read-model inline-until-it-grows precedent).

---

## Observability

- Logback MDC `traceId / requestId / tenantId (= erp) / userId` (libs/java-observability).
- Custom Micrometer metrics:
  - **Event consumer**: `notification_event_dedupe_skipped_total`,
    `notification_event_dlt_total{topic}`, `notification_consumer_lag{topic}`.
  - **Dispatch / delivery**: `notification_dispatched_total{type}`,
    `notification_delivery_status_total{status}` (Category C delivery-outcome
    signal — DELIVERED / FAILED).
  - **Inbox**: `notification_inbox_read_total`, `notification_mark_read_total`.
- Tracing: OTLP via `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`;
  dev sampling 100%. The consumed approval envelope's `traceId` is propagated so
  the approval transition → notification dispatch is one continuous trace
  (federation observability parity, MONO-144 chain).
- Prometheus scrape on `/actuator/prometheus`, **internal docker network only**.

---

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Duplicate approval `eventId` | skipped, no duplicate notification (`processed_events`, T8) |
| 2 | Invalid envelope (null `eventId` / `payload`) | immediate `<topic>.DLT`, no retry |
| 3 | Unrecognized `eventType` (out-of-contract topic body) | routed to `<topic>.DLT` (never silently dropped) |
| 4 | Transient consume processing error | `@RetryableTopic` 3 retries (exponential) → `<topic>.DLT` on exhaustion |
| 5 | Non-`erp` envelope `tenantId` (out-of-contract) | routed to DLT (single-tenant invariant) |
| 6 | IN_APP delivery (v1) | persist commits → `DELIVERED`, `attempt_count=1`, same Tx as `Notification` + dedupe (no retry loop) |
| 7 | External delivery transient fail (**v2**) | `markRetryable` → `PENDING` + `scheduled_retry_at`; `DeliveryRetryScheduler` re-attempts; cap 5 → `FAILED` + `DELIVERY_RETRY_EXHAUSTED` |
| 8 | Cross-tenant JWT on inbox — `tenant_id ∉ {erp, *}` **and** `entitled_domains ∌ erp` | 403 `TENANT_FORBIDDEN` |
| 9 | Missing JWT / invalid signature / expired | 401 `UNAUTHORIZED` |
| 10 | Caller lacks read authorization (no `erp.read`, not operator, not entitled) | 403 `PERMISSION_DENIED` |
| 11 | Inbox list / mark-read of another recipient's notification | scoped out — list returns only own rows; mark-read of a non-owned id → 404 `NOTIFICATION_NOT_FOUND` (no enumeration oracle) |
| 12 | mark-read of an already-read notification | idempotent no-op 200 (`read` stays true, `read_at` unchanged) |
| 13 | External (non-internal-network) traffic at ingress | rejected at Traefik / network layer (`EXTERNAL_TRAFFIC_REJECTED` on a surfaced debug path) |
| 14 | Dispatch-trace / `processed_events` append fails | whole consume Tx fails (A7 atomicity) — event NOT acked, redelivered; no "notification without recorded dispatch" |
| 15 | Delegation event (granted or revoked) with null/blank `delegateId` (TASK-ERP-BE-014/016) | invalid envelope → immediate `<topic>.DLT`, no retry (no recipient to deliver to) |
| 16 | Delegation `validTo` absent (open-ended grant) | body renders "무기한" (NON_NULL absent, not an error) |

---

## Testing Strategy

- **Unit** (`:notification-service:test`):
  - domain — `RecipientResolverTest` (the four-event submitted→approver /
    terminal→submitter mapping table **+ delegated→delegate**, TASK-ERP-BE-014);
    `NotificationFactoryTest` (title/body render incl. reason on reject/withdraw,
    reason-absent on approved **+ delegation: delegator/validFrom/validTo-무기한/reason-absent**);
    `NotificationDeliveryStateMachineTest` (PENDING → DELIVERED / FAILED, terminal
    immutability, `attempt_count`/`scheduled_retry_at` invariants);
    `EventDedupeRecord`.
  - application — `NotifyOnApprovalEventUseCaseTest`
    (`@ExtendWith(MockitoExtension.class)` STRICT_STUBS): one happy per event type
    + dedupe-skip (duplicate `eventId` → no second notification);
    `QueryInboxUseCaseTest` (recipient-scoped); `MarkNotificationReadUseCaseTest`
    (idempotent set; non-owned id → not-found).
  - adapters — `TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`,
    `TenantClaimEnforcerTest`, the Kafka envelope mapper.
- **Slice**: JPA adapter slices; `@WebMvcTest` + SecurityConfig +
  `GlobalExceptionHandler` error-envelope; inbox controller slice asserting
  recipient scoping (caller sees only own; cross-recipient → 404).
- **Integration** (`:notification-service:integrationTest`, `@Tag("integration")`,
  **Testcontainers MySQL + Kafka** + WireMock/MockWebServer JWKS — **H2
  forbidden**):
  - Publish each of the 4 `erp.approval.*` events → assert exactly one
    `Notification` created with the **correct recipient** (submitted→approver;
    approved/rejected→submitter; withdrawn→approver) + the rendered title/body (reason
    present on reject/withdraw) + a `NotificationDelivery` row `DELIVERED`
    `attempt_count=1` (IN_APP).
  - **Dedupe**: same `eventId` delivered twice → exactly **one** notification
    (idempotent T8).
  - **Recipient mapping**: a `submitted` for approver-A and an `approved` for
    submitter-B land in the correct inboxes (cross-check the mapping table).
  - Poison envelope → DLT; transient error → 3-retry then DLT; non-`erp`
    envelope → DLT.
  - **Inbox scoping**: recipient-A's JWT lists only A's notifications; A marking
    B's notification id → 404 `NOTIFICATION_NOT_FOUND`; mark-read idempotent
    (twice → one `read_at`).
  - Cross-tenant JWT → 403 `TENANT_FORBIDDEN`; entitled cross-tenant
    (`entitled_domains ∋ erp`) → 2xx (dual-accept); no read scope → 403
    `PERMISSION_DENIED`; no token → 401.

`integrationTest` is excluded from `./gradlew check` (Docker-free fast loop —
masterdata / read-model / approval convention). The monorepo "Integration
(erp-platform, Testcontainers)" CI job (TASK-ERP-BE-004 established) runs it on
Linux runners; local Windows Docker availability is host-dependent (honest gap —
project memory `project_testcontainers_docker_desktop_blocker`).

---

## Mandatory Rule mapping (rules/domains/erp.md)

| Rule | Status | Mechanism |
|---|---|---|
| **E1** Master single source of record + reference integrity | N/A (consumer side) | This service holds no master; it stores `source_id = approvalRequestId` as an opaque back-reference, never a master fact. No reference-integrity ownership. |
| **E2** Effective-dated master change + immutable audit | N/A | No master change surface. |
| **E3** Approval state machine | N/A (no logic) | The approval state machine lives in `approval-service`; this service only **notifies** on observed transitions, never reconstructs the machine (`erp-approval-events.md` § Consumer rules). The `NotificationDelivery` machine is a *delivery* machine, not an approval machine. |
| **E4** Approval transition idempotent + audit | `processed_events` dedupe (T8) | Transition idempotency + audit are owned by `approval-service`; this consumer only dedupes on `eventId` and dispatches at-most-once per event. |
| **E5** Integrated read model holds NO domain logic — read-only | ✅ (boundary respected, with a note) | notification-service is **not** a read-model (it has recipient-resolution + render logic), **but** it holds **no domain business logic** and **re-emits no authoritative fact** (no outbox, no `erp.notification.*` — § Internal Event Catalog). Each rendered field traces to the source event; ids are not display-name-fabricated when absent (E5 no-fabrication spirit). |
| **E6** Authorization via permission matrix + data scope — fail-closed | ✅ (read + recipient scope) | READ gate fail-closed: `erp.read` ∨ operator ∨ entitled, else `PERMISSION_DENIED`. Data-scope = recipient grain: a caller sees / marks only `recipient_id == sub` (non-owned → 404). |
| **E7** internal-system boundary — no external traffic, SSO enforced | ✅ | OAuth2 RS (IAM SSO) only on the inbox; entitlement-trust tenant gate fail-closed; actuator network-isolated; external traffic rejected at edge; no anonymous/self-signup. |
| **E8** Permission/org change audited | Partial (dispatch traceability) | No permission/org mutation surface. Notification **dispatch** is traceable via `processed_events` + the `NotificationDelivery` outcome + the lightweight dispatch trace (I6 "최소한의 구조화 기록"); inbox self-reads use the standard observability log (no separate operator-audit surface in this increment). |

---

## Trait Rule mapping (rules/traits/)

| Trait Rule | Status | Mechanism |
|---|---|---|
| **internal-system I1** SSO single auth | ✅ | OAuth2 RS (IAM JWKS, RS256) on the inbox; no self-credential store; consumer trusts same-project internal topic. |
| **internal-system I2** No external exposure / network boundary | ✅ | `erp.local` internal Traefik; actuator network-isolated; defense-in-depth (auth independent of network — `PublicPaths` rejects non-actuator unauthenticated). |
| **internal-system I6** Operational traceability | ✅ (lightweight) | Dispatch trace (`processed_events` + delivery outcome, A2 shape) records "what notification was dispatched, when, with what outcome"; recording mechanism delegated to audit-heavy A2/A3/A7 (§ Dispatch traceability). |
| **transactional T1** Idempotency on mutating endpoints | N/A | The only inbox mutation (`mark-read`) is a naturally-idempotent set-to-true; idempotency is event-side (T8). |
| **transactional T8** Idempotent event consumption | ✅ | `processed_events` keyed on `eventId`; duplicate → skip; no duplicate notification. |
| **transactional T2** Atomic command boundary | ✅ | Consume use case writes `Notification` + `NotificationDelivery` + `processed_events` in one `@Transactional` (single-aggregate boundary; no cross-service Tx). |
| **transactional T3** Outbox / event publication | N/A | No published events — terminal consumer; `OutboxAutoConfiguration` excluded. |
| **transactional T4** State machine via dedicated module | ✅ (delivery only) | `NotificationDelivery` is a pure state-machine module (PENDING → DELIVERED/FAILED); no direct `status` UPDATE. It is a *delivery* machine, not a domain-fact machine. |
| **transactional T5** Optimistic locking | ✅ (v2-relevant) | `version` on `notification_delivery` guards the v2 retry-scheduler contention; v1 IN_APP has no concurrent writer. |
| **audit-heavy A2** Standard audit schema | ✅ (dispatch trace) | Dispatch trace uses the A2 minimal shape (event_id / occurred_at / actor / action / target / outcome). |
| **audit-heavy A3** Immutability | ✅ | `processed_events` + dispatch trace are append-only (no UPDATE/DELETE port); the `read` flag flip is a UI receipt, not an audit fact. |
| **audit-heavy A6** UTC clock | ✅ | `occurred_at` UTC ISO-8601, server clock. |
| **audit-heavy A7** Atomicity | ✅ | Dispatch trace commits in the same Tx as the notification insert (no "notification without recorded dispatch"). |
| **audit-heavy A4/A5** Retention / meta-audit | N/A (this increment) | No operator audit-read surface (the inbox is recipient-self-scoped); no `GET /audit`. Retention of the heavy `audit_log` lives in approval/masterdata; the lightweight dispatch trace inherits the project ≥ 1y default. |

---

## Required Artifacts mapping (rules/domains/erp.md § Required Artifacts)

| # | Artifact | Disposition |
|---|---|---|
| 5 | internal-system boundary policy | **Inlined** (§ Security + § Multi-tenancy); gateway is the dedicated artifact when activated. |
| 6 | Error-code registration | Reuses existing erp codes (`PERMISSION_DENIED`, `EXTERNAL_TRAFFIC_REJECTED`, `MASTERDATA_NOT_FOUND`-analog). New codes `NOTIFICATION_NOT_FOUND` + (v2) `DELIVERY_RETRY_EXHAUSTED` / `DELIVERY_STATE_TRANSITION_INVALID` register in `platform/error-handling.md` (the notification-api.md contract PR confirms). |
| 7 | Bounded-context map | This service realises the **Audit / Operations** bounded context's notification fan-out (erp.md § Bounded Contexts) as a separate deployable — the third context split after read-model + approval. |

Other erp Required Artifacts (#1 master model, #2 approval diagram, #3 permission
matrix, #4 integrated read model) are owned by masterdata / approval / read-model
and are unchanged by this increment.

---

## Out-of-Scope (notification-service v2 — deferred, NOT designed here)

Named as deferred per the first-increment discipline (ADR-MONO-016 § D3 +
read-model / approval precedent); these are **not** designed in depth here:

- ~~**External channels** (Slack / SMTP / push) — the `NoopExternalChannelAdapter`
  is a stub; the real adapter + the **exercised** Category C
  `DeliveryRetryScheduler` (exponential backoff ±20% jitter, cap 5, terminal
  `FAILED` + `DELIVERY_RETRY_EXHAUSTED`, ADR-MONO-005 § D5) are v2.~~ → **SLACK DONE
  (TASK-ERP-BE-020, § v2.0 amendment)**: the real `SlackWebhookChannelAdapter`
  (best-effort, property-gated) + the **exercised** Category C `DeliveryRetryScheduler`
  (exponential ±20% jitter, cap 5, terminal `FAILED` + `DELIVERY_RETRY_EXHAUSTED`) ship
  behind the same `NotificationChannelPort`, default-OFF (net-zero). **SMTP / push** stay
  deferred (the enum reserves `SMTP`); multi-instance retry concurrency (the `version`
  optimistic-lock / ShedLock enforcement) is a follow-on.
- **Masterdata-change notifications** — consuming `erp.masterdata.*.changed.v1`
  (department/employee/jobgrade/costcenter changes). v2.
- **Permission-change notifications** — consuming `erp.permission.*` (owned by v2
  `permission-service`). v2.
- ~~**Delegation notifications** — consuming `erp.approval.delegated.v1` /
  `erp.approval.delegation.revoked.v1`~~ → **DONE** — the delegate is notified on
  grant create (TASK-ERP-BE-014, § v1.1) **and** on revoke (TASK-ERP-BE-016, § v1.2,
  after ERP-BE-015 added the revoke producer event). The `read-model-service`
  delegation **projection** ("who may act for whom" query) shipped separately
  (TASK-ERP-BE-015).
- **Notification preferences / routing rules** — per-recipient opt-in/out, channel
  routing (the wms notification-service `RoutingRule` analog). v2.
- **Digest / batching** — daily digest, coalescing. v2.
- **Display-name enrichment** — read-time masterdata lookup to render employee
  names instead of ids. v2 (E5 read-only, eventually-consistent).
- **Console parity slice** — the platform-console notification-bell / inbox UI is a
  separate PC-FE task (ADR-MONO-013 § D3.1 parity discipline); notification-service
  is backend-only.

---

## Deploy dependencies (bootstrap — NOT designed here)

The notification-service bootstrap requires (mention only; designed by the
bootstrap / follow-up tasks, not this spec):

- Root `settings.gradle` include `projects:erp-platform:apps:notification-service`
  + root `package.json` shortcut.
- A CI per-service path filter for notification-service in
  `.github/workflows/ci.yml` (mirror masterdata / read-model / approval,
  **pure-positive** — MONO-074/075 negation prohibition) + the existing
  "Integration (erp-platform, Testcontainers)" job picks up
  `:notification-service:integrationTest` (Kafka container in the IT matrix).
- `docker-compose` `erp.local` Traefik routing entry with a **PathPrefix
  `/api/erp/notifications`** (it IS a `rest-api` for the inbox, so the inbox
  surface is path-routed like read-model's `/api/erp/read-model` — ADR-MONO-001
  Option C, no PORT_PREFIX) on the shared `infra/traefik/`. The Kafka consumer
  needs no Traefik route (broker-internal).
- IAM `erp-platform-internal-services-client` scope set already carries `erp.read`
  (sufficient for the inbox READ gate) — no new IAM V-slot scope required for v1.

---

## References

- `platform/architecture-decision-rule.md`, `platform/service-types/INDEX.md`,
  `platform/service-types/event-consumer.md` + `platform/service-types/rest-api.md`
  (dual-type — documented exception, see § Service Type Composition),
  `platform/error-handling.md`, `platform/testing-strategy.md`,
  `platform/hardstop-rules.md` (HARDSTOP-09/10)
- `rules/domains/erp.md` (E5 — boundary respected with the notification-logic
  note; E6 recipient-scope; E7 internal-only; E8 dispatch traceability; § Internal
  Event Catalog — **no** `erp.notification.*` topic, notification is a consumer),
  `rules/traits/internal-system.md` (I1 / I2 / I6),
  `rules/traits/transactional.md` (T2 / T4 / T5 / T8),
  `rules/traits/audit-heavy.md` (A2 / A3 / A6 / A7)
- `projects/erp-platform/PROJECT.md` (§ Service Map v2 — notification-service),
  [`iam-integration.md`](../../integration/iam-integration.md)
- [`notification-api.md`](../../contracts/http/notification-api.md) (sibling-authored,
  this bundle), [`notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md)
  (sibling-authored, this bundle),
  [`erp-approval-events.md`](../../contracts/events/erp-approval-events.md)
  (producer contract — consumed unchanged; the four `erp.approval.*` topics +
  payloads this service maps to recipients)
- precedent: `projects/erp-platform/specs/services/read-model-service/architecture.md`
  (rest-api + event-consumer dual-type, `processed_events` dedupe, no-outbox
  terminal consumer — closest analog),
  `projects/erp-platform/specs/services/approval-service/architecture.md`
  (the producer sibling + Category note + state-machine style),
  `projects/wms-platform/apps/notification-service/` (the `NotificationDelivery`
  state-machine + `ChannelPort` + Category C structural precedent — adapted to
  IN_APP-first, not Slack)
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D3 (notification-service v2
  forward-declaration — this spec executes it as a first increment, no D3 reopen),
  `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`
  (notification-service is the **Category C** reference — § 1.1 row 6 / § 2.5 D5 / § 6),
  `docs/adr/ADR-MONO-019-...` (§ D5 entitlement-trust dual-accept),
  `docs/adr/ADR-MONO-012-...` (canonical architecture.md form)
- TASK-ERP-BE-... — this spec + impl task
