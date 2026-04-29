package com.wms.inbound.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.application.port.in.ConfirmPutawayLineUseCase;
import com.wms.inbound.application.port.in.GetPutawayInstructionUseCase;
import com.wms.inbound.application.port.in.InstructPutawayUseCase;
import com.wms.inbound.application.port.in.SkipPutawayLineUseCase;
import com.wms.inbound.application.result.PutawayConfirmationResult;
import com.wms.inbound.application.result.PutawayInstructionResult;
import com.wms.inbound.application.result.PutawaySkipResult;
import com.wms.inbound.config.SecurityConfig;
import com.wms.inbound.domain.exception.PutawayInstructionNotFoundException;
import com.wms.inbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PutawayController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class PutawayControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean InstructPutawayUseCase instructPutaway;
    @MockBean ConfirmPutawayLineUseCase confirmPutawayLine;
    @MockBean SkipPutawayLineUseCase skipPutawayLine;
    @MockBean GetPutawayInstructionUseCase queryPutaway;

    private static final UUID ASN_ID = UUID.randomUUID();
    private static final UUID INSTRUCTION_ID = UUID.randomUUID();
    private static final UUID LINE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");

    // ---------------------------------------------------------------------
    // POST /putaway:instruct
    // ---------------------------------------------------------------------

    @Test
    void instruct_withWriteRole_returns201() throws Exception {
        when(instructPutaway.instruct(any())).thenReturn(stubInstructionResult("PENDING"));

        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/putaway:instruct", ASN_ID)
                        .with(jwt().jwt(b -> b.subject("user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInstructBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.putawayInstructionId").value(INSTRUCTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void instruct_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/putaway:instruct", ASN_ID)
                        .header("Idempotency-Key", "idem-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInstructBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void instruct_withReadOnlyRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/putaway:instruct", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ")))
                        .header("Idempotency-Key", "idem-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInstructBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void instruct_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/putaway:instruct", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInstructBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void instruct_emptyLines_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/putaway:instruct", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\": [], \"version\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void instruct_stateTransitionInvalid_returns422() throws Exception {
        when(instructPutaway.instruct(any()))
                .thenThrow(new StateTransitionInvalidException("CREATED", "IN_PUTAWAY"));

        mockMvc.perform(post("/api/v1/inbound/asns/{asnId}/putaway:instruct", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-422")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInstructBody()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------------
    // POST /putaway/{instructionId}/lines/{lineId}:confirm
    // ---------------------------------------------------------------------

    @Test
    void confirm_withWriteRole_returns200() throws Exception {
        when(confirmPutawayLine.confirm(any())).thenReturn(stubConfirmationResult("COMPLETED"));

        mockMvc.perform(post("/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:confirm",
                        INSTRUCTION_ID, LINE_ID)
                        .with(jwt().jwt(b -> b.subject("user-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"actualLocationId\": \"%s\", \"qtyConfirmed\": 50}",
                                UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction.status").value("COMPLETED"))
                .andExpect(jsonPath("$.asn.status").value("PUTAWAY_DONE"));
    }

    @Test
    void confirm_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:confirm",
                        INSTRUCTION_ID, LINE_ID)
                        .header("Idempotency-Key", "idem-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"actualLocationId\": \"%s\", \"qtyConfirmed\": 50}",
                                UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // POST /putaway/{instructionId}/lines/{lineId}:skip
    // ---------------------------------------------------------------------

    @Test
    void skip_withWriteRole_returns200() throws Exception {
        when(skipPutawayLine.skip(any())).thenReturn(stubSkipResult());

        mockMvc.perform(post("/api/v1/inbound/putaway/{instructionId}/lines/{lineId}:skip",
                        INSTRUCTION_ID, LINE_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_WRITE")))
                        .header("Idempotency-Key", "idem-skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Location unavailable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    // ---------------------------------------------------------------------
    // GET /putaway/{instructionId}
    // ---------------------------------------------------------------------

    @Test
    void getInstruction_withReadRole_returns200() throws Exception {
        when(queryPutaway.findByInstructionId(INSTRUCTION_ID))
                .thenReturn(stubInstructionResult("COMPLETED"));

        mockMvc.perform(get("/api/v1/inbound/putaway/{instructionId}", INSTRUCTION_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.putawayInstructionId").value(INSTRUCTION_ID.toString()));
    }

    @Test
    void getInstruction_notFound_returns404() throws Exception {
        when(queryPutaway.findByInstructionId(INSTRUCTION_ID))
                .thenThrow(new PutawayInstructionNotFoundException(INSTRUCTION_ID));

        mockMvc.perform(get("/api/v1/inbound/putaway/{instructionId}", INSTRUCTION_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInstructionByAsn_withReadRole_returns200() throws Exception {
        when(queryPutaway.findByAsnId(ASN_ID)).thenReturn(stubInstructionResult("PENDING"));

        mockMvc.perform(get("/api/v1/inbound/asns/{asnId}/putaway", ASN_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asnId").value(ASN_ID.toString()));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static String validInstructBody() {
        UUID asnLineId = UUID.randomUUID();
        UUID destinationLocationId = UUID.randomUUID();
        return String.format("""
                {
                  "lines": [
                    {"asnLineId": "%s", "destinationLocationId": "%s", "qtyToPutaway": 95}
                  ],
                  "version": 2
                }""", asnLineId, destinationLocationId);
    }

    private static PutawayInstructionResult stubInstructionResult(String status) {
        return new PutawayInstructionResult(
                INSTRUCTION_ID, ASN_ID, "IN_PUTAWAY", UUID.randomUUID(), "user-1",
                status, 0L, NOW, NOW,
                List.of(new PutawayInstructionResult.Line(
                        LINE_ID, UUID.randomUUID(), UUID.randomUUID(), null, null,
                        UUID.randomUUID(), "WH01-A-01-01-01", 95, "PENDING")));
    }

    private static PutawayConfirmationResult stubConfirmationResult(String instructionStatus) {
        return new PutawayConfirmationResult(
                UUID.randomUUID(), LINE_ID, INSTRUCTION_ID,
                UUID.randomUUID(), "WH01-A-01-01-01", 50, "user-1", NOW,
                new PutawayConfirmationResult.InstructionState(instructionStatus, 1L, 0L, 1),
                new PutawayConfirmationResult.AsnState("PUTAWAY_DONE"));
    }

    private static PutawaySkipResult stubSkipResult() {
        return new PutawaySkipResult(
                LINE_ID, "SKIPPED", "Location unavailable", "user-1", NOW,
                new PutawayConfirmationResult.InstructionState("PARTIALLY_COMPLETED", 0L, 1L, 1),
                new PutawayConfirmationResult.AsnState("PUTAWAY_DONE"));
    }
}
