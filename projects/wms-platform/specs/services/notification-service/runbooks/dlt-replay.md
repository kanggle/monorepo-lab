# notification-service — DLT Replay Runbook

Operator playbook for draining the DLT (Dead-Letter Topic) backlog and
manually replaying notification deliveries.

Read after [`../architecture.md`](../architecture.md) § Kafka Consumption
and [`../idempotency.md`](../idempotency.md) § Failure Modes.

---

## Purpose

`notification-service` consumes 6 source topics
([`../../contracts/events/notification-subscriptions.md`](../../../contracts/events/notification-subscriptions.md))
and dispatches Slack webhooks. Two distinct retry layers exist:

| Layer | Trigger | Routing |
|---|---|---|
| **Kafka consumer retry** | Listener method throws / Postgres TX rollback | Spring Kafka `DefaultErrorHandler` — 3 retries with exponential backoff, then `<source-topic>.DLT` |
| **Delivery retry** (per-row) | Slack webhook 5xx / timeout / circuit-breaker open | In-place `attempt_count++` on `notification_delivery` row, up to 5 attempts (see [`../idempotency.md`](../idempotency.md) § Retry budget). Terminal `status=FAILED` if exhausted. |

This runbook covers operator intervention for **both** layers:

1. **Kafka DLT backlog** — events that never entered the application
   service due to repeated consumer-side failures (Postgres outage,
   deserialization error, listener exception).
2. **Application-layer terminal failures** — events that were classified
   `QUEUED` but the delivery row reached `FAILED` after retry exhaustion.

---

## Prerequisites

Operator MUST have:

- Access to the Kafka cluster's `kafka-consumer-groups.sh` /
  `kafka-console-consumer.sh` / `kafka-topics.sh` (or equivalent UI —
  e.g. `kafka-ui` running at `http://kafka.wms.local/` per
  [`../../../../../../CLAUDE.md`](../../../../../../CLAUDE.md) § Local Network Convention).
- Read + UPDATE access to the `notification_delivery` Postgres table
  (DBeaver / `psql`). Connection details: per `docker-compose.bootrun.yml`
  overlay (`postgres.wms.local:5432`).
- The on-call engineer's PagerDuty acknowledgement (this is a
  user-impacting incident class — notifications missed).

---

## Step 1 — Identify the backlog

### Kafka DLT inspection

```bash
# List DLT topics with non-zero backlog
kafka-topics.sh --bootstrap-server kafka.wms.local:9092 \
  --list | grep '\.DLT$'

# For each DLT, get message count
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --bootstrap-server kafka.wms.local:9092 \
  --topic wms.inventory.alert.v1.DLT \
  --time -1
```

