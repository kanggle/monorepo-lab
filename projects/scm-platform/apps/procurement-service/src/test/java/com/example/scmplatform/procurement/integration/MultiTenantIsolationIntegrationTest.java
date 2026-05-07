package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-1: Multi-tenant isolation.
 *
 * <p>A Purchase Order created under tenant A must be invisible to an actor
 * whose JWT carries tenant B. The repository layer always scopes reads by
 * tenant_id, so a cross-tenant lookup surfaces as {@link PoNotFoundException}
 * (Edge Case #5 in the task spec — information hiding from cross-tenant
 * actors).
 */
@Tag("integration")
@DisplayName("IT-1: Multi-tenant PO isolation")
class MultiTenantIsolationIntegrationTest extends AbstractProcurementIntegrationTest {

    @Autowired
    private PurchaseOrderApplicationService service;

    @Test
    @DisplayName("tenant A PO를 tenant B actor로 조회하면 PoNotFoundException 발생 (404 의미)")
    void crossTenantReadReturnsNotFound() {
        // Arrange — create data in TENANT_SCM ("scm")
        Supplier supplierA = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder poA = persistDraftPo(TENANT_SCM, supplierA.getId());

        // Act & Assert — actor belongs to TENANT_OTHER, but requests PO from TENANT_SCM
        ActorContext tenantBBuyer = new ActorContext("buyer-b-001", TENANT_OTHER, Set.of("BUYER"));
        assertThatThrownBy(() -> service.get(poA.getId(), tenantBBuyer))
                .isInstanceOf(PoNotFoundException.class)
                .hasMessageContaining(poA.getId());
    }

    @Test
    @DisplayName("같은 tenant actor는 자신의 PO를 정상 조회한다")
    void sameTenantReadSucceeds() {
        // Arrange
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());

        // Act
        ActorContext tenantABuyer = new ActorContext("buyer-a-001", TENANT_SCM, Set.of("BUYER"));
        var view = service.get(po.getId(), tenantABuyer);

        // Assert
        assertThat(view.id()).isEqualTo(po.getId());
        assertThat(view.tenantId()).isEqualTo(TENANT_SCM);
    }
}
