package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Maps a completed account-service transaction to a balanced 2-line journal
 * entry (architecture.md § Posting Policy). A <b>pure</b> function — no Spring,
 * no I/O. Every produced entry satisfies {@code Σ debit == Σ credit} by
 * construction (and the {@link JournalEntry} factory re-asserts it).
 *
 * <table>
 *   <caption>Posting Policy</caption>
 *   <tr><th>type</th><th>debit</th><th>credit</th></tr>
 *   <tr><td>TOPUP</td><td>CASH_CLEARING</td><td>CUSTOMER_WALLET:{acct}</td></tr>
 *   <tr><td>WITHDRAW</td><td>CUSTOMER_WALLET:{acct}</td><td>CASH_CLEARING</td></tr>
 *   <tr><td>CAPTURE</td><td>CUSTOMER_WALLET:{acct}</td><td>SETTLEMENT_SUSPENSE</td></tr>
 *   <tr><td>TRANSFER</td><td>CUSTOMER_WALLET:{from}</td><td>CUSTOMER_WALLET:{to}</td></tr>
 *   <tr><td>HOLD / RELEASE</td><td>—</td><td>— (no entry)</td></tr>
 * </table>
 *
 * REVERSAL is NOT produced here — a reversal is built by
 * {@link JournalEntry#reversalEntry} from the looked-up original (the
 * {@code reversed.v1} path), so the policy keeps the forward mapping only.
 */
public final class PostingPolicy {

    private PostingPolicy() {
    }

    /**
     * The balanced entry for a completed transaction, or {@link Optional#empty()}
     * for a {@code HOLD}/{@code RELEASE} (no confirmed-balance change — documented,
     * not silently dropped).
     *
     * @param entryId  the id to assign the produced entry
     * @param postedAt the posting instant
     * @param source   the provenance (source transaction id + signed event id)
     * @param txn      the parsed completed transaction
     */
    public static Optional<JournalEntry> toEntry(String entryId, Instant postedAt,
                                                 SourceRef source, CompletedTransaction txn) {
        String tenantId = txn.tenantId();
        Money money = txn.money();
        String wallet = LedgerAccountCodes.customerWallet(txn.accountId());

        List<JournalLine> lines = switch (txn.type()) {
            case TOPUP -> List.of(
                    JournalLine.debit(tenantId, LedgerAccountCodes.CASH_CLEARING, money),
                    JournalLine.credit(tenantId, wallet, money));
            case WITHDRAW -> List.of(
                    JournalLine.debit(tenantId, wallet, money),
                    JournalLine.credit(tenantId, LedgerAccountCodes.CASH_CLEARING, money));
            case CAPTURE -> List.of(
                    JournalLine.debit(tenantId, wallet, money),
                    JournalLine.credit(tenantId, LedgerAccountCodes.SETTLEMENT_SUSPENSE, money));
            case TRANSFER -> {
                if (txn.counterpartyAccountId() == null || txn.counterpartyAccountId().isBlank()) {
                    throw new IllegalArgumentException(
                            "TRANSFER requires a counterpartyAccountId");
                }
                String toWallet = LedgerAccountCodes.customerWallet(txn.counterpartyAccountId());
                yield List.of(
                        JournalLine.debit(tenantId, wallet, money),
                        JournalLine.credit(tenantId, toWallet, money));
            }
            case HOLD, RELEASE -> null; // no confirmed-balance change → no entry
            case REVERSAL -> throw new IllegalArgumentException(
                    "REVERSAL is posted via the reversed.v1 path, not the forward policy");
        };

        if (lines == null) {
            return Optional.empty();
        }
        return Optional.of(JournalEntry.post(entryId, tenantId, postedAt, source, lines));
    }
}
