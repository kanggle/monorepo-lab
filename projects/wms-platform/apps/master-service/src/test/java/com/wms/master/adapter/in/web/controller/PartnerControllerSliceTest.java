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
import com.wms.master.application.command.CreatePartnerCommand;
import com.wms.master.application.command.DeactivatePartnerCommand;
import com.wms.master.application.command.ReactivatePartnerCommand;
import com.wms.master.application.command.UpdatePartnerCommand;
import com.wms.master.application.port.in.PartnerCrudUseCase;
import com.wms.master.application.port.in.PartnerQueryUseCase;
import com.wms.master.application.query.ListPartnersQuery;
import com.wms.master.application.result.PartnerResult;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.exception.PartnerNotFoundException;
import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.testsupport.TestConstants;
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

@WebMvcTest(controllers = PartnerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PartnerControllerSliceTest {

    private static final String ACTOR = "user-42";
    private static final Instant NOW = Instant.parse("2026-04-19T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PartnerCrudUseCase crudUseCase;

    @MockitoBean
    private PartnerQueryUseCase queryUseCase;

    // ---------- POST /partners ----------

    @Test
    void create_returns201_withEtag() throws Exception {
        PartnerResult result = sampleResult("SUP-001", WarehouseStatus.ACTIVE, 0L);
        when(crudUseCase.create(any(CreatePartnerCommand.class))).thenReturn(result);

        String body = """
                {
                  "partnerCode": "SUP-001",
                  "name": "ACME Supplier",
                  "partnerType": "SUPPLIER"
                }
                """;

        mockMvc.perform(post("/api/v1/master/partners")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location", "/api/v1/master/partners/" + result.id()))
                .andExpect(jsonPath("$.partnerCode").value("SUP-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        verify(crudUseCase).create(new CreatePartnerCommand(
                "SUP-001", "ACME Supplier", PartnerType.SUPPLIER,
                null, null, null, null, null, ACTOR));
    }

    @Test
    void create_returns400_whenPartnerCodeMissing() throws Exception {
        String body = """
                { "name": "ACME", "partnerType": "SUPPLIER" }
                """;

        mockMvc.perform(post("/api/v1/master/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.partnerCode").exists());
    }

    @Test
    void create_returns400_whenPartnerTypeMissing() throws Exception {
        String body = """
                { "partnerCode": "SUP-1", "name": "ACME" }
                """;

        mockMvc.perform(post("/api/v1/master/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.partnerType").exists());
    }

    @Test
    void create_returns400_whenContactEmailMalformed() throws Exception {
        String body = """
                {
                  "partnerCode": "SUP-1",
                  "name": "ACME",
                  "partnerType": "SUPPLIER",
                  "contactEmail": "notanemail"
                }
                """;

        mockMvc.perform(post("/api/v1/master/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.contactEmail").exists());
    }

    @Test
    void create_returns400_whenMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/master/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Malformed request body"));
    }

    @Test
    void create_returns409_whenPartnerCodeDuplicate() throws Exception {
        when(crudUseCase.create(any())).thenThrow(new PartnerCodeDuplicateException("SUP-DUP"));

        String body = """
                { "partnerCode": "SUP-DUP", "name": "ACME", "partnerType": "SUPPLIER" }
                """;

        mockMvc.perform(post("/api/v1/master/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PARTNER_CODE_DUPLICATE"));
    }

    // ---------- GET /partners/{id} ----------

    @Test
    void getById_returns200_withEtag() throws Exception {
        PartnerResult result = sampleResult("SUP-002", WarehouseStatus.ACTIVE, 3L);
        when(queryUseCase.findById(result.id())).thenReturn(result);

        mockMvc.perform(get("/api/v1/master/partners/" + result.id()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.id").value(result.id().toString()))
                .andExpect(jsonPath("$.partnerCode").value("SUP-002"));
    }

    @Test
    void getById_returns404_whenUnknown() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryUseCase.findById(id)).thenThrow(new PartnerNotFoundException(id.toString()));

        mockMvc.perform(get("/api/v1/master/partners/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PARTNER_NOT_FOUND"));
    }

    @Test
    void getById_returns400_whenIdNotUuid() throws Exception {
        mockMvc.perform(get("/api/v1/master/partners/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- PATCH /partners/{id} ----------

    @Test
    void update_returns200_andMapsCommand() throws Exception {
        UUID id = UUID.randomUUID();
        PartnerResult result = new PartnerResult(id, "SUP-005", "Renamed",
                PartnerType.BOTH, null, null, null, null, null,
                WarehouseStatus.ACTIVE, 4L, NOW, ACTOR, NOW, ACTOR);
        when(crudUseCase.update(any(UpdatePartnerCommand.class))).thenReturn(result);

        String body = """
                { "name": "Renamed", "partnerType": "BOTH", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/partners/" + id)
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.partnerType").value("BOTH"));

        verify(crudUseCase).update(new UpdatePartnerCommand(
                id, "Renamed", PartnerType.BOTH, null, null, null, null, null,
                null, 3L, ACTOR));
    }

    @Test
    void update_returns422_whenPartnerCodeAttempted() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(new ImmutableFieldException("partnerCode"));

        String body = """
                { "partnerCode": "NEW-CODE", "version": 0 }
                """;

        mockMvc.perform(patch("/api/v1/master/partners/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMMUTABLE_FIELD"))
                .andExpect(jsonPath("$.error.timestamp",
                        org.hamcrest.Matchers.matchesRegex(TestConstants.ISO_TIMESTAMP_REGEX)));
    }

    @Test
    void update_returns409_onOptimisticLockConflict() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.update(any())).thenThrow(
                new ConcurrencyConflictException("Partner", id.toString(), 3L, 4L));

        String body = """
                { "name": "Renamed", "version": 3 }
                """;

        mockMvc.perform(patch("/api/v1/master/partners/" + id)
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

        mockMvc.perform(patch("/api/v1/master/partners/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.version").exists());
    }

    // ---------- POST /partners/{id}/deactivate ----------

    @Test
    void deactivate_returns200_andEtagUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        PartnerResult result = sampleResultWithId(id, "SUP-006", WarehouseStatus.INACTIVE, 4L);
        when(crudUseCase.deactivate(any(DeactivatePartnerCommand.class))).thenReturn(result);

        String body = """
                { "version": 3, "reason": "discontinued" }
                """;

        mockMvc.perform(post("/api/v1/master/partners/" + id + "/deactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v4\""))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(crudUseCase).deactivate(new DeactivatePartnerCommand(id, "discontinued", 3L, ACTOR));
    }

    @Test
    void deactivate_returns422_onInvalidTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudUseCase.deactivate(any())).thenThrow(
                new InvalidStateTransitionException("INACTIVE", "deactivate"));

        String body = """
                { "version": 0 }
                """;

        mockMvc.perform(post("/api/v1/master/partners/" + id + "/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATE_TRANSITION_INVALID"));
    }

    // ---------- POST /partners/{id}/reactivate ----------

    @Test
    void reactivate_returns200_andEtagUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        PartnerResult result = sampleResultWithId(id, "SUP-007", WarehouseStatus.ACTIVE, 5L);
        when(crudUseCase.reactivate(any(ReactivatePartnerCommand.class))).thenReturn(result);

        String body = """
                { "version": 4 }
                """;

        mockMvc.perform(post("/api/v1/master/partners/" + id + "/reactivate")
                        .header("X-Actor-Id", ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v5\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(crudUseCase).reactivate(new ReactivatePartnerCommand(id, 4L, ACTOR));
    }

    // ---------- GET /partners (list) ----------

    @Test
    void list_returnsPageEnvelope_matchingContract() throws Exception {
        PartnerResult r = sampleResult("SUP-008", WarehouseStatus.ACTIVE, 0L);
        when(queryUseCase.list(any(ListPartnersQuery.class)))
                .thenReturn(new PageResult<>(List.of(r), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/master/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].partnerCode").value("SUP-008"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("updatedAt,desc"));
    }

    @Test
    void list_returns400_whenStatusUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/partners").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returns400_whenPartnerTypeUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/master/partners").param("partnerType", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- helpers ----------

    private static PartnerResult sampleResult(String code, WarehouseStatus status, long version) {
        return new PartnerResult(
                UUID.randomUUID(),
                code,
                "Sample Partner",
                PartnerType.SUPPLIER,
                null, null, null, null, null,
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }

    private static PartnerResult sampleResultWithId(UUID id, String code, WarehouseStatus status, long version) {
        return new PartnerResult(
                id,
                code,
                "Sample Partner",
                PartnerType.SUPPLIER,
                null, null, null, null, null,
                status,
                version,
                NOW,
                ACTOR,
                NOW,
                ACTOR);
    }
}
