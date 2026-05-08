package com.wms.admin.application.port;

import com.wms.admin.domain.AssignmentStatus;
import com.wms.admin.domain.UserRoleAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssignmentRepository {

    UserRoleAssignment save(UserRoleAssignment assignment);

    Optional<UserRoleAssignment> findById(UUID id);

    Optional<UserRoleAssignment> findActiveTriple(UUID userId, UUID roleId, UUID warehouseId);

    List<UserRoleAssignment> findActiveByUserId(UUID userId);

    List<UserRoleAssignment> findActiveByRoleId(UUID roleId);

    int countActiveByUserId(UUID userId);

    int countActiveByRoleId(UUID roleId);

    Page<UserRoleAssignment> search(UUID userId, UUID roleId, UUID warehouseId,
                                    AssignmentStatus status, Pageable pageable);
}
