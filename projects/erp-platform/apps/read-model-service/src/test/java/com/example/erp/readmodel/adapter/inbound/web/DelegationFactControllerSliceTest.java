package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.erp.readmodel.application.QueryDelegationFactUseCase;
import com.example.erp.readmodel.application.query.DelegationFactPage;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.presentation.security.OrgScope;
import com.example.erp.readmodel.presentation.security.ReadAccessDeniedException;
import com.example.erp.readmodel.presentation.security.ReadAuthorizationGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link DelegationFactController} + the
 * {@link GlobalExceptionHandler} error envelope (TASK-ERP-BE-015). Security
 * filters bypassed; the READ gate is mocked. Asserts the list envelope + filters,
 * the detail 404 (projection miss / out-of-scope), the NON_NULL absent fields
 * (revokedAt absent while ACTIVE), and bad-input validation.
 */
@WebMvcTest(DelegationFactController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DelegationFactControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryDelegationFactUseCase useCase;
    @MockitoBean
    ReadAuthorizationGate readGate;

    @BeforeEach
    void platformScopeByDefault() {
        lenient().when(readGate.orgScope(any())).thenReturn(OrgScope.platform());
    }

    private DelegationFactProjection activeGrant() {
        // REQUEST-scoped grant: both scope + scopeRequestId present.
        return DelegationFactProjection.ofGranted("dgr-1", "emp-a", "emp-d",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-30T00:00:00Z"),
                "vacation", Instant.parse("2026-06-01T00:00:00Z"), "evt-1",
                "REQUEST", "appr-1");
    }

    @Test
    void getOneReturnsEnvelopeWithWarningAndAbsentRevokedAt() throws Exception {
        when(useCase.getOne("dgr-1", null)).thenReturn(activeGrant());

        mockMvc.perform(get("/api/erp/read-model/delegations/dgr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grantId").value("dgr-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.delegatorId").value("emp-a"))
                .andExpect(jsonPath("$.data.delegateId").value("emp-d"))
                .andExpect(jsonPath("$.data.validFrom").exists())
                .andExpect(jsonPath("$.data.validTo").exists())
                .andExpect(jsonPath("$.data.revokedAt").doesNotExist())
                // AC-2: a REQUEST grant exposes scope + scopeRequestId.
                .andExpect(jsonPath("$.data.scope").value("REQUEST"))
                .andExpect(jsonPath("$.data.scopeRequestId").value("appr-1"))
                .andExpect(jsonPath("$.meta.warning").value("Eventually-consistent read-model"))
                .andExpect(jsonPath("$.meta.unresolved").doesNotExist());
    }

    @Test
    void getOneGlobalGrantOmitsScopeRequestId() throws Exception {
        DelegationFactProjection global = DelegationFactProjection.ofGranted("dgr-g", "emp-a",
                "emp-d", Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"), "vacation",
                Instant.parse("2026-06-01T00:00:00Z"), "evt-1", "GLOBAL", null);
        when(useCase.getOne("dgr-g", null)).thenReturn(global);

        mockMvc.perform(get("/api/erp/read-model/delegations/dgr-g"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("GLOBAL"))
                // NON_NULL → scopeRequestId ABSENT for a GLOBAL grant.
                .andExpect(jsonPath("$.data.scopeRequestId").doesNotExist());
    }

    @Test
    void getOneRevokedShowsRevokedAt() throws Exception {
        DelegationFactProjection revoked = DelegationFactProjection.ofRevoked(
                "dgr-2", "emp-a", "emp-d", "back", Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-10T00:00:00Z"), "evt-2");
        when(useCase.getOne("dgr-2", null)).thenReturn(revoked);

        mockMvc.perform(get("/api/erp/read-model/delegations/dgr-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.revokedAt").exists())
                // Out-of-order revoke leaves the window absent; here grant came first
                // so validFrom present — but validFrom/validTo are NON_NULL when absent.
                .andExpect(jsonPath("$.data.validFrom").doesNotExist())
                // NON_NULL → a revoke-only row's scope is ABSENT (unknown).
                .andExpect(jsonPath("$.data.scope").doesNotExist());
    }

    @Test
    void getOneProjectionMissOrOutOfScopeReturns404() throws Exception {
        when(useCase.getOne("ghost", null)).thenThrow(new ReadModelNotFoundException("ghost"));

        mockMvc.perform(get("/api/erp/read-model/delegations/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MASTERDATA_NOT_FOUND"));
    }

    @Test
    void listReturnsPagedEnvelopeWithFilters() throws Exception {
        when(useCase.list(eq("emp-a"), isNull(), eq(DelegationFactStatus.ACTIVE),
                isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new DelegationFactPage(List.of(activeGrant()), 0, 20, 1L));

        mockMvc.perform(get("/api/erp/read-model/delegations"
                        + "?delegatorId=emp-a&status=ACTIVE&page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].grantId").value("dgr-1"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.warning").value("Eventually-consistent read-model"));
    }

    @Test
    void readGateDenialReturns403PermissionDenied() throws Exception {
        doThrow(new ReadAccessDeniedException("no read")).when(readGate).requireRead(any());

        mockMvc.perform(get("/api/erp/read-model/delegations/dgr-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void badStatusReturns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/erp/read-model/delegations?status=BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void badActiveAtReturns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/erp/read-model/delegations?activeAt=not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void badSizeReturns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/erp/read-model/delegations?size=999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
