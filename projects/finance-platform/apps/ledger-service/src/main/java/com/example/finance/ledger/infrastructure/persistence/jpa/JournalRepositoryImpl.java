package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link JournalRepository}. */
@Component
@RequiredArgsConstructor
public class JournalRepositoryImpl implements JournalRepository {

    private final JournalEntryJpaRepository entryJpa;
    private final JournalLineJpaRepository lineJpa;

    @Override
    public JournalEntry save(JournalEntry entry) {
        return entryJpa.save(entry);
    }

    @Override
    public Optional<JournalEntry> findByEntryId(String entryId, String tenantId) {
        return entryJpa.findByEntryIdAndTenantId(entryId, tenantId);
    }

    @Override
    public Optional<JournalEntry> findBySourceTransactionId(String sourceTransactionId,
                                                            String tenantId) {
        return entryJpa.findBySourceSourceTransactionIdAndTenantId(sourceTransactionId, tenantId);
    }

    @Override
    public Optional<JournalEntry> findBySourceEventId(String sourceEventId, String tenantId) {
        return entryJpa.findBySourceSourceEventIdAndTenantId(sourceEventId, tenantId);
    }

    @Override
    public LinePage findLinesByAccountCode(String ledgerAccountCode, String tenantId,
                                           int page, int size) {
        Page<JournalLine> p = lineJpa
                .findByLedgerAccountCodeAndTenantIdOrderByPostedAtDescIdDesc(
                        ledgerAccountCode, tenantId, PageRequest.of(page, size));
        List<LineRow> content = p.getContent().stream()
                .map(l -> new LineRow(l.entryId(), l.postedAt(), l))
                .toList();
        return new LinePage(content, p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public List<AccountTotals> accountTotals(String tenantId) {
        return lineJpa.accountTotals(tenantId).stream().map(this::toTotals).toList();
    }

    @Override
    public Optional<AccountTotals> accountTotals(String ledgerAccountCode, String tenantId) {
        return lineJpa.accountTotalsForCode(ledgerAccountCode, tenantId).stream()
                .findFirst().map(this::toTotals);
    }

    @Override
    public List<AccountTotals> accountTotalsUpTo(String tenantId, java.time.Instant to) {
        return lineJpa.accountTotalsUpTo(tenantId, to).stream().map(this::toTotals).toList();
    }

    @Override
    public long countEntriesUpTo(String tenantId, java.time.Instant to) {
        return entryJpa.countByTenantIdAndPostedAtBefore(tenantId, to);
    }

    private AccountTotals toTotals(AccountTotalsRow row) {
        return new AccountTotals(row.ledgerAccountCode(), row.currency().code(),
                row.debitMinor(), row.creditMinor());
    }
}
