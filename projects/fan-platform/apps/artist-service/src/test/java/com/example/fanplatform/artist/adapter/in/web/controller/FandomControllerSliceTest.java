package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.exception.ArtistNotPublishedException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @MockitoBean UpdateFandomUseCase updateUseCase;
    @MockitoBean GetFandomUseCase getUseCase;

    @Test
    @DisplayName("PUT /api/fandoms/{id} (FAN role) → 403")
    void put_fanRole403() throws Exception {
        String body = """
                {"fandomName":"Hearts"}
                """;
        mockMvc.perform(put("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/fandoms/{id} (ADMIN, draft artist) → 422 ARTIST_NOT_PUBLISHED")
    void put_admin_draftArtist422() throws Exception {
        doThrow(new ArtistNotPublishedException("a-1"))
                .when(updateUseCase).upsert(any(UpdateFandomCommand.class));

        String body = """
                {"fandomName":"Hearts"}
                """;
        mockMvc.perform(put("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("PUT /api/fandoms/{id} (ADMIN, valid) → 200 + envelope")
    void put_admin_ok() throws Exception {
        FandomView view = new FandomView("a-1", "fan-platform", "Hearts",
                "#FFAA00", null, "Forever", Instant.now(), Instant.now());
        when(updateUseCase.upsert(any(UpdateFandomCommand.class))).thenReturn(view);

        String body = """
                {"fandomName":"Hearts","colorHex":"#FFAA00","slogan":"Forever"}
                """;
        mockMvc.perform(put("/api/fandoms/a-1")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fandomName").value("Hearts"))
                .andExpect(jsonPath("$.data.colorHex").value("#FFAA00"));
    }
}
