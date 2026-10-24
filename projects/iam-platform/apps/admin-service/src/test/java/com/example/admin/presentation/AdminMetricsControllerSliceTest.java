package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminMetricsUseCase;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.example.security.jwt.JwtVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/admin/metrics} slice (TASK-MONO-811) — same harness as {@link AuditControllerSliceTest}
 * (operator JWT filter, {@link RequiresPermissionAspect}, the real {@link AdminExceptionHandler}).
 * AC-2 field census; AC-3 401 / 403 PERMISSION_DENIED / 403 TENANT_SCOPE_DENIED; 400 on period; 503.
 */
@WebMvcTest(controllers = AdminMetricsController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminMetricsControllerSliceTest.JwtBeans.class})
class AdminMetricsControllerSliceTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AdminMetricsUseCase useCase;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;

    @BeforeEach
    void grantAuditRead() {
        when(permissionEvaluator.hasPermission(anyString(), eq(Permission.AUDIT_READ))).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-1");
    }

    private static PerformanceMetrics sample(MetricsPeriod period) {
        KstPeriodBounds bounds = KstPeriodBounds.from(Clock.fixed(Instant.parse("2026-10-23T05:05:00Z"), ZoneOffset.UTC));
        return PerformanceMetrics.of(bounds, period, List.of(
                        Metric.count("loginSuccess", 120, 98),
                        Metric.count("loginFailure", 7, 11),
                        Metric.count("accountLocked", 1, 0),
                        Metric.count("newAccount", 14, 9)),
                Map.of(LocalDate.parse("2026-10-20"), Map.of("loginSuccess", 30L, "newAccount", 2L)));
    }

    @Test
    @DisplayName("200 — the shared shape, bare (no envelope), four count keys in display order, integer strings")
    void ok() throws Exception {
        when(useCase.query(any(OperatorContext.class), eq(MetricsPeriod.WEEK), isNull()))
                .thenReturn(sample(MetricsPeriod.WEEK));

        mockMvc.perform(get("/api/admin/metrics").param("period", "week").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("week"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.current.from").value("2026-10-19T00:00:00+09:00"))
                .andExpect(jsonPath("$.current.to").value("2026-10-23T14:05:00+09:00"))
                .andExpect(jsonPath("$.previous.from").value("2026-10-12T00:00:00+09:00"))
                .andExpect(jsonPath("$.metrics[0].key").value("loginSuccess"))
                .andExpect(jsonPath("$.metrics[0].unit").value("count"))
                .andExpect(jsonPath("$.metrics[0].current").value("120"))
                .andExpect(jsonPath("$.metrics[0].previous").value("98"))
                .andExpect(jsonPath("$.metrics[1].key").value("loginFailure"))
                .andExpect(jsonPath("$.metrics[2].key").value("accountLocked"))
                .andExpect(jsonPath("$.metrics[3].key").value("newAccount"))
                .andExpect(jsonPath("$.series[1].date").value("2026-10-20"))
                .andExpect(jsonPath("$.series[1].values.loginSuccess").value("30"))
                .andExpect(jsonPath("$.series[1].values.loginFailure").value("0"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("🔴 AC-2 field census: exactly the shared fields at every level; no account id, email, IP, fingerprint anywhere")
    void fieldCensus() throws Exception {
        when(useCase.query(any(OperatorContext.class), eq(MetricsPeriod.MONTH), isNull()))
                .thenReturn(sample(MetricsPeriod.MONTH));

        String body = mockMvc.perform(get("/api/admin/metrics").param("period", "month")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(names(root)).containsExactly("period", "timezone", "current", "previous", "metrics", "series");
        assertThat(names(root.get("current"))).containsExactly("from", "to");
        assertThat(names(root.get("previous"))).containsExactly("from", "to");
        for (JsonNode metric : root.get("metrics")) {
            assertThat(names(metric)).containsExactly("key", "unit", "currency", "current", "previous");
            assertThat(metric.get("currency").isNull()).isTrue();
        }
        for (JsonNode point : root.get("series")) {
            assertThat(names(point)).containsExactly("date", "values");
            assertThat(names(point.get("values")))
                    .containsExactly("loginSuccess", "loginFailure", "accountLocked", "newAccount");
        }
        String lower = body.toLowerCase();
        assertThat(lower).doesNotContain("accountid", "email", "\"ip", "ipmasked", "fingerprint", "eventid",
                "operatorid", "useragent");
    }

    @Test
    @DisplayName("period MONTH (case-insensitive) and tenantId are passed through")
    void tenantPassedThrough() throws Exception {
        when(useCase.query(any(OperatorContext.class), eq(MetricsPeriod.MONTH), eq("ecommerce")))
                .thenReturn(sample(MetricsPeriod.MONTH));

        mockMvc.perform(get("/api/admin/metrics").param("period", "MONTH").param("tenantId", "ecommerce")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("month"));
        verify(useCase).query(any(OperatorContext.class), eq(MetricsPeriod.MONTH), eq("ecommerce"));
    }

    @Test
    @DisplayName("period missing → 400 VALIDATION_ERROR, nothing queried")
    void periodMissing400() throws Exception {
        mockMvc.perform(get("/api/admin/metrics").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("period unknown (today) → 400 VALIDATION_ERROR")
    void periodUnknown400() throws Exception {
        mockMvc.perform(get("/api/admin/metrics").param("period", "today").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("no JWT → 401 TOKEN_INVALID")
    void noJwt401() throws Exception {
        mockMvc.perform(get("/api/admin/metrics").param("period", "week"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("🔴 AC-3: without audit.read → 403 PERMISSION_DENIED + DENIED row (METRICS_QUERY), even with a bad period")
    void withoutAuditRead403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), eq(Permission.AUDIT_READ))).thenReturn(false);

        mockMvc.perform(get("/api/admin/metrics").param("period", "today").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(auditor).recordDenied(eq(ActionCode.METRICS_QUERY), eq(Permission.AUDIT_READ),
                eq("/api/admin/metrics"), eq("GET"), isNull());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("🔴 AC-3: tenant outside the audit.read grant scope → 403 TENANT_SCOPE_DENIED")
    void tenantScopeDenied403() throws Exception {
        when(useCase.query(any(OperatorContext.class), eq(MetricsPeriod.WEEK), eq("other-tenant")))
                .thenThrow(new TenantScopeDeniedException("Operator is not scoped to tenant 'other-tenant'"));

        mockMvc.perform(get("/api/admin/metrics").param("period", "week").param("tenantId", "other-tenant")
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("either downstream failing → 503 DOWNSTREAM_ERROR (no partial body)")
    void downstream503() throws Exception {
        when(useCase.query(any(OperatorContext.class), eq(MetricsPeriod.WEEK), isNull()))
                .thenThrow(new DownstreamFailureException("security-service unavailable"));

        mockMvc.perform(get("/api/admin/metrics").param("period", "week").header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_ERROR"))
                .andExpect(jsonPath("$.metrics").doesNotExist());
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
