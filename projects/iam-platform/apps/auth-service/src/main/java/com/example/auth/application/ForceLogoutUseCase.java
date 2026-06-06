package com.example.auth.application;

import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForceLogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final BulkInvalidationStore bulkInvalidationStore;
    private final AccessTokenInvalidationStore accessTokenInvalidationStore;
    private final TokenGeneratorPort tokenGeneratorPort;

    @Transactional
    public Result execute(String accountId) {
        int revokedCount = refreshTokenRepository.revokeAllByAccountId(accountId);
        Instant revokedAt = Instant.now();
        bulkInvalidationStore.invalidateAll(accountId, tokenGeneratorPort.refreshTokenTtlSeconds());
        accessTokenInvalidationStore.invalidateAccessBefore(
                accountId, revokedAt, tokenGeneratorPort.accessTokenTtlSeconds());
        return new Result(accountId, revokedCount, revokedAt);
    }

    public record Result(String accountId, int revokedTokenCount, Instant revokedAt) {}
}
