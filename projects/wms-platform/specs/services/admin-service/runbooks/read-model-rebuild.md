# admin-service — Read-Model Rebuild Runbook

Manual ops procedure to rebuild `admin-service`'s CQRS read-model tables by
re-consuming source topics from offset 0. Triggered by:

- A read-model schema migration that adds / removes / reshapes denormalised
  columns
- Detected drift between projection state and source-of-truth services
  (rare; surfaced by reconciliation queries)
- Recovery from a corrupted read-model state (e.g., a bad projection-handler
  release applied incorrect mutations)

This runbook is **not scheduled**. It is an explicitly-triggered, sequenced
procedure with operator-in-the-loop checkpoints.

> Read this together with [`architecture.md § Read-Model Rebuild Procedure`](../architecture.md)
> and [`idempotency.md`](../idempotency.md). The rebuild is safe because:
>
> - All projection writes are idempotent
> - Last-write-wins prevents stale state from reappearing
> - The dedupe table is wiped together with the projection state
> - Source services are unaffected (no calls back; pure consumer pattern)

---

## Pre-flight Checklist

Before starting, confirm:

- [ ] You have `WMS_SUPERADMIN` role and DB-admin credentials for the
      `admin-service` PostgreSQL instance
- [ ] You have `kubectl` (or equivalent) access to scale the `admin-service`
      deployment
- [ ] You have Kafka admin credentials capable of resetting consumer group
      offsets (`kafka-consumer-groups --group admin-projection
      --reset-offsets`)
- [ ] Source-topic broker retention covers the rebuild window. The procedure
      assumes **broker retention ≥ 7 days** (default). If older history is
      required (e.g., re-projecting >7d old events), broker retention must
      be raised first or accept partial rebuild from the earliest available
      offset
- [ ] No active Flyway migration is running on `admin-service`
- [ ] An entry has been logged in the ops-channel (`#wms-ops`) declaring
      the rebuild window, expected duration, and operator
- [ ] Current projection lag is observed (capture the baseline metric
      `admin.projection.lag.seconds` per topic)

### Estimating Duration

Rebuild duration ≈ `(number of historical events) / (steady-state apply
rate)`. As of v1, conservative numbers per environment:

| Env | Approx events / topic | Apply rate | Wall-clock estimate |
|---|---|---|---|
| dev / standalone | < 10k | 200 events/s | < 1 min |
| staging | ~ 500k | 500 events/s | ~ 17 min |
| prod (v1) | ~ 5M | 1000 events/s | ~ 90 min |

Numbers refresh as the platform grows. The
`/api/v1/admin/operations/projection-status` endpoint surfaces lifetime apply
counts that feed the next estimate.

---

## What Survives a Rebuild

| Data | Survives? | Why |
|---|---|---|
| `admin_user`, `admin_role`, `admin_user_role_assignment`, `admin_setting` | YES | Owned aggregates, not projections |
| `admin_outbox` (published rows) | YES | Owned write-side state |
| `admin_event_dedupe` | NO | Wiped together with projections so re-consumed events apply |
| `*_ref` (warehouse / zone / location / sku / lot / partner) | NO | Truncated and re-projected |
| `asn_summary`, `inspection_summary`, `order_summary`, `shipment_summary` | NO | Truncated |
| `inventory_snapshot` | NO | Truncated |
| `adjustment_audit`, `alert_log` | NO | Truncated; replayed from source events |
| `throughput_inbound_daily`, `throughput_outbound_daily` | NO | Truncated; replayed |
| Redis idempotency cache (`admin:idempotency:*`) | YES | Independent of projection state; not touched |

`alert_log.acknowledged_at` / `acknowledged_by` are **owned by admin-service**
(set by `POST /alerts/{id}/acknowledge`), not derivable from any source
event. The rebuild **drops these acknowledgements** because the table is
truncated. See § "Acknowledgement Preservation" below for the optional
preservation step.

---

## Procedure

### Step 1 — Announce and Freeze Writes (5 min)

1. Post in `#wms-ops`:

   ```
   :warning: admin-service read-model rebuild starting at <UTC time>.
   Operator: <name>. Expected duration: <estimate>.
   Dashboards may be empty / stale during this window.
   No outage to other services.
   ```

