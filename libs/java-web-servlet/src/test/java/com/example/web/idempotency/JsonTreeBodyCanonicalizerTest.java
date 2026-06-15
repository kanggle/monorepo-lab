package com.example.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonTreeBodyCanonicalizer} (Family B — master/admin
 * {@code readTree} tree-sort, lenient raw-byte fallback). Verifies parity with
 * the lifted {@code master-service RequestBodyCanonicalizer} semantics.
 */
class JsonTreeBodyCanonicalizerTest {

    private static final String SHA256_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final JsonTreeBodyCanonicalizer canon = new JsonTreeBodyCanonicalizer(new ObjectMapper());

    @Test
    void emptyBody_hashesToSha256OfEmpty() {
        assertThat(canon.hash(new byte[0])).isEqualTo(SHA256_EMPTY);
    }

    @Test
    void nullBody_hashesToSha256OfEmpty() {
        assertThat(canon.hash(null)).isEqualTo(SHA256_EMPTY);
    }

    @Test
    void keyOrderNormalised_sameHash() {
        byte[] ab = "{\"b\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] ba = "{\"a\":2,\"b\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(canon.hash(ab)).isEqualTo(canon.hash(ba));
    }

    @Test
    void whitespaceIgnored_sameHash() {
        byte[] compact = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] spaced = "{ \"a\" : 1 , \"b\" : 2 }".getBytes(StandardCharsets.UTF_8);
        assertThat(canon.hash(compact)).isEqualTo(canon.hash(spaced));
    }

    @Test
    void differentValues_differentHash() {
        byte[] v1 = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);
        assertThat(canon.hash(v1)).isNotEqualTo(canon.hash(v2));
    }

    @Test
    void arrayOrderPreserved_differentHash() {
        byte[] a = "{\"x\":[1,2,3]}".getBytes(StandardCharsets.UTF_8);
        byte[] b = "{\"x\":[3,2,1]}".getBytes(StandardCharsets.UTF_8);
        assertThat(canon.hash(a)).isNotEqualTo(canon.hash(b));
    }

    @Test
    void nonJsonBody_fallsBackToRawByteHash() {
        byte[] plain = "not-json-content".getBytes(StandardCharsets.UTF_8);
        assertThat(canon.hash(plain)).isEqualTo(BodyHashUtil.sha256hex(plain));
    }

    @Test
    void canonicalize_nestedObjectSortedAtEveryLevel() {
        byte[] nested = "{\"z\":{\"d\":1,\"c\":2},\"a\":3}".getBytes(StandardCharsets.UTF_8);
        assertThat(canon.canonicalize(nested)).isEqualTo("{\"a\":3,\"z\":{\"c\":2,\"d\":1}}");
    }
}
