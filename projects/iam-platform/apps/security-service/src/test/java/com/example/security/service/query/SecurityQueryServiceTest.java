package com.example.security.service.query;

import com.example.security.service.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-656 — which finder each tenant value reaches. The predicates themselves (tenant_id OR site_tenant_id,
 * isolation, the pool rule) are proved on MySQL by {@code SecurityQueryTenantIntegrationTest}; this pins the
 * routing locally: the caller's tenant reaches the tenant finder verbatim (not the old hard-coded
 * {@code fan-platform}), only an explicit {@code '*'} reaches the all-tenant finder, and a blank tenant reaches
 * neither.
 */
@DisplayName("SecurityQueryService — tenant routing (TASK-BE-656)")
class SecurityQueryServiceTest {

    private static final Instant FROM = Instant.parse("2021-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2021-12-31T00:00:00Z");
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    private LoginHistoryJpaRepository loginRepo;
    private SuspiciousEventJpaRepository suspiciousRepo;
    private SecurityQueryService service;

    @BeforeEach
    void setUp() {
        loginRepo = mock(LoginHistoryJpaRepository.class);
        suspiciousRepo = mock(SuspiciousEventJpaRepository.class);
        service = new SecurityQueryService(loginRepo, suspiciousRepo, new ObjectMapper());
        when(loginRepo.findByTenantAndAccountAndFilters(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        when(loginRepo.findByAccountAndFiltersAllTenants(any(), any(), any(), any(), any())).thenReturn(Page.empty());
        when(suspiciousRepo.findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                any(), any(), any(), any(), any(PageRequest.class))).thenReturn(Page.empty());
        when(suspiciousRepo.findByTenantIdAndAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
                any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        when(suspiciousRepo.findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(suspiciousRepo.findByAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
                any(), any(), any(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("🔴 login history: the caller's tenant reaches the tenant finder verbatim")
    void loginHistory_tenantPassedThrough() {
        service.findLoginHistory("ecommerce", "acc-1", FROM, TO, null, PAGE);

        verify(loginRepo).findByTenantAndAccountAndFilters(eq("ecommerce"), eq("acc-1"), eq(FROM), eq(TO), isNull(), eq(PAGE));
        verifyNoMoreInteractions(loginRepo);
    }

    @Test
    @DisplayName("login history: '*' reaches the all-tenant finder")
    void loginHistory_allTenants() {
        service.findLoginHistory("*", "acc-1", FROM, TO, "FAILURE", PAGE);

        verify(loginRepo).findByAccountAndFiltersAllTenants(eq("acc-1"), eq(FROM), eq(TO), eq("FAILURE"), eq(PAGE));
        verifyNoMoreInteractions(loginRepo);
    }

    @Test
    @DisplayName("🔴 suspicious events: the caller's tenant reaches the tenant finders verbatim (with and without ruleCode)")
    void suspicious_tenantPassedThrough() {
        service.findSuspiciousEvents("consumer-pool", "acc-1", FROM, TO, null, PAGE);
        service.findSuspiciousEvents("consumer-pool", "acc-1", FROM, TO, "GEO_ANOMALY", PAGE);

        verify(suspiciousRepo).findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                "consumer-pool", "acc-1", FROM, TO, PAGE);
        verify(suspiciousRepo).findByTenantIdAndAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
                "consumer-pool", "acc-1", "GEO_ANOMALY", FROM, TO, PAGE);
        verifyNoMoreInteractions(suspiciousRepo);
    }

    @Test
    @DisplayName("suspicious events: '*' reaches the all-tenant finders (with and without ruleCode)")
    void suspicious_allTenants() {
        service.findSuspiciousEvents("*", "acc-1", FROM, TO, null, PAGE);
        service.findSuspiciousEvents("*", "acc-1", FROM, TO, "VELOCITY", PAGE);

        verify(suspiciousRepo).findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc("acc-1", FROM, TO, PAGE);
        verify(suspiciousRepo).findByAccountIdAndRuleCodeAndDetectedAtBetweenOrderByDetectedAtDesc(
                "acc-1", "VELOCITY", FROM, TO, PAGE);
        verifyNoMoreInteractions(suspiciousRepo);
    }

    @Test
    @DisplayName("🔴 a blank or missing tenant reaches no finder — never the all-tenant one")
    void blankTenant_reachesNoFinder() {
        assertThatThrownBy(() -> service.findLoginHistory("", "acc-1", FROM, TO, null, PAGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findSuspiciousEvents(null, "acc-1", FROM, TO, null, PAGE))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(loginRepo, suspiciousRepo);
    }
}
