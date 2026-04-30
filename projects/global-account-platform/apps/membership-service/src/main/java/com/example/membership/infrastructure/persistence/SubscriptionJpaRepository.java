package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, String> {

    List<SubscriptionJpaEntity> findByAccountId(String accountId);

    List<SubscriptionJpaEntity> findByAccountIdAndStatus(String accountId, SubscriptionStatus status);

    Optional<SubscriptionJpaEntity> findByAccountIdAndPlanLevelAndStatus(
            String accountId, PlanLevel planLevel, SubscriptionStatus status);

    @Query("SELECT s FROM SubscriptionJpaEntity s " +
           "WHERE s.status = :status " +
           "AND s.expiresAt IS NOT NULL " +
           "AND s.expiresAt <= :cutoff " +
           "ORDER BY s.expiresAt ASC")
    List<SubscriptionJpaEntity> findExpirable(@Param("status") SubscriptionStatus status,
                                              @Param("cutoff") LocalDateTime cutoff,
                                              Pageable pageable);
}
