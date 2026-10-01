-- TASK-BE-614 (ADR-MONO-078 A; account-service data-model.md § consumer_site_memberships /
-- § consumer_site_roles; multi-tenancy.md § 소비자 계정 풀 § 1). New tables only — no existing
-- table, column, index or constraint is touched (AC-1), so this applies unchanged to a volume that
-- already carries every migration up to V0029.
--
-- consumer_site_memberships: which consumer sites a pool account (accounts.tenant_id =
-- 'consumer-pool') has entered. No row for a site ⇒ no token for that site (TASK-BE-615) and a
-- first-visit consent screen (TASK-BE-616).
--   * site_tenant_id must never be 'consumer-pool' — enforced in the application
--     (ConsumerSiteMembership), as the data model says.
--   * ON DELETE CASCADE on the account FK mirrors account_roles (V0013): a physically removed
--     account takes its memberships with it. Accounts are normally soft-deleted, so this only
--     matters to hard deletes (test cleanup, regulated purge).
--   * idx (site_tenant_id, status) serves the site-scoped account lookups (§ 5).
CREATE TABLE consumer_site_memberships (
    account_id     VARCHAR(36) NOT NULL,
    site_tenant_id VARCHAR(32) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    consented_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id, site_tenant_id),
    INDEX idx_consumer_site_memberships_site_status (site_tenant_id, status),
    CONSTRAINT fk_consumer_site_memberships_account
        FOREIGN KEY (account_id) REFERENCES accounts(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_consumer_site_memberships_site_tenant
        FOREIGN KEY (site_tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT ck_consumer_site_memberships_status
        CHECK (status IN ('ACTIVE', 'LEFT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- consumer_site_roles: a pool account's site roles OUTSIDE the seed (e.g. fan ARTIST, ecommerce
-- SELLER once moved). Seed roles (CUSTOMER / FAN) are never stored — issuance adds them per site.
-- Not in account_roles on purpose: its composite FK (tenant_id, account_id) → accounts(tenant_id, id)
-- would break, because the role's tenant is the SITE and the account's tenant is the pool.
-- The FK to the membership (ON DELETE CASCADE) means a site role without that site's membership
-- cannot exist. No writer ships in TASK-BE-614 (TASK-BE-615 reads, TASK-BE-618 / TASK-MONO-745 write).
CREATE TABLE consumer_site_roles (
    account_id     VARCHAR(36) NOT NULL,
    site_tenant_id VARCHAR(32) NOT NULL,
    role_name      VARCHAR(64) NOT NULL,
    granted_by     VARCHAR(36) NULL,
    granted_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id, site_tenant_id, role_name),
    CONSTRAINT fk_consumer_site_roles_membership
        FOREIGN KEY (account_id, site_tenant_id)
        REFERENCES consumer_site_memberships(account_id, site_tenant_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
