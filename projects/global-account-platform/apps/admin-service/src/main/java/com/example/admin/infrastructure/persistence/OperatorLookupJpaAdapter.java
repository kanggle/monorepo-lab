package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA-backed adapter for {@link OperatorLookupPort}. Keeps the admin_operators
 * repository out of the application layer's import graph.
 */
@Component
@RequiredArgsConstructor
public class OperatorLookupJpaAdapter implements OperatorLookupPort {

    private final AdminOperatorJpaRepository repository;

    @Override
    public Optional<Long> findInternalId(String operatorId) {
        return repository.findByOperatorId(operatorId).map(AdminOperatorJpaEntity::getId);
    }

    @Override
    public Optional<OperatorSummary> findByOperatorId(String operatorId) {
        return repository.findByOperatorId(operatorId)
                .map(e -> new OperatorSummary(e.getId(), e.getOperatorId()));
    }
}
