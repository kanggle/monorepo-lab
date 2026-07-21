package com.wms.inbound.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.web.idempotency.IdempotencyFilterConfig;
import com.example.web.idempotency.IdempotencyKeyFilter;
import com.example.web.idempotency.JsonValueBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.adapter.in.web.filter.InboundIdempotencyErrorWriter;
import com.wms.inbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inbound.application.port.in.AcknowledgeDiscrepancyUseCase;
import com.wms.inbound.application.port.in.QueryInspectionUseCase;
import com.wms.inbound.application.port.in.RecordInspectionUseCase;
import com.wms.inbound.application.port.in.StartInspectionUseCase;
import com.wms.inbound.application.result.InspectionResult;
import com.wms.inbound.config.SecurityConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller web-slice for {@link InspectionController} — pins the
 * {@code Idempotency-Key} required-guard on {@code inspection:start} and
 * {@code inspection} (record) that was missing before TASK-BE-551 (the two
 * inbound write endpoints that never enforced it, unlike every ASN/putaway
 * sibling and {@code acknowledgeDiscrepancy} in this same controller).
 *
 * <p>The outer {@code @WebMvcTest} slice covers AC-2(a) (missing key → 400) and
 * AC-3 (a valid key leaves the happy path unchanged). The nested
 * {@link IdempotencyFilterReach} proves the shared
 * {@link IdempotencyKeyFilter} — registered blanket for
 * {@code POST /api/v1/inbound/*} in
 * {@link com.wms.inbound.config.IdempotencyConfig} — actually reaches these two
 * paths, and that once a key IS sent the dedupe + {@code DUPLICATE_REQUEST}
 * behaviour works (AC-2 b/c). Both mechanisms live in one compliant
 * {@code *ControllerSliceTest} file (scripts/check-controller-slice-naming.sh).
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

    private static String validRecordBody(int qtyPassed) {
        return """
                {
                  "notes": "inspection ok",
                  "lines": [
                    { "asnLineId": "%s", "qtyPassed": %d, "qtyDamaged": 0, "qtyShort": 0 }
                  ]
                }
                """.formatted(ASN_LINE_ID, qtyPassed);
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
                        .content(validRecordBody(10)))
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
                        .content(validRecordBody(10)))
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
                        .content(validRecordBody(10)))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordInspection_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                        .header(IDEMPOTENCY_KEY, "idem-record-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecordBody(10)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Proves the shared {@link IdempotencyKeyFilter} reaches the inspection
     * paths and dedupes once a key is sent (AC-2 b/c). Docker-free: a standalone
     * MockMvc wiring the real filter through the <em>same</em>
     * {@link IdempotencyFilterConfig} shape as production ({@code POST} + prefix
     * {@code /api/v1/inbound/}, webhook-skipping) with the in-memory store, so
     * the filter's own {@code shouldApply} predicate — not a test-only URL
     * pattern — decides these paths are covered.
     */
    @Nested
    class IdempotencyFilterReach {

        private StartInspectionUseCase start;
        private RecordInspectionUseCase record;
        private MockMvc filterMockMvc;

        @BeforeEach
        void setUp() {
            start = mock(StartInspectionUseCase.class);
            record = mock(RecordInspectionUseCase.class);
            AcknowledgeDiscrepancyUseCase ack = mock(AcknowledgeDiscrepancyUseCase.class);
            QueryInspectionUseCase query = mock(QueryInspectionUseCase.class);

            InspectionController controller = new InspectionController(start, record, ack, query);

            ObjectMapper objectMapper = new ObjectMapper();
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

            filterMockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    // Resolve @AuthenticationPrincipal Jwt to null (no security context here) —
                    // the controller falls back to actorId "anonymous"; this targets the filter.
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .addFilters(filter)
                    .build();
        }

        @Test
        void startInspection_sameKey_isReplayedNotReExecuted() throws Exception {
            filterMockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                            .header(IDEMPOTENCY_KEY, "idem-start-replay"))
                    .andExpect(status().isOk());
            // Same key + same (empty) body → filter replays the cached 200; controller not re-invoked.
            filterMockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection:start", ASN_ID)
                            .header(IDEMPOTENCY_KEY, "idem-start-replay"))
                    .andExpect(status().isOk());

            verify(start, times(1)).start(any());
        }

        @Test
        void recordInspection_sameKeySameBody_isReplayedNotReExecuted() throws Exception {
            when(record.record(any())).thenReturn(stubResult());

            filterMockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                            .header(IDEMPOTENCY_KEY, "idem-record-replay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecordBody(10)))
                    .andExpect(status().isCreated());
            filterMockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                            .header(IDEMPOTENCY_KEY, "idem-record-replay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecordBody(10)))
                    .andExpect(status().isCreated());

            verify(record, times(1)).record(any());
        }

        @Test
        void recordInspection_sameKeyDifferentBody_returns409DuplicateRequest() throws Exception {
            when(record.record(any())).thenReturn(stubResult());

            filterMockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                            .header(IDEMPOTENCY_KEY, "idem-record-conflict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecordBody(10)))
                    .andExpect(status().isCreated());
            // Same key, different body → shared filter emits 409 DUPLICATE_REQUEST.
            filterMockMvc.perform(post("/api/v1/inbound/asns/{asnId}/inspection", ASN_ID)
                            .header(IDEMPOTENCY_KEY, "idem-record-conflict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRecordBody(7)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

            verify(record, times(1)).record(any());
        }
    }
}
