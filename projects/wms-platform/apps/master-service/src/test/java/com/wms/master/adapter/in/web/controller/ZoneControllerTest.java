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
import com.wms.master.application.command.CreateZoneCommand;
import com.wms.master.application.command.DeactivateZoneCommand;
import com.wms.master.application.command.ReactivateZoneCommand;
import com.wms.master.application.command.UpdateZoneCommand;
import com.wms.master.application.port.in.ZoneCrudUseCase;
import com.wms.master.application.port.in.ZoneQueryUseCase;
import com.wms.master.application.query.ListZonesQuery;
import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
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

@WebMvcTest(controllers = ZoneController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ZoneControllerTest {

    private static final String ACTOR = "user-42";
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");
    private static final UUID WAREHOUSE_ID =
            UUID.fromString("01910000-0000-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZoneCrudUseCase crudUseCase;

    @MockitoBean
    private ZoneQueryUseCase queryUseCase;

    // ---------- POST ----------

    @Test
    void create_returns201_withEtag_andMapsCommand() throws Exception {
        ZoneResult result = sampleResult("Z-A", ZoneType.AMBIENT, WarehouseStatus.ACTIVE, 0L);
        when(crudUseCase.create(any(CreateZoneCommand.class))).thenReturn(result);

        String body = """
                {
                  "zoneCode": "Z-A",
                  "name": "Ambient A",
                  "zoneType": "AMBIENT"
                }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location",
                        "/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + result.id()))
                .andExpect(jsonPath("$.zoneCode").value("Z-A"))
                .andExpect(jsonPath("$.warehouseId").value(WAREHOUSE_ID.toString()))
                .andExpect(jsonPath("$.zoneType").value("AMBIENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        verify(crudUseCase).create(new CreateZoneCommand(
                WAREHOUSE_ID, "Z-A", "Ambient A", ZoneType.AMBIENT, ACTOR));
    }

    @Test
    void create_returns400_whenZoneCodeMissing() throws Exception {
        String body = """
                { "name": "X", "zoneType": "AMBIENT" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.zoneCode").exists());
    }

    @Test
    void create_returns400_whenZoneCodePatternInvalid() throws Exception {
        String body = """
                { "zoneCode": "bad", "name": "X", "zoneType": "AMBIENT" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns400_whenZoneTypeMissing() throws Exception {
        String body = """
                { "zoneCode": "Z-A", "name": "X" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns404_whenParentWarehouseUnknown() throws Exception {
        when(crudUseCase.create(any()))
                .thenThrow(new WarehouseNotFoundException(WAREHOUSE_ID.toString()));

        String body = """
                { "zoneCode": "Z-A", "name": "X", "zoneType": "AMBIENT" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void create_returns409_whenParentWarehouseInactive() throws Exception {
        when(crudUseCase.create(any()))
                .thenThrow(new InvalidStateTransitionException("parent warehouse is not ACTIVE"));

        String body = """
                { "zoneCode": "Z-A", "name": "X", "zoneType": "AMBIENT" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    void create_returns409_whenDuplicateZoneCode() throws Exception {
        when(crudUseCase.create(any()))
                .thenThrow(new ZoneCodeDuplicateException(WAREHOUSE_ID, "Z-A"));

        String body = """
                { "zoneCode": "Z-A", "name": "X", "zoneType": "AMBIENT" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ZONE_CODE_DUPLICATE"));
    }

    // ---------- GET /{zoneId} ----------

    @Test
    void getById_returns200_withEtag() throws Exception {
        ZoneResult result = sampleResult("Z-A", ZoneType.AMBIENT, WarehouseStatus.ACTIVE, 3L);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + result.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.id").value(result.id().toString()))
                .andExpect(jsonPath("$.warehouseId").value(WAREHOUSE_ID.toString()));
    }

    @Test
    void getById_returns404_whenUnknown() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenThrow(new ZoneNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ZONE_NOT_FOUND"));
    }

    @Test
    void getById_returns404_whenZoneBelongsToDifferentWarehouse() throws Exception {
        UUID otherWarehouseId = UUID.randomUUID();
        ZoneResult result = new ZoneResult(
                UUID.randomUUID(), otherWarehouseId, "Z-A", "Name", ZoneType.AMBIENT,
                WarehouseStatus.ACTIVE, 0L, NOW, ACTOR, NOW, ACTOR);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + result.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ZONE_NOT_FOUND"));
    }

    // ---------- GET list ----------

    @Test
    void list_returnsPageEnvelope_matchingContract() throws Exception {
        ZoneResult r = sampleResult("Z-A", ZoneType.AMBIENT, WarehouseStatus.ACTIVE, 0L);
        when(queryUseCase.list(any(ListZonesQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].zoneCode").value("Z-A"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void list_acceptsZoneTypeFilter() throws Exception {
        when(queryUseCase.list(any(ListZonesQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .param("zoneType", "CHILLED"))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns400_whenZoneTypeUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones")
                        .param("zoneType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- PATCH ----------

    @Test
    void update_returns200_andMapsCommand() throws Exception {
        UUID id = UUID.randomUUID();
        ZoneResult result = new ZoneResult(
                id, WAREHOUSE_ID, "Z-A", "Renamed", ZoneType.CHILLED,
                WarehouseStatus.ACTIVE, 4L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.update(any(UpdateZoneCommand.class))).thenReturn(result);

        String body = """
                { "name": "Renamed", "zoneType": "CHILLED", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id)
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.zoneType").value("CHILLED"));

        verify(crudUseCase).update(new UpdateZoneCommand(
                id, "Renamed", ZoneType.CHILLED, null, null, 3L, ACTOR));
    }

    @Test
    void update_returns422_whenImmutableFieldAttempted() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any()))
                .thenThrow(new ImmutableFieldException("zoneCode"));

        String body = """
                { "zoneCode": "Z-B", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns400_whenVersionMissing() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                { "name": "Renamed" }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.version").exists());
    }

    @Test
    void update_returns409_onOptimisticLockConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(
                new ConcurrencyConflictException("Zone", id.toString(), 3L, 4L));

        String body = """
                { "name": "Renamed", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // ---------- POST .../deactivate ----------

    @Test
    void deactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        ZoneResult result = new ZoneResult(
                id, WAREHOUSE_ID, "Z-A", "X", ZoneType.AMBIENT,
                WarehouseStatus.INACTIVE, 4L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.deactivate(any(DeactivateZoneCommand.class))).thenReturn(result);

        String body = """
                { "version": 3, "reason": "closing" }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id + "/deactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(crudUseCase).deactivate(new DeactivateZoneCommand(id, "closing", 3L, ACTOR));
    }

    @Test
    void deactivate_returns409_onInvalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.deactivate(any()))
                .thenThrow(new InvalidStateTransitionException("INACTIVE", "deactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    // ---------- POST .../reactivate ----------

    @Test
    void reactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        ZoneResult result = new ZoneResult(
                id, WAREHOUSE_ID, "Z-A", "X", ZoneType.AMBIENT,
                WarehouseStatus.ACTIVE, 5L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.reactivate(any(ReactivateZoneCommand.class))).thenReturn(result);

        String body = """
                { "version": 4 }
                """;

        mockMvc.perform(post("/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + id + "/reactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(crudUseCase).reactivate(new ReactivateZoneCommand(id, 4L, ACTOR));
    }

    // ---------- helpers ----------

    private static ZoneResult sampleResult(String code, ZoneType zoneType,
                                           WarehouseStatus status, long version) {
        return new ZoneResult(
                UUID.randomUUID(),
                WAREHOUSE_ID,
                code,
                "Ambient A",
                zoneType,
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }
}
