package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** TASK-BE-631 (V0052) — {@code domain_permission_set}. */
public interface DomainPermissionSetJpaRepository extends JpaRepository<DomainPermissionSetJpaEntity, Long> {

    Optional<DomainPermissionSetJpaEntity> findBySetId(String setId);

    List<DomainPermissionSetJpaEntity> findByTenantIdIsNull();

    List<DomainPermissionSetJpaEntity> findByTenantIdOrderByCreatedAtAscIdAsc(String tenantId);
}
