package com.example.fanplatform.artist.integration;

import com.example.fanplatform.artist.adapter.out.store.HttpStoreSellerDirectory;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.example.fanplatform.artist.testsupport.FakeIdpAndStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-759 AC-1 · AC-2 end to end over the real application: the Spring-wired
 * {@link StoreSellerDirectory} (no {@code @MockitoBean} — the production bean), the real
 * {@code PATCH /api/agencies/{id}/store-seller} endpoint and the real schema, talking real HTTP
 * to an IdP + store stand-in. Complements the Docker-free {@code AgencyStoreSellerTransportTest}
 * by proving the BEAN that ships is the HTTP adapter and that the HTTP status codes are right.
 */
class StoreSellerLinkTransportIntegrationTest extends ArtistServiceIntegrationBase {

    private static final FakeIdpAndStore FAKE;

    static {
        try {
            FAKE = new FakeIdpAndStore();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void store(DynamicPropertyRegistry r) {
        r.add("iam.internal-client.token-uri", FAKE::tokenUri);
        r.add("artist.store-seller.base-url", FAKE::baseUrl);
    }

    @AfterAll
    static void stop() throws IOException {
        FAKE.close();
    }

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;
    @Autowired StoreSellerDirectory shippedDirectory;

    private HttpHeaders admin() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.signAdminToken("admin-759"));
        return h;
    }

    private ResponseEntity<String> call(String path, HttpMethod m, String body) {
        return rest.exchange(path, m, new HttpEntity<>(body, admin()), String.class);
    }

    private String storedSeller(String agencyId) throws Exception {
        JsonNode d = objectMapper.readTree(call("/api/agencies/" + agencyId, HttpMethod.GET, null).getBody())
                .path("data");
        return d.path("storeSellerId").isNull() ? null : d.path("storeSellerId").asText();
    }

    @Test
    @DisplayName("AC-4 — the bean that ships is the HTTP adapter")
    void shippedBeanIsTheHttpAdapter() {
        assertThat(shippedDirectory).isInstanceOf(HttpStoreSellerDirectory.class);
    }

    @Test
    @DisplayName("AC-1 · AC-2 — ACTIVE 200 saved · missing/CLOSED 422 · store down 503 unchanged · store up again 200")
    void linkOverTheWire() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> created = call("/api/agencies", HttpMethod.POST,
                "{\"name\":\"Transport " + suffix + "\"}");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String agencyId = objectMapper.readTree(created.getBody()).path("data").path("id").asText();
        String path = "/api/agencies/" + agencyId + "/store-seller";

        FAKE.active("good-" + suffix, "ACTIVE");
        FAKE.active("other-" + suffix, "ACTIVE");
        FAKE.active("closed-" + suffix, "CLOSED");

        ResponseEntity<String> ok = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"good-" + suffix + "\"}");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);

        ResponseEntity<String> ghost = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"ghost-" + suffix + "\"}");
        assertThat(ghost.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ghost.getBody()).contains("STORE_SELLER_NOT_FOUND");

        ResponseEntity<String> closed = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"closed-" + suffix + "\"}");
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(closed.getBody()).contains("STORE_SELLER_CLOSED");
        assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);

        FAKE.storeUp = false;
        try {
            ResponseEntity<String> down = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"other-" + suffix + "\"}");
            assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(down.getBody()).contains("STORE_SELLER_LOOKUP_UNAVAILABLE");
            assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);
        } finally {
            FAKE.storeUp = true;
        }

        ResponseEntity<String> again = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"other-" + suffix + "\"}");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(storedSeller(agencyId)).isEqualTo("other-" + suffix);
    }
}
