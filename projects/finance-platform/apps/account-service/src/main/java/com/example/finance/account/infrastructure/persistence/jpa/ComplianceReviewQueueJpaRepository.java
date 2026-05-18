package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.compliance.ComplianceReviewQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceReviewQueueJpaRepository
        extends JpaRepository<ComplianceReviewQueueEntry, String> {
}
