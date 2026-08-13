package com.wms.outbound.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.domain.exception.TenantScopeDeniedException;
import com.wms.outbound.domain.model.OrderSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the cross-tenant {@link CallerScope} value object (TASK-MONO-304). */
class CallerScopeTest {

    private static final UUID ORDER_ID = UUID.randomUUID();

    private static OrderQueryCommand emptyQuery() {
        return new OrderQueryCommand(null, null, null, null, null, null,
                null, null, null, null, 0, 20);
    }

    @Test
    void unrestricted_scopeListQuery_returnsCommandUnchanged() {
        OrderQueryCommand cmd = emptyQuery();
        assertThat(CallerScope.unrestricted().scopeListQuery(cmd)).isSameAs(cmd);
    }

    @Test
    void restricted_scopeListQuery_pinsTenant() {
        OrderQueryCommand scoped = CallerScope.restrictedTo("ecommerce")
                .scopeListQuery(emptyQuery());
        assertThat(scoped.tenantId()).isEqualTo("ecommerce");
    }

    /**
     * ADR-MONO-064 § D2 — the inverse of {@code restricted_scopeListQuery_overridesClientSuppliedSource},
     * which asserted the {@code source} override this decision removed.
     *
     * <p>The override was redundant while {@code tenant_id} was populated on the
     * fulfilment path only (pinning the tenant already implied the source), and became
     * harmful once § D1 started stamping the caller's tenant onto its own
     * {@code MANUAL} orders: it filtered out exactly the rows the operator had just
     * created. Isolation is carried by {@code tenant_id} alone — see
     * {@link #restricted_scopeListQuery_stillPinsTenant_whenClientAsksForAnotherSource}
     * for the half that must not move.
     */
    @Test
    void restricted_scopeListQuery_keepsClientSuppliedSource() {
        OrderQueryCommand withManualSource = new OrderQueryCommand(
                null, null, null, "MANUAL", null, null, null, null, null, null, 0, 20);
        OrderQueryCommand scoped = CallerScope.restrictedTo("demo-corp")
                .scopeListQuery(withManualSource);
        assertThat(scoped.source())
                .as("§ D2: the client's source filter is honoured, not overridden")
                .isEqualTo("MANUAL");
        assertThat(scoped.tenantId()).isEqualTo("demo-corp");
    }

    /**
     * AC-4 (isolation must not weaken). § D2 widened what a caller sees <em>within</em>
     * its tenant; it must not have loosened the tenant pin itself. A client that names
     * someone else's source — or, below, someone else's tenant — still cannot escape
     * its own {@code tenant_id}.
     */
    @Test
    void restricted_scopeListQuery_stillPinsTenant_whenClientAsksForAnotherSource() {
        OrderQueryCommand clientSupplied = new OrderQueryCommand(
                null, null, null, OrderSource.FULFILLMENT_ECOMMERCE.name(), null,
                "acme-corp" /* a client-supplied tenant filter is NOT trusted */,
                null, null, null, null, 0, 20);
        OrderQueryCommand scoped = CallerScope.restrictedTo("demo-corp")
                .scopeListQuery(clientSupplied);
        assertThat(scoped.tenantId())
                .as("the signed claim overwrites any client-supplied tenant filter")
                .isEqualTo("demo-corp");
    }

    @Test
    void unrestricted_requireOrderAccess_neverThrows() {
        assertThatCode(() -> CallerScope.unrestricted().requireOrderAccess(null, ORDER_ID))
                .doesNotThrowAnyException();
        assertThatCode(() -> CallerScope.unrestricted().requireOrderAccess("anything", ORDER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void restricted_requireOrderAccess_allowsOwnTenant() {
        assertThatCode(() -> CallerScope.restrictedTo("ecommerce")
                .requireOrderAccess("ecommerce", ORDER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void restricted_requireOrderAccess_deniesForeignTenant() {
        assertThatThrownBy(() -> CallerScope.restrictedTo("ecommerce")
                .requireOrderAccess("acme-corp", ORDER_ID))
                .isInstanceOf(TenantScopeDeniedException.class);
    }

    @Test
    void restricted_requireOrderAccess_deniesNullTenant() {
        // B2B / standalone orders (tenantId == null) are never visible to a
        // tenant-scoped caller.
        assertThatThrownBy(() -> CallerScope.restrictedTo("ecommerce")
                .requireOrderAccess(null, ORDER_ID))
                .isInstanceOf(TenantScopeDeniedException.class);
    }
}
