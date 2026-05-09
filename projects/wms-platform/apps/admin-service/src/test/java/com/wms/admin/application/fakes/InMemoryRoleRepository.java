package com.wms.admin.application.fakes;

import com.wms.admin.application.repository.RoleRepository;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class InMemoryRoleRepository implements RoleRepository {

    private final Map<UUID, Role> store = new LinkedHashMap<>();

    @Override
    public Role save(Role role) {
        Role stored = new Role(role.id(), role.roleCode(), role.name(), role.description(),
                role.permissionsJson(), role.status(), role.isBuiltin(),
                store.containsKey(role.id()) ? role.version() + 1 : role.version(),
                role.createdAt(), role.createdBy(), role.updatedAt(), role.updatedBy());
        store.put(stored.id(), stored);
        return stored;
    }

    public void seed(Role role) {
        store.put(role.id(), role);
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Role> findByRoleCode(String roleCode) {
        return store.values().stream().filter(r -> r.roleCode().equals(roleCode)).findFirst();
    }

    @Override
    public boolean existsByRoleCode(String roleCode) {
        return findByRoleCode(roleCode).isPresent();
    }

    @Override
    public Page<Role> search(RoleStatus status, Pageable pageable) {
        List<Role> all = store.values().stream()
                .filter(r -> status == null || r.status() == status)
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }
}
