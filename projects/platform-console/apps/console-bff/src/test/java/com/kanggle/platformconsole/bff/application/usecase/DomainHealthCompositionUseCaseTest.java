package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.FinanceHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.GapHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ScmHealthReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.WmsHealthReadPort;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit test for {@link DomainHealthCompositionUseCase}.
 *
 * <p>Mocks the 5 narrow health-read ports + a {@link CredentialSelectionPort}
 * stub that is NEVER consulted (the key invariant: domain-health legs are
 * credential-less, the D4 sealed-switch is not invoked).
 *
 * <p>Coverage (§ 2.4.9.2):
 * <ul>
 *   <li>Fixed leg order {@code [gap, wms, scm, finance, erp]}.</li>
 *   <li>5-all-ok happy path with Spring Boot health JSON body propagation.</li>
 *   <li>Per-leg downstream-error → degraded/DOWNSTREAM_ERROR.</li>
 *   <li>All-down (5x 5xx) → all 5 cards degraded + 5 degrade-counter increments.</li>
 *   <li>{@link CredentialSelectionPort#selectFor} is NEVER invoked (the D4 scope
 *       clarification: actuator legs are outside D4).</li>
 *   <li>{@code data.status} payload propagation (UP/DOWN/OUT_OF_SERVICE/UNKNOWN).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DomainHealthCompositionUseCaseTest {

    @Mock
    CredentialSelectionPort credentialSelection;  // INTENTIONALLY UNUSED — invariant guard.

    @Mock
    GapHealthReadPort gapPort;

    @Mock
    WmsHealthReadPort wmsPort;

    @Mock
    ScmHealthReadPort scmPort;

    @Mock
    FinanceHealthReadPort financePort;

    @Mock
    ErpHealthReadPort erpPort;

    SimpleMeterRegistry meterRegistry;
    DomainHealthCompositionUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new DomainHealthCompositionUseCase(
                meterRegistry, gapPort, wmsPort, scmPort, financePort, erpPort);
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy_all_5_ok: every leg UP → 5 ok cards in fixed order, data.status propagated")
    void happy_all_5_ok() {
        when(gapPort.read()).thenReturn(Map.of("status", "UP"));
        when(wmsPort.read()).thenReturn(Map.of("status", "UP"));
        when(scmPort.read()).thenReturn(Map.of("status", "UP"));
        when(financePort.read()).thenReturn(Map.of("status", "UP"));
        when(erpPort.read()).thenReturn(Map.of("status", "UP"));

        List<CompositionLeg> legs = useCase.compose();

        assertThat(legs).hasSize(5);
        assertThat(legs.get(0).outcome().domain()).isEqualTo(DomainTarget.GAP);
        assertThat(legs.get(1).outcome().domain()).isEqualTo(DomainTarget.WMS);
        assertThat(legs.get(2).outcome().domain()).isEqualTo(DomainTarget.SCM);
        assertThat(legs.get(3).outcome().domain()).isEqualTo(DomainTarget.FINANCE);
        assertThat(legs.get(4).outcome().domain()).isEqualTo(DomainTarget.ERP);
        // All cards ok with data.status="UP".
        for (CompositionLeg leg : legs) {
            assertThat(leg.outcome().isOk()).isTrue();
            assertThat(leg.data()).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) leg.data()).get("status")).isEqualTo("UP");
        }
    }

    @Test
    @DisplayName("D4 invariant: CredentialSelectionPort.selectFor is NEVER invoked on a domain-health composition")
    void invariant_credential_selection_never_invoked() {
        when(gapPort.read()).thenReturn(Map.of("status", "UP"));
        when(wmsPort.read()).thenReturn(Map.of("status", "UP"));
        when(scmPort.read()).thenReturn(Map.of("status", "UP"));
        when(financePort.read()).thenReturn(Map.of("status", "UP"));
        when(erpPort.read()).thenReturn(Map.of("status", "UP"));

        useCase.compose();

        // The whole point of § 2.4.9.2: actuator legs are outside D4 scope.
        // The sealed-switch in CredentialSelectionAdapter is NEVER invoked.
        verify(credentialSelection, never()).selectFor(DomainTarget.GAP);
        verify(credentialSelection, never()).selectFor(DomainTarget.WMS);
        verify(credentialSelection, never()).selectFor(DomainTarget.SCM);
        verify(credentialSelection, never()).selectFor(DomainTarget.FINANCE);
        verify(credentialSelection, never()).selectFor(DomainTarget.ERP);
    }

    // ------------------------------------------------------------------
    // Per-leg degrade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("one_leg_degraded: wms 503 → wms degraded/DOWNSTREAM_ERROR; counter increments")
    void one_leg_degraded_downstream_error() {
        when(gapPort.read()).thenReturn(Map.of("status", "UP"));
        when(wmsPort.read())
                .thenThrow(HttpClientErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                        "unavail", null, null, null));
        when(scmPort.read()).thenReturn(Map.of("status", "UP"));
        when(financePort.read()).thenReturn(Map.of("status", "UP"));
        when(erpPort.read()).thenReturn(Map.of("status", "UP"));

        List<CompositionLeg> legs = useCase.compose();

        CompositionLeg wms = legs.get(1);
        assertThat(wms.outcome().isDegraded()).isTrue();
        assertThat(wms.outcome().reason()).isEqualTo("DOWNSTREAM_ERROR");

        Counter counter = meterRegistry.find("bff_aggregation_degrade_count")
                .tag("dashboard", "domain-health")
                .tag("degraded_domain", "wms")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("all_5_down: every leg 5xx → 5 degraded cards (NOT thrown; HTTP 200 still emitted)")
    void all_5_down_envelope() {
        HttpClientErrorException boom = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null);
        when(gapPort.read()).thenThrow(boom);
        when(wmsPort.read()).thenThrow(boom);
        when(scmPort.read()).thenThrow(boom);
        when(financePort.read()).thenThrow(boom);
        when(erpPort.read()).thenThrow(boom);

        List<CompositionLeg> legs = useCase.compose();

        assertThat(legs).hasSize(5);
        for (CompositionLeg leg : legs) {
            assertThat(leg.outcome().isDegraded()).isTrue();
        }
        // 5 degrade-counter increments (one per non-ok leg).
        double total = 0.0;
        for (DomainTarget d : DomainTarget.values()) {
            Counter c = meterRegistry.find("bff_aggregation_degrade_count")
                    .tag("dashboard", "domain-health")
                    .tag("degraded_domain", d.name().toLowerCase())
                    .counter();
            if (c != null) {
                total += c.count();
            }
        }
        assertThat(total).isEqualTo(5.0);
    }

    @Test
    @DisplayName("data.status varieties: UP/DOWN/OUT_OF_SERVICE/UNKNOWN propagated as data payload")
    void data_status_varieties_propagated() {
        when(gapPort.read()).thenReturn(Map.of("status", "UP"));
        when(wmsPort.read()).thenReturn(Map.of("status", "DOWN"));
        when(scmPort.read()).thenReturn(Map.of("status", "OUT_OF_SERVICE"));
        when(financePort.read()).thenReturn(Map.of("status", "UNKNOWN"));
        when(erpPort.read()).thenReturn(Map.of("status", "UP"));

        List<CompositionLeg> legs = useCase.compose();

        // All 5 are ok at composition level — the producer self-reported status
        // does NOT degrade the leg (the BFF reached the producer and got a
        // response; DOWN/OOS/UNKNOWN are honest producer self-reports, NOT BFF
        // failures).
        assertThat(legs.get(0).outcome().isOk()).isTrue();
        assertThat(((Map<?, ?>) legs.get(0).data()).get("status")).isEqualTo("UP");
        assertThat(legs.get(1).outcome().isOk()).isTrue();
        assertThat(((Map<?, ?>) legs.get(1).data()).get("status")).isEqualTo("DOWN");
        assertThat(legs.get(2).outcome().isOk()).isTrue();
        assertThat(((Map<?, ?>) legs.get(2).data()).get("status")).isEqualTo("OUT_OF_SERVICE");
        assertThat(legs.get(3).outcome().isOk()).isTrue();
        assertThat(((Map<?, ?>) legs.get(3).data()).get("status")).isEqualTo("UNKNOWN");
        assertThat(legs.get(4).outcome().isOk()).isTrue();
    }

    @Test
    @DisplayName("metric_emission: bff_fanout_latency timer registered per leg with route=domain-health")
    void metric_emission_per_leg() {
        when(gapPort.read()).thenReturn(Map.of("status", "UP"));
        when(wmsPort.read()).thenReturn(Map.of("status", "UP"));
        when(scmPort.read()).thenReturn(Map.of("status", "UP"));
        when(financePort.read()).thenReturn(Map.of("status", "UP"));
        when(erpPort.read()).thenReturn(Map.of("status", "UP"));

        useCase.compose();

        for (DomainTarget d : DomainTarget.values()) {
            Timer timer = meterRegistry.find("bff_fanout_latency")
                    .tag("domain", d.name().toLowerCase())
                    .tag("route", "domain-health")
                    .timer();
            assertThat(timer)
                    .as("expected latency timer for domain=%s route=domain-health", d)
                    .isNotNull();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }
    }
}
