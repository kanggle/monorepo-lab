package com.example.security.query;

import com.example.security.domain.Tenants;
import com.example.security.infrastructure.persistence.LoginHistoryJpaEntity;
import com.example.security.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaEntity;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.gap.security.pii.PiiMaskingUtils;
import com.example.security.query.dto.LoginHistoryView;
import com.example.security.query.dto.SuspiciousEventView;
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

    public Page<SuspiciousEventView> findSuspiciousEvents(
            String accountId, Instant from, Instant to, String ruleCode, Pageable pageable) {
        // TASK-BE-248 Phase 1: query routes are not yet tenant-scoped at the
        // controller layer. Default to Tenants.DEFAULT_TENANT_ID; Phase 2 will
        // thread X-Tenant-Id from the gateway through to the JPA finder.
        String tenantId = Tenants.DEFAULT_TENANT_ID;
        Page<SuspiciousEventJpaEntity> page;
        if (ruleCode != null && !ruleCode.isBlank()) {
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

    public Page<LoginHistoryView> findLoginHistory(String accountId, Instant from, Instant to,
                                                    String outcome, Pageable pageable) {
        // TASK-BE-248 Phase 1 placeholder — see findSuspiciousEvents above.
        String tenantId = Tenants.DEFAULT_TENANT_ID;
        Page<LoginHistoryJpaEntity> page = loginHistoryJpaRepository.findByTenantAndAccountAndFilters(
                tenantId, accountId, from, to, outcome, pageable);

        return page.map(this::toView);
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
