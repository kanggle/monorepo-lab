package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.exception.ArtistNotPublishedException;
import com.example.fanplatform.artist.application.exception.FandomAlreadyExistsException;
import com.example.fanplatform.artist.application.exception.FandomNotFoundException;
import com.example.fanplatform.artist.application.port.in.CreateFandomUseCase;
import com.example.fanplatform.artist.application.port.in.CreateFandomUseCase.CreateFandomCommand;
import com.example.fanplatform.artist.application.port.in.FandomView;
import com.example.fanplatform.artist.application.port.in.GetFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateFandomUseCase.UpdateFandomCommand;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FandomController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class FandomControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean CreateFandomUseCase createUseCase;
    @MockitoBean UpdateFandomUseCase updateUseCase;
    @MockitoBean GetFandomUseCase getUseCase;

    private static final String BODY = """
            {"fandomName":"Hearts"}
            """;

    // -- POST (create) ----------------------------------------------------

    @Test
    @DisplayName("POST /api/fandoms/{id} (FAN role) → 403")
    void post_fanRole403() throws Exception {
        mockMvc.perform(post("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/fandoms/{id} (ADMIN, draft artist) → 422 ARTIST_NOT_PUBLISHED")
    void post_admin_draftArtist422() throws Exception {
        doThrow(new ArtistNotPublishedException("a-1"))
                .when(createUseCase).create(any(CreateFandomCommand.class));

        mockMvc.perform(post("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("POST /api/fandoms/{id} (ADMIN, second create) → 422 FANDOM_ALREADY_EXISTS")
    void post_admin_secondCreate422() throws Exception {
        doThrow(new FandomAlreadyExistsException("a-1"))
                .when(createUseCase).create(any(CreateFandomCommand.class));

        mockMvc.perform(post("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FANDOM_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/fandoms/{id} (ADMIN, valid) → 201 + envelope")
    void post_admin_ok() throws Exception {
        FandomView view = new FandomView("a-1", "fan-platform", "Hearts",
                "#FFAA00", null, "Forever", Instant.now(), Instant.now());
        when(createUseCase.create(any(CreateFandomCommand.class))).thenReturn(view);

        String body = """
                {"fandomName":"Hearts","colorHex":"#FFAA00","slogan":"Forever"}
                """;
        mockMvc.perform(post("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fandomName").value("Hearts"))
                .andExpect(jsonPath("$.data.colorHex").value("#FFAA00"));
    }

    // -- PATCH (update) ---------------------------------------------------

    @Test
    @DisplayName("PATCH /api/fandoms/{id} (FAN role) → 403")
    void patch_fanRole403() throws Exception {
        mockMvc.perform(patch("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/fandoms/{id} (ADMIN, no fandom) → 404 FANDOM_NOT_FOUND")
    void patch_admin_missingFandom404() throws Exception {
        doThrow(new FandomNotFoundException("a-1"))
                .when(updateUseCase).update(any(UpdateFandomCommand.class));

        mockMvc.perform(patch("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FANDOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/fandoms/{id} (ADMIN, valid) → 200 + envelope")
    void patch_admin_ok() throws Exception {
        FandomView view = new FandomView("a-1", "fan-platform", "Hearts",
                "#FFAA00", null, "Forever", Instant.now(), Instant.now());
        when(updateUseCase.update(any(UpdateFandomCommand.class))).thenReturn(view);

        String body = """
                {"fandomName":"Hearts","colorHex":"#FFAA00","slogan":"Forever"}
                """;
        mockMvc.perform(patch("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fandomName").value("Hearts"))
                .andExpect(jsonPath("$.data.colorHex").value("#FFAA00"));
    }
}
