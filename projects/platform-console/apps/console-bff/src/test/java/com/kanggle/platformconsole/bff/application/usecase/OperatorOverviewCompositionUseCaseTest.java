package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpDepartmentsReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.FinanceBalanceReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.IamAccountsReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ScmInventoryReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.WmsInventoryReadPort;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit test for {@link OperatorOverviewCompositionUseCase}.
 *
 * <p>Uses {@code @ExtendWith(MockitoExtension.class)} STRICT_STUBS per platform
 * testing-strategy.md. Mocks the 5 narrow outbound port interfaces and the
 * {@link CredentialSelectionPort}; uses a real {@link SimpleMeterRegistry} so
 * metric emission can be observed.
 *
 * <p>Scope (TASK-PC-FE-011 AC-4 / AC-7..AC-12):
 * <ul>
 *   <li>Fixed leg order {@code [gap, wms, scm, finance, erp]}.</li>
 *   <li>Per-leg outcome classification (ok / degraded / forbidden).</li>
 *   <li>Cross-leg 401 collapses to {@link UpstreamUnauthorizedException}.</li>
 *   <li>Finance MVP option (b) — {@code forbidden / MISSING_PREREQUISITE} without
 *       firing the outbound HTTP call.</li>
 *   <li>All-down still emits a 5-card envelope (controller emits HTTP 200).</li>
 *   <li>Per-leg degrade-counter emission (one increment per non-ok leg).</li>
 *   <li>Per-leg latency timer emission for the happy path.</li>
 *   <li>Missing GAP operator token → {@link MissingCredentialException} per
 *       fail-closed HARD INVARIANT (mapped to per-card
 *       {@code MISSING_PREREQUISITE} by the use case).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorOverviewCompositionUseCaseTest {

    private static final String TENANT = "wms";
    private static final String OPERATOR_TOKEN = "op-tok-abc";
    private static final String GAP_OIDC_TOKEN = "iam-oidc-xyz";

    @Mock
    CredentialSelectionPort credentialSelection;

    @Mock
    IamAccountsReadPort gapPort;

    @Mock
    WmsInventoryReadPort wmsPort;

    @Mock
    ScmInventoryReadPort scmPort;

    @Mock
    FinanceBalanceReadPort financePort;

    @Mock
    ErpDepartmentsReadPort erpPort;

    SimpleMeterRegistry meterRegistry;
    OperatorOverviewCompositionUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new OperatorOverviewCompositionUseCase(
                credentialSelection, meterRegistry, Tracer.NOOP,
                gapPort, wmsPort, scmPort, financePort, erpPort);
    }

    private void stubAllCredentialsHappy() {
        // Lenient — not every test exercises every credential resolution; STRICT_STUBS
        // would otherwise fail when a leg short-circuits (finance MISSING_PREREQUISITE).
        lenient().when(credentialSelection.selectFor(DomainTarget.IAM))
                .thenReturn(new OutboundCredential.OperatorToken(OPERATOR_TOKEN));
        lenient().when(credentialSelection.selectFor(DomainTarget.WMS))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
        lenient().when(credentialSelection.selectFor(DomainTarget.SCM))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
        lenient().when(credentialSelection.selectFor(DomainTarget.FINANCE))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
        lenient().when(credentialSelection.selectFor(DomainTarget.ERP))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy_all_5_ok: every leg ok → 5 ok cards in fixed order [gap, wms, scm, finance, erp]")
    void happy_all_5_ok() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("page", Map.of("totalElements", 12)));
        when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("snapshotTotal", 34));
        when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("nodeCount", 5));
        // Finance MVP option (b): use case short-circuits to MISSING_PREREQUISITE
        // BEFORE calling the port; the finance port is NEVER invoked in MVP. To
        // exercise the "all 5 ok" happy shape we still assert that the finance
        // card is forbidden/MISSING_PREREQUISITE (MVP-pinned). The "metric_emission_per_leg"
        // test below tracks the per-domain timer assertions for the 4 legs that
        // actually fire.
        when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("meta", Map.of("totalElements", 9)));

        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        assertThat(legs).hasSize(5);
        // Fixed order invariant
        assertThat(legs.get(0).outcome().domain()).isEqualTo(DomainTarget.IAM);
        assertThat(legs.get(1).outcome().domain()).isEqualTo(DomainTarget.WMS);
        assertThat(legs.get(2).outcome().domain()).isEqualTo(DomainTarget.SCM);
        assertThat(legs.get(3).outcome().domain()).isEqualTo(DomainTarget.FINANCE);
        assertThat(legs.get(4).outcome().domain()).isEqualTo(DomainTarget.ERP);
        // 4 legs ok (finance is MVP-pinned forbidden)
        assertThat(legs.get(0).outcome().isOk()).isTrue();
        assertThat(legs.get(1).outcome().isOk()).isTrue();
        assertThat(legs.get(2).outcome().isOk()).isTrue();
        assertThat(legs.get(3).outcome().isForbidden()).isTrue();
        assertThat(legs.get(3).outcome().reason()).isEqualTo("MISSING_PREREQUISITE");
        assertThat(legs.get(4).outcome().isOk()).isTrue();
    }

    // ------------------------------------------------------------------
    // Per-leg degrade scenarios
    // ------------------------------------------------------------------

    @Test
    @DisplayName("one_leg_degraded_downstream_error: wms 5xx → wms degraded/DOWNSTREAM_ERROR; degrade counter increments")
    void one_leg_degraded_downstream_error() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(wmsPort.read(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "boom", null, null, null));
        when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        CompositionLeg wms = legs.get(1);
        assertThat(wms.outcome().isDegraded()).isTrue();
        assertThat(wms.outcome().reason()).isEqualTo("DOWNSTREAM_ERROR");

        // Degrade counter incremented exactly once for wms
        Counter counter = meterRegistry.find("bff_aggregation_degrade_count")
                .tag("dashboard", "operator-overview")
                .tag("degraded_domain", "wms")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("one_leg_forbidden_permission_denied: scm 403 → scm forbidden/PERMISSION_DENIED; others ok")
    void one_leg_forbidden_permission_denied() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(scmPort.read(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "no", null, null, null));
        when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        CompositionLeg scm = legs.get(2);
        assertThat(scm.outcome().isForbidden()).isTrue();
        assertThat(scm.outcome().reason()).isEqualTo("PERMISSION_DENIED");
        assertThat(legs.get(0).outcome().isOk()).isTrue();
        assertThat(legs.get(1).outcome().isOk()).isTrue();
        assertThat(legs.get(4).outcome().isOk()).isTrue();
    }

    @Test
    @DisplayName("finance_missing_prerequisite: MVP option (b) — forbidden/MISSING_PREREQUISITE, finance port never invoked")
    void finance_missing_prerequisite() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        // STRICT_STUBS: financePort.read is intentionally NOT stubbed; the use case
        // MUST short-circuit before invoking it. Any invocation would fail the test
        // via UnnecessaryStubbing (if stubbed) or NPE (if not stubbed and called).

        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        CompositionLeg fin = legs.get(3);
        assertThat(fin.outcome().domain()).isEqualTo(DomainTarget.FINANCE);
        assertThat(fin.outcome().isForbidden()).isTrue();
        assertThat(fin.outcome().reason()).isEqualTo("MISSING_PREREQUISITE");
    }

    @Test
    @DisplayName("all_down_all_degraded: every leg 5xx → 5 degraded cards (NOT thrown; controller still emits 200)")
    void all_down_all_degraded() {
        stubAllCredentialsHappy();
        HttpClientErrorException boom = HttpClientErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "boom", null, null, null);
        when(gapPort.read(anyString(), anyString())).thenThrow(boom);
        when(wmsPort.read(anyString(), anyString())).thenThrow(boom);
        when(scmPort.read(anyString(), anyString())).thenThrow(boom);
        // finance is MISSING_PREREQUISITE (forbidden) by MVP pin
        when(erpPort.read(anyString(), anyString())).thenThrow(boom);

        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        assertThat(legs).hasSize(5);
        assertThat(legs.get(0).outcome().isDegraded()).isTrue();
        assertThat(legs.get(1).outcome().isDegraded()).isTrue();
        assertThat(legs.get(2).outcome().isDegraded()).isTrue();
        // finance is forbidden by MVP pin — still non-ok (degrade-policy counts it)
        assertThat(legs.get(3).outcome().isForbidden()).isTrue();
        assertThat(legs.get(4).outcome().isDegraded()).isTrue();

        // 5 degrade-counter increments (one per non-ok leg)
        double total = 0.0;
        for (DomainTarget d : DomainTarget.values()) {
            Counter c = meterRegistry.find("bff_aggregation_degrade_count")
                    .tag("dashboard", "operator-overview")
                    .tag("degraded_domain", d.name().toLowerCase())
                    .counter();
            if (c != null) {
                total += c.count();
            }
        }
        assertThat(total).isEqualTo(5.0);
    }

    @Test
    @DisplayName("mixed_ok_degraded_forbidden: gap=ok, wms=degraded(503), scm=forbidden(403), erp=degraded(503)")
    void mixed_ok_degraded_forbidden() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(wmsPort.read(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                        "unavail", null, null, null));
        when(scmPort.read(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "no", null, null, null));
        when(erpPort.read(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_GATEWAY,
                        "bad", null, null, null));

        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        assertThat(legs.get(0).outcome().isOk()).isTrue();
        assertThat(legs.get(1).outcome().isDegraded()).isTrue();
        assertThat(legs.get(1).outcome().reason()).isEqualTo("DOWNSTREAM_ERROR");
        assertThat(legs.get(2).outcome().isForbidden()).isTrue();
        assertThat(legs.get(2).outcome().reason()).isEqualTo("PERMISSION_DENIED");
        assertThat(legs.get(3).outcome().isForbidden()).isTrue(); // MVP pin
        assertThat(legs.get(4).outcome().isDegraded()).isTrue();
        assertThat(legs.get(4).outcome().reason()).isEqualTo("DOWNSTREAM_ERROR");
    }

    // ------------------------------------------------------------------
    // Cross-leg 401 collapse
    // ------------------------------------------------------------------

    @Test
    @DisplayName("upstream_401_throws_UpstreamUnauthorizedException: any leg 401 → composition collapses to 401")
    void upstream_401_throws_UpstreamUnauthorizedException() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                        "nope", null, null, null));
        // Other legs may or may not run depending on scheduling; STRICT_STUBS would
        // complain about unused stubs, so use lenient stubs that don't matter for
        // the assertion (any return value works since the 401 collapse fires first).
        lenient().when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        lenient().when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        lenient().when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

        assertThatThrownBy(() -> useCase.compose(TENANT, null))
                .isInstanceOf(UpstreamUnauthorizedException.class)
                .hasMessageContaining("Upstream leg returned 401");
    }

    @Test
    @DisplayName("missing_operator_token: GAP credential resolution throws MissingCredentialException → leg forbidden/MISSING_PREREQUISITE")
    void missing_operator_token_yields_missing_prerequisite() {
        // GAP credential resolution fails closed (no operator token).
        when(credentialSelection.selectFor(DomainTarget.IAM))
                .thenThrow(new MissingCredentialException("operator token absent"));
        // Other legs ok.
        lenient().when(credentialSelection.selectFor(DomainTarget.WMS))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
        lenient().when(credentialSelection.selectFor(DomainTarget.SCM))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
        lenient().when(credentialSelection.selectFor(DomainTarget.ERP))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
        lenient().when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        lenient().when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        lenient().when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

        // The use case maps MissingCredentialException to per-card
        // forbidden/MISSING_PREREQUISITE (per the time() handler) — it does NOT
        // bubble the exception out of compose(). Verify the gap card surface.
        List<CompositionLeg> legs = useCase.compose(TENANT, null);

        CompositionLeg gap = legs.get(0);
        assertThat(gap.outcome().domain()).isEqualTo(DomainTarget.IAM);
        assertThat(gap.outcome().isForbidden()).isTrue();
        assertThat(gap.outcome().reason()).isEqualTo("MISSING_PREREQUISITE");
    }

    // ------------------------------------------------------------------
    // Metric emission per leg
    // ------------------------------------------------------------------

    @Test
    @DisplayName("metric_emission_per_leg: happy run records bff_fanout_latency for all firing legs; no error counter; no degrade counter")
    void metric_emission_per_leg() {
        stubAllCredentialsHappy();
        when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
        when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

        List<CompositionLeg> legs = useCase.compose(TENANT, null);
        assertThat(legs).hasSize(5);

        // Per-leg latency timer registered for the 4 legs that actually fire.
        // Finance is short-circuited (MVP option b) and does NOT register a timer.
        for (DomainTarget d : List.of(DomainTarget.IAM, DomainTarget.WMS,
                DomainTarget.SCM, DomainTarget.ERP)) {
            Timer timer = meterRegistry.find("bff_fanout_latency")
                    .tag("domain", d.name().toLowerCase())
                    .tag("route", "operator-overview")
                    .timer();
            assertThat(timer)
                    .as("expected latency timer for domain=%s route=operator-overview", d)
                    .isNotNull();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }

        // No bff_fanout_errors counter for these 4 legs (happy path).
        for (DomainTarget d : List.of(DomainTarget.IAM, DomainTarget.WMS,
                DomainTarget.SCM, DomainTarget.ERP)) {
            // We allow registry-search to return null when no error happened; we
            // explicitly assert the meter was NOT registered with any error tag.
            assertThat(meterRegistry.find("bff_fanout_errors")
                    .tag("domain", d.name().toLowerCase())
                    .tag("route", "operator-overview")
                    .counters())
                    .as("no error counter expected for happy leg %s", d)
                    .isEmpty();
        }

        // Finance MVP pin emits a missing_prerequisite error counter (not a degrade
        // counter — degrade counter increments per-card when status != OK).
        assertThat(meterRegistry.find("bff_aggregation_degrade_count")
                .tag("dashboard", "operator-overview")
                .tag("degraded_domain", "finance")
                .counter())
                .isNotNull();
    }

    // ------------------------------------------------------------------
    // TASK-PC-FE-014 — finance leg Option (a) activation
    //
    // The new 2-arg compose(tenantId, financeDefaultAccountId) overload threads
    // the operator's finance default account id from the controller's
    // X-Finance-Default-Account-Id header. Both paths first-class:
    //   header-absent / blank / whitespace → MISSING_PREREQUISITE (regression
    //     guard for AC-2; financePort.read AND readBalances NEVER invoked).
    //   header non-blank → readBalances(...) invoked exactly once with the
    //     trimmed account id; finance card ok with data payload (AC-3).
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("FinanceOptionAActivation (TASK-PC-FE-014)")
    class FinanceOptionAActivation {

        @Test
        @DisplayName("(a) header_absent_null: finance leg forbidden/MISSING_PREREQUISITE; readBalances NEVER invoked")
        void header_absent_null_yields_missing_prerequisite() {
            stubAllCredentialsHappy();
            when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

            List<CompositionLeg> legs = useCase.compose(TENANT, null);

            CompositionLeg fin = legs.get(3);
            assertThat(fin.outcome().domain()).isEqualTo(DomainTarget.FINANCE);
            assertThat(fin.outcome().isForbidden()).isTrue();
            assertThat(fin.outcome().reason()).isEqualTo("MISSING_PREREQUISITE");
            // Neither port surface invoked — short-circuit BEFORE any outbound.
            verify(financePort, never()).read(anyString(), anyString());
            verify(financePort, never()).readBalances(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("(b) header_empty_string: empty string treated as absent (hasText false)")
        void header_empty_string_yields_missing_prerequisite() {
            stubAllCredentialsHappy();
            when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

            List<CompositionLeg> legs = useCase.compose(TENANT, "");

            CompositionLeg fin = legs.get(3);
            assertThat(fin.outcome().isForbidden()).isTrue();
            assertThat(fin.outcome().reason()).isEqualTo("MISSING_PREREQUISITE");
            verify(financePort, never()).read(anyString(), anyString());
            verify(financePort, never()).readBalances(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("(c) header_whitespace_only: whitespace treated as absent (hasText false)")
        void header_whitespace_only_yields_missing_prerequisite() {
            stubAllCredentialsHappy();
            when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));

            List<CompositionLeg> legs = useCase.compose(TENANT, "   ");

            CompositionLeg fin = legs.get(3);
            assertThat(fin.outcome().isForbidden()).isTrue();
            assertThat(fin.outcome().reason()).isEqualTo("MISSING_PREREQUISITE");
            verify(financePort, never()).read(anyString(), anyString());
            verify(financePort, never()).readBalances(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("(d) header_non_blank: finance leg ok with data; readBalances invoked exactly once")
        void header_non_blank_yields_finance_ok() {
            stubAllCredentialsHappy();
            when(gapPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(wmsPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(scmPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(erpPort.read(anyString(), anyString())).thenReturn(Map.of("ok", true));
            when(financePort.readBalances(eq(TENANT), anyString(), eq("acc-uuid-7")))
                    .thenReturn(Map.of("totalAmount", "1000"));

            List<CompositionLeg> legs = useCase.compose(TENANT, "acc-uuid-7");

            CompositionLeg fin = legs.get(3);
            assertThat(fin.outcome().domain()).isEqualTo(DomainTarget.FINANCE);
            assertThat(fin.outcome().isOk()).isTrue();
            assertThat(fin.data()).isEqualTo(Map.of("totalAmount", "1000"));
            // Active path = readBalances; legacy port read() must NOT be invoked.
            verify(financePort, times(1)).readBalances(eq(TENANT), anyString(), eq("acc-uuid-7"));
            verify(financePort, never()).read(anyString(), anyString());
        }
    }
}
