package com.example.scmplatform.procurement.integration;

import com.example.common.page.PageQuery;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
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

    @Test
    @DisplayName("TASK-MONO-677: 공급사 참조는 같은 tenant 안에서 id 로, 없으면 code 로 풀리고 — 다른 tenant 행은 안 풀린다")
    void supplierReferenceResolvesInsideTheTenantOnly() {
        // Arrange — one supplier per tenant. persistActiveSupplier sets code = UPPER(id).
        Supplier own = persistActiveSupplier(TENANT_SCM);
        Supplier foreign = persistActiveSupplier(TENANT_OTHER);
        PurchaseOrder refById = persistDraftPo(TENANT_SCM, own.getId());
        PurchaseOrder refByCode = persistDraftPo(TENANT_SCM, own.getCode());
        PurchaseOrder refForeign = persistDraftPo(TENANT_SCM, foreign.getId());
        ActorContext buyer = new ActorContext("buyer-a-677", TENANT_SCM, Set.of("BUYER"));

        // Detail path.
        PurchaseOrderView viaId = service.get(refById.getId(), buyer);
        assertThat(viaId.supplierCode()).isEqualTo(own.getCode());
        assertThat(viaId.supplierName()).isEqualTo(own.getName());

        PurchaseOrderView viaCode = service.get(refByCode.getId(), buyer);
        assertThat(viaCode.supplierId()).isEqualTo(own.getCode());
        assertThat(viaCode.supplierCode()).isEqualTo(own.getCode());
        assertThat(viaCode.supplierName()).isEqualTo(own.getName());

        PurchaseOrderView viaForeign = service.get(refForeign.getId(), buyer);
        assertThat(viaForeign.supplierCode()).isNull();
        assertThat(viaForeign.supplierName()).isNull();

        // List path (the batched IN-queries) must give the same three answers. Filtering
        // by the stored supplierId keeps each page to exactly this test's PO.
        PageQuery page = PageQuery.of(0, 20, "createdAt", "DESC");
        assertThat(service.search(buyer, null, own.getId(), page).content())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.supplierCode()).isEqualTo(own.getCode());
                    assertThat(v.supplierName()).isEqualTo(own.getName());
                });
        assertThat(service.search(buyer, null, own.getCode(), page).content())
                .singleElement()
                .satisfies(v -> assertThat(v.supplierName()).isEqualTo(own.getName()));
        assertThat(service.search(buyer, null, foreign.getId(), page).content())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.supplierCode()).isNull();
                    assertThat(v.supplierName()).isNull();
                });
    }
}
