package com.wms.master.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.wms.master.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.master.application.command.CreateSkuCommand;
import com.wms.master.application.command.DeactivateSkuCommand;
import com.wms.master.application.command.ReactivateSkuCommand;
import com.wms.master.application.command.UpdateSkuCommand;
import com.wms.master.application.port.in.SkuCrudUseCase;
import com.wms.master.application.port.in.SkuQueryUseCase;
import com.wms.master.application.query.ListSkusQuery;
import com.wms.master.application.result.SkuResult;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.SkuCodeDuplicateException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
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

@WebMvcTest(controllers = SkuController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SkuControllerTest {

    private static final String ACTOR = "user-42";
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkuCrudUseCase crudUseCase;

    @MockitoBean
    private SkuQueryUseCase queryUseCase;

    // ---------- POST /skus ----------

    @Test
    void create_returns201_withEtag_andUppercasesSkuCode() throws Exception {
        // lowercase input in the request body → controller normalises → UPPERCASE in response
        SkuResult result = sampleResult("SKU-001", WarehouseStatus.ACTIVE, 0L);
        when(crudUseCase.create(any(CreateSkuCommand.class))).thenReturn(result);

        String body = """
                {
                  "skuCode": "sku-001",
                  "name": "Gala Apple 1kg",
                  "baseUom": "EA",
                  "trackingType": "LOT"
                }
                """;

        mockMvc.perform(post("/api/v1/master/skus")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location", "/api/v1/master/skus/" + result.id()))
                .andExpect(jsonPath("$.skuCode").value("SKU-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        // Verify the command carries the UPPERCASED code
        verify(crudUseCase).create(new CreateSkuCommand(
                "SKU-001", "Gala Apple 1kg", null, null,
                BaseUom.EA, TrackingType.LOT,
                null, null, null, null, ACTOR));
    }

    @Test
    void create_returns400_whenSkuCodeMissing() throws Exception {
        String body = """
                { "name": "Apple", "baseUom": "EA", "trackingType": "NONE" }
                """;

        mockMvc.perform(post("/api/v1/master/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.skuCode").exists());
    }

    @Test
    void create_returns400_whenBaseUomMissing() throws Exception {
        String body = """
                { "skuCode": "SKU-X01", "name": "Apple", "trackingType": "NONE" }
                """;

        mockMvc.perform(post("/api/v1/master/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.baseUom").exists());
    }

    @Test
    void create_returns400_whenTrackingTypeMissing() throws Exception {
        String body = """
                { "skuCode": "SKU-X02", "name": "Apple", "baseUom": "EA" }
                """;

        mockMvc.perform(post("/api/v1/master/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.trackingType").exists());
    }

    @Test
    void create_returns400_whenMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/master/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Malformed request body"));
    }

    @Test
    void create_returns409_whenSkuCodeDuplicate() throws Exception {
        when(crudUseCase.create(any())).thenThrow(new SkuCodeDuplicateException("SKU-DUP"));

        String body = """
                { "skuCode": "SKU-DUP", "name": "Apple", "baseUom": "EA", "trackingType": "NONE" }
                """;

        mockMvc.perform(post("/api/v1/master/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SKU_CODE_DUPLICATE"));
    }

    // ---------- GET /skus/{id} ----------

    @Test
    void getById_returns200_withEtag() throws Exception {
        SkuResult result = sampleResult("SKU-002", WarehouseStatus.ACTIVE, 3L);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/skus/" + result.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.id").value(result.id().toString()))
                .andExpect(jsonPath("$.skuCode").value("SKU-002"));
    }

    @Test
    void getById_returns404_whenUnknown() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenThrow(new SkuNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/master/skus/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SKU_NOT_FOUND"));
    }

    @Test
    void getById_returns400_whenIdNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/master/skus/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- GET /skus/by-code/{skuCode} ----------

    @Test
    void getByCode_returns200_withExactUppercaseInput() throws Exception {
        SkuResult result = sampleResult("SKU-003", WarehouseStatus.ACTIVE, 1L);
        when(queryUseCase.findBySkuCode("SKU-003")).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/skus/by-code/SKU-003"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.skuCode").value("SKU-003"));
    }

    @Test
    void getByCode_returns200_withMixedCaseInput() throws Exception {
        // The controller forwards raw input; the service layer is responsible for uppercasing.
        // The mock stubs on "sku-003" to verify the forwarding is exact (no double-normalisation).
        SkuResult result = sampleResult("SKU-003", WarehouseStatus.ACTIVE, 1L);
        when(queryUseCase.findBySkuCode("sku-003")).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/skus/by-code/sku-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuCode").value("SKU-003"));
    }

    @Test
    void getByCode_returns404_whenAbsent() throws Exception {
        when(queryUseCase.findBySkuCode("GHOST")).thenThrow(new SkuNotFoundException("GHOST"));

        mockMvc.perform(get("/api/v1/master/skus/by-code/GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SKU_NOT_FOUND"));
    }

    // ---------- GET /skus/by-barcode/{barcode} ----------

    @Test
    void getByBarcode_returns200_onExactMatch() throws Exception {
        SkuResult result = sampleResult("SKU-004", WarehouseStatus.ACTIVE, 2L);
        when(queryUseCase.findByBarcode("8801234567890")).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/skus/by-barcode/8801234567890"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""))
                .andExpect(jsonPath("$.skuCode").value("SKU-004"));
    }

    @Test
    void getByBarcode_returns404_whenAbsent() throws Exception {
        when(queryUseCase.findByBarcode("0000000000000"))
                .thenThrow(new SkuNotFoundException("0000000000000"));

        mockMvc.perform(get("/api/v1/master/skus/by-barcode/0000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SKU_NOT_FOUND"));
    }

    // ---------- PATCH /skus/{id} ----------

    @Test
    void update_returns200_andMapsCommand() throws Exception {
        UUID id = UUID.randomUUID();
        SkuResult result = new SkuResult(id, "SKU-005", "Renamed Apple", null, null,
                BaseUom.EA, TrackingType.NONE, null, null, null, null,
                WarehouseStatus.ACTIVE, 4L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.update(any(UpdateSkuCommand.class))).thenReturn(result);

        String body = """
                { "name": "Renamed Apple", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/skus/" + id)
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""))
                .andExpect(jsonPath("$.name").value("Renamed Apple"));

        verify(crudUseCase).update(new UpdateSkuCommand(
                id, "Renamed Apple", null, null, null, null, null, null,
                null, null, null, 3L, ACTOR));
    }

    @Test
    void update_returns422_whenSkuCodeAttempted() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("skuCode"));

        String body = """
                { "skuCode": "NEW-CODE", "version": 0 }
                """;

        mockMvc.perform(patch("/api/v1/master/skus/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns422_whenBaseUomAttempted() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("baseUom"));

        String body = """
                { "baseUom": "BOX", "version": 0 }
                """;

        mockMvc.perform(patch("/api/v1/master/skus/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns422_whenTrackingTypeAttempted() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("trackingType"));

        String body = """
                { "trackingType": "LOT", "version": 0 }
                """;

        mockMvc.perform(patch("/api/v1/master/skus/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"))
                // BE-008: every error envelope carries an ISO 8601 UTC timestamp
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(
                                "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")));
    }

    @Test
    void update_returns409_onOptimisticLockConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(
                new ConcurrencyConflictException("Sku", id.toString(), 3L, 4L));

        String body = """
                { "name": "Renamed", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/skus/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void update_returns400_whenVersionMissing() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                { "name": "Renamed" }
                """;

        mockMvc.perform(patch("/api/v1/master/skus/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.version").exists());
    }

    // ---------- POST /skus/{id}/deactivate ----------

    @Test
    void deactivate_returns200_andEtagUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        SkuResult result = sampleResultWithId(id, "SKU-006", WarehouseStatus.INACTIVE, 4L);
        when(crudUseCase.deactivate(any(DeactivateSkuCommand.class))).thenReturn(result);

        String body = """
                { "version": 3, "reason": "discontinued" }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + id + "/deactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(crudUseCase).deactivate(new DeactivateSkuCommand(id, "discontinued", 3L, ACTOR));
    }

    @Test
    void deactivate_returns422_onInvalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.deactivate(any())).thenThrow(
                new InvalidStateTransitionException("INACTIVE", "deactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"))
                // BE-008: timestamp is always present in the error envelope
                .andExpect(jsonPath("$.error.timestamp").isString())
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(
                                "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")));
    }

    // ---------- POST /skus/{id}/reactivate ----------

    @Test
    void reactivate_returns200_andEtagUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        SkuResult result = sampleResultWithId(id, "SKU-007", WarehouseStatus.ACTIVE, 5L);
        when(crudUseCase.reactivate(any(ReactivateSkuCommand.class))).thenReturn(result);

        String body = """
                { "version": 4 }
                """;

        mockMvc.perform(post("/api/v1/master/skus/" + id + "/reactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v5\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(crudUseCase).reactivate(new ReactivateSkuCommand(id, 4L, ACTOR));
    }

    // ---------- GET /skus (list) ----------

    @Test
    void list_returnsPageEnvelope_matchingContract() throws Exception {
        SkuResult r = sampleResult("SKU-008", WarehouseStatus.ACTIVE, 0L);
        when(queryUseCase.list(any(ListSkusQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].skuCode").value("SKU-008"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void list_returns400_whenStatusUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/skus").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returns400_whenTrackingTypeUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/skus").param("trackingType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- helpers ----------

    private static SkuResult sampleResult(String code, WarehouseStatus status, long version) {
        return new SkuResult(
                UUID.randomUUID(),
                code,
                "Sample SKU name",
                null,
                null,
                BaseUom.EA,
                TrackingType.NONE,
                null,
                null,
                null,
                null,
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }

    private static SkuResult sampleResultWithId(UUID id, String code, WarehouseStatus status, long version) {
        return new SkuResult(
                id,
                code,
                "Sample SKU name",
                null,
                null,
                BaseUom.EA,
                TrackingType.NONE,
                null,
                null,
                null,
                null,
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }
}