The reported `offset` minus the consumer group's current offset
(usually 0 for DLT topics that aren't consumed by the live system) is
the backlog count.

### Application-layer terminal failures

```sql
SELECT id, event_id, source_topic, channel_id, last_error, attempt_count, updated_at
  FROM notification_delivery
 WHERE status = 'FAILED'
   AND updated_at > now() - interval '1 hour'
 ORDER BY updated_at DESC;
```

Group by `last_error` to identify the root failure class. Cross-check
the `wms.notification.delivery.terminal` metric in Grafana for the
total terminal-failure rate over time.

---

## Step 2 — Diagnostic classification

Match the failure pattern to one of the following:

| Pattern | Root cause | Replay strategy |
|---|---|---|
| All DLT messages from one source topic, same time window | Postgres outage / consumer connection lost | Wait for environment recovery, then **§ Step 3a — DLT drain**. |
| DLT messages have malformed JSON / unknown event type | Schema drift (publisher emitted unexpected payload, or upstream contract change unannounced) | **§ Step 3c — DLT message removal** + open a fix task against the publishing service. |
| `notification_delivery` rows in `FAILED` with `last_error LIKE '5xx%'` | Slack vendor outage post-recovery | **§ Step 3b — Manual delivery retry**. |
| `notification_delivery` rows in `FAILED` with `last_error LIKE '4xx%'` | Production bug (malformed Slack payload from routing rule) | **Do not replay.** Fix the routing rule or the formatter, deploy, then optionally retry post-fix. |
| `notification_delivery` rows in `FAILED` with `last_error = 'Circuit breaker open'` | Slack vendor was unavailable beyond CB timeout (typically also covers 5xx pattern) | **§ Step 3b — Manual delivery retry**. |

---

## Step 3a — DLT drain (Kafka consumer retry exhausted)

Goal: re-enqueue messages from `<source-topic>.DLT` back onto the source
topic so the live consumer picks them up again. The application's
inbound dedupe ([`../idempotency.md`](../idempotency.md) § Inbound) prevents
double-processing — replaying an already-processed `eventId` is a no-op.

```bash
# 1. Consume from DLT to a local file (preserves keys + headers)
kafka-console-consumer.sh \
  --bootstrap-server kafka.wms.local:9092 \
  --topic wms.inventory.alert.v1.DLT \
  --from-beginning \
  --max-messages <BACKLOG_COUNT> \
  --property print.key=true \
  --property print.headers=true \
  --formatter kafka.tools.DefaultMessageFormatter \
  > /tmp/dlt-replay-$(date +%s).jsonl

# 2. Replay into the original topic
#    (use a script that strips DLT headers like kafka_dlt-exception-* before re-publishing)
./scripts/kafka-dlt-replay.sh \
  --from /tmp/dlt-replay-*.jsonl \
  --to wms.inventory.alert.v1 \
  --bootstrap kafka.wms.local:9092

# 3. Watch the consumer group lag drop
kafka-consumer-groups.sh \
  --bootstrap-server kafka.wms.local:9092 \
  --group wms-notification-v1 \
  --describe
```

> **Note**: A `scripts/kafka-dlt-replay.sh` helper does not exist in
> the monorepo yet — if you need it operationally, file a follow-up
> task (`TASK-MONO-XXX-kafka-dlt-replay-script`). For one-off drains
> the manual `kafka-console-producer.sh` with stripped headers also
> works.

---

## Step 3b — Manual delivery retry (application terminal failures)

Goal: reset `status` from `FAILED` back to `PENDING` so the in-process
`@Scheduled` retry picker re-attempts the Slack dispatch.

```sql
-- Reset a specific delivery row for retry
UPDATE notification_delivery
   SET status            = 'PENDING',
       attempt_count     = 0,            -- reset retry budget
       scheduled_retry_at = now(),       -- pick up on next scheduler tick (~30s)
       last_error        = NULL,
       version           = version + 1   -- bump @Version
 WHERE id = '<delivery-row-uuid>'
   AND status = 'FAILED';                -- guard: do not reset SUCCEEDED rows
```

Or bulk-reset for an entire failure pattern:

```sql
UPDATE notification_delivery
   SET status            = 'PENDING',
       attempt_count     = 0,
       scheduled_retry_at = now(),
       last_error        = NULL,
       version           = version + 1
 WHERE status = 'FAILED'
   AND last_error LIKE '5xx%'
   AND updated_at BETWEEN '<vendor outage start>' AND '<vendor recovery>';
```

**Safety**:

- The `delivery_idempotency_key UQ` constraint stays intact, so no
  duplicate row is created.
- If Slack happens to deliver both the original (silently-accepted
  during outage) and the retry, the user sees the alert twice. Accepted
  v1 tradeoff — better double than missed.
- DO NOT bump `version` in production code; the manual UPDATE above is
  the only sanctioned write outside the application service.

---

## Step 3c — DLT message removal (poison pill / schema drift)

When a DLT message will NEVER be processable (malformed payload, unknown
event type), it must be removed so the operator's drain workflow isn't
permanently blocked. Spring Kafka's DLT is itself a Kafka topic, so
"removing" means advancing a consumer group's offset past the message,
or compacting/recreating the topic during a maintenance window.

```bash
# Advance a hypothetical replay consumer group past poison messages
kafka-consumer-groups.sh \
  --bootstrap-server kafka.wms.local:9092 \
  --group dlt-replay-tool \
  --topic wms.inventory.alert.v1.DLT \
  --reset-offsets \
  --to-offset <SAFE_OFFSET> \
  --execute
```

ALWAYS file a fix task against the upstream publisher (the service that
produced the malformed event) so the root cause is addressed.

---

## Step 4 — Verification

After replay:

1. Watch `wms.notification.dedupe.outcome{outcome=fresh-queued}` in
   Grafana — should spike during replay, then decay.
2. Watch `wms.notification.delivery.terminal{reason=SUCCEEDED}` —
   should follow with a lag of one scheduler tick (~30s).
3. Verify in the destination Slack channel that messages arrived.
4. Query for residual terminal failures:
   ```sql
   SELECT count(*) FROM notification_delivery
    WHERE status = 'FAILED' AND updated_at > '<replay start time>';
   ```
   Expected: 0 (or a small count of genuinely-unrecoverable 4xx errors
   that should be investigated separately).

---

## Rollback / safety

- All operations in this runbook are **forward-only**. There is no
  "undo a replay" — once Slack receives the message, the user has seen it.
- If a replay accidentally targets the wrong topic, the live
  consumer's inbound dedupe still protects against duplicate
  processing (eventId-based). Worst case is one extra Slack message per
  unique replayed eventId.
- DO NOT delete rows from `notification_event_dedupe` to "force a
  reprocess" — this breaks the at-most-once delivery guarantee at the
  application layer. Use § Step 3b instead.

---

## Escalation

If replay does not resolve the backlog within 30 minutes, escalate to:

- **wms-platform on-call** — primary owner of `notification-service`.
- **GAP on-call** — if the failure is JWT-validation related (token
  refresh, JWKS staleness) on Slack adapter; unlikely but possible.
- The originating service's on-call — for poison-pill / schema-drift
  cases (per § Step 3c).

---

## References

- [`../architecture.md`](../architecture.md) § Kafka Consumption + § Failure Modes
- [`../idempotency.md`](../idempotency.md) § Inbound + § Retry budget
- [`../../contracts/events/notification-subscriptions.md`](../../../contracts/events/notification-subscriptions.md) — 6 source topic catalog
- [`../../../../../../platform/event-driven-policy.md`](../../../../../../platform/event-driven-policy.md) § Consumer Rules
- [`../../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`](../../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) § D5 Category C — retry + DLT pattern reference
- [`../../../../../../platform/error-handling.md`](../../../../../../platform/error-handling.md) — `DELIVERY_RETRY_EXHAUSTED`, `IDEMPOTENCY_KEY_DUPLICATE` error codes
