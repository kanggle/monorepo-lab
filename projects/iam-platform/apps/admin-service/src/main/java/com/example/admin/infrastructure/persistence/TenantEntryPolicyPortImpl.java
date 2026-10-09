package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.TenantEntryPolicyManagementPort;
import com.example.admin.application.port.TenantEntryPolicyPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-MONO-771 — JPA-backed {@link TenantEntryPolicyPort} (the entry-decision read) and
 * {@link TenantEntryPolicyManagementPort} (the S5 management read/write) over the same table — so what the
 * management API writes is exactly what the two entries read. Read failures propagate (never swallowed into
 * «off»): the callers turn them into a fail-closed refusal.
 *
 * <p>S5 write path: a first write is an explicit {@code persist} (the PK is assigned, not generated, and
 * the {@code @Version} is a primitive — Spring Data's {@code save} would {@code merge} it, which Hibernate
 * treats as an update of a row that does not exist). Two racing first writes collide on the PK; the loser
 * is reported as the same optimistic-lock conflict an update race produces (→ 409), not as a 500.
 */
@Component
@RequiredArgsConstructor
public class TenantEntryPolicyPortImpl implements TenantEntryPolicyPort, TenantEntryPolicyManagementPort {

    private final TenantEntryPolicyJpaRepository repository;
    private final AdminOperatorJpaRepository operatorRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Set<String> findTenantsRequiringMfa(Collection<String> tenantIds) {
        Set<String> candidates = new LinkedHashSet<>();
        if (tenantIds != null) {
            for (String t : tenantIds) {
                if (t != null && !t.isBlank() && !AdminOperator.PLATFORM_TENANT_ID.equals(t)) {
                    candidates.add(t);
                }
            }
        }
        if (candidates.isEmpty()) {
            return Set.of(); // nothing that can hold a policy — no read issued
        }
        return Set.copyOf(repository.findTenantIdsRequiringMfa(candidates));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntryPolicyView> find(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            return Optional.empty();
        }
        return repository.findById(tenantId).map(this::toView);
    }

    @Override
    @Transactional
    public EntryPolicyView save(String tenantId, boolean requireMfa, Long updatedByInternalId, Instant at) {
        Optional<TenantEntryPolicyJpaEntity> existing = repository.findById(tenantId);
        TenantEntryPolicyJpaEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.apply(requireMfa, updatedByInternalId, at);
            repository.saveAndFlush(entity);
        } else {
            entity = TenantEntryPolicyJpaEntity.create(tenantId, requireMfa, updatedByInternalId, at);
            try {
                entityManager.persist(entity);
                entityManager.flush();
            } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException e) {
                // A concurrent first write for the same tenant won the PK. Same outcome as an update race.
                throw new ObjectOptimisticLockingFailureException(TenantEntryPolicyJpaEntity.class, tenantId, e);
            }
        }
        return toView(entity);
    }

    private EntryPolicyView toView(TenantEntryPolicyJpaEntity e) {
        String updatedBy = e.getUpdatedBy() == null
                ? null
                : operatorRepository.findById(e.getUpdatedBy()).map(AdminOperatorJpaEntity::getOperatorId).orElse(null);
        return new EntryPolicyView(e.getTenantId(), e.isRequireMfa(), e.getUpdatedAt(), updatedBy);
    }
}
