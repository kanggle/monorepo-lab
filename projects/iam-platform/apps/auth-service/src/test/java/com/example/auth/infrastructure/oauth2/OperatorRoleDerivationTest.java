package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-376 (ADR-MONO-035 O1 / step 4a): pure unit tests for
 * {@link OperatorRoleDerivation} — the operator-role mirror of {@code RoleSeedPolicy},
 * keyed on the selected tenant's entitled domains.
 */
@DisplayName("OperatorRoleDerivation 단위 테스트 (TASK-BE-376, 엔타이틀먼트→운영자 롤 파생)")
class OperatorRoleDerivationTest {

    @Test
    @DisplayName("known single domains map to their operator role")
    void knownDomains_mapToOperatorRole() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms")))
                .containsExactly("WMS_OPERATOR");
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
    @DisplayName("multiple domains → union of operator roles, stable input order")
    void multipleDomains_unionStableOrder() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("finance", "wms")))
                .containsExactly("FINANCE_OPERATOR", "WMS_OPERATOR");
        // order follows the input order, not the table order.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms", "finance")))
                .containsExactly("WMS_OPERATOR", "FINANCE_OPERATOR");
    }

    @Test
    @DisplayName("duplicate domains and fan/fan-platform aliasing → de-duplicated")
    void duplicates_deDuplicated() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("wms", "wms")))
                .containsExactly("WMS_OPERATOR");
        // fan and fan-platform both map to FAN_OPERATOR → single entry.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("fan", "fan-platform")))
                .containsExactly("FAN_OPERATOR");
    }

    @Test
    @DisplayName("gap and unknown domains are skipped (no operator role)")
    void gapAndUnknown_skipped() {
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("gap"))).isEmpty();
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("bogus-domain"))).isEmpty();
        // mixed: known + gap + unknown → only the known role survives.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("gap", "wms", "bogus")))
                .containsExactly("WMS_OPERATOR");
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
                .containsExactly("WMS_OPERATOR", "FINANCE_OPERATOR");
        // surrounding whitespace is trimmed before the lookup.
        assertThat(OperatorRoleDerivation.fromEntitledDomains(List.of("  wms  ")))
                .containsExactly("WMS_OPERATOR");
    }

    @Test
    @DisplayName("result is immutable")
    void resultIsImmutable() {
        List<String> roles = OperatorRoleDerivation.fromEntitledDomains(List.of("wms"));
        assertThatThrownBy(() -> roles.add("INJECTED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
