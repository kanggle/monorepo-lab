package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.application.command.RegisterSupplierCommand;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.audit.AuditLogRepository;
import com.example.scmplatform.procurement.domain.error.PermissionDeniedException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SupplierApplicationService} (TASK-SCM-BE-059).
 *
 * <p>Test count: 7
 */
@ExtendWith(MockitoExtension.class)
class SupplierApplicationServiceTest {

    private static final String TENANT = "scm";
    private static final String CODE = "SUP-ACME-001";

    private static final ActorContext OPERATOR =
            new ActorContext("operator-001", TENANT, Set.of("OPERATOR"));
    private static final ActorContext SCM_OPERATOR =
            new ActorContext("operator-002", TENANT, Set.of("SCM_OPERATOR"));
    private static final ActorContext BUYER =
            new ActorContext("buyer-001", TENANT, Set.of("BUYER"));

    @Mock
    SupplierRepository supplierRepository;

    @Mock
    AuditLogRepository auditLogRepository;

    @InjectMocks
    SupplierApplicationService service;

    private RegisterSupplierCommand command(ActorContext actor) {
        return new RegisterSupplierCommand(actor, CODE, "ACME Components Co.", null, null);
    }

    @Test
    @DisplayName("register() creates an ACTIVE supplier for an OPERATOR actor")
    void registerCreatesActiveSupplier() {
        when(supplierRepository.findByCode(CODE, TENANT)).thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierRegistration result = service.register(command(OPERATOR));

        assertThat(result.created()).isTrue();
        assertThat(result.supplier().code()).isEqualTo(CODE);
        assertThat(result.supplier().status()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(result.supplier().tenantId()).isEqualTo(TENANT);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("register() accepts the assume-tenant SCM_OPERATOR role, not only the generic one")
    void registerAcceptsScmOperatorRole() {
        // The demo/console operator never carries a bare "OPERATOR" role — iam's
        // token exchange mints SCM_OPERATOR from the tenant's scm entitlement.
        // If this gate only accepted "OPERATOR", the endpoint would be
        // unreachable for the only operator identity that exists.
        when(supplierRepository.findByCode(CODE, TENANT)).thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.register(command(SCM_OPERATOR)).created()).isTrue();
    }

    @Test
    @DisplayName("register() refuses a BUYER actor with PERMISSION_DENIED and writes nothing")
    void registerDeniesBuyer() {
        assertThatThrownBy(() -> service.register(command(BUYER)))
                .isInstanceOf(PermissionDeniedException.class);

        verify(supplierRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() converges on an existing code — created=false, no second row")
    void registerConvergesOnExistingCode() {
        Supplier existing = Supplier.create(
                "sup-existing", TENANT, CODE, "ACME Components Co.", SupplierStatus.ACTIVE);
        when(supplierRepository.findByCode(CODE, TENANT)).thenReturn(Optional.of(existing));

        SupplierRegistration result = service.register(command(OPERATOR));

        assertThat(result.created())
                .as("a caller that lost its Idempotency-Key must converge, not duplicate")
                .isFalse();
        assertThat(result.supplier().id()).isEqualTo("sup-existing");
        // The row count is the assertion that matters — a 2xx alone proves nothing.
        verify(supplierRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() rejects a contract window that ends before it starts")
    void registerRejectsInvertedContractWindow() {
        when(supplierRepository.findByCode(CODE, TENANT)).thenReturn(Optional.empty());
        Instant start = Instant.parse("2027-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> service.register(
                new RegisterSupplierCommand(OPERATOR, CODE, "ACME", start, end)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("get() throws SUPPLIER_NOT_FOUND for another tenant's row")
    void getIsTenantScoped() {
        when(supplierRepository.findById("sup-001", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("sup-001", OPERATOR))
                .isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    @DisplayName("register() stores no credential — the v1 master has no path that accepts one")
    void registerStoresNoCredential() {
        when(supplierRepository.findByCode(CODE, TENANT)).thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierRegistration result = service.register(command(OPERATOR));

        // Structural, not value-based: the component list is pinned against the
        // CONTRACT's supplier shape (procurement-api.md § POST
        // /api/procurement/suppliers), not against whatever the code happens to
        // emit today — a snapshot of current output could not catch a field
        // that was never there. Adding a credential component fails this
        // assertion, which is the point of the ADR-SCM-001 rider.
        assertThat(SupplierView.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "tenantId", "code", "name", "status",
                        "contractStartedAt", "contractExpiresAt", "createdAt", "updatedAt");
        assertThat(result.supplier()).isNotNull();
    }
}
