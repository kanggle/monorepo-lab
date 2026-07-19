package com.example.scmplatform.procurement.integration;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.DraftFromSuggestionCommand;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT: materialize a reorder suggestion into a DRAFT PO via the additive
 * {@code from-suggestion} entry (ADR-MONO-027 D5 / TASK-SCM-BE-025).
 *
 * <p>Proves the cross-service contract the Docker-free {@code :check} cannot:
 * <ul>
 *   <li>AC-1: DRAFT PO created carrying {@code origin=DEMAND_PLANNING} +
 *       {@code sourceSuggestionId}.</li>
 *   <li>AC-2: re-call with the same {@code sourceSuggestionId} yields no second
 *       PO (idempotent — returns the existing one).</li>
 *   <li>Edge: no supplier FK validation — the supplier is NOT persisted, yet the
 *       DRAFT is still created (FK-free cross-service convention).</li>
 *   <li>AC-4 regression: the operator {@code draft()} path still FK-validates the
 *       supplier (byte-unchanged behaviour).</li>
 * </ul>
 */
@Tag("integration")
@DisplayName("IT: from-suggestion DRAFT-PO materialization (ADR-027 D5)")
class FromSuggestionMaterializationIntegrationTest extends AbstractProcurementIntegrationTest {

    private static final ActorContext OPERATOR =
            new ActorContext("operator-dp-001", TENANT_SCM, Set.of("OPERATOR"));

    @Autowired
    private PurchaseOrderApplicationService service;

    private static final String WAREHOUSE_ID = "0192cccc-0000-0000-0000-000000000002";

    private DraftFromSuggestionCommand commandFor(String suggestionId, String supplierId) {
        return new DraftFromSuggestionCommand(
                OPERATOR,
                supplierId,
                "KRW",
                suggestionId,
                // ADR-MONO-050 addressing carried from demand-planning.
                WAREHOUSE_ID,
                "WMS_WAREHOUSE",
                7,
                List.of(new DraftFromSuggestionCommand.Line(1, "SKU-APPLE-001", 100, "LAST_KNOWN"))
        );
    }

    @Test
    @DisplayName("AC-1 + no-FK: creates DRAFT with provenance even for an unpersisted supplier")
    void createsDraftWithProvenanceAndNoSupplierFk() {
        String suggestionId = UuidV7.randomString();
        // supplierId is a reference that does NOT exist in the suppliers table.
        String unknownSupplierId = UuidV7.randomString();

        PurchaseOrderView view = service.draftFromSuggestion(commandFor(suggestionId, unknownSupplierId));

        assertThat(view.status()).isEqualTo(PoStatus.DRAFT);
        assertThat(view.origin()).isEqualTo(PoOrigin.DEMAND_PLANNING);
        assertThat(view.sourceSuggestionId()).isEqualTo(suggestionId);
        assertThat(view.supplierId()).isEqualTo(unknownSupplierId);
        assertThat(view.lines()).hasSize(1);
        assertThat(view.lines().get(0).sku()).isEqualTo("SKU-APPLE-001");
        assertThat(view.lines().get(0).quantity()).isEqualByComparingTo("100");
        // unitPriceRef is a placeholder → persisted as 0 pending operator review.
        assertThat(view.lines().get(0).unitPrice()).isEqualByComparingTo("0");

        // No supplier row was created — proves the FK-free path.
        assertThat(supplierJpa.findById(unknownSupplierId)).isEmpty();
        // Exactly one PO for this suggestion.
        assertThat(poJpa.findBySourceSuggestionIdAndTenantId(suggestionId, TENANT_SCM)).isPresent();
    }

    @Test
    @DisplayName("AC-2: re-call with same sourceSuggestionId returns the existing PO (idempotent, no duplicate)")
    void idempotentOnSourceSuggestionId() {
        String suggestionId = UuidV7.randomString();
        String supplierId = UuidV7.randomString();

        PurchaseOrderView first = service.draftFromSuggestion(commandFor(suggestionId, supplierId));
        PurchaseOrderView second = service.draftFromSuggestion(commandFor(suggestionId, supplierId));

        assertThat(second.id())
                .as("idempotent re-call returns the same PO id")
                .isEqualTo(first.id());

        long countForSuggestion = poJpa.findAll().stream()
                .filter(po -> suggestionId.equals(po.getSourceSuggestionId()))
                .count();
        assertThat(countForSuggestion)
                .as("exactly one PO exists for the suggestion")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-4 regression: operator draft() still FK-validates the supplier")
    void operatorDraftStillValidatesSupplier() {
        DraftPurchaseOrderCommand cmd = new DraftPurchaseOrderCommand(
                OPERATOR,
                UuidV7.randomString(), // unpersisted supplier
                "KRW",
                List.of(new DraftPurchaseOrderCommand.Line(
                        1, "SKU-001", "SUP-001", new BigDecimal("10"), new BigDecimal("5.00")))
        );

        assertThatThrownBy(() -> service.draft(cmd))
                .isInstanceOf(SupplierNotFoundException.class);
    }
}
