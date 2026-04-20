package com.wms.master.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.master.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.master.application.command.CreateLocationCommand;
import com.wms.master.application.port.in.LocationCrudUseCase;
import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LocationCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LocationCreateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LocationCreateControllerTest {

    private static final String ACTOR = "user-42";
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");
    private static final UUID WAREHOUSE_ID = UUID.fromString("01910000-0000-7000-8000-000000000001");
    private static final UUID ZONE_ID = UUID.fromString("01910000-0000-7000-8000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocationCrudUseCase crudUseCase;

    @Test
    void create_returns201_withEtag_andMapsCommand() throws Exception {
        LocationResult result = sampleResult(0L);
        when(crudUseCase.create(any(CreateLocationCommand.class))).thenReturn(result);

        String body = """
                {
                  "locationCode": "WH01-A-01-02-03",
                  "aisle": "01",
                  "rack": "02",
                  "level": "03",
                  "locationType": "STORAGE",
                  "capacityUnits": 500
                }
                """;

        mockMvc.perform(post(url())
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location",
                        "/api/v1/master/locations/" + result.id()))
                .andExpect(jsonPath("$.locationCode").value("WH01-A-01-02-03"))
                .andExpect(jsonPath("$.warehouseId").value(WAREHOUSE_ID.toString()))
                .andExpect(jsonPath("$.zoneId").value(ZONE_ID.toString()))
                .andExpect(jsonPath("$.locationType").value("STORAGE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        verify(crudUseCase).create(new CreateLocationCommand(
                WAREHOUSE_ID, ZONE_ID, "WH01-A-01-02-03",
                "01", "02", "03", null,
                LocationType.STORAGE, 500, ACTOR));
    }

    @Test
    void create_returns400_whenLocationCodeMissing() throws Exception {
        String body = """
                { "locationType": "STORAGE" }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.locationCode").exists());
    }

    @Test
    void create_returns400_whenLocationCodePatternInvalid() throws Exception {
        String body = """
                { "locationCode": "not-valid", "locationType": "STORAGE" }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns400_whenLocationTypeMissing() throws Exception {
        String body = """
                { "locationCode": "WH01-A-01-02-03" }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns400_whenCapacityUnitsTooSmall() throws Exception {
        String body = """
                { "locationCode": "WH01-A-01-02-03", "locationType": "STORAGE", "capacityUnits": 0 }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_returns404_whenParentZoneUnknown() throws Exception {
        when(crudUseCase.create(any()))
                .thenThrow(new ZoneNotFoundException(ZONE_ID.toString()));

        String body = """
                { "locationCode": "WH01-A-01-02-03", "locationType": "STORAGE" }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ZONE_NOT_FOUND"));
    }

    @Test
    void create_returns422_whenParentZoneInactive() throws Exception {
        when(crudUseCase.create(any()))
                .thenThrow(new InvalidStateTransitionException("parent zone is not ACTIVE"));

        String body = """
                { "locationCode": "WH01-A-01-02-03", "locationType": "STORAGE" }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    void create_returns409_whenDuplicateLocationCode() throws Exception {
        when(crudUseCase.create(any()))
                .thenThrow(new LocationCodeDuplicateException("WH01-A-01-02-03"));

        String body = """
                { "locationCode": "WH01-A-01-02-03", "locationType": "STORAGE" }
                """;

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LOCATION_CODE_DUPLICATE"));
    }

    private static String url() {
        return "/api/v1/master/warehouses/" + WAREHOUSE_ID + "/zones/" + ZONE_ID + "/locations";
    }

    private static LocationResult sampleResult(long version) {
        return new LocationResult(
                UUID.randomUUID(),
                WAREHOUSE_ID,
                ZONE_ID,
                "WH01-A-01-02-03",
                "01", "02", "03", null,
                LocationType.STORAGE,
                500,
                WarehouseStatus.ACTIVE,
                version,
                NOW, ACTOR, NOW, ACTOR);
    }
}
