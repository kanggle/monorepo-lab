package com.wms.inbound.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BodyHashUtil}.
 *
 * <p>Key properties verified:
 * <ul>
 *   <li>JSON key ordering is normalised: {@code {"b":1,"a":2}} and
 *       {@code {"a":2,"b":1}} produce the same hash.</li>
 *   <li>Empty body has a stable, deterministic hash (SHA-256 of empty input).</li>
 *   <li>Different JSON payloads produce different hashes.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BodyHashUtilTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // sha256hex
    // -------------------------------------------------------------------------

    @Test
    void sha256hex_emptyBytes_producesKnownHash() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String hash = BodyHashUtil.sha256hex(new byte[0]);
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256hex_sameInput_returnsSameHash() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(BodyHashUtil.sha256hex(input))
                .isEqualTo(BodyHashUtil.sha256hex(input));
    }

    @Test
    void sha256hex_differentInput_returnsDifferentHash() {
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] b = "world".getBytes(StandardCharsets.UTF_8);
        assertThat(BodyHashUtil.sha256hex(a)).isNotEqualTo(BodyHashUtil.sha256hex(b));
    }

    // -------------------------------------------------------------------------
    // normalizedJson
    // -------------------------------------------------------------------------

    @Test
    void normalizedJson_sortsDifferentKeyOrders_toSameString() throws Exception {
        byte[] ab = "{\"b\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] ba = "{\"a\":2,\"b\":1}".getBytes(StandardCharsets.UTF_8);

        String normalAb = BodyHashUtil.normalizedJson(ab, MAPPER);
        String normalBa = BodyHashUtil.normalizedJson(ba, MAPPER);

        assertThat(normalAb).isEqualTo(normalBa);
    }

    // -------------------------------------------------------------------------
    // computeHash
    // -------------------------------------------------------------------------

    @Test
    void computeHash_emptyBody_isStable() {
        String h1 = BodyHashUtil.computeHash(new byte[0], MAPPER);
        String h2 = BodyHashUtil.computeHash(new byte[0], MAPPER);
        assertThat(h1).isEqualTo(h2);
        // Must equal SHA-256("") since empty body bypasses JSON parsing
        assertThat(h1).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void computeHash_nullBody_isStableAndSameAsEmpty() {
        String h = BodyHashUtil.computeHash(null, MAPPER);
        assertThat(h).isEqualTo(BodyHashUtil.computeHash(new byte[0], MAPPER));
    }

    @Test
    void computeHash_sameJsonDifferentKeyOrder_produceSameHash() {
        byte[] ab = "{\"b\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] ba = "{\"a\":2,\"b\":1}".getBytes(StandardCharsets.UTF_8);

        String hashAb = BodyHashUtil.computeHash(ab, MAPPER);
        String hashBa = BodyHashUtil.computeHash(ba, MAPPER);

        assertThat(hashAb).isEqualTo(hashBa);
    }

    @Test
    void computeHash_differentJson_produceDifferentHashes() {
        byte[] body1 = "{\"asnNo\":\"ASN-001\"}".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "{\"asnNo\":\"ASN-002\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(BodyHashUtil.computeHash(body1, MAPPER))
                .isNotEqualTo(BodyHashUtil.computeHash(body2, MAPPER));
    }

    @Test
    void computeHash_emptyJsonObject_differFromEmptyBody() {
        byte[] emptyJson = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] emptyBody = new byte[0];

        assertThat(BodyHashUtil.computeHash(emptyJson, MAPPER))
                .isNotEqualTo(BodyHashUtil.computeHash(emptyBody, MAPPER));
    }

    @Test
    void computeHash_nonJsonBody_isHashedRawBytes() {
        // plain text — not valid JSON; hash falls back to raw bytes
        byte[] plainText = "not-json-content".getBytes(StandardCharsets.UTF_8);
        String hash = BodyHashUtil.computeHash(plainText, MAPPER);
        assertThat(hash).isEqualTo(BodyHashUtil.sha256hex(plainText));
    }
}
