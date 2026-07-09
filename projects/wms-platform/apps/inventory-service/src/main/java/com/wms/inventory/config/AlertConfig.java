package com.wms.inventory.config;

import com.wms.inventory.adapter.out.alert.InMemoryLowStockAlertDebounceAdapter;
import com.wms.inventory.adapter.out.alert.InMemoryLowStockThresholdAdapter;
import com.wms.inventory.adapter.out.alert.RedisLowStockAlertDebounceAdapter;
import com.wms.inventory.application.port.out.LowStockAlertDebouncePort;
import com.wms.inventory.application.port.out.LowStockThresholdPort;
import com.wms.inventory.application.port.out.LowStockThresholdWriterPort;
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
 * <p>Threshold: a single in-memory holder is exposed as both the read
 * ({@link LowStockThresholdPort}) and write ({@link LowStockThresholdWriterPort})
 * ports so {@code AdminSettingsConsumer}'s live updates are visible to
 * {@code findThreshold}. Bootstrap value = {@code inventory.alert.low-stock.default-threshold}
 * config; {@code admin.settings.changed} events then update it live (TASK-BE-459,
 * Option B — restart-durability deferred).
 *
 * <p>Debounce: Redis SETNX in real profiles, in-memory under {@code standalone}.
 */
@Configuration
public class AlertConfig {

    @Bean
    @ConditionalOnMissingBean(InMemoryLowStockThresholdAdapter.class)
    InMemoryLowStockThresholdAdapter lowStockThresholdHolder(
            @Value("${inventory.alert.low-stock.default-threshold:#{null}}") Integer defaultThreshold) {
        InMemoryLowStockThresholdAdapter adapter = new InMemoryLowStockThresholdAdapter();
        if (defaultThreshold != null) {
            adapter.setDefaultThreshold(defaultThreshold);
        }
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean(LowStockThresholdPort.class)
    LowStockThresholdPort lowStockThresholdPort(InMemoryLowStockThresholdAdapter holder) {
        return holder;
    }

    @Bean
    @ConditionalOnMissingBean(LowStockThresholdWriterPort.class)
    LowStockThresholdWriterPort lowStockThresholdWriterPort(InMemoryLowStockThresholdAdapter holder) {
        return holder;
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
