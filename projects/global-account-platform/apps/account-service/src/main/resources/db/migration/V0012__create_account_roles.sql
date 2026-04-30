-- TASK-BE-231: Create account_roles table for per-tenant role assignments.
-- Roles are stored as simple strings per the task spec — TenantRoleCatalog validation
-- is deferred to a later task. The provisioning API accepts any string[] and persists
-- them here. Unique constraint prevents duplicate role assignments per account.
CREATE TABLE account_roles (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id  VARCHAR(32)  NOT NULL,
    account_id VARCHAR(36)  NOT NULL,
    role_name  VARCHAR(50)  NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_account_role (tenant_id, account_id, role_name),
    CONSTRAINT fk_account_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT fk_account_roles_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
