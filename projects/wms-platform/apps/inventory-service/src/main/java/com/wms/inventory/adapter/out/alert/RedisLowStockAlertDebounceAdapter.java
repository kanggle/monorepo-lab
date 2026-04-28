package com.wms.inventory.adapter.out.alert;

import com.wms.inventory.application.port.out.LowStockAlertDebouncePort;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed debounce. Key {@code inventory:low-stock-alert:{inventoryId}}
 * with 1h TTL per the spec; absent key → fire and set, present key → skip.
 *
 * <p>Fail-open on Redis errors: any exception is swallowed and the method
 * returns {@code true} so the mutation flow never blocks on the alert path
 * (correctness > deduplication).
 */
public class RedisLowStockAlertDebounceAdapter implements LowStockAlertDebouncePort {

    private static final Logger log = LoggerFactory.getLogger(RedisLowStockAlertDebounceAdapter.class);
    private static final String PREFIX = "inventory:low-stock-alert:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public RedisLowStockAlertDebounceAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean shouldFire(UUID inventoryId) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + inventoryId, "1", TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn("Low-stock debounce SETNX failed; failing open and firing alert", e);
            return true;
        }
    }

    @Override
    public void clear(UUID inventoryId) {
        try {
            redis.delete(PREFIX + inventoryId);
        } catch (RuntimeException e) {
            log.warn("Low-stock debounce DEL failed", e);
        }
    }
}
