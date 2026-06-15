# notification-service — External Integrations

External vendor catalog for `notification-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

This document declares **every** external system `notification-service`
integrates with — direction, auth, timeouts, circuit-breaker policy, retry
policy, observability hooks. Implementation must match these declarations.
Changes here precede code changes (per `CLAUDE.md` Contract Rule).

The marquee integration in v1 is the **outbound Slack Incoming Webhooks
push** — the only real external HTTP call, subject to the full
`integration-heavy` rule set (I1–I4, I7–I9). The other vendors are
project infrastructure (Kafka, PostgreSQL) and config plumbing (Secret
Manager / env-var injection).

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| **Slack Incoming Webhooks** | outbound (push) | HTTPS POST | URL-embedded token | actual alert delivery |
| **Kafka cluster** | both | TCP / SASL | mTLS or SCRAM | event consume (6 source topics) + outbox publish (`notification.delivered.v1`) |
| **PostgreSQL** | outbound (DB) | TCP | password | NotificationDelivery audit + RoutingConfig persistence |
| **Secret Manager** | outbound (config) | env-var (v1) / HTTPS (v1 prod) | n/a / service-account | per-channel Slack webhook URL injection |

Internal services (`master-service`, `inbound-service`,
`inventory-service`, `outbound-service`) are not "external" — they live in
the same project, publish through the same Kafka cluster, and follow
internal-event contracts in `specs/contracts/`. The 6 source topics this
service consumes are catalogued in
[`../../contracts/events/notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md).

---

## 1. Slack Incoming Webhooks — Outbound Delivery (integration-heavy core)

Slack is the **sole real external channel** in v1. After a NotificationDelivery
row reaches `PENDING`, the delivery dispatcher invokes the Slack adapter
with the channel alias and a serialised `AlertEnvelope`. On 2xx the
delivery transitions to `SUCCEEDED`; on terminal failure (4xx / retry-exhaustion)
it transitions to `FAILED`.

### 1.1 Endpoint

```
POST {channel.webhook_url}
```

`{channel.webhook_url}` is a vendor-controlled URL of the form
`https://hooks.slack.com/services/T.../B.../...` — no path or hostname
contributed by us; the URL itself encodes the workspace + channel +
token. Loaded from environment variables per channel alias (§ 5 Secret
Manager).

### 1.2 Authentication

- **URL-embedded token** — no separate `Authorization` header.
- 2 channel aliases in v1:
  - `wms-alerts` ← `SLACK_WEBHOOK_URL_WMS_ALERTS`
  - `wms-shipping` ← `SLACK_WEBHOOK_URL_WMS_SHIPPING`
- TLS: hooks.slack.com certificate validated against the system trust
  store; no certificate pinning in v1.
- **URL is treated as a secret** — never logged at any level, never
  persisted to the DB, never echoed back in error messages. The adapter
  intentionally omits the response body from `WARN` / `ERROR` logs because
  Slack 4xx responses can echo the channel id, and the URL itself carries
  the token.

### 1.3 Anti-Replay (Service-Side Idempotency)

Slack incoming webhooks do **not** support vendor-side idempotency
headers. The service-side defence is the
`(eventId, channelId, recipient)` triplet documented in
[`idempotency.md § Outbound (channel)`](idempotency.md), surfaced via the
`NotificationDelivery` aggregate's natural key. A duplicate event arriving
via Kafka re-poll absorbs at the inbound dedupe table before any Slack
call is issued.

### 1.4 Failure Modes

| Scenario | Adapter outcome | Saga outcome | Observable |
|---|---|---|---|
| 2xx | `ChannelPort.send()` returns | `NotificationDelivery → SUCCEEDED` | metric `notification.delivery.attempts{channel=SLACK,status=SUCCESS}` |
| 4xx (404 channel not found / 410 token revoked / others) | `ChannelPermanentFailureException` | `NotificationDelivery → FAILED` (no retry, ops fix) | metric `..{status=FAILED_PERMANENT}` + log WARN (no body) |
| 5xx | `RuntimeException` (caught by `@Retry`) | retry per § 1.8; on exhaustion `→ FAILED` | metric `..{status=FAILED_TRANSIENT}` per attempt |
| IO error / timeout | `RuntimeException` (caught by `@Retry`) | same as 5xx | same |
| Webhook URL unset for alias | `ChannelNotConfiguredException` | `NotificationDelivery → FAILED` (fail-closed) | metric `..{status=FAILED_NOT_CONFIGURED}` + alert |
| Circuit OPEN | `CallNotPermittedException` (Resilience4j fast-fail) | re-tried on next outer-budget poll (per § 1.13) | gauge `notification.channel.circuit.state{channel=SLACK}` = 2 |

