package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoleSeedPolicy} — the ADR-MONO-033 S3 aud-default seed table
 * (TASK-BE-369). TASK-MONO-263 (ADR-032 D5 step 4): decoupled from {@code account_type}
 * — the seed is now keyed on platform only and returns the CONSUMER role
 * ({@code ecommerce → CUSTOMER}, {@code fan-platform → FAN}, else {@code []}). The
 * OPERATOR branch is removed (operators get domain roles at assume-tenant, BE-376).
 */
class RoleSeedPolicyTest {

    // -----------------------------------------------------------------------
    // Consumer seed (platform-keyed)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ecommerce → [CUSTOMER]")
    void ecommerce() {
        assertThat(RoleSeedPolicy.seed("ecommerce")).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("fan-platform → [FAN]")
    void fan() {
        assertThat(RoleSeedPolicy.seed("fan-platform")).containsExactly("FAN");
    }

    // -----------------------------------------------------------------------
    // Non-consumer platforms → [] (operators seeded at assume-tenant, BE-376)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("wms → [] (no consumer surface; operators via assume-tenant)")
    void wms_empty() {
        assertThat(RoleSeedPolicy.seed("wms")).isEmpty();
    }

    @Test
    @DisplayName("scm → []")
    void scm_empty() {
        assertThat(RoleSeedPolicy.seed("scm")).isEmpty();
    }

    @Test
    @DisplayName("erp → []")
    void erp_empty() {
        assertThat(RoleSeedPolicy.seed("erp")).isEmpty();
    }

    @Test
    @DisplayName("mes → []")
    void mes_empty() {
        assertThat(RoleSeedPolicy.seed("mes")).isEmpty();
    }

    @Test
    @DisplayName("gap (operator base login platform) → [] (operators get roles at assume-tenant)")
    void gap_empty() {
        assertThat(RoleSeedPolicy.seed("gap")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Unknown platform → []
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("unknown platform → []")
    void unknownPlatform_empty() {
        assertThat(RoleSeedPolicy.seed("finance")).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Null-safety / blank → []
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("null platform → []")
    void nullPlatform_empty() {
        assertThat(RoleSeedPolicy.seed(null)).isEmpty();
    }

    @Test
    @DisplayName("blank platform → []")
    void blankPlatform_empty() {
        assertThat(RoleSeedPolicy.seed("   ")).isEmpty();
    }

    @Test
    @DisplayName("seed never returns null (immutable empty on miss)")
    void neverNull() {
        assertThat(RoleSeedPolicy.seed("nope")).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("platform value is trimmed before lookup")
    void platformTrimmed() {
        assertThat(RoleSeedPolicy.seed("  ecommerce  ")).containsExactly("CUSTOMER");
    }
}
