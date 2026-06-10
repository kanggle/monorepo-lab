package com.wms.master.adapter.in.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit test for the ADR-MONO-025 controller-side claim reader (TASK-BE-350).
 * The zone/location controllers thread {@link DataScopeSupport#warehouseScopeCodes}
 * straight into the query/use-case, so this pins the net-zero vs scoped decision.
 */
class DataScopeSupportTest {

    private static Jwt jwtWith(String claim, Object value) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none");
        if (claim != null) {
            b.claim(claim, value);
        } else {
            b.claim("sub", "operator"); // a JWT must carry at least one claim
        }
        return b.build();
    }

    @Test
    @DisplayName("deliberately scoped data_scope → the code set")
    void scoped() {
        assertThat(DataScopeSupport.warehouseScopeCodes(jwtWith("data_scope", List.of("WH-A", "WH-B"))))
                .containsExactlyInAnyOrder("WH-A", "WH-B");
    }

    @Test
    @DisplayName("legacy org_scope alias is honoured")
    void orgScopeAlias() {
        assertThat(DataScopeSupport.warehouseScopeCodes(jwtWith("org_scope", List.of("WH-A"))))
                .containsExactly("WH-A");
    }

    @Test
    @DisplayName("wildcard \"*\" → null (net-zero, unrestricted)")
    void wildcard() {
        assertThat(DataScopeSupport.warehouseScopeCodes(jwtWith("data_scope", List.of("*")))).isNull();
    }

    @Test
    @DisplayName("no data-scope claim → null (net-zero, base/machine token)")
    void noClaim() {
        assertThat(DataScopeSupport.warehouseScopeCodes(jwtWith(null, null))).isNull();
    }

    @Test
    @DisplayName("null jwt → null (defensive; resource server rejects unauthenticated earlier)")
    void nullJwt() {
        assertThat(DataScopeSupport.warehouseScopeCodes(null)).isNull();
    }
}
