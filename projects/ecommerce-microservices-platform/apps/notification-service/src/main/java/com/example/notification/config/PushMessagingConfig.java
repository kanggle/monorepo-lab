package com.example.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link WebPushProperties} binding for the Web Push (VAPID) adapter (TASK-BE-464). */
@Configuration
@EnableConfigurationProperties(WebPushProperties.class)
public class PushMessagingConfig {
}