### 1.5 Adapter Layout

Per Hexagonal architecture
([`architecture.md`](architecture.md) § Architecture Style):

```
adapter/outbound/slack/
├── SlackChannelAdapter.java       // implements SlackChannelPort, R4j-wrapped
├── SlackBodyRenderer.java         // AlertEnvelope JSON → Slack {"text":...} body
└── SlackChannelProperties.java    // @ConfigurationProperties prefix=wms.notification.channels
```

`SlackChannelPort` and the internal `AlertEnvelope` record live in
`application/port/outbound/` and `domain/alert/` respectively. The domain
calls `port.send(channelAlias, payloadJson)` without knowing Slack exists
— full ports & adapters separation per `integration-heavy` I7 (vendor
adapter) and I8 (internal model translation). `SlackBodyRenderer` is
adapter-internal; the vendor body shape never leaks across the port
boundary.

### 1.6 Timeouts (I1)

| Stage | Value | Source |
|---|---|---|
| `connectTimeout` | **3 s** | `SlackChannelAdapter.CONNECT_TIMEOUT` |
| `readTimeout` | **5 s** | `SlackChannelAdapter.READ_TIMEOUT` (applied via `HttpRequest.timeout(...)`) |
| Total per-attempt budget | ~5 s | dominated by read |

Implementation: JDK `java.net.http.HttpClient` (not Spring `RestClient`).
Rationale: Slack body is trivial (`{"text":"..."}`) — no Spring HTTP
plumbing needed; virtual-thread direct compatible (Java 21 / Spring Boot
`spring.threads.virtual.enabled=true`). Sibling outbound-service uses
`RestClient` because its TMS body is rich (multi-field shipment DTO);
notification's choice is intentional, not a missed-opportunity for
unification.

### 1.7 Circuit Breaker (I2)

Resilience4j `slack` instance (declared in `application.yml`):

| Property | Value |
|---|---|
| `slidingWindowType` | `TIME_BASED` |
| `slidingWindowSize` | 10 (seconds) |
| `minimumNumberOfCalls` | 5 |
| `failureRateThreshold` | 50% |
| `waitDurationInOpenState` | **10 s** |
| `permittedNumberOfCallsInHalfOpenState` | 3 |
| `registerHealthIndicator` | true |

When OPEN: calls fail fast with `CallNotPermittedException`. The outer
delivery dispatcher catches and translates to a `RETRY_SCHEDULED`
delivery state; the next outer-budget poll picks the row up after the
back-off window (§ 1.13).

State exposed as gauge `notification.channel.circuit.state{channel=SLACK}`
(0=closed, 1=half-open, 2=open).

### 1.8 Retry (I3, Adapter-Inner)

Resilience4j `slack` retry instance:

| Property | Value |
|---|---|
| `maxAttempts` | **3** (1 initial + 2 retries) |
| `waitDuration` | 500 ms |
| `enableExponentialBackoff` | true |
| `exponentialBackoffMultiplier` | 2.0 |
| `ignoreExceptions` | `com.wms.notification.domain.error.ChannelPermanentFailureException` |

Effective delay sequence: ~500 ms → ~1 s (per `exponentialBackoffMultiplier=2.0`).

`ChannelPermanentFailureException` (4xx) bypasses retry entirely — vendor
policy I3 (4xx never retried). `ChannelNotConfiguredException` is also
permanent (fail-closed); it never reaches the retry layer because it's
thrown pre-flight before `HttpClient.send()`.

### 1.9 Idempotency Toward Slack (I4)

Slack vendor **does not support** an idempotency header. The service-side
defences are:

1. **Inbound dedupe** (`notification_event_dedupe` keyed by `eventId`) —
   a repeated event arriving via Kafka re-poll short-circuits before any
   Slack call.
2. **Delivery-row uniqueness** — `NotificationDelivery (event_id,
   channel_id, recipient)` natural-key UNIQUE constraint prevents a
   second delivery row for the same triplet from being created.
3. **Per-delivery `deliveryId`** — used as a log correlator only; Slack
   itself has no awareness of repeats.

Risk: if both the inbound dedupe row and the delivery row are bypassed
(e.g., direct manual call via test-only endpoint), Slack can receive
duplicate posts. v1 accepts this risk because there is no test-only
bypass on the production deploy.

