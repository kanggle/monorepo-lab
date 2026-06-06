package com.kanggle.platformconsole.bff.adapter.outbound.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

/**
 * Static helper for the authenticated outbound read legs used by
 * {@link IamAccountsReadAdapter}, {@link WmsInventoryReadAdapter},
 * {@link ScmInventoryReadAdapter}, {@link FinanceBalanceReadAdapter},
 * and {@link ErpDepartmentsReadAdapter}.
 *
 * <p>All authenticated legs share the same three-header pattern
 * (ADR-MONO-017 D4.A, console-integration-contract § 2.4.5–2.4.8):
 * <ol>
 *   <li>{@code Authorization: Bearer <credential>}</li>
 *   <li>{@code X-Tenant-Id: <tenantId>} (forwarded verbatim — D6.A)</li>
 *   <li>{@code Accept: application/json}</li>
 * </ol>
 *
 * <p>The only variation between adapters is the {@link RestClient} instance
 * (one per domain, keyed by qualifier) and the URI. This helper eliminates
 * the duplicated header-set boilerplate without introducing a base class
 * (preferred here because the adapters already extend their port contracts
 * via distinct nested interfaces).
 */
public final class RestClientHelper {

    private RestClientHelper() {
        // utility — no instances
    }

    /**
     * Executes an authenticated {@code GET} against the given URI, attaching
     * the standard BFF outbound header set.
     *
     * @param client     the pre-configured domain {@link RestClient}
     * @param uri        the target URI (absolute or relative to the client's
     *                   base URL)
     * @param tenantId   forwarded verbatim as {@code X-Tenant-Id} (D6.A)
     * @param credential the bearer token value (without the {@code Bearer } prefix)
     * @return the response body deserialized as {@code Map<String, Object>}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> authenticatedGet(
            RestClient client,
            String uri,
            String tenantId,
            String credential) {
        return (Map<String, Object>) client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .header("X-Tenant-Id", tenantId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    /**
     * URI-builder overload for adapters whose URI requires query parameters
     * (e.g. pagination, filter params). Accepts a {@link Function} over
     * {@link UriBuilder} so callers can construct parameterised URIs inline
     * without losing the standard header set.
     *
     * @param client        the pre-configured domain {@link RestClient}
     * @param uriFunction   a URI-builder function (e.g.
     *                      {@code b -> b.path("/api/...").queryParam("page", 0).build()})
     * @param tenantId      forwarded verbatim as {@code X-Tenant-Id} (D6.A)
     * @param credential    the bearer token value (without the {@code Bearer } prefix)
     * @return the response body deserialized as {@code Map<String, Object>}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> authenticatedGet(
            RestClient client,
            Function<UriBuilder, URI> uriFunction,
            String tenantId,
            String credential) {
        return (Map<String, Object>) client.get()
                .uri(uriFunction)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .header("X-Tenant-Id", tenantId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
