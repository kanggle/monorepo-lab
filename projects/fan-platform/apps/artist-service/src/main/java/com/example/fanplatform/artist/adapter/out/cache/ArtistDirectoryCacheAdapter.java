package com.example.fanplatform.artist.adapter.out.cache;

import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.example.fanplatform.artist.application.port.out.ArtistDirectoryCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Redis read-through cache for the artist directory search. Fail-open: any
 * Redis error is logged and counted, never thrown — the application service
 * falls back to a direct DB query.
 *
 * <p>Key shape: {@code <namespace><tenantId>:<queryHash>}. The trailing
 * {@code <tenantId>:*} pattern lets {@link #invalidateAll(String)} drop every
 * key for one tenant in one SCAN+DEL.
 */
@Slf4j
@Component
public class ArtistDirectoryCacheAdapter implements ArtistDirectoryCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Counter cacheUnavailableCounter;
    private final String namespace;
    private final Duration ttl;

    public ArtistDirectoryCacheAdapter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${fanplatform.artist.cache.directory.namespace:cache:fan-platform:artist:directory:}") String namespace,
            @Value("${fanplatform.artist.cache.directory.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheUnavailableCounter = Counter.builder("artist_directory_cache_unavailable_total")
                .description("Number of artist directory cache operations that failed because Redis was unavailable.")
                .register(meterRegistry);
        this.namespace = namespace;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<DirectorySearchResult> get(String tenantId, String queryKey) {
        String key = key(tenantId, queryKey);
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, DirectorySearchResult.class));
        } catch (DataAccessException e) {
            log.warn("artist directory cache GET failed (Redis unavailable): tenant={} key={}", tenantId, queryKey);
            cacheUnavailableCounter.increment();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("artist directory cache GET failed: tenant={} key={} reason={}", tenantId, queryKey, e.toString());
            cacheUnavailableCounter.increment();
            return Optional.empty();
        }
    }

    @Override
    public void put(String tenantId, String queryKey, DirectorySearchResult value) {
        String key = key(tenantId, queryKey);
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, ttl);
        } catch (DataAccessException e) {
            log.warn("artist directory cache PUT failed (Redis unavailable): tenant={} key={}", tenantId, queryKey);
            cacheUnavailableCounter.increment();
        } catch (Exception e) {
            log.warn("artist directory cache PUT failed: tenant={} key={} reason={}", tenantId, queryKey, e.toString());
            cacheUnavailableCounter.increment();
        }
    }

    @Override
    public void invalidateAll(String tenantId) {
        String pattern = namespace + tenantId + ":*";
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (DataAccessException e) {
            log.warn("artist directory cache invalidate failed (Redis unavailable): tenant={}", tenantId);
            cacheUnavailableCounter.increment();
        } catch (Exception e) {
            log.warn("artist directory cache invalidate failed: tenant={} reason={}", tenantId, e.toString());
            cacheUnavailableCounter.increment();
        }
    }

    private String key(String tenantId, String queryKey) {
        return namespace + tenantId + ":" + queryKey;
    }
}
