package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.adapter.outbound.http.MissingTenantException;
import com.kanggle.platformconsole.bff.adapter.outbound.http.OperatorCredentialContext;
import com.kanggle.platformconsole.bff.application.usecase.OperatorOverviewCompositionUseCase;
import com.kanggle.platformconsole.bff.application.usecase.OperatorOverviewCompositionUseCase.CompositionLeg;
import com.kanggle.platformconsole.bff.application.usecase.UpstreamUnauthorizedException;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Slice test for {@link OperatorOverviewController}.
 *
 * <p>{@code @WebMvcTest} with security filters disabled
 * ({@code addFilters = false}) — mirrors the {@code ActuatorHealthSliceTest}
 * precedent (security is exercised by IT). Mocks
 * {@link OperatorOverviewCompositionUseCase} and {@link OperatorCredentialContext}
 * directly so the slice exercises the controller → handler envelope wiring.
 *
 * <p>Imports {@link GlobalExceptionHandler} so exception mappings are loaded
 * by the slice context.
 *
 * <p>Coverage:
 * <ul>
 *   <li>200 happy envelope shape (§ 2.4.9.1 schema).</li>
 *   <li>400 NO_ACTIVE_TENANT via MissingTenantException.</li>
 *   <li>401 TOKEN_INVALID via MissingCredentialException.</li>
 *   <li>401 TOKEN_INVALID via UpstreamUnauthorizedException.</li>
 *   <li>500 INTERNAL_ERROR via generic RuntimeException
 *       (GlobalExceptionHandler scope=inbound.web catches).</li>
 * </ul>
 */
