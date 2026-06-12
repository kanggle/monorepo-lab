package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/** Spring Data repository for journal entries (insert-only; immutable, F3). */
public interface JournalEntryJpaRepository extends JpaRepository<JournalEntry, String> {

    Optional<JournalEntry> findByEntryIdAndTenantId(String entryId, String tenantId);

    Optional<JournalEntry> findBySourceSourceTransactionIdAndTenantId(
            String sourceTransactionId, String tenantId);

    /** Count of entries with {@code postedAt < to}, tenant-scoped (period entryCount). */
    long countByTenantIdAndPostedAtBefore(String tenantId, Instant to);
}
