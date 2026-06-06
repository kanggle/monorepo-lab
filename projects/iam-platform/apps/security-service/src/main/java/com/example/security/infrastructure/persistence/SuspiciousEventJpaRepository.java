package com.example.security.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SuspiciousEventJpaRepository extends JpaRepository<SuspiciousEventJpaEntity, String> {

    /**
     * TASK-BE-248: tenant-aware finders use the rebuilt
     * {@code idx_suspicious_tenant_account_detected} index.
     */
    List<SuspiciousEventJpaEntity> findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
            String tenantId, String accountId, Instant from, Instant to);

    Page<SuspiciousEventJpaEntity> findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
            String tenantId, String accountId, Instant from, Instant to, Pageable pageable);

    Page<SuspiciousEventJpaEntity> findByTenantIdAndAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
            String tenantId, String accountId, String ruleCode, Instant from, Instant to, Pageable pageable);
}
