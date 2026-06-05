package com.example.erp.notification.config;

import com.example.erp.notification.domain.recipient.RecipientResolver;
import com.example.erp.notification.domain.render.NotificationFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the pure domain modules ({@link RecipientResolver} +
 * {@link NotificationFactory}) as beans. They carry no Spring annotations
 * (Hexagonal domain — framework-free, unit-testable) so they are wired here.
 */
@Configuration
public class DomainConfig {

    @Bean
    public RecipientResolver recipientResolver() {
        return new RecipientResolver();
    }

    @Bean
    public NotificationFactory notificationFactory() {
        return new NotificationFactory();
    }
}
