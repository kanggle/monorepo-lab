package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.compliance.ComplianceReviewQueueEntry;
import com.example.finance.account.domain.compliance.ComplianceReviewQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Append-only operator queue — only {@code save} (F4/F8 no auto-clear). */
@Component
@RequiredArgsConstructor
public class ComplianceReviewQueueRepositoryAdapter
        implements ComplianceReviewQueueRepository {

    private final ComplianceReviewQueueJpaRepository jpa;

    @Override
    public ComplianceReviewQueueEntry save(ComplianceReviewQueueEntry entry) {
        return jpa.save(entry);
    }
}
