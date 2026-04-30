package com.example.security.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LoginHistoryJpaRepository extends JpaRepository<LoginHistoryJpaEntity, Long> {

    boolean existsByEventId(String eventId);

    Optional<LoginHistoryJpaEntity> findFirstByAccountIdAndOutcomeOrderByOccurredAtDesc(
            String accountId, String outcome);

    @Query("SELECT h FROM LoginHistoryJpaEntity h WHERE h.accountId = :accountId " +
            "AND (:from IS NULL OR h.occurredAt >= :from) " +
            "AND (:to IS NULL OR h.occurredAt <= :to) " +
            "AND (:outcome IS NULL OR h.outcome = :outcome) " +
            "ORDER BY h.occurredAt DESC")
    Page<LoginHistoryJpaEntity> findByAccountIdAndFilters(
            @Param("accountId") String accountId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("outcome") String outcome,
            Pageable pageable);
}
