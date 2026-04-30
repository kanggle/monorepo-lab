package com.example.messaging.outbox;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@AutoConfiguration
@Import({OutboxJpaConfig.class, OutboxSchedulerConfig.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(OutboxJpaRepository outboxJpaRepository) {
        return new OutboxWriter(outboxJpaRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(OutboxJpaRepository outboxJpaRepository) {
        return new OutboxPublisher(outboxJpaRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @Profile("!standalone")
    @ConditionalOnProperty(name = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
    OutboxPollingScheduler outboxPollingScheduler(
            OutboxPublisher outboxPublisher,
            KafkaTemplate<String, String> kafkaTemplate,
            ThreadPoolTaskScheduler outboxTaskScheduler,
            OutboxProperties outboxProperties,
            ObjectProvider<OutboxFailureHandler> failureHandlerProvider) {
        return new OutboxPollingScheduler(outboxPublisher, kafkaTemplate, outboxTaskScheduler,
                outboxProperties, failureHandlerProvider.getIfAvailable());
    }
}
