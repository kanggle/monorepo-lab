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
import com.wms.admin.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.data.domain.PageRequest;
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

    private static final java.time.Instant FIXED = Instant.parse("2026-05-09T10:00:00Z");

    private UserService proxiedService;
    private InMemoryUserRepository userRepo;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryUserRepository();
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

    // ----- update ------------------------------------------------------------

    @Test
    @DisplayName("update: ADMIN passes the gate")
    void admin_can_update() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_ADMIN");
        assertThat(proxiedService.update(new UpdateUserCommand(
                u.id(), "New Name", null, null, null, "admin"))).isNotNull();
    }

    @Test
    @DisplayName("update: SUPERADMIN passes the gate")
    void superadmin_can_update() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_SUPERADMIN");
        assertThat(proxiedService.update(new UpdateUserCommand(
                u.id(), "New Name", null, null, null, "admin"))).isNotNull();
    }

    @Test
    @DisplayName("update: OPERATOR is denied with AccessDeniedException")
    void operator_cannot_update_raisesAccessDenied() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_OPERATOR");
        assertThatThrownBy(() -> proxiedService.update(new UpdateUserCommand(
                u.id(), "New Name", null, null, null, "admin")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("update: VIEWER is denied with AccessDeniedException")
    void viewer_cannot_update_raisesAccessDenied() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_VIEWER");
        assertThatThrownBy(() -> proxiedService.update(new UpdateUserCommand(
                u.id(), "New Name", null, null, null, "admin")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----- deactivate --------------------------------------------------------

    @Test
    @DisplayName("deactivate: ADMIN passes the gate")
    void admin_can_deactivate() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_ADMIN");
        DeactivateUserResult result = proxiedService.deactivate(
                new DeactivateUserCommand(u.id(), false, "admin", false));
        assertThat(result.user().status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("deactivate: SUPERADMIN passes the gate")
    void superadmin_can_deactivate() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_SUPERADMIN");
        assertThat(proxiedService.deactivate(
                new DeactivateUserCommand(u.id(), false, "admin", true))).isNotNull();
    }

    @Test
    @DisplayName("deactivate: OPERATOR is denied with AccessDeniedException")
    void operator_cannot_deactivate_raisesAccessDenied() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_OPERATOR");
        assertThatThrownBy(() -> proxiedService.deactivate(
                new DeactivateUserCommand(u.id(), false, "admin", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deactivate: VIEWER is denied with AccessDeniedException")
    void viewer_cannot_deactivate_raisesAccessDenied() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_VIEWER");
        assertThatThrownBy(() -> proxiedService.deactivate(
                new DeactivateUserCommand(u.id(), false, "admin", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----- reactivate --------------------------------------------------------

    @Test
    @DisplayName("reactivate: ADMIN passes the gate")
    void admin_can_reactivate() {
        User u = seedInactiveUser();
        authenticateAs("ROLE_WMS_ADMIN");
        User reactivated = proxiedService.reactivate(u.id(), "admin");
        assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("reactivate: SUPERADMIN passes the gate")
    void superadmin_can_reactivate() {
        User u = seedInactiveUser();
        authenticateAs("ROLE_WMS_SUPERADMIN");
        assertThat(proxiedService.reactivate(u.id(), "admin")).isNotNull();
    }

    @Test
    @DisplayName("reactivate: OPERATOR is denied with AccessDeniedException")
    void operator_cannot_reactivate_raisesAccessDenied() {
        User u = seedInactiveUser();
        authenticateAs("ROLE_WMS_OPERATOR");
        assertThatThrownBy(() -> proxiedService.reactivate(u.id(), "admin"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("reactivate: VIEWER is denied with AccessDeniedException")
    void viewer_cannot_reactivate_raisesAccessDenied() {
        User u = seedInactiveUser();
        authenticateAs("ROLE_WMS_VIEWER");
        assertThatThrownBy(() -> proxiedService.reactivate(u.id(), "admin"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----- findById (read) — TASK-BE-523 -------------------------------------

    @Test
    @DisplayName("findById: ADMIN passes the gate")
    void admin_can_findById() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_ADMIN");
        assertThat(proxiedService.findById(u.id())).isNotNull();
    }

    @Test
    @DisplayName("findById: SUPERADMIN passes the gate")
    void superadmin_can_findById() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_SUPERADMIN");
        assertThat(proxiedService.findById(u.id())).isNotNull();
    }

    @Test
    @DisplayName("findById: OPERATOR is denied with AccessDeniedException")
    void operator_cannot_findById_raisesAccessDenied() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_OPERATOR");
        assertThatThrownBy(() -> proxiedService.findById(u.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("findById: VIEWER is denied with AccessDeniedException")
    void viewer_cannot_findById_raisesAccessDenied() {
        User u = seedActiveUser();
        authenticateAs("ROLE_WMS_VIEWER");
        assertThatThrownBy(() -> proxiedService.findById(u.id()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----- search (read) — TASK-BE-523 ---------------------------------------

    @Test
    @DisplayName("search: ADMIN passes the gate")
    void admin_can_search() {
        seedActiveUser();
        authenticateAs("ROLE_WMS_ADMIN");
        assertThat(proxiedService.search(null, null, null, PageRequest.of(0, 20)))
                .isNotNull();
    }

    @Test
    @DisplayName("search: SUPERADMIN passes the gate")
    void superadmin_can_search() {
        seedActiveUser();
        authenticateAs("ROLE_WMS_SUPERADMIN");
        assertThat(proxiedService.search(null, null, null, PageRequest.of(0, 20)))
                .isNotNull();
    }

    @Test
    @DisplayName("search: OPERATOR is denied with AccessDeniedException")
    void operator_cannot_search_raisesAccessDenied() {
        authenticateAs("ROLE_WMS_OPERATOR");
        assertThatThrownBy(() -> proxiedService.search(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("search: VIEWER is denied with AccessDeniedException")
    void viewer_cannot_search_raisesAccessDenied() {
        authenticateAs("ROLE_WMS_VIEWER");
        assertThatThrownBy(() -> proxiedService.search(null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----- helpers -----------------------------------------------------------

    private User seedActiveUser() {
        return userRepo.save(User.create(UUID.randomUUID(), "USR-1", "alice@example.com",
                "Alice", null, null, FIXED, "system"));
    }

    private User seedInactiveUser() {
        return userRepo.save(new User(UUID.randomUUID(), "USR-1", "alice@example.com", "Alice",
                null, UserStatus.INACTIVE, null, 0L, FIXED, "system", FIXED, "system"));
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
