package com.wms.admin.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.fakes.InMemorySettingRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.Setting;
import com.wms.admin.domain.SettingScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Validates that {@code @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")}
 * on {@link SettingsService#upsert} fires through the method-security AOP proxy —
 * {@code SettingsServiceTest} uses the raw bean and never exercises the gate.
 * Covers TASK-BE-525 AC-1 (SettingsService.upsert×1).
 */
class SettingsServiceAuthzTest {

    private static final Instant FIXED = Instant.parse("2026-05-09T10:00:00Z");
    private static final String KEY = "inventory.reservation.ttl_hours";

    private InMemorySettingRepository repo;
    private ObjectMapper mapper;
    private SettingsService proxied;

    @BeforeEach
    void setUp() {
        repo = new InMemorySettingRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(FIXED, ZoneOffset.UTC);
        SettingsService raw = new SettingsService(repo, outbox,
                new AdminEventEnvelopeBuilder(mapper), mapper, fixed);

        ProxyFactory pf = new ProxyFactory(raw);
        pf.addAdvice(AuthorizationManagerBeforeMethodInterceptor
                .preAuthorize(new PreAuthorizeAuthorizationManager()));
        pf.setProxyTargetClass(true);
        this.proxied = (SettingsService) pf.getProxy();

        repo.seed(new Setting(KEY, SettingScope.GLOBAL, null, "24",
                "{\"type\":\"integer\",\"minimum\":1,\"maximum\":168}", "TTL", 0L,
                Instant.parse("2026-05-09T09:00:00Z"), "system",
                Instant.parse("2026-05-09T09:00:00Z"), "system"));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("upsert: ADMIN passes the gate")
    void admin_allowed() throws Exception {
        authenticateAs("ROLE_WMS_ADMIN");
        Setting saved = proxied.upsert(new UpsertSettingCommand(KEY, null, value("36"), "admin"));
        assertThat(saved.valueJson()).isEqualTo("36");
    }

    @Test
    @DisplayName("upsert: SUPERADMIN passes the gate")
    void superadmin_allowed() throws Exception {
        authenticateAs("ROLE_WMS_SUPERADMIN");
        assertThat(proxied.upsert(new UpsertSettingCommand(KEY, null, value("48"), "admin")))
                .isNotNull();
    }

    @Test
    @DisplayName("upsert: OPERATOR is denied with AccessDeniedException")
    void operator_deniedWithAccessDenied() throws Exception {
        authenticateAs("ROLE_WMS_OPERATOR");
        JsonNode v = value("36");
        assertThatThrownBy(() -> proxied.upsert(new UpsertSettingCommand(KEY, null, v, "admin")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("upsert: VIEWER is denied with AccessDeniedException")
    void viewer_deniedWithAccessDenied() throws Exception {
        authenticateAs("ROLE_WMS_VIEWER");
        JsonNode v = value("36");
        assertThatThrownBy(() -> proxied.upsert(new UpsertSettingCommand(KEY, null, v, "admin")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("upsert carries @PreAuthorize referencing WMS_ADMIN")
    void upsert_carriesPreAuthorize() throws Exception {
        PreAuthorize ann = SettingsService.class
                .getMethod("upsert", UpsertSettingCommand.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).contains("WMS_ADMIN");
    }

    private JsonNode value(String json) throws Exception {
        return mapper.readTree(json);
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
