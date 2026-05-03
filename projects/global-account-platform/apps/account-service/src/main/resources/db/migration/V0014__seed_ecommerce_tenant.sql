-- TASK-MONO-027: register the 'ecommerce' tenant so accounts.tenant_id FK
-- (V0010) and account_roles.tenant_id FK (V0012) succeed when consumer
-- signup or operator provisioning runs against the ecommerce tenant.
--
-- Mirrors V0009's pattern (fan-platform tenant seed). wms tenant is not
-- seeded in this table because wms uses GAP only for OAuth client_credentials
-- service-to-service flows in v1; consumer-style provisioning that hits
-- accounts.tenant_id is ecommerce / fan-platform only.
--
-- INSERT IGNORE keeps the migration idempotent so re-running on environments
-- that may have hand-seeded the row stays safe.
-- account-service's TenantType enum is {B2C_CONSUMER, B2B_ENTERPRISE} (see
-- domain/tenant/TenantType.java). The auth-service oauth_clients table has
-- its own independent tenant_type column (V0008) where ecommerce stores 'B2C'.
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('ecommerce', 'E-Commerce Platform', 'B2C_CONSUMER', 'ACTIVE', NOW(6), NOW(6));
