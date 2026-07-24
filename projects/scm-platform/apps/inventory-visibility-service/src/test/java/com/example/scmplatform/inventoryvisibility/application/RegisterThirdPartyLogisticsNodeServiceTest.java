package com.example.scmplatform.inventoryvisibility.application;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-SCM-BE-046 — {@link RegisterThirdPartyLogisticsNodeService} unit coverage:
 * new registration, idempotent repeat, type conflict, blank-input validation, and the
 * concurrent-registration race against {@code uq_inventory_nodes_tenant_external}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RegisterThirdPartyLogisticsNodeServiceTest {

    private static final String TENANT = "scm";
    private static final String EXTERNAL_ID = "3PL-EXT-1";
    private static final String NAME = "품고 물류센터";

    @Mock InventoryNodeRepository nodeRepository;
    @Mock ClockPort clock;

    RegisterThirdPartyLogisticsNodeService service;

    private final Instant now = Instant.parse("2026-07-24T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new RegisterThirdPartyLogisticsNodeService(nodeRepository, clock);
    }

    @Test
    void registersNewThirdPartyLogisticsNodeWhenAbsent() {
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.empty());
        when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterThirdPartyLogisticsNodeResult result = service.register(TENANT, EXTERNAL_ID, NAME);

        assertThat(result.created()).isTrue();
        assertThat(result.node().getNodeType()).isEqualTo(NodeType.THIRD_PARTY_LOGISTICS);
        assertThat(result.node().getName()).isEqualTo(NAME);
        assertThat(result.node().getNodeExternalId()).isEqualTo(EXTERNAL_ID);

        ArgumentCaptor<InventoryNode> captor = ArgumentCaptor.forClass(InventoryNode.class);
        verify(nodeRepository).save(captor.capture());
        assertThat(captor.getValue().getWarehouseCode()).isNull();
    }

    @Test
    void repeatRegistration_sameType_isIdempotentNoOp() {
        InventoryNode existing = InventoryNode.registerThirdPartyLogistics(
                NodeId.of(UUID.randomUUID()), TENANT, EXTERNAL_ID, NAME, now);
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.of(existing));

        RegisterThirdPartyLogisticsNodeResult result = service.register(TENANT, EXTERNAL_ID, NAME);

        assertThat(result.created()).isFalse();
        assertThat(result.node()).isSameAs(existing);
        verify(nodeRepository, never()).save(any());
    }

    @Test
    void repeatRegistration_differentType_throwsConflict() {
        InventoryNode existingWarehouse = InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), TENANT, EXTERNAL_ID, "WH01", now);
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.of(existingWarehouse));

        assertThatThrownBy(() -> service.register(TENANT, EXTERNAL_ID, NAME))
                .isInstanceOf(NodeTypeConflictException.class);
        verify(nodeRepository, never()).save(any());
    }

    @Test
    void blankNodeExternalId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.register(TENANT, "  ", NAME))
                .isInstanceOf(IllegalArgumentException.class);
        verify(nodeRepository, never()).findByTenantIdAndExternalId(any(), any());
    }

    @Test
    void nullNodeExternalId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.register(TENANT, null, NAME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.register(TENANT, EXTERNAL_ID, " "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(nodeRepository, never()).findByTenantIdAndExternalId(any(), any());
    }

    @Test
    void nullName_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.register(TENANT, EXTERNAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Edge Case: concurrent-registration race backed by {@code uq_inventory_nodes_tenant_external}.
     * The losing writer's {@code save()} raises {@link DataIntegrityViolationException}; the
     * service must re-read and return the existing (same-type) node rather than propagating a 500.
     */
    @Test
    void concurrentRegistrationRace_findsExistingAfterUniqueViolation() {
        when(clock.now()).thenReturn(now);
        InventoryNode winner = InventoryNode.registerThirdPartyLogistics(
                NodeId.of(UUID.randomUUID()), TENANT, EXTERNAL_ID, NAME, now);

        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(nodeRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_inventory_nodes_tenant_external"));

        RegisterThirdPartyLogisticsNodeResult result = service.register(TENANT, EXTERNAL_ID, NAME);

        assertThat(result.created()).isFalse();
        assertThat(result.node()).isSameAs(winner);
        verify(nodeRepository, times(2)).findByTenantIdAndExternalId(TENANT, EXTERNAL_ID);
    }

    /**
     * If the re-read after a unique violation somehow comes back empty (should not happen in
     * practice), the original exception must not be swallowed silently.
     */
    @Test
    void concurrentRegistrationRace_reReadEmpty_rethrowsOriginalException() {
        when(clock.now()).thenReturn(now);
        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.empty());
        DataIntegrityViolationException dive =
                new DataIntegrityViolationException("uq_inventory_nodes_tenant_external");
        when(nodeRepository.save(any())).thenThrow(dive);

        assertThatThrownBy(() -> service.register(TENANT, EXTERNAL_ID, NAME))
                .isSameAs(dive);
    }

    /**
     * A race where the winner registered a *different* type is still a genuine conflict,
     * not laundered into a false idempotent success.
     */
    @Test
    void concurrentRegistrationRace_winnerDifferentType_throwsConflict() {
        when(clock.now()).thenReturn(now);
        InventoryNode winnerWarehouse = InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), TENANT, EXTERNAL_ID, "WH01", now);

        when(nodeRepository.findByTenantIdAndExternalId(TENANT, EXTERNAL_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerWarehouse));
        when(nodeRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_inventory_nodes_tenant_external"));

        assertThatThrownBy(() -> service.register(TENANT, EXTERNAL_ID, NAME))
                .isInstanceOf(NodeTypeConflictException.class);
    }
}
