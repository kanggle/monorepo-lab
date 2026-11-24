# Runbook — SecurityDlqDepthHigh

Assumptions: [`README.md`](README.md) § Environment. Kafka tool names below are the standard Kafka
3.7 ones from the `iam-kafka` image; they were not executed while writing this page.

## 1. What it means

`security_dlq_depth > 100` for 5 minutes, severity `warning`.

Metric: gauge `security_dlq_depth`, exported by
`security-service/.../infrastructure/config/SecurityMetricsConfig.java` (`registerDlqDepthGauge`,
value refreshed every 30 s by `refreshDlqDepth` → `computeDlqDepth`). It is the sum, over the
`.DLT` topics `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`,
`auth.token.refreshed`, `auth.token.reuse.detected` and `account.locked`, of
`latest offset − committed offset of consumer group "security-service.dlq"`.

🔴 **Read what that means.** Nothing in security-service consumes `.DLT` topics, so group
`security-service.dlq` does not exist, and the code then counts the **latest offset** of each
`.DLT` partition (`fetchCommittedOffsetsForDlqGroup` returns null → depth = latest offset).
A partition's latest offset never goes down — not when records expire (`.DLT` retention is 30 days,
`DLQ_RETENTION_MS=2592000000`, `docker-compose.yml:187`). So once more than 100 records have ever
been parked, **the gauge stays above 100 and the alert keeps firing even after every poison record
was handled or has expired**, until the `.DLT` topics are recreated or a `security-service.dlq`
group commits offsets. Also: on any Kafka error the gauge reports `0` (log `Failed to compute DLQ
depth metric, returning 0`), and `account.deleted.DLT` is not in the list at all.

How records get there (`security-service/.../infrastructure/kafka/KafkaConsumerConfig.java`):
after 3 retries (1 s → 30 s backoff), or immediately for `DeserializationException`
(payload is not valid JSON per `StrictJsonStringDeserializer`) and `MissingTenantIdException`
(event without `tenant_id`), `DeadLetterPublishingRecoverer` publishes to `<topic>.DLT` and logs
`Sending to DLQ: topic=…, partition=…, offset=…, eventKey=…, cause=…`.

## 2. First checks

```bash
# How many were parked, by reason (counter since this security-service process started)
docker exec iam-prometheus wget -qO- --post-data='query=outbox_dlq_size_total' http://localhost:9090/api/v1/query

# Real size of each .DLT topic: the latest offset per partition (the number the gauge sums)
docker exec iam-kafka /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic auth.login.failed.DLT --time -1
docker exec iam-kafka /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --bootstrap-server localhost:9092 --topic account.locked.DLT --time -1

# Read the newest parked records with their headers (the exception text is in the dlt headers)
docker exec iam-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic auth.login.failed.DLT --from-beginning --max-messages 5 --timeout-ms 10000 --property print.headers=true --property print.key=true
```

Repeat the last two for the other topics in § 1. security-service log: `Sending to DLQ` lines carry
the `cause`.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| A producer sends events without `tenant_id` | `outbox_dlq_size_total{reason="tenant_id_missing"}` rising; `cause=MissingTenantIdException` |
| A producer sends malformed JSON | `reason="deserialization_failure"` rising; headers show the deserialization error |
| A handler bug or a dependency failing on one record type | `reason="other"` rising; the same exception class repeating in `Sending to DLQ` |
| **Old records only; nothing new arriving** | The counters above are flat, `GetOffsetShell` offsets do not change between two runs — the alert is the permanent-depth effect in § 1, not a live problem |
| `DLQ publish FAILED` in the log | The DLT publish itself failed (serialiser / broker); the record is then not parked |

## 4. Recover / mitigate

- Find which producer emits the bad records (`eventKey`, `topic` in the log) and fix it; a record
  without `tenant_id` or with invalid JSON cannot be replayed as is.
- **Replaying parked records: no tooling exists in this repository — not verified.** Handlers are
  idempotent on the event id (`login_history.event_id` is unique; `RecordAccountLockHistoryUseCase`
  tolerates a replay), so re-publishing a *corrected* event to the source topic is safe in principle;
  design the procedure with the security-service owner before doing it by hand.
- Do not delete `.DLT` topics to silence the alert unless the owner has accepted that the parked
  records are discarded (deleting a topic is irreversible).
- Confirm: `outbox_dlq_size_total` flat, and `GetOffsetShell` offsets unchanged.

## 5. Escalate

- Rising `reason` counters: the owner of the producing service (auth-service for `auth.*`,
  account-service for `account.locked`) plus security-service.
- The alert stuck on after the cause is fixed: file it as the gauge-semantics gap in § 1 (needs a
  drain group or a different depth definition) — it is a code/spec decision, not an on-call action.
