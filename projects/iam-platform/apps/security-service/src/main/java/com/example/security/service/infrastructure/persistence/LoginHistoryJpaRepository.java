package com.example.security.service.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoginHistoryJpaRepository extends JpaRepository<LoginHistoryJpaEntity, Long> {

    boolean existsByEventId(String eventId);

    /**
     * TASK-BE-248: lookups must lead with {@code tenant_id} to use
     * {@code idx_login_history_tenant_account_outcome}.
     */
    Optional<LoginHistoryJpaEntity> findFirstByTenantIdAndAccountIdAndOutcomeOrderByOccurredAtDesc(
            String tenantId, String accountId, String outcome);

    @Query("SELECT h FROM LoginHistoryJpaEntity h " +
            "WHERE h.tenantId = :tenantId " +
            "AND h.accountId = :accountId " +
            "AND (:from IS NULL OR h.occurredAt >= :from) " +
            "AND (:to IS NULL OR h.occurredAt <= :to) " +
            "AND (:outcome IS NULL OR h.outcome = :outcome) " +
            "ORDER BY h.occurredAt DESC")
    Page<LoginHistoryJpaEntity> findByTenantAndAccountAndFilters(
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("outcome") String outcome,
            Pageable pageable);

    /**
     * TASK-MONO-811 — {@code (outcome, occurred_at)} of one tenant's rows with an outcome in
     * {@code outcomes} and {@code occurred_at ∈ [from, to)} (half-open). Projection only — no PII
     * column is selected. Served by {@code idx_login_history_tenant_outcome_occurred} (V0012).
     */
    @Query("SELECT new com.example.security.service.infrastructure.persistence.LoginOutcomeAt(h.outcome, h.occurredAt) "
            + "FROM LoginHistoryJpaEntity h "
            + "WHERE h.tenantId = :tenantId AND h.outcome IN :outcomes "
            + "AND h.occurredAt >= :from AND h.occurredAt < :to")
    List<LoginOutcomeAt> findOutcomesInWindow(@Param("tenantId") String tenantId,
                                              @Param("outcomes") Collection<String> outcomes,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);

    /**
     * TASK-MONO-811 — {@link #findOutcomesInWindow} across <b>every</b> tenant (the platform-scope
     * {@code '*'} caller). Served by {@code idx_login_history_occurred_at} (V0001).
     */
    @Query("SELECT new com.example.security.service.infrastructure.persistence.LoginOutcomeAt(h.outcome, h.occurredAt) "
            + "FROM LoginHistoryJpaEntity h "
            + "WHERE h.outcome IN :outcomes "
            + "AND h.occurredAt >= :from AND h.occurredAt < :to")
    List<LoginOutcomeAt> findOutcomesInWindowAllTenants(@Param("outcomes") Collection<String> outcomes,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);
}
