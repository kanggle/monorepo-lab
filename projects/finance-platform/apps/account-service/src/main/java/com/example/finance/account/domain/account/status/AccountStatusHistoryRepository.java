package com.example.finance.account.domain.account.status;

/**
 * Outbound port for the append-only account status history (F6). Only
 * {@code save} — no update/delete capability by design.
 */
public interface AccountStatusHistoryRepository {
    AccountStatusHistory save(AccountStatusHistory row);
}
