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
import com.wms.master.application.command.DeactivateLocationCommand;
import com.wms.master.application.command.ReactivateLocationCommand;
import com.wms.master.application.command.UpdateLocationCommand;
import com.wms.master.application.port.in.LocationCrudUseCase;
import com.wms.master.application.port.in.LocationQueryUseCase;
import com.wms.master.application.query.ListLocationsQuery;
import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LocationNotFoundException;
import com.wms.master.domain.model.LocationType;
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

@WebMvcTest(controllers = LocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LocationControllerTest {

    private static final String ACTOR = "user-42";
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");
    private static final UUID WAREHOUSE_ID = UUID.fromString("01910000-0000-7000-8000-000000000001");
    private static final UUID ZONE_ID = UUID.fromString("01910000-0000-7000-8000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationCrudUseCase crudUseCase;

    @MockitoBean
    private LocationQueryUseCase queryUseCase;

    // ---------- GET /{id} ----------

    @Test
    void getById_returns200_withEtag() throws Exception {
        LocationResult result = sampleResult(3L, WarehouseStatus.ACTIVE);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/locations/" + result.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.id").value(result.id().toString()))
                .andExpect(jsonPath("$.locationCode").value("WH01-A-01-02-03"));
    }

    @Test
    void getById_returns404_whenUnknown() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenThrow(new LocationNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/master/locations/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LOCATION_NOT_FOUND"));
    }

    // ---------- GET list ----------

    @Test
    void list_returnsPageEnvelope_matchingContract() throws Exception {
        LocationResult r = sampleResult(0L, WarehouseStatus.ACTIVE);
        when(queryUseCase.list(any(ListLocationsQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].locationCode").value("WH01-A-01-02-03"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void list_acceptsAllFilters() throws Exception {
        when(queryUseCase.list(any(ListLocationsQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/master/locations")
                        .param("warehouseId", WAREHOUSE_ID.toString())
                        .param("zoneId", ZONE_ID.toString())
                        .param("locationType", "STORAGE")
                        .param("code", "WH01-A-01-02-03")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns400_whenLocationTypeUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/locations")
                        .param("locationType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- PATCH ----------

    @Test
    void update_returns200_andMapsCommand() throws Exception {
        UUID id = UUID.randomUUID();
        LocationResult result = sampleResultWithId(id, 4L, WarehouseStatus.ACTIVE);
        when(crudUseCase.update(any(UpdateLocationCommand.class))).thenReturn(result);

        String body = """
                { "locationType": "DAMAGED", "capacityUnits": 200, "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/locations/" + id)
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""));

        verify(crudUseCase).update(new UpdateLocationCommand(
                id, LocationType.DAMAGED, 200,
                null, null, null, null,
                null, null, null, 3L, ACTOR));
    }

    @Test
    void update_returns422_whenImmutableFieldAttempted() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any()))
                .thenThrow(new ImmutableFieldException("locationCode"));

        String body = """
                { "locationCode": "WH01-B-09-09-09", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/locations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void update_returns400_whenVersionMissing() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                { "locationType": "DAMAGED" }
                """;

        mockMvc.perform(patch("/api/v1/master/locations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.version").exists());
    }

    @Test
    void update_returns409_onOptimisticLockConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(
                new ConcurrencyConflictException("Location", id.toString(), 3L, 4L));

        String body = """
                { "locationType": "DAMAGED", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/locations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    // ---------- POST .../deactivate ----------

    @Test
    void deactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LocationResult result = sampleResultWithId(id, 4L, WarehouseStatus.INACTIVE);
        when(crudUseCase.deactivate(any(DeactivateLocationCommand.class))).thenReturn(result);

        String body = """
                { "version": 3, "reason": "closing" }
                """;

        mockMvc.perform(post("/api/v1/master/locations/" + id + "/deactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(crudUseCase).deactivate(new DeactivateLocationCommand(id, "closing", 3L, ACTOR));
    }

    @Test
    void deactivate_returns422_onInvalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.deactivate(any()))
                .thenThrow(new InvalidStateTransitionException("INACTIVE", "deactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/locations/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    // ---------- POST .../reactivate ----------

    @Test
    void reactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LocationResult result = sampleResultWithId(id, 5L, WarehouseStatus.ACTIVE);
        when(crudUseCase.reactivate(any(ReactivateLocationCommand.class))).thenReturn(result);

        String body = """
                { "version": 4 }
                """;

        mockMvc.perform(post("/api/v1/master/locations/" + id + "/reactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(crudUseCase).reactivate(new ReactivateLocationCommand(id, 4L, ACTOR));
    }

    // ---------- helpers ----------

    private static LocationResult sampleResult(long version, WarehouseStatus status) {
        return sampleResultWithId(UUID.randomUUID(), version, status);
    }

    private static LocationResult sampleResultWithId(UUID id, long version, WarehouseStatus status) {
        return new LocationResult(
                id,
                WAREHOUSE_ID,
                ZONE_ID,
                "WH01-A-01-02-03",
                "01", "02", "03", null,
                LocationType.STORAGE,
                500,
                status,
                version,
                NOW, ACTOR, NOW, ACTOR);
    }
}
