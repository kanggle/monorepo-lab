#!/usr/bin/env bash
# admin-throughput-kst-backfill.sh — TASK-MONO-808 (ADR-MONO-084 D3)
#
# One-off, for a volume whose admin_db predates V6. Copies every putaway admin has
# already applied from inbound_db.inbound_outbox into admin_putaway_fact, then rebuilds
# admin_throughput_inbound_daily on the KST day. A fresh volume needs none of this.
# (Outbound needs nothing: V6 rebuilt it from admin_shipment_summary.)
#
# Why the outbox: admin keeps no per-putaway row before V6, the daily rows hold no time
# of day, and Kafka keeps 7 days — inbound_outbox is the only exact source (append-only,
# DELETE rejected by trigger; row id = the event id admin deduped on).
#
# 🔴 Only events admin_event_dedupe records as APPLIED are copied. A published event
# admin has not consumed yet is left out on purpose: when it is consumed, the projection
# adds it to the daily row AND inserts its fact. Copying it now as well would count it
# twice after the rebuild.
#
# Safe to re-run: facts are ON CONFLICT DO NOTHING and the rebuild re-derives the rows.
# The rebuild refuses (and changes nothing) if the facts still hold less than the daily
# rows — read its message; usually it means this copy saw a different inbound_db.
#
# Usage (from projects/wms-platform, stack up):
#   scripts/admin-throughput-kst-backfill.sh            # dry run: counts only
#   scripts/admin-throughput-kst-backfill.sh --apply
#
# Override the two psql invocations if the stack is not this compose project:
#   PSQL_INBOUND='psql postgresql://inbound:...@host/inbound_db' \
#   PSQL_ADMIN='psql postgresql://admin:...@host/admin_db' scripts/admin-throughput-kst-backfill.sh --apply
set -euo pipefail

PSQL_INBOUND=${PSQL_INBOUND:-"docker compose exec -T postgres psql -U inbound -d inbound_db"}
PSQL_ADMIN=${PSQL_ADMIN:-"docker compose exec -T postgres psql -U admin -d admin_db"}

apply=false
case "${1:-}" in
  --apply) apply=true ;;
  "") ;;
  *) echo "usage: $0 [--apply]" >&2; exit 2 ;;
esac

csv=$(mktemp)
trap 'rm -f "$csv"' EXIT

# event_id, warehouse_id, completed_at (else the envelope's occurredAt — the projection's
# rule), Σ lines[].qtyReceived (a missing qtyReceived counts 0, as in the projection)
$PSQL_INBOUND -v ON_ERROR_STOP=1 -qAt -c "\copy (
  SELECT o.id,
         o.payload->'payload'->>'warehouseId',
         COALESCE(o.payload->'payload'->>'completedAt', o.payload->>'occurredAt'),
         COALESCE((SELECT SUM(COALESCE((l->>'qtyReceived')::int, 0))
                     FROM jsonb_array_elements(o.payload->'payload'->'lines') l), 0)
    FROM inbound_outbox o
   WHERE o.event_type = 'inbound.putaway.completed'
     AND o.published_at IS NOT NULL
) TO STDOUT WITH CSV" > "$csv"

echo "inbound_outbox published putaways: $(wc -l < "$csv" | tr -d ' ')"

finish=ROLLBACK
$apply && finish=COMMIT

{
  cat <<'SQL'
\set ON_ERROR_STOP 1
BEGIN;
CREATE TEMP TABLE putaway_backfill (
    event_id     UUID,
    warehouse_id UUID,
    completed_at TIMESTAMPTZ,
    qty_received INTEGER
) ON COMMIT DROP;
COPY putaway_backfill FROM STDIN WITH CSV;
SQL
  cat "$csv"
  printf '\\.\n'
  cat <<'SQL'
SELECT COUNT(*) AS outbox_rows,
       COUNT(d.event_id) AS applied_in_admin,
       COUNT(*) - COUNT(d.event_id) AS not_applied_yet_left_out
  FROM putaway_backfill b
  LEFT JOIN admin_event_dedupe d ON d.event_id = b.event_id AND d.outcome = 'APPLIED';
INSERT INTO admin_putaway_fact (event_id, warehouse_id, completed_at, qty_received)
SELECT b.event_id, b.warehouse_id, b.completed_at, b.qty_received
  FROM putaway_backfill b
  JOIN admin_event_dedupe d ON d.event_id = b.event_id AND d.outcome = 'APPLIED'
ON CONFLICT (event_id) DO NOTHING;
SELECT * FROM admin_rebuild_inbound_daily_kst();
SQL
  echo "$finish;"
} | $PSQL_ADMIN

if $apply; then
  echo "applied."
else
  echo "dry run — rolled back. Re-run with --apply."
fi
