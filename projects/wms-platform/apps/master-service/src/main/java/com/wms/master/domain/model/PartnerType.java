package com.wms.master.domain.model;

/**
 * Partner type per {@code specs/services/master-service/domain-model.md} §5.
 *
 * <ul>
 *   <li>{@code SUPPLIER} — appears as an ASN supplier (inbound side)
 *   <li>{@code CUSTOMER} — appears as an outbound order recipient
 *   <li>{@code BOTH} — both roles
 * </ul>
 *
 * <p>The type is mutable post-create (a supplier may later become a customer
 * as the business relationship evolves), distinct from the immutable
 * {@code partnerCode}.
 */
public enum PartnerType {
    SUPPLIER,
    CUSTOMER,
    BOTH
}
