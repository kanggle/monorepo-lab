package com.example.admin.application.console;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.TenantScopeResolver;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.tenant.ListTenantDomainSubscriptionsUseCase;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantDomainSubscriptionSummary;
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

    @Mock
    private ListTenantDomainSubscriptionsUseCase listSubscriptionsUseCase;

    @Mock
    private TenantScopeResolver tenantScopeResolver;

    private ConsoleRegistryUseCase useCase() {
        return new ConsoleRegistryUseCase(
                operatorRepository, listTenantsUseCase, listSubscriptionsUseCase,
                tenantScopeResolver);
    }

    private void stubOperator(String operatorId, String tenantId) {
        stubOperator(operatorId, tenantId, null);
    }

    /**
     * TASK-BE-326: stub ONLY the operator entity lookup (no resolver stub) — for
     * tests that drive the effective scope explicitly via {@link #stubEffectiveScope}.
     */
    private void stubOperatorEntityOnly(String operatorId, String tenantId) {
        AdminOperatorJpaEntity entity = AdminOperatorJpaEntity.create(
                operatorId, operatorId + "@example.com", "x", "Op",
                "ACTIVE", tenantId, Instant.now());
        when(operatorRepository.findByOperatorId(operatorId)).thenReturn(Optional.of(entity));
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
        // TASK-BE-326 NET-ZERO default: no assignments → effective scope = {home tenant}.
        // The factory leaves id null in unit context; match any id + the home tenant.
        stubEffectiveScope(tenantId, tenantId);
    }

    /**
     * TASK-BE-326: stub the dual-read resolver. {@code homeTenant} is the
     * operator's home {@code admin_operators.tenant_id} (the lookup key);
     * {@code effective} are the resolved effective tenantIds (home ∪ assigned).
     */
    private void stubEffectiveScope(String homeTenant, String... effective) {
        when(tenantScopeResolver.resolveEffectiveTenantScope(any(), eq(homeTenant)))
                .thenReturn(java.util.Set.of(effective));
    }

    private void stubTenants(TenantSummary... tenants) {
        when(listTenantsUseCase.execute(eq("ACTIVE"), any(), anyInt(), anyInt()))
                .thenReturn(new TenantPageSummary(List.of(tenants), 0, 100,
                        tenants.length, 1));
    }

    /**
     * TASK-BE-322: the backward-compatible seed (ADR-019 step 1) — each
     * domain-slug tenant subscribes to its own domain. With this seed the
     * subscription-driven binding reproduces the legacy
     * {@code tenantSlug == domain} binding byte-identically; every catalog
     * assertion below therefore holds exactly as before.
     */
    private void stubBackwardCompatSubscriptions() {
        when(listSubscriptionsUseCase.execute()).thenReturn(List.of(
                new TenantDomainSubscriptionSummary("wms", "wms"),
                new TenantDomainSubscriptionSummary("scm", "scm"),
                new TenantDomainSubscriptionSummary("erp", "erp"),
                new TenantDomainSubscriptionSummary("finance", "finance")));
    }

    private void stubSubscriptions(TenantDomainSubscriptionSummary... subs) {
        when(listSubscriptionsUseCase.execute()).thenReturn(List.of(subs));
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
    @DisplayName("catalog: exactly 6 products; erp/finance/ecommerce available=true + tenants=[] when their slugs not registered (TASK-BE-305 / TASK-MONO-240)")
    void catalog_six_products_erp_finance_ecommerce_available_noRegisteredTenants() {
        // Seeds only fan-platform (no erp/finance/ecommerce tenant rows) — the
        // tenant-selection rule returns tenants:[] for those (slug not in
        // activeTenants), but available stays true (AC-1 / TASK-BE-305 + TASK-MONO-240).
        stubOperator("op-1", "*");
        stubTenants(tenant("fan-platform", "ACTIVE"));
        stubBackwardCompatSubscriptions();

        ConsoleRegistry r = useCase().execute(new OperatorContext("op-1", "jti"));

        assertThat(r.products()).extracting(ConsoleProduct::productKey)
                .containsExactly("iam", "wms", "scm", "erp", "finance", "ecommerce");
        assertThat(product(r, "erp").available()).isTrue();
        assertThat(product(r, "erp").tenants()).isEmpty();
        assertThat(product(r, "finance").available()).isTrue();
        assertThat(product(r, "finance").tenants()).isEmpty();
        // TASK-MONO-240: ecommerce is the 6th catalog member, available=true,
        // tenants:[] (no ecommerce subscription in the backward-compat seed).
        assertThat(product(r, "ecommerce").available()).isTrue();
        assertThat(product(r, "ecommerce").tenants()).isEmpty();
        assertThat(product(r, "ecommerce").baseRoute()).isEqualTo("/ecommerce");
        assertThat(product(r, "iam").baseRoute()).isEqualTo("/iam");
    }

    @Test
    @DisplayName("platform-scope operator: gap binds ALL active tenants; wms/scm bind own slug")
    void platformScope_seesAllActiveTenants() {
        stubOperator("super", "*");
        stubTenants(
                tenant("fan-platform", "ACTIVE"),
                tenant("wms", "ACTIVE"),
                tenant("scm", "ACTIVE"));
        stubBackwardCompatSubscriptions();

        ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

        assertThat(product(r, "iam").tenants())
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
        stubBackwardCompatSubscriptions();

        ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

        assertThat(product(r, "iam").tenants()).containsExactly("fan-platform");
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
        stubBackwardCompatSubscriptions();

        ConsoleRegistry r = useCase().execute(new OperatorContext("wms-op", "jti"));

        // gap federates all tenants, but a wms-scoped operator only sees its own.
        assertThat(product(r, "iam").tenants())
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
        stubBackwardCompatSubscriptions();

        ConsoleRegistry r = useCase().execute(new OperatorContext("wms-op", "jti"));

        assertThat(product(r, "iam").tenants()).isEmpty();
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
     * TASK-BE-322 (ADR-MONO-019 D4): the domain product binding is now driven by
     * the account-service subscription read, not the fixed {@code tenantSlug}.
     */
    @Nested
    @DisplayName("TASK-BE-322: subscription-driven domain binding")
    class SubscriptionDrivenBinding {

        @Test
        @DisplayName("net-zero: backward-compat self-subscriptions reproduce the legacy slug binding exactly")
        void backwardCompatSeed_reproducesLegacyBinding() {
            stubOperator("super", "*");
            stubTenants(
                    tenant("fan-platform", "ACTIVE"),
                    tenant("wms", "ACTIVE"),
                    tenant("scm", "ACTIVE"));
            stubBackwardCompatSubscriptions();

            ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

            // identical to the pre-BE-322 binding: each domain product → own slug
            assertThat(product(r, "wms").tenants()).containsExactly("wms");
            assertThat(product(r, "scm").tenants()).containsExactly("scm");
            // gap (bindsAllTenants) is unaffected by the subscription read
            assertThat(product(r, "iam").tenants())
                    .containsExactlyInAnyOrder("fan-platform", "wms", "scm");
        }

        @Test
        @DisplayName("a domain with NO active subscription → tenants:[] even when other slugs are ACTIVE")
        void noSubscription_emptyTenants() {
            stubOperator("super", "*");
            stubTenants(
                    tenant("wms", "ACTIVE"),
                    tenant("scm", "ACTIVE"));
            // only wms self-subscribes; scm has no subscription row.
            stubSubscriptions(new TenantDomainSubscriptionSummary("wms", "wms"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

            assertThat(product(r, "wms").tenants()).containsExactly("wms");
            assertThat(product(r, "scm").tenants())
                    .as("scm has no ACTIVE subscription → empty binding")
                    .isEmpty();
        }

        @Test
        @DisplayName("binding follows the subscription, not the slug: a non-slug tenant subscribed to wms is bound")
        void subscriptionDrivesBinding_notSlug() {
            // 'acme' (a future-style customer tenant) subscribes to the wms domain.
            stubOperator("super", "*");
            stubTenants(
                    tenant("acme", "ACTIVE"),
                    tenant("wms", "ACTIVE"));
            stubSubscriptions(
                    new TenantDomainSubscriptionSummary("acme", "wms"),
                    new TenantDomainSubscriptionSummary("wms", "wms"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

            assertThat(product(r, "wms").tenants())
                    .as("binding = subscriptions(wms) ∩ activeTenants")
                    .containsExactlyInAnyOrder("acme", "wms");
        }

        @Test
        @DisplayName("subscribed tenant not registered/ACTIVE → excluded by the activeTenants intersection")
        void subscribedButNotActive_excluded() {
            stubOperator("super", "*");
            // 'acme' subscribes to wms but is NOT in the active tenant list.
            stubTenants(tenant("wms", "ACTIVE"));
            stubSubscriptions(
                    new TenantDomainSubscriptionSummary("acme", "wms"),
                    new TenantDomainSubscriptionSummary("wms", "wms"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

            assertThat(product(r, "wms").tenants())
                    .as("acme subscribed but not ACTIVE-registered → excluded")
                    .containsExactly("wms");
        }
    }

    /**
     * TASK-BE-325 (ADR-MONO-019 § 3.3 step 2 — net-positive): real customer
     * tenant {@code acme-corp} subscribed to finance + wms appears in those
     * products' selectableTenants for a platform-scope operator. scm and erp
     * must NOT list acme-corp (not subscribed). The {@code gap}
     * {@code bindsAllTenants} branch and operator scope invariants are
     * unchanged — this is an additive assertion on top of the backward-compat
     * subscription seed.
     */
    @Nested
    @DisplayName("TASK-BE-325: real customer tenant acme-corp in catalog (ADR-019 step 2)")
    class RealCustomerTenantAcmeCorp {

        @Test
        @DisplayName("platform-scope operator: acme-corp in finance+wms tenants; absent in scm+erp")
        void acmeCorp_appearsInFinanceAndWms_absentInScmErp() {
            stubOperator("super", "*");
            // All five slug tenants + acme-corp ACTIVE
            stubTenants(
                    tenant("fan-platform", "ACTIVE"),
                    tenant("wms", "ACTIVE"),
                    tenant("scm", "ACTIVE"),
                    tenant("erp", "ACTIVE"),
                    tenant("finance", "ACTIVE"),
                    tenant("acme-corp", "ACTIVE"));
            // Backward-compat self-subs PLUS acme-corp → finance and acme-corp → wms
            stubSubscriptions(
                    new TenantDomainSubscriptionSummary("wms", "wms"),
                    new TenantDomainSubscriptionSummary("scm", "scm"),
                    new TenantDomainSubscriptionSummary("erp", "erp"),
                    new TenantDomainSubscriptionSummary("finance", "finance"),
                    new TenantDomainSubscriptionSummary("acme-corp", "finance"),
                    new TenantDomainSubscriptionSummary("acme-corp", "wms"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

            // finance: acme-corp subscribed → present
            assertThat(product(r, "finance").tenants())
                    .as("finance.tenants must include acme-corp (subscribed via V0020)")
                    .contains("acme-corp");

            // wms: acme-corp subscribed → present
            assertThat(product(r, "wms").tenants())
                    .as("wms.tenants must include acme-corp (subscribed via V0020)")
                    .contains("acme-corp");

            // scm: acme-corp NOT subscribed → absent
            assertThat(product(r, "scm").tenants())
                    .as("scm.tenants must NOT include acme-corp (not subscribed)")
                    .doesNotContain("acme-corp");

            // erp: acme-corp NOT subscribed → absent
            assertThat(product(r, "erp").tenants())
                    .as("erp.tenants must NOT include acme-corp (not subscribed)")
                    .doesNotContain("acme-corp");
        }

        @Test
        @DisplayName("gap bindsAllTenants: acme-corp appears in gap.tenants (ACTIVE, platform-scope)")
        void acmeCorp_appearsInGap_bindsAllTenants() {
            stubOperator("super", "*");
            stubTenants(
                    tenant("wms", "ACTIVE"),
                    tenant("acme-corp", "ACTIVE"));
            stubSubscriptions(
                    new TenantDomainSubscriptionSummary("wms", "wms"),
                    new TenantDomainSubscriptionSummary("acme-corp", "wms"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("super", "jti"));

            // gap federates ALL active tenants regardless of subscriptions
            assertThat(product(r, "iam").tenants())
                    .as("gap.bindsAllTenants federates acme-corp automatically (no gap subscription row needed)")
                    .contains("acme-corp");
        }

        @Test
        @DisplayName("operator scope invariant: single-tenant wms operator does NOT see acme-corp")
        void singleTenantWmsOperator_doesNotSeeAcmeCorp() {
            stubOperator("wms-op", "wms");
            stubTenants(
                    tenant("wms", "ACTIVE"),
                    tenant("acme-corp", "ACTIVE"));
            stubSubscriptions(
                    new TenantDomainSubscriptionSummary("wms", "wms"),
                    new TenantDomainSubscriptionSummary("acme-corp", "finance"),
                    new TenantDomainSubscriptionSummary("acme-corp", "wms"));

            ConsoleRegistry r = useCase().execute(new OperatorContext("wms-op", "jti"));

            // M6 isolation: single-tenant operator never sees another tenant's id
            assertThat(r.products())
                    .as("M6: single-tenant wms operator must not see acme-corp in any product")
                    .allSatisfy(p -> assertThat(p.tenants()).doesNotContain("acme-corp"));
        }
    }

    /**
     * TASK-BE-326 (ADR-MONO-020 D6 step 1): when a single-tenant operator has
     * one or more {@code operator_tenant_assignment} rows, its effective tenant
     * scope is the union {home ∪ assigned}; the catalog's selectableTenants is
     * therefore {@code bound ∩ effectiveTenants}. Net-zero is covered by every
     * other test (no assignment stub → {home tenant}); this nested class proves
     * the union path.
     */
    @Nested
    @DisplayName("TASK-BE-326: assignment-driven effective tenant scope (dual-read)")
    class AssignmentDrivenScope {

        @Test
        @DisplayName("wms operator with an assignment to scm → sees scm in gap+scm products (bound ∩ {wms,scm})")
        void wmsOperator_assignedScm_seesScm() {
            // operator home = wms; effective scope = UNION {wms, scm}
            // (simulating one operator_tenant_assignment row → scm).
            stubOperatorEntityOnly("wms-op", "wms");
            stubEffectiveScope("wms", "wms", "scm");
            stubTenants(
                    tenant("fan-platform", "ACTIVE"),
                    tenant("wms", "ACTIVE"),
                    tenant("scm", "ACTIVE"));
            stubBackwardCompatSubscriptions();

            ConsoleRegistry r = useCase().execute(new OperatorContext("wms-op", "jti"));

            // gap binds all tenants; effective {wms,scm} → gap shows wms + scm (not fan-platform)
            assertThat(product(r, "iam").tenants())
                    .as("effective scope {wms,scm}: gap shows both, never the un-assigned fan-platform")
                    .containsExactlyInAnyOrder("wms", "scm");
            assertThat(product(r, "wms").tenants()).containsExactly("wms");
            // scm product (bound={scm}) ∩ {wms,scm} = {scm} → now visible (was empty pre-assignment)
            assertThat(product(r, "scm").tenants())
                    .as("scm product now selectable via the scm assignment")
                    .containsExactly("scm");
            // fan-platform is neither home nor assigned → never leaked
            assertThat(r.products())
                    .allSatisfy(p -> assertThat(p.tenants()).doesNotContain("fan-platform"));
        }
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
            stubBackwardCompatSubscriptions();

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
            stubBackwardCompatSubscriptions();

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
            stubBackwardCompatSubscriptions();

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
            stubBackwardCompatSubscriptions();

            ConsoleRegistry r = useCase().execute(new OperatorContext("op-4", "jti"));

            assertThat(product(r, "finance").operatorContext())
                    .as("whitespace-only column → operatorContext omitted (no empty defaultAccountId rendered)")
                    .isNull();
        }
    }
}
