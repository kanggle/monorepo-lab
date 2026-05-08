package com.wms.admin.infra.persistence;

import com.wms.admin.application.port.AssignmentRepository;
import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.UserRoleAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class AssignmentRepositoryAdapter implements AssignmentRepository {

    private final AdminAssignmentJpaRepository repo;

    public AssignmentRepositoryAdapter(AdminAssignmentJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserRoleAssignment save(UserRoleAssignment assignment) {
        return repo.save(AdminAssignmentJpaEntity.fromDomain(assignment)).toDomain();
    }

    @Override
    public Optional<UserRoleAssignment> findById(UUID id) {
        return repo.findById(id).map(AdminAssignmentJpaEntity::toDomain);
    }

    @Override
    public Optional<UserRoleAssignment> findActiveTriple(UUID userId, UUID roleId, UUID warehouseId) {
        return repo.findActiveTriple(userId, roleId, warehouseId).map(AdminAssignmentJpaEntity::toDomain);
    }

    @Override
    public List<UserRoleAssignment> findActiveByUserId(UUID userId) {
        return repo.findActiveByUserId(userId).stream().map(AdminAssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public List<UserRoleAssignment> findActiveByRoleId(UUID roleId) {
        return repo.findActiveByRoleId(roleId).stream().map(AdminAssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public int countActiveByUserId(UUID userId) {
        return repo.countActiveByUserId(userId);
    }

    @Override
    public int countActiveByRoleId(UUID roleId) {
        return repo.countActiveByRoleId(roleId);
    }

    @Override
    public Page<UserRoleAssignment> search(UUID userId, UUID roleId, UUID warehouseId,
                                           AssignmentStatus status, Pageable pageable) {
        return repo.search(userId, roleId, warehouseId, status, pageable)
                .map(AdminAssignmentJpaEntity::toDomain);
    }
}
