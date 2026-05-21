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
 * <p>TASK-BE-304: {@code operatorContext} (nullable) is the extensible
 * per-operator per-product profile attributes carrier. In v1 only the finance
 * product item populates this (with {@code defaultAccountId} from
 * {@code admin_operators.finance_default_account_id}). Other 4 products always
 * pass {@code null} for {@code operatorContext} in v1 — the field is omitted
 * from the JSON via {@code @JsonInclude(Include.NON_NULL)} on the response
 * DTO.
 *
 * @param productKey       one of {@code gap|wms|scm|erp|finance}
 * @param displayName      catalog tile label
 * @param available        {@code false} → console renders "coming soon"
 * @param tenants          tenant ids the operator may select for this product
 * @param baseRoute        console-internal route prefix for the product's screens
 * @param operatorContext  per-operator per-product profile attributes (TASK-BE-304);
 *                         {@code null} = omit from JSON; finance only in v1
 */
public record ConsoleProduct(
        String productKey,
        String displayName,
        boolean available,
        List<String> tenants,
        String baseRoute,
        ProductOperatorContext operatorContext
) {
}
