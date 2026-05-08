package com.wms.admin.application.port;

import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findById(UUID id);

    Optional<Role> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    Page<Role> search(RoleStatus status, Pageable pageable);
}
