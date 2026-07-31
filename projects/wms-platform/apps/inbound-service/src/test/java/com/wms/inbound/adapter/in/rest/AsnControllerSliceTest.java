package com.wms.inbound.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.application.port.in.CancelAsnUseCase;
import com.wms.inbound.application.port.in.CloseAsnUseCase;
import com.wms.inbound.application.port.in.QueryAsnUseCase;
import com.wms.inbound.application.port.in.ReceiveAsnUseCase;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.application.result.AsnSummaryResult;
import com.wms.inbound.application.result.CloseAsnResult;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import com.wms.inbound.config.SecurityConfig;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AsnController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class AsnControllerSliceTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ReceiveAsnUseCase receiveAsnUseCase;
    @MockitoBean CancelAsnUseCase cancelAsnUseCase;
    @MockitoBean CloseAsnUseCase closeAsnUseCase;
    @MockitoBean QueryAsnUseCase queryAsnUseCase;

    private static final UUID ASN_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");

    private static AsnResult stubResult() {
        return new AsnResult(
                ASN_ID, "ASN-20260429-0001", "MANUAL",
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 5, 1), "notes",
                "CREATED", 0L, NOW, "user-123", NOW,
                List.of());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/inbound/asns
    // -------------------------------------------------------------------------

    @Test
    void createAsn_withWriteRole_returns201() throws Exception {
        when(receiveAsnUseCase.receive(any())).thenReturn(stubResult());

        mockMvc.perform(post("/api/v1/inbound/asns")
                        .with(jwt().jwt(b -> b.subject("user-123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asnNo").value("ASN-20260429-0001"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void createAsn_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns")
                        .header("Idempotency-Key", "idem-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAsn_withReadOnlyRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns")
                        .with(jwt().jwt(b -> b.subject("reader"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ")))
                        .header("Idempotency-Key", "idem-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAsn_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAsn_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/inbound/asns/{id}
    // -------------------------------------------------------------------------

    @Test
    void getAsn_withReadRole_returns200() throws Exception {
        when(queryAsnUseCase.findById(ASN_ID)).thenReturn(stubResult());

        mockMvc.perform(get("/api/v1/inbound/asns/{id}", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ASN_ID.toString()))
                .andExpect(jsonPath("$.asnNo").value("ASN-20260429-0001"));
    }

    @Test
    void getAsn_notFound_returns404() throws Exception {
        when(queryAsnUseCase.findById(ASN_ID)).thenThrow(new AsnNotFoundException(ASN_ID));

        mockMvc.perform(get("/api/v1/inbound/asns/{id}", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASN_NOT_FOUND"));
    }

    @Test
    void getAsn_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/inbound/asns/{id}", ASN_ID))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/inbound/asns (list)
    // -------------------------------------------------------------------------

    @Test
    void listAsns_withReadRole_returns200() throws Exception {
        when(queryAsnUseCase.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/inbound/asns")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0))
                .andExpect(jsonPath("$.sort").value("createdAt,desc"));
    }

    @Test
    void listAsns_multiPage_returnsContentAndTotalPages() throws Exception {
        AsnSummaryResult summary = new AsnSummaryResult(
                ASN_ID, "ASN-20260429-0001", "MANUAL",
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 5, 1), "CREATED", 0L, NOW);
        when(queryAsnUseCase.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(summary), 0, 20, 25L, 2));

        mockMvc.perform(get("/api/v1/inbound/asns")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ASN_ID.toString()))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(25))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void listAsns_sizeAboveMax_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/inbound/asns")
                        .param("size", "101")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/inbound/asns/{id}:close
    // -------------------------------------------------------------------------

    @Test
    void closeAsn_withWriteRole_returns200() throws Exception {
        when(closeAsnUseCase.close(any())).thenReturn(stubCloseResult());

        mockMvc.perform(post("/api/v1/inbound/asns/{id}:close", ASN_ID)
                        .with(jwt().jwt(b -> b.subject("user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-close-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.summary.expectedTotal").value(100));
    }

    @Test
    void closeAsn_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{id}:close", ASN_ID)
                        .header("Idempotency-Key", "idem-close-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 4}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void closeAsn_withReadOnlyRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{id}:close", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ")))
                        .header("Idempotency-Key", "idem-close-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 4}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeAsn_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{id}:close", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeAsn_stateTransitionInvalid_returns422() throws Exception {
        when(closeAsnUseCase.close(any()))
                .thenThrow(new StateTransitionInvalidException("IN_PUTAWAY", "CLOSED"));

        mockMvc.perform(post("/api/v1/inbound/asns/{id}:close", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-close-422")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static CloseAsnResult stubCloseResult() {
        return new CloseAsnResult(
                ASN_ID, "ASN-20260429-0001", "CLOSED", NOW, "user-1",
                new CloseAsnResult.Summary(100, 95, 3, 2, 95, 1),
                5L);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static String validCreateBody() {
        UUID partnerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        return String.format("""
                {
                  "supplierPartnerId": "%s",
                  "warehouseId": "%s",
                  "expectedArriveDate": "2026-05-01",
                  "lines": [{"skuId": "%s", "expectedQty": 10}]
                }""", partnerId, warehouseId, skuId);
    }
}
