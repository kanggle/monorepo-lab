package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public Optional<RefreshToken> findByJti(String jti) {
        return refreshTokenJpaRepository.findByJti(jti).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = RefreshTokenJpaEntity.fromDomain(refreshToken);
        return refreshTokenJpaRepository.save(entity).toDomain();
    }

    @Override
    public boolean existsByRotatedFrom(String jti) {
        return refreshTokenJpaRepository.existsByRotatedFrom(jti);
    }

    @Override
    public int revokeAllByAccountId(String accountId) {
        return refreshTokenJpaRepository.revokeAllByAccountId(accountId);
    }

    @Override
    public List<String> findActiveJtisByAccountId(String accountId) {
        return refreshTokenJpaRepository.findActiveJtisByAccountId(accountId);
    }

    @Override
    public Optional<RefreshToken> findByRotatedFrom(String jti) {
        return refreshTokenJpaRepository.findByRotatedFrom(jti).map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public List<String> findActiveJtisByDeviceId(String deviceId) {
        return refreshTokenJpaRepository.findActiveJtisByDeviceId(deviceId);
    }

    @Override
    public int revokeAllByDeviceId(String deviceId) {
        return refreshTokenJpaRepository.revokeAllByDeviceId(deviceId);
    }
}
