-- idx_audit_log_user_id is redundant: the composite index (user_id, event_type)
-- can serve queries that filter only on user_id (leading column rule).
DROP INDEX IF EXISTS idx_audit_log_user_id;
