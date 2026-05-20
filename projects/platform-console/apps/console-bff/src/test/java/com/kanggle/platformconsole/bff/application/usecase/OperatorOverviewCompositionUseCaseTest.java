package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application unit test — verifies the degrade-policy classification shape.
 *
 * <p>Uses {@code @ExtendWith(MockitoExtension.class)} STRICT_STUBS per platform
 * testing-strategy.md.
 *
 * <p>This is the composition use-case fixture referenced in architecture.md
 * § Test Pyramid (application unit layer). The empty stub of
 * {@link OperatorOverviewCompositionUseCase} is the hook; TASK-PC-FE-011 replaces
 * it with the actual 5-domain fan-out.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorOverviewCompositionUseCaseTest {

    @Mock
    CredentialSelectionPort credentialSelection;

    @Test
    @DisplayName("Skeleton compose() returns empty list")
    void skeletonCompose_returnsEmptyList() {
        var useCase = new OperatorOverviewCompositionUseCase(credentialSelection);
        List<LegOutcome> outcomes = useCase.compose("wms");
        assertThat(outcomes).isEmpty();
    }

    @Test
    @DisplayName("DegradePolicy.isPartialFailure returns false for empty outcomes")
    void degradePolicy_emptyOutcomes_noPartialFailure() {
        assertThat(DegradePolicy.isPartialFailure(List.of())).isFalse();
    }

    @Test
    @DisplayName("DegradePolicy.isAllDown returns false for empty outcomes")
    void degradePolicy_emptyOutcomes_notAllDown() {
        assertThat(DegradePolicy.isAllDown(List.of())).isFalse();
    }

    @Test
    @DisplayName("DegradePolicy: 1 ok + 1 degraded → partial failure")
    void degradePolicy_partialFailure() {
        List<LegOutcome> outcomes = List.of(
                LegOutcome.ok(DomainTarget.GAP),
                LegOutcome.degraded(DomainTarget.WMS, "timeout")
        );
        assertThat(DegradePolicy.isPartialFailure(outcomes)).isTrue();
        assertThat(DegradePolicy.isAllDown(outcomes)).isFalse();
        assertThat(DegradePolicy.countDegraded(outcomes)).isEqualTo(1L);
    }

    @Test
    @DisplayName("DegradePolicy: all degraded → all-down (still returns 200 envelope per D5.B rejection)")
    void degradePolicy_allDown() {
        List<LegOutcome> outcomes = List.of(
                LegOutcome.degraded(DomainTarget.GAP, "circuit_open"),
                LegOutcome.degraded(DomainTarget.WMS, "timeout"),
                LegOutcome.degraded(DomainTarget.SCM, "5xx"),
                LegOutcome.degraded(DomainTarget.FINANCE, "timeout"),
                LegOutcome.degraded(DomainTarget.ERP, "timeout")
        );
        assertThat(DegradePolicy.isAllDown(outcomes)).isTrue();
        assertThat(DegradePolicy.isPartialFailure(outcomes)).isTrue();
        assertThat(DegradePolicy.countDegraded(outcomes)).isEqualTo(5L);
    }

    @Test
    @DisplayName("DegradePolicy: forbidden leg classified separately from degraded")
    void degradePolicy_forbiddenLeg() {
        List<LegOutcome> outcomes = List.of(
                LegOutcome.ok(DomainTarget.GAP),
                LegOutcome.forbidden(DomainTarget.WMS, "TENANT_FORBIDDEN")
        );
        assertThat(DegradePolicy.isPartialFailure(outcomes)).isTrue();
        assertThat(DegradePolicy.isAllDown(outcomes)).isFalse();
        // forbidden is counted alongside degraded for degrade-count purposes
        assertThat(DegradePolicy.countDegraded(outcomes)).isEqualTo(1L);
        // verify classification
        assertThat(outcomes.get(1).isForbidden()).isTrue();
        assertThat(outcomes.get(1).isDegraded()).isFalse();
    }
}
