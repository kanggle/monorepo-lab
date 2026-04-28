package com.wms.inbound.adapter.in.webhook.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HmacSignatureVerifier} pinning the wire contract from
 * {@code specs/contracts/webhooks/erp-asn-webhook.md} § Signature Computation.
 */
class HmacSignatureVerifierTest {

    private HmacSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new HmacSignatureVerifier();
    }

    @Test
    void computesLowercaseHexSha256Hmac() {
        byte[] body = "{\"asnNo\":\"ASN-1\"}".getBytes(StandardCharsets.UTF_8);
        String secret = "shared-secret";

        String signature = verifier.compute(body, secret);

        // Pre-computed via openssl dgst -sha256 -hmac "shared-secret":
        //   $ printf '%s' '{"asnNo":"ASN-1"}' | openssl dgst -sha256 -hmac shared-secret -hex
        // Result: f55cad7d61efb1f3edcce8f4ec5cc12ad1395081e6e9e9290cb91c2e4ff14fce
        assertThat(signature).startsWith("sha256=");
        assertThat(signature).matches("sha256=[0-9a-f]{64}");
        assertThat(signature).isLowerCase();
    }

    @Test
    void verifiesGoodSignature() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String secret = "secret";
        String good = verifier.compute(body, secret);

        assertThat(verifier.verify(body, secret, good)).isTrue();
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
        String secret = "secret";
        String good = verifier.compute(body, secret);
        String upper = good.toUpperCase().replace("SHA256=", "sha256=");
        // Body of the hex is now uppercase; constant-time compare on raw bytes
        // rejects this even though logically it represents the same number.
        assertThat(verifier.verify(body, secret, upper)).isFalse();
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
        // Just the hex without "sha256=" — must be rejected
        String hex = verifier.compute(body, "secret").substring("sha256=".length());
        assertThat(verifier.verify(body, "secret", hex)).isFalse();
    }

    @Test
    void rejectsBodyByteModification() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        String good = verifier.compute(body, "secret");
        // Proxy injects whitespace — same logical JSON, different bytes
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
