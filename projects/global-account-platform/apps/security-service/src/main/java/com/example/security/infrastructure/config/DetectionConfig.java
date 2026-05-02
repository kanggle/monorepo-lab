package com.example.security.infrastructure.config;

import com.example.security.domain.detection.*;
import com.example.security.domain.repository.LoginHistoryRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the detection pipeline. Rules are exposed as individual beans and
 * auto-collected by {@code DetectSuspiciousActivityUseCase} via
 * {@code List<SuspiciousActivityRule>} — new rules can be added by declaring a
 * new {@code @Bean} without touching the use-case.
 */
@Configuration
@EnableConfigurationProperties(DetectionProperties.class)
public class DetectionConfig {

    @Bean
    public DetectionThresholds detectionThresholds(DetectionProperties props) {
        return props.toThresholds();
    }

    @Bean
    public VelocityRule velocityRule(VelocityCounter counter, DetectionThresholds thresholds) {
        return new VelocityRule(counter, thresholds);
    }

    @Bean
    public GeoAnomalyRule geoAnomalyRule(GeoLookup geoLookup,
                                         LastKnownGeoStore geoStore,
                                         DetectionThresholds thresholds) {
        return new GeoAnomalyRule(geoLookup, geoStore, thresholds);
    }

    @Bean
    public DeviceChangeRule deviceChangeRule(KnownDeviceStore store, DetectionThresholds thresholds) {
        return new DeviceChangeRule(store, thresholds);
    }

    @Bean
    public TokenReuseRule tokenReuseRule(TokenReuseCounter counter) {
        return new TokenReuseRule(counter);
    }

    @Bean
    public ImpossibleTravelRule impossibleTravelRule(LoginHistoryRepository loginHistoryRepository,
                                                      DetectionThresholds thresholds) {
        return new ImpossibleTravelRule(loginHistoryRepository, thresholds);
    }

    @Bean
    public IpReputationRule ipReputationRule(DetectionProperties props,
                                              DetectionThresholds thresholds) {
        return new IpReputationRule(props.getIpReputation().getSuspiciousCidrs(), thresholds);
    }
}
