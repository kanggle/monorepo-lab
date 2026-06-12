package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for accounting periods (the OPEN→CLOSED aggregate). */
public interface AccountingPeriodJpaRepository extends JpaRepository<AccountingPeriod, String> {

    Optional<AccountingPeriod> findByPeriodIdAndTenantId(String periodId, String tenantId);

    List<AccountingPeriod> findByTenantIdOrderByFromDesc(String tenantId);

    /**
     * Periods whose half-open window overlaps {@code [from, to)} for the tenant —
     * two windows overlap iff each starts before the other ends (abutting windows
     * do NOT overlap).
     */
    @Query("""
            select p from AccountingPeriod p
            where p.tenantId = :tenantId
              and p.from < :to
              and :from < p.to
            """)
    List<AccountingPeriod> findOverlapping(@Param("tenantId") String tenantId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    /**
     * The period of {@code status} whose window covers {@code postedAt}
     * ({@code from ≤ postedAt < to}) for the tenant (the posting guard).
     */
    @Query("""
            select p from AccountingPeriod p
            where p.tenantId = :tenantId
              and p.status = :status
              and p.from <= :postedAt
              and :postedAt < p.to
            """)
    List<AccountingPeriod> findCovering(@Param("tenantId") String tenantId,
                                        @Param("postedAt") Instant postedAt,
                                        @Param("status") PeriodStatus status);
}
