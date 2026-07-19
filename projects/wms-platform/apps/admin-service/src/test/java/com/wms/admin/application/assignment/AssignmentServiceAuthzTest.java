package com.wms.admin.application.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.fakes.InMemoryAssignmentRepository;
import com.wms.admin.application.fakes.InMemoryRoleRepository;
import com.wms.admin.application.fakes.InMemoryUserRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserRoleAssignment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * on {@link AssignmentService#grant}/{@code revoke} fires through the method-security
 * AOP proxy — {@code AssignmentServiceTest} uses the raw bean and never exercises
 * the gate. Covers TASK-BE-525 AC-1 (AssignmentService×2).
 */
class AssignmentServiceAuthzTest {

    private static final Instant FIXED = Instant.parse("2026-05-09T10:00:00Z");

    private InMemoryUserRepository userRepo;
    private InMemoryRoleRepository roleRepo;
    private InMemoryAssignmentRepository assignmentRepo;
    private AssignmentService proxied;

    private UUID activeUserId;
    private UUID activeRoleId;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryUserRepository();
        roleRepo = new InMemoryRoleRepository();
        assignmentRepo = new InMemoryAssignmentRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(FIXED, ZoneOffset.UTC);
        AssignmentService raw = new AssignmentService(assignmentRepo, userRepo, roleRepo, outbox,
                new AdminEventEnvelopeBuilder(mapper), fixed);

        ProxyFactory pf = new ProxyFactory(raw);
        pf.addAdvice(AuthorizationManagerBeforeMethodInterceptor
                .preAuthorize(new PreAuthorizeAuthorizationManager()));
        pf.setProxyTargetClass(true);
        this.proxied = (AssignmentService) pf.getProxy();

        User user = userRepo.save(User.create(UUID.randomUUID(), "USR-1", "alice@example.com",
                "Alice", null, null, FIXED, "system"));
        activeUserId = user.id();
        Role role = Role.create(UUID.randomUUID(), "WMS_CUSTOM", "Custom", null,
                "[\"INVENTORY_READ\"]", FIXED, "system");
        roleRepo.seed(role);
        activeRoleId = role.id();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("grant @PreAuthorize gate")
    class Grant {
        @Test
        void admin_allowed() {
            authenticateAs("ROLE_WMS_ADMIN");
            GrantAssignmentResult result = proxied.grant(
                    new GrantAssignmentCommand(activeUserId, activeRoleId, null, "admin"));
            assertThat(result.created()).isTrue();
        }

        @Test
        void superadmin_allowed() {
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.grant(
                    new GrantAssignmentCommand(activeUserId, activeRoleId, null, "admin")))
                    .isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.grant(
                    new GrantAssignmentCommand(activeUserId, activeRoleId, null, "admin")))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.grant(
                    new GrantAssignmentCommand(activeUserId, activeRoleId, null, "admin")))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("revoke @PreAuthorize gate")
    class Revoke {
        @Test
        void admin_allowed() {
            UUID assignmentId = seedActiveAssignment();
            authenticateAs("ROLE_WMS_ADMIN");
            UserRoleAssignment revoked = proxied.revoke(assignmentId, "admin");
            assertThat(revoked).isNotNull();
        }

        @Test
        void superadmin_allowed() {
            UUID assignmentId = seedActiveAssignment();
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.revoke(assignmentId, "admin")).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            UUID assignmentId = seedActiveAssignment();
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.revoke(assignmentId, "admin"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            UUID assignmentId = seedActiveAssignment();
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.revoke(assignmentId, "admin"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("both gated methods carry @PreAuthorize referencing WMS_ADMIN")
    void gatedMethods_carryPreAuthorize() {
        assertThatCode(() -> {
            for (String m : List.of("grant", "revoke")) {
                boolean found = false;
                for (var method : AssignmentService.class.getMethods()) {
                    if (method.getName().equals(m)
                            && method.isAnnotationPresent(PreAuthorize.class)) {
                        assertThat(method.getAnnotation(PreAuthorize.class).value())
                                .contains("WMS_ADMIN");
                        found = true;
                    }
                }
                assertThat(found).as("@PreAuthorize present on AssignmentService." + m).isTrue();
            }
        }).doesNotThrowAnyException();
    }

    private UUID seedActiveAssignment() {
        UserRoleAssignment a = UserRoleAssignment.grant(
                UUID.randomUUID(), activeUserId, activeRoleId, null, FIXED, "system");
        return assignmentRepo.save(a).id();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