### 1.10 Bulkhead (I9) — N/A in v1

`notification-service` v1 has a **single external vendor** (Slack). The
JDK `HttpClient` uses its default executor (a cached thread pool); no
dedicated bulkhead is required because there is no second vendor to
isolate against.

When v2 multi-vendor lands (email / SMS / push), each new vendor
adapter MUST declare its own dedicated executor (or Resilience4j
`Bulkhead`) — not share with Slack. The Slack adapter at that point
should also be retrofitted with a dedicated executor; the spec is
re-revisited in the v2 task.

### 1.11 Internal Model Translation (I7, I8)

Mapping `AlertEnvelope` (domain) → Slack body is performed in
`SlackBodyRenderer.render(objectMapper, payloadJson, channelAlias)`,
package-private inside `adapter/outbound/slack/`. The domain model
never leaks vendor-shaped fields.

v1 body shape (intentionally minimal):

```json
{ "text": "<rendered alert text>" }
```

Full `blocks` templating is **v2** — placeholders + per-event-type
template authoring is captured in
[`architecture.md`](architecture.md) § Channel Templates as a deferred
item.

### 1.12 4xx / Permanent Failures

| HTTP status | Treatment | Delivery outcome |
|---|---|---|
| 200 / 204 | Success | `SUCCEEDED` |
| 400 | Permanent. Body invalid — likely renderer bug. | `FAILED`, `failure_reason=CHANNEL_BAD_REQUEST` |
| 403 | Permanent. Token scope insufficient. | `FAILED`, alert ops |
| 404 | Permanent. Channel not found or webhook deleted. | `FAILED`, `failure_reason=CHANNEL_NOT_FOUND` |
| 410 | Permanent. Token revoked. | `FAILED`, alert ops (rotate secret) |
| 429 (rate limit) | **No retry** in v1 — treat as permanent | `FAILED`, alert ops to upgrade Slack plan |
| 500 / 502 / 503 / 504 | Retry per § 1.8 | If exhausted: outer-budget retry per § 1.13 |
| Timeout / IO | Retry per § 1.8 | Same |

Note on 429: Slack Incoming Webhooks have a documented rate limit (~1
message/sec per channel). v1 alert volume is well under this in dev/stg
and observed under in prod; if a 429 ever fires, it's a misconfiguration
(a noisy event source flooding one alias). Treating as permanent
+ alerting is the right operator signal.

### 1.13 Outer Retry Budget (Delivery Dispatcher Layer)

Above the Resilience4j `slack` retry sits the **delivery dispatcher**
retry budget — the row-level retry for `NotificationDelivery`:

| Property | Value |
|---|---|
| `wms.notification.delivery.max-attempts` | **5** (total dispatcher attempts) |
| `wms.notification.delivery.backoff-seconds` | **[1, 5, 30, 120, 600]** (per-attempt back-off, with ±20 % jitter per `architecture.md § Retry budget`) |
| `wms.notification.delivery.retry-poll-interval-ms` | 5000 (dispatcher poll cadence) |
| Terminal failure code | `DELIVERY_RETRY_EXHAUSTED` (422) |

Layered semantics:

1. Adapter-inner Resilience4j (§ 1.8): 3 in-process attempts spanning ~1.5 s.
2. If the in-process attempts all fail transiently, the delivery row
   stays `PENDING` (or `RETRY_SCHEDULED` when circuit was OPEN); the
   dispatcher requeues per § 1.13 backoff.
3. After 5 dispatcher attempts × outer back-off, the delivery transitions
   to terminal `FAILED` with `DELIVERY_RETRY_EXHAUSTED` and an outbox
   audit event `notification.delivered.v1 outcome=FAILED_RETRY_EXHAUSTED`
   (the audit event acts as the DLT analog per ADR-MONO-005 Category C
   single-step retry+DLT).

This two-layer design (in-process burst + dispatcher long-wait) is
intentional: in-process catches blink-fast 503s without waiting 1s; the
dispatcher catches sustained outages of minutes.

---

## 2. Kafka Cluster

### 2.1 Direction

