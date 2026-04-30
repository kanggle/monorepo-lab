package com.example.admin.infrastructure.persistence.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminRoleJpaRepository extends JpaRepository<AdminRoleJpaEntity, Long> {
    Optional<AdminRoleJpaEntity> findByName(String name);

    /** Bulk lookup for TASK-BE-083 — resolves a list of role names in one query. */
    List<AdminRoleJpaEntity> findByNameIn(Collection<String> names);
}
