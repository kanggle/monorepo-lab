package com.example.auth.domain.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-653 — {@link TenantContext#siteTenantOfPoolLogin}: the optional {@code siteTenantId} of the
 * three login events. A site only for a consumer-pool account AND a real initiating site client.
 */
@DisplayName("TenantContext.siteTenantOfPoolLogin (TASK-BE-653)")
class TenantContextSiteTenantTest {

    @ParameterizedTest(name = "account={0} client={1} → {2}")
    @CsvSource(nullValues = "NULL", value = {
            // pool account through a consumer-site client → that site
            "consumer-pool, ecommerce,     ecommerce",
            "consumer-pool, fan-platform,  fan-platform",
            "consumer-pool, ' ecommerce ', ecommerce",
            // pool account with no site: no client, the console, the pool itself, blank
            "consumer-pool, NULL,          NULL",
            "consumer-pool, iam,           NULL",
            "consumer-pool, consumer-pool, NULL",
            "consumer-pool, '  ',          NULL",
            // control: a non-pool account never carries a site, whatever the client
            "ecommerce,     ecommerce,     NULL",
            "fan-platform,  ecommerce,     NULL",
            "iam,           iam,           NULL",
            "NULL,          ecommerce,     NULL",
    })
    void siteTenantOfPoolLogin(String accountTenant, String clientTenant, String expected) {
        assertThat(TenantContext.siteTenantOfPoolLogin(accountTenant, clientTenant)).isEqualTo(expected);
    }
}
