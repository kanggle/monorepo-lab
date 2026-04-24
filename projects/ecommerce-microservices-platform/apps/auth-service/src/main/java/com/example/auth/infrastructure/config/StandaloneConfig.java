package com.example.auth.infrastructure.config;

import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.repository.OAuthStateStore;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone 프로파일용 인메모리 구현체 모음.
 * Docker 없이 auth-service를 로컬에서 실행할 때 사용한다.
 * Redis / Kafka 의존성이 없으며, 프로세스 재시작 시 데이터가 초기화된다.
 */
@Slf4j
@Configuration
@Profile("standalone")
public class StandaloneConfig {

    @Bean
    AccessTokenBlocklist inMemoryAccessTokenBlocklist() {
        return new AccessTokenBlocklist() {
            private final Map<String, Long> blockedTokens = new ConcurrentHashMap<>();
            private final Map<UUID, Long> blockedUsers = new ConcurrentHashMap<>();

            @Override
            public void block(String token, long ttlSeconds) {
                blockedTokens.put(token, System.currentTimeMillis() + ttlSeconds * 1000);
                log.debug("[standalone] access token blocked");
            }

            @Override
            public boolean isBlocked(String token) {
                Long exp = blockedTokens.get(token);
                return exp != null && exp > System.currentTimeMillis();
            }

            @Override
            public void blockByUserId(UUID userId, long ttlSeconds) {
                blockedUsers.put(userId, System.currentTimeMillis() + ttlSeconds * 1000);
                log.debug("[standalone] user {} blocked", userId);
            }

            @Override
            public boolean isUserBlocked(UUID userId) {
                Long exp = blockedUsers.get(userId);
                return exp != null && exp > System.currentTimeMillis();
            }
        };
    }

    @Bean
    OAuthStateStore inMemoryOAuthStateStore() {
        return new OAuthStateStore() {
            private final Map<String, String> store = new ConcurrentHashMap<>();

            @Override
            public void save(String state, String callbackUrl, Duration ttl) {
                store.put(state, callbackUrl);
            }

            @Override
            public Optional<String> getAndDelete(String state) {
                return Optional.ofNullable(store.remove(state));
            }
        };
    }

    @Bean
    RateLimiter inMemoryRateLimiter() {
        // standalone 모드에서는 rate limit을 적용하지 않는다
        return (clientKey, maxRequests, windowSeconds) -> false;
    }

    @Bean
    RefreshTokenStore inMemoryRefreshTokenStore() {
        return new RefreshTokenStore() {
            private final Map<String, UUID> tokenToUser = new ConcurrentHashMap<>();
            private final Map<String, Long> expiries = new ConcurrentHashMap<>();
            private final Set<String> revoked = ConcurrentHashMap.newKeySet();
            private final Map<UUID, Set<String>> userTokens = new ConcurrentHashMap<>();

            @Override
            public void save(String token, UUID userId, long ttlSeconds) {
                tokenToUser.put(token, userId);
                expiries.put(token, System.currentTimeMillis() + ttlSeconds * 1000);
                userTokens.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(token);
            }

            @Override
            public Optional<UUID> findUserIdByToken(String token) {
                if (isExpired(token) || revoked.contains(token)) return Optional.empty();
                return Optional.ofNullable(tokenToUser.get(token));
            }

            @Override
            public boolean isRevoked(String token) {
                return revoked.contains(token) || isExpired(token);
            }

            @Override
            public boolean invalidate(String token, long revokedTtlSeconds) {
                if (!tokenToUser.containsKey(token)) return false;
                revoked.add(token);
                UUID userId = tokenToUser.remove(token);
                if (userId != null) {
                    Set<String> tokens = userTokens.get(userId);
                    if (tokens != null) tokens.remove(token);
                }
                return true;
            }

            @Override
            public Set<String> findAllTokenHashesByUserId(UUID userId) {
                return userTokens.getOrDefault(userId, Collections.emptySet());
            }

            @Override
            public void invalidateAllByUserId(UUID userId, long revokedTtlSeconds) {
                Set<String> tokens = userTokens.remove(userId);
                if (tokens != null) {
                    tokens.forEach(t -> {
                        revoked.add(t);
                        tokenToUser.remove(t);
                    });
                }
            }

            private boolean isExpired(String token) {
                Long exp = expiries.get(token);
                return exp != null && exp < System.currentTimeMillis();
            }
        };
    }

    @Bean
    UserSessionRegistry inMemoryUserSessionRegistry() {
        return new UserSessionRegistry() {
            private final Map<UUID, LinkedList<String>> sessions = new ConcurrentHashMap<>();

            @Override
            public RegistrationResult registerSession(UUID userId, String refreshToken, long inactivityTimeoutSeconds) {
                LinkedList<String> list = sessions.computeIfAbsent(userId, k -> new LinkedList<>());
                list.addLast(refreshToken);
                String evicted = null;
                if (list.size() > 5) {
                    evicted = list.removeFirst();
                }
                return new RegistrationResult(refreshToken, evicted);
            }

            @Override
            public void rotateSession(UUID userId, String oldRefreshToken, String newRefreshToken, long inactivityTimeoutSeconds) {
                LinkedList<String> list = sessions.get(userId);
                if (list != null) {
                    list.remove(oldRefreshToken);
                    list.addLast(newRefreshToken);
                }
            }

            @Override
            public void removeSession(UUID userId, String refreshToken) {
                LinkedList<String> list = sessions.get(userId);
                if (list != null) list.remove(refreshToken);
            }

            @Override
            public void removeAllSessions(UUID userId) {
                sessions.remove(userId);
            }
        };
    }

    @Bean
    AuthEventPublisher noOpAuthEventPublisher() {
        return event -> log.info("[standalone] auth event: {}", event.getClass().getSimpleName());
    }
}
