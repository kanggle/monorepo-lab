package com.wms.admin.application.fakes;

import com.wms.admin.application.port.UserRepository;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class InMemoryUserRepository implements UserRepository {

    private final Map<UUID, User> store = new LinkedHashMap<>();

    @Override
    public User save(User user) {
        // simulate version increment on every save (mimics @Version behaviour)
        User stored = new User(user.id(), user.userCode(), user.email(), user.name(), user.phone(),
                user.status(), user.defaultWarehouseId(),
                store.containsKey(user.id()) ? user.version() + 1 : user.version(),
                user.createdAt(), user.createdBy(), user.updatedAt(), user.updatedBy());
        store.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByEmailIgnoreCase(String email) {
        return store.values().stream()
                .filter(u -> u.email().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return findByEmailIgnoreCase(email).isPresent();
    }

    @Override
    public boolean existsByUserCode(String userCode) {
        return store.values().stream().anyMatch(u -> u.userCode().equals(userCode));
    }

    @Override
    public Page<User> search(UserStatus status, UUID warehouseId, String q, Pageable pageable) {
        List<User> all = store.values().stream()
                .filter(u -> status == null || u.status() == status)
                .toList();
        return new PageImpl<>(all, pageable, all.size());
    }

    public int size() {
        return store.size();
    }
}
