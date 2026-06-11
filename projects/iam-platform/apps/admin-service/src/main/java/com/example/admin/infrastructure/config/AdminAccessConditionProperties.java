package com.example.admin.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * ADR-MONO-026 (axis ② 2단계) — admin-service access-condition configuration.
 *
 * <p>The {@code SOURCE_IP} access condition (the first pilot, ADR-MONO-026 § D4)
 * and the {@code TIME_WINDOW} condition (the 2nd type, ADR-MONO-028) are both
 * carried as <b>domain/endpoint guard-config</b> (§ D3-B), not on a JWT claim:
 * their parameters live here and are read entirely consumer-side (no producer /
 * token-customizer change).
 *
 * <p><b>Net-zero / opt-in:</b> {@link #getSourceIpAllowedCidrs()} defaults to an
 * <b>empty list</b> and {@link #getTimeWindow()} to an <b>empty window</b> ⟺ each
 * gate is unconfigured ⟺ admin mutations behave exactly as before
 * access-conditioning. A gate only bites once its parameters are configured
 * (properties {@code admin.access.source-ip-allowed-cidrs} /
 * {@code admin.access.time-window.*}). When both are configured they compose
 * <b>AND-only</b> (ADR-028 § D2 — both must hold).
 *
 * <p>See {@code platform/access-conditions.md} and the shared evaluators
 * {@code com.example.security.access.SourceIpCondition} /
 * {@code com.example.security.access.TimeWindowCondition}.
 */
@ConfigurationProperties(prefix = "admin.access")
public class AdminAccessConditionProperties {

    /**
     * Allowed source-IP CIDRs (e.g. {@code ["10.0.0.0/8", "203.0.113.5"]}) for the
     * admin mutation surface. Empty (the default) ⇒ net-zero (no gate).
     */
    private List<String> sourceIpAllowedCidrs = List.of();

    /**
     * ADR-MONO-028 — the {@code TIME_WINDOW} condition for the admin mutation
     * surface. An empty window (no zone / days / start / end) ⇒ net-zero (no gate).
     */
    private TimeWindow timeWindow = new TimeWindow();

    /**
     * ADR-MONO-029 — the {@code RESOURCE_TAG} condition (deny-if-present) for the
     * admin mutation surface. An empty forbidden-tag list (the default) ⇒ net-zero.
     */
    private ResourceTag resourceTag = new ResourceTag();

    public List<String> getSourceIpAllowedCidrs() {
        return sourceIpAllowedCidrs;
    }

    public void setSourceIpAllowedCidrs(List<String> sourceIpAllowedCidrs) {
        this.sourceIpAllowedCidrs = sourceIpAllowedCidrs == null ? List.of() : sourceIpAllowedCidrs;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(TimeWindow timeWindow) {
        this.timeWindow = timeWindow == null ? new TimeWindow() : timeWindow;
    }

    public ResourceTag getResourceTag() {
        return resourceTag;
    }

    public void setResourceTag(ResourceTag resourceTag) {
        this.resourceTag = resourceTag == null ? new ResourceTag() : resourceTag;
    }

    /**
     * The {@code TIME_WINDOW} guard-config (ADR-MONO-028 § D3): an IANA zone, the
     * allowed days-of-week, and a same-day {@code [start, end)} local window. All
     * blank/empty (the default) ⇒ the window is undeclared ⇒ net-zero.
     */
    public static class TimeWindow {
        private String zone = "";
        private List<String> days = List.of();
        private String start = "";
        private String end = "";

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone == null ? "" : zone;
        }

        public List<String> getDays() {
            return days;
        }

        public void setDays(List<String> days) {
            this.days = days == null ? List.of() : days;
        }

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start == null ? "" : start;
        }

        public String getEnd() {
            return end;
        }

        public void setEnd(String end) {
            this.end = end == null ? "" : end;
        }
    }

    /**
     * The {@code RESOURCE_TAG} guard-config (ADR-MONO-029 § D3): the deny-if-present
     * forbidden tags. An operator carrying any of these tags has its role/status/
     * profile mutation denied. Empty (the default) ⇒ net-zero (no gate).
     */
    public static class ResourceTag {
        private List<String> forbidden = List.of();

        public List<String> getForbidden() {
            return forbidden;
        }

        public void setForbidden(List<String> forbidden) {
            this.forbidden = forbidden == null ? List.of() : forbidden;
        }
    }
}