@WebMvcTest(controllers = OperatorOverviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OperatorOverviewSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OperatorOverviewCompositionUseCase compositionUseCase;

    @MockitoBean
    OperatorCredentialContext credentialContext;

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy_200_envelope: 5-card all-ok envelope shape matches § 2.4.9.1")
    void happy_200_envelope() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(credentialContext.getTenantId()).thenReturn("wms");
        when(compositionUseCase.compose(anyString(), nullable(String.class))).thenReturn(List.of(
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), Map.of("page", Map.of("totalElements", 12))),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), Map.of("snapshotTotal", 34)),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), Map.of("nodeCount", 5)),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE), Map.of("balance", 0)),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), Map.of("meta", Map.of("totalElements", 9)))
        ));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-200 body:\n%s", body).isEqualTo(200);
        // Envelope shape — must contain asOf + 5 cards in fixed order.
        assertThat(body).as("body shape:\n%s", body).contains("\"asOf\"");
        assertThat(body).as("body shape:\n%s", body).contains("\"cards\"");
        assertThat(body).as("body shape:\n%s", body)
                .contains("\"domain\":\"gap\"")
                .contains("\"domain\":\"wms\"")
                .contains("\"domain\":\"scm\"")
                .contains("\"domain\":\"finance\"")
                .contains("\"domain\":\"erp\"");
        // All cards ok status; no degrade reason on ok cards (NON_NULL elides).
        assertThat(body).as("body shape:\n%s", body).contains("\"status\":\"ok\"");
        // Fixed order: gap before wms before scm before finance before erp.
        int gapIdx = body.indexOf("\"domain\":\"gap\"");
        int wmsIdx = body.indexOf("\"domain\":\"wms\"");
        int scmIdx = body.indexOf("\"domain\":\"scm\"");
        int finIdx = body.indexOf("\"domain\":\"finance\"");
        int erpIdx = body.indexOf("\"domain\":\"erp\"");
        assertThat(gapIdx).as("body:\n%s", body).isLessThan(wmsIdx);
        assertThat(wmsIdx).isLessThan(scmIdx);
        assertThat(scmIdx).isLessThan(finIdx);
        assertThat(finIdx).isLessThan(erpIdx);
    }

    // ------------------------------------------------------------------
    // Error envelopes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("400_no_active_tenant_via_handler: MissingTenantException → 400 NO_ACTIVE_TENANT")
    void error_400_no_active_tenant() throws Exception {
        // hasTenant=false → controller throws MissingTenantException
        when(credentialContext.hasTenant()).thenReturn(false);

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-400 body:\n%s", body).isEqualTo(400);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"NO_ACTIVE_TENANT\"");
    }

    @Test
    @DisplayName("401_token_invalid_missing_credential: MissingCredentialException → 401 TOKEN_INVALID")
    void error_401_missing_credential() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(credentialContext.getTenantId()).thenReturn("wms");
        when(compositionUseCase.compose(anyString(), nullable(String.class)))
                .thenThrow(new MissingCredentialException("operator token absent"));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-401 body:\n%s", body).isEqualTo(401);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"TOKEN_INVALID\"");
    }

    @Test
    @DisplayName("401_token_invalid_upstream: UpstreamUnauthorizedException → 401 TOKEN_INVALID")
    void error_401_upstream_unauthorized() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(credentialContext.getTenantId()).thenReturn("wms");
        when(compositionUseCase.compose(anyString(), nullable(String.class)))
                .thenThrow(new UpstreamUnauthorizedException("upstream wms returned 401"));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-401 body:\n%s", body).isEqualTo(401);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"TOKEN_INVALID\"");
    }

    @Test
    @DisplayName("500_generic_internal_error: RuntimeException → 500 INTERNAL_ERROR (handler scope=inbound.web)")
    void error_500_generic() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(credentialContext.getTenantId()).thenReturn("wms");
        when(compositionUseCase.compose(anyString(), nullable(String.class)))
                .thenThrow(new RuntimeException("upstream"));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-500 body:\n%s", body).isEqualTo(500);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"INTERNAL_ERROR\"");
    }

    // ------------------------------------------------------------------
    // TASK-PC-FE-014 — X-Finance-Default-Account-Id header acceptance
    // ------------------------------------------------------------------

    @Test
    @DisplayName("header_omitted_finance_missing_prerequisite: no header → forwards null to use-case; finance card forbidden/MISSING_PREREQUISITE")
    void header_omitted_finance_missing_prerequisite() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(credentialContext.getTenantId()).thenReturn("test-tenant");
        when(compositionUseCase.compose(eq("test-tenant"), nullable(String.class)))
                .thenReturn(List.of(
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), Map.of("ok", true)),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), Map.of("ok", true)),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), Map.of("ok", true)),
                        CompositionLeg.outcomeOnly(LegOutcome.forbidden(DomainTarget.FINANCE, "MISSING_PREREQUISITE")),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), Map.of("ok", true))
                ));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-200 body:\n%s", body).isEqualTo(200);
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"finance\"")
                .contains("\"status\":\"forbidden\"")
                .contains("\"reason\":\"MISSING_PREREQUISITE\"");
        // Verify the 2-arg signature was invoked with null header (the controller
        // threaded the absent header through verbatim — AC-2 regression guard).
        verify(compositionUseCase).compose("test-tenant", null);
    }

    @Test
    @DisplayName("header_set_finance_ok: X-Finance-Default-Account-Id forwarded verbatim to use-case; finance card ok with data")
    void header_set_finance_ok() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(credentialContext.getTenantId()).thenReturn("test-tenant");
        when(compositionUseCase.compose(eq("test-tenant"), eq("acc-uuid-7")))
                .thenReturn(List.of(
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), Map.of("ok", true)),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), Map.of("ok", true)),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), Map.of("ok", true)),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE), Map.of("totalAmount", "1000")),
                        CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), Map.of("ok", true))
                ));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/operator-overview")
                        .header("X-Finance-Default-Account-Id", "acc-uuid-7")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-200 body:\n%s", body).isEqualTo(200);
        assertThat(body).as("body:\n%s", body)
                .contains("\"domain\":\"finance\"")
                .contains("\"status\":\"ok\"")
                .contains("\"totalAmount\":\"1000\"");
        // Verify the 2-arg signature was invoked with the header value verbatim.
        verify(compositionUseCase).compose("test-tenant", "acc-uuid-7");
    }
}
