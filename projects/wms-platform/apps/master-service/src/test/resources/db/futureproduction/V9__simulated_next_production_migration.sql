-- TASK-MONO-531 — TEST FIXTURE, not a shipping migration.
--
-- This file stands in for "the next production migration anyone adds to
-- master-service". master's production timeline currently tops out at V8, so V9
-- is literally the next number. It lives in src/test/resources/db/futureproduction
-- and is named by ExistingSeedVolumeMigrationOrderIT only — no application
-- profile, no compose override, and no `spring.flyway.locations` in the repo
-- references this directory.
--
-- Why it has to exist: the defect this suite pins is invisible until a
-- production migration resolves BELOW an already-applied dev-seed band. Without
-- a pending migration under the band there is nothing to be out of order, and
-- the control cell would pass for the wrong reason — it would be measuring an
-- empty question. (Precedent for a deliberate test-only migration directory:
-- erp approval-service src/test/resources/db/collision/.)

CREATE TABLE IF NOT EXISTS mono531_next_production_marker (
    id INTEGER PRIMARY KEY
);
