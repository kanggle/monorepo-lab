package com.example.scmplatform.procurement.domain.po.status;

/**
 * Actor type for PO state transitions and audit history.
 *
 * <ul>
 *   <li>{@code BUYER} — internal buyer / operator submitting / canceling a PO.</li>
 *   <li>{@code OPERATOR} — privileged operator (admin) acting on behalf of buyer.</li>
 *   <li>{@code SUPPLIER} — external supplier (via webhook) ack-ing or sending ASN.</li>
 *   <li>{@code SYSTEM} — automated transition (e.g. ASN reconciliation).</li>
 * </ul>
 */
public enum ActorType {
    BUYER,
    OPERATOR,
    SUPPLIER,
    SYSTEM
}
