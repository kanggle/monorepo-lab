-- ===========================================================================
-- V6 — daily throughput moves from the UTC day to the KST day (ADR-MONO-084 D3,
-- TASK-MONO-808).
--
-- Until now both projections cut the day with atZone(UTC), so 00:00–09:00 KST
-- work landed on the previous day. The projections now cut with
-- KstPeriodBounds.dateOf; this migration rebuilds what was already written.
--
-- 🔴 The daily rows hold only «UTC date + totals» — no time of day — so they
-- cannot be re-cut on their own (15:00–24:00 UTC belongs to the next KST day).
-- Each side is rebuilt from per-event rows instead:
--
--   outbound — admin_shipment_summary already holds one row per shipment with
--              the same shipped_at the projection bucketed by and the same
--              total_qty it added. Rebuilt here, in this migration.
--   inbound  — admin keeps no per-putaway row. This migration adds one
--              (admin_putaway_fact, written by the projection from now on).
--              Rows from before this migration exist only in
--              inbound_db.inbound_outbox — another database, which Flyway here
--              cannot read — so the inbound rebuild is NOT run here. It is run
--              by the runbook after the backfill
--              (specs/services/admin-service/runbooks/read-model-rebuild.md
--               § KST day rebuild). A fresh volume needs neither.
--
-- Kafka replay is not an option: topics keep 7 days, so truncate-and-replay
-- would erase every older day instead of rebuilding it.
-- ===========================================================================

CREATE TABLE admin_putaway_fact (
    event_id        UUID            PRIMARY KEY,
    warehouse_id    UUID            NOT NULL,
    completed_at    TIMESTAMPTZ     NOT NULL,
    qty_received    INTEGER         NOT NULL DEFAULT 0,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_putaway_fact_completed_at
    ON admin_putaway_fact (completed_at);

CREATE INDEX idx_admin_putaway_fact_warehouse_completed_at
    ON admin_putaway_fact (warehouse_id, completed_at);

CREATE INDEX idx_admin_shipment_summary_warehouse_shipped_at
    ON admin_shipment_summary (warehouse_id, shipped_at);

-- ---------------------------------------------------------------------------
-- admin_rebuild_outbound_daily_kst() — re-derives every outbound daily row
-- from admin_shipment_summary on the KST day.
--
-- Totals are preserved when the counter and the summary agree. They can
-- disagree on an old volume: before TASK-BE-643 (2026-10-22) the counter
-- dropped shipments the summary kept, and a shipment re-delivered under a new
-- eventId is one summary row but two counter increments. The summary is the
-- per-shipment truth, so the rebuild follows it and reports the difference
-- rather than hiding it.
--
-- The table lock blocks concurrent projection upserts until commit, so an
-- event arriving during the rebuild is counted once (its summary row is either
-- visible to the INSERT … SELECT, or its upsert runs after the commit).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION admin_rebuild_outbound_daily_kst()
RETURNS TABLE (before_count BIGINT, before_qty BIGINT, after_count BIGINT, after_qty BIGINT)
LANGUAGE plpgsql AS $$
BEGIN
    LOCK TABLE admin_throughput_outbound_daily IN SHARE ROW EXCLUSIVE MODE;
    LOCK TABLE admin_shipment_summary IN SHARE MODE;

    SELECT COALESCE(SUM(shipment_count), 0), COALESCE(SUM(qty_shipped), 0)
      INTO before_count, before_qty
      FROM admin_throughput_outbound_daily;

    DELETE FROM admin_throughput_outbound_daily;

    INSERT INTO admin_throughput_outbound_daily
        (date, warehouse_id, shipment_count, qty_shipped, last_event_at, version)
    SELECT (s.shipped_at AT TIME ZONE 'Asia/Seoul')::date,
           s.warehouse_id,
           COUNT(*),
           COALESCE(SUM(s.total_qty), 0),
           MAX(s.last_event_at),
           1
      FROM admin_shipment_summary s
     GROUP BY 1, 2;

    SELECT COALESCE(SUM(shipment_count), 0), COALESCE(SUM(qty_shipped), 0)
      INTO after_count, after_qty
      FROM admin_throughput_outbound_daily;

    IF before_count <> after_count OR before_qty <> after_qty THEN
        RAISE NOTICE 'admin_rebuild_outbound_daily_kst: totals changed (count % -> %, qty % -> %) — the counter and admin_shipment_summary disagreed; the summary is followed',
            before_count, after_count, before_qty, after_qty;
    END IF;
    RETURN NEXT;
END;
$$;

-- ---------------------------------------------------------------------------
-- admin_rebuild_inbound_daily_kst() — re-derives every inbound daily row from
-- admin_putaway_fact on the KST day.
--
-- 🔴 Refuses when the facts hold less than the daily rows do. On a volume that
-- predates this migration the facts start empty, and rebuilding from them
-- would delete every earlier day. Backfill first (runbook § KST day rebuild),
-- then call this.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION admin_rebuild_inbound_daily_kst()
RETURNS TABLE (before_count BIGINT, before_qty BIGINT, after_count BIGINT, after_qty BIGINT)
LANGUAGE plpgsql AS $$
DECLARE
    fact_count BIGINT;
    fact_qty   BIGINT;
BEGIN
    LOCK TABLE admin_throughput_inbound_daily IN SHARE ROW EXCLUSIVE MODE;
    LOCK TABLE admin_putaway_fact IN SHARE MODE;

    SELECT COALESCE(SUM(putaway_count), 0), COALESCE(SUM(qty_received), 0)
      INTO before_count, before_qty
      FROM admin_throughput_inbound_daily;

    SELECT COUNT(*), COALESCE(SUM(qty_received), 0)
      INTO fact_count, fact_qty
      FROM admin_putaway_fact;

    IF fact_count < before_count OR fact_qty < before_qty THEN
        RAISE EXCEPTION 'admin_rebuild_inbound_daily_kst: admin_putaway_fact holds % putaways / % qty but the daily rows hold % / % — backfill from inbound_outbox first (runbook § KST day rebuild); nothing was changed',
            fact_count, fact_qty, before_count, before_qty;
    END IF;

    DELETE FROM admin_throughput_inbound_daily;

    INSERT INTO admin_throughput_inbound_daily
        (date, warehouse_id, putaway_count, qty_received, last_event_at, version)
    SELECT (f.completed_at AT TIME ZONE 'Asia/Seoul')::date,
           f.warehouse_id,
           COUNT(*),
           COALESCE(SUM(f.qty_received), 0),
           MAX(f.completed_at),
           1
      FROM admin_putaway_fact f
     GROUP BY 1, 2;

    SELECT COALESCE(SUM(putaway_count), 0), COALESCE(SUM(qty_received), 0)
      INTO after_count, after_qty
      FROM admin_throughput_inbound_daily;

    IF before_count <> after_count OR before_qty <> after_qty THEN
        RAISE NOTICE 'admin_rebuild_inbound_daily_kst: totals changed (count % -> %, qty % -> %)',
            before_count, after_count, before_qty, after_qty;
    END IF;
    RETURN NEXT;
END;
$$;

SELECT * FROM admin_rebuild_outbound_daily_kst();
