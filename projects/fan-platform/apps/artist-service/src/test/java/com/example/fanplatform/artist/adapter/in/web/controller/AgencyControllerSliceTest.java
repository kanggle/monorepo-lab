package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.exception.AgencyNameConflictException;
import com.example.fanplatform.artist.application.exception.StoreSellerClosedException;
import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.exception.StoreSellerNotFoundException;
import com.example.fanplatform.artist.application.port.in.AgencyView;
import com.example.fanplatform.artist.application.port.in.LinkAgencyStoreSellerUseCase;
import com.example.fanplatform.artist.application.port.in.ManageAgencyUseCase;
import com.example.fanplatform.artist.domain.agency.AgencyStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TASK-MONO-748 — {@code /api/agencies} envelope, auth gate and error codes. */
@WebMvcTest(controllers = AgencyController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class AgencyControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean ManageAgencyUseCase manageUseCase;
    @MockitoBean LinkAgencyStoreSellerUseCase linkUseCase;

    private static AgencyView view(String sellerId) {
        return new AgencyView("ag-1", "fan-platform", "Aurora Entertainment", AgencyStatus.ACTIVE,
                sellerId, Instant.now(), Instant.now());
    }

    private static String admin() {
        return "Bearer " + jwt.signAdminToken("admin-1");
    }

    @Test
    @DisplayName("POST /api/agencies (ADMIN) → 201 + Location + envelope")
    void create_admin() throws Exception {
        when(manageUseCase.create(any(ActorContext.class), eq("Aurora Entertainment"))).thenReturn(view(null));

        mockMvc.perform(post("/api/agencies")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aurora Entertainment\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/agencies/ag-1"))
                .andExpect(jsonPath("$.data.id").value("ag-1"))
                .andExpect(jsonPath("$.data.name").value("Aurora Entertainment"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/agencies (FAN) → 403 FORBIDDEN, use case never called")
    void create_fanForbidden() throws Exception {
        mockMvc.perform(post("/api/agencies")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(manageUseCase);
    }

    @Test
    @DisplayName("POST /api/agencies duplicate → 409 AGENCY_NAME_CONFLICT")
    void create_duplicate() throws Exception {
        when(manageUseCase.create(any(ActorContext.class), anyString()))
                .thenThrow(new AgencyNameConflictException("Aurora Entertainment"));

        mockMvc.perform(post("/api/agencies")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aurora Entertainment\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGENCY_NAME_CONFLICT"));
    }

    @Test
    @DisplayName("POST /api/agencies blank name → 422 VALIDATION_ERROR")
    void create_blank() throws Exception {
        mockMvc.perform(post("/api/agencies")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(manageUseCase);
    }

    @Test
    @DisplayName("GET /api/agencies/{id} (FAN) → 200 — reads are any authenticated member")
    void get_fan() throws Exception {
        when(manageUseCase.getById(any(ActorContext.class), eq("ag-1"))).thenReturn(view("aurora-goods"));

        mockMvc.perform(get("/api/agencies/ag-1")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeSellerId").value("aurora-goods"));
    }

    @Test
    @DisplayName("PATCH /api/agencies/{id}/status ACTIVE → 422 (only ARCHIVED is a target)")
    void status_activeRejected() throws Exception {
        mockMvc.perform(patch("/api/agencies/ag-1/status")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(manageUseCase);
    }

    @Test
    @DisplayName("PATCH store-seller: ACTIVE seller → 200 with the link")
    void link_ok() throws Exception {
        when(linkUseCase.linkStoreSeller(any(ActorContext.class), eq("ag-1"), eq("aurora-goods")))
                .thenReturn(view("aurora-goods"));

        mockMvc.perform(patch("/api/agencies/ag-1/store-seller")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSellerId\":\"aurora-goods\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeSellerId").value("aurora-goods"));
    }

    @Test
    @DisplayName("PATCH store-seller: null clears")
    void link_clear() throws Exception {
        when(linkUseCase.linkStoreSeller(any(ActorContext.class), eq("ag-1"), isNull())).thenReturn(view(null));

        mockMvc.perform(patch("/api/agencies/ag-1/store-seller")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSellerId\":null}"))
                .andExpect(status().isOk());
        verify(linkUseCase).linkStoreSeller(any(ActorContext.class), eq("ag-1"), isNull());
    }

    @Test
    @DisplayName("PATCH store-seller: missing seller → 422 STORE_SELLER_NOT_FOUND")
    void link_missing() throws Exception {
        when(linkUseCase.linkStoreSeller(any(ActorContext.class), eq("ag-1"), eq("ghost")))
                .thenThrow(new StoreSellerNotFoundException("ghost"));

        mockMvc.perform(patch("/api/agencies/ag-1/store-seller")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSellerId\":\"ghost\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STORE_SELLER_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH store-seller: CLOSED seller → 422 STORE_SELLER_CLOSED")
    void link_closed() throws Exception {
        when(linkUseCase.linkStoreSeller(any(ActorContext.class), eq("ag-1"), eq("gone")))
                .thenThrow(new StoreSellerClosedException("gone"));

        mockMvc.perform(patch("/api/agencies/ag-1/store-seller")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSellerId\":\"gone\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STORE_SELLER_CLOSED"));
    }

    @Test
    @DisplayName("PATCH store-seller: lookup unavailable → 503 STORE_SELLER_LOOKUP_UNAVAILABLE")
    void link_unavailable() throws Exception {
        when(linkUseCase.linkStoreSeller(any(ActorContext.class), eq("ag-1"), eq("aurora-goods")))
                .thenThrow(new StoreSellerLookupUnavailableException("down"));

        mockMvc.perform(patch("/api/agencies/ag-1/store-seller")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSellerId\":\"aurora-goods\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORE_SELLER_LOOKUP_UNAVAILABLE"));
    }
}
