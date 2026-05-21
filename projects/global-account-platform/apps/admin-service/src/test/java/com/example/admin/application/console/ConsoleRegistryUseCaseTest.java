package com.example.admin.application.console;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-296: unit coverage for {@link ConsoleRegistryUseCase} — the product
 * catalog projection + multi-tenant scoping logic.
 *
 * <p>{@code rules/traits/multi-tenant.md} M6: a single-tenant operator must
 * never see another tenant's slug in any product's {@code tenants} array.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ConsoleRegistryUseCaseTest {

    @Mock
    private AdminOperatorJpaRepository operatorRepository;

    @Mock
    private ListTenantsUseCase listTenantsUseCase;

    private ConsoleRegistryUseCase useCase() {
        return new ConsoleRegistryUseCase(operatorRepository, listTenantsUseCase);
    }

    private void stubOperator(String operatorId, String tenantId) {
        stubOperator(operatorId, tenantId, null);
    }

    /**
     * TASK-BE-304: stub helper extended to inject {@code financeDefaultAccountId}.
     * The factory does not expose this column (out-of-scope mutation surface);
     * reflection-set is the test-only path used by other tests in this module
     * (e.g. {@code TokenExchangeIntegrationTest} uses raw JDBC). For unit
     * coverage we set the field directly on the in-memory entity.
     */
    private void stubOperator(String operatorId, String tenantId, String financeDefaultAccountId) {
        AdminOperatorJpaEntity entity = AdminOperatorJpaEntity.create(
                operatorId, operatorId + "@example.com", "x", "Op",
                "ACTIVE", tenantId, Instant.now());
        if (financeDefaultAccountId != null) {
            ReflectionTestUtils.setField(entity, "financeDefaultAccountId", financeDefaultAccountId);
        }
        when(operatorRepository.findByOperatorId(operatorId)).thenReturn(Optional.of(entity));
    }

    private void stubTenants(TenantSummary... tenants) {
        when(listTenantsUseCase.execute(eq("ACTIVE"), any(), anyInt(), anyInt()))
                .thenReturn(new TenantPageSummary(List.of(tenants), 0, 100,
                        tenants.length, 1));
    }

    private static TenantSummary tenant(String id, String status) {
        return new TenantSummary(id, id, "B2B_ENTERPRISE", status,
                Instant.now(), Instant.now());
    }

    private ConsoleProduct product(ConsoleRegistry r, String key) {
        return r.products().stream()
                .filter(p -> p.productKey().equals(key))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("catalog: exactly 5 products; erp/finance available=false + tenants=[]")
    void catalog_five_products_erp_finance_unavailable() {
        stubOperator("op-1", "*");
        stubTenants(tenant("fan-platform", "ACTIVE"));

        ConsoleRegistry r = useCase().execute(new OperatorContext("op-1", "jti"));

        assertThat(r.products()).extracting(ConsoleProduct::productKey)
                .containsExactly("gap", "wms", "scm", "erp", "finance");
        assertThat(product(r, "erp").available()).isFalse();
        assertThat(product(r, "erp").tenants()).isEmpty();
        assertThat(product(r, "finance").available()).isFalse();
        assertThat(product(r, "finance").tenants()).isEmpty();
        assertThat(product(r, "gap").baseRoute()).isEqualTo("/gap");
    }

    @Test
    @DisplayName("platform-scope operator: gap binds ALL active tenants; wms/scm bind own slug")
    void platformScope_seesAllActiveTenants() {
        stubOperator("super", "*");
        stubTenants(
                tenant("fan-platform", "ACTIVE"),
                tenant("wms", "ACTIVE"),
                tenant("scm", "ACTIVE"));

        ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

        assertThat(product(r, "gap").tenants())
                .containsExactlyInAnyOrder("fan-platform", "wms", "scm");
        assertThat(product(r, "wms").tenants()).containsExactly("wms");
        assertThat(product(r, "scm").tenants()).containsExactly("scm");
    }

    @Test
    @DisplayName("SUSPENDED tenant excluded from every product's tenants")
    void suspendedTenant_excluded() {
        stubOperator("super", "*");
        stubTenants(
                tenant("fan-platform", "ACTIVE"),
                tenant("wms", "SUSPENDED"));

        ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

        assertThat(product(r, "gap").tenants()).containsExactly("fan-platform");
        assertThat(product(r, "wms").tenants())
                .as("wms tenant SUSPENDED → not selectable")
                .isEmpty();
    }

    @Test
    @DisplayName("multi-tenant isolation: single-tenant operator never sees other tenants' slugs (M6)")
    void singleTenantOperator_isolation() {
        stubOperator("wms-op", "wms");
        stubTenants(
                tenant("fan-platform", "ACTIVE"),
                tenant("wms", "ACTIVE"),
                tenant("scm", "ACTIVE"));

        ConsoleRegistry r = useCase().execute(new OperatorContext("wms-op", "jti"));

        // gap federates all tenants, but a wms-scoped operator only sees its own.
        assertThat(product(r, "gap").tenants())
                .as("single-tenant operator: gap shows only own tenant, never fan-platform/scm")
                .containsExactly("wms");
        assertThat(product(r, "wms").tenants()).containsExactly("wms");
        // scm product binds to the scm tenant — not visible to a wms operator.
        assertThat(product(r, "scm").tenants())
                .as("scm product not selectable by a wms-scoped operator")
                .isEmpty();
        // No product anywhere may leak another tenant's slug.
        assertThat(r.products())
                .allSatisfy(p -> assertThat(p.tenants())
                        .doesNotContain("fan-platform", "scm"));
    }

    @Test
    @DisplayName("single-tenant operator whose tenant is SUSPENDED → empty tenants everywhere")
    void singleTenantOperator_ownTenantSuspended() {
        stubOperator("wms-op", "wms");
        stubTenants(tenant("wms", "SUSPENDED"));

        ConsoleRegistry r = useCase().execute(new OperatorContext("wms-op", "jti"));

        assertThat(product(r, "gap").tenants()).isEmpty();
        assertThat(product(r, "wms").tenants()).isEmpty();
    }

    @Test
    @DisplayName("operator row missing → OperatorUnauthorizedException (401)")
    void operatorMissing_throws() {
        when(operatorRepository.findByOperatorId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(new OperatorContext("ghost", "jti")))
                .isInstanceOf(OperatorUnauthorizedException.class);
    }

    /**
     * TASK-BE-304: per-product per-operator profile attributes emission rule.
     *
     * <p>Authoritative spec:
     * {@code specs/contracts/http/console-registry-api.md
     * § Per-operator profile attributes (operatorContext)} — finance only in v1,
     * other 4 products always {@code null}; finance emits when
     * {@code admin_operators.finance_default_account_id} is non-null + non-empty
     * after trim.
     */
    @Nested
    @DisplayName("TASK-BE-304: operatorContext emission")
    class OperatorContextEmission {

        private static final String ACCOUNT_UUID =
                "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000";

        @Test
        @DisplayName("(a) financeDefaultAccountId=null → finance.operatorContext == null")
        void nullColumn_financeOperatorContextNull() {
            stubOperator("op-1", "*", null);
            stubTenants(tenant("fan-platform", "ACTIVE"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("op-1", "jti"));

            assertThat(product(r, "finance").operatorContext())
                    .as("AC-2: null column → operatorContext is null (omitted in JSON via @JsonInclude.NON_NULL)")
                    .isNull();
        }

        @Test
        @DisplayName("(b) financeDefaultAccountId=<uuid> → finance.operatorContext.defaultAccountId == <uuid>")
        void setColumn_financeOperatorContextPopulated() {
            stubOperator("op-2", "*", ACCOUNT_UUID);
            stubTenants(tenant("fan-platform", "ACTIVE"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("op-2", "jti"));

            assertThat(product(r, "finance").operatorContext())
                    .as("AC-3: set column → operatorContext populated on finance product item")
                    .isNotNull();
            assertThat(product(r, "finance").operatorContext().defaultAccountId())
                    .isEqualTo(ACCOUNT_UUID);
        }

        @Test
        @DisplayName("(c) regression guard: other 4 products always have operatorContext == null")
        void otherProducts_operatorContextAlwaysNull() {
            // Even when finance has a value set, the leak guard applies to the
            // other 4 product items (gap / wms / scm / erp) — they NEVER
            // receive operatorContext in v1 (AC-3 substring-count-1 invariant).
            stubOperator("op-3", "*", ACCOUNT_UUID);
            stubTenants(tenant("fan-platform", "ACTIVE"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("op-3", "jti"));

            assertThat(r.products())
                    .filteredOn(p -> !"finance".equals(p.productKey()))
                    .as("regression guard: only finance populates operatorContext in v1")
                    .allSatisfy(p -> assertThat(p.operatorContext())
                            .as("product=%s must NOT carry operatorContext (v1 leak guard)", p.productKey())
                            .isNull());
        }

        @Test
        @DisplayName("(d) edge case: empty / whitespace-only value treated as null (StringUtils.hasText)")
        void emptyOrWhitespace_treatedAsNull() {
            // Edge Case "finance_default_account_id set to an empty string /
            // whitespace-only → treated as NULL (StringUtils.hasText)".
            stubOperator("op-4", "*", "   ");
            stubTenants(tenant("fan-platform", "ACTIVE"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("op-4", "jti"));

            assertThat(product(r, "finance").operatorContext())
                    .as("whitespace-only column → operatorContext omitted (no empty defaultAccountId rendered)")
                    .isNull();
        }
    }
}
