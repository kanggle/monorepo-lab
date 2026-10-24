package com.example.security.service.infrastructure.persistence;

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

    /**
     * TASK-BE-656 — the platform-scope {@code '*'} read: no tenant predicate. Only reachable with an explicit
     * {@code tenantId=*} ({@code security-query-api.md} § suspicious-events «테넌트»).
     */
    Page<SuspiciousEventJpaEntity> findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
            String accountId, Instant from, Instant to, Pageable pageable);

    Page<SuspiciousEventJpaEntity> findByAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
            String accountId, String ruleCode, Instant from, Instant to, Pageable pageable);
}
