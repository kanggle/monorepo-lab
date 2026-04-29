package com.wms.outbound.adapter.in.webhook.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HmacVerifierTest {

    private HmacVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new HmacVerifier();
    }

    @Test
    void computesLowercaseHexSha256Hmac() {
        byte[] body = "{\"orderNo\":\"ORD-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = verifier.compute(body, "shared-secret");

        assertThat(signature).startsWith("sha256=");
        assertThat(signature).matches("sha256=[0-9a-f]{64}");
        assertThat(signature).isLowerCase();
    }

    @Test
    void verifiesGoodSignature() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String good = verifier.compute(body, "secret");

        assertThat(verifier.verify(body, "secret", good)).isTrue();
    }

    @Test
    void rejectsMismatchSignature() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String wrong = "sha256=" + "0".repeat(64);
        assertThat(verifier.verify(body, "secret", wrong)).isFalse();
    }

    @Test
    void rejectsUppercaseHexSignature() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String good = verifier.compute(body, "secret");
        String upper = good.toUpperCase().replace("SHA256=", "sha256=");
        assertThat(verifier.verify(body, "secret", upper)).isFalse();
    }

    @Test
    void rejectsNullSignatureHeader() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(body, "secret", null)).isFalse();
    }

    @Test
    void rejectsBlankSignatureHeader() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(body, "secret", "")).isFalse();
        assertThat(verifier.verify(body, "secret", "   ")).isFalse();
    }

    @Test
    void rejectsHeaderWithoutSha256Prefix() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String hex = verifier.compute(body, "secret").substring("sha256=".length());
        assertThat(verifier.verify(body, "secret", hex)).isFalse();
    }

    @Test
    void rejectsBodyByteModification() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String good = verifier.compute(body, "secret");
        byte[] modified = "{\"a\":1} ".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(modified, "secret", good)).isFalse();
    }

    @Test
    void emptySecretIsRejectedAtCompute() {
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.compute(body, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> verifier.compute(body, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullBodyIsRejectedAtCompute() {
        assertThatThrownBy(() -> verifier.compute(null, "secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
