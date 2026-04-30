package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminOperatorJpaRepository extends JpaRepository<AdminOperatorJpaEntity, Long> {
    Optional<AdminOperatorJpaEntity> findByEmail(String email);

    /**
     * Look up an operator by the external UUID (JWT {@code sub} claim).
     * The internal BIGINT {@code id} is never exposed to callers.
     */
    Optional<AdminOperatorJpaEntity> findByOperatorId(String operatorId);

    boolean existsByEmail(String email);

    /** Pagination for {@code GET /api/admin/operators} with optional status filter. */
    Page<AdminOperatorJpaEntity> findByStatus(String status, Pageable pageable);
}
