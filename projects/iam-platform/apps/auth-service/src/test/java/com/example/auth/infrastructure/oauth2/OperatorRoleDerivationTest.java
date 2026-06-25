package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-376 (ADR-MONO-035 O1 / step 4a): pure unit tests for
 * {@link OperatorRoleDerivation} — the operator-role mirror of {@code RoleSeedPolicy},
 * keyed on the selected tenant's entitled domains.
 *
 * <p>TASK-BE-433: the {@code wms} entitlement now derives the granular wms-service
 * operator-tier roles (so outbound/inbound/inventory admit the operator), not just the
 * coarse {@code WMS_OPERATOR}.
 */
@DisplayName("OperatorRoleDerivation 단위 테스트 (TASK-BE-376/433, 엔타이틀먼트→운영자 롤 파생)")
class OperatorRoleDerivationTest {

    /** Expected ordered wms operator-tier role set (TASK-BE-433). */
    private static final List<String> WMS = List.of(
            "WMS_OPERATOR",
            "OUTBOUND_READ", "OUTBOUND_WRITE",
            "INBOUND_READ", "INBOUND_WRITE",
            "INVENTORY_READ", "INVENTORY_WRITE",
            "MASTER_READ");

    /** wms roles followed by {@code extra} (input domain order: wms first). */
    private static List<String> wmsThen(String... extra) {
        List<String> out = new ArrayList<>(WMS);
        out.addAll(Arrays.asList(extra));
        return out;
    }

    /** {@code pre} followed by the wms roles (input domain order: wms last). */
    private static List<String> thenWms(String... pre) {
        List<String> out = new ArrayList<>(Arrays.asList(pre));
        out.addAll(WMS);
        return out;
    }

    @Test
    @DisplayName("wms → granular operator-tier role set; other domains → their single operator role")
    void knownDomains_mapToOperatorRole() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms")))
                .containsExactlyElementsOf(WMS);
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("ecommerce")))
                .containsExactly("ADMIN");
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("scm")))
                .containsExactly("SCM_OPERATOR");
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("erp")))
                .containsExactly("ERP_OPERATOR");
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("finance")))
                .containsExactly("FINANCE_OPERATOR");
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("mes")))
                .containsExactly("MES_OPERATOR");
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("fan")))
                .containsExactly("FAN_OPERATOR");
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("fan-platform")))
                .containsExactly("FAN_OPERATOR");
    }

    @Test
    @DisplayName("wms grants exactly the operator tier — no *_ADMIN / WMS_ADMIN")
    void wms_excludesAdminTier() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms")))
                .doesNotContain("WMS_ADMIN", "OUTBOUND_ADMIN", "INBOUND_ADMIN",
                        "INVENTORY_ADMIN", "MASTER_ADMIN", "MASTER_WRITE");
    }

    @Test
    @DisplayName("multiple domains → union of operator roles, stable input order")
    void multipleDomains_unionStableOrder() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("finance", "wms")))
                .containsExactlyElementsOf(thenWms("FINANCE_OPERATOR"));
        // order follows the input order, not the table order.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms", "finance")))
                .containsExactlyElementsOf(wmsThen("FINANCE_OPERATOR"));
    }

    @Test
    @DisplayName("duplicate domains and fan/fan-platform aliasing → de-duplicated")
    void duplicates_deDuplicated() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms", "wms")))
                .containsExactlyElementsOf(WMS);
        // fan and fan-platform both map to FAN_OPERATOR → single entry.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("fan", "fan-platform")))
                .containsExactly("FAN_OPERATOR");
    }

    @Test
    @DisplayName("gap and unknown domains are skipped (no operator role)")
    void gapAndUnknown_skipped() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("gap"))).isEmpty();
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("bogus-domain"))).isEmpty();
        // mixed: known + gap + unknown → only the known role(s) survive.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("gap", "wms", "bogus")))
                .containsExactlyElementsOf(WMS);
    }

    @Test
    @DisplayName("null / empty / all-unknown input → immutable empty list (never null)")
    void nullEmptyAllUnknown_emptyList() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(null)).isEmpty();
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of())).isEmpty();
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("gap", "unknown"))).isEmpty();
    }

    @Test
    @DisplayName("null and blank domain keys are skipped/trimmed")
    void nullAndBlankKeys_skippedTrimmed() {
        // Arrays.asList tolerates a null element (List.of does not).
        assertThat(OperatorRoleDerivation.fromEntitledDomains(Arrays.asList("wms", null, "  ", "finance")))
                .containsExactlyElementsOf(wmsThen("FINANCE_OPERATOR"));
        // surrounding whitespace is trimmed before the lookup.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("  wms  ")))
                .containsExactlyElementsOf(WMS);
    }

    @Test
    @DisplayName("result is immutable")
    void resultIsImmutable() {
        List<String> roles = OperatorRoleDerivation.fromEntitledDomains(List.of("wms"));
        assertThatThrownBy(() -> roles.add("INJECTED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
