package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.port.in.ArtistView;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.SearchArtistDirectoryQuery;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.testsupport.JwtTestHelper;
import com.example.fanplatform.artist.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ArtistDirectoryController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class ArtistDirectoryControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean SearchArtistDirectoryUseCase searchUseCase;

    @Test
    @DisplayName("GET /api/artists?q=foo (no auth) → 401")
    void search_noAuth() throws Exception {
        mockMvc.perform(get("/api/artists").param("q", "foo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/artists?q=foo (fan) → 200 + envelope with PageMeta")
    void search_fanReturnsEnvelope() throws Exception {
        ArtistView view = new ArtistView("a-1", "fan-platform", ArtistType.SOLO,
                ArtistStatus.PUBLISHED, "STAGE", null, null, null, null, null,
                Instant.now(), Instant.now(), Instant.now(), null);
        when(searchUseCase.search(any(SearchArtistDirectoryQuery.class)))
                .thenReturn(new DirectorySearchResult(List.of(view), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/artists")
                        .param("q", "STAGE")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stageName").value("STAGE"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/artists?type=BOGUS → 400 VALIDATION_ERROR")
    void search_invalidType() throws Exception {
        mockMvc.perform(get("/api/artists")
                        .param("type", "BOGUS")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
