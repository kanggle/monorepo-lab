-- TASK-MONO-810 (ADR-MONO-084 P5) — indexes for the overview performance metrics
-- (GET /api/procurement/po/metrics) and the GET /po submit-date filter.
--
-- purchase_orders: poCount / poAmount read `tenant_id = ? AND submitted_at >= ? AND
-- submitted_at < ?`, and GET /po?submittedFrom=&submittedTo= filters the same range.
-- The V1 indexes lead with (tenant_id, status, …) / (tenant_id, supplier_id, …) and
-- cannot serve a range on submitted_at. DRAFT rows (submitted_at IS NULL) are not
-- wanted by either reader, so the index is partial.
CREATE INDEX idx_po_tenant_submitted
    ON purchase_orders (tenant_id, submitted_at)
    WHERE submitted_at IS NOT NULL;

-- po_status_history: receivedCount reads `tenant_id = ? AND to_status = 'RECEIVED' AND
-- occurred_at >= ? AND occurred_at < ?`. The V1 index (tenant_id, po_id, occurred_at)
-- serves per-PO timelines, not a per-status range across POs. po_id is included so
-- the scan can answer from the index alone (the query selects po_id, occurred_at).
CREATE INDEX idx_psh_tenant_to_status_occurred
    ON po_status_history (tenant_id, to_status, occurred_at)
    INCLUDE (po_id);
