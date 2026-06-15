package com.example.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BodyHashUtil} — the single shared idempotency
 * body-hash utility.
 *
 * <p>Key properties verified:
 * <ul>
 *   <li>{@code sha256hex} is a stable, content-sensitive SHA-256 digest.</li>
 *   <li>JSON key ordering is normalised: {@code {"b":1,"a":2}} and
 *       {@code {"a":2,"b":1}} produce the same hash.</li>
 *   <li>Empty / null body has a stable, deterministic hash (SHA-256 of empty
 *       input), distinct from an empty JSON object {@code {}}.</li>
 *   <li><strong>Different JSON payloads produce different hashes</strong> — the
 *       content-sensitivity property that the TASK-BE-342 scala-module bug
 *       silently violated; locked in here so a future regression fails fast.</li>
 *   <li>Non-JSON bodies fall back to a raw-byte hash.</li>
 * </ul>
 *
 * <p>The {@link ObjectMapper} argument is ignored by {@link BodyHashUtil}
 * (retained for source compatibility); a vanilla mapper is passed to mirror
 * call sites.
 */
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

    @Test
    void normalizedJson_distinctValues_produceDistinctStrings() throws Exception {
        // Guards the BE-342 property at the canonicalisation layer: two bodies
        // with the same keys but different values MUST canonicalise differently.
        byte[] v1 = "{\"qty\":1,\"sku\":\"A\"}".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "{\"qty\":2,\"sku\":\"A\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(BodyHashUtil.normalizedJson(v1, MAPPER))
                .isNotEqualTo(BodyHashUtil.normalizedJson(v2, MAPPER));
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
