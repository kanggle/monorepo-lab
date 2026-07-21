package com.wms.inbound.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.web.idempotency.IdempotencyFilterConfig;
import com.example.web.idempotency.IdempotencyKeyFilter;
import com.example.web.idempotency.JsonValueBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.rest.dto.RecordInspectionRequest;
import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.adapter.in.web.filter.InboundIdempotencyErrorWriter;
import com.wms.inbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inbound.application.port.in.AcknowledgeDiscrepancyUseCase;
import com.wms.inbound.application.port.in.QueryInspectionUseCase;
import com.wms.inbound.application.port.in.RecordInspectionUseCase;
import com.wms.inbound.application.port.in.StartInspectionUseCase;
import com.wms.inbound.application.result.InspectionResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Proves the shared {@link IdempotencyKeyFilter} (registered blanket for
 * {@code POST /api/v1/inbound/*} in
 * {@link com.wms.inbound.config.IdempotencyConfig}) actually reaches
 * {@code InspectionController}'s {@code inspection:start} and {@code inspection}
 * paths, and that once a key IS sent the dedupe + {@code DUPLICATE_REQUEST}
 * behaviour works (TASK-BE-551 AC-2 b/c).
 *
 * <p>Docker-free: uses the same {@link IdempotencyFilterConfig} shape as
 * production ({@code POST} + prefix {@code /api/v1/inbound/}, webhook-skipping)
 * with the in-memory store, so the filter's own {@code shouldApply} predicate —
 * not a test-only URL pattern — decides that these paths are covered.
 */
class InspectionControllerIdempotencyFilterTest {

    private static final UUID ASN_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID INSPECTION_ID = UUID.fromString("22222222-0000-7000-8000-000000000002");
    private static final UUID ASN_LINE_ID = UUID.fromString("33333333-0000-7000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");

    private StartInspectionUseCase startInspection;
    private RecordInspectionUseCase recordInspection;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        startInspection = mock(StartInspectionUseCase.class);
        recordInspection = mock(RecordInspectionUseCase.class);
        AcknowledgeDiscrepancyUseCase acknowledgeDiscrepancy = mock(AcknowledgeDiscrepancyUseCase.class);
        QueryInspectionUseCase queryInspection = mock(QueryInspectionUseCase.class);

        InspectionController controller = new InspectionController(
                startInspection, recordInspection, acknowledgeDiscrepancy, queryInspection);

        ObjectMapper objectMapper = new ObjectMapper();
        // Mirror IdempotencyConfig: POST, /api/v1/inbound/ prefix (webhook-skipping).
        IdempotencyFilterConfig config = IdempotencyFilterConfig.builder()
                .methods("POST")
                .applyToPrefixSkippingWebhook("/api/v1/inbound/", "/webhooks/")
                .lockTtl(Duration.ofSeconds(30))
                .entryTtl(Duration.ofHours(24))
                .build();
        IdempotencyKeyFilter filter = new IdempotencyKeyFilter(
                new InMemoryIdempotencyStore(),
                new JsonValueBodyCanonicalizer(objectMapper),
                new InboundIdempotencyErrorWriter(objectMapper),
                null,
                config);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                // Resolve @AuthenticationPrincipal Jwt to null (no security context here) —
                // the controller falls back to actorId "anonymous"; this test targets the
                // idempotency filter, not auth.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(filter)
                .build();
    }

    private static InspectionResult stubResult() {
        return new InspectionResult(
                INSPECTION_ID, ASN_ID, "user-123", NOW, "ok",
                1L, NOW, List.of(), List.of());
    }

    private static String recordBody(int qtyPassed) {
        return """
                {
                  "notes": "inspection",
                  "lines": [
                    { "asnLineId": "%s", "qtyPassed": %d, "qtyDamaged": 0, "qtyShort": 0 }
                  ]
                }
                """.formatted(ASN_LINE_ID, qtyPassed);
    }

    @Test
    void startInspection_sameKey_isReplayedNotReExecuted() throws Exception {
        // First call reaches the :start path through the filter.
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                        .header("Idempotency-Key", "idem-start-replay"))
                .andExpect(status().isOk());
        // Same key + same (empty) body → filter replays the cached 200; controller not re-invoked.
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                        .header("Idempotency-Key", "idem-start-replay"))
                .andExpect(status().isOk());

        verify(startInspection, times(1)).start(any());
    }

    @Test
    void recordInspection_sameKeySameBody_isReplayedNotReExecuted() throws Exception {
        when(recordInspection.record(any())).thenReturn(stubResult());

        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .header("Idempotency-Key", "idem-record-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(10)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .header("Idempotency-Key", "idem-record-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(10)))
                .andExpect(status().isCreated());

        verify(recordInspection, times(1)).record(any());
    }

    @Test
    void recordInspection_sameKeyDifferentBody_returns409DuplicateRequest() throws Exception {
        when(recordInspection.record(any())).thenReturn(stubResult());

        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .header("Idempotency-Key", "idem-record-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(10)))
                .andExpect(status().isCreated());
        // Same key, different body → shared filter emits 409 DUPLICATE_REQUEST.
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .header("Idempotency-Key", "idem-record-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordBody(7)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        verify(recordInspection, times(1)).record(any());
    }
}
