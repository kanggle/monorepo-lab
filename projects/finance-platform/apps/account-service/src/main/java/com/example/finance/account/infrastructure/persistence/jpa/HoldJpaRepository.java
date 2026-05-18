package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.balance.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldJpaRepository extends JpaRepository<Hold, String> {

    Optional<Hold> findByIdAndTenantId(String id, String tenantId);

    List<Hold> findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            HoldStatus status, Instant before, org.springframework.data.domain.Pageable pageable);
}
