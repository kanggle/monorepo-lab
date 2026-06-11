package com.example.admin.infrastructure.config;

import com.example.security.access.SourceIpCondition;
import com.example.security.access.TimeWindowCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-MONO-026 / ADR-MONO-028 (axis ② 2단계) — wires the {@code SOURCE_IP} and
 * {@code TIME_WINDOW} access conditions for admin-service from
 * {@link AdminAccessConditionProperties} (domain guard-config, § D3-B). Both beans
 * are consumed by {@code RequiresPermissionAspect} as the 4th authorization gate,
 * composed <b>AND-only</b> (ADR-028 § D2).
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
}
