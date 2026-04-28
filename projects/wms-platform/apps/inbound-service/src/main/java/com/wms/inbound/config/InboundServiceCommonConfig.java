package com.wms.inbound.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Common cross-cutting beans used across consumers, adapters, and services.
 * Tests can override with a fixed-clock {@code @Primary} bean.
 */
@Configuration
public class InboundServiceCommonConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Programmatic transaction template used by background processors that
     * need to scope polling reads + status writes outside the {@code @Scheduled}
     * thread's default no-transaction state.
     */
    @Bean
    @ConditionalOnMissingBean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
