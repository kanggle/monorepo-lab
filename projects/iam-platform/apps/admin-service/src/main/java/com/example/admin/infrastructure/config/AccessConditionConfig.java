package com.example.admin.infrastructure.config;

import com.example.security.access.ResourceTagCondition;
import com.example.security.access.SourceIpCondition;
import com.example.security.access.TimeWindowCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-MONO-026 / ADR-MONO-028 / ADR-MONO-029 (axis ② 2단계) — wires the
 * {@code SOURCE_IP}, {@code TIME_WINDOW} and {@code RESOURCE_TAG} access conditions
 * for admin-service from {@link AdminAccessConditionProperties} (domain guard-config,
 * § D3-B). All three beans are consumed by {@code RequiresPermissionAspect} as the
 * 4th authorization gate, composed <b>AND-only</b> (ADR-028 § D2). The
 * {@code RESOURCE_TAG} condition's resource tags are resolved by a
 * {@code ResourceTagResolver} (ADR-029 § D2-A — the single decision site is kept).
 *
 * <p>Each bean is always present in the running service (built from config that
 * defaults to empty ⇒ {@code isConfigured()} false ⇒ net-zero). {@code @WebMvcTest}
 * slices that do not import this config simply have no bean, which the aspect
 * treats as net-zero too — so existing slice tests are unaffected. The aspect
 * resolves the request {@code Clock} from the context if a unique one is present,
 * else {@code Clock.systemUTC()} (no Clock bean is registered here, keeping
 * production on the system clock while letting slice tests inject a fixed one).
 */
@Configuration
@EnableConfigurationProperties(AdminAccessConditionProperties.class)
public class AccessConditionConfig {

    @Bean
    public SourceIpCondition sourceIpCondition(AdminAccessConditionProperties properties) {
        return SourceIpCondition.fromAllowedCidrs(properties.getSourceIpAllowedCidrs());
    }

    @Bean
    public TimeWindowCondition timeWindowCondition(AdminAccessConditionProperties properties) {
        AdminAccessConditionProperties.TimeWindow tw = properties.getTimeWindow();
        return TimeWindowCondition.fromConfig(tw.getZone(), tw.getDays(), tw.getStart(), tw.getEnd());
    }

    @Bean
    public ResourceTagCondition resourceTagCondition(AdminAccessConditionProperties properties) {
        // Pilot semantics = deny-if-present (ADR-029 § D3): an operator carrying any
        // forbidden tag (e.g. `protected`) is denied. Empty list ⇒ net-zero.
        return ResourceTagCondition.forbidden(properties.getResourceTag().getForbidden());
    }
}
