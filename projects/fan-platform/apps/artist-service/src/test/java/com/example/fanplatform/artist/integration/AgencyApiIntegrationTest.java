package com.example.fanplatform.artist.integration;

import com.example.fanplatform.artist.application.exception.StoreSellerLookupUnavailableException;
import com.example.fanplatform.artist.application.port.out.StoreSellerDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-748 end to end over the real schema (V4): AC-2 (CRUD · duplicate 409 ·
 * affiliation change), AC-3 (seller link against the {@link StoreSellerDirectory}
 * port — mocked here to pin the RULES; the real transport is TASK-MONO-759's
 * {@code StoreSellerLinkTransportIntegrationTest})
 * and AC-4 (the artist detail shows the agency name, from the entity).
 */
class AgencyApiIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean StoreSellerDirectory storeSellerDirectory;

    private HttpHeaders admin() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.signAdminToken("admin-1"));
        return h;
    }

    private HttpHeaders fan() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken("fan-1"));
        return h;
    }

    private ResponseEntity<String> call(String path, HttpMethod m, String body, HttpHeaders h) {
        return rest.exchange(path, m, new HttpEntity<>(body, h), String.class);
    }

    private JsonNode data(ResponseEntity<String> r) throws Exception {
        return objectMapper.readTree(r.getBody()).path("data");
    }

    private String createAgency(String name) throws Exception {
        ResponseEntity<String> r = call("/api/agencies", HttpMethod.POST,
                "{\"name\":\"" + name + "\"}", admin());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return data(r).path("id").asText();
    }

    @Test
    @DisplayName("AC-2: create · duplicate (incl. whitespace variant) 409 · rename · list")
    void crud() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String id = createAgency("Agency " + suffix);

        ResponseEntity<String> dup = call("/api/agencies", HttpMethod.POST,
                "{\"name\":\"  Agency   " + suffix + " \"}", admin());
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody()).contains("AGENCY_NAME_CONFLICT");

        ResponseEntity<String> renamed = call("/api/agencies/" + id, HttpMethod.PATCH,
                "{\"name\":\"Renamed " + suffix + "\"}", admin());
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(renamed).path("name").asText()).isEqualTo("Renamed " + suffix);

        ResponseEntity<String> fanCreate = call("/api/agencies", HttpMethod.POST,
                "{\"name\":\"Nope " + suffix + "\"}", fanJson());
        assertThat(fanCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> list = call("/api/agencies?size=100", HttpMethod.GET, null, fan());
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("Renamed " + suffix);
    }

    private HttpHeaders fanJson() {
        HttpHeaders h = fan();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("AC-2 + AC-4: affiliate an artist → detail shows the ENTITY name; rename follows; clear empties")
    void affiliation_and_display() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String agencyId = createAgency("Label " + suffix);

        ResponseEntity<String> reg = call("/api/artists", HttpMethod.POST,
                "{\"accountId\":\"acc-ag-" + suffix + "\",\"artistType\":\"SOLO\",\"stageName\":\"AG-"
                        + suffix + "\",\"agencyId\":\"" + agencyId + "\"}", admin());
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String artistId = data(reg).path("id").asText();
        assertThat(data(reg).path("agencyId").asText()).isEqualTo(agencyId);
        call("/api/artists/" + artistId + "/status", HttpMethod.PATCH, "{\"status\":\"PUBLISHED\"}", admin());

        // AC-4 — what a reader sees
        JsonNode detail = data(call("/api/artists/" + artistId, HttpMethod.GET, null, fan()));
        assertThat(detail.path("agency").asText()).isEqualTo("Label " + suffix);

        // rename the agency → the artist's display follows (it is read from the entity)
        call("/api/agencies/" + agencyId, HttpMethod.PATCH, "{\"name\":\"Label2 " + suffix + "\"}", admin());
        detail = data(call("/api/artists/" + artistId, HttpMethod.GET, null, fan()));
        assertThat(detail.path("agency").asText()).isEqualTo("Label2 " + suffix);

        // move to another agency, then clear (Edge Case 2 — unaffiliated)
        String other = createAgency("Other " + suffix);
        ResponseEntity<String> moved = call("/api/artists/" + artistId + "/agency", HttpMethod.PATCH,
                "{\"agencyId\":\"" + other + "\"}", admin());
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(moved).path("agency").asText()).isEqualTo("Other " + suffix);

        ResponseEntity<String> cleared = call("/api/artists/" + artistId + "/agency", HttpMethod.PATCH,
                "{\"agencyId\":null}", admin());
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(cleared).path("agencyId").isNull()).isTrue();
        assertThat(data(cleared).path("agency").isNull()).isTrue();

        // unknown agency → 404, nothing changes
        ResponseEntity<String> unknown = call("/api/artists/" + artistId + "/agency", HttpMethod.PATCH,
                "{\"agencyId\":\"" + UUID.randomUUID() + "\"}", admin());
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).contains("AGENCY_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-3: ACTIVE → saved · CLOSED / missing → 422, unchanged · lookup failure → 503, unchanged")
    void sellerLink_failClosed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String agencyId = createAgency("Seller " + suffix);
        String path = "/api/agencies/" + agencyId + "/store-seller";

        when(storeSellerDirectory.findStatus("good-" + suffix)).thenReturn(Optional.of("ACTIVE"));
        ResponseEntity<String> ok = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"good-" + suffix + "\"}", admin());
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);

        when(storeSellerDirectory.findStatus("closed-" + suffix)).thenReturn(Optional.of("CLOSED"));
        ResponseEntity<String> closed = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"closed-" + suffix + "\"}", admin());
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(closed.getBody()).contains("STORE_SELLER_CLOSED");
        assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);

        when(storeSellerDirectory.findStatus("ghost-" + suffix)).thenReturn(Optional.empty());
        ResponseEntity<String> ghost = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"ghost-" + suffix + "\"}", admin());
        assertThat(ghost.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ghost.getBody()).contains("STORE_SELLER_NOT_FOUND");
        assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);

        when(storeSellerDirectory.findStatus("down-" + suffix))
                .thenThrow(new StoreSellerLookupUnavailableException("store down"));
        ResponseEntity<String> down = call(path, HttpMethod.PATCH, "{\"storeSellerId\":\"down-" + suffix + "\"}", admin());
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(down.getBody()).contains("STORE_SELLER_LOOKUP_UNAVAILABLE");
        assertThat(storedSeller(agencyId)).isEqualTo("good-" + suffix);
    }

    private String storedSeller(String agencyId) throws Exception {
        JsonNode d = data(call("/api/agencies/" + agencyId, HttpMethod.GET, null, fan()));
        return d.path("storeSellerId").isNull() ? null : d.path("storeSellerId").asText();
    }
}
