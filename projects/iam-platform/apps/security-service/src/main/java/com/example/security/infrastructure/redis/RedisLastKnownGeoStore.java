package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.LastKnownGeoStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link LastKnownGeoStore}.
 *
 * <p>Key format: {@code security:geo:last:{tenantId}:{accountId}}.
 * Each tenant/account pair maintains a completely independent geo snapshot, so a
 * login from one tenant never influences the geo-anomaly baseline for another tenant.</p>
 *
 * <p>TASK-BE-248 Phase 1: the key scheme changed from
 * {@code security:geo:last:{accountId}} to include {@code tenantId}.
 * Legacy keys are not actively removed — they expire after the TTL (30 days).
 * During the transition window {@link GeoAnomalyRule} and {@link ImpossibleTravelRule}
 * will return false-negatives for accounts whose baseline was stored under the old key.
 * This is acceptable (safe bias) and preferable to cross-tenant data leakage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLastKnownGeoStore implements LastKnownGeoStore {

    private static final String PREFIX = "security:geo:last:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<Snapshot> get(String tenantId, String accountId) {
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(key(tenantId, accountId));
            if (raw == null || raw.isEmpty()) {
                return Optional.empty();
            }
            String country = (String) raw.get("country");
            String lat = (String) raw.get("lat");
            String lon = (String) raw.get("lon");
            String occ = (String) raw.get("occurred_at");
            if (country == null || lat == null || lon == null || occ == null) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(country, Double.parseDouble(lat), Double.parseDouble(lon), Instant.parse(occ)));
        } catch (Exception e) {
            log.warn("Redis geo:last GET failed for tenantId={}, accountId={}", tenantId, accountId, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String tenantId, String accountId, Snapshot snapshot) {
        try {
            String k = key(tenantId, accountId);
            Map<String, String> fields = new HashMap<>();
            fields.put("country", snapshot.country());
            fields.put("lat", Double.toString(snapshot.latitude()));
            fields.put("lon", Double.toString(snapshot.longitude()));
            fields.put("occurred_at", snapshot.occurredAt().toString());
            redisTemplate.opsForHash().putAll(k, fields);
            redisTemplate.expire(k, TTL);
        } catch (Exception e) {
            log.warn("Redis geo:last PUT failed for tenantId={}, accountId={}", tenantId, accountId, e);
        }
    }

    private static String key(String tenantId, String accountId) {
        return PREFIX + tenantId + ":" + accountId;
    }
}
