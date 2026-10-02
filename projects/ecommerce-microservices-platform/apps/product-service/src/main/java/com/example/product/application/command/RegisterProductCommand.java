package com.example.product.application.command;

import java.util.List;
import java.util.UUID;

public record RegisterProductCommand(
        String name,
        String description,
        long price,
        UUID categoryId,
        String thumbnailUrl,
        String sellerId,
        List<VariantCommand> variants,
        /**
         * Required by {@code RegisterProductService.register} (TASK-BE-536) — a
         * replayed registration must not create a second product with a second
         * stock ledger. {@code null} in the pre-existing backward-compat
         * constructors below (they throw {@code IdempotencyKeyRequiredException} at
         * the service boundary); new call sites should use the canonical
         * constructor and supply one.
         */
        String idempotencyKey,
        /**
         * Optional fan artist collection (ADR-MONO-079 D3). {@code null}/blank =
         * no collection. {@code null} in every backward-compat constructor.
         */
        String collectionRef
) {
    public RegisterProductCommand(String name, String description, long price, UUID categoryId,
                                  String thumbnailUrl, String sellerId, List<VariantCommand> variants,
                                  String idempotencyKey) {
        this(name, description, price, categoryId, thumbnailUrl, sellerId, variants, idempotencyKey, null);
    }

    public RegisterProductCommand(String name, String description, long price, UUID categoryId,
                                  List<VariantCommand> variants) {
        this(name, description, price, categoryId, null, null, variants, null, null);
    }

    public RegisterProductCommand(String name, String description, long price, UUID categoryId,
                                  String thumbnailUrl, List<VariantCommand> variants) {
        this(name, description, price, categoryId, thumbnailUrl, null, variants, null, null);
    }

    public RegisterProductCommand(String name, String description, long price, UUID categoryId,
                                  String thumbnailUrl, String sellerId, List<VariantCommand> variants) {
        this(name, description, price, categoryId, thumbnailUrl, sellerId, variants, null, null);
    }
}
