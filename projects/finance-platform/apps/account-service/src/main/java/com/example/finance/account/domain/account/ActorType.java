package com.example.finance.account.domain.account;

/**
 * Who initiated a fund/regulatory operation — recorded on
 * {@code account_status_history} and {@code audit_log} (F6) and emitted on
 * {@code finance.account.status.changed}.
 */
public enum ActorType {
    HOLDER,
    OPERATOR,
    COMPLIANCE,
    SYSTEM
}
