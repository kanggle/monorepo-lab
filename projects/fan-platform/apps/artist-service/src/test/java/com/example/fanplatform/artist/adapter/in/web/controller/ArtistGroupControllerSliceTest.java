package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.port.in.AddGroupMemberUseCase;
import com.example.fanplatform.artist.application.port.in.ArtistGroupView;
import com.example.fanplatform.artist.application.port.in.CreateArtistGroupUseCase;
import com.example.fanplatform.artist.application.port.in.CreateArtistGroupUseCase.CreateArtistGroupCommand;
import com.example.fanplatform.artist.application.port.in.GetArtistGroupUseCase;
import com.example.fanplatform.artist.application.port.in.RemoveGroupMemberUseCase;
import com.example.fanplatform.artist.domain.group.ArtistGroupStatus;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ArtistGroupController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class ArtistGroupControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean CreateArtistGroupUseCase createUseCase;
    @MockitoBean GetArtistGroupUseCase getUseCase;
    @MockitoBean AddGroupMemberUseCase addMemberUseCase;
    @MockitoBean RemoveGroupMemberUseCase removeMemberUseCase;

    @Test
    @DisplayName("POST /api/artist-groups (FAN role) → 403")
    void create_fanRole403() throws Exception {
        String body = """
                {"name":"Group X"}
                """;
        mockMvc.perform(post("/api/artist-groups")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/artist-groups (ADMIN) → 201")
    void create_adminCreated() throws Exception {
        ArtistGroupView view = new ArtistGroupView("g-1", "fan-platform", "Group X",
                null, null, null, ArtistGroupStatus.ACTIVE, Instant.now(), Instant.now(), List.of());
        when(createUseCase.create(any(CreateArtistGroupCommand.class))).thenReturn(view);

        String body = """
                {"name":"Group X"}
                """;
        mockMvc.perform(post("/api/artist-groups")
                        .header("Authorization", "Bearer " + jwt.signAdminToken("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("g-1"));
    }
}
