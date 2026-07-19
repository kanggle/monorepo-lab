package com.example.scmplatform.demandplanning.application;

import com.example.scmplatform.demandplanning.application.port.outbound.InventoryVisibilityPort;
import com.example.scmplatform.demandplanning.application.port.outbound.InventoryVisibilityPort.SkuWarehouseQty;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderPolicyPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.application.usecase.SweepReorderUseCase;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the batch sweep leg of ADR-MONO-050 D9 (TASK-SCM-BE-037).
 *
 * <p>The load-bearing property: a BATCH-origin suggestion now carries the warehouse
 * <b>CODE</b> that IVS learned from wms's inventory mutation events, so a PO
 * materialized from it can address a wms {@code inbound-expected.v1} by code — the
 * same as the live alert path. Before this task the batch path hard-coded
 * {@code warehouseCode = null} and could never emit.
 *
 * <p>The null-code case is equally load-bearing and asserted separately: the code is
 * best-effort, so a null must degrade only the downstream addressing — never suppress
 * the suggestion itself (fail-closed, no uuid leak).
 */
@ExtendWith(MockitoExtension.class)
class SweepReorderUseCaseTest {

    private static final String TENANT = "scm";
    private static final String SKU = "SKU-1";
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Mock private ReorderPolicyPort policyPort;
    @Mock private SkuSupplierMappingPort mappingPort;
    @Mock private ReorderSuggestionPort suggestionPort;
    @Mock private InventoryVisibilityPort ivsPort;

    private SweepReorderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SweepReorderUseCase(policyPort, mappingPort, suggestionPort, ivsPort,
                new SimpleMeterRegistry());
    }

    /** Below the reorder point, mapped SKU, no open suggestion → one suggestion raised. */
    private void stubHappyPath() {
        when(mappingPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.of(
                new SkuSupplierMapping(SKU, "SUP-0043", 25, 3, "KRW", TENANT)));
        when(policyPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.of(
                new ReorderPolicy(SKU, 10, 2, 40, TENANT, 0, Instant.now())));
        when(suggestionPort.hasOpenSuggestion(eq(TENANT), eq(SKU), any(UUID.class)))
                .thenReturn(false);
        when(suggestionPort.save(any(ReorderSuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ReorderSuggestion capturedSuggestion() {
        ArgumentCaptor<ReorderSuggestion> captor =
                ArgumentCaptor.forClass(ReorderSuggestion.class);
        verify(suggestionPort).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void batchSweepRaisesSuggestionCarryingTheWarehouseCode() {
        when(ivsPort.findAllBelowReorderPoint(TENANT)).thenReturn(List.of(
                new SkuWarehouseQty(SKU, WAREHOUSE_ID, 3, "WH01")));
        stubHappyPath();

        int raised = useCase.sweep();

        assertThat(raised).isEqualTo(1);
        ReorderSuggestion suggestion = capturedSuggestion();
        assertThat(suggestion.getSource()).isEqualTo(SuggestionSource.BATCH);
        assertThat(suggestion.getWarehouseCode()).isEqualTo("WH01");
        // The uuid dimension is retained purely as the dedup key — never emitted downstream.
        assertThat(suggestion.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
    }

    @Test
    void nullWarehouseCodeStillRaisesTheSuggestion() {
        when(ivsPort.findAllBelowReorderPoint(TENANT)).thenReturn(List.of(
                new SkuWarehouseQty(SKU, WAREHOUSE_ID, 3, null)));
        stubHappyPath();

        int raised = useCase.sweep();

        assertThat(raised).isEqualTo(1);
        assertThat(capturedSuggestion().getWarehouseCode()).isNull();
    }

    @Test
    void aboveReorderPointRaisesNothing() {
        when(ivsPort.findAllBelowReorderPoint(TENANT)).thenReturn(List.of(
                new SkuWarehouseQty(SKU, WAREHOUSE_ID, 99, "WH01")));
        when(mappingPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.of(
                new SkuSupplierMapping(SKU, "SUP-0043", 25, 3, "KRW", TENANT)));
        when(policyPort.findBySkuCode(TENANT, SKU)).thenReturn(Optional.of(
                new ReorderPolicy(SKU, 10, 2, 40, TENANT, 0, Instant.now())));

        assertThat(useCase.sweep()).isZero();
        verify(suggestionPort, never()).save(any());
    }

    @Test
    void ivsUnavailableSkipsTheRunWithoutThrowing() {
        when(ivsPort.findAllBelowReorderPoint(TENANT))
                .thenThrow(new RuntimeException("connection refused"));

        assertThat(useCase.sweep()).isZero();
        verify(suggestionPort, never()).save(any());
        verify(mappingPort, never()).findBySkuCode(anyString(), anyString());
    }
}
