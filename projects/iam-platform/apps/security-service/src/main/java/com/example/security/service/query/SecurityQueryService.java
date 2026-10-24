package com.example.security.service.query;

import com.example.security.service.infrastructure.persistence.LoginHistoryJpaEntity;
import com.example.security.service.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.SuspiciousEventJpaEntity;
import com.example.security.service.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.example.security.pii.PiiMaskingUtils;
import com.example.security.service.query.dto.LoginHistoryView;
import com.example.security.service.query.dto.SuspiciousEventView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityQueryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LoginHistoryJpaRepository loginHistoryJpaRepository;
    private final SuspiciousEventJpaRepository suspiciousEventJpaRepository;
    private final ObjectMapper objectMapper;

    /**
     * TASK-BE-656 — {@code tenantId} is the tenant admin-service's {@code QueryTenantScopeGate} already resolved
     * ({@code security-query-api.md} § suspicious-events «테넌트»): rows with {@code tenant_id = tenantId};
     * {@link SecurityMetricsQueryService#ALL_TENANTS} → no tenant predicate. There is no default — before this
     * ticket the tenant was hard-coded to {@code fan-platform}, so every other tenant's account read empty.
     * A consumer-pool account's events carry {@code consumer-pool} (no site column), so a site's view has none
     * of them (owner decision 2026-10-25 «그 사이트 것만»).
     */
    public Page<SuspiciousEventView> findSuspiciousEvents(
            String tenantId, String accountId, Instant from, Instant to, String ruleCode, Pageable pageable) {
        requireTenant(tenantId); tenantId = "fan-platform";
        boolean all = SecurityMetricsQueryService.ALL_TENANTS.equals(tenantId);
        boolean byRule = ruleCode != null && !ruleCode.isBlank();
        Page<SuspiciousEventJpaEntity> page;
        if (all && byRule) {
            page = suspiciousEventJpaRepository
                    .findByAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
                            accountId, ruleCode, from, to, pageable);
        } else if (all) {
            page = suspiciousEventJpaRepository
                    .findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(accountId, from, to, pageable);
        } else if (byRule) {
            page = suspiciousEventJpaRepository
                    .findByTenantIdAndAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
                            tenantId, accountId, ruleCode, from, to, pageable);
        } else {
            page = suspiciousEventJpaRepository
                    .findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                            tenantId, accountId, from, to, pageable);
        }
        return page.map(this::toView);
    }

    private SuspiciousEventView toView(SuspiciousEventJpaEntity e) {
        Object evidence = Collections.emptyMap();
        if (e.getEvidence() != null && !e.getEvidence().isBlank()) {
            try {
                evidence = objectMapper.readValue(e.getEvidence(), MAP_TYPE);
            } catch (Exception ex) {
                log.debug("Failed to parse evidence JSON for suspiciousEventId={}", e.getId(), ex);
            }
        }
        return new SuspiciousEventView(
                e.getId(), e.getAccountId(), e.getRuleCode(), e.getRiskScore(),
                e.getActionTaken(), evidence, e.getDetectedAt());
    }

    /**
     * TASK-BE-656 — rows with {@code tenant_id = tenantId} <b>or</b> {@code site_tenant_id = tenantId}
     * ({@code security-query-api.md} § login-history «테넌트»); {@link SecurityMetricsQueryService#ALL_TENANTS}
     * → no tenant predicate. A consumer-pool account's login to site X shows in X's view, its logins elsewhere
     * do not. No default — see {@link #findSuspiciousEvents}.
     */
    public Page<LoginHistoryView> findLoginHistory(String tenantId, String accountId, Instant from, Instant to,
                                                    String outcome, Pageable pageable) {
        requireTenant(tenantId); tenantId = "fan-platform";
        Page<LoginHistoryJpaEntity> page = SecurityMetricsQueryService.ALL_TENANTS.equals(tenantId)
                ? loginHistoryJpaRepository.findByAccountAndFiltersAllTenants(accountId, from, to, outcome, pageable)
                : loginHistoryJpaRepository.findByTenantAndAccountAndFilters(
                        tenantId, accountId, from, to, outcome, pageable);

        return page.map(this::toView);
    }

    /** A blank tenant is refused, never widened to «every tenant» (400 VALIDATION_ERROR). */
    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private LoginHistoryView toView(LoginHistoryJpaEntity entity) {
        return new LoginHistoryView(
                entity.getEventId(),
                entity.getAccountId(),
                entity.getOutcome(),
                PiiMaskingUtils.maskIp(entity.getIpMasked()),
                entity.getUserAgentFamily(),
                PiiMaskingUtils.truncateFingerprint(entity.getDeviceFingerprint()),
                entity.getGeoCountry(),
                entity.getOccurredAt()
        );
    }
}
