package com.example.fanplatform.artist.adapter.out.store;

import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.example.fanplatform.artist.config.StoreSellerDirectoryConfig;
import com.example.fanplatform.artist.testsupport.FakeIdpAndStore;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-759 — the store seller adapter over REAL HTTP (an IdP + store stand-in on a socket),
 * built by the production wiring ({@link StoreSellerDirectoryConfig}), not by hand.
 *
 * <p>The mapping is the port's whole contract: a status only when the store said one, empty only
 * on the store's definite {@code SELLER_NOT_FOUND}, and «cannot verify» for everything else.
 */
@DisplayName("HttpStoreSellerDirectory — real HTTP mapping (TASK-MONO-759)")
class HttpStoreSellerDirectoryTest {

    private FakeIdpAndStore fake;
    private StoreSellerDirectory directory;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeIdpAndStore();
        directory = build("ecommerce", 1000, 1000);
    }

    @AfterEach
    void tearDown() throws Exception {
        fake.close();
    }

    private StoreSellerDirectory build(String tenant, int connectMs, int readMs) {
        return new StoreSellerDirectoryConfig().httpStoreSellerDirectory(
                fake.tokenUri(), "artist-service-client", "secret", "store.seller.read",
                tenant, fake.baseUrl(), connectMs, readMs);
    }

    // ------------------------------------------------------------------ definite answers

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED", "PENDING_PROVISIONING", "CLOSED"})
    @DisplayName("200 with a known status → that status verbatim")
    void knownStatus(String status) {
        fake.active("s-1", status);
        assertThat(directory.findStatus("s-1")).contains(status);
    }

    @Test
    @DisplayName("404 SELLER_NOT_FOUND → empty (the one definite «no such seller»)")
    void notFound() {
        assertThat(directory.findStatus("ghost")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("the wire: cc token (scope store.seller.read) → exchange (audience=ecommerce) → GET with the EXCHANGED bearer")
    void wireShape() throws Exception {
        fake.active("s-1", "ACTIVE");
        directory.findStatus("s-1");

        List<RecordedRequest> r = fake.requests;
        assertThat(r).hasSize(3);
        String cc = r.get(0).getBody().clone().readUtf8();
        assertThat(r.get(0).getPath()).isEqualTo("/oauth2/token");
        assertThat(r.get(0).getHeader("Authorization")).startsWith("Basic ");
        assertThat(cc).contains("grant_type=client_credentials").contains("scope=store.seller.read");

        String ex = r.get(1).getBody().clone().readUtf8();
        assertThat(ex).contains("token-exchange").contains("audience=ecommerce")
                .contains("subject_token=" + FakeIdpAndStore.CC_TOKEN);

        assertThat(r.get(2).getMethod()).isEqualTo("GET");
        assertThat(r.get(2).getPath()).isEqualTo("/internal/sellers/s-1");
        assertThat(r.get(2).getHeader("Authorization")).isEqualTo("Bearer " + FakeIdpAndStore.STORE_TOKEN);
    }

    @Test
    @DisplayName("the exchanged token is cached — a second lookup costs one store call, no token calls")
    void tokenCached() {
        fake.active("s-1", "ACTIVE");
        directory.findStatus("s-1");
        directory.findStatus("s-1");
        assertThat(fake.requests).hasSize(4);
        assertThat(fake.storeCalls()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 🔴 everything else → unavailable

    private void assertUnavailable(String sellerId) {
        assertThatThrownBy(() -> directory.findStatus(sellerId))
                .isInstanceOf(StoreSellerLookupUnavailableException.class);
    }

    @Test
    @DisplayName("🔴 store 500 → unavailable")
    void serverError() {
        fake.overrides.put("s-1", new Object[] {500, "{\"code\":\"INTERNAL_ERROR\"}"});
        assertUnavailable("s-1");
    }

    @Test
    @DisplayName("🔴 a 404 WITHOUT SELLER_NOT_FOUND (route miss / proxy) is NOT «no such seller»")
    void codeless404() {
        fake.overrides.put("s-1", new Object[] {404, "{\"code\":\"NOT_FOUND\"}"});
        assertUnavailable("s-1");
        fake.overrides.put("s-2", new Object[] {404, ""});
        assertUnavailable("s-2");
    }

    @Test
    @DisplayName("🔴 401 / 403 from the gateway or product-service → unavailable")
    void authRefusal() {
        fake.overrides.put("s-1", new Object[] {401, "{\"code\":\"UNAUTHORIZED\"}"});
        assertUnavailable("s-1");
        fake.overrides.put("s-2", new Object[] {403, "{\"code\":\"FORBIDDEN\"}"});
        assertUnavailable("s-2");
    }

    @Test
    @DisplayName("🔴 an unknown status string (a new store enum value) → unavailable (Edge Case 3)")
    void unknownStatus() {
        fake.active("s-1", "FROZEN");
        assertUnavailable("s-1");
    }

    @Test
    @DisplayName("🔴 malformed body / answer about a different seller → unavailable")
    void malformedOrMismatched() {
        fake.overrides.put("s-1", new Object[] {200, "not json"});
        assertUnavailable("s-1");
        fake.sellers.put("s-2", "{\"sellerId\":\"someone-else\",\"status\":\"ACTIVE\"}");
        assertUnavailable("s-2");
        fake.sellers.put("s-3", "{\"sellerId\":\"s-3\"}");
        assertUnavailable("s-3");
    }

    @Test
    @DisplayName("🔴 store down (connection dropped) → unavailable")
    void storeDown() {
        fake.active("s-1", "ACTIVE");
        fake.storeUp = false;
        assertUnavailable("s-1");
    }

    @Test
    @DisplayName("🔴 store slower than the read timeout → unavailable")
    void storeTimeout() {
        directory = build("ecommerce", 1000, 300);
        fake.active("s-1", "ACTIVE");
        fake.storeDelayMs = 2000;
        assertUnavailable("s-1");
    }

    @Test
    @DisplayName("🔴 IdP down → unavailable, the store is never asked")
    void idpDown() {
        fake.active("s-1", "ACTIVE");
        fake.idpUp = false;
        assertUnavailable("s-1");
        assertThat(fake.storeCalls()).isZero();
    }

    @Test
    @DisplayName("🔴 exchange refused (a tenant the catalog does not grant) → unavailable, the store is never asked")
    void exchangeRefused() {
        directory = build("wms", 1000, 1000);
        fake.active("s-1", "ACTIVE");
        assertUnavailable("s-1");
        assertThat(fake.storeCalls()).isZero();
    }
}
