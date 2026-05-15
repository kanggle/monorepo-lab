package com.example.admin.application.console;

import java.util.List;

/**
 * TASK-BE-296: A single product/tenant registry item for platform-console.
 *
 * <p>Free of HTTP/framework types. The field set mirrors the normative item
 * shape in
 * {@code platform-console/specs/contracts/console-integration-contract.md § 2.2}
 * (the consumer contract — GAP is the producer). See
 * {@code specs/contracts/http/console-registry-api.md} for the GAP-side
 * authoritative envelope.
 *
 * @param productKey  one of {@code gap|wms|scm|erp|finance}
 * @param displayName catalog tile label
 * @param available   {@code false} → console renders "coming soon"
 * @param tenants     tenant ids the operator may select for this product
 * @param baseRoute   console-internal route prefix for the product's screens
 */
public record ConsoleProduct(
        String productKey,
        String displayName,
        boolean available,
        List<String> tenants,
        String baseRoute
) {
}
