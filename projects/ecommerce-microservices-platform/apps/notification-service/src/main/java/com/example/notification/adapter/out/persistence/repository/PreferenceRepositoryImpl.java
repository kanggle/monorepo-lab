package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.mapper.PreferencePersistenceMapper;
import com.example.notification.application.port.out.PreferenceRepository;
import com.example.notification.domain.model.UserNotificationPreference;
import com.example.notification.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PreferenceRepositoryImpl implements PreferenceRepository {

    private final UserNotificationPreferenceJpaRepository jpaRepository;
    private final PreferencePersistenceMapper mapper;

    @Override
    public UserNotificationPreference save(UserNotificationPreference preference) {
        var entity = mapper.toEntity(preference);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserNotificationPreference> findByUserId(String userId) {
        return findByUserId(userId, TenantContext.currentTenant());
    }

    @Override
    public Optional<UserNotificationPreference> findByUserId(String userId, String tenantId) {
        return jpaRepository.findByUserIdAndTenantId(userId, TenantContext.resolveOrDefault(tenantId))
                .map(mapper::toDomain);
    }
}