2. Verify no in-flight admin writes are blocking (these are NOT halted by
   the rebuild — admin write paths target owned aggregates and are
   independent). The freeze is informational only: dashboards will be
   incomplete during the window.

### Step 2 — Scale Down Projection Consumers (2 min)

`admin-service` runs a single deployment that hosts both REST and consumers.
Scale to `0`:

```bash
kubectl scale deployment/admin-service --replicas=0
```

Wait until pods are gone (`kubectl get pods -l app=admin-service` empty).

REST traffic returns 503 from the gateway during this window — this is the
visible cost of the procedure. If the platform requires REST availability
during rebuild, split the deployment into `admin-rest` and
`admin-projection` first (out of v1 scope).

### Step 3 — Truncate Read-Model and Dedupe Tables (5 min)

Connect to the `admin-service` DB as the schema owner.

> **Caution**: this step is destructive. Take a logical backup of the
> projection tables first if the operator is uncertain about the rebuild's
> success:
>
> ```bash
> pg_dump -h <host> -U <admin> -d admin \
>   --table='admin_*_ref' --table='admin_*_summary' \
>   --table='admin_inventory_snapshot' --table='admin_adjustment_audit' \
>   --table='admin_alert_log' --table='admin_throughput_*' \
>   --table='admin_event_dedupe' \
>   --data-only --column-inserts > admin-readmodel-backup-<date>.sql
> ```

Run inside a single transaction so a failure rolls back the truncates:

```sql
BEGIN;

-- Reference tables
TRUNCATE TABLE admin_warehouse_ref, admin_zone_ref, admin_location_ref,
               admin_sku_ref, admin_lot_ref, admin_partner_ref RESTART IDENTITY;

-- Inbound projections
TRUNCATE TABLE admin_asn_summary, admin_inspection_summary RESTART IDENTITY;

-- Outbound projections
TRUNCATE TABLE admin_order_summary, admin_shipment_summary RESTART IDENTITY;

-- Inventory projections
TRUNCATE TABLE admin_inventory_snapshot RESTART IDENTITY;
TRUNCATE TABLE admin_adjustment_audit RESTART IDENTITY;
TRUNCATE TABLE admin_alert_log RESTART IDENTITY;

-- Throughput counters
TRUNCATE TABLE admin_throughput_inbound_daily,
               admin_throughput_outbound_daily RESTART IDENTITY;

-- Dedupe (wipe so re-consumed events apply)
TRUNCATE TABLE admin_event_dedupe RESTART IDENTITY;

COMMIT;
```

> **Do NOT** truncate `admin_user`, `admin_role`,
> `admin_user_role_assignment`, `admin_setting`, or `admin_outbox`. These are
> owned aggregates / write-side infrastructure.

### Step 4 — Reset Consumer-Group Offsets (3 min)

Reset the `admin-projection` consumer group to earliest for every consumed
topic. Use a dry-run first to inspect the planned reset:

```bash
kafka-consumer-groups --bootstrap-server <broker> \
  --group admin-projection \
  --topic 'wms\.master\..*\.v1' \
  --topic 'wms\.inbound\..*\.v1' \
  --topic 'wms\.outbound\..*\.v1' \
  --topic 'wms\.inventory\..*\.v1' \
  --reset-offsets --to-earliest \
  --dry-run
```

Verify the printed offsets cover the expected topic surface, then execute
without `--dry-run` (replace with `--execute`). The full topic list is in
[`admin-events.md § Topic Layout`](../../../contracts/events/admin-events.md).

If the rebuild is for a **partial** scope (e.g., only inventory projections
need rebuild — say after a column-only schema change), reset offsets only
for that source service's topics. The dedupe table truncate must still wipe
all rows because we cannot identify which `event_id` belonged to which
topic without joining against historical Kafka offsets.

> **Partial-scope alternative**: if precision is critical, instead of
> wiping the dedupe table, run a targeted `DELETE FROM admin_event_dedupe
> WHERE event_type LIKE 'inventory.%';` before resetting offsets. This
> preserves dedupe state for un-touched topics but introduces operator
> error-surface — only use it when partial scope is genuinely required.

