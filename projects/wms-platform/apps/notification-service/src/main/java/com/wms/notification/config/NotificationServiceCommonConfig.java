package com.wms.notification.config;

import com.wms.notification.adapter.outbound.slack.SlackChannelProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cross-cutting beans. Mirrors the {@code InventoryServiceCommonConfig}
 * shape so unit tests can override the {@link Clock} via a {@code @Primary}
 * fixed-clock bean.
 */
@Configuration
@EnableConfigurationProperties(SlackChannelProperties.class)
public class NotificationServiceCommonConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
