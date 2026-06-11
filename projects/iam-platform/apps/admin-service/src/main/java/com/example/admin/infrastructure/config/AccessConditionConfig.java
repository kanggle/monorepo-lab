package com.example.admin.infrastructure.config;

import com.example.security.access.SourceIpCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-MONO-026 (axis ② 2단계) — wires the {@code SOURCE_IP} access condition for
 * admin-service from {@link AdminAccessConditionProperties} (domain guard-config,
 * § D3-B). The {@link SourceIpCondition} bean is consumed by
 * {@code RequiresPermissionAspect} as the 4th authorization gate.
 *
 * <p>The bean is always present in the running service (built from config that
 * defaults to an empty allowlist ⇒ {@link SourceIpCondition#isConfigured()} false
 * ⇒ net-zero). {@code @WebMvcTest} slices that do not import this config simply
 * have no bean, which the aspect treats as net-zero too — so existing slice tests
 * are unaffected.
 */
@Configuration
@EnableConfigurationProperties(AdminAccessConditionProperties.class)
public class AccessConditionConfig {

    @Bean
    public SourceIpCondition sourceIpCondition(AdminAccessConditionProperties properties) {
        return SourceIpCondition.fromAllowedCidrs(properties.getSourceIpAllowedCidrs());
    }
}
