package com.wms.admin.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.assignment.AssignmentEventHelper;
import com.wms.admin.application.fakes.InMemoryAssignmentRepository;
import com.wms.admin.application.fakes.InMemoryUserRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Validates that {@code @PreAuthorize} on {@link UserService} actually fires
 * when invoked through Spring's method-security AOP proxy. This complements
 * the controller-slice tests (which mock the service and therefore cannot
 * exercise authz). Covers AC-08.
 */
class UserServiceAuthzTest {

    private UserService proxiedService;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        InMemoryAssignmentRepository assignmentRepo = new InMemoryAssignmentRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC);
        AdminEventEnvelopeBuilder envelopeBuilder = new AdminEventEnvelopeBuilder(mapper);
        UserService raw = new UserService(userRepo, assignmentRepo, outbox,
                envelopeBuilder, new AssignmentEventHelper(assignmentRepo, outbox, envelopeBuilder), fixed);

        ProxyFactory pf = new ProxyFactory(raw);
        pf.addAdvice(AuthorizationManagerBeforeMethodInterceptor
                .preAuthorize(new PreAuthorizeAuthorizationManager()));
        // Ensure the proxy targets the class so @PreAuthorize is reflected on
        // method invocation regardless of interface presence.
        pf.setProxyTargetClass(true);
        this.proxiedService = (UserService) pf.getProxy();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void admin_can_create() {
        authenticateAs("ROLE_WMS_ADMIN");
        User saved = proxiedService.create(new CreateUserCommand(
                "USR-1", "alice@example.com", "Alice", null, null, "admin"));
        assertThat(saved).isNotNull();
    }

    @Test
    void superadmin_can_create() {
        authenticateAs("ROLE_WMS_SUPERADMIN");
        User saved = proxiedService.create(new CreateUserCommand(
                "USR-1", "alice@example.com", "Alice", null, null, "admin"));
        assertThat(saved).isNotNull();
    }

    @Test
    void operator_cannot_create_raisesAccessDenied() {
        authenticateAs("ROLE_WMS_OPERATOR");
        assertThatThrownBy(() -> proxiedService.create(new CreateUserCommand(
                "USR-1", "alice@example.com", "Alice", null, null, "admin")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void viewer_cannot_create_raisesAccessDenied() {
        authenticateAs("ROLE_WMS_VIEWER");
        assertThatThrownBy(() -> proxiedService.create(new CreateUserCommand(
                "USR-1", "alice@example.com", "Alice", null, null, "admin")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preAuthorize_present_on_userService_create() {
        // Reflective check that the @PreAuthorize annotation is in fact wired
        // onto UserService.create — the contract relies on it for authz.
        try {
            PreAuthorize ann = UserService.class
                    .getMethod("create", CreateUserCommand.class)
                    .getAnnotation(PreAuthorize.class);
            assertThat(ann).isNotNull();
            assertThat(ann.value()).contains("WMS_ADMIN");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
