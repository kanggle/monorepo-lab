package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.LastKnownGeoStore.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-248 Phase 1: key format changed to
 * {@code security:geo:last:{tenantId}:{accountId}}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("RedisLastKnownGeoStore 단위 테스트 (tenant-aware key)")
class RedisLastKnownGeoStoreUnitTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Mock StringRedisTemplate redisTemplate;
    @Mock HashOperations<String, Object, Object> hashOps;

    private RedisLastKnownGeoStore store;

    private static final Instant FIXED = Instant.parse("2026-04-29T10:00:00Z");

    @BeforeEach
    void setUp() {
        store = new RedisLastKnownGeoStore(redisTemplate);
    }

    @Test
    @DisplayName("get — 모든 필드 존재 → Snapshot 반환")
    void get_allFieldsPresent_returnsSnapshot() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("security:geo:last:tenant-a:acc-1")).thenReturn(Map.of(
                "country", "KR",
                "lat", "37.5665",
                "lon", "126.9780",
                "occurred_at", FIXED.toString()
        ));

        Optional<Snapshot> result = store.get(TENANT_A, "acc-1");

        assertThat(result).isPresent();
        assertThat(result.get().country()).isEqualTo("KR");
        assertThat(result.get().latitude()).isEqualTo(37.5665);
        assertThat(result.get().longitude()).isEqualTo(126.9780);
        assertThat(result.get().occurredAt()).isEqualTo(FIXED);
    }

    @Test
    @DisplayName("get — 키 없음 (빈 map) → Optional.empty()")
    void get_emptyMap_returnsEmpty() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(any())).thenReturn(Map.of());

        assertThat(store.get(TENANT_A, "acc-2")).isEmpty();
    }

    @Test
    @DisplayName("get — 필드 일부 누락 → Optional.empty()")
    void get_missingField_returnsEmpty() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("security:geo:last:tenant-a:acc-3")).thenReturn(Map.of(
                "country", "US",
                "lat", "40.7128"
                // lon, occurred_at 누락
        ));

        assertThat(store.get(TENANT_A, "acc-3")).isEmpty();
    }

    @Test
    @DisplayName("get — Redis 오류 → fail-open (Optional.empty())")
    void get_redisException_returnsEmpty() {
        when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis down"));

        assertThat(store.get(TENANT_A, "acc-err")).isEmpty();
    }

    @Test
    @DisplayName("put — 정상: hash putAll 후 expire 호출 (tenant-aware key)")
    void put_normal_storesAllFieldsAndSetsExpiry() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        Snapshot snap = new Snapshot("JP", 35.6762, 139.6503, FIXED);

        store.put(TENANT_A, "acc-4", snap);

        verify(hashOps).putAll(eq("security:geo:last:tenant-a:acc-4"), anyMap());
        verify(redisTemplate).expire("security:geo:last:tenant-a:acc-4", Duration.ofDays(30));
    }

    @Test
    @DisplayName("put — Redis 오류 → 예외 흡수")
    void put_redisException_exceptionSwallowed() {
        when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis down"));

        store.put(TENANT_A, "acc-err", new Snapshot("KR", 37.0, 127.0, FIXED));
    }

    // ── TASK-BE-248: Cross-Tenant Isolation ──────────────────────────────────

    @Test
    @DisplayName("[cross-tenant] tenantA와 tenantB의 geo key가 서로 다름")
    void crossTenantIsolation_differentKeys() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        // tenantA has a known geo entry; tenantB has none for the same accountId
        when(hashOps.entries("security:geo:last:tenant-a:acc-1")).thenReturn(Map.of(
                "country", "KR",
                "lat", "37.5665",
                "lon", "126.9780",
                "occurred_at", FIXED.toString()
        ));
        when(hashOps.entries("security:geo:last:tenant-b:acc-1")).thenReturn(Map.of());

        Optional<Snapshot> fromA = store.get(TENANT_A, "acc-1");
        Optional<Snapshot> fromB = store.get(TENANT_B, "acc-1");

        assertThat(fromA).isPresent();
        assertThat(fromB).isEmpty();
    }
}
