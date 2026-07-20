# Runbook — notification-service DLQ replay

**Required by** [`ADR-006`](../../docs/adr/ADR-006-at-least-once-delivery-policy.md)
§ notification-service Scenario B ("Document the DLQ replay procedure … in `knowledge/runbooks/`").

**You are here because** one of these fired, or someone reported missing notifications:

| Signal | Meaning |
|---|---|
| `NotificationDeliveryFailureRateHigh` / `...Critical` | Sends are being attempted and failing — the message was consumed, so it is **not** in a DLQ. Go to [§5](#5-when-the-message-is-not-in-a-dlq). |
| Consumer error spike / `KafkaConsumerLagHigh` on `notification-service` | Messages may have exhausted retries and landed in a DLQ. Start at [§1](#1-check-whether-anything-is-actually-in-a-dlq). |
| "Customer says they never got the email" | Start at [§1](#1-check-whether-anything-is-actually-in-a-dlq), then [§5](#5-when-the-message-is-not-in-a-dlq). |

---

## 0. What the DLQ wiring actually is

Read this before running anything — the topic names below are **derived**, not configured, and a
reader who guesses them will find empty topics and conclude "nothing to replay".

`notification-service` registers a `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`
(`adapter/in/kafka/KafkaConsumerConfig.java`). Its behaviour:

- **Retries**: exponential backoff, initial 1s, multiplier 2.0, max interval 30s, **max 3 attempts**.
- **Not retried**: `JsonProcessingException` (a malformed payload will never parse — it goes
  straight to the DLQ).
- **DLQ topic name**: the **original topic plus the literal suffix `.dlq`**, same partition.
  Note this is `<topic>.dlq`, *not* the spring-kafka default `<topic>.DLT`. ADR-006's prose
  mentions `notification.send.failed.DLT`; **no such topic exists** — the resolver in
  `KafkaConsumerConfig` is authoritative.

So the four DLQ topics are exactly:

| Consumer | Source topic | DLQ topic |
|---|---|---|
| `AccountCreatedEventConsumer` | `account.created` | `account.created.dlq` |
| `OrderPlacedEventConsumer` | `order.order.placed` | `order.order.placed.dlq` |
| `PaymentCompletedEventConsumer` | `payment.payment.completed` | `payment.payment.completed.dlq` |
| `ShippingStatusChangedEventConsumer` | `shipping.shipping.status-changed` | `shipping.shipping.status-changed.dlq` |

All four consumers use consumer group **`notification-service`**.

**Replay is safe.** `NotificationSendService.sendNotification` checks
`notificationRepository.existsByEventId(eventId, tenantId)` and returns early on a duplicate, so
re-delivering a message that was already processed produces no second notification. This is why
the procedure below re-publishes rather than trying to determine "was this one already handled".

---

## 1. Check whether anything is actually in a DLQ

All commands run against the local compose stack. Kafka is `ecommerce-kafka`, broker
`localhost:9092` **inside** the container.

```bash
# List DLQ topics that exist at all (an absent topic means nothing was ever dead-lettered).
docker exec ecommerce-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list | grep '\.dlq$'
```

Count the messages sitting in one:

```bash
# Replace TOPIC with a value from the table in §0.
TOPIC=order.order.placed.dlq

docker exec ecommerce-kafka /opt/kafka/bin/kafka-run-class.sh \
  kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic "$TOPIC"
```

Output is `topic:partition:endOffset` per partition. **Sum of endOffsets = 0, or the topic is not
listed → nothing was dead-lettered.** Stop here and go to [§5](#5-when-the-message-is-not-in-a-dlq).

---

## 2. Read the dead-lettered messages before replaying anything

Never replay blind — if the cause is a malformed payload, replaying just refills the DLQ.

```bash
TOPIC=order.order.placed.dlq

docker exec ecommerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC" \
  --from-beginning \
  --timeout-ms 10000 \
  --property print.headers=true \
  --property print.timestamp=true
```

`print.headers=true` matters: spring-kafka stamps the failure reason onto the record. Look for:

- `kafka_dlt-exception-fqcn` — the exception class that caused the dead-letter.
- `kafka_dlt-exception-message` — its message.
- `kafka_dlt-original-topic`, `kafka_dlt-original-partition`, `kafka_dlt-original-offset`.

Decide from the exception class:

| `kafka_dlt-exception-fqcn` | Cause | Action |
|---|---|---|
| `...JsonProcessingException` / `...JsonMappingException` | Malformed or schema-drifted payload. It was never retried and replay **will** fail again. | Do **not** replay. Fix the producer or the DTO, then replay. |
| `...MailSendException`, `...MailAuthenticationException` | SMTP was down or credentials were wrong at the time. | Fix SMTP first ([§3](#3-confirm-the-underlying-cause-is-gone)), then replay. |
| `...CannotCreateTransactionException`, `...JDBCConnectionException` | notification-postgres was unreachable. | Confirm DB is up ([§3](#3-confirm-the-underlying-cause-is-gone)), then replay. |
| anything else | Unknown. | Read the message, then decide. Do not replay a cause you have not identified. |

---

## 3. Confirm the underlying cause is gone

Replaying into a still-broken dependency just re-fills the DLQ.

```bash
# notification-service is up and its DB is reachable
docker exec ecommerce-notification-service curl -sf http://localhost:8080/actuator/health | head -c 400
```

Expect `"status":"UP"`. If the service does not expose `curl`, use the host:
`curl -sf http://notification.local/actuator/health`.

```bash
# The DB itself
docker exec ecommerce-notification-postgres pg_isready -U notification_user -d notification_db
```

Expect `accepting connections`.

For an SMTP-caused dead-letter, verify the mail relay the service is configured against
(`spring.mail.*` in `apps/notification-service/src/main/resources/application.yml` and any
compose-level override) answers before replaying.

---

## 4. Replay

Replay = consume from `<topic>.dlq` and re-produce to the original topic. The consumers are
idempotent on `eventId` (see §0), so this is safe to run more than once.

**Step 4a — capture the DLQ contents to a file.** Keys must be preserved (they carry the partition
routing), so consume with the key printed and a tab separator:

```bash
TOPIC=order.order.placed
mkdir -p /tmp/dlq-replay

docker exec ecommerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic "${TOPIC}.dlq" \
  --from-beginning \
  --timeout-ms 15000 \
  --property print.key=true \
  --property key.separator=$'\t' \
  > /tmp/dlq-replay/${TOPIC}.tsv

wc -l /tmp/dlq-replay/${TOPIC}.tsv
```

The line count must match the offset sum from §1. **If it does not, stop** — you are about to
replay a partial set, and the missing ones will look "handled".

**Step 4b — re-produce to the original topic:**

```bash
TOPIC=order.order.placed

docker exec -i ecommerce-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC" \
  --property parse.key=true \
  --property key.separator=$'\t' \
  < /tmp/dlq-replay/${TOPIC}.tsv
```

**Step 4c — verify the replay was consumed**, not just produced:

```bash
docker exec ecommerce-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group notification-service --describe
```

`LAG` for the replayed topic must return to `0`. A lag that stays put means the consumer is not
running — check `docker logs ecommerce-notification-service`.

Then confirm rows actually landed:

```bash
docker exec ecommerce-notification-postgres psql -U notification_user -d notification_db -c \
  "SELECT status, channel, count(*) FROM notifications
   WHERE created_at > now() - interval '15 minutes'
   GROUP BY status, channel ORDER BY 1,2;"
```

Expect `SENT` rows. `FAILED` rows here mean the send is failing for a *new* reason — go to §5.

**Step 4d — purge the DLQ so the next on-call does not replay it again.** Only after §4c is clean:

```bash
TOPIC=order.order.placed

cat > /tmp/dlq-replay/purge.json <<EOF
{"partitions": [{"topic": "${TOPIC}.dlq", "partition": 0, "offset": -1}], "version": 1}
EOF
docker cp /tmp/dlq-replay/purge.json ecommerce-kafka:/tmp/purge.json

docker exec ecommerce-kafka /opt/kafka/bin/kafka-delete-records.sh \
  --bootstrap-server localhost:9092 --offset-json-file /tmp/purge.json
```

`offset: -1` truncates to the current end (delete everything). If the topic has more than one
partition, add an entry per partition — the partition list is the one §1's `GetOffsetShell`
printed.

---

## 5. When the message is NOT in a DLQ

This is the common case and the one the delivery-failure alerts point at. A send that fails inside
`NotificationSendService` is **caught** — the notification row is marked `FAILED`, the
`notification_failed_total{channel,reason}` counter is incremented, and the Kafka message is
**acknowledged normally**. Nothing is dead-lettered, so there is nothing to replay.

Find them and see why:

```bash
docker exec ecommerce-notification-postgres psql -U notification_user -d notification_db -c \
  "SELECT notification_id, tenant_id, user_id, channel, status, retry_count, event_id, created_at
   FROM notifications
   WHERE status = 'FAILED' AND created_at > now() - interval '1 hour'
   ORDER BY created_at DESC LIMIT 50;"
```

Cross-reference the reason label from the metric:

```bash
docker exec ecommerce-notification-service \
  curl -s http://localhost:8080/actuator/prometheus | grep '^notification_failed_total'
```

The `reason` label is the bounded enum in `NotificationFailureReason`:

| `reason` | Meaning | Where to look |
|---|---|---|
| `mail_auth` | SMTP rejected the credentials | mail relay config / secret rotation |
| `mail_send` | SMTP transport failed (unreachable, rejected, reset) | mail relay availability |
| `push_delivery` | Web Push failed for **every** one of the user's subscriptions | push endpoint reachability, VAPID config |
| `serialization` | Payload could not be serialised — a code bug, not an outage | `WebPushSender#toPayload`, escalate to the owning team |
| `timeout` | The send did not complete in time | dependency latency |
| `unknown` | Not classified | read `docker logs ecommerce-notification-service` for the stack trace and extend `NotificationFailureReason` |

There is **no automated re-send for `FAILED` rows** — notification delivery is best-effort by
ADR-006's explicit decision (the notification is the terminal of the saga; a lost email is not
state divergence). To re-notify a specific user after the cause is fixed, re-publish the source
event using §4b with a **new** `eventId`; reusing the old one is deduplicated away by design.

---

## 6. Escalate

Escalate to the service owner rather than improvising if:

- The DLQ refills immediately after a replay (the cause is not what §2 suggested).
- `reason=serialization` appears — that is a code defect, not an ops condition.
- The `notifications` table shows `SENT` rows but users report non-receipt — the failure is
  downstream of SMTP (spam filtering, bounce), which this runbook does not cover and which
  ADR-006 explicitly notes SMTP `250 OK` cannot rule out.
