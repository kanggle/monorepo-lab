package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpReputationRuleTest {

    private final DetectionThresholds thresholds = DetectionThresholds.defaults();

    private EvaluationContext succeededCtx(String ip) {
        return new EvaluationContext("evt-1", "auth.login.succeeded", "acc-1",
                ip, "fp-1", "KR", Instant.now(), null);
    }

    @Test
    @DisplayName("Clean IP → no alert")
    void cleanIpDoesNotFire() {
        List<String> cidrs = List.of("10.0.0.0/8");
        DetectionResult r = new IpReputationRule(cidrs, thresholds)
                .evaluate(succeededCtx("192.168.1.1"));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("IP matching suspicious CIDR → ALERT")
    void suspiciousIpFires() {
        List<String> cidrs = List.of("10.0.0.0/8", "172.16.0.0/12");
        DetectionResult r = new IpReputationRule(cidrs, thresholds)
                .evaluate(succeededCtx("10.5.3.2"));

        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(60);
        assertThat(r.ruleCode()).isEqualTo("IP_REPUTATION");
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.ALERT);
        assertThat(r.evidence()).containsEntry("matchedCidr", "10.0.0.0/8");
    }

    @Test
    @DisplayName("IP matching second CIDR in list → ALERT with correct evidence")
    void matchesSecondCidr() {
        List<String> cidrs = List.of("10.0.0.0/8", "172.16.0.0/12");
        DetectionResult r = new IpReputationRule(cidrs, thresholds)
                .evaluate(succeededCtx("172.20.1.1"));

        assertThat(r.fired()).isTrue();
        assertThat(r.evidence()).containsEntry("matchedCidr", "172.16.0.0/12");
    }

    @Test
    @DisplayName("Empty CIDR list → no alert (default safe behavior)")
    void emptyCidrListDoesNotFire() {
        DetectionResult r = new IpReputationRule(Collections.emptyList(), thresholds)
                .evaluate(succeededCtx("10.0.0.1"));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Null CIDR list → no alert")
    void nullCidrListDoesNotFire() {
        DetectionResult r = new IpReputationRule(null, thresholds)
                .evaluate(succeededCtx("10.0.0.1"));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Masked IP (cannot parse) → no alert")
    void maskedIpDoesNotFire() {
        List<String> cidrs = List.of("10.0.0.0/8");
        DetectionResult r = new IpReputationRule(cidrs, thresholds)
                .evaluate(succeededCtx("1.2.3.***"));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Null IP → no alert")
    void nullIpDoesNotFire() {
        List<String> cidrs = List.of("10.0.0.0/8");
        DetectionResult r = new IpReputationRule(cidrs, thresholds)
                .evaluate(succeededCtx(null));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Invalid CIDR entries are skipped, valid ones still evaluated")
    void invalidCidrSkipped() {
        List<String> cidrs = List.of("not-a-cidr", "10.0.0.0/8");
        DetectionResult r = new IpReputationRule(cidrs, thresholds)
                .evaluate(succeededCtx("10.1.2.3"));

        assertThat(r.fired()).isTrue();
        assertThat(r.evidence()).containsEntry("matchedCidr", "10.0.0.0/8");
    }

    @Test
    @DisplayName("Single IP CIDR (no prefix) matches exactly")
    void singleIpCidr() {
        List<String> cidrs = List.of("10.0.0.1/32");
        IpReputationRule rule = new IpReputationRule(cidrs, thresholds);

        assertThat(rule.evaluate(succeededCtx("10.0.0.1")).fired()).isTrue();
        assertThat(rule.evaluate(succeededCtx("10.0.0.2")).fired()).isFalse();
    }

    @Test
    @DisplayName("Configurable score affects outcome")
    void configurableScore() {
        DetectionThresholds strict = new DetectionThresholds(
                10, 3600, 80, 900, 85, 15, 50, true, 3600, 70, 80);
        List<String> cidrs = List.of("10.0.0.0/8");
        DetectionResult r = new IpReputationRule(cidrs, strict)
                .evaluate(succeededCtx("10.1.2.3"));

        assertThat(r.riskScore()).isEqualTo(80);
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("Works with auth.login.failed events too")
    void worksWithFailedEvents() {
        List<String> cidrs = List.of("10.0.0.0/8");
        EvaluationContext failedCtx = new EvaluationContext("evt-1", "auth.login.failed",
                "acc-1", "10.1.2.3", "fp-1", "KR", Instant.now(), 3);

        DetectionResult r = new IpReputationRule(cidrs, thresholds).evaluate(failedCtx);

        assertThat(r.fired()).isTrue();
    }
}