### Step 5 — Scale Consumers Back Up (2 min)

```bash
kubectl scale deployment/admin-service --replicas=2
```

Verify pods reach `Ready` and the consumer group rebalances. Within ~30 s
the projection consumers begin re-applying events from offset 0.

### Step 6 — Monitor Catch-Up (varies)

Two signals to watch:

1. **Kafka consumer-group lag** — drops as consumers chew through history:

   ```bash
   kafka-consumer-groups --bootstrap-server <broker> \
     --group admin-projection --describe
   ```

   Initially `LAG` ≈ topic high-water-mark. Target: lag ≈ 0 for every topic.

2. **Application metrics**:

   - `admin.projection.lag.seconds{topic}` — event time → applied time
   - `admin.projection.applied.count{topic}` — apply rate
   - `admin.projection.error.count{topic}` — should remain at 0; non-zero
     indicates schema mismatch or a poison message in history

Optional: poll the projection-status endpoint:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://<gateway>/api/v1/admin/operations/projection-status | jq
```

`worstLagSeconds < 5` is the steady-state threshold. The rebuild is
complete when **every topic** is below that threshold.

If errors appear (DLT publishes, consumer exceptions), abort the verification
and follow § "Failure Modes" below.

### Step 7 — Smoke-Test Dashboards (5 min)

Sample queries to confirm the read-model is populated:

| Endpoint | Expected non-empty for |
|---|---|
| `GET /api/v1/admin/dashboard/refs/warehouses` | Any warehouse seeded in master-service |
| `GET /api/v1/admin/dashboard/inventory?warehouseId=<known>` | Returns inventory rows for SKUs/locations with stock |
| `GET /api/v1/admin/dashboard/asns?warehouseId=<known>` | ASN history visible |
| `GET /api/v1/admin/dashboard/orders?warehouseId=<known>` | Order history visible |
| `GET /api/v1/admin/dashboard/throughput?warehouseId=<known>&from=<-7d>&to=<today>` | Daily counts non-zero on days with traffic |
| `GET /api/v1/admin/dashboard/alerts?acknowledged=false` | Unacknowledged low-stock alerts |

Cross-check against the source-of-truth services (e.g., compare
`admin.dashboard.inventory` row count with `inventory-service`'s
`GET /inventory` count for the same warehouse). Counts should match modulo
recently in-flight events.

### Step 8 — Verify Counters

Throughput counters are most-likely to surface bugs because they aggregate.
Cross-check a representative day:

```sql
-- From admin (rebuilt)
SELECT date, putaway_count, qty_received
FROM admin_throughput_inbound_daily
WHERE warehouse_id = '<known>' AND date BETWEEN <D-7> AND <today>
ORDER BY date;

-- From inbound (source of truth)
-- Run on inbound-service DB:
SELECT DATE(received_at AT TIME ZONE 'UTC') AS day,
       COUNT(*) AS putaway_count,
       SUM(qty_received) AS qty_received
FROM inbound_putaway_completed_event_log
WHERE warehouse_id = '<known>' AND received_at BETWEEN <D-7> AND <today>
GROUP BY day
ORDER BY day;
```

Numbers should match. If they don't, capture the diff and abort to
investigate before announcing completion.

### Step 9 — Announce Completion (2 min)

Post in `#wms-ops`:

```
:white_check_mark: admin-service read-model rebuild complete at <UTC time>.
Total duration: <minutes>. Dashboards verified.
worstLagSeconds: <value>.
```

Capture the rebuild record in the ops log:

| Field | Value |
|---|---|
| Date / operator | |
| Trigger | (schema migration / drift / corruption) |
| Topics replayed | (list) |
| Wall-clock duration | |
| Events replayed (per topic) | from `admin.projection.applied.count` delta |
| Errors (DLT count) | |
| Pre-rebuild backup file | (path / null) |

---

## Acknowledgement Preservation (Optional)

`alert_log.acknowledged_at` / `acknowledged_by` are admin-owned and lost
during truncate. To preserve them across rebuild:

