package com.kanggle.platformconsole.bff.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanggle.platformconsole.bff.application.composition.CompositionLeg;
import com.kanggle.platformconsole.bff.application.port.outbound.EcommerceOverviewReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ErpDepartmentsReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.FinanceBalanceReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.IamAccountsReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.ScmInventoryReadPort;
import com.kanggle.platformconsole.bff.application.port.outbound.WmsInventoryReadPort;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import com.kanggle.platformconsole.bff.support.LegResilienceDoubles;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The console-bff half of the shared leg-body contract (TASK-PC-FE-295 AC-3).
 *
 * <p>Both sides of the operator-overview wire read the SAME file —
 * {@code projects/platform-console/specs/contracts/fixtures/operator-overview-leg-bodies.json}.
 * console-web's {@code leg-body-contract.test.tsx} asserts each card RENDERS a
 * value from those bodies; this class asserts the composition carries each body
 * to the card UNCHANGED. Neither assertion is worth much alone: a renderer that
 * reads a shape nothing sends is green in isolation, and a pass-through that is
 * never compared to what the renderer reads is green in isolation too. That is
 * precisely how three cards (finance, wms, scm) shipped broken — the bff slice
 * seeded {@code Map.of("balance", 0)}, the web test seeded
 * {@code { balance: { amount, currency } }}, both suites agreed with themselves,
 * and the operator saw «잔액 정보 없음».
 *
 * <p>🔴 Verbatim pass-through is the CONTRACT, not an implementation detail:
 * § 2.4.9.1 declares each leg's {@code data} to be the producer's own body, so
 * a bff that started reshaping a body would break the consumer that this file
 * pins. If a future leg genuinely needs composing (e.g. a second query), the
 * contract has to say so first — and this test is where that change becomes
 * visible.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorOverviewLegBodyContractTest {

    private static final String TENANT = "demo-corp";
    private static final String OPERATOR_TOKEN = "op-tok-abc";
    private static final String OIDC_TOKEN = "iam-oidc-xyz";
    private static final String FINANCE_ACCOUNT_ID = "sample-account-0001";

    private static final String FIXTURE_RELATIVE_PATH =
            "specs/contracts/fixtures/operator-overview-leg-bodies.json";

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
    @Mock
    EcommerceOverviewReadPort ecommercePort;

    OperatorOverviewCompositionUseCase useCase;
    Map<String, Object> legs;

    @BeforeEach
    void setUp() throws IOException {
        useCase = new OperatorOverviewCompositionUseCase(
                credentialSelection, new SimpleMeterRegistry(), Tracer.NOOP,
                LegResilienceDoubles.passThrough(),
                gapPort, wmsPort, scmPort, financePort, erpPort, ecommercePort);
        legs = readLegFixtures();
    }

    /**
     * Walks up from the test's working directory to the {@code platform-console}
     * project root, so the fixture resolves whether Gradle runs the test from
     * the module directory or from the repo root.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> readLegFixtures() throws IOException {
        File dir = new File("").getAbsoluteFile();
        File found = null;
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParentFile()) {
            File candidate = new File(dir, FIXTURE_RELATIVE_PATH);
            if (candidate.isFile()) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException(
                    "leg-body contract fixture not found from " + new File("").getAbsolutePath()
                            + " — expected an ancestor holding " + FIXTURE_RELATIVE_PATH);
        }
        Map<String, Object> root = new ObjectMapper().readValue(found, Map.class);
        Map<String, Object> declared = (Map<String, Object>) root.get("legs");
        if (declared == null || declared.isEmpty()) {
            throw new IllegalStateException("leg-body contract fixture declares no legs");
        }
        return declared;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(String leg) {
        Map<String, Object> entry = (Map<String, Object>) legs.get(leg);
        assertThat(entry)
                .as("the shared fixture must declare the %s leg", leg)
                .isNotNull();
        return (Map<String, Object>) entry.get("body");
    }

    private void stubCredentials() {
        when(credentialSelection.selectFor(DomainTarget.IAM))
                .thenReturn(new OutboundCredential.OperatorToken(OPERATOR_TOKEN));
        when(credentialSelection.selectFor(DomainTarget.WMS))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(OIDC_TOKEN));
        when(credentialSelection.selectFor(DomainTarget.SCM))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(OIDC_TOKEN));
        when(credentialSelection.selectFor(DomainTarget.FINANCE))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(OIDC_TOKEN));
        when(credentialSelection.selectFor(DomainTarget.ERP))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(OIDC_TOKEN));
        when(credentialSelection.selectFor(DomainTarget.ECOMMERCE))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(OIDC_TOKEN));
    }

    @Test
    @DisplayName("the shared fixture declares exactly the six composed legs (a new leg must add one)")
    void fixture_covers_the_six_legs() {
        assertThat(legs.keySet())
                .containsExactlyInAnyOrder("iam", "wms", "scm", "finance", "erp", "ecommerce");
    }

    @Test
    @DisplayName("every leg's producer body reaches the card unchanged — no reshaping anywhere in the composition")
    void every_leg_body_reaches_the_card_unchanged() {
        stubCredentials();
        when(gapPort.read(anyString(), anyString())).thenReturn(body("iam"));
        when(wmsPort.read(anyString(), anyString())).thenReturn(body("wms"));
        when(scmPort.read(anyString(), anyString())).thenReturn(body("scm"));
        when(financePort.readBalances(anyString(), anyString(), anyString()))
                .thenReturn(body("finance"));
        when(erpPort.read(anyString(), anyString())).thenReturn(body("erp"));
        when(ecommercePort.read(anyString(), anyString())).thenReturn(body("ecommerce"));

        List<CompositionLeg> composed = useCase.compose(TENANT, FINANCE_ACCOUNT_ID);

        assertThat(composed).hasSize(6);
        assertThat(composed).allSatisfy(leg ->
                assertThat(leg.outcome().isOk())
                        .as("leg %s must be ok for this test to say anything about its body",
                                leg.outcome().domain())
                        .isTrue());
        assertThat(composed.get(0).data()).isEqualTo(body("iam"));
        assertThat(composed.get(1).data()).isEqualTo(body("wms"));
        assertThat(composed.get(2).data()).isEqualTo(body("scm"));
        assertThat(composed.get(3).data()).isEqualTo(body("finance"));
        assertThat(composed.get(4).data()).isEqualTo(body("erp"));
        assertThat(composed.get(5).data()).isEqualTo(body("ecommerce"));
    }

    @Test
    @DisplayName("the fields each card reads are present in the bodies this test carries (the fixture is not vacuous)")
    @SuppressWarnings("unchecked")
    void the_fixture_bodies_actually_carry_what_the_cards_read() {
        // 🔴 Without this cell, the pass-through assertion above would still pass
        // if every body were `{}` — "unchanged" is trivially true of nothing. The
        // keys below are exactly the ones the console-web renderers read, so this
        // is the Java-side statement of what the TS census asserts by rendering.
        assertThat(body("iam").get("totalElements")).isInstanceOf(Number.class);
        assertThat(((Map<String, Object>) body("wms").get("page")).get("totalElements"))
                .isInstanceOf(Number.class);
        assertThat(((Map<String, Object>) body("scm").get("data")).get("totalElements"))
                .isInstanceOf(Number.class);
        assertThat(((Map<String, Object>) body("scm").get("meta")).get("warning"))
                .isInstanceOf(String.class);
        List<Map<String, Object>> balances = (List<Map<String, Object>>) body("finance").get("data");
        assertThat(balances).isNotEmpty();
        // F5: money fields stay minor-units STRINGS on the wire.
        assertThat(balances.get(0).get("ledger")).isInstanceOf(String.class);
        assertThat(balances.get(0).get("currency")).isInstanceOf(String.class);
        assertThat(((Map<String, Object>) body("erp").get("meta")).get("totalElements"))
                .isInstanceOf(Number.class);
        assertThat(body("ecommerce").get("totalElements")).isInstanceOf(Number.class);
    }
}
