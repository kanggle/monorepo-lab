package com.example.scmplatform.procurement.application;

/**
 * Outcome of {@link SupplierApplicationService#register}. The {@code created}
 * flag is what lets the controller answer the contract's two-way idempotency
 * split without a second query:
 *
 * <ul>
 *   <li>{@code created=true} → 201, a row was inserted;</li>
 *   <li>{@code created=false} → 200, the tenant already had this {@code code}
 *       and <b>no</b> second row was written.</li>
 * </ul>
 *
 * <p>This record is also what {@code IdempotencyExecutor} caches, so a replay
 * of the same {@code Idempotency-Key} re-derives the same status line from the
 * cached value rather than from a second lookup.
 */
public record SupplierRegistration(SupplierView supplier, boolean created) {
}
