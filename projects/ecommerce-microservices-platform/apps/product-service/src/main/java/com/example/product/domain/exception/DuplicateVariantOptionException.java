package com.example.product.domain.exception;

import java.util.UUID;

/**
 * Thrown when {@code POST /api/admin/products/{productId}/variants} would create a
 * second variant with an {@code optionName} that already exists on the same product.
 * Surfaces as HTTP 409 {@code DUPLICATE_VARIANT_OPTION} (TASK-BE-536).
 *
 * <p>The guarding mechanism is the DB-level {@code uq_product_variants_option UNIQUE
 * (product_id, option_name)} constraint (Flyway V5) — a natural key, not a client
 * {@code Idempotency-Key}: a duplicate {@code optionName} under the same product is
 * never a legitimate second request, so no caller coordination is needed (unlike the
 * stock/create/coupon endpoints, where two identical requests can both be genuine).
 * The unique index is scoped to {@code product_id} — the same {@code optionName} on a
 * DIFFERENT product is not a duplicate.
 *
 * <p>Two write paths reach this constraint (TASK-BE-541 AC-0): the add-variant endpoint
 * above, and {@code POST /api/admin/products} when the create request itself carries two
 * variants with the same {@code optionName}. The second case is rejected BEFORE persisting
 * — the collision is fully visible in the request payload, so there is nothing to leave to
 * the database.
 */
public class DuplicateVariantOptionException extends RuntimeException {

    public DuplicateVariantOptionException(UUID productId, String optionName, Throwable cause) {
        super("Variant option already exists on this product: productId=" + productId
                + ", optionName=" + optionName, cause);
    }

    /**
     * Product-registration variant: the duplicate is between two variants of the SAME
     * create request, so no product id exists yet and there is no DB exception to wrap
     * (TASK-BE-541).
     */
    public DuplicateVariantOptionException(String optionName) {
        super("Duplicate variant optionName within one product-registration request: optionName="
                + optionName);
    }
}
