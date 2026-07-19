package com.example.scmplatform.demandplanning.application;

import com.example.scmplatform.demandplanning.application.port.outbound.OpsAlertPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderPolicyPort;
import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.application.port.outbound.SkuSupplierMappingPort;
import com.example.scmplatform.demandplanning.application.usecase.EvaluateReorderUseCase;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EvaluateReorderUseCaseTest {

    @Mock ReorderPolicyPort policyPort;
    @Mock SkuSupplierMappingPort mappingPort;
    @Mock ReorderSuggestionPort suggestionPort;
    @Mock ProcessedEventPort processedEventPort;
    @Mock OpsAlertPort opsAlertPort;

    private EvaluateReorderUseCase useCase;

    static final String SKU = "SKU-APPLE-001";
    static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String WAREHOUSE_CODE = "WH-SEOUL-01";
    // ADR-MONO-050 D9: supplierId is a supplier CODE (String), not a UUID.
    static final String SUPPLIER_ID = "SUP-0042";
    static final UUID EVENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new EvaluateReorderUseCase(policyPort, mappingPort, suggestionPort,
                processedEventPort, opsAlertPort, new SimpleMeterRegistry());
    }

    @Test
    void evaluateFromAlert_raisessuggestion_whenBelowReorderPoint() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(false);
        when(mappingPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(mapping()));
        when(policyPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(policy(10, 100)));
        when(suggestionPort.hasOpenSuggestion("scm", SKU, WAREHOUSE_ID)).thenReturn(false);
        when(suggestionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 5, 8, Instant.now());

        verify(suggestionPort).save(any(ReorderSuggestion.class));
        verify(processedEventPort).markProcessed(eq(EVENT_ID), eq("scm"), any(), any());
    }

    @Test
    void evaluateFromAlert_noOp_whenAboveReorderPoint() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(false);
        when(mappingPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(mapping()));
        when(policyPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(policy(10, 100)));

        useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 15, 8, Instant.now());

        verify(suggestionPort, never()).save(any());
        verify(processedEventPort).markProcessed(any(), any(), any(), any()); // still marks processed
    }

    @Test
    void evaluateFromAlert_threadsWarehouseCode_ontoSuggestion_ADR050D9() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(false);
        when(mappingPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(mapping()));
        when(policyPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(policy(10, 100)));
        when(suggestionPort.hasOpenSuggestion("scm", SKU, WAREHOUSE_ID)).thenReturn(false);
        when(suggestionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 5, 8, Instant.now());

        org.mockito.ArgumentCaptor<ReorderSuggestion> saved =
                org.mockito.ArgumentCaptor.forClass(ReorderSuggestion.class);
        verify(suggestionPort).save(saved.capture());
        // ADR-MONO-050 D9: the warehouse CODE (not the UUID) flows to the PO destination;
        // supplier code carried from the mapping. warehouse_id stays the dedup dimension.
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getWarehouseCode())
                .isEqualTo(WAREHOUSE_CODE);
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getWarehouseId())
                .isEqualTo(WAREHOUSE_ID);
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getSupplierId())
                .isEqualTo(SUPPLIER_ID);
    }

    @Test
    void evaluateFromAlert_skipsDuplicate_viaT8Dedup() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(true);

        useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 5, 8, Instant.now());

        verify(suggestionPort, never()).save(any());
        verify(processedEventPort, never()).markProcessed(any(), any(), any(), any());
    }

    @Test
    void evaluateFromAlert_skipsWhenOpenSuggestionExists_D6Guard() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(false);
        when(mappingPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(mapping()));
        when(policyPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(policy(10, 100)));
        when(suggestionPort.hasOpenSuggestion("scm", SKU, WAREHOUSE_ID)).thenReturn(true);

        useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 5, 8, Instant.now());

        verify(suggestionPort, never()).save(any());
        verify(processedEventPort).markProcessed(any(), any(), any(), any()); // still marks processed
    }

    @Test
    void evaluateFromAlert_throwsSkuUnmapped_failsClosed() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(false);
        when(mappingPort.findBySkuCode("scm", SKU)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 5, 8, Instant.now()))
                .isInstanceOf(SkuSupplierUnmappedException.class);

        verify(opsAlertPort).alertUnmappedSku(eq(SKU), any(), any());
        verify(suggestionPort, never()).save(any());
    }

    @Test
    void evaluateFromAlert_usesFallbackPolicy_whenNoPolicyRow() {
        when(processedEventPort.isDuplicate(EVENT_ID)).thenReturn(false);
        when(mappingPort.findBySkuCode("scm", SKU))
                .thenReturn(Optional.of(mapping()));
        when(policyPort.findBySkuCode("scm", SKU)).thenReturn(Optional.empty());
        when(suggestionPort.hasOpenSuggestion("scm", SKU, WAREHOUSE_ID)).thenReturn(false);
        when(suggestionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // alertThreshold=8, availableQty=5 → 5 <= 8 → should raise (degraded path)
        useCase.evaluateFromAlert(EVENT_ID, SKU, WAREHOUSE_ID, WAREHOUSE_CODE, 5, 8, Instant.now());

        verify(suggestionPort).save(any(ReorderSuggestion.class));
    }

    private static ReorderPolicy policy(int reorderPoint, int reorderQty) {
        return new ReorderPolicy(SKU, reorderPoint, 5, reorderQty, "scm", 0, Instant.now());
    }

    private static SkuSupplierMapping mapping() {
        return new SkuSupplierMapping(SKU, SUPPLIER_ID, 200, 7, "KRW", "scm");
    }
}
