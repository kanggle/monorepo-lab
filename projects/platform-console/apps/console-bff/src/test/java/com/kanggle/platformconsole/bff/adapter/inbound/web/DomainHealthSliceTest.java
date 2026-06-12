package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.adapter.outbound.http.OperatorCredentialContext;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.usecase.DomainHealthCompositionUseCase;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Slice test for {@link DomainHealthController}.
 *
 * <p>Mirrors {@code OperatorOverviewSliceTest} — {@code @WebMvcTest} with
 * security filters disabled. Mocks {@link DomainHealthCompositionUseCase} and
 * {@link OperatorCredentialContext} directly.
 *
 * <p>Coverage (§ 2.4.9.2):
 * <ul>
 *   <li>200 happy envelope shape — 6 cards in fixed order with {@code data.status}.</li>
 *   <li>200 mixed envelope — ok + degraded cards (NO forbidden branch).</li>
 *   <li>400 NO_ACTIVE_TENANT via MissingTenantException.</li>
 * </ul>
 *
 * <p>Note: this route's use-case {@code compose()} takes no arguments
 * (no tenantId / credential pass-through to actuator legs).
 */
@WebMvcTest(controllers = DomainHealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DomainHealthSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DomainHealthCompositionUseCase compositionUseCase;

    @MockitoBean
    OperatorCredentialContext credentialContext;

    @Test
    @DisplayName("happy_200_envelope: 6-card all-UP envelope; data.status present, no reason on ok cards")
    void happy_200_envelope() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(compositionUseCase.compose()).thenReturn(List.of(
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.IAM), Map.of("status", "UP")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.WMS), Map.of("status", "UP")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), Map.of("status", "UP")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.FINANCE), Map.of("status", "UP")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), Map.of("status", "UP")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.ECOMMERCE), Map.of("status", "UP"))
        ));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/domain-health")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-200 body:\n%s", body).isEqualTo(200);
        assertThat(body).as("body shape:\n%s", body).contains("\"asOf\"").contains("\"cards\"");
        // Fixed order: gap → wms → scm → finance → erp → ecommerce.
        int gapIdx = body.indexOf("\"domain\":\"iam\"");
        int wmsIdx = body.indexOf("\"domain\":\"wms\"");
        int scmIdx = body.indexOf("\"domain\":\"scm\"");
        int finIdx = body.indexOf("\"domain\":\"finance\"");
        int erpIdx = body.indexOf("\"domain\":\"erp\"");
        int ecomIdx = body.indexOf("\"domain\":\"ecommerce\"");
        assertThat(gapIdx).as("body:\n%s", body).isLessThan(wmsIdx);
        assertThat(wmsIdx).isLessThan(scmIdx);
        assertThat(scmIdx).isLessThan(finIdx);
        assertThat(finIdx).isLessThan(erpIdx);
        // ecommerce card renders last (TASK-MONO-241 6th card).
        assertThat(ecomIdx).as("ecommerce card present + last, body:\n%s", body).isGreaterThan(erpIdx);
        // data.status present on ok cards.
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"UP\"");
        // NO 'forbidden' anywhere — this route only emits {ok, degraded}.
        assertThat(body).as("body:\n%s", body).doesNotContain("\"status\":\"forbidden\"");
        assertThat(body).as("body:\n%s", body).doesNotContain("\"forbidden\"");
    }

    @Test
    @DisplayName("mixed_200_envelope: ok + degraded cards; no FORBIDDEN branch ever")
    void mixed_200_envelope() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(true);
        when(compositionUseCase.compose()).thenReturn(List.of(
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.IAM), Map.of("status", "UP")),
                CompositionLeg.outcomeOnly(LegOutcome.degraded(DomainTarget.WMS, "DOWNSTREAM_ERROR")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.SCM), Map.of("status", "DOWN")),
                CompositionLeg.outcomeOnly(LegOutcome.degraded(DomainTarget.FINANCE, "TIMEOUT")),
                CompositionLeg.ok(LegOutcome.ok(DomainTarget.ERP), Map.of("status", "OUT_OF_SERVICE"))
        ));

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/domain-health")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-200 body:\n%s", body).isEqualTo(200);
        // wms degraded with reason; scm ok with DOWN data; finance degraded TIMEOUT.
        assertThat(body).as("body:\n%s", body).contains("\"reason\":\"DOWNSTREAM_ERROR\"");
        assertThat(body).as("body:\n%s", body).contains("\"reason\":\"TIMEOUT\"");
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"DOWN\"");
        assertThat(body).as("body:\n%s", body).contains("\"status\":\"OUT_OF_SERVICE\"");
        // NO 'forbidden' anywhere.
        assertThat(body).as("body:\n%s", body).doesNotContain("\"forbidden\"");
    }

    @Test
    @DisplayName("400_no_active_tenant: MissingTenantException → 400 NO_ACTIVE_TENANT (for log MDC / audit)")
    void error_400_no_active_tenant() throws Exception {
        when(credentialContext.hasTenant()).thenReturn(false);

        MvcResult result = mockMvc.perform(get("/api/console/dashboards/domain-health")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();

        assertThat(status).as("non-400 body:\n%s", body).isEqualTo(400);
        assertThat(body).as("body:\n%s", body).contains("\"code\":\"NO_ACTIVE_TENANT\"");
    }
}
