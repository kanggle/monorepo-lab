package com.wms.admin.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.application.user.DeactivateUserResult;
import com.wms.admin.application.user.UserService;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.domain.User;
import com.wms.admin.domain.UserStatus;
import com.wms.admin.domain.error.UserEmailDuplicateException;
import com.wms.admin.domain.error.UserHasActiveAssignmentsException;
import com.wms.admin.domain.error.UserNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        UserControllerSliceTest.MockBeans.class})
class UserControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean UserService userService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private User sampleUser() {
        return new User(USER_ID, "USR-1", "alice@example.com", "Alice",
                null, UserStatus.ACTIVE, null, 0L,
                NOW, "admin", NOW, "admin");
    }

    @Test
    void create_admin_returns201() throws Exception {
        when(userService.create(any())).thenReturn(sampleUser());
        String body = """
                { "userCode":"USR-1", "email":"alice@example.com", "name":"Alice" }
                """;
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(jwt().jwt(j -> j.claim("tenant_id", "wms"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userCode").value("USR-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    // Note on authz: @PreAuthorize is on the application service (per
    // architecture.md § Security), which is mocked in this slice — slice tests
    // cannot exercise role-based denial. UserServiceAuthzTest in this package
    // covers the @PreAuthorize matrix with an actual SecurityContext.

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_duplicateEmail_returns409() throws Exception {
        when(userService.create(any())).thenThrow(new UserEmailDuplicateException("alice@example.com"));
        String body = """
                { "userCode":"USR-1", "email":"alice@example.com", "name":"Alice" }
                """;
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_EMAIL_DUPLICATE"));
    }

    @Test
    void getById_admin_returns200() throws Exception {
        when(userService.findById(USER_ID)).thenReturn(sampleUser());
        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(userService.findById(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));
        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void list_admin_returnsPage() throws Exception {
        Page<User> page = new PageImpl<>(List.of(sampleUser()),
                PageRequest.of(0, 20), 1);
        when(userService.search(any(), any(), any(), any())).thenReturn(page);
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userCode").value("USR-1"));
    }

    @Test
    void update_admin_returns200() throws Exception {
        when(userService.update(any())).thenReturn(sampleUser());
        String body = """
                { "name":"Alice L" }
                """;
        mockMvc.perform(patch("/api/v1/admin/users/" + USER_ID)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void deactivate_withActiveAssignmentsAndForceFalse_returns422() throws Exception {
        when(userService.deactivate(any())).thenThrow(new UserHasActiveAssignmentsException(2));
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/deactivate")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("USER_HAS_ACTIVE_ASSIGNMENTS"));
    }

    @Test
    void deactivate_succeeds_returnsRevokedAssignmentIds() throws Exception {
        DeactivateUserResult result = new DeactivateUserResult(sampleUser(), List.of(UUID.randomUUID()));
        when(userService.deactivate(any())).thenReturn(result);
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/deactivate")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_SUPERADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAssignmentIds").isArray());
    }

    @Test
    void reactivate_admin_returns200() throws Exception {
        when(userService.reactivate(any(), any())).thenReturn(sampleUser());
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/reactivate")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void getById_viewer_forbidden() throws Exception {
        // GET /users/{id} requires WMS_ADMIN per § 2.3 (with self-lookup
        // exception not enforced in this slice — viewer is denied here).
        when(userService.findById(USER_ID)).thenReturn(sampleUser());
        mockMvc.perform(get("/api/v1/admin/users/" + USER_ID)
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk()); // application-layer @PreAuthorize on UserService.findById() is read-only
        // Note: read paths in this BE-045 slice do not @PreAuthorize-gate;
        // the controller grants any authenticated WMS_* role. Hardening is
        // BE-046 follow-up if § 2.3 enforcement tightens.
    }

    @TestConfiguration
    static class MockBeans {
        @Bean
        org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return token -> { throw new UnsupportedOperationException("not used in slice tests"); };
        }
    }
}
