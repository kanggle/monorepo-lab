package com.wms.admin.application.fakes;

import com.wms.admin.application.repository.AssignmentRepository;
import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.UserRoleAssignment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class InMemoryAssignmentRepository implements AssignmentRepository {

    private final Map<UUID, UserRoleAssignment> store = new LinkedHashMap<>();

    @Override
    public UserRoleAssignment save(UserRoleAssignment assignment) {
        UserRoleAssignment stored = new UserRoleAssignment(
                assignment.id(), assignment.userId(), assignment.roleId(), assignment.warehouseId(),
                assignment.grantedAt(), assignment.grantedBy(),
                assignment.revokedAt(), assignment.revokedBy(),
                assignment.status(),
                store.containsKey(assignment.id()) ? assignment.version() + 1 : assignment.version(),
                assignment.createdAt(), assignment.updatedAt());
        store.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<UserRoleAssignment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<UserRoleAssignment> findActiveTriple(UUID userId, UUID roleId, UUID warehouseId) {
        return store.values().stream()
                .filter(a -> a.status() == AssignmentStatus.ACTIVE)
                .filter(a -> a.userId().equals(userId) && a.roleId().equals(roleId)
                        && Objects.equals(a.warehouseId(), warehouseId))
                .findFirst();
    }

    @Override
    public List<UserRoleAssignment> findActiveByUserId(UUID userId) {
        return store.values().stream()
                .filter(a -> a.status() == AssignmentStatus.ACTIVE && a.userId().equals(userId))
                .toList();
    }

    @Override
    public List<UserRoleAssignment> findActiveByRoleId(UUID roleId) {
        return store.values().stream()
                .filter(a -> a.status() == AssignmentStatus.ACTIVE && a.roleId().equals(roleId))
                .toList();
    }

    @Override
    public int countActiveByUserId(UUID userId) {
        return findActiveByUserId(userId).size();
    }

    @Override
    public int countActiveByRoleId(UUID roleId) {
        return findActiveByRoleId(roleId).size();
    }

    @Override
    public Page<UserRoleAssignment> search(UUID userId, UUID roleId, UUID warehouseId,
                                           AssignmentStatus status, Pageable pageable) {
        List<UserRoleAssignment> all = store.values().stream()
                .filter(a -> userId == null || a.userId().equals(userId))
                .filter(a -> roleId == null || a.roleId().equals(roleId))
                .filter(a -> warehouseId == null || warehouseId.equals(a.warehouseId()))
                .filter(a -> status == null || a.status() == status)
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }
}
