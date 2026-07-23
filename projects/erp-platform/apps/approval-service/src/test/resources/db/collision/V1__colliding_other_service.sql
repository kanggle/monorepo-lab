-- TASK-ERP-BE-035 regression fixture (NOT a production migration).
-- Stands in for masterdata/notification "winning" version 1 first in a SHARED
-- erp_db: a V1 whose checksum differs from approval's real V1__init.sql. Loaded
-- only by FlywayHistoryIsolationIntegrationTest to reproduce the collision that
-- crash-looped approval on the demo host. Distinct content = distinct checksum.
CREATE TABLE erp035_other_service_probe (
    id BIGINT PRIMARY KEY
);
