package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoleSeedPolicy} — the ADR-MONO-033 S3 aud-default seed table
 * (TASK-BE-369). Each seed-table cell + null-safety + unknown platform → {@code []}.
 */
class RoleSeedPolicyTest {

    // -----------------------------------------------------------------------
    // CONSUMER surface
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ecommerce + CONSUMER → [CUSTOMER]")
    void ecommerceConsumer() {
        assertThat(RoleSeedPolicy.seed("ecommerce", "CONSUMER")).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("fan-platform + CONSUMER → [FAN]")
    void fanConsumer() {
        assertThat(RoleSeedPolicy.seed("fan-platform", "CONSUMER")).containsExactly("FAN");
    }

    @Test
    @DisplayName("wms + CONSUMER → [] (no consumer surface on wms)")
    void wmsConsumer_empty() {
        assertThat(RoleSeedPolicy.seed("wms", "CONSUMER")).isEmpty();
    }

    @Test
    @DisplayName("scm + CONSUMER → []")
    void scmConsumer_empty() {
        assertThat(RoleSeedPolicy.seed("scm", "CONSUMER")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // OPERATOR surface
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ecommerce + OPERATOR → [ADMIN]")
    void ecommerceOperator() {
        assertThat(RoleSeedPolicy.seed("ecommerce", "OPERATOR")).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("wms + OPERATOR → [WMS_OPERATOR]")
    void wmsOperator() {
        assertThat(RoleSeedPolicy.seed("wms", "OPERATOR")).containsExactly("WMS_OPERATOR");
    }

    @Test
    @DisplayName("scm + OPERATOR → [SCM_OPERATOR]")
    void scmOperator() {
        assertThat(RoleSeedPolicy.seed("scm", "OPERATOR")).containsExactly("SCM_OPERATOR");
    }

    @Test
    @DisplayName("erp + OPERATOR → [ERP_OPERATOR]")
    void erpOperator() {
        assertThat(RoleSeedPolicy.seed("erp", "OPERATOR")).containsExactly("ERP_OPERATOR");
    }

    @Test
    @DisplayName("mes + OPERATOR → [MES_OPERATOR]")
    void mesOperator() {
        assertThat(RoleSeedPolicy.seed("mes", "OPERATOR")).containsExactly("MES_OPERATOR");
    }

    @Test
    @DisplayName("fan-platform + OPERATOR → [FAN]")
    void fanOperator() {
        assertThat(RoleSeedPolicy.seed("fan-platform", "OPERATOR")).containsExactly("FAN");
    }

    // -----------------------------------------------------------------------
    // Unknown platform / account_type → []
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("unknown platform + OPERATOR → []")
    void unknownPlatformOperator_empty() {
        assertThat(RoleSeedPolicy.seed("finance", "OPERATOR")).isEmpty();
    }

    @Test
    @DisplayName("unknown platform + CONSUMER → []")
    void unknownPlatformConsumer_empty() {
        assertThat(RoleSeedPolicy.seed("finance", "CONSUMER")).isEmpty();
    }

    @Test
    @DisplayName("known platform + unknown account_type → []")
    void unknownAccountType_empty() {
        assertThat(RoleSeedPolicy.seed("ecommerce", "SERVICE")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Null-safety / blank → []
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("null platform → []")
    void nullPlatform_empty() {
        assertThat(RoleSeedPolicy.seed(null, "OPERATOR")).isEmpty();
    }

    @Test
    @DisplayName("blank platform → []")
    void blankPlatform_empty() {
        assertThat(RoleSeedPolicy.seed("   ", "OPERATOR")).isEmpty();
    }

    @Test
    @DisplayName("null account_type → []")
    void nullAccountType_empty() {
        assertThat(RoleSeedPolicy.seed("ecommerce", null)).isEmpty();
    }

    @Test
    @DisplayName("blank account_type → []")
    void blankAccountType_empty() {
        assertThat(RoleSeedPolicy.seed("ecommerce", "  ")).isEmpty();
    }

    @Test
    @DisplayName("both null → []")
    void bothNull_empty() {
        assertThat(RoleSeedPolicy.seed(null, null)).isEmpty();
    }

    @Test
    @DisplayName("seed never returns null (immutable empty on miss)")
    void neverNull() {
        assertThat(RoleSeedPolicy.seed("nope", "nope")).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("platform value is trimmed before lookup")
    void platformTrimmed() {
        assertThat(RoleSeedPolicy.seed("  wms  ", "OPERATOR")).containsExactly("WMS_OPERATOR");
    }
}
