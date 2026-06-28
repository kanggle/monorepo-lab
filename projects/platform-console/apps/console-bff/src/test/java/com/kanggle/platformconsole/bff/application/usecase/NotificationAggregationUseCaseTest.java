package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.application.port.outbound.ErpNotificationsReadPort;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import com.kanggle.platformconsole.bff.infrastructure.config.NotificationAggregatorProperties;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Application unit test for {@link NotificationAggregationUseCase}
 * (ADR-MONO-043 P3a / D2).
 *
 * <p>{@code @ExtendWith(MockitoExtension.class)} STRICT_STUBS per platform
 * testing-strategy.md. Mocks the erp inbox read port + {@link CredentialSelectionPort};
 * uses a real {@link SimpleMeterRegistry} + {@link Tracer#NOOP}.
 *
 * <p>Scope:
 * <ul>
 *   <li>Merge + {@code createdAt}-desc sort across a domain.</li>
 *   <li>{@code sourceDomain} injection when a domain omits it (contract § 4.2);
 *       a pre-existing {@code sourceDomain} is preserved, never overwritten.</li>
 *   <li>Per-domain degrade → {@code degradedDomains} populated, still returns a
 *       result (no throw) with the surviving domains' items (D5).</li>
 *   <li>NO cross-leg 401 collapse — a 401 degrades only that leg (the divergence
 *       from Operator Overview).</li>
 *   <li>Mark-read dispatch to the owning domain with its credential (D6);
 *       unknown sourceDomain → {@link UnknownNotificationDomainException}.</li>
 *   <li>erp leg dispatches the IAM OIDC token, never the operator token (D6).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class NotificationAggregationUseCaseTest {

    private static final String GAP_OIDC_TOKEN = "iam-oidc-xyz";

    @Mock
    CredentialSelectionPort credentialSelection;

    @Mock
    ErpNotificationsReadPort erpPort;

    SimpleMeterRegistry meterRegistry;
    NotificationAggregationUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        NotificationAggregatorProperties props = new NotificationAggregatorProperties();
        props.setDomains(List.of("erp"));
        useCase = new NotificationAggregationUseCase(
                props, credentialSelection, meterRegistry, Tracer.NOOP, erpPort);
    }

    private void stubErpCredential() {
        when(credentialSelection.selectFor(DomainTarget.ERP))
                .thenReturn(new OutboundCredential.IamOidcAccessToken(GAP_OIDC_TOKEN));
    }

    private static Map<String, Object> item(String id, String createdAt) {
        return new java.util.LinkedHashMap<>(Map.of(
                "id", id,
                "type", "APPROVAL_SUBMITTED",
                "title", "t-" + id,
                "body", "b-" + id,
                "read", false,
                "createdAt", createdAt));
    }

    // ------------------------------------------------------------------
    // Merge + sort + sourceDomain injection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aggregate_merges_and_sorts_createdAt_desc: items returned newest-first")
    void aggregate_merges_and_sorts() {
        stubErpCredential();
        when(erpPort.readInbox(eq(GAP_OIDC_TOKEN), anyInt(), anyInt(), any()))
                .thenReturn(Map.of(
                        "data", List.of(
                                item("a", "2026-06-28T01:00:00Z"),
                                item("b", "2026-06-28T03:00:00Z"),
                                item("c", "2026-06-28T02:00:00Z")),
                        "meta", Map.of("totalElements", 3)));

        NotificationAggregationResult result = useCase.aggregate(0, 20, null);

        assertThat(result.degradedDomains()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.items()).extracting(i -> i.get("id"))
                .containsExactly("b", "c", "a");
    }

    @Test
    @DisplayName("aggregate_injects_sourceDomain_when_absent: erp item gets sourceDomain=erp")
    void aggregate_injects_source_domain_when_absent() {
        stubErpCredential();
        when(erpPort.readInbox(eq(GAP_OIDC_TOKEN), anyInt(), anyInt(), any()))
                .thenReturn(Map.of(
                        "data", List.of(item("a", "2026-06-28T01:00:00Z")),
                        "meta", Map.of("totalElements", 1)));

        NotificationAggregationResult result = useCase.aggregate(0, 20, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0)).containsEntry("sourceDomain", "erp");
    }

    @Test
    @DisplayName("aggregate_preserves_existing_sourceDomain: a domain-supplied sourceDomain is not overwritten")
    void aggregate_preserves_existing_source_domain() {
        stubErpCredential();
        Map<String, Object> carried = item("a", "2026-06-28T01:00:00Z");
        carried.put("sourceDomain", "erp");
        when(erpPort.readInbox(eq(GAP_OIDC_TOKEN), anyInt(), anyInt(), any()))
                .thenReturn(Map.of("data", List.of(carried), "meta", Map.of("totalElements", 1)));

        NotificationAggregationResult result = useCase.aggregate(0, 20, null);

        assertThat(result.items().get(0)).containsEntry("sourceDomain", "erp");
    }

    // ------------------------------------------------------------------
    // Per-domain degrade (D5) — still returns, NO throw
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aggregate_degrades_per_domain_on_5xx: erp down → degradedDomains=[erp], empty items, NO throw")
    void aggregate_degrades_per_domain_on_5xx() {
        stubErpCredential();
        when(erpPort.readInbox(eq(GAP_OIDC_TOKEN), anyInt(), anyInt(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                        "down", null, null, null));

        NotificationAggregationResult result = useCase.aggregate(0, 20, null);

        assertThat(result.degradedDomains()).containsExactly("erp");
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("aggregate_401_degrades_only_that_leg_no_collapse: erp 401 → degradedDomains=[erp], NO UpstreamUnauthorizedException")
    void aggregate_401_does_not_collapse() {
        stubErpCredential();
        when(erpPort.readInbox(eq(GAP_OIDC_TOKEN), anyInt(), anyInt(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                        "unauthorized", null, null, null));

        // Must NOT throw (the divergence from Operator Overview's cross-leg 401 collapse).
        NotificationAggregationResult result = useCase.aggregate(0, 20, null);

        assertThat(result.degradedDomains()).containsExactly("erp");
        assertThat(result.items()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Credential dispatch (D6) — erp leg gets the IAM OIDC token
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aggregate_dispatches_iam_oidc_token_to_erp: erp leg bearer = IAM OIDC access token")
    void aggregate_dispatches_iam_oidc_token() {
        stubErpCredential();
        when(erpPort.readInbox(eq(GAP_OIDC_TOKEN), eq(0), eq(20), eq(Boolean.TRUE)))
                .thenReturn(Map.of("data", List.of(), "meta", Map.of("totalElements", 0)));

        useCase.aggregate(0, 20, true);

        verify(erpPort).readInbox(eq(GAP_OIDC_TOKEN), eq(0), eq(20), eq(Boolean.TRUE));
    }

    // ------------------------------------------------------------------
    // Mark-read dispatch (contract § 4.5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("markRead_dispatches_to_owning_domain: erp mark-read uses the erp port + IAM OIDC token")
    void mark_read_dispatches_to_owning_domain() {
        stubErpCredential();
        when(erpPort.markRead(GAP_OIDC_TOKEN, "n-1"))
                .thenReturn(Map.of("data", Map.of("id", "n-1", "read", true)));

        Map<String, Object> body = useCase.markRead("erp", "n-1");

        verify(erpPort).markRead(GAP_OIDC_TOKEN, "n-1");
        assertThat(body).containsKey("data");
    }

    @Test
    @DisplayName("markRead_unknown_domain_throws: unknown sourceDomain → UnknownNotificationDomainException; no port call")
    void mark_read_unknown_domain_throws() {
        assertThatThrownBy(() -> useCase.markRead("wms", "n-1"))
                .isInstanceOf(UnknownNotificationDomainException.class);

        verify(erpPort, never()).markRead(anyString(), anyString());
        verifyNoInteractions(credentialSelection);
    }
}
