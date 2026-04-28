package com.wms.inventory.config;

import com.wms.inventory.adapter.out.alert.InMemoryLowStockAlertDebounceAdapter;
import com.wms.inventory.adapter.out.alert.InMemoryLowStockThresholdAdapter;
import com.wms.inventory.adapter.out.alert.RedisLowStockAlertDebounceAdapter;
import com.wms.inventory.application.port.out.LowStockAlertDebouncePort;
import com.wms.inventory.application.port.out.LowStockThresholdPort;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires the low-stock threshold + debounce ports.
 *
 * <p>Threshold: in-memory in v1 — populated either by a configured default
 * or via test setup. Future task wires a Spring Kafka consumer for
 * {@code admin.settings.changed}; this configuration stays the same shape.
 *
 * <p>Debounce: Redis SETNX in real profiles, in-memory under {@code standalone}.
 */
@Configuration
public class AlertConfig {

    @Bean
    @ConditionalOnMissingBean(LowStockThresholdPort.class)
    LowStockThresholdPort lowStockThresholdPort(
            @Value("${inventory.alert.low-stock.default-threshold:#{null}}") Integer defaultThreshold) {
        InMemoryLowStockThresholdAdapter adapter = new InMemoryLowStockThresholdAdapter();
        if (defaultThreshold != null) {
            adapter.setDefaultThreshold(defaultThreshold);
        }
        return adapter;
    }

    @Bean
    @Profile("standalone")
    @ConditionalOnMissingBean(LowStockAlertDebouncePort.class)
    LowStockAlertDebouncePort inMemoryDebounce(Clock clock) {
        return new InMemoryLowStockAlertDebounceAdapter(clock);
    }

    @Bean
    @Profile("!standalone")
    @ConditionalOnMissingBean(LowStockAlertDebouncePort.class)
    LowStockAlertDebouncePort redisDebounce(StringRedisTemplate redisTemplate) {
        return new RedisLowStockAlertDebounceAdapter(redisTemplate);
    }
}
