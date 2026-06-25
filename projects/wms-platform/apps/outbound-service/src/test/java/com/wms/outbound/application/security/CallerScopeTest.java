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
    void restricted_scopeListQuery_pinsTenantAndEcommerceSource() {
        OrderQueryCommand scoped = CallerScope.restrictedTo("ecommerce")
                .scopeListQuery(emptyQuery());
        assertThat(scoped.tenantId()).isEqualTo("ecommerce");
        assertThat(scoped.source()).isEqualTo(OrderSource.FULFILLMENT_ECOMMERCE.name());
    }

    @Test
    void restricted_scopeListQuery_overridesClientSuppliedSource() {
        OrderQueryCommand withForeignSource = new OrderQueryCommand(
                null, null, null, "MANUAL", null, null, null, null, null, null, 0, 20);
        OrderQueryCommand scoped = CallerScope.restrictedTo("ecommerce")
                .scopeListQuery(withForeignSource);
        assertThat(scoped.source()).isEqualTo(OrderSource.FULFILLMENT_ECOMMERCE.name());
        assertThat(scoped.tenantId()).isEqualTo("ecommerce");
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
