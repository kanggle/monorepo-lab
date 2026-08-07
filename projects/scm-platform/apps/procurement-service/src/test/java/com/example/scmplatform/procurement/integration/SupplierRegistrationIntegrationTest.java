package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.SupplierApplicationService;
import com.example.scmplatform.procurement.application.SupplierRegistration;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.RegisterSupplierCommand;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplier registration against a real Postgres (TASK-SCM-BE-059 AC-2 / AC-3).
 *
 * <p>The idempotency claim is judged by <b>row count</b>, never by the status
 * line or a log label: a converge answers 2xx exactly like a creation does,
 * which is how {@code seed-scm.sh} once reported a replay as a fresh insert.
 *
 * <p>Also covers the thing the whole ticket exists for — a supplier registered
 * through the API is immediately usable as {@code POST /po}'s {@code supplierId},
 * which is what {@code SUPPLIER_NOT_FOUND} previously blocked with no API-level
 * way out.
 */
@Tag("integration")
@DisplayName("Supplier registration: create, converge on natural key, usable for a PO")
class SupplierRegistrationIntegrationTest extends AbstractProcurementIntegrationTest {

    @Autowired
    private SupplierApplicationService supplierService;

    @Autowired
    private PurchaseOrderApplicationService poService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private ActorContext operator() {
        return new ActorContext("operator-it-001", TENANT_SCM, Set.of("SCM_OPERATOR"));
    }

    private String uniqueCode() {
        return "SUP-IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    @DisplayName("two registrations with the same code leave exactly one row")
    void registerIsIdempotentOnCode() {
        String code = uniqueCode();
        ActorContext actor = operator();

        SupplierRegistration first = supplierService.register(
                new RegisterSupplierCommand(actor, code, "ACME IT", null, null));
        SupplierRegistration second = supplierService.register(
                new RegisterSupplierCommand(actor, code, "ACME IT", null, null));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.supplier().id())
                .as("the converge path returns the SAME row, not a new one")
                .isEqualTo(first.supplier().id());

        // The measurement. Both calls answered 2xx; only this distinguishes
        // "created twice" from "created once and converged".
        long rows = supplierJpa.findAll().stream()
                .filter(s -> TENANT_SCM.equals(s.getTenantId()) && code.equals(s.getCode()))
                .count();
        assertThat(rows).as("row count for (tenant, code)").isEqualTo(1);
    }

    @Test
    @DisplayName("the same code in a different tenant is a different supplier")
    void codeIsUniquePerTenantNotGlobally() {
        String code = uniqueCode();

        SupplierRegistration inScm = supplierService.register(new RegisterSupplierCommand(
                new ActorContext("op-1", TENANT_SCM, Set.of("SCM_OPERATOR")),
                code, "ACME IT", null, null));
        SupplierRegistration inOther = supplierService.register(new RegisterSupplierCommand(
                new ActorContext("op-2", TENANT_OTHER, Set.of("SCM_OPERATOR")),
                code, "ACME IT", null, null));

        assertThat(inScm.created()).isTrue();
        assertThat(inOther.created())
                .as("the UNIQUE is (tenant_id, code) — another tenant is not a duplicate")
                .isTrue();
        assertThat(inOther.supplier().id()).isNotEqualTo(inScm.supplier().id());
    }

    @Test
    @DisplayName("a supplier registered through the API resolves for POST /po")
    void registeredSupplierIsUsableForPoDraft() {
        ActorContext actor = operator();
        SupplierRegistration registered = supplierService.register(
                new RegisterSupplierCommand(actor, uniqueCode(), "ACME IT", null, null));

        PurchaseOrderView po = poService.draft(new DraftPurchaseOrderCommand(
                actor,
                registered.supplier().id(),
                "USD",
                List.of(new DraftPurchaseOrderCommand.Line(
                        1, "sku-it-supplier", "sup-sku", new BigDecimal("10"),
                        new BigDecimal("5.00")))));

        assertThat(po.supplierId()).isEqualTo(registered.supplier().id());
    }

    @Test
    @DisplayName("registration writes one audit_log row and no credential row")
    void registrationIsAuditedAndCredentialLess() {
        SupplierRegistration registered = supplierService.register(
                new RegisterSupplierCommand(operator(), uniqueCode(), "ACME IT", null, null));

        long auditRows = auditLogJpa.findAll().stream()
                .filter(a -> "supplier".equals(a.getAggregateType())
                        && registered.supplier().id().equals(a.getAggregateId()))
                .count();
        assertThat(auditRows).isEqualTo(1);

        Supplier stored = supplierJpa.findById(registered.supplier().id()).orElseThrow();
        assertThat(stored.getCode()).isEqualTo(registered.supplier().code());

        // Measure the credential table itself rather than infer from the entity:
        // `supplier_credentials` had no writer anywhere in this service (measured
        // 2026-08-08), and registration must not become its first one. Reading
        // the row count is the only thing that actually says so.
        Integer credentialRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier_credentials WHERE supplier_id = ?",
                Integer.class, registered.supplier().id());
        assertThat(credentialRows)
                .as("v1 accepts supplier credentials on no path (ADR-SCM-001 rider)")
                .isZero();
    }
}
