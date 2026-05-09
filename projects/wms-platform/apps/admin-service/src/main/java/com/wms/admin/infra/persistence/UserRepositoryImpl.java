package com.wms.admin.infra.persistence;

import com.wms.admin.application.repository.UserRepository;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryImpl implements UserRepository {

    private final AdminUserJpaRepository repo;

    public UserRepositoryImpl(AdminUserJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public User save(User user) {
        AdminUserJpaEntity saved = repo.save(AdminUserJpaEntity.fromDomain(user));
        return saved.toDomain();
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repo.findById(id).map(AdminUserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmailIgnoreCase(String email) {
        return repo.findByEmailIgnoreCase(email).map(AdminUserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return repo.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUserCode(String userCode) {
        return repo.existsByUserCode(userCode);
    }

    @Override
    public Page<User> search(UserStatus status, UUID warehouseId, String q, Pageable pageable) {
        return repo.search(status, warehouseId, q, pageable).map(AdminUserJpaEntity::toDomain);
    }
}
