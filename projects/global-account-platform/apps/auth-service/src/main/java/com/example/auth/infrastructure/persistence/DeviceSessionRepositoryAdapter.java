package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeviceSessionRepositoryAdapter implements DeviceSessionRepository {

    private final DeviceSessionJpaRepository jpa;

    @Override
    public DeviceSession save(DeviceSession session) {
        DeviceSessionJpaEntity entity = (session.getId() != null
                ? jpa.findById(session.getId())
                    .map(existing -> {
                        existing.updateFromDomain(session);
                        return existing;
                    })
                    .orElseGet(() -> DeviceSessionJpaEntity.fromDomain(session))
                : jpa.findByDeviceId(session.getDeviceId())
                    .map(existing -> {
                        existing.updateFromDomain(session);
                        return existing;
                    })
                    .orElseGet(() -> DeviceSessionJpaEntity.fromDomain(session)));
        return jpa.save(entity).toDomain();
    }

    @Override
    public Optional<DeviceSession> findByDeviceId(String deviceId) {
        return jpa.findByDeviceId(deviceId).map(DeviceSessionJpaEntity::toDomain);
    }

    @Override
    public Optional<DeviceSession> findActiveByAccountAndFingerprint(String accountId, String fingerprint) {
        return jpa.findActiveByAccountAndFingerprint(accountId, fingerprint)
                .map(DeviceSessionJpaEntity::toDomain);
    }

    @Override
    public List<DeviceSession> findActiveByAccountId(String accountId) {
        return jpa.findActiveByAccountIdOrderByLastSeenDesc(accountId).stream()
                .map(DeviceSessionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countActiveByAccountId(String accountId) {
        return jpa.countActiveByAccountId(accountId);
    }

    @Override
    public List<DeviceSession> findOldestActiveByAccountId(String accountId, int limit) {
        return jpa.findOldestActiveByAccountId(accountId, PageRequest.of(0, limit)).stream()
                .map(DeviceSessionJpaEntity::toDomain)
                .toList();
    }
}