- **Inbound (consume)**: 6 source topics (per
  [`../../contracts/events/notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md)) —
  `wms.inventory.alert.v1`, `wms.inventory.adjusted.v1`,
  `wms.inbound.inspection.completed.v1`,
  `wms.inbound.asn.cancelled.v1`,
  `wms.outbound.order.cancelled.v1`,
  `wms.outbound.shipping.confirmed.v1`.
- **Outbound (publish)**: 1 audit topic
  `wms.notification.delivered.v1` (per
  [`../../contracts/events/notification-events.md`](../../contracts/events/notification-events.md)).

### 2.2 Connection

- Bootstrap: `KAFKA_BOOTSTRAP_SERVERS` (env-driven, 3+ brokers in prod).
- Auth: SASL/SCRAM-SHA-512 in dev/staging; mTLS in prod (per
  `platform/security-rules.md`).
- Consumer group: `wms-notification-v1` (single group for all 6
  listeners, per-class container per
  [`architecture.md`](architecture.md) § Consumer Rules).

### 2.3 Producer Config (outbox audit publisher)

```yaml
spring.kafka.producer:
  acks: all
  retries: 2147483647
  properties:
    enable.idempotence: true
    max.in.flight.requests.per.connection: 5
```

Outbox is **audit-only** — even with outage of the outbox publisher, the
actual Slack delivery already happened (the audit row records what was
attempted). The publisher polls `notification_outbox` rows where
`published_at IS NULL` every `wms.notification.outbox.polling-interval-ms`
(default 500 ms) in batches of 100.

### 2.4 Consumer Config

```yaml
spring.kafka.consumer:
  auto-offset-reset: earliest
  enable-auto-commit: false
  key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
  value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.listener.ack-mode: record
```

Per-listener:
- Manual ACK after the delivery row reaches a terminal state (SUCCEEDED /
  FAILED_PERMANENT) or is scheduled for outer retry. Replay-safe because
  the dedupe table absorbs duplicates.
- DLT routing inherits sibling pattern (`<topic>.DLT`) for any
  unparseable / poison-record case.

### 2.5 Failure Modes

| Scenario | Behavior |
|---|---|
| Broker unreachable on consume | Listener pauses; auto-recovery on next rebalance |
| Broker unreachable on publish (audit) | Outbox row stays `published_at=null`; publisher retries; alerts at `pending.count > 100` |
| Consumer hits unparseable record | DLT (manual replay via runbook) |
| Consumer hits transient DB failure | Retried in-process per Spring `DefaultErrorHandler` then DLT |

### 2.6 No Distributed Transactions (T2)

- Slack call is **outside** the consumer transaction (per
  `architecture.md` § Sequence Diagram). The consumer TX commits the
  NotificationDelivery row + outbox row; the dispatcher then issues the
  Slack call asynchronously.
- A Slack 5xx during the in-TX consumer call would have rolled back the
  delivery row creation → at-least-once Kafka semantics would replay the
  event, creating yet another delivery attempt → unbounded retry storm.
  The out-of-TX dispatch is the architectural lever that bounds this.

---

## 3. PostgreSQL

### 3.1 Direction

Outbound (read + write). One logical DB per service —
`notification_db`. Owned exclusively; no other service connects.

### 3.2 Connection

- HikariCP via Spring Boot.
- `maximum-pool-size: 10`, `minimum-idle: 2`.
- Connection timeout: Spring Boot default (30s); tunable.

### 3.3 Migrations

- Flyway, baseline V1 — see
  `apps/notification-service/src/main/resources/db/migration/`.
- Naming: `V{n}__{description}.sql`.
- RoutingConfig rows are Flyway-seeded; runtime mutation absent in v1.

### 3.4 Failure Modes

| Scenario | Behavior |
|---|---|
| DB connection pool exhausted | Request blocks until timeout; then dispatcher poll skips and retries on next tick |
| DB master failover | Reconnect via DNS / pgbouncer; brief outage window. Outbox publisher resumes from where it stopped |
| Migration failure on startup | Pod fails to start (CrashLoopBackOff). Manual rollback via Flyway `clean` + `migrate` |

---

## 4. Secret Manager (v1 env-var primary)

### 4.1 Direction

Outbound (read). Used for:

- Per-channel Slack incoming webhook URLs (`SLACK_WEBHOOK_URL_WMS_ALERTS`,
  `SLACK_WEBHOOK_URL_WMS_SHIPPING`).
- Future: SMTP credentials (v2), Twilio token (v2), FCM service-account
  JSON (v2).

### 4.2 Provider

- **v1 dev / stg**: env-var injection at pod start. `application.yml`
  defaults to empty (`""`) — the adapter throws
  `ChannelNotConfiguredException` at call-time if an alias is requested
  but its URL is blank (fail-closed).
- **v1 prod**: AWS Secrets Manager (or equivalent — concrete provider
  chosen at deploy time). The deployment manifest injects the secret as
  env-var via the platform's secret-injection mechanism (no SDK call
  from this service in v1).
- Refresh cadence: **boot-only** (no hot reload). Operators rotate the
  Slack webhook URL by:
  1. Generating a new URL via Slack workspace admin.
  2. Updating the secret in Secret Manager.
  3. Triggering a rolling restart of `notification-service` pods.

### 4.3 Failure Modes

| Scenario | Behavior |
|---|---|
| Secret Manager unreachable at boot | If env-var injection failed: pod has empty webhook URLs → first alert attempt throws `ChannelNotConfiguredException` → `FAILED` + alert ops |
| Channel URL value missing (blank) | `ChannelNotConfiguredException` (fail-closed) → `NotificationDelivery → FAILED` + metric `notification.delivery.attempts{status=FAILED_NOT_CONFIGURED}` |
| Stale URL after revocation | First call returns 410 → `ChannelPermanentFailureException` → `FAILED` + alert ops to rotate |

---

## 5. Aggregated Resilience Policy

| Vendor | Timeout (connect / read) | Circuit Breaker | Retry (count, base, max) | Idempotency | Bulkhead | DLQ / Recovery |
|---|---|---|---|---|---|---|
| **Slack (out)** | **3 s / 5 s** | **50% over 10s TIME_BASED, open 10 s** | **3 in-process, 500 ms × 2.0 exp** + **outer 5 attempts × [1, 5, 30, 120, 600] s ±20% jitter** | service-side (`eventId, channelId, recipient`) | n/a v1 (single vendor) | terminal `FAILED` + outbox audit `outcome=FAILED_RETRY_EXHAUSTED` |
| Kafka producer (audit) | (broker session) | n/a | broker idempotent retries `2147483647` | `eventId` (downstream) | Spring default | outbox stays unpublished |
| Kafka consumer | (broker session) | n/a | Spring `DefaultErrorHandler` then DLT | `eventId` (inbound dedupe table) | Spring default | `<topic>.DLT` |
| PostgreSQL | (HikariCP 30s default) | n/a (failure → dispatcher retry) | 0 in-statement | n/a | HikariCP pool 10 | n/a |
| Secret Manager (boot env-var) | n/a | n/a | n/a (boot-only) | n/a | n/a | n/a |

Bulkhead (`integration-heavy` I9): not applicable in v1 — single
external vendor. v2 multi-vendor entry MUST add dedicated executors per
vendor (Slack / email / SMS / push), each isolated from Kafka producer /
consumer pools and the HTTP server's request thread pool.

---

## 6. Observability

Per `rules/traits/integration-heavy.md` § Interaction with Common Rules:

| Metric | Vendor | Description |
|---|---|---|
| `notification.delivery.attempts{channel, status}` | Slack-out | Counter of delivery attempts by channel + outcome |
| `notification.delivery.duration.seconds` | Slack-out | Histogram p50/p95/p99 of adapter `send()` duration |
| `notification.channel.circuit.state{channel}` | Slack-out | Gauge 0=closed, 1=half-open, 2=open |
| `notification.channel.send.failed{channel, reason}` | Slack-out | Counter by failure reason (`PERMANENT_4XX`, `RETRY_EXHAUSTED`, `NOT_CONFIGURED`, `CIRCUIT_OPEN`) |
| `notification.outbox.pending.count` | Kafka-out | Gauge of unpublished audit-outbox rows |
| `notification.outbox.lag.seconds` | Kafka-out | Histogram of oldest unpublished row age |
| `notification.consumer.received.total{topic, outcome}` | Kafka-in | Counter by topic + applied/duplicate/failed |
| `notification.consumer.dlt.records.total{topic}` | Kafka-in | Counter; alerts at >0 |
| `notification.event.dedupe.hit.rate` | Kafka-in | Computed metric |

Logs (structured JSON, INFO level — see
[`idempotency.md`](idempotency.md) § Observability for the precise event
keys; the per-log-line MDC carries `traceId`, `eventId`, `deliveryId`,
`channelId`, `attempt` per `application.yml` logging pattern).

Tracing (OTel): Slack call is a child span `slack.webhook.send` of the
delivery dispatcher's span, with `channel` and `attempt` attributes. The
webhook URL is **never** included in span attributes (would leak the
token).

---

## 7. Test Suite (per `integration-heavy` I10)

All external-integration paths must have failure-mode tests using fakes:

| Path | Test framework |
|---|---|
| **Slack adapter** | **WireMock** — success, 4xx (404 / 410), 5xx (500 / 503), timeout, circuit-open, blank-URL `ChannelNotConfiguredException`, retry exhaustion → terminal `FAILED` |
| Kafka consumer (6 source topics + dedupe) | Testcontainers Kafka, poison-record case routes to DLT |
| Kafka producer (audit outbox) | Testcontainers Kafka |
| PostgreSQL (delivery + routing + outbox + dedupe) | Testcontainers PostgreSQL |
| Secret Manager | Mock `SlackChannelProperties` map (env-var simulation) |

Tests for production vendor SDKs (e.g., AWS Secrets Manager client) use
LocalStack or equivalent — the service code does not reach real AWS
during test runs.

---

## 8. Per-Vendor Runbook Pointers

When an integration breaks in production, ops follows the per-vendor
runbook (stored in `docs/runbooks/<vendor>.md` of the deploying repo,
NOT in this spec):

- Slack outage → `docs/runbooks/slack-webhook.md`
  - Identify `RETRY_SCHEDULED` deliveries, batch-trigger manual replay
    via DLT runbook ([`runbooks/dlt-replay.md`](runbooks/dlt-replay.md))
    after the circuit recovers.
  - On terminal `FAILED`: review per-delivery `failure_reason`; if
    `CHANNEL_NOT_FOUND` / `TOKEN_REVOKED`, rotate the secret and trigger
    rolling restart.
- Kafka cluster outage → `docs/runbooks/kafka.md`
- PostgreSQL primary failover → `docs/runbooks/postgres.md`

---

## 9. Not In v1

- **Email channel** (SMTP / SES) — v2.
- **SMS channel** (Twilio) — v2.
- **Mobile push** (FCM / APNs) — v2.
- **Mattermost / Discord / Teams** vendor swap — v2 (port already
  abstracts; adapter swap only).
- **Bidirectional Slack interactions** (slash commands, button callbacks
  via Slack Events API) — v2; would introduce **inbound** webhook surface
  for the first time.
- **Slack `blocks` templating** — v2 (v1 ships `{"text":"..."}` only).
- **Per-user preference UI** — v2 (`admin-service` owns).
- **Slack rate-limit 429 retry** — v1 treats as permanent; v2 may add
  exponential back-off with respect to Slack's `Retry-After` header.
- **Slack webhook URL hot reload** — v1 is boot-only; v2 may add
  Actuator `RefreshScope`-style refresh.

---

## References

- [`architecture.md`](architecture.md) — § Identity, § Dependencies, § Routing Rules, § Retry budget
- [`domain-model.md`](domain-model.md) — AlertEnvelope, NotificationDelivery, ChannelType, ChannelTarget
- [`idempotency.md`](idempotency.md) — Inbound dedupe + Outbound dedupe (deliveryId)
- [`runbooks/dlt-replay.md`](runbooks/dlt-replay.md) — manual replay procedure
- [`../../contracts/events/notification-subscriptions.md`](../../contracts/events/notification-subscriptions.md) — 6 source topic catalog
- [`../../contracts/events/notification-events.md`](../../contracts/events/notification-events.md) — `notification.delivered.v1` audit schema
- [`../inbound-service/external-integrations.md`](../inbound-service/external-integrations.md) — sibling (ERP webhook + Kafka + Postgres + Secret Manager)
- [`../outbound-service/external-integrations.md`](../outbound-service/external-integrations.md) — sibling (TMS marquee, full I1-I10 reference)
- [`../inventory-service/external-integrations.md`](../inventory-service/external-integrations.md) — sibling (zero-state, BE-156)
- `../../../apps/notification-service/src/main/resources/application.yml` — Resilience4j `slack` + channel aliases
- `../../../apps/notification-service/src/main/java/com/wms/notification/adapter/outbound/slack/SlackChannelAdapter.java` — adapter source-of-truth
- `../../../../../rules/traits/integration-heavy.md` — I1-I10
- `../../../../../platform/api-gateway-policy.md` — webhook routing (N/A inbound v1)
- `../../../../../platform/security-rules.md` — Secret Manager policy
- `../../../../../platform/observability.md` — required metrics
- `../../../../../platform/error-handling.md` — `DELIVERY_RETRY_EXHAUSTED` etc.
