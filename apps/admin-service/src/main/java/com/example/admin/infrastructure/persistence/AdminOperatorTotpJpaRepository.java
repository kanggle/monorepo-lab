package com.example.admin.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminOperatorTotpJpaRepository extends JpaRepository<AdminOperatorTotpJpaEntity, Long> {

    /**
     * TASK-BE-084 — bulk fetch of TOTP rows for a set of operators, to avoid
     * the N+1 lookup in {@code OperatorAdminUseCase.bulkLoadEnrolledTotpIds}.
     */
    List<AdminOperatorTotpJpaEntity> findByOperatorIdIn(Collection<Long> operatorIds);
}
