package com.example.erp.masterdata.domain.employee.link;

/**
 * Employee ↔ IAM account link proposal lifecycle (masterdata-api.md § Employee ↔ IAM
 * account link § Data model):
 *
 * <pre>
 * PENDING ─(account owner accepts, owner ≠ proposer)→ ACCEPTED ★
 *         ─(account owner declines)────────────────→ DECLINED ★
 *         ─(erp.write in the employee's scope)─────→ REVOKED  ★
 * </pre>
 *
 * ★ terminal — a non-PENDING row is immutable decision history.
 */
public enum LinkProposalStatus {
    PENDING, ACCEPTED, DECLINED, REVOKED
}
