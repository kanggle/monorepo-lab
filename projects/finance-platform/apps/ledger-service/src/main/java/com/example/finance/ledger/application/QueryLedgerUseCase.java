package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.AccountLinePageView;
import com.example.finance.ledger.application.view.AccountLineView;
import com.example.finance.ledger.application.view.JournalEntryView;
import com.example.finance.ledger.application.view.JournalLineView;
import com.example.finance.ledger.application.view.LedgerAccountBalanceView;
import com.example.finance.ledger.application.view.TrialBalanceView;
import com.example.finance.ledger.domain.account.LedgerAccount;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import com.example.finance.ledger.domain.journal.repository.JournalRepository.AccountTotals;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-side use case (architecture.md § REST endpoints): journal entry detail,
 * a ledger account's lines + running balance, and the trial balance. Reads are
 * tenant-scoped and side-effect free; no {@code @Transactional} write.
 */
@Service
@RequiredArgsConstructor
public class QueryLedgerUseCase {

    private final JournalRepository journalRepository;
    private final LedgerAccountRepository ledgerAccountRepository;

    @Transactional(readOnly = true)
    public JournalEntryView getEntry(String entryId, String tenantId) {
        JournalEntry entry = journalRepository.findByEntryId(entryId, tenantId)
                .orElseThrow(() -> new JournalEntryNotFoundException(
                        "journal entry not found: " + entryId));
        List<JournalLineView> lines = entry.lines().stream()
                .map(l -> new JournalLineView(l.ledgerAccountCode(), l.direction(), l.money()))
                .toList();
        return new JournalEntryView(
                entry.entryId(), entry.postedAt(),
                entry.source().getSourceType(),
                entry.source().getSourceTransactionId(),
                entry.source().getSourceEventId(),
                entry.reversalOfEntryId(), lines, entry.isBalanced());
    }

    @Transactional(readOnly = true)
    public AccountLinePageView getAccountLines(String ledgerAccountCode, String tenantId,
                                               int page, int size) {
        requireAccount(ledgerAccountCode, tenantId);
        JournalRepository.LinePage linePage =
                journalRepository.findLinesByAccountCode(ledgerAccountCode, tenantId, page, size);
        List<AccountLineView> content = linePage.content().stream()
                .map(r -> new AccountLineView(r.entryId(), r.postedAt(),
                        r.line().direction(), r.line().money()))
                .toList();
        return new AccountLinePageView(content, linePage.page(), linePage.size(),
                linePage.totalElements(), linePage.totalPages());
    }

    @Transactional(readOnly = true)
    public LedgerAccountBalanceView getBalance(String ledgerAccountCode, String tenantId) {
        LedgerAccount account = requireAccount(ledgerAccountCode, tenantId);
        AccountTotals totals = journalRepository.accountTotals(ledgerAccountCode, tenantId)
                .orElse(new AccountTotals(ledgerAccountCode, Currency.KRW.code(), 0L, 0L));
        Currency currency = Currency.of(totals.currency());
        Money debitTotal = Money.of(totals.debitMinor(), currency);
        Money creditTotal = Money.of(totals.creditMinor(), currency);
        Money balance = account.runningBalance(debitTotal, creditTotal);
        return new LedgerAccountBalanceView(
                ledgerAccountCode, account.getType(), account.getNormalSide(),
                debitTotal, creditTotal, balance,
                LedgerAccount.balanceSide(debitTotal, creditTotal));
    }

    @Transactional(readOnly = true)
    public TrialBalanceView getTrialBalance(String tenantId) {
        List<AccountTotals> totals = journalRepository.accountTotals(tenantId);
        // First increment is single-currency; default to KRW when the ledger is empty.
        Currency currency = totals.isEmpty()
                ? Currency.KRW : Currency.of(totals.get(0).currency());
        List<TrialBalanceView.AccountTotalsView> accounts = new ArrayList<>(totals.size());
        Money grandDebit = Money.zero(currency);
        Money grandCredit = Money.zero(currency);
        for (AccountTotals t : totals) {
            Currency c = Currency.of(t.currency());
            Money debit = Money.of(t.debitMinor(), c);
            Money credit = Money.of(t.creditMinor(), c);
            accounts.add(new TrialBalanceView.AccountTotalsView(
                    t.ledgerAccountCode(), debit, credit));
            grandDebit = grandDebit.add(debit);
            grandCredit = grandCredit.add(credit);
        }
        return new TrialBalanceView(accounts, grandDebit, grandCredit,
                grandDebit.equals(grandCredit));
    }

    private LedgerAccount requireAccount(String ledgerAccountCode, String tenantId) {
        return ledgerAccountRepository.findByCode(ledgerAccountCode, tenantId)
                .orElseThrow(() -> new LedgerAccountNotFoundException(
                        "ledger account not found: " + ledgerAccountCode));
    }
}
