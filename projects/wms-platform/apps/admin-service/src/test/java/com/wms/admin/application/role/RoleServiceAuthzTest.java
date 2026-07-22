package com.wms.admin.application.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.admin.application.AdminEventEnvelopeBuilder;
import com.wms.admin.application.assignment.AssignmentEventHelper;
import com.wms.admin.application.fakes.InMemoryAssignmentRepository;
import com.wms.admin.application.fakes.InMemoryRoleRepository;
import com.wms.admin.application.fakes.RecordingOutboxPort;
import com.wms.admin.domain.Role;
import com.wms.admin.domain.RoleStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Validates that {@code @PreAuthorize("hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')")}
 * on {@link RoleService#create}/{@code update}/{@code deactivate}/{@code reactivate}
 * actually fires when the bean is invoked through Spring's method-security AOP
 * proxy — the {@code RoleServiceTest} instantiates the raw (unproxied) bean and
 * therefore never exercises the gate. Mirrors the canonical
 * {@code UserServiceAuthzTest} pattern. Covers TASK-BE-525 AC-1 (RoleService×4).
 */
class RoleServiceAuthzTest {

    private static final Instant FIXED = Instant.parse("2026-05-09T10:00:00Z");

    private InMemoryRoleRepository roleRepo;
    private InMemoryAssignmentRepository assignmentRepo;
    private RoleService proxied;

    @BeforeEach
    void setUp() {
        roleRepo = new InMemoryRoleRepository();
        assignmentRepo = new InMemoryAssignmentRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(FIXED, ZoneOffset.UTC);
        AdminEventEnvelopeBuilder envelopeBuilder = new AdminEventEnvelopeBuilder(mapper);
        RoleService raw = new RoleService(roleRepo, assignmentRepo, outbox, envelopeBuilder,
                new AssignmentEventHelper(assignmentRepo, outbox, envelopeBuilder), mapper, fixed);

        ProxyFactory pf = new ProxyFactory(raw);
        pf.addAdvice(AuthorizationManagerBeforeMethodInterceptor
                .preAuthorize(new PreAuthorizeAuthorizationManager()));
        pf.setProxyTargetClass(true);
        this.proxied = (RoleService) pf.getProxy();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ----- create ------------------------------------------------------------

    @Nested
    @DisplayName("create @PreAuthorize gate")
    class Create {
        @Test
        void admin_allowed() {
            authenticateAs("ROLE_WMS_ADMIN");
            Role saved = proxied.create(cmd());
            assertThat(saved).isNotNull();
            assertThat(saved.status()).isEqualTo(RoleStatus.ACTIVE);
        }

        @Test
        void superadmin_allowed() {
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.create(cmd())).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.create(cmd()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.create(cmd()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        private CreateRoleCommand cmd() {
            return new CreateRoleCommand("WMS_SHIFT_LEAD", "Shift Lead", "desc",
                    List.of("INVENTORY_READ"), "admin");
        }
    }

    // ----- update ------------------------------------------------------------

    @Nested
    @DisplayName("update @PreAuthorize gate")
    class Update {
        @Test
        void admin_allowed() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_ADMIN");
            assertThat(proxied.update(cmd(r.id()))).isNotNull();
        }

        @Test
        void superadmin_allowed() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.update(cmd(r.id()))).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.update(cmd(r.id())))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.update(cmd(r.id())))
                    .isInstanceOf(AccessDeniedException.class);
        }

        private UpdateRoleCommand cmd(UUID id) {
            return new UpdateRoleCommand(id, "NewName", "NewDesc",
                    List.of("INVENTORY_WRITE"), "admin");
        }
    }

    // ----- deactivate --------------------------------------------------------

    @Nested
    @DisplayName("deactivate @PreAuthorize gate")
    class Deactivate {
        @Test
        void admin_allowed() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_ADMIN");
            DeactivateRoleResult result = proxied.deactivate(
                    new DeactivateRoleCommand(r.id(), false, "admin", false));
            assertThat(result.role().status()).isEqualTo(RoleStatus.INACTIVE);
        }

        @Test
        void superadmin_allowed() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.deactivate(
                    new DeactivateRoleCommand(r.id(), false, "admin", true))).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.deactivate(
                    new DeactivateRoleCommand(r.id(), false, "admin", false)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.deactivate(
                    new DeactivateRoleCommand(r.id(), false, "admin", false)))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ----- reactivate --------------------------------------------------------

    @Nested
    @DisplayName("reactivate @PreAuthorize gate")
    class Reactivate {
        @Test
        void admin_allowed() {
            Role r = seedInactiveRole();
            authenticateAs("ROLE_WMS_ADMIN");
            Role reactivated = proxied.reactivate(r.id(), "admin");
            assertThat(reactivated.status()).isEqualTo(RoleStatus.ACTIVE);
        }

        @Test
        void superadmin_allowed() {
            Role r = seedInactiveRole();
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.reactivate(r.id(), "admin")).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            Role r = seedInactiveRole();
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.reactivate(r.id(), "admin"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            Role r = seedInactiveRole();
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.reactivate(r.id(), "admin"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ----- findById (read) — TASK-BE-523 -------------------------------------

    @Nested
    @DisplayName("findById @PreAuthorize gate")
    class FindById {
        @Test
        void admin_allowed() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_ADMIN");
            assertThat(proxied.findById(r.id())).isNotNull();
        }

        @Test
        void superadmin_allowed() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.findById(r.id())).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.findById(r.id()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            Role r = seedActiveRole();
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.findById(r.id()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ----- search (read) — TASK-BE-523 ---------------------------------------

    @Nested
    @DisplayName("search @PreAuthorize gate")
    class Search {
        @Test
        void admin_allowed() {
            seedActiveRole();
            authenticateAs("ROLE_WMS_ADMIN");
            assertThat(proxied.search(null, PageRequest.of(0, 20))).isNotNull();
        }

        @Test
        void superadmin_allowed() {
            seedActiveRole();
            authenticateAs("ROLE_WMS_SUPERADMIN");
            assertThat(proxied.search(null, PageRequest.of(0, 20))).isNotNull();
        }

        @Test
        void operator_deniedWithAccessDenied() {
            authenticateAs("ROLE_WMS_OPERATOR");
            assertThatThrownBy(() -> proxied.search(null, PageRequest.of(0, 20)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void viewer_deniedWithAccessDenied() {
            authenticateAs("ROLE_WMS_VIEWER");
            assertThatThrownBy(() -> proxied.search(null, PageRequest.of(0, 20)))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("all six gated methods carry @PreAuthorize referencing WMS_ADMIN")
    void gatedMethods_carryPreAuthorize() {
        assertThatCode(() -> {
            for (String m : List.of("create", "update", "deactivate", "reactivate", "findById", "search")) {
                boolean found = false;
                for (var method : RoleService.class.getMethods()) {
                    if (method.getName().equals(m)
                            && method.isAnnotationPresent(PreAuthorize.class)) {
                        assertThat(method.getAnnotation(PreAuthorize.class).value())
                                .contains("WMS_ADMIN");
                        found = true;
                    }
                }
                assertThat(found).as("@PreAuthorize present on RoleService." + m).isTrue();
            }
        }).doesNotThrowAnyException();
    }

    // ----- helpers -----------------------------------------------------------

    private Role seedActiveRole() {
        Role r = Role.create(UUID.randomUUID(), "WMS_CUSTOM", "Custom", "desc",
                "[\"INVENTORY_READ\"]", FIXED, "system");
        roleRepo.seed(r);
        return r;
    }

    private Role seedInactiveRole() {
        Role r = new Role(UUID.randomUUID(), "WMS_CUSTOM", "Custom", "desc",
                "[\"INVENTORY_READ\"]", RoleStatus.INACTIVE, false, 0L,
                FIXED, "system", FIXED, "system");
        roleRepo.seed(r);
        return r;
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
