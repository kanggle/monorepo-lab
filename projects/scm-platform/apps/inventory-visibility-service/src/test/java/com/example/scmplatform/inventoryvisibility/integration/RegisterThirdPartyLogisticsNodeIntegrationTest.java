package com.example.scmplatform.inventoryvisibility.integration;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.InventoryNodeJpaEntity;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService;
import com.example.scmplatform.inventoryvisibility.application.service.RegisterThirdPartyLogisticsNodeService.RegisterThirdPartyLogisticsNodeResult;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-SCM-BE-046 — real-Postgres idempotency guard for
 * {@link RegisterThirdPartyLogisticsNodeService} against
 * {@code uq_inventory_nodes_tenant_external}: a repeat registration of the same
 * {@code (tenantId, nodeExternalId)} must persist exactly one row and never raise.
 */
@DisplayName("IT: RegisterThirdPartyLogisticsNodeService idempotency against uq_inventory_nodes_tenant_external")
class RegisterThirdPartyLogisticsNodeIntegrationTest extends AbstractInventoryVisibilityIntegrationTest {

    @Autowired
    RegisterThirdPartyLogisticsNodeService registrationService;

    @Test
    @DisplayName("같은 (tenantId, nodeExternalId) 반복 등록 → row 1개, 예외 없음, 기존 노드 반환")
    void repeatRegistration_persistsExactlyOneRow() {
        String externalId = "3pl-it-" + UUID.randomUUID();

        RegisterThirdPartyLogisticsNodeResult first =
                registrationService.register(TENANT_SCM, externalId, "품고 물류센터");
        RegisterThirdPartyLogisticsNodeResult second =
                registrationService.register(TENANT_SCM, externalId, "품고 물류센터");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.node().getId()).isEqualTo(first.node().getId());

        Optional<InventoryNodeJpaEntity> row =
                nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, externalId);
        assertThat(row).isPresent();
        assertThat(row.get().getNodeType())
                .isEqualTo(InventoryNodeJpaEntity.NodeTypeJpa.THIRD_PARTY_LOGISTICS);

        List<InventoryNodeJpaEntity> allWithExternalId = nodeJpa.findAllByTenantId(TENANT_SCM).stream()
                .filter(e -> e.getNodeExternalId().equals(externalId))
                .toList();
        assertThat(allWithExternalId).hasSize(1);
    }

    @Test
    @DisplayName("이미 WMS_WAREHOUSE 로 등록된 externalId 를 3PL 로 재등록 시도 → NodeTypeConflictException, row 안 늘어남")
    void registeringOverAnExistingWarehouseExternalId_throwsConflict() {
        InventoryNodeJpaEntity warehouse = persistNode(TENANT_SCM, "wh-it-" + UUID.randomUUID());

        assertThatThrownBy(() ->
                registrationService.register(TENANT_SCM, warehouse.getNodeExternalId(), "품고 물류센터"))
                .isInstanceOf(NodeTypeConflictException.class);

        Optional<InventoryNodeJpaEntity> row =
                nodeJpa.findByTenantIdAndNodeExternalId(TENANT_SCM, warehouse.getNodeExternalId());
        assertThat(row).isPresent();
        assertThat(row.get().getNodeType())
                .isEqualTo(InventoryNodeJpaEntity.NodeTypeJpa.WMS_WAREHOUSE);
    }
}
