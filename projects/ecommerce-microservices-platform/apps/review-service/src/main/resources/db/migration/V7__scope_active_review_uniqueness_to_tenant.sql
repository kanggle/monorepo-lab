-- TASK-BE-540 case B — align the constraint's scope with the pre-check's scope.
--
-- V2 created uq_reviews_user_product_active on (user_id, product_id) WHERE status='ACTIVE'.
-- V5 then introduced tenant_id as the row-level isolation axis but added only a non-unique
-- helper index, leaving the unique index tenant-blind. The application pre-check
-- (ReviewRepositoryImpl:55 -> existsByUserIdAndProductIdAndStatusAndTenantId) has been
-- tenant-scoped since V5, so pre-check and constraint have been asserting different scopes.
--
-- REACHABILITY (AC-0, measured — this is a consistency fix, not an outage fix):
--   No normal path produces the same (user_id, product_id) ACTIVE pair under two tenants.
--   * product_id is a per-product UUID (Product.java:60, UUID.randomUUID()) and a product
--     belongs to exactly one tenant, so two tenants cannot share one.
--   * user_id is globally unique (uq_user_profiles_user_id) and V4__add_tenant_id.sql:9
--     states tenant_id is "the isolation column, not part of the identity key" — one user id
--     resolves to one tenant.
--   So the divergence was NOT producing 500s (nor, since TASK-BE-542, 409s). It is corrected
--   because a constraint that disagrees with its pre-check is a latent trap for whoever next
--   changes either side — not because it is firing today.
--
-- The new key is WIDER than the old one, so it cannot reject rows the old index admitted.
-- That is a deduction about the old index, not an observation of this database, so verify.
DO $$
DECLARE
    offending_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO offending_rows FROM (
        SELECT 1 FROM reviews
        WHERE status = 'ACTIVE'
        GROUP BY tenant_id, user_id, product_id
        HAVING COUNT(*) > 1
    ) AS duplicates;

    IF offending_rows > 0 THEN
        RAISE EXCEPTION
            'V7 aborted: % (tenant_id, user_id, product_id) group(s) already hold duplicate '
            'ACTIVE reviews; reconcile them before narrowing the index.', offending_rows;
    END IF;
END $$;

DROP INDEX IF EXISTS uq_reviews_user_product_active;

CREATE UNIQUE INDEX uq_reviews_tenant_user_product_active
    ON reviews (tenant_id, user_id, product_id)
    WHERE status = 'ACTIVE';
