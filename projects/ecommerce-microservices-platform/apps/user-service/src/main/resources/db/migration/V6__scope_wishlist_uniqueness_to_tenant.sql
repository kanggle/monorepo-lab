-- TASK-BE-546 — align the constraint's scope with the pre-check's scope.
--
-- V3 created uq_wishlist_items_user_product as UNIQUE (user_id, product_id).
-- V4 then introduced tenant_id as the row-level isolation axis but added only a
-- non-unique helper index (idx_wishlist_items_tenant_user), and its comment
-- explicitly noted the unique constraints were left "unchanged". The application
-- pre-check (WishlistItemRepositoryImpl:37 -> existsByUserIdAndProductIdAndTenantId,
-- fed by TenantContext.currentTenant()) has been tenant-scoped since V4, so
-- pre-check and constraint have been asserting different scopes ever since.
--
-- REACHABILITY (AC-0, measured — this is a consistency fix, not an outage fix):
--   No path produces the same (user_id, product_id) pair under two tenants, and here
--   the reason is structural, not merely conventional:
--   * wishlist_items.user_id carries a FK to user_profiles(user_id) (V3, fk_wishlist_
--     items_user_id), and user_profiles.user_id is globally unique (uq_user_profiles_
--     user_id). So every wishlist row resolves to exactly one profile, hence exactly
--     one tenant_id (denormalized from that parent). Two wishlist rows sharing a
--     user_id necessarily share a tenant_id — the two-column and three-column keys
--     reject the identical set of rows on this table.
--   * product_id is a per-product UUID and a product belongs to exactly one tenant.
--   * V4__add_tenant_id.sql:9 states tenant_id is "the isolation column, not part of
--     the identity key".
--   So the divergence is NOT producing 500s or 409s today, and the cross-tenant
--   "same (user_id, product_id)" row this wider key newly admits cannot actually be
--   constructed while that FK stands. It is corrected because a
--   constraint that cannot express the invariant it stands for (a user does not wish
--   for the same product twice, and that user is only meaningful inside a tenant) is a
--   latent trap for whoever next changes the user_id-issuance rule that currently makes
--   it unreachable — the guard has no way to warn them, because the reason it is safe
--   lives outside the constraint. Sibling review-service was already aligned in its V7.
--
-- The new key is WIDER than the old one (three columns vs two), so it cannot reject
-- rows the old constraint admitted. That is a deduction about the old constraint, not
-- an observation of this database, so verify before narrowing (AC-1).
DO $$
DECLARE
    offending_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO offending_rows FROM (
        SELECT 1 FROM wishlist_items
        GROUP BY tenant_id, user_id, product_id
        HAVING COUNT(*) > 1
    ) AS duplicates;

    IF offending_rows > 0 THEN
        RAISE EXCEPTION
            'V6 aborted: % (tenant_id, user_id, product_id) group(s) already hold '
            'duplicate wishlist rows; reconcile them before rescoping the constraint.',
            offending_rows;
    END IF;
END $$;

ALTER TABLE wishlist_items DROP CONSTRAINT uq_wishlist_items_user_product;

ALTER TABLE wishlist_items
    ADD CONSTRAINT uq_wishlist_items_tenant_user_product
    UNIQUE (tenant_id, user_id, product_id);
