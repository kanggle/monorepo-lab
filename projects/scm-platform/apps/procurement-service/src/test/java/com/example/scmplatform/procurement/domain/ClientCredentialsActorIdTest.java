package com.example.scmplatform.procurement.domain;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-SCM-BE-050 — the actor-identity fields carry the raw IAM {@code sub},
 * which for scm's client-credentials caller is {@code client_id ==
 * "scm-platform-internal-services-client"} (37 chars, one over the old
 * {@code VARCHAR(36)} limit). These pure-domain assertions pin that the domain
 * constructors/factories store the value <em>verbatim</em> — no truncation,
 * hashing, or normalisation (Failure Scenario A). The DB-side round-trip through
 * the widened columns is proven by
 * {@code ClientCredentialsActorOverflowIntegrationTest}.
 */
class ClientCredentialsActorIdTest {

    /** scm's documented machine caller subject — 37 chars. */
    private static final String CLIENT_CREDENTIALS_SUB = "scm-platform-internal-services-client";

    @Test
    @DisplayName("createDraft stores the 37-char client-credentials sub verbatim")
    void createDraftKeepsFullBuyerAccountId() {
        assertThat(CLIENT_CREDENTIALS_SUB).hasSize(37);

        PurchaseOrder po = PurchaseOrder.createDraft(
                UuidV7.randomString(), "scm", "PO-TEST-0001",
                UuidV7.randomString(), CLIENT_CREDENTIALS_SUB, "KRW");

        assertThat(po.getBuyerAccountId())
                .as("buyer_account_id must round-trip the full sub, not a truncated 36-char prefix")
                .isEqualTo(CLIENT_CREDENTIALS_SUB);
    }

    @Test
    @DisplayName("createDraftFromSuggestion (3PL destination) stores the 37-char sub verbatim")
    void createDraftFromSuggestionKeepsFullBuyerAccountId() {
        PurchaseOrder po = PurchaseOrder.createDraftFromSuggestion(
                UuidV7.randomString(), "scm", "PO-TEST-0002",
                UuidV7.randomString(), CLIENT_CREDENTIALS_SUB, "KRW",
                UuidV7.randomString(),                       // sourceSuggestionId
                UuidV7.randomString(),                       // destinationWarehouseId (3PL node id)
                PurchaseOrder.NODE_TYPE_THIRD_PARTY_LOGISTICS,
                7);

        assertThat(po.getBuyerAccountId()).isEqualTo(CLIENT_CREDENTIALS_SUB);
        assertThat(po.isThirdPartyLogisticsDestination()).isTrue();
    }

    @Test
    @DisplayName("PoStatusHistory.record keeps the full actor id (sibling column)")
    void poStatusHistoryKeepsFullActorId() {
        PoStatusHistory h = PoStatusHistory.record(
                UuidV7.randomString(), "scm",
                PoStatus.DRAFT, PoStatus.CANCELED,
                ActorType.BUYER, CLIENT_CREDENTIALS_SUB, "cancelled by machine caller");

        assertThat(h.getActorAccountId()).isEqualTo(CLIENT_CREDENTIALS_SUB);
    }

    @Test
    @DisplayName("AuditLog.of keeps the full actor id (sibling column)")
    void auditLogKeepsFullActorId() {
        AuditLog row = AuditLog.of(
                "scm", "purchase_order", UuidV7.randomString(), "DRAFT",
                CLIENT_CREDENTIALS_SUB, ActorType.BUYER, null, null);

        assertThat(row.getActorAccountId()).isEqualTo(CLIENT_CREDENTIALS_SUB);
    }
}
