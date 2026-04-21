package com.wms.master.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.master.application.command.CreateLotCommand;
import com.wms.master.application.command.DeactivateLotCommand;
import com.wms.master.application.command.ReactivateLotCommand;
import com.wms.master.application.command.UpdateLotCommand;
import com.wms.master.application.port.in.LotCrudUseCase;
import com.wms.master.application.port.in.LotQueryUseCase;
import com.wms.master.application.query.ListLotsQuery;
import com.wms.master.application.result.LotResult;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.exception.LotNotFoundException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.model.LotStatus;
import com.wms.master.testsupport.TestConstants;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebMvcTest for {@link LotController}. Mirrors {@link SkuControllerTest} in structure.
 * Covers all 7 Lot endpoints with 20+ test methods including ISO 8601 timestamp
 * assertions on 422 error envelopes (BE-008 compliance).
 */
@WebMvcTest(controllers = LotController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LotControllerTest {

    private static final String ACTOR = "user-lot-test";
    private static final Instant NOW = Instant.parse("2026-04-20T00:00:00Z");
    private static final LocalDate MFD = LocalDate.of(2026, 1, 1);
    private static final LocalDate EXP = LocalDate.of(2026, 12, 31);
    private static final String ISO_TIMESTAMP_REGEX = TestConstants.ISO_TIMESTAMP_REGEX;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LotCrudUseCase crudUseCase;

    @MockitoBean
    private LotQueryUseCase queryUseCase;

    // ---------- POST /api/v1/master/skus/{skuId}/lots ----------

    @Test
    void create_returns201_withEtag_andLocation() throws Exception {
        UUID skuId = UUID.randomUUID();
        LotResult result = sampleResult(skuId, "LOT-001", LotStatus.ACTIVE, 0L);
        when(crudUseCase.create(any(CreateLotCommand.class))).thenReturn(result);

        String body = """
                {
                  "lotNo": "LOT-001",
                  "manufacturedDate": "2026-01-01",
                  "expiryDate": "2026-12-31"
                }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location", "/api/v1/master/lots/" + result.id()))
                .andExpect(jsonPath("$.lotNo").value("LOT-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void create_returns400_whenLotNoMissing() throws Exception {
        UUID skuId = UUID.randomUUID();
        String body = """
                { "expiryDate": "2026-12-31" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.lotNo").exists());
    }

    @Test
    void create_returns400_whenLotNoBlank() throws Exception {
        UUID skuId = UUID.randomUUID();
        String body = """
                { "lotNo": "" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns404_whenSkuNotFound() throws Exception {
        UUID skuId = UUID.randomUUID();
        when(crudUseCase.create(any())).thenThrow(new SkuNotFoundException(skuId.toString()));

        String body = """
                { "lotNo": "LOT-001" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SKU_NOT_FOUND"));
    }

    @Test
    void create_returns422_whenParentSkuNotActive() throws Exception {
        UUID skuId = UUID.randomUUID();
        when(crudUseCase.create(any())).thenThrow(
                new InvalidStateTransitionException("parent SKU " + skuId + " is not ACTIVE"));

        String body = """
                { "lotNo": "LOT-001" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"))
                // BE-008: ISO 8601 timestamp must be present in every error envelope
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(ISO_TIMESTAMP_REGEX)));
    }

    @Test
    void create_returns422_whenParentSkuNotLotTracked() throws Exception {
        UUID skuId = UUID.randomUUID();
        when(crudUseCase.create(any())).thenThrow(
                new InvalidStateTransitionException("parent SKU " + skuId + " is not LOT-tracked"));

        String body = """
                { "lotNo": "LOT-001" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"))
                // BE-008: ISO 8601 timestamp must be present in every error envelope
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(ISO_TIMESTAMP_REGEX)));
    }

    @Test
    void create_returns409_whenLotNoDuplicate() throws Exception {
        UUID skuId = UUID.randomUUID();
        when(crudUseCase.create(any())).thenThrow(new LotNoDuplicateException(skuId, "LOT-DUP"));

        String body = """
                { "lotNo": "LOT-DUP" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + skuId + "/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LOT_NO_DUPLICATE"));
    }

    // ---------- GET /api/v1/master/lots/{id} ----------

    @Test
    void getById_returns200_withEtag() throws Exception {
        UUID skuId = UUID.randomUUID();
        LotResult result = sampleResult(skuId, "LOT-002", LotStatus.ACTIVE, 3L);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/lots/" + result.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.id").value(result.id().toString()))
                .andExpect(jsonPath("$.lotNo").value("LOT-002"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenThrow(new LotNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/master/lots/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LOT_NOT_FOUND"));
    }

    // ---------- GET /api/v1/master/skus/{skuId}/lots ----------

    @Test
    void listBySku_returnsPageEnvelope() throws Exception {
        UUID skuId = UUID.randomUUID();
        LotResult r = sampleResult(skuId, "LOT-003", LotStatus.ACTIVE, 0L);
        when(queryUseCase.list(any(ListLotsQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/skus/" + skuId + "/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].lotNo").value("LOT-003"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void listBySku_returns400_whenStatusInvalid() throws Exception {
        UUID skuId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/master/skus/" + skuId + "/lots")
                        .param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- GET /api/v1/master/lots ----------

    @Test
    void list_returnsPageEnvelope_withFilters() throws Exception {
        UUID skuId = UUID.randomUUID();
        LotResult r = sampleResult(skuId, "LOT-004", LotStatus.ACTIVE, 0L);
        when(queryUseCase.list(any(ListLotsQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/lots")
                        .param("status", "ACTIVE")
                        .param("skuId", skuId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].skuId").value(skuId.toString()));
    }

    @Test
    void list_returns400_whenStatusInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/master/lots").param("status", "GARBAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returns400_whenExpiryBeforeInvalidDate() throws Exception {
        mockMvc.perform(get("/api/v1/master/lots").param("expiryBefore", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- PATCH /api/v1/master/lots/{id} ----------

    @Test
    void update_returns200_andEtag() throws Exception {
        UUID skuId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        LotResult result = new LotResult(
                lotId, skuId, "LOT-005", MFD, LocalDate.of(2027, 1, 1),
                null, LotStatus.ACTIVE, 2L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.update(any(UpdateLotCommand.class))).thenReturn(result);

        String body = """
                { "expiryDate": "2027-01-01", "version": 1 }
                """;

        mockMvc.perform(patch("/api/v1/master/lots/" + lotId)
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""))
                .andExpect(jsonPath("$.expiryDate").value("2027-01-01"));
    }

    @Test
    void update_returns422_whenSkuIdAttempted() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("skuId"));

        String body = """
                { "skuId": "%s", "version": 0 }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(patch("/api/v1/master/lots/" + lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns422_whenLotNoAttempted() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("lotNo"));

        String body = """
                { "lotNo": "NEW-LOT", "version": 0 }
                """;

        mockMvc.perform(patch("/api/v1/master/lots/" + lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns422_whenManufacturedDateAttempted() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("manufacturedDate"));

        String body = """
                { "manufacturedDate": "2025-01-01", "version": 0 }
                """;

        mockMvc.perform(patch("/api/v1/master/lots/" + lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns409_onVersionMismatch() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(
                new ConcurrencyConflictException("Lot", lotId.toString(), 2L, 5L));

        String body = """
                { "expiryDate": "2027-01-01", "version": 2 }
                """;

        mockMvc.perform(patch("/api/v1/master/lots/" + lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void update_returns400_whenVersionMissing() throws Exception {
        UUID lotId = UUID.randomUUID();
        String body = """
                { "expiryDate": "2027-01-01" }
                """;

        mockMvc.perform(patch("/api/v1/master/lots/" + lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.version").exists());
    }

    // ---------- POST /api/v1/master/lots/{id}/deactivate ----------

    @Test
    void deactivate_returns200_andEtagBumped() throws Exception {
        UUID skuId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        LotResult result = new LotResult(
                lotId, skuId, "LOT-006", MFD, EXP,
                null, LotStatus.INACTIVE, 2L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.deactivate(any(DeactivateLotCommand.class))).thenReturn(result);

        String body = """
                { "version": 1, "reason": "damaged stock" }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/deactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivate_returns422_onInvalidTransition() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.deactivate(any())).thenThrow(
                new InvalidStateTransitionException("INACTIVE", "deactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    void deactivate_returns404_whenLotNotFound() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.deactivate(any())).thenThrow(new LotNotFoundException(lotId.toString()));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LOT_NOT_FOUND"));
    }

    // ---------- POST /api/v1/master/lots/{id}/reactivate ----------

    @Test
    void reactivate_returns200_andEtagBumped() throws Exception {
        UUID skuId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        LotResult result = new LotResult(
                lotId, skuId, "LOT-007", MFD, EXP,
                null, LotStatus.ACTIVE, 3L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.reactivate(any(ReactivateLotCommand.class))).thenReturn(result);

        String body = """
                { "version": 2 }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/reactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void reactivate_returns422_whenReactivatingFromExpired() throws Exception {
        // Critical edge case: EXPIRED is terminal for reactivation per domain-model.md §6.
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.reactivate(any())).thenThrow(
                new InvalidStateTransitionException("EXPIRED", "reactivate"));

        String body = """
                { "version": 2 }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"))
                // BE-008: ISO 8601 timestamp must be present in every 422 error envelope
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(ISO_TIMESTAMP_REGEX)));
    }

    @Test
    void reactivate_returns422_whenAlreadyActive() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.reactivate(any())).thenThrow(
                new InvalidStateTransitionException("ACTIVE", "reactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"))
                // BE-008: second 422 case asserting ISO 8601 timestamp regex
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(ISO_TIMESTAMP_REGEX)));
    }

    @Test
    void reactivate_returns404_whenLotNotFound() throws Exception {
        UUID lotId = UUID.randomUUID();
        when(crudUseCase.reactivate(any())).thenThrow(new LotNotFoundException(lotId.toString()));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/lots/" + lotId + "/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LOT_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private static LotResult sampleResult(UUID skuId, String lotNo, LotStatus status, long version) {
        return new LotResult(
                UUID.randomUUID(),
                skuId,
                lotNo,
                MFD,
                EXP,
                null,
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }
}