Before § Step 3 truncate, capture:

```sql
CREATE TEMP TABLE alert_ack_backup AS
SELECT id, acknowledged_at, acknowledged_by
FROM admin_alert_log
WHERE acknowledged_at IS NOT NULL;
```

After § Step 7 smoke-test confirms projections are populated, restore:

```sql
UPDATE admin_alert_log AS al
SET acknowledged_at = b.acknowledged_at,
    acknowledged_by = b.acknowledged_by
FROM alert_ack_backup AS b
WHERE al.id = b.id;
```

This preserves operational acknowledgement state. Skip if the rebuild is
caused by acknowledgement-data corruption.

---

## Failure Modes

### Consumer Errors During Rebuild

If `admin.projection.error.count{topic}` increases:

1. Inspect the DLT (`<topic>.DLT`) for the failing record headers
2. Common causes:
   - Schema mismatch: a payload field type changed in source service but
     `admin-service` projection wasn't updated → patch projection code,
     re-deploy, the dedupe-table-already-wiped state means the message
     replays cleanly
   - Unknown enum value from a newer source-service version → same fix
   - DB constraint violation (e.g., a denormalised FK column points at a
     row not yet in `*_ref`): non-fatal under the runbook because
     `*_ref` projections converge eventually; rerun after master-service
     events catch up
3. If a payload is genuinely poison (corrupt JSON in source), publish a
   compaction-style tombstone in the source service or skip the offset
   manually (last resort)

### Lag Stalls Mid-Way

If lag plateaus and apply rate drops to 0:

1. `kubectl get pods -l app=admin-service` — is a pod stuck restarting?
2. Check DB connection pool saturation (Hikari metrics)
3. Check Kafka client logs for rebalance loops (consumer group churning)
4. If unrecoverable: scale to 0, restore from `admin-readmodel-backup-*.sql`
   (if Step 3 captured one), and re-attempt the rebuild after fixing root
   cause

### Discrepancy After Smoke-Test

If counts diverge from source:

1. Capture the diff (queries from § Step 8) and post in `#wms-ops`
2. Do NOT announce completion. The rebuild is partial — investigate before
   considering done
3. Likely root causes:
   - Source service published events that admin-service projection skipped
     (handler bug)
   - Broker retention truncated history before admin started consuming
     (raise broker retention and re-rebuild)
   - DLT contains relevant records — replay DLT to original topic and
     re-apply

### Need to Abort Mid-Procedure

After § Step 3 truncate but before § Step 7 smoke-test pass, the read-model
is incomplete. To abort cleanly:

1. Restore the backup captured pre-truncate (if Step 3 captured one):
   `psql -d admin < admin-readmodel-backup-<date>.sql`
2. The dedupe table also restores from backup, so no risk of double-apply
3. Scale back up

If no backup was captured (developer environment, brief production rebuild
window), the alternative is to **complete** the rebuild — there's no fast
revert.

---

## Frequency and Triggers

| Trigger | Frequency expectation |
|---|---|
| Read-model schema migration | Per migration that reshapes denormalised columns |
| Detected drift | < 1× / quarter (target) |
| Corruption recovery | Incident-driven |
| Routine "drift sweep" | Out of v1 scope; v2 may add a nightly reconciliation that emits a metric, not a rebuild |

Frequent rebuilds (> 1× / month) signal an underlying instability — the
projection handlers are dropping or mis-applying events. Investigate before
making rebuild a habit.

---

## References

- [`specs/services/admin-service/architecture.md`](../architecture.md)
  § Read-Model Rebuild Procedure, § Observability
- [`specs/services/admin-service/domain-model.md`](../domain-model.md)
  § Read-Model Tables
- [`specs/services/admin-service/idempotency.md`](../idempotency.md)
- [`specs/contracts/events/admin-events.md`](../../../contracts/events/admin-events.md)
  § Topic Layout, § Consumed Events
- [`specs/contracts/http/admin-service-api.md`](../../../contracts/http/admin-service-api.md)
  § 6.2 Projection Status
- `rules/traits/transactional.md` — T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I5 (DLT)
