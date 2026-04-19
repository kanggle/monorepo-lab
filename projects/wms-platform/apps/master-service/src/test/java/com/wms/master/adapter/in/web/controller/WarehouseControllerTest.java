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
import com.wms.master.application.command.CreateWarehouseCommand;
import com.wms.master.application.command.DeactivateWarehouseCommand;
import com.wms.master.application.command.ReactivateWarehouseCommand;
import com.wms.master.application.command.UpdateWarehouseCommand;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
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

@WebMvcTest(controllers = WarehouseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WarehouseControllerTest {

    private static final String ACTOR = "user-42";
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseCrudUseCase crudUseCase;

    @MockitoBean
    private WarehouseQueryUseCase queryUseCase;

    // ---------- POST /warehouses ----------

    @Test
    void create_returns201_withEtag_andMapsCommand() throws Exception {
        WarehouseResult result = sampleResult("WH01", WarehouseStatus.ACTIVE, 0L);
        when(crudUseCase.create(any(CreateWarehouseCommand.class))).thenReturn(result);

        String body = """
                {
                  "warehouseCode": "WH01",
                  "name": "Seoul Main",
                  "address": "Seoul",
                  "timezone": "Asia/Seoul"
                }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location", "/api/v1/master/warehouses/" + result.id()))
                .andExpect(jsonPath("$.warehouseCode").value("WH01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        verify(crudUseCase).create(new CreateWarehouseCommand("WH01", "Seoul Main", "Seoul", "Asia/Seoul", ACTOR));
    }

    @Test
    void create_returns400_whenWarehouseCodeMissing() throws Exception {
        String body = """
                { "name": "X", "timezone": "Asia/Seoul" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.warehouseCode").exists());
    }

    @Test
    void create_returns400_whenWarehouseCodePatternInvalid() throws Exception {
        String body = """
                { "warehouseCode": "INVALID", "name": "X", "timezone": "Asia/Seoul" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns409_whenDuplicateCode() throws Exception {
        when(crudUseCase.create(any())).thenThrow(new WarehouseCodeDuplicateException("WH01"));

        String body = """
                { "warehouseCode": "WH01", "name": "X", "timezone": "Asia/Seoul" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WAREHOUSE_CODE_DUPLICATE"));
    }

    @Test
    void create_returns400_whenMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/master/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Malformed request body"));
    }

    // ---------- GET /warehouses/{id} ----------

    @Test
    void getById_returns200_withEtag() throws Exception {
        WarehouseResult result = sampleResult("WH01", WarehouseStatus.ACTIVE, 3L);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/warehouses/" + result.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.id").value(result.id().toString()));
    }

    @Test
    void getById_returns404_whenUnknown() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenThrow(new WarehouseNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/master/warehouses/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void getById_returns400_whenIdNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/master/warehouses/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- GET /warehouses ----------

    @Test
    void list_returnsPageEnvelope_matchingContract() throws Exception {
        WarehouseResult r = sampleResult("WH01", WarehouseStatus.ACTIVE, 0L);
        when(queryUseCase.list(any(ListWarehousesQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].warehouseCode").value("WH01"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void list_returns400_whenStatusUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/warehouses").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- PATCH /warehouses/{id} ----------

    @Test
    void update_returns200_andMapsCommand() throws Exception {
        UUID id = UUID.randomUUID();
        WarehouseResult result = new WarehouseResult(id, "WH01", "Renamed", "addr", "Asia/Seoul",
                WarehouseStatus.ACTIVE, 4L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.update(any(UpdateWarehouseCommand.class))).thenReturn(result);

        String body = """
                { "name": "Renamed", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + id)
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""))
                .andExpect(jsonPath("$.name").value("Renamed"));

        verify(crudUseCase).update(new UpdateWarehouseCommand(id, "Renamed", null, null, 3L, ACTOR));
    }

    @Test
    void update_returns400_whenVersionMissing() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                { "name": "Renamed" }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.version").exists());
    }

    @Test
    void update_returns409_onOptimisticLockConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(
                new ConcurrencyConflictException("Warehouse", id.toString(), 3L, 4L));

        String body = """
                { "name": "Renamed", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // ---------- POST /warehouses/{id}/deactivate ----------

    @Test
    void deactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        WarehouseResult result = new WarehouseResult(id, "WH01", "X", null, "Asia/Seoul",
                WarehouseStatus.INACTIVE, 4L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.deactivate(any(DeactivateWarehouseCommand.class))).thenReturn(result);

        String body = """
                { "version": 3, "reason": "closing" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + id + "/deactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(crudUseCase).deactivate(new DeactivateWarehouseCommand(id, "closing", 3L, ACTOR));
    }

    @Test
    void deactivate_returns409_onInvalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.deactivate(any())).thenThrow(
                new InvalidStateTransitionException("INACTIVE", "deactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    // ---------- POST /warehouses/{id}/reactivate ----------

    @Test
    void reactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        WarehouseResult result = new WarehouseResult(id, "WH01", "X", null, "Asia/Seoul",
                WarehouseStatus.ACTIVE, 5L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.reactivate(any(ReactivateWarehouseCommand.class))).thenReturn(result);

        String body = """
                { "version": 4 }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + id + "/reactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(crudUseCase).reactivate(new ReactivateWarehouseCommand(id, 4L, ACTOR));
    }

    // ---------- helpers ----------

    private static WarehouseResult sampleResult(String code, WarehouseStatus status, long version) {
        return new WarehouseResult(
                UUID.randomUUID(),
                code,
                "Seoul Main",
                "Seoul",
                "Asia/Seoul",
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }

}
