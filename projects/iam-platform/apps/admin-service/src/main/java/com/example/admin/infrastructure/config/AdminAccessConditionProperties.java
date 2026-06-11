package com.example.admin.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * ADR-MONO-026 (axis ② 2단계) — admin-service access-condition configuration.
 *
 * <p>The {@code SOURCE_IP} access condition (the first pilot, ADR-MONO-026 § D4)
 * is carried as <b>domain/endpoint guard-config</b> (§ D3-B), not on a JWT claim:
 * the allowed-CIDR list lives here and is read entirely consumer-side (no producer
 * / token-customizer change).
 *
 * <p><b>Net-zero / opt-in:</b> {@link #getSourceIpAllowedCidrs()} defaults to an
 * <b>empty list</b> ⟺ the gate is unconfigured ⟺ admin mutations behave exactly
 * as before access-conditioning. The gate only bites once at least one CIDR is
 * configured (property {@code admin.access.source-ip-allowed-cidrs}).
 *
 * <p>See {@code platform/access-conditions.md} and the shared evaluator
 * {@code com.example.security.access.SourceIpCondition}.
 */
@ConfigurationProperties(prefix = "admin.access")
public class AdminAccessConditionProperties {

    /**
     * Allowed source-IP CIDRs (e.g. {@code ["10.0.0.0/8", "203.0.113.5"]}) for the
     * admin mutation surface. Empty (the default) ⇒ net-zero (no gate).
     */
    private List<String> sourceIpAllowedCidrs = List.of();

    public List<String> getSourceIpAllowedCidrs() {
        return sourceIpAllowedCidrs;
    }

    public void setSourceIpAllowedCidrs(List<String> sourceIpAllowedCidrs) {
        this.sourceIpAllowedCidrs = sourceIpAllowedCidrs == null ? List.of() : sourceIpAllowedCidrs;
    }
}
