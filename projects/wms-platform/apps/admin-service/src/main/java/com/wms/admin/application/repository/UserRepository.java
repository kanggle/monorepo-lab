package com.wms.admin.application.repository;

import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserCode(String userCode);

    Page<User> search(UserStatus status, UUID warehouseId, String q, Pageable pageable);
}
