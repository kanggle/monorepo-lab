# Runbook — withdrawn user whose orders were never cancelled

**Required by** [`ADR-006`](../../docs/adr/ADR-006-at-least-once-delivery-policy.md)
§ user-service Scenario B ("Add a withdrawn-but-not-cancelled ops query in `knowledge/runbooks/`
so on-call has a recovery path").

**You are here because** `UserWithdrawnEventPublishFailure` fired, or someone reported that a
withdrawn account still has live orders.

---

## 0. Why this state is reachable

ADR-006 accepts **best-effort** publishing for `user.user.withdrawn`. `KafkaUserProfileEventPublisher`
publishes from a `@TransactionalEventListener(AFTER_COMMIT)`, i.e. **after** the withdrawal is
already committed in `user_db`. If the broker send then fails there is no outbox row and no retry:

- `user_profiles.status` is `WITHDRAWN` — permanently.
- `order-service` never runs `cancelOrdersForWithdrawnUser`, so `PENDING`/`CONFIRMED` orders stay live.

> **`order-service` is the only live consumer.** ADR-006 § user-service Scenario B says
> `user.user.withdrawn` "does have downstream consumers (order + auth-service)". That was true when
> the ADR was written; ecommerce's `auth-service` was decommissioned by **TASK-BE-132** (excluded
> from `settings.gradle`, removed from `docker-compose.yml`, replaced by IAM OIDC). Its
> `UserWithdrawnEventConsumer` is still on disk under `apps/auth-service/` as preserved history and
> **does not run** — there is no `auth-service` consumer group to check and no account-deactivation
> step to verify. Verified 2026-07-20 (TASK-BE-533): `@KafkaListener(topics = "user.user.withdrawn")`
> exists in exactly two files repo-wide, and only order-service's is in a built, deployed module.

There is no automatic repair. **This runbook is the recovery path**, and the alert is the only
signal that it is needed — which is why the alert is `severity: critical` for a single occurrence.

The counter behind the alert is emitted at two places in `publishEvent`: the async
`whenComplete` callback (broker rejected/timed out) and the synchronous `JsonProcessingException`
catch (serialisation failed). Both call `userMetrics.incrementEventPublishFailure("UserWithdrawn")`.

---

## 1. Confirm the failure and get its blast radius

```bash
# The alert's underlying series. Non-zero => at least one withdrawal did not fan out.
docker exec ecommerce-user-service \
  curl -s http://localhost:8080/actuator/prometheus \
  | grep 'event_publish_failure_total' | grep 'UserWithdrawn'
```

The counter tells you **how many** failed, not **which users**. Get the user ids from the log —
the publisher logs the id on both failure paths:

```bash
docker logs ecommerce-user-service --since 24h 2>&1 \
  | grep "Event publishing failed" | grep "UserWithdrawn"
```

Each line ends with `userId=<uuid>`. Collect those ids; they are your candidate set. If the log has
already rotated past the failure, skip to §2 — the query there finds the same users from state
rather than from logs, and does not depend on log retention.

---

## 2. The ops query — who is withdrawn but not cancelled

`user-service` and `order-service` own **separate databases** (`user_db` on
`ecommerce-user-postgres`, `order_db` on `ecommerce-order-postgres`). There is no cross-database
join available, so this runs as two queries and a comparison. Do not look for a single SQL that
answers it — there isn't one, and that is the design, not an oversight.

**Step 2a — withdrawn users, from `user_db`:**

```bash
docker exec ecommerce-user-postgres psql -U user_user -d user_db -At -F',' -c \
  "SELECT user_id
   FROM user_profiles
   WHERE status = 'WITHDRAWN'
     AND updated_at > now() - interval '7 days'
   ORDER BY updated_at DESC;"
```

`-At -F','` prints bare values, one per line — directly pasteable into the next query. Widen
`interval '7 days'` if the failure is older; narrow it if the list is large.

**Step 2b — of those, who still has live orders, from `order_db`:**

Paste the ids from 2a into the `IN` list (quoted, comma-separated). `PENDING` and `CONFIRMED` are
exactly the statuses `UserWithdrawalOrderService.ACTIVE_STATUSES` cancels — matching that list is
what makes this query mean "the consumer did not run".

