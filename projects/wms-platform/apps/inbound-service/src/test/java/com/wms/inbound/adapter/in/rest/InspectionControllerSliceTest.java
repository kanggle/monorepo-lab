package com.wms.inbound.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.application.port.in.AcknowledgeDiscrepancyUseCase;
import com.wms.inbound.application.port.in.QueryInspectionUseCase;
import com.wms.inbound.application.port.in.RecordInspectionUseCase;
import com.wms.inbound.application.port.in.StartInspectionUseCase;
import com.wms.inbound.application.result.InspectionResult;
import com.wms.inbound.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link InspectionController} — pins the
 * {@code Idempotency-Key} required-guard on {@code inspection:start} and
 * {@code inspection} (record) that was missing before TASK-BE-551 (the two
 * inbound write endpoints that never enforced it, unlike every ASN/putaway
 * sibling and {@code acknowledgeDiscrepancy} in this same controller).
 *
 * <p>Covers AC-2(a) (missing key → 400) and AC-3 (a valid key leaves the happy
 * path unchanged). The shared {@link com.example.web.idempotency.IdempotencyKeyFilter}
 * dedupe/409 behaviour (AC-2 b/c) is proven against these exact paths by the
 * companion {@link InspectionControllerIdempotencyFilterTest}.
 */
@WebMvcTest(controllers = InspectionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class InspectionControllerSliceTest {

    private static final UUID ASN_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID INSPECTION_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID ASN_LINE_ID = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired MockMvc mockMvc;

    @MockitoBean StartInspectionUseCase startInspection;
    @MockitoBean RecordInspectionUseCase recordInspection;
    @MockitoBean AcknowledgeDiscrepancyUseCase acknowledgeDiscrepancy;
    @MockitoBean QueryInspectionUseCase queryInspection;

    private static InspectionResult stubResult() {
        return new InspectionResult(
                INSPECTION_ID, ASN_ID, "user-123", NOW, "ok",
                1L, NOW, List.of(), List.of());
    }

    private static String validRecordBody() {
        return """
                {
                  "notes": "inspection ok",
                  "lines": [
                    { "asnLineId": "%s", "qtyPassed": 10, "qtyDamaged": 0, "qtyShort": 0 }
                  ]
                }
                """.formatted(ASN_LINE_ID);
    }

    // -------------------------------------------------------------------------
    // POST .../inspection:start (startInspection)
    // -------------------------------------------------------------------------

    @Test
    void startInspection_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // Guard fires before the domain — the use case is never reached.
        verifyNoInteractions(startInspection);
    }

    @Test
    void startInspection_withKeyAndWriteRole_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                        .with(jwt().jwt(b -> b.subject("user-123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header(IDEMPOTENCY_KEY, "idem-start-1"))
                .andExpect(status().isOk());
        verify(startInspection).start(any());
    }

    @Test
    void startInspection_withReadOnlyRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ")))
                        .header(IDEMPOTENCY_KEY, "idem-start-2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void startInspection_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                        .header(IDEMPOTENCY_KEY, "idem-start-3"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // POST .../inspection (recordInspection)
    // -------------------------------------------------------------------------

    @Test
    void recordInspection_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecordBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(recordInspection);
    }

    @Test
    void recordInspection_withKeyAndWriteRole_returns201() throws Exception {
        when(recordInspection.record(any())).thenReturn(stubResult());

        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .with(jwt().jwt(b -> b.subject("user-123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header(IDEMPOTENCY_KEY, "idem-record-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecordBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(INSPECTION_ID.toString()));
        verify(recordInspection).record(any());
    }

    @Test
    void recordInspection_withReadOnlyRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ")))
                        .header(IDEMPOTENCY_KEY, "idem-record-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecordBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordInspection_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .header(IDEMPOTENCY_KEY, "idem-record-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecordBody()))
                .andExpect(status().isUnauthorized());
    }
}
