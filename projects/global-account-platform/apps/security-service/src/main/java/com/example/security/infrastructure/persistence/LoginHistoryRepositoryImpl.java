package com.example.security.infrastructure.persistence;

import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.example.security.domain.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LoginHistoryRepositoryImpl implements LoginHistoryRepository {

    private final LoginHistoryJpaRepository jpaRepository;

    @Override
    public void save(LoginHistoryEntry entry) {
        LoginHistoryJpaEntity entity = LoginHistoryJpaEntity.from(
                entry.getEventId(),
                entry.getAccountId(),
                entry.getOutcome().name(),
                entry.getIpMasked(),
                entry.getUserAgentFamily(),
                entry.getDeviceFingerprint(),
                entry.getGeoCountry(),
                entry.getOccurredAt()
        );
        jpaRepository.save(entity);
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }

    @Override
    public Optional<LoginHistoryEntry> findLatestSuccessByAccountId(String accountId) {
        return jpaRepository.findFirstByAccountIdAndOutcomeOrderByOccurredAtDesc(
                accountId, LoginOutcome.SUCCESS.name()
        ).map(entity -> new LoginHistoryEntry(
                entity.getEventId(),
                entity.getAccountId(),
                LoginOutcome.valueOf(entity.getOutcome()),
                entity.getIpMasked(),
                entity.getUserAgentFamily(),
                entity.getDeviceFingerprint(),
                entity.getGeoCountry(),
                entity.getOccurredAt()
        ));
    }
}