```bash
docker exec ecommerce-order-postgres psql -U order_user -d order_db -c \
  "SELECT user_id, order_id, status, total_price, created_at
   FROM orders
   WHERE status IN ('PENDING','CONFIRMED')
     AND user_id IN (
       '00000000-0000-0000-0000-000000000000'  -- <- replace with the ids from step 2a
     )
   ORDER BY user_id, created_at;"
```

**Rows returned = the damage.** Each row is a live order belonging to an account that has been
withdrawn. Zero rows means the fan-out did happen (or the users had no active orders) and no
repair is needed — record that and stop.

---

## 3. Repair — re-publish the event

Do **not** hand-edit `orders`. Re-publishing drives the same code path the consumer normally
runs, so the cancellation **and** its downstream `OrderCancelled` event happen together and in
order. Editing rows directly does the first and skips the second.

The consumer is idempotent, so re-publishing is safe even if you are unsure whether it already ran:

- `order-service` checks `EventDeduplicationChecker.isDuplicate(eventId, "UserWithdrawn")`.
- `order-service` also filters on `ACTIVE_STATUSES`, so an already-cancelled order is a no-op.

Because order-service dedupes on `eventId`, the replacement event needs a **fresh `eventId`** — a
re-send carrying the original id would be discarded as a duplicate and silently do nothing.

Build one message per affected user. Field names and shape come from `UserWithdrawnEvent`:

```bash
USER_ID=00000000-0000-0000-0000-000000000000   # from step 2b
TENANT_ID=ecommerce                            # user_profiles.tenant_id for that user
EVENT_ID=$(cat /proc/sys/kernel/random/uuid)   # MUST be new — see above
WITHDRAWN_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

cat > /tmp/withdrawn-replay.tsv <<EOF
${USER_ID}	{"eventId":"${EVENT_ID}","eventType":"UserWithdrawn","occurredAt":"${WITHDRAWN_AT}","source":"user-service","tenantId":"${TENANT_ID}","payload":{"userId":"${USER_ID}","withdrawnAt":"${WITHDRAWN_AT}"}}
EOF
```

Confirm the `tenantId` before sending — get it from `user_db`:

```bash
docker exec ecommerce-user-postgres psql -U user_user -d user_db -At -c \
  "SELECT tenant_id FROM user_profiles WHERE user_id = '${USER_ID}';"
```

The key is the user id (matching what the publisher uses), so ordering per user is preserved:

```bash
docker cp /tmp/withdrawn-replay.tsv ecommerce-kafka:/tmp/withdrawn-replay.tsv

docker exec -i ecommerce-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic user.user.withdrawn \
  --property parse.key=true \
  --property key.separator=$'\t' \
  < /tmp/withdrawn-replay.tsv
```

---

## 4. Verify the repair

Re-run **step 2b** with the same user ids. It must now return **zero rows**.

```bash
# The consumer actually processed it (lag back to 0)
docker exec ecommerce-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --group order-service --describe | grep user.user.withdrawn
```

```bash
# The orders moved to CANCELLED rather than merely disappearing from the active filter
docker exec ecommerce-order-postgres psql -U order_user -d order_db -c \
  "SELECT order_id, status, updated_at FROM orders
   WHERE user_id = '${USER_ID}' ORDER BY updated_at DESC;"
```

Expect `CANCELLED`. Also confirm order-service logged the cancellation:

```bash
docker logs ecommerce-order-service --since 10m 2>&1 | grep "withdrawn user"
```

Expect `Cancelled active orders for withdrawn user: userId=…, cancelledCount=N`.

If lag returned to 0 but the orders are still `PENDING`, the event was consumed and **deduplicated**
— the `eventId` you sent was not new. Regenerate `EVENT_ID` and repeat §3.

---

## 5. Escalate

- **The alert fires repeatedly** — this is no longer an incident to repair one user at a time; the
  broker or the publisher is broken. Fix the publish path first, then run §2 once at the end.
- **Sustained failure rate** — ADR-006 names its own v2 trigger: if best-effort publishing stops
  being acceptable, the decision to promote `user-service` to Scenario A (transactional outbox,
  as `payment-service` did in TASK-BE-136) belongs in a follow-up ADR, not in this runbook.
