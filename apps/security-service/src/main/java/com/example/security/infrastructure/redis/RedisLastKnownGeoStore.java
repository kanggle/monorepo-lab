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

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLastKnownGeoStore implements LastKnownGeoStore {

    private static final String PREFIX = "security:geo:last:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<Snapshot> get(String accountId) {
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(PREFIX + accountId);
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
            log.warn("Redis geo:last GET failed for accountId={}", accountId, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String accountId, Snapshot snapshot) {
        try {
            String key = PREFIX + accountId;
            Map<String, String> fields = new HashMap<>();
            fields.put("country", snapshot.country());
            fields.put("lat", Double.toString(snapshot.latitude()));
            fields.put("lon", Double.toString(snapshot.longitude()));
            fields.put("occurred_at", snapshot.occurredAt().toString());
            redisTemplate.opsForHash().putAll(key, fields);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Redis geo:last PUT failed for accountId={}", accountId, e);
        }
    }
}
