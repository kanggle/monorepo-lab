package com.wms.admin.infra.persistence;

import com.wms.admin.application.repository.RoleRepository;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class RoleRepositoryImpl implements RoleRepository {

    private final AdminRoleJpaRepository repo;

    public RoleRepositoryImpl(AdminRoleJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Role save(Role role) {
        return repo.save(AdminRoleJpaEntity.fromDomain(role)).toDomain();
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return repo.findById(id).map(AdminRoleJpaEntity::toDomain);
    }

    @Override
    public Optional<Role> findByRoleCode(String roleCode) {
        return repo.findByRoleCode(roleCode).map(AdminRoleJpaEntity::toDomain);
    }

    @Override
    public boolean existsByRoleCode(String roleCode) {
        return repo.existsByRoleCode(roleCode);
    }

    @Override
    public Page<Role> search(RoleStatus status, Pageable pageable) {
        return repo.search(status, pageable).map(AdminRoleJpaEntity::toDomain);
    }
}
