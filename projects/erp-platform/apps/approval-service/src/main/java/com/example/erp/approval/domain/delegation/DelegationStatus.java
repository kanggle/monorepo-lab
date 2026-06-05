package com.example.erp.approval.domain.delegation;

/**
 * Lifecycle status of a {@link DelegationGrant} (TASK-ERP-BE-013, 대결/위임).
 * A grant is {@code ACTIVE} from creation; {@code revoke} moves it to
 * {@code REVOKED} (terminal). Only an ACTIVE grant within its validity window
 * authorizes a delegate to act for the delegator (architecture.md § v2.1).
 */
public enum DelegationStatus {
    ACTIVE,
    REVOKED
}
