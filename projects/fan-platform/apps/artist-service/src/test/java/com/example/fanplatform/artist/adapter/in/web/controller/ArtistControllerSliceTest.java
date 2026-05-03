package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.port.in.ArchiveArtistUseCase;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.GetArtistUseCase;
import com.example.fanplatform.artist.application.port.in.PublishArtistUseCase;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase.RegisterArtistCommand;
import com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.testsupport.JwtTestHelper;
import com.example.fanplatform.artist.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ArtistController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class ArtistControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean RegisterArtistUseCase registerUseCase;
    @MockitoBean UpdateArtistUseCase updateUseCase;
    @MockitoBean PublishArtistUseCase publishUseCase;
    @MockitoBean ArchiveArtistUseCase archiveUseCase;
    @MockitoBean GetArtistUseCase getUseCase;

    private static ArtistView sampleView(ArtistStatus status) {
        return new ArtistView("a-1", "fan-platform", ArtistType.SOLO, status,
                "STAGE", null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("POST /api/artists (no auth) → 401")
    void register_noAuth401() throws Exception {
        String body = """
                {"artistType":"SOLO","stageName":"STAGE"}
                """;
        mockMvc.perform(post("/api/artists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/artists (FAN role) → 403 FORBIDDEN")
    void register_fanRoleForbidden() throws Exception {
        String body = """
                {"artistType":"SOLO","stageName":"STAGE"}
                """;
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/artists (ADMIN role + valid body) → 201 + envelope")
    void register_adminCreated() throws Exception {
        when(registerUseCase.register(any(RegisterArtistCommand.class)))
                .thenReturn(sampleView(ArtistStatus.DRAFT));
        String body = """
                {"artistType":"SOLO","stageName":"STAGE"}
                """;
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("a-1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/artists (missing stageName) → 422 VALIDATION_ERROR")
    void register_missingStageName() throws Exception {
        String body = """
                {"artistType":"SOLO"}
                """;
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/artists/{id} → 404 ARTIST_NOT_FOUND")
    void getById_404() throws Exception {
        doThrow(new ArtistNotFoundException("missing"))
                .when(getUseCase).getById(any(), any());

        mockMvc.perform(get("/api/artists/missing")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    @Test
    @DisplayName("cross-tenant token (tenant_id=wms) → 403 TENANT_FORBIDDEN")
    void crossTenantBlocked() throws Exception {
        mockMvc.perform(get("/api/artists/a-1")
                        .header("Authorization", "Bearer " + jwt.signCrossTenantToken("op-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /api/artists/{id}/status with status=DRAFT → 422 VALIDATION_ERROR (error envelope, not data:null)")
    void changeStatus_draftReturnsErrorEnvelope() throws Exception {
        String body = """
                {"status":"DRAFT"}
                """;
        mockMvc.perform(patch("/api/artists/a-1/status")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                // Error envelope (ApiErrorBody): { code, message, timestamp } — NOT { data, meta }
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }
}
