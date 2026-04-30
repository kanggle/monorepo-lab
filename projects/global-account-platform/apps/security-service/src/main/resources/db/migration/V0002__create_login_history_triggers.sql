-- Append-only triggers: prevent UPDATE and DELETE on login_history.
--
-- TASK-BE-051: Rewritten as single-statement triggers (no BEGIN/END, no custom
-- delimiter) to be compatible with Flyway 10 Community + flyway-mysql.
--
-- Root cause of the previous failure:
--   The prior version used `--flyway:delimiter=//` (no space, colon form). The
--   Flyway Community SQL parser does not recognise this directive form — it
--   expects comment-style directives to be proper comments (`-- ...` with a
--   leading space). As a result the line was passed through verbatim to MySQL,
--   which reported `SQL State 42000 / error 1064 near '--flyway:delimiter=//'`.
--   Testcontainers-based tests did not catch this because MySQL integration
--   tests are gated on `isDockerAvailable` and were effectively skipped on the
--   CI path that did not have Docker, while the e2e compose stack always runs
--   them. See TASK-BE-051 for the full investigation.
--
-- The rewrite below is semantically identical: both triggers raise
-- SQLSTATE '45000' with the same message text; they just no longer require a
-- compound (BEGIN...END) body, which is what forced the delimiter directive.
-- Pattern mirrors apps/account-service V0004.

CREATE TRIGGER trg_login_history_no_update
BEFORE UPDATE ON login_history FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UPDATE not allowed on login_history (append-only)';

CREATE TRIGGER trg_login_history_no_delete
BEFORE DELETE ON login_history FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DELETE not allowed on login_history (append-only)';
