package com.example.admin.application.console;

/**
 * TASK-BE-304: Extensible per-operator per-product profile attributes
 * carrier for {@link ConsoleProduct}. In v1 only the finance product item
 * populates this (with {@code defaultAccountId} from
 * {@code admin_operators.finance_default_account_id}). Future per-operator
 * per-product attributes (wms {@code defaultWarehouseId}, scm
 * {@code defaultNodeId}, erp {@code defaultDepartmentId}) nest under the same
 * shape on the relevant product item.
 *
 * <p>Authoritative shape rationale:
 * {@code specs/contracts/http/console-registry-api.md § Per-operator profile
 * attributes (operatorContext)} — extensible carrier (not a top-level
 * polymorphic field) so future per-product attributes scale without per-product
 * item shape divergence.
 *
 * <p>Named {@code ProductOperatorContext} (not {@code OperatorContext}) to
 * disambiguate from the in-flight request {@code com.example.admin.application.OperatorContext}
 * (which represents the calling operator's JWT identity, a fundamentally
 * different concept).
 *
 * @param defaultAccountId opaque foreign-system account UUID; {@code null}
 *                         when not set (omitted from JSON via
 *                         {@code @JsonInclude(Include.NON_NULL)} on the
 *                         response DTO).
 */
public record ProductOperatorContext(String defaultAccountId) {
}
