package com.example.admin.presentation;

import com.example.admin.infrastructure.security.AdminJwtKeyStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Serves the admin IdP's JWKS document at
 * {@code GET /.well-known/admin/jwks.json}.
 *
 * <p>Exposes public keys only for every currently registered {@code kid}
 * (active + grace-period rotations). Unauthenticated per
 * {@code specs/services/admin-service/architecture.md} — "JWKS Exposure
 * Policy". Cached for up to 5 minutes.
 */
@RestController
public class WellKnownController {

    private final AdminJwtKeyStore keyStore;

    public WellKnownController(AdminJwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @GetMapping(path = "/.well-known/admin/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        for (Map.Entry<String, RSAPublicKey> entry : keyStore.publicKeys().entrySet()) {
            RSAPublicKey pk = entry.getValue();
            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("use", "sig");
            jwk.put("alg", "RS256");
            jwk.put("kid", entry.getKey());
            jwk.put("n", base64Url(pk.getModulus().toByteArray()));
            jwk.put("e", base64Url(pk.getPublicExponent().toByteArray()));
            keys.add(jwk);
        }
        Map<String, Object> body = Map.of("keys", keys);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(body);
    }

    /**
     * Encodes a big-endian two's-complement integer (as produced by
     * {@link java.math.BigInteger#toByteArray()}) in the unsigned,
     * big-endian, base64url (no-padding) form required by RFC 7518 §6.3.1.
     */
    private static String base64Url(byte[] bytes) {
        int start = 0;
        // Strip leading sign byte (0x00) so the value is unsigned.
        while (start < bytes.length - 1 && bytes[start] == 0) {
            start++;
        }
        byte[] unsigned = new byte[bytes.length - start];
        System.arraycopy(bytes, start, unsigned, 0, unsigned.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
