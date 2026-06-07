package com.example.shipping.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FulfillmentProperties.class)
class FulfillmentConfig {
}
